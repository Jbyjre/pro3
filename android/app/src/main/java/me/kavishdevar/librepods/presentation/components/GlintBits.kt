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
import androidx.compose.animation.core.animateFloatAsState
import me.kavishdevar.librepods.presentation.theme.ThemeReveal
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.onGloballyPositioned
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
    val mode by GlintAppearance.mode(context)
    val options = listOf(GlintAppearance.SYSTEM to "Automatic", GlintAppearance.LIGHT to "Light", GlintAppearance.DARK to "Dark")
    val selected = options.indexOfFirst { it.first == (mode ?: GlintAppearance.SYSTEM) }.coerceAtLeast(0)
    LiquidSegments(
        options.map { it.second }, selected, { GlintAppearance.set(context, options[it].first) }, modifier,
        icons = listOf(
            { on, c -> AppearanceGlyph(0, on, c) },
            { on, c -> AppearanceGlyph(1, on, c) },
            { on, c -> AppearanceGlyph(2, on, c) },
        ),
        onSelectFrom = { i, at -> ThemeReveal.change(context, options[i].first, at) },
    )
}

/**
 * Small animated symbols for the appearance picker: [kind] 0 = Automatic (a half-lit disc
 * that turns over), 1 = Light (a sun whose rays stretch out and turn), 2 = Dark (a crescent
 * that rocks into place with a twinkling star). They animate when picked.
 */
@Composable
fun AppearanceGlyph(kind: Int, selected: Boolean, color: Color) {
    val context = LocalContext.current
    val reduce = remember { GlintComfort.reduceMotion(context) }
    val p by animateFloatAsState(if (selected) 1f else 0f, if (reduce) tween(0) else spring(0.55f, 260f), label = "glyph")
    androidx.compose.foundation.Canvas(Modifier.padding(end = 6.dp).size(16.dp)) {
        val c = center
        val r = size.minDimension / 2f
        when (kind) {
            0 -> rotate(180f * p, c) {
                drawCircle(color, r * 0.78f, c, style = androidx.compose.ui.graphics.drawscope.Stroke(r * 0.2f))
                drawArc(color, 90f, 180f, true, androidx.compose.ui.geometry.Offset(c.x - r * 0.78f, c.y - r * 0.78f), androidx.compose.ui.geometry.Size(r * 1.56f, r * 1.56f))
            }
            1 -> rotate(45f * p, c) {
                drawCircle(color, r * (0.36f + 0.06f * p), c)
                val len = r * (0.18f + 0.14f * p)
                for (k in 0 until 8) {
                    val a = Math.toRadians(k * 45.0)
                    val d = r * 0.62f
                    val s0 = androidx.compose.ui.geometry.Offset(c.x + (d * kotlin.math.cos(a)).toFloat(), c.y + (d * kotlin.math.sin(a)).toFloat())
                    val s1 = androidx.compose.ui.geometry.Offset(c.x + ((d + len) * kotlin.math.cos(a)).toFloat(), c.y + ((d + len) * kotlin.math.sin(a)).toFloat())
                    drawLine(color, s0, s1, r * 0.16f, androidx.compose.ui.graphics.StrokeCap.Round)
                }
            }
            else -> {
                rotate(-30f + 30f * p, c) {
                    val moon = androidx.compose.ui.graphics.Path().apply {
                        addOval(androidx.compose.ui.geometry.Rect(c, r * 0.8f))
                        op(this, androidx.compose.ui.graphics.Path().apply { addOval(androidx.compose.ui.geometry.Rect(androidx.compose.ui.geometry.Offset(c.x + r * 0.42f, c.y - r * 0.3f), r * 0.7f)) }, androidx.compose.ui.graphics.PathOperation.Difference)
                    }
                    drawPath(moon, color)
                }
                if (p > 0.05f) drawCircle(color.copy(alpha = p), r * 0.14f * p, androidx.compose.ui.geometry.Offset(c.x + r * 0.62f, c.y - r * 0.62f))
            }
        }
    }
}

/**
 * A segmented control with a liquid glass thumb: it stretches along its travel, settles with
 * a little give, and carries a soft top sheen and rim like a drop of glass. Still with
 * Reduce motion.
 */
@Composable
fun LiquidSegments(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    track: Color? = null,
    /** Optional symbol before each label, given (selected, colour). */
    icons: List<@Composable (Boolean, Color) -> Unit>? = null,
    /** Like [onSelect], with the tapped segment's centre in window coordinates. */
    onSelectFrom: ((Int, androidx.compose.ui.geometry.Offset) -> Unit)? = null,
) {
    var origin by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    var boxSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val ink = if (dark) Color.White else Color.Black
    val reduceMotion = remember { GlintComfort.reduceMotion(context) }
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(track ?: if (dark) Color(0xFF1C1C1E) else Color.White, RoundedCornerShape(22.dp))
            .onGloballyPositioned { origin = it.positionInWindow(); boxSize = it.size }
            .padding(4.dp)
    ) {
        val segment = maxWidth / options.size
        val x by animateDpAsState(segment * selected, if (reduceMotion) tween(0) else spring(0.8f, 420f), label = "segment")
        val thumb = if (dark) Color.White.copy(alpha = 0.16f) else Color.Black.copy(alpha = 0.07f)
        val sheen = if (dark) 0.22f else 0.65f
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
                .background(thumb, RoundedCornerShape(18.dp))
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        0f to Color.White.copy(alpha = sheen * 0.5f), 0.45f to Color.Transparent, 1f to Color.White.copy(alpha = sheen * 0.12f)
                    ),
                    RoundedCornerShape(18.dp)
                )
                .border(
                    0.75.dp,
                    androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.White.copy(alpha = sheen), Color.White.copy(alpha = sheen * 0.15f), Color.White.copy(alpha = sheen * 0.45f))),
                    RoundedCornerShape(18.dp)
                )
        )
        Row(Modifier.fillMaxSize().selectableGroup()) {
            options.forEachIndexed { i, label ->
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(18.dp))
                        .selectable(selected = i == selected, role = Role.RadioButton) {
                            val at = origin + androidx.compose.ui.geometry.Offset(boxSize.width * (i + 0.5f) / options.size, boxSize.height / 2f)
                            onSelectFrom?.invoke(i, at) ?: onSelect(i)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                    icons?.getOrNull(i)?.invoke(i == selected, ink.copy(alpha = if (i == selected) 1f else 0.65f))
                    Text(
                        label,
                        maxLines = 1,
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
}
