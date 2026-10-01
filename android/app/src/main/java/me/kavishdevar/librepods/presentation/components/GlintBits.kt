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

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import me.kavishdevar.librepods.presentation.glint.GlintComfort
import me.kavishdevar.librepods.presentation.theme.GlintAppearance
import me.kavishdevar.librepods.presentation.theme.glintFontFamily

/**
 * A small "i" you can tap for the longer explanation, so screens can say things in a few words
 * and keep the detail one tap away. The explanation opens in a bubble and closes on any tap.
 */
@Composable
fun InfoTip(title: String, text: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val ink = if (dark) Color.White else Color.Black
    var open by remember { mutableStateOf(false) }
    Box(
        modifier
            .size(28.dp) // touch target; the visible dot is smaller
            .clip(CircleShape)
            .clickable { open = !open }
            .semantics { role = Role.Button; contentDescription = "More about $title" },
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.size(18.dp).border(1.3.dp, ink.copy(alpha = 0.45f), CircleShape), contentAlignment = Alignment.Center) {
            Text("i", style = TextStyle(fontFamily = glintFontFamily, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = ink.copy(alpha = 0.6f)))
        }
        if (open) {
            Popup(
                alignment = Alignment.TopCenter,
                offset = IntOffset(0, 80),
                onDismissRequest = { open = false },
                properties = PopupProperties(focusable = true),
            ) {
                // The bubble grows out of the "i", like a glass control expanding into a sheet.
                val shown = remember { androidx.compose.animation.core.MutableTransitionState(false) }.apply { targetState = true }
                val reduceMotion = remember { GlintComfort.reduceMotion(context) }
                androidx.compose.animation.AnimatedVisibility(
                    visibleState = shown,
                    enter = if (reduceMotion) androidx.compose.animation.fadeIn(tween(120)) else
                        androidx.compose.animation.fadeIn(tween(140)) + androidx.compose.animation.scaleIn(
                            spring(0.72f, 420f), initialScale = 0.6f,
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f)
                        ),
                ) {
                Column(
                    Modifier
                        .padding(horizontal = 24.dp)
                        .widthIn(max = 320.dp)
                        .shadow(18.dp, RoundedCornerShape(22.dp), ambientColor = Color.Black, spotColor = Color.Black)
                        .background(if (dark) Color(0xFF2C2C2E) else Color.White, RoundedCornerShape(22.dp))
                        .clickable { open = false }
                        .padding(horizontal = 18.dp, vertical = 14.dp)
                ) {
                    Text(title, style = TextStyle(fontFamily = glintFontFamily, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = ink))
                    Text(text, modifier = Modifier.padding(top = 4.dp), style = TextStyle(fontFamily = glintFontFamily, fontSize = 14.sp, lineHeight = 19.sp, color = ink.copy(alpha = 0.75f)))
                }
                }
            }
        }
    }
}

/** Automatic / Light / Dark, for the whole app and the pop-ups. */
@Composable
fun AppearancePicker(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val ink = if (dark) Color.White else Color.Black
    val reduceMotion = remember { GlintComfort.reduceMotion(context) }
    val mode by GlintAppearance.mode(context)
    val options = listOf(GlintAppearance.SYSTEM to "Automatic", GlintAppearance.LIGHT to "Light", GlintAppearance.DARK to "Dark")
    val selected = options.indexOfFirst { it.first == (mode ?: GlintAppearance.SYSTEM) }.coerceAtLeast(0)
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(if (dark) Color(0xFF1C1C1E) else Color.White, RoundedCornerShape(22.dp))
            .padding(4.dp)
    ) {
        val segment = maxWidth / options.size
        val x by animateDpAsState(segment * selected, if (reduceMotion) tween(0) else spring(0.8f, 420f), label = "theme")
        Box(
            Modifier
                .offset { IntOffset(x.roundToPx(), 0) }
                .width(segment)
                .fillMaxHeight()
                .graphicsLayer {
                    // Liquid thumb: stretches along its travel, settles with a little give.
                    val travel = (kotlin.math.abs((segment * selected - x).toPx()) / segment.toPx()).coerceIn(0f, 1f)
                    scaleX = 1f + 0.25f * travel
                    scaleY = 1f - 0.06f * travel
                }
                .background(if (dark) Color.White.copy(alpha = 0.16f) else Color.Black.copy(alpha = 0.07f), RoundedCornerShape(18.dp))
        )
        Row(Modifier.fillMaxSize().selectableGroup()) {
            options.forEachIndexed { i, (value, label) ->
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(18.dp))
                        .selectable(selected = i == selected, role = Role.RadioButton) { GlintAppearance.set(context, value) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        style = TextStyle(
                            fontFamily = glintFontFamily, fontSize = 15.sp,
                            fontWeight = if (i == selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = ink.copy(alpha = if (i == selected) 1f else 0.65f)
                        )
                    )
                }
            }
        }
    }
}
