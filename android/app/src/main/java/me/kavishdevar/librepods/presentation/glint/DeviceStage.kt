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
package me.kavishdevar.librepods.presentation.glint

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch

/**
 * A stage for the AirPods and headphones pictures (Jake, 2026-10-11: they looked flat): the
 * device turns in real 3D perspective with the phone's tilt (the same light as all of pro's
 * glass), a highlight slides across it as the light moves, a soft two-part shadow grounds it
 * (a tight contact shadow and a wider ambient one, the Liquid Glass depth recipe), and it springs
 * into place when it appears. Where it's safe ([draggable], never inside the islands, whose drags
 * are gestures) a finger can turn it, and it springs back.
 *
 * The pictures themselves stay LibrePods' own AirPods renders and clips (Jake's choice); nothing
 * here replaces them. With Reduce motion it holds still.
 */
@Composable
fun DeviceStage(
    modifier: Modifier = Modifier,
    /** Springs in when it first appears. */
    enter: Boolean = true,
    /** A finger can turn it (app pages only). */
    draggable: Boolean = false,
    /** The shadow underneath (off where the picture already sits on a surface). */
    shadow: Boolean = true,
    /** How dark the shadow is (lighter on dark backgrounds, where it barely shows). */
    shadowStrength: Float = 1f,
    /** The sliding highlight (off for a clip with its own backdrop, where it would light the backdrop too). */
    sheen: Boolean = true,
    /** Turns with the tilt (off for a real 3D model, which turns itself). */
    turn: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val context = LocalContext.current
    val reduce = remember { GlintComfort.reduceMotion(context) }
    val arrive = remember { Animatable(if (enter && !reduce) 0f else 1f) }
    LaunchedEffect(Unit) { if (arrive.value < 1f) arrive.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = 240f)) }
    val spinX = remember { Animatable(0f) }
    val spinY = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    Box(modifier) {
        if (shadow) Canvas(Modifier.matchParentSize()) {
            val a = arrive.value.coerceIn(0f, 1f)
            val tilt = if (reduce) Offset.Zero else GlassLight.tilt.value
            // The shadow falls away from the light, and grows as the device settles down onto it.
            val cx = size.width / 2f - tilt.x * size.width * 0.04f
            val cy = size.height * 0.94f
            val w = size.width * 0.62f * (0.8f + 0.2f * a)
            val k = shadowStrength.coerceIn(0f, 1.5f) * a
            // Ambient: wide and soft.
            drawOval(
                Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.22f * k), Color.Transparent), Offset(cx, cy), w / 2f),
                Offset(cx - w / 2f, cy - w * 0.09f), Size(w, w * 0.18f),
            )
            // Contact: tight and a little darker, right under it.
            val cw = w * 0.55f
            drawOval(
                Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.28f * k), Color.Transparent), Offset(cx, cy), cw / 2f),
                Offset(cx - cw / 2f, cy - cw * 0.06f), Size(cw, cw * 0.12f),
            )
        }
        // The device itself sizes the stage; the shadow fills whatever it takes.
        Box(
            Modifier
                .then(
                    if (draggable && !reduce) Modifier.pointerInput(Unit) {
                        detectDragGestures(
                            onDragEnd = { scope.launch { spinY.animateTo(0f, spring(0.5f, 180f)) }; scope.launch { spinX.animateTo(0f, spring(0.5f, 180f)) } },
                            onDragCancel = { scope.launch { spinY.animateTo(0f, spring(0.5f, 180f)) }; scope.launch { spinX.animateTo(0f, spring(0.5f, 180f)) } },
                        ) { change, drag ->
                            change.consume()
                            scope.launch { spinY.snapTo((spinY.value + drag.x * 0.25f).coerceIn(-35f, 35f)) }
                            scope.launch { spinX.snapTo((spinX.value - drag.y * 0.18f).coerceIn(-18f, 18f)) }
                        }
                    } else Modifier
                )
                .graphicsLayer {
                    val a = arrive.value
                    val tilt = if (reduce) Offset.Zero else GlassLight.tilt.value
                    cameraDistance = 14f * density
                    // Turns with the tilt (a few degrees, like an object on a table), plus a finger's spin,
                    // and on arrival swings in from a quarter turn away.
                    if (turn) {
                        rotationY = tilt.x * DeviceStageSpec.TILT_Y_DEG + spinY.value + (1f - a) * DeviceStageSpec.ENTER_TURN_DEG
                        rotationX = (tilt.y - GlassLight.REST_Y) * DeviceStageSpec.TILT_X_DEG + spinX.value
                    }
                    val sc = 0.86f + 0.14f * a
                    scaleX = sc; scaleY = sc
                    translationY = (1f - a) * 18f * density
                    alpha = a.coerceIn(0f, 1f)
                    compositingStrategy = CompositingStrategy.Offscreen
                }
                .drawWithContent {
                    drawContent()
                    if (!sheen) return@drawWithContent
                    // The light on its surface: a soft band that slides with the tilt (and a turn),
                    // drawn only where the device is (SrcAtop), never on the background around it.
                    val tilt = if (reduce) Offset.Zero else GlassLight.tilt.value
                    val turn = (spinY.value / 35f).coerceIn(-1f, 1f)
                    val pos = (0.38f - tilt.x * 0.3f - turn * 0.3f).coerceIn(-0.2f, 1.2f)
                    val x = size.width * pos
                    val y = size.height * (0.15f + (tilt.y - GlassLight.REST_Y) * 0.2f)
                    drawRect(
                        Brush.linearGradient(
                            listOf(Color.White.copy(alpha = 0f), Color.White.copy(alpha = DeviceStageSpec.SHEEN), Color.White.copy(alpha = 0f)),
                            Offset(x - size.width * 0.22f, y - size.height * 0.3f), Offset(x + size.width * 0.22f, y + size.height * 0.3f),
                        ),
                        blendMode = BlendMode.SrcAtop,
                    )
                },
            content = content,
        )
    }
}

/** The stage's numbers (by eye; kept small so it reads as an object, never a gimmick). */
object DeviceStageSpec {
    const val TILT_Y_DEG = 9f
    const val TILT_X_DEG = 6f
    const val ENTER_TURN_DEG = -16f
    const val SHEEN = 0.2f
}
