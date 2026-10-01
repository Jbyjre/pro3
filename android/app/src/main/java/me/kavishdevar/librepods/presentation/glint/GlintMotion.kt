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

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * When a screen opens, its cards rise and fade in one after another. [GlintEnter.screenOpened]
 * marks the moment (NavigationRoot calls it on every screen change); only content composed
 * within a short window after that animates, so scrolling a long list later doesn't make
 * rows jump in.
 */
object GlintEnter {
    private const val WINDOW_MS = 700L
    @Volatile private var openedAt = 0L
    @Volatile private var next = 0

    fun screenOpened() {
        openedAt = SystemClock.uptimeMillis()
        next = 0
    }

    /** The stagger slot for a newly composed card, or null when it shouldn't animate. */
    internal fun claim(): Int? {
        if (SystemClock.uptimeMillis() - openedAt > WINDOW_MS) return null
        return next++
    }
}

/**
 * Rise in: fade, a short lift and a slight scale, on a soft spring, staggered by [index]
 * (or automatically by order on screen when null). Instant with Reduce motion.
 */
@Composable
fun Modifier.riseIn(index: Int? = null): Modifier {
    val context = LocalContext.current
    val reduce = remember { GlintComfort.reduceMotion(context) }
    val slot = remember { if (reduce) null else GlintEnter.claim()?.let { index ?: it } }
    val p = remember { Animatable(if (slot == null) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (slot == null) return@LaunchedEffect
        kotlinx.coroutines.delay(40L * slot.coerceAtMost(8))
        p.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 220f))
    }
    return this.graphicsLayer {
        val v = p.value
        alpha = v.coerceIn(0f, 1f)
        translationY = (1f - v) * 28.dp.toPx()
        val s = 0.97f + 0.03f * v
        scaleX = s; scaleY = s
    }
}

/** Clickable that squishes slightly under the finger and springs back, like Apple's glass. */
@Composable
fun Modifier.pressable(onClick: () -> Unit): Modifier {
    val context = LocalContext.current
    val reduce = remember { GlintComfort.reduceMotion(context) }
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed && !reduce) 0.965f else 1f, spring(dampingRatio = 0.5f, stiffness = 700f), label = "press")
    return this
        .graphicsLayer { scaleX = s; scaleY = s }
        .clickable(interactionSource = source, indication = LocalIndication.current, onClick = onClick)
}

/**
 * Light under the finger: while a row is pressed, a soft glow blooms from the touch point
 * and fades when you let go, like Apple's interactive glass lighting up where you touch.
 * Watches touches without consuming them, so the row's own tap handling is unchanged.
 * Off with Reduce motion.
 */
@Composable
fun Modifier.touchGlow(color: Color, enabled: Boolean = true): Modifier {
    val context = LocalContext.current
    val reduce = remember { GlintComfort.reduceMotion(context) }
    if (reduce || !enabled) return this
    var at by remember { mutableStateOf(Offset.Unspecified) }
    var down by remember { mutableStateOf(false) }
    val glow by animateFloatAsState(if (down) 1f else 0f, if (down) spring(0.9f, 900f) else tween(360), label = "glow")
    return this
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val e = awaitPointerEvent(PointerEventPass.Initial)
                    val c = e.changes.firstOrNull() ?: continue
                    when {
                        c.pressed && !c.previousPressed -> { at = c.position; down = true }
                        c.pressed -> at = c.position
                        else -> down = false
                    }
                }
            }
        }
        .drawWithContent {
            drawContent()
            if (glow > 0.01f && at != Offset.Unspecified) {
                val r = size.width * (0.30f + 0.15f * glow)
                drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.16f * glow), Color.Transparent), at, r), r, at)
            }
        }
}
