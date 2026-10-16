package me.kavishdevar.librepods

import me.kavishdevar.librepods.services.HeartInsights
import me.kavishdevar.librepods.services.HeartPace
import me.kavishdevar.librepods.services.HeartPacing
import me.kavishdevar.librepods.services.HeartRate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Spot checks (Jake, 2026-10-15: one minute every 15 minutes "looks terrible on the graph"; fill
 * it with good information without using too much battery).
 */
class HeartPacingTest {
    @Test fun storedPacesKeepWorking() {
        // 0 and 2 keep their names; 1 (the old Balanced) is now Smart. Anything odd is continuous.
        assertEquals(HeartPace.Continuous, HeartPace.of(0))
        assertEquals(HeartPace.Smart, HeartPace.of(1))
        assertEquals(HeartPace.Saver, HeartPace.of(2))
        assertEquals(HeartPace.Continuous, HeartPace.of(9))
    }

    @Test fun aSpotNeedsThreeReadingsOverTenSeconds() {
        assertFalse(HeartPacing.spotDone(2, 0L, 30_000L))
        assertFalse(HeartPacing.spotDone(3, 0L, 9_000L)) // three readings, but in a blink
        assertTrue(HeartPacing.spotDone(3, 0L, 10_000L))
    }

    @Test fun theSpotIsItsMiddleReading() {
        assertEquals(72, HeartPacing.spotValue(listOf(70, 72, 140)))  // one stray reading can't pull it
        assertEquals(71, HeartPacing.spotValue(listOf(70, 72)))
        assertNull(HeartPacing.spotValue(emptyList()))
    }

    @Test fun smartRestsWhenCalmAndKeepsGoingWhenItMatters() {
        // First spot: the normal two-minute rhythm.
        assertEquals(HeartPacing.SMART_REST_MS, HeartPacing.restAfter(HeartPace.Smart, 70, null, 0))
        // A jump of 8 or more, or 100 and over: no rest, measure straight on.
        assertEquals(0L, HeartPacing.restAfter(HeartPace.Smart, 80, 70, 0))
        assertEquals(0L, HeartPacing.restAfter(HeartPace.Smart, 104, 103, 4))
        // Steady three times in a row: the longer rest.
        var run = 0
        var prev: Int? = null
        for (v in listOf(70, 71, 70, 72)) { run = HeartPacing.steadyRun(v, prev, run); prev = v }
        assertEquals(3, run)
        assertEquals(HeartPacing.SMART_STEADY_REST_MS, HeartPacing.restAfter(HeartPace.Smart, 72, 70, run))
        // A small wobble breaks the steady run but doesn't mean moving.
        assertEquals(0, HeartPacing.steadyRun(76, 70, 3))
        assertEquals(HeartPacing.SMART_REST_MS, HeartPacing.restAfter(HeartPace.Smart, 76, 70, 0))
    }

    @Test fun lowAirPodsEaseSmartToSaversRhythm() {
        assertTrue(HeartPacing.podsLow(listOf(18, 64)))
        assertFalse(HeartPacing.podsLow(listOf(45, 64)))
        assertFalse(HeartPacing.podsLow(emptyList()))      // nothing known: no change
        assertFalse(HeartPacing.podsLow(listOf(-1, 80)))   // unknown levels are ignored
        assertEquals(HeartPacing.SAVER_REST_MS, HeartPacing.restAfter(HeartPace.Smart, 70, 70, 0, podsLow = true))
        // Still checked while it changes, but a minute apart instead of nonstop.
        assertEquals(HeartPacing.LOW_PODS_BUSY_REST_MS, HeartPacing.restAfter(HeartPace.Smart, 110, 70, 0, podsLow = true))
    }

    @Test fun saverIsAFixedRhythmWithTheOldSaversSensorTime() {
        assertEquals(HeartPacing.SAVER_REST_MS, HeartPacing.restAfter(HeartPace.Saver, 130, 70, 0))
        // About 20 s on in each 5 minutes: within a point of the old 1 minute in 15 (6.7%).
        val share = 20_000.0 / (20_000 + HeartPacing.SAVER_REST_MS)
        assertTrue("share $share", share in 0.06..0.075)
    }

    @Test fun spotChecksBecomeOneJoinedPointEach() {
        // An hour of Saver: 3 readings, 5 s apart, every 5 minutes.
        val s = (0 until 12).flatMap { spot -> (0 until 3).map { HeartRate.Sample(spot * 300_000L + it * 5_000L, 70 + it) } }
        val pts = HeartInsights.chartPoints(s)
        assertEquals(12, pts.size)
        assertTrue(pts.all { it.spot && it.bpm == 71 && it.low == 70 && it.high == 72 })
        assertEquals(5_000L, pts.first().timeMs) // the middle of the spot
        // One line through the hour, no breaks.
        assertTrue(pts.zipWithNext().all { (a, b) -> HeartInsights.joined(a, b) })
    }

    @Test fun continuousReadingsStayAsTheyCame() {
        val s = (0 until 600).map { HeartRate.Sample(it * 5_000L, 60 + it % 7) }
        val pts = HeartInsights.chartPoints(s)
        assertEquals(600, pts.size)
        assertTrue(pts.none { it.spot })
    }

    @Test fun mixedSessionsKeepTheDetailWhereItMatters() {
        // Two spot checks, then three minutes of nonstop readings (Smart while it changes).
        val spots = listOf(0L, 120_000L).flatMap { t -> (0 until 3).map { HeartRate.Sample(t + it * 5_000L, 72) } }
        val run = (0 until 36).map { HeartRate.Sample(240_000L + it * 5_000L, 90 + it) }
        val pts = HeartInsights.chartPoints(spots + run)
        assertEquals(2 + 36, pts.size)
        assertEquals(2, pts.count { it.spot })
    }

    @Test fun realGapsStillBreakTheLine() {
        val a = HeartInsights.ChartPoint(0L, 70, 70, 70, true)
        assertFalse(HeartInsights.joined(a, a.copy(timeMs = 25 * 60_000L)))   // buds out for a while
        val d = HeartInsights.ChartPoint(0L, 70, 70, 70, false)
        assertFalse(HeartInsights.joined(d, d.copy(timeMs = 4 * 60_000L)))   // a gap in continuous readings
        assertTrue(HeartInsights.joined(d, d.copy(timeMs = 60_000L)))
    }

    @Test fun restingEstimateFromSpotChecks() {
        // Twelve spots: 70, except three calm ones in a row at 58, 59, 60.
        val values = listOf(70, 70, 70, 58, 59, 60, 70, 70, 70, 70, 70, 70)
        val s = values.flatMapIndexed { i, v -> (0 until 3).map { HeartRate.Sample(i * 300_000L + it * 5_000L, v) } }
        assertEquals(59, HeartInsights.restingEstimate(s))
        // Too few spots: no estimate rather than a guess.
        assertNull(HeartInsights.restingEstimate(s.take(6)))
    }
}
