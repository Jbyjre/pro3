package me.kavishdevar.librepods

import android.app.Application
import android.content.Context
import me.kavishdevar.librepods.presentation.navigation.AppTab
import me.kavishdevar.librepods.services.ChosenDevice
import me.kavishdevar.librepods.services.DeviceKind
import me.kavishdevar.librepods.services.GlanceRules
import me.kavishdevar.librepods.services.IslandLook
import me.kavishdevar.librepods.services.IslandLook.Situation
import me.kavishdevar.librepods.services.IslandLook.Slot
import me.kavishdevar.librepods.services.IslandPrefs
import me.kavishdevar.librepods.services.IslandTimer
import me.kavishdevar.librepods.services.MiniIslandRules
import me.kavishdevar.librepods.services.PhoneControls
import me.kavishdevar.librepods.services.PhoneRules
import me.kavishdevar.librepods.services.PhoneStatus
import me.kavishdevar.librepods.services.ScreenApp
import me.kavishdevar.librepods.services.ScreenRules
import me.kavishdevar.librepods.services.SoundRules
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

    @Before fun setUp() {
        prefs.edit().clear().commit()
        ScreenApp.resetForTest()
    }

    @After fun tearDown() {
        ScreenApp.resetForTest()
        SoundSourceReset.reset()
    }

    // ---- Which app is on screen ----

    @Test fun onlyRealAppScreensChangeTheIcon() {
        val none = ScreenApp.Place.Unknown
        // An app's own screen: that app.
        assertEquals(ScreenRules.Next.App("com.spotify.music"), ScreenRules.next(none, "com.spotify.music", SoundRules.Role.App, isScreen = true, locked = false))
        // A toast, a pop-up or a chat bubble from another app: nothing changes.
        assertEquals(ScreenRules.Next.Stay, ScreenRules.next(none, "com.facebook.orca", SoundRules.Role.App, isScreen = false, locked = false))
        // The phone's own pieces (the notification shade, the keyboard): nothing changes.
        assertEquals(ScreenRules.Next.Stay, ScreenRules.next(none, "com.android.systemui", SoundRules.Role.System, isScreen = true, locked = false))
        // The launcher: the home screen (but not while the lock screen is up).
        assertEquals(ScreenRules.Next.Home, ScreenRules.next(none, "com.sec.android.app.launcher", SoundRules.Role.Launcher, isScreen = true, locked = false))
        assertEquals(ScreenRules.Next.Stay, ScreenRules.next(none, "com.sec.android.app.launcher", SoundRules.Role.Launcher, isScreen = true, locked = true))
        // The same app again (another of its screens): no swap animation.
        val spotify = ScreenApp.Place.App("com.spotify.music", "Spotify", null)
        assertEquals(ScreenRules.Next.Stay, ScreenRules.next(spotify, "com.spotify.music", SoundRules.Role.App, isScreen = true, locked = false))
    }

    @Test fun recentAppsAreRememberedOnceNewestFirstWithoutPro() {
        var list = emptyList<String>()
        list = ScreenRules.remember(list, "a", own = "pro")
        list = ScreenRules.remember(list, "b", own = "pro")
        list = ScreenRules.remember(list, "a", own = "pro")
        list = ScreenRules.remember(list, "pro", own = "pro")
        assertEquals(listOf("a", "b"), list)
        repeat(30) { list = ScreenRules.remember(list, "app$it", own = "pro") }
        assertEquals(ScreenRules.RECENT_MAX, list.size)
        assertEquals("app29", list.first())
    }

    private fun idle() = org.robolectric.shadows.ShadowLooper.idleMainLooper()

    @Test fun windowEventsFollowTheAppInFront() {
        ScreenApp.attachForTest(context)
        // Icon lookups run off the main thread on the phone; here, in place.
        ScreenApp.offMain = { it.run() }
        me.kavishdevar.librepods.services.SoundSource.roleOverride = { pkg ->
            when (pkg) {
                "launcher" -> SoundRules.Role.Launcher
                "com.android.systemui" -> SoundRules.Role.System
                else -> SoundRules.Role.App
            }
        }
        ScreenApp.setActivityForTest("com.spotify.music", "com.spotify.MainActivity", true)
        ScreenApp.setActivityForTest("com.spotify.music", "android.widget.Toast\$TN", false)
        ScreenApp.setActivityForTest("launcher", "launcher.Home", true)
        ScreenApp.setActivityForTest("com.anthropic.claude", "com.anthropic.Main", true)

        ScreenApp.windowChanged("com.spotify.music", "com.spotify.MainActivity")
        idle()
        assertEquals("com.spotify.music", (ScreenApp.place.value as ScreenApp.Place.App).pkg)
        // The shade pulled down over it: still Spotify.
        ScreenApp.windowChanged("com.android.systemui", "com.android.systemui.Shade")
        assertEquals("com.spotify.music", (ScreenApp.place.value as ScreenApp.Place.App).pkg)
        ScreenApp.windowChanged("launcher", "launcher.Home")
        assertEquals(ScreenApp.Place.Home, ScreenApp.place.value)
        // A toast from Spotify while on the home screen: still home.
        ScreenApp.windowChanged("com.spotify.music", "android.widget.Toast\$TN")
        assertEquals(ScreenApp.Place.Home, ScreenApp.place.value)
        ScreenApp.windowChanged("com.anthropic.claude", "com.anthropic.Main")
        idle()
        assertEquals("com.anthropic.claude", (ScreenApp.place.value as ScreenApp.Place.App).pkg)
        assertEquals(listOf("com.anthropic.claude", "com.spotify.music"), ScreenApp.recent.value)
        // The accessibility switch went off: pro no longer knows.
        ScreenApp.lostSight()
        assertEquals(ScreenApp.Place.Unknown, ScreenApp.place.value)
    }

    @Test fun theIconIsTheAppsRealIconNotADrawing() {
        ScreenApp.attachForTest(context)
        ScreenApp.offMain = { it.run() }
        // An app installed on the (test) phone, with its own icon: a distinctive picture.
        val pkg = "com.spotify.music"
        val pm = org.robolectric.Shadows.shadowOf(context.packageManager)
        pm.installPackage(android.content.pm.PackageInfo().apply {
            packageName = pkg
            applicationInfo = android.content.pm.ApplicationInfo().apply { packageName = pkg; name = "Spotify" }
        })
        val picture = android.graphics.Bitmap.createBitmap(48, 48, android.graphics.Bitmap.Config.ARGB_8888).apply {
            for (x in 0 until 48) for (y in 0 until 48) setPixel(x, y, if ((x / 8 + y / 8) % 2 == 0) 0xFF1DB954.toInt() else 0xFF000000.toInt())
        }
        pm.setApplicationIcon(pkg, android.graphics.drawable.BitmapDrawable(context.resources, picture))
        me.kavishdevar.librepods.services.SoundSource.forgetInfo(pkg)
        ScreenApp.setActivityForTest(pkg, "com.spotify.MainActivity", true)
        me.kavishdevar.librepods.services.SoundSource.roleOverride = { SoundRules.Role.App }

        ScreenApp.windowChanged(pkg, "com.spotify.MainActivity")
        idle()
        val shown = (ScreenApp.place.value as ScreenApp.Place.App).icon!!.asAndroidBitmap()
        // Exactly the icon Android has for the app, drawn at the island's size (sharp: 144 px or more).
        val real = context.packageManager.getApplicationIcon(pkg).toBitmap(shown.width, shown.height)
        assertTrue("the island's icon must be the app's own icon", shown.sameAs(real))
        assertTrue(shown.width >= 144)
        // Previews use a real app on the phone too (one used lately), never the drawn bubble.
        val (examplePkg, info) = me.kavishdevar.librepods.services.SoundSource.exampleApp(context)
        assertEquals(pkg, examplePkg)
        assertTrue(info.icon!!.asAndroidBitmap().sameAs(real))
        val drawn = me.kavishdevar.librepods.presentation.overlays.sampleIcon().asAndroidBitmap()
        assertFalse(info.icon!!.asAndroidBitmap().sameAs(drawn))
    }

    // ---- What the pill shows, and what a tap opens ----

    @Test fun thePhoneComesBeforeTheAirPodsButAfterMusicAndSounds() {
        fun c(playing: Boolean = false, sound: Boolean = false, known: Boolean = true, airPods: Boolean = true, paused: Long = Long.MAX_VALUE, played: Boolean = false) =
            MiniIslandRules.content(playing, paused, played, airPods, sound, placeKnown = known)
        assertEquals(MiniIslandRules.Content.Music, c(playing = true))
        assertEquals(MiniIslandRules.Content.Sound, c(sound = true))
        assertEquals(MiniIslandRules.Content.Music, c(paused = 5_000, played = true))
        assertEquals(MiniIslandRules.Content.Screen, c())
        assertEquals(MiniIslandRules.Content.AirPods, c(known = false))
        assertEquals(MiniIslandRules.Content.Rest, c(known = false, airPods = false))
    }

    @Test fun aTapNeverOpensAnEmptyMusicPlayer() {
        assertEquals(MiniIslandRules.TapOpens.Glance, MiniIslandRules.tapOpens(MiniIslandRules.Content.Screen))
        assertEquals(MiniIslandRules.TapOpens.Glance, MiniIslandRules.tapOpens(MiniIslandRules.Content.Rest))
        assertEquals(MiniIslandRules.TapOpens.Music, MiniIslandRules.tapOpens(MiniIslandRules.Content.Music))
        assertEquals(MiniIslandRules.TapOpens.AirPods, MiniIslandRules.tapOpens(MiniIslandRules.Content.AirPods))
        assertEquals(MiniIslandRules.TapOpens.SoundApp, MiniIslandRules.tapOpens(MiniIslandRules.Content.Sound))
    }

    @Test fun hiddenAppsHideItButATimerAlwaysShows() {
        val base = MiniIslandRules.Inputs(
            enabled = true, canDraw = true, playing = false, pausedForMs = Long.MAX_VALUE, playedRecently = false,
            airPodsOnly = false, airPodsUp = false, screenUnlocked = true, anytime = true,
        )
        assertTrue(MiniIslandRules.wanted(base))
        assertFalse(MiniIslandRules.wanted(base.copy(hiddenHere = true)))
        assertTrue(MiniIslandRules.wanted(base.copy(hiddenHere = true, timer = true)))
        // A timer brings it up even with "Always on" off; never with the screen off or the switch off.
        assertTrue(MiniIslandRules.wanted(base.copy(anytime = false, timer = true)))
        assertFalse(MiniIslandRules.wanted(base.copy(timer = true, screenUnlocked = false)))
        assertFalse(MiniIslandRules.wanted(base.copy(timer = true, enabled = false)))
    }

    @Test fun situationsForWhereYouAre() {
        fun s(place: IslandLook.Place?, music: Boolean = false, playing: Boolean = false, sound: Boolean = false, talking: Boolean = false) =
            IslandLook.situation(music, playing, talking, charging = false, sound = sound, place = place)
        assertEquals(Situation.App, s(IslandLook.Place.App))
        assertEquals(Situation.Home, s(IslandLook.Place.Home))
        assertEquals(Situation.Locked, s(IslandLook.Place.Locked))
        // Music and sounds still win.
        assertEquals(Situation.Music, s(IslandLook.Place.App, music = true, playing = true))
        assertEquals(Situation.Sound, s(IslandLook.Place.Home, sound = true))
        assertEquals(Situation.Idle, s(null))
    }

    @Test fun defaultsShowTheAppTheHomeScreenAndAPadlockNeverTheTime() {
        val look = IslandLook.read(prefs)
        assertEquals(Slot.Screen to Slot.Glance, look.slots(Situation.App))
        assertEquals(Slot.Home to Slot.Date, look.slots(Situation.Home))
        assertEquals(Slot.Lock to Slot.Glance, look.slots(Situation.Locked))
        Situation.entries.forEach { s ->
            val (l, r) = look.slots(s)
            assertFalse("$s shows the clock by default", l == Slot.Clock || r == Slot.Clock)
        }
        // The new situations are saved and reset like the others.
        IslandLook.setSlot(prefs, Situation.App, false, Slot.Phone)
        assertEquals(Slot.Screen to Slot.Phone, IslandLook.read(prefs).slots(Situation.App))
        IslandLook.reset(prefs)
        assertEquals(Slot.Screen to Slot.Glance, IslandLook.read(prefs).slots(Situation.App))
    }

    // ---- The glance ----

    @Test fun glanceOrderTimerChargingHeadphonesLowBatteryAlarm() {
        val now = 1_000_000L
        val timer = IslandTimer.State(total = 60_000L, endsAt = 50_000L)
        val all = GlanceRules.live(timer, PhoneStatus.Info(15, true), budsUp = true, budsLevel = 70, headphones = false, nextAlarm = now + 3_600_000L, wallNow = now)
        assertTrue(all[0] is GlanceRules.Item.Timer)
        assertTrue(all[1] is GlanceRules.Item.Charging)
        assertTrue(all[2] is GlanceRules.Item.Buds)
        assertTrue(all[3] is GlanceRules.Item.Alarm)
        // Charging hides "low battery"; unplugged at 15% it's there.
        val low = GlanceRules.live(null, PhoneStatus.Info(15, false), false, null, false, null, now)
        assertEquals(listOf(GlanceRules.Item.LowPhone(15)), low)
        // An alarm more than a day away isn't worth a glance; nothing at all leaves the battery.
        val calm = GlanceRules.live(null, PhoneStatus.Info(80, false), false, null, false, now + 2 * GlanceRules.ALARM_AHEAD_MS, now)
        assertEquals(listOf(GlanceRules.Item.Battery(80, false)), calm)
        assertEquals(2, GlanceRules.rows(all).size)
    }

    @Test fun thePillsGlanceSpotIsEmptyWhenThereIsNothingToSay() {
        assertNull(GlanceRules.pill(null, PhoneStatus.Info(80, false), false, null, false))
        assertEquals(GlanceRules.Item.Charging(40), GlanceRules.pill(null, PhoneStatus.Info(40, true), false, null, false))
        assertEquals(GlanceRules.Item.Buds(60, true), GlanceRules.pill(null, PhoneStatus.Info(80, false), true, 60, true))
        assertNull(GlanceRules.pill(null, PhoneStatus.Info(), false, null, false))
    }

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

    @Test fun hideInAppsIsSavedPerApp() {
        IslandPrefs.setHideIn(prefs, "com.game", true)
        IslandPrefs.setHideIn(prefs, "com.video", true)
        IslandPrefs.setHideIn(prefs, "com.game", false)
        assertEquals(setOf("com.video"), IslandPrefs.hideIn(prefs))
        assertTrue(IslandPrefs.phoneMoments(prefs))
        assertTrue(IslandPrefs.appPop(prefs))
    }

    // ---- Tabs ----

    @Test fun theHeadphonesTabIsNamedAfterTheChosenDevice() {
        assertEquals(listOf(AppTab.Phone, AppTab.Island, AppTab.Headphones), AppTab.entries.toList())
        assertEquals("AirPods", AppTab.Headphones.label(ChosenDevice(DeviceKind.AIRPODS, "", "Jake's AirPods Pro")))
        assertEquals("Beats", AppTab.Headphones.label(ChosenDevice(DeviceKind.BEATS_SOLO_4, "x", "Beats Solo 4")))
        assertEquals("Headphones", AppTab.Headphones.label(ChosenDevice(DeviceKind.HEADPHONES, "x", "Car audio")))
        assertEquals("Phone", AppTab.Phone.label(ChosenDevice(DeviceKind.AIRPODS, "", "")))
    }
}

/** Puts the sound listener's test hooks back. */
private object SoundSourceReset {
    fun reset() { me.kavishdevar.librepods.services.SoundSource.roleOverride = null }
}
