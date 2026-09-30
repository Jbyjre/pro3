package me.kavishdevar.librepods

import me.kavishdevar.librepods.bluetooth.AACPManager
import me.kavishdevar.librepods.data.AirPodsModels
import me.kavishdevar.librepods.data.AirPodsNotifications
import me.kavishdevar.librepods.data.Battery
import me.kavishdevar.librepods.data.BatteryComponent
import me.kavishdevar.librepods.data.BatteryStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Feeds AirPods messages (formats and examples from docs/AAP Definitions.md) through the same
 * decoders the app uses, so battery, listening mode, ear detection and model detection are
 * checked without a phone.
 */
class AirPodsProtocolTest {
    private fun hex(s: String): ByteArray =
        s.split(" ", "\n").filter { it.isNotBlank() }.map { it.toInt(16).toByte() }.toByteArray()

    private fun List<Battery>.of(component: Int) = first { it.component == component }

    @Test
    fun batteryWithAllThreeParts() {
        val b = AirPodsNotifications.BatteryNotification()
        // Example from the protocol notes: right 100% discharging, left 99% charging, case 17%.
        b.setBattery(hex("04 00 04 00 04 00 03 02 01 64 02 01 04 01 63 01 01 08 01 11 02 01"))
        val list = b.getBattery()
        assertEquals(Battery(BatteryComponent.LEFT, 99, BatteryStatus.CHARGING), list.of(BatteryComponent.LEFT))
        assertEquals(Battery(BatteryComponent.RIGHT, 100, BatteryStatus.NOT_CHARGING), list.of(BatteryComponent.RIGHT))
        assertEquals(Battery(BatteryComponent.CASE, 17, BatteryStatus.NOT_CHARGING), list.of(BatteryComponent.CASE))
        // The app always expects left, right, case in that order.
        assertEquals(listOf(BatteryComponent.LEFT, BatteryComponent.RIGHT, BatteryComponent.CASE), list.map { it.component })
    }

    @Test
    fun batteryReportWithoutTheCaseStillUpdatesTheBuds() {
        val b = AirPodsNotifications.BatteryNotification()
        b.setBattery(hex("04 00 04 00 04 00 03 02 01 64 02 01 04 01 63 01 01 08 01 11 02 01"))
        // Later report with only the two buds (case out of range): used to be ignored entirely.
        b.setBattery(hex("04 00 04 00 04 00 02 04 01 50 02 01 02 01 4B 02 01"))
        val list = b.getBattery()
        assertEquals(80, list.of(BatteryComponent.LEFT).level)
        assertEquals(75, list.of(BatteryComponent.RIGHT).level)
        assertEquals(17, list.of(BatteryComponent.CASE).level)
        assertEquals(BatteryStatus.DISCONNECTED, list.of(BatteryComponent.CASE).status)
    }

    @Test
    fun singleBudReport() {
        val b = AirPodsNotifications.BatteryNotification()
        b.setBattery(hex("04 00 04 00 04 00 01 04 01 2A 02 01"))
        val list = b.getBattery()
        assertEquals(42, list.of(BatteryComponent.LEFT).level)
        assertEquals(BatteryStatus.DISCONNECTED, list.of(BatteryComponent.RIGHT).status)
    }

    @Test
    fun invalidLevelKeepsTheLastKnownOne() {
        val b = AirPodsNotifications.BatteryNotification()
        b.setBattery(hex("04 00 04 00 04 00 03 04 01 50 02 01 02 01 50 02 01 08 01 3C 02 01"))
        b.setBattery(hex("04 00 04 00 04 00 03 04 01 50 02 01 02 01 50 02 01 08 01 FF 02 01"))
        assertEquals(60, b.getBattery().of(BatteryComponent.CASE).level)
    }

    @Test
    fun truncatedBatteryReportIsIgnoredSafely() {
        val b = AirPodsNotifications.BatteryNotification()
        b.setBattery(hex("04 00 04 00 04 00 03 04 01 50"))
        assertEquals(BatteryStatus.DISCONNECTED, b.getBattery().of(BatteryComponent.LEFT).status)
    }

    @Test
    fun listeningModeMessage() {
        // "04 00 04 00 09 00 0D [mode] 00 00 00", 02 = noise cancellation.
        val cmd = AACPManager.ControlCommand.fromByteArray(hex("04 00 04 00 09 00 0D 02 00 00 00"))
        assertEquals(AACPManager.Companion.ControlCommandIdentifiers.LISTENING_MODE.value, cmd.identifier)
        assertEquals(2, cmd.value[0].toInt())
        val anc = AirPodsNotifications.ANC()
        anc.setStatus(byteArrayOf(cmd.value[0]))
        assertEquals(2, anc.status)
    }

    @Test
    fun conversationAwarenessStateMessage() {
        val cmd = AACPManager.ControlCommand.fromByteArray(hex("04 00 04 00 09 00 28 01 00 00 00"))
        assertEquals(AACPManager.Companion.ControlCommandIdentifiers.CONVERSATION_DETECT_CONFIG.value, cmd.identifier)
        assertEquals(1, cmd.value[0].toInt())
    }

    @Test
    fun earDetectionMessage() {
        val ear = AirPodsNotifications.EarDetection()
        // Primary in ear (00), secondary in case (02).
        ear.setStatus(hex("04 00 04 00 06 00 00 02"))
        assertEquals(listOf<Byte>(0x00, 0x02), ear.status)
    }

    @Test
    fun deviceInformationFromProtocolNotes() {
        val packet = hex(
            "04 00 04 00 1d 00 02 d5 00 04 00 41 69 72 50 6f 64 73 20 50 72 6f 00 41 33 30 34 38 00 " +
                "41 70 70 6c 65 20 49 6e 63 2e 00 51 58 4e 52 48 48 59 58 50 36 00 36 31 2e 31 38 36 38 " +
                "30 34 30 30 30 32 30 30 30 30 30 30 2e 32 37 31 33 00"
        )
        val info = AACPManager().parseInformationPacket(packet)
        assertEquals("AirPods Pro", info.name)
        assertEquals("A3048", info.modelNumber)
        assertEquals("Apple Inc.", info.manufacturer)
        assertEquals("QXNRHHYXP6", info.serialNumber)
    }

    @Test
    fun airPodsPro3ModelNumbersAreRecognised() {
        // Apple's model numbers for AirPods Pro 3: A3063 (left), A3064 (right), A3065 (case).
        for (number in listOf("A3063", "A3064", "A3065")) {
            val model = AirPodsModels.getModelByModelNumber(number)
            assertNotNull("$number not recognised", model)
            assertEquals("AirPods Pro 3", model!!.name)
        }
        val caps = AirPodsModels.getModelByModelNumber("A3064")!!.capabilities
        assertTrue(caps.contains(me.kavishdevar.librepods.data.Capability.LISTENING_MODE))
        assertTrue(caps.contains(me.kavishdevar.librepods.data.Capability.CONVERSATION_AWARENESS))
        assertTrue(caps.contains(me.kavishdevar.librepods.data.Capability.HEAD_GESTURES))
    }
}
