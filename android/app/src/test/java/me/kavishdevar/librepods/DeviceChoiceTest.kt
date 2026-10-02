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

package me.kavishdevar.librepods

import android.app.Application
import android.content.Context
import me.kavishdevar.librepods.bluetooth.AirPodsDetection
import me.kavishdevar.librepods.bluetooth.HeadphoneBeacon
import me.kavishdevar.librepods.presentation.glint.FrameRate
import me.kavishdevar.librepods.presentation.overlays.GlintOverlays
import me.kavishdevar.librepods.services.ChosenDevice
import me.kavishdevar.librepods.services.DeviceChoice
import me.kavishdevar.librepods.services.DeviceChoice.Route
import me.kavishdevar.librepods.services.DeviceKind
import me.kavishdevar.librepods.services.HeadphoneLink
import me.kavishdevar.librepods.services.HeadphoneState
import me.kavishdevar.librepods.services.PREF_DEVICE_KIND
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Which device pro follows, and that each device's controls stay with that device. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class DeviceChoiceTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val prefs get() = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val airPodsMac = "AA:BB:CC:DD:EE:FF"
    private val beatsMac = "00:11:22:33:44:55"
    private val solo4 = ChosenDevice(DeviceKind.BEATS_SOLO_4, beatsMac, "Beats Solo 4")

    @Before fun setUp() {
        prefs.edit().clear().commit()
        DeviceChoice.reset()
    }

    @After fun tearDown() {
        prefs.edit().clear().commit()
        DeviceChoice.reset()
    }

    // ---- What kind of device is it ----

    @Test fun solo4IsRecognisedByName() {
        assertEquals(DeviceKind.BEATS_SOLO_4, DeviceChoice.kindOf("Beats Solo 4", null))
        assertEquals(DeviceKind.BEATS_SOLO_4, DeviceChoice.kindOf("Jake’s Solo 4", null))
        assertEquals(DeviceKind.BEATS_SOLO_4, DeviceChoice.kindOf("Beats Solo4", null))
    }

    @Test fun otherSoloModelsAreNotTheSolo4() {
        assertEquals(DeviceKind.HEADPHONES, DeviceChoice.kindOf("Beats Solo3", null))
        assertEquals(DeviceKind.HEADPHONES, DeviceChoice.kindOf("Beats Solo Pro", null))
        assertEquals(DeviceKind.HEADPHONES, DeviceChoice.kindOf("Beats Solo Buds", null))
        assertEquals(DeviceKind.HEADPHONES, DeviceChoice.kindOf("Beats Solo 40th", null))
    }

    @Test fun beatsNeverCountAsAirPodsEvenWithApplesService() {
        // Some Beats list Apple's control service; they must not get the AirPods controls.
        assertEquals(DeviceKind.BEATS_SOLO_4, DeviceChoice.kindOf("Beats Solo 4", listOf(AirPodsDetection.AAP_UUID)))
        assertEquals(DeviceKind.HEADPHONES, DeviceChoice.kindOf("Powerbeats Pro", listOf(AirPodsDetection.AAP_UUID)))
    }

    @Test fun airPodsAndOthers() {
        assertEquals(DeviceKind.AIRPODS, DeviceChoice.kindOf("Jake's AirPods Pro", null))
        assertEquals(DeviceKind.AIRPODS, DeviceChoice.kindOf("Pods", listOf(AirPodsDetection.AAP_UUID)))
        assertEquals(DeviceKind.HEADPHONES, DeviceChoice.kindOf("WH-1000XM5", null))
        assertEquals(DeviceKind.HEADPHONES, DeviceChoice.kindOf(null, null))
    }

    @Test fun onlyAirPodsUseTheAirPodsLink() {
        assertTrue(DeviceKind.AIRPODS.usesAirPodsLink)
        assertFalse(DeviceKind.BEATS_SOLO_4.usesAirPodsLink)
        assertFalse(DeviceKind.HEADPHONES.usesAirPodsLink)
        assertEquals(0x2520, DeviceKind.BEATS_SOLO_4.beaconModel)
        assertNull(DeviceKind.HEADPHONES.beaconModel)
    }

    // ---- The choice is kept until changed ----

    @Test fun nothingChosenMeansAirPodsAsBefore() {
        prefs.edit().putString("mac_address", airPodsMac).putString("name", "Jake's AirPods").commit()
        val c = DeviceChoice.read(prefs)
        assertEquals(DeviceKind.AIRPODS, c.kind)
        assertEquals(airPodsMac, c.address)
        assertEquals("Jake's AirPods", c.name)
        assertTrue(DeviceChoice.followsAirPods(context))
    }

    @Test fun choiceSurvivesARestart() {
        assertTrue(DeviceChoice.choose(context, solo4))
        DeviceChoice.reset() // a new process: nothing cached
        assertEquals(solo4, DeviceChoice.current(context))
        assertFalse(DeviceChoice.followsAirPods(context))
    }

    @Test fun choosingTheSameDeviceAgainIsNotAChange() {
        DeviceChoice.choose(context, solo4)
        assertFalse(DeviceChoice.choose(context, solo4.copy(address = beatsMac.lowercase())))
    }

    @Test fun choosingAirPodsKeepsTheirSavedAddress() {
        DeviceChoice.choose(context, ChosenDevice(DeviceKind.AIRPODS, airPodsMac, "AirPods Pro"))
        assertEquals(airPodsMac, prefs.getString("mac_address", null))
        // And choosing Beats leaves the AirPods' saved address alone for when you come back.
        DeviceChoice.choose(context, solo4)
        assertEquals(airPodsMac, prefs.getString("mac_address", null))
    }

    @Test fun unknownSavedKindFallsBackToAirPods() {
        prefs.edit().putString(PREF_DEVICE_KIND, "SOMETHING_NEWER").commit()
        assertEquals(DeviceKind.AIRPODS, DeviceChoice.read(prefs).kind)
    }

    // ---- Controls never cross over ----

    @Test fun withAirPodsChosenBeatsAreLeftAlone() {
        val chosen = ChosenDevice(DeviceKind.AIRPODS, airPodsMac, "AirPods")
        assertEquals(Route.IGNORE, DeviceChoice.route(chosen, beatsMac, "Beats Solo 4", listOf(AirPodsDetection.AAP_UUID)))
        assertEquals(Route.AIRPODS, DeviceChoice.route(chosen, airPodsMac, "AirPods Pro", null))
        // Renamed AirPods without cached services still reach the old detection.
        assertEquals(Route.AIRPODS, DeviceChoice.route(chosen, "11:11:11:11:11:11", "Jake's Pods", null))
    }

    @Test fun withBeatsChosenAirPodsAreLeftAlone() {
        assertEquals(Route.IGNORE, DeviceChoice.route(solo4, airPodsMac, "AirPods Pro", listOf(AirPodsDetection.AAP_UUID)))
        assertEquals(Route.HEADPHONES, DeviceChoice.route(solo4, beatsMac.lowercase(), "Beats Solo 4", null))
        assertEquals(Route.IGNORE, DeviceChoice.route(solo4, "22:22:22:22:22:22", "Car audio", null))
        assertEquals(Route.IGNORE, DeviceChoice.route(solo4, null, "Beats Solo 4", null))
    }

    @Test fun overlaysDropTheAirPodsWhileBeatsAreChosen() {
        DeviceChoice.choose(context, solo4)
        assertFalse(GlintOverlays.airPodsAudio(context))
    }

    // ---- Beats Solo 4 beacon ----

    /** CAPod's Beats Solo 4 sample (model 0x2520, 40%). */
    private val capodSample = "07 19 01 25 20 62 04 80 01 0F 40 0D 70 50 16 F2 40 83 16 BF 10 16 34 9B 74 84 E8"
        .split(" ").map { it.toInt(16).toByte() }.toByteArray()

    @Test fun decodesTheSolo4Beacon() {
        val r = HeadphoneBeacon.decode("X", capodSample, rssi = -50, at = 0L)!!
        assertEquals(0x2520, r.model)
        assertEquals(40, r.battery)
    }

    @Test fun ignoresOtherMessages() {
        assertNull(HeadphoneBeacon.decode("X", byteArrayOf(0x10, 0x05, 1, 2, 3, 4, 5), -50, 0L))
        assertNull(HeadphoneBeacon.decode("X", byteArrayOf(0x07, 0x19), -50, 0L))
        val unknown = capodSample.copyOf().also { it[6] = 0x0F }
        assertNull(HeadphoneBeacon.decode("X", unknown, -50, 0L)!!.battery)
    }

    @Test fun gateTrustsOnlyCloseConnectedSameModel() {
        val gate = HeadphoneBeacon.Gate()
        val near = HeadphoneBeacon.decode("A", capodSample, rssi = -45, at = 1_000L)!!
        assertNull("not connected", gate.accept(near, 0x2520, connected = false))
        assertNull("other model chosen", gate.accept(near, 0x2620, connected = true))
        assertNull("nothing to match", gate.accept(near, null, connected = true))
        assertEquals(40, gate.accept(near, 0x2520, connected = true))
        val far = near.copy(rssi = -80, at = 2_000L)
        assertNull("too far away", HeadphoneBeacon.Gate().accept(far, 0x2520, connected = true))
    }

    @Test fun gateDistrustsTwoSolo4sNearby() {
        val gate = HeadphoneBeacon.Gate()
        val mine = HeadphoneBeacon.decode("A", capodSample, rssi = -45, at = 1_000L)!!
        val friends = mine.copy(address = "B", battery = 90, rssi = -50, at = 2_000L)
        assertEquals(40, gate.accept(mine, 0x2520, connected = true))
        assertNull(gate.accept(friends, 0x2520, connected = true))
        assertNull(gate.accept(mine.copy(at = 3_000L), 0x2520, connected = true))
        // The friend leaves (no beacon for longer than the window): trusted again.
        assertEquals(40, gate.accept(mine.copy(at = 40_000L), 0x2520, connected = true))
    }

    // ---- Low battery, refresh rate, overlay data ----

    @Test fun lowBatteryAnnouncesTwentyAndTenOnce() {
        val latch = HeadphoneLink.LowBatteryLatch()
        assertNull(latch.check(60, false))
        assertEquals(20, latch.check(20, false))
        assertNull(latch.check(18, false))
        assertEquals(10, latch.check(10, false))
        assertNull(latch.check(9, false))
        assertNull(latch.check(null, false))
        assertNull(latch.check(30, false)) // charged past 25%: armed again
        assertEquals(15, latch.check(15, false))
        assertNull(HeadphoneLink.LowBatteryLatch().check(10, charging = true))
    }

    @Test fun picksTheFastestRateAtTheSameResolution() {
        val modes = listOf(
            Triple("60", 1080 to 2400, 60f),
            Triple("120", 1080 to 2400, 120f),
            Triple("144-other-res", 720 to 1600, 144f),
        )
        assertEquals("120", FrameRate.pickBy(1080, 2400, modes))
        assertNull("60 Hz only: nothing to ask for", FrameRate.pickBy(1080, 2400, modes.take(1)))
        assertNull(FrameRate.pickBy(1440, 3200, modes))
    }

    @Test fun headphonesSnapshotHasOneBatteryAndNoCase() {
        val s = HeadphoneState(solo4, connected = true, battery = 70).snapshot()
        assertTrue(s.headphones)
        assertEquals(70, s.budsLevel)
        assertNull(s.case)
        assertEquals("Beats Solo 4", s.name)
        assertEquals(0, s.listeningMode)
    }
}
