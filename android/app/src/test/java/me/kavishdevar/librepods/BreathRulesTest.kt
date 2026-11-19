package me.kavishdevar.librepods

import me.kavishdevar.librepods.breathing.AppRule
import me.kavishdevar.librepods.breathing.BreathRules
import me.kavishdevar.librepods.breathing.PauseMode
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Android's own JSON library, so it runs under Robolectric. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = android.app.Application::class)
class BreathRulesTest {
    @Test fun rulesSurviveEncodingAndDecoding() {
        val rules = mapOf("gmail" to AppRule(PauseMode.EveryTime, 12_000), "chess" to AppRule(durationMs = 30_000), "chat" to AppRule(mode = PauseMode.AfterBreak))
        assertEquals(rules, BreathRules.decode(BreathRules.encode(rules)))
    }
    @Test fun anEmptyRuleIsNotStoredAndBadTextIsNoRulesNeverACrash() {
        assertEquals("{}", BreathRules.encode(mapOf("gmail" to AppRule())))
        assertTrue(BreathRules.decode(null).isEmpty())
        assertTrue(BreathRules.decode("not json {").isEmpty())
        assertTrue(BreathRules.decode("""{"gmail":"oops","x":{"mode":"future"}}""").isEmpty())
    }
    @Test fun anAppsLengthIsKeptWithinWhatMakesSense() {
        assertEquals(30_000L, BreathRules.decode(BreathRules.encode(mapOf("a" to AppRule(durationMs = 600_000)))).getValue("a").durationMs)
        assertEquals(2_000L, BreathRules.decode("""{"a":{"ms":5}}""").getValue("a").durationMs)
    }
}
