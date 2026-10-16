package me.kavishdevar.librepods

import android.os.PowerManager
import me.kavishdevar.librepods.services.HeartPace
import me.kavishdevar.librepods.services.PowerPlan
import me.kavishdevar.librepods.services.PowerPlan.Level
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** pro's automatic battery plan (Jake, 2026-10-11): efficient by itself, nothing you touch slowed. */
class PowerPlanTest {
    @Test fun chargingIsFullANormalDayIsBalanced() {
        assertEquals(Level.Full, PowerPlan.levelFor(charging = true, battery = 40, powerSave = false, thermal = PowerManager.THERMAL_STATUS_NONE))
        assertEquals(Level.Balanced, PowerPlan.levelFor(charging = false, battery = 80, powerSave = false, thermal = PowerManager.THERMAL_STATUS_LIGHT))
    }

    @Test fun saverOnBatterySaverLowBatteryOrHeat() {
        assertEquals(Level.Saver, PowerPlan.levelFor(false, 80, powerSave = true, thermal = 0))
        assertEquals(Level.Saver, PowerPlan.levelFor(false, PowerPlan.LOW_BATTERY, false, 0))
        assertEquals(Level.Balanced, PowerPlan.levelFor(false, PowerPlan.LOW_BATTERY + 1, false, 0))
        // Warm while charging still eases off (charging makes heat worse).
        assertEquals(Level.Saver, PowerPlan.levelFor(true, 50, false, PowerManager.THERMAL_STATUS_MODERATE))
        // Low but plugged in: full.
        assertEquals(Level.Full, PowerPlan.levelFor(true, 5, false, 0))
    }

    @Test fun thePillsBarsArePacedTheOpenedIslandStaysSmooth() {
        assertEquals(33L, PowerPlan.barsFrameMs(Level.Balanced, synced = true))
        assertEquals(16L, PowerPlan.barsFrameMs(Level.Full, synced = true))
        assertEquals(16L, PowerPlan.barsFrameMs(Level.Balanced, synced = true, big = true))
        // Never slower than the music is heard on a normal day (20 a second = 50 ms).
        assertTrue(PowerPlan.barsFrameMs(Level.Balanced, synced = true) <= 50L)
        assertTrue(Level.entries.all { l -> PowerPlan.barsFrameMs(l, true) <= PowerPlan.barsFrameMs(l, false) })
    }

    @Test fun theModelRestsWhenSaving() {
        assertEquals(33L, PowerPlan.spinFrameMs(Level.Balanced))
        assertNull(PowerPlan.spinFrameMs(Level.Saver))
    }

    @Test fun listeningAndHeartEaseOffOnlyWhenSaving() {
        assertEquals(20_000, PowerPlan.listenRateMilliHz(Level.Balanced, 20_000))
        assertEquals(10_000, PowerPlan.listenRateMilliHz(Level.Saver, 20_000))
        assertEquals(HeartPace.Smart, PowerPlan.heartPace(HeartPace.Continuous, Level.Saver, background = true))
        assertEquals(HeartPace.Continuous, PowerPlan.heartPace(HeartPace.Continuous, Level.Saver, background = false))
        assertEquals(HeartPace.Saver, PowerPlan.heartPace(HeartPace.Saver, Level.Saver, background = true))
        assertEquals(HeartPace.Continuous, PowerPlan.heartPace(HeartPace.Continuous, Level.Balanced, background = true))
    }
}
