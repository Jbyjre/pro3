/*
    pro, a fork of LibrePods - AirPods liberated from Apple's ecosystem
    Copyright (C) 2026 pro contributors

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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Glass that knows when to go easy. Real blur and refraction are GPU work; when the phone is
 * saving battery or getting hot, full glass is what makes scrolling stutter (and makes the
 * phone hotter). Then the glass switches to a lighter recipe: less blur, no colour fringe,
 * a bit more tint so text stays easy to read. It looks nearly the same and switches back by
 * itself when the phone recovers.
 */
object GlassBudget {
    /** True while the glass should use the lighter recipe. Read inside draw code only. */
    val light = mutableStateOf(false)

    /** Battery Saver, or the phone reports it is at least moderately hot. */
    fun shouldLighten(powerSave: Boolean, thermalStatus: Int): Boolean =
        powerSave || thermalStatus >= PowerManager.THERMAL_STATUS_MODERATE

    internal fun update(context: Context) {
        val pm = context.getSystemService(PowerManager::class.java) ?: return
        val next = shouldLighten(pm.isPowerSaveMode, pm.currentThermalStatus)
        if (light.value != next) light.value = next
    }
}

/** Keeps [GlassBudget] current while the app is on screen. Place once at the top of the app. */
@Composable
fun TrackGlassBudget() {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val pm = context.getSystemService(PowerManager::class.java)
        val thermal = PowerManager.OnThermalStatusChangedListener { GlassBudget.update(context) }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) = GlassBudget.update(context)
        }
        var on = false
        fun start() {
            if (on) return
            on = true
            GlassBudget.update(context)
            ContextCompat.registerReceiver(context, receiver, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
            pm?.addThermalStatusListener(context.mainExecutor, thermal)
        }
        fun stop() {
            if (!on) return
            on = false
            runCatching { context.unregisterReceiver(receiver) }
            pm?.removeThermalStatusListener(thermal)
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
