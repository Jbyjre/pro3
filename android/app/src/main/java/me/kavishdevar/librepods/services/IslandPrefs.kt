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

import android.content.Context
import android.content.SharedPreferences

/**
 * When the island appears and how it behaves (Settings > Island). Each moment has its own
 * switch; the master switch ("show_island_popup", LibrePods' original key) turns all of them off.
 */
object IslandPrefs {
    const val PREF_MASTER = "show_island_popup"
    const val PREF_DURATION = "glint_island_duration"
    const val PREF_HAPTICS = "glint_island_haptics"
    const val PREF_SONG_NAMES = "glint_island_song_names"
    /** The mini island: a small pill around the front camera while something plays. */
    const val PREF_MINI = "glint_mini_island"
    /** Let the mini island widen for a moment to show each new song's name. */
    const val PREF_MINI_NAMES = "glint_mini_song_names"
    /** Only show the mini island while the AirPods are connected (off: for any audio). */
    const val PREF_MINI_AIRPODS_ONLY = "glint_mini_airpods_only"
    /** Keep the mini island up the whole time the AirPods are connected (not only for music). */
    const val PREF_MINI_ALWAYS = "glint_mini_always"
    /** Keep the mini island up all the time, even with nothing playing and nothing connected. */
    const val PREF_MINI_ANYTIME = "glint_mini_anytime"
    /** Pop the Dynamic Island for any sound (alerts, calls, voice notes), not only music. */
    const val PREF_MINI_ANY_SOUND = "glint_mini_any_sound"
    /** Show the app's icon for a sound (off: a symbol for the kind of sound instead). */
    const val PREF_SOUND_ICONS = "glint_sound_icons"
    /** How long a short sound keeps the Dynamic Island up after it ends. */
    const val PREF_SOUND_LINGER = "glint_sound_linger"
    /** Apps whose sounds never pop the Dynamic Island. */
    const val PREF_SOUND_IGNORED = "glint_sound_ignored"
    /** Apps the Dynamic Island hides in (games, video): it steps aside while they're in front. */
    const val PREF_HIDE_IN = "glint_di_hide_in"
    /** The app's icon swaps with a little pop as you change apps (off: a plain cross-fade). */
    const val PREF_APP_POP = "glint_di_app_pop"
    /** Show a moment when a message arrives (a text, a chat), with the app's icon and name. */
    const val PREF_MSG = "glint_msg_on"
    /** Also show moments for other notifications that alert (off: messages only). */
    const val PREF_MSG_OTHERS = "glint_msg_others"
    /** Show who the message is from (reads the notification's title; off: only the app's name). */
    const val PREF_MSG_SENDER = "glint_msg_sender"
    /** No message moments while Do Not Disturb would silence that notification. */
    const val PREF_MSG_DND = "glint_msg_dnd"
    /** No sound or message moments from the app you're using right now. */
    const val PREF_SKIP_IN_USE = "glint_skip_in_use"
    /** A moment when the phone starts charging, gets full, or runs low (with a line of words, like the iPhone's). */
    const val PREF_PHONE_MOMENTS = "glint_phone_moments"
    /** How long the song's name stays out under the camera. */
    const val PREF_NAME_LINGER = "glint_name_linger"
    /** How many times (and when last) the "make it tappable" pop-up was shown. */
    const val PREF_TAP_NUDGES = "glint_tap_nudges"
    const val PREF_TAP_NUDGE_AT = "glint_tap_nudge_at"

    enum class Trigger(val key: String, val default: Boolean, val label: String, val description: String) {
        Connected("glint_island_connect", true, "AirPods connect", "Once each time they connect."),
        InEar("glint_island_in_ear", true, "AirPods go in", "When you put them in your ears."),
        BudOut("glint_island_bud_out", true, "An AirPod comes out", "Shows that music paused, with a play button."),
        MusicStarts("glint_island_music", true, "Something starts playing", "Music or video starting on your AirPods, with a pause button."),
        SongChanges("glint_island_song", false, "Each new song", "Needs \"Show song names\"."),
        ListeningMode("glint_island_mode_changes", true, "Listening mode changes", "When you switch modes on the AirPods."),
        LowBattery("glint_island_low_battery", true, "Low battery", "At 20% and at 10%."),
        Charging("glint_island_charging", true, "Charging starts", "When the case or the buds start charging."),
        OtherDevice("glint_island_other_device", true, "Moved to another device", "When another device takes your AirPods."),
        HeartAlert(PREF_HR_ISLAND, true, "High heart rate", "When it goes above your alert limit."),
    }

    /** How long the island stays before it tucks away. */
    enum class Duration(val label: String, val compactMs: Long, val expandedMs: Long) {
        Short("Short", 2_500, 7_000),
        Normal("Normal", 4_000, 12_000),
        Long("Long", 7_000, 20_000),
    }

    fun prefs(context: Context): SharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun enabled(prefs: SharedPreferences, trigger: Trigger): Boolean =
        prefs.getBoolean(PREF_MASTER, true) && prefs.getBoolean(trigger.key, trigger.default)

    fun duration(prefs: SharedPreferences): Duration =
        Duration.entries.getOrNull(prefs.getInt(PREF_DURATION, Duration.Normal.ordinal)) ?: Duration.Normal

    fun haptics(prefs: SharedPreferences): Boolean = prefs.getBoolean(PREF_HAPTICS, true)

    /** Song names and the music app's own controls (needs Notification access). */
    fun songNames(prefs: SharedPreferences): Boolean = prefs.getBoolean(PREF_SONG_NAMES, true)

    fun mini(prefs: SharedPreferences): Boolean = prefs.getBoolean(PREF_MINI, true)
    fun miniNames(prefs: SharedPreferences): Boolean = prefs.getBoolean(PREF_MINI_NAMES, true)
    fun miniAirPodsOnly(prefs: SharedPreferences): Boolean = prefs.getBoolean(PREF_MINI_AIRPODS_ONLY, false)
    fun miniAlways(prefs: SharedPreferences): Boolean = prefs.getBoolean(PREF_MINI_ALWAYS, true)
    fun miniAnytime(prefs: SharedPreferences): Boolean = prefs.getBoolean(PREF_MINI_ANYTIME, true)
    fun anySound(prefs: SharedPreferences): Boolean = prefs.getBoolean(PREF_MINI_ANY_SOUND, true)
    fun messages(prefs: SharedPreferences): Boolean = prefs.getBoolean(PREF_MSG, true)
    fun messagesOthers(prefs: SharedPreferences): Boolean = prefs.getBoolean(PREF_MSG_OTHERS, false)
    fun messageSender(prefs: SharedPreferences): Boolean = prefs.getBoolean(PREF_MSG_SENDER, false)
    fun messagesRespectDnd(prefs: SharedPreferences): Boolean = prefs.getBoolean(PREF_MSG_DND, true)
    fun skipInUse(prefs: SharedPreferences): Boolean = prefs.getBoolean(PREF_SKIP_IN_USE, true)
    fun phoneMoments(prefs: SharedPreferences): Boolean = prefs.getBoolean(PREF_PHONE_MOMENTS, true)

    /** How long the song's name stays out under the camera. */
    enum class NameLinger(val label: String, val ms: Long) {
        Short("Short", 1_800L),
        Normal("Normal", 3_200L),
        Long("Long", 5_000L),
    }

    fun nameLinger(prefs: SharedPreferences): NameLinger =
        NameLinger.entries.getOrNull(prefs.getInt(PREF_NAME_LINGER, NameLinger.Normal.ordinal)) ?: NameLinger.Normal
    fun soundIcons(prefs: SharedPreferences): Boolean = prefs.getBoolean(PREF_SOUND_ICONS, true)

    fun soundLinger(prefs: SharedPreferences): SoundRules.Linger =
        SoundRules.Linger.entries.getOrNull(prefs.getInt(PREF_SOUND_LINGER, SoundRules.Linger.Normal.ordinal)) ?: SoundRules.Linger.Normal

    /** Packages switched off in Settings (a copy: the stored set must never be edited in place). */
    fun soundIgnored(prefs: SharedPreferences): Set<String> = prefs.getStringSet(PREF_SOUND_IGNORED, emptySet())?.toSet().orEmpty()

    fun hideIn(prefs: SharedPreferences): Set<String> = prefs.getStringSet(PREF_HIDE_IN, emptySet())?.toSet().orEmpty()

    fun setHideIn(prefs: SharedPreferences, pkg: String, hidden: Boolean) {
        val now = hideIn(prefs)
        prefs.edit().putStringSet(PREF_HIDE_IN, if (hidden) now + pkg else now - pkg).apply()
    }

    fun appPop(prefs: SharedPreferences): Boolean = prefs.getBoolean(PREF_APP_POP, true)

    fun setSoundIgnored(prefs: SharedPreferences, pkg: String, ignored: Boolean) {
        val now = soundIgnored(prefs)
        prefs.edit().putStringSet(PREF_SOUND_IGNORED, if (ignored) now + pkg else now - pkg).apply()
    }
}
