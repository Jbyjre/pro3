package me.kavishdevar.librepods

import android.app.Application
import me.kavishdevar.librepods.breathing.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[36],application=Application::class)
class BreathOptionsTest {
    @Test fun everyGradientAndItsInkKeepReadableContrastWithoutAScrim() {
        val base=androidx.core.graphics.ColorUtils.calculateContrast(BreathPalette.BASE_INK,BreathPalette.BASE)
        assertTrue(base >= 4.5)
        BreathPalette.entries.forEach { palette ->
            val colors=palette.gradientColors
            var lowest=Double.MAX_VALUE
            for (i in 0 until colors.lastIndex) {
                for (step in 0..16) {
                    val background=androidx.core.graphics.ColorUtils.blendARGB(colors[i],colors[i+1],step/16f)
                    lowest=minOf(lowest,androidx.core.graphics.ColorUtils.calculateContrast(palette.ink,background))
                }
            }
            println("${palette.label} minimum text contrast: $lowest")
            assertTrue("${palette.label} text contrast $lowest",lowest >= 4.5)
        }
    }
    @Test fun unsupportedSavedAppearanceFallsBack() {
        val c=RuntimeEnvironment.getApplication()
        BreathPrefs.prefs(c).edit().clear().putString("palette","future-value")
            .putString("still","bad-value").putLong("paused_until",System.currentTimeMillis()+60_000).commit()
        assertEquals(BreathOptions(),BreathPrefs.options(c))
    }
}
