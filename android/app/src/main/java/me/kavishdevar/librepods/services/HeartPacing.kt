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

import kotlin.math.abs

/**
 * How often background heart readings are taken (Settings in the Heart page, "Pace").
 * The stored number never changes meaning for Continuous (0); 1 and 2 were "one minute every
 * 5 / 15 minutes" before 2026-10-15 and are now spot checks (see [HeartPacing]).
 */
enum class HeartPace {
    /** The sensor stays on while the buds are worn: a reading about every 5 seconds. */
    Continuous,
    /**
     * Spot checks about every 2 minutes, every 4 when your heart rate is steady, and straight
     * through (no rests) while it's changing or high, so the graph has detail where it matters.
     */
    Smart,
    /** A spot check every 5 minutes, always: the same sensor time as the old "one minute in 15". */
    Saver;

    companion object {
        fun of(v: Int) = entries.getOrElse(v) { Continuous }
    }
}

/**
 * The spot-check rules, kept pure so they're unit-tested (Jake, 2026-10-15: "the scan once every
 * 15 min looks terrible on the graph ... fill the graph with good info without using too much
 * battery").
 *
 * Before: one minute of readings, then 4 or 14 minutes of nothing. On the graph that was a short
 * squiggle, a long gap, another squiggle. Now the same (or less) sensor time is spread into short
 * spot checks: the sensor starts, gives [SPOT_READINGS] good readings over at least
 * [SPOT_SPREAD_MS] (about 20 seconds at the usual one-reading-every-5-seconds), and rests. Each
 * spot becomes one point on the graph (its middle reading, with its lowest and highest as a faint
 * bar), and the points are joined into one smooth line.
 *
 * Sensor time, worked out from the rules (not measured on the AirPods' battery):
 * - Saver: about 20 s in every 5 minutes, about 7%: the same as the old Saver (1 min in 15).
 * - Smart, steady: about 20 s in every 4 minutes, about 8%. Normal: about 20 s every 2 minutes, about
 *   17% (the old Balanced was 20%). Changing or high: continuous until it settles.
 */
object HeartPacing {
    /** Good readings (after the sensor's warm-up) that make one spot check. */
    const val SPOT_READINGS = 3
    /** ...spread over at least this long, so one twitch can't be the whole spot. */
    const val SPOT_SPREAD_MS = 10_000L
    /** A spot that brings no good readings in this long rests anyway (the buds may be loose). */
    const val SPOT_GIVE_UP_MS = 60_000L

    const val SMART_REST_MS = 100_000L
    const val SMART_STEADY_REST_MS = 220_000L
    const val SAVER_REST_MS = 280_000L

    /** A change this big from the last spot means you're on the move: keep measuring. */
    const val MOVING_BPM = 8
    /** Within this of the last spot counts as steady. */
    const val STEADY_BPM = 3
    /** At or above this, Smart keeps measuring (exercise, stress): it's what the graph is for. */
    const val BUSY_BPM = 100
    /** Steady spots in a row before Smart stretches its rest. */
    const val STEADY_RUN = 3
    /**
     * At or below this (the lower of the worn buds, not charging), Smart eases to Saver's rhythm:
     * the sensor runs on the AirPods' battery, and a nearly flat bud matters more than detail.
     */
    const val PODS_LOW = 20
    /** With low AirPods, a changing or high rate is still checked, but a minute apart, not nonstop. */
    const val LOW_PODS_BUSY_REST_MS = 60_000L

    /** True when the lowest of [levels] (worn buds, 0..100; others ignored) is at or under [PODS_LOW]. */
    fun podsLow(levels: List<Int>): Boolean = levels.filter { it in 0..100 }.minOrNull()?.let { it <= PODS_LOW } ?: false

    /** The spot has what it needs. [firstMs] is when its first good reading came. */
    fun spotDone(readings: Int, firstMs: Long, now: Long): Boolean =
        readings >= SPOT_READINGS && now - firstMs >= SPOT_SPREAD_MS

    /** The spot's value: its middle reading (one odd reading can't pull it). */
    fun spotValue(bpms: List<Int>): Int? {
        if (bpms.isEmpty()) return null
        val s = bpms.sorted()
        return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2] + 1) / 2
    }

    /** How many steady spots in a row, counting this one. */
    fun steadyRun(spot: Int, previous: Int?, runBefore: Int): Int =
        if (previous != null && abs(spot - previous) <= STEADY_BPM) runBefore + 1 else 0

    /**
     * How long the sensor rests after a spot, in ms; 0 means carry straight on (no rest).
     * [steady] is [steadyRun]'s answer for this spot.
     */
    fun restAfter(pace: HeartPace, spot: Int, previous: Int?, steady: Int, podsLow: Boolean = false): Long = when (pace) {
        HeartPace.Continuous -> 0L
        HeartPace.Saver -> SAVER_REST_MS
        HeartPace.Smart -> {
            val busy = spot >= BUSY_BPM || (previous != null && abs(spot - previous) >= MOVING_BPM)
            when {
                podsLow -> if (busy) LOW_PODS_BUSY_REST_MS else SAVER_REST_MS
                busy -> 0L
                steady >= STEADY_RUN -> SMART_STEADY_REST_MS
                else -> SMART_REST_MS
            }
        }
    }
}
