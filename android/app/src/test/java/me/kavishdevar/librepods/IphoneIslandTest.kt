package me.kavishdevar.librepods

import android.app.Application
import android.graphics.Rect
import me.kavishdevar.librepods.presentation.overlays.IslandGeometry
import me.kavishdevar.librepods.presentation.overlays.MiniGeometry
import me.kavishdevar.librepods.services.IslandSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The iPhone look follows Apple's published numbers (Human Interface Guidelines, Live
 * Activities): a 44 pt corner, opened width = screen minus 11 pt each side (at most 408 pt),
 * concentric corners, a key line only on a dark background, and a pop-up that opens out of the
 * Dynamic Island itself, around the camera, when it can sit above the status bar.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class, qualifiers = "w412dp-h915dp-xxhdpi")
class IphoneIslandTest {
    private val app get() = RuntimeEnvironment.getApplication()

    @Test fun applesOwnWidthsComeOut() {
        // Apple's table: 371 pt wide on a 393 pt iPhone, 408 pt on a 430 pt one.
        assertEquals(371f, IslandSpec.expandedWidthDp(393f), 0.01f)
        assertEquals(408f, IslandSpec.expandedWidthDp(430f), 0.01f)
        // A wider Android phone never goes past the largest iPhone's island.
        assertEquals(408f, IslandSpec.expandedWidthDp(480f), 0.01f)
        // A Pixel 6 (about 411 dp wide): 11 dp each side.
        assertEquals(389f, IslandSpec.expandedWidthDp(411f), 0.01f)
    }

    @Test fun cornersAre44OrACapsule() {
        val d = 3f
        assertEquals(44f * d, IslandSpec.cornerPx(160f * d, d), 0.01f)
        // Shorter than 88: a full capsule (the compact island).
        assertEquals(18.335f * d, IslandSpec.cornerPx(36.67f * d, d), 0.01f)
        assertEquals(30f, IslandSpec.concentric(44f, 14f), 0.01f)
        assertEquals(0f, IslandSpec.concentric(10f, 14f), 0.01f)
    }

    @Test fun keyLineOnlyOnDark() {
        assertEquals(0f, IslandSpec.keylineAlpha(darkBackground = false, glow = 1f), 0f)
        assertTrue(IslandSpec.keylineAlpha(darkBackground = true, glow = 1f) > 0f)
        assertEquals(0f, IslandSpec.keylineAlpha(darkBackground = true, glow = 0f), 0f)
        assertTrue(IslandSpec.keylineAlpha(darkBackground = true, glow = 2f) <= 0.32f)
    }

    @Test fun opensAroundTheCameraOnlyWhenItCan() {
        assertTrue(IslandSpec.opensAroundCamera(IslandSpec.Style.IPhone, pillUp = true, aboveStatusBar = true))
        assertFalse("no pill to grow out of", IslandSpec.opensAroundCamera(IslandSpec.Style.IPhone, pillUp = false, aboveStatusBar = true))
        assertFalse("the clock would draw over it", IslandSpec.opensAroundCamera(IslandSpec.Style.IPhone, pillUp = true, aboveStatusBar = false))
        assertFalse("glass drops out under it", IslandSpec.opensAroundCamera(IslandSpec.Style.Glass, pillUp = true, aboveStatusBar = true))
    }

    @Test fun aPopUpGrowsOutOfTheDynamicIslandItself() {
        val d = app.resources.displayMetrics.density
        val w = app.resources.displayMetrics.widthPixels
        val hole = Rect((w / 2 - 13 * d).toInt(), (10 * d).toInt(), (w / 2 + 13 * d).toInt(), (36 * d).toInt())
        val origin = MiniGeometry(app, listOf(hole)).origin()
        val g = IslandGeometry(app, origin, style = IslandSpec.Style.IPhone, aboveStatusBar = true)
        assertTrue(g.around)
        // Its top edge is the pill's top edge, and it starts as exactly the pill.
        assertEquals(origin.top, g.windowTop + g.restTop, 1f)
        assertEquals(g.restTop, g.seedTop, 0.01f)
        assertEquals(origin.width, g.seedW, 0.01f)
        assertEquals(origin.height, g.seedH, 0.01f)
        // The camera ends up inside it (the shape reaches below the hole).
        assertTrue(g.windowTop + g.restTop + g.compactH >= hole.bottom)
        // Apple's opened width and corner.
        assertEquals(minOf(w - 22f * d, 408f * d), g.expandedW, 1f)
        assertEquals(44f * d, g.expandedRadius, 0.01f)
    }

    @Test fun withoutTheSwitchItStillDropsOutBelow() {
        val d = app.resources.displayMetrics.density
        val w = app.resources.displayMetrics.widthPixels
        val hole = Rect((w / 2 - 13 * d).toInt(), (10 * d).toInt(), (w / 2 + 13 * d).toInt(), (36 * d).toInt())
        val origin = MiniGeometry(app, listOf(hole)).origin()
        val g = IslandGeometry(app, origin, style = IslandSpec.Style.IPhone, aboveStatusBar = false)
        assertFalse(g.around)
        assertTrue("window starts under the pill", g.windowTop >= origin.top + origin.height)
    }

    @Test fun theDetachedCircleHasRoomAndFits() {
        val w = app.resources.displayMetrics.widthPixels
        val mini = MiniGeometry(app, emptyList())
        val needed = mini.size.compactWidth / 2f + mini.detachedGap + mini.detachedD
        assertTrue("room for the circle beside the pill", mini.detachedWindow.width / 2f >= needed)
        assertTrue("fits the screen", mini.detachedWindow.width <= w)
        assertEquals(mini.compactWindow.height, mini.detachedWindow.height)
    }
}
