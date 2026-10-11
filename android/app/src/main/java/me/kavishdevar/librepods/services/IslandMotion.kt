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
 * How the island moves: the springs it opens and closes on. Pure numbers, so they're unit-tested.
 *
 * Apple doesn't publish the Dynamic Island's springs (not verified; matched by eye). What it does
 * say (`IslandSpec`): animations last at most two seconds. What these keep, whatever the feel:
 * - opening may overshoot a little, like a drop of liquid settling;
 * - closing never does: a close that overshoots would shrink smaller than the pill for a moment
 *   and then pop back, which reads as a glitch (it did, before 2026-10-10);
 * - the height follows the width a touch later, so the shape grows like one drop, not a box;
 * - it never swells in both directions at once (a balloon): the height bounces less than the
 *   width, is capped just past its target, and gives a little while the width overshoots, as a
 *   drop thins when it stretches ([volumeGive]);
 * - the corner grows from the pill's own curve to the opened 44 dp ([morphRadius]), so the
 *   half-grown shape is a rounded slab, never a fat capsule.
 */
object IslandMotion {
    /** A spring: [damping] 1 = no overshoot, lower = more bounce; [stiffness] higher = quicker. */
    data class Spring(val damping: Float, val stiffness: Float)

    enum class Feel(val label: String, val description: String, val open: Spring, val openHeight: Spring, val close: Spring) {
        Smooth("Smooth", "Like the iPhone: quick, with a soft settle", Spring(0.8f, 380f), Spring(0.88f, 320f), Spring(1f, 420f)),
        Snappy("Snappy", "Faster, almost no bounce", Spring(0.9f, 620f), Spring(0.94f, 540f), Spring(1f, 700f)),
        Bouncy("Bouncy", "Playful, with more spring", Spring(0.62f, 300f), Spring(0.74f, 260f), Spring(0.95f, 380f)),
    }

    /** The most the height may run past its target (a share of the distance), whatever the feel. */
    const val MAX_HEIGHT_OVERSHOOT = 0.02f

    /** How much the height gives for each bit the width runs past its target. */
    const val VOLUME = 0.6f

    /**
     * The height's factor while the width is at [widthProgress] (1 = arrived): 1 until it
     * overshoots, then a little less, so the stretched shape thins like liquid instead of swelling.
     */
    fun volumeGive(widthProgress: Float): Float =
        1f / (1f + VOLUME * (widthProgress - 1f).coerceAtLeast(0f))

    /**
     * The corner while morphing from a shape with corner [from] (the pill: half its height) to
     * one with [to] (44 dp), [progress] of the way, for a shape [height] tall right now.
     */
    fun morphRadius(from: Float, to: Float, progress: Float, height: Float): Float =
        (from + (to - from) * progress.coerceIn(0f, 1f)).coerceIn(0f, (height / 2f).coerceAtLeast(0f))

    /**
     * Roughly how long a spring takes to settle within 1% of where it's going, in milliseconds
     * (for a mass of 1). Used to check every feel stays inside Apple's two-second limit.
     */
    fun settleMs(s: Spring): Long {
        val w = kotlin.math.sqrt(s.stiffness.toDouble())
        val z = s.damping.toDouble().coerceAtLeast(0.05)
        // Underdamped: the envelope e^(-z w t) falls to 1%; critically damped needs a bit longer.
        val t = if (z < 1.0) kotlin.math.ln(100.0) / (z * w) else 6.6 / w
        return (t * 1000.0).toLong()
    }

    /** How far past its target a spring overshoots, as a share of the distance travelled (0 = none). */
    fun overshoot(s: Spring): Float {
        val z = s.damping.toDouble()
        if (z >= 1.0) return 0f
        return kotlin.math.exp(-z * Math.PI / kotlin.math.sqrt(1.0 - z * z)).toFloat()
    }
}
