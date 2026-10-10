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
package me.kavishdevar.librepods.services

/**
 * The iPhone's Dynamic Island, by Apple's own numbers, so every part of pro's island uses the
 * same ones. Pure, so it is unit-tested without a phone.
 *
 * Source: Apple's Human Interface Guidelines, "Live Activities" (updated 2025-12-16), read on
 * 2026-10-09. Apple gives sizes in points; on Android a dp is close to a point (both about
 * 1/160 inch), so the numbers are used as dp. What Apple says, quoted or from its tables:
 * - "The Dynamic Island uses a corner radius of 44 points."
 * - "Live Activities in the Dynamic Island use a black opaque background", and "You can't
 *   customize background colors for compact, minimal, and expanded presentations."
 * - "When the background is dark — for example, in Dark Mode — a key line appears around the
 *   Dynamic Island", tinted to match the content (`keylineTint`).
 * - Expanded width 371 pt on a 393 pt wide iPhone and 408 pt on a 430 pt one: 11 pt each side.
 *   Expanded height 84 to 160 pt. Compact and minimal are 36.67 pt tall; minimal is 36.67 to
 *   45 pt wide.
 * - Two activities at once: "One appears attached to the Dynamic Island while the other
 *   appears detached ... circular or oval."
 * - Animations last "a maximum duration of two seconds"; keep content "snug within a margin
 *   that's concentric to the outer edge"; inner corner = outer corner minus the margin.
 *
 * Not published by Apple (matched by eye from pictures, so not verified): the springs, the
 * gap between the island and its detached circle, and the shape of the compact album art.
 */
object IslandSpec {
    /** The island's corner radius, in dp. */
    const val CORNER_DP = 44f
    /** Space between the opened island and each side of the screen, in dp. */
    const val SIDE_MARGIN_DP = 11f
    /** The widest an opened island gets (the largest iPhone), in dp. */
    const val EXPANDED_MAX_W_DP = 408f
    const val EXPANDED_MIN_H_DP = 84f
    const val EXPANDED_MAX_H_DP = 160f
    /** Height of the compact and minimal island, and the detached circle's size, in dp. */
    const val COMPACT_H_DP = 36.67f
    const val MINIMAL_MAX_W_DP = 45f
    /** Gap between the island and a detached circle, in dp (by eye, not published). */
    const val DETACHED_GAP_DP = 7f
    /** Apple's limit for any island animation. */
    const val MAX_ANIMATION_MS = 2_000L
    /** The default content margin inside the island (the Lock Screen's standard margin is 14 pt). */
    const val CONTENT_MARGIN_DP = 14f

    /** How wide the opened island is on a screen [screenWDp] wide (both in dp). */
    fun expandedWidthDp(screenWDp: Float): Float =
        (screenWDp - 2f * SIDE_MARGIN_DP).coerceAtMost(EXPANDED_MAX_W_DP).coerceAtLeast(0f)

    /**
     * The corner radius for a shape [heightPx] tall: 44 dp, or a full capsule when the shape is
     * shorter than 88 dp (a radius can't be more than half the height).
     */
    fun cornerPx(heightPx: Float, density: Float): Float =
        minOf(CORNER_DP * density, heightPx / 2f).coerceAtLeast(0f)

    /**
     * A corner nested [gap] inside a corner of [outer]: shares its centre (Apple's "concentric"
     * rule). Never negative; at or below zero the inner shape should be a capsule instead.
     */
    fun concentric(outer: Float, gap: Float): Float = (outer - gap).coerceAtLeast(0f)

    /**
     * The half-width of a rounded square, centred in a round space of radius [fit], whose
     * rounded corners just reach that circle (corner = [cornerRatio] x the half-width). Inside
     * the pill's round end this is Apple's "concentric placement": the square's corners follow
     * the island's curve instead of poking into it.
     */
    fun roundedSquareHalf(fit: Float, cornerRatio: Float): Float {
        val q = cornerRatio.coerceIn(0f, 1f)
        return (fit / (kotlin.math.sqrt(2f) * (1f - q) + q)).coerceAtLeast(0f)
    }

    /**
     * The space left between a rounded shape's corner (radius [innerCorner]) and the island's own
     * corner (radius [outer]) when the shape sits [inset] in from both edges. Apple: keep these
     * even, so the inner shape never pokes into the island's curve. Negative = it pokes out.
     */
    fun cornerGap(outer: Float, inset: Float, innerCorner: Float): Float {
        val d = (outer - inset - innerCorner).coerceAtLeast(0f)
        // Corner centres sit on the diagonal; the inner corner's farthest point is d*sqrt(2) + its radius away.
        return outer - (d * kotlin.math.sqrt(2f) + innerCorner)
    }

    /**
     * How far in from a shape's right (or left) edge a circle of radius [r] must sit so it keeps
     * [gap] from the shape's rounded corner of radius [corner], when the circle's centre is
     * [fromBottom] above the shape's bottom edge. Never closer than [gap] + [r] to the side.
     */
    fun insetFromCorner(corner: Float, fromBottom: Float, r: Float, gap: Float): Float {
        val room = corner - gap - r
        val side = gap + r
        if (room <= 0f) return maxOf(side, corner)
        val dy = (corner - fromBottom).coerceAtLeast(0f)
        if (dy >= room) return maxOf(side, corner)
        val dx = kotlin.math.sqrt(room * room - dy * dy)
        return maxOf(side, corner - dx)
    }

    /**
     * How the island looks: like the iPhone's (opaque black in light and dark, a key line only
     * in dark mode, growing out of itself around the camera), or pro's earlier frosted glass.
     */
    enum class Style(val label: String, val description: String) {
        IPhone("iPhone", "Black, like the real Dynamic Island, opening around the camera"),
        Glass("Glass", "Frosted glass that drops out under the camera"),
    }

    /**
     * The island's edge. Apple's key line: a thin line in the content's colour, only on a dark
     * background. Liquid Glass: a rim that catches the light from above (brightest along the top,
     * swinging a little as the phone tilts), in light and dark, like the glass in the rest of pro.
     */
    enum class Edge(val label: String, val description: String) {
        KeyLine("Key line", "Apple's thin edge, only on dark backgrounds"),
        Glass("Liquid Glass", "A rim that catches the light, in light and dark"),
    }

    /** The Liquid Glass rim's strength along the top edge (0..1 alpha), by background and Glow choice. */
    fun glassRimAlpha(darkBackground: Boolean, glow: Float): Float =
        ((if (darkBackground) 0.34f else 0.5f) * (0.55f + 0.45f * glow)).coerceIn(0f, 0.6f)

    /**
     * Whether a pop-up opens around the camera, out of the Dynamic Island itself (Apple's way):
     * only in the iPhone style, only when the Dynamic Island is there to grow out of, and only
     * above the status bar (pro's accessibility switch), or the clock would draw over it.
     */
    fun opensAroundCamera(style: Style, pillUp: Boolean, aboveStatusBar: Boolean): Boolean =
        style == Style.IPhone && pillUp && aboveStatusBar

    /** The key line's strength (0..1 alpha): only on a dark background, scaled by the Glow choice. */
    fun keylineAlpha(darkBackground: Boolean, glow: Float): Float =
        if (!darkBackground) 0f else (0.16f * glow).coerceIn(0f, 0.32f)

    /**
     * Two things at once (a timer while music plays): the second one sits in a detached circle
     * beside the island. Returns how far right of the island's right edge the circle's centre is.
     */
    fun detachedCentreOffset(circleD: Float, gap: Float): Float = gap + circleD / 2f
}
