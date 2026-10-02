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

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.accessibility.AccessibilityManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import kotlin.math.abs

/** Accessibility and comfort preferences that change how Glint draws and moves. */
object GlintComfort {
    const val PREF_REDUCE_MOTION = "glint_reduce_motion"
    const val PREF_REDUCE_TRANSPARENCY = "glint_reduce_transparency"
    const val PREF_TILT_LIGHT = "glint_tilt_light"

    private fun prefs(context: Context) = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** System "Remove animations" (animator scale 0) or the in-app switch. */
    fun reduceMotion(context: Context): Boolean {
        val scale = try {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        } catch (_: Exception) { 1f }
        return scale == 0f || prefs(context).getBoolean(PREF_REDUCE_MOTION, false)
    }

    /** In-app switch, or high-contrast text (Android 16+ exposes it publicly). */
    fun reduceTransparency(context: Context): Boolean {
        if (prefs(context).getBoolean(PREF_REDUCE_TRANSPARENCY, false)) return true
        if (Build.VERSION.SDK_INT >= 36) {
            val am = context.getSystemService(AccessibilityManager::class.java)
            if (am?.isHighContrastTextEnabled == true) return true
        }
        return false
    }

    fun tiltLight(context: Context): Boolean =
        prefs(context).getBoolean(PREF_TILT_LIGHT, true) && !reduceMotion(context)
}

/**
 * Where the light comes from, each axis -1..1, driven by how the phone is tilted relative to
 * how it was held when the surface appeared. Read it inside draw lambdas only, so tilting
 * redraws the highlights without recomposing anything.
 */
@Composable
fun rememberTiltLight(enabled: Boolean): State<Offset> {
    val context = LocalContext.current
    // The same light as all other glass (app and islands), running while this is on screen.
    DisposableEffect(enabled) {
        if (enabled) GlassLight.acquire(context)
        onDispose { if (enabled) GlassLight.release() }
    }
    val still = remember { mutableStateOf(Offset(0f, GlassLight.REST_Y)) }
    return if (enabled) GlassLight.tilt else still
}

/** Small, purposeful haptics. Respects the system "Touch feedback" setting via the View. */
class GlintHaptics(private val view: View) {
    fun appear() {
        val vibrator = view.context.getSystemService(Vibrator::class.java)
        if (vibrator != null && vibrator.hasVibrator() &&
            vibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, VibrationEffect.Composition.PRIMITIVE_CLICK) &&
            hapticsEnabled()
        ) {
            vibrator.vibrate(
                VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, 0.35f)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.6f, 40)
                    .compose()
            )
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        }
    }

    /** A light, immediate tap the moment a finger lands. */
    fun touch() = primitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.7f, HapticFeedbackConstants.VIRTUAL_KEY)

    /** A firm click when a gesture does something. */
    fun confirm() = primitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.85f, HapticFeedbackConstants.CONFIRM)

    private fun primitive(id: Int, scale: Float, fallback: Int) {
        val vibrator = view.context.getSystemService(Vibrator::class.java)
        if (vibrator != null && vibrator.hasVibrator() && vibrator.areAllPrimitivesSupported(id) && hapticsEnabled()) {
            runCatching { vibrator.vibrate(VibrationEffect.startComposition().addPrimitive(id, scale).compose()) }
        } else {
            view.performHapticFeedback(fallback)
        }
    }

    fun expand() = view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    fun tick() = view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    fun dismiss() = view.performHapticFeedback(HapticFeedbackConstants.GESTURE_END)
    fun warn() = view.performHapticFeedback(HapticFeedbackConstants.REJECT)

    private fun hapticsEnabled(): Boolean = try {
        Settings.System.getInt(view.context.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) != 0
    } catch (_: Exception) { true }
}
