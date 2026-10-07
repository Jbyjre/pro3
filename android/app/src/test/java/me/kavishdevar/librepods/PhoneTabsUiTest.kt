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
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import me.kavishdevar.librepods.billing.BillingManager
import me.kavishdevar.librepods.billing.FOSSBillingProvider
import me.kavishdevar.librepods.presentation.glint.PodsVideoConfig
import me.kavishdevar.librepods.presentation.navigation.NavigationRoot
import me.kavishdevar.librepods.presentation.overlays.GlancePanel
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

    @Test fun opensOnPhoneAndTheTabsSwitchPagesAndBackReturnsToPhone() {
        rule.setContent { LibrePodsTheme(m3eEnabled = false) { NavigationRoot(airPodsViewModel = AirPodsViewModel()) } }
        rule.waitForIdle()
        // The Phone tab is the main page.
        rule.onNode(hasText("Make it work fully")).assertExists()
        tab("Island").performClick()
        rule.waitForIdle()
        // The Island page itself is showing (not only its title), and the Phone page is gone.
        rule.onNode(hasText("Hide in these apps")).assertExists()
        rule.onNode(hasText("Make it work fully")).assertDoesNotExist()
        tab("AirPods").performClick()
        rule.waitForIdle()
        rule.onNode(hasText("Hide in these apps")).assertDoesNotExist()
        rule.onNode(hasText("Make it work fully")).assertDoesNotExist()
        // Back from a tab other than Phone goes to Phone, not out of the app.
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
        rule.onNode(hasText("Make it work fully")).assertExists()
        assertTrue("Back on the Phone tab should leave the app as usual", !rule.activity.isFinishing)
    }

    @Test fun theGlanceStartsATimerFromItsPresets() {
        rule.setContent {
            GlancePanel(content = Color.White, secondary = Color.Gray, dark = true, active = true, reduceMotion = true, onTouch = {}, onClose = {})
        }
        rule.onNode(hasContentDescription("Timer")).performClick()
        rule.waitForIdle()
        rule.onNode(hasContentDescription("Start a 5 minute timer")).performClick()
        rule.waitForIdle()
        val t = IslandTimer.state.value!!
        assertEquals(5 * 60_000L, t.total)
        assertTrue(t.running)
    }

    @Test fun theGlanceSwitchesSoundAndVibrate() {
        val audio = context.getSystemService(AudioManager::class.java)
        audio.ringerMode = AudioManager.RINGER_MODE_NORMAL
        rule.setContent {
            GlancePanel(content = Color.White, secondary = Color.Gray, dark = true, active = true, reduceMotion = true, onTouch = {}, onClose = {})
        }
        rule.onNode(hasContentDescription("Sound")).performClick()
        rule.waitForIdle()
        assertEquals(AudioManager.RINGER_MODE_VIBRATE, audio.ringerMode)
        rule.onNode(hasContentDescription("Vibrate, on")).performClick()
        rule.waitForIdle()
        assertEquals(AudioManager.RINGER_MODE_NORMAL, audio.ringerMode)
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
}
