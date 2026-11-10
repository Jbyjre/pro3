package me.kavishdevar.librepods

import android.app.Application
import android.bluetooth.le.ScanSettings
import me.kavishdevar.librepods.bluetooth.AACPManager
import me.kavishdevar.librepods.bluetooth.BLEManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The battery audit of 2026-10-15: things that ran harder or longer than they needed to. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class BatteryAuditTest {
    @Test fun theBeaconScanBoostsBrieflyThenBalancesWhileScreenIsOn() {
        assertEquals(ScanSettings.SCAN_MODE_LOW_LATENCY, BLEManager.modeFor(screenOn = true, quickResponse = true))
        assertEquals(ScanSettings.SCAN_MODE_BALANCED, BLEManager.modeFor(screenOn = true))
        assertEquals(ScanSettings.SCAN_MODE_LOW_POWER, BLEManager.modeFor(screenOn = false))
        assertEquals(ScanSettings.SCAN_MODE_LOW_POWER, BLEManager.modeFor(screenOn = false, quickResponse = true))
        assertTrue(BLEManager.SCREEN_BOOST_MS in 5_000L..20_000L)
    }

    @Test fun theSameBeaconAddressIsAcceptedAcrossCallbacksButOnceWithinABatch() {
        // Previously onScanResult would silently discard all later updates from the same buds.
        assertTrue(BLEManager.acceptAddress("AA:BB:CC"))
        assertTrue(BLEManager.acceptAddress("AA:BB:CC"))
        val batch = mutableSetOf<String>()
        assertTrue(BLEManager.acceptAddress("AA:BB:CC", batch))
        assertTrue(!BLEManager.acceptAddress("AA:BB:CC", batch))
        assertTrue(BLEManager.acceptAddress("DD:EE:FF", batch))
        assertTrue(BLEManager.acceptAddress("AA:BB:CC", mutableSetOf()))
    }

    @Test fun scanRestartsNeverTripAndroidsLimit() {
        val now = 100_000L
        // Fewer than four starts in the last 30 s: go now.
        assertEquals(0L, BLEManager.waitBeforeStart(listOf(now - 5_000, now - 3_000, now - 1_000), now))
        // Four already: wait until the oldest of them is more than 30 s old.
        val four = listOf(now - 20_000, now - 10_000, now - 5_000, now - 1_000)
        val wait = BLEManager.waitBeforeStart(four, now)
        assertTrue("wait $wait", wait in 10_000L..11_000L)
        // Old starts don't hold anything up.
        assertEquals(0L, BLEManager.waitBeforeStart(listOf(now - 90_000, now - 80_000, now - 70_000, now - 60_000), now))
    }

    @OptIn(ExperimentalStdlibApi::class)
    @Test fun thePacketHeaderCheckMatchesTheOldTextCheck() {
        val m = AACPManager()
        val samples = listOf(
            byteArrayOf(0x04, 0x00, 0x04, 0x00, 0x09, 0x00),
            byteArrayOf(0x04, 0x00, 0x04, 0x00),
            byteArrayOf(0x04, 0x00, 0x04),
            byteArrayOf(0x04, 0x00, 0x05, 0x00, 0x09),
            byteArrayOf(0x00, 0x04, 0x00, 0x04),
            byteArrayOf(),
        )
        for (p in samples) assertEquals(p.toHexString(), p.toHexString().startsWith("04000400"), m.hasHeader(p))
    }
}
