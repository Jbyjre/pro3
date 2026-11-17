package me.kavishdevar.librepods

import android.app.Application
import android.content.Context
import android.media.AudioManager
import androidx.activity.ComponentActivity
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import me.kavishdevar.librepods.billing.BillingManager
import me.kavishdevar.librepods.billing.FOSSBillingProvider
import me.kavishdevar.librepods.presentation.glint.PodsVideoConfig
import me.kavishdevar.librepods.presentation.navigation.NavigationRoot
import me.kavishdevar.librepods.presentation.overlays.TimerPanel
import me.kavishdevar.librepods.presentation.theme.LibrePodsTheme
import me.kavishdevar.librepods.presentation.viewmodel.AirPodsViewModel
import me.kavishdevar.librepods.services.IslandTimer
import me.kavishdevar.librepods.services.TimerRules
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Pressing the real buttons: the tab bar, Back between tabs, the glance's Timer and Sound, and the
 * timer ringing after Android closed pro. (Unlike the screenshots, these check what happens.)
 */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class, qualifiers = "w412dp-h915dp-xxhdpi")
class PhoneTabsUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Before fun setUp() {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().clear().commit()
        me.kavishdevar.librepods.services.DeviceChoice.reset()
        PodsVideoConfig.enabled = false
        BillingManager.provider = FOSSBillingProvider(context)
        IslandTimer.forgetForTest()
    }

    @After fun tearDown() {
        IslandTimer.cancel(context)
        IslandTimer.forgetForTest()
    }

    private fun tab(label: String) = rule.onNode(hasText(label) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))

    @Test fun opensOnAirPodsAndTheTabsSwitchPagesAndBackReturnsToAirPods() {
        rule.setContent { LibrePodsTheme(m3eEnabled = false) { NavigationRoot(airPodsViewModel = AirPodsViewModel()) } }
        rule.waitForIdle()
        // The AirPods tab is the home tab (Jake, 2026-11-13): first in the bar and the page pro opens on.
        tab("AirPods").assertIsSelected()
        rule.onNode(hasText("Headphone pop-ups")).assertDoesNotExist()
        rule.onNode(hasText("A little space before your apps.")).assertDoesNotExist()
        tab("Phone").performClick()
        rule.waitForIdle()
        rule.onNode(hasText("Headphone pop-ups")).assertExists()
        tab("Breathe").performClick()
        rule.waitForIdle()
        // The Breathe page itself is showing (not only its tab title), and Phone is gone.
        rule.onNode(hasText("A little space before your apps.")).assertExists()
        rule.onNode(hasText("Headphone pop-ups")).assertDoesNotExist()
        // Back from a tab other than AirPods goes to AirPods, not out of the app.
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
        tab("AirPods").assertIsSelected()
        rule.onNode(hasText("A little space before your apps.")).assertDoesNotExist()
        rule.onNode(hasText("Headphone pop-ups")).assertDoesNotExist()
        assertTrue("Back on the AirPods tab should leave the app as usual", !rule.activity.isFinishing)
    }

    @Test fun disconnectAsksFirst() {
        val vm = AirPodsViewModel().apply { activateDemoMode() }
        rule.setContent { LibrePodsTheme(m3eEnabled = false) { NavigationRoot(airPodsViewModel = vm) } }
        rule.waitForIdle()
        val disconnect = hasText("Disconnect") and androidx.compose.ui.test.hasClickAction()
        rule.onNode(androidx.compose.ui.test.hasScrollToNodeAction()).performScrollToNode(disconnect)
        rule.onNode(disconnect).performClick()
        rule.waitForIdle()
        // A question first, and still connected.
        rule.onNode(hasText("Cancel")).assertExists()
        assertTrue(vm.uiState.value.isLocallyConnected)
        rule.onNode(hasText("Cancel")).performClick()
        rule.waitForIdle()
        assertTrue(vm.uiState.value.isLocallyConnected)
        // Asked again and confirmed: now it disconnects.
        rule.onNode(androidx.compose.ui.test.hasScrollToNodeAction()).performScrollToNode(disconnect)
        rule.onAllNodes(disconnect)[0].performClick()
        rule.waitForIdle()
        val buttons = rule.onAllNodes(disconnect).fetchSemanticsNodes().size
        rule.onAllNodes(disconnect)[buttons - 1].performClick()
        rule.waitForIdle()
        assertTrue(!vm.uiState.value.isLocallyConnected)
    }

    @Test fun aTimerStillRingsWhenAndroidClosedProBeforeTheEnd() {
        IslandTimer.start(context, 1L)
        // Android closes pro: everything in memory is gone, only what was saved remains.
        IslandTimer.forgetForTest()
        Thread.sleep(20)
        // The alarm wakes pro just for this.
        me.kavishdevar.librepods.services.TimerReceiver().onReceive(
            context, android.content.Intent("me.kavishdevar.librepods.TIMER_FIRE"),
        )
        assertTrue("the timer should be ringing", IslandTimer.state.value?.ringing == true)
        IslandTimer.cancel(context)
        assertNull(IslandTimer.state.value)
    }

    @Test fun aTimerMissedLongAgoIsNotRungLate() {
        assertNull(TimerRules.restore(60_000L, 1_000_000L, -1L, 1_000_000L + TimerRules.LATE_RING_MS + 1L, 5_000L))
        // Ended a moment ago (the alarm woke pro right at the end): due now.
        val due = TimerRules.restore(60_000L, 1_000_000L, -1L, 1_000_500L, 5_000L)!!
        assertEquals(5_000L, due.endsAt)
    }
    @Test fun restartingRestoresAnActivePhoneTimerWithoutHeadphones() {
        IslandTimer.start(context,60_000L)
        val expected=IslandTimer.state.value!!.endsAt
        IslandTimer.forgetForTest()
        IslandTimer.attach(context)
        // Restore converts the saved wall deadline to elapsed time; allow clock-sampling jitter.
        assertTrue(kotlin.math.abs(expected-IslandTimer.state.value!!.endsAt) < 100L)
        assertTrue(IslandTimer.state.value!!.running)
    }

}
