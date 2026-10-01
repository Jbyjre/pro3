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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Keep media volume at or below a level you pick while it plays through your AirPods. */
const val PREF_VOLUME_LIMIT_ON = "glint_volume_limit_on"
/** The limit, as a percentage of the phone's maximum media volume. */
const val PREF_VOLUME_LIMIT = "glint_volume_limit"

/**
 * Volume limit, a hearing-protection feature that works on any phone (no root): while media
 * plays through the saved AirPods, Glint brings the media volume back down to your limit
 * whenever something raises it above. It watches Android's volume-changed broadcast and also
 * checks when the AirPods connect and when the limit changes.
 *
 * The limit is a share of the phone's volume steps, not a decibel level: Glint can't measure
 * how loud the sound in your ears actually is.
 */
object VolumeGuard {
    private const val TAG = "VolumeGuard"
    /** Android's (long-standing but undocumented) broadcast when a stream's volume changes. */
    private const val VOLUME_CHANGED = "android.media.VOLUME_CHANGED_ACTION"
    const val DEFAULT_LIMIT = 70

    data class Event(val atMs: Long, val limitedTo: Int)

    private val _lastLimited = MutableStateFlow<Event?>(null)
    /** The last time Glint turned the volume down, for the screen to show. */
    val lastLimited: StateFlow<Event?> = _lastLimited.asStateFlow()

    private var receiver: BroadcastReceiver? = null
    private var appContext: Context? = null

    fun attach(context: Context) {
        if (receiver != null) return
        appContext = context.applicationContext
        receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                if (intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", AudioManager.STREAM_MUSIC) == AudioManager.STREAM_MUSIC) check()
            }
        }
        try {
            context.registerReceiver(receiver, IntentFilter(VOLUME_CHANGED), Context.RECEIVER_EXPORTED)
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't watch volume changes", e)
            receiver = null
        }
    }

    fun detach(context: Context) {
        receiver?.let { runCatching { context.unregisterReceiver(it) } }
        receiver = null
    }

    /** The highest allowed volume step for [max] steps and a [percent] limit (at least 1). */
    fun capSteps(max: Int, percent: Int): Int = (max * percent.coerceIn(10, 100) / 100.0).toInt().coerceIn(1, max)

    /** Turns media down to the limit if it's above it and playing through the saved AirPods. */
    fun check() {
        val context = appContext ?: return
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        if (!prefs.getBoolean(PREF_VOLUME_LIMIT_ON, false)) return
        val am = context.getSystemService(AudioManager::class.java) ?: return
        if (!mediaGoesToAirPods(am, prefs.getString("mac_address", "").orEmpty())) return
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val cap = capSteps(max, prefs.getInt(PREF_VOLUME_LIMIT, DEFAULT_LIMIT))
        val now = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        if (now > cap) {
            try {
                am.setStreamVolume(AudioManager.STREAM_MUSIC, cap, 0)
                _lastLimited.value = Event(System.currentTimeMillis(), prefs.getInt(PREF_VOLUME_LIMIT, DEFAULT_LIMIT))
            } catch (e: SecurityException) {
                // Do Not Disturb access can block volume changes on some phones.
                Log.w(TAG, "Couldn't lower the volume", e)
            }
        }
    }

    /** True when media currently plays through a Bluetooth headset with the AirPods' address. */
    private fun mediaGoesToAirPods(am: AudioManager, mac: String): Boolean {
        val media = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build()
        val devices = runCatching { am.getAudioDevicesForAttributes(media) }.getOrNull().orEmpty()
        return devices.any { d ->
            (d.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || d.type == AudioDeviceInfo.TYPE_BLE_HEADSET) &&
                (mac.isEmpty() || d.address.equals(mac, ignoreCase = true))
        }
    }
}
