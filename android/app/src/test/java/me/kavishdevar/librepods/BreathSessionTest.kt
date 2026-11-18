package me.kavishdevar.librepods

import me.kavishdevar.librepods.breathing.BreathSession
import org.junit.Assert.*
import org.junit.Test

class BreathSessionTest {
    @Test fun threeSecondsFromFirstFrameAndNoSecondDelivery() {
        val session = BreathSession()
        assertFalse(session.complete(5000))
        session.firstFrame(5000)
        session.firstFrame(5100)
        assertEquals(1L, session.remaining(7999))
        assertFalse(session.complete(7999))
        assertTrue(session.complete(8000))
        assertFalse(session.complete(10000))
    }
    @Test fun leavingNeverLaunchesEvenIfDeadlinePassed() {
        val session = BreathSession()
        session.firstFrame(0)
        session.cancel()
        assertFalse(session.complete(5000))
    }
    @Test fun theChosenLengthIsTheWholeRun() {
        val session = BreathSession(5_000)
        session.firstFrame(1_000)
        assertEquals(1L, session.remaining(5_999))
        assertTrue(session.complete(6_000))
    }
    @Test fun aLengthChangedBeforeTheFirstFrameIsUsed() {
        val session = BreathSession()
        session.durationMs = 2_000
        session.firstFrame(0)
        assertTrue(session.complete(2_000))
    }
}
