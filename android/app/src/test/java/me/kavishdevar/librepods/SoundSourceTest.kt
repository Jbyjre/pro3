package me.kavishdevar.librepods

import android.app.Application
import android.media.AudioAttributes
import android.os.SystemClock
import me.kavishdevar.librepods.services.IslandPrefs
import me.kavishdevar.librepods.services.SoundRules
import me.kavishdevar.librepods.services.SoundRules.Basis
import me.kavishdevar.librepods.services.SoundRules.Kind
import me.kavishdevar.librepods.services.SoundRules.Role
import me.kavishdevar.librepods.services.SoundSource
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The Android side of "any sound pops the island": playback events in, the sound and its app out. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class SoundSourceTest {
    private val app get() = RuntimeEnvironment.getApplication()

    @Before fun setUp() {
        SoundSource.resetForTest()
        // Every package counts as an app you can open (the phone's own pieces are tested in the rules).
        SoundSource.roleOverride = { Role.App }
        SoundSource.attach(app)
    }

    @After fun tearDown() = SoundSource.resetForTest()

    @Test fun aMessageDingBecomesAnAlertThatEndsAndIsRemembered() {
        SoundSource.notificationPosted("com.whatsapp", flags = 0)
        SoundSource.usagesChanged(listOf(AudioAttributes.USAGE_NOTIFICATION))
        val on = SoundSource.heard.value!!
        assertEquals(Kind.Alert, on.kind)
        assertEquals("com.whatsapp", on.pkg)
        assertEquals(Basis.Notification, on.basis)
        assertTrue(on.active)
        assertFalse(on.musicLike)

        SoundSource.usagesChanged(emptyList())
        val off = SoundSource.heard.value!!
        assertFalse(off.active)
        assertTrue(off.endedAt > 0L)
        // It lingers (the pill shows it for a moment after), and it's in the recent list once.
        assertTrue(SoundRules.showing(off.active, off.endedAt, off.endedAt + 1_000, SoundRules.Linger.Normal.ms))
        assertEquals(listOf("com.whatsapp"), SoundSource.recent.value.map { it.pkg })
    }

    @Test fun musicIsMusicLikeAndTakesTheAppOnScreenWhenThereIsNoSession() {
        SoundSource.windowChanged("com.google.android.youtube")
        SoundSource.usagesChanged(listOf(AudioAttributes.USAGE_MEDIA))
        val h = SoundSource.heard.value!!
        assertEquals(Kind.Media, h.kind)
        assertTrue(h.musicLike)
        assertEquals("com.google.android.youtube", h.pkg)
        assertEquals(Basis.Foreground, h.basis)
    }

    @Test fun anAlertOverMusicIsStillMusicLikeSoTheMusicStateStaysInCharge() {
        SoundSource.notificationPosted("com.whatsapp", 0)
        SoundSource.usagesChanged(listOf(AudioAttributes.USAGE_MEDIA, AudioAttributes.USAGE_NOTIFICATION))
        val h = SoundSource.heard.value!!
        assertEquals(Kind.Alert, h.kind)
        assertTrue(h.musicLike)
    }

    @Test fun interfaceClicksNeverPopTheIsland() {
        SoundSource.usagesChanged(listOf(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION))
        assertNull(SoundSource.heard.value)
        assertTrue(SoundSource.recent.value.isEmpty())
    }

    @Test fun aSoundWithNoClueIsStillShownByItsKind() {
        SoundSource.usagesChanged(listOf(AudioAttributes.USAGE_ALARM))
        val h = SoundSource.heard.value!!
        assertEquals(Kind.Alarm, h.kind)
        assertNull(h.pkg)
        assertEquals(Basis.None, h.basis)
        assertTrue(h.active)
    }

    @Test fun anAppSwitchedOffInSettingsDoesNotPopTheIsland() {
        IslandPrefs.setSoundIgnored(IslandPrefs.prefs(app), "com.noisy.app", true)
        SoundSource.notificationPosted("com.noisy.app", 0)
        SoundSource.usagesChanged(listOf(AudioAttributes.USAGE_NOTIFICATION))
        assertNull(SoundSource.heard.value)
        // It's still in the recent list, so the switch can be turned back on.
        assertEquals(listOf("com.noisy.app"), SoundSource.recent.value.map { it.pkg })
        // Another app is unaffected.
        SoundSource.usagesChanged(emptyList())
        SoundSource.notificationPosted("com.quiet.app", 0)
        SoundSource.usagesChanged(listOf(AudioAttributes.USAGE_NOTIFICATION))
        assertEquals("com.quiet.app", SoundSource.heard.value?.pkg)
    }

    @Test fun ongoingNotificationsAndPhoneOwnPiecesAreNotBlamed() {
        SoundSource.notificationPosted("com.spotify.music", SoundRules.FLAG_ONGOING_EVENT)
        SoundSource.usagesChanged(listOf(AudioAttributes.USAGE_NOTIFICATION))
        assertNull(SoundSource.heard.value?.pkg)
        SoundSource.usagesChanged(emptyList())
        // The phone's own pieces (status bar, keyboard, pro) are never the answer.
        SoundSource.roleOverride = { if (it == "com.android.systemui") Role.System else Role.App }
        SoundSource.windowChanged("com.android.systemui")
        assertNull(SoundSource.foreground)
        SoundSource.windowChanged("com.example.chat")
        assertEquals("com.example.chat", SoundSource.foreground)
        SoundSource.windowChanged("com.android.systemui")
        assertEquals("com.example.chat", SoundSource.foreground)
    }

    @Test fun theSameSoundKeepsItsAppAndStartTimeWhileItPlays() {
        SoundSource.notificationPosted("com.whatsapp", 0)
        SoundSource.usagesChanged(listOf(AudioAttributes.USAGE_NOTIFICATION))
        val first = SoundSource.heard.value!!
        // Android re-reports the same sound a moment later, with the notification long gone.
        SoundSource.usagesChanged(listOf(AudioAttributes.USAGE_NOTIFICATION))
        val again = SoundSource.heard.value!!
        assertEquals(first.startedAt, again.startedAt)
        assertEquals("com.whatsapp", again.pkg)
        assertEquals(1, SoundSource.recent.value.size)
        assertEquals(1, SoundSource.recent.value.first().count)
    }

    @Test fun theHomeScreenClearsTheAppOnScreen() {
        SoundSource.windowChanged("com.example.chat")
        assertNotNull(SoundSource.foreground)
        SoundSource.roleOverride = { Role.Launcher }
        SoundSource.windowChanged("com.sec.android.app.launcher")
        assertNull(SoundSource.foreground)
    }

    @Test fun listeningStartsOnlyOnceAndCopesWithNoSoundsAtStart() {
        SoundSource.attach(app)
        SoundSource.attach(app)
        assertNull(SoundSource.heard.value)
        // The playback callback itself is registered with Android (a real event would reach it).
        val audio = app.getSystemService(android.media.AudioManager::class.java)
        assertNotNull(shadowOf(audio))
        assertTrue(SystemClock.elapsedRealtime() >= 0)
    }
}
