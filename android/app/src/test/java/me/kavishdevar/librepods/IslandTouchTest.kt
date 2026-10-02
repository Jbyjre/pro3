package me.kavishdevar.librepods

import me.kavishdevar.librepods.services.IslandGestures
import me.kavishdevar.librepods.services.IslandGestures.Action
import me.kavishdevar.librepods.services.IslandGestures.Gesture
import me.kavishdevar.librepods.services.IslandGestures.Kind
import me.kavishdevar.librepods.services.ListeningModes
import me.kavishdevar.librepods.services.PanelRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where a touch on the Dynamic Island goes, and what it then does. The window layers are the
 * ones Android itself uses (AOSP WindowManagerPolicy.getWindowLayerFromTypeLw: application
 * overlay 11, status bar 15, notification shade 17, accessibility overlay 31; the status bar
 * window is full width, its own height, and touchable, see SystemUI's
 * StatusBarWindowControllerImpl). Android hands a touch to the topmost touchable window that
 * contains it.
 */
class IslandTouchTest {
    private data class Win(val name: String, val layer: Int, val l: Int, val t: Int, val r: Int, val b: Int, val touchable: Boolean = true)

    /** Android's rule: the topmost touchable window containing the point gets the touch. */
    private fun route(windows: List<Win>, x: Int, y: Int): String? =
        windows.filter { it.touchable && x >= it.l && x < it.r && y >= it.t && y < it.b }.maxByOrNull { it.layer }?.name

    // A 1080 x 2340 phone with a 110 px status bar and the camera hole centred at y = 55.
    private val statusBar = Win("status bar", 15, 0, 0, 1080, 110)
    private val app = Win("app", 2, 0, 0, 1080, 2340)
    private val pillCentre = 540 to 55

    @Test fun asANormalOverlayThePillNeverGetsTouches() {
        val pill = Win("pill", 11, 400, 20, 680, 92)
        assertEquals("status bar", route(listOf(app, statusBar, pill), pillCentre.first, pillCentre.second))
        // Every point of the pill is inside the status bar strip, so none of it can be touched.
        for (x in pill.l until pill.r step 7) for (y in pill.t until pill.b step 7) {
            assertEquals("status bar", route(listOf(app, statusBar, pill), x, y))
        }
    }

    @Test fun aboveTheStatusBarThePillGetsTouches() {
        val pill = Win("pill", 31, 400, 20, 680, 92)
        assertEquals("pill", route(listOf(app, statusBar, pill), pillCentre.first, pillCentre.second))
        // The rest of the status bar still pulls down notifications as before.
        assertEquals("status bar", route(listOf(app, statusBar, pill), 100, 55))
        assertEquals("status bar", route(listOf(app, statusBar, pill), 1000, 55))
    }

    @Test fun whenHiddenOrUnderTheShadeTheShadeGetsTouches() {
        val shade = Win("shade", 17, 0, 0, 1080, 2340)
        val hiddenPill = Win("pill", 31, 400, 20, 680, 92, touchable = false)
        assertEquals("shade", route(listOf(app, statusBar, shade, hiddenPill), pillCentre.first, pillCentre.second))
    }

    @Test fun aPopUpBelowThePillDoesNotCoverIt() {
        // The pop-up's window starts just under the pill (section 29), so the pill stays tappable.
        val pill = Win("pill", 31, 400, 20, 680, 92)
        val popUp = Win("pop-up", 31, 0, 93, 1080, 500)
        assertEquals("pill", route(listOf(app, statusBar, pill, popUp), pillCentre.first, pillCentre.second))
    }

    // ---- What a touch then does ----

    @Test fun tapsCountAndOnlyTheFinalCountActs() {
        val c = IslandGestures.TapCounter(300)
        val max = IslandGestures.maxUsefulTaps(IslandGestures.defaults)
        assertEquals(3, max)
        assertEquals(IslandGestures.TapCounter.Result(1, false), c.tap(1_000, max))
        assertEquals(IslandGestures.TapCounter.Result(2, false), c.tap(1_200, max))
        // Waiting out the gap: two taps.
        assertEquals(2, c.expire())
        assertEquals(0, c.expire())
    }

    @Test fun aThirdTapActsAtOnceAndRapidTappingNeverPilesUp() {
        val c = IslandGestures.TapCounter(300)
        var t = 0L
        val results = (1..7).map { c.tap(t.also { t += 120 }, 3) }
        // 1, 2, 3 (final), 1, 2, 3 (final), 1
        assertEquals(listOf(1, 2, 3, 1, 2, 3, 1), results.map { it.count })
        assertEquals(listOf(false, false, true, false, false, true, false), results.map { it.final })
        assertEquals(1, c.expire())
    }

    @Test fun aSlowSecondTapStartsAgain() {
        val c = IslandGestures.TapCounter(300)
        c.tap(0, 3)
        assertEquals(1, c.tap(500, 3).count)
    }

    @Test fun withDoubleAndTripleTapOffASingleTapIsInstant() {
        val actions = IslandGestures.defaults + mapOf(Gesture.Tap2 to Action.Nothing, Gesture.Tap3 to Action.Nothing)
        val max = IslandGestures.maxUsefulTaps(actions)
        assertEquals(1, max)
        assertTrue(IslandGestures.TapCounter(300).tap(0, max).final)
        // Double tap on, triple off: the second tap is final.
        assertEquals(2, IslandGestures.maxUsefulTaps(IslandGestures.defaults + (Gesture.Tap3 to Action.Nothing)))
    }

    @Test fun fingerPathsBecomeGestures() {
        val slop = 20f
        val swipe = 80f
        assertEquals(Kind.Tap, IslandGestures.classify(3f, -4f, 120, slop, swipe))
        assertEquals(Kind.Hold, IslandGestures.classify(0f, 0f, 600, slop, swipe))
        assertEquals(Kind.None, IslandGestures.classify(0f, 0f, 600, slop, swipe, heldAlready = true))
        assertEquals(Kind.SwipeLeft, IslandGestures.classify(-120f, 10f, 200, slop, swipe))
        assertEquals(Kind.SwipeRight, IslandGestures.classify(130f, -30f, 200, slop, swipe))
        assertEquals(Kind.PullDown, IslandGestures.classify(10f, 120f, 200, slop, swipe))
        // Up is not a gesture on the pill; a short wobble is nothing.
        assertEquals(Kind.None, IslandGestures.classify(5f, -120f, 200, slop, swipe))
        assertEquals(Kind.None, IslandGestures.classify(40f, 30f, 200, slop, swipe))
        assertTrue(IslandGestures.isHold(4f, 4f, 450, slop))
        assertFalse(IslandGestures.isHold(40f, 4f, 900, slop))
    }

    @Test fun defaultsAreTheOriginalBehaviour() {
        val d = IslandGestures.defaults
        assertEquals(Action.Expand, d[Gesture.Tap1])
        assertEquals(Action.PlayPause, d[Gesture.Tap2])
        assertEquals(Action.Next, d[Gesture.Tap3])
        assertEquals(Action.OpenApp, d[Gesture.Hold])
        assertEquals(Action.Next, d[Gesture.SwipeLeft])
        assertEquals(Action.Previous, d[Gesture.SwipeRight])
        assertEquals(Gesture.entries.toSet(), d.keys)
        assertEquals(Gesture.Tap2, IslandGestures.taps(2))
        assertEquals(Gesture.Tap3, IslandGestures.taps(9))
        // Every action can be read back from what's stored.
        Action.entries.forEach { assertEquals(it, IslandGestures.fromKey(it.key)) }
    }

    @Test fun listeningModesCycleLikeTheTile() {
        assertEquals(listOf(1, 3, 4, 2), ListeningModes.cycle(offAllowed = true))
        assertEquals(3, ListeningModes.next(1, true))
        assertEquals(1, ListeningModes.next(2, true))
        assertEquals(3, ListeningModes.next(2, false))
        assertEquals(1, ListeningModes.next(0, true)) // unknown: start of the cycle
    }

    // ---- Stepping aside for the notification shade ----

    private val w = 1080
    private val h = 2340

    @Test fun theShadeAndSystemDialogsCoverTheTop() {
        val shade = PanelRules.Win(system = true, left = 0, top = 0, right = w, bottom = h)
        assertTrue(PanelRules.covered(listOf(shade), w, h, locked = false))
    }

    @Test fun barsVolumeAndAppsDoNot() {
        val wins = listOf(
            PanelRules.Win(true, 0, 0, w, 110), // status bar
            PanelRules.Win(true, 0, h - 130, w, h), // navigation bar
            PanelRules.Win(true, w - 220, 500, w, 1500), // volume panel
            PanelRules.Win(true, w - 40, 300, w, 1300), // edge handle
            PanelRules.Win(false, 0, 0, w, h), // the app in front
        )
        assertFalse(PanelRules.covered(wins, w, h, locked = false))
    }

    @Test fun onTheLockScreenItStays() {
        val keyguard = PanelRules.Win(system = true, left = 0, top = 0, right = w, bottom = h)
        assertFalse(PanelRules.covered(listOf(keyguard), w, h, locked = true))
    }
}
