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
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import kotlinx.coroutines.delay

/** Glint's heart shape, filling [size]. */
fun heartPath(size: Size): Path {
    val w = size.width
    val h = size.height
    return Path().apply {
        moveTo(w * 0.5f, h * 0.92f)
        cubicTo(w * 0.1f, h * 0.62f, -w * 0.02f, h * 0.30f, w * 0.22f, h * 0.14f)
        cubicTo(w * 0.36f, h * 0.04f, w * 0.48f, h * 0.14f, w * 0.5f, h * 0.24f)
        cubicTo(w * 0.52f, h * 0.14f, w * 0.64f, h * 0.04f, w * 0.78f, h * 0.14f)
        cubicTo(w * 1.02f, h * 0.30f, w * 0.9f, h * 0.62f, w * 0.5f, h * 0.92f)
        close()
    }
}

/**
 * The beat of a heart at [bpm] as a 1-to-[peak] scale: a quick lub-dub swell per beat.
 * Holds at 1 with no reading or with Reduce motion. Returns the animatable to read in the
 * draw phase (so beating never recomposes).
 */
@Composable
fun rememberHeartBeat(bpm: Int?, reduceMotion: Boolean, peak: Float = 1.14f): Animatable<Float, *> {
    val scale = remember { Animatable(1f) }
    LaunchedEffect(bpm, reduceMotion) {
        if (bpm == null || reduceMotion) { scale.snapTo(1f); return@LaunchedEffect }
        while (true) {
            val period = (60_000 / bpm.coerceAtLeast(25)).toLong().coerceIn(240L, 2_400L)
            // "Lub" then a smaller "dub", like a real heartbeat.
            scale.animateTo(peak, tween(100, easing = FastOutSlowInEasing))
            scale.animateTo(1f + (peak - 1f) * 0.35f, tween(90, easing = FastOutSlowInEasing))
            scale.animateTo(1f + (peak - 1f) * 0.6f, tween(80, easing = FastOutSlowInEasing))
            scale.animateTo(1f, tween(200, easing = FastOutSlowInEasing))
            delay((period - 470).coerceAtLeast(0))
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
