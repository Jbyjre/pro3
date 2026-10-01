/*
    Glint, a fork of LibrePods - AirPods liberated from Apple's ecosystem
    Copyright (C) 2026 Glint contributors

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>.
*/

package me.kavishdevar.librepods.services

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.util.Log
import android.view.KeyEvent
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.scale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What's playing, and play/pause/skip for the island.
 *
 * Two levels, so the island works with any music app (Spotify included):
 * - **Always:** playing or paused (Android's "is music active"), and the media buttons
 *   (play, pause, next, previous) Android routes to the app that last played, the same way
 *   a headset button works. No permission needed.
 * - **With Notification access** (optional, Settings > Island > Show song names): Android
 *   shares the music app's media session, so Glint shows the song, artist and cover, and
 *   uses the app's own controls. Glint reads nothing else from notifications.
 */
object NowPlaying {
    @Immutable
    data class Track(
        val playing: Boolean = false,
        val title: String? = null,
        val artist: String? = null,
        val app: String? = null,
        val art: ImageBitmap? = null,
        /** True when the details come from the music app itself (Notification access). */
        val fromSession: Boolean = false,
    )

    private const val TAG = "NowPlaying"
    private val main = Handler(Looper.getMainLooper())
    private val _state = MutableStateFlow(Track())
    val state: StateFlow<Track> = _state.asStateFlow()

    /** When Glint itself last pressed play/pause/skip (so the island doesn't announce it). */
    @Volatile var lastOwnActionAt = 0L
        private set

    private var appContext: Context? = null
    private var sessions: MediaSessionManager? = null
    private var current: MediaController? = null
    private var listening = false

    fun hasAccess(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    private fun listener(context: Context) = ComponentName(context, MediaAccessService::class.java)

    /** Starts following the music app's session when access is granted (safe to call often). */
    fun attach(context: Context) = main.post {
        val app = context.applicationContext
        appContext = app
        if (!IslandPrefs.songNames(IslandPrefs.prefs(app)) || !hasAccess(app)) { detachNow(); return@post }
        if (listening) { pick(); return@post }
        try {
            val msm = app.getSystemService(MediaSessionManager::class.java) ?: return@post
            msm.addOnActiveSessionsChangedListener(sessionsChanged, listener(app), main)
            sessions = msm
            listening = true
            pick()
        } catch (e: SecurityException) {
            // Access was turned off between the check and the call.
            Log.w(TAG, "No media session access: ${e.message}")
            detachNow()
        }
    }

    fun detach() = main.post { detachNow() }

    private fun detachNow() {
        if (listening) try { sessions?.removeOnActiveSessionsChangedListener(sessionsChanged) } catch (_: Exception) {}
        listening = false
        sessions = null
        current?.unregisterCallback(callback)
        current = null
        val ctx = appContext
        _state.value = Track(playing = ctx?.let { audioActive(it) } ?: _state.value.playing)
    }

    private val sessionsChanged = MediaSessionManager.OnActiveSessionsChangedListener { pick() }

    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = pick()
        override fun onMetadataChanged(metadata: MediaMetadata?) = pick()
        override fun onSessionDestroyed() = pick()
    }

    /** Follows the session that's playing, or else the most important one (index 0). */
    private fun pick() {
        val ctx = appContext ?: return
        val list = try {
            sessions?.getActiveSessions(listener(ctx)).orEmpty()
        } catch (e: SecurityException) {
            detachNow(); return
        }
        val chosen = list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: list.firstOrNull()
        if (chosen?.sessionToken != current?.sessionToken) {
            current?.unregisterCallback(callback)
            current = chosen
            chosen?.registerCallback(callback, main)
        }
        publish(ctx, chosen)
    }

    private fun publish(context: Context, c: MediaController?) {
        if (c == null) {
            _state.value = Track(playing = audioActive(context))
            return
        }
        val md = c.metadata
        val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() }
            ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)?.takeIf { it.isNotBlank() }
        val artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST)?.takeIf { it.isNotBlank() }
            ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)?.takeIf { it.isNotBlank() }
            ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)?.takeIf { it.isNotBlank() }
        val old = _state.value
        val sameSong = old.title == title && old.artist == artist && old.fromSession
        val art = if (sameSong && old.art != null) old.art else artwork(md)
        _state.value = Track(
            playing = c.playbackState?.state.let { it == PlaybackState.STATE_PLAYING || it == PlaybackState.STATE_BUFFERING },
            title = title,
            artist = artist,
            app = appName(context, c.packageName),
            art = art,
            fromSession = true,
        )
    }

    /** Cover art, shrunk to a thumbnail so the island never holds a large bitmap. */
    private fun artwork(md: MediaMetadata?): ImageBitmap? {
        val bmp: Bitmap = md?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: md?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
            ?: return null
        return try {
            val side = 144
            val small = if (bmp.width > side || bmp.height > side) bmp.scale(side, side * bmp.height / bmp.width.coerceAtLeast(1)) else bmp
            small.asImageBitmap()
        } catch (_: Exception) { null }
    }

    /** Called by the playback watcher whenever audio starts or stops (any app). */
    fun audioChanged(active: Boolean) = main.post {
        if (current != null) return@post // the music app's own state is more precise
        if (_state.value.playing != active) _state.value = _state.value.copy(playing = active)
    }

    private fun audioActive(context: Context): Boolean =
        try { context.getSystemService(AudioManager::class.java).isMusicActive } catch (_: Exception) { false }

    /** Sets what's shown, for screenshots and Glint Lab only. */
    internal fun preview(track: Track) { _state.value = track }

    // ---- Controls ----

    fun playPause(context: Context) {
        val playing = _state.value.playing
        lastOwnActionAt = SystemClock.elapsedRealtime()
        me.kavishdevar.librepods.utils.MediaController.userPlayback(play = !playing)
        val t = current?.transportControls
        if (t != null) {
            if (playing) t.pause() else t.play()
        } else {
            key(context, if (playing) KeyEvent.KEYCODE_MEDIA_PAUSE else KeyEvent.KEYCODE_MEDIA_PLAY)
        }
        // Show the change straight away; the real state follows within a moment.
        _state.value = _state.value.copy(playing = !playing)
        main.postDelayed({ if (current == null) _state.value = _state.value.copy(playing = audioActive(context)) }, 900)
    }

    fun next(context: Context) {
        lastOwnActionAt = SystemClock.elapsedRealtime()
        current?.transportControls?.skipToNext() ?: key(context, KeyEvent.KEYCODE_MEDIA_NEXT)
    }

    fun previous(context: Context) {
        lastOwnActionAt = SystemClock.elapsedRealtime()
        current?.transportControls?.skipToPrevious() ?: key(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
    }

    private fun key(context: Context, code: Int) {
        val am = context.getSystemService(AudioManager::class.java) ?: return
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }

    // ---- Helpers ----

    private val knownApps = mapOf(
        "com.spotify.music" to "Spotify",
        "com.google.android.apps.youtube.music" to "YouTube Music",
        "com.google.android.youtube" to "YouTube",
        "com.apple.android.music" to "Apple Music",
        "com.amazon.mp3" to "Amazon Music",
        "com.soundcloud.android" to "SoundCloud",
        "deezer.android.app" to "Deezer",
        "com.aspiro.tidal" to "TIDAL",
        "com.pandora.android" to "Pandora",
        "com.sec.android.app.music" to "Samsung Music",
        "com.audible.application" to "Audible",
        "au.com.shiftyjelly.pocketcasts" to "Pocket Casts",
        "com.google.android.apps.podcasts" to "Podcasts",
        "com.netflix.mediaclient" to "Netflix",
    )

    internal fun appName(context: Context, pkg: String?): String? {
        if (pkg == null) return null
        knownApps[pkg]?.let { return it }
        return try {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (_: Exception) { null }
    }

    /** The system page where Glint can be given Notification access. */
    fun accessSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, listener(context).flattenToString())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Glint's App info page, where "Allow restricted settings" lives for sideloaded apps. */
    fun appInfoIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** The two-line description of a track for the island. */
    internal fun words(t: Track): Pair<String, String> {
        val title = t.title ?: if (t.playing) "Now playing" else "Paused"
        val sub = listOfNotNull(t.artist, t.app).distinct().joinToString(" · ").ifBlank {
            if (t.title == null) (if (t.playing) "On your AirPods" else "Tap play to continue") else ""
        }
        return title to sub
    }
}

/**
 * Exists only so Android can grant Glint access to media sessions (song names and the
 * music app's controls). It ignores the notifications themselves.
 */
class MediaAccessService : NotificationListenerService() {
    override fun onListenerConnected() {
        NowPlaying.attach(this)
    }

    override fun onListenerDisconnected() {
        NowPlaying.detach()
    }
}
