package me.kavishdevar.librepods.breathing

/** Small, persisted choices; timing always stays one second. */
data class BreathOptions(val palette: BreathPalette = BreathPalette.Electric, val still: Boolean = false)

enum class BreathPalette(val key: String, val label: String, val primary: Int, val secondary: Int) {
    Electric("electric", "Electric", 0xff2864ee.toInt(), 0xff8959ef.toInt()),
    Coral("coral", "Coral", 0xffef6957.toInt(), 0xffd34680.toInt()),
    Lagoon("lagoon", "Lagoon", 0xff20bda0.toInt(), 0xff258bcf.toInt());

    companion object {
        fun from(key: String?): BreathPalette = entries.firstOrNull { it.key == key } ?: Electric
    }
}
