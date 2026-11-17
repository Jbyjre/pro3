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
    private fun install(pkg: String, activityName: String = "$pkg.Main") {
        val app = ApplicationInfo().apply { packageName = pkg; enabled = true; nonLocalizedLabel = pkg }
        val info = ActivityInfo().apply { packageName = pkg; name = activityName; applicationInfo = app; enabled = true; exported = true }
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

    @Test fun failedAttachmentRetriesTheSameAppInsteadOfConsumingItsPause() {
        val real = service.getSystemService(WindowManager::class.java)
        var fail = true
        val failingOnce = java.lang.reflect.Proxy.newProxyInstance(WindowManager::class.java.classLoader,
            arrayOf(WindowManager::class.java)) { _, method, args ->
            if (method.name == "addView" && fail) {
                fail = false
                throw WindowManager.BadTokenException("fixture: token not ready")
            }
            method.invoke(real, *(args ?: emptyArray()))
        } as WindowManager
        org.robolectric.util.ReflectionHelpers.setField(service, "windowManager", failingOnce)
        event(chess)
        assertTrue(layers().isEmpty())
        assertTrue(BreathAccess.connected.value)
        assertTrue(BreathAccess.needsAttention.value)
        assertFalse(BreathInteraction.active)
        event(chess)
        assertEquals(1, layers().size)
        assertFalse(BreathAccess.needsAttention.value)
        draw()
        ShadowLooper.idleMainLooper(2500, MILLISECONDS)
        assertTrue(layers().isEmpty())
    }
    @Test fun aWindowThatNeverDrawsCannotKeepBlockingThePhone() {
        event(chess)
        layers().single().visibility = android.view.View.INVISIBLE
        ShadowLooper.idleMainLooper(1000, MILLISECONDS)
        assertTrue(layers().isEmpty())
        assertFalse(BreathInteraction.active)
        assertTrue(BreathAccess.needsAttention.value)
        event(chess)
        layers().single().visibility = android.view.View.VISIBLE
        draw()
        ShadowLooper.idleMainLooper(2499, MILLISECONDS)
        assertEquals(1, layers().size)
        ShadowLooper.idleMainLooper(1, MILLISECONDS)
        assertTrue(layers().isEmpty())
        assertFalse(BreathAccess.needsAttention.value)
    }
    @Test fun interruptionDiscardsAnUnfinishedActivityLookup() {
        // This activity appeared after the service prepared its known-class set.
        install(chess, "$chess.NewActivity")
        event(chess, "$chess.NewActivity")
        service.onInterrupt()
        resolve()
        assertTrue(layers().isEmpty())
        assertFalse(BreathInteraction.active)
        // A subsequent real event is still covered after that cancellation.
        event(chess, "$chess.NewActivity")
        resolve()
        assertEquals(1, layers().size)
    }

    @Test fun removingAnAppTakesEffectBeforeBackgroundPreparationFinishes() {
        event(chess); draw()
        BreathPrefs.select(context, chess, false)
        assertTrue(layers().isEmpty())
        assertFalse(BreathInteraction.active)
        event(chess)
        assertTrue(layers().isEmpty())
    }
    @Test fun choosingAKnownAppAppliesBeforeItsLabelAndAppearanceReload() {
        event(home); resolve() // Classify an ordinary app that is not selected yet.
        event(service.packageName, "me.kavishdevar.librepods.MainActivity")
        BreathPrefs.select(context, home, true)
        event(home)
        assertEquals(1, layers().size)
        draw()
    }
    @Test fun lateEventsAfterScreenOffWaitForAnInteractiveDisplay() {
        val power = context.getSystemService(android.os.PowerManager::class.java)
        shadowOf(context.getSystemService(android.app.KeyguardManager::class.java)).setKeyguardLocked(false)
        shadowOf(power).setIsInteractive(false)
        event(chess)
        assertTrue(layers().isEmpty())
        resolve()
        assertFalse(BreathInteraction.active)
        shadowOf(power).setIsInteractive(true)
        event(chess)
        assertEquals(1, layers().size)
        draw()
    }

    @Test fun pendingActivityLookupRechecksDisplayBeforePresenting() {
        install(chess, "$chess.NewActivity")
        event(chess, "$chess.NewActivity")
        val power = context.getSystemService(android.os.PowerManager::class.java)
        shadowOf(power).setIsInteractive(false)
        resolve()
        assertTrue(layers().isEmpty())
        assertFalse(BreathInteraction.active)
        shadowOf(power).setIsInteractive(true)
        event(chess, "$chess.NewActivity")
        assertEquals(1, layers().size)
        draw()
    }

}
