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
package me.kavishdevar.librepods.presentation.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.kavishdevar.librepods.presentation.theme.glintFontFamily
import me.kavishdevar.librepods.services.BatteryEstimate
import me.kavishdevar.librepods.services.BatteryWords

/**
 * Listening time left (or time to full while charging) under the battery rings. With no estimate (the AirPods' own link isn't up, so only coarse
 * 10% levels are known) it says so instead of guessing.
 */
@Composable
fun BatteryTimeLeft(estimate: BatteryEstimate?, connected: Boolean) {
    val dark = isSystemInDarkTheme()
    val ink = if (dark) Color.White else Color.Black
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val headline = when {
            estimate != null -> BatteryWords.headline(estimate)
            connected -> "Estimating time left…"
            else -> null
        }
        if (headline != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    headline,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, fontFamily = glintFontFamily, color = ink, textAlign = TextAlign.Center)
                )
                InfoTip(
                    "How this is worked out",
                    (estimate?.let { BatteryWords.detail(it) + ". " } ?: "") +
                        "It starts from Apple's rating (8 hours with noise cancellation) and switches to your real drain rate after about half an hour of listening, using the AirPods' 1% battery reports."
                )
            }
            estimate?.let { BatteryWords.case(it) }?.let {
                Text(it, style = TextStyle(fontSize = 13.sp, fontFamily = glintFontFamily, color = ink.copy(alpha = 0.55f), textAlign = TextAlign.Center))
            }
        }
    }
}
