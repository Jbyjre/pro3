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

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

/** Screenshot tests switch the idle turn off so the screen can settle. */
object PodsSpinnerConfig {
    @Volatile var idleTurn = true
}

/**
 * The turning earbuds from the island clip, as 48 still frames around one full, seamless turn
 * (assets/spin/buds; background already keyed out). Drawing frames directly is smoother and
 * more dependable in an overlay window than a video player, and lets a finger spin them.
 */
object SpinFrames {
    private const val FOLDER = "spin/buds"
    @Volatile private var frames: List<ImageBitmap>? = null

    /** The first frame, decoded on the spot so there's never an empty space. */
    fun poster(context: Context): ImageBitmap? = frames?.firstOrNull() ?: decode(context, names(context).firstOrNull())

    fun cached(): List<ImageBitmap>? = frames

    fun load(context: Context): List<ImageBitmap> =
        frames ?: synchronized(this) { frames ?: names(context).mapNotNull { decode(context, it) }.also { frames = it } }

    /** Frees the frames (about 30 MB) once no overlay needs them. */
    fun release() { frames = null }

    private fun names(context: Context): List<String> =
        context.assets.list(FOLDER).orEmpty().filter { it.endsWith(".webp") }.sorted()

    private fun decode(context: Context, name: String?): ImageBitmap? = if (name == null) null else try {
        context.assets.open("$FOLDER/$name").use { BitmapFactory.decodeStream(it) }?.asImageBitmap()
    } catch (_: Exception) { null }
}

/**
 * Earbuds turning continuously, one revolution every [turnMillis]. With [interactive], a
 * sideways drag turns them and a flick spins them, then the idle turn picks up again. With
 * reduce motion on, they hold still. Between two frames the next fades in, so it's continuous.
 */
@Composable
fun PodsSpinner(
    modifier: Modifier = Modifier,
    turnMillis: Int = 8000,
    spinning: Boolean = true,
    interactive: Boolean = false,
) {
    val context = LocalContext.current
    val hostView = LocalView.current
    val haptics = remember(hostView) { GlintHaptics(hostView) }
    val reduceMotion = remember { GlintComfort.reduceMotion(context) }
    val poster = remember { SpinFrames.poster(context) }
    var frames by remember { mutableStateOf(SpinFrames.cached()) }
    LaunchedEffect(Unit) {
        if (frames == null) frames = withContext(Dispatchers.IO) { SpinFrames.load(context) }
    }
    val position = remember { Animatable(0f) }
    var lastTouch by remember { mutableLongStateOf(0L) }
    val scope = rememberCoroutineScope()
    val count = frames?.size ?: 48

    LaunchedEffect(spinning, reduceMotion, frames != null) {
        if (!spinning || !PodsSpinnerConfig.idleTurn || reduceMotion || frames == null) return@LaunchedEffect
        while (isActive) {
            val idle = System.currentTimeMillis() - lastTouch
            if (idle < 1500) { delay(1500 - idle); continue }
            if (position.isRunning) { delay(100); continue }
            // A quarter turn at a time, linear, so consecutive steps join without a hitch.
            position.animateTo(position.value + count / 4f, tween(turnMillis / 4, easing = LinearEasing))
        }
    }

    Box(
        modifier.then(
            if (!interactive) Modifier else Modifier.pointerInput(count) {
                val tracker = VelocityTracker()
                val pxPerFrame = size.width / (count / 2f) // full width = half a turn
                var lastTick = 0
                detectHorizontalDragGestures(
                    onDragStart = {
                        lastTouch = System.currentTimeMillis()
                        tracker.resetTracking()
                        scope.launch { position.stop() }
                        lastTick = floor(position.value / (count / 8f)).toInt()
                    },
                    onDragEnd = {
                        lastTouch = System.currentTimeMillis()
                        val v = (tracker.calculateVelocity().x / pxPerFrame).coerceIn(-count * 3f, count * 3f)
                        scope.launch { position.animateDecay(v, exponentialDecay(frictionMultiplier = 1.6f)) }
                    },
                    onDragCancel = { lastTouch = System.currentTimeMillis() },
                ) { change, dx ->
                    change.consume()
                    tracker.addPosition(change.uptimeMillis, change.position)
                    lastTouch = System.currentTimeMillis()
                    scope.launch { position.snapTo(position.value + dx / pxPerFrame) }
                    val tick = floor(position.value / (count / 8f)).toInt()
                    if (tick != lastTick) { lastTick = tick; haptics.tick() }
                }
            }
        )
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val list = frames
            if (list.isNullOrEmpty()) {
                poster?.let { drawFrame(it, 1f) }
                return@Canvas
            }
            val n = list.size
            val p = ((position.value % n) + n) % n
            val i = floor(p).toInt() % n
            val f = p - floor(p)
            drawFrame(list[i], 1f)
            if (f > 0.02f) drawFrame(list[(i + 1) % n], f)
        }
    }
}

/** Fit the frame inside the canvas, centred, keeping its shape. */
private fun DrawScope.drawFrame(image: ImageBitmap, alpha: Float) {
    val scale = min(size.width / image.width, size.height / image.height)
    val w = (image.width * scale).roundToInt()
    val h = (image.height * scale).roundToInt()
    drawImage(
        image,
        dstOffset = IntOffset(((size.width - w) / 2f).roundToInt(), ((size.height - h) / 2f).roundToInt()),
        dstSize = IntSize(w, h),
        alpha = alpha,
    )
}
