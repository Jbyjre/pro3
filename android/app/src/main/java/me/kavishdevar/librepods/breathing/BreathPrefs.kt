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
        // Colours: a saved set wins. Otherwise everything colourful may appear, which is what randomized means.
        val palettes = (values["palettes"] as? Set<*>)?.mapNotNull { (it as? String)?.let { k -> BreathPalette.entries.firstOrNull { p -> p.key == k } } }
            ?.toSet()?.takeIf { it.isNotEmpty() } ?: defaults.palettes
        val savedCooldown = (values["cooldown_min"] as? Number)?.toInt()
        val savedGrace = (values["grace_min"] as? Number)?.toInt()
        // Before pause modes existed, "0 minutes" meant "every open". Keep that meaning when no cooldown was set.
        val legacyEvery = !values.containsKey("mode") && !values.containsKey("cooldown_min") && savedGrace == 0
        return BreathOptions(
            palettes = palettes,
            paletteOrder = PaletteOrder.from(values["palette_order"] as? String),
            still = values["still"] as? Boolean ?: defaults.still,
            durationMs = BreathOptions.clampDuration((values["duration_ms"] as? Number)?.toLong() ?: defaults.durationMs),
            transition = BreathTransition.from(values["transition"] as? String),
            words = BreathWords.from(values["words"] as? String),
            customText = BreathOptions.cleanCustom(values["custom_text"] as? String ?: ""),
            haptics = values["haptics"] as? Boolean ?: defaults.haptics,
            showAppName = values["show_name"] as? Boolean ?: defaults.showAppName,
            graceMinutes = (savedCooldown ?: defaults.graceMinutes).coerceIn(0, 24 * 60),
            mode = if (legacyEvery) PauseMode.EveryTime else PauseMode.from(values["mode"] as? String) ?: defaults.mode,
            gray = GrayMode.from(values["gray"] as? String),
        )
    }

    fun setOptions(c: Context, value: BreathOptions) {
        prefs(c).edit()
            .putStringSet("palettes", value.chosenPalettes.map { it.key }.toSet())
            .putString("palette_order", value.paletteOrder.key)
            .putBoolean("still", value.still)
            .putLong("duration_ms", BreathOptions.clampDuration(value.durationMs))
            .putString("transition", value.transition.key)
            .putString("words", value.words.key)
            .putString("custom_text", BreathOptions.cleanCustom(value.customText))
            .putBoolean("haptics", value.haptics)
            .putBoolean("show_name", value.showAppName)
            .putInt("cooldown_min", value.graceMinutes.coerceIn(0, 24 * 60))
            .remove("grace_min")
            .putString("mode", value.mode.key)
            .putString("gray", value.gray.key)
            .apply()
    }

    /** Per-app rules: how a few apps are made harder to open than the rest. */
    fun appRules(c: Context): Map<String, AppRule> = BreathRules.decode(prefs(c).getString("app_rules", null))

    fun setAppRule(c: Context, pkg: String, rule: AppRule) {
        val next = appRules(c).toMutableMap()
        if (rule.isDefault) next.remove(pkg) else next[pkg] = rule
        prefs(c).edit().putString("app_rules", BreathRules.encode(next)).apply()
    }
}
