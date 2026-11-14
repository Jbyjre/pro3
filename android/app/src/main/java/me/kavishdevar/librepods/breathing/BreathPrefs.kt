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
}
