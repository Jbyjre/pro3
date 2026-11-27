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
    private val normal = BreathAppGate.Rule(PauseMode.NewOpens, 15 * minute)
    private val noCooldown = BreathAppGate.Rule(PauseMode.NewOpens, 0L)
    private var wall = 1_000_000L

    private fun BreathAppGate.open(pkg: String, now: Long, rule: BreathAppGate.Rule = normal, kind: Kind = Kind.App) =
        enter(pkg, kind, chosen, now, wall, rule)

    /** An app comes to the front and, if it pauses, the pause is drawn (as the service does). */
    private fun BreathAppGate.openAndPause(pkg: String, now: Long, rule: BreathAppGate.Rule = normal): BreathAppGate.Decision =
        open(pkg, now, rule).also { if (it.pause) recordPause(pkg, wall) }

    @Test fun firstOpenPausesThenFifteenMinuteCooldownBlocksAllChosenAppsUntilCooldownEnds() {
        val gate = BreathAppGate()
        assertEquals(Why.FirstOpen, gate.openAndPause("gmail", 0).why)
        // Within 15 minutes: opening another chosen app, reopening from Home, or tapping a notification stays quiet.
        assertEquals(Why.Cooldown, gate.open("claude", 10_000).why)
        gate.open("launcher", 12_000, kind = Kind.Launcher)
        wall += 14 * minute + 59_999L
        assertEquals(Why.Cooldown, gate.open("gmail", 20_000).why)
        gate.notificationWindow(25_000)
        assertEquals(Why.Cooldown, gate.open("claude", 26_000).why)
        // Exactly at 15 minutes since the first pause: opening again pauses and starts a fresh 15-minute cooldown.
        wall += 1L
        gate.open("launcher", 30_000, kind = Kind.Launcher)
        val reopened = gate.openAndPause("gmail", 35_000)
        assertTrue(reopened.pause)
        assertEquals(Why.AwayLong, reopened.why)
        assertEquals(Why.Cooldown, gate.open("claude", 36_000).why)
    }

    @Test fun versePopupCanOpenAtMostFourTimesInAnyOneHourWindow() {
        val gate = BreathAppGate()
        val apps = setOf("gmail", "claude", "youtube", "instagram")
        var pauses = 0
        val pauseMinutes = ArrayList<Int>()
        // Every minute for 60 minutes (minutes 0..59), leave to Home and open a chosen app (or tap a notification).
        for (m in 0 until 60) {
            val mono = m * minute
            val currentWall = wall + mono
            gate.enter("launcher", Kind.Launcher, apps, mono, currentWall, normal)
            if (m % 3 == 0) gate.notificationWindow(mono + 1_500L)
            val app = when (m % 4) { 0 -> "gmail"; 1 -> "claude"; 2 -> "youtube"; else -> "instagram" }
            val decision = gate.enter(app, Kind.App, apps, mono + 2_000L, currentWall, normal)
            if (decision.pause) {
                pauses++
                pauseMinutes += m
                gate.recordPause(app, currentWall)
            }
        }
        assertEquals("15-minute cooldown caps pauses at 4 per hour", 4, pauses)
        assertEquals(listOf(0, 15, 30, 45), pauseMinutes)
    }

    @Test fun switchingThroughAnotherAppAndBackDoesNotPauseWhenCooldownIsOff() {
        val gate = BreathAppGate()
        assertEquals(Why.FirstOpen, gate.openAndPause("gmail", 0, noCooldown).why)
        assertEquals(Why.FirstOpen, gate.openAndPause("claude", 10_000, noCooldown).why)
        val back = gate.open("gmail", 20_000, noCooldown)
        assertFalse("Gmail to Claude and back through the swipe or Recents must stay quiet", back.pause)
        assertEquals(Why.Switched, back.why)
    }

    @Test fun closingAnAppAndOpeningItFromHomeAfterCooldownPausesAgain() {
        val gate = BreathAppGate()
        gate.openAndPause("gmail", 0)
        gate.open("launcher", 1_000, kind = Kind.Launcher) // swiped away in Recents, landed on Home
        wall += 15 * minute
        val again = gate.open("gmail", 15 * minute + 4_000)
        assertTrue("closed, then opened again after cooldown", again.pause)
        assertEquals(Why.FromHome, again.why)
    }

    @Test fun comingBackFromHomeOrRecentsWithinAMomentIsAPeekNotANewOpen() {
        val gate = BreathAppGate()
        gate.openAndPause("gmail", 0)
        wall += 15 * minute
        val base = 15 * minute
        gate.open("launcher", base, kind = Kind.Launcher)
        val peek = gate.open("gmail", base + BreathAppGate.HOME_GAP_MS - 1)
        assertFalse(peek.pause); assertEquals(Why.QuickReturn, peek.why)
        val gate2 = BreathAppGate()
        gate2.openAndPause("gmail", 0)
        wall += 15 * minute
        gate2.open("launcher", base, kind = Kind.Launcher)
        assertTrue("exactly at the gap it counts", gate2.open("gmail", base + BreathAppGate.HOME_GAP_MS).pause)
    }

    @Test fun recentsAfterUsingAnotherAppIsStillSwitching() {
        val gate = BreathAppGate()
        gate.openAndPause("gmail", 0, noCooldown)
        gate.openAndPause("claude", 5_000, noCooldown)
        gate.open("launcher", 60_000, noCooldown, Kind.Launcher)   // opened Recents from Claude
        val back = gate.open("gmail", 90_000, noCooldown)          // a long look at Recents, then Gmail's card
        assertFalse(back.pause); assertEquals(Why.Switched, back.why)
    }

    @Test fun anAppOpenedFromANotificationPausesOnceCooldownAllowsIt() {
        val gate = BreathAppGate()
        gate.openAndPause("gmail", 0)
        gate.open("claude", 5_000)
        wall += 15 * minute
        gate.notificationWindow(15 * minute + 9_000)
        val d = gate.open("gmail", 15 * minute + 10_000)
        assertTrue(d.pause); assertEquals(Why.FromNotification, d.why)
        val gate2 = BreathAppGate()
        gate2.openAndPause("gmail", 0, noCooldown); gate2.openAndPause("claude", 5_000, noCooldown)
        gate2.notificationWindow(1_000)
        assertFalse("a shade seen long before is not the reason", gate2.open("gmail", 1_000 + BreathAppGate.NOTIFICATION_MS + 1, noCooldown).pause)
    }

    @Test fun everyTimeAppsPauseWheneverTheyComeForwardAndAfterABreakAppsOnlyAfterTheBreak() {
        val every = BreathAppGate.Rule(PauseMode.EveryTime, 15 * minute)
        val gate = BreathAppGate()
        assertTrue(gate.openAndPause("gmail", 0, every).pause)
        gate.openAndPause("claude", 1_000, every)
        assertEquals(Why.EveryTime, gate.open("gmail", 2_000, every).why)
        val brk = BreathAppGate.Rule(PauseMode.AfterBreak, 15 * minute)
        val g2 = BreathAppGate()
        assertTrue(g2.openAndPause("gmail", 0, brk).pause)
        g2.open("launcher", 1_000, brk, Kind.Launcher)
        val reopened = g2.open("gmail", 9_000, brk)
        assertFalse("even from Home, an after-a-break app waits for its cooldown", reopened.pause); assertEquals(Why.Cooldown, reopened.why)
        g2.open("launcher", 10_000, brk, Kind.Launcher)
        wall += 15 * minute
        assertEquals(Why.AwayLong, g2.open("gmail", 20_000, brk).why)
        val brkForever = BreathAppGate.Rule(PauseMode.AfterBreak, 0L)
        val g3 = BreathAppGate()
        assertTrue(g3.openAndPause("gmail", 0, brkForever).pause)
        g3.open("launcher", 1_000, brkForever, Kind.Launcher)
        assertEquals(Why.AfterBreak, g3.open("gmail", 9_000, brkForever).why)
    }

    @Test fun theCooldownTimeBringsThePauseBackEvenWhenYouOnlySwitchBack() {
        val gate = BreathAppGate()
        gate.openAndPause("gmail", 0); gate.open("claude", 1_000)
        wall += 15 * minute - 1
        assertEquals(Why.Cooldown, gate.open("gmail", 2_000).why)
        gate.open("claude", 3_000)
        wall += 2
        val d = gate.open("gmail", 4_000)
        assertTrue(d.pause); assertEquals(Why.AwayLong, d.why)
    }

    @Test fun aCooldownOfOffMeansTimeAloneNeverPausesAndAClockSetBackCountsAsLongAgo() {
        val gate = BreathAppGate()
        gate.openAndPause("gmail", 0, noCooldown); gate.openAndPause("claude", 1_000, noCooldown)
        wall += 5_000 * minute
        assertFalse(gate.open("gmail", 2_000, noCooldown).pause)
        val g2 = BreathAppGate()
        val future = wall + 10_000_000
        g2.enter("gmail", Kind.App, chosen, 0, future, normal)
        g2.recordPause("gmail", future)  // recorded in the "future": the clock was set back afterwards
        g2.enter("calculator", Kind.App, chosen, 500, wall, normal)
        assertEquals(Why.AwayLong, g2.open("gmail", 1_000).why)
    }

    @Test fun aFailedAttemptNeverUsesUpTheEntryOrStartsCooldownAndTheRetryIsJudgedAsBefore() {
        val gate = BreathAppGate()
        gate.openAndPause("gmail", 0)
        wall += 15 * minute
        gate.open("launcher", 1_000, kind = Kind.Launcher)
        assertEquals(Why.FromHome, gate.open("gmail", 5_000).why)
        gate.retry("gmail") // the layer could not be shown; recordPause was never called
        assertEquals("judged exactly as before, not as a switch or cooldown", Why.FromHome, gate.open("gmail", 6_000).why)
    }

    @Test fun wakingThePhoneIntoTheSameAppIsJudgedByHowLongItWasOffAndCooldown() {
        val gate = BreathAppGate()
        gate.openAndPause("gmail", 0)
        gate.screenOff(1_000)
        wall += 14 * minute
        assertEquals(Why.SameApp, gate.open("gmail", 1_000 + 14 * minute).why)
        gate.screenOff(2_000_000)
        wall += 15 * minute
        val long = gate.open("gmail", 2_000_000 + 15 * minute)
        assertTrue(long.pause); assertEquals(Why.AwayLong, long.why)
        val every = BreathAppGate.Rule(PauseMode.EveryTime, 15 * minute)
        gate.screenOff(9_000_000)
        assertEquals(Why.EveryTime, gate.open("gmail", 9_000_100, every).why)
    }

    @Test fun aPinnedShortcutsPauseCountsStartsCooldownAndHandsOverOnce() {
        val gate = BreathAppGate()
        val seen = ArrayList<Pair<String, Long>>()
        gate.onPause = { pkg, at -> seen += pkg to at }
        gate.shortcutCompleted("gmail", 0, wall)
        assertEquals(listOf("gmail" to wall), seen)
        assertEquals(Why.Handoff, gate.open("gmail", 10).why)
        gate.open("launcher", 20, kind = Kind.Launcher)
        assertEquals("the shortcut also started the 15-minute cooldown", Why.Cooldown, gate.open("gmail", 30).why)
        wall += 15 * minute
        assertNotEquals("after cooldown, the handoff exemption has long expired", Why.Handoff, gate.open("gmail", 15 * minute + 30).why)
        val late = BreathAppGate(); late.shortcutCompleted("claude", 0, wall)
        wall += 15 * minute
        assertNotEquals("and it expires", Why.Handoff, late.open("claude", BreathAppGate.SHORTCUT_HANDOFF_MS + 1).why)
    }

    @Test fun savedHistoryRestoresCooldownAndAfterCooldownTreatsUnknownAppAsFreshOpen() {
        val gate = BreathAppGate().apply { restore(mapOf("gmail" to wall - minute)) }
        val duringCooldown = gate.open("gmail", 0)
        assertFalse("service restart inside the 15-minute cooldown must keep cooling down", duringCooldown.pause)
        assertEquals(Why.Cooldown, duringCooldown.why)
        val afterCooldown = BreathAppGate().apply { restore(mapOf("gmail" to wall - 15 * minute)) }
        val d = afterCooldown.open("gmail", 0)
        assertTrue(d.pause); assertEquals(Why.NewSession, d.why)
    }

    @Test fun resetKeepsWhenAppsLastPausedButForgetPausesClearsIt() {
        val gate = BreathAppGate()
        gate.openAndPause("gmail", 0)
        gate.reset()
        assertNotNull(gate.lastPauseOf("gmail"))
        assertNotNull(gate.lastPauseTime())
        gate.forgetPauses()
        assertNull(gate.lastPauseOf("gmail"))
        assertNull(gate.lastPauseTime())
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
        assertTrue(gate.enter("chrome", Kind.App, browser, 0, wall, noCooldown).pause)
        gate.recordPause("chrome", wall)
        gate.leave(BreathWebApps.marker("chrome"))
        val back = gate.enter("chrome", Kind.App, browser, 5_000, wall, noCooldown)
        assertFalse(back.pause); assertEquals(Why.Switched, back.why)
    }
}
