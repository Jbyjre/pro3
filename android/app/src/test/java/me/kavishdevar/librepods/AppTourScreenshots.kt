package me.kavishdevar.librepods

import android.app.Application
import android.content.Context
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import me.kavishdevar.librepods.billing.BillingManager
import me.kavishdevar.librepods.billing.FOSSBillingProvider
import me.kavishdevar.librepods.presentation.glint.PodsVideoConfig
import me.kavishdevar.librepods.presentation.navigation.NavigationRoot
import me.kavishdevar.librepods.presentation.navigation.Screen
import me.kavishdevar.librepods.presentation.screens.onboarding.NotSupportedPage
import me.kavishdevar.librepods.presentation.screens.onboarding.PermissionsPage
import me.kavishdevar.librepods.presentation.screens.onboarding.StayConnectedPage
import me.kavishdevar.librepods.presentation.theme.LibrePodsTheme
import me.kavishdevar.librepods.presentation.viewmodel.AirPodsViewModel
import me.kavishdevar.librepods.services.GlintStatus
import me.kavishdevar.librepods.services.LinkState
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A tour of the real app screens with LibrePods' demo AirPods data, in light and dark, written
 * to build/screenshots/tour. Tall window so most of each page is visible in one image.
 */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = Application::class, qualifiers = "w412dp-h1500dp-xxhdpi")
// Head gestures, rename and equalizer read the live AirPods service while drawing, so they can
// only be shown with real AirPods connected; they're left out of this tour.
class AppTourScreenshots {
    @get:Rule val rule = createComposeRule()

    private val out = "build/screenshots/tour"
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Before
    fun setUp() {
        PodsVideoConfig.enabled = false
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().clear().commit()
        BillingManager.provider = FOSSBillingProvider(context)
        GlintStatus.set(LinkState.Idle)
    }

    private fun tour(name: String, dark: Boolean, stack: List<Screen>, demo: Boolean = true, onboarding: Boolean = false) {
        if (dark) RuntimeEnvironment.setQualifiers("+night")
        val vm = AirPodsViewModel().apply { if (demo) activateDemoMode() }
        rule.setContent {
            LibrePodsTheme(m3eEnabled = false) {
                NavigationRoot(airPodsViewModel = vm, initialStack = stack, showOnboarding = onboarding)
            }
        }
        rule.mainClock.advanceTimeBy(1_500)
        Thread.sleep(1_500) // let pages that load in the background (licences) finish
        rule.mainClock.advanceTimeBy(500)
        rule.onRoot().captureRoboImage("$out/${name}_${if (dark) "dark" else "light"}.png")
    }

    private fun both(name: String, stack: List<Screen>, demo: Boolean = true) {
        tour(name, dark = false, stack = stack, demo = demo)
    }

    @Test fun home() = both("01_home", emptyList())
    @Test fun homeDark() = tour("01_home", dark = true, stack = emptyList())
    @Test fun homeDisconnected() = both("02_home_disconnected", emptyList(), demo = false)
    @Test fun homeDisconnectedDark() = tour("02_home_disconnected", dark = true, stack = emptyList(), demo = false)
    @Test fun appSettings() = both("03_app_settings", listOf(Screen.AppSettings))
    @Test fun appSettingsDark() = tour("03_app_settings", dark = true, stack = listOf(Screen.AppSettings))
    @Test fun stayConnected() = both("04_stay_connected", listOf(Screen.StayConnected))
    @Test fun purchase() = both("05_unlock", listOf(Screen.Purchase))
    @Test fun purchaseDark() = tour("05_unlock", dark = true, stack = listOf(Screen.Purchase))
    @Test fun accessibility() = both("06_accessibility", listOf(Screen.Accessibility))
    @Test fun adaptive() = both("07_adaptive_strength", listOf(Screen.AdaptiveStrength))
    @Test fun hearingProtection() = both("08_hearing_protection", listOf(Screen.HearingProtection))
    @Test fun longPress() = both("09_press_and_hold", listOf(Screen.LongPress("Left")))
    @Test fun version() = both("12_version", listOf(Screen.VersionInfo))
    @Test fun troubleshooting() = both("13_troubleshooting", listOf(Screen.Troubleshooting))
    @Test fun glintLab() = both("14_glint_lab", listOf(Screen.GlintLab))
    @Test fun hearingAid() = both("16_hearing_aid", listOf(Screen.HearingAid))
    @Test fun transparency() = both("17_transparency", listOf(Screen.TransparencyCustomization))
    @Test fun callControl() = both("18_call_control", listOf(Screen.CallControl("Mute/Unmute")))
    @Test fun microphone() = both("19_microphone", listOf(Screen.MicrophoneSettings))
    @Test fun licenses() = both("20_licenses", listOf(Screen.OpenSourceLicenses))
    @Test fun releaseNotes() = both("21_release_notes", listOf(Screen.ReleaseNotes))
    @Test fun onboardingStart() = tour("22_onboarding", dark = false, stack = emptyList(), onboarding = true)

    private fun page(name: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        rule.setContent { LibrePodsTheme(m3eEnabled = false) { content() } }
        rule.mainClock.advanceTimeBy(1_000)
        rule.onRoot().captureRoboImage("$out/$name.png")
    }

    @Test fun onboardingPermissions() = page("23_onboarding_permissions") { PermissionsPage({}, {}) }
    @Test fun onboardingThisPhone() = page("24_onboarding_this_phone") { NotSupportedPage {} }
    @Test fun onboardingStayConnected() = page("25_onboarding_stay_connected") { StayConnectedPage(onFinish = {}) }
}
