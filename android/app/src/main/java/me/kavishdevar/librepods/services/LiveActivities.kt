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

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Live activities, Android's way: what an app is doing right now that it keeps a notification up
 * for (Google Maps navigating, a ride on its way, a download, a recording, a stopwatch, a call).
 * The Dynamic Island shows it the iPhone way, balanced either side of the camera: the app on the
 * left, its one live number on the right ("200 m", "4 min", "45%", "12:03"), and the opened glance
 * shows the app's own words and its own buttons (Maps' "Exit", a download's "Cancel").
 *
 * So each app is shown with what it says itself, not a guess from what kind of app it is
 * (Jake, 2026-10-11: "personalized per app ... if I have Google Maps going ... show that").
 *
 * Only ongoing notifications count, never messages or chats, and never the music player (the
 * island has its own music look). Their words are only shown, never stored. Needs Notification
 * access (the same switch as the song names); off in Settings > Island > Live from apps.
 */
object LiveActivities {
    private const val TAG = "LiveActivities"

    enum class Kind { Navigation, Ride, Progress, Timer, Call, Recording, Other }

    /** One of an app's own buttons on its notification. */
    class Action(val title: String, val intent: PendingIntent)

    data class Live(
        val pkg: String,
        val key: String,
        val kind: Kind,
        /** What shows left of the camera: Maps' own turn arrow (its notification's picture), else the app's icon. */
        val picture: ImageBitmap? = null,
        /** The app's own first line (the next turn, "Your driver is arriving"). */
        val title: String?,
        /** Its second line (the road, the car). */
        val text: String?,
        /** The one short live value for the right of the camera, or null (then the app's icon alone). */
        val short: String?,
        /** 0..1 when the app shows a progress bar. */
        val progress: Float?,
        /** A running clock's start (wall ms) when the app shows one ([countDown]: counting to it). */
        val clockFrom: Long?,
        val countDown: Boolean,
        val icon: ImageBitmap?,
        val actions: List<Action>,
        val open: PendingIntent?,
        val postedAt: Long,
    ) {
        /** What the right of the camera shows at [wallNow]: the clock if it runs, else [short]. */
        fun value(wallNow: Long): String? = clockFrom?.let { LiveRules.clock(it, wallNow, countDown) } ?: short
    }

    private val _all = MutableStateFlow<Map<String, Live>>(emptyMap())
    /** Every live activity now, by notification key. */
    val all: StateFlow<Map<String, Live>> = _all.asStateFlow()

    private val main = Handler(Looper.getMainLooper())
    /** Reading a notification may look its app's icon up: off the main thread, one at a time, in order. */
    private val worker = java.util.concurrent.Executors.newSingleThreadExecutor()

    /** The one to show: the app in front's own, else navigation, else the newest. */
    fun pick(front: String?, all: Map<String, Live> = _all.value): Live? = LiveRules.pick(all.values, front)

    fun forApp(pkg: String?): Live? = pkg?.let { p -> _all.value.values.filter { it.pkg == p }.maxByOrNull { it.postedAt } }

    /** A notification was posted or updated (from the notification listener, any thread). */
    fun posted(context: Context, sbn: StatusBarNotification) = worker.execute {
        if (!IslandPrefs.liveFromApps(IslandPrefs.prefs(context))) { if (_all.value.isNotEmpty()) clear(); return@execute }
        val live = runCatching { read(context, sbn) }.onFailure { Log.w(TAG, "Couldn't read a notification", it) }.getOrNull()
        val key = sbn.key ?: return@execute
        main.post {
            val now = _all.value
            _all.value = if (live == null) now - key else now + (key to live)
        }
    }

    fun removed(key: String?) {
        key ?: return
        worker.execute { main.post { if (key in _all.value) _all.value = _all.value - key } }
    }

    /** All of them at once (when the listener connects). */
    fun replaceAll(context: Context, list: () -> Array<StatusBarNotification>?) = worker.execute {
        if (!IslandPrefs.liveFromApps(IslandPrefs.prefs(context))) { clear(); return@execute }
        val found = runCatching { list() }.getOrNull().orEmpty().mapNotNull { sbn -> runCatching { read(context, sbn) }.getOrNull()?.let { sbn.key to it } }.toMap()
        main.post { _all.value = found }
    }

    fun clear() = main.post { _all.value = emptyMap() }

    /** The notification listener's own list of what's up now (set while it's connected). */
    @Volatile internal var source: (() -> Array<StatusBarNotification>?)? = null

    /** Settings > Island > Live from apps was switched: start over from what's up now, or clear. */
    fun settingChanged(context: Context) {
        val list = source
        if (IslandPrefs.liveFromApps(IslandPrefs.prefs(context)) && list != null) replaceAll(context, list) else clear()
    }

    /** Screenshots and tests. */
    internal fun preview(list: List<Live>) { _all.value = list.associateBy { it.key } }

    private fun read(context: Context, sbn: StatusBarNotification): Live? {
        val n = sbn.notification ?: return null
        val pkg = sbn.packageName ?: return null
        val x = n.extras
        val template = x?.getString(Notification.EXTRA_TEMPLATE)
        val max = x?.getInt(Notification.EXTRA_PROGRESS_MAX, 0) ?: 0
        val cur = x?.getInt(Notification.EXTRA_PROGRESS, 0) ?: 0
        val indeterminate = x?.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false) ?: false
        val chrono = x?.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER, false) ?: false
        val down = x?.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN, false) ?: false
        // Android 16's Live Updates ("promoted ongoing"): the app itself says this is live.
        @android.annotation.SuppressLint("InlinedApi")
        val promoted = android.os.Build.VERSION.SDK_INT >= 36 && (n.flags and Notification.FLAG_PROMOTED_ONGOING) != 0
        val kind = LiveRules.kind(
            pkg = pkg, own = context.packageName, flags = n.flags, category = n.category, template = template,
            hasProgress = max > 0 && !indeterminate, chrono = chrono, promoted = promoted,
        ) ?: return null
        val title = x?.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.takeIf { it.isNotBlank() }
        val text = (x?.getCharSequence(Notification.EXTRA_TEXT) ?: x?.getCharSequence(Notification.EXTRA_SUB_TEXT))?.toString()?.takeIf { it.isNotBlank() }
        val progress = if (max > 0 && !indeterminate) (cur.toFloat() / max).coerceIn(0f, 1f) else null
        val clockFrom = if (chrono && n.`when` > 0L) n.`when` else null
        val short = LiveRules.short(kind, title, text, progress)
        val actions = n.actions.orEmpty().filter { it.title != null && it.actionIntent != null && it.remoteInputs.isNullOrEmpty() }
            .take(3).map { Action(it.title.toString(), it.actionIntent) }
        // Navigation and rides: the app's own picture (Maps puts the next turn's arrow there).
        val picture = if (kind == Kind.Navigation || kind == Kind.Ride) runCatching {
            n.getLargeIcon()?.loadDrawable(context)?.let { d ->
                d.toBitmap(96, 96).asImageBitmap()
            }
        }.getOrNull() else null
        return Live(
            pkg = pkg, key = sbn.key, kind = kind, picture = picture, title = title, text = text, short = short, progress = progress,
            clockFrom = clockFrom, countDown = down, icon = SoundSource.appInfo(context, pkg).icon, actions = actions,
            open = n.contentIntent, postedAt = sbn.postTime,
        )
    }

    /** Presses one of the app's own buttons (Maps' "Exit", a download's "Cancel"). */
    fun press(a: Action): Boolean = send(a.intent)

    /** Opens the app at what the activity is about (its notification's own tap). */
    fun open(context: Context, l: Live) {
        val sent = l.open?.let { send(it) } ?: false
        if (!sent) SoundSource.openApp(context, l.pkg)
    }

    /**
     * Sends an app's own action. From Android 14 an app's action may open its screen from pro only
     * if pro says it may (pro is on screen, the island being tapped): without this, Maps' "Exit"
     * or a tap on the card could do nothing at all.
     */
    private fun send(pi: PendingIntent): Boolean = runCatching {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val mode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.BAKLAVA) {
                android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE
            } else {
                @Suppress("DEPRECATION") android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
            }
            pi.send(android.app.ActivityOptions.makeBasic().setPendingIntentBackgroundActivityStartMode(mode).toBundle())
        } else pi.send()
        true
    }.onFailure { Log.w(TAG, "The app's action didn't go through", it) }.getOrDefault(false)
}

/** The pure rules behind [LiveActivities], unit-tested. */
object LiveRules {
    private val MAPS = setOf("com.google.android.apps.maps", "com.waze", "com.here.app.maps", "net.osmand", "net.osmand.plus", "app.organicmaps", "com.sygic.aura")
    private val RIDES = setOf("com.ubercab", "com.lyft.android", "com.bolt.client", "com.ubercab.eats", "com.dd.doordash", "com.grubhub.android", "com.instacart.client")

    /**
     * Whether a notification is a live activity, and which kind; null when it isn't (a message, a
     * plain alert, the music player, pro's own). Only ongoing ones count.
     */
    fun kind(
        pkg: String, own: String, flags: Int, category: String?, template: String?,
        hasProgress: Boolean, chrono: Boolean, promoted: Boolean,
    ): LiveActivities.Kind? {
        if (pkg == own) return null
        val ongoing = flags and (Notification.FLAG_ONGOING_EVENT or Notification.FLAG_FOREGROUND_SERVICE) != 0
        if (!ongoing && !promoted) return null
        // Music has its own look; messages are never a live activity.
        if (template?.endsWith("MediaStyle") == true || category == Notification.CATEGORY_TRANSPORT) return null
        if (category == Notification.CATEGORY_MESSAGE || category == Notification.CATEGORY_EMAIL || category == Notification.CATEGORY_SOCIAL) return null
        return when {
            category == Notification.CATEGORY_NAVIGATION || pkg in MAPS -> LiveActivities.Kind.Navigation
            pkg in RIDES || category == "location_sharing" -> LiveActivities.Kind.Ride
            category == Notification.CATEGORY_CALL -> LiveActivities.Kind.Call
            category == Notification.CATEGORY_STOPWATCH || category == Notification.CATEGORY_ALARM -> LiveActivities.Kind.Timer
            hasProgress || category == Notification.CATEGORY_PROGRESS -> LiveActivities.Kind.Progress
            chrono -> LiveActivities.Kind.Recording
            promoted -> LiveActivities.Kind.Other
            // An ongoing notification with nothing live in it (a "running in background" note): not one.
            else -> null
        }
    }

    private val DISTANCE = Regex("""(\d+(?:[.,]\d+)?)\s?(km|mi|m|ft|yd)\b""", RegexOption.IGNORE_CASE)
    private val MINUTES = Regex("""(\d+)\s?(min|mins|minutes|h|hr|hrs|hours)\b""", RegexOption.IGNORE_CASE)
    private val CLOCK = Regex("""\b(\d{1,2}:\d{2})\b""")
    private val PERCENT = Regex("""\b(\d{1,3})\s?%""")

    /** The one short value for the right of the camera, from the app's own words; null when there's none. */
    fun short(kind: LiveActivities.Kind, title: String?, text: String?, progress: Float?): String? {
        val words = listOfNotNull(title, text).joinToString("  ")
        fun distance() = DISTANCE.find(words)?.let { "${it.groupValues[1].replace(',', '.')} ${it.groupValues[2].lowercase()}" }
        fun minutes() = MINUTES.find(words)?.let { m ->
            val unit = m.groupValues[2].lowercase()
            "${m.groupValues[1]} ${if (unit.startsWith("h")) "h" else "min"}"
        }
        fun percent() = progress?.let { "${(it * 100).toInt()}%" } ?: PERCENT.find(words)?.let { "${it.groupValues[1]}%" }
        val v = when (kind) {
            LiveActivities.Kind.Navigation -> distance() ?: minutes()
            LiveActivities.Kind.Ride -> minutes() ?: CLOCK.find(words)?.groupValues?.get(1)
            LiveActivities.Kind.Progress -> percent()
            else -> percent() ?: minutes() ?: distance()
        }
        // Balanced with the left side: short, or nothing (the icon alone says enough).
        return v?.takeIf { it.length <= 7 }
    }

    /** A running clock as "4:05" (or "1:02:05"), counting up from [from] or down to it. */
    fun clock(from: Long, now: Long, countDown: Boolean): String {
        val ms = (if (countDown) from - now else now - from).coerceAtLeast(0L)
        val s = ms / 1000L
        val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
        return if (h > 0) "%d:%02d:%02d".format(java.util.Locale.ROOT, h, m, sec) else "%d:%02d".format(java.util.Locale.ROOT, m, sec)
    }

    /** The one to show: the app in front's own, else navigation, else the newest. */
    fun pick(all: Collection<LiveActivities.Live>, front: String?): LiveActivities.Live? =
        all.filter { it.pkg == front }.maxByOrNull { it.postedAt }
            ?: all.filter { it.kind == LiveActivities.Kind.Navigation }.maxByOrNull { it.postedAt }
            ?: all.maxByOrNull { it.postedAt }
}
