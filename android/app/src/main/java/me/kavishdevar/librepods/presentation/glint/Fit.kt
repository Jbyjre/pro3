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

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * How content fits the islands (Jake, 2026-10-11: "inconsistent padding, text truncation and
 * poorly aligned buttons"). The rules, everywhere in the islands:
 * - One line of words never wraps or spills: it ends in "…", or, where it's the song's name, it
 *   scrolls once and its ends fade softly instead of being cut ([scrollingLine]).
 * - A row of buttons shares the width evenly and its buttons shrink together on a narrow phone
 *   (glance controls), never pushing the last one off the edge ([FitRules.buttonSize]).
 * - System text sizes are followed up to 115% inside the islands (CappedFontScale), so the shapes
 *   never have to grow past Apple's sizes.
 */

/** Fades the content's left and right [fade] into nothing (the content is drawn into its own layer). */
fun Modifier.edgeFade(fade: Dp = 10.dp, left: Boolean = true, right: Boolean = true): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val f = (fade.toPx() / size.width.coerceAtLeast(1f)).coerceIn(0f, 0.45f)
        drawRect(
            Brush.horizontalGradient(
                0f to (if (left) Color.Transparent else Color.Black),
                f to Color.Black,
                1f - f to Color.Black,
                1f to (if (right) Color.Transparent else Color.Black),
            ),
            blendMode = BlendMode.DstIn,
        )
    }

/**
 * A line that scrolls once when it's too long, with soft ends, and sits exactly where a plain line
 * would at rest: the fade on the left lives just outside the line's own space, so the first letter
 * is never dimmed while it's still.
 */
fun Modifier.scrollingLine(enabled: Boolean, fade: Dp = 10.dp, delayMs: Int = 1_400): Modifier {
    if (!enabled) return this
    return this
        .layout { measurable, c ->
            val extra = fade.roundToPx()
            val max = if (c.hasBoundedWidth) c.maxWidth + extra else c.maxWidth
            val p = measurable.measure(c.copy(minWidth = minOf(c.minWidth + extra, max), maxWidth = max))
            layout((p.width - extra).coerceAtLeast(0), p.height) { p.place(-extra, 0) }
        }
        .edgeFade(fade)
        .padding(start = fade)
        .basicMarquee(iterations = 1, initialDelayMillis = delayMs)
}

/** Pure sizing rules for content in the islands, unit-tested. */
object FitRules {
    /** A round button's size in a slot [slotDp] wide: its usual [usualDp], smaller when the slot is narrower. */
    fun buttonSize(slotDp: Float, usualDp: Float = 46f, minDp: Float = 34f): Float =
        (slotDp - 6f).coerceIn(minDp, usualDp)
}
