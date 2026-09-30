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
 * A transparent system overlay window hosting Compose content, used by the island and the
 * connection card. The window is only as large as the content currently needs (plus room for
 * shadows), so it never blocks touches on the rest of the screen for longer than a transition.
 */
internal class OverlayWindow(
    private val context: Context,
    private val tag: String,
    private val anchorTop: Boolean,
) : LifecycleOwner, SavedStateRegistryOwner {

    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    /** True while the phone allows cross-window blur (off in battery saver, etc.). */
    val blurAllowed = mutableStateOf(SystemBlur.isEnabled(windowManager))
    private val blurListener = Consumer<Boolean> { blurAllowed.value = it }

    private var view: ComposeView? = null
    private var params: WindowManager.LayoutParams? = null
    val isShowing: Boolean get() = view != null

    init {
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    fun canShow(): Boolean = Settings.canDrawOverlays(context)

    fun show(initialSize: IntSize, offsetY: Int, content: @Composable () -> Unit): Boolean {
        if (view != null) return true
        if (!canShow()) {
            Log.d(tag, "No overlay permission")
            return false
        }
        val composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(this@OverlayWindow)
            setViewTreeSavedStateRegistryOwner(this@OverlayWindow)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent(content)
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
            title = tag
            windowAnimations = 0
            fitInsetsTypes = 0
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        return try {
            windowManager.addView(composeView, lp)
            view = composeView
            params = lp
            try {
                windowManager.addCrossWindowBlurEnabledListener(context.mainExecutor, blurListener)
            } catch (_: Throwable) {}
            lifecycleRegistry.currentState = Lifecycle.State.RESUMED
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

    fun dismiss() {
        val v = view ?: return
        view = null
        try { windowManager.removeCrossWindowBlurEnabledListener(blurListener) } catch (_: Throwable) {}
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        try { windowManager.removeViewImmediate(v) } catch (e: Exception) { Log.w(tag, "remove: ${e.message}") }
    }
}
