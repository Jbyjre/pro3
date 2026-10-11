package me.kavishdevar.librepods

import me.kavishdevar.librepods.services.PulseShaper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 2026-10-11: sound bars that follow the voice, and the beat when nobody sings (Jake's item 7). */
class PulseShaperTest {
    private val rate = 44_100_000 // 44.1 kHz, in milli-hertz as Android reports it
    private val bins = 256
    private val hz = 44_100f / 2f / bins

    /** A capture with the given magnitude at each frequency (Hz to magnitude). */
    private fun capture(vararg tones: Pair<Float, Int>, noise: Int = 0): ByteArray {
        val fft = ByteArray(bins * 2)
        if (noise > 0) for (k in 1 until bins) fft[2 * k] = noise.toByte()
        for ((f, m) in tones) {
            val k = (f / hz).toInt().coerceIn(1, bins - 1)
            fft[2 * k] = m.coerceAtMost(127).toByte()
        }
        return fft
    }

    /** A voice: a fundamental and its harmonics inside the singing range, nothing else. */
    private fun voice(level: Int) = capture(220f to level, 440f to level, 660f to level, 880f to level, 1_320f to level / 2, 1_760f to level / 2, 2_640f to level / 3)

    @Test fun silenceIsFlat() {
        val s = PulseShaper()
        val out = s.frame(ByteArray(bins * 2), rate)
        assertEquals(PulseShaper.SIZE, out.size)
        assertTrue(out.all { it == 0f })
    }

    @Test fun aVoiceIsHeardAndTheMiddleBarsLead() {
        val s = PulseShaper()
        var out = FloatArray(PulseShaper.SIZE)
        for (i in 0 until 30) out = s.frame(voice(if (i % 4 == 0) 120 else 70), rate)
        assertTrue("vocal ${s.vocal}", s.vocal > 0.5f)
        assertTrue("middle ${out[1]},${out[2]} vs outer ${out[0]},${out[3]}", minOf(out[1], out[2]) > maxOf(out[0], out[3]))
    }

    @Test fun noiseIsNotAVoice() {
        // A cymbal-like wash: flat across the whole spectrum.
        val s = PulseShaper()
        repeat(30) { s.frame(capture(noise = 60), rate) }
        assertTrue("vocal ${s.vocal}", s.vocal < 0.2f)
    }

    @Test fun aKickRipplesFromTheMiddleOut() {
        val s = PulseShaper()
        // A steady quiet bed, then a kick.
        repeat(10) { s.frame(capture(80f to 10, 500f to 10, 5_000f to 10), rate) }
        val hit = s.frame(capture(80f to 127, 500f to 10, 5_000f to 10), rate)
        assertEquals(1f, hit[PulseShaper.KICK], 0f)
        // The middle bars take it at once...
        assertTrue(hit[1] > hit[3])
        // ...the outer ones a capture later.
        val after = s.frame(capture(80f to 10, 500f to 10, 5_000f to 10), rate)
        assertTrue(after[3] > hit[3])
        assertTrue((hit + after).all { it in 0f..1f })
    }

    @Test fun theAmbientBeatMovesSmoothlyAndStaysInRange() {
        var last = PulseShaper.ambient(0L, 1)
        var maxStep = 0f
        var lo = 1f; var hi = 0f
        for (t in 16L..4_000L step 16L) {
            val v = PulseShaper.ambient(t, 1)
            assertTrue(v in 0f..1f)
            maxStep = maxOf(maxStep, kotlin.math.abs(v - last))
            lo = minOf(lo, v); hi = maxOf(hi, v)
            last = v
        }
        // It really moves (a beat), never sits still.
        assertTrue(hi - lo > 0.3f)
        // Each beat's rise is sharp but no jump is wild; between hits it's smooth.
        assertTrue("step $maxStep", maxStep < 0.55f)
    }
}
