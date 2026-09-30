package me.kavishdevar.librepods

import me.kavishdevar.librepods.services.BatteryEstimate
import me.kavishdevar.librepods.services.BatteryEstimator
import me.kavishdevar.librepods.services.BatteryEstimator.Confidence
import me.kavishdevar.librepods.services.BatteryEstimator.Reading
import me.kavishdevar.librepods.services.BatteryWords
import me.kavishdevar.librepods.services.CardGate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

private const val MIN = 60_000L
private const val ANC = 2

private fun reading(
    t: Long, left: Int?, right: Int? = left, case: Int? = 80,
    charging: Boolean = false, worn: Boolean = true, mode: Int = ANC,
) = Reading(t, left, right, case, charging, charging, worn, worn, mode)

private class MemoryStore : BatteryEstimator.RateStore {
    val rates = mutableMapOf<Int, Float>()
    override fun load(mode: Int) = rates[mode]
    override fun save(mode: Int, percentPerHour: Float) { rates[mode] = percentPerHour }
}

class BatteryEstimatorTest {
    @Test fun fullBudsStartAtApplesRating() {
        val e = BatteryEstimator().update(reading(0, 100))!!
        assertEquals(480, e.minutesLeft) // 8 h
        assertEquals(Confidence.RATED, e.confidence)
    }

    @Test fun noDataGivesNoEstimate() {
        assertNull(BatteryEstimator().update(reading(0, null, null, null)))
    }

    /** Buds that drain 1% every 3 minutes (20%/h, i.e. 5 h per charge) instead of Apple's 4.8. */
    @Test fun steadyDrainIsMeasured() {
        val est = BatteryEstimator()
        var e: BatteryEstimate? = null
        var level = 90
        var t = 0L
        est.update(reading(t, level))
        repeat(15) { t += 3 * MIN; level -= 1; e = est.update(reading(t, level)) }
        // 45 min of measured drain: fully trusted. 75% at 20%/h = 225 min.
        assertEquals(Confidence.MEASURED, e!!.confidence)
        assertTrue("got ${e!!.minutesLeft}", abs(e!!.minutesLeft!! - 225) <= 2)
    }

    @Test fun earlyMeasurementBlendsWithRating() {
        val est = BatteryEstimator()
        est.update(reading(0, 90))
        est.update(reading(3 * MIN, 89))
        est.update(reading(6 * MIN, 88))
        val e = est.update(reading(9 * MIN, 87))!!
        assertEquals(Confidence.LEARNING, e.confidence)
        val rated = 87 / 12.5 * 60
        val measured = 87 / 20.0 * 60
        assertTrue(e.minutesLeft!! < rated && e.minutesLeft!! > measured)
    }

    @Test fun aLongPauseSinceTheLastDropSlowsTheRate() {
        val est = BatteryEstimator()
        var t = 0L
        est.update(reading(t, 90))
        for (l in 89 downTo 80) { t += 3 * MIN; est.update(reading(t, l)) }
        val busy = est.update(reading(t, 80))!!.minutesLeft!!
        // Twenty quiet minutes (e.g. paused music) without a drop.
        val quiet = est.update(reading(t + 20 * MIN, 80))!!.minutesLeft!!
        assertTrue(quiet > busy)
    }

    @Test fun theBudThatRunsOutFirstDecides() {
        val est = BatteryEstimator()
        var t = 0L
        est.update(Reading(t, 90, 60, 80, false, false, true, true, ANC))
        val e = est.update(Reading(t + MIN, 90, 60, 80, false, false, true, true, ANC))!!
        assertEquals(288, e.minutesLeft) // 60% of 8 h
    }

    @Test fun onlyTheWornBudCounts() {
        val e = BatteryEstimator().update(Reading(0, 20, 90, 80, false, false, false, true, ANC))!!
        assertEquals(432, e.minutesLeft) // 90% of 8 h: the left bud (20%) is not in use
    }

    @Test fun modeChangeStartsAFreshMeasurement() {
        val est = BatteryEstimator()
        var t = 0L
        est.update(reading(t, 90))
        for (l in 89 downTo 80) { t += 3 * MIN; est.update(reading(t, l)) }
        val e = est.update(reading(t + MIN, 80, mode = 3))!!
        assertEquals(Confidence.RATED, e.confidence)
    }

    @Test fun finishedSessionsTeachTheRate() {
        val store = MemoryStore()
        val est = BatteryEstimator(store)
        var t = 0L
        est.update(reading(t, 90))
        for (l in 89 downTo 70) { t += 3 * MIN; est.update(reading(t, l)) }
        est.update(reading(t + MIN, 70, charging = true)) // back in the case
        assertEquals(20f, store.rates[ANC]!!, 0.5f)
        // Next time, the starting estimate uses what it learned: 100% at 20%/h = 5 h.
        val e = BatteryEstimator(store).update(reading(0, 100))!!
        assertEquals(300, e.minutesLeft)
    }

    @Test fun chargingMeasuresTimeToFull() {
        val est = BatteryEstimator()
        var e = est.update(reading(0, 40, charging = true, worn = false))!!
        assertTrue(e.charging)
        assertNull(e.minutesToFull)
        for (i in 1..4) e = est.update(reading(i * 2 * MIN, 40 + i, charging = true, worn = false))!!
        // 1% every 2 minutes; 56% to go
        assertEquals(112, e.minutesToFull)
        assertEquals("Full in about 1 h 50 min", BatteryWords.headline(e))
    }

    @Test fun jumpingLevelsUpResetsTheDrainMeasurement() {
        val est = BatteryEstimator()
        var t = 0L
        est.update(reading(t, 90))
        for (l in 89 downTo 80) { t += 3 * MIN; est.update(reading(t, l)) }
        assertEquals(Confidence.RATED, est.update(reading(t + MIN, 85))!!.confidence)
    }
}

class BatteryWordsTest {
    @Test fun roundsHonestly() {
        assertEquals("under 10 min", BatteryWords.duration(7))
        assertEquals("25 min", BatteryWords.duration(23))
        assertEquals("1 h", BatteryWords.duration(62))
        assertEquals("3 h 20 min", BatteryWords.duration(197))
        assertEquals("8 h", BatteryWords.duration(480))
    }

    @Test fun caseCharges() {
        fun detail(case: Int) = BatteryWords.detail(BatteryEstimate(100, Confidence.RATED, true, false, BatteryEstimator.caseCharges(case)))
        assertTrue(detail(100).endsWith("Case: about 2 more full charges"))
        assertTrue(detail(50).endsWith("Case: about 1 more full charge"))
        assertTrue(detail(80).endsWith("Case: about 1.5 more full charges"))
        assertTrue(detail(10).endsWith("less than half a charge left"))
    }
}

class CardGateTest {
    @Test fun firstOpenShowsOnce() {
        val g = CardGate()
        assertTrue(g.onLidOpened(0, bothInEar = false))
        assertFalse(g.onLidOpened(1_000, bothInEar = false))
    }

    @Test fun shortAdvertGapsDoNotReArm() {
        val g = CardGate()
        assertTrue(g.onLidOpened(0, false))
        // Samsung batches adverts: a 3 s gap looks like "closed", then "open" again.
        g.onLidClosed(100_000)
        assertFalse(g.onLidOpened(103_000, false))
    }

    @Test fun realCloseAndReopenShowsAgain() {
        val g = CardGate()
        assertTrue(g.onLidOpened(0, false))
        g.onLidClosed(100_000)
        assertTrue(g.onLidOpened(115_000, false))
    }

    @Test fun cooldownLimitsToOnePerMinute() {
        val g = CardGate()
        assertTrue(g.onLidOpened(0, false))
        g.onLidClosed(1_000)
        assertFalse(g.onLidOpened(20_000, false)) // closed 19 s, but within 60 s of the last card
    }

    @Test fun ignoredWhileBothBudsAreWorn() {
        val g = CardGate()
        assertFalse(g.onLidOpened(0, bothInEar = true))
        assertTrue(g.onLidOpened(1_000, bothInEar = false))
    }

    @Test fun estimateIsPresentForFullBuds() {
        assertNotNull(BatteryEstimator().update(reading(0, 100)))
    }
}
