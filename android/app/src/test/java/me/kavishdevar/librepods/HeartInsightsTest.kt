package me.kavishdevar.librepods

import me.kavishdevar.librepods.services.HeartInsights
import me.kavishdevar.librepods.services.HeartInsights.Zone
import me.kavishdevar.librepods.services.HeartRate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HeartInsightsTest {
    @Test fun zonesFollowTheAhaPercentages() {
        // Age 30: max 190. 50% = 95, 70% = 133, 85% = 161.5
        assertEquals(190, HeartInsights.maxHeartRate(30))
        assertEquals(Zone.Light, HeartInsights.zone(94, 30))
        assertEquals(Zone.Moderate, HeartInsights.zone(95, 30))
        assertEquals(Zone.Moderate, HeartInsights.zone(132, 30))
        assertEquals(Zone.Vigorous, HeartInsights.zone(133, 30))
        assertEquals(Zone.Vigorous, HeartInsights.zone(161, 30))
        assertEquals(Zone.Peak, HeartInsights.zone(162, 30))
    }

    @Test fun timeInZonesCountsSeconds() {
        val s = (0 until 120).map { HeartRate.Sample(it * 1000L, if (it < 60) 80 else 140) }
        val t = HeartInsights.timeInZones(s, 30)
        assertEquals(60L, t[Zone.Light])
        assertEquals(60L, t[Zone.Vigorous])
    }

    @Test fun trendNeedsEnoughDataAndAClearChange() {
        val now = 300_000L
        val rising = (0..300).map { HeartRate.Sample(it * 1000L, if (it > 180) 110 else 90) }
        assertEquals(HeartInsights.Trend.Rising, HeartInsights.trend(rising, now))
        val steady = (0..300).map { HeartRate.Sample(it * 1000L, 90 + it % 3) }
        assertEquals(HeartInsights.Trend.Steady, HeartInsights.trend(steady, now))
        assertNull(HeartInsights.trend(steady.takeLast(10), now))
    }

    @Test fun sessionsUnderAMinuteAreNotSaved() {
        assertNull(HeartInsights.summarize((0 until 30).map { HeartRate.Sample(it * 1000L, 80) }))
        val s = HeartInsights.summarize((0..90).map { HeartRate.Sample(it * 1000L, 70 + it % 11) })!!
        assertEquals(70, s.min); assertEquals(80, s.max)
        assertEquals(s, HeartInsights.Session.decode(s.encode()))
    }

    @Test fun oldSummaryLinesStillDecode() {
        val old = HeartInsights.Session.decode("1000,61000,72,60,90")!!
        assertEquals(0, old.readings); assertEquals(0, old.resting); assertEquals(72, old.average)
        assertNull(HeartInsights.Session.decode("garbage"))
    }

    @Test fun csvRoundTripsBothLayouts() {
        val s = (0 until 50).map { HeartRate.Sample(1_700_000_000_000L + it * 5000L, 60 + it) }
        assertEquals(s, HeartInsights.parseCsv(HeartInsights.compactCsv(s)))
        assertEquals(s, HeartInsights.parseCsv(HeartInsights.csv(s))) // the export layout
        // Junk and impossible readings are skipped.
        assertEquals(1, HeartInsights.parseCsv("time_ms,bpm\n5,300\nx,y\n10,70\n").size)
    }

    @Test fun meaningUsesRestingRangeThenZones() {
        assertEquals(HeartInsights.Band.Resting, HeartInsights.meaning(72, 0, 0).band)
        assertEquals(HeartInsights.Band.Low, HeartInsights.meaning(55, 0, 0).band)
        assertEquals(HeartInsights.Band.Raised, HeartInsights.meaning(110, 0, 0).band)
        assertEquals(HeartInsights.Band.High, HeartInsights.meaning(130, 0, 0).band)
        // With an age, effort zones take over above the light zone (age 30: moderate from 95).
        assertEquals("Moderate effort", HeartInsights.meaning(110, 30, 0).headline)
        assertEquals("Near your maximum", HeartInsights.meaning(170, 30, 0).headline)
        // Compared with your usual resting rate.
        assert(HeartInsights.meaning(80, 0, 64).detail.contains("16 above your usual"))
        assert(HeartInsights.meaning(66, 0, 64).detail.contains("Right around"))
    }

    @Test fun usualRestingNeedsThreeRecentSessions() {
        val day = 86_400_000L
        val now = 40 * day
        fun s(start: Long, r: Int) = HeartInsights.Session(start, start + 600_000, 70, 60, 90, 100, r)
        assertNull(HeartInsights.usualResting(listOf(s(now - day, 60), s(now - 2 * day, 62)), now))
        assertEquals(62, HeartInsights.usualResting(listOf(s(now - day, 60), s(now - 2 * day, 62), s(now - 3 * day, 70), s(now - 35 * day, 40)), now))
    }

    @Test fun daysGroupByLocalDateAndWeightByLength() {
        val z = java.time.ZoneOffset.UTC
        val d0 = java.time.LocalDate.of(2026, 9, 30).atStartOfDay(z).toInstant().toEpochMilli()
        val a = HeartInsights.Session(d0 + 3_600_000, d0 + 3 * 3_600_000, 60, 50, 80, 10, 55) // 2 h at 60
        val b = HeartInsights.Session(d0 + 5 * 3_600_000, d0 + 5 * 3_600_000 + 1_200_000, 120, 90, 150, 10, 0) // 20 min at 120
        val c = HeartInsights.Session(d0 + 86_400_000, d0 + 86_400_000 + 600_000, 70, 65, 75, 10, 0)
        val days = HeartInsights.days(listOf(a, b, c), z)
        assertEquals(2, days.size)
        assertEquals(d0 + 86_400_000, days[0].dayStartMs) // newest first
        val first = days[1]
        assertEquals(50, first.min); assertEquals(150, first.max); assertEquals(2, first.sessions)
        assertEquals(69, first.average) // (60*120 + 120*20) / 140 = 68.6
        assertEquals(55, first.resting)
    }

    @Test fun restingEstimateWorksWithBatterySavingBursts() {
        // Balanced pace: 12 readings (one a 5 s) every 5 minutes, for an hour.
        val s = (0 until 12).flatMap { burst -> (0 until 12).map { HeartRate.Sample(burst * 300_000L + it * 5000L, if (burst == 7) 58 else 70) } }
        assertEquals(58, HeartInsights.restingEstimate(s))
        // Continuous data keeps the 3-minute rule.
        val dense = (0 until 400).map { HeartRate.Sample(it * 1000L, if (it in 100..300) 62 else 75) }
        assertEquals(62, HeartInsights.restingEstimate(dense))
    }

    @Test fun peakAverageFindsTheHardestFiveMinutes() {
        // 20 minutes at 70 with a 6-minute stretch at 130 in the middle.
        val s = (0 until 1200).map { HeartRate.Sample(it * 1000L, if (it in 500..860) 130 else 70) }
        assertEquals(130, HeartInsights.peakAverage(s))
        assertNull(HeartInsights.peakAverage(s.take(200))) // under 5 minutes
    }

    @Test fun secondsAboveCountsTimeNotReadings() {
        val s = (0 until 60).map { HeartRate.Sample(it * 5000L, if (it < 12) 120 else 80) } // one a 5 s
        assertEquals(60L, HeartInsights.secondsAbove(s, 100))
    }

    @Test fun recordsAndRestingSeries() {
        val day = 86_400_000L
        val z = java.time.ZoneOffset.UTC
        val now = 50 * day
        val a = HeartInsights.Session(now - 3 * day, now - 3 * day + 600_000, 70, 58, 120, 10, 60)
        val b = HeartInsights.Session(now - 2 * day, now - 2 * day + 3_600_000, 75, 55, 165, 10, 57)
        val c = HeartInsights.Session(now - 40 * day, now - 40 * day + 60_000, 90, 80, 100, 10, 50)
        val r = HeartInsights.records(listOf(a, b, c))
        assertEquals(c, r.lowestResting); assertEquals(b, r.highestPeak); assertEquals(b, r.longest)
        // The 30-day series leaves out the 40-day-old session and runs oldest first.
        assertEquals(listOf(60, 57), HeartInsights.restingSeries(listOf(a, b, c), now, 30, z).map { it.second })
    }
}
