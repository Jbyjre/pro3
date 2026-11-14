package me.kavishdevar.librepods

import android.app.Application
import android.view.WindowManager
import androidx.compose.ui.unit.IntSize
import me.kavishdevar.librepods.presentation.overlays.OverlayWindow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings

/** Which layer the islands' windows go in, and that hidden windows can't eat other apps' taps. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class OverlayLayerTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private val windows = mutableListOf<OverlayWindow>()
    @org.junit.After fun close() { windows.forEach { it.dismiss() } }

    private fun window(above: Boolean = true) = OverlayWindow(app, "test", anchorTop = true).also { windows += it }

    @Test fun withoutTheServiceItsANormalOverlay() {
        ShadowSettings.setCanDrawOverlays(true)
        val w = window()
        assertTrue(w.show(IntSize(300, 120), 10) {})
        assertEquals(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, w.windowParams?.type)
    }

    @Test fun outOfSightItsTransparentAndUntouchable() {
        ShadowSettings.setCanDrawOverlays(true)
        val w = window()
        w.show(IntSize(300, 120), 10) {}
        w.setPresent(false)
        assertEquals(0f, w.windowParams!!.alpha)
        assertTrue(w.windowParams!!.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE != 0)
        w.setPresent(true)
        assertEquals(1f, w.windowParams!!.alpha)
        assertTrue(w.windowParams!!.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE == 0)
    }

    @Test fun withoutAnyPermissionNothingShows() {
        ShadowSettings.setCanDrawOverlays(false)
        val w = window()
        assertFalse(w.canShow())
        assertFalse(w.show(IntSize(300, 120), 10) {})
    }
}
