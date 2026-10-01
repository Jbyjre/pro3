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
}
