package me.kavishdevar.librepods

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Application
import android.content.Intent
import android.content.pm.ResolveInfo
import android.content.pm.ServiceInfo
import android.view.accessibility.AccessibilityManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.CompositionLocalProvider
import com.github.takahirom.roborazzi.captureRoboImage
import me.kavishdevar.librepods.breathing.*
import me.kavishdevar.librepods.presentation.screens.BreatheScreen
import me.kavishdevar.librepods.presentation.theme.LibrePodsTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class, qualifiers = "w412dp-h915dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BreathSetupTest {
    @get:Rule val rule = createComposeRule()
    private val c get() = RuntimeEnvironment.getApplication()
    private fun allow(on: Boolean) {
        val info = AccessibilityServiceInfo()
        ReflectionHelpers.setField(info, "mResolveInfo", ResolveInfo().apply {
            serviceInfo = ServiceInfo().apply { packageName = c.packageName; name = BreathAccessService::class.java.name }
        })
        shadowOf(c.getSystemService(AccessibilityManager::class.java)).setEnabledAccessibilityServiceList(if (on) listOf(info) else emptyList())
    }
    @Test fun realSetupReflectsPermissionAndConnectionSeparatelyOnResume() {
        BreathPrefs.prefs(c).edit().clear().commit()
        BreathPrefs.select(c, "fixture.chat", true)
        allow(false)
        val owner = object : LifecycleOwner {
            val registry = LifecycleRegistry.createUnsafe(this)
            override val lifecycle: Lifecycle = registry
        }
        rule.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        rule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                LibrePodsTheme(m3eEnabled = false) { BreatheScreen() }
            }
        }
        rule.onNodeWithText("One step to connect").assertExists()
        rule.onNodeWithText("Connect Breathe").performScrollTo().performClick()
        assertEquals("android.settings.ACCESSIBILITY_DETAILS_SETTINGS", shadowOf(c).nextStartedActivity.action)
        rule.runOnIdle {
            owner.registry.currentState = Lifecycle.State.STARTED
            allow(true)
            owner.registry.currentState = Lifecycle.State.RESUMED
        }
        rule.onNodeWithText("Waiting for Android").assertExists()
        rule.runOnIdle { BreathAccess.connection.value = true }
        rule.onNodeWithText("Ready when you open").assertExists()
        rule.onNodeWithText("1 app · 3 seconds · Again after 10 min").assertExists()
        rule.onNodeWithText("Breathing pause").assertDoesNotExist()
        rule.onNodeWithText("Pause for 15 minutes").assertDoesNotExist()
        rule.onNodeWithText("Add icon").assertDoesNotExist()
        rule.onRoot().captureRoboImage("build/screenshots/breathe/setup-ready.png")
        rule.runOnIdle { BreathAccess.presentationFailed.value = true }
        rule.onNodeWithText("Breathe needs attention").assertExists()
        rule.onNodeWithText("Check connection").performScrollTo().assertIsDisplayed()
        rule.onRoot().captureRoboImage("build/screenshots/breathe/setup-recovery.png")
        rule.runOnIdle { BreathAccess.presentationFailed.value = false }
        rule.runOnIdle {
            owner.registry.currentState = Lifecycle.State.STARTED
            allow(false)
            BreathAccess.connection.value = false
            owner.registry.currentState = Lifecycle.State.RESUMED
        }
        rule.onNodeWithText("One step to connect").assertExists()
        rule.onNodeWithText("Connect Breathe").assertExists()
    }
    @Test fun initialSetupAndLargeTextAreScrollable() {
        RuntimeEnvironment.setFontScale(1.8f)
        try {
            BreathPrefs.prefs(c).edit().clear().commit()
            allow(false)
            rule.setContent { LibrePodsTheme(m3eEnabled = false) { BreatheScreen() } }
            rule.onNodeWithText("Start with your apps").assertExists()
            rule.onNodeWithText("Connect Breathe").performScrollTo().assertIsDisplayed()
            rule.onRoot().captureRoboImage("build/screenshots/breathe/setup-large-text.png")
            rule.onNodeWithText("Still color layer").performScrollTo().performClick()
            assertTrue(BreathPrefs.options(c).still)
            rule.onRoot().captureRoboImage("build/screenshots/breathe/design-large-controls.png")
        } finally { RuntimeEnvironment.setFontScale(1f) }
    }
}
