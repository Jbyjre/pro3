package me.kavishdevar.librepods

import me.kavishdevar.librepods.services.HeartInsights
import me.kavishdevar.librepods.services.HeartLink
import me.kavishdevar.librepods.services.HeartRate
import me.kavishdevar.librepods.services.HeartRateGatt
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HeartLinkTest {
    @Test fun measurementFollowsTheBluetoothHeartRateFormat() {
        // Flags 0x06: 8-bit value, sensor contact supported and detected.
        assertArrayEquals(byteArrayOf(0x06, 72), HeartRateGatt.measurement(72))
        assertArrayEquals(byteArrayOf(0x06, 0xC8.toByte()), HeartRateGatt.measurement(200))
        // Above 255: 16-bit little-endian with flag bit 0 set.
        assertArrayEquals(byteArrayOf(0x07, 0x2C, 0x01), HeartRateGatt.measurement(300))
        assertEquals("0000180d-0000-1000-8000-00805f9b34fb", HeartRateGatt.SERVICE.toString())
        assertEquals("00002a37-0000-1000-8000-00805f9b34fb", HeartRateGatt.MEASUREMENT.toString())
    }

    @Test fun jsonBody() {
        assertEquals("{\"bpm\":72,\"time\":1000,\"status\":\"live\"}", HeartLink.json(72, 1000L, "live"))
        assertEquals("{\"bpm\":null,\"time\":5,\"status\":\"stopped\"}", HeartLink.json(null, 5L, "stopped"))
    }

    @Test fun onlyHttpsAddresses() {
        assertTrue(HeartLink.isValidUrl("https://example.com/api/webhook/abc"))
        assertTrue(HeartLink.isValidUrl(" https://hooks.zapier.com/x "))
        assertFalse(HeartLink.isValidUrl("http://192.168.1.5:8123/api/webhook/x"))
        assertFalse(HeartLink.isValidUrl("example.com"))
        assertFalse(HeartLink.isValidUrl(""))
    }

    @Test fun restingIsTheLowestThreeMinuteAverage() {
        // 10 minutes: 90 BPM, except minutes 4-7 at 62.
        val s = (0..600).map { HeartRate.Sample(it * 1000L, if (it in 240..420) 62 else 90) }
        assertEquals(62, HeartInsights.restingEstimate(s))
        assertNull(HeartInsights.restingEstimate(s.take(100)))
    }

    @Test fun recoveryIsTheDropAMinuteAfterThePeak() {
        val s = (0..200).map { HeartRate.Sample(it * 1000L, if (it <= 100) 100 + it / 2 else 150 - (it - 100) / 3) }
        // Peak 150 at 100 s; 55-65 s later the readings are 150 - 18..21.
        val drop = HeartInsights.recovery(s)!!
        assertTrue("drop $drop", drop in 18..22)
        // No effort, no recovery figure.
        assertNull(HeartInsights.recovery((0..200).map { HeartRate.Sample(it * 1000L, 80) }))
        // The minute after the peak hasn't passed yet.
        assertNull(HeartInsights.recovery(s.take(130)))
    }

    @Test fun csvHasOneRowPerReading() {
        val csv = HeartInsights.csv(listOf(HeartRate.Sample(0L, 70), HeartRate.Sample(1000L, 71)))
        assertEquals("time_utc,time_ms,bpm\n1970-01-01T00:00:00Z,0,70\n1970-01-01T00:00:01Z,1000,71\n", csv)
    }

    @Test fun aLongBreakStartsANewSession() {
        HeartRate.status(HeartRate.Status.Off)
        HeartRate.starting(0L)
        HeartRate.reading(70, 1_000L)
        HeartRate.status(HeartRate.Status.NotConnected)
        // Back within half an hour: same session.
        HeartRate.starting(10 * 60_000L, background = true)
        assertEquals(1, HeartRate.state.value.samples.size)
        assertTrue(HeartRate.state.value.background)
        HeartRate.status(HeartRate.Status.NotConnected)
        // After a longer break: a fresh one.
        HeartRate.starting(1_000L + HeartRate.NEW_SESSION_GAP_MS + 1)
        assertEquals(0, HeartRate.state.value.samples.size)
        HeartRate.status(HeartRate.Status.Off)
        assertFalse(HeartRate.state.value.background)
        HeartRate.clear()
    }
}
