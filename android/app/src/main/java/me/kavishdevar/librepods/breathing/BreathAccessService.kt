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
import android.database.ContentObserver
import android.provider.Settings
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import androidx.core.view.doOnAttach
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.kavishdevar.librepods.presentation.glint.GlintComfort
import me.kavishdevar.librepods.presentation.overlays.GlintOverlays

/**
 * Window events only: no screen tree, text, usage polling, app relaunch, or Bluetooth dependency.
 * Selected activity classes, labels, appearance and the layer are prepared before an app opens.
 */
class BreathAccessService : AccessibilityService() {
    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var revision = 0
    private var eventRevision = 0
    private var lastTransition = 0
    private var prepared = false
    private var pendingForeground: String? = null
    private var selected = emptySet<String>()
    private var labels = emptyMap<String, String>()
    private var permissionController: String? = null
    private val activities = mutableSetOf<ComponentName>()
    private var layer: BreathView? = null
    private var shownPackage: String? = null
    private var windowManager: WindowManager? = null
    private var session = BreathSession()
    private var attached = false
    private var acceptingEvents = false
    private var attachedAt = 0L
    private val back = OnBackInvokedCallback { leave() }
    private val motionObserver = object : ContentObserver(main) {
        override fun onChange(selfChange: Boolean) { refresh() }
    }
    private val cancelInteraction: () -> Unit = { hide() }
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        if (prefs === BreathPrefs.prefs(this) || key == GlintComfort.PREF_REDUCE_MOTION) refresh()
    }
    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            hide()
            BreathAccess.gate.reset()
            pendingForeground = null
            lastTransition = ++eventRevision
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
        if (layer != null) { refresh(); return }
        windowManager = getSystemService(WindowManager::class.java)
        layer = BreathView(this).apply {
            onFirstFrame = {
                val now = SystemClock.elapsedRealtime()
                android.util.Log.d("Breathe", "Layer attach to first draw: ${now - attachedAt} ms")
                session.firstFrame(now)
                main.postDelayed(finishPause, BreathSession.DURATION_MS)
            }
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
        hide()
    }

    private fun refresh() {
        val version = ++revision
        scope.launch {
            val loaded = withContext(Dispatchers.IO) {
                val apps = BreathPrefs.selected(this@BreathAccessService) - packageName
                val names = mutableMapOf<String, String>()
                val components = mutableSetOf<ComponentName>()
                for (pkg in apps) runCatching {
                    val info = packageManager.getPackageInfo(pkg, PackageManager.GET_ACTIVITIES)
                    info.applicationInfo?.let { names[pkg] = packageManager.getApplicationLabel(it).toString() }
                    info.activities.orEmpty().forEach { components += ComponentName(pkg, it.name) }
                }
                val options = BreathPrefs.options(this@BreathAccessService).also { it.palette.gradientColors }
                Prepared(apps, names, components, options, GlintComfort.reduceMotion(this@BreathAccessService), packageManager.resolveActivity(Intent("android.content.pm.action.REQUEST_PERMISSIONS"), PackageManager.MATCH_SYSTEM_ONLY)?.activityInfo?.packageName)
            }
            if (version != revision || !acceptingEvents) return@launch
            selected = loaded.apps
            labels = loaded.names
            permissionController = loaded.permissionController
            activities.addAll(loaded.components)
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
        // Our own overlay sends window events too. Only pro's real activity counts as leaving.
        if (pkg == packageName) {
            if (cls == "me.kavishdevar.librepods.MainActivity") {
                lastTransition = ++eventRevision
                pendingForeground = null
                hide()
                BreathAccess.gate.enter(pkg, selected, SystemClock.elapsedRealtime())
            }
            return
        }
        if (pkg == "com.android.systemui" || pkg == "android" || pkg == permissionController) {
            lastTransition = ++eventRevision
            pendingForeground = null
            BreathAccess.gate.reset()
            hide() // Do not obstruct the shade, lock screen, permission sheets, or system dialogs.
            return
        }
        val component = ComponentName(pkg, if (cls.startsWith(".")) pkg + cls else cls)
        if (component in activities) {
            lastTransition = ++eventRevision
            enter(pkg)
        } else {
            // Unknown windows (including IMEs/dialogs) are validated off main, never guessed.
            val version = ++eventRevision
            scope.launch {
                val isActivity = withContext(Dispatchers.IO) {
                    runCatching { packageManager.getActivityInfo(component, 0) }.isSuccess
                }
                if (isActivity) activities += component
                if (acceptingEvents && version > lastTransition && isActivity) { lastTransition = version; enter(pkg) }
            }
        }
    }

    private fun enter(pkg: String) {
        if (!prepared) { pendingForeground = pkg; return }
        if (getSystemService(KeyguardManager::class.java).isKeyguardLocked) { hide(); return }
        if (shownPackage != null && shownPackage != pkg) hide()
        val shouldShow = BreathAccess.gate.enter(pkg, selected, SystemClock.elapsedRealtime())
        if (!shouldShow || BreathInteraction.active) return
        show(pkg)
    }

    private fun show(pkg: String) {
        val view = layer ?: return
        session = BreathSession()
        shownPackage = pkg
        view.destination = labels[pkg].orEmpty()
        view.restart()
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.OPAQUE,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            title = "pro · Breathe"
            // Keep navigation and the shade available; consume all touches inside the app area.
            fitInsetsTypes = android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.displayCutout()
            windowAnimations = 0
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        }
        attachedAt = SystemClock.elapsedRealtime()
        view.doOnAttach {
            view.findOnBackInvokedDispatcher()?.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, back)
        }
        runCatching { windowManager?.addView(view, params) }.onSuccess {
            attached = true
            BreathAccess.connection.value = true
            BreathInteraction.cancel = cancelInteraction
            GlintOverlays.dismissAll()
            view.requestFocus()
        }.onFailure {
            shownPackage = null
            BreathAccess.connection.value = false
        }
    }

    private fun hide() {
        session.cancel()
        main.removeCallbacks(finishPause)
        if (attached) {
            layer?.findOnBackInvokedDispatcher()?.unregisterOnBackInvokedCallback(back)
            runCatching { windowManager?.removeViewImmediate(layer) }
        }
        attached = false
        shownPackage = null
        if (BreathInteraction.cancel === cancelInteraction) BreathInteraction.cancel = null
    }

    override fun onInterrupt() { hide() }
    override fun onUnbind(intent: Intent?): Boolean {
        acceptingEvents = false
        revision++; lastTransition = ++eventRevision
        prepared = false; pendingForeground = null
        BreathAccess.gate.reset()
        hide()
        BreathAccess.connection.value = false
        return super.onUnbind(intent)
    }
    override fun onDestroy() {
        acceptingEvents = false
        revision++; eventRevision++
        hide()
        scope.cancel()
        BreathAccess.connection.value = false
        BreathAccess.gate.reset()
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
    )
}
