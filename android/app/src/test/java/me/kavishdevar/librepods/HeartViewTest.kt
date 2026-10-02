package me.kavishdevar.librepods

import me.kavishdevar.librepods.services.HeartRate
import me.kavishdevar.librepods.services.HeartView
import me.kavishdevar.librepods.services.HeartView.Kind
import me.kavishdevar.librepods.services.HeartView.Tap
import me.kavishdevar.librepods.services.LinkState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every heart state looks different and says something true; "busy" only while something happens. */
class HeartViewTest {
    private val now = 2_000_000_000L
    private val up = LinkState.Connected("AirPods Pro")
    private fun view(st: HeartRate.State, link: LinkState = up, audio: Boolean = true) = HeartView.of(st, link, audio, now)

    @Test fun liveShowsTheNumber() {
        val v = view(HeartRate.State(status = HeartRate.Status.Live, bpm = 68, lastReadingMs = now - 1_000))
        assertEquals(Kind.Live, v.kind)
        assertEquals(68, v.bpm)
        assertEquals(Tap.Explain, v.tap)
        assertTrue(v.worthAPill)
    }

    @Test fun anOldLiveReadingIsNoLongerLive() {
        val v = view(HeartRate.State(status = HeartRate.Status.Live, bpm = 68, lastReadingMs = now - 60_000))
        assertEquals(Kind.NoSignal, v.kind)
        assertNull(v.bpm)
        assertEquals(Tap.Retry, v.tap)
    }

    @Test fun restingKeepsTheLastNumberAndSaysWhenNext() {
        val v = view(HeartRate.State(status = HeartRate.Status.Resting, bpm = 61, lastReadingMs = now - 30_000, nextBurstMs = now + 150_000))
        assertEquals(Kind.Resting, v.kind)
        assertEquals(61, v.bpm)
        assertTrue(v.line.contains("3 min"))
    }

    @Test fun startingIsTheOnlyWaitingLook() {
        val v = view(HeartRate.State(status = HeartRate.Status.Starting))
        assertEquals(Kind.Starting, v.kind)
        assertEquals(Tap.None, v.tap)
    }

    @Test fun offOffersToMeasure() {
        val v = view(HeartRate.State())
        assertEquals(Kind.Off, v.kind)
        assertEquals("Measure", v.short)
        assertEquals(Tap.Start, v.tap)
    }

    @Test fun noSignalOffersToTryAgain() {
        val v = view(HeartRate.State(status = HeartRate.Status.NoSignal))
        assertEquals(Kind.NoSignal, v.kind)
        assertEquals(Tap.Retry, v.tap)
    }

    @Test fun aRefusedLinkIsBlockedWithAReconnect() {
        // The AirPods play sound, but the phone refuses the control link the sensor needs.
        val v = view(HeartRate.State(), link = LinkState.GaveUp("AirPods", "refused"), audio = true)
        assertEquals(Kind.Blocked, v.kind)
        assertEquals(Tap.Reconnect, v.tap)
        // Still opening: blocked for now, but nothing to tap (it's working on it).
        val linking = view(HeartRate.State(), link = LinkState.Connecting("AirPods", 1), audio = true)
        assertEquals(Kind.Blocked, linking.kind)
        assertEquals(Tap.None, linking.tap)
        // Audio up but no link state at all (refused before it got going).
        assertEquals(Kind.Blocked, view(HeartRate.State(), link = LinkState.Idle, audio = true).kind)
    }

    @Test fun noAirPodsNothingToShow() {
        assertEquals(Kind.Away, view(HeartRate.State(), link = LinkState.Idle, audio = false).kind)
    }

    @Test fun everyStateLooksDifferent() {
        val kinds = listOf(
            view(HeartRate.State(status = HeartRate.Status.Live, bpm = 70, lastReadingMs = now)),
            view(HeartRate.State(status = HeartRate.Status.Resting, bpm = 70, lastReadingMs = now)),
            view(HeartRate.State(status = HeartRate.Status.Starting)),
            view(HeartRate.State(status = HeartRate.Status.NoSignal)),
            view(HeartRate.State(), link = LinkState.GaveUp("AirPods", "x")),
            view(HeartRate.State()),
        ).map { it.kind }
        assertEquals(kinds.size, kinds.toSet().size)
    }
}
