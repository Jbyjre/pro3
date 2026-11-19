package me.kavishdevar.librepods

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.github.takahirom.roborazzi.captureRoboImage
import me.kavishdevar.librepods.breathing.*
import me.kavishdevar.librepods.presentation.glint.GlintComfort
import me.kavishdevar.librepods.presentation.screens.*
import me.kavishdevar.librepods.presentation.theme.LibrePodsTheme
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class, qualifiers = "w412dp-h915dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BreatheDesignTest {
    @get:Rule val rule = createComposeRule()
    private val c get() = RuntimeEnvironment.getApplication()
    @Before fun reset() {
        BreathPrefs.prefs(c).edit().clear().commit()
        c.getSharedPreferences("settings", 0).edit().clear().commit()
        BreathAccess.connection.value = false
        BreathAccess.presentationFailed.value = false
    }
    @Test fun connectionHelpExpandsAndOpensTheActualAppSettings() {
        var chosen = false
        rule.setContent { LibrePodsTheme { BreatheScreen(navigateToApps = { chosen = true }) } }
        rule.onNodeWithText("Start with your apps").performClick()
        assertTrue(chosen)
        rule.onNodeWithText("Open pro’s App info").assertDoesNotExist()
        rule.onNodeWithText("Connection help").performScrollTo().performClick()
        rule.onNodeWithText("Open pro’s App info").performScrollTo().performClick()
        val intent = shadowOf(c).nextStartedActivity
        assertEquals(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, intent.action)
        assertEquals("package:${c.packageName}", intent.data.toString())
        rule.onNodeWithText("Connection help").performScrollTo().performClick()
        rule.onNodeWithText("Open pro’s App info").assertDoesNotExist()
        rule.onNodeWithText("How Breathe works").performScrollTo().performClick()
        rule.onNodeWithText("No screen text, messages or typing are read.", substring = true).performScrollTo().assertIsDisplayed()
    }
    @Test fun pickerClearSearchAndDonePreserveChosenApps() {
        var finished = false
        rule.setContent { LibrePodsTheme { AppsScreen(listOf(AppEntry("fixture.chess", "Chess"), AppEntry("fixture.chat", "ChatGPT")), onDone = { finished = true }) } }
        rule.onNodeWithText("Chess").performClick()
        rule.onNodeWithText("1 chosen").assertIsDisplayed()
        rule.onNode(hasSetTextAction()).performTextInput("missing")
        rule.onNodeWithText("No matches for \"missing\".").assertExists()
        rule.onNodeWithContentDescription("Clear search").performClick()
        rule.onNodeWithText("Chess").assertIsOn()
        rule.onRoot().captureRoboImage("build/screenshots/breathe/design-picker-light.png")
        rule.onNodeWithText("Done").performClick()
        assertTrue(finished)
        assertEquals(setOf("fixture.chess"), BreathPrefs.selected(c))
    }
    @Test fun previewStopsOnBackgroundAndRespondsToReducedMotion() {
        val owner = object : LifecycleOwner {
            val registry = LifecycleRegistry.createUnsafe(this)
            override val lifecycle: Lifecycle = registry
        }
        rule.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        rule.setContent { CompositionLocalProvider(LocalLifecycleOwner provides owner) { LibrePodsTheme { BreatheScreen() } } }
        assertFalse("Motion should be enabled for this playback test", GlintComfort.reduceMotion(c))
        rule.mainClock.autoAdvance = false
        rule.onNodeWithText("Replay").performScrollTo().performClick()
        rule.runOnIdle { org.robolectric.shadows.ShadowLooper.idleMainLooper() }
        rule.mainClock.advanceTimeBy(300)
        rule.onNodeWithText("Playing").assertIsNotEnabled()
        rule.mainClock.advanceTimeBy(2800)
        rule.onNodeWithText("Replay").assertIsEnabled()
        rule.onNodeWithText("Replay").performClick()
        rule.runOnIdle { org.robolectric.shadows.ShadowLooper.idleMainLooper() }
        rule.mainClock.advanceTimeBy(300)
        rule.onNodeWithText("Playing").assertIsNotEnabled()
        rule.runOnIdle { owner.registry.currentState = Lifecycle.State.STARTED }
        rule.mainClock.advanceTimeByFrame()
        rule.onNodeWithText("Replay").assertIsEnabled()
        rule.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        rule.mainClock.advanceTimeBy(3000)
        rule.onNodeWithText("Replay").assertIsEnabled()
        rule.onNodeWithText("Replay").performClick()
        rule.runOnIdle { org.robolectric.shadows.ShadowLooper.idleMainLooper() }
        rule.mainClock.advanceTimeBy(300)
        rule.onNodeWithText("Playing").assertIsNotEnabled()
        rule.runOnIdle { c.getSharedPreferences("settings", 0).edit().putBoolean(GlintComfort.PREF_REDUCE_MOTION, true).commit() }
        // Preference -> recomposition -> effect cancellation -> updated button semantics.
        rule.mainClock.advanceTimeBy(64)
        rule.onNodeWithText("Replay").assertIsEnabled()
        rule.onNodeWithText("Still, by design").assertExists()
        rule.mainClock.autoAdvance = true
    }
    @Test fun darkAppearanceAndPaletteControlsRemainReadable() {
        BreathPrefs.select(c, "fixture.chess", true)
        rule.setContent { LibrePodsTheme(darkTheme = true) { BreatheScreen() } }
        rule.onRoot().captureRoboImage("build/screenshots/breathe/design-dark-top.png")
        rule.onNodeWithText("Tangerine").performScrollTo().performClick().assertIsOff()
        rule.onNodeWithText("Still color layer").performScrollTo().performClick().assertIsOn()
        rule.onRoot().captureRoboImage("build/screenshots/breathe/design-dark-controls.png")
        assertEquals(BreathOptions(palettes = BreathPalette.COLORS - BreathPalette.Coral, still = true), BreathPrefs.options(c))
    }
}
