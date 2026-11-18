package me.kavishdevar.librepods

import me.kavishdevar.librepods.breathing.BreathTimeline
import me.kavishdevar.librepods.breathing.BreathTimeline.Phase
import me.kavishdevar.librepods.breathing.BreathTransition
import org.junit.Assert.*
import org.junit.Test

class BreathTimelineTest {
    private fun fade(ms: Long = 3000, reduced: Boolean = false) = BreathTimeline(ms, BreathTransition.Fade, reduced)

    @Test fun fractionIsClampedAndScalesWithTheChosenLength() {
        assertEquals(0f, fade().fraction(-50), 0f)
        assertEquals(0.5f, fade().fraction(1500), 1e-6f)
        assertEquals(1f, fade().fraction(99_999), 0f)
        assertEquals(0.5f, fade(8000).fraction(4000), 1e-6f)
    }
    @Test fun theColorRisesSmoothlyFromNothingToFullByTheMiddle() {
        val t = fade()
        assertEquals(0f, t.sheet(0f), 0f)
        assertEquals(1f, t.sheet(0.5f), 1e-6f)
        assertEquals(1f, t.sheet(1f), 0f)
        var previous = -1f
        for (i in 0..50) { val v = t.sheet(i / 100f); assertTrue("rises, never falls", v >= previous); previous = v }
        assertTrue("starts softly", t.sheet(0.02f) < 0.01f)
        assertTrue("lands softly", t.sheet(0.48f) > 0.99f)
    }
    @Test fun withoutMotionTheColorIsFullFromTheFirstFrame() {
        assertEquals(1f, fade(reduced = true).sheet(0f), 0f)
    }
    @Test fun fadeIsSolidThroughTheInhaleThenClearsAndEndsAtExactlyZero() {
        val t = fade()
        assertEquals(1f, t.layerAlpha(0f), 0f)
        assertEquals(1f, t.layerAlpha(0.52f), 0f)
        assertEquals(0f, t.layerAlpha(1f), 0f)
        var previous = 2f
        for (i in 0..100) { val a = t.layerAlpha(i / 100f); assertTrue("opacity never rises ($i)", a <= previous + 1e-6f); previous = a }
        for (i in 1..100) assertTrue("no cliff", t.layerAlpha((i - 1) / 100f) - t.layerAlpha(i / 100f) < 0.1f)
        assertEquals(0f, t.lift(0.8f), 0f)
    }
    @Test fun liftKeepsFullOpacityAndRisesToTheTopByTheEnd() {
        val t = BreathTimeline(3000, BreathTransition.Lift, false)
        assertEquals(1f, t.layerAlpha(0.9f), 0f)
        assertEquals(0f, t.lift(0.52f), 0f)
        assertEquals(1f, t.lift(1f), 0f)
        assertTrue(t.lift(0.76f) in 0.3f..0.7f)
    }
    @Test fun liftWithoutMotionBecomesAFadeAndInstantNeverClearsEarly() {
        val still = BreathTimeline(3000, BreathTransition.Lift, true)
        assertEquals(0f, still.lift(0.9f), 0f)
        assertEquals(0f, still.layerAlpha(1f), 0f)
        val instant = BreathTimeline(3000, BreathTransition.Instant, false)
        assertEquals(1f, instant.layerAlpha(0.999f), 0f)
        assertEquals(0f, instant.lift(0.999f), 0f)
    }
    @Test fun theWordsChangeAtTheMiddleAndTheExitStartsAfterThem() {
        val t = fade()
        assertEquals(Phase.In, t.phase(0.499f))
        assertEquals(Phase.Out, t.phase(0.5f))
        assertEquals(1500L, t.switchMs())
        assertTrue(BreathTimeline.EXIT_FROM > BreathTimeline.EXHALE_AT)
    }
    @Test fun theFirstMomentArrivesAtOnceAndTheSecondAfterItLeaves() {
        val t = fade()
        assertEquals(0f, t.arriving(0, Phase.In).alpha, 0f)
        assertEquals(1f, t.arriving(BreathTimeline.ARRIVE_MS, Phase.In).alpha, 1e-6f)
        assertEquals(1f, t.leaving(1400).alpha, 0f)
        assertEquals(0f, t.leaving(1500 + BreathTimeline.LEAVE_MS).alpha, 1e-6f)
        assertEquals(0f, t.arriving(1500, Phase.Out).alpha, 0f)
        assertEquals(1f, t.arriving(1500 + BreathTimeline.OUT_DELAY_MS + BreathTimeline.ARRIVE_MS, Phase.Out).alpha, 1e-6f)
    }
    @Test fun textRisesIntoPlaceOnlyWhenMotionIsOn() {
        assertTrue(fade().arriving(0, Phase.In).rise > 0f)
        assertEquals(0f, fade(reduced = true).arriving(0, Phase.In).rise, 0f)
        assertTrue(fade().leaving(1500 + BreathTimeline.LEAVE_MS).rise < 0f)
    }
    @Test fun theEasingCurvesStartAndEndExactly() {
        listOf(BreathTimeline::smoother, BreathTimeline::easeInOut, BreathTimeline::easeOut, BreathTimeline::easeIn).forEach { f ->
            assertEquals(0f, f(0f), 1e-6f); assertEquals(1f, f(1f), 1e-6f)
        }
    }
}
