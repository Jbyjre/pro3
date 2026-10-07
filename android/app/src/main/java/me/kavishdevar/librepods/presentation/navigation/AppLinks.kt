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
package me.kavishdevar.librepods.presentation.navigation

import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow

/** Opening pro on a particular page (for example from a pop-up that says "turn this on"). */
object AppLinks {
    const val EXTRA = "glint_open"
    /** Settings > Islands. */
    const val ISLANDS = "islands"
    /** The heart-rate page. */
    const val HEART = "heart"
    /** Troubleshooting (freeze and crash reports). */
    const val TROUBLESHOOTING = "troubleshooting"

    /** A page asked for and not yet opened. */
    val pending = MutableStateFlow<String?>(null)

    fun handle(intent: Intent?) {
        intent?.getStringExtra(EXTRA)?.let { pending.value = it }
        intent?.removeExtra(EXTRA)
    }
}
