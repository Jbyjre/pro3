package me.kavishdevar.librepods.breathing

import androidx.core.graphics.ColorUtils
import kotlin.random.Random

/** How the pause leaves, revealing the app that has been loading underneath. */
enum class BreathTransition(val key: String, val label: String, val note: String) {
    Fade("fade", "Fade", "Dissolves into your app"),
    Lift("lift", "Lift", "Rises away like a curtain"),
    Drop("drop", "Drop", "Falls away like a curtain"),
    Iris("iris", "Iris", "Opens like a camera lens"),
    Slide("slide", "Slide", "Slides off to the side"),
    Zoom("zoom", "Zoom", "Swells and clears"),
    Instant("instant", "Instant", "Cuts straight to your app");

    companion object {
        fun from(key: String?): BreathTransition = entries.firstOrNull { it.key == key } ?: Fade
    }
}

/** Which colour comes next when several are chosen. */
enum class PaletteOrder(val key: String, val label: String) {
    Random("random", "Random"),
    InOrder("order", "In order");

    companion object { fun from(key: String?): PaletteOrder = entries.firstOrNull { it.key == key } ?: Random }
}

/** When a chosen app gets a pause. Each app can use its own. */
enum class PauseMode(val key: String, val label: String, val note: String) {
    NewOpens("new", "New opens", "Pauses when you open an app from Home, from Recents after a moment, or from a notification, then waits for your cooldown. Switching through another app stays quiet."),
    EveryTime("every", "Every time", "Pauses every single time the app comes to the front, ignoring cooldown. The strictest choice."),
    AfterBreak("break", "After a break", "Pauses on the first open, then only after your cooldown.");

    companion object { fun from(key: String?): PauseMode? = entries.firstOrNull { it.key == key } }
}

/** Phones set to grayscale lose colour, so the layer leans on light and dark instead. */
enum class GrayMode(val key: String, val label: String) {
    Auto("auto", "Automatic"),
    On("on", "Always"),
    Off("off", "Never");

    companion object { fun from(key: String?): GrayMode = entries.firstOrNull { it.key == key } ?: Auto }
}

/**
 * Everything the user can change about a pause. The pause length is the WHOLE run: from the layer
 * covering the app to the app being fully visible again, including the exit transition.
 */
data class BreathOptions(
    /** The colours that may appear. A pause uses one of them, picked by [paletteOrder]. */
    val palettes: Set<BreathPalette> = BreathPalette.COLORS,
    val paletteOrder: PaletteOrder = PaletteOrder.Random,
    val still: Boolean = false,
    val durationMs: Long = DEFAULT_MS,
    val transition: BreathTransition = BreathTransition.Fade,
    val words: BreathWords = BreathWords.Verse,
    /** Used when [words] is Your words. */
    val customText: String = "",
    val haptics: Boolean = true,
    val showAppName: Boolean = false,
    /** Cooldown in minutes after any pause before another pause may open (15 min = max 4 per hour). 0 means no cooldown. */
    val graceMinutes: Int = DEFAULT_GRACE_MINUTES,
    val mode: PauseMode = PauseMode.NewOpens,
    val gray: GrayMode = GrayMode.Auto,
) {
    /** The colours as a stable list, never empty. */
    val chosenPalettes: List<BreathPalette> get() = palettes.ifEmpty { BreathPalette.COLORS }.sortedBy { it.ordinal }

    /** The colour drawn when none has been picked for a pause: the first chosen one. */
    val palette: BreathPalette get() = chosenPalettes.first()

    val saying: Saying get() = BreathWords.sayingFor(words, customText)
    val graceMs: Long get() = graceMinutes.coerceAtLeast(0) * 60_000L
    val seconds: Float get() = durationMs / 1000f

    companion object {
        const val DEFAULT_MS = 3_000L
        const val MIN_MS = 2_000L
        const val MAX_MS = 10_000L
        /** A single app may be made much harder than the rest. */
        const val APP_MAX_MS = 30_000L
        const val DEFAULT_GRACE_MINUTES = 15
        val GRACE_CHOICES = listOf(0, 5, 10, 15, 30, 60)
        const val CUSTOM_TEXT_MAX = 28
        fun clampDuration(ms: Long): Long = ms.coerceIn(MIN_MS, MAX_MS)
        fun clampAppDuration(ms: Long): Long = ms.coerceIn(MIN_MS, APP_MAX_MS)

        /** One line of your own words: trimmed, single spaced, short enough to stay big. */
        fun cleanCustom(text: String): String = text.trim().replace(Regex("\\s+"), " ").take(CUSTOM_TEXT_MAX).trim()
    }
}

/** Stored keys stay stable so existing colour selections survive visual revisions. */
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
    Lagoon("lagoon", "Pool", 0xff32c9ae.toInt(), 0xff69dfba.toInt(), 0xffb6edbf.toInt(), 0xff073c36.toInt()),
    Violet("violet", "Violet", 0xff3b1fb8.toInt(), 0xff5a35d6.toInt(), 0xff7a52e8.toInt(), 0xfffffaef.toInt()),
    Crimson("crimson", "Crimson", 0xff9b1239.toInt(), 0xffbb1f4c.toInt(), 0xffcc3358.toInt(), 0xfffffaef.toInt()),
    /** Light sheet, dark ink: the strongest light-and-dark contrast, made for grayscale phones. */
    Mono("mono", "Mono", 0xffc9ced6.toInt(), 0xffe3e6eb.toInt(), 0xfff4f5f7.toInt(), 0xff101318.toInt());

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

        /** The colourful ones that appear by default. Mono is opt-in (and used automatically on grayscale). */
        val COLORS: Set<BreathPalette> = linkedSetOf(Electric, Coral, Lagoon, Violet, Crimson)

        fun from(key: String?): BreathPalette = entries.firstOrNull { it.key == key } ?: Electric

        /**
         * Which colour this pause uses. Random never repeats the last one when there is a choice;
         * In order steps through the chosen colours and wraps around.
         */
        fun pick(chosen: List<BreathPalette>, order: PaletteOrder, last: BreathPalette?, random: Random = Random): BreathPalette {
            if (chosen.isEmpty()) return Electric
            if (chosen.size == 1) return chosen.first()
            return when (order) {
                PaletteOrder.InOrder -> {
                    val at = chosen.indexOf(last)
                    if (at < 0) chosen.first() else chosen[(at + 1) % chosen.size]
                }
                PaletteOrder.Random -> (chosen - setOfNotNull(last)).ifEmpty { chosen }.random(random)
            }
        }
    }
}
