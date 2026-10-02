package me.kavishdevar.librepods

import me.kavishdevar.librepods.presentation.overlays.IslandEvent
import me.kavishdevar.librepods.presentation.overlays.PodsSnapshot
import me.kavishdevar.librepods.presentation.overlays.islandText
import me.kavishdevar.librepods.services.HeartRate
import me.kavishdevar.librepods.services.IslandPrefs
import me.kavishdevar.librepods.services.NowPlaying
import me.kavishdevar.librepods.services.PREF_HR_ISLAND
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The island's new moments: wording, when the heart shows, and the settings defaults. */
class IslandMomentsTest {
    private val pods = PodsSnapshot(name = "Jake's AirPods Pro", left = 80, right = 70, case = 50)

    @Test fun budOutWording() {
        assertEquals("One AirPod out" to "Music paused", islandText(IslandEvent.BudOut(remaining = 1, paused = true), pods))
        assertEquals("One AirPod out" to "Still in one ear", islandText(IslandEvent.BudOut(remaining = 1, paused = false), pods))
        assertEquals("AirPods out" to "Music paused", islandText(IslandEvent.BudOut(remaining = 0, paused = true), pods))
        assertEquals("AirPods out" to "Jake's AirPods Pro", islandText(IslandEvent.BudOut(remaining = 0, paused = false), pods))
        assertEquals("Both AirPods in" to "Jake's AirPods Pro", islandText(IslandEvent.BothIn, pods))
    }

    @Test fun chargingSaysWhatIsCharging() {
        assertEquals("Case charging" to "Case 50%", islandText(IslandEvent.Charging, pods.copy(caseCharging = true)))
        assertEquals("Charging" to "AirPods 70% · Case 50%", islandText(IslandEvent.Charging, pods.copy(leftCharging = true)))
    }

    @Test fun musicWords() {
        assertEquals("Now playing" to "On your AirPods", NowPlaying.words(NowPlaying.Track(playing = true)))
        assertEquals("Paused" to "Tap play to continue", NowPlaying.words(NowPlaying.Track(playing = false)))
        assertEquals(
            "Midnight City" to "M83 · Spotify",
            NowPlaying.words(NowPlaying.Track(playing = true, title = "Midnight City", artist = "M83", app = "Spotify", fromSession = true))
        )
        // A podcast app that repeats its own name as the artist doesn't say it twice.
        assertEquals("Episode 4" to "Pocket Casts", NowPlaying.words(NowPlaying.Track(true, "Episode 4", "Pocket Casts", "Pocket Casts")))
    }

    @Test fun heartOnlyWithACurrentReading() {
        val now = 1_000_000_000L
        val up = me.kavishdevar.librepods.services.LinkState.Connected("AirPods")
        fun pill(st: HeartRate.State) = me.kavishdevar.librepods.services.HeartView.of(st, up, true, now).worthAPill
        val live = HeartRate.State(status = HeartRate.Status.Live, bpm = 72, lastReadingMs = now - 2_000)
        assertTrue(pill(live))
        assertTrue(pill(live.copy(status = HeartRate.Status.Resting)))
        assertFalse(pill(live.copy(status = HeartRate.Status.Off)))
        assertFalse(pill(live.copy(status = HeartRate.Status.NotConnected)))
        assertFalse(pill(live.copy(bpm = null)))
        assertFalse(pill(live.copy(lastReadingMs = now - 60_000L)))
        assertFalse(pill(live.copy(status = HeartRate.Status.Resting, lastReadingMs = now - 21 * 60_000L)))
    }

    @Test fun settingsDefaults() {
        val on = IslandPrefs.Trigger.entries.filter { it.default }.toSet()
        // Only "each new song" starts off (it would show every few minutes).
        assertEquals(IslandPrefs.Trigger.entries.toSet() - IslandPrefs.Trigger.SongChanges, on)
        // Existing switches keep their stored keys, so nobody's earlier choice is lost.
        assertEquals("glint_island_mode_changes", IslandPrefs.Trigger.ListeningMode.key)
        assertEquals(PREF_HR_ISLAND, IslandPrefs.Trigger.HeartAlert.key)
        assertEquals("show_island_popup", IslandPrefs.PREF_MASTER)
        assertEquals(IslandPrefs.Trigger.entries.size, IslandPrefs.Trigger.entries.map { it.key }.toSet().size)
    }

    @Test fun durationsGrow() {
        val d = IslandPrefs.Duration.entries
        assertTrue(d.zipWithNext().all { (a, b) -> a.compactMs < b.compactMs && a.expandedMs < b.expandedMs })
    }
}
