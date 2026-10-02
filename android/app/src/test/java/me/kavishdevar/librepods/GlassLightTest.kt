package me.kavishdevar.librepods

import android.app.Application
import android.hardware.SensorManager
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import me.kavishdevar.librepods.presentation.glint.GlassLight
import me.kavishdevar.librepods.presentation.glint.GlassPress
import me.kavishdevar.librepods.presentation.glint.rimAxis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** One light for all glass: shared fairly, never left running, pointing the right way. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class GlassLightTest {
    @Test fun sharedAndAlwaysReleased() {
        val app = RuntimeEnvironment.getApplication()
        val start = GlassLight.holders
        GlassLight.acquire(app) // the app
        GlassLight.acquire(app) // an island over another app
        assertEquals(start + 2, GlassLight.holders)
        GlassLight.release()
        assertEquals(start + 1, GlassLight.holders)
        GlassLight.release()
        assertEquals(start, GlassLight.holders)
        // Releasing more than acquired can't go negative (a stray release is harmless).
        repeat(3) { GlassLight.release() }
        assertTrue(GlassLight.holders >= 0)
        assertEquals(false, GlassLight.listening && GlassLight.holders == 0)
    }

    @Test fun lightFallsWhereAReflectionWould() {
        val g = SensorManager.GRAVITY_EARTH
        // Upright-ish at a normal reading angle: at rest, straight above.
        val rest = GlassLight.tiltFor(0f, 0.55f * g)
        assertEquals(0f, rest.x, 0.001f)
        assertEquals(GlassLight.REST_Y, rest.y, 0.001f)
        // Right edge down (gx negative): the light slides the other way.
        assertTrue(GlassLight.tiltFor(-3f, 0.55f * g).x > 0f)
        assertTrue(GlassLight.tiltFor(3f, 0.55f * g).x < 0f)
        // Never past the edges.
        val far = GlassLight.tiltFor(-50f, -50f)
        assertTrue(far.x in -1f..1f && far.y in -1f..1f)
    }

    @Test fun rimLightSwingsLikeTheApps() {
        val r = Rect(0f, 0f, 200f, 60f)
        val (a, b) = rimAxis(r, 0f)
        // Level: straight down the middle.
        assertEquals(Offset(100f, 0f), a)
        assertEquals(Offset(100f, 60f), b)
        // Swung: the lit point moves sideways, symmetrically.
        val (c, d) = rimAxis(r, 14f)
        assertTrue(c.x < 100f && d.x > 100f)
        assertEquals(200f, c.x + d.x, 0.01f)
    }

    @Test fun glassSwellsUnderAFinger() {
        assertTrue(GlassPress.SWELL > 1f && GlassPress.SWELL < 1.15f)
    }
}
