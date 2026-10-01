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
package me.kavishdevar.librepods.utils

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * When Conversation Awareness gives your music back, and how. The AirPods say "talking" and
 * "stopped talking"; a real conversation has short gaps (breaths, the other person's turn), so
 * giving the music back on every "stopped" made it cut in and out. Instead it waits for a short
 * quiet spell, and waits a little longer the more back-and-forth there has been.
 *
 * The numbers are a design choice (not from Apple): long enough to cover a breath or a short
 * reply, short enough that the music is back about as soon as you'd notice it's missing.
 */
object ConversationTiming {
    /** Quiet needed before the volume comes back up. */
    const val VOLUME_QUIET_MS = 2_000L
    /** Quiet needed before paused music plays again (a resume mid-sentence is more jarring). */
    const val PAUSE_QUIET_MS = 3_000L
    /** Each extra time you started talking in the last [WINDOW_MS] adds this much. */
    const val PER_TURN_MS = 750L
    const val VOLUME_MAX_MS = 5_000L
    const val PAUSE_MAX_MS = 8_000L
    /** How far back "the same conversation" reaches. */
    const val WINDOW_MS = 30_000L
    /** No "stopped talking" for this long after the last "talking": give the music back anyway. */
    const val STUCK_MS = 5 * 60_000L

    /** How long to wait after "stopped talking"; [turns] = times you started talking in the last 30 s. */
    fun restoreDelayMs(pauseMode: Boolean, turns: Int): Long {
        val base = if (pauseMode) PAUSE_QUIET_MS else VOLUME_QUIET_MS
        val cap = if (pauseMode) PAUSE_MAX_MS else VOLUME_MAX_MS
        val extra = (turns - 1).coerceAtLeast(0) * (if (pauseMode) 1_000L else PER_TURN_MS)
        return (base + extra).coerceAtMost(cap)
    }

    /**
     * The pause before each one-step volume change from [from] to [to]. Going down is quick
     * (you're talking now); coming back up eases in and out over about a second, so it swells
     * back rather than jumping.
     */
    fun rampDelays(from: Int, to: Int): List<Long> {
        val steps = kotlin.math.abs(to - from)
        if (steps == 0) return emptyList()
        if (to < from) return List(steps) { 35L }
        val total = (steps * 90L).coerceIn(450L, 1_300L)
        // Smoothstep spacing: small gaps in the middle, larger at the start and end.
        val times = (0..steps).map { i ->
            val t = i.toFloat() / steps
            // Inverse of smoothstep, so equal volume steps land on an eased curve in time.
            val eased = 0.5f - kotlin.math.sin(kotlin.math.asin(1f - 2f * t) / 3f)
            (eased * total).toLong()
        }
        return (1..steps).map { (times[it] - times[it - 1]).coerceAtLeast(15L) }
    }

    private val _talking = MutableStateFlow(false)
    /** True while Conversation Awareness has your music turned down or paused. */
    val talking: StateFlow<Boolean> = _talking.asStateFlow()
    internal fun setTalking(on: Boolean) { _talking.value = on }
}
