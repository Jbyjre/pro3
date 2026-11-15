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
    @Test fun unsupportedSavedAppearanceFallsBackAndEnablingResumesThePause() {
        val c=RuntimeEnvironment.getApplication()
        BreathPrefs.prefs(c).edit().clear().putString("palette","future-value")
            .putString("still","bad-value").putLong("paused_until",System.currentTimeMillis()+60_000).commit()
        assertEquals(BreathOptions(),BreathPrefs.options(c))
        assertTrue(BreathPrefs.paused(c))
        BreathPrefs.setEnabled(c,true)
        assertTrue(BreathPrefs.enabled(c))
        assertFalse(BreathPrefs.paused(c))
    }
}
