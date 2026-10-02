/*
    Glint, a fork of LibrePods - AirPods liberated from Apple's ecosystem
    Copyright (C) 2026 Glint contributors

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>.
*/

package me.kavishdevar.librepods.presentation.overlays

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.PixelFormat
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import me.kavishdevar.librepods.presentation.glint.SystemBlur
import me.kavishdevar.librepods.services.IslandAccess
import java.util.function.Consumer

/**
 * A transparent system overlay window hosting Compose content, used by the islands and the
 * connection card. The window is only as large as the content currently needs (plus room for
 * shadows), so it never blocks touches on the rest of the screen for longer than a transition.
 *
 * [aboveStatusBar]: the Dynamic Island and its pop-ups. While pro's accessibility service runs
 * ([IslandAccess]) they're placed above the phone's status bar, the only place a window around
 * the front camera can be touched (Android gives the status bar every touch in its strip and
 * draws it over normal app overlays). Otherwise they're normal app overlays.
 */
internal class OverlayWindow(
    private val context: Context,
    private val tag: String,
    private val anchorTop: Boolean,
    private val aboveStatusBar: Boolean = false,
) : LifecycleOwner, SavedStateRegistryOwner {

    private val appWindowManager = context.getSystemService(WindowManager::class.java)
    /** The window manager the current window was added with (the service's, above the status bar). */
    private var windowManager: WindowManager = appWindowManager
    /** The accessibility service the window was placed through, or null for a normal overlay. */
    private var host: AccessibilityService? = null
    /** The service that was available when it was shown (even if placing through it failed). */
    private var shownWith: AccessibilityService? = null
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    /** True while the phone allows cross-window blur (off in battery saver, etc.). */
    val blurAllowed = mutableStateOf(SystemBlur.isEnabled(appWindowManager))
    private val blurListener = Consumer<Boolean> { blurAllowed.value = it }

    private var view: ComposeView? = null
    private var params: WindowManager.LayoutParams? = null
    val isShowing: Boolean get() = view != null

    /** Told when the window is added (true) or removed (false). */
    var onShownChanged: ((Boolean) -> Unit)? = null

    /**
     * False while the app in front hides the status bar (a full-screen video or game), as
     * reported by Android. Not verified on every launcher/skin.
     */
    val statusBarVisible = mutableStateOf(true)

    /**
     * Android only tells a window about the status bar when the status bar is above it, so a
     * window placed above the status bar can't see it hide. This invisible 1-pixel overlay
     * (below the status bar, fully transparent, never touchable) listens for it instead.
     */
    private var probe: View? = null

    init {
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    /** The service to place the window through now, or null for a normal overlay. */
    private fun wantedHost(): AccessibilityService? = if (aboveStatusBar) IslandAccess.service.value else null

    /** True when shown above the status bar (so it can be touched around the camera). */
    val aboveBar: Boolean get() = view != null && host != null

    /** Shown in a different layer from where it would go now (the service started or stopped). */
    val misplaced: Boolean get() = view != null && shownWith !== wantedHost()

    fun canShow(): Boolean = wantedHost() != null || Settings.canDrawOverlays(context)

    /** For tests: the window's and the status-bar probe's settings. */
    internal val windowParams: WindowManager.LayoutParams? get() = params
    internal val windowView: View? get() = view
    internal val probeParams: WindowManager.LayoutParams? get() = probe?.layoutParams as? WindowManager.LayoutParams

    fun show(initialSize: IntSize, offsetY: Int, content: @Composable () -> Unit): Boolean = show(initialSize, offsetY, 0, content)

    fun show(initialSize: IntSize, offsetY: Int, offsetX: Int, content: @Composable () -> Unit): Boolean {
        if (view != null) return true
        val service = wantedHost()
        if (service == null && !Settings.canDrawOverlays(context)) {
            Log.d(tag, "No overlay permission")
            return false
        }
        // Above the status bar through the service when it runs; a normal overlay otherwise, or
        // if placing it there fails for any reason.
        val serviceWm = service?.let { runCatching { it.getSystemService(WindowManager::class.java) }.getOrNull() }
        if (serviceWm != null && add(serviceWm, above = true, initialSize, offsetY, offsetX, content)) {
            host = service
        } else if (Settings.canDrawOverlays(context) && add(appWindowManager, above = false, initialSize, offsetY, offsetX, content)) {
            host = null
        } else {
            return false
        }
        shownWith = service
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        onShownChanged?.invoke(true)
        return true
    }

    private fun add(wm: WindowManager, above: Boolean, initialSize: IntSize, offsetY: Int, offsetX: Int, content: @Composable () -> Unit): Boolean {
        // The pop-ups follow Glint's light/dark choice, pictures and clips included.
        val composeView = ComposeView(me.kavishdevar.librepods.presentation.theme.GlintAppearance.context(context)).apply {
            setViewTreeLifecycleOwner(this@OverlayWindow)
            setViewTreeSavedStateRegistryOwner(this@OverlayWindow)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent { CappedFontScale(content) }
            if (!above) setOnApplyWindowInsetsListener { v, insets ->
                statusBarVisible.value = insets.isVisible(android.view.WindowInsets.Type.statusBars())
                v.onApplyWindowInsets(insets)
            }
        }
        val lp = WindowManager.LayoutParams(
            initialSize.width,
            initialSize.height,
            if (above) WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY else WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = (if (anchorTop) Gravity.TOP else Gravity.BOTTOM) or Gravity.CENTER_HORIZONTAL
            y = offsetY
            x = offsetX
            title = tag
            windowAnimations = 0
            fitInsetsTypes = 0
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        return try {
            wm.addView(composeView, lp)
            // If Android takes the window away by itself (the overlay permission revoked, the
            // accessibility service stopped), let go of it so the next show() works again,
            // instead of believing it's still there.
            composeView.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {}
                override fun onViewDetachedFromWindow(v: View) {
                    v.removeOnAttachStateChangeListener(this)
                    if (view === v) lost()
                }
            })
            view = composeView
            params = lp
            windowManager = wm
            statusBarVisible.value = true
            if (above) addProbe()
            try {
                wm.addCrossWindowBlurEnabledListener(context.mainExecutor, blurListener)
            } catch (_: Throwable) {}
            true
        } catch (e: Exception) {
            Log.e(tag, "Could not add overlay (above status bar: $above): ${e.message}")
            false
        }
    }

    private fun addProbe() {
        if (probe != null || !Settings.canDrawOverlays(context)) return
        val v = View(context).apply {
            setOnApplyWindowInsetsListener { _, insets ->
                statusBarVisible.value = insets.isVisible(android.view.WindowInsets.Type.statusBars())
                insets
            }
        }
        val lp = WindowManager.LayoutParams(
            1, 1,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            // Fully transparent windows never get in the way of touches meant for other apps.
            alpha = 0f
            title = "$tag-probe"
            windowAnimations = 0
            fitInsetsTypes = 0
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        try {
            appWindowManager.addView(v, lp)
            probe = v
        } catch (e: Exception) {
            Log.w(tag, "probe: ${e.message}")
        }
    }

    fun resize(size: IntSize) {
        val v = view ?: return
        val lp = params ?: return
        if (lp.width == size.width && lp.height == size.height) return
        lp.width = size.width
        lp.height = size.height
        try { windowManager.updateViewLayout(v, lp) } catch (e: Exception) { Log.w(tag, "resize: ${e.message}") }
    }

    /** When false, touches go straight through to whatever is underneath. */
    fun setTouchable(touchable: Boolean) {
        val v = view ?: return
        val lp = params ?: return
        val flags = if (touchable) lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        else lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        if (flags == lp.flags) return
        lp.flags = flags
        try { windowManager.updateViewLayout(v, lp) } catch (e: Exception) { Log.w(tag, "touchable: ${e.message}") }
    }

    /**
     * Out of sight but still there (for example while a full-screen app hides it): fully
     * transparent and untouchable. Android blocks touches that pass through another app's
     * visible overlay, so an invisible-but-opaque window would eat taps meant for the app below.
     */
    fun setPresent(present: Boolean) {
        val v = view ?: return
        val lp = params ?: return
        val alpha = if (present) 1f else 0f
        val flags = if (present) lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        else lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        if (lp.alpha == alpha && flags == lp.flags) return
        lp.alpha = alpha
        lp.flags = flags
        try { windowManager.updateViewLayout(v, lp) } catch (e: Exception) { Log.w(tag, "present: ${e.message}") }
    }

    /** The window went away without [dismiss] (see above): tidy up the same way. */
    private fun lost() {
        Log.w(tag, "window removed by the system")
        view = null
        params = null
        onShownChanged?.invoke(false)
        try { windowManager.removeCrossWindowBlurEnabledListener(blurListener) } catch (_: Throwable) {}
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        probe?.let { p -> try { appWindowManager.removeViewImmediate(p) } catch (_: Exception) {} }
        probe = null
        host = null
        shownWith = null
        windowManager = appWindowManager
        statusBarVisible.value = true
    }

    fun dismiss() {
        val v = view ?: return
        view = null
        params = null
        onShownChanged?.invoke(false)
        try { windowManager.removeCrossWindowBlurEnabledListener(blurListener) } catch (_: Throwable) {}
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        // The service may already be gone (its windows go with it): removing can then fail.
        try { windowManager.removeViewImmediate(v) } catch (e: Exception) { Log.w(tag, "remove: ${e.message}") }
        probe?.let { p -> try { appWindowManager.removeViewImmediate(p) } catch (_: Exception) {} }
        probe = null
        host = null
        shownWith = null
        windowManager = appWindowManager
        statusBarVisible.value = true
    }
}

/**
 * The pop-ups have fixed heights, so very large system font sizes would overflow them on
 * some phones: text follows the setting up to 115%, then stops growing.
 */
@androidx.compose.runtime.Composable
internal fun CappedFontScale(content: @androidx.compose.runtime.Composable () -> Unit) {
    val d = androidx.compose.ui.platform.LocalDensity.current
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(d.density, d.fontScale.coerceAtMost(1.15f)),
        content = content,
    )
}
