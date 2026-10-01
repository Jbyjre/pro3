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
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.abs
import kotlin.math.asin

/**
 * Glass that catches the light as you move the phone: Apple's Liquid Glass "reacts to
 * movement with specular highlights". Tilting the phone sideways swings the rim light a
 * little (at most [MAX] degrees), smoothed so it drifts rather than jitters. Uses the gravity
 * sensor only while the app is on screen; off with Reduce motion.
 */
object GlassTilt {
    const val MAX = 14f

    /** Light swing for a sideways gravity reading [gx] (m/s², positive = left edge down). */
    fun swingFor(gx: Float): Float {
        val roll = Math.toDegrees(asin((gx / SensorManager.GRAVITY_EARTH).coerceIn(-1f, 1f).toDouble())).toFloat()
        // The light stays put in the room, so it moves against the tilt; half strength keeps it subtle.
        return (-roll * 0.5f).coerceIn(-MAX, MAX)
    }

    /** Low-pass step toward [target]; snaps to exactly 0 near level so the default look is exact. */
    fun smooth(current: Float, target: Float): Float {
        val next = current + (target - current) * 0.12f
        return if (abs(next) < 0.25f && abs(target) < 0.25f) 0f else next
    }
}

/** Follows the phone's tilt while the screen is resumed. Place once at the top of the app. */
@Composable
fun TrackGlassTilt() {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val sensors = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val gravity = sensors?.getDefaultSensor(Sensor.TYPE_GRAVITY)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val now = GlintLight.swing.floatValue
                val next = GlassTilt.smooth(now, GlassTilt.swingFor(event.values[0]))
                // Only redraw the glass for a visible change.
                if (abs(next - now) >= 0.2f || (next == 0f && now != 0f)) GlintLight.swing.floatValue = next
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        fun start() {
            if (gravity != null && !GlintComfort.reduceMotion(context)) sensors.registerListener(listener, gravity, SensorManager.SENSOR_DELAY_UI)
        }
        fun stop() {
            sensors?.unregisterListener(listener)
            GlintLight.swing.floatValue = 0f
        }
        val observer = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_RESUME -> start()
                Lifecycle.Event.ON_PAUSE -> stop()
                else -> {}
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); stop() }
    }
}
