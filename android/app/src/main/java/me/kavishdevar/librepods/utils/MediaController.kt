/*
    LibrePods - AirPods liberated from Apple’s ecosystem
    Copyright (C) 2025 LibrePods contributors

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

@file:OptIn(ExperimentalEncodingApi::class)

package me.kavishdevar.librepods.utils

import android.content.SharedPreferences
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import androidx.annotation.RequiresApi
import me.kavishdevar.librepods.services.ServiceManager
import kotlin.io.encoding.ExperimentalEncodingApi

object MediaController {
    private var initialVolume: Int? = null
    private lateinit var audioManager: AudioManager
    var iPausedTheMedia = false
    var userPlayedTheMedia = false
    private lateinit var sharedPreferences: SharedPreferences
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var preferenceChangeListener: SharedPreferences.OnSharedPreferenceChangeListener

    var pausedWhileTakingOver = false
    var pausedForOtherDevice = false

    private var lastSelfActionAt: Long = 0L
    private const val SELF_ACTION_IGNORE_MS = 800L
    private const val PLAYBACK_DEBOUNCE_MS = 300L
    private var lastPlaybackCallbackAt: Long = 0L
    private var lastKnownIsMusicActive: Boolean? = null

    private const val PAUSED_FOR_OTHER_DEVICE_CLEAR_MS = 500L
    private val clearPausedForOtherDeviceRunnable = Runnable {
        pausedForOtherDevice = false
        Log.d("MediaController", "Cleared pausedForOtherDevice after timeout, resuming normal playback monitoring")
    }

    private var relativeVolume: Boolean = false
    private var conversationalAwarenessVolume: Int = 2
    private var conversationalAwarenessPauseMusic: Boolean = false

    var recentlyLostOwnership: Boolean = false

    private var lastPlayWithReplay: Boolean = false
    private var lastPlayTime: Long = 0L

    fun initialize(audioManager: AudioManager, sharedPreferences: SharedPreferences) {
        if (this::audioManager.isInitialized) {
            return
        }
        this.audioManager = audioManager
        this.sharedPreferences = sharedPreferences
        Log.d("MediaController", "Initializing MediaController")
        relativeVolume = sharedPreferences.getBoolean("relative_conversational_awareness_volume", false)
        conversationalAwarenessVolume = sharedPreferences.getInt("conversational_awareness_volume", (audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) / 0.4).toInt())
        conversationalAwarenessPauseMusic = sharedPreferences.getBoolean("conversational_awareness_pause_music", false)

        preferenceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                "relative_conversational_awareness_volume" -> {
                    relativeVolume = sharedPreferences.getBoolean("relative_conversational_awareness_volume", false)
                }
                "conversational_awareness_volume" -> {
                    conversationalAwarenessVolume = sharedPreferences.getInt("conversational_awareness_volume", (audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) * 0.4).toInt())
                }
                "conversational_awareness_pause_music" -> {
                    conversationalAwarenessPauseMusic = sharedPreferences.getBoolean("conversational_awareness_pause_music", false)
                }
            }
        }

        sharedPreferences.registerOnSharedPreferenceChangeListener(preferenceChangeListener)

        audioManager.registerAudioPlaybackCallback(cb, null)
    }

    val cb = object : AudioManager.AudioPlaybackCallback() {
        @RequiresApi(Build.VERSION_CODES.R)
        override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>?) {
            super.onPlaybackConfigChanged(configs)
            val now = SystemClock.uptimeMillis()
            val isActive = audioManager.isMusicActive
            me.kavishdevar.librepods.services.NowPlaying.audioChanged(isActive)
            Log.d("MediaController", "Playback config changed, iPausedTheMedia: $iPausedTheMedia, isActive: $isActive, pausedForOtherDevice: $pausedForOtherDevice, lastKnownIsMusicActive: $lastKnownIsMusicActive")

            if (!isActive && lastPlayWithReplay && now - lastPlayTime < 2500L) {
                Log.d("MediaController", "Music paused shortly after play with replay; retrying play")
                lastPlayWithReplay = false
                sendPlay()
                lastKnownIsMusicActive = true
                return
            }

            if (now - lastPlaybackCallbackAt < PLAYBACK_DEBOUNCE_MS) {
                Log.d("MediaController", "Ignoring playback callback due to debounce (${now - lastPlaybackCallbackAt}ms)")
                lastPlaybackCallbackAt = now
                return
            }
            lastPlaybackCallbackAt = now

            if (now - lastSelfActionAt < SELF_ACTION_IGNORE_MS) {
                Log.d("MediaController", "Ignoring playback callback because it's likely caused by our own action (${now - lastSelfActionAt}ms since last self-action)")
                lastKnownIsMusicActive = isActive
                return
            }

            Log.d("MediaController", "Configs received: ${configs?.size ?: 0} configurations")
            val currentActiveContentTypes = configs?.flatMap { config ->
                Log.d("MediaController", "Processing config: ${config}, audioAttributes: ${config.audioAttributes}")
                config.audioAttributes?.let { attrs ->
                    val contentType = attrs.contentType
                    Log.d("MediaController", "Config content type: $contentType")
                    listOf(contentType)
                } ?: run {
                    Log.d("MediaController", "Config has no audioAttributes")
                    emptyList()
                }
            }?.toSet() ?: emptySet()

            Log.d("MediaController", "Current active content types: $currentActiveContentTypes")

            val hasNewMusicOrMovie = currentActiveContentTypes.any { contentType ->
                contentType == android.media.AudioAttributes.CONTENT_TYPE_MUSIC ||
                contentType == android.media.AudioAttributes.CONTENT_TYPE_MOVIE
            }

            Log.d("MediaController", "Has new music or movie: $hasNewMusicOrMovie")

            if (pausedForOtherDevice) {
                handler.removeCallbacks(clearPausedForOtherDeviceRunnable)
                handler.postDelayed(clearPausedForOtherDeviceRunnable, PAUSED_FOR_OTHER_DEVICE_CLEAR_MS)

                if (isActive) {
                    Log.d("MediaController", "Detected play while pausedForOtherDevice; attempting to take over")
                    if (!recentlyLostOwnership && hasNewMusicOrMovie) {
                        pausedForOtherDevice = false
                        userPlayedTheMedia = true
                        if (!pausedWhileTakingOver) {
                            ServiceManager.getService()?.takeOver("music")
                        }
                    } else {
                        Log.d("MediaController", "Skipping take-over due to recent ownership loss or no new music/movie")
                    }
                } else {
                    Log.d("MediaController", "Still not active while pausedForOtherDevice; will clear state after timeout")
                }

                lastKnownIsMusicActive = isActive
                return
            }

            if (configs != null && !iPausedTheMedia) {
                val localMac = ServiceManager.getService()?.localMac ?: return
                if (localMac == "") return
                ServiceManager.getService()?.aacpManager?.sendMediaInformataion(
                    localMac,
                    isActive
                )
                Log.d("MediaController", "User changed media state themselves; will wait for ear detection pause before auto-play")
                handler.postDelayed({
                    userPlayedTheMedia = audioManager.isMusicActive
                    if (audioManager.isMusicActive) {
                        pausedForOtherDevice = false
                    }
                }, 7)
            }

            Log.d("MediaController", "pausedWhileTakingOver: $pausedWhileTakingOver")
            if (!pausedWhileTakingOver && isActive && hasNewMusicOrMovie) {
                if (lastKnownIsMusicActive != true) {
                    if (!recentlyLostOwnership) {
                        Log.d("MediaController", "Music/movie is active and not pausedWhileTakingOver; requesting takeOver")
                        ServiceManager.getService()?.takeOver("music")
                    } else {
                        Log.d("MediaController", "Skipping take-over due to recent ownership loss")
                    }
                }
            }

            lastKnownIsMusicActive = hasNewMusicOrMovie && isActive
        }
    }

    @Synchronized
    fun getMusicActive(): Boolean {
        return audioManager.isMusicActive
    }

    @Synchronized
    fun sendPlayPause() {
        if (audioManager.isMusicActive) {
            Log.d("MediaController", "Sending pause because music is active")
            sendPause()
        } else {
            Log.d("MediaController", "Sending play because music is not active")
            sendPlay()
        }
    }

    /**
     * Play or pause pressed on Glint's island. The island sends the key itself; this only keeps
     * the auto-pause bookkeeping right: a pause you chose is never undone when a bud goes back
     * in, and a play you chose counts as yours.
     */
    @Synchronized
    fun userPlayback(play: Boolean) {
        if (!this::audioManager.isInitialized) return
        iPausedTheMedia = false
        userPlayedTheMedia = play
        if (play) pausedWhileTakingOver = false
        lastSelfActionAt = SystemClock.uptimeMillis()
    }

    @Synchronized
    fun sendPreviousTrack() {
        Log.d("MediaController", "Sending previous track")
        audioManager.dispatchMediaKeyEvent(
            KeyEvent(
                KeyEvent.ACTION_DOWN,
                KeyEvent.KEYCODE_MEDIA_PREVIOUS
            )
        )
        audioManager.dispatchMediaKeyEvent(
            KeyEvent(
                KeyEvent.ACTION_UP,
                KeyEvent.KEYCODE_MEDIA_PREVIOUS
            )
        )
        lastSelfActionAt = SystemClock.uptimeMillis()
    }

    @Synchronized
    fun sendNextTrack() {
        Log.d("MediaController", "Sending next track")
        audioManager.dispatchMediaKeyEvent(
            KeyEvent(
                KeyEvent.ACTION_DOWN,
                KeyEvent.KEYCODE_MEDIA_NEXT
            )
        )
        audioManager.dispatchMediaKeyEvent(
            KeyEvent(
                KeyEvent.ACTION_UP,
                KeyEvent.KEYCODE_MEDIA_NEXT
            )
        )
        lastSelfActionAt = SystemClock.uptimeMillis()
    }

    @Synchronized
    fun sendPause(force: Boolean = false) {
        Log.d("MediaController", "Sending pause with iPausedTheMedia: $iPausedTheMedia, userPlayedTheMedia: $userPlayedTheMedia, isMusicActive: ${audioManager.isMusicActive}, force: $force")
        if ((audioManager.isMusicActive) && (!userPlayedTheMedia || force)) {
            iPausedTheMedia = if (force) audioManager.isMusicActive else true
            userPlayedTheMedia = false
            audioManager.dispatchMediaKeyEvent(
                KeyEvent(
                    KeyEvent.ACTION_DOWN,
                    KeyEvent.KEYCODE_MEDIA_PAUSE
                )
            )
            audioManager.dispatchMediaKeyEvent(
                KeyEvent(
                    KeyEvent.ACTION_UP,
                    KeyEvent.KEYCODE_MEDIA_PAUSE
                )
            )
            lastSelfActionAt = SystemClock.uptimeMillis()
        }
    }

    @Synchronized
    fun sendPlay(replayWhenPaused: Boolean = false, force: Boolean = false) {
        Log.d("MediaController", "Sending play with iPausedTheMedia: $iPausedTheMedia, replayWhenPaused: $replayWhenPaused, force: $force")
        if (replayWhenPaused) {
            lastPlayWithReplay = true
            lastPlayTime = SystemClock.uptimeMillis()
        }
        if (iPausedTheMedia || force) { // very creative, ik. thanks.
            Log.d("MediaController", "Sending play and setting userPlayedTheMedia to false")
            userPlayedTheMedia = false
            audioManager.dispatchMediaKeyEvent(
                KeyEvent(
                    KeyEvent.ACTION_DOWN,
                    KeyEvent.KEYCODE_MEDIA_PLAY
                )
            )
            audioManager.dispatchMediaKeyEvent(
                KeyEvent(
                    KeyEvent.ACTION_UP,
                    KeyEvent.KEYCODE_MEDIA_PLAY
                )
            )
            lastSelfActionAt = SystemClock.uptimeMillis()
        }
        if (!audioManager.isMusicActive) {
            Log.d("MediaController", "Setting iPausedTheMedia to false")
            iPausedTheMedia = false
        }
        if (pausedWhileTakingOver) {
            Log.d("MediaController", "Setting pausedWhileTakingOver to false")
            pausedWhileTakingOver = false
        }
    }

    // Conversation Awareness: a pending "give the music back", the volume ramp in progress, the
    // level we turned it down to (to notice if you changed it yourself meanwhile), and when you
    // started talking recently (to tell a real conversation from one remark).
    private var restoreTask: Runnable? = null
    private var rampTask: Runnable? = null
    private var duckedTo: Int? = null
    private val talkStarts = ArrayDeque<Long>()
    private var stuckTask: Runnable? = null

    @Synchronized
    fun startSpeaking() {
        Log.d("MediaController", "Starting speaking max vol: ${audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)}, current vol: ${audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)}, conversationalAwarenessVolume: $conversationalAwarenessVolume, relativeVolume: $relativeVolume")
        // Talking again before the music came back: keep it down, don't bounce.
        restoreTask?.let { handler.removeCallbacks(it) }
        restoreTask = null
        val now = SystemClock.uptimeMillis()
        talkStarts.addLast(now)
        while (talkStarts.isNotEmpty() && now - talkStarts.first() > ConversationTiming.WINDOW_MS) talkStarts.removeFirst()
        ConversationTiming.setTalking(true)
        // Safety: if "stopped talking" never comes (the AirPods disconnected mid-conversation),
        // give the music back after a while instead of leaving it down for good.
        stuckTask?.let { handler.removeCallbacks(it) }
        stuckTask = Runnable { restoreAfterConversation() }.also { handler.postDelayed(it, ConversationTiming.STUCK_MS) }

        if (initialVolume == null) {
            initialVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            Log.d("MediaController", "Initial Volume: $initialVolume")
            val targetVolume = if (relativeVolume) {
                (initialVolume!! * conversationalAwarenessVolume / 100)
            } else if (initialVolume!! > (audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) * conversationalAwarenessVolume / 100)) {
                (audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) * conversationalAwarenessVolume / 100)
            } else {
                initialVolume!!
            }
            duckedTo = targetVolume
            smoothVolumeTransition(initialVolume!!, targetVolume)
            if (conversationalAwarenessPauseMusic) {
                sendPause(force = true)
            }
        }
        Log.d("MediaController", "Initial Volume: $initialVolume")
    }

    /**
     * You stopped talking: give the music back after a short quiet spell (longer during a real
     * back-and-forth), unless you start talking again first. See [ConversationTiming].
     */
    @Synchronized
    fun stopSpeaking() {
        Log.d("MediaController", "Stopping speaking, initialVolume: $initialVolume")
        if (initialVolume == null) { ConversationTiming.setTalking(false); return }
        restoreTask?.let { handler.removeCallbacks(it) }
        val now = SystemClock.uptimeMillis()
        val turns = talkStarts.count { now - it <= ConversationTiming.WINDOW_MS }
        val wait = ConversationTiming.restoreDelayMs(conversationalAwarenessPauseMusic, turns)
        val task = Runnable { restoreAfterConversation() }
        restoreTask = task
        handler.postDelayed(task, wait)
    }

    @Synchronized
    private fun restoreAfterConversation() {
        restoreTask = null
        stuckTask?.let { handler.removeCallbacks(it) }
        stuckTask = null
        val start = initialVolume ?: return
        val now = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        // If you changed the volume yourself while talking, your choice wins.
        val youChangedIt = rampTask == null && duckedTo != null && now != duckedTo
        if (!youChangedIt) smoothVolumeTransition(now, start)
        if (conversationalAwarenessPauseMusic) sendPlay()
        initialVolume = null
        duckedTo = null
        ConversationTiming.setTalking(false)
    }

    /** One volume step at a time on [ConversationTiming.rampDelays]; a new ramp replaces one in progress. */
    private fun smoothVolumeTransition(fromVolume: Int, toVolume: Int) {
        Log.d("MediaController", "Smooth volume transition from $fromVolume to $toVolume")
        rampTask?.let { handler.removeCallbacks(it) }
        val delays = ConversationTiming.rampDelays(fromVolume, toVolume)
        if (delays.isEmpty()) { rampTask = null; return }
        val step = if (fromVolume < toVolume) 1 else -1
        var currentVolume = fromVolume
        var i = 0
        val task = object : Runnable {
            override fun run() {
                if (currentVolume == toVolume || i >= delays.size) { rampTask = null; return }
                currentVolume += step
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, currentVolume, 0)
                handler.postDelayed(this, delays[i++])
            }
        }
        rampTask = task
        handler.post(task)
    }
}
