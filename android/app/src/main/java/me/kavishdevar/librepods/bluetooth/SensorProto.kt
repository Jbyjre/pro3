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
package me.kavishdevar.librepods.bluetooth

/**
 * The AirPods' sensor stream ("SensorDataWX", carried in AACP message 0x17). Its body is a
 * small protobuf; the few fields Glint needs are encoded and decoded by hand here so no
 * protobuf library is needed.
 *
 * Message layout: 04 00 04 00 | 17 00 | descriptor u32 LE (0x00100000) | length u16 LE | protobuf
 *
 * SensorDataWX: 1 seq (varint), 7 command {1 service, 3 payload}, 8 service_settings
 * {1 service, 2 setting (2 = report interval), 3 configuration (01 + interval µs u32 LE)}.
 * Field numbers and service ids from the protocol definition in LibrePods' rewrite branch.
 */
object SensorProto {
    const val DESCRIPTOR_SENSOR_DATA_WX = 0x00100000
    const val OPCODE: Byte = 0x17

    // Sensor service ids.
    const val ACTIVITY = 14
    const val HEARTRATE = 19
    const val HEARTRATE_V2 = 20
    const val HEARTRATE_COMMAND = 84

    /** The opcode + body for "report [service] every [intervalMicros]" (0 stops it). */
    fun reportInterval(service: Int, intervalMicros: Long, seq: Int): ByteArray {
        val config = byteArrayOf(
            0x01,
            intervalMicros.toByte(), (intervalMicros shr 8).toByte(),
            (intervalMicros shr 16).toByte(), (intervalMicros shr 24).toByte(),
        )
        val setting = varintField(1, service.toLong()) + varintField(2, 2) + bytesField(3, config)
        val proto = varintField(1, seq.toLong()) + bytesField(8, setting)
        return byteArrayOf(OPCODE, 0x00) + le32(DESCRIPTOR_SENSOR_DATA_WX) + le16(proto.size) + proto
    }

    data class Command(val service: Int, val payload: ByteArray)

    /** The sensor command in a received 0x17 packet (with the 04 00 04 00 header), if any. */
    fun parseCommand(packet: ByteArray): Command? {
        if (packet.size < 12 || packet[4] != OPCODE) return null
        val descriptor = u8(packet, 6) or (u8(packet, 7) shl 8) or (u8(packet, 8) shl 16) or (u8(packet, 9) shl 24)
        if (descriptor != DESCRIPTOR_SENSOR_DATA_WX) return null
        val length = u8(packet, 10) or (u8(packet, 11) shl 8)
        if (packet.size < 12 + length) return null
        val body = packet.copyOfRange(12, 12 + length)
        val command = fields(body)?.firstOrNull { it.number == 7 }?.bytes ?: return null
        val inner = fields(command) ?: return null
        val service = inner.firstOrNull { it.number == 1 }?.varint?.toInt() ?: return null
        val payload = inner.firstOrNull { it.number == 3 }?.bytes ?: ByteArray(0)
        return Command(service, payload)
    }

    /** Beats per minute from a heart-rate command payload (18 bytes, bpm at index 1). */
    fun heartRate(command: Command): Int? {
        if (command.service != HEARTRATE && command.service != HEARTRATE_V2 && command.service != HEARTRATE_COMMAND) return null
        if (command.payload.size != 18) return null
        val bpm = command.payload[1].toInt() and 0xFF
        return bpm.takeIf { it in 25..250 }
    }

    private class Field(val number: Int, val varint: Long?, val bytes: ByteArray?)

    private fun fields(data: ByteArray): List<Field>? {
        val out = mutableListOf<Field>()
        var i = 0
        fun varint(): Long? {
            var result = 0L
            var shift = 0
            while (i < data.size && shift < 64) {
                val b = data[i++].toInt() and 0xFF
                result = result or ((b and 0x7F).toLong() shl shift)
                if (b and 0x80 == 0) return result
                shift += 7
            }
            return null
        }
        while (i < data.size) {
            val key = varint() ?: return null
            val number = (key shr 3).toInt()
            when ((key and 7).toInt()) {
                0 -> out += Field(number, varint() ?: return null, null)
                2 -> {
                    val len = varint()?.toInt() ?: return null
                    if (len < 0 || i + len > data.size) return null
                    out += Field(number, null, data.copyOfRange(i, i + len))
                    i += len
                }
                5 -> { if (i + 4 > data.size) return null; i += 4 }
                1 -> { if (i + 8 > data.size) return null; i += 8 }
                else -> return null
            }
        }
        return out
    }

    private fun varint(value: Long): ByteArray {
        val out = mutableListOf<Byte>()
        var v = value
        do {
            var b = (v and 0x7F).toInt()
            v = v ushr 7
            if (v != 0L) b = b or 0x80
            out += b.toByte()
        } while (v != 0L)
        return out.toByteArray()
    }

    private fun varintField(number: Int, value: Long) = varint((number shl 3).toLong()) + varint(value)
    private fun bytesField(number: Int, value: ByteArray) = varint(((number shl 3) or 2).toLong()) + varint(value.size.toLong()) + value
    private fun le32(v: Int) = byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte())
    private fun le16(v: Int) = byteArrayOf(v.toByte(), (v shr 8).toByte())
    private fun u8(b: ByteArray, i: Int) = b[i].toInt() and 0xFF
}
