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

/**
 * The thinking behind "any sound pops the Dynamic Island, with the app that made it". Pure
 * functions, so it's all unit-tested without a phone ([SoundSource] is the Android side).
 *
 * What Android gives an app (checked in Android's source and SDK): while something plays, a list
 * of the sounds that are playing right now, each with only its type (media, alert, call...). It
 * never says which app. So the app is worked out from three clues, best first:
 * 1. the music app's media session (Notification access), for anything musical;
 * 2. an app whose notification was posted a moment ago (Notification access), for alerts;
 * 3. the app on screen (the Dynamic Island accessibility service), for anything else.
 * With none of them the island still shows the sound, with a symbol for its type.
 */
object SoundRules {
    /** What kind of sound it is, from Android's usage number. */
    enum class Kind(
        val label: String,
        /** Music, video and games: the island's music state takes these (with the app's icon). */
        val musicLike: Boolean,
        /** Short, attention-grabbing sounds that may pop over music for a moment. */
        val blip: Boolean,
        /** Which wins when several play at once: lower first. */
        val rank: Int,
    ) {
        Call("Call", false, true, 0),
        Alarm("Alarm", false, true, 1),
        Alert("Alert", false, true, 2),
        Voice("Voice", false, true, 3),
        Media("Media", true, false, 4),
        Game("Game", true, false, 5),
        Other("Sound", true, false, 6),
    }

    /**
     * The kind for one of Android's AudioAttributes usages, or null for sounds that shouldn't
     * pop the island at all: interface clicks (keyboard, touch, screen lock), a screen reader's
     * speech and virtual sources (screen recording and the like). Numbers checked against the
     * Android SDK.
     */
    fun kindOf(usage: Int): Kind? = when (usage) {
        1 -> Kind.Media                // USAGE_MEDIA
        14 -> Kind.Game                // USAGE_GAME
        0 -> Kind.Other                // USAGE_UNKNOWN
        2, 3, 6 -> Kind.Call           // voice communication (+ signalling), ringtone
        4 -> Kind.Alarm                // USAGE_ALARM
        5, 7, 8, 9, 10 -> Kind.Alert   // notification and its communication/event variants
        12, 16 -> Kind.Voice           // spoken directions, assistant
        // Interface clicks (keyboard, touch, screen lock), a screen reader's speech (TalkBack would
        // otherwise pop the island with every word) and virtual sources (screen recording).
        13, 11, 15 -> null
        else -> Kind.Other
    }

    /** The sounds playing right now, boiled down. */
    data class Summary(val kind: Kind, val musicLike: Boolean)

    /** What's playing, from the usages of everything active; null when nothing worth showing. */
    fun summarize(usages: List<Int>): Summary? {
        val kinds = usages.mapNotNull(::kindOf)
        val top = kinds.minByOrNull { it.rank } ?: return null
        return Summary(top, kinds.any { it.musicLike })
    }

    // ---- Which app ----

    enum class Basis { Session, Notification, Foreground, None }

    /** The clues for one sound: the music session's app, a recent notification's app, the app on screen. */
    data class Evidence(val session: String?, val notification: String?, val foreground: String?)

    /**
     * The app behind a sound and how we know. Musical sounds trust the media session first, the
     * screen second; alerts trust the notification that just arrived first. [skip] says which
     * packages never count (pro itself, the phone's own pieces).
     */
    fun attribute(kind: Kind, e: Evidence, skip: (String) -> Boolean = { false }): Pair<String?, Basis> {
        val order = if (kind.musicLike) {
            listOf(Basis.Session to e.session, Basis.Foreground to e.foreground, Basis.Notification to e.notification)
        } else {
            listOf(Basis.Notification to e.notification, Basis.Session to e.session, Basis.Foreground to e.foreground)
        }
        val hit = order.firstOrNull { (_, pkg) -> pkg != null && !skip(pkg) }
        return if (hit == null) null to Basis.None else hit.second to hit.first
    }

    // ---- Notifications posted a moment ago ----

    /** How far back a notification still explains a sound. */
    const val NOTE_WINDOW_MS = 3_000L

    /**
     * Whether a notification can explain a sound: not an ongoing one (a music player's controls, a
     * running service), not a group's summary, and not from [own]. Only the app name and time
     * matter; nothing in the notification is read.
     */
    fun worthNoting(flags: Int, pkg: String?, own: String): Boolean {
        if (pkg == null || pkg == own) return false
        val ongoing = flags and FLAG_ONGOING_EVENT != 0
        val service = flags and FLAG_FOREGROUND_SERVICE != 0
        val summary = flags and FLAG_GROUP_SUMMARY != 0
        return !ongoing && !service && !summary
    }

    // Android's Notification flags (values checked in the SDK), repeated so tests need no Android.
    const val FLAG_ONGOING_EVENT = 2
    const val FLAG_FOREGROUND_SERVICE = 64
    const val FLAG_GROUP_SUMMARY = 512

    /** The last few notifications (app and time), newest last. */
    class NoteLog(private val keep: Int = 8) {
        private val items = ArrayDeque<Pair<String, Long>>()

        fun add(pkg: String, at: Long) {
            items.addLast(pkg to at)
            while (items.size > keep) items.removeFirst()
        }

        fun clear() = items.clear()

        /** The app whose notification came within [window] before [now], newest first; null if none. */
        fun recent(now: Long, window: Long = NOTE_WINDOW_MS, skip: (String) -> Boolean = { false }): String? =
            items.lastOrNull { (pkg, at) -> now - at in -500L..window && !skip(pkg) }?.first
    }

    // ---- Messages and other notifications: a moment of their own ----

    /**
     * A notification that has just arrived, as far as the island cares. [title] is only filled in
     * when "Show who it's from" is on; otherwise nothing inside the notification is ever read.
     * [importance] is Android's (0 none, 1 min, 2 low, 3 default, 4 high; below zero when unknown),
     * [interrupts] whether Do Not Disturb would let it through.
     */
    data class Posted(
        val pkg: String?, val key: String, val flags: Int, val category: String?, val importance: Int,
        val conversation: Boolean, val interrupts: Boolean, val title: String?,
    )

    const val CATEGORY_MESSAGE = "msg"
    const val FLAG_ONLY_ALERT_ONCE = 8
    const val IMPORTANCE_DEFAULT = 3

    fun isMessage(p: Posted): Boolean = p.category == CATEGORY_MESSAGE || p.conversation

    /**
     * Whether a notification earns a moment on the island. The island follows the phone: nothing
     * for a silent or muted notification (a muted chat has a lower importance), nothing when Do
     * Not Disturb would hold it back (if asked), never for ongoing ones. Messages by default;
     * other alerting notifications only when [othersOn].
     */
    fun worthAMoment(p: Posted, own: String, messagesOn: Boolean, othersOn: Boolean, respectDnd: Boolean): Boolean {
        if (!worthNoting(p.flags, p.pkg, own)) return false
        if (respectDnd && !p.interrupts) return false
        if (p.importance in 0 until IMPORTANCE_DEFAULT) return false
        return if (isMessage(p)) messagesOn else othersOn && p.importance >= IMPORTANCE_DEFAULT
    }

    /**
     * Stops one conversation from popping the island twice for one message: the same notification
     * again within [windowMs] is the same news, and one flagged "alert only once" is not news again
     * for [onceWindowMs]. A new message in the same chat a few seconds later does pop.
     */
    class Dedupe(private val windowMs: Long = 4_000L, private val onceWindowMs: Long = 600_000L, private val keep: Int = 64) {
        private val last = LinkedHashMap<String, Long>()

        fun fresh(key: String, flags: Int, now: Long): Boolean {
            val before = last.remove(key)
            last[key] = now
            while (last.size > keep) last.remove(last.keys.first())
            if (before == null) return true
            val gap = now - before
            if (gap < windowMs) return false
            return !(flags and FLAG_ONLY_ALERT_ONCE != 0 && gap < onceWindowMs)
        }
    }

    // ---- The app on screen ----

    /** What a package is, for working out the app on screen. */
    enum class Role {
        /** An app a person opens. */
        App,
        /** The home screen: nothing is "in front". */
        Launcher,
        /** The phone's own pieces (status bar, keyboard, pro): never the answer, never clears it. */
        System,
    }

    /** The app on screen after a window changed: apps replace it, the home screen clears it, system pieces leave it. */
    fun foregroundAfter(current: String?, pkg: String, role: Role): String? = when (role) {
        Role.App -> pkg
        Role.Launcher -> null
        Role.System -> current
    }

    // ---- How long it stays ----

    /** How long a short sound keeps the pill up after it ends (Settings > Island). */
    enum class Linger(val label: String, val ms: Long) {
        Short("Short", 2_200L),
        Normal("Normal", 3_800L),
        Long("Long", 6_500L),
    }

    // ---- What's been heard ----

    /** One app (or one kind of sound, when the app isn't known) in the recent list. */
    data class Seen(val pkg: String?, val kind: Kind, val at: Long, val count: Int) {
        val key: String get() = pkg ?: "kind:${kind.name}"
    }

    /** Puts a new sound at the top of the list, folding repeats of the same app into one row. */
    fun remember(list: List<Seen>, pkg: String?, kind: Kind, at: Long, max: Int = 12): List<Seen> {
        val fresh = Seen(pkg, kind, at, 1)
        val old = list.firstOrNull { it.key == fresh.key }
        val row = if (old != null) fresh.copy(count = old.count + 1) else fresh
        return (listOf(row) + list.filter { it.key != fresh.key }).take(max)
    }

    /** One plain sentence on how well pro can tell which app made a sound, given which clues are switched on. */
    fun clueSummary(notificationAccess: Boolean, screenSwitch: Boolean): String = when {
        notificationAccess && screenSwitch -> "pro can tell which app made almost every sound."
        notificationAccess -> "pro can tell for alerts and music apps. For sounds from the app you're in, turn on the Dynamic Island switch."
        screenSwitch -> "pro can tell for the app you're in. For alerts that arrive while you're elsewhere, allow Notification access."
        else -> "pro shows a symbol for the kind of sound (bell, speaker, call). Turn on a switch below to see app icons."
    }

    /** "now", "2 min ago", "3 h ago": short and plain. */
    fun ago(ms: Long): String = when {
        ms < 45_000L -> "just now"
        ms < 90 * 60_000L -> "${maxOf(1L, ms / 60_000L)} min ago"
        ms < 36 * 3_600_000L -> "${ms / 3_600_000L} h ago"
        else -> "${ms / 86_400_000L} d ago"
    }

    /**
     * Whether the pill should be showing for this sound at [now]: while it plays, and for
     * [lingerMs] after it ends (so even a half-second ding is seen).
     */
    fun showing(active: Boolean, endedAt: Long, now: Long, lingerMs: Long): Boolean =
        active || (endedAt > 0L && now - endedAt < lingerMs)
}
