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
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import kotlinx.coroutines.delay
import me.kavishdevar.librepods.presentation.glint.GlassTier
import me.kavishdevar.librepods.presentation.glint.GlintColors
import me.kavishdevar.librepods.presentation.glint.GlintComfort
import me.kavishdevar.librepods.presentation.glint.glintGlass
import me.kavishdevar.librepods.presentation.theme.glintFontFamily
import me.kavishdevar.librepods.services.CommandFeedback

/**
 * A floating glass pill that says whether your last change reached the AirPods: "Switching
 * to Adaptive…", then "Adaptive is on", or what went wrong with a Reconnect button.
 */
@Composable
fun CommandNotice(backdrop: Backdrop, onReconnect: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val notice by CommandFeedback.notice.collectAsState()
    val dark = isSystemInDarkTheme()
    val solid = remember { GlintComfort.reduceTransparency(context) }
    val reduceMotion = remember { GlintComfort.reduceMotion(context) }
    var shown by remember { mutableStateOf(notice) }
    LaunchedEffect(notice) {
        val n = notice ?: return@LaunchedEffect
        shown = n
        when (n.kind) {
            CommandFeedback.Kind.Pending -> Unit
            CommandFeedback.Kind.Done -> { delay(2_200); if (CommandFeedback.notice.value == n) CommandFeedback.clear() }
            CommandFeedback.Kind.Failed -> { delay(7_000); if (CommandFeedback.notice.value == n) CommandFeedback.clear() }
        }
    }
    val ink = if (dark) Color.White else Color.Black
    AnimatedVisibility(
        visible = notice != null,
        modifier = modifier,
        enter = if (reduceMotion) fadeIn(tween(120)) else fadeIn(tween(160)) + slideInVertically(spring(0.75f, 420f)) { it / 2 } + scaleIn(spring(0.7f, 420f), initialScale = 0.85f),
        exit = if (reduceMotion) fadeOut(tween(120)) else fadeOut(tween(160)) + slideOutVertically(tween(200)) { it / 3 } + scaleOut(tween(200), targetScale = 0.9f),
    ) {
        val n = shown ?: return@AnimatedVisibility
        Row(
            Modifier
                .widthIn(max = 420.dp)
                .padding(horizontal = 16.dp)
                .glintGlass(backdrop, dark, solid, RoundedCornerShape(26.dp), GlassTier.Floating)
                .padding(horizontal = 18.dp, vertical = 13.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
            verticalAlignment = Alignment.CenterVertically
        ) {
            val dot = when (n.kind) {
                CommandFeedback.Kind.Pending -> GlintColors.Amber
                CommandFeedback.Kind.Done -> GlintColors.Green
                CommandFeedback.Kind.Failed -> GlintColors.Red
            }
            Box(Modifier.size(9.dp).background(dot, CircleShape))
            Spacer(Modifier.width(10.dp))
            Text(
                n.text,
                modifier = Modifier.weight(1f, fill = false),
                style = TextStyle(fontFamily = glintFontFamily, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = ink)
            )
            if (n.kind == CommandFeedback.Kind.Failed) {
                Spacer(Modifier.width(10.dp))
                Text(
                    "Reconnect",
                    style = TextStyle(fontFamily = glintFontFamily, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (dark) Color(0xFF64A8FF) else Color(0xFF0A60D6)),
                    modifier = Modifier.clickable { CommandFeedback.clear(); onReconnect() }.padding(6.dp)
                )
            }
        }
    }
}
