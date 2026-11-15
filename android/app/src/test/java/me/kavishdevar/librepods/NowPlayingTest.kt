package me.kavishdevar.librepods

import android.app.Application
import android.content.ComponentName
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Looper
import android.provider.Settings
import androidx.compose.ui.graphics.asImageBitmap
import me.kavishdevar.librepods.services.MediaAccessService
import me.kavishdevar.librepods.services.NowPlaying
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Media-session regressions retained after the camera-island feature was removed. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class NowPlayingTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private val sessions = mutableListOf<MediaSession>()
    private val manager get() = shadowOf(app.getSystemService(MediaSessionManager::class.java))

    @Before fun allowMusicAccess() {
        Settings.Secure.putString(app.contentResolver, "enabled_notification_listeners",
            ComponentName(app, MediaAccessService::class.java).flattenToString())
        NowPlaying.preview(NowPlaying.Track())
    }

    @After fun releaseSessions() {
        NowPlaying.detach()
        shadowOf(Looper.getMainLooper()).idle()
        manager.clearControllers()
        sessions.forEach { it.release() }
    }

    private fun controller(pkg: String, state: Int, title: String = "Same song"): MediaController {
        val session = MediaSession(app, pkg).also { sessions += it }
        return MediaController(app, session.sessionToken).also {
            shadowOf(it).setPackageName(pkg)
            shadowOf(it).setPlaybackState(PlaybackState.Builder().setState(state, 0L, 1f).build())
            shadowOf(it).setMetadata(MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, "Same artist").build())
        }
    }

    private fun attach() {
        NowPlaying.attach(app)
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test fun bufferingMusicWinsOverAnIdleSession() {
        manager.addController(controller("com.example.idle", PlaybackState.STATE_PAUSED))
        manager.addController(controller("com.example.buffering", PlaybackState.STATE_BUFFERING))
        attach()
        assertEquals("com.example.buffering", NowPlaying.state.value.pkg)
        assertTrue(NowPlaying.state.value.playing)
    }

    @Test fun switchingAppsWithTheSameSongDoesNotKeepThePreviousCover() {
        val previousCover = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).asImageBitmap()
        NowPlaying.preview(NowPlaying.Track(playing = true, title = "Same song", artist = "Same artist",
            pkg = "com.example.previous", art = previousCover, fromSession = true))
        manager.addController(controller("com.example.current", PlaybackState.STATE_PLAYING))
        attach()
        assertEquals("com.example.current", NowPlaying.state.value.pkg)
        assertNull(NowPlaying.state.value.art)
    }

    @Test fun metadataCallbacksPublishTheNewTitleWithoutLosingPlayback() {
        val controller = controller("com.example.music", PlaybackState.STATE_PLAYING)
        manager.addController(controller)
        attach()
        shadowOf(controller).executeOnMetadataChanged(MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, "Next song").build())
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("Next song", NowPlaying.state.value.title)
        assertTrue(NowPlaying.state.value.playing)
    }
}
