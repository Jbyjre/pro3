/*
    Glint, a fork of LibrePods - AirPods liberated from Apple's ecosystem
    Copyright (C) 2026 Glint contributors

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
package me.kavishdevar.librepods.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Records straight from the AirPods' microphones over their own link (AACP message 0x58),
 * without switching Bluetooth into call mode. The AirPods send AAC-ELD frames; Android's
 * decoder turns them into PCM, saved as a WAV file. Ported from LibrePods' rewrite branch,
 * where it is marked work in progress: experimental here too.
 */
object AirPodsRecorder {
    const val OPCODE: Byte = 0x58

    /** Body to start the microphone stream (after the 04 00 04 00 header). */
    val START = byteArrayOf(OPCODE, 0x00, 0x00, 0x00, 0x09, 0x00, 0x00, 0x01, 0x82.toByte(), 0x00, 0x00, 0x00, 0x04, 0x96.toByte(), 0x00)
    val STOP = byteArrayOf(OPCODE, 0x00, 0x00, 0x00, 0x02, 0x00, 0x03, 0x01)

    data class State(
        val recording: Boolean = false,
        val startedAt: Long = 0L,
        val frames: Int = 0,
        val file: File? = null,
        val error: String? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var decoder: EldDecoder? = null
    private var writer: WavWriter? = null

    fun folder(context: Context): File = File(context.filesDir, "recordings").apply { mkdirs() }

    fun recordings(context: Context): List<File> =
        folder(context).listFiles { f -> f.extension == "wav" }.orEmpty().sortedByDescending { it.lastModified() }

    @Synchronized
    fun begin(context: Context): File? = try {
        val file = File(folder(context), "Recording ${java.text.SimpleDateFormat("yyyy-MM-dd HH.mm.ss", java.util.Locale.US).format(java.util.Date())}.wav")
        decoder = EldDecoder()
        writer = WavWriter(file)
        _state.value = State(recording = true, startedAt = System.currentTimeMillis(), file = file)
        file
    } catch (e: Exception) {
        Log.e("AirPodsRecorder", "Couldn't start", e)
        end()
        _state.value = State(error = "Couldn't start the recorder on this phone: ${e.message}")
        null
    }

    @Synchronized
    fun end() {
        try { writer?.close() } catch (_: Exception) {}
        try { decoder?.close() } catch (_: Exception) {}
        writer = null
        decoder = null
        _state.update { it.copy(recording = false) }
    }

    /** A 0x58 packet (with the 04 00 04 00 header) from the AirPods. */
    @Synchronized
    fun onPacket(packet: ByteArray) {
        val d = decoder ?: return
        val w = writer ?: return
        val frames = parseFrames(packet)
        for (au in frames) {
            try { d.decode(au) { pcm -> w.write(pcm) } } catch (e: Exception) { Log.w("AirPodsRecorder", "decode: ${e.message}") }
        }
        if (frames.isNotEmpty()) _state.update { it.copy(frames = it.frames + frames.size) }
    }

    /** Access units in a microphone packet: from byte 22, repeated (timestamp u32 LE, length u8, data). */
    fun parseFrames(packet: ByteArray): List<ByteArray> {
        if (packet.size < 22 || packet[4] != OPCODE) return emptyList()
        if (packet[6] != 0x01.toByte() || packet[7] != 0x00.toByte()) return emptyList()
        val out = mutableListOf<ByteArray>()
        var offset = 22
        while (offset + 5 <= packet.size) {
            val length = packet[offset + 4].toInt() and 0xFF
            val start = offset + 5
            val end = start + length
            if (end > packet.size) break
            out += packet.copyOfRange(start, end)
            offset = end
        }
        return out
    }
}

/** AAC-ELD decoder configured the way the AirPods' microphone stream is encoded. */
class EldDecoder : AutoCloseable {
    private val codec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
    private val info = MediaCodec.BufferInfo()

    init {
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, 64_000, 1)
        format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectELD)
        format.setByteBuffer("csd-0", ByteBuffer.wrap(byteArrayOf(0xF8.toByte(), 0xE6.toByte(), 0x30, 0x00)))
        codec.configure(format, null, null, 0)
        codec.start()
    }

    fun decode(accessUnit: ByteArray, onPcm: (ByteArray) -> Unit) {
        val input = codec.dequeueInputBuffer(10_000)
        if (input >= 0) {
            codec.getInputBuffer(input)?.apply { clear(); put(accessUnit) }
            codec.queueInputBuffer(input, 0, accessUnit.size, 0, 0)
        }
        while (true) {
            val output = codec.dequeueOutputBuffer(info, 0)
            if (output < 0) {
                if (output == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) continue
                break
            }
            val buffer = codec.getOutputBuffer(output) ?: break
            val pcm = ByteArray(info.size)
            buffer.position(info.offset)
            buffer.limit(info.offset + info.size)
            buffer.get(pcm)
            codec.releaseOutputBuffer(output, false)
            onPcm(pcm)
        }
    }

    override fun close() {
        codec.stop()
        codec.release()
    }
}

/** 16-bit mono PCM to a WAV file; the header sizes are filled in on close. */
class WavWriter(file: File, private val sampleRate: Int = 64_000) : AutoCloseable {
    private val raf = RandomAccessFile(file, "rw")
    private var dataSize = 0L

    init {
        val h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        h.put("RIFF".toByteArray()).putInt(0).put("WAVE".toByteArray())
        h.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(sampleRate).putInt(sampleRate * 2).putShort(2).putShort(16)
        h.put("data".toByteArray()).putInt(0)
        raf.write(h.array())
    }

    fun write(pcm: ByteArray) {
        raf.write(pcm)
        dataSize += pcm.size
    }

    override fun close() {
        val b = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
        raf.seek(4); raf.write(b.putInt(0, (36 + dataSize).toInt()).array())
        raf.seek(40); raf.write(b.putInt(0, dataSize.toInt()).array())
        raf.close()
    }
}
