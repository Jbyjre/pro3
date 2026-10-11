package me.kavishdevar.librepods

import me.kavishdevar.librepods.services.CommandFeedback
import me.kavishdevar.librepods.services.CommandFeedback.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CommandFeedbackTest {
    private val mode = CommandFeedback.LISTENING_MODE

    @Before fun reset() {
        CommandFeedback.reset()
        CommandFeedback.resend = null
        CommandFeedback.restore = null
    }

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
        assertEquals("AirPods stayed in Noise Cancellation", n.text)
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
        assertEquals("AirPods didn't switch to Transparency", n.text)
    }

    @Test fun stemChangesWithoutARequestStayQuiet() {
        CommandFeedback.received(mode, byteArrayOf(2))
        assertEquals(null, CommandFeedback.notice.value)
    }

    // ---- 2026-10-11: every sender, retry, undo, quick taps ----

    @Test fun twoQuickTapsCountOnlyTheLastOne() {
        CommandFeedback.received(mode, byteArrayOf(3)) // the AirPods are in Transparency
        CommandFeedback.sent(mode, byteArrayOf(2), sent = true) // tap: Noise Cancellation
        CommandFeedback.sent(mode, byteArrayOf(4), sent = true) // tap again: Adaptive
        assertEquals(4, CommandFeedback.pending)
        // The answer to the first tap arrives: not an error, and not shown (no flicker).
        assertFalse(CommandFeedback.received(mode, byteArrayOf(2)))
        assertEquals(Kind.Pending, CommandFeedback.notice.value!!.kind)
        // The old mode before the switch lands: not trusted either.
        assertFalse(CommandFeedback.received(mode, byteArrayOf(3)))
        assertTrue(CommandFeedback.received(mode, byteArrayOf(4)))
        assertEquals("Adaptive is on", CommandFeedback.notice.value!!.text)
        assertEquals(null, CommandFeedback.pending)
    }

    @Test fun noAnswerSendsOnceMoreThenPutsTheScreensBack() {
        val resent = mutableListOf<Int>()
        val restored = mutableListOf<Int>()
        CommandFeedback.resend = { resent += it; true }
        CommandFeedback.restore = { restored += it }
        CommandFeedback.sending(mode, byteArrayOf(2), shown = byteArrayOf(3))
        CommandFeedback.sent(mode, byteArrayOf(2), sent = true)
        assertTrue(CommandFeedback.retryDue(2))
        assertTrue(CommandFeedback.retryDue(2)) // only once
        assertEquals(listOf(2), resent)
        CommandFeedback.timedOut(2)
        assertEquals(listOf(3), restored)
        assertEquals("AirPods didn't switch to Noise Cancellation", CommandFeedback.notice.value!!.text)
    }

    @Test fun aFailedSendPutsTheScreensBackAtOnce() {
        val restored = mutableListOf<Int>()
        CommandFeedback.restore = { restored += it }
        CommandFeedback.received(mode, byteArrayOf(1))
        CommandFeedback.sending(mode, byteArrayOf(2), shown = byteArrayOf(1))
        CommandFeedback.sent(mode, byteArrayOf(2), sent = false)
        assertEquals(listOf(1), restored)
        assertEquals(null, CommandFeedback.pending)
    }

    @Test fun aLostLinkOnTheRetrySaysSoAtOnce() {
        val restored = mutableListOf<Int>()
        CommandFeedback.resend = { false }
        CommandFeedback.restore = { restored += it }
        CommandFeedback.received(mode, byteArrayOf(3))
        CommandFeedback.sent(mode, byteArrayOf(2), sent = true)
        assertFalse(CommandFeedback.retryDue(2))
        assertEquals(listOf(3), restored)
        assertEquals(Kind.Failed, CommandFeedback.notice.value!!.kind)
    }

    @Test fun aStemSqueezeDuringASwitchWins() {
        CommandFeedback.received(mode, byteArrayOf(3))
        CommandFeedback.sent(mode, byteArrayOf(2), sent = true)
        // The AirPods went to Off on their own: that's the truth, shown.
        assertTrue(CommandFeedback.received(mode, byteArrayOf(1)))
        assertEquals("AirPods switched to Off instead", CommandFeedback.notice.value!!.text)
        assertEquals(null, CommandFeedback.pending)
    }
}
