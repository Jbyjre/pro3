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

import android.content.SharedPreferences

/**
 * What the Dynamic Island shows (Settings > Islands): for each situation, what goes left and
 * right of the camera, plus its size, width and glow. Stored in the "settings" preferences, so
 * choices survive updates and restarts, and read live, so they apply at once. The defaults are
 * exactly how it looked before customisation existed. Pure apart from reading/writing prefs.
 */
object IslandLook {
    enum class Situation(val key: String, val label: String) {
        Music("music", "Playing"),
        Paused("paused", "Paused"),
        Idle("idle", "AirPods"),
        Charging("charging", "Charging"),
        Talking("talking", "Talking"),
    }

    enum class Slot(val key: String, val label: String) {
        /** The song's cover with a thin ring showing how far through the song you are. */
        Cover("cover", "Cover"),
        /** Four sound bars in the cover's colour (flat dots while paused). */
        Bars("bars", "Sound bars"),
        /** The buds' battery as a ring with the number. */
        Battery("battery", "Battery"),
        /** Left, right and case as three small rings. */
        Buds("buds", "L · R · case"),
        /** The listening mode's symbol. */
        Mode("mode", "Mode"),
        /** Your heart rate when there's a real reading; otherwise the listening mode. */
        Heart("heart", "Heart"),
        /** The first few words of the song's title. */
        Title("title", "Song title"),
        /** Three soft dots that ripple like speech. */
        Talk("talk", "Talking dots"),
        /** Talking only: whatever the situation underneath shows there. */
        Same("same", "Keep"),
        Nothing("none", "Nothing"),
    }

    enum class Size(val label: String, val factor: Float) { Small("Small", 0.88f), Normal("Normal", 1f), Large("Large", 1.14f) }

    /** The space between the camera and what's beside it, in dp. */
    enum class Width(val label: String, val gapDp: Float) { Snug("Snug", 1f), Normal("Normal", 3f), Roomy("Roomy", 8f) }

    /** How strongly its rim catches the light (and how light the idle tone is). */
    enum class Glow(val label: String, val amount: Float) { Off("Off", 0f), Soft("Soft", 1f), Bright("Bright", 2f) }

    data class Look(
        val slots: Map<Situation, Pair<Slot, Slot>> = DEFAULT_SLOTS,
        val size: Size = Size.Normal,
        val width: Width = Width.Normal,
        val glow: Glow = Glow.Soft,
    ) {
        /** Left and right for [s], with "Keep" resolved against [under] (the situation without talking). */
        fun slots(s: Situation, under: Situation = s): Pair<Slot, Slot> {
            val (l, r) = slots[s] ?: DEFAULT_SLOTS.getValue(s)
            val (ul, ur) = slots[under] ?: DEFAULT_SLOTS.getValue(under)
            fun keep(x: Slot, fallback: Slot) = if (x == Slot.Same) (if (fallback == Slot.Same) Slot.Nothing else fallback) else x
            return keep(l, ul) to keep(r, ur)
        }

        val isDefault: Boolean get() = this == Look()
    }

    val DEFAULT_SLOTS: Map<Situation, Pair<Slot, Slot>> = mapOf(
        Situation.Music to (Slot.Cover to Slot.Bars),
        Situation.Paused to (Slot.Cover to Slot.Bars),
        Situation.Idle to (Slot.Battery to Slot.Heart),
        Situation.Charging to (Slot.Battery to Slot.Heart),
        Situation.Talking to (Slot.Same to Slot.Talk),
    )

    /** What can go on a side in a situation: "Keep" only while talking. */
    fun options(s: Situation): List<Slot> = Slot.entries.filter { it != Slot.Same || s == Situation.Talking }

    /**
     * The situation right now. Talking (Conversation Awareness has the music down) wins; then
     * music playing or paused; then the AirPods themselves, charging or not.
     */
    fun situation(music: Boolean, playing: Boolean, talking: Boolean, charging: Boolean): Situation = when {
        talking -> Situation.Talking
        music && playing -> Situation.Music
        music -> Situation.Paused
        charging -> Situation.Charging
        else -> Situation.Idle
    }

    /** The same, ignoring talking: what "Keep" refers to. */
    fun underneath(music: Boolean, playing: Boolean, charging: Boolean): Situation = situation(music, playing, false, charging)

    // ---- Storage ----

    private fun key(s: Situation, left: Boolean) = "glint_di_slot_${s.key}_${if (left) "l" else "r"}"
    const val PREF_SIZE = "glint_di_size"
    const val PREF_WIDTH = "glint_di_width"
    const val PREF_GLOW = "glint_di_glow"

    /** Every preference key this look uses (to react to changes). */
    val keys: Set<String> = Situation.entries.flatMap { listOf(key(it, true), key(it, false)) }.toSet() + setOf(PREF_SIZE, PREF_WIDTH, PREF_GLOW)

    fun read(prefs: SharedPreferences): Look {
        val slots = Situation.entries.associateWith { s ->
            val (dl, dr) = DEFAULT_SLOTS.getValue(s)
            fun slot(left: Boolean, d: Slot) = Slot.entries.firstOrNull { it.key == prefs.getString(key(s, left), null) }
                ?.takeIf { it in options(s) } ?: d
            slot(true, dl) to slot(false, dr)
        }
        return Look(
            slots = slots,
            size = Size.entries.firstOrNull { it.name == prefs.getString(PREF_SIZE, null) } ?: Size.Normal,
            width = Width.entries.firstOrNull { it.name == prefs.getString(PREF_WIDTH, null) } ?: Width.Normal,
            glow = Glow.entries.firstOrNull { it.name == prefs.getString(PREF_GLOW, null) } ?: Glow.Soft,
        )
    }

    fun setSlot(prefs: SharedPreferences, s: Situation, left: Boolean, slot: Slot) {
        prefs.edit().putString(key(s, left), slot.key).apply()
    }

    fun setSize(prefs: SharedPreferences, v: Size) = prefs.edit().putString(PREF_SIZE, v.name).apply()
    fun setWidth(prefs: SharedPreferences, v: Width) = prefs.edit().putString(PREF_WIDTH, v.name).apply()
    fun setGlow(prefs: SharedPreferences, v: Glow) = prefs.edit().putString(PREF_GLOW, v.name).apply()

    /** Back to how it came: every slot, size, width and glow (gestures have their own reset). */
    fun reset(prefs: SharedPreferences) {
        prefs.edit().apply { keys.forEach { remove(it) } }.apply()
    }

    // ---- Sizes (pixels) ----

    /** How wide a slot is, for a pill whose round items are [side] across. */
    fun slotWidth(slot: Slot, side: Float, density: Float, factor: Float): Float = when (slot) {
        Slot.Nothing, Slot.Same -> 0f
        Slot.Title -> 74f * density * factor
        Slot.Buds -> side * 2.3f
        else -> side
    }
}
