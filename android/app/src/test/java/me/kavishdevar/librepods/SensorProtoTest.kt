package me.kavishdevar.librepods

import me.kavishdevar.librepods.bluetooth.AACPManager
import me.kavishdevar.librepods.bluetooth.SensorProto
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SensorProtoTest {
    private fun hex(s: String) = s.split(" ").map { it.toInt(16).toByte() }.toByteArray()

    /** The encoder reproduces the head-tracking start message the app has always sent. */
    @Test fun encodesTheKnownHeadTrackingStart() {
        val known = AACPManager().createStartHeadTrackingPacket()
        assertArrayEquals(known, SensorProto.reportInterval(SensorProto.ACTIVITY, 40_000L, seq = 289))
    }

    @Test fun heartRateStartAsksForOneSecond() {
        val p = SensorProto.reportInterval(SensorProto.HEARTRATE_COMMAND, 1_000_000L, seq = 1)
        // 17 00 | 00 00 10 00 | len | 08 01 42 0B 08 54 10 02 1A 05 01 40 42 0F 00
        assertArrayEquals(hex("17 00 00 00 10 00 0F 00 08 01 42 0B 08 54 10 02 1A 05 01 40 42 0F 00"), p)
    }

    @Test fun stopIsIntervalZero() {
        val p = SensorProto.reportInterval(SensorProto.HEARTRATE, 0L, seq = 2)
        assertArrayEquals(hex("08 02 42 0B 08 13 10 02 1A 05 01 00 00 00 00"), p.copyOfRange(8, p.size))
    }

    private fun heartRatePacket(service: Int, bpm: Int, payloadSize: Int = 18): ByteArray {
        val payload = ByteArray(payloadSize).also { it[1] = bpm.toByte() }
        val command = byteArrayOf(0x08, service.toByte(), 0x1A, payloadSize.toByte()) + payload
        val proto = byteArrayOf(0x08, 0x05, 0x3A, command.size.toByte()) + command
        return hex("04 00 04 00 17 00 00 00 10 00") + byteArrayOf(proto.size.toByte(), 0) + proto
    }

    @Test fun readsHeartRate() {
        val cmd = SensorProto.parseCommand(heartRatePacket(SensorProto.HEARTRATE_V2, 72))!!
        assertEquals(SensorProto.HEARTRATE_V2, cmd.service)
        assertEquals(72, SensorProto.heartRate(cmd))
    }

    @Test fun ignoresImplausibleOrMalformedReadings() {
        assertNull(SensorProto.heartRate(SensorProto.parseCommand(heartRatePacket(SensorProto.HEARTRATE, 0))!!))
        assertNull(SensorProto.heartRate(SensorProto.parseCommand(heartRatePacket(SensorProto.HEARTRATE, 80, payloadSize = 10))!!))
        assertNull(SensorProto.heartRate(SensorProto.parseCommand(heartRatePacket(SensorProto.ACTIVITY, 80))!!))
        assertNull(SensorProto.parseCommand(hex("04 00 04 00 17 00 00 00 10 00 40 00 08")))
    }

    @Test fun headMotionPacketsAreNotHeartRate() {
        // A real head-motion report is long and has no heart-rate command: it must fall through.
        val packet = hex("04 00 04 00 17 00") + ByteArray(80) { 0x11 }
        val cmd = SensorProto.parseCommand(packet)
        assertNull(cmd?.let { SensorProto.heartRate(it) })
    }
}
