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

import android.app.KeyguardManager
import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Where you are on the phone right now, for the Dynamic Island: in an app (which one, with its
 * icon), on the home screen, or on the lock screen. The island shows that app's icon beside the
 * camera, a home glyph on the home screen and a padlock while locked.
 *
 * Which app is in front comes from the "pro Dynamic Island" accessibility switch (Android tells
 * no other kind of app). Only the app's name is used, never anything on the screen. Without the
 * switch only "locked" is known (that needs nothing), and the island falls back to its plain look.
 * The rules are pure ([ScreenRules]), so they're unit-tested without a phone.
 */
object ScreenApp {
    private const val TAG = "ScreenApp"

    @Immutable
    sealed interface Place {
        /** An app is in front. [pkg] is its package; [label] and [icon] as the launcher shows them. */
        data class App(val pkg: String, val label: String?, val icon: ImageBitmap?) : Place
        data object Home : Place
        /** The lock screen. [opening] for a moment right after unlocking (the padlock springs open). */
        data class Locked(val opening: Boolean = false) : Place
        /** Not known: the accessibility switch is off, or nothing has come to the front yet. */
        data object Unknown : Place
    }

    private val _place = MutableStateFlow<Place>(Place.Unknown)
    val place: StateFlow<Place> = _place.asStateFlow()

    private val _recent = MutableStateFlow<List<String>>(emptyList())
    /** Apps that were in front lately, newest first (for Settings > Island > Hide in these apps). */
    val recent: StateFlow<List<String>> = _recent.asStateFlow()

    private val _wallpaper = MutableStateFlow<List<Color>>(emptyList())
    /** The home screen wallpaper's main colours (up to three), for the home glyph. Empty until read. */
    val wallpaper: StateFlow<List<Color>> = _wallpaper.asStateFlow()

    /** Where you were before the lock screen came up (unlocking goes back there). */
    private var beforeLock: Place = Place.Unknown
    private var appContext: Context? = null
    private val main = Handler(Looper.getMainLooper())
    private val activityCache = HashMap<String, Boolean>()

    /** Starts following the screen (safe to call often; the background service does it at start). */
    fun attach(context: Context) {
        if (appContext != null) return
        val app = context.applicationContext
        appContext = app
        ContextCompat.registerReceiver(
            app, object : BroadcastReceiver() {
                override fun onReceive(c: Context, i: Intent) = onScreen(i.action)
            },
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        if (isLocked(app)) lock()
        readWallpaper(app)
        runCatching {
            WallpaperManager.getInstance(app).addOnColorsChangedListener({ _, which ->
                if (which and WallpaperManager.FLAG_SYSTEM != 0) readWallpaper(app)
            }, main)
        }.onFailure { Log.w(TAG, "No wallpaper colour updates", it) }
    }

    private fun isLocked(c: Context): Boolean = c.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true

    private fun onScreen(action: String?) {
        val c = appContext ?: return
        when (action) {
            // The screen turned off or on still locked: the lock screen is what's in front.
            Intent.ACTION_SCREEN_OFF -> lock()
            Intent.ACTION_SCREEN_ON -> if (isLocked(c)) lock() else unlock()
            Intent.ACTION_USER_PRESENT -> unlock()
        }
    }

    private fun lock() {
        main.removeCallbacks(settle)
        val cur = _place.value
        if (cur !is Place.Locked) beforeLock = cur
        _place.value = Place.Locked()
    }

    /** Unlocked: the padlock springs open for a moment, then you're back where you were. */
    private fun unlock() {
        if (_place.value !is Place.Locked) return
        _place.value = Place.Locked(opening = true)
        main.removeCallbacks(settle)
        main.postDelayed(settle, ScreenRules.UNLOCK_SHOW_MS)
    }

    private val settle = Runnable {
        if (_place.value is Place.Locked) _place.value = beforeLock
    }

    /**
     * A window came to the front (from [IslandAccessService]): [pkg] made it, [className] is its
     * class. Only real app screens (activities) count, so a pop-up, a toast or a chat bubble from
     * another app doesn't change the icon; the home screen clears it; the phone's own pieces
     * (notification shade, keyboard) are ignored.
     */
    fun windowChanged(pkg: CharSequence?, className: CharSequence?) {
        val c = appContext ?: return
        val p = pkg?.toString()?.takeIf { it.isNotBlank() } ?: return
        val cls = className?.toString()
        val own = p == c.packageName
        val role = if (own) SoundRules.Role.App else SoundSource.roleOf(c, p)
        val next = ScreenRules.next(
            current = _place.value,
            pkg = p,
            role = role,
            isScreen = isActivity(c, p, cls),
            locked = isLocked(c),
        )
        when (next) {
            is ScreenRules.Next.Stay -> return
            is ScreenRules.Next.Home -> set(Place.Home)
            is ScreenRules.Next.App -> {
                if ((_place.value as? Place.App)?.pkg == next.pkg) return
                val info = SoundSource.appInfo(c, next.pkg)
                set(Place.App(next.pkg, info.label, info.icon))
                _recent.value = ScreenRules.remember(_recent.value, next.pkg, own = c.packageName)
            }
        }
    }

    private fun set(p: Place) {
        if (_place.value is Place.Locked) { beforeLock = p; return }
        _place.value = p
    }

    /** The accessibility switch went off: which app is in front is no longer known. */
    fun lostSight() {
        if (_place.value !is Place.Locked) _place.value = Place.Unknown
        beforeLock = Place.Unknown
    }

    /** Whether [cls] in [pkg] is one of its screens (an activity), as opposed to a pop-up or a view. Cached. */
    private fun isActivity(c: Context, pkg: String, cls: String?): Boolean {
        if (cls.isNullOrBlank()) return false
        val key = "$pkg/$cls"
        return activityCache.getOrPut(key) {
            runCatching { c.packageManager.getActivityInfo(ComponentName(pkg, cls), 0); true }.getOrDefault(false)
        }
    }

    private fun readWallpaper(c: Context) {
        // Android works the colours out over a slow system call: never on the main thread.
        Thread {
            val colors = runCatching {
                WallpaperManager.getInstance(c).getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
            }.getOrNull()
            val list = listOfNotNull(colors?.primaryColor, colors?.secondaryColor, colors?.tertiaryColor)
                .map { Color(it.toArgb()) }
            main.post { _wallpaper.value = list }
        }.start()
    }

    /** True while the screen is on (the island has nothing to show on a dark screen). */
    fun screenOn(c: Context): Boolean = c.getSystemService(PowerManager::class.java)?.isInteractive ?: true

    // ---- Screenshots and tests ----

    internal fun preview(p: Place, wallpaper: List<Color> = _wallpaper.value) {
        _place.value = p
        _wallpaper.value = wallpaper
    }

    internal fun resetForTest() {
        main.removeCallbacks(settle)
        appContext = null
        _place.value = Place.Unknown
        _recent.value = emptyList()
        _wallpaper.value = emptyList()
        beforeLock = Place.Unknown
        activityCache.clear()
    }

    /** Tests only: what counts as an activity, without real apps installed. */
    internal fun setActivityForTest(pkg: String, cls: String, isActivity: Boolean) {
        activityCache["$pkg/$cls"] = isActivity
    }

    internal fun attachForTest(context: Context) { appContext = context.applicationContext }
}

/** The pure rules behind [ScreenApp]. */
object ScreenRules {
    /** How long the padlock shows open after unlocking, before the island moves on. */
    const val UNLOCK_SHOW_MS = 900L
    /** How many apps "Hide in these apps" offers from what was used lately. */
    const val RECENT_MAX = 12

    sealed interface Next {
        data object Stay : Next
        data object Home : Next
        data class App(val pkg: String) : Next
    }

    /**
     * What a window coming to the front means. The phone's own pieces never change anything; a
     * launcher window means the home screen; an app counts only when it's one of its screens
     * ([isScreen]), not a pop-up, a toast or a bubble drawn over another app.
     */
    fun next(current: ScreenApp.Place, pkg: String, role: SoundRules.Role, isScreen: Boolean, locked: Boolean): Next = when {
        role == SoundRules.Role.System -> Next.Stay
        role == SoundRules.Role.Launcher -> if (locked) Next.Stay else Next.Home
        !isScreen -> Next.Stay
        (current as? ScreenApp.Place.App)?.pkg == pkg -> Next.Stay
        else -> Next.App(pkg)
    }

    /** Adds [pkg] to the front of the recent list (once, at most [RECENT_MAX]); pro itself isn't offered. */
    fun remember(list: List<String>, pkg: String, own: String): List<String> =
        if (pkg == own) list else (listOf(pkg) + list.filter { it != pkg }).take(RECENT_MAX)
}
