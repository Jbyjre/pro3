package me.kavishdevar.librepods

import android.app.Application
import android.content.Context
import android.graphics.Rect
import me.kavishdevar.librepods.presentation.overlays.GlintOverlays
import me.kavishdevar.librepods.presentation.overlays.MiniGeometry
import me.kavishdevar.librepods.services.IslandPrefs
import me.kavishdevar.librepods.services.MiniIslandRules
import me.kavishdevar.librepods.services.MiniIslandRules.Mode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The Dynamic Island's three modes (Jake, 2026-10-11): On (everything), Music only (only while
 * music plays), Off (a tiny ring round the camera that's always there to tap, showing nothing).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class, qualifiers = "w412dp-h915dp-xxhdpi")
class IslandModeTest {
    private val app get() = RuntimeEnvironment.getApplication()

    private val idle = MiniIslandRules.Inputs(
        enabled = true, canDraw = true, playing = false, pausedForMs = Long.MAX_VALUE, playedRecently = false,
        airPodsOnly = false, airPodsUp = false, screenUnlocked = true, anytime = true, alwaysWithAirPods = true,
    )

    @Test fun onKeepsEverything() {
        assertTrue("always on", MiniIslandRules.wanted(idle.copy(mode = Mode.On)))
        assertTrue("a timer", MiniIslandRules.wanted(idle.copy(mode = Mode.On, anytime = false, timer = true)))
        assertTrue("a sound", MiniIslandRules.wanted(idle.copy(mode = Mode.On, anytime = false, sound = true)))
    }

    @Test fun musicOnlyIsMusicAndNothingElse() {
        val m = idle.copy(mode = Mode.MusicOnly)
        assertFalse("not always on", MiniIslandRules.wanted(m))
        assertFalse("not for a timer", MiniIslandRules.wanted(m.copy(timer = true)))
        assertFalse("not for a sound or message", MiniIslandRules.wanted(m.copy(sound = true)))
        assertFalse("not just for AirPods", MiniIslandRules.wanted(m.copy(airPodsUp = true)))
        assertTrue("while music plays", MiniIslandRules.wanted(m.copy(playing = true, pausedForMs = 0L, playedRecently = true)))
        assertTrue("a moment after a pause", MiniIslandRules.wanted(m.copy(playedRecently = true, pausedForMs = 5_000L)))
        assertFalse("then gone", MiniIslandRules.wanted(m.copy(playedRecently = true, pausedForMs = MiniIslandRules.PAUSED_LINGER_MS + 1)))
        assertFalse("hidden in chosen apps", MiniIslandRules.wanted(m.copy(playing = true, hiddenHere = true)))
        assertFalse("only with AirPods when asked", MiniIslandRules.wanted(m.copy(playing = true, airPodsOnly = true)))
    }

    @Test fun offIsTheRingThatsAlwaysThere() {
        val o = idle.copy(mode = Mode.Off, anytime = false, alwaysWithAirPods = false)
        assertTrue("there with nothing going on", MiniIslandRules.wanted(o))
        assertTrue("and while music plays", MiniIslandRules.wanted(o.copy(playing = true)))
        assertFalse("not on the lock screen", MiniIslandRules.wanted(o.copy(screenUnlocked = false)))
        assertFalse("not without permission", MiniIslandRules.wanted(o.copy(canDraw = false)))
        assertFalse("hidden in chosen apps", MiniIslandRules.wanted(o.copy(hiddenHere = true)))
    }

    @Test fun theOldOffSwitchBecomesOff() {
        val p = app.getSharedPreferences("settings", Context.MODE_PRIVATE)
        p.edit().clear().commit()
        assertEquals("new phones start On", Mode.On, IslandPrefs.mode(p))
        p.edit().putBoolean(IslandPrefs.PREF_MINI, false).commit()
        assertEquals("the old switch off means Off now", Mode.Off, IslandPrefs.mode(p))
        IslandPrefs.setMode(p, Mode.MusicOnly)
        assertEquals("a choice made sticks", Mode.MusicOnly, IslandPrefs.mode(p))
        p.edit().putString(IslandPrefs.PREF_DI_MODE, "something else").commit()
        assertEquals("an unknown value falls back to the old switch", Mode.Off, IslandPrefs.mode(p))
    }

    @Test fun theRingIsTinyHugsTheCameraAndKnowsItsARing() {
        val d = app.resources.displayMetrics.density
        val w = app.resources.displayMetrics.widthPixels
        val hole = Rect((w / 2 - 13 * d).toInt(), (10 * d).toInt(), (w / 2 + 13 * d).toInt(), (36 * d).toInt())
        val g = MiniGeometry(app, listOf(hole))
        assertEquals("2 dp round the camera", hole.height() + 4f * d, g.ringD, 1f)
        assertTrue("smaller than the pill", g.ringD < g.size.height && g.ringD < g.size.compactWidth / 2f)
        assertTrue("its window is narrower than the pill's", g.ringWindow.width < g.compactWindow.width)
        assertTrue(g.origin(g.ringD, g.ringD).isRing)
        assertFalse("the pill is never mistaken for it", g.origin(g.widthFor(
            me.kavishdevar.librepods.services.IslandLook.Slot.Nothing, me.kavishdevar.librepods.services.IslandLook.Slot.Nothing,
        )).isRing)
        // No camera in the middle (a corner camera, or none): a small dot, still inside the status bar.
        val none = MiniGeometry(app, emptyList())
        assertTrue(none.ringD > 0f && none.ringD < none.size.height)
        GlintOverlays.miniOrigin = null
    }
}
