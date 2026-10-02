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

package me.kavishdevar.librepods.presentation.glint

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.kavishdevar.librepods.presentation.theme.glintFontFamily
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

object GlintColors {
    val Green = Color(0xFF30D158)
    val Amber = Color(0xFFFF9F0A)
    val Red = Color(0xFFFF453A)
    val Blue = Color(0xFF0A84FF)

    fun forLevel(level: Int?): Color = when {
        level == null -> Color(0x80888888)
        level <= 10 -> Red
        level <= 20 -> Amber
        else -> Green
    }
}

fun listeningModeName(mode: Int): String = when (mode) {
    1 -> "Off"
    2 -> "Noise Cancellation"
    3 -> "Transparency"
    4 -> "Adaptive"
    else -> "Listening Mode"
}

/** Circular battery gauge with a rounded cap, optional % label and charging bolt. */
@Composable
fun BatteryRing(
    level: Int?,
    charging: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    stroke: Dp = 3.5.dp,
    track: Color = Color(0x33FFFFFF),
    label: Color = Color.White,
    labelSize: TextUnit = 11.sp,
    showLabel: Boolean = true,
    /** Draw a bolt in the middle while charging (off when something else sits in the middle). */
    centerBolt: Boolean = true,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val reduce = androidx.compose.runtime.remember { GlintComfort.reduceMotion(context) }
    val target = ((level ?: 0) / 100f).coerceIn(0f, 1f)
    // Fills from empty when it first appears, then follows changes on a spring.
    val fill = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(if (reduce) target else 0f) }
    androidx.compose.runtime.LaunchedEffect(target) { if (reduce) fill.snapTo(target) else fill.animateTo(target, spring(dampingRatio = 0.8f, stiffness = 120f)) }
    // While charging, a soft light travels round the filled part of the ring.
    val shimmer = if (charging && level != null && !reduce) {
        androidx.compose.animation.core.rememberInfiniteTransition(label = "charge").animateFloat(
            0f, 1f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(2_200, easing = androidx.compose.animation.core.LinearEasing)), label = "charge"
        )
    } else null
    val color = if (charging && (level ?: 0) > 20) GlintColors.Green else GlintColors.forLevel(level)
    Box(
        modifier
            .size(size)
            .semantics { contentDescription = if (level != null) "$level percent${if (charging) ", charging" else ""}" else "battery unknown" },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(size)) {
            val s = stroke.toPx()
            val inset = s / 2f
            val arcSize = Size(this.size.width - s, this.size.height - s)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(s))
            val sweep = fill.value
            if (level != null) {
                drawArc(color, -90f, 360f * sweep, false, Offset(inset, inset), arcSize, style = Stroke(s, cap = StrokeCap.Round))
                shimmer?.let { sh ->
                    val span = 360f * sweep
                    if (span > 30f) {
                        val head = -90f + span * sh.value
                        drawArc(Color.White.copy(alpha = 0.55f), head - 14f, 14f, false, Offset(inset, inset), arcSize, style = Stroke(s * 0.7f, cap = StrokeCap.Round))
                    }
                }
            }
            if (charging) {
                val r = this.size.minDimension * 0.17f
                val c = Offset(this.size.width * 0.5f, this.size.height * 0.5f)
                if (centerBolt && (!showLabel || level == null)) drawBolt(c, r * 1.4f, color)
            }
        }
        if (showLabel && level != null) {
            Text(
                "$level",
                style = TextStyle(fontFamily = glintFontFamily, fontWeight = FontWeight.SemiBold, fontSize = labelSize, color = label)
            )
        }
    }
}

fun DrawScope.drawBolt(center: Offset, r: Float, color: Color) {
    val p = Path().apply {
        moveTo(center.x + r * 0.15f, center.y - r)
        lineTo(center.x - r * 0.55f, center.y + r * 0.12f)
        lineTo(center.x - r * 0.02f, center.y + r * 0.12f)
        lineTo(center.x - r * 0.18f, center.y + r)
        lineTo(center.x + r * 0.55f, center.y - r * 0.15f)
        lineTo(center.x + r * 0.02f, center.y - r * 0.15f)
        close()
    }
    drawPath(p, color)
}

/** Small monochrome symbols for the four listening modes. */
@Composable
fun ListeningModeGlyph(mode: Int, color: Color, modifier: Modifier = Modifier, size: Dp = 22.dp) {
    Canvas(modifier.size(size)) { drawListeningMode(mode, color, center, this.size.minDimension / 2f) }
}

/** The listening mode's symbol, drawn at [c] with radius [r] (so overlays can draw it directly). */
fun androidx.compose.ui.graphics.drawscope.DrawScope.drawListeningMode(mode: Int, color: Color, c: Offset, r: Float) {
    val sw = r * 0.14f
    when (mode) {
        2 -> { // noise cancellation: solid core, two rings
            drawCircle(color, r * 0.3f, c)
            drawCircle(color, r * 0.62f, c, style = Stroke(sw))
            drawCircle(color, r * 0.92f, c, style = Stroke(sw))
        }
        3 -> { // transparency: dotted rings, open core
            drawCircle(color, r * 0.3f, c, style = Stroke(sw))
            for (i in 0 until 12) {
                val a = i * (2 * PI / 12).toFloat()
                drawCircle(color, sw * 0.75f, Offset(c.x + cos(a) * r * 0.72f, c.y + sin(a) * r * 0.72f))
            }
        }
        4 -> { // adaptive: half filled, half outlined
            drawCircle(color, r * 0.9f, c, style = Stroke(sw))
            drawArc(color, 90f, 180f, true, Offset(c.x - r * 0.62f, c.y - r * 0.62f), Size(r * 1.24f, r * 1.24f))
        }
        1 -> { // off: ring with a slash
            drawCircle(color, r * 0.8f, c, style = Stroke(sw))
            drawLine(color, Offset(c.x - r * 0.57f, c.y + r * 0.57f), Offset(c.x + r * 0.57f, c.y - r * 0.57f), sw, StrokeCap.Round)
        }
        else -> drawCircle(color, r * 0.4f, c)
    }
}

/**
 * Headphones art for the islands, sized like the AirPods clip it stands in for: the drawn
 * headphones with a soft light behind them (the studio glow the AirPods video has), so the
 * pop-ups keep the same weight whichever device you chose.
 */
@Composable
fun HeadphonesArt(width: Dp, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(width, width * 0.56f)) {
        val r = size.height * 0.48f
        drawCircle(
            androidx.compose.ui.graphics.Brush.radialGradient(
                listOf(color.copy(alpha = 0.16f), Color.Transparent), center, r * 1.5f,
            ),
            r * 1.5f, center,
        )
        drawHeadphones(center, r, color)
    }
}

/**
 * On-ear headphones (a headband and two cups), drawn at [c] within radius [r]. Used wherever
 * the islands would show AirPods for other headphones (Beats Solo 4 and the rest).
 */
fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHeadphones(c: Offset, r: Float, color: Color) {
    val sw = r * 0.2f
    val band = r * 0.78f
    drawArc(color, 180f, 180f, false, Offset(c.x - band, c.y - band + r * 0.12f), Size(band * 2f, band * 2f), style = Stroke(sw, cap = StrokeCap.Round))
    val cupW = r * 0.46f
    val cupH = r * 0.78f
    val top = c.y + r * 0.02f
    val rad = androidx.compose.ui.geometry.CornerRadius(cupW * 0.45f)
    drawRoundRect(color, Offset(c.x - band - cupW / 2f, top), Size(cupW, cupH), rad)
    drawRoundRect(color, Offset(c.x + band - cupW / 2f, top), Size(cupW, cupH), rad)
}

/**
 * The liquid neck between two circles (metaball connector), so a bubble can bud off from the
 * pill and melt back into it. Returns null when the circles are too far apart to connect.
 * Based on the classic metaball construction (tangent points + cubic handles).
 */
fun metaballNeck(c1: Offset, r1: Float, c2: Offset, r2: Float, v: Float = 0.5f, handle: Float = 2.4f, reach: Float = 2.2f): Path? {
    val d = hypot(c2.x - c1.x, c2.y - c1.y)
    if (r1 <= 0f || r2 <= 0f || d > r1 + r2 * reach || d <= abs(r1 - r2)) return null
    val u1: Float
    val u2: Float
    if (d < r1 + r2) {
        u1 = acos(((r1 * r1 + d * d - r2 * r2) / (2 * r1 * d)).coerceIn(-1f, 1f))
        u2 = acos(((r2 * r2 + d * d - r1 * r1) / (2 * r2 * d)).coerceIn(-1f, 1f))
    } else {
        u1 = 0f; u2 = 0f
    }
    val between = atan2(c2.y - c1.y, c2.x - c1.x)
    val maxSpread = acos(((r1 - r2) / d).coerceIn(-1f, 1f))
    val pi = PI.toFloat()
    val a1 = between + u1 + (maxSpread - u1) * v
    val a2 = between - u1 - (maxSpread - u1) * v
    val a3 = between + pi - u2 - (pi - u2 - maxSpread) * v
    val a4 = between - pi + u2 + (pi - u2 - maxSpread) * v
    fun pt(c: Offset, a: Float, r: Float) = Offset(c.x + cos(a) * r, c.y + sin(a) * r)
    val p1 = pt(c1, a1, r1)
    val p2 = pt(c1, a2, r1)
    val p3 = pt(c2, a3, r2)
    val p4 = pt(c2, a4, r2)
    val total = r1 + r2
    val d2Base = min(v * handle, hypot(p1.x - p3.x, p1.y - p3.y) / total)
    val d2 = d2Base * min(1f, d * 2f / total)
    val r1h = r1 * d2
    val r2h = r2 * d2
    val h1 = pt(p1, a1 - pi / 2f, r1h)
    val h2 = pt(p2, a2 + pi / 2f, r1h)
    val h3 = pt(p3, a3 + pi / 2f, r2h)
    val h4 = pt(p4, a4 - pi / 2f, r2h)
    return Path().apply {
        moveTo(p1.x, p1.y)
        cubicTo(h1.x, h1.y, h3.x, h3.y, p3.x, p3.y)
        lineTo(p4.x, p4.y)
        cubicTo(h4.x, h4.y, h2.x, h2.y, p2.x, p2.y)
        close()
    }
}

fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
