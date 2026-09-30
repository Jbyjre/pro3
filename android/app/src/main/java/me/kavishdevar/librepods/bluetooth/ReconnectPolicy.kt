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

package me.kavishdevar.librepods.bluetooth

/**
 * Timing for re-opening the AirPods control channel after the audio link comes up or the
 * channel drops. AirPods often refuse the first attempt while they are still negotiating
 * audio, so a quick burst of retries is followed by slower ones for as long as the audio
 * link stays up. Pure logic, unit tested.
 */
class ReconnectPolicy(
    private val burstDelaysMs: List<Long> = listOf(0L, 700L, 1_500L, 3_000L, 5_000L, 8_000L),
    private val slowDelayMs: Long = 20_000L,
    private val maxSlowAttempts: Int = 12,
) {
    /**
     * Delay before attempt number [attempt] (0-based), or null when we should stop trying
     * until something new happens (audio reconnects, Bluetooth toggles, user taps retry).
     */
    fun delayBefore(attempt: Int): Long? {
        require(attempt >= 0)
        if (attempt < burstDelaysMs.size) return burstDelaysMs[attempt]
        val slowIndex = attempt - burstDelaysMs.size
        return if (slowIndex < maxSlowAttempts) slowDelayMs else null
    }

    /** Total attempts before giving up; burst + slow. */
    val maxAttempts: Int get() = burstDelaysMs.size + maxSlowAttempts

    /** How many failures in a row, with the audio link up, we treat as "blocked by this phone". */
    val attemptsBeforeBlockedVerdict: Int get() = 4
}
