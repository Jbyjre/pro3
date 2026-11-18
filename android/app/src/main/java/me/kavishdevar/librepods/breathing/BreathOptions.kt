package me.kavishdevar.librepods.breathing

import androidx.core.graphics.ColorUtils

/** How the pause leaves, revealing the app that has been loading underneath. */
enum class BreathTransition(val key: String, val label: String, val note: String) {
    Fade("fade", "Fade", "Dissolves into your app"),
    Lift("lift", "Lift", "Rises away like a curtain"),
    Instant("instant", "Instant", "Cuts straight to your app");

    companion object {
        fun from(key: String?): BreathTransition = entries.firstOrNull { it.key == key } ?: Fade
    }
}

/**
 * Everything the user can change about a pause. The pause length is the WHOLE run: from the layer
 * covering the app to the app being fully visible again, including the exit transition.
 */
data class BreathOptions(
    val palette: BreathPalette = BreathPalette.Electric,
    val still: Boolean = false,
    val durationMs: Long = DEFAULT_MS,
    val transition: BreathTransition = BreathTransition.Fade,
    val words: BreathWords = BreathWords.Mix,
    val haptics: Boolean = true,
    val showAppName: Boolean = false,
    /** A chosen app pauses again only after this many minutes. 0 means every time it opens. */
    val graceMinutes: Int = DEFAULT_GRACE_MINUTES,
) {
    val graceMs: Long get() = graceMinutes.coerceAtLeast(0) * 60_000L
    val seconds: Float get() = durationMs / 1000f

    companion object {
        const val DEFAULT_MS = 3_000L
        const val MIN_MS = 2_000L
        const val MAX_MS = 10_000L
        const val DEFAULT_GRACE_MINUTES = 10
        val GRACE_CHOICES = listOf(0, 5, 10, 30, 60)
        fun clampDuration(ms: Long): Long = ms.coerceIn(MIN_MS, MAX_MS)
    }
}

/** Stored keys stay stable so existing color selections survive this visual revision. */
enum class BreathPalette(
    val key: String,
    val label: String,
    val primary: Int,
    val middle: Int,
    val secondary: Int,
    val ink: Int,
) {
    Electric("electric", "Cobalt", 0xff1739dd.toInt(), 0xff2851ee.toInt(), 0xff4264ec.toInt(), 0xfffffaef.toInt()),
    Coral("coral", "Tangerine", 0xfff45a36.toInt(), 0xffff8947.toInt(), 0xffffbb70.toInt(), 0xff351b12.toInt()),
    Lagoon("lagoon", "Pool", 0xff32c9ae.toInt(), 0xff69dfba.toInt(), 0xffb6edbf.toInt(), 0xff073c36.toInt());

    val darkInk: Boolean = ColorUtils.calculateLuminance(ink) < 0.5

    // Sample perceptually uniform Lab once, not on the frame path. Native Canvas joins the samples.
    val gradientColors: IntArray by lazy {
        val low = DoubleArray(3).also { ColorUtils.colorToLAB(primary, it) }
        val mid = DoubleArray(3).also { ColorUtils.colorToLAB(middle, it) }
        val high = DoubleArray(3).also { ColorUtils.colorToLAB(secondary, it) }
        val sample = DoubleArray(3)
        IntArray(33) { index ->
            val t = index / 16.0
            if (t <= 1.0) ColorUtils.blendLAB(low, mid, t, sample)
            else ColorUtils.blendLAB(mid, high, t - 1.0, sample)
            ColorUtils.LABToColor(sample[0], sample[1], sample[2])
        }
    }

    /**
     * The same hue, much darker. The pause covers the app in this the instant it appears, then the
     * bright gradient rises through it, so there is never a flash of an unrelated dark color.
     * Blending toward black keeps it inside what a screen can show. (Scaling lightness in Lab while
     * keeping saturation pushed Cobalt and Tangerine out of range and shifted Tangerine toward red.)
     */
    val deep: Int by lazy { ColorUtils.blendARGB(primary, 0xff000000.toInt(), DEEP_BLEND) }

    companion object {
        const val BASE: Int = 0xff14191f.toInt()
        const val BASE_INK: Int = 0xfffffaef.toInt()
        private const val DEEP_BLEND = 0.78f
        fun from(key: String?): BreathPalette = entries.firstOrNull { it.key == key } ?: Electric
    }
}
