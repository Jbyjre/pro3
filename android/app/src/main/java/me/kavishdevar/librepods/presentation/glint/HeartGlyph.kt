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

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import kotlinx.coroutines.delay

/**
 * Glint's heart shape, filling [size]: full round lobes and a softly rounded tip (a sharp
 * point and pinched lobes looked odd at the island's size).
 */
fun heartPath(size: Size): Path {
    val w = size.width
    val h = size.height
    return Path().apply {
        moveTo(w * 0.5f, h * 0.28f)
        cubicTo(w * 0.47f, h * 0.15f, w * 0.37f, h * 0.07f, w * 0.26f, h * 0.07f)
        cubicTo(w * 0.12f, h * 0.07f, w * 0.03f, h * 0.18f, w * 0.03f, h * 0.32f)
        cubicTo(w * 0.03f, h * 0.53f, w * 0.22f, h * 0.70f, w * 0.455f, h * 0.895f)
        quadraticTo(w * 0.5f, h * 0.935f, w * 0.545f, h * 0.895f)
        cubicTo(w * 0.78f, h * 0.70f, w * 0.97f, h * 0.53f, w * 0.97f, h * 0.32f)
        cubicTo(w * 0.97f, h * 0.18f, w * 0.88f, h * 0.07f, w * 0.74f, h * 0.07f)
        cubicTo(w * 0.63f, h * 0.07f, w * 0.53f, h * 0.15f, w * 0.5f, h * 0.28f)
        close()
    }
}

private val Swell = CubicBezierEasing(0.3f, 0f, 0.2f, 1f)
private val Release = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)

/**
 * The beat of a heart at [bpm] as a 1-to-[peak] scale: a soft "lub", a smaller "dub", then a
 * long easy release. The beat takes about 60% of each period (so fast rates still get a rest)
 * and one steady loop runs: a new reading changes the next beat's speed instead of restarting
 * the cycle (restarting on every reading made the rhythm stutter). Holds at 1 with no reading
 * or with Reduce motion. Read the result in the draw/layer phase so beating never recomposes.
 */
@Composable
fun rememberHeartBeat(bpm: Int?, reduceMotion: Boolean, peak: Float = 1.14f): Animatable<Float, *> {
    val scale = remember { Animatable(1f) }
    val rate by rememberUpdatedState(bpm)
    LaunchedEffect(bpm == null, reduceMotion) {
        if (bpm == null || reduceMotion) { scale.animateTo(1f, tween(200)); return@LaunchedEffect }
        while (true) {
            val current = rate ?: break
            val period = (60_000 / current.coerceAtLeast(25)).toLong().coerceIn(300L, 2_400L)
            val beat = (period * 0.6f).toLong().coerceIn(240L, 620L)
            scale.animateTo(peak, tween((beat * 0.22f).toInt(), easing = Swell))
            scale.animateTo(1f + (peak - 1f) * 0.45f, tween((beat * 0.18f).toInt(), easing = FastOutSlowInEasing))
            scale.animateTo(1f + (peak - 1f) * 0.72f, tween((beat * 0.16f).toInt(), easing = FastOutSlowInEasing))
            scale.animateTo(1f, tween((beat * 0.44f).toInt(), easing = Release))
            delay(period - beat)
        }
    }
    return scale
}

/** A small heart that beats at [bpm] (still when [bpm] is null). */
@Composable
fun HeartGlyph(bpm: Int?, color: Color, size: Dp = 18.dp, reduceMotion: Boolean = false) {
    val beat = rememberHeartBeat(bpm, reduceMotion)
    Canvas(Modifier.size(size).graphicsLayer { scaleX = beat.value; scaleY = beat.value }) {
        drawPath(heartPath(this.size), if (bpm == null) color.copy(alpha = 0.45f) else color)
    }
}
