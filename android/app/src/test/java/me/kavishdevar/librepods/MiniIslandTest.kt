package me.kavishdevar.librepods

import androidx.compose.ui.graphics.Color
import me.kavishdevar.librepods.presentation.overlays.accentOfPixels
import me.kavishdevar.librepods.services.MiniIslandRules
import me.kavishdevar.librepods.services.MusicPulse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = android.app.Application::class)
class MiniIslandTest {
    private val base = MiniIslandRules.Inputs(
        enabled = true, canDraw = true, playing = true, pausedForMs = 0, playedRecently = true,
        airPodsOnly = false, airPodsUp = false, screenUnlocked = true,
    )

    @Test fun showsWhilePlayingAndBrieflyAfterPausing() {
        assertTrue(MiniIslandRules.wanted(base))
        assertTrue(MiniIslandRules.wanted(base.copy(playing = false, pausedForMs = 10_000)))
        assertFalse(MiniIslandRules.wanted(base.copy(playing = false, pausedForMs = MiniIslandRules.PAUSED_LINGER_MS)))
        // Nothing has played yet: no paused pill out of nowhere.
        assertFalse(MiniIslandRules.wanted(base.copy(playing = false, pausedForMs = 1_000, playedRecently = false)))
    }

    @Test fun respectsSwitchesPermissionAndLockScreen() {
        assertFalse(MiniIslandRules.wanted(base.copy(enabled = false)))
        assertFalse(MiniIslandRules.wanted(base.copy(canDraw = false)))
        assertFalse(MiniIslandRules.wanted(base.copy(screenUnlocked = false)))
        assertFalse(MiniIslandRules.wanted(base.copy(airPodsOnly = true, airPodsUp = false)))
        assertTrue(MiniIslandRules.wanted(base.copy(airPodsOnly = true, airPodsUp = true)))
    }

    @Test fun songChangeNeedsANameAndADifference() {
        assertTrue(MiniIslandRules.songChanged("A", "X", "B", "X"))
        assertTrue(MiniIslandRules.songChanged("A", "X", "A", "Y"))
        assertTrue(MiniIslandRules.songChanged(null, null, "A", "X"))
        assertFalse(MiniIslandRules.songChanged("A", "X", "A", "X"))
        assertFalse(MiniIslandRules.songChanged("A", "X", null, null))
    }

    @Test fun pillRingsTheCameraAndStaysOnScreen() {
        val d = 3f
        val s = MiniIslandRules.size(holeW = 26 * d, holeH = 26 * d, density = d, screenW = 412 * d)
        assertTrue(s.height > 26 * d) // taller than the camera
        assertTrue(s.compactWidth > 26 * d + 2 * (s.height - 8 * d)) // cover and bars fit beside it
        assertTrue(s.wideWidth <= 412 * d - 24 * d)
        assertTrue(s.wideHeight > s.height)
        // No camera hole: still a sensible small pill.
        val none = MiniIslandRules.size(0f, 0f, d, 412 * d)
        assertEquals(28 * d, none.height, 0.01f)
    }

    @Test fun accentIsVividOrWhite() {
        val grey = IntArray(144) { 0xFF808080.toInt() }
        assertEquals(Color.White, accentOfPixels(grey))
        val orange = IntArray(144) { if (it == 7) 0xFFFF6A00.toInt() else 0xFF202020.toInt() }
        val a = accentOfPixels(orange)
        assertNotEquals(Color.White, a)
        assertTrue(a.red > a.blue) // kept orange, brightened
    }

    @Test fun progressMovesOnlyWhilePlaying() {
        val t = me.kavishdevar.librepods.services.NowPlaying.Track(playing = true, durationMs = 200_000, positionMs = 50_000, positionAtMs = 1_000)
        assertEquals(0.25f, t.progress(1_000)!!, 0.001f)
        assertEquals(0.30f, t.progress(11_000)!!, 0.001f)
        assertEquals(0.25f, t.copy(playing = false).progress(11_000)!!, 0.001f)
        assertEquals(1f, t.progress(10_000_000)!!, 0.001f)
        assertEquals(null, t.copy(durationMs = 0).progress(5_000))
    }

    private val w = 1080; private val h = 2340
    private fun box(l: Int, t: Int, r: Int, b: Int) = MiniIslandRules.Box(l, t, r, b)

    @Test fun wrapsACentredPunchHoleOrNotch() {
        val hole = box(510, 30, 570, 90)
        assertEquals(hole, MiniIslandRules.pickCamera(listOf(hole), w, h))
        val notch = box(300, 0, 780, 90)
        assertEquals(notch, MiniIslandRules.pickCamera(listOf(notch), w, h))
        // Two cutouts: the one nearest the middle.
        assertEquals(hole, MiniIslandRules.pickCamera(listOf(box(40, 30, 100, 90), hole), w, h))
    }

    @Test fun cornerSideOrMissingCameraFallsBackToStatusBarMiddle() {
        assertEquals(null, MiniIslandRules.pickCamera(listOf(box(40, 30, 100, 90)), w, h)) // top-left hole
        assertEquals(null, MiniIslandRules.pickCamera(listOf(box(0, 1100, 90, 1200)), w, h)) // on a side
        assertEquals(null, MiniIslandRules.pickCamera(listOf(box(0, 0, 1000, 90)), w, h)) // too wide
        assertEquals(null, MiniIslandRules.pickCamera(emptyList(), w, h))
        assertEquals(40f, MiniIslandRules.centerY(null, 80, 60f, 3f), 0.01f)
    }

    @Test fun pillIsNeverCutOffAtTheTopOrSides() {
        val d = 3f
        // A notch touching the top edge: the pill's middle moves down enough to show it whole.
        val y = MiniIslandRules.centerY(box(300, 0, 780, 20), 90, 40 * d, d)
        assertTrue(y - 20 * d >= 0f)
        // A very narrow phone with a wide notch: still fits.
        val s = MiniIslandRules.size(holeW = 240 * d, holeH = 30 * d, density = d, screenW = 280 * d)
        assertTrue(s.compactWidth <= 280 * d - 16 * d)
        assertTrue(s.wideWidth <= 280 * d - 16 * d)
    }

    @Test fun staysWithAirPodsAndPicksWhatToShow() {
        val idle = base.copy(playing = false, pausedForMs = Long.MAX_VALUE, playedRecently = false, airPodsUp = true)
        assertFalse(MiniIslandRules.wanted(idle))
        assertTrue(MiniIslandRules.wanted(idle.copy(alwaysWithAirPods = true)))
        assertFalse(MiniIslandRules.wanted(idle.copy(alwaysWithAirPods = true, airPodsUp = false)))
        assertFalse(MiniIslandRules.wanted(idle.copy(alwaysWithAirPods = true, screenUnlocked = false)))

        assertEquals(MiniIslandRules.Content.Music, MiniIslandRules.content(playing = true, pausedForMs = 0, playedRecently = true, airPodsUp = true))
        assertEquals(MiniIslandRules.Content.Music, MiniIslandRules.content(playing = false, pausedForMs = 5_000, playedRecently = true, airPodsUp = true))
        assertEquals(MiniIslandRules.Content.AirPods, MiniIslandRules.content(playing = false, pausedForMs = 60_000, playedRecently = true, airPodsUp = true))
        assertEquals(MiniIslandRules.Content.AirPods, MiniIslandRules.content(playing = false, pausedForMs = Long.MAX_VALUE, playedRecently = false, airPodsUp = true))
    }

    @Test fun alwaysOnStaysWithNothingConnected() {
        val nothing = base.copy(playing = false, pausedForMs = Long.MAX_VALUE, playedRecently = false, airPodsUp = false, airPodsOnly = true)
        assertFalse(MiniIslandRules.wanted(nothing))
        assertTrue(MiniIslandRules.wanted(nothing.copy(anytime = true)))
        // Still steps aside with the screen off, the setting off or no overlay permission.
        assertFalse(MiniIslandRules.wanted(nothing.copy(anytime = true, screenUnlocked = false)))
        assertFalse(MiniIslandRules.wanted(nothing.copy(anytime = true, enabled = false)))
        assertFalse(MiniIslandRules.wanted(nothing.copy(anytime = true, canDraw = false)))
        assertEquals(MiniIslandRules.Content.Rest, MiniIslandRules.content(playing = false, pausedForMs = Long.MAX_VALUE, playedRecently = false, airPodsUp = false))
        assertEquals(MiniIslandRules.Content.Music, MiniIslandRules.content(playing = true, pausedForMs = 0, playedRecently = true, airPodsUp = false))
    }

    @Test fun barsFollowTheMusicLevels() {
        val peaks = FloatArray(MusicPulse.BANDS) { 1f }
        // Silence: flat.
        assertTrue(MusicPulse.levels(ByteArray(512), 44_100_000, peaks).all { it == 0f })
        // A loud bass note (~86 Hz per bin at 44.1 kHz with 256 bins): the bass bar leads.
        val fft = ByteArray(512)
        for (k in 1..2) { fft[2 * k] = 100; fft[2 * k + 1] = 40 }
        val l = MusicPulse.levels(fft, 44_100_000, peaks)
        assertTrue(l[0] > 0.9f)
        assertEquals(0f, l[3], 0f)
        assertTrue(l.all { it in 0f..1f })
    }

    @Test fun anySoundPopsThePillEvenWithNothingElseGoingOn() {
        // A message ding: no music, no AirPods, "anytime" off, nothing played before.
        val quiet = base.copy(playing = false, pausedForMs = Long.MAX_VALUE, playedRecently = false)
        assertFalse(MiniIslandRules.wanted(quiet))
        assertTrue(MiniIslandRules.wanted(quiet.copy(sound = true)))
        // Still respects the master switch, the permission, the lock screen and "AirPods only".
        assertFalse(MiniIslandRules.wanted(quiet.copy(sound = true, enabled = false)))
        assertFalse(MiniIslandRules.wanted(quiet.copy(sound = true, canDraw = false)))
        assertFalse(MiniIslandRules.wanted(quiet.copy(sound = true, screenUnlocked = false)))
        assertFalse(MiniIslandRules.wanted(quiet.copy(sound = true, airPodsOnly = true, airPodsUp = false)))
    }

    @Test fun contentPrefersMusicThenSoundsThenPausedMusic() {
        // Music keeps the pill (a ding over it is a brief blip, not a different state).
        assertEquals(MiniIslandRules.Content.Music, MiniIslandRules.content(playing = true, pausedForMs = 0, playedRecently = true, airPodsUp = true, sound = true))
        // A sound beats paused music (the freshest news), then the pill returns to paused music.
        assertEquals(MiniIslandRules.Content.Sound, MiniIslandRules.content(playing = false, pausedForMs = 5_000, playedRecently = true, airPodsUp = true, sound = true))
        assertEquals(MiniIslandRules.Content.Music, MiniIslandRules.content(playing = false, pausedForMs = 5_000, playedRecently = true, airPodsUp = true, sound = false))
        assertEquals(MiniIslandRules.Content.Sound, MiniIslandRules.content(playing = false, pausedForMs = Long.MAX_VALUE, playedRecently = false, airPodsUp = false, sound = true))
        assertEquals(MiniIslandRules.Content.AirPods, MiniIslandRules.content(playing = false, pausedForMs = Long.MAX_VALUE, playedRecently = false, airPodsUp = true))
        assertEquals(MiniIslandRules.Content.Rest, MiniIslandRules.content(playing = false, pausedForMs = Long.MAX_VALUE, playedRecently = false, airPodsUp = false))
    }

    /**
     * Regression: the old check scheduled "look again when the pause runs out" with a wait of
     * (30 s - paused for) + 0.1 s. Once the pause was over that wait was zero or negative, so the
     * check ran again at once, scheduled itself again, and so on without end (with "always on"
     * the pill stays wanted, so this began 30 s after any music paused).
     */
    @Test fun nextLookIsNeverZeroOrNegative() {
        val linger = MiniIslandRules.PAUSED_LINGER_MS
        // Not yet over: look just after it ends.
        assertEquals(10_000L + 100L, MiniIslandRules.nextCheck(playing = false, pausedForMs = linger - 10_000, soundLeftMs = null))
        // Over (exactly, and long ago), or nothing paused: nothing to schedule.
        assertNull(MiniIslandRules.nextCheck(playing = false, pausedForMs = linger, soundLeftMs = null))
        assertNull(MiniIslandRules.nextCheck(playing = false, pausedForMs = linger + 12_345, soundLeftMs = null))
        assertNull(MiniIslandRules.nextCheck(playing = false, pausedForMs = Long.MAX_VALUE, soundLeftMs = null))
        assertNull(MiniIslandRules.nextCheck(playing = true, pausedForMs = 0, soundLeftMs = null))
        // A sound that just ended and paused music: the sooner of the two.
        assertEquals(1_500L + 100L, MiniIslandRules.nextCheck(playing = false, pausedForMs = 1_000, soundLeftMs = 1_500))
        assertNull(MiniIslandRules.nextCheck(playing = false, pausedForMs = Long.MAX_VALUE, soundLeftMs = 0))
        assertNull(MiniIslandRules.nextCheck(playing = false, pausedForMs = Long.MAX_VALUE, soundLeftMs = -5))
        // Every answer, for any input, is a real wait.
        for (p in listOf(0L, 1L, linger - 1, linger, linger + 1, Long.MAX_VALUE)) for (s in listOf(null, -1L, 0L, 1L, 3_000L)) {
            val r = MiniIslandRules.nextCheck(playing = false, pausedForMs = p, soundLeftMs = s)
            assertTrue(r == null || r > 100L)
        }
    }

    @Test fun aHalfSecondBlipLeavesNoPausedPillButIsStillSeenAsASound() {
        assertFalse(MiniIslandRules.countsAsPlayed(300))
        assertFalse(MiniIslandRules.countsAsPlayed(MiniIslandRules.SUSTAINED_MS - 1))
        assertTrue(MiniIslandRules.countsAsPlayed(MiniIslandRules.SUSTAINED_MS))
        // A short musical sound (a game effect) shows as a sound once it's over...
        assertTrue(MiniIslandRules.showAsSound(heardOn = true, musicLike = true, playing = false, musicCounted = false))
        // ...but real music that paused keeps the music look (Paused), not a sound.
        assertFalse(MiniIslandRules.showAsSound(heardOn = true, musicLike = true, playing = false, musicCounted = true))
        // An alert is always a sound while no music plays, and never while music does (that's a blip).
        assertTrue(MiniIslandRules.showAsSound(heardOn = true, musicLike = false, playing = false, musicCounted = true))
        assertFalse(MiniIslandRules.showAsSound(heardOn = true, musicLike = false, playing = true, musicCounted = true))
        assertFalse(MiniIslandRules.showAsSound(heardOn = false, musicLike = false, playing = false, musicCounted = false))
    }

    @Test fun aBlipNeverRestartsOrCreatesAPause() {
        val t = MiniIslandRules.PlayTracker()
        // Nothing has played: a half-second blip leaves nothing paused.
        t.update(true, 1_000); assertTrue(t.playing)
        t.update(false, 1_400)
        assertFalse(t.played); assertFalse(t.counted); assertEquals(0L, t.stoppedAt)
        // Real music for 5 s, then pause: it counts and the pause clock starts at the pause.
        t.update(true, 10_000); t.update(false, 15_000)
        assertTrue(t.played); assertTrue(t.counted); assertEquals(15_000L, t.stoppedAt)
        // A blip during that pause: the pause clock keeps its time, and the blip is a sound, not music.
        t.update(true, 20_000); assertEquals(0L, t.stoppedAt) // playing again (any sound counts while it plays)
        t.update(false, 20_300)
        assertEquals(15_000L, t.stoppedAt)
        assertFalse(t.counted)
        assertTrue(t.played) // the earlier music still happened
        // Music again, long enough: a fresh pause.
        t.update(true, 30_000); t.update(false, 40_000)
        assertEquals(40_000L, t.stoppedAt); assertTrue(t.counted)
        // Updating with no change does nothing.
        t.update(false, 50_000)
        assertEquals(40_000L, t.stoppedAt)
    }

    @Test fun phoneBatteryNumbersBecomeAPercentAndAChargingFlag() {
        val p = me.kavishdevar.librepods.services.PhoneStatus
        val info = { level: Int, charging: Boolean -> me.kavishdevar.librepods.services.PhoneStatus.Info(level, charging) }
        assertEquals(info(84, false), p.parse(level = 84, scale = 100, status = 3 /* discharging */, plugged = 0))
        assertEquals(info(50, true), p.parse(level = 5, scale = 10, status = 2 /* charging */, plugged = 1))
        // Full while plugged in counts as charging; full unplugged doesn't.
        assertEquals(info(100, true), p.parse(level = 100, scale = 100, status = 5 /* full */, plugged = 2))
        assertEquals(info(100, false), p.parse(level = 100, scale = 100, status = 5, plugged = 0))
        // No reading yet.
        assertFalse(p.parse(level = -1, scale = 100, status = 2, plugged = 1).known)
        assertFalse(p.parse(level = 50, scale = 0, status = 2, plugged = 1).known)
    }
}
