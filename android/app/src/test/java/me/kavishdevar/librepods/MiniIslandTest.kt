package me.kavishdevar.librepods

import androidx.compose.ui.graphics.Color
import me.kavishdevar.librepods.presentation.overlays.accentOfPixels
import me.kavishdevar.librepods.services.MiniIslandRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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
}
