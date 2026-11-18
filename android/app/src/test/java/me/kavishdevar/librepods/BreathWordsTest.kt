package me.kavishdevar.librepods

import me.kavishdevar.librepods.breathing.BreathWords
import org.junit.Assert.*
import org.junit.Test

class BreathWordsTest {
    @Test fun threeSayingsTheOriginalPlusTwoNew() {
        assertEquals(3, BreathWords.sayings.size)
        assertEquals("Breathe in.", BreathWords.sayings.first().inHead)
        assertEquals(BreathWords.sayings.size, BreathWords.sayings.map { it.inHead }.toSet().size)
    }
    @Test fun everySayingIsShortAndBothMomentsBreakIntoTheSameNumberOfLines() {
        BreathWords.sayings.forEach { s ->
            listOf(s.inHead, s.outHead).forEach { assertTrue("'$it' is too long for big type", it.length <= 14) }
            listOf(s.inNote, s.outNote).forEach { assertTrue("'$it' is too long for a small line", it.length <= 28) }
            assertEquals("'${s.inHead}' / '${s.outHead}' must break alike so the type never jumps",
                BreathWords.split(s.inHead).size, BreathWords.split(s.outHead).size)
            assertTrue(s.lines in 1..2)
        }
    }
    @Test fun noSayingUsesEmojiOrShouting() {
        BreathWords.sayings.flatMap { listOf(it.inHead, it.inNote, it.outHead, it.outNote) }.forEach { text ->
            assertTrue("'$text'", text.all { it.code < 0x2000 })
            assertFalse("'$text'", text.contains("!"))
        }
    }
    @Test fun aMixStepsThroughAllSayingsAndWrapsAround() {
        var last = -1
        val order = List(7) { BreathWords.pick(BreathWords.Mix, last).also { last = it } }
        assertEquals(listOf(0, 1, 2, 0, 1, 2, 0), order)
    }
    @Test fun aPickAlwaysShowsItselfWhateverCameBefore() {
        assertEquals(0, BreathWords.pick(BreathWords.BreatheIn, 0))
        assertEquals(1, BreathWords.pick(BreathWords.SlowDown, 2))
        assertEquals(2, BreathWords.pick(BreathWords.Reset, -1))
    }
    @Test fun bigTypeBreaksAfterTheFirstWordOnlyWhenItHelps() {
        assertEquals(listOf("BREATHE", "IN."), BreathWords.split("Breathe in."))
        assertEquals(listOf("SLOW", "DOWN."), BreathWords.split("Slow down."))
        assertEquals(listOf("NO", "RUSH."), BreathWords.split("No rush."))
        assertEquals(listOf("RESET."), BreathWords.split("Reset."))
    }
    @Test fun unknownSavedWordsFallBackToMix() {
        assertEquals(BreathWords.Mix, BreathWords.from("nonsense"))
        assertEquals(BreathWords.Reset, BreathWords.from("reset"))
    }
}
