package me.kavishdevar.librepods

import android.app.Application
import android.provider.Settings
import me.kavishdevar.librepods.breathing.BreathGray
import me.kavishdevar.librepods.breathing.GrayMode
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class BreathGrayTest {
    private val c get() = RuntimeEnvironment.getApplication()
    private fun filter(enabled: Int, mode: Int) {
        Settings.Secure.putInt(c.contentResolver, "accessibility_display_daltonizer_enabled", enabled)
        Settings.Secure.putInt(c.contentResolver, "accessibility_display_daltonizer", mode)
    }
    @Test fun androidsGrayscaleColorFilterIsDetected() {
        filter(1, 0)
        assertTrue(BreathGray.detected(c))
    }
    @Test fun otherColorFiltersAndAnOffFilterAreNotGrayscale() {
        filter(1, 12); assertFalse("a color-blindness correction is not grayscale", BreathGray.detected(c))
        filter(0, 0); assertFalse("the filter is off", BreathGray.detected(c))
    }
    @Test fun theManualChoiceAlwaysWinsOverDetection() {
        filter(0, -1)
        assertTrue(BreathGray.active(c, GrayMode.On))
        assertFalse(BreathGray.active(c, GrayMode.Off))
        assertFalse(BreathGray.active(c, GrayMode.Auto))
        filter(1, 0)
        assertTrue(BreathGray.active(c, GrayMode.Auto)); assertFalse(BreathGray.active(c, GrayMode.Off))
    }
    @Test fun itWatchesBothSettingsSoTheLayerReactsWhenGrayscaleIsSwitched() {
        assertEquals(2, BreathGray.watched.size)
    }
}
