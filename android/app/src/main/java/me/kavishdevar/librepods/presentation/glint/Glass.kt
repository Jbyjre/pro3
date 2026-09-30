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

import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The Glint glass material. Five layers, per the liquid-glass recipe: (1) real system blur
 * of what's behind the window when the phone allows it, (2) a tint with a gentle top-to-bottom
 * falloff, (3) a specular bloom and glint that follow the light (tilt/touch), (4) a bright
 * rim that is strongest on the light-facing edge with a softer caustic on the opposite edge,
 * and (5) an elevation shadow drawn by the caller. When blur is off (battery saver, reduce
 * transparency, unsupported), the tint becomes nearly opaque so text stays legible anywhere.
 */
@Immutable
data class GlassLook(
    val fillTop: Color,
    val fillBottom: Color,
    val opaqueTop: Color,
    val opaqueBottom: Color,
    val blurTint: Color,
    val blurRadiusPx: Int,
    val sheen: Float,
    val specular: Float,
    val rim: Float,
    val caustic: Float,
    val edge: Color,
    val content: Color,
    val contentSecondary: Color,
    val dark: Boolean,
)

object GlassLooks {
    /** Smoked glass for the island: reads like the Dynamic Island but lets light through. */
    fun island(density: Float) = GlassLook(
        fillTop = Color(0xB8121216),
        fillBottom = Color(0xD0070709),
        opaqueTop = Color(0xF7141418),
        opaqueBottom = Color(0xFA08080A),
        blurTint = Color(0x66000000),
        blurRadiusPx = (40 * density).roundToInt(),
        sheen = 0.07f,
        specular = 0.07f,
        rim = 0.38f,
        caustic = 0.14f,
        edge = Color(0x14FFFFFF),
        content = Color(0xFFF5F5F7),
        contentSecondary = Color(0x99EBEBF5),
        dark = true,
    )

    /** The connection card; follows the system light/dark theme. */
    fun card(dark: Boolean, density: Float) = if (dark) GlassLook(
        // Close to the connect clip's night backdrop (#1B1B1B) so the clip melts into the glass.
        fillTop = Color(0xC21E1E20),
        fillBottom = Color(0xD0161618),
        opaqueTop = Color(0xFA1D1D1E),
        opaqueBottom = Color(0xFC161617),
        blurTint = Color(0x40000000),
        blurRadiusPx = (56 * density).roundToInt(),
        sheen = 0.09f,
        specular = 0.09f,
        rim = 0.46f,
        caustic = 0.18f,
        edge = Color(0x1FFFFFFF),
        content = Color(0xFFF5F5F7),
        contentSecondary = Color(0x99EBEBF5),
        dark = true,
    ) else GlassLook(
        // Close to the connect clip's white backdrop so the clip melts into the glass.
        fillTop = Color(0xC7FFFFFF),
        fillBottom = Color(0xD2F6F6F9),
        opaqueTop = Color(0xFAFFFFFF),
        opaqueBottom = Color(0xFCF2F2F6),
        blurTint = Color(0x33FFFFFF),
        blurRadiusPx = (56 * density).roundToInt(),
        sheen = 0.45f,
        specular = 0.55f,
        rim = 0.95f,
        caustic = 0.45f,
        edge = Color(0x1A000000),
        content = Color(0xFF1C1C1E),
        contentSecondary = Color(0x993C3C43),
        dark = false,
    )
}

fun roundRectPath(rect: Rect, radius: Float): Path = Path().apply {
    addRoundRect(RoundRect(rect, CornerRadius(radius.coerceAtMost(rect.minDimension / 2f))))
}

/**
 * Asks the system to blur what's behind the window inside [rect] (rounded by [cornerRadius]).
 * Must be drawn before anything else in that area: it clears our own pixels there so the
 * blurred backdrop shows through, which also keeps shadows from darkening the glass.
 */
fun DrawScope.drawSystemBlur(blur: SystemBlur, rect: Rect, cornerRadius: Float, look: GlassLook, alpha: Float = 1f) {
    if (rect.width < 1f || rect.height < 1f) return
    blur.update(look.blurRadiusPx, cornerRadius, look.blurTint.copy(alpha = look.blurTint.alpha * alpha).toArgb())
    drawIntoCanvas {
        blur.draw(it.nativeCanvas, rect.left.roundToInt(), rect.top.roundToInt(), rect.width.roundToInt(), rect.height.roundToInt())
    }
}

/**
 * Paints glass inside [outline] whose bounding box is [bounds]. [cornerRadius] is used for the
 * system blur region (the platform blur supports rounded rectangles). [light] is -1..1 per axis.
 */
fun DrawScope.drawGlass(
    outline: Path,
    bounds: Rect,
    cornerRadius: Float,
    look: GlassLook,
    light: Offset,
    blur: SystemBlur?,
    touch: Offset? = null,
    touchStrength: Float = 0f,
    alpha: Float = 1f,
    /** True when the caller already drew blur regions for this shape (multi-part shapes). */
    blurredElsewhere: Boolean = false,
) {
    if (bounds.width <= 1f || bounds.height <= 1f || alpha <= 0.001f) return
    if (blur != null) drawSystemBlur(blur, bounds, cornerRadius, look, alpha)
    val blurring = blur != null || blurredElsewhere
    val top = if (blurring) look.fillTop else look.opaqueTop
    val bottom = if (blurring) look.fillBottom else look.opaqueBottom
    drawPath(outline, Brush.verticalGradient(listOf(top, bottom), bounds.top, bounds.bottom), alpha = alpha)

    clipPath(outline) {
        // Thick-glass top sheen.
        drawRect(
            Brush.verticalGradient(
                listOf(Color.White.copy(alpha = look.sheen), Color.White.copy(alpha = 0f)),
                bounds.top, bounds.top + bounds.height * 0.55f
            ),
            topLeft = bounds.topLeft, size = bounds.size, alpha = alpha
        )
        // Specular: a soft band hugging the top edge that slides with the light. Kept flat
        // and wide so on dark glass it reads as a reflection, not a smudge.
        val cx = bounds.center.x - light.x * bounds.width * 0.3f
        val bandH = (bounds.height * 0.42f).coerceAtMost(64f * density)
        val cy = bounds.top + bandH * (0.18f - light.y * 0.25f)
        val rx = max(bounds.width * 0.46f, 24f)
        // Squash a circular gradient into the band so it fades out on every side.
        withTransform({ scale(1f, bandH / (2f * rx), pivot = Offset(cx, cy)) }) {
            drawCircle(
                Brush.radialGradient(
                    listOf(Color.White.copy(alpha = look.specular), Color.White.copy(alpha = 0f)),
                    center = Offset(cx, cy), radius = rx
                ),
                radius = rx, center = Offset(cx, cy), alpha = alpha
            )
        }
        if (touch != null && touchStrength > 0f) {
            drawCircle(
                Brush.radialGradient(
                    listOf(Color.White.copy(alpha = 0.22f * touchStrength), Color.White.copy(alpha = 0f)),
                    center = touch, radius = bounds.maxDimension * 0.6f
                ),
                radius = bounds.maxDimension * 0.6f, center = touch, blendMode = BlendMode.Plus, alpha = alpha
            )
        }
        // A whisper of inner shadow at the bottom for thickness.
        drawRect(
            Brush.verticalGradient(
                listOf(Color.Transparent, Color.Black.copy(alpha = if (look.dark) 0.18f else 0.05f)),
                bounds.bottom - bounds.height * 0.3f, bounds.bottom
            ),
            topLeft = bounds.topLeft, size = bounds.size, alpha = alpha
        )
    }

    // Rim: light-facing edge bright, far edge a softer caustic.
    val stroke = max(1.2f * density, 1f)
    val lx = bounds.center.x - light.x * bounds.width * 0.5f - bounds.width * 0.25f
    val ly = bounds.top - light.y * bounds.height * 0.3f
    drawPath(
        outline,
        Brush.linearGradient(
            0f to Color.White.copy(alpha = look.rim),
            0.42f to Color.White.copy(alpha = look.rim * 0.12f),
            0.62f to Color.White.copy(alpha = look.rim * 0.08f),
            1f to Color.White.copy(alpha = look.caustic),
            start = Offset(lx, ly),
            end = Offset(bounds.right - (lx - bounds.left) * 0.2f, bounds.bottom),
        ),
        style = Stroke(stroke),
        alpha = alpha
    )
    drawPath(outline, look.edge, style = Stroke(stroke * 0.6f), alpha = alpha)
}

/**
 * Two-layer elevation shadow drawn straight onto the canvas, for shapes whose bounds animate
 * every frame. Where the system blur is active it clears these pixels inside the shape, so
 * the shadow only shows around the edges; without blur the near-opaque fill covers it.
 */
fun DrawScope.drawFloatingShadow(rect: Rect, cornerRadius: Float, fill: Color, dark: Boolean, strength: Float) {
    if (strength <= 0.001f) return
    val k = (if (dark) 1.35f else 1f) * strength.coerceIn(0f, 1f)
    drawIntoCanvas { canvas ->
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = fill.copy(alpha = 1f).toArgb() }
        val native = canvas.nativeCanvas
        paint.setShadowLayer(30f * density, 0f, 12f * density, Color.Black.copy(alpha = 0.22f * k).toArgb())
        native.drawRoundRect(rect.left, rect.top, rect.right, rect.bottom, cornerRadius, cornerRadius, paint)
        paint.setShadowLayer(3f * density, 0f, 1.5f * density, Color.Black.copy(alpha = 0.12f * k).toArgb())
        native.drawRoundRect(rect.left, rect.top, rect.right, rect.bottom, cornerRadius, cornerRadius, paint)
    }
}

/**
 * Two-layer elevation shadow (tight contact + wide ambient) for a small floating surface.
 * Inside the shape the system blur region clears it, so it only shows around the edges.
 */
fun Modifier.glassShadow(shape: Shape, dark: Boolean, strength: Float = 1f): Modifier {
    val k = (if (dark) 1.3f else 1f) * strength.coerceIn(0f, 1f)
    if (k <= 0f) return this
    return this
        .dropShadow(shape, Shadow(radius = 34.dp, color = Color.Black, offset = DpOffset(0.dp, 14.dp), alpha = 0.24f * k))
        .dropShadow(shape, Shadow(radius = 4.dp, color = Color.Black, offset = DpOffset(0.dp, 2.dp), alpha = 0.14f * k))
}
