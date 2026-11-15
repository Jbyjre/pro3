package me.kavishdevar.librepods.breathing

import android.content.Context

object BreathPrefs {
    fun prefs(c: Context) = c.getSharedPreferences("breathing", Context.MODE_PRIVATE)
    fun enabled(c: Context) = prefs(c).getBoolean("enabled", true)
    fun selected(c: Context): Set<String> = prefs(c).getStringSet("apps", emptySet())?.toSet().orEmpty()
    fun select(c: Context, pkg: String, on: Boolean) {
        val selected = selected(c)
        prefs(c).edit().putStringSet("apps", if (on) selected + pkg else selected - pkg).apply()
    }
    fun paused(c: Context) = prefs(c).getLong("paused_until", 0) > System.currentTimeMillis()
    fun options(c: Context): BreathOptions {
        val values = prefs(c).all
        return BreathOptions(BreathPalette.from(values["palette"] as? String), values["still"] as? Boolean ?: false)
    }
    fun setOptions(c: Context, value: BreathOptions) {
        prefs(c).edit().putString("palette", value.palette.key).putBoolean("still", value.still).apply()
    }
    fun setEnabled(c: Context, on: Boolean) {
        prefs(c).edit().putBoolean("enabled", on).apply {
            if (on) putLong("paused_until", 0L)
        }.apply()
    }

}
