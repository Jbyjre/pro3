package me.kavishdevar.librepods

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import me.kavishdevar.librepods.breathing.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.android.controller.ServiceController
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.TimeUnit.MILLISECONDS

/** Real service + Android window manager: no synthetic replacement for the interception path. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class BreathAccessServiceTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private lateinit var controller: ServiceController<BreathAccessService>
    private lateinit var service: BreathAccessService
    private val chess = "fixture.chess"
    private val chat = "fixture.chat"
    private val home = "fixture.home"
    private fun install(pkg: String) {
        val app = ApplicationInfo().apply { packageName = pkg; enabled = true; nonLocalizedLabel = pkg }
        val info = ActivityInfo().apply { packageName = pkg; name = "$pkg.Main"; applicationInfo = app; enabled = true; exported = true }
        shadowOf(context.packageManager).installPackage(PackageInfo().apply {
            packageName = pkg; applicationInfo = app; activities = arrayOf(info)
        })
    }
    @Before fun setup() {
        listOf(chess, chat, home).forEach(::install)
        BreathPrefs.prefs(context).edit().clear().putStringSet("apps", setOf(chess, chat))
            .putBoolean("enabled", false).putLong("paused_until", Long.MAX_VALUE).commit()
        controller = Robolectric.buildService(BreathAccessService::class.java).create()
        service = controller.get()
        org.robolectric.util.ReflectionHelpers.callInstanceMethod<Unit>(service, "onServiceConnected")
        resolve()
        assertTrue(BreathAccess.connected.value)
    }
    @After fun cleanup() { controller.destroy() }
    private fun resolve() { repeat(50) { Thread.sleep(5); ShadowLooper.idleMainLooper() } }
    private fun event(pkg: String, cls: String = "$pkg.Main", type: Int = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
        service.onAccessibilityEvent(AccessibilityEvent.obtain(type).apply { packageName = pkg; className = cls })
    }
    private fun layers(): List<BreathView> = org.robolectric.shadow.api.Shadow.extract<org.robolectric.shadows.ShadowWindowManagerImpl>(service.getSystemService(WindowManager::class.java)).views.filterIsInstance<BreathView>()
    private fun draw() {
        val view = layers().single()
        view.layout(0, 0, 412, 915)
        val bitmap = Bitmap.createBitmap(412, 915, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap)); bitmap.recycle()
    }
    @Test fun ordinaryAppEventAttachesImmediatelyThenHoldsForExactlyTwoAndAHalfSeconds() {
        event(chess)
        // No handler tick, sleep, or package query is needed for a prepared selected app.
        assertEquals(1, layers().size)
        assertTrue(BreathInteraction.active)
        val params = layers().single().layoutParams as WindowManager.LayoutParams
        assertEquals(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, params.type)
        assertEquals(0, params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
        draw()
        ShadowLooper.idleMainLooper(2499, MILLISECONDS)
        assertEquals(1, layers().size)
        ShadowLooper.idleMainLooper(1, MILLISECONDS)
        assertTrue(layers().isEmpty())
        assertFalse(BreathInteraction.active)
        // Underlying notification/link activity is never replaced by a launcher intent.
        assertNull(shadowOf(context as Application).nextStartedActivity)
    }
    @Test fun repeatedActivityDialogKeyboardAndContentEventsDoNotRestartThePause() {
        event(chess); draw()
        ShadowLooper.idleMainLooper(1500, MILLISECONDS)
        event(chess)
        event(chess, "android.app.Dialog")
        event("fixture.keyboard", "android.inputmethodservice.SoftInputWindow")
        event(chat, type = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
        resolve()
        assertEquals(1, layers().size)
        ShadowLooper.idleMainLooper(1000, MILLISECONDS)
        assertTrue(layers().isEmpty())
        event(chess)
        assertTrue(layers().isEmpty())
    }
    @Test fun homeRearmsAndRapidAppSwitchReplacesInsteadOfStacking() {
        event(chess); draw()
        ShadowLooper.idleMainLooper(500, MILLISECONDS)
        event(chat)
        assertEquals(1, layers().size)
        assertEquals(chat, layers().single().destination)
        draw()
        event(home); resolve()
        assertTrue(layers().isEmpty())
        event(chess)
        assertEquals(1, layers().size)
        draw()
        ShadowLooper.idleMainLooper(2500, MILLISECONDS)
        assertTrue(layers().isEmpty())
    }
    @Test fun lockSystemSheetsAndDisconnectCleanUpWindowsAndCallbacks() {
        event(chess); draw()
        context.sendBroadcast(Intent(Intent.ACTION_SCREEN_OFF)); ShadowLooper.idleMainLooper()
        assertTrue(layers().isEmpty())
        event(chess); draw()
        event("com.android.systemui", "android.widget.FrameLayout")
        assertTrue(layers().isEmpty())
        event(chat); draw()
        service.onUnbind(Intent())
        assertTrue(layers().isEmpty())
        assertFalse(BreathAccess.connected.value)
        ShadowLooper.idleMainLooper(10_000, MILLISECONDS)
        assertFalse(BreathInteraction.active)
        assertNull(shadowOf(context as Application).nextStartedActivity)
    }
    @Test fun changingSelectionRemovesAnActiveLayer() {
        event(chess); draw()
        BreathPrefs.select(context, chess, false)
        resolve()
        assertTrue(layers().isEmpty())
        assertFalse(BreathInteraction.active)
    }
    @Test fun legacyShortcutHandoffDoesNotAddAnotherPause() {
        BreathAccess.gate.shortcutCompleted(chess, android.os.SystemClock.elapsedRealtime())
        event(chess)
        assertTrue(layers().isEmpty())
        event(home); resolve()
        event(chess)
        assertEquals(1, layers().size)
    }
    @Test fun returningFromTheShadeCannotPermanentlyBypassTheSelectedApp() {
        event(chess); draw()
        event("com.android.systemui", "android.widget.FrameLayout")
        assertTrue(layers().isEmpty())
        event(chess)
        assertEquals(1, layers().size)
        draw()
        ShadowLooper.idleMainLooper(2499, MILLISECONDS)
        assertEquals(1, layers().size)
        ShadowLooper.idleMainLooper(1, MILLISECONDS)
        assertTrue(layers().isEmpty())
    }
    @Test fun backLeavesForHomeWithoutLaunchingOrKeepingATouchTarget() {
        event(chess); draw()
        val view = layers().single()
        view.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_BACK))
        view.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_BACK))
        assertTrue(layers().isEmpty())
        assertTrue(shadowOf(service).globalActionsPerformed.contains(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME))
        ShadowLooper.idleMainLooper(5000, MILLISECONDS)
        assertNull(shadowOf(context as Application).nextStartedActivity)
    }

}
