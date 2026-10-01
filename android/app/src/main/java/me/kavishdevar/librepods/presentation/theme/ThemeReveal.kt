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
package me.kavishdevar.librepods.presentation.theme

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import me.kavishdevar.librepods.presentation.glint.GlintComfort

/**
 * Switching between light and dark: instead of a hard cut, the new look spreads out in a
 * circle from where you tapped, with a soft glowing edge, like light filling a room.
 * [change] is what the appearance picker calls; it falls back to a plain switch when no
 * [ThemeRevealHost] is on screen, and with Reduce motion.
 */
object ThemeReveal {
    @Volatile internal var origin: Offset? = null
    @Volatile internal var request: ((String) -> Unit)? = null

    /** Changes the appearance to [value], revealing it from [from] (window coordinates). */
    fun change(context: Context, value: String, from: Offset? = null) {
        origin = from
        request?.invoke(value) ?: GlintAppearance.set(context, value)
    }
}

/** Wraps the app so an appearance change can be revealed from the tap point. */
@Composable
fun ThemeRevealHost(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val layer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()
    var snapshot by remember { mutableStateOf<ImageBitmap?>(null) }
    var center by remember { mutableStateOf(Offset.Unspecified) }
    var hostOffset by remember { mutableStateOf(Offset.Zero) }
    val progress = remember { Animatable(1f) }
    val reduce = remember { GlintComfort.reduceMotion(context) }
    DisposableEffect(Unit) {
        ThemeReveal.request = { value ->
            scope.launch {
                val wasDark = GlintAppearance.isDark(context)
                // A picture of the screen as it is now, peeled away as the new look spreads.
                val shot = if (reduce || snapshot != null) null else runCatching { layer.toImageBitmap() }.getOrNull()
                GlintAppearance.set(context, value)
                if (shot == null || GlintAppearance.isDark(context) == wasDark) return@launch
                center = ThemeReveal.origin?.let { it - hostOffset } ?: Offset.Unspecified
                snapshot = shot
                progress.snapTo(0f)
                progress.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
                snapshot = null
            }
        }
        onDispose { ThemeReveal.request = null }
    }
    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { hostOffset = it.positionInWindow() }
            .drawWithContent {
                layer.record { this@drawWithContent.drawContent() }
                drawLayer(layer)
                val s = snapshot ?: return@drawWithContent
                val c = if (center == Offset.Unspecified) Offset(size.width / 2f, size.height / 2f) else center
                val far = listOf(Offset.Zero, Offset(size.width, 0f), Offset(0f, size.height), Offset(size.width, size.height))
                    .maxOf { (it - c).getDistance() }
                val r = far * progress.value
                val hole = Path().apply { addOval(Rect(c, r)) }
                clipPath(hole, ClipOp.Difference) { drawImage(s) }
                // The glowing edge of the reveal, fading as it reaches the corners.
                val edge = (1f - progress.value).coerceIn(0f, 1f)
                if (r > 1f) drawCircle(
                    Brush.radialGradient(
                        0.82f to Color.Transparent,
                        0.97f to Color.White.copy(alpha = 0.45f * edge),
                        1f to Color.Transparent,
                        center = c, radius = r
                    ),
                    radius = r, center = c
                )
            }
    ) { content() }
}
