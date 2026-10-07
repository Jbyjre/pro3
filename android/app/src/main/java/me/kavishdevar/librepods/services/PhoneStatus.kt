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

package me.kavishdevar.librepods.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.runtime.Immutable
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The phone's own battery, so the Dynamic Island has something to show with nothing playing and
 * nothing connected (Phone battery slot). No permission needed: Android sends battery changes to
 * any app.
 */
object PhoneStatus {
    /** [level] is 0..100, or -1 before the first reading. */
    @Immutable
    data class Info(val level: Int = -1, val charging: Boolean = false) {
        val known: Boolean get() = level in 0..100
    }

    private val _state = MutableStateFlow(Info())
    val state: StateFlow<Info> = _state.asStateFlow()
    private var attached = false

    /** Starts following the battery (safe to call often). */
    fun attach(context: Context) {
        if (attached) return
        attached = true
        val app = context.applicationContext
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) { _state.value = read(i) }
        }
        // The battery broadcast is sticky: registering returns the latest reading straight away.
        val sticky = ContextCompat.registerReceiver(
            app, receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        if (sticky != null) _state.value = read(sticky)
    }

    private fun read(i: Intent): Info = parse(
        level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1),
        scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100),
        status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1),
        plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0),
    )

    /** Android's battery numbers as a percentage and a charging flag (pure, for tests). */
    fun parse(level: Int, scale: Int, status: Int, plugged: Int): Info {
        if (level < 0 || scale <= 0) return Info()
        val pct = (level * 100f / scale).toInt().coerceIn(0, 100)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            (status == BatteryManager.BATTERY_STATUS_FULL && plugged != 0)
        return Info(pct, charging)
    }

    /** Things worth a moment on the island. */
    enum class Moment { Charging, Low, VeryLow, Full }

    /**
     * What changed between two readings that deserves a moment: plugging in, getting full, or
     * dropping to 20% or 10% while not charging. Nothing for the first reading or for ordinary
     * ups and downs. Pure.
     */
    fun momentBetween(prev: Info?, now: Info): Moment? {
        if (prev == null || !prev.known || !now.known) return null
        return when {
            !prev.charging && now.charging -> Moment.Charging
            now.charging && now.level >= 100 && prev.level < 100 -> Moment.Full
            !now.charging && now.level <= 10 && prev.level > 10 -> Moment.VeryLow
            !now.charging && now.level <= 20 && prev.level > 20 -> Moment.Low
            else -> null
        }
    }

    /** Sets what's shown, for screenshots and tests only. */
    internal fun preview(info: Info) { _state.value = info }
}
