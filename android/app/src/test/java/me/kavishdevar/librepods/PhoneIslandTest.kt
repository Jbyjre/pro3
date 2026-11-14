package me.kavishdevar.librepods

import android.app.Application
import android.content.Context
import me.kavishdevar.librepods.presentation.navigation.AppTab
import me.kavishdevar.librepods.services.ChosenDevice
import me.kavishdevar.librepods.services.DeviceKind
import me.kavishdevar.librepods.services.IslandPrefs
import me.kavishdevar.librepods.services.IslandTimer
import me.kavishdevar.librepods.services.PhoneControls
import me.kavishdevar.librepods.services.PhoneRules
import me.kavishdevar.librepods.services.PhoneStatus
import me.kavishdevar.librepods.services.SoundRules
import me.kavishdevar.librepods.services.SoundSource
import me.kavishdevar.librepods.services.TimerRules
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.core.graphics.drawable.toBitmap
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The phone-first Dynamic Island (session 2026-10-07): which app is on screen, what the pill
 * shows and what a tap opens, the glance, the timer, and the tabs.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class PhoneIslandTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val prefs get() = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    // ---- Which app is on screen ----

    // ---- Locking and unlocking ----

    private fun phone(screenOn: Boolean, lockScreen: Boolean) {
        org.robolectric.Shadows.shadowOf(context.getSystemService(android.os.PowerManager::class.java)).setIsInteractive(screenOn)
        org.robolectric.Shadows.shadowOf(context.getSystemService(android.app.KeyguardManager::class.java)).setKeyguardLocked(lockScreen)
    }
    private fun waitMs(ms: Long) = org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(ms))

    @Test fun settingsHiddenEmergencyHomeScreenIsNotTheHomeScreen() {
        // Android's list of home screens includes Settings' FallbackHome at priority -1000.
        val list = listOf("com.google.android.apps.nexuslauncher" to 0, "com.android.settings" to -1000)
        val home = SoundRules.homeApps(list, default = "com.google.android.apps.nexuslauncher")
        assertEquals(setOf("com.google.android.apps.nexuslauncher"), home)
        // No default chosen yet: Android answers with its chooser ("android"), which isn't a home screen.
        assertEquals(setOf("com.google.android.apps.nexuslauncher"), SoundRules.homeApps(list, default = "android"))
        // A second launcher installed still counts.
        assertTrue("com.teslacoilsw.launcher" in SoundRules.homeApps(list + ("com.teslacoilsw.launcher" to 0), "com.google.android.apps.nexuslauncher"))
    }

    private fun idle() = org.robolectric.shadows.ShadowLooper.idleMainLooper()

    // ---- 2026-10-11: switching apps without waiting (Jake's item 8) ----

    // ---- What the pill shows, and what a tap opens ----

    // ---- The glance ----

    // ---- The timer ----

    @Test fun timerArithmetic() {
        val s = IslandTimer.State(total = 300_000L, endsAt = 10_300_000L)
        assertEquals(120_000L, TimerRules.left(s, 10_180_000L))
        assertEquals(0L, TimerRules.left(s, 99_999_999L))
        assertEquals(0.6f, TimerRules.progress(s, 10_180_000L), 0.001f)
        // Rounded up, so it never says 0:00 while time is left.
        assertEquals("0:01", TimerRules.format(1L))
        assertEquals("4:05", TimerRules.format(245_000L))
        assertEquals("1:02:03", TimerRules.format(3_723_000L))
        assertEquals("5", TimerRules.short(245_000L))
        assertEquals("42", TimerRules.short(41_200L))
        // Paused and ringing.
        assertEquals(30_000L, TimerRules.left(s.copy(pausedLeft = 30_000L), 0L))
        assertEquals(0L, TimerRules.left(s.copy(ringing = true), 0L))
    }

    @Test fun timerPlusOneMinuteAndRestore() {
        val now = 1_000L
        val running = IslandTimer.State(total = 60_000L, endsAt = now + 10_000L)
        assertEquals(now + 70_000L, TimerRules.plusMinute(running, now).endsAt)
        val paused = IslandTimer.State(total = 60_000L, pausedLeft = 5_000L)
        assertEquals(65_000L, TimerRules.plusMinute(paused, now).pausedLeft)
        val ringing = IslandTimer.State(total = 60_000L, ringing = true)
        val again = TimerRules.plusMinute(ringing, now)
        assertTrue(again.running)
        assertEquals(now + 60_000L, again.endsAt)
        // After pro restarts: still running, paused, or gone if it ended meanwhile (never rung late).
        assertEquals(500_000L + 30_000L, TimerRules.restore(60_000L, 2_030_000L, -1L, 2_000_000L, 500_000L)!!.endsAt)
        assertEquals(5_000L, TimerRules.restore(60_000L, 0L, 5_000L, 2_000_000L, 500_000L)!!.pausedLeft)
        assertNull(TimerRules.restore(60_000L, 1_000_000L, -1L, 2_000_000L, 500_000L))
        assertNull(TimerRules.restore(0L, 9_999_999L, -1L, 2_000_000L, 500_000L))
    }

    @Test fun timerStartPauseResumeCancelInTheApp() {
        IslandTimer.start(context, 120_000L)
        assertTrue(IslandTimer.state.value!!.running)
        IslandTimer.pause(context)
        assertTrue(IslandTimer.state.value!!.paused)
        IslandTimer.resume(context)
        assertTrue(IslandTimer.state.value!!.running)
        IslandTimer.addMinute(context)
        assertEquals(180_000L, IslandTimer.state.value!!.total)
        IslandTimer.cancel(context)
        assertNull(IslandTimer.state.value)
        // Nothing is left saved to come back after a restart.
        assertEquals(0L, prefs.getLong("glint_timer_total", 0L))
    }

    // ---- Phone controls and words ----

    @Test fun ringerOnlySwitchesSoundAndVibrate() {
        assertEquals(PhoneControls.Ring.Vibrate, PhoneRules.nextRing(PhoneControls.Ring.Sound))
        assertEquals(PhoneControls.Ring.Sound, PhoneRules.nextRing(PhoneControls.Ring.Vibrate))
        // Silent goes back to sound (pro never turns on Do Not Disturb).
        assertEquals(PhoneControls.Ring.Sound, PhoneRules.nextRing(PhoneControls.Ring.Silent))
    }

    @Test fun timeInPlainWords() {
        assertEquals("in 45 min", PhoneRules.inWords(45 * 60_000L))
        assertEquals("in 1 h", PhoneRules.inWords(60 * 60_000L))
        assertEquals("in 3 h 05 min", PhoneRules.inWords((3 * 60 + 5) * 60_000L))
        assertEquals("in 1 min", PhoneRules.inWords(10_000L))
        assertEquals("now", PhoneRules.inWords(0L))
    }

    // ---- Tabs ----

    @Test fun theHeadphonesTabIsNamedAfterTheChosenDevice() {
        assertEquals(listOf(AppTab.Headphones, AppTab.Phone, AppTab.Breathe), AppTab.entries.toList())
        assertEquals(AppTab.Headphones, AppTab.Home)
        assertEquals("AirPods", AppTab.Headphones.label(ChosenDevice(DeviceKind.AIRPODS, "", "Jake's AirPods Pro")))
        assertEquals("Beats", AppTab.Headphones.label(ChosenDevice(DeviceKind.BEATS_SOLO_4, "x", "Beats Solo 4")))
        assertEquals("Headphones", AppTab.Headphones.label(ChosenDevice(DeviceKind.HEADPHONES, "x", "Car audio")))
        assertEquals("Phone", AppTab.Phone.label(ChosenDevice(DeviceKind.AIRPODS, "", "")))
    }
}

/** Puts the sound listener's test hooks back. */
private object SoundSourceReset {

}
