package me.kavishdevar.librepods
import android.app.Application
import android.view.WindowManager
import androidx.compose.ui.unit.IntSize
import me.kavishdevar.librepods.presentation.overlays.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class, qualifiers = "w412dp-h915dp-xxhdpi")
class IslandHandoverTest {
    private val app get() = RuntimeEnvironment.getApplication()
    @Test fun popupRestsBelowStatusBarAndCameraWithoutASeedWindow() {
        val geo = IslandGeometry(app)
        assertTrue(geo.windowTop + geo.restTop >= GlintOverlays.statusBarHeight(app))
    }
    @Test fun aWindowAndroidTakesAwayIsLetGo() {
        ShadowSettings.setCanDrawOverlays(true)
        val w = OverlayWindow(app, "test", anchorTop = true)
        var shown: Boolean? = null
        w.onShownChanged = { shown = it }
        assertTrue(w.show(IntSize(200, 80), 0) {})
        org.robolectric.shadows.ShadowLooper.idleMainLooper() // attached and drawn
        assertTrue(w.isShowing)
        assertTrue(w.windowView!!.isAttachedToWindow)
        // The system removes it (overlay permission revoked, service stopped).
        val wm = app.getSystemService(WindowManager::class.java)
        val v = w.windowView!!
        wm.removeViewImmediate(v)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertFalse("not believed to be up any more", w.isShowing)
        assertEquals(false, shown)
        // ...and it can come back.
        assertTrue(w.show(IntSize(200, 80), 0) {})
        assertTrue(w.isShowing)
        w.dismiss()
    }
}
