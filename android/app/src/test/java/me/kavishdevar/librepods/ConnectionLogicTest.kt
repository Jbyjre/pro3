package me.kavishdevar.librepods

import me.kavishdevar.librepods.bluetooth.AirPodsDetection
import me.kavishdevar.librepods.bluetooth.AirPodsDetection.Match
import me.kavishdevar.librepods.bluetooth.ReconnectPolicy
import me.kavishdevar.librepods.services.getNextMode
import me.kavishdevar.librepods.utils.SupportLevel
import me.kavishdevar.librepods.utils.supportVerdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AirPodsDetectionTest {
    private val mac = "AA:BB:CC:DD:EE:FF"

    @Test fun aapUuidIsDefinite() {
        assertEquals(Match.DEFINITE, AirPodsDetection.match(mac, "Headphones", listOf("0000110b-0000-1000-8000-00805f9b34fb", AirPodsDetection.AAP_UUID.uppercase()), null))
    }

    @Test fun savedAddressIsDefiniteEvenWithoutUuids() {
        // Issue 595: Pro 3 connects before Android has cached its SDP records.
        assertEquals(Match.DEFINITE, AirPodsDetection.match(mac, null, null, mac.lowercase()))
    }

    @Test fun nameOnlyIsLikely() {
        assertEquals(Match.LIKELY, AirPodsDetection.match(mac, "Jake’s AirPods Pro", emptyList(), ""))
        assertEquals(Match.LIKELY, AirPodsDetection.match(mac, "AIRPODS PRO 3", null, null))
    }

    @Test fun unrelatedDevicesAreIgnored() {
        assertEquals(Match.NONE, AirPodsDetection.match(mac, "Galaxy Buds3 Pro", listOf("0000110b-0000-1000-8000-00805f9b34fb"), "11:22:33:44:55:66"))
        assertEquals(Match.NONE, AirPodsDetection.match(null, "AirPods", null, null))
        assertEquals(Match.NONE, AirPodsDetection.match(mac, null, null, null))
    }
}

class ReconnectPolicyTest {
    @Test fun burstThenSlowThenStop() {
        val p = ReconnectPolicy(burstDelaysMs = listOf(0, 100, 200), slowDelayMs = 1000, maxSlowAttempts = 2)
        assertEquals(0L, p.delayBefore(0))
        assertEquals(200L, p.delayBefore(2))
        assertEquals(1000L, p.delayBefore(3))
        assertEquals(1000L, p.delayBefore(4))
        assertNull(p.delayBefore(5))
        assertEquals(5, p.maxAttempts)
    }

    @Test fun defaultPolicyKeepsTryingForSeveralMinutes() {
        val p = ReconnectPolicy()
        val total = (0 until p.maxAttempts).sumOf { p.delayBefore(it)!! }
        assertTrue("total retry window was $total ms", total in 180_000L..600_000L)
        assertNull(p.delayBefore(p.maxAttempts))
    }
}

class SupportVerdictTest {
    private fun verdict(
        sdk: Int, maker: String, build: String = "X", bypass: Boolean = false,
        xposed: Boolean = false, ever: Boolean = false, blocked: Boolean = false,
    ) = supportVerdict(sdk, maker, build, bypass, xposed, ever, blocked)

    @Test fun samsungOnAndroid16NeedsOneUi9() {
        val v = verdict(36, "samsung")
        assertEquals(SupportLevel.NEEDS_ONE_UI_9, v.level)
        assertFalse(v.canConnect)
        assertTrue(v.message.contains("One UI 9"))
    }

    @Test fun samsungOnAndroid17IsExpectedButCheckedLive() {
        val v = verdict(37, "samsung")
        assertEquals(SupportLevel.EXPECTED, v.level)
        assertTrue(v.canConnect)
    }

    @Test fun observedBlockOverridesVersionGuess() {
        assertEquals(SupportLevel.BLOCKED_ON_THIS_PHONE, verdict(37, "samsung", blocked = true).level)
        assertEquals(SupportLevel.NEEDS_ONE_UI_9, verdict(36, "samsung", blocked = true).level)
    }

    @Test fun realConnectionWins() {
        assertEquals(SupportLevel.CONFIRMED, verdict(35, "samsung", ever = true).level)
    }

    @Test fun pixelQpr3AndOppoFamily() {
        assertEquals(SupportLevel.EXPECTED, verdict(36, "Google", build = "CP1A.260305.018").level)
        assertEquals(SupportLevel.NEEDS_UPDATE_OR_ROOT, verdict(36, "Google", build = "BP4A.251205.006").level)
        assertEquals(SupportLevel.EXPECTED, verdict(36, "OnePlus").level)
    }

    @Test fun xposedOrBypassAllowsTrying() {
        assertEquals(SupportLevel.EXPECTED, verdict(34, "xiaomi", xposed = true).level)
        assertEquals(SupportLevel.EXPECTED, verdict(34, "xiaomi", bypass = true).level)
    }
}

class ListeningModeCycleTest {
    // Modes: 1 off, 2 noise cancellation, 3 transparency, 4 adaptive. Config bits: 1 off, 2 ANC, 4 transparency, 8 adaptive.
    @Test fun cyclesThroughEnabledModes() {
        assertEquals(4, getNextMode(currentMode = 3, configByte = 0b1110, offmodeEnabled = false))
        assertEquals(2, getNextMode(currentMode = 4, configByte = 0b1110, offmodeEnabled = false))
        assertEquals(3, getNextMode(currentMode = 2, configByte = 0b1110, offmodeEnabled = false))
    }

    @Test fun offOnlyWhenAllowed() {
        assertEquals(1, getNextMode(currentMode = 3, configByte = 0b0101, offmodeEnabled = true))
        assertEquals(3, getNextMode(currentMode = 3, configByte = 0b0101, offmodeEnabled = false))
    }
}
