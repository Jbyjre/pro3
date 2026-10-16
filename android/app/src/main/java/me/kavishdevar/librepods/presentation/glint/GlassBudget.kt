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
package me.kavishdevar.librepods.presentation.glint

import android.os.PowerManager
import androidx.compose.runtime.mutableStateOf

/**
 * Glass that knows when to go easy. Real blur and refraction are GPU work; when the phone is
 * saving battery or getting hot, full glass is what makes scrolling stutter (and makes the
 * phone hotter). Then the glass switches to a lighter recipe: less blur, no colour fringe,
 * a bit more tint so text stays easy to read. It looks nearly the same and switches back by
 * itself when the phone recovers.
 */
object GlassBudget {
    /** True while the glass should use the lighter recipe. Read inside draw code only. */
    val light = mutableStateOf(false)

    /**
     * Battery Saver, or the phone is warm: at least [warmFrom] (moderately hot on most phones,
     * from the first level on the Pixel 6 Pro: see [me.kavishdevar.librepods.services.PhoneProfile]).
     */
    fun shouldLighten(powerSave: Boolean, thermalStatus: Int, warmFrom: Int = PowerManager.THERMAL_STATUS_MODERATE): Boolean =
        powerSave || thermalStatus >= warmFrom

    /**
     * Kept current by [me.kavishdevar.librepods.services.PowerPlan], which follows Battery Saver
     * and the phone's heat for the app's whole life (in the app and in the overlays).
     */
    internal fun set(powerSave: Boolean, thermalStatus: Int) {
        val next = shouldLighten(powerSave, thermalStatus, me.kavishdevar.librepods.services.PhoneProfile.warmFrom)
        if (light.value != next) light.value = next
    }
}
