package me.kavishdevar.librepods.breathing

import android.content.Context

object BreathPrefs {
    fun prefs(c: Context) = c.getSharedPreferences("breathing", Context.MODE_PRIVATE)

    /** Chosen apps. Installed web apps are never chosen apps, even if an old selection somehow holds one. */
    fun selected(c: Context): Set<String> =
        prefs(c).getStringSet("apps", emptySet())?.toSet().orEmpty().filterNot(BreathWebApps::isWebAppPackage).toSet()

    fun select(c: Context, pkg: String, on: Boolean) {
        val selected = selected(c)
        prefs(c).edit().putStringSet("apps", if (on && !BreathWebApps.isWebAppPackage(pkg)) selected + pkg else selected - pkg).apply()
    }

    fun options(c: Context): BreathOptions {
        val values = prefs(c).all
        val defaults = BreathOptions()
        return BreathOptions(
            palette = BreathPalette.from(values["palette"] as? String),
            still = values["still"] as? Boolean ?: defaults.still,
            durationMs = BreathOptions.clampDuration((values["duration_ms"] as? Number)?.toLong() ?: defaults.durationMs),
            transition = BreathTransition.from(values["transition"] as? String),
            words = BreathWords.from(values["words"] as? String),
            haptics = values["haptics"] as? Boolean ?: defaults.haptics,
            showAppName = values["show_name"] as? Boolean ?: defaults.showAppName,
            graceMinutes = ((values["grace_min"] as? Number)?.toInt() ?: defaults.graceMinutes).coerceIn(0, 24 * 60),
        )
    }

    fun setOptions(c: Context, value: BreathOptions) {
        prefs(c).edit()
            .putString("palette", value.palette.key)
            .putBoolean("still", value.still)
            .putLong("duration_ms", BreathOptions.clampDuration(value.durationMs))
            .putString("transition", value.transition.key)
            .putString("words", value.words.key)
            .putBoolean("haptics", value.haptics)
            .putBoolean("show_name", value.showAppName)
            .putInt("grace_min", value.graceMinutes.coerceIn(0, 24 * 60))
            .apply()
    }
}
