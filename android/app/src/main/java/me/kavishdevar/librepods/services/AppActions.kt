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

/**
 * Which controls the opened glance offers, for the kind of app you're in ([AppKinds]). Every app
 * gets a row that fits its kind, by one rule, instead of the same five buttons everywhere: a game
 * gets Mute, Do Not Disturb and a chat app; a music app with nothing playing gets Play and Search
 * (its recent songs show above); the home screen and plain apps keep the phone's own controls.
 * Pure, unit-tested.
 */
object AppActions {
    enum class Action(val label: String) {
        Torch("Torch"), Timer("Timer"), Ring("Sound"), Capture("Capture"), Lock("Lock"),
        /** The phone's media sound on and off (the game's or video's sound, not the ringer). */
        Mute("Mute"),
        /** Do Not Disturb. */
        Quiet("Focus"),
        /** Opens a chat app ([chatApp]). */
        Chat("Chat"),
        /** Plays again in this app. */
        Resume("Play"),
        /** This app's own search. */
        Search("Search"),
        Camera("Camera"),
    }

    /** The phone's own five, for the home screen, the lock screen and plain apps. */
    val PHONE = listOf(Action.Torch, Action.Timer, Action.Ring, Action.Capture, Action.Lock)

    /** Always five, so the row keeps its rhythm whichever app you're in. */
    fun row(kind: AppKinds.Kind?): List<Action> = when (kind) {
        AppKinds.Kind.Game -> listOf(Action.Mute, Action.Quiet, Action.Chat, Action.Capture, Action.Lock)
        AppKinds.Kind.Video -> listOf(Action.Mute, Action.Quiet, Action.Timer, Action.Capture, Action.Lock)
        AppKinds.Kind.Music, AppKinds.Kind.Listen -> listOf(Action.Resume, Action.Search, Action.Timer, Action.Ring, Action.Lock)
        AppKinds.Kind.Chat, AppKinds.Kind.Social -> listOf(Action.Quiet, Action.Camera, Action.Timer, Action.Capture, Action.Lock)
        AppKinds.Kind.Maps -> listOf(Action.Mute, Action.Quiet, Action.Torch, Action.Capture, Action.Lock)
        AppKinds.Kind.Camera -> listOf(Action.Torch, Action.Timer, Action.Ring, Action.Quiet, Action.Lock)
        AppKinds.Kind.Fitness -> listOf(Action.Timer, Action.Mute, Action.Quiet, Action.Torch, Action.Lock)
        else -> PHONE
    }

    /** Chat apps to offer, most fitting first: while gaming Discord leads (voice chat for games). */
    val CHAT_FOR_GAMES = listOf(
        "com.discord", "com.whatsapp", "org.telegram.messenger", "com.facebook.orca",
        "com.google.android.apps.messaging", "com.samsung.android.messaging", "org.thoughtcrime.securesms",
    )
    val CHAT = listOf(
        "com.whatsapp", "org.telegram.messenger", "com.google.android.apps.messaging", "com.samsung.android.messaging",
        "com.facebook.orca", "org.thoughtcrime.securesms", "com.discord",
    )

    /** The chat app to open: the first of the list that's installed (and isn't the app you're in). */
    fun chatApp(installed: (String) -> Boolean, forGame: Boolean, front: String?): String? =
        (if (forGame) CHAT_FOR_GAMES else CHAT).firstOrNull { it != front && installed(it) }

    /** A short name for the chat button, so it says what it opens. */
    fun chatLabel(pkg: String?): String = when (pkg) {
        "com.discord" -> "Discord"
        "com.whatsapp" -> "WhatsApp"
        "org.telegram.messenger" -> "Telegram"
        "com.facebook.orca" -> "Messenger"
        "org.thoughtcrime.securesms" -> "Signal"
        "com.google.android.apps.messaging", "com.samsung.android.messaging" -> "Messages"
        else -> "Chat"
    }

    /**
     * A music app is open with nothing playing: the glance shows its idle card (Play, Search, your
     * recent songs from it) instead of the generic one.
     */
    fun musicIdle(kind: AppKinds.Kind?, playing: Boolean): Boolean = kind?.plays == true && !playing
}

/**
 * How a game is treating the phone, in plain words, for the game's glance. Android doesn't let one
 * app read another's frame rate (not possible without root), so this is what the phone itself
 * reports: battery, its temperature, how hot Android says it's running, and how long you've played.
 */
object GameStats {
    /** Android's PowerManager thermal status (0 none .. 6 shutdown) in words. */
    fun heat(status: Int): String = when (status) {
        0 -> "Cool"
        1 -> "Warm"
        2 -> "Hot"
        3 -> "Very hot"
        4, 5, 6 -> "Too hot"
        else -> "Normal"
    }

    /** Hot enough that a game will likely slow itself down (Android's "moderate" and up). */
    fun throttling(status: Int): Boolean = status >= 2

    /** "38.5°C" from the battery's temperature in tenths of a degree, or null when unknown. */
    fun temperature(tenths: Int?): String? =
        tenths?.takeIf { it in -400..1200 }?.let { "%.1f°C".format(java.util.Locale.ROOT, it / 10f) }

    /** "Playing for 42 min", "Playing for 1 h 05 min", or "Just started". */
    fun played(ms: Long): String {
        val min = ms / 60_000L
        if (min < 1) return "Just started"
        if (min < 60) return "Playing for $min min"
        val h = min / 60; val m = min % 60
        return if (m == 0L) "Playing for $h h" else "Playing for $h h %02d min".format(java.util.Locale.ROOT, m)
    }

    /** The one line under the game's name: "Cool · 36.2°C · 64%". */
    fun line(status: Int, tenths: Int?, battery: Int?): String =
        listOfNotNull(heat(status), temperature(tenths), battery?.let { "$it%" }).joinToString(" · ")
}

/**
 * The songs you played lately in each music app (newest first, a few per app), so a music app
 * open with nothing playing can offer them again. Only the song's name and artist are kept, on the
 * phone, from what the music app already shows in its notification.
 */
object MusicMemory {
    const val PER_APP = 4
    private const val SEP = '\u001F'
    private const val ROW = '\u001E'

    data class Song(val title: String, val artist: String?)

    /** [song] goes to the front, once. */
    fun add(list: List<Song>, song: Song): List<Song> =
        (listOf(song) + list.filter { !it.title.equals(song.title, ignoreCase = true) || it.artist != song.artist }).take(PER_APP)

    fun encode(list: List<Song>): String = list.joinToString(ROW.toString()) { it.title + SEP + (it.artist ?: "") }

    fun decode(s: String?): List<Song> = s.orEmpty().split(ROW).mapNotNull { row ->
        val parts = row.split(SEP)
        val title = parts.getOrNull(0)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        Song(title, parts.getOrNull(1)?.takeIf { it.isNotBlank() })
    }.take(PER_APP)

    private fun key(pkg: String) = "glint_recent_songs_$pkg"
    private fun prefs(c: android.content.Context) = c.applicationContext.getSharedPreferences("music_memory", android.content.Context.MODE_PRIVATE)

    /** Remembers [title] by [artist] for [pkg] (cheap; skips when it's already the newest). */
    fun saw(c: android.content.Context, pkg: String?, title: String?, artist: String?) {
        if (pkg.isNullOrBlank() || title.isNullOrBlank()) return
        val p = prefs(c)
        val now = decode(p.getString(key(pkg), null))
        val song = Song(title, artist)
        if (now.firstOrNull() == song) return
        p.edit().putString(key(pkg), encode(add(now, song))).apply()
    }

    fun recent(c: android.content.Context, pkg: String): List<Song> = decode(prefs(c).getString(key(pkg), null))
}
