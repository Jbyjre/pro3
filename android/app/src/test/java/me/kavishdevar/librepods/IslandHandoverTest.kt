package me.kavishdevar.librepods

import android.app.Application
import android.graphics.Rect
import android.view.WindowManager
import androidx.compose.ui.unit.IntSize
import me.kavishdevar.librepods.presentation.overlays.GlintOverlays
import me.kavishdevar.librepods.presentation.overlays.IslandGeometry
import me.kavishdevar.librepods.presentation.overlays.MiniGeometry
import me.kavishdevar.librepods.presentation.overlays.OverlayWindow
import me.kavishdevar.librepods.services.IslandLook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings

/**
 * The hand-over between the Dynamic Island and the pop-ups, for every camera shape and look:
 * a pop-up never settles over the camera or the clock, its window never covers the Dynamic
 * Island (so it stays tappable), and a window Android takes away is never believed to still be up.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class, qualifiers = "w412dp-h915dp-xxhdpi")
class IslandHandoverTest {
    private val app get() = RuntimeEnvironment.getApplication()

    private fun shapes(): Map<String, List<Rect>> {
        val d = app.resources.displayMetrics.density
        val w = app.resources.displayMetrics.widthPixels
        return mapOf(
            "punch-hole" to listOf(Rect((w / 2 - 13 * d).toInt(), (10 * d).toInt(), (w / 2 + 13 * d).toInt(), (36 * d).toInt())),
            "notch" to listOf(Rect((w / 2 - 80 * d).toInt(), 0, (w / 2 + 80 * d).toInt(), (30 * d).toInt())),
            "corner" to listOf(Rect((16 * d).toInt(), (10 * d).toInt(), (42 * d).toInt(), (36 * d).toInt())),
            "none" to emptyList(),
            "big hole low" to listOf(Rect((w / 2 - 20 * d).toInt(), (18 * d).toInt(), (w / 2 + 20 * d).toInt(), (58 * d).toInt())),
        )
    }

    private val looks = listOf(
        IslandLook.Look(),
        IslandLook.Look(size = IslandLook.Size.Large, width = IslandLook.Width.Roomy,
            slots = IslandLook.DEFAULT_SLOTS + (IslandLook.Situation.Music to (IslandLook.Slot.Title to IslandLook.Slot.Buds))),
        IslandLook.Look(size = IslandLook.Size.Small, slots = IslandLook.Situation.entries.associateWith { IslandLook.Slot.Nothing to IslandLook.Slot.Nothing }),
    )

    @Test fun aPopUpSettlesBelowTheCameraAndTheClock() {
        val d = app.resources.displayMetrics.density
        val bar = GlintOverlays.statusBarHeight(app)
        for ((name, cut) in shapes()) for (look in looks) {
            val mini = MiniGeometry(app, cut, look)
            val origin = mini.origin()
            val island = IslandGeometry(app, origin)
            val restOnScreen = island.windowTop + island.restTop
            assertTrue("$name: below the clock", restOnScreen >= bar)
            assertTrue("$name: below the Dynamic Island", restOnScreen >= origin.top + origin.height + 5 * d)
            // Without the Dynamic Island it still clears the clock and the camera.
            val alone = IslandGeometry(app, null)
            assertTrue("$name: alone, below the clock", alone.windowTop + alone.restTop >= bar)
        }
    }

    @Test fun aPopUpsWindowNeverCoversTheDynamicIsland() {
        for ((name, cut) in shapes()) for (look in looks) {
            val origin = MiniGeometry(app, cut, look).origin()
            val island = IslandGeometry(app, origin)
            assertTrue("$name: window starts under the pill", island.windowTop >= origin.top + origin.height)
            // ...and the pop-up grows out of the pill's underside, from its width.
            assertEquals(origin.width, island.seedW, 0.01f)
            assertEquals(0f, island.seedTop, 0.01f)
        }
    }

    @Test fun theDynamicIslandIsNeverCutOffAndFitsTheScreen() {
        val w = app.resources.displayMetrics.widthPixels
        for ((name, cut) in shapes()) for (look in looks) {
            val g = MiniGeometry(app, cut, look)
            assertTrue("$name: top inside the screen", g.centerY - g.size.height / 2f >= 0f)
            assertTrue("$name: fits across", g.compactWindow.width <= w && g.wideWindow.width <= w)
            // The compact pill is at least as wide as the camera it wraps.
            g.hole?.let { assertTrue("$name: wraps the camera", g.size.compactWidth >= it.width()) }
        }
    }

    @Test fun aWindowAndroidTakesAwayIsLetGo() {
        ShadowSettings.setCanDrawOverlays(true)
        val w = OverlayWindow(app, "test", anchorTop = true, aboveStatusBar = true)
        var shown: Boolean? = null
        w.onShownChanged = { shown = it }
        assertTrue(w.show(IntSize(200, 80), 0) {})
        org.robolectric.shadows.ShadowLooper.idleMainLooper() // attached and drawn
        assertTrue(w.isShowing)
        assertTrue(w.windowView!!.isAttachedToWindow)
        // The system removes it (overlay permission revoked, service stopped).
        val wm = app.getSystemService(WindowManager::class.java)
        val v = w.windowView!!
        wm.removeViewImmediate(v)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertFalse("not believed to be up any more", w.isShowing)
        assertEquals(false, shown)
        // ...and it can come back.
        assertTrue(w.show(IntSize(200, 80), 0) {})
        assertTrue(w.isShowing)
        w.dismiss()
    }
}
