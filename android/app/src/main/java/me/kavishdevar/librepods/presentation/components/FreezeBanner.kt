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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import me.kavishdevar.librepods.presentation.glint.IconAction
import me.kavishdevar.librepods.presentation.glint.RowIconTile
import me.kavishdevar.librepods.presentation.glint.RowIcons
import me.kavishdevar.librepods.presentation.glint.pressable
import me.kavishdevar.librepods.presentation.navigation.AppLinks
import me.kavishdevar.librepods.presentation.theme.glintFontFamily
import me.kavishdevar.librepods.services.FreezeReport

/**
 * A small card on the main page when pro stopped responding or crashed since you last looked:
 * a tap opens Troubleshooting (where the details can be copied), × dismisses it. Not shown
 * otherwise.
 */
@Composable
fun FreezeBanner(modifier: Modifier = Modifier, preset: Boolean? = null) {
    val context = LocalContext.current
    var unread by remember { mutableStateOf(preset ?: FreezeReport.hasUnread(context)) }
    // The report is saved a moment after pro starts, so look again for a little while.
    LaunchedEffect(preset) {
        if (preset != null) return@LaunchedEffect
        repeat(5) { unread = FreezeReport.hasUnread(context); delay(2_000) }
    }
    val dark = isSystemInDarkTheme()
    val ink = if (dark) Color.White else Color.Black
    AnimatedVisibility(
        unread,
        enter = fadeIn() + expandVertically(spring(0.85f, 300f)),
        exit = fadeOut() + shrinkVertically(spring(0.9f, 400f)),
        modifier = modifier,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(if (dark) Color(0xFF1C1C1E) else Color.White)
                .pressable { AppLinks.pending.value = AppLinks.TROUBLESHOOTING }
                .padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RowIconTile(RowIcons.Wrench, ink, dark, size = 34.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("pro stopped earlier", style = TextStyle(fontFamily = glintFontFamily, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = ink))
                Text("Tap to see what happened", style = TextStyle(fontFamily = glintFontFamily, fontSize = 13.sp, color = ink.copy(alpha = 0.6f)))
            }
            IconAction(RowIcons.Close, "Dismiss", ink, dark, size = 34.dp) {
                FreezeReport.markRead(context)
                unread = false
            }
        }
    }
}
