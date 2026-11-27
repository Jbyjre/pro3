package me.kavishdevar.librepods.breathing

/**
 * Decides whether an app coming to the front deserves a pause. Pure logic with no Android in it, so
 * every rule can be tested exactly.
 *
 * What it can and cannot know. Android never tells a service that is not reading the screen whether
 * an app was *closed* or only *switched away from*: the usage log hides the "activity destroyed"
 * event, and the window id that would show a brand-new screen is removed for services without
 * content access (both checked in Android's source on 19 November 2026). So it uses what it can see:
 *  - what was in front just before (the Home screen / Recents, another app, or a notification);
 *  - whether another app was in front since this one left;
 *  - how long it was away;
 *  - when it last paused.
 * "New opens" means: from Home (or Recents) after a moment with no other app in between, or from a
 * notification. Moving through another app and back does not pause. The one case it cannot catch:
 * closing an app, opening a different app, then going back to the first.
 *
 * It is only consulted when a window opens: no timer, no polling, so it uses no battery.
 */
class BreathAppGate {
    /** What took the front: an ordinary app, or the Home screen / Recents. */
    enum class Kind { App, Launcher }

    /** Plain-language reasons, shown in the on-phone decision log. */
    enum class Why(val text: String, val pauses: Boolean) {
        FirstOpen("first open of this app", true),
        FromHome("opened from Home or Recents", true),
        FromNotification("opened from a notification", true),
        NewSession("no memory of it leaving, treated as a fresh open", true),
        AwayLong("it has been a while since its last pause", true),
        EveryTime("this app pauses every time", true),
        Cooldown("cooling down after a recent pause", false),
        Switched("switched back through another app", false),
        QuickReturn("back from Home or Recents within a moment", false),
        AfterBreak("only pauses after a break", false),
        Handoff("the pinned shortcut already paused it", false),
        SameApp("already in front", false),
        NotChosen("not a chosen app", false),
    }

    data class Decision(val pause: Boolean, val why: Why)

    /** The rule for one app: its mode and the cooldown before another pause may open. */
    data class Rule(val mode: PauseMode = PauseMode.NewOpens, val graceMs: Long = BreathOptions().graceMs)

    private class Left(val at: Long, var sawApp: Boolean)

    private var foreground: String? = null
    private var foregroundKind = Kind.App
    private var shortcutPackage: String? = null
    private var shortcutUntil = 0L
    private var lastPauseAt: Long? = null
    private val lastPause = HashMap<String, Long>()
    private val left = HashMap<String, Left>()
    private var notificationAt = -1L
    private var screenOffAt = -1L
    private var savedLeft: Pair<String, Left>? = null
    private var savedPrevKind = Kind.App
    private var savedNotificationAt = -1L

    /** Called with the app and the wall-clock time each time a pause really starts, so it can be kept. */
    var onPause: ((String, Long) -> Unit)? = null

    fun restore(saved: Map<String, Long>, lastAny: Long? = null) {
        lastPause.putAll(saved)
        val latest = listOfNotNull(lastPauseAt, lastAny, saved.values.maxOrNull()).maxOrNull()
        if (latest != null) lastPauseAt = latest
    }

    /** True while the cooldown from the most recent pause is still running. */
    fun inCooldown(wallNow: Long, graceMs: Long): Boolean {
        if (graceMs <= 0L) return false
        val last = lastPauseAt ?: return false
        val since = wallNow - last
        // A clock set backwards (since < 0) counts as long ago, never locking out pauses forever.
        return since in 0 until graceMs
    }

    /** A pause drawn by a pinned shortcut counts as this app's pause, and hands over without a second layer. */
    fun shortcutCompleted(pkg: String, now: Long, wallNow: Long = System.currentTimeMillis()) {
        shortcutPackage = pkg
        shortcutUntil = now + SHORTCUT_HANDOFF_MS
        recordPause(pkg, wallNow)
    }

    /**
     * [pkg] is coming to the front. [now] is the monotonic clock; [wallNow] is the wall clock, which survives
     * a restart of the service. A clock set backwards counts as "long ago", never as "never again".
     */
    fun enter(pkg: String, kind: Kind, selected: Set<String>, now: Long, wallNow: Long = System.currentTimeMillis(), rule: Rule = Rule()): Decision {
        val chosen = kind == Kind.App && pkg in selected
        if (pkg == foreground) {
            // Same app, so normally nothing. The exception is waking the phone after a long time away,
            // provided any cooldown from a recent pause has also finished.
            val wasOff = screenOffAt >= 0
            val away = if (wasOff) now - screenOffAt else 0L
            screenOffAt = -1L
            if (!chosen || !wasOff) return Decision(false, Why.SameApp)
            if (rule.mode == PauseMode.EveryTime) return Decision(true, Why.EveryTime)
            if (rule.graceMs > 0 && away >= rule.graceMs && !inCooldown(wallNow, rule.graceMs)) {
                return Decision(true, Why.AwayLong)
            }
            return Decision(false, Why.SameApp)
        }
        val prev = foreground
        val prevKind = foregroundKind
        val fromNotification = notificationAt >= 0 && now - notificationAt <= NOTIFICATION_MS
        val away = left[pkg]
        val decision = if (!chosen) Decision(false, Why.NotChosen) else evaluate(pkg, now, wallNow, rule, prevKind, fromNotification, away)

        // Bookkeeping: the app that was in front has now left; every app that is away has now seen an app.
        if (prev != null && prevKind == Kind.App) left[prev] = Left(now, false)
        if (kind == Kind.App) for ((p, l) in left) if (p != pkg) l.sawApp = true
        savedPrevKind = prevKind; savedNotificationAt = notificationAt
        foreground = pkg; foregroundKind = kind
        notificationAt = -1L; screenOffAt = -1L
        savedLeft = null
        if (decision.pause) { away?.let { savedLeft = pkg to it }; left.remove(pkg) }
        if (decision.why == Why.Handoff) shortcutPackage = null
        return decision
    }

    private fun evaluate(pkg: String, now: Long, wallNow: Long, rule: Rule, prevKind: Kind, fromNotification: Boolean, away: Left?): Decision {
        if (pkg == shortcutPackage && now < shortcutUntil) return Decision(false, Why.Handoff)
        if (rule.mode == PauseMode.EveryTime) return Decision(true, Why.EveryTime)
        // Cooldown: once a pause has opened, wait for the cooldown (15 min by default) before opening again.
        if (rule.graceMs > 0 && inCooldown(wallNow, rule.graceMs)) return Decision(false, Why.Cooldown)
        val last = lastPause[pkg] ?: return Decision(true, Why.FirstOpen)
        val since = wallNow - last
        if (rule.mode != PauseMode.AfterBreak) {
            if (fromNotification) return Decision(true, Why.FromNotification)
            if (away == null) return Decision(true, Why.NewSession)
            if (!away.sawApp && prevKind == Kind.Launcher) {
                return if (now - away.at >= HOME_GAP_MS) Decision(true, Why.FromHome) else Decision(false, Why.QuickReturn)
            }
        }
        if (rule.graceMs > 0 && (since < 0 || since >= rule.graceMs)) return Decision(true, Why.AwayLong)
        if (rule.mode == PauseMode.AfterBreak) return Decision(false, Why.AfterBreak)
        return Decision(false, Why.Switched)
    }

    /** The notification shade or a heads-up appeared. A chosen app opening right after counts as opened from it. */
    fun notificationWindow(now: Long) { notificationAt = now }

    /** The phone went to sleep. The app in front stays in front; waking later is judged by how long it was off. */
    fun screenOff(now: Long) { screenOffAt = now }

    /** The layer really appeared. Only now does the app count as paused: a failed attempt must not use up its turn. */
    fun recordPause(pkg: String, wallNow: Long) {
        lastPause[pkg] = wallNow
        lastPauseAt = wallNow
        onPause?.invoke(pkg, wallNow)
    }

    /** A failed presentation must not consume this entry: put things back so the next real event decides again. */
    fun retry(pkg: String) {
        if (foreground != pkg) return
        foreground = null
        foregroundKind = savedPrevKind
        notificationAt = savedNotificationAt
        savedLeft?.takeIf { it.first == pkg }?.let { left[pkg] = it.second }
        savedLeft = null
    }

    /** Something that is not a chosen app took the screen (a web app, say). Coming back counts as new. */
    fun leave(marker: String) {
        val prev = foreground
        if (prev != null && foregroundKind == Kind.App && prev != marker) left[prev] = Left(0L, true)
        foreground = marker; foregroundKind = Kind.App
    }

    /** Forgets what is in front and who has been away. It deliberately keeps when each app last paused. */
    fun reset() {
        foreground = null; foregroundKind = Kind.App
        shortcutPackage = null
        left.clear(); notificationAt = -1L; screenOffAt = -1L; savedLeft = null
    }

    /** Forgets everything, including when each app last paused. */
    fun forgetPauses() { lastPause.clear(); lastPauseAt = null }

    /** The package currently in front as far as the gate knows (a launcher counts), or null. */
    fun frontPackage(): String? = foreground

    /** For tests and the log: when this app last paused, or null. */
    fun lastPauseOf(pkg: String): Long? = lastPause[pkg]

    /** When any app most recently paused, or null. */
    fun lastPauseTime(): Long? = lastPauseAt

    companion object {
        const val SHORTCUT_HANDOFF_MS = 5_000L
        /** Coming back from Home or Recents faster than this is a peek, not a new open. */
        const val HOME_GAP_MS = 1_200L
        /** An app opening this soon after the notification shade appeared was opened from it. */
        const val NOTIFICATION_MS = 4_000L
    }
}
