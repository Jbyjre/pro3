/*
    LibrePods - AirPods liberated from Apple’s ecosystem
    Copyright (C) 2025 LibrePods contributors

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

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

val ColorScheme.sectionHeader: Color
    get() = onBackground.copy(alpha = 0.6f)

private val AppleDarkColorScheme = darkColorScheme(
    surfaceContainer = Color(0xFF000000), // for some reason background is not used as the background in gmail and settings app, but surfacecontainer, so using that
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF1C1C1E),
    onSurface = Color(0xFFFFFFFF),
    surfaceDim = Color(0x40888888),
    primary = Color(0xFF0091FF),
    secondaryContainer = Color(0xFF366AA8),
    onSecondaryContainer = Color(0xFF0091FF),
    onPrimary = Color(0xFFFFFFFF),
    // Glint: the roles below were unset, so Material's default purple palette leaked into any
    // screen using them (welcome page, permissions, release notes). iOS-style values instead.
    background = Color(0xFF000000),
    primaryContainer = Color(0xFF0A3A66),
    onPrimaryContainer = Color(0xFFCFE6FF),
    secondary = Color(0xFF98989F),
    onSecondary = Color(0xFF000000),
    tertiary = Color(0xFF0091FF),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFF0A3A66),
    onTertiaryContainer = Color(0xFFCFE6FF),
    surfaceVariant = Color(0xFF2C2C2E),
    onSurfaceVariant = Color(0x99EBEBF5),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF111113),
    surfaceContainerHigh = Color(0xFF2C2C2E),
    surfaceContainerHighest = Color(0xFF3A3A3C),
    surfaceTint = Color(0xFF1C1C1E),
    outline = Color(0xFF545458),
    outlineVariant = Color(0xFF38383A),
    error = Color(0xFFFF453A),
    onError = Color(0xFFFFFFFF),
)

private val AppleLightColorScheme = lightColorScheme(
    surfaceContainer = Color(0xFFF2F2F7),
    onBackground = Color(0xFF000000),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF000000),
    surfaceDim = Color(0x40D9D9D9),
    secondaryContainer = Color(0xFF6BC0FF),
    onSecondaryContainer = Color(0xFF0088FF),
    primary = Color(0xFF0088FF),
    onPrimary = Color(0xFFFFFFFF),
    // Glint: see the dark scheme; fills the roles Material would otherwise make purple.
    background = Color(0xFFF2F2F7),
    primaryContainer = Color(0xFFD9ECFF),
    onPrimaryContainer = Color(0xFF00386B),
    secondary = Color(0xFF8E8E93),
    onSecondary = Color(0xFFFFFFFF),
    tertiary = Color(0xFF0088FF),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD9ECFF),
    onTertiaryContainer = Color(0xFF00386B),
    surfaceVariant = Color(0xFFE5E5EA),
    onSurfaceVariant = Color(0x993C3C43),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F7FA),
    surfaceContainerHigh = Color(0xFFEBEBF0),
    surfaceContainerHighest = Color(0xFFE5E5EA),
    surfaceTint = Color(0xFFFFFFFF),
    outline = Color(0xFFC6C6C8),
    outlineVariant = Color(0xFFD1D1D6),
    error = Color(0xFFFF3B30),
    onError = Color(0xFFFFFFFF),
)

@Composable
fun LibrePodsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    m3eEnabled: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        m3eEnabled -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> AppleDarkColorScheme
        else -> AppleLightColorScheme
    }

    CompositionLocalProvider(
        LocalDesignSystem provides
            if (m3eEnabled) DesignSystem.Material
            else DesignSystem.Apple
    ) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            motionScheme = MotionScheme.expressive(),
            typography = if (m3eEnabled) MaterialTypography else AppleTypography,
            content = content
        )
    }
}
