/*
    Glint, a fork of LibrePods - AirPods liberated from Apple's ecosystem
    Copyright (C) 2026 Glint contributors

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>.
*/

package me.kavishdevar.librepods.services

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import me.kavishdevar.librepods.bluetooth.HeadphoneBeacon
import me.kavishdevar.librepods.presentation.overlays.IslandEvent
import me.kavishdevar.librepods.presentation.overlays.PodsSnapshot

/** Live state of the chosen non-AirPods headphones (Beats Solo 4 or any other). */
data class HeadphoneState(
    /** The followed device, or null while AirPods are the chosen device. */
    val device: ChosenDevice? = null,
    val connected: Boolean = false,
    /** 0-100, or null when neither Android nor the headphones' beacon has said. */
    val battery: Int? = null,
    val source: BatterySource = BatterySource.None,
) {
    enum class BatterySource { None, Android, Beacon }

    /** The overlays' data for these headphones: one battery, no case, no ear or mode info. */
    fun snapshot(): PodsSnapshot = PodsSnapshot(
        name = device?.name ?: "Headphones",
        left = battery, right = battery, case = null,
        headphones = true,
    )
}

/**
 * Follows the chosen headphones when they aren't AirPods. Everything here is ordinary Android
 * (audio routing, the battery level Android already shows in Bluetooth settings, media keys),
 * plus Apple's nearby beacon for the Beats Solo 4. It never opens the AirPods control channel
 * and never sends anything to the headphones, so it can't change or break their settings.
 */
object HeadphoneLink {
    private const val TAG = "HeadphoneLink"

    /** Android's own "a Bluetooth device's battery changed" broadcast (hidden constant, stable since Android 8.1). */
    private const val ACTION_BATTERY_LEVEL_CHANGED = "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED"
    private const val EXTRA_BATTERY_LEVEL = "android.bluetooth.device.extra.BATTERY_LEVEL"

    private val _state = MutableStateFlow(HeadphoneState())
    val state: StateFlow<HeadphoneState> = _state.asStateFlow()

    private val main = Handler(Looper.getMainLooper())
    private val gate = HeadphoneBeacon.Gate()
    private val lowBattery = LowBatteryLatch()
    private var receiver: BroadcastReceiver? = null
    @SuppressLint("StaticFieldLeak") // application context only
    private var appContext: Context? = null
    private var systemLevel: Int? = null
    private var beaconLevel: Int? = null
    private var beaconAt = 0L
    private var lastConnectedPop = 0L

    /**
     * Answers "is this address connected for audio right now?" (the service's profile
     * proxies), or null when it can't tell yet. Falls back to Android's audio outputs.
     */
    @Volatile var probe: ((String) -> Boolean?)? = null

    /** Starts listening (called by the service once). Safe to call again. */
    fun start(context: Context) {
        val app = context.applicationContext
        appContext = app
        if (receiver == null) {
            val r = object : BroadcastReceiver() {
                override fun onReceive(c: Context, intent: Intent) {
                    val dev = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java) ?: return
                    val level = intent.getIntExtra(EXTRA_BATTERY_LEVEL, -1)
                    onSystemBattery(dev.address, level)
                }
            }
            try {
                app.registerReceiver(r, IntentFilter(ACTION_BATTERY_LEVEL_CHANGED), Context.RECEIVER_EXPORTED)
                receiver = r
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't listen for battery changes", e)
            }
        }
        refresh(app)
    }

    fun stop() {
        val app = appContext ?: return
        receiver?.let { runCatching { app.unregisterReceiver(it) } }
        receiver = null
    }

    /** Re-reads who is chosen, whether it's connected, and its battery. Call after any change. */
    fun refresh(context: Context? = appContext) {
        val app = (context ?: return).applicationContext
        appContext = app
        val chosen = DeviceChoice.current(app)
        if (chosen.isAirPods) {
            reset()
            return
        }
        val before = _state.value
        if (before.device?.address?.equals(chosen.address, ignoreCase = true) != true) {
            systemLevel = null
            beaconLevel = null
            gate.clear()
            lowBattery.reset()
        }
        val connected = isConnected(app, chosen.address)
        if (connected) readSystemBattery(app, chosen.address)?.let { systemLevel = it }
        if (!connected) {
            beaconLevel = null
            systemLevel = null
        }
        publish(chosen, connected)
    }

    /** Android reports [address] connecting or disconnecting. */
    fun onConnectionEvent(context: Context, address: String) {
        val chosen = DeviceChoice.current(context)
        if (chosen.isAirPods || !address.equals(chosen.address, ignoreCase = true)) return
        // Profile state settles a moment after the broadcast; look now and again shortly.
        refresh(context)
        main.postDelayed({ refresh(context) }, 1_500L)
    }

    private fun onSystemBattery(address: String, level: Int) {
        val chosen = _state.value.device ?: return
        if (!address.equals(chosen.address, ignoreCase = true)) return
        systemLevel = level.takeIf { it in 0..100 }
        publish(chosen, _state.value.connected || systemLevel != null)
    }

    /** A beacon from the BLE scanner (main thread). */
    fun onBeacon(reading: HeadphoneBeacon.Reading) = main.post {
        val s = _state.value
        val chosen = s.device ?: return@post
        val level = gate.accept(reading, chosen.kind.beaconModel, s.connected) ?: return@post
        beaconLevel = level
        beaconAt = reading.at
        publish(chosen, s.connected)
    }

    /** Screenshots and the Lab: show [state] without real headphones. */
    internal fun preview(state: HeadphoneState) {
        _state.value = state
    }

    private fun reset() {
        systemLevel = null
        beaconLevel = null
        gate.clear()
        lowBattery.reset()
        _state.value = HeadphoneState()
    }

    private fun publish(chosen: ChosenDevice, connected: Boolean) {
        // Android's number wins (it comes from the headphones over the audio link); the beacon
        // fills in when Android has none, and only while it's fresh.
        val beacon = beaconLevel?.takeIf { System.currentTimeMillis() - beaconAt < 120_000L }
        val (battery, source) = when {
            !connected -> null to HeadphoneState.BatterySource.None
            systemLevel != null -> systemLevel to HeadphoneState.BatterySource.Android
            beacon != null -> beacon to HeadphoneState.BatterySource.Beacon
            else -> null to HeadphoneState.BatterySource.None
        }
        val before = _state.value
        val now = HeadphoneState(chosen, connected, battery, source)
        if (now == before) return
        _state.update { now }
        GlintOverlaysBridge.publish(now)
        val service = ServiceManager.getService() ?: return
        val t = android.os.SystemClock.elapsedRealtime()
        if (connected && !before.connected && before.device?.address.equals(chosen.address, ignoreCase = true) &&
            t - lastConnectedPop > 30_000L
        ) {
            // At most once every 30 s, like the AirPods: a flaky link doesn't keep popping it.
            lastConnectedPop = t
            main.post { service.showIslandEvent(IslandEvent.Connected, IslandPrefs.Trigger.Connected) }
        }
        lowBattery.check(battery, charging = false)?.let { level ->
            main.post { service.showIslandEvent(IslandEvent.LowBattery(level), IslandPrefs.Trigger.LowBattery) }
        }
        main.post { GlintOverlaysBridge.refreshMini(service) }
    }

    @SuppressLint("MissingPermission")
    private fun isConnected(context: Context, address: String): Boolean {
        if (address.isBlank()) return false
        probe?.invoke(address)?.let { return it }
        return audioOutputHas(context, address)
    }

    /** Whether Android lists [address] among its Bluetooth audio outputs. */
    fun audioOutputHas(context: Context, address: String): Boolean = runCatching {
        if (address.isBlank()) return false
        val am = context.getSystemService(AudioManager::class.java) ?: return false
        am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { d ->
            (d.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || d.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                d.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) && d.address.equals(address, ignoreCase = true)
        }
    }.getOrDefault(false)

    /**
     * The battery Android shows for this device in Bluetooth settings. The method is hidden
     * (but present since Android 8.1); some phones block it, so a failure just means "unknown"
     * and the battery broadcast or beacon fills in.
     */
    @SuppressLint("MissingPermission")
    private fun readSystemBattery(context: Context, address: String): Int? = runCatching {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return null
        val device = adapter.getRemoteDevice(address)
        (BluetoothDevice::class.java.getMethod("getBatteryLevel").invoke(device) as? Int)?.takeIf { it in 0..100 }
    }.getOrNull()

    /** Announces 20% and 10% once each; re-arms after charging above 25%. Pure, for tests. */
    class LowBatteryLatch {
        private var announced = 100

        fun reset() { announced = 100 }

        /** Returns the level to announce now, or null. */
        fun check(level: Int?, charging: Boolean): Int? {
            if (level == null) return null
            if (level > 25) { announced = 100; return null }
            if (charging) return null
            val threshold = when {
                level <= 10 -> 10
                level <= 20 -> 20
                else -> return null
            }
            if (threshold >= announced) return null
            announced = threshold
            return level
        }
    }
}

/** Keeps the overlay code out of the link's logic (and lets tests run without overlays). */
internal object GlintOverlaysBridge {
    fun publish(state: HeadphoneState) {
        if (state.device == null) return
        me.kavishdevar.librepods.presentation.overlays.GlintOverlays.updateSnapshot(state.snapshot())
    }

    fun refreshMini(context: Context) {
        me.kavishdevar.librepods.presentation.overlays.GlintOverlays.refreshMiniIsland(context)
    }
}
