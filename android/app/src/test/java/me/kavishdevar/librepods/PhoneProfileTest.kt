package me.kavishdevar.librepods

import android.app.Application
import android.os.PowerManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import me.kavishdevar.librepods.presentation.glint.GlassBudget
import me.kavishdevar.librepods.presentation.screens.HeartChart
import me.kavishdevar.librepods.presentation.screens.PhoneProfileContent
import me.kavishdevar.librepods.presentation.theme.LibrePodsTheme
import me.kavishdevar.librepods.services.HeartRate
import me.kavishdevar.librepods.services.MiniIslandRules
import me.kavishdevar.librepods.services.PhoneProfile
import me.kavishdevar.librepods.services.PhoneProfile.Choice
import me.kavishdevar.librepods.services.PhoneProfile.Phone
import me.kavishdevar.librepods.services.PowerPlan
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** pro tuned for the Galaxy S26 FE and the Pixel 6 Pro (Jake, 2026-10-15). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = Application::class, qualifiers = "w412dp-h915dp-xxhdpi")
class PhoneProfileTest {
    @get:Rule val rule = createComposeRule()
    private val out = "build/screenshots"

    @After fun backToGeneral() = PhoneProfile.preview(Choice.Auto, Phone.Other)

    @Test fun recognisesBothPhonesAndNothingElse() {
        assertEquals(Phone.Pixel6Pro, PhoneProfile.detect("Google", "Pixel 6 Pro"))
        assertEquals(Phone.Other, PhoneProfile.detect("Google", "Pixel 6"))      // the smaller, flat one
        assertEquals(Phone.Other, PhoneProfile.detect("Google", "Pixel 6a"))
        // Every S26 FE variant shares SM-S741 (B, B/DS, U, U1 ...).
        assertEquals(Phone.GalaxyS26FE, PhoneProfile.detect("samsung", "SM-S741B"))
        assertEquals(Phone.GalaxyS26FE, PhoneProfile.detect("samsung", "SM-S741U1"))
        assertEquals(Phone.Other, PhoneProfile.detect("samsung", "SM-S731B"))   // the S25 FE
        assertEquals(Phone.Other, PhoneProfile.detect("OnePlus", "SM-S741B"))
    }

    @Test fun aChoiceBeatsTheGuessAndAutomaticFollowsThePhone() {
        assertEquals(Phone.Pixel6Pro, PhoneProfile.resolve(Choice.Auto, Phone.Pixel6Pro))
        assertEquals(Phone.GalaxyS26FE, PhoneProfile.resolve(Choice.GalaxyS26FE, Phone.Pixel6Pro))
        assertEquals(Phone.Pixel6Pro, PhoneProfile.resolve(Choice.Pixel6Pro, Phone.Other))
    }

    @Test fun eachPhoneGetsItsOwnTuning() {
        val s26 = PhoneProfile.tuning(Phone.GalaxyS26FE)
        val p6 = PhoneProfile.tuning(Phone.Pixel6Pro)
        val other = PhoneProfile.tuning(Phone.Other, samsung = false)
        // Flat screen: the original 10 dp; curved Pixel: further in.
        assertEquals(10f, s26.edgeDp)
        assertTrue(p6.edgeDp > s26.edgeDp)
        // Tensor runs warm: saves from the first heat level; the others as before.
        assertEquals(PowerManager.THERMAL_STATUS_LIGHT, p6.warmFrom)
        assertEquals(PowerManager.THERMAL_STATUS_MODERATE, s26.warmFrom)
        assertEquals(PhoneProfile.KeepAwake.Samsung, s26.keepAwake)
        assertEquals(PhoneProfile.KeepAwake.Android, p6.keepAwake)
        // Any other phone keeps exactly what pro did before.
        assertEquals(MiniIslandRules.WIDE_EDGE_DP, other.edgeDp)
        assertEquals(PowerManager.THERMAL_STATUS_MODERATE, other.warmFrom)
    }

    @Test fun warmthCountsEarlierOnThePixel() {
        val light = PowerManager.THERMAL_STATUS_LIGHT
        assertEquals(PowerPlan.Level.Balanced, PowerPlan.levelFor(false, 80, false, light))
        assertEquals(PowerPlan.Level.Saver, PowerPlan.levelFor(false, 80, false, light, warmFrom = light))
        assertFalse(GlassBudget.shouldLighten(false, light))
        assertTrue(GlassBudget.shouldLighten(false, light, warmFrom = light))
    }

    @Test fun theStretchedIslandKeepsOffThePixelsCurve() {
        val d = 2.625f
        val w = 1080f
        val flat = MiniIslandRules.size(60f, 60f, d, w, edgeDp = 10f)
        val curved = MiniIslandRules.size(60f, 60f, d, w, edgeDp = 18f)
        // The stretched pill: 10 dp in on a flat screen, 18 dp in on the curve.
        assertEquals(w - 2 * 10f * d, flat.wideWidth, 0.5f)
        assertEquals(w - 2 * 18f * d, curved.wideWidth, 0.5f)
        // The everyday pill is the same size on both (only the stretch comes near the sides).
        assertEquals(flat.compactWidth, curved.compactWidth, 0.5f)
    }

    // ---- Pictures (build/screenshots) ----

    private fun page(name: String, choice: Choice, phone: Phone, dark: Boolean, awake: Boolean = false) {
        if (dark) RuntimeEnvironment.setQualifiers("+night")
        PhoneProfile.preview(choice, phone)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            LibrePodsTheme(m3eEnabled = false) {
                PhoneProfileContent(
                    choice = choice,
                    tuning = PhoneProfile.tuning(PhoneProfile.resolve(choice, phone), samsung = false),
                    detected = phone,
                    keptAwake = awake,
                    onChoose = {},
                    onKeepAwake = {},
                )
            }
        }
        rule.mainClock.advanceTimeBy(1_500)
        rule.onRoot().captureRoboImage("$out/$name.png")
    }

    @Test fun pagePixel6ProDark() = page("phone_profile_pixel6pro_dark", Choice.Auto, Phone.Pixel6Pro, dark = true)
    @Test fun pageS26FeLight() = page("phone_profile_s26fe_light", Choice.Auto, Phone.GalaxyS26FE, dark = false, awake = true)
    @Test fun pageOtherPhoneChosenPixel() = page("phone_profile_other_set_pixel", Choice.Pixel6Pro, Phone.Other, dark = false)

    /** The heart graph with each pace, an hour each: continuous, Smart (with a walk) and Saver. */
    @Test fun heartGraphPaces() {
        val t0 = 1_760_000_000_000L
        fun wobble(i: Int) = ((i * 7919) % 5) - 2
        // Continuous: a reading every 5 s.
        val continuous = (0 until 720).map { HeartRate.Sample(t0 + it * 5_000L, 68 + wobble(it) + if (it in 300..420) 30 else 0) }
        // Smart: spots every 2 to 4 min while calm, nonstop through a 10-minute walk.
        val smart = buildList {
            var t = t0
            var i = 0
            while (t < t0 + 3_600_000L) {
                val walking = t in (t0 + 1_500_000L)..(t0 + 2_100_000L)
                val base = if (walking) 98 + ((t - t0 - 1_500_000L) / 30_000L).toInt() % 6 else 68
                if (walking) { add(HeartRate.Sample(t, base + wobble(i++))); t += 5_000L }
                else { repeat(3) { add(HeartRate.Sample(t + it * 5_000L, base + wobble(i++))) }; t += if (t < t0 + 1_500_000L) 120_000L else 240_000L }
            }
        }
        // Saver: a spot every 5 minutes.
        val saver = (0 until 12).flatMap { s -> (0 until 3).map { HeartRate.Sample(t0 + s * 300_000L + it * 5_000L, 66 + (s % 4) * 2 + wobble(s + it)) } }
        // The old Saver for comparison: one minute every 15 minutes.
        val oldSaver = (0 until 4).flatMap { b -> (0 until 12).map { HeartRate.Sample(t0 + b * 900_000L + it * 5_000L, 66 + b * 2 + wobble(it)) } }
        rule.mainClock.autoAdvance = false
        rule.setContent {
            LibrePodsTheme(m3eEnabled = false) {
                Column(Modifier.background(Color(0xFFF2F2F7)).padding(16.dp)) {
                    listOf(continuous, smart, saver, oldSaver).forEach { s ->
                        Column(Modifier.padding(bottom = 12.dp).background(Color.White).padding(12.dp)) {
                            HeartChart(s, Color(0xFFFF375F), Color.Black, Color.White, dark = false, height = 150.dp, average = s.map { it.bpm }.average().toInt())
                        }
                    }
                }
            }
        }
        rule.mainClock.advanceTimeBy(2_000)
        rule.onRoot().captureRoboImage("$out/heart_graph_paces.png")
    }
}
