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
import androidx.compose.ui.graphics.asImageBitmap
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

    // ---- 2026-10-10: concentric contents, the music island, motion ----

    @Test fun theSquareCoverFitsThePillsRoundEnd() {
        val mini = MiniGeometry(app, emptyList())
        val fit = mini.size.side / 2f
        val half = IslandSpec.roundedSquareHalf(fit, 0.42f)
        // The square's farthest point (its rounded corner, on the diagonal) is exactly at the circle.
        val k = half * 0.42f
        val reach = (half - k) * kotlin.math.sqrt(2f) + k
        assertEquals(fit, reach, 0.01f)
        // And that circle sits inside the pill's round end with a margin.
        assertTrue(fit < mini.size.height / 2f)
        // Every spot beside the camera is centred on the round end's own centre (concentric).
        assertEquals(mini.size.height / 2f, mini.size.inset + mini.size.side / 2f, 0.01f)
    }

    @Test fun theOpenedCoverKeepsClearOfTheCorner() {
        val d = app.resources.displayMetrics.density
        val gap = IslandSpec.cornerGap(IslandSpec.CORNER_DP * d, 18f * d, 12f * d)
        // At least 10 dp between the cover's corner and the island's curve (never poking into it).
        assertTrue("gap ${gap / d} dp", gap >= 10f * d)
        // The formula itself: a fully concentric corner keeps exactly the inset all round.
        assertEquals(14f, IslandSpec.cornerGap(44f, 14f, 30f), 0.01f)
    }

    @Test fun aCircleInTheCornerKeepsItsGap() {
        val x = IslandSpec.insetFromCorner(corner = 40f, fromBottom = 22f, r = 15f, gap = 6f)
        val dx = 40f - x
        val dy = 40f - 22f
        val reach = kotlin.math.sqrt(dx * dx + dy * dy) + 15f
        assertTrue("reach $reach", reach <= 40f - 6f + 0.01f)
        // On a straight side it just keeps the gap.
        assertEquals(6f + 10f, IslandSpec.insetFromCorner(corner = 10f, fromBottom = 40f, r = 10f, gap = 6f), 0.01f)
    }

    @Test fun theMusicIslandFitsApplesHeightsAndWrapsTheCamera() {
        val d = app.resources.displayMetrics.density
        val w = app.resources.displayMetrics.widthPixels
        val hole = Rect((w / 2 - 13 * d).toInt(), (10 * d).toInt(), (w / 2 + 13 * d).toInt(), (36 * d).toInt())
        val origin = MiniGeometry(app, listOf(hole)).origin()
        for (bar in listOf(false, true)) {
            val g = IslandGeometry(app, origin, style = IslandSpec.Style.IPhone, aboveStatusBar = true, music = true, musicBar = bar)
            assertTrue(g.musicPage)
            val h = g.expandedH / d
            assertTrue("height $h dp", h >= IslandSpec.EXPANDED_MIN_H_DP && h <= IslandSpec.EXPANDED_MAX_H_DP)
            // Wraps the camera: nothing below a band, and the song's name starts below the lens.
            assertEquals(0f, g.expandedBand, 0.01f)
            assertTrue(g.windowTop + g.restTop + g.textTop >= hole.bottom)
            // The cover sits beside the camera, never under it.
            assertTrue(g.artInset + g.artSize < g.expandedW / 2f - hole.width() / 2f)
        }
        // The glass style keeps its own opened island.
        assertFalse(IslandGeometry(app, origin, style = IslandSpec.Style.Glass, aboveStatusBar = true, music = true).musicPage)
    }

    @Test fun everyFeelStaysInsideApplesTwoSecondsAndClosesWithoutBouncing() {
        for (f in me.kavishdevar.librepods.services.IslandMotion.Feel.entries) {
            for (s in listOf(f.open, f.openHeight, f.close)) {
                assertTrue("$f settles in ${me.kavishdevar.librepods.services.IslandMotion.settleMs(s)} ms",
                    me.kavishdevar.librepods.services.IslandMotion.settleMs(s) <= IslandSpec.MAX_ANIMATION_MS)
            }
            // A close that overshot would shrink smaller than the Dynamic Island and pop back.
            assertTrue("$f close", me.kavishdevar.librepods.services.IslandMotion.overshoot(f.close) <= 0.0001f)
        }
        assertEquals(0f, me.kavishdevar.librepods.services.IslandMotion.overshoot(me.kavishdevar.librepods.services.IslandMotion.Spring(1f, 400f)), 0f)
        assertTrue(me.kavishdevar.librepods.services.IslandMotion.overshoot(me.kavishdevar.librepods.services.IslandMotion.Spring(0.5f, 400f)) > 0.1f)
    }

    @Test fun songTimesReadLikeAMusicPlayer() {
        val np = me.kavishdevar.librepods.services.NowPlaying
        assertEquals("0:07", np.clock(7_400L))
        assertEquals("3:42", np.clock(222_000L))
        assertEquals("1:02:09", np.clock(3_729_000L))
        assertEquals("0:00", np.clock(-5L))
        val t = me.kavishdevar.librepods.services.NowPlaying.Track(playing = true, durationMs = 200_000L, positionMs = 50_000L, positionAtMs = 1_000L)
        assertEquals(60_000L, t.positionAt(11_000L))
        assertEquals(200_000L, t.positionAt(10_000_000L))
        assertEquals(0.3f, t.progress(11_000L)!!, 0.001f)
        // Paused: it stays where it is.
        assertEquals(50_000L, t.copy(playing = false).positionAt(11_000L))
        // Unknown length: no position.
        assertEquals(null, t.copy(durationMs = 0L).positionAt(11_000L))
    }

    @Test fun aWideCoverIsCroppedNotSquashed() {
        val img = android.graphics.Bitmap.createBitmap(160, 90, android.graphics.Bitmap.Config.ARGB_8888)
        val (o, sz) = me.kavishdevar.librepods.presentation.overlays.centreSquare(img.asImageBitmap())
        assertEquals(90, sz.width); assertEquals(90, sz.height)
        assertEquals(35, o.x); assertEquals(0, o.y)
    }

    @Test fun theLiquidGlassRimShowsInLightAndDark() {
        assertTrue(IslandSpec.glassRimAlpha(darkBackground = false, glow = 1f) > 0f)
        assertTrue(IslandSpec.glassRimAlpha(darkBackground = true, glow = 0f) > 0f)
        // Apple's key line stays dark-only.
        assertEquals(0f, IslandSpec.keylineAlpha(darkBackground = false, glow = 1f), 0f)
    }
}
