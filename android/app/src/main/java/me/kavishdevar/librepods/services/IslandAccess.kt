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
import android.app.KeyguardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Makes the Dynamic Island tappable.
 *
 * Android puts its status bar (the strip with the clock and icons) above every app's floating
 * window and hands it every touch in that strip, so a pill drawn around the front camera as a
 * normal app overlay can be seen but never touched. Windows placed by an accessibility service
 * sit above the status bar, so while this service is on the Dynamic Island lives there and gets
 * its taps (and can show on the lock screen).
 *
 * It only looks at where the phone's own panels are (so the island steps aside while you pull
 * down notifications) and at which app's window came to the front (so a sound can be matched to
 * its app); it never reads what's on the screen.
 */
class IslandAccessService : AccessibilityService() {
    private val main = Handler(Looper.getMainLooper())
    private val check = Runnable { IslandAccess.updatePanels(this) }

    override fun onServiceConnected() {
        super.onServiceConnected()
        IslandAccess.connected(this)
        IslandAccess.updatePanels(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Which app just came to the front (the app's name only, nothing on the screen), so a
        // sound can be matched to the app you're in.
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) SoundSource.windowChanged(event.packageName)
        // Windows come and go in bursts (an animation can send dozens): look once things settle.
        main.removeCallbacks(check)
        main.postDelayed(check, PANEL_CHECK_MS)
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        main.removeCallbacks(check)
        IslandAccess.disconnected(this)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        main.removeCallbacks(check)
        IslandAccess.disconnected(this)
        super.onDestroy()
    }

    private companion object {
        const val PANEL_CHECK_MS = 60L
    }
}

/** The app's side of [IslandAccessService]: whether it's running and what it sees. */
object IslandAccess {
    private const val TAG = "IslandAccess"

    private val _service = MutableStateFlow<AccessibilityService?>(null)
    /** The running service (its window manager places windows above the status bar), or null. */
    val service: StateFlow<AccessibilityService?> = _service.asStateFlow()

    private val _panelOpen = MutableStateFlow(false)
    /** True while the notification shade, quick settings or a system dialog covers the top. */
    val panelOpen: StateFlow<Boolean> = _panelOpen.asStateFlow()

    internal fun connected(s: AccessibilityService) {
        Log.d(TAG, "connected")
        _service.value = s
    }

    internal fun disconnected(s: AccessibilityService) {
        if (_service.value !== s) return
        Log.d(TAG, "disconnected")
        _service.value = null
        _panelOpen.value = false
    }

    internal fun updatePanels(s: AccessibilityService) {
        if (_service.value !== s) return
        val wins = runCatching { s.windows }.getOrNull().orEmpty()
        val r = Rect()
        val list = wins.map { w ->
            w.getBoundsInScreen(r)
            PanelRules.Win(system = w.type == AccessibilityWindowInfo.TYPE_SYSTEM, left = r.left, top = r.top, right = r.right, bottom = r.bottom)
        }
        val bounds = runCatching { s.getSystemService(android.view.WindowManager::class.java).currentWindowMetrics.bounds }.getOrNull()
        val locked = s.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true
        val open = bounds != null && PanelRules.covered(list, bounds.width(), bounds.height(), locked)
        if (open != _panelOpen.value) {
            Log.d(TAG, "panel open: $open")
            _panelOpen.value = open
        }
    }

    fun component(context: Context) = ComponentName(context, IslandAccessService::class.java)

    /** Switched on in Android's Accessibility settings (it may take a moment to start running). */
    fun isEnabled(context: Context): Boolean {
        val list = runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        }.getOrNull() ?: return false
        val me = component(context)
        return list.split(':').any { ComponentName.unflattenFromString(it.trim()) == me }
    }

    val isRunning: Boolean get() = _service.value != null

    /** This build includes the service (the Play build doesn't). */
    fun isAvailable(context: Context): Boolean =
        runCatching { context.packageManager.getServiceInfo(component(context), 0) }.isSuccess

    /**
     * Android's Accessibility page, with pro's row highlighted where the phone supports it.
     * (The page for one service is reserved for system apps, so this is the closest.)
     */
    fun settingsIntent(context: Context): Intent {
        val name = component(context).flattenToString()
        return Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .putExtra(":settings:fragment_args_key", name)
            .putExtra(":settings:show_fragment_args", Bundle().apply { putString(":settings:fragment_args_key", name) })
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** Opens the notification shade (pulling down on the Dynamic Island still does what you'd expect). */
    fun openNotifications(): Boolean =
        _service.value?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS) == true
}

/** Pure rules for [IslandAccess.panelOpen], so they're unit-tested without a phone. */
object PanelRules {
    /** A window on screen: [system] for the phone's own (status bar, shade, dialogs). */
    data class Win(val system: Boolean, val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val width get() = right - left
        val height get() = bottom - top
    }

    /**
     * Something of the phone's own covers the top of the screen: the notification shade or quick
     * settings (being pulled or open), a heads-up notification's host, the power menu. Those are
     * tall, wide system windows reaching the top; the status bar and navigation bar are thin, the
     * volume panel and edge handles narrow. On the lock screen the lock screen itself is such a
     * window, so there nothing counts (the island stays, as asked).
     */
    fun covered(windows: List<Win>, screenW: Int, screenH: Int, locked: Boolean): Boolean {
        if (locked || screenW <= 0 || screenH <= 0) return false
        return windows.any { w ->
            w.system && w.top <= screenH / 10 && w.height >= screenH / 2 && w.width >= screenW * 7 / 10
        }
    }
}
