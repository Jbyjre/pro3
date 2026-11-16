package me.kavishdevar.librepods.breathing

/** One monotonic deadline; frames and Android animation scales never control the wait. */
class BreathSession {
    var startedAt: Long? = null
        private set
    var cancelled = false
        private set
    private var delivered = false
    fun firstFrame(now: Long) { if (startedAt == null) startedAt = now }
    fun remaining(now: Long): Long = startedAt?.let { (DURATION_MS - (now - it)).coerceIn(0, DURATION_MS) } ?: DURATION_MS
    fun complete(now: Long): Boolean {
        if (cancelled || delivered || startedAt == null || remaining(now) > 0) return false
        delivered = true
        return true
    }
    fun cancel() { cancelled = true }
    companion object { const val DURATION_MS = 2_500L }
}

/** Shared ownership prevents headphone pop-ups or the two launch routes from stacking. */
object BreathInteraction {
    @Volatile var cancel: (() -> Unit)? = null
    val active: Boolean get() = cancel != null
}
