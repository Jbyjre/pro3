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

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.kavishdevar.librepods.presentation.glint.GlintComfort
import me.kavishdevar.librepods.presentation.glint.pressable
import me.kavishdevar.librepods.presentation.theme.glintFontFamily
import me.kavishdevar.librepods.services.HeartInsights
import me.kavishdevar.librepods.services.HeartRate
import java.text.DateFormat
import java.util.Date
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

/** Heart-rate colours, checked for contrast and lightness on the light and dark cards. */
internal object HeartColors {
    val Light = Color(0xFFE0303A)
    val Dark = Color(0xFFF04A50)
    fun accent(dark: Boolean) = if (dark) Dark else Light

    /** Colour for each band of the "what it means" scale. */
    fun band(b: HeartInsights.Band, dark: Boolean): Color = when (b) {
        HeartInsights.Band.Low -> if (dark) Color(0xFF64D2FF) else Color(0xFF0A84D0)
        HeartInsights.Band.Resting -> if (dark) Color(0xFF30D158) else Color(0xFF1E9E45)
        HeartInsights.Band.Raised -> if (dark) Color(0xFFFFC542) else Color(0xFFC98A00)
        HeartInsights.Band.Exercise -> if (dark) Color(0xFFFF8A4C) else Color(0xFFE0621B)
        HeartInsights.Band.High -> accent(dark)
    }
}

internal fun heartText(size: Int, color: Color, weight: FontWeight = FontWeight.Normal) =
    TextStyle(fontFamily = glintFontFamily, fontSize = size.sp, fontWeight = weight, color = color)

/** Milliseconds from the frame clock while [running]; read it in draw code only. */
@Composable
internal fun rememberFrameTime(running: Boolean): MutableLongState {
    val t = remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        val wall = System.currentTimeMillis()
        var first = -1L
        // An "infinite animation" frame: tests and Android's animation-off setting pause it.
        while (true) androidx.compose.animation.core.withInfiniteAnimationFrameMillis { if (first < 0) first = it; t.longValue = wall + (it - first) }
    }
    return t
}

/**
 * A number whose digits roll like an odometer when it changes: up when it rises, down when
 * it falls. Still with Reduce motion.
 */
@Composable
internal fun RollingNumber(value: String, style: TextStyle, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val reduce = remember { GlintComfort.reduceMotion(context) }
    var last by remember { mutableStateOf(value) }
    val up = (value.toIntOrNull() ?: 0) >= (last.toIntOrNull() ?: 0)
    LaunchedEffect(value) { last = value }
    Row(modifier) {
        // Keyed from the right, so the ones digit always rolls in place.
        value.forEachIndexed { i, c ->
            androidx.compose.runtime.key(value.length - i) {
                AnimatedContent(
                    targetState = c,
                    transitionSpec = {
                        if (reduce) (fadeIn(tween(0)) togetherWith fadeOut(tween(0)))
                        else (slideInVertically(spring(0.75f, 380f)) { if (up) it else -it } + fadeIn(tween(160))) togetherWith
                            (slideOutVertically(spring(0.75f, 380f)) { if (up) -it else it } + fadeOut(tween(120))) using SizeTransform(clip = false)
                    },
                    label = "digit"
                ) { ch -> Text(ch.toString(), style = style) }
            }
        }
    }
}

/**
 * The live card's moving light: soft colour blooms that drift slowly and swell with each
 * beat. It's what the glass on top bends and blurs. Still with Reduce motion.
 */
internal fun androidx.compose.ui.graphics.drawscope.DrawScope.drawAurora(base: Color, accent: Color, dark: Boolean, t: Long, beat: Float, live: Boolean) {
    drawRect(base)
    val k = if (live) 1f else 0.45f
    val a = (if (dark) 0.42f else 0.26f) * k
    val s = (t % 60_000L) / 60_000f * 2f * Math.PI.toFloat()
    val w = size.width
    val h = size.height
    fun bloom(c: Color, x: Float, y: Float, r: Float, alpha: Float) {
        val center = Offset(x, y)
        drawCircle(Brush.radialGradient(listOf(c.copy(alpha = alpha), Color.Transparent), center, r), r, center)
    }
    val swell = 1f + 0.10f * beat
    bloom(accent, w * (0.28f + 0.06f * sin(s)), h * (0.30f + 0.04f * sin(s * 2f)), w * 0.55f * swell, a)
    bloom(Color(0xFFFF4FA3), w * (0.78f + 0.05f * sin(s + 2f)), h * 0.22f, w * 0.42f * swell, a * 0.75f)
    bloom(Color(0xFFFF9F45), w * (0.55f + 0.07f * sin(s * 1.5f + 1f)), h * 0.95f, w * 0.5f, a * 0.55f)
}

/** An ECG-style sweep: one spike per beat at the measured rate, fading out to the left. */
@Composable
internal fun EkgTrace(bpm: Int?, color: Color, time: MutableLongState, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(40.dp)) {
        val mid = size.height * 0.62f
        val amp = size.height * 0.55f
        val stroke = 2.dp.toPx()
        val brush = Brush.horizontalGradient(listOf(Color.Transparent, color.copy(alpha = 0.55f), color))
        if (bpm == null) {
            drawLine(brush, Offset(0f, mid), Offset(size.width, mid), stroke, StrokeCap.Round)
            return@Canvas
        }
        val now = time.longValue
        val period = 60_000.0 / bpm.coerceIn(25, 240)
        val window = 3_200.0
        val n = 140
        val path = Path()
        for (i in 0..n) {
            val f = i / n.toDouble()
            val tt = now - (1 - f) * window
            val phase = ((tt % period) / period).toFloat()
            val y = mid - amp * ekgShape(phase)
            if (i == 0) path.moveTo(0f, y) else path.lineTo((f * size.width).toFloat(), y)
        }
        drawPath(path, brush, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
        val headPhase = ((now % period) / period).toFloat()
        val head = Offset(size.width, mid - amp * ekgShape(headPhase))
        drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.45f), Color.Transparent), head, 10.dp.toPx()), 10.dp.toPx(), head)
        drawCircle(color, 3.dp.toPx(), head)
    }
}

/** A simplified heartbeat trace for one beat (phase 0..1): small P, sharp QRS, round T. */
internal fun ekgShape(p: Float): Float {
    fun g(c: Float, w: Float, a: Float) = a * exp(-((p - c) * (p - c)) / (2f * w * w))
    return g(0.16f, 0.025f, 0.10f) + g(0.285f, 0.008f, -0.12f) + g(0.31f, 0.011f, 1f) + g(0.34f, 0.010f, -0.28f) + g(0.56f, 0.045f, 0.22f)
}

/**
 * Where a reading sits: a bar from 40 to 200 BPM coloured low / resting / raised / effort /
 * high, a marker that glides to the reading on a spring and stretches while it moves, and
 * a small tick at your usual resting rate.
 */
@Composable
internal fun HeartScale(bpm: Int, usual: Int, age: Int, dark: Boolean, ink: Color) {
    val context = LocalContext.current
    val reduce = remember { GlintComfort.reduceMotion(context) }
    val target = bpm.coerceIn(40, 200).toFloat()
    val pos by animateFloatAsState(target, if (reduce) tween(0) else spring(0.62f, 160f), label = "scale")
    val measurer = rememberTextMeasurer()
    val label = heartText(11, ink.copy(alpha = 0.55f))
    // Where effort colour starts: moderate effort for your age, kept above the raised band.
    val exercise = if (age > 0) (HeartInsights.maxHeartRate(age) * 0.5f).coerceIn(112f, 160f) else 120f
    val stops = listOf(
        40f to HeartColors.band(HeartInsights.Band.Low, dark),
        58f to HeartColors.band(HeartInsights.Band.Low, dark),
        66f to HeartColors.band(HeartInsights.Band.Resting, dark),
        96f to HeartColors.band(HeartInsights.Band.Resting, dark),
        106f to HeartColors.band(HeartInsights.Band.Raised, dark),
        exercise to HeartColors.band(HeartInsights.Band.Exercise, dark),
        170f to HeartColors.band(HeartInsights.Band.High, dark),
        200f to HeartColors.band(HeartInsights.Band.High, dark),
    ).sortedBy { it.first }
    Canvas(
        Modifier.fillMaxWidth().height(52.dp)
            .semantics { contentDescription = "Scale from 40 to 200 beats per minute; your reading is at $bpm" }
    ) {
        val pad = 10.dp.toPx()
        val barY = 18.dp.toPx()
        val barH = 8.dp.toPx()
        fun x(v: Float) = pad + (size.width - 2 * pad) * ((v - 40f) / 160f)
        val brush = Brush.horizontalGradient(
            colorStops = stops.map { ((it.first - 40f) / 160f) to it.second }.toTypedArray(),
            startX = pad, endX = size.width - pad
        )
        drawRoundRect(brush, Offset(pad, barY - barH / 2), androidx.compose.ui.geometry.Size(size.width - 2 * pad, barH), androidx.compose.ui.geometry.CornerRadius(barH / 2))
        for (v in listOf(60, 100)) {
            val t = measurer.measure(v.toString(), label)
            drawText(t, topLeft = Offset(x(v.toFloat()) - t.size.width / 2f, barY + barH + 6.dp.toPx()))
        }
        if (usual in 40..200) {
            val ux = x(usual.toFloat())
            drawLine(ink.copy(alpha = 0.6f), Offset(ux, barY - barH), Offset(ux, barY - barH / 2 - 1.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
            val t = measurer.measure("usual", label)
            drawText(t, topLeft = Offset((ux - t.size.width / 2f).coerceIn(0f, size.width - t.size.width), 0f))
        }
        // The marker: a glassy bead in the band's colour, stretched along its travel.
        val mx = x(pos)
        val stretch = (kotlin.math.abs(target - pos) / 40f).coerceIn(0f, 0.5f)
        val r = 9.dp.toPx()
        val c = stops.lastOrNull { it.first <= pos }?.second ?: stops.first().second
        drawCircle(Color.Black.copy(alpha = if (dark) 0.5f else 0.18f), r * 1.05f, Offset(mx, barY + 1.5.dp.toPx()))
        drawOval(Color.White, Offset(mx - r * (1 + stretch), barY - r), androidx.compose.ui.geometry.Size(2 * r * (1 + stretch), 2 * r))
        drawOval(c, Offset(mx - (r - 2.5.dp.toPx()) * (1 + stretch), barY - r + 2.5.dp.toPx()), androidx.compose.ui.geometry.Size(2 * (r - 2.5.dp.toPx()) * (1 + stretch), 2 * (r - 2.5.dp.toPx())))
        drawCircle(Color.White.copy(alpha = 0.55f), r * 0.28f, Offset(mx - r * 0.25f, barY - r * 0.3f))
    }
}

/**
 * A session as one 2dp line over a soft fill, drawn in from the left when it first appears.
 * Touch and drag to read any point: a thin crosshair and the value and time appear above.
 */
@Composable
internal fun HeartChart(samples: List<HeartRate.Sample>, line: Color, ink: Color, surface: Color, dark: Boolean, height: Dp = 170.dp) {
    val context = LocalContext.current
    val reduce = remember { GlintComfort.reduceMotion(context) }
    val reveal = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(Unit) { reveal.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
    val measurer = rememberTextMeasurer()
    var probe by remember { mutableStateOf<Float?>(null) }
    val lo = (floor((samples.minOf { it.bpm } - 5) / 10.0) * 10).toInt()
    val hi = (ceil((samples.maxOf { it.bpm } + 5) / 10.0) * 10).toInt().coerceAtLeast(lo + 20)
    val t0 = samples.first().timeMs
    val t1 = samples.last().timeMs.coerceAtLeast(t0 + 1)
    val multiDay = t1 - t0 > 20 * 3_600_000L
    val timeFmt = remember(multiDay) { if (multiDay) DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) else DateFormat.getTimeInstance(DateFormat.SHORT) }
    val grid = ink.copy(alpha = if (dark) 0.12f else 0.08f)
    val label = heartText(11, ink.copy(alpha = 0.55f))
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(height)
            .semantics { contentDescription = "Heart rate from ${samples.minOf { it.bpm }} to ${samples.maxOf { it.bpm }} beats per minute" }
            .pointerInput(samples.size) {
                detectDragGestures(
                    onDragStart = { probe = it.x },
                    onDragEnd = { probe = null },
                    onDragCancel = { probe = null },
                ) { change, _ -> probe = change.position.x }
            }
            .pointerInput(samples.size) { detectTapGestures(onPress = { probe = it.x; tryAwaitRelease(); probe = null }) }
    ) {
        val left = 34.dp.toPx()
        val top = 22.dp.toPx()
        val bottom = size.height - 18.dp.toPx()
        val right = size.width - 6.dp.toPx()
        fun x(t: Long) = left + (right - left) * ((t - t0).toFloat() / (t1 - t0))
        fun y(b: Int) = bottom - (bottom - top) * ((b - lo).toFloat() / (hi - lo))
        for (v in listOf(lo, (lo + hi) / 2, hi)) {
            val gy = y(v)
            drawLine(grid, Offset(left, gy), Offset(right, gy), strokeWidth = 1.dp.toPx())
            val t = measurer.measure(v.toString(), label)
            drawText(t, topLeft = Offset(0f, gy - t.size.height / 2f))
        }
        val start = measurer.measure(timeFmt.format(Date(t0)), label)
        drawText(start, topLeft = Offset(left, bottom + 3.dp.toPx()))
        val end = measurer.measure(timeFmt.format(Date(t1)), label)
        drawText(end, topLeft = Offset(right - end.size.width, bottom + 3.dp.toPx()))
        // The line, thinned to about one point per pixel column. Gaps over 3 minutes (buds
        // out, or a battery-saving pause) break the line instead of drawing a false slope.
        val step = (samples.size / (right - left)).toInt().coerceAtLeast(1)
        val path = Path()
        val fill = Path()
        var prevT = Long.MIN_VALUE
        var segStartX = 0f
        var lastX = 0f
        samples.forEachIndexed { i, s ->
            if (i % step != 0 && i != samples.lastIndex) return@forEachIndexed
            val px = x(s.timeMs); val py = y(s.bpm)
            if (prevT == Long.MIN_VALUE || s.timeMs - prevT > 180_000L) {
                if (prevT != Long.MIN_VALUE) { fill.lineTo(lastX, bottom); fill.lineTo(segStartX, bottom); fill.close() }
                path.moveTo(px, py); fill.moveTo(px, py); segStartX = px
            } else { path.lineTo(px, py); fill.lineTo(px, py) }
            prevT = s.timeMs; lastX = px
        }
        fill.lineTo(lastX, bottom); fill.lineTo(segStartX, bottom); fill.close()
        clipRect(right = left + (right - left) * reveal.value + 8.dp.toPx()) {
            drawPath(fill, Brush.verticalGradient(listOf(line.copy(alpha = if (dark) 0.28f else 0.18f), line.copy(alpha = 0f)), top, bottom))
            drawPath(path, line, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        if (reveal.value >= 1f) {
            val lastP = Offset(x(samples.last().timeMs), y(samples.last().bpm))
            drawCircle(surface, 6.dp.toPx(), lastP)
            drawCircle(line, 4.dp.toPx(), lastP)
        }
        probe?.let { px ->
            val target = t0 + ((px.coerceIn(left, right) - left) / (right - left) * (t1 - t0)).roundToInt()
            val s = samples.minBy { kotlin.math.abs(it.timeMs - target) }
            val sx = x(s.timeMs)
            drawLine(ink.copy(alpha = 0.35f), Offset(sx, top), Offset(sx, bottom), strokeWidth = 1.dp.toPx())
            drawCircle(surface, 6.dp.toPx(), Offset(sx, y(s.bpm)))
            drawCircle(line, 4.dp.toPx(), Offset(sx, y(s.bpm)))
            val read = measurer.measure("${s.bpm} BPM · ${timeFmt.format(Date(s.timeMs))}", label.copy(color = ink, fontWeight = FontWeight.Medium))
            val rx = (sx - read.size.width / 2f).coerceIn(left, right - read.size.width)
            drawText(read, topLeft = Offset(rx, 0f))
        }
    }
}

/**
 * Days at a glance: one capsule per day from its lowest to highest reading with a dot at the
 * average, growing from the average when it first appears. Tap a day to pick it.
 */
@Composable
internal fun DayBars(
    days: List<HeartInsights.Day>,
    count: Int,
    accent: Color,
    ink: Color,
    dark: Boolean,
    selected: Long?,
    onSelect: (Long?) -> Unit,
    height: Dp = 150.dp,
) {
    val context = LocalContext.current
    val reduce = remember { GlintComfort.reduceMotion(context) }
    val grow = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, spring(0.7f, 120f)) }
    val zone = java.time.ZoneId.systemDefault()
    val today = java.time.LocalDate.now(zone)
    val slots = (count - 1 downTo 0).map { today.minusDays(it.toLong()) }
    val byDate = days.associateBy { java.time.Instant.ofEpochMilli(it.dayStartMs).atZone(zone).toLocalDate() }
    val shown = slots.mapNotNull { byDate[it] }
    val lo = ((shown.minOfOrNull { it.min } ?: 50) - 5).let { (floor(it / 10.0) * 10).toInt() }
    val hi = ((shown.maxOfOrNull { it.max } ?: 120) + 5).let { (ceil(it / 10.0) * 10).toInt() }.coerceAtLeast(lo + 30)
    val measurer = rememberTextMeasurer()
    val label = heartText(11, ink.copy(alpha = 0.55f))
    val grid = ink.copy(alpha = if (dark) 0.12f else 0.08f)
    val dayFmt = remember { java.time.format.DateTimeFormatter.ofPattern("EEEEE") }
    Canvas(
        Modifier.fillMaxWidth().height(height)
            .semantics { contentDescription = "Daily heart rate ranges for the last $count days" }
            .pointerInput(days, count) {
                detectTapGestures { p ->
                    val left = 30.dp.toPx()
                    val slotW = (size.width - left) / count
                    val i = ((p.x - left) / slotW).toInt()
                    val d = slots.getOrNull(i)?.let { byDate[it] }
                    onSelect(if (d == null || d.dayStartMs == selected) null else d.dayStartMs)
                }
            }
    ) {
        val left = 30.dp.toPx()
        val top = 8.dp.toPx()
        val bottom = size.height - 18.dp.toPx()
        fun y(v: Int) = bottom - (bottom - top) * ((v - lo).toFloat() / (hi - lo))
        for (v in listOf(lo, (lo + hi) / 2, hi)) {
            drawLine(grid, Offset(left, y(v)), Offset(size.width, y(v)), 1.dp.toPx())
            val t = measurer.measure(v.toString(), label)
            drawText(t, topLeft = Offset(0f, y(v) - t.size.height / 2f))
        }
        val slotW = (size.width - left) / count
        val barW = (slotW * 0.42f).coerceAtMost(12.dp.toPx())
        slots.forEachIndexed { i, date ->
            val cx = left + slotW * (i + 0.5f)
            val d = byDate[date]
            if (d == null) {
                drawCircle(grid, 2.dp.toPx(), Offset(cx, bottom - 4.dp.toPx()))
            } else {
                val avgY = y(d.average)
                val g = grow.value.coerceIn(0f, 1.2f)
                val topY = avgY - (avgY - y(d.max)) * g
                val botY = avgY + (y(d.min) - avgY) * g
                val isSel = selected == d.dayStartMs
                val a = if (selected == null || isSel) 1f else 0.35f
                drawRoundRect(
                    Brush.verticalGradient(listOf(accent.copy(alpha = 0.95f * a), accent.copy(alpha = 0.45f * a)), topY, botY),
                    Offset(cx - barW / 2, topY), androidx.compose.ui.geometry.Size(barW, (botY - topY).coerceAtLeast(barW)),
                    androidx.compose.ui.geometry.CornerRadius(barW / 2)
                )
                drawCircle(if (dark) Color.Black else Color.White, barW * 0.32f, Offset(cx, avgY))
            }
            if (i % 2 == (count - 1) % 2 || count <= 7) {
                val t = measurer.measure(date.format(dayFmt), label)
                drawText(t, topLeft = Offset(cx - t.size.width / 2f, bottom + 3.dp.toPx()))
            }
        }
    }
}

/** A small range bar for list rows: lowest to highest on a shared scale, dot at the average. */
@Composable
internal fun RangeBar(min: Int, max: Int, avg: Int, lo: Int, hi: Int, accent: Color, ink: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.height(10.dp)) {
        fun x(v: Int) = size.width * ((v - lo).toFloat() / (hi - lo).coerceAtLeast(1))
        val h = size.height
        drawRoundRect(ink.copy(alpha = 0.08f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(h / 2))
        val l = x(min); val r = x(max).coerceAtLeast(l + h)
        drawRoundRect(accent.copy(alpha = 0.75f), Offset(l, 0f), androidx.compose.ui.geometry.Size(r - l, h), androidx.compose.ui.geometry.CornerRadius(h / 2))
        drawCircle(Color.White, h * 0.28f, Offset(x(avg).coerceIn(l + h / 2, r - h / 2), h / 2))
    }
}

/** Time in each effort zone, as one bar from light to peak with labels. */
@Composable
internal fun ZoneBar(times: Map<HeartInsights.Zone, Long>, accent: Color, ink: Color, muted: Color) {
    val total = times.values.sum().coerceAtLeast(1)
    val shades = listOf(0.25f, 0.5f, 0.75f, 1f)
    Text("Time in zones", style = heartText(13, muted, FontWeight.Medium))
    Spacer(Modifier.height(6.dp))
    Row(Modifier.fillMaxWidth().height(10.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        HeartInsights.Zone.entries.forEachIndexed { i, z ->
            val secs = times[z] ?: 0
            if (secs > 0) Box(Modifier.weight(secs.toFloat() / total).fillMaxSize().background(accent.copy(alpha = shades[i]), RoundedCornerShape(4.dp)))
        }
    }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        HeartInsights.Zone.entries.forEachIndexed { i, z ->
            val secs = times[z] ?: 0
            if (secs >= 30) Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(accent.copy(alpha = shades[i]), CircleShape))
                Spacer(Modifier.width(5.dp))
                Text("${z.label} ${(secs + 30) / 60} min", maxLines = 1, style = heartText(12, ink.copy(alpha = 0.75f)))
            }
        }
    }
}

@Composable
internal fun Stat(label: String, value: Int?, ink: Color, muted: Color, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value?.takeIf { it > 0 }?.toString() ?: "--", style = heartText(22, ink, FontWeight.SemiBold))
        Text(label, style = heartText(12, muted))
    }
}

@Composable
internal fun Insight(label: String, value: String, ink: Color, muted: Color, modifier: Modifier, unit: String = " BPM") {
    Column(modifier) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, modifier = Modifier.alignByBaseline(), style = heartText(20, ink, FontWeight.SemiBold))
            if (unit.isNotEmpty()) Text(unit, modifier = Modifier.alignByBaseline(), style = heartText(12, muted))
        }
        Text(label, style = heartText(12, muted))
    }
}

@Composable
internal fun Stepper(symbol: String, ink: Color, dark: Boolean, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (dark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.06f))
            .semantics { contentDescription = description }
            .pressable(onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(symbol, style = heartText(20, ink, FontWeight.Medium))
    }
}

/** "17 h" / "3 h 20 min" / "45 min". */
internal fun durationWords(minutes: Long): String = when {
    minutes >= 600 -> "${(minutes + 30) / 60} h" // long totals: whole hours, so they fit one line
    minutes >= 60 -> "${minutes / 60} h" + if (minutes % 60 > 0) " ${minutes % 60} min" else ""
    else -> "$minutes min"
}

/** Writes readings to a CSV file and opens the share sheet. */
internal fun exportCsv(context: Context, samples: List<HeartRate.Sample>) {
    if (samples.isEmpty()) return
    val dir = java.io.File(context.filesDir, "exports").apply { mkdirs() }
    dir.listFiles()?.forEach { it.delete() } // keep only the latest export
    val stamp = java.text.SimpleDateFormat("yyyy-MM-dd-HHmm", java.util.Locale.US).format(Date(samples.first().timeMs))
    val file = java.io.File(dir, "heart-rate-$stamp.csv")
    file.writeText(HeartInsights.csv(samples))
    val uri = androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".provider", file)
    val send = android.content.Intent(android.content.Intent.ACTION_SEND)
        .setType("text/csv")
        .putExtra(android.content.Intent.EXTRA_STREAM, uri)
        .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(android.content.Intent.createChooser(send, "Export heart rate"))
}
