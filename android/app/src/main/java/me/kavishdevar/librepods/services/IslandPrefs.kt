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
}
