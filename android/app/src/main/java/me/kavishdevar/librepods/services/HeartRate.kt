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
package me.kavishdevar.librepods.services

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.roundToInt

/**
 * Live heart rate from the AirPods Pro 3's sensor. The service starts and stops the sensor
 * stream; readings land here (about one a second while you wear the buds) and the screen
 * reads [state]. The current session is kept in memory: up to [MAX_SAMPLES] readings.
 */
object HeartRate {
    enum class Status {
        /** Not measuring. */
        Off,
        /** Asked the AirPods to start; waiting for the first reading. */
        Starting,
        /** Readings are arriving. */
        Live,
        /** Asked, but no reading arrived (not worn, loose fit, or not supported). */
        NoSignal,
        /** The AirPods' control connection isn't up, so the sensor can't be reached. */
        NotConnected,
    }

    data class Sample(val timeMs: Long, val bpm: Int)

    data class State(
        val status: Status = Status.Off,
        val bpm: Int? = null,
        val lastReadingMs: Long = 0L,
        val sessionStartMs: Long = 0L,
        val samples: List<Sample> = emptyList(),
    ) {
        val min: Int? get() = samples.minOfOrNull { it.bpm }
        val max: Int? get() = samples.maxOfOrNull { it.bpm }
        val average: Int? get() = if (samples.isEmpty()) null else samples.map { it.bpm }.average().roundToInt()
    }

    const val MAX_SAMPLES = 4 * 3600 // four hours at one a second

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    internal fun starting(now: Long) = _state.update {
        if (it.status == Status.Live) it else it.copy(status = Status.Starting, sessionStartMs = if (it.samples.isEmpty()) now else it.sessionStartMs)
    }

    internal fun reading(bpm: Int, now: Long) = _state.update {
        val samples = (it.samples + Sample(now, bpm)).let { list -> if (list.size > MAX_SAMPLES) list.drop(list.size - MAX_SAMPLES) else list }
        it.copy(status = Status.Live, bpm = bpm, lastReadingMs = now, samples = samples, sessionStartMs = if (it.sessionStartMs == 0L) now else it.sessionStartMs)
    }

    internal fun status(status: Status) = _state.update { it.copy(status = status, bpm = if (status == Status.Live) it.bpm else null) }

    /** Clears the session's readings (the Reset button). */
    fun clear() = _state.update { State(status = it.status) }
}
