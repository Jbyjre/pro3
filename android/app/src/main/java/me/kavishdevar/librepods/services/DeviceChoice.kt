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
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.kavishdevar.librepods.bluetooth.AirPodsDetection
import java.util.Locale

const val PREF_DEVICE_KIND = "glint_device_kind"
const val PREF_DEVICE_ADDRESS = "glint_device_address"
const val PREF_DEVICE_NAME = "glint_device_name"

/**
 * What kind of headphones pro is following. Each kind has its own controls, and only the
 * chosen device's controls ever run, so two devices can't step on each other.
 */
enum class DeviceKind(val label: String) {
    /** Apple's control channel: listening modes, ear detection, heart rate and the rest. */
    AIRPODS("AirPods"),

    /** Beats Solo 4: battery, music, volume and the islands. Its buttons work on their own. */
    BEATS_SOLO_4("Beats Solo 4"),

    /** Any other Bluetooth headphones or speaker: battery (when Android knows it), music, volume, islands. */
    HEADPHONES("Headphones");

    /** True for the kinds that open the AirPods control channel. Only AirPods do. */
    val usesAirPodsLink: Boolean get() = this == AIRPODS

    /**
     * The model code these headphones put in Apple's nearby beacon, or null when there is none.
     * Beats Solo 4 sends 0x2520 (from CAPod's verified device list, model A3140).
     */
    val beaconModel: Int? get() = if (this == BEATS_SOLO_4) BEATS_SOLO_4_MODEL else null

    companion object {
        const val BEATS_SOLO_4_MODEL = 0x2520

        fun fromName(name: String?): DeviceKind? = entries.firstOrNull { it.name == name }
    }
}

/** The device pro follows. [address] can be empty for AirPods that were never connected yet. */
data class ChosenDevice(val kind: DeviceKind, val address: String, val name: String) {
    val isAirPods: Boolean get() = kind.usesAirPodsLink
}

/** One of the phone's paired audio devices, as shown in the device list. */
data class KnownDevice(
    val address: String,
    val name: String,
    val kind: DeviceKind,
    val connected: Boolean,
)

/**
 * Which headphones pro follows. Chosen once in Settings > Devices (or the headphones button on
 * the main page) and kept until changed: nothing switches it automatically, not a different
 * device connecting, not a restart, not an update.
 *
 * Before anything was chosen (everyone updating from an older version) it is AirPods, so the
 * app behaves exactly as before.
 */
object DeviceChoice {
    private val _chosen = MutableStateFlow(ChosenDevice(DeviceKind.AIRPODS, "", "AirPods"))
    val chosen: StateFlow<ChosenDevice> = _chosen.asStateFlow()

    @Volatile private var loaded = false

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** Reads the saved choice once per process; cheap to call from anywhere. */
    fun load(context: Context): ChosenDevice {
        if (!loaded) {
            _chosen.value = read(prefs(context))
            loaded = true
        }
        return _chosen.value
    }

    /** The current choice (loading it first if this process hasn't yet). */
    fun current(context: Context): ChosenDevice = load(context)

    /** True when the AirPods' own controls may run: AirPods are the chosen device. */
    fun followsAirPods(context: Context): Boolean = load(context).isAirPods

    /** Saves [device] as the one to follow. Returns true when this changed the kind or the device. */
    fun choose(context: Context, device: ChosenDevice): Boolean {
        val before = load(context)
        write(prefs(context), device)
        _chosen.value = device
        return before.kind != device.kind || !before.address.equals(device.address, ignoreCase = true)
    }

    /** Tests and demo screens: forget the cached value so the next read comes from [prefs]. */
    internal fun reset() {
        loaded = false
        _chosen.value = ChosenDevice(DeviceKind.AIRPODS, "", "AirPods")
    }

    fun read(prefs: SharedPreferences): ChosenDevice {
        val kind = DeviceKind.fromName(prefs.getString(PREF_DEVICE_KIND, null))
        val savedAirPods = prefs.getString("mac_address", "").orEmpty()
        val airPodsName = prefs.getString("name", null)?.takeIf { it.isNotBlank() } ?: "AirPods"
        return when (kind) {
            null, DeviceKind.AIRPODS -> ChosenDevice(
                DeviceKind.AIRPODS,
                prefs.getString(PREF_DEVICE_ADDRESS, null)?.takeIf { it.isNotBlank() && kind != null } ?: savedAirPods,
                prefs.getString(PREF_DEVICE_NAME, null)?.takeIf { it.isNotBlank() && kind != null } ?: airPodsName,
            )
            else -> ChosenDevice(
                kind,
                prefs.getString(PREF_DEVICE_ADDRESS, "").orEmpty(),
                prefs.getString(PREF_DEVICE_NAME, null)?.takeIf { it.isNotBlank() } ?: kind.label,
            )
        }
    }

    fun write(prefs: SharedPreferences, device: ChosenDevice) = prefs.edit {
        putString(PREF_DEVICE_KIND, device.kind.name)
        putString(PREF_DEVICE_ADDRESS, device.address)
        putString(PREF_DEVICE_NAME, device.name)
        // The AirPods code reads its own saved address; keep it pointing at the chosen AirPods.
        if (device.kind == DeviceKind.AIRPODS && device.address.isNotBlank()) putString("mac_address", device.address)
    }

    /**
     * The phone's paired headphones and speakers (Bluetooth audio devices), with the chosen one
     * first, then connected ones, then by name. Empty without the Nearby devices permission.
     */
    @SuppressLint("MissingPermission")
    fun pairedAudioDevices(context: Context): List<KnownDevice> {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return emptyList()
        val bonded = try { adapter.bondedDevices.orEmpty() } catch (_: SecurityException) { return emptyList() }
        val chosen = current(context)
        return bonded.mapNotNull { d ->
            val name = try { d.name } catch (_: SecurityException) { null }?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val uuids = try { d.uuids?.map { it.uuid.toString() } } catch (_: SecurityException) { null }
            val major = try { d.bluetoothClass?.majorDeviceClass } catch (_: SecurityException) { null }
            val audio = major == BluetoothClass.Device.Major.AUDIO_VIDEO ||
                uuids?.any { it.equals(A2DP_SINK_UUID, ignoreCase = true) } == true
            val kind = kindOf(name, uuids)
            if (!audio && kind != DeviceKind.AIRPODS && kind != DeviceKind.BEATS_SOLO_4) return@mapNotNull null
            KnownDevice(d.address, name, kind, connected = isDeviceConnected(context, d))
        }.sortedWith(
            compareByDescending<KnownDevice> { it.address.equals(chosen.address, ignoreCase = true) && it.kind == chosen.kind }
                .thenByDescending { it.connected }
                .thenBy { it.name.lowercase(Locale.ROOT) }
        )
    }

    /** Android's audio outputs, then its own (hidden) per-device connection check as a fallback. */
    private fun isDeviceConnected(context: Context, device: BluetoothDevice): Boolean {
        if (HeadphoneLink.audioOutputHas(context, device.address)) return true
        return runCatching { BluetoothDevice::class.java.getMethod("isConnected").invoke(device) as? Boolean }.getOrNull() == true
    }

    /** The Bluetooth audio "sink" service: what headphones and speakers list. */
    private const val A2DP_SINK_UUID = "0000110b-0000-1000-8000-00805f9b34fb"

    /**
     * Sorts a paired device into a kind from what Android tells us about it. Beats are checked
     * first: some Beats list Apple's control service too, and must never get the AirPods
     * controls by accident.
     */
    fun kindOf(name: String?, uuids: Collection<String>?): DeviceKind {
        val n = name.orEmpty().lowercase(Locale.ROOT).replace('’', '\'')
        if (looksLikeSolo4(n)) return DeviceKind.BEATS_SOLO_4
        if (isBeats(name)) return DeviceKind.HEADPHONES
        if (AirPodsDetection.looksLikeAirPods(name)) return DeviceKind.AIRPODS
        if (uuids?.any { it.equals(AirPodsDetection.AAP_UUID, ignoreCase = true) } == true) return DeviceKind.AIRPODS
        return DeviceKind.HEADPHONES
    }

    /** Any Beats product by name ("Beats Solo 4", "Powerbeats Pro", "Beats Studio Buds"). */
    fun isBeats(name: String?): Boolean {
        val n = name.orEmpty().lowercase(Locale.ROOT)
        return "beats" in n || looksLikeSolo4(n)
    }

    /** "Beats Solo 4", "Jake's Solo 4", "Beats Solo4": the Solo 4 (not the Solo 3, Solo Pro or Solo Buds). */
    private fun looksLikeSolo4(n: String): Boolean =
        Regex("""\bsolo\s?4\b""").containsMatchIn(n)

    /**
     * What happens when Android reports [address] (named [name]) connecting, with [chosen] as
     * the followed device. Only the chosen device is ever acted on, so the AirPods controls never
     * reach Beats and the Beats path never touches AirPods.
     */
    enum class Route {
        /** Hand it to the AirPods control channel (AirPods are chosen and this looks like them). */
        AIRPODS,

        /** It's the chosen non-AirPods device: refresh its state. */
        HEADPHONES,

        /** Not the chosen device: leave it alone. */
        IGNORE,
    }

    fun route(chosen: ChosenDevice, address: String?, name: String?, uuids: Collection<String>?): Route {
        if (address.isNullOrBlank()) return Route.IGNORE
        return if (chosen.isAirPods) {
            // Beats never get the AirPods channel, even when they list Apple's service. Anything
            // else goes to the old detection, which also catches renamed AirPods ("Jake's Pods").
            val isSaved = chosen.address.isNotBlank() && address.equals(chosen.address, ignoreCase = true)
            if (isBeats(name) && !isSaved) Route.IGNORE else Route.AIRPODS
        } else {
            if (address.equals(chosen.address, ignoreCase = true)) Route.HEADPHONES else Route.IGNORE
        }
    }
}
