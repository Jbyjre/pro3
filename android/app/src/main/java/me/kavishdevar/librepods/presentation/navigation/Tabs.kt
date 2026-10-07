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

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.shadow.Shadow
import me.kavishdevar.librepods.presentation.glint.GlassBudget
import me.kavishdevar.librepods.presentation.glint.GlintComfort
import me.kavishdevar.librepods.presentation.glint.GlintLight
import me.kavishdevar.librepods.presentation.glint.drawHeadphones
import me.kavishdevar.librepods.presentation.overlays.drawBudPair
import me.kavishdevar.librepods.presentation.theme.glintFontFamily
import me.kavishdevar.librepods.services.ChosenDevice
import me.kavishdevar.librepods.services.DeviceKind

/**
 * pro's three main pages, along the bottom: the phone itself (first, the main page), the Dynamic
 * Island's own page, and your headphones (AirPods or Beats, whichever you chose).
 */
enum class AppTab(val title: String) {
    Phone("Phone"),
    Island("Island"),
    Headphones("Headphones");

    /** The tab's word under its picture: the headphones tab is named after the chosen device. */
    fun label(device: ChosenDevice): String = when (this) {
        Headphones -> when (device.kind) {
            DeviceKind.AIRPODS -> "AirPods"
            DeviceKind.BEATS_SOLO_4 -> "Beats"
            DeviceKind.HEADPHONES -> "Headphones"
        }
        else -> title
    }
}

/** How much room the floating tab bar takes at the bottom (0 when it isn't showing). Pages add it below their content. */
val LocalTabBarSpace = compositionLocalOf { 0.dp }

/** The tab bar's height, and the gap under it above the navigation bar. */
val TAB_BAR_HEIGHT: Dp = 64.dp
val TAB_BAR_GAP: Dp = 10.dp

/**
 * The tab bar: one floating capsule of Liquid Glass over the page (it really bends and blurs what
 * scrolls under it, through the page's [backdrop]), with a lighter glass lens that slides to the
 * chosen tab on a spring and stretches a little as it travels, like iOS 26's tab bar.
 */
@Composable
fun GlassTabBar(
    selected: AppTab,
    onSelect: (AppTab) -> Unit,
    backdrop: LayerBackdrop,
    device: ChosenDevice,
    modifier: Modifier = Modifier,
) {
    val dark = isSystemInDarkTheme()
    val ink = if (dark) Color.White else Color.Black
    val context = LocalContext.current
    val reduce = remember { GlintComfort.reduceMotion(context) }
    val reduceTransparency = remember { GlintComfort.reduceTransparency(context) }
    val haptics = LocalHapticFeedback.current
    val tabs = AppTab.entries
    val at = remember { Animatable(selected.ordinal.toFloat()) }
    LaunchedEffect(selected) {
        if (reduce) at.snapTo(selected.ordinal.toFloat())
        else at.animateTo(selected.ordinal.toFloat(), spring(dampingRatio = 0.72f, stiffness = 420f))
    }
    var width by remember { mutableIntStateOf(0) }
    val shape = RoundedCornerShape(TAB_BAR_HEIGHT / 2)
    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp)
            .height(TAB_BAR_HEIGHT)
            .onSizeChanged { width = it.width }
            .drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                highlight = { GlintLight.rim(if (dark) 0.7f else 0.95f) },
                // Anchored along the bottom, not flying free: a soft, wide, faint shadow.
                shadow = { Shadow(radius = 22.dp, offset = DpOffset(0.dp, 6.dp), color = Color.Black.copy(alpha = if (dark) 0.34f else 0.12f)) },
                effects = {
                    vibrancy()
                    blur(if (GlassBudget.light.value) 6f.dp.toPx() else 10f.dp.toPx())
                    lens(14f.dp.toPx(), 30f.dp.toPx(), depthEffect = true, chromaticAberration = !GlassBudget.light.value)
                },
                onDrawSurface = {
                    // Reduce transparency: nearly solid, so the words stay crisp over anything.
                    val tint = when {
                        reduceTransparency -> if (dark) Color(0xF21C1C1E) else Color(0xF2F7F7FA)
                        dark -> Color(0xFF1C1C1E).copy(alpha = 0.42f)
                        else -> Color.White.copy(alpha = 0.52f)
                    }
                    drawRect(tint)
                },
            )
            .drawBehind {
                // The selection lens: a lighter capsule behind the chosen tab, sliding between tabs.
                if (width <= 0) return@drawBehind
                val slot = size.width / tabs.size
                val target = selected.ordinal.toFloat()
                val travel = kotlin.math.abs(at.value - target).coerceAtMost(1f)
                val inset = 5.dp.toPx()
                val w = slot - inset * 2 + slot * 0.22f * travel
                val h = size.height - inset * 2 - 3.dp.toPx() * travel
                val cx = slot * (at.value + 0.5f)
                val left = (cx - w / 2f).coerceIn(inset, size.width - inset - w)
                val top = (size.height - h) / 2f
                val r = CornerRadius(h / 2f)
                drawRoundRect(if (dark) Color.White.copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.07f), Offset(left, top), Size(w, h), r)
                drawRoundRect(
                    Brush.verticalGradient(listOf(Color.White.copy(alpha = if (dark) 0.34f else 0.9f), Color.White.copy(alpha = if (dark) 0.04f else 0.3f)), top, top + h),
                    Offset(left + 0.5f, top + 0.5f), Size(w - 1f, h - 1f), CornerRadius(h / 2f - 0.5f), style = Stroke(1.dp.toPx()),
                )
            },
    ) {
        Row(Modifier.fillMaxWidth().fillMaxHeight()) {
            tabs.forEach { tab ->
                val on = tab == selected
                val tint = ink.copy(alpha = if (on) 1f else 0.55f)
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .selectable(
                            selected = on, role = Role.Tab,
                            interactionSource = remember { MutableInteractionSource() }, indication = null,
                        ) {
                            if (!on) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            onSelect(tab)
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                ) {
                    Canvas(Modifier.size(26.dp)) { tabGlyph(tab, device, tint, on) }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        tab.label(device), maxLines = 1,
                        style = TextStyle(fontFamily = glintFontFamily, fontSize = 11.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium, color = tint),
                    )
                }
            }
        }
    }
}

/** The tab's picture: a phone with its own little island, the island itself, or the headphones. */
private fun DrawScope.tabGlyph(tab: AppTab, device: ChosenDevice, color: Color, on: Boolean) {
    val c = center
    val u = size.minDimension / 2f
    val sw = u * 0.13f
    when (tab) {
        AppTab.Phone -> {
            val w = u * 1.0f; val h = u * 1.7f
            val tl = Offset(c.x - w / 2f, c.y - h / 2f)
            if (on) drawRoundRect(color.copy(alpha = 0.16f), tl, Size(w, h), CornerRadius(u * 0.26f))
            drawRoundRect(color, tl, Size(w, h), CornerRadius(u * 0.26f), style = Stroke(sw))
            // Its own Dynamic Island.
            drawRoundRect(color, Offset(c.x - w * 0.2f, tl.y + h * 0.1f), Size(w * 0.4f, h * 0.075f), CornerRadius(h * 0.04f))
        }
        AppTab.Island -> {
            val w = u * 1.9f; val h = u * 0.82f
            val tl = Offset(c.x - w / 2f, c.y - h / 2f)
            if (on) drawRoundRect(color, tl, Size(w, h), CornerRadius(h / 2f))
            else drawRoundRect(color, tl, Size(w, h), CornerRadius(h / 2f), style = Stroke(sw))
            // The camera in the middle, an app on the left, a ring on the right.
            val inner = if (on) (if (color.red < 0.5f) Color.White else Color.Black) else color
            drawCircle(inner, h * 0.14f, c)
            drawRoundRect(inner, Offset(tl.x + h * 0.28f, c.y - h * 0.2f), Size(h * 0.4f, h * 0.4f), CornerRadius(h * 0.1f))
            drawCircle(inner, h * 0.19f, Offset(tl.x + w - h * 0.48f, c.y), style = Stroke(sw * 0.8f))
        }
        AppTab.Headphones -> if (device.isAirPods) drawBudPair(c, u * 1.7f, color)
        else drawHeadphones(c, u * 0.78f, color)
    }
}
