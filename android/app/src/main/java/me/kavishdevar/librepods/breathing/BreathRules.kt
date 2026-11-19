package me.kavishdevar.librepods.breathing

import org.json.JSONObject

/**
 * A rule for one app, laid over the general settings. Anything left empty follows the general setting.
 * This is how a few apps are made harder to open than the rest.
 */
data class AppRule(val mode: PauseMode? = null, val durationMs: Long? = null) {
    val isDefault: Boolean get() = mode == null && durationMs == null
}

object BreathRules {
    fun encode(rules: Map<String, AppRule>): String {
        val out = JSONObject()
        for ((pkg, rule) in rules) {
            if (rule.isDefault) continue
            out.put(pkg, JSONObject().apply {
                rule.mode?.let { put("mode", it.key) }
                rule.durationMs?.let { put("ms", BreathOptions.clampAppDuration(it)) }
            })
        }
        return out.toString()
    }

    /** Bad or missing text is no rules at all, never a crash. */
    fun decode(json: String?): Map<String, AppRule> {
        if (json.isNullOrBlank()) return emptyMap()
        return runCatching {
            val root = JSONObject(json)
            buildMap {
                for (pkg in root.keys()) {
                    val o = root.optJSONObject(pkg) ?: continue
                    val rule = AppRule(PauseMode.from(o.optString("mode", "")), if (o.has("ms")) BreathOptions.clampAppDuration(o.optLong("ms")) else null)
                    if (!rule.isDefault) put(pkg, rule)
                }
            }
        }.getOrDefault(emptyMap())
    }
}
