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

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.audiofx.Visualizer
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.hypot
import kotlin.math.ln

/**
 * How loud the music is right now in four bands (bass to treble), for the sound bars. Listens
 * to what the phone is playing (Android's Visualizer on the whole output, the same thing music
 * visualiser apps use), so the bars follow any app's music, whatever plays it and whatever the
 * volume. Needs the microphone permission (Android asks for it to protect what's playing; pro
 * never records anything). Without it, or on a phone that refuses, [live] stays false and the
 * bars fall back to their own gentle motion.
 */
object MusicPulse {
    const val BANDS = 4

    /** 0..1 per band, about 20 times a second. */
    private val _levels = MutableStateFlow(FloatArray(BANDS))
    val levels: StateFlow<FloatArray> = _levels

    /**
     * True while real sound is coming in. Some phones hand over silence instead of refusing
     * (for example while pro is in the background): after a few seconds of nothing, this goes
     * false so the bars fall back to their own motion rather than lying flat under music.
     */
    private val _live = MutableStateFlow(false)
    val live: StateFlow<Boolean> = _live

    // The Visualizer is created and reports on its own thread: creating it asks Android's audio
    // system (which can be slow when it is busy) and it reports about 20 times a second. Doing
    // that on the main thread could freeze the app.
    private val thread by lazy { android.os.HandlerThread("pro-pulse").apply { start() } }
    private val handler by lazy { android.os.Handler(thread.looper) }

    private var visualizer: Visualizer? = null   // only touched on the pulse thread
    @Volatile private var users = 0
    private val peaks = FloatArray(BANDS) { 1f }
    private var heardAt = 0L
    private const val SILENT_MS = 4_000L

    fun allowed(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /** Starts listening (counted: every [acquire] needs a [release]). Call on the main thread. */
    fun acquire(context: Context) {
        users++
        val app = context.applicationContext
        handler.post { if (users > 0 && visualizer == null) open(app) }
    }

    fun release() {
        users = (users - 1).coerceAtLeast(0)
        if (users == 0) handler.post { if (users == 0) close() }
    }

    /** Permission just granted: try again for whoever is waiting. */
    fun retry(context: Context) {
        if (users <= 0) return
        val app = context.applicationContext
        handler.post { if (users > 0 && visualizer == null) open(app) }
    }

    private fun open(context: Context) {
        if (!allowed(context)) return
        visualizer = try {
            Visualizer(0).apply {
                enabled = false
                val range = Visualizer.getCaptureSizeRange()
                captureSize = 512.coerceIn(range[0], range[1])
                scalingMode = Visualizer.SCALING_MODE_NORMALIZED
                val rate = Visualizer.getMaxCaptureRate()
                setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(v: Visualizer?, waveform: ByteArray?, samplingRate: Int) {}
                    override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                        if (fft == null) return
                        val l = levels(fft, samplingRate, peaks)
                        _levels.value = l
                        val now = android.os.SystemClock.elapsedRealtime()
                        if (l.any { it > 0f }) heardAt = now
                        _live.value = heardAt > 0L && now - heardAt < SILENT_MS
                    }
                }, rate, false, true)
                enabled = true
            }
        } catch (e: Throwable) {
            // Some phones refuse the whole-output visualiser (or another app holds it).
            Log.w("MusicPulse", "Visualizer unavailable", e)
            null
        }
    }

    private fun close() {
        runCatching { visualizer?.enabled = false }
        runCatching { visualizer?.release() }
        visualizer = null
        _live.value = false
        heardAt = 0L
        _levels.value = FloatArray(BANDS)
        peaks.fill(1f)
    }

    /** Band edges in Hz: bass, low mids, high mids, treble. */
    private val EDGES = floatArrayOf(40f, 160f, 600f, 2_400f, 9_000f)

    /**
     * Turns one FFT capture (Android's layout: real DC, real Nyquist, then real/imaginary pairs)
     * at [samplingRateMilliHz] into four 0..1 levels. Each band is measured against its own
     * recent loudest moment ([peaks], updated in place, slowly forgetting), so quiet songs move
     * the bars as much as loud ones, and silence is flat.
     */
    internal fun levels(fft: ByteArray, samplingRateMilliHz: Int, peaks: FloatArray): FloatArray {
        val bins = fft.size / 2
        val out = FloatArray(BANDS)
        if (bins < 2 || samplingRateMilliHz <= 0) return out
        val nyquist = samplingRateMilliHz / 1000f / 2f
        val hzPerBin = nyquist / bins
        var next = 1
        for (b in 0 until BANDS) {
            val from = maxOf(next, (EDGES[b] / hzPerBin).toInt()).coerceIn(1, bins - 1)
            val to = (EDGES[b + 1] / hzPerBin).toInt().coerceIn(from + 1, bins)
            next = to
            var sum = 0f
            for (k in from until to) {
                val re = fft[2 * k].toFloat()
                val im = if (2 * k + 1 < fft.size) fft[2 * k + 1].toFloat() else 0f
                sum += hypot(re, im)
            }
            val energy = ln(1f + sum / (to - from))
            peaks[b] = maxOf(energy, peaks[b] * 0.992f, 0.6f)
            out[b] = if (energy < 0.25f) 0f else (energy / peaks[b]).coerceIn(0f, 1f)
        }
        return out
    }
}
