package me.kavishdevar.librepods

import me.kavishdevar.librepods.services.CommandFeedback
import me.kavishdevar.librepods.services.CommandFeedback.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CommandFeedbackTest {
    private val mode = CommandFeedback.LISTENING_MODE

    @Before fun reset() = CommandFeedback.clear()

    @Test fun confirmedWhenTheAirPodsAnswerWithTheSameMode() {
        CommandFeedback.sent(mode, byteArrayOf(4), sent = true)
        assertEquals(Kind.Pending, CommandFeedback.notice.value!!.kind)
        CommandFeedback.received(mode, byteArrayOf(4))
        assertEquals(Kind.Done, CommandFeedback.notice.value!!.kind)
        assertEquals("Adaptive is on", CommandFeedback.notice.value!!.text)
    }

    @Test fun saysWhenTheAirPodsStayInAnotherMode() {
        CommandFeedback.sent(mode, byteArrayOf(1), sent = true)
        CommandFeedback.received(mode, byteArrayOf(2))
        val n = CommandFeedback.notice.value!!
        assertEquals(Kind.Failed, n.kind)
        assertTrue(n.text.startsWith("Your AirPods stayed in Noise Cancellation"))
    }

    @Test fun saysWhenNothingCouldBeSent() {
        CommandFeedback.sent(mode, byteArrayOf(3), sent = false)
        assertEquals(Kind.Failed, CommandFeedback.notice.value!!.kind)
    }

    @Test fun saysWhenNoAnswerArrives() {
        CommandFeedback.sent(mode, byteArrayOf(3), sent = true)
        CommandFeedback.timedOut(3)
        val n = CommandFeedback.notice.value!!
        assertEquals(Kind.Failed, n.kind)
        assertTrue(n.text.contains("didn't confirm Transparency"))
    }

    @Test fun stemChangesWithoutARequestStayQuiet() {
        CommandFeedback.received(mode, byteArrayOf(2))
        assertEquals(null, CommandFeedback.notice.value)
    }
}
