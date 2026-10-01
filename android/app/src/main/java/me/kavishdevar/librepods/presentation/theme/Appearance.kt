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

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.content.edit

/**
 * Light, dark, or follow the phone, for the whole app and the island/card pop-ups.
 * The app's screens and pop-ups all ask Compose "is it dark?", so overriding the night bit of
 * the configuration they see switches everything at once.
 */
object GlintAppearance {
    const val PREF = "glint_appearance"
    const val SYSTEM = "system"
    const val LIGHT = "light"
    const val DARK = "dark"

    private val mode = mutableStateOf<String?>(null)

    private fun prefs(context: Context) = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun mode(context: Context): State<String?> {
        if (mode.value == null) mode.value = prefs(context).getString(PREF, SYSTEM)
        return mode
    }

    fun set(context: Context, value: String) {
        prefs(context).edit { putString(PREF, value) }
        mode.value = value
    }

    private fun systemDark(config: Configuration) =
        (config.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    fun isDark(context: Context, config: Configuration = context.resources.configuration): Boolean =
        when (mode(context).value) {
            LIGHT -> false
            DARK -> true
            else -> systemDark(config)
        }

    /** [config] with its night bit set to the chosen appearance. */
    fun apply(context: Context, config: Configuration): Configuration {
        val dark = isDark(context, config)
        return Configuration(config).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                (if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO)
        }
    }

    /** A context whose resources (night pictures and clips) follow the chosen appearance. */
    fun context(context: Context): Context = context.createConfigurationContext(apply(context, context.resources.configuration))
}

/** Makes everything inside follow the chosen appearance. */
@Composable
fun ProvideAppearance(context: Context, content: @Composable () -> Unit) {
    val chosen = GlintAppearance.mode(context).value
    val base = LocalConfiguration.current
    val config = remember(base, chosen) { GlintAppearance.apply(context, base) }
    CompositionLocalProvider(LocalConfiguration provides config) { content() }
}

