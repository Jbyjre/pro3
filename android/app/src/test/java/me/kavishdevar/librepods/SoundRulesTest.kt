package me.kavishdevar.librepods

import me.kavishdevar.librepods.services.SoundRules
import me.kavishdevar.librepods.services.SoundRules.Basis
import me.kavishdevar.librepods.services.SoundRules.Evidence
import me.kavishdevar.librepods.services.SoundRules.Kind
import me.kavishdevar.librepods.services.SoundRules.Role
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** "Any sound pops the Dynamic Island, with the app that made it": the rules, without a phone. */
class SoundRulesTest {
    @Test fun androidUsagesBecomeKinds() {
        assertEquals(Kind.Media, SoundRules.kindOf(1))
        assertEquals(Kind.Game, SoundRules.kindOf(14))
        assertEquals(Kind.Other, SoundRules.kindOf(0))
        assertEquals(Kind.Call, SoundRules.kindOf(2))
        assertEquals(Kind.Call, SoundRules.kindOf(3))
        assertEquals(Kind.Call, SoundRules.kindOf(6))
        assertEquals(Kind.Alarm, SoundRules.kindOf(4))
        for (u in intArrayOf(5, 7, 8, 9, 10)) assertEquals(Kind.Alert, SoundRules.kindOf(u))
        for (u in intArrayOf(12, 16)) assertEquals(Kind.Voice, SoundRules.kindOf(u))
        // Keyboard and touch clicks, a screen reader's speech and virtual sources never pop the island.
        assertNull(SoundRules.kindOf(13))
        assertNull(SoundRules.kindOf(11))
        assertNull(SoundRules.kindOf(15))
        // A number Android adds one day is still a sound.
        assertEquals(Kind.Other, SoundRules.kindOf(99))
    }

    @Test fun severalSoundsAtOnceShowTheMostUrgent() {
        // A message ding over music: the alert leads, and the music is still known to be there.
        val s = SoundRules.summarize(listOf(1, 5))!!
        assertEquals(Kind.Alert, s.kind)
        assertTrue(s.musicLike)
        // A call beats an alarm beats an alert.
        assertEquals(Kind.Call, SoundRules.summarize(listOf(5, 4, 2))!!.kind)
        assertEquals(Kind.Alarm, SoundRules.summarize(listOf(5, 4))!!.kind)
        // Only clicks, or nothing: nothing to show.
        assertNull(SoundRules.summarize(listOf(13, 13)))
        assertNull(SoundRules.summarize(emptyList()))
        assertFalse(SoundRules.summarize(listOf(5))!!.musicLike)
    }

    @Test fun musicTrustsTheSessionThenTheScreen_alertsTrustTheNotification() {
        val all = Evidence(session = "spotify", notification = "whatsapp", foreground = "chrome")
        assertEquals("spotify" to Basis.Session, SoundRules.attribute(Kind.Media, all))
        assertEquals("whatsapp" to Basis.Notification, SoundRules.attribute(Kind.Alert, all))
        // No session: music falls to the app on screen, then to a notification.
        assertEquals("chrome" to Basis.Foreground, SoundRules.attribute(Kind.Media, all.copy(session = null)))
        assertEquals("whatsapp" to Basis.Notification, SoundRules.attribute(Kind.Media, all.copy(session = null, foreground = null)))
        // An alert with no notification clue: the session, then the screen.
        assertEquals("chrome" to Basis.Foreground, SoundRules.attribute(Kind.Alert, all.copy(notification = null, session = null)))
        assertEquals(null to Basis.None, SoundRules.attribute(Kind.Alert, Evidence(null, null, null)))
    }

    @Test fun skippedPackagesNeverCountAndTheNextClueIsUsed() {
        val e = Evidence(session = "pro", notification = "whatsapp", foreground = "pro")
        val skip: (String) -> Boolean = { it == "pro" }
        assertEquals("whatsapp" to Basis.Notification, SoundRules.attribute(Kind.Media, e, skip))
        assertEquals(null to Basis.None, SoundRules.attribute(Kind.Media, e.copy(notification = "pro"), skip))
    }

    @Test fun onlyRealNotificationsExplainASound() {
        assertTrue(SoundRules.worthNoting(0, "whatsapp", own = "pro"))
        // Ongoing (a music player's controls), a running service, a group's summary, pro itself: no.
        assertFalse(SoundRules.worthNoting(SoundRules.FLAG_ONGOING_EVENT, "spotify", own = "pro"))
        assertFalse(SoundRules.worthNoting(SoundRules.FLAG_FOREGROUND_SERVICE, "maps", own = "pro"))
        assertFalse(SoundRules.worthNoting(SoundRules.FLAG_GROUP_SUMMARY, "whatsapp", own = "pro"))
        assertFalse(SoundRules.worthNoting(0, "pro", own = "pro"))
        assertFalse(SoundRules.worthNoting(0, null, own = "pro"))
    }

    @Test fun aNotificationExplainsASoundOnlyForAFewSeconds() {
        val log = SoundRules.NoteLog()
        log.add("whatsapp", at = 10_000)
        assertEquals("whatsapp", log.recent(now = 10_700))
        assertEquals("whatsapp", log.recent(now = 10_000 + SoundRules.NOTE_WINDOW_MS))
        assertNull(log.recent(now = 10_000 + SoundRules.NOTE_WINDOW_MS + 1))
        // The newest wins, and a skipped app is passed over.
        log.add("gmail", at = 10_500)
        assertEquals("gmail", log.recent(now = 11_000))
        assertEquals("whatsapp", log.recent(now = 11_000, skip = { it == "gmail" }))
        // A notification a hair in the future (clock jitter between two callbacks) still counts.
        assertEquals("gmail", log.recent(now = 10_300))
    }

    @Test fun noteLogKeepsOnlyTheLastFew() {
        val log = SoundRules.NoteLog(keep = 3)
        for (i in 1..5) log.add("app$i", at = i * 100L)
        assertEquals("app5", log.recent(now = 600))
        assertNull(log.recent(now = 600, skip = { it in setOf("app3", "app4", "app5") }))
    }

    @Test fun theAppOnScreenFollowsWindowsAndForgetsAtTheHomeScreen() {
        assertEquals("whatsapp", SoundRules.foregroundAfter(null, "whatsapp", Role.App))
        assertEquals("chrome", SoundRules.foregroundAfter("whatsapp", "chrome", Role.App))
        // The keyboard, the status bar or pro itself coming up doesn't change who's in front.
        assertEquals("whatsapp", SoundRules.foregroundAfter("whatsapp", "keyboard", Role.System))
        // The home screen: nobody is in front, so a later sound isn't blamed on the last app.
        assertNull(SoundRules.foregroundAfter("whatsapp", "launcher", Role.Launcher))
    }

    @Test fun recentListFoldsRepeatsAndKeepsTheNewestFirst() {
        var list = emptyList<SoundRules.Seen>()
        list = SoundRules.remember(list, "whatsapp", Kind.Alert, 1_000)
        list = SoundRules.remember(list, "spotify", Kind.Media, 2_000)
        list = SoundRules.remember(list, "whatsapp", Kind.Alert, 3_000)
        assertEquals(listOf("whatsapp", "spotify"), list.map { it.pkg })
        assertEquals(2, list.first().count)
        assertEquals(3_000, list.first().at)
        // Sounds we couldn't name fold by their kind.
        list = SoundRules.remember(list, null, Kind.Alert, 4_000)
        list = SoundRules.remember(list, null, Kind.Alert, 5_000)
        assertEquals(3, list.size)
        assertEquals(2, list.first().count)
        // The list is capped.
        for (i in 1..20) list = SoundRules.remember(list, "app$i", Kind.Media, 6_000L + i, max = 12)
        assertEquals(12, list.size)
    }

    @Test fun agoIsShortAndPlain() {
        assertEquals("just now", SoundRules.ago(10_000))
        assertEquals("1 min ago", SoundRules.ago(50_000))
        assertEquals("5 min ago", SoundRules.ago(5 * 60_000L))
        assertEquals("2 h ago", SoundRules.ago(2 * 3_600_000L + 5))
        assertEquals("2 d ago", SoundRules.ago(2 * 86_400_000L + 5))
    }

    @Test fun aShortSoundIsSeenEvenAfterItEnds() {
        val linger = SoundRules.Linger.Normal.ms
        assertTrue(SoundRules.showing(active = true, endedAt = 0, now = 5_000, lingerMs = linger))
        assertTrue(SoundRules.showing(active = false, endedAt = 5_000, now = 5_000 + linger - 1, lingerMs = linger))
        assertFalse(SoundRules.showing(active = false, endedAt = 5_000, now = 5_000 + linger, lingerMs = linger))
        // Never ended and not playing: nothing.
        assertFalse(SoundRules.showing(active = false, endedAt = 0, now = 5_000, lingerMs = linger))
        assertTrue(SoundRules.Linger.Short.ms < SoundRules.Linger.Normal.ms && SoundRules.Linger.Normal.ms < SoundRules.Linger.Long.ms)
    }

    @Test fun theClueSentenceMatchesWhichSwitchesAreOn() {
        val all = SoundRules.clueSummary(true, true)
        val neither = SoundRules.clueSummary(false, false)
        assertTrue(all.contains("almost every sound"))
        assertTrue(neither.contains("symbol"))
        assertEquals(4, setOf(all, neither, SoundRules.clueSummary(true, false), SoundRules.clueSummary(false, true)).size)
    }
}
