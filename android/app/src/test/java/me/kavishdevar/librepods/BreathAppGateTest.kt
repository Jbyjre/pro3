package me.kavishdevar.librepods

import me.kavishdevar.librepods.breathing.BreathAppGate
import me.kavishdevar.librepods.breathing.BreathWebApps
import org.junit.Assert.*
import org.junit.Test

class BreathAppGateTest {
    @Test fun pausesOnEntryAndReentryButNotInternalActivityTransitions() {
        val gate = BreathAppGate()
        val selected = setOf("chat", "chess")
        assertTrue(gate.enter("chat", selected, 0))
        assertFalse(gate.enter("chat", selected, 3000))
        assertFalse(gate.enter("home", selected, 3100))
        assertTrue(gate.enter("chat", selected, 3200))
        assertTrue(gate.enter("chess", selected, 3300))
    }
    @Test fun shortcutExemptionIsConsumedAndExpires() {
        val gate = BreathAppGate()
        val selected = setOf("chat")
        gate.shortcutCompleted("chat", 0)
        assertFalse(gate.enter("chat", selected, 10))
        gate.enter("home", selected, 20)
        assertTrue(gate.enter("chat", selected, 30))
        gate.reset()
        gate.shortcutCompleted("chat", 0)
        assertTrue(gate.enter("chat", selected, 5000))
    }

    private val minute = 60_000L
    @Test fun anAppPausesOnFirstOpenThenStaysQuietWhileYouMoveBetweenAppsUntilTheGraceTimePasses() {
        val gate = BreathAppGate()
        val selected = setOf("gmail", "claude")
        val grace = 10 * minute
        var wall = 1_000_000L
        assertTrue(gate.enter("gmail", selected, 0, wall, grace)); gate.recordPause("gmail", wall)
        wall += minute
        assertTrue(gate.enter("claude", selected, 1, wall, grace)); gate.recordPause("claude", wall)
        wall += 2 * minute
        assertFalse("back to Gmail 3 minutes in", gate.enter("gmail", selected, 2, wall, grace))
        wall += 2 * minute
        assertFalse("and to Claude again", gate.enter("claude", selected, 3, wall, grace))
        wall = 1_000_000L + 10 * minute - 1
        assertFalse("one millisecond before Gmail's ten minutes", gate.enter("gmail", selected, 4, wall, grace))
        wall = 1_000_000L + 10 * minute
        assertFalse("claude paused a minute later, so still quiet", gate.enter("claude", selected, 5, wall, grace))
        assertTrue("Gmail's ten minutes are up", gate.enter("gmail", selected, 6, wall, grace))
    }
    @Test fun aFailedAttemptNeverUsesUpTheGraceTime() {
        val gate = BreathAppGate()
        assertTrue(gate.enter("gmail", setOf("gmail"), 0, 5_000, 10 * minute))
        gate.retry("gmail") // the layer could not be shown, nothing was recorded
        assertTrue(gate.enter("gmail", setOf("gmail"), 1, 6_000, 10 * minute))
    }
    @Test fun aClockSetBackwardsCountsAsLongAgoNotAsNeverAgain() {
        val gate = BreathAppGate()
        gate.recordPause("gmail", 10_000_000L)
        assertTrue(gate.enter("gmail", setOf("gmail"), 0, 1_000L, 10 * minute))
    }
    @Test fun graceZeroMeansEveryOpenAndSavedHistoryIsRestored() {
        val gate = BreathAppGate()
        gate.recordPause("gmail", 100L)
        assertTrue(gate.enter("gmail", setOf("gmail"), 0, 101L, 0L))
        val restored = BreathAppGate().apply { restore(mapOf("gmail" to 100L)) }
        assertFalse(restored.enter("gmail", setOf("gmail"), 0, 101L, 10 * minute))
    }
    @Test fun resetKeepsTheMemoryOfPausesButForgetPausesClearsIt() {
        val gate = BreathAppGate()
        gate.recordPause("gmail", 100L)
        gate.reset()
        assertFalse(gate.enter("gmail", setOf("gmail"), 0, 200L, 10 * minute))
        gate.forgetPauses(); gate.reset()
        assertTrue(gate.enter("gmail", setOf("gmail"), 1, 300L, 10 * minute))
    }
    @Test fun aShortcutPauseCountsAndEveryPauseIsReported() {
        val gate = BreathAppGate()
        val seen = ArrayList<Pair<String, Long>>()
        gate.onPause = { pkg, at -> seen += pkg to at }
        gate.shortcutCompleted("gmail", 0, 777L)
        assertEquals(listOf("gmail" to 777L), seen)
        gate.reset()
        assertFalse("the shortcut's pause is this app's pause", gate.enter("gmail", setOf("gmail"), 10_000, 778L, 10 * minute))
    }
    @Test fun leavingForAWebAppMakesTheBrowserANewEntryButStillRespectsTheGraceTime() {
        val gate = BreathAppGate()
        val chrome = setOf("chrome")
        assertTrue(gate.enter("chrome", chrome, 0, 1_000L, 10 * minute)); gate.recordPause("chrome", 1_000L)
        gate.leave(BreathWebApps.marker("chrome"))
        assertFalse(gate.enter("chrome", chrome, 1, 2_000L, 10 * minute))
        gate.leave(BreathWebApps.marker("chrome"))
        assertTrue(gate.enter("chrome", chrome, 2, 1_000L + 10 * minute, 10 * minute))
    }
}
