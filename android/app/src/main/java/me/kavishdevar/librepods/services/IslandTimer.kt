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

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.VibratorManager
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.kavishdevar.librepods.R
import me.kavishdevar.librepods.presentation.overlays.GlintOverlays
import me.kavishdevar.librepods.presentation.overlays.IslandEvent

/**
 * The Dynamic Island's own timer, like the iPhone's timer Live Activity: started from the opened
 * island (Timer, then a length), it counts down as a ring beside the camera whatever app you're
 * in, and rings when it ends (the phone's alarm sound and a buzz, until you stop it, at most a
 * minute), with a Stop button on the island and on a notification.
 *
 * It runs on Android's alarm clock, so it rings on time even with the screen off or pro closed.
 * The countdown itself is pure arithmetic over the end time ([TimerRules]), so nothing ticks in the
 * background while it runs.
 */
object IslandTimer {
    private const val TAG = "IslandTimer"
    private const val PREF_END = "glint_timer_end_wall"
    private const val PREF_TOTAL = "glint_timer_total"
    private const val PREF_LEFT = "glint_timer_paused_left"
    private const val CHANNEL = "pro_timer"
    private const val NOTE_ID = 7_301
    internal const val ACTION_FIRE = "me.kavishdevar.librepods.TIMER_FIRE"
    internal const val ACTION_STOP = "me.kavishdevar.librepods.TIMER_STOP"
    /** The longest it rings before stopping by itself. */
    const val RING_MAX_MS = 60_000L

    /**
     * A timer. While running it ends at [endsAt] (elapsedRealtime); paused it has [pausedLeft] to
     * go. [total] is the length it was set for (for the ring), [ringing] once it has ended.
     */
    @Immutable
    data class State(
        val total: Long,
        val endsAt: Long = 0L,
        val pausedLeft: Long = -1L,
        val ringing: Boolean = false,
    ) {
        val paused: Boolean get() = pausedLeft >= 0L && !ringing
        val running: Boolean get() = !paused && !ringing
    }

    private val _state = MutableStateFlow<State?>(null)
    val state: StateFlow<State?> = _state.asStateFlow()

    private var appContext: Context? = null
    private val main = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null

    /** Picks up a timer that was running before pro restarted (safe to call often). */
    fun attach(context: Context) {
        if (appContext != null) return
        val app = context.applicationContext
        appContext = app
        restore(app)
    }

    /** Reads a saved timer back (pro was restarted, or Android woke it just for the alarm). */
    private fun restore(app: Context) {
        val p = prefs(app)
        val total = p.getLong(PREF_TOTAL, 0L)
        if (total <= 0L) return
        val left = p.getLong(PREF_LEFT, -1L)
        val wallEnd = p.getLong(PREF_END, 0L)
        val restored = TimerRules.restore(total, wallEnd, left, System.currentTimeMillis(), SystemClock.elapsedRealtime())
        if (restored == null) { clearSaved(app); return }
        _state.value = restored
        if (restored.running) schedule(app, restored.endsAt)
    }

    /** Starts a timer of [ms] (replacing any other). */
    fun start(context: Context, ms: Long) {
        val app = context.applicationContext
        appContext = appContext ?: app
        stopSound()
        val s = State(total = ms, endsAt = SystemClock.elapsedRealtime() + ms)
        set(app, s)
        schedule(app, s.endsAt)
    }

    fun pause(context: Context) {
        val s = _state.value ?: return
        if (!s.running) return
        val app = context.applicationContext
        cancelAlarm(app)
        set(app, s.copy(pausedLeft = TimerRules.left(s, SystemClock.elapsedRealtime())))
    }

    fun resume(context: Context) {
        val s = _state.value ?: return
        if (!s.paused) return
        val app = context.applicationContext
        val next = s.copy(endsAt = SystemClock.elapsedRealtime() + s.pausedLeft, pausedLeft = -1L)
        set(app, next)
        schedule(app, next.endsAt)
    }

    /** One more minute: on a running or paused timer, or on one that's ringing (snooze-style). */
    fun addMinute(context: Context) {
        val s = _state.value ?: return
        val app = context.applicationContext
        val now = SystemClock.elapsedRealtime()
        val next = TimerRules.plusMinute(s, now)
        if (s.ringing) {
            // Snoozed: quiet again, and the "Timer done" notification goes with it.
            stopSound()
            app.getSystemService(NotificationManager::class.java)?.cancel(NOTE_ID)
        }
        set(app, next)
        if (next.running) schedule(app, next.endsAt)
    }

    /** Stops it: cancels a running timer, or silences one that's ringing. */
    fun cancel(context: Context) {
        val app = context.applicationContext
        cancelAlarm(app)
        stopSound()
        _state.value = null
        clearSaved(app)
        app.getSystemService(NotificationManager::class.java)?.cancel(NOTE_ID)
    }

    private fun set(c: Context, s: State) {
        _state.value = s
        prefs(c).edit {
            putLong(PREF_TOTAL, s.total)
            putLong(PREF_LEFT, if (s.paused) s.pausedLeft else -1L)
            // Wall-clock end, so a restart of pro (not of the phone's clock) finds it again.
            putLong(PREF_END, if (s.running) System.currentTimeMillis() + (s.endsAt - SystemClock.elapsedRealtime()) else 0L)
        }
    }

    private fun clearSaved(c: Context) = prefs(c).edit { remove(PREF_TOTAL); remove(PREF_LEFT); remove(PREF_END) }

    private fun prefs(c: Context) = c.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private fun firePending(c: Context): PendingIntent = PendingIntent.getBroadcast(
        c, 7_302, Intent(c, TimerReceiver::class.java).setAction(ACTION_FIRE),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /**
     * On time even with the screen off: an exact alarm when Android allows pro one (always in the
     * GitHub build, which declares the alarm-clock permission), otherwise the closest Android
     * allows, which can be a little late in deep sleep.
     */
    private fun schedule(c: Context, endsAt: Long) {
        val am = c.getSystemService(AlarmManager::class.java) ?: return
        val pi = firePending(c)
        try {
            if (am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, endsAt, pi)
            else am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, endsAt, pi)
        } catch (e: SecurityException) {
            Log.w(TAG, "Exact alarm refused, using an inexact one", e)
            am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, endsAt, pi)
        }
        // While pro is running anyway, a plain timer catches the end on the dot too.
        main.removeCallbacks(onTime)
        main.postDelayed(onTime, (endsAt - SystemClock.elapsedRealtime()).coerceAtLeast(1L))
    }

    private val onTime = Runnable { appContext?.let { fired(it) } }

    private fun cancelAlarm(c: Context) {
        main.removeCallbacks(onTime)
        runCatching { c.getSystemService(AlarmManager::class.java)?.cancel(firePending(c)) }
    }

    /** The end came (from the alarm or the in-app timer, whichever is first; the second does nothing). */
    internal fun fired(context: Context) {
        val app = context.applicationContext
        appContext = appContext ?: app
        // Android may have closed pro and woken it only for this alarm: read the timer back first
        // (calling attach here would do nothing, since the context is already set).
        if (_state.value == null) restore(app)
        val s = _state.value ?: return
        if (s.ringing || !s.running) return
        if (TimerRules.left(s, SystemClock.elapsedRealtime()) > 500L) {
            // Woken early (an inexact alarm, a clock change): wait for the real end.
            schedule(app, s.endsAt)
            return
        }
        cancelAlarm(app)
        _state.value = s.copy(ringing = true)
        clearSaved(app)
        ring(app)
        notifyDone(app)
        GlintOverlays.showIsland(app, IslandEvent.TimerDone, expand = true)
        main.postDelayed({ if (_state.value?.ringing == true) cancel(app) }, RING_MAX_MS)
    }

    private fun ring(c: Context) {
        stopSound()
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        player = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                setDataSource(c, uri)
                isLooping = true
                prepare()
                start()
            }
        }.onFailure { Log.w(TAG, "Couldn't play the alarm sound", it) }.getOrNull()
        runCatching {
            c.getSystemService(VibratorManager::class.java)?.defaultVibrator
                ?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 450, 350, 450, 900), 0))
        }
    }

    private fun stopSound() {
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
        appContext?.let { c -> runCatching { c.getSystemService(VibratorManager::class.java)?.defaultVibrator?.cancel() } }
    }

    private fun notifyDone(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Timer", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "When a timer started from the Dynamic Island ends"
            setSound(null, null)
        })
        val stop = PendingIntent.getBroadcast(
            c, 7_303, Intent(c, TimerReceiver::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val note = NotificationCompat.Builder(c, CHANNEL)
            .setSmallIcon(R.drawable.airpods)
            .setContentTitle("Timer done")
            .setContentText("Tap Stop to silence it")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .setDeleteIntent(stop)
            .addAction(0, "Stop", stop)
            .build()
        runCatching { nm.notify(NOTE_ID, note) }.onFailure { Log.w(TAG, "Couldn't post the timer notification", it) }
    }

    // ---- Screenshots and tests ----

    internal fun preview(s: State?) { _state.value = s }

    /** Tests only: forget everything in memory, as if Android had closed pro (what's saved stays). */
    internal fun forgetForTest() {
        main.removeCallbacks(onTime)
        stopSound()
        _state.value = null
        appContext = null
    }
}

/** Gets the timer's alarm (and the notification's Stop button), even when pro isn't running. */
class TimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            IslandTimer.ACTION_FIRE -> IslandTimer.fired(context)
            IslandTimer.ACTION_STOP -> IslandTimer.cancel(context)
        }
    }
}

/** The pure arithmetic of [IslandTimer], unit-tested without a phone. */
object TimerRules {
    /** Lengths offered on the opened island, in minutes. */
    val PRESETS = listOf(1, 3, 5, 10, 15, 30)

    /** What's left at [now] (elapsedRealtime), never below zero. */
    fun left(s: IslandTimer.State, now: Long): Long = when {
        s.ringing -> 0L
        s.paused -> s.pausedLeft
        else -> (s.endsAt - now).coerceAtLeast(0L)
    }

    /** How far through it is, 0 at the start to 1 at the end (the ring beside the camera). */
    fun progress(s: IslandTimer.State, now: Long): Float =
        if (s.total <= 0L) 1f else (1f - left(s, now).toFloat() / s.total).coerceIn(0f, 1f)

    /** "4:05", "59", "1:02:03": minutes and seconds (hours when there are any), rounded up so it never shows 0:00 early. */
    fun format(ms: Long): String {
        val total = ((ms + 999L) / 1000L).coerceAtLeast(0L)
        val h = total / 3600; val m = (total % 3600) / 60; val sec = total % 60
        return if (h > 0) "%d:%02d:%02d".format(java.util.Locale.ROOT, h, m, sec) else "%d:%02d".format(java.util.Locale.ROOT, m, sec)
    }

    /** The short form for the tiny ring: whole minutes left ("5"), or seconds in the last minute ("42"). */
    fun short(ms: Long): String {
        val sec = ((ms + 999L) / 1000L).coerceAtLeast(0L)
        return if (sec >= 60) "${(sec + 59) / 60}" else "$sec"
    }

    /** One more minute. A ringing timer starts again with one minute to go. */
    fun plusMinute(s: IslandTimer.State, now: Long): IslandTimer.State = when {
        s.ringing -> IslandTimer.State(total = 60_000L, endsAt = now + 60_000L)
        s.paused -> s.copy(pausedLeft = s.pausedLeft + 60_000L, total = s.total + 60_000L)
        else -> s.copy(endsAt = maxOf(s.endsAt, now) + 60_000L, total = s.total + 60_000L)
    }

    /** A timer that ended this recently while pro wasn't running still rings (the alarm woke pro for it). */
    const val LATE_RING_MS = 60_000L

    /**
     * A timer saved before pro restarted: still running if its wall-clock end is ahead, paused if it
     * was paused, due now if it ended within [LATE_RING_MS] (Android's alarm starting pro for it lands
     * right at or just after the end), otherwise gone (a timer missed long ago isn't rung late).
     */
    fun restore(total: Long, wallEnd: Long, pausedLeft: Long, wallNow: Long, elapsedNow: Long): IslandTimer.State? = when {
        total <= 0L -> null
        pausedLeft >= 0L -> IslandTimer.State(total = total, pausedLeft = pausedLeft)
        wallEnd > 0L && wallEnd > wallNow - LATE_RING_MS -> IslandTimer.State(total = total, endsAt = elapsedNow + (wallEnd - wallNow).coerceAtLeast(0L))
        else -> null
    }
}
