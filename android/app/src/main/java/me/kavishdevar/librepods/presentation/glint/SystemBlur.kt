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

package me.kavishdevar.librepods.presentation.glint

import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.Log
import android.view.View
import android.view.WindowManager
import java.lang.reflect.Method

/**
 * Real blur of whatever is behind an overlay window, shaped to our glass.
 *
 * Android never gives an app the pixels of other apps, so an overlay cannot refract or bend
 * the home screen itself. What the system *can* do is ask SurfaceFlinger to blur the region
 * under our window. The per-shape version of that (rounded, following a shape that animates
 * every frame) is the platform's BackgroundBlurDrawable, which the notification shade uses.
 * It is not part of the public SDK, so we reach it by reflection and always keep a clean
 * non-blur fallback: if anything here fails, [create] returns null and the glass is drawn as
 * a frosted tint instead.
 *
 * Only used when [WindowManager.isCrossWindowBlurEnabled] is true. That turns false when
 * battery saver is on, when the device doesn't support it, or when the user turned window
 * blurs off, and it can flip while an overlay is showing (see [addBlurStateListener]).
 */
class SystemBlur private constructor(
    private val drawable: Drawable,
    private val setBlurRadius: Method,
    private val setCornerRadius: Method,
    private val setColor: Method,
) {
    private var lastRadius = -1
    private var lastCorner = -1f
    private var lastColor = 0

    fun update(blurRadiusPx: Int, cornerRadiusPx: Float, tintArgb: Int) {
        try {
            if (blurRadiusPx != lastRadius) {
                setBlurRadius.invoke(drawable, blurRadiusPx); lastRadius = blurRadiusPx
            }
            if (cornerRadiusPx != lastCorner) {
                setCornerRadius.invoke(drawable, cornerRadiusPx); lastCorner = cornerRadiusPx
            }
            if (tintArgb != lastColor) {
                setColor.invoke(drawable, tintArgb); lastColor = tintArgb
            }
        } catch (e: Exception) {
            Log.w(TAG, "blur update failed: ${e.message}")
        }
    }

    /** Draw into the current (hardware) canvas at local bounds 0,0,[width],[height]. */
    fun draw(canvas: Canvas, left: Int, top: Int, width: Int, height: Int) {
        if (!canvas.isHardwareAccelerated) return
        drawable.setBounds(left, top, left + width, top + height)
        drawable.draw(canvas)
    }

    fun hide() {
        try { setBlurRadius.invoke(drawable, 0); lastRadius = 0 } catch (_: Exception) {}
        drawable.setVisible(false, false)
    }

    fun show() {
        drawable.setVisible(true, false)
    }

    companion object {
        private const val TAG = "GlintSystemBlur"

        init {
            // Loads the helper that allows the non-SDK calls below (see bluetooth_socket.cpp).
            try { System.loadLibrary("bluetooth_socket") } catch (_: Throwable) {}
        }

        fun isEnabled(windowManager: WindowManager): Boolean = try {
            windowManager.isCrossWindowBlurEnabled
        } catch (_: Throwable) {
            false
        }

        /** Must be called on the UI thread with [view] attached to its window. */
        fun create(view: View): SystemBlur? {
            return try {
                val viewRoot = view.rootView.parent ?: return null
                if (viewRoot.javaClass.name != "android.view.ViewRootImpl") return null
                val factory = viewRoot.javaClass.getMethod("createBackgroundBlurDrawable")
                val drawable = factory.invoke(viewRoot) as? Drawable ?: return null
                val cls = drawable.javaClass
                SystemBlur(
                    drawable,
                    cls.getMethod("setBlurRadius", Int::class.javaPrimitiveType),
                    cls.getMethod("setCornerRadius", Float::class.javaPrimitiveType),
                    cls.getMethod("setColor", Int::class.javaPrimitiveType),
                )
            } catch (t: Throwable) {
                Log.w(TAG, "System blur unavailable: ${t.javaClass.simpleName} ${t.message}")
                null
            }
        }
    }
}
