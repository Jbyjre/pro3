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

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.PowerManager
import android.view.Display
import android.view.Window
import android.view.WindowManager

/**
 * Smoother motion: asks for the screen's fastest refresh rate (90 or 120 Hz on most phones
 * that have it) while pro is open and while a pop-up island is on screen. Many phones run
 * apps and overlays at 60 Hz unless asked, which makes springs and glass look steppy.
 *
 * Not on Battery Saver or a hot phone (the same rule that lightens the glass), and never for
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
        return !GlassBudget.shouldLighten(pm.isPowerSaveMode, pm.currentThermalStatus)
    }

    /** For pro's own window: prefer the fastest mode, or hand the choice back to the phone. */
    fun apply(window: Window, context: Context) {
        val mode = if (allowed(context)) fastestMode(context.display) else null
        val lp = window.attributes
        val id = mode?.modeId ?: 0
        if (lp.preferredDisplayModeId == id) return
        lp.preferredDisplayModeId = id
        window.attributes = lp
    }

    /** For a pop-up overlay's layout params (set before the window is added). */
    fun applyTo(lp: WindowManager.LayoutParams, context: Context) {
        if (!allowed(context)) return
        val display = context.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)
        val mode = fastestMode(display) ?: return
        lp.preferredRefreshRate = mode.refreshRate
    }
}
