/*
    pro, a fork of LibrePods - AirPods liberated from Apple's ecosystem
    Copyright (C) 2026 pro contributors

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>.
*/
package me.kavishdevar.librepods.services

import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.sin

/**
 * Turns what the phone is playing (one spectrum capture at a time, about 20 a second) into the
 * four sound bars, so they move with the song itself (Jake, 2026-10-11: "match the vocals").
 *
 * - **Singing**: is there a voice right now? A voice puts most of its energy between about
 *   300 and 3,400 Hz and is harmonic there (peaky, not flat like noise or a cymbal). While it is,
 *   the two middle bars follow the voice: its body (300 to 1,000 Hz) and its presence (1,000 to
 *   3,400 Hz), with a lift on each new syllable (a sudden rise in that range). The outer bars keep
 *   the kick drum (left) and the hi-hats (right), a little lower, so the voice leads.
 * - **Instrumental** (no voice): the beat drives it. Each kick (a sudden rise in the bass, above
 *   its own recent average) sends a ripple out from the middle bars to the outer ones, on top of
 *   each band's smoothed loudness, so the bars flow with the beat rather than flicker.
 * - The two blend by how sure it is that someone sings, so nothing jumps.
 * - Nothing to hear (no permission, or the phone hands over silence): [ambient], a steady beat-like
 *   wave instead of random wiggles.
 *
 * Limits, honestly: Android never gives other apps a song's lyrics (Spotify keeps them inside
 * Spotify), so the bars follow the singing itself, not the lyric lines; and a lead guitar or
 * piano melody is harmonic too, so it can count as "singing" (the bars then follow the melody).
 * Pure (no Android), so it is unit-tested.
 */
class PulseShaper {
    companion object {
        /** The bars, left to right. */
        const val BARS = 4
        /** Index of the kick's pulse (for the cover's glow) after the bars. */
        const val KICK = 4
        const val SIZE = 5

        /** Band edges in Hz: kick, low, voice body, voice presence, air. */
        internal val EDGES = floatArrayOf(40f, 150f, 300f, 1_000f, 3_400f, 9_000f)
        private const val N = 5

        /** Below this (log energy) it's silence: flat bars. */
        private const val SILENT = 0.25f

        /**
         * The gentle beat used when nothing can be heard: about 118 beats a minute, a sharp rise and
         * a soft fall, rippling out from the middle bars, over a slow drift so it never looks looped.
         * [tMs] is any steadily rising clock; returns 0..1 for bar [i].
         */
        fun ambient(tMs: Long, i: Int): Float {
            val beatMs = 508.0
            // The middle bars hit first, the outer ones a moment later.
            val delay = if (i == 1 || i == 2) 0.0 else 70.0
            val phase = (((tMs - delay) / beatMs) % 1.0 + 1.0) % 1.0
            val hit = exp(-phase * 4.2).toFloat()
            val drift = 0.5f + 0.5f * sin(tMs / 1000.0 * (0.9 + 0.37 * i) + i * 1.9).toFloat()
            return (0.28f + 0.5f * hit * (if (i == 1 || i == 2) 1f else 0.8f) + 0.18f * drift).coerceIn(0f, 1f)
        }
    }

    private val peaks = FloatArray(N) { 0.6f }
    private val prev = FloatArray(N)
    private var prevVoice = 0f
    private var fluxMean = 0.05f
    private var voiceFluxMean = 0.05f
    private var kick = 0f
    private var syllable = 0f
    /** How sure it is that someone sings, 0..1, smoothed over about a third of a second. */
    var vocal = 0f
        private set
    /** Kicks of the last few captures, newest first: the ripple's delay line. */
    private val kicks = FloatArray(3)

    fun reset() {
        peaks.fill(0.6f); prev.fill(0f); kicks.fill(0f)
        prevVoice = 0f; fluxMean = 0.05f; voiceFluxMean = 0.05f; kick = 0f; syllable = 0f; vocal = 0f
    }

    /**
     * One capture in Android's Visualizer layout (real DC, real Nyquist, then real/imaginary pairs)
     * at [samplingRateMilliHz]. Returns [SIZE] values 0..1: four bars left to right, then the kick.
     */
    fun frame(fft: ByteArray, samplingRateMilliHz: Int): FloatArray {
        val out = FloatArray(SIZE)
        val bins = fft.size / 2
        if (bins < 4 || samplingRateMilliHz <= 0) return out
        val hzPerBin = samplingRateMilliHz / 1000f / 2f / bins
        fun mag(k: Int): Float {
            val re = fft[2 * k].toFloat()
            val im = if (2 * k + 1 < fft.size) fft[2 * k + 1].toFloat() else 0f
            return hypot(re, im)
        }
        val mean = FloatArray(N)
        val sums = FloatArray(N)
        val energy = FloatArray(N)
        var next = 1
        for (b in 0 until N) {
            val from = maxOf(next, (EDGES[b] / hzPerBin).toInt()).coerceIn(1, bins - 1)
            val to = (EDGES[b + 1] / hzPerBin).toInt().coerceIn(from + 1, bins)
            next = to
            var sum = 0f
            for (k in from until to) sum += mag(k)
            sums[b] = sum
            mean[b] = sum / (to - from)
            energy[b] = ln(1f + mean[b])
        }
        if (energy.max() < SILENT) {
            // Silence: everything settles (a kick doesn't hang in the air).
            kick *= 0.6f; syllable *= 0.6f; vocal *= 0.9f
            prev.fill(0f); prevVoice = 0f
            for (i in kicks.indices.reversed()) kicks[i] = if (i == 0) 0f else kicks[i - 1]
            return out
        }
        // Each band against its own recent loudest moment, so quiet songs move as much as loud ones.
        val n = FloatArray(N)
        for (b in 0 until N) {
            peaks[b] = maxOf(energy[b], peaks[b] * 0.992f, 0.6f)
            n[b] = if (energy[b] < SILENT) 0f else (energy[b] / peaks[b]).coerceIn(0f, 1f)
        }

        // ---- A kick: the bass jumps above its own recent average ----
        val flux = (energy[0] - prev[0]).coerceAtLeast(0f)
        kick = if (flux > fluxMean * 1.8f + 0.04f) 1f else kick * 0.72f
        fluxMean = fluxMean * 0.9f + flux * 0.1f
        for (i in kicks.indices.reversed()) kicks[i] = if (i == 0) kick else kicks[i - 1]

        // ---- A voice: energy in the singing range, and harmonic (peaky) there ----
        // Total energy, not band averages (the bass bands are only a few bins wide).
        val total = sums.sum().coerceAtLeast(1e-3f)
        val share = (sums[2] + sums[3]) / total
        val flat = flatness(::mag, (EDGES[2] / hzPerBin).toInt().coerceIn(1, bins - 1), (EDGES[4] / hzPerBin).toInt().coerceIn(2, bins))
        val raw = ((share - 0.35f) / 0.3f).coerceIn(0f, 1f) * ((0.7f - flat) / 0.35f).coerceIn(0f, 1f)
        vocal += (raw - vocal) * 0.15f
        // A syllable: the singing range jumps.
        val voiceE = (energy[2] + energy[3]) / 2f
        val vFlux = (voiceE - prevVoice).coerceAtLeast(0f)
        syllable = if (vFlux > voiceFluxMean * 1.6f + 0.03f) 1f else syllable * 0.7f
        voiceFluxMean = voiceFluxMean * 0.9f + vFlux * 0.1f
        prevVoice = voiceE
        for (b in 0 until N) prev[b] = energy[b]

        // ---- The two looks, blended by how sure it is that someone sings ----
        val sing = floatArrayOf(
            0.75f * (0.55f * n[0] + 0.45f * kick),
            0.7f * n[2] + 0.3f * syllable,
            0.7f * n[3] + 0.3f * syllable,
            0.75f * (0.7f * n[4] + 0.3f * n[3]),
        )
        // The ripple: the middle bars take this kick, the outer ones the one before.
        val play = floatArrayOf(
            0.55f * n[0] + 0.45f * kicks[1],
            0.55f * n[1] + 0.45f * kicks[0],
            0.55f * ((n[2] + n[3]) / 2f) + 0.45f * kicks[0],
            0.55f * n[4] + 0.45f * kicks[1],
        )
        for (i in 0 until BARS) out[i] = (play[i] + (sing[i] - play[i]) * vocal).coerceIn(0f, 1f)
        out[KICK] = kick.coerceIn(0f, 1f)
        return out
    }

    /** Spectral flatness of bins [from, to): 1 = flat like noise, near 0 = a few strong harmonics. */
    private inline fun flatness(mag: (Int) -> Float, from: Int, to: Int): Float {
        var logSum = 0.0
        var sum = 0.0
        var count = 0
        for (k in from until to) {
            val m = mag(k).toDouble() + 1e-3
            logSum += ln(m)
            sum += m
            count++
        }
        if (count == 0 || sum <= 0.0) return 1f
        val geo = exp(logSum / count)
        return (geo / (sum / count)).toFloat().coerceIn(0f, 1f)
    }
}
