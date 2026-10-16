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

package me.kavishdevar.librepods.presentation.glint

import android.app.Activity
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.os.PowerManager
import android.view.Display
import android.view.Window
import android.view.WindowManager

/**
 * Smoother motion: asks for the screen's fastest refresh rate (90 or 120 Hz on most phones
 * that have it) while you touch pro and for a moment after ([Booster]), and while a pop-up
 * island is on screen. Many phones run apps and overlays at 60 Hz unless asked, which makes
 * springs and glass look steppy.
 *
 * When nothing has been touched for [Booster.LINGER_MS], pro hands the rate back to the phone:
 * the Galaxy S26 FE's adaptive screen goes back to its own choice (60 to 120 Hz) and the Pixel
 * 6 Pro's LTPO screen can drop as low as 10 Hz while the page is still. Before 2026-10-15 the
 * fast rate was held the whole time pro was open, even while you just read a page.
 *
 * Not on Battery Saver or a warm phone (the same rule that lightens the glass), and never for
 * the Dynamic Island pill itself: it stays on screen for hours, and a fast screen all day
 * would cost real battery.
 */
object FrameRate {
    /** The fastest mode at the display's current resolution, or null when there's only one rate. */
    fun fastestMode(display: Display?): Display.Mode? {
        display ?: return null
        val now = display.mode
        return pick(now, display.supportedModes.toList())
    }

    /** The fastest of [modes] with [now]'s resolution, when that is faster than 60 Hz. */
    fun pick(now: Display.Mode, modes: List<Display.Mode>): Display.Mode? =
        pickBy(now.physicalWidth, now.physicalHeight, modes.map { Triple(it, it.physicalWidth to it.physicalHeight, it.refreshRate) })

    /** Pure, for tests: same as [pick] over (item, width to height, rate) triples. */
    internal fun <T> pickBy(width: Int, height: Int, modes: List<Triple<T, Pair<Int, Int>, Float>>): T? {
        // Same resolution only: switching resolution would make the screen blink.
        val best = modes.filter { it.second.first == width && it.second.second == height }.maxByOrNull { it.third } ?: return null
        return best.first.takeIf { best.third > 61f }
    }

    /** True when it's fine to ask for the faster rate right now. */
    fun allowed(context: Context): Boolean {
        val pm = context.getSystemService(PowerManager::class.java) ?: return true
        return !GlassBudget.shouldLighten(pm.isPowerSaveMode, pm.currentThermalStatus, me.kavishdevar.librepods.services.PhoneProfile.warmFrom)
    }

    /** For pro's own window: prefer the fastest mode, or hand the choice back to the phone. */
    fun apply(window: Window, context: Context) {
        val mode = if (allowed(context)) fastestMode(context.display) else null
        set(window, mode?.modeId ?: 0)
    }

    /** Hands the rate back to the phone (its own adaptive or LTPO choice). */
    fun release(window: Window) = set(window, 0)

    private fun set(window: Window, id: Int) {
        val lp = window.attributes
        if (lp.preferredDisplayModeId == id) return
        lp.preferredDisplayModeId = id
        window.attributes = lp
    }

    /**
     * The fast rate for one activity's window: on at a touch (and when the page comes back), off
     * [LINGER_MS] after the last finger lifts, long enough for a fling or a spring to settle.
     */
    class Booster(private val activity: Activity) {
        private val handler = Handler(Looper.getMainLooper())
        private val relax = Runnable { release(activity.window) }

        /** Call from dispatchTouchEvent. Cheap: only the first and last events of a touch do anything. */
        fun onTouch(ev: MotionEvent) {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> boost(hold = true)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> lingerThenRelax()
            }
        }

        /** Fast now; with [hold] it stays until the touch ends, else it eases off by itself. */
        fun boost(hold: Boolean = false) {
            handler.removeCallbacks(relax)
            apply(activity.window, activity)
            if (!hold) lingerThenRelax()
        }

        private fun lingerThenRelax() {
            handler.removeCallbacks(relax)
            handler.postDelayed(relax, LINGER_MS)
        }

        /** The page went away: hand the rate back now and forget pending work. */
        fun stop() {
            handler.removeCallbacks(relax)
            release(activity.window)
        }

        companion object {
            /** How long the fast rate stays after the last touch: a fling or a page's spring is done by then. */
            const val LINGER_MS = 2_500L
        }
    }

    /** For a pop-up overlay's layout params (set before the window is added). */
    fun applyTo(lp: WindowManager.LayoutParams, context: Context) {
        if (!allowed(context)) return
        val display = context.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)
        val mode = fastestMode(display) ?: return
        lp.preferredRefreshRate = mode.refreshRate
    }
}
