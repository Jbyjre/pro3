package me.kavishdevar.librepods

import me.kavishdevar.librepods.breathing.BreathSession
import org.junit.Assert.*
import org.junit.Test

class BreathSessionTest {
    @Test fun twoAndAHalfSecondsFromFirstFrameAndNoSecondDelivery() {
        val session = BreathSession()
        assertFalse(session.complete(5000))
        session.firstFrame(5000)
        session.firstFrame(5100)
        assertEquals(1L, session.remaining(7499))
        assertFalse(session.complete(7499))
        assertTrue(session.complete(7500))
        assertFalse(session.complete(10000))
    }
    @Test fun leavingNeverLaunchesEvenIfDeadlinePassed() {
        val session = BreathSession()
        session.firstFrame(0)
        session.cancel()
        assertFalse(session.complete(1000))
    }
}
