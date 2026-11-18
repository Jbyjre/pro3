package me.kavishdevar.librepods.breathing

/**
 * The whole pause as pure math, so the phone, the preview and the tests all agree.
 *
 * One breath, laid over the run:
 *  - the first half is the inhale: the color rises through the deep base;
 *  - the second half is the exhale: the words change and the layer clears by the chosen transition,
 *    finishing exactly when the run ends, so the app is never revealed with a hard cut.
 * Time is a fraction of the chosen length (0..1), not milliseconds, so any length keeps the same shape.
 */
class BreathTimeline(val durationMs: Long, val transition: BreathTransition, val reduced: Boolean) {
    enum class Phase { In, Out }

    /** How far through the run [elapsedMs] is. */
    fun fraction(elapsedMs: Long): Float = (elapsedMs / durationMs.toFloat()).coerceIn(0f, 1f)

    fun phase(t: Float): Phase = if (t < EXHALE_AT) Phase.In else Phase.Out

    /** 0 = only the deep base, 1 = the gradient fills the screen. Without motion it is full from the start. */
    fun sheet(t: Float): Float = if (reduced) 1f else smoother((t / EXHALE_AT).coerceIn(0f, 1f))

    /** 0..1 progress of the exit (the exhale), eased both ways. */
    fun exit(t: Float): Float = easeInOut(((t - EXIT_FROM) / (1f - EXIT_FROM)).coerceIn(0f, 1f))

    /** 1 = fully there, 0 = gone. Only Fade (and Lift when motion is off) clears by opacity. */
    fun layerAlpha(t: Float): Float = when {
        transition == BreathTransition.Fade || (transition == BreathTransition.Lift && reduced) -> 1f - exit(t)
        else -> 1f
    }

    /** 0..1 how far the curtain has risen. Lift only, and only when motion is on. */
    fun lift(t: Float): Float = if (transition == BreathTransition.Lift && !reduced) exit(t) else 0f

    /** The next piece of text for a phase: where it is and how visible, in line heights. */
    data class TextState(val alpha: Float, val rise: Float)

    /** A headline or note that arrives with the phase it belongs to. */
    fun arriving(elapsedMs: Long, phase: Phase, delayMs: Long = 0L): TextState {
        val begin = (if (phase == Phase.In) 0L else switchMs() + OUT_DELAY_MS) + delayMs
        val p = ((elapsedMs - begin) / ARRIVE_MS.toFloat()).coerceIn(0f, 1f)
        val e = easeOut(p)
        return TextState(e, if (reduced) 0f else (1f - e) * ARRIVE_RISE)
    }

    /** The first phase's text leaving as the second phase begins. */
    fun leaving(elapsedMs: Long, delayMs: Long = 0L): TextState {
        val p = ((elapsedMs - switchMs() - delayMs) / LEAVE_MS.toFloat()).coerceIn(0f, 1f)
        val e = easeIn(p)
        return TextState(1f - e, if (reduced) 0f else -e * LEAVE_RISE)
    }

    /** When the words change. */
    fun switchMs(): Long = (durationMs * EXHALE_AT).toLong()

    companion object {
        const val EXHALE_AT = 0.5f
        /** The exit starts just after the words change, so "Breathe out." is read before the layer clears. */
        const val EXIT_FROM = 0.52f
        /** The soft tick when the words change, and the confirming tick just before the end. */
        const val FINISH_CUE_AT = 0.9f
        const val ARRIVE_MS = 340L
        const val LEAVE_MS = 200L
        const val OUT_DELAY_MS = 120L
        const val NOTE_DELAY_MS = 90L
        private const val ARRIVE_RISE = 0.30f
        private const val LEAVE_RISE = 0.18f

        /** Perlin's smootherstep: zero slope and zero curvature at both ends, so it starts and lands softly. */
        fun smoother(x: Float): Float = x * x * x * (x * (x * 6f - 15f) + 10f)
        fun easeInOut(x: Float): Float = if (x < 0.5f) 4f * x * x * x else 1f - Math.pow((-2.0 * x + 2.0), 3.0).toFloat() / 2f
        fun easeOut(x: Float): Float = 1f - (1f - x) * (1f - x) * (1f - x)
        fun easeIn(x: Float): Float = x * x * x
    }
}
