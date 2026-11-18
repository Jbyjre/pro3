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

    @Test fun everyChoiceSurvivesSavingAndLoading() {
        val c=RuntimeEnvironment.getApplication()
        val chosen=BreathOptions(BreathPalette.Lagoon,true,7_000,BreathTransition.Lift,BreathWords.SlowDown,false,true,30)
        BreathPrefs.setOptions(c,chosen)
        assertEquals(chosen,BreathPrefs.options(c))
    }
    @Test fun theDefaultsAreThreeSecondsFadeMixHapticsOnNameOffTenMinutes() {
        val d=BreathOptions()
        assertEquals(3_000L,d.durationMs); assertEquals(BreathTransition.Fade,d.transition); assertEquals(BreathWords.Mix,d.words)
        assertTrue(d.haptics); assertFalse(d.showAppName); assertEquals(10,d.graceMinutes); assertEquals(600_000L,d.graceMs)
    }
    @Test fun anOutOfRangeSavedLengthIsClamped() {
        val c=RuntimeEnvironment.getApplication()
        BreathPrefs.prefs(c).edit().clear().putLong("duration_ms",500).commit()
        assertEquals(2_000L,BreathPrefs.options(c).durationMs)
        BreathPrefs.prefs(c).edit().putLong("duration_ms",120_000).commit()
        assertEquals(10_000L,BreathPrefs.options(c).durationMs)
    }
    @Test fun theDeepBaseIsTheSameHueMuchDarkerAndReadableUnderCream() {
        BreathPalette.entries.forEach { p ->
            val lab=DoubleArray(3).also { androidx.core.graphics.ColorUtils.colorToLAB(p.deep,it) }
            val primary=DoubleArray(3).also { androidx.core.graphics.ColorUtils.colorToLAB(p.primary,it) }
            assertTrue("${p.label} deep is much darker",lab[0] < primary[0]*0.4)
            assertTrue("${p.label} cream text on deep",androidx.core.graphics.ColorUtils.calculateContrast(BreathPalette.BASE_INK,p.deep) >= 7.0)
        }
    }
    @Test fun aSavedWebAppIsNeverReturnedAsAChosenApp() {
        val c=RuntimeEnvironment.getApplication()
        BreathPrefs.prefs(c).edit().clear().putStringSet("apps",setOf("fixture.chess","org.chromium.webapk.abc")).commit()
        assertEquals(setOf("fixture.chess"),BreathPrefs.selected(c))
        BreathPrefs.select(c,"org.chromium.webapk.xyz",true)
        assertEquals(setOf("fixture.chess"),BreathPrefs.selected(c))
    }
}
