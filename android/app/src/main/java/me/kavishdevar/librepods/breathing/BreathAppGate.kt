package me.kavishdevar.librepods.breathing

/**
 * Decides whether an app opening deserves a pause. Pure logic with no Android in it, so every rule
 * can be tested exactly.
 *
 * Two memories, kept apart on purpose:
 *  - the app in front, so keyboards, dialogs and a second screen of the same app never count;
 *  - when each app last paused, so moving between apps you already opened does not pause again until
 *    the grace time has passed. Looking this up costs nothing: it happens only when a window opens,
 *    with no timer and no polling, so it uses no battery.
 */
class BreathAppGate {
    private var foreground: String? = null
    private var shortcutPackage: String? = null
    private var shortcutUntil = 0L
    private val lastPause = HashMap<String, Long>()

    /** Called with the app and the wall-clock time each time a pause really starts, so it can be kept. */
    var onPause: ((String, Long) -> Unit)? = null

    fun restore(saved: Map<String, Long>) { lastPause.putAll(saved) }

    /** A pause drawn by a pinned shortcut counts as this app's pause, and hands over without a second layer. */
    fun shortcutCompleted(pkg: String, now: Long, wallNow: Long = System.currentTimeMillis()) {
        shortcutPackage = pkg
        shortcutUntil = now + SHORTCUT_HANDOFF_MS
        recordPause(pkg, wallNow)
    }

    /**
     * [now] is the monotonic clock (for the short shortcut hand-off); [wallNow] is the wall clock, which
     * survives a restart of the service. A clock set backwards counts as "long ago", never as "never again".
     */
    fun enter(pkg: String, selected: Set<String>, now: Long, wallNow: Long = System.currentTimeMillis(), graceMs: Long = 0L): Boolean {
        if (pkg == foreground) return false
        foreground = pkg
        val fromShortcut = pkg == shortcutPackage && now < shortcutUntil
        shortcutPackage = null
        if (pkg !in selected || fromShortcut) return false
        return !withinGrace(pkg, wallNow, graceMs)
    }

    fun withinGrace(pkg: String, wallNow: Long, graceMs: Long): Boolean {
        val last = lastPause[pkg] ?: return false
        return graceMs > 0 && wallNow - last in 0 until graceMs
    }

    /** The layer really appeared. Only now does the app count as paused: a failed attempt must not use it up. */
    fun recordPause(pkg: String, wallNow: Long) {
        lastPause[pkg] = wallNow
        onPause?.invoke(pkg, wallNow)
    }

    /** Something that is not a chosen app took the screen (a web app, say). Coming back counts as new. */
    fun leave(marker: String) { foreground = marker }

    /** A failed presentation must not consume this entry; retry on the next real event. */
    fun retry(pkg: String) { if (foreground == pkg) foreground = null }

    /** Forgets what is in front. It deliberately keeps when each app last paused. */
    fun reset() { foreground = null; shortcutPackage = null }

    /** Forgets everything, including when each app last paused. */
    fun forgetPauses() { lastPause.clear() }

    companion object { const val SHORTCUT_HANDOFF_MS = 5_000L }
}
