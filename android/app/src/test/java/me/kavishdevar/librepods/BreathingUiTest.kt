package me.kavishdevar.librepods

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import me.kavishdevar.librepods.presentation.screens.AppEntry
import me.kavishdevar.librepods.presentation.screens.AppsScreen
import me.kavishdevar.librepods.presentation.screens.BreatheScreen
import me.kavishdevar.librepods.breathing.*
import me.kavishdevar.librepods.presentation.theme.LibrePodsTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import com.github.takahirom.roborazzi.captureRoboImage
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Config

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[36],application=Application::class,qualifiers="w412dp-h915dp-xxhdpi")
class BreathingUiTest {
    @get:Rule val rule=createComposeRule()
    @Test fun colorAndStillControlsPersistWithoutLosingSelectedApps() {
        val c=org.robolectric.RuntimeEnvironment.getApplication()
        BreathPrefs.prefs(c).edit().clear().commit()
        BreathPrefs.select(c,"fixture.chess",true)
        rule.setContent { LibrePodsTheme(m3eEnabled=false) { BreatheScreen() } }
        rule.onNodeWithText("Tangerine").performScrollTo().performClick().assertIsSelected()
        rule.onNodeWithText("Still color layer").performScrollTo().performClick()
        rule.runOnIdle {
            org.junit.Assert.assertEquals(BreathOptions(BreathPalette.Coral,true),BreathPrefs.options(c))
            org.junit.Assert.assertEquals(setOf("fixture.chess"),BreathPrefs.selected(c))
        }
        // An externally changed setting (e.g. the Quick Settings tile) refreshes the actual page.
        rule.runOnIdle { BreathPrefs.setOptions(c,BreathOptions(BreathPalette.Lagoon,true)) }
        rule.onNodeWithText("Pool").performScrollTo().assertIsSelected()
    }
    @Test fun pickerSearchFindsOrdinaryAppsByActualLabel() {
        val apps=listOf(AppEntry("fixture.chess","Chess"),AppEntry("fixture.claude","Claude"),
            AppEntry("fixture.muse","Meta Muse"),AppEntry("fixture.chat","ChatGPT"),
            AppEntry("fixture.scores","ESPN"),AppEntry("fixture.fantasy","ESPN Fantasy"))
        rule.setContent { LibrePodsTheme(m3eEnabled=false) { AppsScreen(preset=apps) } }
        rule.onNode(hasSetTextAction()).performTextInput("espn")
        rule.onNodeWithText("ESPN").assertExists()
        rule.onNodeWithText("ESPN Fantasy").assertExists()
        rule.onNodeWithText("Chess").assertDoesNotExist()
        rule.onNode(hasSetTextAction()).performTextReplacement("muse")
        rule.onNodeWithText("Meta Muse").assertExists()
        rule.onNodeWithText("ESPN").assertDoesNotExist()
    }
    @Test fun wholeRowsSelectAppsAndChosenFilterKeepsSetupManageable() {
        val c = org.robolectric.RuntimeEnvironment.getApplication()
        BreathPrefs.prefs(c).edit().clear().commit()
        org.robolectric.RuntimeEnvironment.setFontScale(1.8f)
        try {
            val apps = listOf(AppEntry("fixture.chess", "Chess"), AppEntry("fixture.fantasy", "ESPN Fantasy Football"))
            rule.setContent { LibrePodsTheme(m3eEnabled = false) { AppsScreen(preset = apps) } }
            rule.onNodeWithText("ESPN Fantasy Football").performClick().assertIsOn()
            org.junit.Assert.assertEquals(setOf("fixture.fantasy"), BreathPrefs.selected(c))
            rule.onNodeWithText("Chosen (1)").performClick().assertIsSelected()
            rule.onNodeWithText("Chess").assertDoesNotExist()
            rule.onNodeWithText("ESPN Fantasy Football").assertIsOn()
            rule.onRoot().captureRoboImage("build/screenshots/breathe/picker-large-text.png")
            rule.onNodeWithText("ESPN Fantasy Football").performClick()
            rule.onNodeWithText("Chosen (0)").assertExists()
            rule.onNodeWithText("Choose apps from All apps to see them here.").assertExists()
            rule.onNodeWithText("All apps").performClick()
            rule.onNodeWithText("Chess").assertExists()
            org.junit.Assert.assertTrue(BreathPrefs.selected(c).isEmpty())
        } finally { org.robolectric.RuntimeEnvironment.setFontScale(1f) }
    }

}
