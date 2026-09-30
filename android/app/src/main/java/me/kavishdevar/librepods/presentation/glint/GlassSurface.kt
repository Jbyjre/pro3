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

import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle

/**
 * How high a glass surface floats. Blur, refraction and shadow grow with it, except that
 * wide anchored bars stay soft (a big dark shadow would make them look like they are falling).
 */
enum class GlassTier(val blur: Dp, val refraction: Dp, val shadow: Float) {
    /** Inline controls: a segmented control, a chip. */
    Inline(10.dp, 12.dp, 0.35f),
    /** Sheets and cards. */
    Card(20.dp, 22.dp, 0.8f),
    /** Small, freely floating buttons: the strongest shadow for their size. */
    Floating(24.dp, 18.dp, 1f),
}

/**
 * Glint's one glass material, used everywhere a control floats: backdrop blur + vibrancy,
 * real GPU edge refraction with a slight chromatic edge (Kyant backdrop, Android 13+), a tint
 * that keeps text legible, the rim highlight, and a two-layer shadow.
 *
 * Accessibility: with "reduce transparency" (Glint's switch, or Android's high-contrast
 * text) the surface turns solid, drops blur and refraction, and gets a visible 1dp border.
 */
fun Modifier.glintGlass(
    backdrop: Backdrop,
    dark: Boolean,
    solid: Boolean,
    shape: Shape = RoundedCornerShape(28.dp),
    tier: GlassTier = GlassTier.Card,
    tint: Color = glassTint(dark, solid),
): Modifier = this
    .glassShadow(shape, dark, tier.shadow)
    .drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            if (!solid) {
                vibrancy()
                blur(tier.blur.toPx())
                lens(
                    refractionHeight = tier.refraction.toPx(),
                    refractionAmount = (tier.refraction * 1.8f).toPx(),
                    depthEffect = true,
                    chromaticAberration = true
                )
            }
        },
        highlight = { GlintLight.rim(if (solid) 0f else if (dark) 0.6f else 0.9f) },
        onDrawSurface = { drawRect(tint) }
    )
    .then(if (solid) Modifier.border(1.dp, if (dark) Color.White.copy(alpha = 0.55f) else Color.Black.copy(alpha = 0.45f), shape) else Modifier)

/**
 * The rim light for every Kyant glass surface. The library's built-in highlights light the
 * edge from a 45-degree diagonal (bright top-left, dark bottom-right), which made buttons and
 * sheets look tilted. Glint lights them from straight above instead: a bright top edge, a
 * matching catch-light along the bottom, and quiet sides, the same on every shape.
 */
object GlintLight {
    private val overhead = HighlightStyle.Default(angle = 90f, falloff = 2.2f)
    fun rim(alpha: Float = 1f): Highlight = Highlight.Default.copy(alpha = alpha.coerceIn(0f, 1f), style = overhead)
}

fun glassTint(dark: Boolean, solid: Boolean): Color = when {
    solid -> if (dark) Color(0xFF1C1C1E) else Color(0xFFF7F7F9)
    dark -> Color(0xFF1C1C1E).copy(alpha = 0.55f)
    else -> Color.White.copy(alpha = 0.55f)
}

val CapsuleShape: Shape = RoundedCornerShape(percent = 50)

/**
 * The studio backdrop behind setup: a seamless sweep with one overhead
 * key light, and a faint trace of the status light's green low in the frame for glass edges
 * to pick up.
 */
fun DrawScope.drawStudio(dark: Boolean, keyY: Float = 0.22f, glowY: Float = 0.62f) {
    drawRect(
        Brush.verticalGradient(
            if (dark) listOf(Color(0xFF26272B), Color(0xFF121214), Color(0xFF08080A))
            else listOf(Color(0xFFFFFFFF), Color(0xFFF0F1F4), Color(0xFFDCDEE4))
        )
    )
    val key = Offset(size.width / 2f, size.height * keyY)
    drawCircle(
        Brush.radialGradient(listOf(Color.White.copy(alpha = if (dark) 0.10f else 0.75f), Color.Transparent), key, size.width * 0.9f),
        radius = size.width * 0.9f, center = key
    )
    val glow = Offset(size.width * 0.5f, size.height * glowY)
    drawCircle(
        Brush.radialGradient(listOf(Color(0xFF30D158).copy(alpha = if (dark) 0.16f else 0.10f), Color.Transparent), glow, size.width * 0.55f),
        radius = size.width * 0.55f, center = glow
    )
}
