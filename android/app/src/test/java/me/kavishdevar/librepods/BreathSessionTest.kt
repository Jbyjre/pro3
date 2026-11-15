package me.kavishdevar.librepods

import me.kavishdevar.librepods.breathing.BreathSession
import org.junit.Assert.*
import org.junit.Test

class BreathSessionTest {
    @Test fun oneSecondFromFirstFrameAndNoSecondDelivery() {
        val session = BreathSession()
        assertFalse(session.complete(5000))
        session.firstFrame(5000)
        session.firstFrame(5100)
        assertEquals(1L, session.remaining(5999))
        assertFalse(session.complete(5999))
        assertTrue(session.complete(6000))
        assertFalse(session.complete(10000))
    }
    @Test fun leavingNeverLaunchesEvenIfDeadlinePassed() {
        val session = BreathSession()
        session.firstFrame(0)
        session.cancel()
        assertFalse(session.complete(1000))
    }
}
