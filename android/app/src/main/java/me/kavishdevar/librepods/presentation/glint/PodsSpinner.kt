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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
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

/**
 * The three ways to look at the AirPods, all from LibrePods' own rendered turntable clips.
 * Frames were taken from the clips once, evenly around one full turn, and their studio
 * backgrounds keyed out so the AirPods stand on whatever is behind them.
 */
enum class SpinView(
    val folder: String,
    val label: String,
    val aspect: Float,
    val loops: Boolean,
    /** Largest size before the source clip (418 px for the earbuds) would look soft. */
    val maxWidthDp: Int,
    /** Where the AirPods meet the floor, as a fraction of the frame height. */
    val floor: Float,
) {
    /** A complete, seamless turn: spins freely. */
    Buds("buds", "Earbuds", 1f, loops = true, maxWidthDp = 300, floor = 0.84f),
    /** The case clip covers most of a turn but doesn't close the circle: it turns end to end. */
    Case("case", "Case", 530f / 354f, loops = false, maxWidthDp = 330, floor = 0.95f),
    Together("both", "Together", 840f / 283f, loops = false, maxWidthDp = 520, floor = 0.93f),
}

/** Screenshot tests switch the idle turn off so the screen can settle. */
object PodsSpinnerConfig {
    @Volatile var idleTurn = true
}

/** Loads a view's frames from assets. Small (about 20 KB each), decoded once per view. */
object SpinFrames {
    private val cache = HashMap<SpinView, List<ImageBitmap>>()

    fun names(context: Context, view: SpinView): List<String> =
        context.assets.list("spin/${view.folder}").orEmpty().filter { it.endsWith(".webp") }.sorted()

    fun decode(context: Context, view: SpinView, name: String): ImageBitmap? = try {
        context.assets.open("spin/${view.folder}/$name").use { BitmapFactory.decodeStream(it) }?.asImageBitmap()
    } catch (_: Exception) { null }

    fun cached(view: SpinView): List<ImageBitmap>? = synchronized(cache) { cache[view] }

    fun loadAll(context: Context, view: SpinView): List<ImageBitmap> {
        cached(view)?.let { return it }
        val frames = names(context, view).mapNotNull { decode(context, view, it) }
        // Keep only the current view in memory (a view is about 35 MB decoded).
        synchronized(cache) { cache.keys.removeAll { it != view }; cache[view] = frames }
        return frames
    }
}

/**
 * Drag sideways to turn the AirPods, flick to spin them; they ease to a stop. With
 * [autoRotate], they turn slowly by themselves until touched, then again after a pause.
 * Between two stored frames the next one fades in, so turning looks continuous.
 */
@Composable
fun PodsSpinner(
    view: SpinView,
    modifier: Modifier = Modifier,
    autoRotate: Boolean = true,
    interactive: Boolean = true,
    dark: Boolean = false,
) {
    val context = LocalContext.current
    val hostView = LocalView.current
    val haptics = remember(hostView) { GlintHaptics(hostView) }
    val reduceMotion = remember { GlintComfort.reduceMotion(context) }
    val poster = remember(view) {
        SpinFrames.cached(view)?.firstOrNull() ?: SpinFrames.names(context, view).firstOrNull()?.let { SpinFrames.decode(context, view, it) }
    }
    var frames by remember(view) { mutableStateOf(SpinFrames.cached(view)) }
    LaunchedEffect(view) {
        if (frames == null) frames = withContext(Dispatchers.IO) { SpinFrames.loadAll(context, view) }
    }
    val position = remember(view) { Animatable(0f) }
    var lastTouch by remember { mutableLongStateOf(0L) }
    val scope = rememberCoroutineScope()
    val count = frames?.size ?: 48

    val last = (count - 1).toFloat()
    fun clampIfNeeded(v: Float) = if (view.loops) v else v.coerceIn(0f, last)

    // Slow idle turn (one revolution in 14 s; end to end and back for the case clips),
    // paused for 4 s after any touch.
    LaunchedEffect(view, autoRotate, reduceMotion, frames != null) {
        if (!autoRotate || !PodsSpinnerConfig.idleTurn || reduceMotion || frames == null) return@LaunchedEffect
        var forward = true
        while (isActive) {
            val idle = System.currentTimeMillis() - lastTouch
            if (idle < 4000) { delay(4000 - idle); continue }
            if (position.isRunning) { delay(200); continue }
            if (view.loops) {
                position.animateTo(position.value + count / 4f, tween(3500, easing = LinearEasing))
            } else {
                val target = if (forward) last else 0f
                val ms = (kotlin.math.abs(target - position.value) / count * 14000).roundToInt()
                if (ms > 50) position.animateTo(target, tween(ms, easing = androidx.compose.animation.core.FastOutSlowInEasing))
                forward = !forward
            }
        }
    }

    val turn: (Float) -> Unit = { frames ->
        lastTouch = System.currentTimeMillis()
        scope.launch { position.animateTo(clampIfNeeded(position.value + frames), tween(if (reduceMotion) 0 else 450)) }
    }

    Box(
        modifier
            .semantics {
                contentDescription = "AirPods ${view.label.lowercase()}, 3D view. Drag sideways to turn."
                customActions = listOf(
                    CustomAccessibilityAction("Turn left") { turn(-count / 8f); true },
                    CustomAccessibilityAction("Turn right") { turn(count / 8f); true },
                )
            }
            .then(
                if (!interactive) Modifier else Modifier.pointerInput(view, count) {
                    val tracker = VelocityTracker()
                    // Dragging the full width turns the AirPods half way round.
                    val pxPerFrame = size.width / (count / 2f)
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
                            scope.launch {
                                if (view.loops) {
                                    position.animateDecay(v, exponentialDecay(frictionMultiplier = 1.6f))
                                } else if (position.value < 0f || position.value > last) {
                                    // Pulled past the end of the clip: settle back.
                                    position.animateTo(position.value.coerceIn(0f, last), androidx.compose.animation.core.spring(0.8f, 400f))
                                } else {
                                    position.updateBounds(0f, last)
                                    position.animateDecay(v, exponentialDecay(frictionMultiplier = 1.6f))
                                    position.updateBounds(null, null)
                                }
                            }
                        },
                        onDragCancel = {
                            lastTouch = System.currentTimeMillis()
                            scope.launch { position.animateTo(clampIfNeeded(position.value)) }
                        },
                    ) { change, dx ->
                        change.consume()
                        tracker.addPosition(change.uptimeMillis, change.position)
                        lastTouch = System.currentTimeMillis()
                        // Past either end of a clip that doesn't loop, it resists like a rubber band.
                        val outside = !view.loops && (position.value < 0f || position.value > last)
                        scope.launch { position.snapTo(position.value + dx / pxPerFrame * (if (outside) 0.3f else 1f)) }
                        // A light tick every eighth of a turn.
                        val tick = floor(position.value / (count / 8f)).toInt()
                        if (tick != lastTick) { lastTick = tick; haptics.tick() }
                    }
                }
            )
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawContactShadow(dark, view.floor)
            val list = frames
            if (list.isNullOrEmpty()) {
                poster?.let { drawFrame(it, 1f) }
                return@Canvas
            }
            val n = list.size
            val p = if (view.loops) ((position.value % n) + n) % n else position.value.coerceIn(0f, (n - 1).toFloat())
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

/** A soft pool of shadow under the AirPods so they sit on the surface instead of floating. */
private fun DrawScope.drawContactShadow(dark: Boolean, floor: Float) {
    val c = Offset(size.width / 2f, size.height * (floor + 0.02f))
    val r = size.width * 0.38f
    scale(1f, 0.12f, c) {
        drawCircle(
            Brush.radialGradient(listOf(Color.Black.copy(alpha = if (dark) 0.55f else 0.22f), Color.Transparent), c, r),
            radius = r, center = c
        )
    }
}

private inline fun DrawScope.scale(sx: Float, sy: Float, pivot: Offset, block: DrawScope.() -> Unit) =
    drawContext.transform.let {
        drawContext.canvas.save()
        it.scale(sx, sy, pivot)
        block()
        drawContext.canvas.restore()
    }
