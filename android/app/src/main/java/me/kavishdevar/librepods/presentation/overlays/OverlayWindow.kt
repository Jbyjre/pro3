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
import java.util.function.Consumer

/**
 * A transparent system overlay window hosting Compose content, used by the islands and the
 * connection card. The window is only as large as the content currently needs (plus room for
 * shadows), so it never blocks touches on the rest of the screen for longer than a transition.
 *
 */
internal class OverlayWindow(
    private val context: Context,
    private val tag: String,
    private val anchorTop: Boolean,
    /** Ask for the screen's fastest refresh rate while shown (short-lived pop-ups only, see [FrameRate]). */
    private val fastFrames: Boolean = false,
) : LifecycleOwner, SavedStateRegistryOwner {

    private val appWindowManager = context.getSystemService(WindowManager::class.java)
    /** The window manager the current window was added with (the service's, above the status bar). */
    private var windowManager: WindowManager = appWindowManager
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

    init {
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    fun canShow(): Boolean = Settings.canDrawOverlays(context)
    internal val windowParams: WindowManager.LayoutParams? get() = params
    internal val windowView: View? get() = view

    fun show(initialSize: IntSize, offsetY: Int, content: @Composable () -> Unit): Boolean = show(initialSize, offsetY, 0, content)

    fun show(initialSize: IntSize, offsetY: Int, offsetX: Int, content: @Composable () -> Unit): Boolean {
        if (view != null) return true
        if (!canShow() || !add(appWindowManager, initialSize, offsetY, offsetX, content)) return false
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        onShownChanged?.invoke(true)
        return true
    }

    private fun add(wm: WindowManager, initialSize: IntSize, offsetY: Int, offsetX: Int, content: @Composable () -> Unit): Boolean {
        // The pop-ups follow Glint's light/dark choice, pictures and clips included.
        val composeView = ComposeView(me.kavishdevar.librepods.presentation.theme.GlintAppearance.context(context)).apply {
            setViewTreeLifecycleOwner(this@OverlayWindow)
            setViewTreeSavedStateRegistryOwner(this@OverlayWindow)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent { CappedFontScale(content) }

        }
        val lp = WindowManager.LayoutParams(
            initialSize.width,
            initialSize.height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
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
            if (fastFrames) me.kavishdevar.librepods.presentation.glint.FrameRate.applyTo(this, context)
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
            try {
                wm.addCrossWindowBlurEnabledListener(context.mainExecutor, blurListener)
            } catch (_: Throwable) {}
            true
        } catch (e: Exception) {
            Log.e(tag, "Could not add overlay: ${e.message}")
            false
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
        windowManager = appWindowManager
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
        windowManager = appWindowManager
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
