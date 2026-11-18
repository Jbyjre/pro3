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
    private var clock = 1_700_000_000_000L
    private val chrome = "fixture.chrome"
    private val webAppWindow = "org.chromium.chrome.browser.webapps.SameTaskWebApkActivity"
    @Before fun setup() {
        listOf(chess, chat, home).forEach(::install)
        BreathAccess.wallClock = { clock }
        BreathAccess.gate.reset(); BreathAccess.gate.forgetPauses()
        context.getSharedPreferences("breath_seen", Context.MODE_PRIVATE).edit().clear().commit()
        // The older tests below describe one pause per open, so they run with the memory off.
        BreathPrefs.prefs(context).edit().clear().putStringSet("apps", setOf(chess, chat))
            .putBoolean("enabled", false).putLong("paused_until", Long.MAX_VALUE).putInt("grace_min", 0).commit()
        startService()
    }
    private fun startService() {
        controller = Robolectric.buildService(BreathAccessService::class.java).create()
        service = controller.get()
        org.robolectric.util.ReflectionHelpers.callInstanceMethod<Unit>(service, "onServiceConnected")
        resolve()
        assertTrue(BreathAccess.connected.value)
    }
    @After fun cleanup() { controller.destroy(); BreathAccess.wallClock = { System.currentTimeMillis() } }
    private fun withGrace(minutes: Int) { BreathPrefs.setOptions(context, BreathPrefs.options(context).copy(graceMinutes = minutes)); resolve() }
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
    @Test fun ordinaryAppEventAttachesImmediatelyThenHoldsForExactlyThreeSeconds() {
        event(chess)
        // No handler tick, sleep, or package query is needed for a prepared selected app.
        assertEquals(1, layers().size)
        assertTrue(BreathInteraction.active)
        val params = layers().single().layoutParams as WindowManager.LayoutParams
        assertEquals(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, params.type)
        assertEquals(0, params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
        draw()
        ShadowLooper.idleMainLooper(2999, MILLISECONDS)
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
        ShadowLooper.idleMainLooper(1500, MILLISECONDS)
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
        ShadowLooper.idleMainLooper(3000, MILLISECONDS)
        assertTrue(layers().isEmpty())
    }
    @Test fun lockSystemSheetsAndDisconnectCleanUpWindowsAndCallbacks() {
        event(chess); draw()
        context.sendBroadcast(Intent(Intent.ACTION_SCREEN_OFF)); ShadowLooper.idleMainLooper()
        assertTrue(layers().isEmpty())
        event(chess); draw()
        event("com.android.systemui", "android.widget.FrameLayout")
        assertEquals("a system window must not cut a running pause", 1, layers().size)
        event(chat); draw()
        assertEquals(chat, layers().single().destination)
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
    @Test fun systemWindowsNeverCutARunningPauseShort() {
        event(chess); draw()
        ShadowLooper.idleMainLooper(500, MILLISECONDS)
        // A heads-up notification, the volume panel, a permission sheet: none may end the pause halfway.
        event("com.android.systemui", "android.widget.FrameLayout")
        event("android", "android.app.AlertDialog")
        ShadowLooper.idleMainLooper(1000, MILLISECONDS)
        event("com.android.systemui", "android.widget.LinearLayout")
        assertEquals(1, layers().size)
        ShadowLooper.idleMainLooper(1499, MILLISECONDS)
        assertEquals("still there one millisecond before the end", 1, layers().size)
        ShadowLooper.idleMainLooper(1, MILLISECONDS)
        assertTrue(layers().isEmpty())
    }
    @Test fun returningFromTheShadeDoesNotRepauseWithinTheGraceTimeButDoesAfterIt() {
        withGrace(10)
        event(chess); draw(); ShadowLooper.idleMainLooper(3000, MILLISECONDS)
        event("com.android.systemui", "android.widget.FrameLayout")
        event(chess)
        assertTrue("pulling the shade and coming back is not a new visit", layers().isEmpty())
        clock += 10 * 60_000L + 1
        event("com.android.systemui", "android.widget.FrameLayout")
        event(chess)
        assertEquals(1, layers().size)
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
        ShadowLooper.idleMainLooper(3000, MILLISECONDS)
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
        ShadowLooper.idleMainLooper(2999, MILLISECONDS)
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

    @Test fun movingBetweenAppsAlreadyOpenedDoesNotPauseAgainUntilTheGraceTimePasses() {
        withGrace(10)
        event(chess); draw(); ShadowLooper.idleMainLooper(3000, MILLISECONDS)
        event(chat); draw(); ShadowLooper.idleMainLooper(3000, MILLISECONDS)
        event(chess); assertTrue("Gmail to Claude and back must stay quiet", layers().isEmpty())
        event(chat); assertTrue(layers().isEmpty())
        clock += 9 * 60_000L + 59_000L
        event(chess); assertTrue("one second short of ten minutes", layers().isEmpty())
        clock += 2_000L
        event(chat)
        assertEquals("ten minutes later it pauses once more", 1, layers().size)
        draw(); ShadowLooper.idleMainLooper(3000, MILLISECONDS)
        event(chess)
        assertEquals(1, layers().size)
    }
    @Test fun aLayerThatNeverDrewDoesNotUseUpTheGraceTime() {
        withGrace(10)
        event(chess)
        layers().single().visibility = android.view.View.INVISIBLE
        ShadowLooper.idleMainLooper(1000, MILLISECONDS)
        assertTrue(layers().isEmpty())
        event(chess)
        assertEquals("the next real event still pauses", 1, layers().size)
    }
    @Test fun leavingEarlyStillCountsAsHavingPaused() {
        withGrace(10)
        event(chess); draw()
        val view = layers().single()
        view.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_BACK))
        view.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_BACK))
        event(home); resolve()
        event(chess)
        assertTrue(layers().isEmpty())
    }
    @Test fun theGraceTimeSurvivesTheServiceRestarting() {
        withGrace(10)
        event(chess); draw(); resolve()
        assertTrue(BreathHistory.load(context, clock).containsKey(chess))
        controller.destroy()
        BreathAccess.gate.forgetPauses() // what a restarted process would start with
        startService()
        event(chess)
        assertTrue("remembered from the saved history", layers().isEmpty())
    }
    @Test fun aGraceOfZeroMeansEveryOpen() {
        withGrace(0)
        event(chess); draw(); ShadowLooper.idleMainLooper(3000, MILLISECONDS)
        event(home); resolve()
        event(chess)
        assertEquals(1, layers().size)
    }

    private fun installChrome() {
        val app = ApplicationInfo().apply { packageName = chrome; enabled = true; nonLocalizedLabel = "Chrome" }
        val infos = listOf("$chrome.Main", webAppWindow).map { name ->
            ActivityInfo().apply { packageName = chrome; this.name = name; applicationInfo = app; enabled = true; exported = true }
        }
        shadowOf(context.packageManager).installPackage(PackageInfo().apply { packageName = chrome; applicationInfo = app; activities = infos.toTypedArray() })
        BreathPrefs.select(context, chrome, true); resolve()
    }
    @Test fun installedWebAppsNeverPauseEvenWhenTheBrowserIsChosen() {
        installChrome()
        event(chrome, webAppWindow)
        assertTrue("the workout app opened from Chrome must not breathe", layers().isEmpty())
        assertFalse(BreathInteraction.active)
        event(chrome, "$chrome.Main")
        assertEquals("Chrome itself still pauses", 1, layers().size)
        draw()
    }
    @Test fun aWebAppOpeningClearsAPauseThatBeganForTheBrowser() {
        installChrome()
        event(chrome, "$chrome.Main"); draw()
        event(chrome, webAppWindow)
        assertTrue(layers().isEmpty())
        event(home); resolve()
        event(chrome, webAppWindow)
        assertTrue(layers().isEmpty())
    }
    @Test fun aWebApkPackageIsNeverChosenOrPaused() {
        val webapk = "org.chromium.webapk.a1b2c3"
        install(webapk)
        BreathPrefs.prefs(context).edit().putStringSet("apps", setOf(chess, webapk)).commit(); resolve()
        event(webapk, "$webapk.Main")
        assertTrue(layers().isEmpty())
        assertEquals(setOf(chess), BreathPrefs.selected(context))
    }

    @Test fun theChosenLengthAndTransitionAreUsedAndTheWindowIsTranslucentFullBleed() {
        BreathPrefs.setOptions(context, BreathOptions(durationMs = 5_000, transition = BreathTransition.Lift)); resolve()
        event(chess)
        val view = layers().single()
        assertEquals(5_000L, view.options.durationMs)
        assertEquals(BreathTransition.Lift, view.options.transition)
        val params = view.layoutParams as WindowManager.LayoutParams
        assertEquals(android.graphics.PixelFormat.TRANSLUCENT, params.format)
        assertEquals(WindowManager.LayoutParams.MATCH_PARENT, params.width)
        assertEquals(WindowManager.LayoutParams.MATCH_PARENT, params.height)
        assertEquals(0, params.fitInsetsTypes)
        draw()
        ShadowLooper.idleMainLooper(4999, MILLISECONDS)
        assertEquals(1, layers().size)
        ShadowLooper.idleMainLooper(1, MILLISECONDS)
        assertTrue(layers().isEmpty())
    }
    @Test fun aMixStepsThroughTheSayingsAndAPickAlwaysShowsItself() {
        val seen = ArrayList<Int>()
        repeat(4) {
            event(chess); draw()
            seen += layers().single().sayingIndex
            ShadowLooper.idleMainLooper(3000, MILLISECONDS)
            event(home); resolve()
        }
        assertEquals(listOf(0, 1, 2, 0), seen)
        BreathPrefs.setOptions(context, BreathOptions(words = BreathWords.Reset, graceMinutes = 0)); resolve()
        event(chess); draw()
        assertEquals(2, layers().single().sayingIndex)
    }
    @Test fun theLastPausesRealSpeedIsRecordedForThePhone() {
        event(chess); draw()
        assertNotNull(BreathAccess.lastTiming.value)
    }
}
