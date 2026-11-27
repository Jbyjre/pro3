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
        val chosen=BreathOptions(setOf(BreathPalette.Lagoon,BreathPalette.Violet),PaletteOrder.InOrder,true,7_000,BreathTransition.Iris,BreathWords.Custom,"Be still",false,true,30,PauseMode.AfterBreak,GrayMode.On)
        BreathPrefs.setOptions(c,chosen)
        assertEquals(chosen,BreathPrefs.options(c))
    }
    @Test fun theDefaultsAreTheVerseThreeSecondsFadeAllColorsRandomNewOpensFifteenMinutes() {
        val d=BreathOptions()
        assertEquals(3_000L,d.durationMs); assertEquals(BreathTransition.Fade,d.transition); assertEquals(BreathWords.Verse,d.words)
        assertEquals(BreathPalette.COLORS,d.palettes); assertEquals(PaletteOrder.Random,d.paletteOrder)
        assertEquals(PauseMode.NewOpens,d.mode); assertEquals(GrayMode.Auto,d.gray)
        assertTrue(d.haptics); assertFalse(d.showAppName); assertEquals(15,d.graceMinutes); assertEquals(900_000L,d.graceMs)
        assertTrue(15 in BreathOptions.GRACE_CHOICES)
        assertEquals("Psalm 51:10",d.saying.inHead)
    }
    @Test fun anOutOfRangeSavedLengthIsClamped() {
        val c=RuntimeEnvironment.getApplication()
        BreathPrefs.prefs(c).edit().clear().putLong("duration_ms",500).commit()
        assertEquals(2_000L,BreathPrefs.options(c).durationMs)
        BreathPrefs.prefs(c).edit().putLong("duration_ms",120_000).commit()
        assertEquals(10_000L,BreathPrefs.options(c).durationMs)
    }
    @Test fun oldSavedChoicesMigrateTheWayTheUserAsked() {
        val c=RuntimeEnvironment.getApplication()
        // Before pause modes existed: one colour, "mix" words, and "0 minutes" meaning every open.
        BreathPrefs.prefs(c).edit().clear().putString("palette","coral").putString("words","mix").putInt("grace_min",0).commit()
        val o=BreathPrefs.options(c)
        assertEquals("mix becomes the verse",BreathWords.Verse,o.words)
        assertEquals("every colour may appear",BreathPalette.COLORS,o.palettes)
        assertEquals("zero minutes meant every open",PauseMode.EveryTime,o.mode)
        assertEquals(15,o.graceMinutes)
    }
    @Test fun existingInstallMigratesOnceToFifteenMinuteCooldownWithoutLosingChosenAppsOrColors() {
        val c=RuntimeEnvironment.getApplication()
        c.getSharedPreferences("settings",0).edit().clear().commit()
        BreathPrefs.prefs(c).edit().clear()
            .putStringSet("apps",setOf("fixture.chess","fixture.chat"))
            .putStringSet("palettes",setOf(BreathPalette.Lagoon.key))
            .putInt("grace_min",10)
            .putString("mode","every")
            .putString("app_rules",BreathRules.encode(mapOf("fixture.chess" to AppRule(PauseMode.EveryTime,12_000L))))
            .commit()
        me.kavishdevar.librepods.services.FeatureMigration.retireCamera(c)
        val migrated=BreathPrefs.options(c)
        assertEquals(15,migrated.graceMinutes)
        assertEquals(PauseMode.NewOpens,migrated.mode)
        assertEquals(setOf("fixture.chess","fixture.chat"),BreathPrefs.selected(c))
        assertEquals(setOf(BreathPalette.Lagoon),migrated.palettes)
        assertEquals("custom app length stays while old every-time override resets",AppRule(null,12_000L),BreathPrefs.appRules(c)["fixture.chess"])
        // Once migrated, future user choices stick across restarts.
        BreathPrefs.setOptions(c,migrated.copy(graceMinutes=30))
        me.kavishdevar.librepods.services.FeatureMigration.retireCamera(c)
        assertEquals(30,BreathPrefs.options(c).graceMinutes)
    }
    @Test fun anEmptyOrUnknownSavedColourSetFallsBackToAllColours() {
        val c=RuntimeEnvironment.getApplication()
        BreathPrefs.prefs(c).edit().clear().putStringSet("palettes",setOf("nonsense")).commit()
        assertEquals(BreathPalette.COLORS,BreathPrefs.options(c).palettes)
        assertEquals(BreathPalette.COLORS.toList().sortedBy{it.ordinal},BreathOptions(palettes=emptySet()).chosenPalettes)
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
    @Test fun appRulesAreSavedPerAppAndCleared() {
        val c=RuntimeEnvironment.getApplication()
        BreathPrefs.prefs(c).edit().clear().commit()
        BreathPrefs.setAppRule(c,"gmail",AppRule(PauseMode.EveryTime,15_000))
        assertEquals(AppRule(PauseMode.EveryTime,15_000),BreathPrefs.appRules(c)["gmail"])
        BreathPrefs.setAppRule(c,"gmail",AppRule())
        assertTrue(BreathPrefs.appRules(c).isEmpty())
    }
    @Test fun randomNeverRepeatsTheLastColourAndInOrderStepsAndWraps() {
        val three=listOf(BreathPalette.Electric,BreathPalette.Coral,BreathPalette.Lagoon)
        var last: BreathPalette?=null
        repeat(200) { val next=BreathPalette.pick(three,PaletteOrder.Random,last,kotlin.random.Random(it)); assertNotEquals(last,next); assertTrue(next in three); last=next }
        assertEquals(BreathPalette.Coral,BreathPalette.pick(three,PaletteOrder.InOrder,BreathPalette.Electric))
        assertEquals(BreathPalette.Electric,BreathPalette.pick(three,PaletteOrder.InOrder,BreathPalette.Lagoon))
        assertEquals(BreathPalette.Electric,BreathPalette.pick(three,PaletteOrder.InOrder,null))
        assertEquals(BreathPalette.Violet,BreathPalette.pick(listOf(BreathPalette.Violet),PaletteOrder.Random,BreathPalette.Violet))
        assertEquals(BreathPalette.Electric,BreathPalette.pick(emptyList(),PaletteOrder.Random,null))
    }
    @Test fun everyColourAppearsEventuallyWhenRandom() {
        val seen=HashSet<BreathPalette>(); var last: BreathPalette?=null
        repeat(300) { last=BreathPalette.pick(BreathOptions().chosenPalettes,PaletteOrder.Random,last,kotlin.random.Random(it*31)); seen+=last!! }
        assertEquals(BreathPalette.COLORS,seen)
    }
    @Test fun monoIsTheStrongestLightAndDarkContrastForGrayscale() {
        val m=BreathPalette.Mono
        assertTrue(androidx.core.graphics.ColorUtils.calculateContrast(m.ink,m.gradientColors.first())>=7.0)
        assertTrue("sheet against its own dark base",androidx.core.graphics.ColorUtils.calculateContrast(m.gradientColors.first(),m.deep)>=7.0)
        BreathPalette.COLORS.forEach { assertTrue(it.label,androidx.core.graphics.ColorUtils.calculateContrast(it.gradientColors.first(),it.deep)>=1.5) }
    }
}
