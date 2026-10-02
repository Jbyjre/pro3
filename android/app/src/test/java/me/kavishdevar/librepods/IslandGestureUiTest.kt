package me.kavishdevar.librepods

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.DpSize
import me.kavishdevar.librepods.presentation.overlays.MiniGeometry
import me.kavishdevar.librepods.presentation.overlays.MiniIslandHost
import me.kavishdevar.librepods.services.IslandGestures
import me.kavishdevar.librepods.services.IslandGestures.Action
import me.kavishdevar.librepods.services.IslandGestures.Gesture
import me.kavishdevar.librepods.services.NowPlaying
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Real touches on the real Dynamic Island composable (the same code the phone runs), checking
 * what each gesture does and that nothing is lost or doubled.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = Application::class, qualifiers = "w412dp-h915dp-xxhdpi")
class IslandGestureUiTest {
    @get:Rule val rule = createComposeRule()

    private val done = mutableListOf<Action>()
    private var pulledOutside = 0
    private lateinit var geo: MiniGeometry

    private fun setUp(actions: Map<Gesture, Action> = IslandGestures.defaults) {
        rule.mainClock.autoAdvance = false
        val app = RuntimeEnvironment.getApplication()
        val d = app.resources.displayMetrics.density
        val w = app.resources.displayMetrics.widthPixels
        val hole = android.graphics.Rect((w / 2 - 13 * d).toInt(), (10 * d).toInt(), (w / 2 + 13 * d).toInt(), (36 * d).toInt())
        geo = MiniGeometry(app, listOf(hole))
        val track = NowPlaying.Track(playing = true, title = "Midnight City", artist = "M83", app = "Spotify", fromSession = true)
        rule.setContent {
            val dens = LocalDensity.current
            Box(Modifier.size(with(dens) { DpSize(geo.compactWindow.width.toDp(), geo.compactWindow.height.toDp()) })) {
                MiniIslandHost(
                    geometry = geo, track = track, leaving = false, hidden = false,
                    onWindowSize = {}, onTouchable = {}, onGone = {},
                    actions = actions,
                    onAction = { done += it },
                    onPullOutside = { pulledOutside++ },
                )
            }
        }
        // Let it grow out of the camera first.
        rule.mainClock.advanceTimeBy(900)
    }

    private val centre get() = Offset(geo.compactWindow.width / 2f, geo.margin + geo.size.height / 2f)

    @Test fun oneTapExpandsAfterTheDoubleTapWait() {
        setUp()
        rule.onRoot().performTouchInput { click(centre) }
        rule.mainClock.advanceTimeBy(100)
        assertEquals("waits for a possible second tap", emptyList<Action>(), done)
        rule.mainClock.advanceTimeBy(400)
        assertEquals(listOf(Action.Expand), done)
    }

    @Test fun twoTapsPlayOrPauseAndNeverExpandFirst() {
        setUp()
        rule.onRoot().performTouchInput { click(centre); advanceEventTime(120); click(centre) }
        rule.mainClock.advanceTimeBy(600)
        assertEquals(listOf(Action.PlayPause), done)
    }

    @Test fun threeTapsSkipAtOnce() {
        setUp()
        rule.onRoot().performTouchInput { click(centre); advanceEventTime(110); click(centre); advanceEventTime(110); click(centre) }
        rule.mainClock.advanceTimeBy(16)
        assertEquals(listOf(Action.Next), done)
        rule.mainClock.advanceTimeBy(600)
        assertEquals("nothing left over", listOf(Action.Next), done)
    }

    @Test fun rapidTappingNeverLosesOrDoublesActions() {
        setUp()
        rule.onRoot().performTouchInput {
            repeat(6) { i -> if (i > 0) advanceEventTime(90); click(centre) }
        }
        rule.mainClock.advanceTimeBy(600)
        assertEquals(listOf(Action.Next, Action.Next), done)
    }

    @Test fun holdingOpensProWhileStillHeld() {
        setUp()
        rule.onRoot().performTouchInput { down(centre) }
        rule.mainClock.advanceTimeBy(300)
        assertEquals(emptyList<Action>(), done)
        rule.mainClock.advanceTimeBy(300)
        assertEquals(listOf(Action.OpenApp), done)
        rule.onRoot().performTouchInput { up() }
        rule.mainClock.advanceTimeBy(600)
        assertEquals("lifting doesn't add a tap", listOf(Action.OpenApp), done)
    }

    @Test fun swipesChangeSong() {
        setUp()
        val c = centre
        rule.onRoot().performTouchInput { swipe(c, c.copy(x = c.x - 140f), 160) }
        rule.mainClock.advanceTimeBy(400)
        rule.onRoot().performTouchInput { swipe(c, c.copy(x = c.x + 140f), 160) }
        rule.mainClock.advanceTimeBy(400)
        assertEquals(listOf(Action.Next, Action.Previous), done)
    }

    @Test fun pullingDownOpensTheIsland() {
        setUp()
        val c = centre
        rule.onRoot().performTouchInput { swipe(c, c.copy(y = c.y + 130f), 160) }
        rule.mainClock.advanceTimeBy(400)
        assertEquals(listOf(Action.Expand), done)
    }

    @Test fun pullingTheEdgeAroundThePillOpensNotifications() {
        setUp()
        val start = Offset(6f, 6f)
        rule.onRoot().performTouchInput { swipe(start, start.copy(y = geo.compactWindow.height - 4f), 160) }
        rule.mainClock.advanceTimeBy(400)
        assertEquals(1, pulledOutside)
        assertEquals("not a gesture on the pill", emptyList<Action>(), done)
        // A tap on the edge does nothing (the status bar ignores taps too).
        rule.onRoot().performTouchInput { click(start) }
        rule.mainClock.advanceTimeBy(400)
        assertEquals(emptyList<Action>(), done)
    }

    @Test fun customActionsAreUsedAndASingleTapIsInstantWhenNothingElseIsSet() {
        setUp(IslandGestures.defaults + mapOf(Gesture.Tap1 to Action.PlayPause, Gesture.Tap2 to Action.Nothing, Gesture.Tap3 to Action.Nothing))
        rule.onRoot().performTouchInput { click(centre) }
        rule.mainClock.advanceTimeBy(16)
        assertEquals(listOf(Action.PlayPause), done)
    }

    @Test fun aGestureSetToNothingDoesNothing() {
        setUp(IslandGestures.defaults + (Gesture.SwipeLeft to Action.Nothing))
        val c = centre
        rule.onRoot().performTouchInput { swipe(c, c.copy(x = c.x - 140f), 160) }
        rule.mainClock.advanceTimeBy(600)
        assertTrue(done.isEmpty())
    }
}
