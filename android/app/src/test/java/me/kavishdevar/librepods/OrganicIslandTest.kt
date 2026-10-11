package me.kavishdevar.librepods

import me.kavishdevar.librepods.services.IslandMotion
import me.kavishdevar.librepods.services.SmoothCorner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 2026-10-11: continuous corners and the "no balloon" morph rules (Jake's item 6). */
class OrganicIslandTest {
    @Test fun aContinuousCornerLandsExactlyOnTheNextEdge() {
        for (r in listOf(4f, 20f, 44f, 80f)) for (s in listOf(0f, 0.3f, 0.6f, 1f)) for (short in listOf(30f, 90f, 200f, 400f)) {
            val k = SmoothCorner.corner(r, s, short)
            val end = SmoothCorner.joints(k).last()
            assertEquals("r=$r s=$s short=$short", k.p, end.first, 0.01f)
            assertEquals("r=$r s=$s short=$short", k.p, end.second, 0.01f)
            // Never uses more edge than the shape has.
            assertTrue(k.p <= short / 2f + 0.001f)
            assertTrue(k.radius <= short / 2f + 0.001f)
        }
    }

    @Test fun aCapsuleStaysAnExactCapsule() {
        // The compact pill: the corner fills the whole half-height, so there's nothing to ease.
        val k = SmoothCorner.corner(18f, SmoothCorner.IOS, 36f)
        assertEquals(18f, k.radius, 0.001f)
        assertEquals(18f, k.p, 0.001f)
        assertEquals(90f, k.arcDeg, 0.001f)
        assertEquals(0f, k.a + k.b + k.c + k.d, 0.001f)
    }

    @Test fun theOpenedIslandEasesIntoItsCorners() {
        // 44 dp on a 160 dp tall island: room to ease, so it starts the bend before a circle would.
        val k = SmoothCorner.corner(44f, SmoothCorner.IOS, 160f)
        assertEquals(44f * 1.6f, k.p, 0.01f)
        assertTrue(k.arcDeg < 90f)
        assertTrue(k.a > 0f)
        // On a 135 dp one there's only 67.5 dp of half-edge: it eases a little less, still smooth.
        val t = SmoothCorner.corner(44f, SmoothCorner.IOS, 135f)
        assertEquals(67.5f, t.p, 0.01f)
        assertTrue(t.arcDeg in k.arcDeg..90f)
    }

    @Test fun theHalfGrownShapeIsNotAFatCapsule() {
        // Pill 37 dp tall (corner 18.5) growing into a 135 dp island (corner 44), halfway there.
        val h = 86f
        val r = IslandMotion.morphRadius(18.5f, 44f, 0.5f, h)
        assertTrue("corner $r should be well under half the height (${h / 2f})", r < h / 2f - 8f)
        // Arrived: Apple's 44 dp. Never more than half the height, however far it overshoots.
        assertEquals(44f, IslandMotion.morphRadius(18.5f, 44f, 1f, 135f), 0.001f)
        assertEquals(15f, IslandMotion.morphRadius(18.5f, 44f, 1.2f, 30f), 0.001f)
    }

    @Test fun itThinsAsItStretchesInsteadOfSwelling() {
        assertEquals(1f, IslandMotion.volumeGive(0.4f), 0f)
        assertEquals(1f, IslandMotion.volumeGive(1f), 0f)
        val g = IslandMotion.volumeGive(1.05f)
        assertTrue(g < 1f)
        // Width x height stays about the same as at rest (liquid keeps its volume).
        assertEquals(1f, 1.05f * g, 0.025f)
    }

    @Test fun theHeightNeverBouncesMoreThanTheWidth() {
        for (f in IslandMotion.Feel.entries) {
            assertTrue("$f", IslandMotion.overshoot(f.openHeight) <= IslandMotion.overshoot(f.open) + 1e-6f)
        }
        assertTrue(IslandMotion.MAX_HEIGHT_OVERSHOOT <= 0.03f)
    }

    @Test fun theUnlockFitsItsMomentAndFeelsFluid() {
        val shackle = IslandMotion.Spring(
            me.kavishdevar.librepods.presentation.overlays.UNLOCK_DAMPING, me.kavishdevar.librepods.presentation.overlays.UNLOCK_STIFFNESS,
        )
        val show = me.kavishdevar.librepods.services.ScreenRules.UNLOCK_SHOW_MS
        // The shackle settles, and the light finishes its glide, before the app's icon takes over.
        assertTrue(IslandMotion.settleMs(shackle) < show)
        assertTrue(me.kavishdevar.librepods.presentation.overlays.UNLOCK_SWEEP_MS < show)
        // A little give, never a wild bounce; and only slightly longer than before (900 ms), no lag.
        assertTrue(IslandMotion.overshoot(shackle) in 0.03f..0.12f)
        assertTrue(show in 1_000L..1_300L)
    }
}
