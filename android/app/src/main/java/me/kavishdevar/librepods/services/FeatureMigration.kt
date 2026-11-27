package me.kavishdevar.librepods.services

import android.content.Context

/** Retire camera-only preferences once. Headphone and temporary-pop-up preferences are preserved. */
object FeatureMigration {
    fun retireCamera(c: Context) {
        val p = c.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val edit = p.edit()
        var changed = false
        if (!p.getBoolean("breathing_migration_v1", false)) {
            val prefixes = listOf("glint_mini_", "glint_di_", "glint_sound_", "glint_msg_", "glint_tap_")
            val exact = setOf("glint_read_screen", "glint_live_apps", "glint_skip_in_use", "glint_phone_moments", "glint_name_linger", "asked_hear_music")
            p.all.keys.filter { key -> prefixes.any(key::startsWith) || key in exact }.forEach { edit.remove(it) }
            edit.putBoolean("breathing_migration_v1", true)
            changed = true
        }
        if (!p.getBoolean("case_popup_default_v1", false)) {
            edit.putBoolean(SmartAudioRules.PREF_CASE_POPUP, true)
            edit.putBoolean("case_popup_default_v1", true)
            changed = true
        }
        if (!p.getBoolean("breath_cooldown_15m_v1", false)) {
            val bp = me.kavishdevar.librepods.breathing.BreathPrefs.prefs(c)
            val cleanedRules = me.kavishdevar.librepods.breathing.BreathPrefs.appRules(c)
                .mapValues { (_, r) ->
                    if (r.mode == me.kavishdevar.librepods.breathing.PauseMode.EveryTime) r.copy(mode = null) else r
                }
                .filterValues { !it.isDefault }
            bp.edit()
                .putInt("cooldown_min", me.kavishdevar.librepods.breathing.BreathOptions.DEFAULT_GRACE_MINUTES)
                .putString("mode", me.kavishdevar.librepods.breathing.PauseMode.NewOpens.key)
                .putString("app_rules", me.kavishdevar.librepods.breathing.BreathRules.encode(cleanedRules))
                .remove("grace_min")
                .apply()
            edit.putBoolean("breath_cooldown_15m_v1", true)
            changed = true
        }
        if (changed) edit.apply()
    }
}
