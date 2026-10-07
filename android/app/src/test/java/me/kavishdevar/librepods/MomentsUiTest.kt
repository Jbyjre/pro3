package me.kavishdevar.librepods

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import me.kavishdevar.librepods.presentation.overlays.MiniGeometry
import me.kavishdevar.librepods.presentation.overlays.MiniIslandHost
import me.kavishdevar.librepods.presentation.overlays.sampleIcon
import me.kavishdevar.librepods.services.IslandPrefs
import me.kavishdevar.librepods.services.MiniIslandRules
import me.kavishdevar.librepods.services.NowPlaying
import me.kavishdevar.librepods.services.SoundSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Real touches and real timing on the real Dynamic Island composable: swiping up puts away what
 * the pill has out, and a message's wider window grows before the name and shrinks after.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = Application::class, qualifiers = "w412dp-h915dp-xxhdpi")
class MomentsUiTest {
    @get:Rule val rule = createComposeRule()

    private val sizes = mutableListOf<IntSize>()
    private var putAway = 0
    private lateinit var geo: MiniGeometry

    private fun geometry(): MiniGeometry {
        val app = RuntimeEnvironment.getApplication()
        val d = app.resources.displayMetrics.density
        val w = app.resources.displayMetrics.widthPixels
        val hole = android.graphics.Rect((w / 2 - 13 * d).toInt(), (10 * d).toInt(), (w / 2 + 13 * d).toInt(), (36 * d).toInt())
        return MiniGeometry(app, listOf(hole))
    }

    private val centre get() = Offset(geo.wideWindow.width / 2f, geo.margin + geo.size.height / 2f)

    @Test fun swipingUpTucksTheSongNameBackAtOnce() {
        rule.mainClock.autoAdvance = false
        geo = geometry()
        val track = NowPlaying.Track(playing = true, title = "Midnight City", artist = "M83", app = "Spotify", fromSession = true)
        rule.setContent {
            val dens = LocalDensity.current
            // The window is as big as the widest the pill asks for, as on the phone.
            Box(Modifier.size(with(dens) { DpSize(geo.wideWindow.width.toDp(), geo.wideWindow.height.toDp()) })) {
                MiniIslandHost(
                    geometry = geo, track = track, leaving = false, hidden = false,
                    onWindowSize = { sizes += it }, onTouchable = {}, onGone = {}, onAction = {},
                    onPutAway = { putAway++; true },
                )
            }
        }
        // It grows out of the camera and the song's name comes out (the window was asked to be wide).
        rule.mainClock.advanceTimeBy(1_300)
        assertEquals("the name is out", geo.wideWindow, sizes.last())
        val c = centre
        rule.onRoot().performTouchInput { swipe(c, c.copy(y = c.y - 140f), 160) }
        // Well before the name would tuck back by itself (3.2 s), it has.
        rule.mainClock.advanceTimeBy(900)
        assertEquals("tucked back at once", geo.compactWindow, sizes.last())
        assertEquals("the swipe tucked the name, it didn't also put a moment away", 0, putAway)
    }

    @Test fun withoutASwipeTheNameStaysTheTimeYouChose() {
        rule.mainClock.autoAdvance = false
        geo = geometry()
        val prefs = IslandPrefs.prefs(RuntimeEnvironment.getApplication())
        prefs.edit().putInt(IslandPrefs.PREF_NAME_LINGER, IslandPrefs.NameLinger.Short.ordinal).commit()
        val track = NowPlaying.Track(playing = true, title = "Midnight City", artist = "M83", app = "Spotify", fromSession = true)
        rule.setContent {
            val dens = LocalDensity.current
            Box(Modifier.size(with(dens) { DpSize(geo.wideWindow.width.toDp(), geo.wideWindow.height.toDp()) })) {
                MiniIslandHost(
                    geometry = geo, track = track, leaving = false, hidden = false,
                    onWindowSize = { sizes += it }, onTouchable = {}, onGone = {}, onAction = {},
                )
            }
        }
        rule.mainClock.advanceTimeBy(1_300)
        assertEquals(geo.wideWindow, sizes.last())
        // Short is 1.8 s: gone by about 3 s after it came out, which the Normal 3.2 s would not be.
        rule.mainClock.advanceTimeBy(2_400)
        assertEquals(geo.compactWindow, sizes.last())
    }

    @Test fun swipingUpOnTheCompactPillPutsAwayAMomentWhenThereIsOne() {
        rule.mainClock.autoAdvance = false
        geo = geometry()
        // No song name here (no title), so the swipe is for the moment.
        val track = NowPlaying.Track(playing = true, fromSession = true)
        rule.setContent {
            val dens = LocalDensity.current
            Box(Modifier.size(with(dens) { DpSize(geo.compactWindow.width.toDp(), geo.compactWindow.height.toDp()) })) {
                MiniIslandHost(
                    geometry = geo, track = track, leaving = false, hidden = false,
                    onWindowSize = {}, onTouchable = {}, onGone = {}, onAction = {},
                    onPutAway = { putAway++; true },
                )
            }
        }
        rule.mainClock.advanceTimeBy(900)
        val c = Offset(geo.compactWindow.width / 2f, geo.margin + geo.size.height / 2f)
        rule.onRoot().performTouchInput { swipe(c, c.copy(y = c.y - 140f), 160) }
        rule.mainClock.advanceTimeBy(400)
        assertEquals(1, putAway)
    }

    @Test fun theMessageWindowGrowsFirstAndShrinksAfterTheMessageLeaves() {
        rule.mainClock.autoAdvance = false
        geo = geometry()
        assertTrue("the message look needs a wider window than usual", geo.messageWindow.width > geo.compactWindow.width)
        val message = SoundSource.Message("m", "com.chat", "Messages", sampleIcon(), "Alex", at = 1L, startedAt = 1L)
        var showing by mutableStateOf(true)
        rule.setContent {
            val dens = LocalDensity.current
            Box(Modifier.size(with(dens) { DpSize(geo.messageWindow.width.toDp(), geo.messageWindow.height.toDp()) })) {
                MiniIslandHost(
                    geometry = geo, track = NowPlaying.Track(), leaving = false, hidden = false,
                    content = if (showing) MiniIslandRules.Content.Message else MiniIslandRules.Content.Rest,
                    message = if (showing) message else null,
                    onWindowSize = { sizes += it }, onTouchable = {}, onGone = {}, onAction = {},
                )
            }
        }
        rule.mainClock.advanceTimeBy(1_000)
        assertTrue("asked for the wider window", geo.messageWindow in sizes)
        assertFalse("and did not shrink while the message shows", sizes.last() == geo.compactWindow)
        // The message leaves: the pill narrows first, then the window follows (not at the same instant).
        val before = sizes.size
        showing = false
        rule.mainClock.advanceTimeBy(200)
        assertEquals("the window waits for the pill to narrow", before, sizes.size)
        rule.mainClock.advanceTimeBy(700)
        assertEquals(geo.compactWindow, sizes.last())
    }
}
