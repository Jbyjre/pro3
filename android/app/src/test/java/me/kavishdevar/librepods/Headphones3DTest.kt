package me.kavishdevar.librepods

import me.kavishdevar.librepods.presentation.glint.HeadphoneModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

/** 2026-10-11: the Solo 4-shaped 3D model is sound (Jake: "the actual model like a spinning model"). */
class Headphones3DTest {
    private val m = HeadphoneModel.mesh

    @Test fun itFitsOneBatchAndItsBox() {
        // One draw call per frame: every vertex must be reachable by a 16-bit index.
        assertTrue(m.vertices < Short.MAX_VALUE.toInt())
        assertTrue("enough detail to look smooth", m.triangles > 3_000)
        for (i in 0 until m.vertices) for (a in 0 until 3) assertTrue(abs(m.pos[i * 3 + a]) <= 1.1f)
        assertTrue(m.tri.all { it in 0 until m.vertices })
    }

    @Test fun everyNormalIsAUnitArrow() {
        for (i in 0 until m.vertices) {
            val x = m.nrm[i * 3]; val y = m.nrm[i * 3 + 1]; val z = m.nrm[i * 3 + 2]
            assertEquals(1f, sqrt(x * x + y * y + z * z), 0.01f)
        }
    }

    @Test fun itsLeftAndRightAreMirrorImages() {
        var minX = 0f; var maxX = 0f
        for (i in 0 until m.vertices) { minX = minOf(minX, m.pos[i * 3]); maxX = maxOf(maxX, m.pos[i * 3]) }
        assertEquals(-minX, maxX, 0.01f)
        // The cups hang just under the band's ends (the Solo 4's short sliders), not on long rods.
        val lowest = (0 until m.vertices).minOf { m.pos[it * 3 + 1] }
        assertTrue("cups' bottom at $lowest", lowest > -0.7f)
    }

    @Test fun itTurnsCalmly() {
        // About one turn every 16 s: a shop-window turntable, never a spin that pulls the eye.
        val secondsPerTurn = 2 * Math.PI / HeadphoneModel.SPIN_RAD_PER_S
        assertTrue(secondsPerTurn in 12.0..20.0)
    }
}
