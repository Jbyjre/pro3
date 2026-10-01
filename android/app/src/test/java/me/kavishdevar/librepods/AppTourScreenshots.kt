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
import me.kavishdevar.librepods.presentation.theme.LibrePodsTheme
import me.kavishdevar.librepods.presentation.viewmodel.AirPodsViewModel
import me.kavishdevar.librepods.services.GlintStatus
import me.kavishdevar.librepods.services.LinkState
import org.junit.Before
import me.kavishdevar.librepods.presentation.components.StyledListItem
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import me.kavishdevar.librepods.services.BatteryEstimate
import me.kavishdevar.librepods.services.BatteryEstimator
import me.kavishdevar.librepods.services.BatteryTimeLeft
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
        java.io.File(context.filesDir, "heart").deleteRecursively()
        me.kavishdevar.librepods.services.HeartHistory.resetCache()
        BillingManager.provider = FOSSBillingProvider(context)
        GlintStatus.set(LinkState.Idle)
        BatteryTimeLeft.publish(null)
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
    /** The whole main screen, top to bottom (it's longer than the standard tour window). */
    @Test fun homeFull() {
        RuntimeEnvironment.setQualifiers("w412dp-h3400dp-xxhdpi")
        val vm = AirPodsViewModel().apply { activateDemoMode() }
        rule.setContent { LibrePodsTheme(m3eEnabled = false) { NavigationRoot(airPodsViewModel = vm) } }
        rule.mainClock.advanceTimeBy(2_000)
        rule.onRoot().captureRoboImage("$out/01d_home_full.png")
    }
    /** The rows from the "Needs a rooted phone" list, which used to wrap into each other. */
    @Test fun longListRows() {
        RuntimeEnvironment.setQualifiers("w412dp-h915dp-night-xxhdpi")
        rule.setContent {
            LibrePodsTheme(m3eEnabled = false) {
                androidx.compose.foundation.layout.Column(
                    androidx.compose.ui.Modifier.fillMaxSize()
                        .background(androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainer)
                        .padding(16.dp)
                ) {
                    me.kavishdevar.librepods.presentation.components.StyledList(title = "Needs a rooted phone") {
                        StyledListItem(name = "Hearing Aid", description = "Hearing aid mode and hearing test", enabled = false, onClick = null)
                        StyledListItem(name = "Loud Sound Reduction", description = "Softens loud noise", enabled = false, onClick = null)
                        StyledListItem(name = "Transparency customization", description = "Amplification, tone and balance", enabled = false, onClick = null)
                        StyledListItem(name = "System battery display", description = "Battery in Android's Bluetooth settings", enabled = false, onClick = null)
                    }
                }
            }
        }
        rule.mainClock.advanceTimeBy(800)
        rule.onRoot().captureRoboImage("$out/33_long_rows_dark.png")
    }
    @Test fun homeDark() = tour("01_home", dark = true, stack = emptyList())
    @Test fun homeDisconnected() = both("02_home_disconnected", emptyList(), demo = false)
    @Test fun homeDisconnectedDark() = tour("02_home_disconnected", dark = true, stack = emptyList(), demo = false)
    @Test fun appSettings() = both("03_app_settings", listOf(Screen.AppSettings))
    @Test fun appSettingsDark() = tour("03_app_settings", dark = true, stack = listOf(Screen.AppSettings))
    @Test fun stayConnected() = both("04_stay_connected", listOf(Screen.StayConnected))
    @Test fun homeTimeLeft() {
        BatteryTimeLeft.publish(BatteryEstimate(197, BatteryEstimator.Confidence.MEASURED, worn = true, charging = false, caseCharges = 1.52))
        both("01b_home_time_left", emptyList())
    }
    @Test fun homeCharging() {
        BatteryTimeLeft.publish(BatteryEstimate(null, BatteryEstimator.Confidence.MEASURED, worn = false, charging = true, caseCharges = 1.5, minutesToFull = 38))
        tour("01c_home_charging", dark = true, stack = emptyList())
    }
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
    @Test fun heartRate() {
        val t0 = System.currentTimeMillis() - 20 * 60_000L
        me.kavishdevar.librepods.services.HeartRate.clear()
        for (i in 0 until 1200) {
            val bpm = (72 + 14 * kotlin.math.sin(i / 90.0) + (if (i in 600..760) 38 else 0) * kotlin.math.sin((i - 600) / 160.0 * Math.PI)).toInt()
            me.kavishdevar.librepods.services.HeartRate.reading(bpm, t0 + i * 1000L)
        }
        val day = 24 * 3600_000L
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
            .putInt("glint_hr_age", 30)
            .putString("glint_hr_sessions", "${t0 - day},${t0 - day + 41 * 60_000},96,62,151\n${t0 - 2 * day},${t0 - 2 * day + 18 * 60_000},74,58,92")
            .commit()
        both("31_heart_rate", listOf(Screen.HeartRate))
        tour("31_heart_rate", dark = true, stack = listOf(Screen.HeartRate))
        me.kavishdevar.librepods.services.HeartRate.status(me.kavishdevar.librepods.services.HeartRate.Status.Off)
        me.kavishdevar.librepods.services.HeartRate.clear()
    }
    /** Three weeks of demo sessions with readings, for the history screens. */
    private fun seedHistory(): Long {
        val day = 24 * 3600_000L
        val today = java.time.LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        var last = 0L
        for (d in 20 downTo 0) {
            if (d % 4 == 3) continue
            for (k in 0 until (if (d % 3 == 0) 2 else 1)) {
                val start = today - d * day + (8 + k * 9) * 3600_000L
                val minutes = 25 + (d * 7 + k * 13) % 50
                val base = 64 + (d % 5) - k * 2
                val samples = (0 until minutes * 12).map { i ->
                    val effort = if (k == 1 && i in 120..260) (40 * kotlin.math.sin((i - 120) / 140.0 * Math.PI)).toInt() else 0
                    me.kavishdevar.librepods.services.HeartRate.Sample(start + i * 5000L, base + (6 * kotlin.math.sin(i / 40.0)).toInt() + effort)
                }
                me.kavishdevar.librepods.services.HeartHistory.save(context, samples)
                last = start
            }
        }
        return last
    }

    @Test fun heartHistory() {
        seedHistory()
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putInt("glint_hr_age", 30).commit()
        both("35_heart_history", listOf(Screen.HeartRate, Screen.HeartHistory))
        tour("35_heart_history", dark = true, stack = listOf(Screen.HeartRate, Screen.HeartHistory))
    }

    @Test fun heartSession() {
        val start = seedHistory()
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putInt("glint_hr_age", 30).commit()
        both("36_heart_session", listOf(Screen.HeartHistory, Screen.HeartSession(start)))
    }

    @Test fun recorder() = both("32_recorder", listOf(Screen.Recorder))
    @Test fun heartShare() {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
            .putBoolean("glint_link_ble", true)
            .putBoolean("glint_link_webhook", true)
            .putString("glint_link_webhook_url", "https://example.com/api/webhook/heart")
            .commit()
        me.kavishdevar.librepods.services.HeartLink.refresh()
        both("34_heart_share", listOf(Screen.HeartRate, Screen.HeartShare))
        tour("34_heart_share", dark = true, stack = listOf(Screen.HeartRate, Screen.HeartShare))
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().clear().commit()
    }
    @Test fun licenses() = both("20_licenses", listOf(Screen.OpenSourceLicenses))


    private fun setup(name: String, step: Int, dark: Boolean = false) {
        // Phone-sized window (Galaxy S25 FE is about 412 x 915 dp) to judge proportions.
        RuntimeEnvironment.setQualifiers(if (dark) "w412dp-h915dp-night-xxhdpi" else "w412dp-h915dp-xxhdpi")
        rule.setContent { me.kavishdevar.librepods.presentation.screens.onboarding.OnboardingScreen(startStep = step) {} }
        rule.mainClock.advanceTimeBy(1_500)
        rule.onRoot().captureRoboImage("$out/$name.png")
    }

    @Test fun setupWelcome() = setup("23_setup_welcome", 0)
    @Test fun setupWelcomeDark() = setup("23_setup_welcome_dark", 0, dark = true)
    @Test fun setupPermissionsDark() = setup("25_setup_permissions_dark", 2, dark = true)
    @Test fun setupThisPhone() = setup("24_setup_this_phone", 1)
    @Test fun setupPermissions() = setup("25_setup_permissions", 2)
    @Test fun setupStayConnected() = setup("26_setup_stay_connected", 3)
    @Test fun setupStayConnectedDark() = setup("26_setup_stay_connected_dark", 3, dark = true)
}
