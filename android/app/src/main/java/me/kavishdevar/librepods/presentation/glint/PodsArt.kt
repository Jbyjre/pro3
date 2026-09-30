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

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser

/**
 * Original, code-drawn artwork of AirPods Pro 3 (buds and charging case). Nothing here is
 * copied from Apple's imagery: the silhouettes are hand-authored vector paths sized from the
 * published dimensions (bud 30.9 mm tall and 19.2 mm wide; case 62.2 x 47.2 mm, per MacRumors'
 * Pro 3 design overview), and all shading is procedural so it stays sharp at any size and can
 * react to state (charging, in-ear, lid) and to the light direction (device tilt or touch).
 *
 * The same path strings are used by the vector drawables in res/drawable (glint_pro3_*.xml),
 * which Android needs for widgets and system Bluetooth icons.
 */
object PodsGeometry {
    /** Left earbud seen from the front, ear tip pointing right. Viewport 100 x 160. */
    const val BUD_BODY =
        "M40,4 C60,4 74,20 74,44 C74,62 66,76 54,84 C51,86 49,90 49,96 L47,150 " +
            "C47,154 43,157 39.5,157 C36,157 32,154 32,150 L33,98 C33,90 29,86 22,81 " +
            "C11,73 6,60 6,44 C6,21 20,4 40,4 Z"
    const val BUD_TIP = "M66,24 C82,21 97,30 98,44 C99,58 85,66 67,64 C74,52 73,36 66,24 Z"
    const val BUD_TIP_MESH =
        "M86,44 C86,39.5 87.6,37.5 89.6,37.5 C91.6,37.5 93.2,39.5 93.2,44 C93.2,48.5 91.6,50.5 89.6,50.5 C87.6,50.5 86,48.5 86,44 Z"
    const val BUD_TOP_MESH = "M13,30.5 C13,28.4 17,25.6 19.6,26 C21.4,26.4 21,28 19.4,29.3 C17.4,30.9 13.5,32.2 13,30.5 Z"
    const val BUD_SENSOR =
        "M58.4,66 C58.4,64.8 59.3,63.9 60.4,63.9 C61.5,63.9 62.4,64.8 62.4,66 C62.4,67.2 61.5,68.1 60.4,68.1 C59.3,68.1 58.4,67.2 58.4,66 Z"
    const val BUD_STEM_MESH =
        "M35,153.6 C35,152.5 37,151.9 39.5,151.9 C42,151.9 44,152.5 44,153.6 " +
            "C44,154.7 42,155.3 39.5,155.3 C37,155.3 35,154.7 35,153.6 Z"
    const val BUD_W = 100f
    const val BUD_H = 160f
    const val STEM_TOP = 90f
    const val STEM_LEFT = 32f
    const val STEM_RIGHT = 49f

    /** Charging case, front, lid closed. Viewport 124 x 94 (62.2 x 47.2 mm at 2 units/mm). */
    const val CASE_BODY =
        "M30,0 L94,0 C112,0 124,10 124,30 L124,64 C124,84 112,94 94,94 L30,94 " +
            "C12,94 0,84 0,64 L0,30 C0,10 12,0 30,0 Z"
    const val CASE_W = 124f
    const val CASE_H = 94f
    const val CASE_SEAM_Y = 27.5f
}

@Immutable
data class BudVisual(
    val present: Boolean = true,
    val charging: Boolean = false,
    val inEar: Boolean = false,
)

@Immutable
data class CaseVisual(
    val present: Boolean = true,
    val charging: Boolean = false,
    /** 0..100 or null; drives the status light colour when it shows. */
    val level: Int? = null,
    val lidOpen: Boolean = false,
)

private val parsedPaths = HashMap<String, Path>()
private fun path(data: String): Path = parsedPaths.getOrPut(data) { PathParser().parsePathString(data).toPath() }

private val PlasticHighlight = Color(0xFFFFFFFF)
private val PlasticMid = Color(0xFFF2F2F5)
private val PlasticShade = Color(0xFFD9DAE0)
private val PlasticDeep = Color(0xFFBFC1C8)
private val TipLight = Color(0xFFF0F1F3)
private val TipShade = Color(0xFFCDD0D6)
private val Mesh = Color(0xFF2E2F33)

/**
 * Draws one earbud into the current DrawScope, scaled to fit [size]. [light] is the direction
 * the light comes from, each axis -1..1 (0,0 = straight ahead, slightly above).
 */
fun DrawScope.drawBud(size: Size, mirrored: Boolean, light: Offset, visual: BudVisual) {
    val s = minOf(size.width / PodsGeometry.BUD_W, size.height / PodsGeometry.BUD_H)
    val w = PodsGeometry.BUD_W * s
    val h = PodsGeometry.BUD_H * s
    translate((size.width - w) / 2f, (size.height - h) / 2f) {
        withTransform({
            if (mirrored) scale(-1f, 1f, pivot = Offset(w / 2f, h / 2f))
            scale(s, s, pivot = Offset.Zero)
        }) {
            // Mirror the light too, so the highlight stays on the same screen side.
            val lx = if (mirrored) -light.x else light.x
            val ly = light.y
            val body = path(PodsGeometry.BUD_BODY)
            val tip = path(PodsGeometry.BUD_TIP)

            // Soft contact shadow under the stem.
            drawOval(
                brush = Brush.radialGradient(
                    listOf(Color.Black.copy(alpha = 0.16f), Color.Transparent),
                    center = Offset(40f, 159f), radius = 20f
                ),
                topLeft = Offset(18f, 154f), size = Size(44f, 10f)
            )

            // Ear tip: matte silicone behind the body edge, lit from the same light.
            drawPath(
                tip,
                Brush.radialGradient(
                    listOf(TipLight, TipShade),
                    center = Offset(80f - lx * 6f, 36f - ly * 6f), radius = 34f
                )
            )
            clipPath(tip) {
                drawPath(tip, Color.Black.copy(alpha = 0.05f), style = Stroke(width = 3f))
            }
            drawPath(path(PodsGeometry.BUD_TIP_MESH), Mesh.copy(alpha = 0.55f))

            // Glossy body.
            val bodyBounds = androidx.compose.ui.geometry.Rect(0f, 0f, 100f, 160f)
            drawPath(body, Brush.verticalGradient(listOf(PlasticHighlight, PlasticMid, PlasticShade), 4f, 157f))
            clipPath(body) {
                val hx = 32f - lx * 14f
                val hy = 28f - ly * 12f
                // Form shading: smooth falloff toward the edges, no hard boundary.
                drawRect(
                    Brush.radialGradient(
                        0f to Color.Transparent,
                        0.62f to Color.Transparent,
                        1f to PlasticDeep.copy(alpha = 0.34f),
                        center = Offset(hx + 6f, hy + 10f), radius = 60f
                    ),
                    topLeft = bodyBounds.topLeft, size = bodyBounds.size
                )
                // Stem reads as a cylinder: dark sides, a soft highlight just left of centre.
                val sx = PodsGeometry.STEM_LEFT - 2f
                val sw = PodsGeometry.STEM_RIGHT - PodsGeometry.STEM_LEFT + 4f
                val stemBrush = Brush.horizontalGradient(
                    0f to PlasticDeep.copy(alpha = 0.30f),
                    0.34f to Color.Transparent,
                    0.46f to Color.White.copy(alpha = 0.55f + lx * 0.1f),
                    0.6f to Color.Transparent,
                    1f to PlasticDeep.copy(alpha = 0.36f),
                    startX = sx, endX = sx + sw
                )
                // Fade the cylinder shading in over the neck in thin slices (no hard edge).
                val fadeStart = PodsGeometry.STEM_TOP - 2f
                for (k in 0 until 8) {
                    drawRect(stemBrush, Offset(sx, fadeStart + k * 1.5f), Size(sw, 1.5f), alpha = (k + 1) / 9f)
                }
                drawRect(stemBrush, Offset(sx, fadeStart + 12f), Size(sw, 160f - fadeStart - 12f))
                drawPath(body, Color.Black.copy(alpha = 0.035f), style = Stroke(width = 2.5f))
                // Specular: a broad soft bloom plus a small crisp glint.
                drawOval(
                    brush = Brush.radialGradient(
                        listOf(Color.White.copy(alpha = 0.95f), Color.White.copy(alpha = 0f)),
                        center = Offset(hx, hy), radius = 22f
                    ),
                    topLeft = Offset(hx - 24f, hy - 15f), size = Size(48f, 30f)
                )
                drawOval(Color.White.copy(alpha = 0.85f), topLeft = Offset(hx - 5f, hy - 2.6f), size = Size(10f, 5f))
            }
            drawPath(
                body,
                Brush.linearGradient(
                    listOf(Color.White.copy(alpha = 0.8f), Color.White.copy(alpha = 0f)),
                    start = Offset(20f - lx * 20f, 0f - ly * 20f), end = Offset(70f, 120f)
                ),
                style = Stroke(width = 1.2f)
            )
            drawPath(path(PodsGeometry.BUD_TOP_MESH), Mesh.copy(alpha = 0.42f))
            drawPath(path(PodsGeometry.BUD_SENSOR), Mesh.copy(alpha = 0.55f))
            drawPath(path(PodsGeometry.BUD_STEM_MESH), Mesh.copy(alpha = 0.6f))
        }
    }
}

/** Draws the charging case (front, lid closed) scaled to fit [size]. */
fun DrawScope.drawCase(size: Size, light: Offset, visual: CaseVisual, ledPulse: Float) {
    val s = minOf(size.width / PodsGeometry.CASE_W, size.height / (PodsGeometry.CASE_H + 10f))
    val w = PodsGeometry.CASE_W * s
    val h = PodsGeometry.CASE_H * s
    translate((size.width - w) / 2f, (size.height - h) / 2f - 4f * s) {
        scale(s, s, pivot = Offset.Zero) {
            val lx = light.x
            val ly = light.y
            val body = path(PodsGeometry.CASE_BODY)
            drawOval(
                brush = Brush.radialGradient(
                    listOf(Color.Black.copy(alpha = 0.13f), Color.Transparent),
                    center = Offset(62f, 97f), radius = 64f
                ),
                topLeft = Offset(6f, 90f), size = Size(112f, 14f)
            )
            drawPath(body, Brush.verticalGradient(listOf(PlasticHighlight, PlasticMid, PlasticShade), 0f, 94f))
            clipPath(body) {
                val hx = 40f - lx * 24f
                val hy = 16f - ly * 10f
                drawRect(
                    Brush.radialGradient(
                        0f to Color.Transparent,
                        0.6f to Color.Transparent,
                        1f to PlasticDeep.copy(alpha = 0.38f),
                        center = Offset(62f - lx * 10f, 38f - ly * 8f), radius = 88f
                    ),
                    topLeft = Offset.Zero, size = Size(124f, 94f)
                )
                // Thickness: the lower body curves away from the light.
                drawRect(
                    Brush.verticalGradient(listOf(Color.Transparent, PlasticDeep.copy(alpha = 0.22f)), 70f, 94f),
                    topLeft = Offset(0f, 70f), size = Size(124f, 24f)
                )
                drawPath(body, Color.Black.copy(alpha = 0.035f), style = Stroke(width = 3f))
                // Lid seam: a shadow line with a thin highlight just under it.
                drawLine(Color.Black.copy(alpha = 0.16f), Offset(0f, PodsGeometry.CASE_SEAM_Y), Offset(124f, PodsGeometry.CASE_SEAM_Y), 0.9f)
                drawLine(Color.White.copy(alpha = 0.85f), Offset(0f, PodsGeometry.CASE_SEAM_Y + 1.1f), Offset(124f, PodsGeometry.CASE_SEAM_Y + 1.1f), 0.8f)
                // Long soft reflection on the lid plus a glint.
                drawOval(
                    brush = Brush.radialGradient(
                        listOf(Color.White.copy(alpha = 0.9f), Color.White.copy(alpha = 0f)),
                        center = Offset(hx, hy), radius = 34f
                    ),
                    topLeft = Offset(hx - 42f, hy - 10f), size = Size(84f, 20f)
                )
                drawOval(Color.White.copy(alpha = 0.85f), Offset(hx - 7f, hy - 2f), Size(14f, 4f))
            }
            drawPath(
                body,
                Brush.linearGradient(
                    listOf(Color.White.copy(alpha = 0.95f), Color.White.copy(alpha = 0.0f)),
                    start = Offset(30f - lx * 30f, -10f - ly * 10f), end = Offset(90f, 94f)
                ),
                style = Stroke(width = 1.4f)
            )
            // Pro 3's status light is invisible until the lid opens or the case charges.
            if (visual.lidOpen || visual.charging) {
                val full = (visual.level ?: 0) >= 95
                val led = if (full) Color(0xFF30D158) else Color(0xFFFF9F0A)
                val alpha = 0.55f + 0.45f * ledPulse
                drawCircle(
                    brush = Brush.radialGradient(listOf(led.copy(alpha = 0.55f * alpha), Color.Transparent), Offset(62f, 50f), 7f),
                    radius = 7f, center = Offset(62f, 50f)
                )
                drawCircle(led.copy(alpha = alpha), radius = 1.5f, center = Offset(62f, 50f))
            }
        }
    }
}

/** A single earbud with animated state. */
@Composable
fun PodBud(
    modifier: Modifier = Modifier,
    mirrored: Boolean,
    light: Offset,
    visual: BudVisual,
) {
    val lift by animateFloatAsState(if (visual.inEar) 1f else 0f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow), label = "lift")
    val presence by animateFloatAsState(if (visual.present) 1f else 0.32f, label = "presence")
    Canvas(
        modifier
            .aspectRatio(PodsGeometry.BUD_W / PodsGeometry.BUD_H)
            .graphicsLayer {
                alpha = presence
                translationY = -size.height * 0.04f * lift
            }
    ) {
        drawBud(size, mirrored, light, visual)
    }
}

/** The charging case with animated state. */
@Composable
fun PodCase(
    modifier: Modifier = Modifier,
    light: Offset,
    visual: CaseVisual,
    ledPulse: Float = 1f,
) {
    val presence by animateFloatAsState(if (visual.present) 1f else 0.32f, label = "presence")
    Canvas(
        modifier
            .aspectRatio(PodsGeometry.CASE_W / (PodsGeometry.CASE_H + 10f))
            .graphicsLayer { alpha = presence }
    ) {
        drawCase(size, light, visual, ledPulse)
    }
}
