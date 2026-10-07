/*
    pro, a fork of LibrePods - AirPods liberated from Apple's ecosystem
    Copyright (C) 2026 pro contributors

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

import android.accessibilityservice.AccessibilityService
import android.app.AlarmManager
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The phone controls on the opened Dynamic Island: the torch, sound or vibrate, a screenshot and
 * locking the screen, plus two things worth a glance (the next alarm, and how long until the
 * battery is full). Each says honestly when it can't work:
 * - the torch needs no permission (Android lets any app switch it), but not while the camera is in use;
 * - sound/vibrate switches only between those two (silencing the phone fully is Do Not Disturb,
 *   which needs a separate permission, so it's left to Android);
 * - the screenshot and the lock need the "pro Dynamic Island" accessibility switch.
 */
object PhoneControls {
    private const val TAG = "PhoneControls"
    private val main = Handler(Looper.getMainLooper())

    private val _torch = MutableStateFlow(false)
    /** The torch is on (whoever switched it on). */
    val torch: StateFlow<Boolean> = _torch.asStateFlow()
    private var torchId: String? = null
    private var torchWatching = false

    /** The back camera's flash, if the phone has one. */
    private fun torchCamera(c: Context): String? {
        torchId?.let { return it }
        val cm = c.getSystemService(CameraManager::class.java) ?: return null
        torchId = runCatching {
            cm.cameraIdList.firstOrNull { id ->
                val ch = cm.getCameraCharacteristics(id)
                ch.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                    ch.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            } ?: cm.cameraIdList.firstOrNull { cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
        }.getOrNull()
        return torchId
    }

    fun hasTorch(c: Context): Boolean = torchCamera(c) != null

    /** Starts following the torch (the Quick Settings tile may switch it too). Safe to call often. */
    fun watchTorch(c: Context) {
        if (torchWatching) return
        val cm = c.applicationContext.getSystemService(CameraManager::class.java) ?: return
        val id = torchCamera(c) ?: return
        torchWatching = true
        cm.registerTorchCallback(object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(cameraId: String, enabled: Boolean) { if (cameraId == id) _torch.value = enabled }
            override fun onTorchModeUnavailable(cameraId: String) { if (cameraId == id) _torch.value = false }
        }, main)
    }

    /** Switches the torch; false when it can't (no flash, or the camera is busy). */
    fun toggleTorch(c: Context): Boolean {
        val cm = c.getSystemService(CameraManager::class.java) ?: return false
        val id = torchCamera(c) ?: return false
        watchTorch(c)
        return runCatching { cm.setTorchMode(id, !_torch.value); _torch.value = !_torch.value; true }
            .onFailure { Log.w(TAG, "Torch unavailable", it) }.getOrDefault(false)
    }

    /** What the ringer does now. */
    enum class Ring(val label: String) { Sound("Sound"), Vibrate("Vibrate"), Silent("Silent") }

    fun ring(c: Context): Ring = when (c.getSystemService(AudioManager::class.java)?.ringerMode) {
        AudioManager.RINGER_MODE_VIBRATE -> Ring.Vibrate
        AudioManager.RINGER_MODE_SILENT -> Ring.Silent
        else -> Ring.Sound
    }

    /** Sound to vibrate and back (silent goes back to sound). Returns the new mode, or null when refused. */
    fun toggleRing(c: Context): Ring? {
        val am = c.getSystemService(AudioManager::class.java) ?: return null
        val to = PhoneRules.nextRing(ring(c))
        return runCatching {
            am.ringerMode = if (to == Ring.Vibrate) AudioManager.RINGER_MODE_VIBRATE else AudioManager.RINGER_MODE_NORMAL
            ring(c)
        }.onFailure { Log.w(TAG, "Ringer change refused", it) }.getOrNull()
    }

    /** Screenshots and locking work only through the accessibility switch. */
    fun canUseSystemActions(): Boolean = IslandAccess.isRunning

    /**
     * Takes a screenshot, [afterMs] from now: the opened island tucks away first and the Dynamic
     * Island steps out of the picture for the moment of the capture, then comes back.
     */
    fun screenshot(afterMs: Long = 650L): Boolean {
        if (!canUseSystemActions()) return false
        val overlays = me.kavishdevar.librepods.presentation.overlays.GlintOverlays
        overlays.capturing.value = true
        main.postDelayed({ IslandAccess.service.value?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT) }, afterMs)
        main.postDelayed({ overlays.capturing.value = false }, afterMs + 900L)
        return true
    }

    fun lockScreen(afterMs: Long = 250L): Boolean {
        if (!canUseSystemActions()) return false
        main.postDelayed({ IslandAccess.service.value?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN) }, afterMs)
        return true
    }

    /** When the next alarm set in the Clock app rings (wall-clock ms), or null. Needs no permission. */
    fun nextAlarm(c: Context): Long? = runCatching {
        c.getSystemService(AlarmManager::class.java)?.nextAlarmClock?.triggerTime
    }.getOrNull()

    /** How long until the battery is full while charging, in ms, or null when Android can't say. */
    fun timeToFull(c: Context): Long? = runCatching {
        c.getSystemService(BatteryManager::class.java)?.computeChargeTimeRemaining()?.takeIf { it > 0L }
    }.getOrNull()
}

/** Pure helpers for [PhoneControls] and the opened island, unit-tested. */
object PhoneRules {
    fun nextRing(now: PhoneControls.Ring): PhoneControls.Ring =
        if (now == PhoneControls.Ring.Sound) PhoneControls.Ring.Vibrate else PhoneControls.Ring.Sound

    /** "in 45 min", "in 3 h 05 min", "in 1 h": how far away something is, in plain words. */
    fun inWords(ms: Long): String {
        val min = ((ms + 59_999L) / 60_000L).coerceAtLeast(0L)
        if (min < 1) return "now"
        if (min < 60) return "in $min min"
        val h = min / 60; val m = min % 60
        return if (m == 0L) "in $h h" else "in $h h %02d min".format(java.util.Locale.ROOT, m)
    }

    /** "45 min", "1 h 05 min": a length without "in". */
    fun length(ms: Long): String = inWords(ms).removePrefix("in ")
}
