package me.kavishdevar.librepods

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import me.kavishdevar.librepods.presentation.screens.AppEntry
import me.kavishdevar.librepods.presentation.screens.AppsScreen
import me.kavishdevar.librepods.presentation.theme.LibrePodsTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[36],application=Application::class,qualifiers="w412dp-h915dp-xxhdpi")
class BreathingUiTest {
    @get:Rule val rule=createComposeRule()
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
}
