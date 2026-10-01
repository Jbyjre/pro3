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
        /**
         * Measuring in short bursts to save battery (the Balanced or Saver pace): the sensor
         * is off until the next burst. The last reading stays on screen.
         */
        Resting,
    }

    data class Sample(val timeMs: Long, val bpm: Int)

    data class State(
        val status: Status = Status.Off,
        val bpm: Int? = null,
        val lastReadingMs: Long = 0L,
        val sessionStartMs: Long = 0L,
        val samples: List<Sample> = emptyList(),
        /** Measuring by itself because the buds are worn (always-on mode), about every 5 s. */
        val background: Boolean = false,
        /** With a battery-saving pace: when the next burst of readings starts (0 = n/a). */
        val nextBurstMs: Long = 0L,
    ) {
        val min: Int? get() = samples.minOfOrNull { it.bpm }
        val max: Int? get() = samples.maxOfOrNull { it.bpm }
        val average: Int? get() = if (samples.isEmpty()) null else samples.map { it.bpm }.average().roundToInt()
    }

    const val MAX_SAMPLES = 4 * 3600 // four hours at one a second

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    internal fun starting(now: Long, background: Boolean = false) = _state.update {
        when {
            it.status == Status.Live -> it.copy(background = background)
            // After a stop, or a long break, starting again begins a new session (the old one
            // is in the history).
            it.status == Status.Off || (it.lastReadingMs > 0 && now - it.lastReadingMs > NEW_SESSION_GAP_MS) ->
                State(status = Status.Starting, sessionStartMs = now, background = background)
            else -> it.copy(status = Status.Starting, background = background, sessionStartMs = if (it.samples.isEmpty()) now else it.sessionStartMs)
        }
    }

    /** A break this long between readings starts a new session. */
    const val NEW_SESSION_GAP_MS = 30 * 60_000L

    /** Earlier sessions, newest first (all of them; see [HeartHistory]). */
    fun history(context: android.content.Context): List<HeartInsights.Session> = HeartHistory.sessions(context)

    /**
     * Saves the current session (all its readings) to the history if it lasted at least a
     * minute, then lets the GitHub backup pick it up if that's turned on.
     */
    internal fun saveSession(context: android.content.Context) {
        val samples = state.value.samples
        if (samples.size < 2) return
        HeartHistory.save(context, samples) ?: return
        HeartBackup.onSessionSaved(context)
    }

    /** The session is full: it was saved, so carry on in a fresh one. */
    internal fun rollOver(now: Long) = _state.update { State(status = it.status, bpm = it.bpm, sessionStartMs = now, background = it.background) }

    internal fun reading(bpm: Int, now: Long) = _state.update {
        val samples = (it.samples + Sample(now, bpm)).let { list -> if (list.size > MAX_SAMPLES) list.drop(list.size - MAX_SAMPLES) else list }
        it.copy(status = Status.Live, bpm = bpm, lastReadingMs = now, samples = samples, sessionStartMs = if (it.sessionStartMs == 0L) now else it.sessionStartMs)
    }

    internal fun status(status: Status) = _state.update {
        it.copy(
            status = status,
            // Keep the last number between battery-saving bursts (Resting, then Starting again).
            bpm = if (status == Status.Live || status == Status.Resting || (status == Status.Starting && it.status == Status.Resting)) it.bpm else null,
            background = it.background && status != Status.Off,
            nextBurstMs = if (status == Status.Resting) it.nextBurstMs else 0L,
        )
    }

    /** Between bursts of readings (battery-saving pace) until [nextMs]. */
    internal fun resting(nextMs: Long) = _state.update { it.copy(status = Status.Resting, nextBurstMs = nextMs) }

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
        if (recent.size < 15 || before.size < 20) return null
        val d = recent.map { it.bpm }.average() - before.map { it.bpm }.average()
        return when {
            d >= 5 -> Trend.Rising
            d <= -5 -> Trend.Falling
            else -> Trend.Steady
        }
    }

    /** A finished session, kept so you can compare with earlier ones. */
    data class Session(
        val startMs: Long,
        val endMs: Long,
        val average: Int,
        val min: Int,
        val max: Int,
        /** How many readings are saved (0 for sessions from before readings were kept). */
        val readings: Int = 0,
        /** Lowest 3-minute average, when the session was long enough (else 0). */
        val resting: Int = 0,
    ) {
        val minutes: Long get() = (endMs - startMs) / 60_000
        fun encode() = "$startMs,$endMs,$average,$min,$max,$readings,$resting"
        companion object {
            fun decode(line: String): Session? = line.split(",").mapNotNull { it.trim().toLongOrNull() }
                .takeIf { it.size == 5 || it.size == 7 }
                ?.let { Session(it[0], it[1], it[2].toInt(), it[3].toInt(), it[4].toInt(), it.getOrElse(5) { 0L }.toInt(), it.getOrElse(6) { 0L }.toInt()) }
        }
    }

    /**
     * A resting estimate: the lowest average over any 3-minute stretch of the session (needs at
     * least 3 minutes of readings). Most accurate when you've been sitting still for a while.
     * With a battery-saving pace (short bursts a few minutes apart) it's the lowest average of
     * any one burst with at least 10 readings over 45 seconds or more.
     */
    fun restingEstimate(samples: List<HeartRate.Sample>): Int? {
        if (samples.size < 10) return null
        val span = samples.last().timeMs - samples.first().timeMs
        val sparse = span > 0 && samples.size * 60_000.0 / span < 6.0 // under 6 readings a minute
        if (sparse) {
            // Split into bursts at gaps of over 90 seconds.
            val bursts = mutableListOf(mutableListOf(samples.first()))
            samples.zipWithNext().forEach { (a, b) -> if (b.timeMs - a.timeMs > 90_000) bursts.add(mutableListOf(b)) else bursts.last().add(b) }
            return bursts.filter { it.size >= 10 && it.last().timeMs - it.first().timeMs >= 45_000 }
                .minOfOrNull { b -> b.map { it.bpm }.average() }?.let { kotlin.math.round(it).toInt() }
        }
        val window = 180_000L
        var best: Double? = null
        var start = 0
        var sum = 0L
        samples.forEachIndexed { end, s ->
            sum += s.bpm
            while (s.timeMs - samples[start].timeMs > window) { sum -= samples[start].bpm; start++ }
            val count = end - start + 1
            // A full window with enough readings in it (some may be missing while a bud moves).
            if (s.timeMs - samples[start].timeMs >= window - 10_000 && count >= 30) {
                val avg = sum.toDouble() / count
                if (best == null || avg < best!!) best = avg
            }
        }
        return best?.let { kotlin.math.round(it).toInt() }
    }

    /**
     * Recovery: how far your heart rate fell in the minute after the session's highest point,
     * using the readings 52–68 seconds after it. Null until that minute has passed, or when
     * the peak wasn't an effort (below 100 BPM).
     */
    fun recovery(samples: List<HeartRate.Sample>): Int? {
        val peak = samples.maxByOrNull { it.bpm } ?: return null
        if (peak.bpm < 100) return null
        val after = samples.filter { it.timeMs - peak.timeMs in 52_000..68_000 }
        if (after.size < 2) return null
        return peak.bpm - kotlin.math.round(after.map { it.bpm }.average()).toInt()
    }

    /** The session as CSV: one row per reading, ISO time (UTC), milliseconds, BPM. */
    fun csv(samples: List<HeartRate.Sample>): String = buildString {
        append("time_utc,time_ms,bpm\n")
        samples.forEach { append(java.time.Instant.ofEpochMilli(it.timeMs)).append(',').append(it.timeMs).append(',').append(it.bpm).append('\n') }
    }

    /** A summary of [samples] if they cover at least a minute. */
    fun summarize(samples: List<HeartRate.Sample>): Session? {
        if (samples.size < 2 || samples.last().timeMs - samples.first().timeMs < 60_000) return null
        val bpms = samples.map { it.bpm }
        return Session(
            samples.first().timeMs, samples.last().timeMs, bpms.average().roundToInt(), bpms.min(), bpms.max(),
            readings = samples.size, resting = restingEstimate(samples) ?: 0,
        )
    }

    /**
     * Readings as compact CSV for the saved session files: a header, then one
     * `time_ms,bpm` row per reading. Readable in any spreadsheet.
     */
    fun compactCsv(samples: List<HeartRate.Sample>): String = buildString(samples.size * 18 + 16) {
        append("time_ms,bpm\n")
        samples.forEach { append(it.timeMs).append(',').append(it.bpm).append('\n') }
    }

    /** Reads either CSV layout Glint writes (export or saved session); skips anything else. */
    fun parseCsv(text: String): List<HeartRate.Sample> = text.lineSequence().mapNotNull { line ->
        val parts = line.split(',')
        if (parts.size < 2) return@mapNotNull null
        // "time_ms,bpm" or "time_utc,time_ms,bpm".
        val t = parts[parts.size - 2].trim().toLongOrNull() ?: return@mapNotNull null
        val b = parts.last().trim().toIntOrNull() ?: return@mapNotNull null
        if (b !in 20..250) null else HeartRate.Sample(t, b)
    }.toList().sortedBy { it.timeMs }

    /** Where a reading sits, for the colour of the "what it means" scale. */
    enum class Band { Low, Resting, Raised, Exercise, High }

    /** What a reading means right now, in plain words. */
    data class Meaning(val headline: String, val detail: String, val band: Band)

    /**
     * Explains [bpm]. With an [age], effort zones (AHA: max about 220 minus age; moderate
     * 50–70%, vigorous 70–85%) take over above the light zone. Otherwise it's compared with
     * the typical adult resting range (60–100 BPM, AHA) and, when known, your own usual
     * resting rate ([usualResting], from your history).
     */
    fun meaning(bpm: Int, age: Int, usualResting: Int): Meaning {
        val vsUsual = if (usualResting > 0) {
            val d = bpm - usualResting
            when {
                d >= 6 -> " About $d above your usual resting rate ($usualResting)."
                d <= -6 -> " About ${-d} below your usual resting rate ($usualResting)."
                else -> " Right around your usual resting rate ($usualResting)."
            }
        } else ""
        if (age > 0) {
            val z = zone(bpm, age)
            val pct = (bpm * 100f / maxHeartRate(age)).roundToInt()
            when (z) {
                Zone.Moderate -> return Meaning("Moderate effort", "$pct% of your estimated maximum (${maxHeartRate(age)}). A brisk-walk level: good for steady exercise.", Band.Exercise)
                Zone.Vigorous -> return Meaning("Vigorous effort", "$pct% of your estimated maximum (${maxHeartRate(age)}). Hard work like running; builds fitness.", Band.Exercise)
                Zone.Peak -> return Meaning("Near your maximum", "$pct% of your estimated maximum (${maxHeartRate(age)}). Only sustainable briefly; ease off if you feel unwell.", Band.High)
                Zone.Light -> Unit
            }
        }
        return when {
            bpm < 50 -> Meaning("Lower than typical", "Under the usual 60–100 resting range. Common during deep rest and in very fit people.$vsUsual", Band.Low)
            bpm < 60 -> Meaning("Calm, low resting range", "Just under the typical 60–100 range; common when relaxed or fit.$vsUsual", Band.Low)
            bpm <= 100 -> Meaning("Normal resting range", "Within the typical adult resting range of 60–100 BPM.$vsUsual", Band.Resting)
            bpm <= 120 -> Meaning("Raised", "Above the typical resting range: normal while moving, after coffee or when stressed.$vsUsual", Band.Raised)
            else -> Meaning("High for resting", "Normal during exercise. If you're sitting still and it stays this high, rest and check again.$vsUsual", Band.High)
        }
    }

    /**
     * Your usual resting rate: the middle value of the resting estimates from sessions in the
     * last 30 days (needs at least 3). Null when there isn't enough history yet.
     */
    fun usualResting(sessions: List<Session>, now: Long): Int? {
        val r = sessions.filter { it.resting > 0 && now - it.startMs <= 30L * 86_400_000 }.map { it.resting }.sorted()
        if (r.size < 3) return null
        return r[r.size / 2]
    }

    /** One day of history for the overview chart. */
    data class Day(val dayStartMs: Long, val min: Int, val max: Int, val average: Int, val minutes: Long, val sessions: Int, val resting: Int)

    /** Sessions grouped by local calendar day, newest first. Averages are weighted by length. */
    fun days(sessions: List<Session>, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): List<Day> =
        sessions.groupBy { java.time.Instant.ofEpochMilli(it.startMs).atZone(zone).toLocalDate() }
            .map { (date, list) ->
                val weights = list.map { (it.endMs - it.startMs).coerceAtLeast(60_000L).toDouble() }
                val avg = list.zip(weights).sumOf { (s, w) -> s.average * w } / weights.sum()
                Day(
                    dayStartMs = date.atStartOfDay(zone).toInstant().toEpochMilli(),
                    min = list.minOf { it.min },
                    max = list.maxOf { it.max },
                    average = avg.roundToInt(),
                    minutes = list.sumOf { it.minutes },
                    sessions = list.size,
                    resting = list.filter { it.resting > 0 }.minOfOrNull { it.resting } ?: 0,
                )
            }
            .sortedByDescending { it.dayStartMs }

    /**
     * Highest average over any [windowMs] stretch (default 5 minutes): your hardest sustained
     * effort in the session, less jumpy than the single highest reading. Null if too short.
     */
    fun peakAverage(samples: List<HeartRate.Sample>, windowMs: Long = 300_000L): Int? {
        if (samples.size < 2 || samples.last().timeMs - samples.first().timeMs < windowMs) return null
        var best: Double? = null
        var start = 0
        var sum = 0L
        samples.forEachIndexed { end, s ->
            sum += s.bpm
            while (s.timeMs - samples[start].timeMs > windowMs) { sum -= samples[start].bpm; start++ }
            if (s.timeMs - samples[start].timeMs >= windowMs - 15_000) {
                val avg = sum.toDouble() / (end - start + 1)
                if (best == null || avg > best!!) best = avg
            }
        }
        return best?.let { kotlin.math.round(it).toInt() }
    }

    /** Seconds spent at or above [bpm] (each reading counts until the next, at most 5 s). */
    fun secondsAbove(samples: List<HeartRate.Sample>, bpm: Int): Long =
        samples.indices.sumOf { i ->
            val s = samples[i]
            if (s.bpm < bpm) 0L else (((samples.getOrNull(i + 1)?.timeMs ?: (s.timeMs + 1000)) - s.timeMs) / 1000).coerceIn(0, 5)
        }

    /** Personal bests across your history (null parts when there isn't one yet). */
    data class Records(val lowestResting: Session?, val highestPeak: Session?, val longest: Session?)

    fun records(sessions: List<Session>): Records = Records(
        lowestResting = sessions.filter { it.resting > 0 }.minByOrNull { it.resting },
        highestPeak = sessions.maxByOrNull { it.max },
        longest = sessions.maxByOrNull { it.endMs - it.startMs },
    )

    /** One point per day with a resting estimate, oldest first, for the last [span] days. */
    fun restingSeries(sessions: List<Session>, now: Long, span: Int = 30, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): List<Pair<Long, Int>> =
        days(sessions.filter { now - it.startMs <= span * 86_400_000L }, zone)
            .filter { it.resting > 0 }
            .map { it.dayStartMs to it.resting }
            .sortedBy { it.first }
}
