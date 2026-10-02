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

/**
 * One light for every piece of glass, in the app and in the islands over other apps. The
 * gravity sensor runs only while something glassy is on screen and wants it: [acquire] when it
 * appears, [release] when it goes (counted, so the app, an island and the Dynamic Island can
 * share it). Off with Reduce motion. When the last user lets go, the light stays where it was
 * (no jump) and drifts from there next time.
 */
object GlassLight {
    /**
     * Where the light falls on drawn glass, each axis -1..1: x left/right, y up/down. The resting
     * value (0, -0.2) is straight above, a little toward you.
     */
    val tilt = androidx.compose.runtime.mutableStateOf(androidx.compose.ui.geometry.Offset(0f, REST_Y))

    private var users = 0
    private var sensors: SensorManager? = null

    /** Light position for a gravity reading: tilting the right edge down slides it left, like a reflection. */
    fun tiltFor(gx: Float, gy: Float): androidx.compose.ui.geometry.Offset {
        val x = (-gx / SensorManager.GRAVITY_EARTH * 1.4f).coerceIn(-1f, 1f)
        // Held at a normal reading angle (about 33 degrees from upright) the light is at rest.
        val y = (REST_Y + (0.55f - gy / SensorManager.GRAVITY_EARTH) * 0.8f).coerceIn(-1f, 1f)
        return androidx.compose.ui.geometry.Offset(x, y)
    }

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val gx = event.values[0]
            val gy = event.values[1]
            val now = GlintLight.swing.floatValue
            val next = GlassTilt.smooth(now, GlassTilt.swingFor(gx))
            // Only redraw the glass for a visible change.
            if (abs(next - now) >= 0.2f || (next == 0f && now != 0f)) GlintLight.swing.floatValue = next
            val target = tiltFor(gx, gy)
            val cur = tilt.value
            val fx = cur.x + (target.x - cur.x) * 0.18f
            val fy = cur.y + (target.y - cur.y) * 0.18f
            if (abs(cur.x - fx) > 0.004f || abs(cur.y - fy) > 0.004f) tilt.value = androidx.compose.ui.geometry.Offset(fx, fy)
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    fun acquire(context: Context) {
        users++
        if (users != 1 || sensors != null) return
        if (GlintComfort.reduceMotion(context)) return
        val sm = context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
        val sensor = sm.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        if (sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)) sensors = sm
    }

    fun release() {
        if (users == 0) return
        users--
        if (users == 0) {
            sensors?.unregisterListener(listener)
            sensors = null
        }
    }

    /** For tests: how many are using it, and whether the sensor runs. */
    internal val holders: Int get() = users
    internal val listening: Boolean get() = sensors != null

    const val REST_Y = -0.2f
}

/** Follows the phone's tilt while the screen is resumed. Place once at the top of the app. */
@Composable
fun TrackGlassTilt() {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        var on = false
        fun start() { if (!on) { on = true; GlassLight.acquire(context) } }
        fun stop() { if (on) { on = false; GlassLight.release() } }
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
