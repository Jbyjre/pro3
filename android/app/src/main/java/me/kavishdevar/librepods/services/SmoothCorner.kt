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

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Apple's "continuous" corners (the squircle-like curve on every iPhone shape, the Dynamic Island
 * included), instead of a plain quarter circle. A plain circle corner jumps from a straight edge
 * straight into a bend, which the eye reads as a kink and makes a growing shape look like a
 * balloon; a continuous corner eases into the bend.
 *
 * The construction is Figma's published corner smoothing ("Desperately seeking squircles", Figma
 * blog, 2018; the same numbers as the open-source figma-squircle): each corner is a short circular
 * arc in the middle with a cubic curve either side that eases from the straight edge into it.
 * Smoothing 0 is a plain circle corner; Figma's "iOS" preset is 0.6 (Apple doesn't publish its own).
 *
 * When the corner takes up the whole half-side (a capsule), there is no room left to ease, so the
 * smoothing drops to 0 by itself and the shape is an exact capsule: the compact pill stays the
 * pill, and it turns into a continuous rounded rectangle as it grows. Pure numbers, unit-tested.
 */
object SmoothCorner {
    /** Figma's iOS preset. */
    const val IOS = 0.6f

    /**
     * One corner, in its own frame: x along the edge it leaves, y toward the next edge, starting
     * [p] before the corner point. Segments: cubic (a, b, c, d), arc of [arcLen] on each axis
     * (radius [radius], [arcDeg] wide, starting [startDeg] past the edge's direction), cubic back.
     */
    data class Corner(
        val radius: Float,
        val p: Float,
        val a: Float,
        val b: Float,
        val c: Float,
        val d: Float,
        val arcLen: Float,
        val arcDeg: Float,
        /**
         * Where the arc starts, in degrees from the edge's own direction (turning toward the next
         * edge is positive); the arc's centre sits [radius] in from both edges.
         */
        val startDeg: Float,
    )

    /**
     * The corner for [radius] with [smoothing] inside a shape whose shorter side is [shortSide].
     * The radius is first limited to half the shorter side (no corner can be more than that).
     */
    fun corner(radius: Float, smoothing: Float, shortSide: Float): Corner {
        val budget = (shortSide / 2f).coerceAtLeast(0f)
        val r = radius.coerceIn(0f, budget)
        if (r <= 0f) return Corner(0f, 0f, 0f, 0f, 0f, 0f, 0f, 90f, -90f)
        // There's only [budget] of edge to use: the smoothing shrinks as the corner fills it.
        val s = minOf(smoothing.coerceIn(0f, 1f), budget / r - 1f).coerceAtLeast(0f)
        val p = minOf((1f + s) * r, budget)
        val arcDeg = 90f * (1f - s)
        val arcLen = (sin(Math.toRadians(arcDeg / 2.0)) * r * sqrt(2.0)).toFloat()
        val alpha = (90f - arcDeg) / 2f
        val p3p4 = (r * tan(Math.toRadians(alpha / 2.0))).toFloat()
        val beta = 45f * s
        val c = (p3p4 * cos(Math.toRadians(beta.toDouble()))).toFloat()
        val d = (c * tan(Math.toRadians(beta.toDouble()))).toFloat()
        val b = ((p - arcLen - c - d) / 3f).coerceAtLeast(0f)
        val a = 2f * b
        return Corner(r, p, a, b, c, d, arcLen, arcDeg, -90f + alpha)
    }

    /**
     * Points on one corner's outline, in its own frame (see [Corner]), for checking the shape in
     * tests: where each segment starts and ends. The last one lands on (p, p), the start of the
     * next edge, when the corner is built right.
     */
    fun joints(k: Corner): List<Pair<Float, Float>> {
        val x1 = k.a + k.b + k.c
        val q = (x1 + k.arcLen) to (k.d + k.arcLen)
        return listOf(0f to 0f, x1 to k.d, q, (q.first + k.d) to (q.second + x1))
    }
}
