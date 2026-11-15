package me.kavishdevar.librepods
import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[36],application=Application::class,qualifiers="w412dp-h915dp-xxhdpi")
class PopupGestureUiTest {
    @get:Rule val rule=createComposeRule()
    @Test fun anIslandTapMidAnimationIsNeverIgnored() {
        rule.mainClock.autoAdvance = false
        val app = RuntimeEnvironment.getApplication()
        var asked: me.kavishdevar.librepods.presentation.overlays.IslandPhase? = null
        val island = me.kavishdevar.librepods.presentation.overlays.IslandGeometry(app)
        rule.setContent {
            val dens = LocalDensity.current
            Box(Modifier.size(with(dens) { DpSize(island.expandedWindow.width.toDp(), island.expandedWindow.height.toDp()) })) {
                me.kavishdevar.librepods.presentation.overlays.IslandHost(
                    island, me.kavishdevar.librepods.presentation.overlays.IslandEvent.Connected,
                    me.kavishdevar.librepods.presentation.overlays.IslandPhase.Expanded, 0, blurAllowed = false,
                    onPhase = { asked = it }, onWindowSize = {}, onGone = {},
                )
            }
        }
        // Mid-opening, tap the middle of where it's growing: it turns around (to the pill).
        rule.mainClock.advanceTimeBy(140)
        rule.onRoot().performTouchInput { click(androidx.compose.ui.geometry.Offset(island.expandedWindow.width / 2f, island.restTop + 20f * app.resources.displayMetrics.density)) }
        rule.mainClock.advanceTimeBy(100)
        assertEquals(me.kavishdevar.librepods.presentation.overlays.IslandPhase.Compact, asked)
    }

}
