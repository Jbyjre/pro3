package me.kavishdevar.librepods.services

import android.content.Context

/** Retire camera-only preferences once. Headphone and temporary-pop-up preferences are preserved. */
object FeatureMigration {
    fun retireCamera(c: Context) {
        val p = c.getSharedPreferences("settings", Context.MODE_PRIVATE)
        if (p.getBoolean("breathing_migration_v1", false)) return
        val prefixes = listOf("glint_mini_", "glint_di_", "glint_sound_", "glint_msg_", "glint_tap_")
        val exact = setOf("glint_read_screen", "glint_live_apps", "glint_skip_in_use", "glint_phone_moments", "glint_name_linger", "asked_hear_music")
        val edit = p.edit()
        p.all.keys.filter { key -> prefixes.any(key::startsWith) || key in exact }.forEach { edit.remove(it) }
        edit.putBoolean("breathing_migration_v1", true).apply()
    }
}
