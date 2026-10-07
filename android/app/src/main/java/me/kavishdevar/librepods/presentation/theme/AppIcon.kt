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

package me.kavishdevar.librepods.presentation.theme

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log

/**
 * The home-screen icon. Each choice is an activity alias in the manifest that opens the same
 * screen; switching enables the chosen alias first, then disables the others, so there's
 * always exactly one launcher entry.
 */
enum class AppIcon(val alias: String, val label: String) {
    Black("IconBlack", "Black"),
    White("IconWhite", "White"),
    Graphite("IconGraphite", "Graphite");

    private fun component(context: Context) =
        ComponentName(context.packageName, "me.kavishdevar.librepods.$alias")

    companion object {
        fun current(context: Context): AppIcon {
            val pm = context.packageManager
            return entries.firstOrNull {
                when (pm.getComponentEnabledSetting(it.component(context))) {
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                    // Not changed yet: the manifest default decides (only Black starts enabled).
                    PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> it == Black
                    else -> false
                }
            } ?: Black
        }

        /** Returns false if Android refused the change (the old icon then stays). */
        fun select(context: Context, icon: AppIcon): Boolean = try {
            val pm = context.packageManager
            pm.setComponentEnabledSetting(icon.component(context), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
            entries.filter { it != icon }.forEach {
                pm.setComponentEnabledSetting(it.component(context), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
            }
            // The Dynamic Island shows pro's real icon while pro is open: the new one from now on.
            me.kavishdevar.librepods.services.ScreenApp.iconChanged(context.applicationContext)
            true
        } catch (e: Exception) {
            Log.w("AppIcon", "Couldn't switch the icon: ${e.message}")
            false
        }
    }
}
