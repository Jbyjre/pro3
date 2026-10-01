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

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.ParcelUuid
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

const val PREF_LINK_BLE = "glint_link_ble"
const val PREF_LINK_WEBHOOK = "glint_link_webhook"
const val PREF_LINK_WEBHOOK_URL = "glint_link_webhook_url"
const val PREF_LINK_WEBHOOK_SECS = "glint_link_webhook_secs"
const val PREF_LINK_BROADCAST = "glint_link_broadcast"

/**
 * Shares the live heart rate with other things while you measure. Three outlets, each off
 * until you turn it on, and all quiet when you're not measuring:
 *
 *  - Bluetooth: the phone shows up as a standard Bluetooth heart-rate sensor (the same kind
 *    a chest strap is), so watches, bike computers, gym machines and fitness apps can pair.
 *  - Web address: posts a small JSON message to an https address you choose (Home Assistant,
 *    a Zapier/Make/IFTTT webhook, your own app), at most once every few seconds.
 *  - Automation apps: an Android broadcast that apps like Tasker or MacroDroid can react to.
 */
object HeartLink {
    private const val TAG = "HeartLink"

    /** Android broadcast action; extras: "bpm" (Int), "time" (Long, ms since 1970), "status" (String). */
    const val ACTION = "io.github.jbyjre.glint.HEART_RATE"

    const val MIN_WEBHOOK_SECS = 5
    const val DEFAULT_WEBHOOK_SECS = 10

    sealed interface Beacon {
        data object Off : Beacon
        /** On, but only runs while measuring. */
        data object Waiting : Beacon
        data class Advertising(val listeners: Int) : Beacon
        data class Failed(val reason: String) : Beacon
    }

    data class Status(
        val beacon: Beacon = Beacon.Off,
        /** When the web address last answered OK. */
        val webhookOkMs: Long = 0L,
        /** Why the last post failed, if it did. */
        val webhookError: String? = null,
        val broadcasts: Int = 0,
    )

    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status.asStateFlow()

    private var job: Job? = null
    private var appContext: Context? = null
    private var scope: CoroutineScope? = null
    private var lastPostMs = 0L
    private var lastSent: Long = 0L

    /** Starts following [HeartRate.state]; called once by the service. */
    fun attach(context: Context, scope: CoroutineScope) {
        appContext = context.applicationContext
        this.scope = scope
        job?.cancel()
        refresh()
        job = scope.launch {
            var wasMeasuring = false
            HeartRate.state.collect { st ->
                val measuring = st.status != HeartRate.Status.Off
                if (measuring != wasMeasuring) {
                    wasMeasuring = measuring
                    if (measuring) refresh() else stopped()
                }
                val bpm = st.bpm
                if (st.status == HeartRate.Status.Live && bpm != null && st.lastReadingMs != lastSent) {
                    lastSent = st.lastReadingMs
                    publish(bpm, st.lastReadingMs)
                }
            }
        }
    }

    fun detach() {
        job?.cancel()
        job = null
        Beaconer.stop()
        _status.update { it.copy(beacon = Beacon.Off) }
    }

    /** Re-reads the switches (after a change on the share screen). */
    fun refresh() {
        val ctx = appContext ?: return
        val prefs = prefs(ctx)
        val measuring = HeartRate.state.value.status != HeartRate.Status.Off
        when {
            !prefs.getBoolean(PREF_LINK_BLE, false) -> { Beaconer.stop(); _status.update { it.copy(beacon = Beacon.Off) } }
            !measuring -> { Beaconer.stop(); _status.update { it.copy(beacon = Beacon.Waiting) } }
            else -> Beaconer.start(ctx)
        }
    }

    private fun stopped() {
        val ctx = appContext ?: return
        val prefs = prefs(ctx)
        Beaconer.stop()
        _status.update { it.copy(beacon = if (prefs.getBoolean(PREF_LINK_BLE, false)) Beacon.Waiting else Beacon.Off) }
        val now = System.currentTimeMillis()
        if (prefs.getBoolean(PREF_LINK_BROADCAST, false)) broadcast(ctx, null, now, "stopped")
        if (prefs.getBoolean(PREF_LINK_WEBHOOK, false)) post(ctx, null, now, "stopped")
        lastPostMs = 0L
    }

    private fun publish(bpm: Int, time: Long) {
        val ctx = appContext ?: return
        val prefs = prefs(ctx)
        if (prefs.getBoolean(PREF_LINK_BLE, false)) Beaconer.send(bpm)
        if (prefs.getBoolean(PREF_LINK_BROADCAST, false)) broadcast(ctx, bpm, time, "live")
        if (prefs.getBoolean(PREF_LINK_WEBHOOK, false)) {
            val every = prefs.getInt(PREF_LINK_WEBHOOK_SECS, DEFAULT_WEBHOOK_SECS).coerceAtLeast(MIN_WEBHOOK_SECS)
            if (time - lastPostMs >= every * 1000L) {
                lastPostMs = time
                post(ctx, bpm, time, "live")
            }
        }
    }

    private fun broadcast(ctx: Context, bpm: Int?, time: Long, status: String) {
        ctx.sendBroadcast(Intent(ACTION).apply {
            if (bpm != null) putExtra("bpm", bpm)
            putExtra("time", time)
            putExtra("status", status)
        })
        _status.update { it.copy(broadcasts = it.broadcasts + 1) }
    }

    private fun post(ctx: Context, bpm: Int?, time: Long, status: String) {
        val url = prefs(ctx).getString(PREF_LINK_WEBHOOK_URL, "").orEmpty()
        if (!isValidUrl(url)) { _status.update { it.copy(webhookError = "Add an https:// address") }; return }
        val body = json(bpm, time, status)
        (scope ?: return).launch { send(url, body) }
    }

    /** Sends one test message now; returns null when it worked, else what went wrong. */
    suspend fun test(context: Context): String? {
        val url = prefs(context).getString(PREF_LINK_WEBHOOK_URL, "").orEmpty()
        if (!isValidUrl(url)) return "Add an https:// address first"
        return send(url, json(HeartRate.state.value.bpm, System.currentTimeMillis(), "test"))
    }

    private suspend fun send(url: String, body: String): String? = withContext(Dispatchers.IO) {
        val error = try {
            val conn = URL(url).openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "POST"
                conn.connectTimeout = 5_000
                conn.readTimeout = 5_000
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(body.toByteArray()) }
                val code = conn.responseCode
                if (code in 200..299) null else "The address answered $code"
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            Log.w(TAG, "post failed: ${e.message}")
            "Couldn't reach the address"
        }
        _status.update {
            if (error == null) it.copy(webhookOkMs = System.currentTimeMillis(), webhookError = null) else it.copy(webhookError = error)
        }
        error
    }

    /** Only https addresses: the message is your heart rate, so it shouldn't travel in the clear. */
    fun isValidUrl(url: String): Boolean = try {
        val u = URL(url.trim())
        u.protocol == "https" && !u.host.isNullOrEmpty()
    } catch (_: Exception) {
        false
    }

    /** The JSON body: {"bpm":72,"time":1767225600000,"status":"live"} ("bpm" is null when stopped). */
    fun json(bpm: Int?, time: Long, status: String) = "{\"bpm\":${bpm ?: "null"},\"time\":$time,\"status\":\"$status\"}"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)

    internal fun beaconState(b: Beacon) = _status.update { it.copy(beacon = b) }
}

/**
 * The standard Bluetooth Heart Rate Service (0x180D), as a chest strap offers it: Heart Rate
 * Measurement (0x2A37, notify) and Body Sensor Location (0x2A38, read: ear lobe, the closest
 * standard place to an ear).
 */
object HeartRateGatt {
    val SERVICE: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
    val MEASUREMENT: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
    val LOCATION: UUID = UUID.fromString("00002a38-0000-1000-8000-00805f9b34fb")
    val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    const val EAR_LOBE: Byte = 5

    /**
     * Heart Rate Measurement value. Flags: bit 0 = value is 16-bit, bits 1-2 = 3 means
     * "sensor contact supported and detected". Then the rate (8-bit, or 16-bit little-endian).
     */
    fun measurement(bpm: Int): ByteArray =
        if (bpm <= 255) byteArrayOf(0x06, bpm.toByte())
        else byteArrayOf(0x07, (bpm and 0xFF).toByte(), ((bpm shr 8) and 0xFF).toByte())
}

/** Runs the Bluetooth heart-rate sensor: a GATT server plus advertising. */
@SuppressLint("MissingPermission") // checked in [allowed] before anything runs
private object Beaconer {
    private var server: BluetoothGattServer? = null
    private var measurement: BluetoothGattCharacteristic? = null
    private val listeners = mutableSetOf<BluetoothDevice>()
    private var advertising: AdvertiseCallback? = null
    private var manager: BluetoothManager? = null

    private fun allowed(ctx: Context) = listOf(Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT)
        .all { ctx.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

    @Synchronized
    fun start(ctx: Context) {
        if (server != null) return
        if (!allowed(ctx)) { HeartLink.beaconState(HeartLink.Beacon.Failed("Allow \"Nearby devices\" for pro")); return }
        val bm = ctx.getSystemService(BluetoothManager::class.java) ?: return
        val adapter = bm.adapter
        if (adapter == null || !adapter.isEnabled) { HeartLink.beaconState(HeartLink.Beacon.Failed("Bluetooth is off")); return }
        val advertiser = adapter.bluetoothLeAdvertiser
        if (advertiser == null) { HeartLink.beaconState(HeartLink.Beacon.Failed("This phone can't act as a sensor")); return }
        manager = bm

        val hr = BluetoothGattCharacteristic(HeartRateGatt.MEASUREMENT, BluetoothGattCharacteristic.PROPERTY_NOTIFY, 0).apply {
            addDescriptor(BluetoothGattDescriptor(HeartRateGatt.CCCD, BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE))
        }
        val location = BluetoothGattCharacteristic(HeartRateGatt.LOCATION, BluetoothGattCharacteristic.PROPERTY_READ, BluetoothGattCharacteristic.PERMISSION_READ)
        val service = BluetoothGattService(HeartRateGatt.SERVICE, BluetoothGattService.SERVICE_TYPE_PRIMARY).apply {
            addCharacteristic(hr)
            addCharacteristic(location)
        }
        val gatt = bm.openGattServer(ctx, callback)
        if (gatt == null) { HeartLink.beaconState(HeartLink.Beacon.Failed("Bluetooth didn't start the sensor")); return }
        gatt.addService(service)
        server = gatt
        measurement = hr

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(true)
            .build()
        val data = AdvertiseData.Builder().addServiceUuid(ParcelUuid(HeartRateGatt.SERVICE)).build()
        val withName = AdvertiseData.Builder().setIncludeDeviceName(true).build()
        val cb = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                HeartLink.beaconState(HeartLink.Beacon.Advertising(listeners.size))
            }
            override fun onStartFailure(errorCode: Int) {
                if (errorCode == ADVERTISE_FAILED_DATA_TOO_LARGE) {
                    // A long phone name doesn't fit; advertise without it.
                    advertiser.startAdvertising(settings, data, this)
                    return
                }
                HeartLink.beaconState(HeartLink.Beacon.Failed("Bluetooth refused to advertise ($errorCode)"))
            }
        }
        advertising = cb
        advertiser.startAdvertising(settings, data, withName, cb)
    }

    @Synchronized
    fun stop() {
        val bm = manager ?: return
        try {
            advertising?.let { bm.adapter?.bluetoothLeAdvertiser?.stopAdvertising(it) }
            server?.let { s -> listeners.forEach { s.cancelConnection(it) }; s.close() }
        } catch (e: Exception) {
            Log.w("HeartLink", "stop: ${e.message}")
        }
        advertising = null
        server = null
        measurement = null
        listeners.clear()
        manager = null
    }

    @Synchronized
    fun send(bpm: Int) {
        val s = server ?: return
        val c = measurement ?: return
        val value = HeartRateGatt.measurement(bpm)
        listeners.toList().forEach { s.notifyCharacteristicChanged(it, c, false, value) }
    }

    private val callback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_DISCONNECTED) synchronized(this@Beaconer) {
                listeners.remove(device)
                if (server != null) HeartLink.beaconState(HeartLink.Beacon.Advertising(listeners.size))
            }
        }

        override fun onCharacteristicReadRequest(device: BluetoothDevice, requestId: Int, offset: Int, characteristic: BluetoothGattCharacteristic) {
            val value = if (characteristic.uuid == HeartRateGatt.LOCATION) byteArrayOf(HeartRateGatt.EAR_LOBE) else null
            server?.sendResponse(device, requestId, if (value != null) BluetoothGatt.GATT_SUCCESS else BluetoothGatt.GATT_READ_NOT_PERMITTED, 0, value)
        }

        override fun onDescriptorReadRequest(device: BluetoothDevice, requestId: Int, offset: Int, descriptor: BluetoothGattDescriptor) {
            val on = synchronized(this@Beaconer) { device in listeners }
            val value = if (on) BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE else BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
            server?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, value)
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice, requestId: Int, descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray?,
        ) {
            if (descriptor.uuid == HeartRateGatt.CCCD) synchronized(this@Beaconer) {
                // Bit 0 of the client configuration turns notifications on.
                if (value != null && value.isNotEmpty() && (value[0].toInt() and 1) == 1) listeners.add(device) else listeners.remove(device)
                if (server != null) HeartLink.beaconState(HeartLink.Beacon.Advertising(listeners.size))
            }
            if (responseNeeded) server?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
        }
    }
}
