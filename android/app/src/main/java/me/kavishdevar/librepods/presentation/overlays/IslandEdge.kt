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
package me.kavishdevar.librepods.presentation.overlays

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import me.kavishdevar.librepods.presentation.glint.GlintLight
import kotlin.math.cos
import kotlin.math.sin

/**
 * The black island's edge, one way for the Dynamic Island and its pop-ups alike.
 *
 * - Key line (Apple's): a thin line in the content's own colour, only on a dark background.
 * - Liquid Glass: the black island as a piece of dark glass. A rim that catches the light from
 *   straight above (brightest along the top edge, faint down the sides, a softer catch along
 *   the bottom, like the skill's 0.65 / 0.05 / 0.35 rim), and a faint sheen inside the top. The
 *   light swings a little as the phone tilts, the same light as the rest of pro's glass
 *   ([GlintLight], level = straight overhead). The island stays opaque black: Android never lets
 *   an app see the pixels of other apps behind it, so there is no real refraction over them.
 */
internal fun DrawScope.drawIslandEdge(
    outline: Path,
    bounds: Rect,
    glass: Boolean,
    /** The line's strength (0..1 alpha) along its brightest part. */
    alpha: Float,
    /** The content's colour, for Apple's key line (white when there's none). */
    tint: Color,
    density: Float,
) {
    if (alpha <= 0.001f || bounds.width <= 1f || bounds.height <= 1f) return
    if (!glass) {
        drawPath(outline, tint.copy(alpha = alpha), style = Stroke(0.75f * density))
        return
    }
    val swing = Math.toRadians(GlintLight.swing.floatValue.toDouble()).toFloat()
    val c = bounds.center
    val reach = bounds.height / 2f + bounds.width * 0.08f
    // From the light (above, tilted with the phone) to the far side.
    val from = Offset(c.x - sin(swing) * reach, c.y - cos(swing) * reach)
    val to = Offset(c.x + sin(swing) * reach, c.y + cos(swing) * reach)
    drawPath(
        outline,
        Brush.linearGradient(
            0f to Color.White.copy(alpha = alpha),
            0.38f to Color.White.copy(alpha = alpha * 0.08f),
            0.62f to Color.White.copy(alpha = alpha * 0.08f),
            1f to Color.White.copy(alpha = alpha * 0.45f),
            start = from, end = to,
        ),
        style = Stroke(0.9f * density),
    )
    // A faint sheen just inside the top: the glass's thickness catching the same light.
    clipPath(outline) {
        val sheenH = minOf(bounds.height * 0.45f, 22f * density)
        drawRect(
            Brush.verticalGradient(
                listOf(Color.White.copy(alpha = alpha * 0.16f), Color.White.copy(alpha = 0f)),
                startY = bounds.top, endY = bounds.top + sheenH,
            ),
            topLeft = Offset(bounds.left, bounds.top), size = androidx.compose.ui.geometry.Size(bounds.width, sheenH),
        )
    }
}
