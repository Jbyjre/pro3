package me.kavishdevar.librepods

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.IntentFilter
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
    private val launcher = "fixture.launcher"
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
        install(launcher, "$launcher.Home")
        // The phone's Home app, as Android would list it for the HOME intent.
        shadowOf(context.packageManager).addResolveInfoForIntent(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            android.content.pm.ResolveInfo().apply { activityInfo = ActivityInfo().apply { packageName = launcher; name = "$launcher.Home"
                applicationInfo = ApplicationInfo().apply { packageName = launcher } } })
        BreathLog.clear()
        BreathAccess.wallClock = { clock }
        BreathAccess.gate.reset(); BreathAccess.gate.forgetPauses()
        context.getSharedPreferences("breath_seen", Context.MODE_PRIVATE).edit().clear().commit()
        // The older tests below describe one pause per open, so they run in "every time" mode.
        // The newer ones switch to the real rule with withMode(PauseMode.NewOpens).
        BreathPrefs.prefs(context).edit().clear().putStringSet("apps", setOf(chess, chat))
            .putBoolean("enabled", false).putLong("paused_until", Long.MAX_VALUE).putString("mode", "every").commit()
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
    private fun withMode(mode: PauseMode) { BreathPrefs.setOptions(context, BreathPrefs.options(context).copy(mode = mode)); resolve() }
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
    @Test fun aLayerThatNeverDrewDoesNotUseUpTheGraceTime() {
        withGrace(10)
        event(chess)
        layers().single().visibility = android.view.View.INVISIBLE
        ShadowLooper.idleMainLooper(1000, MILLISECONDS)
        assertTrue(layers().isEmpty())
        event(chess)
        assertEquals("the next real event still pauses", 1, layers().size)
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
    @Test fun theLastPausesRealSpeedIsRecordedForThePhone() {
        event(chess); draw()
        assertNotNull(BreathAccess.lastTiming.value)
    }

    // ---- The real rule: new opens pause, with a 15-minute cooldown (max 4 per hour). ----
    private fun pauseEnds() = ShadowLooper.idleMainLooper(3000, MILLISECONDS)
    private fun homeScreen() = event(launcher, "$launcher.Home")

    @Test fun versePopupOpensOnFirstAppOpenThenCoolsDownForFifteenMinutesMaxFourPerHour() {
        BreathPrefs.setOptions(context, BreathOptions()); resolve() // Real defaults: Verse + NewOpens + 15m cooldown
        var count = 0
        for (minute in 0 until 60) {
            homeScreen(); ShadowLooper.idleMainLooper(2_000, MILLISECONDS)
            val target = if (minute % 2 == 0) chess else chat
            event(target)
            if (layers().isNotEmpty()) {
                assertEquals("Psalm 51:10", layers().single().saying.inHead)
                draw()
                pauseEnds()
                count++
            }
            clock += 60_000L
        }
        assertEquals("at most 4 verse popups in one hour", 4, count)
    }

    @Test fun movingThroughAnotherAppAndBackStaysQuietButClosingAndReopeningFromHomePauses() {
        withMode(PauseMode.NewOpens); withGrace(0)
        event(chess); draw(); pauseEnds()
        event(chat); draw(); pauseEnds()
        event(chess)
        assertTrue("Gmail to Claude and back through Recents or a swipe: no pause", layers().isEmpty())
        homeScreen(); ShadowLooper.idleMainLooper(3000, MILLISECONDS)
        event(chess)
        assertEquals("closed, then opened again from Home: pause", 1, layers().size)
    }
    @Test fun aQuickPeekAtHomeOrRecentsIsNotAnOpen() {
        withMode(PauseMode.NewOpens)
        event(chess); draw(); pauseEnds()
        clock += 15 * 60_000L
        homeScreen(); ShadowLooper.idleMainLooper(1000, MILLISECONDS)
        event(chess)
        assertTrue(layers().isEmpty())
        val log = BreathLog.entries.value.joinToString { it.text }
        assertTrue(log, log.contains("within a moment"))
    }
    @Test fun anAppOpenedFromANotificationPausesAfterCooldown() {
        withMode(PauseMode.NewOpens)
        event(chess); draw(); pauseEnds()
        event(chat)
        assertTrue("second chosen app inside 15-minute cooldown stays quiet", layers().isEmpty())
        clock += 15 * 60_000L
        event("com.android.systemui", "com.android.systemui.shade.NotificationShadeWindowView")
        event(chess)
        assertEquals(1, layers().size)
    }
    @Test fun otherSystemWindowsAndTheRecentsWindowAreTreatedAsSuch() {
        withMode(PauseMode.NewOpens)
        event(chess); draw(); pauseEnds()
        event("com.android.systemui", "android.widget.FrameLayout")   // volume panel and the like: ignored
        clock += 15 * 60_000L
        event("com.android.systemui", "com.android.systemui.recents.RecentsActivity"); ShadowLooper.idleMainLooper(3000, MILLISECONDS)
        event(chess)
        assertEquals("a Recents window counts like Home once cooldown has passed", 1, layers().size)
    }
    @Test fun everyTimeAppsPauseEvenWhenYouJustSwitchBackAndAfterABreakAppsDoNot() {
        withMode(PauseMode.EveryTime)
        event(chess); draw(); pauseEnds(); event(chat); draw(); pauseEnds()
        event(chess); assertEquals(1, layers().size); draw(); pauseEnds()
        withMode(PauseMode.AfterBreak)
        homeScreen(); ShadowLooper.idleMainLooper(3000, MILLISECONDS)
        event(chess)
        assertTrue("an after-a-break app waits for its cooldown even from Home", layers().isEmpty())
    }
    @Test fun theCooldownTimeBringsThePauseBackWhenYouSwitchBackAfterFifteenMinutes() {
        withMode(PauseMode.NewOpens)
        event(chess); draw(); pauseEnds(); event(chat)
        clock += 14 * 60_000L
        event(chess)
        assertTrue("still inside 15-minute cooldown", layers().isEmpty())
        event(chat)
        clock += 60_000L
        event(chess)
        assertEquals("fifteen minutes after its last pause", 1, layers().size)
    }
    @Test fun wakingThePhoneIntoTheSameAppPausesOnlyAfterALongTimeOff() {
        withMode(PauseMode.NewOpens)
        event(chess); draw(); pauseEnds()
        context.sendBroadcast(Intent(Intent.ACTION_SCREEN_OFF)); ShadowLooper.idleMainLooper()
        ShadowLooper.idleMainLooper(14 * 60_000L, MILLISECONDS)
        clock += 14 * 60_000L
        event(chess)
        assertTrue("short sleep", layers().isEmpty())
        context.sendBroadcast(Intent(Intent.ACTION_SCREEN_OFF)); ShadowLooper.idleMainLooper()
        ShadowLooper.idleMainLooper(16 * 60_000L, MILLISECONDS)
        clock += 16 * 60_000L
        event(chess)
        assertEquals("long sleep", 1, layers().size)
    }
    @Test fun anAppOpenedFromTheLockScreenPausesAsSoonAsThePhoneIsUnlocked() {
        val keyguard = shadowOf(context.getSystemService(android.app.KeyguardManager::class.java))
        keyguard.setKeyguardLocked(true)
        event(chess)
        assertTrue(layers().isEmpty())
        keyguard.setKeyguardLocked(false)
        context.sendBroadcast(Intent(Intent.ACTION_USER_PRESENT)); ShadowLooper.idleMainLooper()
        assertEquals(1, layers().size)
        draw()
    }
    @Test fun anEventWhileTheScreenIsOffPausesOnceTheScreenIsOnAndUnlocked() {
        val power = context.getSystemService(android.os.PowerManager::class.java)
        shadowOf(context.getSystemService(android.app.KeyguardManager::class.java)).setKeyguardLocked(false)
        shadowOf(power).setIsInteractive(false)
        event(chess)
        assertTrue(layers().isEmpty()); assertFalse(BreathInteraction.active)
        shadowOf(power).setIsInteractive(true)
        context.sendBroadcast(Intent(Intent.ACTION_USER_PRESENT)); ShadowLooper.idleMainLooper()
        assertEquals("the turn was kept, not lost", 1, layers().size)
        draw()
    }
    @Test fun leavingForHomeEndsAPauseButNotInItsFirstInstant() {
        event(chess); draw()
        homeScreen()
        assertEquals("launchers report their own windows while an app is still opening", 1, layers().size)
        ShadowLooper.idleMainLooper(800, MILLISECONDS)
        homeScreen()
        assertTrue("Home pressed during the pause ends it", layers().isEmpty())
    }
    @Test fun leavingEarlyStillCountsAsHavingPausedForTheCooldown() {
        withMode(PauseMode.NewOpens)
        event(chess); draw()
        val view = layers().single()
        view.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_BACK))
        view.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_BACK))
        event(home); resolve()
        event(chess)
        assertTrue("cooldown started on first frame even when user backed out early", layers().isEmpty())
        event(chat)
        assertTrue("cooldown also covers other chosen apps", layers().isEmpty())
    }
    @Test fun theCooldownSurvivesTheServiceRestarting() {
        withMode(PauseMode.NewOpens)
        event(chess); draw(); resolve()
        assertTrue(BreathHistory.load(context, clock).containsKey(chess))
        assertEquals(clock, BreathHistory.lastPause(context, clock))
        controller.destroy()
        BreathAccess.gate.forgetPauses()
        clock += 5 * 60_000L // Only 5 minutes passed (< 15m cooldown)
        startService()
        assertNotNull("restored from the saved history", BreathAccess.gate.lastPauseOf(chess))
        assertNotNull("restored global cooldown", BreathAccess.gate.lastPauseTime())
        event(chess)
        assertTrue("cooldown remembers the pause across a service restart", layers().isEmpty())
        event(chat)
        assertTrue("cooldown also covers another chosen app across a service restart", layers().isEmpty())
    }
    @Test fun aLayerThatNeverDrewDoesNotUseUpTheTurnEvenInAfterABreakMode() {
        withMode(PauseMode.AfterBreak)
        event(chess)
        layers().single().visibility = android.view.View.INVISIBLE
        ShadowLooper.idleMainLooper(1000, MILLISECONDS)
        assertTrue(layers().isEmpty())
        event(chess)
        assertEquals(1, layers().size)
    }

    // ---- Per-app rules, colours, grayscale, words and the log. ----
    @Test fun oneAppCanBeMadeHarderThanTheRest() {
        withMode(PauseMode.NewOpens)
        BreathPrefs.setAppRule(context, chess, AppRule(PauseMode.EveryTime, 8_000)); resolve()
        event(chess); draw()
        assertEquals(8_000L, layers().single().options.durationMs)
        ShadowLooper.idleMainLooper(7_999, MILLISECONDS); assertEquals(1, layers().size)
        ShadowLooper.idleMainLooper(1, MILLISECONDS); assertTrue(layers().isEmpty())
        event(home); resolve(); event(chess)
        assertEquals("this app pauses every time", 1, layers().size); draw(); ShadowLooper.idleMainLooper(8_000, MILLISECONDS)
        event(chat)
        assertTrue("the other app respects the 15-minute cooldown", layers().isEmpty())
        clock += 15 * 60_000L
        event(home); resolve(); event(chat)
        assertEquals("once cooldown passes, opening chat pauses", 1, layers().size); draw(); pauseEnds()
        event(home); resolve(); event(chat)
        assertTrue("and then chat cools down again", layers().isEmpty())
    }
    @Test fun coloursRotateInOrderAndRandomNeverRepeats() {
        BreathPrefs.setOptions(context, BreathPrefs.options(context).copy(palettes = setOf(BreathPalette.Electric, BreathPalette.Coral, BreathPalette.Lagoon),
            paletteOrder = PaletteOrder.InOrder)); resolve()
        val seen = ArrayList<BreathPalette>()
        repeat(4) { event(chess); draw(); seen += layers().single().options.palette; pauseEnds(); event(home); resolve() }
        assertEquals(listOf(BreathPalette.Electric, BreathPalette.Coral, BreathPalette.Lagoon, BreathPalette.Electric), seen)
        BreathPrefs.setOptions(context, BreathPrefs.options(context).copy(paletteOrder = PaletteOrder.Random)); resolve()
        val random = ArrayList<BreathPalette>()
        repeat(8) { event(chess); draw(); random += layers().single().options.palette; pauseEnds(); event(home); resolve() }
        random.zipWithNext().forEach { (a, b) -> assertNotEquals(a, b) }
    }
    @Test fun aGrayscalePhoneGetsTheLightAndDarkLook() {
        BreathPrefs.setOptions(context, BreathPrefs.options(context).copy(gray = GrayMode.On)); resolve()
        event(chess); draw()
        assertEquals(BreathPalette.Mono, layers().single().options.palette)
        pauseEnds(); event(home); resolve()
        BreathPrefs.setOptions(context, BreathPrefs.options(context).copy(gray = GrayMode.Off)); resolve()
        event(chess); draw()
        assertNotEquals(BreathPalette.Mono, layers().single().options.palette)
    }
    @Test fun theLayerShowsTheVerseReferenceOnlyByDefaultAndYourOwnWordsWhenChosen() {
        event(chess); draw()
        assertEquals("Psalm 51:10", layers().single().saying.inHead)
        assertEquals(layers().single().saying.inHead, layers().single().saying.outHead)
        pauseEnds(); event(home); resolve()
        BreathPrefs.setOptions(context, BreathPrefs.options(context).copy(words = BreathWords.Custom, customText = "Be still")); resolve()
        event(chess); draw()
        assertEquals("Be still", layers().single().saying.inHead)
    }
    @Test fun everyDecisionIsWrittenInPlainWordsToTheLog() {
        withMode(PauseMode.NewOpens)
        event(chess); draw(); pauseEnds()
        event(chat) // blocked by cooldown; chat is now foreground
        withGrace(0)
        event(chess) // switched from chat back to chess: no pause ("switched back through another app")
        val text = BreathLog.asText()
        assertTrue(text, text.contains("pausing: first open of this app"))
        assertTrue(text, text.contains("no pause: cooling down after a recent pause"))
        assertTrue(text, text.contains("no pause: switched back through another app"))
    }
    @Test fun aChosenAppsNonScreenWindowIsLoggedSoItCanBeExplained() {
        event(chess, "android.app.Dialog"); resolve()
        assertTrue(BreathLog.asText(), BreathLog.asText().contains("ignored a window that is not a screen of the app"))
    }

    @Test fun lateLauncherEventDuringGraceWindowDoesNotCorruptForegroundWhenAppTransitionsScreens() {
        withMode(PauseMode.NewOpens)
        install(chess, "$chess.SecondScreen")
        resolve()
        event(chess, "$chess.Main"); draw()
        // Launcher reports a late window event while the app is still opening (within PROTECT_MS)
        homeScreen()
        assertEquals(1, layers().size)
        assertEquals(chess, BreathAccess.gate.frontPackage())
        pauseEnds()
        // App transitions from its splash/main activity to a second activity: must stay SameApp, not re-trigger or corrupt state
        event(chess, "$chess.SecondScreen")
        assertTrue(layers().isEmpty())
    }

    @Test fun samsungFinderAndEdgePanelCountAsHomeAndTransientSystemWindowsAreIgnored() {
        withMode(PauseMode.NewOpens)
        event(chess); draw(); pauseEnds()
        // Transient share sheet, biometric prompt, and keyboard must not count as switching to another app
        event("com.samsung.android.intentresolver", "com.android.internal.app.ChooserActivity")
        event("com.samsung.android.biometrics.app.setting", "com.samsung.android.biometrics.app.setting.BiometricPromptActivity")
        event("com.samsung.android.honeyboard", "com.samsung.android.honeyboard.service.HoneyBoardService")
        // User closes to Home / opens Samsung Finder or Edge Panel, then launches the app after the 15m cooldown
        clock += 15 * 60_000L
        event("com.samsung.android.app.galaxyfinder", "com.samsung.android.app.galaxyfinder.GalaxyFinderActivity")
        ShadowLooper.idleMainLooper(1_500, MILLISECONDS)
        event(chess)
        assertEquals("launched from Samsung Finder counts as opening from Home", 1, layers().size)
    }
}
