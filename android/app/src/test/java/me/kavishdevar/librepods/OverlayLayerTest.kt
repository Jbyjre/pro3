package me.kavishdevar.librepods

import android.app.Application
import android.view.WindowManager
import androidx.compose.ui.unit.IntSize
import me.kavishdevar.librepods.presentation.overlays.OverlayWindow
import me.kavishdevar.librepods.services.IslandAccess
import me.kavishdevar.librepods.services.IslandAccessService
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
    private var service: IslandAccessService? = null
    private val windows = mutableListOf<OverlayWindow>()

    private fun window(above: Boolean = true) = OverlayWindow(app, "test", anchorTop = true, aboveStatusBar = above).also { windows += it }

    @After fun tearDown() {
        windows.forEach { it.dismiss() }
        service?.let { IslandAccess.disconnected(it) }
    }

    @Test fun withoutTheServiceItsANormalOverlay() {
        ShadowSettings.setCanDrawOverlays(true)
        val w = window()
        assertTrue(w.show(IntSize(300, 120), 10) {})
        assertEquals(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, w.windowParams?.type)
        assertFalse(w.aboveBar)
        assertNull("no probe needed: this window hears about the status bar itself", w.probeParams)
    }

    @Test fun withTheServiceItGoesAboveTheStatusBar() {
        ShadowSettings.setCanDrawOverlays(true)
        service = Robolectric.setupService(IslandAccessService::class.java).also { IslandAccess.connected(it) }
        val w = window()
        assertTrue(w.show(IntSize(300, 120), 10) {})
        assertEquals(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, w.windowParams?.type)
        assertTrue(w.aboveBar)
        assertFalse(w.misplaced)
        // The status-bar probe: below the status bar, invisible, never touchable.
        val probe = w.probeParams
        assertNotNull(probe)
        assertEquals(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, probe!!.type)
        assertEquals(0f, probe.alpha)
        assertTrue(probe.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE != 0)
        assertEquals(1, probe.width)
        // The service stops: the window knows it's in the wrong place and goes.
        IslandAccess.disconnected(service!!)
        assertTrue(w.misplaced)
        w.dismiss()
        assertNull(w.probeParams)
    }

    @Test fun theServiceAloneIsEnoughToShow() {
        ShadowSettings.setCanDrawOverlays(false)
        service = Robolectric.setupService(IslandAccessService::class.java).also { IslandAccess.connected(it) }
        val w = window()
        assertTrue(w.canShow())
        assertTrue(w.show(IntSize(300, 120), 10) {})
        assertEquals(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, w.windowParams?.type)
    }

    @Test fun theBottomCardStaysANormalOverlay() {
        ShadowSettings.setCanDrawOverlays(true)
        service = Robolectric.setupService(IslandAccessService::class.java).also { IslandAccess.connected(it) }
        val w = window(above = false)
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
