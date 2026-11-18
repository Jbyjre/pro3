package me.kavishdevar.librepods.breathing

/**
 * One saying is a pair of moments: what to do while the color rises (breathe in), then what to do
 * while it clears (breathe out). Short on purpose: big words, one small line each.
 */
data class Saying(val inHead: String, val inNote: String, val outHead: String, val outNote: String) {
    /** Both moments must break into the same number of lines so the type never jumps when it swaps. */
    val lines: Int get() = maxOf(BreathWords.split(inHead).size, BreathWords.split(outHead).size)
}

enum class BreathWords(val key: String, val label: String, val saying: Saying?) {
    Mix("mix", "Mix", null),
    BreatheIn("breathe_in", "Breathe in", Saying("Breathe in.", "Slow, through your nose.", "Breathe out.", "Let it all out.")),
    SlowDown("slow_down", "Slow down", Saying("Slow down.", "One deep breath.", "No rush.", "Still want this?")),
    Reset("reset", "Reset", Saying("Reset.", "Clear your head.", "Ready.", "Your call."));

    companion object {
        /** The real sayings, in the order a mix cycles through them. */
        val sayings: List<Saying> = entries.mapNotNull { it.saying }

        fun from(key: String?): BreathWords = entries.firstOrNull { it.key == key } ?: Mix

        /** Which saying this pause shows. A mix steps to the next one each time; a pick always shows itself. */
        fun pick(words: BreathWords, last: Int): Int {
            val own = sayings.indexOfFirst { it === words.saying }
            return if (own >= 0) own else Math.floorMod(last + 1, sayings.size)
        }

        /** Big type breaks after the first word: "BREATHE / IN." reads bigger than one thin line. */
        fun split(text: String): List<String> {
            val words = text.trim().uppercase().split(' ').filter { it.isNotEmpty() }
            return if (words.size < 2 || text.length <= 7) listOf(words.joinToString(" "))
            else listOf(words.first(), words.drop(1).joinToString(" "))
        }
    }
}
