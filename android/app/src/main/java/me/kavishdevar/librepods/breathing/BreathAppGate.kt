package me.kavishdevar.librepods.breathing

/** Package transitions, not individual activities: keyboards/dialogs never restart the pause. */
class BreathAppGate {
    private var foreground: String? = null
    private var shortcutPackage: String? = null
    private var shortcutUntil = 0L

    fun shortcutCompleted(pkg: String, now: Long) {
        shortcutPackage = pkg
        shortcutUntil = now + 5_000L
    }

    fun enter(pkg: String, selected: Set<String>, now: Long): Boolean {
        if (pkg == foreground) return false
        foreground = pkg
        val fromShortcut = pkg == shortcutPackage && now < shortcutUntil
        shortcutPackage = null
        return pkg in selected && !fromShortcut
    }

    fun reset() { foreground = null; shortcutPackage = null }
}
