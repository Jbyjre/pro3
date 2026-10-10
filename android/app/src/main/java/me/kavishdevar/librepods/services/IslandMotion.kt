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
 * - the height follows the width a touch softer, so the shape grows like one drop, not a box.
 */
object IslandMotion {
    /** A spring: [damping] 1 = no overshoot, lower = more bounce; [stiffness] higher = quicker. */
    data class Spring(val damping: Float, val stiffness: Float)

    enum class Feel(val label: String, val description: String, val open: Spring, val openHeight: Spring, val close: Spring) {
        Smooth("Smooth", "Like the iPhone: quick, with a soft settle", Spring(0.8f, 380f), Spring(0.76f, 300f), Spring(1f, 420f)),
        Snappy("Snappy", "Faster, almost no bounce", Spring(0.9f, 620f), Spring(0.88f, 520f), Spring(1f, 700f)),
        Bouncy("Bouncy", "Playful, with more spring", Spring(0.62f, 300f), Spring(0.58f, 240f), Spring(0.95f, 380f)),
    }

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
