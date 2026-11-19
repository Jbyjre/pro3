package me.kavishdevar.librepods.breathing

/**
 * What the pause says. A saying has two moments, while the colour rises and while it clears. When both
 * moments are the same (the verse reference, your own words) the type never changes or animates away:
 * it simply stays, in one steady phrase.
 */
data class Saying(val inHead: String, val inNote: String, val outHead: String, val outNote: String) {
    /** Both moments must break into the same number of lines so the type never jumps when it swaps. */
    val lines: Int get() = maxOf(BreathWords.split(inHead).size, BreathWords.split(outHead).size)
}

enum class BreathWords(val key: String, val label: String, val saying: Saying?) {
    /** Psalm 51:10, shown as the reference only (the verse's words are never printed). */
    Verse("verse", "Psalm 51:10", Saying("Psalm 51:10", "", "Psalm 51:10", "")),
    Custom("custom", "Your words", null),
    BreatheIn("breathe_in", "Breathe in", Saying("Breathe in.", "Slow, through your nose.", "Breathe out.", "Let it all out.")),
    SlowDown("slow_down", "Slow down", Saying("Slow down.", "One deep breath.", "No rush.", "Still want this?")),
    Reset("reset", "Reset", Saying("Reset.", "Clear your head.", "Ready.", "Your call."));

    companion object {
        /** An older saved "mix" (and anything unknown) becomes the verse, as asked. */
        fun from(key: String?): BreathWords = entries.firstOrNull { it.key == key } ?: Verse

        fun sayingFor(words: BreathWords, custom: String): Saying {
            if (words != Custom) return words.saying ?: Verse.saying!!
            val text = BreathOptions.cleanCustom(custom)
            return if (text.isEmpty()) Verse.saying!! else Saying(text, "", text, "")
        }

        /** Big type breaks after the first word: "PSALM / 51:10" reads bigger than one thin line. */
        fun split(text: String): List<String> {
            val words = text.trim().uppercase().split(' ').filter { it.isNotEmpty() }
            return if (words.size < 2 || text.length <= 7) listOf(words.joinToString(" "))
            else listOf(words.first(), words.drop(1).joinToString(" "))
        }
    }
}
