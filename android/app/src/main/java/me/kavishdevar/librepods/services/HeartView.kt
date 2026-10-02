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
 * What the heart in the islands should honestly say right now. Every state looks different, and
 * a "busy" look only ever means something is really happening:
 *
 * - [Kind.Live]: readings are arriving; the number and a heart beating at that pace.
 * - [Kind.Starting]: the sensor was just asked and its first reading is on the way.
 * - [Kind.Resting]: battery-saving pace, the sensor is off until the next burst; the last number,
 *   dimmed, with when the next reading comes.
 * - [Kind.NoSignal]: the sensor was asked but nothing comes (not worn, loose fit).
 * - [Kind.Blocked]: the AirPods are there for sound, but the phone refuses pro the control link the
 *   sensor needs (or it's still being opened).
 * - [Kind.Off]: nothing is measuring; tapping starts a short measurement.
 * - [Kind.Away]: no AirPods; nothing to show.
 *
 * Pure, so it's unit-tested without a phone.
 */
object HeartView {
    enum class Kind { Live, Starting, Resting, NoSignal, Blocked, Off, Away }

    /** What a tap on the heart does. */
    enum class Tap { Explain, Start, Retry, Reconnect, None }

    data class View(
        val kind: Kind,
        /** The number to show (live, or the last one while resting), else null. */
        val bpm: Int?,
        /** Two or three words for the chip. */
        val short: String,
        /** One plain line for the explanation page. */
        val line: String,
        val tap: Tap,
    ) {
        /** Something real to show on the small Dynamic Island: a number. */
        val worthAPill: Boolean get() = bpm != null && (kind == Kind.Live || kind == Kind.Resting)
    }

    /** A reading older than this no longer counts as live. */
    const val STALE_MS = 20_000L
    /** While resting, the last reading is shown for at most this long. */
    const val REST_KEEP_MS = 20 * 60_000L

    fun of(hr: HeartRate.State, link: LinkState, airPodsAudio: Boolean, now: Long): View {
        val linkUp = link is LinkState.Connected
        val podsThere = linkUp || airPodsAudio || link is LinkState.Connecting || link is LinkState.Retrying || link is LinkState.GaveUp
        return when {
            hr.status == HeartRate.Status.Live && hr.bpm != null && now - hr.lastReadingMs <= STALE_MS ->
                View(Kind.Live, hr.bpm, "${hr.bpm}", "Measuring now", Tap.Explain)
            hr.status == HeartRate.Status.Resting && hr.bpm != null && now - hr.lastReadingMs <= REST_KEEP_MS -> {
                val mins = if (hr.nextBurstMs > now) ((hr.nextBurstMs - now + 59_999) / 60_000).toInt() else 0
                View(
                    Kind.Resting, hr.bpm, "${hr.bpm}",
                    if (mins > 0) "Saving battery: next reading in $mins min" else "Saving battery: next reading soon",
                    Tap.Explain,
                )
            }
            !podsThere -> View(Kind.Away, null, "", "AirPods not connected", Tap.None)
            link is LinkState.GaveUp ->
                View(Kind.Blocked, null, "Blocked", "Your phone isn't letting pro reach the heart sensor.", Tap.Reconnect)
            link is LinkState.Connecting || link is LinkState.Retrying ->
                View(Kind.Blocked, null, "Linking", "Opening the link to the heart sensor…", Tap.None)
            !linkUp ->
                View(Kind.Blocked, null, "Blocked", "pro can't reach the heart sensor right now.", Tap.Reconnect)
            hr.status == HeartRate.Status.Starting ->
                View(Kind.Starting, null, "Starting", "Starting the sensor: the first reading takes a few seconds", Tap.None)
            hr.status == HeartRate.Status.NoSignal || hr.status == HeartRate.Status.Live || hr.status == HeartRate.Status.Resting ->
                View(Kind.NoSignal, null, "No signal", "No reading: check both AirPods are in snugly", Tap.Retry)
            hr.status == HeartRate.Status.NotConnected ->
                View(Kind.Blocked, null, "Blocked", "pro can't reach the heart sensor right now.", Tap.Reconnect)
            else -> View(Kind.Off, null, "Measure", "Not measuring. Tap to measure for a couple of minutes", Tap.Start)
        }
    }
}
