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

package me.kavishdevar.librepods.presentation.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.kavishdevar.librepods.presentation.overlays.GlassPillButton
import me.kavishdevar.librepods.presentation.theme.glintFontFamily
import me.kavishdevar.librepods.services.FreezeReport
import java.text.DateFormat
import java.util.Date

/**
 * Troubleshooting > Freezes and crashes: when pro last stopped responding or crashed, in Android's
 * own words, with a button that copies the details so they can be pasted to Claude. Nothing is
 * sent anywhere.
 */
@Composable
fun FreezeCard(ink: Color, dark: Boolean, preset: List<FreezeReport.Report>? = null) {
    val context = LocalContext.current
    var reports by remember { mutableStateOf(preset ?: emptyList()) }
    LaunchedEffect(Unit) {
        if (preset == null) reports = withContext(Dispatchers.IO) { FreezeReport.reports(context) }
        FreezeReport.markRead(context)
    }
    val latest = reports.firstOrNull()
    Column(
        Modifier
            .fillMaxWidth()
            .background(if (dark) Color(0xFF1C1C1E) else Color.White, RoundedCornerShape(24.dp))
            .padding(16.dp)
            .animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Freezes and crashes", style = TextStyle(fontFamily = glintFontFamily, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = ink))
        if (latest == null) {
            Text(
                "None recorded. If pro ever stops responding or crashes, Android's note about why is saved here, and you can copy it to send to Claude.",
                style = TextStyle(fontFamily = glintFontFamily, fontSize = 14.sp, lineHeight = 19.sp, color = ink.copy(alpha = 0.7f)),
            )
        } else {
            val whenText = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(latest.at))
            Text(
                "${latest.label} · $whenText" + if (reports.size > 1) " · ${reports.size} saved" else "",
                style = TextStyle(fontFamily = glintFontFamily, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = ink),
            )
            Text(
                "Copy the details and paste them to Claude: they say exactly what pro was doing when it happened.",
                style = TextStyle(fontFamily = glintFontFamily, fontSize = 13.sp, lineHeight = 18.sp, color = ink.copy(alpha = 0.6f)),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassPillButton(text = "Copy details", textColor = ink, dark = dark, height = 40.dp, fontSize = 15.sp) {
                    val all = reports.joinToString("\n\n----------\n\n") { it.text }
                    context.getSystemService(ClipboardManager::class.java)
                        ?.setPrimaryClip(ClipData.newPlainText("pro report", all))
                }
                GlassPillButton(text = "Clear", textColor = ink, dark = dark, height = 40.dp, fontSize = 15.sp) {
                    FreezeReport.clear(context)
                    reports = emptyList()
                }
            }
        }
    }
}
