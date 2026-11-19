package me.kavishdevar.librepods

import me.kavishdevar.librepods.breathing.BreathOptions
import me.kavishdevar.librepods.breathing.BreathWords
import org.junit.Assert.*
import org.junit.Test

class BreathWordsTest {
    @Test fun theDefaultIsOnlyTheVerseReferenceOnePhraseThatNeverChanges() {
        val saying = BreathOptions().saying
        assertEquals("Psalm 51:10", saying.inHead)
        assertEquals("the same words while the color rises and while it clears", saying.inHead, saying.outHead)
        assertEquals("", saying.inNote); assertEquals("", saying.outNote)
        assertEquals(listOf("PSALM", "51:10"), BreathWords.split(saying.inHead))
    }
    @Test fun theVerseItselfIsNeverPrinted() {
        val all = BreathWords.entries.mapNotNull { it.saying }.flatMap { listOf(it.inHead, it.inNote, it.outHead, it.outNote) }
        listOf("create in me", "pure heart", "steadfast", "renew").forEach { forbidden ->
            all.forEach { assertFalse("'$it' must not quote the verse", it.contains(forbidden, ignoreCase = true)) }
        }
    }
    @Test fun anOlderSavedMixAndAnythingUnknownBecomesTheVerse() {
        assertEquals(BreathWords.Verse, BreathWords.from("mix"))
        assertEquals(BreathWords.Verse, BreathWords.from("nonsense"))
        assertEquals(BreathWords.Verse, BreathWords.from(null))
        assertEquals(BreathWords.Reset, BreathWords.from("reset"))
    }
    @Test fun yourOwnWordsAreCleanedShortAndFallBackToTheVerseWhenEmpty() {
        assertEquals("Be still", BreathWords.sayingFor(BreathWords.Custom, "  Be   still ").inHead)
        assertEquals(BreathOptions.CUSTOM_TEXT_MAX, BreathOptions.cleanCustom("x".repeat(100)).length)
        assertEquals("Psalm 51:10", BreathWords.sayingFor(BreathWords.Custom, "   ").inHead)
        val own = BreathWords.sayingFor(BreathWords.Custom, "John 3:16")
        assertEquals("both moments the same, so it never swaps", own.inHead, own.outHead)
    }
    @Test fun theOlderSayingsStillBreakIntoMatchingLinesAndStayShort() {
        listOf(BreathWords.BreatheIn, BreathWords.SlowDown, BreathWords.Reset).mapNotNull { it.saying }.forEach { s ->
            listOf(s.inHead, s.outHead).forEach { assertTrue("'$it'", it.length <= 14) }
            assertEquals(BreathWords.split(s.inHead).size, BreathWords.split(s.outHead).size)
        }
    }
    @Test fun bigTypeBreaksAfterTheFirstWordOnlyWhenItHelps() {
        assertEquals(listOf("BREATHE", "IN."), BreathWords.split("Breathe in."))
        assertEquals(listOf("RESET."), BreathWords.split("Reset."))
        assertEquals(listOf("PSALM", "51:10"), BreathWords.split("Psalm 51:10"))
    }
}
