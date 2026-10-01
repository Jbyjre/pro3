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
        when {
            it.status == Status.Live -> it
            // After a stop, starting again begins a new session (the old one is in the history).
            it.status == Status.Off -> State(status = Status.Starting, sessionStartMs = now)
            else -> it.copy(status = Status.Starting, sessionStartMs = if (it.samples.isEmpty()) now else it.sessionStartMs)
        }
    }

    private const val PREF_SESSIONS = "glint_hr_sessions"

    /** Earlier sessions, newest first (the last 20). */
    fun history(context: android.content.Context): List<HeartInsights.Session> =
        context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)
            .getString(PREF_SESSIONS, "").orEmpty().lines().mapNotNull { HeartInsights.Session.decode(it) }

    /** Saves the current session to the history if it lasted at least a minute. */
    internal fun saveSession(context: android.content.Context) {
        val summary = HeartInsights.summarize(state.value.samples) ?: return
        val prefs = context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)
        val old = history(context).filter { it.startMs != summary.startMs }
        val lines = (listOf(summary) + old).take(20).joinToString("\n") { it.encode() }
        prefs.edit().putString(PREF_SESSIONS, lines).apply()
    }

    internal fun reading(bpm: Int, now: Long) = _state.update {
        val samples = (it.samples + Sample(now, bpm)).let { list -> if (list.size > MAX_SAMPLES) list.drop(list.size - MAX_SAMPLES) else list }
        it.copy(status = Status.Live, bpm = bpm, lastReadingMs = now, samples = samples, sessionStartMs = if (it.sessionStartMs == 0L) now else it.sessionStartMs)
    }

    internal fun status(status: Status) = _state.update { it.copy(status = status, bpm = if (status == Status.Live) it.bpm else null) }

    /** Clears the session's readings (the Reset button). */
    fun clear() = _state.update { State(status = it.status) }
}

/**
 * Plain-language meaning for heart-rate readings. Effort zones follow the American Heart
 * Association's guidance: maximum heart rate is about 220 minus your age; moderate effort is
 * 50–70% of it, vigorous 70–85%. A normal resting rate for most adults is 60–100 BPM.
 */
object HeartInsights {
    enum class Zone(val label: String, val meaning: String) {
        Light("Light", "Resting or easy movement"),
        Moderate("Moderate", "Brisk walking pace: moderate exercise"),
        Vigorous("Vigorous", "Hard exercise: running, fast cycling"),
        Peak("Peak", "Above the vigorous range: near your maximum"),
    }

    fun maxHeartRate(age: Int): Int = 220 - age

    fun zone(bpm: Int, age: Int): Zone {
        val pct = bpm.toFloat() / maxHeartRate(age)
        return when {
            pct < 0.50f -> Zone.Light
            pct < 0.70f -> Zone.Moderate
            pct <= 0.85f -> Zone.Vigorous
            else -> Zone.Peak
        }
    }

    /** Seconds spent in each zone (each reading counts until the next one, at most 5 s). */
    fun timeInZones(samples: List<HeartRate.Sample>, age: Int): Map<Zone, Long> {
        val out = Zone.entries.associateWith { 0L }.toMutableMap()
        samples.forEachIndexed { i, s ->
            val next = samples.getOrNull(i + 1)?.timeMs ?: (s.timeMs + 1000)
            val secs = ((next - s.timeMs) / 1000).coerceIn(0, 5)
            val z = zone(s.bpm, age)
            out[z] = out.getValue(z) + secs
        }
        return out
    }

    enum class Trend(val words: String) { Rising("Rising"), Falling("Coming down"), Steady("Steady") }

    /** Last 2 minutes against the 3 before them; a change of 5 BPM or more counts. */
    fun trend(samples: List<HeartRate.Sample>, now: Long): Trend? {
        val recent = samples.filter { now - it.timeMs <= 120_000 }
        val before = samples.filter { now - it.timeMs in 120_001..300_000 }
        if (recent.size < 20 || before.size < 30) return null
        val d = recent.map { it.bpm }.average() - before.map { it.bpm }.average()
        return when {
            d >= 5 -> Trend.Rising
            d <= -5 -> Trend.Falling
            else -> Trend.Steady
        }
    }

    /** A finished session, kept so you can compare with earlier ones. */
    data class Session(val startMs: Long, val endMs: Long, val average: Int, val min: Int, val max: Int) {
        fun encode() = "$startMs,$endMs,$average,$min,$max"
        companion object {
            fun decode(line: String): Session? = line.split(",").mapNotNull { it.toLongOrNull() }
                .takeIf { it.size == 5 }?.let { Session(it[0], it[1], it[2].toInt(), it[3].toInt(), it[4].toInt()) }
        }
    }

    /** A summary of [samples] if they cover at least a minute. */
    fun summarize(samples: List<HeartRate.Sample>): Session? {
        if (samples.size < 2 || samples.last().timeMs - samples.first().timeMs < 60_000) return null
        val bpms = samples.map { it.bpm }
        return Session(samples.first().timeMs, samples.last().timeMs, bpms.average().toInt(), bpms.min(), bpms.max())
    }
}
