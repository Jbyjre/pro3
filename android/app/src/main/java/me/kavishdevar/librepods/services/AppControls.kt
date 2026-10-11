/*
    pro, a fork of LibrePods - AirPods liberated from Apple's ecosystem
    Copyright (C) 2026 pro contributors

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

import android.app.NotificationManager
import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.PowerManager
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent

/**
 * The controls behind [AppActions]: ordinary Android, nothing that needs root. Each one says what
 * it did (or that it needs a permission first) and never throws.
 */
object AppControls {
    private const val TAG = "AppControls"

    // ---- Sound ----

    /** The media sound (games, videos, music) is muted. */
    fun muted(c: Context): Boolean = runCatching {
        c.getSystemService(AudioManager::class.java)?.isStreamMute(AudioManager.STREAM_MUSIC) == true
    }.getOrDefault(false)

    /** Mutes or unmutes the media sound. Returns the new state. No permission needed. */
    fun toggleMute(c: Context): Boolean {
        val am = c.getSystemService(AudioManager::class.java) ?: return false
        runCatching { am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, 0) }
            .onFailure { Log.w(TAG, "Mute refused", it) }
        return muted(c)
    }

    // ---- Do Not Disturb ----

    /** Do Not Disturb is on (anything quieter than "all notifications"). */
    fun quiet(c: Context): Boolean = runCatching {
        val f = c.getSystemService(NotificationManager::class.java)?.currentInterruptionFilter ?: return false
        f != NotificationManager.INTERRUPTION_FILTER_ALL && f != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
    }.getOrDefault(false)

    /** pro may switch Do Not Disturb (the "Do Not Disturb access" switch in Android's settings). */
    fun canQuiet(c: Context): Boolean =
        c.getSystemService(NotificationManager::class.java)?.isNotificationPolicyAccessGranted == true

    /**
     * Do Not Disturb on (priority only) or off. Without access, opens the page where it's granted
     * and returns null; otherwise the new state.
     */
    fun toggleQuiet(c: Context): Boolean? {
        val nm = c.getSystemService(NotificationManager::class.java) ?: return null
        if (!nm.isNotificationPolicyAccessGranted) {
            start(c, Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            return null
        }
        val on = !quiet(c)
        return runCatching {
            nm.setInterruptionFilter(if (on) NotificationManager.INTERRUPTION_FILTER_PRIORITY else NotificationManager.INTERRUPTION_FILTER_ALL)
            quiet(c)
        }.onFailure { Log.w(TAG, "Do Not Disturb refused", it) }.getOrNull()
    }

    // ---- Apps ----

    fun installed(c: Context, pkg: String): Boolean =
        runCatching { c.packageManager.getLaunchIntentForPackage(pkg) != null }.getOrDefault(false)

    /** The chat app the glance offers (Discord first while gaming), or null when none is installed. */
    fun chatApp(c: Context, forGame: Boolean, front: String?): String? = AppActions.chatApp({ installed(c, it) }, forGame, front)

    fun openCamera(c: Context) {
        start(c, Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
    }

    // ---- A music app with nothing playing ----

    /**
     * Plays again in [pkg]: through its own session when pro can see it (Notification access),
     * otherwise a Play key sent to that app only (never to whichever app played last). Whether a
     * given music app answers that key while it has no session is up to the app (not verified for each).
     */
    fun resume(c: Context, pkg: String) {
        val t = NowPlaying.state.value
        if (t.pkg == pkg) {
            if (!t.playing) NowPlaying.playPause(c)
            return
        }
        for (action in intArrayOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
            runCatching {
                c.sendBroadcast(
                    Intent(Intent.ACTION_MEDIA_BUTTON).setPackage(pkg)
                        .putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(action, KeyEvent.KEYCODE_MEDIA_PLAY))
                )
            }.onFailure { Log.w(TAG, "Play key refused", it) }
        }
    }

    /** Opens [pkg]'s own search: Spotify's search page, an app's media search, its search, or the app. */
    fun search(c: Context, pkg: String) {
        val tries = buildList {
            if (pkg == "com.spotify.music") add(Intent(Intent.ACTION_VIEW, Uri.parse("spotify:search")))
            add(Intent(MediaStore.INTENT_ACTION_MEDIA_SEARCH))
            add(Intent(Intent.ACTION_SEARCH))
        }
        for (i in tries) if (start(c, i.setPackage(pkg))) return
        SoundSource.openApp(c, pkg)
    }

    /**
     * Plays [song] in [pkg] by asking the app to play from a search (what voice assistants use:
     * Android's "play from search"); apps that don't support it open their search instead.
     */
    fun play(c: Context, pkg: String, song: MusicMemory.Song) {
        val query = listOfNotNull(song.title, song.artist).joinToString(" ")
        val i = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).setPackage(pkg)
            .putExtra(SearchManager.QUERY, query)
            .putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/audio")
            .putExtra(MediaStore.EXTRA_MEDIA_TITLE, song.title)
        song.artist?.let { i.putExtra(MediaStore.EXTRA_MEDIA_ARTIST, it) }
        if (!start(c, i)) search(c, pkg)
    }

    // ---- How the phone is doing (a game's glance) ----

    data class Health(val battery: Int?, val tempTenths: Int?, val thermal: Int)

    /** Battery level and temperature (from Android's own battery report) and the thermal status. */
    fun health(c: Context): Health {
        val sticky = runCatching { c.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) }.getOrNull()
        val level = sticky?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = sticky?.getIntExtra(BatteryManager.EXTRA_SCALE, 100)?.takeIf { it > 0 } ?: 100
        val temp = sticky?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)?.takeIf { it != Int.MIN_VALUE }
        val thermal = runCatching { c.getSystemService(PowerManager::class.java)?.currentThermalStatus ?: 0 }.getOrDefault(0)
        return Health(if (level >= 0) level * 100 / scale else null, temp, thermal)
    }

    /** Starts [i] as a new screen; false when no app takes it. */
    private fun start(c: Context, i: Intent): Boolean = try {
        c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (e: Exception) {
        Log.w(TAG, "Couldn't open ${i.action}", e)
        false
    }
}
