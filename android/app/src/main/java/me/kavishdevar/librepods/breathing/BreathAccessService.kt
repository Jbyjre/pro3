package me.kavishdevar.librepods.breathing

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.database.ContentObserver
import android.graphics.Insets
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.kavishdevar.librepods.presentation.glint.FrameRate
import me.kavishdevar.librepods.presentation.glint.GlintComfort
import me.kavishdevar.librepods.presentation.overlays.GlintOverlays

/**
 * Window events only: no screen tree, text, usage polling, app relaunch, or Bluetooth dependency.
 * Selected activity classes, labels, appearance and the layer are prepared before an app opens.
 *
 * What starts a pause: a chosen app comes to the front AND has not paused within the grace time
 * (10 minutes unless changed). That is decided only when a window opens, so there is no timer.
 * What can stop one: Home or Back, the screen turning off, another app taking over, losing the
 * chosen app, or the permission being removed. System windows (a heads-up notification, the volume
 * panel) never cut a running pause short.
 */
class BreathAccessService : AccessibilityService() {
    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var revision = 0
    private var eventRevision = 0
    private var lastTransition = 0
    private var prepared = false
    private var historyLoaded = false
    private var pendingForeground: String? = null
    private var selected = emptySet<String>()
    private var labels = emptyMap<String, String>()
    private var graceMs = BreathOptions().graceMs
    private var lastSaying = -1
    private var permissionController: String? = null
    private val activities = mutableSetOf<ComponentName>()
    private val rejectedWindows = android.util.LruCache<ComponentName, Boolean>(128)
    private val pendingWindows = mutableMapOf<ComponentName, PendingWindow>()
    private class PendingWindow(var sequence: Int)
    private var layer: BreathView? = null
    private var shownPackage: String? = null
    private var windowManager: WindowManager? = null
    private var session = BreathSession()
    private var attached = false
    private var acceptingEvents = false
    private var attachedAt = 0L
    private var reportedToAttach = 0L
    private var lastEventTime = 0L
    private val back = OnBackInvokedCallback { leave() }
    private var backDispatcher: OnBackInvokedDispatcher? = null
    private val firstFrameTimeout = Runnable {
        if (attached && session.startedAt == null) failPresentation()
    }
    private val motionObserver = object : ContentObserver(main) {
        override fun onChange(selfChange: Boolean) { refresh() }
    }
    private val cancelInteraction: () -> Unit = { interruptPending(resetEntry = false) }
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        if (prefs === BreathPrefs.prefs(this) || key == GlintComfort.PREF_REDUCE_MOTION) refresh()
    }
    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            hide()
            BreathAccess.gate.reset()
            pendingForeground = null
            invalidatePending()
        }
    }
    private val finishPause = object : Runnable {
        override fun run() {
            if (!attached || session.cancelled) return
            val left = session.remaining(SystemClock.elapsedRealtime())
            if (left > 0) main.postDelayed(this, left) else hide()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        acceptingEvents = true
        BreathAccess.presentationFailed.value = false
        if (layer != null) { refresh(); return }
        windowManager = getSystemService(WindowManager::class.java)
        BreathAccess.gate.onPause = { pkg, at ->
            scope.launch(Dispatchers.IO) { BreathHistory.record(applicationContext, pkg, at) }
        }
        layer = BreathView(this).apply {
            onFirstFrame = {
                main.removeCallbacks(firstFrameTimeout)
                val now = SystemClock.elapsedRealtime()
                BreathAccess.timing.value = BreathAccess.Timing(reportedToAttach, now - attachedAt)
                android.util.Log.d("Breathe", "Android reported the app, layer attached $reportedToAttach ms later, first draw ${now - attachedAt} ms after that")
                session.firstFrame(now)
                main.postDelayed(finishPause, session.durationMs)
                // Only now has the app really paused: a layer that never drew must not use up its turn.
                shownPackage?.let { BreathAccess.gate.recordPause(it, BreathAccess.wallClock()) }
            }
            addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(view: View) {
                    backDispatcher = view.findOnBackInvokedDispatcher()
                    backDispatcher?.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, back)
                }
                override fun onViewDetachedFromWindow(view: View) { releaseBackDispatcher() }
            })
            setOnKeyListener { _, key, event ->
                if (key == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    leave()
                    true
                } else key == KeyEvent.KEYCODE_BACK
            }
        }
        BreathPrefs.prefs(this).registerOnSharedPreferenceChangeListener(prefsListener)
        getSharedPreferences("settings", MODE_PRIVATE).registerOnSharedPreferenceChangeListener(prefsListener)
        ContextCompat.registerReceiver(this, screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED)
        contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, motionObserver)
        refresh()
    }

    private fun leave() {
        performGlobalAction(GLOBAL_ACTION_HOME)
        interruptPending(resetEntry = false)
    }

    private fun invalidatePending() {
        lastTransition = ++eventRevision
        pendingForeground = null
        pendingWindows.clear()
    }

    private fun interruptPending(resetEntry: Boolean) {
        invalidatePending()
        if (resetEntry) BreathAccess.gate.reset()
        hide()
    }

    private fun releaseBackDispatcher() {
        backDispatcher?.unregisterOnBackInvokedCallback(back)
        backDispatcher = null
    }

    private fun failPresentation() {
        shownPackage?.let { BreathAccess.gate.retry(it) }
        hide()
        BreathAccess.presentationFailed.value = true
    }

    private fun refresh() {
        // Apply the chosen set before any background metadata work. A user can leave setup
        // and open an app immediately; removing a choice must also release its layer now.
        val apps = BreathPrefs.selected(this) - packageName
        selected = apps
        if (shownPackage != null && shownPackage !in apps) hide()
        val version = ++revision
        val loadHistory = !historyLoaded
        scope.launch {
            val loaded = withContext(Dispatchers.IO) {
                val names = mutableMapOf<String, String>()
                val components = mutableSetOf<ComponentName>()
                for (pkg in apps) runCatching {
                    val info = packageManager.getPackageInfo(pkg, PackageManager.GET_ACTIVITIES)
                    info.applicationInfo?.let { names[pkg] = packageManager.getApplicationLabel(it).toString() }
                    info.activities.orEmpty().forEach { components += ComponentName(pkg, it.name) }
                }
                val options = BreathPrefs.options(this@BreathAccessService).also { it.palette.gradientColors; it.palette.deep }
                Prepared(
                    apps, names, components, options, GlintComfort.reduceMotion(this@BreathAccessService),
                    runCatching { packageManager.resolveActivity(Intent("android.content.pm.action.REQUEST_PERMISSIONS"), PackageManager.MATCH_SYSTEM_ONLY)?.activityInfo?.packageName }.getOrNull(),
                    if (loadHistory) BreathHistory.load(this@BreathAccessService, BreathAccess.wallClock()) else null,
                    if (loadHistory) BreathHistory.lastSaying(this@BreathAccessService) else null,
                )
            }
            if (version != revision || !acceptingEvents) return@launch
            selected = loaded.apps
            labels = loaded.names
            graceMs = loaded.options.graceMs
            permissionController = loaded.permissionController
            loaded.history?.let { BreathAccess.gate.restore(it); historyLoaded = true }
            loaded.lastSaying?.let { lastSaying = it }
            activities.clear()
            activities.addAll(loaded.components)
            rejectedWindows.evictAll()
            layer?.apply { options = loaded.options; systemReduced = loaded.reduced }
            if (shownPackage != null && shownPackage !in selected) hide()
            BreathAccess.connection.value = true
            if (!prepared) {
                prepared = true
                pendingForeground?.let { pendingForeground = null; enter(it) }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!acceptingEvents || event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        val cls = event.className?.toString() ?: return
        val eventTime = event.eventTime
        // Our own overlay sends window events too. Only pro's real activity counts as leaving.
        if (pkg == packageName) {
            if (cls == "me.kavishdevar.librepods.MainActivity") {
                lastTransition = ++eventRevision
                pendingForeground = null
                hide()
                BreathAccess.gate.enter(pkg, selected, SystemClock.elapsedRealtime(), BreathAccess.wallClock(), graceMs)
            }
            return
        }
        if (BreathWebApps.isWebAppWindow(pkg, cls)) {
            // An installed web app (for example a workout page added from Chrome) is never paused, even
            // when the browser hosting it is a chosen app. Clear any pause that began for the browser.
            lastTransition = ++eventRevision
            pendingForeground = null
            BreathAccess.gate.leave(BreathWebApps.marker(pkg))
            hide()
            return
        }
        if (pkg == "com.android.systemui" || pkg == "android" || pkg == permissionController) {
            // A running pause is never cut short by system windows (a heads-up notification, the volume
            // panel, a permission sheet): they would otherwise end it halfway at random. With no pause
            // running, remember that the system took the screen so a return is seen as a fresh entry.
            if (!attached) {
                lastTransition = ++eventRevision
                pendingForeground = null
                BreathAccess.gate.reset()
            }
            return
        }
        val component = ComponentName(pkg, if (cls.startsWith(".")) pkg + cls else cls)
        if (component in activities) {
            lastTransition = ++eventRevision
            lastEventTime = eventTime
            enter(pkg)
        } else if (rejectedWindows.get(component) != true) {
            // Coalesce repeated windows while classification is in flight. Dialog/IME misses are
            // bounded and cached, so typing cannot flood package-manager work.
            val version = ++eventRevision
            val existing = pendingWindows[component]
            if (existing != null) { existing.sequence = version; return }
            val request = PendingWindow(version)
            pendingWindows[component] = request
            scope.launch {
                val isActivity = withContext(Dispatchers.IO) {
                    runCatching { packageManager.getActivityInfo(component, 0) }.isSuccess
                }
                if (pendingWindows[component] !== request) return@launch
                pendingWindows.remove(component)
                if (isActivity) activities += component else rejectedWindows.put(component, true)
                if (acceptingEvents && request.sequence > lastTransition && isActivity) {
                    lastTransition = request.sequence
                    lastEventTime = eventTime
                    enter(pkg)
                }
            }
        }
    }

    private fun enter(pkg: String) {
        // Screen-off broadcasts and window events can arrive in either order. Recheck at
        // presentation time, including after asynchronous activity classification finishes.
        if (!getSystemService(PowerManager::class.java).isInteractive ||
            getSystemService(KeyguardManager::class.java).isKeyguardLocked) {
            interruptPending(resetEntry = true)
            return
        }
        if (!prepared) { pendingForeground = pkg; return }
        if (shownPackage != null && shownPackage != pkg) hide()
        val shouldShow = BreathAccess.gate.enter(pkg, selected, SystemClock.elapsedRealtime(), BreathAccess.wallClock(), graceMs)
        if (!shouldShow || BreathInteraction.active) return
        show(pkg)
    }

    /**
     * Covers the whole screen, status bar and gesture area included, so no strip of the loading app
     * shows above or below the color. With three-button navigation the buttons stay usable: only the
     * parts Android calls "tappable" are left out. Falls back to the plain full window if Android
     * cannot report them.
     */
    private fun overlayParams(): WindowManager.LayoutParams {
        val metrics = runCatching { windowManager?.currentWindowMetrics }.getOrNull()
        val tappable = metrics?.windowInsets?.getInsets(WindowInsets.Type.tappableElement()) ?: Insets.NONE
        val bounds = metrics?.bounds
        val full = bounds == null || tappable == Insets.NONE
        return WindowManager.LayoutParams(
            if (full) WindowManager.LayoutParams.MATCH_PARENT else bounds!!.width() - tappable.left - tappable.right,
            if (full) WindowManager.LayoutParams.MATCH_PARENT else bounds!!.height() - tappable.top - tappable.bottom,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = if (full) 0 else tappable.left
            y = if (full) 0 else tappable.top
            title = "pro · Breathe"
            fitInsetsTypes = 0
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            windowAnimations = 0
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
            // Ask for the screen's fastest refresh rate for the few seconds it animates (120 Hz on the S26 FE).
            FrameRate.applyTo(this, this@BreathAccessService)
        }
    }

    private fun show(pkg: String) {
        val view = layer ?: return
        session = BreathSession(view.options.durationMs)
        shownPackage = pkg
        view.destination = labels[pkg].orEmpty()
        val saying = BreathWords.pick(view.options.words, lastSaying)
        view.sayingIndex = saying
        view.restart()
        val params = overlayParams()
        attachedAt = SystemClock.elapsedRealtime()
        reportedToAttach = if (lastEventTime > 0) (SystemClock.uptimeMillis() - lastEventTime).coerceAtLeast(0) else 0
        runCatching { windowManager?.addView(view, params) }.onSuccess {
            attached = true
            BreathAccess.presentationFailed.value = false
            main.postDelayed(firstFrameTimeout, 1_000L)
            BreathInteraction.cancel = cancelInteraction
            GlintOverlays.dismissAll()
            view.requestFocus()
            if (lastSaying != saying) {
                lastSaying = saying
                scope.launch(Dispatchers.IO) { BreathHistory.saveSaying(applicationContext, saying) }
            }
        }.onFailure { failPresentation() }
    }

    private fun hide() {
        session.cancel()
        main.removeCallbacks(finishPause)
        main.removeCallbacks(firstFrameTimeout)
        if (attached) {
            releaseBackDispatcher()
            runCatching { windowManager?.removeViewImmediate(layer) }
        }
        attached = false
        shownPackage = null
        if (BreathInteraction.cancel === cancelInteraction) BreathInteraction.cancel = null
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Rotating or folding during a pause: keep covering the new screen instead of a stale rectangle.
        if (attached) runCatching { windowManager?.updateViewLayout(layer, overlayParams()) }
    }

    override fun onInterrupt() { interruptPending(resetEntry = true) }
    override fun onUnbind(intent: Intent?): Boolean {
        acceptingEvents = false
        revision++; invalidatePending()
        prepared = false; pendingForeground = null
        BreathAccess.gate.reset()
        hide()
        BreathAccess.connection.value = false
        BreathAccess.presentationFailed.value = false
        return super.onUnbind(intent)
    }
    override fun onDestroy() {
        acceptingEvents = false
        revision++; invalidatePending()
        hide()
        scope.cancel()
        BreathAccess.connection.value = false
        BreathAccess.presentationFailed.value = false
        BreathAccess.gate.reset()
        BreathAccess.gate.onPause = null
        BreathPrefs.prefs(this).unregisterOnSharedPreferenceChangeListener(prefsListener)
        getSharedPreferences("settings", MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(prefsListener)
        contentResolver.unregisterContentObserver(motionObserver)
        runCatching { unregisterReceiver(screenOff) }
        layer = null
        super.onDestroy()
    }

    private data class Prepared(
        val apps: Set<String>, val names: Map<String, String>, val components: Set<ComponentName>,
        val options: BreathOptions, val reduced: Boolean, val permissionController: String?,
        val history: Map<String, Long>?, val lastSaying: Int?,
    )
}
