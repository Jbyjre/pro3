package me.kavishdevar.librepods

import me.kavishdevar.librepods.presentation.glint.GlassTilt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassTiltTest {
    @Test fun levelPhoneKeepsTheLightOverhead() {
        assertEquals(0f, GlassTilt.swingFor(0f), 0.001f)
        assertEquals(0f, GlassTilt.smooth(0.1f, 0f), 0f)
    }

    @Test fun tiltSwingsTheLightAgainstItAndIsCapped() {
        // 30 degrees left edge down: light swings about 15 degrees the other way, capped at 14.
        val g = (9.80665 * kotlin.math.sin(Math.toRadians(30.0))).toFloat()
        assertEquals(-GlassTilt.MAX, GlassTilt.swingFor(g), 0.01f)
        val small = (9.80665 * kotlin.math.sin(Math.toRadians(10.0))).toFloat()
        assertEquals(-5f, GlassTilt.swingFor(small), 0.05f)
        assertEquals(GlassTilt.MAX, GlassTilt.swingFor(-20f), 0.01f)
    }

    @Test fun smoothingDriftsInsteadOfJumping() {
        val next = GlassTilt.smooth(0f, 10f)
        assertTrue(next > 0f && next < 2f)
    }
}
