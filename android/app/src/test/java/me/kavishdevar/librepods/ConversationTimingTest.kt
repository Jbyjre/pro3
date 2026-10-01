package me.kavishdevar.librepods

import me.kavishdevar.librepods.utils.ConversationTiming
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationTimingTest {
    @Test fun waitsLongerDuringABackAndForthButNotForever() {
        assertEquals(2_000L, ConversationTiming.restoreDelayMs(pauseMode = false, turns = 1))
        assertEquals(2_750L, ConversationTiming.restoreDelayMs(pauseMode = false, turns = 2))
        assertEquals(5_000L, ConversationTiming.restoreDelayMs(pauseMode = false, turns = 20))
        assertEquals(3_000L, ConversationTiming.restoreDelayMs(pauseMode = true, turns = 1))
        assertEquals(8_000L, ConversationTiming.restoreDelayMs(pauseMode = true, turns = 20))
        assertEquals(2_000L, ConversationTiming.restoreDelayMs(pauseMode = false, turns = 0))
    }

    @Test fun downIsQuickUpSwellsBackOverAboutASecond() {
        val down = ConversationTiming.rampDelays(10, 4)
        assertEquals(6, down.size)
        assertTrue(down.all { it == 35L })
        val up = ConversationTiming.rampDelays(4, 12)
        assertEquals(8, up.size)
        assertTrue(up.sum() in 450L..1_300L)
        // Eased: the middle steps come faster than the first and last.
        assertTrue(up[up.size / 2] < up.first() && up[up.size / 2] < up.last())
        assertEquals(emptyList<Long>(), ConversationTiming.rampDelays(5, 5))
    }
}
