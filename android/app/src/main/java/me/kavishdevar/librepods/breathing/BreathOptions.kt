package me.kavishdevar.librepods.breathing

import androidx.core.graphics.ColorUtils

/** Appearance is independent of the fixed one-second launch policy. */
data class BreathOptions(val palette: BreathPalette = BreathPalette.Electric, val still: Boolean = false)

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
        val low=DoubleArray(3).also { ColorUtils.colorToLAB(primary,it) }
        val mid=DoubleArray(3).also { ColorUtils.colorToLAB(middle,it) }
        val high=DoubleArray(3).also { ColorUtils.colorToLAB(secondary,it) }
        val sample=DoubleArray(3)
        IntArray(33) { index ->
            val t=index/16.0
            if (t <= 1.0) ColorUtils.blendLAB(low,mid,t,sample)
            else ColorUtils.blendLAB(mid,high,t-1.0,sample)
            ColorUtils.LABToColor(sample[0],sample[1],sample[2])
        }
    }
    companion object {
        const val BASE: Int = 0xff14191f.toInt()
        const val BASE_INK: Int = 0xfffffaef.toInt()
        fun from(key: String?): BreathPalette = entries.firstOrNull { it.key == key } ?: Electric
    }
}
