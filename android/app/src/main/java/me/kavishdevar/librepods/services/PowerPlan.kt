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
package me.kavishdevar.librepods.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import androidx.compose.runtime.withFrameMillis
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.kavishdevar.librepods.presentation.glint.GlassBudget

/**
 * pro's battery plan, always on and never a setting (Jake, 2026-10-11: "very battery efficient
 * automatically ... as its main setting, but don't nerf its capability").
 *
 * What stays at full speed, always: anything you touch or watch move once (opening, closing,
 * morphing, springs, presses, the unlock). Those are short, and cutting them is what makes a
 * phone feel slow.
 *
 * What's paced: things that redraw for minutes or hours on their own (the sound bars while music
 * plays, the spinning headphones, the music listener). They run at the speed the eye needs, not
 * the screen's fastest, and ease off further by themselves:
 * - [Level.Full] while charging (and not hot): everything as smooth as the screen allows.
 * - [Level.Balanced], the normal day: bars at 30 frames a second (the music itself is heard 20
 *   times a second, so nothing is lost), the model at 30.
 * - [Level.Saver] on Battery Saver, at 20% or less, or when the phone is warm: bars at 20,
 *   the model rests (still turns with your finger), the listener hears 10 times a second, the glass
 *   lightens, and background heart readings become spot checks.
 * Everything here pauses when the screen is off or the thing isn't on show (each part's own rule).
 */
object PowerPlan {
    enum class Level { Full, Balanced, Saver }

    private val _level = MutableStateFlow(Level.Balanced)
    /** The plan right now. */
    val level: StateFlow<Level> = _level.asStateFlow()

    /** At or below this battery (and not charging), pro saves like Battery Saver. */
    const val LOW_BATTERY = 20

    /**
     * The pure rule, unit-tested. [thermal] is Android's THERMAL_STATUS_* (0 none … 6 shutdown);
     * [warmFrom] is where "warm" starts for this phone ([PhoneProfile]: the Pixel 6 Pro's Tensor
     * runs warm, so it saves from the first, "light" level; other phones from "moderate").
     */
    fun levelFor(charging: Boolean, battery: Int, powerSave: Boolean, thermal: Int, warmFrom: Int = PowerManager.THERMAL_STATUS_MODERATE): Level = when {
        powerSave || thermal >= warmFrom -> Level.Saver
        !charging && battery in 0..LOW_BATTERY -> Level.Saver
        charging -> Level.Full
        else -> Level.Balanced
    }

    /**
     * How often the sound bars redraw, in ms: [big] is the opened music island (on screen for
     * seconds, so smoother), [synced] when they follow the real music (else the gentle wiggle).
     */
    fun barsFrameMs(level: Level, synced: Boolean, big: Boolean = false): Long = when (level) {
        Level.Full -> if (synced) 16L else 33L
        Level.Balanced -> if (big) (if (synced) 16L else 33L) else if (synced) 33L else 50L
        Level.Saver -> if (big) 33L else if (synced) 50L else 66L
    }

    /** The turntable's frame time in ms, or null when it rests (Saver: it still turns with a drag). */
    fun spinFrameMs(level: Level): Long? = when (level) {
        Level.Full -> 16L
        Level.Balanced -> 33L
        Level.Saver -> null
    }

    /** How many times a second the music listener hears, from Android's most ([maxMilliHz] is mHz). */
    fun listenRateMilliHz(level: Level, maxMilliHz: Int): Int = if (level == Level.Saver) maxMilliHz / 2 else maxMilliHz

    /** Background heart readings: on Saver, a continuous stream becomes Smart spot checks. */
    fun heartPace(chosen: HeartPace, level: Level, background: Boolean): HeartPace =
        if (background && level == Level.Saver && chosen == HeartPace.Continuous) HeartPace.Smart else chosen

    /**
     * Waits about [ms] and returns on the screen's next frame, so a paced animation still lands on
     * the display's rhythm (no judder) while the phone sleeps in between.
     */
    suspend fun nextFrame(ms: Long) {
        // A little less than the frame time: the wait for the next frame makes up the rest.
        val sleep = ms - 10L
        if (sleep > 0L) delay(sleep)
        withFrameMillis { }
    }

    // ---- Watching the phone ----
    private var attached = false
    private var charging = false
    private var battery = 100

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            if (i.action == Intent.ACTION_BATTERY_CHANGED) readBattery(i)
            update(c)
        }
    }

    private fun readBattery(i: Intent) {
        val lvl = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        if (lvl >= 0) battery = lvl * 100 / scale
        charging = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
    }

    /**
     * Starts following the battery, Battery Saver and the phone's heat (once, for the app's whole
     * life; Android sends these anyway, so listening costs nothing). Call on the main thread.
     */
    fun attach(context: Context) {
        if (attached) return
        attached = true
        val app = context.applicationContext
        PhoneProfile.load(app)
        val sticky = runCatching {
            ContextCompat.registerReceiver(
                app, receiver,
                IntentFilter().apply {
                    addAction(Intent.ACTION_BATTERY_CHANGED)
                    addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
                },
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }.getOrNull()
        sticky?.let(::readBattery)
        runCatching { app.getSystemService(PowerManager::class.java)?.addThermalStatusListener(app.mainExecutor) { update(app) } }
        update(app)
    }

    /** Looks again now (the phone profile changed what counts as warm). */
    fun recheck(context: Context) = update(context.applicationContext)

    private fun update(context: Context) {
        val pm = context.getSystemService(PowerManager::class.java)
        val saving = pm?.isPowerSaveMode == true
        val thermal = pm?.currentThermalStatus ?: 0
        val next = levelFor(charging, battery, saving, thermal, PhoneProfile.warmFrom)
        if (next != _level.value) {
            _level.value = next
            MusicPulse.replan(context)
        }
        // The glass's lighter recipe follows the same signs, in the overlays too (not only in the app).
        GlassBudget.set(saving, thermal)
    }

    /** Tests and screenshots. */
    internal fun preview(l: Level) { _level.value = l }
}
