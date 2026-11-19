package me.kavishdevar.librepods

import me.kavishdevar.librepods.breathing.BreathAppGate
import me.kavishdevar.librepods.breathing.BreathAppGate.Kind
import me.kavishdevar.librepods.breathing.BreathAppGate.Why
import me.kavishdevar.librepods.breathing.BreathWebApps
import me.kavishdevar.librepods.breathing.PauseMode
import org.junit.Assert.*
import org.junit.Test

/** The pause rule, pinned down: what counts as opening an app, and what is only switching back to it. */
class BreathAppGateTest {
    private val minute = 60_000L
    private val chosen = setOf("gmail", "claude")
    private val normal = BreathAppGate.Rule(PauseMode.NewOpens, 10 * minute)
    private var wall = 1_000_000L

    private fun BreathAppGate.open(pkg: String, now: Long, rule: BreathAppGate.Rule = normal, kind: Kind = Kind.App) =
        enter(pkg, kind, chosen, now, wall, rule)

    /** An app comes to the front and, if it pauses, the pause is drawn (as the service does). */
    private fun BreathAppGate.openAndPause(pkg: String, now: Long, rule: BreathAppGate.Rule = normal): BreathAppGate.Decision =
        open(pkg, now, rule).also { if (it.pause) recordPause(pkg, wall) }

    @Test fun firstOpenPausesThenSwitchingThroughAnotherAppAndBackDoesNot() {
        val gate = BreathAppGate()
        assertEquals(Why.FirstOpen, gate.openAndPause("gmail", 0).why)
        assertEquals(Why.FirstOpen, gate.openAndPause("claude", 10_000).why)
        val back = gate.open("gmail", 20_000)
        assertFalse("Gmail to Claude and back through the swipe or Recents must stay quiet", back.pause)
        assertEquals(Why.Switched, back.why)
    }

    @Test fun closingAnAppAndOpeningItFromHomeAfterAMomentPausesAgain() {
        val gate = BreathAppGate()
        gate.openAndPause("gmail", 0)
        gate.open("launcher", 1_000, kind = Kind.Launcher) // swiped away in Recents, landed on Home
        val again = gate.open("gmail", 4_000)
        assertTrue("closed, then opened again", again.pause)
        assertEquals(Why.FromHome, again.why)
    }

    @Test fun comingBackFromHomeOrRecentsWithinAMomentIsAPeekNotANewOpen() {
        val gate = BreathAppGate()
        gate.openAndPause("gmail", 0)
        gate.open("launcher", 1_000, kind = Kind.Launcher)
        val peek = gate.open("gmail", 1_000 + BreathAppGate.HOME_GAP_MS - 1)
        assertFalse(peek.pause); assertEquals(Why.QuickReturn, peek.why)
        val gate2 = BreathAppGate()
        gate2.openAndPause("gmail", 0)
        gate2.open("launcher", 1_000, kind = Kind.Launcher)
        assertTrue("exactly at the gap it counts", gate2.open("gmail", 1_000 + BreathAppGate.HOME_GAP_MS).pause)
    }

    @Test fun recentsAfterUsingAnotherAppIsStillSwitching() {
        val gate = BreathAppGate()
        gate.openAndPause("gmail", 0)
        gate.openAndPause("claude", 5_000)
        gate.open("launcher", 60_000, kind = Kind.Launcher)   // opened Recents from Claude
        val back = gate.open("gmail", 90_000)                  // a long look at Recents, then Gmail's card
        assertFalse(back.pause); assertEquals(Why.Switched, back.why)
    }

    @Test fun anAppOpenedFromANotificationPausesEvenThoughItWasOnlyAwayBriefly() {
        val gate = BreathAppGate()
        gate.openAndPause("gmail", 0); gate.openAndPause("claude", 5_000)
        gate.notificationWindow(9_000)
        val d = gate.open("gmail", 10_000)
        assertTrue(d.pause); assertEquals(Why.FromNotification, d.why)
        val gate2 = BreathAppGate()
        gate2.openAndPause("gmail", 0); gate2.openAndPause("claude", 5_000)
        gate2.notificationWindow(1_000)
        assertFalse("a shade seen long before is not the reason", gate2.open("gmail", 1_000 + BreathAppGate.NOTIFICATION_MS + 1).pause)
    }

    @Test fun everyTimeAppsPauseWheneverTheyComeForwardAndAfterABreakAppsOnlyAfterTheBreak() {
        val every = BreathAppGate.Rule(PauseMode.EveryTime, 10 * minute)
        val gate = BreathAppGate()
        assertTrue(gate.openAndPause("gmail", 0, every).pause)
        gate.openAndPause("claude", 1_000, every)
        assertEquals(Why.EveryTime, gate.open("gmail", 2_000, every).why)
        val brk = BreathAppGate.Rule(PauseMode.AfterBreak, 10 * minute)
        val g2 = BreathAppGate()
        assertTrue(g2.openAndPause("gmail", 0, brk).pause)
        g2.open("launcher", 1_000, brk, Kind.Launcher)
        val reopened = g2.open("gmail", 9_000, brk)
        assertFalse("even from Home, an after-a-break app waits for its break", reopened.pause); assertEquals(Why.AfterBreak, reopened.why)
        g2.open("launcher", 10_000, brk, Kind.Launcher)
        wall += 10 * minute
        assertEquals(Why.AwayLong, g2.open("gmail", 20_000, brk).why)
    }

    @Test fun theBreakTimeAppliesEvenWhenYouOnlySwitchBack() {
        val gate = BreathAppGate()
        gate.openAndPause("gmail", 0); gate.openAndPause("claude", 1_000)
        wall += 10 * minute - 1
        assertEquals(Why.Switched, gate.open("gmail", 2_000).why)
        gate.openAndPause("claude", 3_000)
        wall += 2
        val d = gate.open("gmail", 4_000)
        assertTrue(d.pause); assertEquals(Why.AwayLong, d.why)
    }

    @Test fun aBreakOfNeverMeansTimeAloneNeverPausesAndAClockSetBackCountsAsLongAgo() {
        val never = BreathAppGate.Rule(PauseMode.NewOpens, 0L)
        val gate = BreathAppGate()
        gate.openAndPause("gmail", 0, never); gate.openAndPause("claude", 1_000, never)
        wall += 5_000 * minute
        assertFalse(gate.open("gmail", 2_000, never).pause)
        val g2 = BreathAppGate()
        g2.recordPause("gmail", wall + 10_000_000)  // recorded in the "future": the clock was set back
        g2.openAndPause("claude", 0)
        assertEquals(Why.AwayLong, g2.open("gmail", 1_000).why)
    }

    @Test fun aFailedAttemptNeverUsesUpTheEntryAndTheRetryIsJudgedAsBefore() {
        val gate = BreathAppGate()
        gate.openAndPause("gmail", 0)
        gate.open("launcher", 1_000, kind = Kind.Launcher)
        assertEquals(Why.FromHome, gate.open("gmail", 5_000).why)
        gate.retry("gmail") // the layer could not be shown
        assertEquals("judged exactly as before, not as a switch", Why.FromHome, gate.open("gmail", 6_000).why)
    }

    @Test fun wakingThePhoneIntoTheSameAppIsJudgedByHowLongItWasOff() {
        val gate = BreathAppGate()
        gate.openAndPause("gmail", 0)
        gate.screenOff(1_000)
        assertEquals(Why.SameApp, gate.open("gmail", 1_000 + 9 * minute).why)
        gate.screenOff(2_000_000)
        val long = gate.open("gmail", 2_000_000 + 10 * minute)
        assertTrue(long.pause); assertEquals(Why.AwayLong, long.why)
        val every = BreathAppGate.Rule(PauseMode.EveryTime, 10 * minute)
        gate.screenOff(9_000_000)
        assertEquals(Why.EveryTime, gate.open("gmail", 9_000_100, every).why)
    }

    @Test fun aPinnedShortcutsPauseCountsAndHandsOverOnce() {
        val gate = BreathAppGate()
        val seen = ArrayList<Pair<String, Long>>()
        gate.onPause = { pkg, at -> seen += pkg to at }
        gate.shortcutCompleted("gmail", 0, 777L)
        assertEquals(listOf("gmail" to 777L), seen)
        assertEquals(Why.Handoff, gate.open("gmail", 10).why)
        gate.open("launcher", 20, kind = Kind.Launcher)
        assertNotEquals("the exemption is used up", Why.Handoff, gate.open("gmail", 30).why)
        val late = BreathAppGate(); late.shortcutCompleted("claude", 0, wall)
        assertNotEquals("and it expires", Why.Handoff, late.open("claude", BreathAppGate.SHORTCUT_HANDOFF_MS + 1).why)
    }

    @Test fun savedHistoryIsRestoredAndAnAppWithNoMemoryOfLeavingCountsAsAFreshOpen() {
        val gate = BreathAppGate().apply { restore(mapOf("gmail" to wall - minute)) }
        val d = gate.open("gmail", 0)
        assertTrue(d.pause); assertEquals(Why.NewSession, d.why)
    }

    @Test fun resetKeepsWhenAppsLastPausedButForgetPausesClearsIt() {
        val gate = BreathAppGate()
        gate.openAndPause("gmail", 0)
        gate.reset()
        assertNotNull(gate.lastPauseOf("gmail"))
        gate.forgetPauses()
        assertNull(gate.lastPauseOf("gmail"))
        assertEquals(Why.FirstOpen, gate.open("gmail", 0).why)
    }

    @Test fun otherAppsAndTheHomeScreenNeverPauseAndTheSameAppNeverRepeats() {
        val gate = BreathAppGate()
        assertEquals(Why.NotChosen, gate.open("calculator", 0).why)
        assertEquals(Why.NotChosen, gate.open("launcher", 1, kind = Kind.Launcher).why)
        gate.openAndPause("gmail", 2)
        assertEquals(Why.SameApp, gate.open("gmail", 3).why)
    }

    @Test fun aWebAppInTheWayCountsAsAnotherAppSoComingBackToTheBrowserIsSwitching() {
        val gate = BreathAppGate()
        val browser = setOf("chrome")
        assertTrue(gate.enter("chrome", Kind.App, browser, 0, wall, normal).pause)
        gate.recordPause("chrome", wall)
        gate.leave(BreathWebApps.marker("chrome"))
        val back = gate.enter("chrome", Kind.App, browser, 5_000, wall, normal)
        assertFalse(back.pause); assertEquals(Why.Switched, back.why)
    }
}
