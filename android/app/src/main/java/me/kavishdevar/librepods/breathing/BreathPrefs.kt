package me.kavishdevar.librepods.breathing

import android.content.Context

object BreathPrefs {
    fun prefs(c: Context) = c.getSharedPreferences("breathing", Context.MODE_PRIVATE)
    fun selected(c: Context): Set<String> = prefs(c).getStringSet("apps", emptySet())?.toSet().orEmpty()
    fun select(c: Context, pkg: String, on: Boolean) {
        val selected = selected(c)
        prefs(c).edit().putStringSet("apps", if (on) selected + pkg else selected - pkg).apply()
    }
    fun options(c: Context): BreathOptions {
        val values = prefs(c).all
        return BreathOptions(BreathPalette.from(values["palette"] as? String), values["still"] as? Boolean ?: false)
    }
    fun setOptions(c: Context, value: BreathOptions) {
        prefs(c).edit().putString("palette", value.palette.key).putBoolean("still", value.still).apply()
    }
}
