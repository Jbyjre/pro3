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
package me.kavishdevar.librepods.presentation.overlays

import kotlinx.coroutines.coroutineScope
import me.kavishdevar.librepods.presentation.glint.rememberHeartBeat
import me.kavishdevar.librepods.presentation.glint.heartPath
import me.kavishdevar.librepods.presentation.glint.listeningModeName
import me.kavishdevar.librepods.presentation.glint.ListeningModeGlyph
import androidx.compose.ui.text.drawText
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.layout.onSizeChanged
import android.content.BroadcastReceiver
import android.content.ComponentCallbacks
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.graphics.scale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import me.kavishdevar.librepods.presentation.glint.GlintComfort
import me.kavishdevar.librepods.presentation.glint.GlintHaptics
import me.kavishdevar.librepods.presentation.glint.lerp
import me.kavishdevar.librepods.presentation.theme.glintFontFamily
import me.kavishdevar.librepods.services.GlintStatus
import me.kavishdevar.librepods.services.IslandAccess
import me.kavishdevar.librepods.services.IslandGestures
import me.kavishdevar.librepods.services.IslandLook
import me.kavishdevar.librepods.presentation.glint.drawListeningMode
import me.kavishdevar.librepods.presentation.glint.drawHeadphones
import androidx.compose.ui.graphics.drawscope.clipRect
import me.kavishdevar.librepods.services.ListeningModes
import me.kavishdevar.librepods.services.IslandPrefs
import me.kavishdevar.librepods.services.LinkState
import me.kavishdevar.librepods.services.MiniIslandRules
import me.kavishdevar.librepods.services.MusicPulse
import me.kavishdevar.librepods.services.NowPlaying
import me.kavishdevar.librepods.services.PhoneStatus
import me.kavishdevar.librepods.services.SoundRules
import me.kavishdevar.librepods.services.SoundSource
import me.kavishdevar.librepods.services.GlanceRules
import me.kavishdevar.librepods.services.IslandTimer
import me.kavishdevar.librepods.services.ScreenApp
import me.kavishdevar.librepods.services.TimerRules
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The mini island: a small black pill wrapped around the front camera while something plays,
 * like the iPhone's Dynamic Island in its music state. The song's cover sits on one side of
 * the camera and sound bars (in the cover's own colour) on the other; it widens for a moment
 * to show each new song's name.
 *
 * In the app it's the "Dynamic Island". Touch: one tap expands it, two play/pause, three skip
 * to the next song; swipe left/right changes song; hold opens pro. It stays while the AirPods
 * are connected (blurring softly while a pop-up grows out of it), steps aside only in
 * full-screen apps, and otherwise leaves 30 seconds after the music stops.
 */
internal class MiniIslandController(private val context: Context) {
    private val window = OverlayWindow(context, "GlintMiniIsland", anchorTop = true, aboveStatusBar = true)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val prefs = IslandPrefs.prefs(context)
    private var started = false
    /** When music started and stopped, and whether it played long enough to count (not a half-second blip). */
    private val play = MiniIslandRules.PlayTracker()
    private var unlocked = true
    /** A connection blip (a few seconds of "reconnecting") doesn't make it vanish and come back. */
    private var lastConnectedAt = 0L
    /**
     * The AirPods count as connected when pro's control link is up, when it gave up but audio
     * still plays through them (some phones refuse the control channel), when Android lists
     * them as an audio output, or for a few seconds after a blip.
     */
    private fun airPodsUp(): Boolean {
        // Other headphones chosen: they count when they're connected (by their own address only).
        if (!me.kavishdevar.librepods.services.DeviceChoice.followsAirPods(context)) {
            return me.kavishdevar.librepods.services.HeadphoneLink.state.value.connected || airPodsAudio()
        }
        val link = GlintStatus.link.value
        return link is LinkState.Connected || link is LinkState.GaveUp || airPodsAudio() ||
            (lastConnectedAt > 0L && SystemClock.elapsedRealtime() - lastConnectedAt < AIRPODS_GRACE_MS)
    }

    private val audio = context.getSystemService(android.media.AudioManager::class.java)

    /** AirPods among Android's current audio outputs (by the saved address or the name). */
    private fun airPodsAudio(): Boolean = GlintOverlays.chosenAudio(context)

    private val audioDevices = object : android.media.AudioDeviceCallback() {
        override fun onAudioDevicesAdded(added: Array<out android.media.AudioDeviceInfo>?) = refresh()
        override fun onAudioDevicesRemoved(removed: Array<out android.media.AudioDeviceInfo>?) = refresh()
    }
    private var lingerJob: Job? = null
    private var shownFor: Any? = null
    private var lastWant = false

    /**
     * Where the pill is, or will be in a moment: lets a pop-up grow out of it even when both
     * start together (the AirPods connecting shows the pill and the "Connected" pop-up at once).
     */
    fun plannedOrigin(): GlintOverlays.MiniOrigin? {
        GlintOverlays.miniOrigin?.let { return it }
        if (!lastWant) return null
        return MiniGeometry(context).origin()
    }

    /** True while the pill should animate away (then the window is removed). */
    private val leaving = mutableStateOf(false)
    /** Sample music for Settings > Island > Try it. */
    private val sample = mutableStateOf<NowPlaying.Track?>(null)
    /** Sample sound for Settings > Island > Try it. */
    private val sampleSound = mutableStateOf<SoundSource.Heard?>(null)
    /** The sound the pill is showing (a non-music sound, or an alert popping over music), else null. */
    private val soundShown = mutableStateOf<SoundSource.Heard?>(null)
    /** Sample message for Settings > Island > Try it. */
    private val sampleMessage = mutableStateOf<SoundSource.Message?>(null)
    /** The message the pill is showing (as its own look, or as an icon popping over music), else null. */
    private val messageShown = mutableStateOf<SoundSource.Message?>(null)
    /** The phone's battery moment (charging, full, low) is showing over music: the ring pops in the right-hand spot. */
    private val phoneBlip = mutableStateOf(false)
    /** Counts phone moments, so each one gets its own little bounce. */
    private val phoneSerial = androidx.compose.runtime.mutableIntStateOf(0)
    private var phoneMomentUntil = 0L
    private var lastPhone: PhoneStatus.Info? = null
    /** Swiped away at this time (elapsedRealtime): moments that began before it stay hidden. */
    private var dismissedAt = 0L
    /** Music or the AirPods themselves. */
    private val content = mutableStateOf(MiniIslandRules.Content.Music)
    /** A short moment with words under the camera (the phone plugged in), or null. */
    private val moment = mutableStateOf<MiniMoment?>(null)

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            // Off only while the screen is off: it stays on the lock screen too (when the phone
            // lets pop-ups show there; Android may hide them under the lock screen).
            // How the screen is now, not which message this was: Android can hand these over
            // late or out of order, and a late "screen off" must not hide the island while you use it.
            unlocked = c.getSystemService(PowerManager::class.java)?.isInteractive ?: (i.action != Intent.ACTION_SCREEN_OFF)
            refresh()
        }
    }

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
        if (key == IslandPrefs.PREF_MINI || key == IslandPrefs.PREF_MINI_AIRPODS_ONLY || key == IslandPrefs.PREF_MINI_ALWAYS ||
            key == IslandPrefs.PREF_MINI_ANYTIME || key == IslandPrefs.PREF_MINI_ANY_SOUND || key == IslandPrefs.PREF_SOUND_LINGER ||
            key == IslandPrefs.PREF_SOUND_IGNORED || key == IslandPrefs.PREF_HIDE_IN) refresh()
        if (IslandGestures.Gesture.entries.any { it.key == key }) actions.value = IslandGestures.all(p)
        if (key in IslandLook.keys) {
            look.value = IslandLook.read(p)
            // A new size, width or a wider choice changes the window: measure and place it again.
            if (window.isShowing && MiniGeometry(context, look = look.value).key != shownFor) window.dismiss()
            refresh()
        }
    }

    /** What it shows and how big (Settings > Islands), read live. */
    private val look = mutableStateOf(IslandLook.read(prefs))

    /** What each gesture does (Settings > Islands), read live. */
    private val actions = mutableStateOf(IslandGestures.all(prefs))

    private val rotation = object : ComponentCallbacks {
        override fun onConfigurationChanged(newConfig: Configuration) {
            // The camera's place on screen moves with rotation, folding and display size: measure again.
            if (window.isShowing && MiniGeometry(context).key != shownFor) window.dismiss()
            refresh()
        }
        @Deprecated("Deprecated in Java")
        override fun onLowMemory() {}
    }

    fun start() {
        if (started) { refresh(); return }
        started = true
        unlocked = context.getSystemService(PowerManager::class.java)?.isInteractive ?: true
        ContextCompat.registerReceiver(
            context, screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
        runCatching { audio?.registerAudioDeviceCallback(audioDevices, android.os.Handler(android.os.Looper.getMainLooper())) }
        context.registerComponentCallbacks(rotation)
        scope.launch { NowPlaying.state.collect { onTrack(it) } }
        // Any other sound (a message ding, a voice note, a call, an alarm): show it for a moment.
        scope.launch { SoundSource.heard.collect { refresh() } }
        // The app in front, the home screen or the lock screen; the island's timer.
        scope.launch { ScreenApp.place.collect { refresh() } }
        scope.launch { IslandTimer.state.collect { refresh() } }
        // A message arrived (a text, a chat): its own moment, with the app's icon and name.
        scope.launch { SoundSource.message.collect { refresh() } }
        // The phone plugged in, got full, or ran low: a moment with its battery ring and a line of words.
        scope.launch { PhoneStatus.state.collect { onPhone(it) } }
        // The accessibility service started or stopped: move the pill above or below the status
        // bar (only above it can it be touched).
        scope.launch {
            IslandAccess.service.collect {
                if (window.misplaced) window.dismiss()
                refresh()
            }
        }
        scope.launch {
            GlintStatus.link.collect { link ->
                if (link is LinkState.Connected) lastConnectedAt = SystemClock.elapsedRealtime()
                else if (lastConnectedAt > 0L) scope.launch { delay(AIRPODS_GRACE_MS + 100); refresh() }
                refresh()
            }
        }
    }

    private fun onTrack(t: NowPlaying.Track) {
        // Only music that played for a while leaves a "Paused" pill to tap and resume.
        play.update(t.playing, SystemClock.elapsedRealtime())
        refresh()
    }

    fun preview() {
        sample.value = NowPlaying.Track(playing = true, title = "Song name", artist = "Artist", app = "pro", fromSession = true)
        unlocked = true
        refresh()
        scope.launch {
            delay(7_000)
            sample.value = null
            refresh()
        }
    }

    private fun onPhone(info: PhoneStatus.Info) {
        val kind = PhoneStatus.momentBetween(lastPhone, info)
        lastPhone = info
        if (kind == null || !IslandPrefs.phoneMoments(prefs)) return
        val now = SystemClock.elapsedRealtime()
        phoneMomentUntil = now + PHONE_MOMENT_MS
        phoneSerial.intValue++
        // And a line of words under the camera, like the iPhone's ("Charging · 76%").
        val m = MiniMoment.of(kind, info.level, now)
        moment.value = m
        refresh()
        scope.launch {
            delay(MiniMoment.SHOW_MS + 1_200L)
            if (moment.value == m) { moment.value = null; refresh() }
        }
    }

    /**
     * Swipe up on the pill: put away whatever it has out (a message, a sound's icon, the phone's
     * battery). Returns whether there was something to put away.
     */
    private fun putAway(): Boolean {
        val had = soundShown.value != null || messageShown.value != null || phoneMomentUntil > SystemClock.elapsedRealtime() || moment.value != null
        if (!had) return false
        dismissedAt = SystemClock.elapsedRealtime()
        phoneMomentUntil = 0L
        moment.value = null
        refresh()
        return true
    }

    /** A pretend message for Settings > Island > Try it, so you can see the Messages look. */
    fun previewMessage() = scope.launch {
        // A real app on this phone (your texting app), looked up off the main thread: never a drawn stand-in.
        val (pkg, app) = kotlinx.coroutines.withContext(Dispatchers.Default) { SoundSource.exampleApp(context) }
        val now = SystemClock.elapsedRealtime()
        sampleMessage.value = SoundSource.Message("sample", pkg, app.label ?: "Messages", app.icon ?: sampleIcon(), "Alex", now, now)
        unlocked = true
        refresh()
        run {
            delay(IslandPrefs.soundLinger(prefs).ms + 600)
            sampleMessage.value = null
            refresh()
        }
    }

    /** A pretend phone battery moment for Settings > Island > Try it. */
    fun previewPhone() {
        phoneMomentUntil = SystemClock.elapsedRealtime() + PHONE_MOMENT_MS
        phoneSerial.intValue++
        unlocked = true
        refresh()
    }

    /** A pretend message ding for Settings > Island > Try it, so you can see the Sounds look. */
    fun previewSound() = scope.launch {
        val (_, app) = kotlinx.coroutines.withContext(Dispatchers.Default) { SoundSource.exampleApp(context) }
        val now = SystemClock.elapsedRealtime()
        sampleSound.value = SoundSource.Heard(
            kind = SoundRules.Kind.Alert, pkg = null, app = app.label ?: "Messages", icon = app.icon ?: sampleIcon(), startedAt = now, active = true,
        )
        unlocked = true
        refresh()
        run {
            delay(3_000)
            sampleSound.value = sampleSound.value?.copy(active = false, endedAt = SystemClock.elapsedRealtime())
            refresh()
            delay(IslandPrefs.soundLinger(prefs).ms + 200)
            sampleSound.value = null
            refresh()
        }
    }

    /** Never more than a few dozen checks a second, whatever asks: the main thread must stay free for touches. */
    private val refreshGuard = MiniIslandRules.RefreshGuard()
    private var trailingRefresh: Job? = null

    fun refresh() {
        if (!refreshGuard.allow(SystemClock.elapsedRealtime())) {
            // Too many at once (something is asking again and again): skip this one and look once more shortly.
            if (trailingRefresh?.isActive != true) {
                android.util.Log.w("MiniIsland", "Too many refreshes in a second; slowing down")
                trailingRefresh = scope.launch { delay(250); refresh() }
            }
            return
        }
        val previewingMusic = sample.value != null
        val previewing = previewingMusic || sampleSound.value != null
        val playing = previewingMusic || NowPlaying.state.value.playing
        val now = SystemClock.elapsedRealtime()
        val pausedFor = if (playing) 0L else if (play.stoppedAt == 0L) Long.MAX_VALUE else now - play.stoppedAt
        // Sounds that aren't music: while they play and for a moment after, even a half-second ding.
        val linger = IslandPrefs.soundLinger(prefs).ms
        val heard = sampleSound.value ?: SoundSource.heard.value
        val sampled = sampleSound.value != null
        // Swiped away, or an alert from the very app you're using (nothing new to tell you): not shown.
        val inUse = !sampled && IslandPrefs.skipInUse(prefs) && heard != null && heard.kind.blip &&
            heard.pkg != null && heard.pkg == SoundSource.foreground
        val heardOn = heard != null && (sampled || IslandPrefs.anySound(prefs)) && !inUse && heard.startedAt > dismissedAt &&
            SoundRules.showing(heard.active, heard.endedAt, now, linger)
        val soundOnly = !previewingMusic && MiniIslandRules.showAsSound(heardOn, heard?.musicLike == true, playing, play.counted)
        // A message that just arrived: its own look (or, over music, its icon in the right-hand spot).
        val msg = sampleMessage.value ?: SoundSource.message.value
        val msgOn = msg != null && msg.at > dismissedAt && SoundRules.showing(false, msg.at, now, linger)
        val messageOnly = !previewingMusic && msgOn && !playing
        val phoneMoment = !previewingMusic && now < phoneMomentUntil
        phoneBlip.value = phoneMoment && playing
        messageShown.value = if (msgOn) msg else null
        // An alert popping over music: the app's icon shows in the right-hand spot for a moment.
        val blip = heardOn && heard!!.kind.blip && playing
        soundShown.value = if (soundOnly || blip) heard else null
        val place = ScreenApp.place.value
        val timerOn = !previewing && IslandTimer.state.value != null
        val hiddenHere = !previewing && place is ScreenApp.Place.App && place.pkg in IslandPrefs.hideIn(prefs)
        val want = MiniIslandRules.wanted(
            MiniIslandRules.Inputs(
                enabled = previewing || IslandPrefs.mini(prefs),
                canDraw = window.canShow(),
                playing = playing,
                pausedForMs = pausedFor,
                playedRecently = previewing || play.played,
                airPodsOnly = !previewing && IslandPrefs.miniAirPodsOnly(prefs),
                airPodsUp = airPodsUp(),
                screenUnlocked = previewing || unlocked,
                alwaysWithAirPods = !previewing && IslandPrefs.miniAlways(prefs),
                anytime = !previewing && IslandPrefs.miniAnytime(prefs),
                // A message, a sound or a phone moment brings the pill up for its few seconds.
                sound = soundOnly || messageOnly || phoneMoment,
                timer = timerOn,
                hiddenHere = hiddenHere,
            )
        )
        content.value = if (previewingMusic) MiniIslandRules.Content.Music else MiniIslandRules.content(
            playing = playing, pausedForMs = pausedFor, playedRecently = play.played,
            airPodsUp = airPodsUp(), sound = soundOnly, message = messageOnly, phoneMoment = phoneMoment,
            placeKnown = place !is ScreenApp.Place.Unknown,
        )
        lastWant = want
        if (!want) GlintOverlays.miniOrigin = null
        if (want) {
            leaving.value = false
            if (!window.isShowing) show()
        } else if (window.isShowing) {
            if (!leaving.value) {
                leaving.value = true
                // Leaving always ends with the window gone, even if its animation stalls (screen
                // off mid-way, phone busy): an invisible window must never linger at the top.
                scope.launch {
                    delay(LEAVE_SAFETY_MS)
                    if (leaving.value && window.isShowing) window.dismiss()
                }
            }
        }
        // Something lingering (paused music, a sound that just ended): check again when it runs out.
        lingerJob?.cancel()
        // The soonest of the things that are about to run out (a sound, a message, a phone moment).
        val soundLeft = listOfNotNull(
            if (heardOn && !heard!!.active) linger - (now - heard.endedAt) else null,
            if (msgOn) linger - (now - msg!!.at) else null,
            if (phoneMoment) phoneMomentUntil - now else null,
        ).filter { it > 0L }.minOrNull()
        MiniIslandRules.nextCheck(playing, pausedFor, soundLeft)?.let { wait ->
            lingerJob = scope.launch {
                delay(wait)
                refresh()
            }
        }
    }

    /**
     * The pill went up as a normal overlay, where the status bar takes its taps: now and then
     * (at most 3 times, a day apart, never once it's switched on) a pop-up points to the one
     * switch that fixes it.
     */
    private fun maybeSuggestTaps() {
        if (window.aboveBar || sample.value != null || !prefs.getBoolean(IslandPrefs.PREF_MASTER, true)) return
        if (!IslandAccess.isAvailable(context) || IslandAccess.isEnabled(context)) return
        val count = prefs.getInt(IslandPrefs.PREF_TAP_NUDGES, 0)
        val now = System.currentTimeMillis()
        if (count >= 3 || now - prefs.getLong(IslandPrefs.PREF_TAP_NUDGE_AT, 0L) < 20 * 60 * 60 * 1000L) return
        prefs.edit().putInt(IslandPrefs.PREF_TAP_NUDGES, count + 1).putLong(IslandPrefs.PREF_TAP_NUDGE_AT, now).apply()
        scope.launch {
            delay(2_000)
            if (window.isShowing && !window.aboveBar) GlintOverlays.showIsland(context, IslandEvent.TapSetup)
        }
    }

    private fun show() {
        val geo = MiniGeometry(context, look = look.value)
        shownFor = geo.key
        window.onShownChanged = { shown ->
            if (!shown) {
                GlintOverlays.miniOrigin = null
                // Taken away by the system while still wanted (permission or service change):
                // look again in a moment, so it comes back by itself when it can.
                if (lastWant) scope.launch { delay(1_000); refresh() }
            }
        }
        window.show(geo.compactWindow, geo.windowTop, geo.offsetX) {
            val live by NowPlaying.state.collectAsState()
            val pods by GlintOverlays.snapshot.collectAsState()
            val heart by me.kavishdevar.librepods.services.HeartRate.state.collectAsState()
            val talking by me.kavishdevar.librepods.utils.ConversationTiming.talking.collectAsState()
            val panel by IslandAccess.panelOpen.collectAsState()
            val link by GlintStatus.link.collectAsState()
            val heardLive by SoundSource.heard.collectAsState()
            val place by ScreenApp.place.collectAsState()
            val timer by IslandTimer.state.collectAsState()
            val wallpaper by ScreenApp.wallpaper.collectAsState()
            MiniIslandHost(
                geometry = geo,
                track = sample.value ?: withSource(live, heardLive),
                heard = soundShown.value,
                message = messageShown.value,
                phoneBlip = phoneBlip.value,
                phoneSerial = phoneSerial.intValue,
                onPutAway = { putAway() },
                content = content.value,
                pods = pods,
                // On the small pill the heart only shows with a real number to show.
                heartBpm = me.kavishdevar.librepods.services.HeartView.of(heart, link, airPodsUp(), System.currentTimeMillis())
                    .takeIf { it.worthAPill }?.bpm,
                leaving = leaving.value,
                hidden = !window.statusBarVisible.value,
                handOff = GlintOverlays.islandVisible.value,
                onVisible = { width ->
                    // Tells the big island where (and how wide) to grow from while the pill is up.
                    GlintOverlays.miniOrigin = width?.let { geo.origin(it) }
                },
                look = look.value,
                talking = talking,
                onWindowSize = { window.resize(it) },
                onTouchable = { window.setTouchable(it) },
                onPresent = { window.setPresent(it) },
                onGone = { window.dismiss() },
                actions = actions.value,
                panelOpen = panel,
                onAction = { perform(it) },
                // The timer beside the music: a ringing one is silenced, a running one opens the glance with its controls.
                onDetachedTap = {
                    if (sample.value == null) {
                        if (IslandTimer.state.value?.ringing == true) IslandTimer.cancel(context)
                        else GlintOverlays.showIsland(context, IslandEvent.Glance, expand = true)
                    }
                },
                onPullOutside = { IslandAccess.openNotifications() },
                place = place,
                timer = timer,
                budsUp = airPodsUp(),
                wallpaper = wallpaper,
                moment = moment.value,
            )
        }
        maybeSuggestTaps()
    }

    /** Does what a gesture on the pill is set to do. The Try-it sample never touches real music. */
    private fun perform(a: IslandGestures.Action) {
        val sampling = sample.value != null
        when (a) {
            IslandGestures.Action.Expand -> {
                // A ringing timer: the tap you'd reach for silences it.
                if (!sampling && IslandTimer.state.value?.ringing == true) { IslandTimer.cancel(context); return }
                when (MiniIslandRules.tapOpens(content.value)) {
                    // A tap on a sound or a message opens the app it came from (nothing when pro can't tell which).
                    MiniIslandRules.TapOpens.SoundApp -> if (!sampling && sampleMessage.value == null) {
                        SoundSource.openApp(context, messageShown.value?.pkg ?: soundShown.value?.pkg)
                    }
                    MiniIslandRules.TapOpens.AirPods -> GlintOverlays.showIsland(context, IslandEvent.Connected, expand = true)
                    // The iPhone style opens straight into the music (Apple's Now Playing); the glass
                    // style keeps its small music pop-up unless the AirPods are there to show.
                    MiniIslandRules.TapOpens.Music -> GlintOverlays.showIsland(
                        context, IslandEvent.Music,
                        expand = airPodsUp() || IslandPrefs.style(prefs) == me.kavishdevar.librepods.services.IslandSpec.Style.IPhone,
                    )
                    // Nothing playing: what's live and the phone's own controls, never an empty music player.
                    MiniIslandRules.TapOpens.Glance -> GlintOverlays.showIsland(context, IslandEvent.Glance, expand = true)
                }
            }
            IslandGestures.Action.PlayPause -> if (!sampling) NowPlaying.playPause(context)
            IslandGestures.Action.Next -> if (!sampling) NowPlaying.skip(context, true)
            IslandGestures.Action.Previous -> if (!sampling) NowPlaying.skip(context, false)
            IslandGestures.Action.OpenApp -> GlintOverlays.openApp(context)
            IslandGestures.Action.ListeningMode -> if (!sampling) GlintOverlays.cycleListeningMode(context)
            IslandGestures.Action.Notifications -> IslandAccess.openNotifications()
            IslandGestures.Action.Nothing -> {}
        }
    }
}

/** Where the front camera is, and the pill's sizes around it, in pixels. */
internal class MiniGeometry(
    context: Context,
    testCutouts: List<android.graphics.Rect>? = null,
    /** What it shows and how big (Settings > Islands). */
    val look: IslandLook.Look = IslandLook.read(IslandPrefs.prefs(context)),
) {
    private val density = context.resources.displayMetrics.density
    private val screen = GlintOverlays.screenSize(context)
    /** Room around the pill for the swipe nudge and the springy overshoot. */
    val margin = 14f * density
    val hole: android.graphics.Rect?
    val size: MiniIslandRules.Size
    /** The camera's centre, measured from the top of the screen. */
    val centerY: Float
    val offsetX: Int

    /**
     * What this was measured for: measure again when any of it changes (rotation, fold, display
     * size, or a size/width/slot choice that changes the window).
     */
    val key: Any

    init {
        val cutouts = (testCutouts ?: GlintOverlays.cameraCutouts(context))
            .map { MiniIslandRules.Box(it.left, it.top, it.right, it.bottom) }
        // The camera to wrap (punch-hole or notch near the middle); corner cameras and phones
        // without a cutout get the pill in the middle of the status bar instead.
        val cam = MiniIslandRules.pickCamera(cutouts, screen.width, screen.height)
        hole = cam?.let { android.graphics.Rect(it.left, it.top, it.right, it.bottom) }
        size = MiniIslandRules.size(cam?.width?.toFloat() ?: 0f, cam?.height?.toFloat() ?: 0f, density, screen.width.toFloat(), look)
        centerY = MiniIslandRules.centerY(cam, GlintOverlays.statusBarHeight(context), size.height, density)
        offsetX = cam?.let { (it.centerX - screen.width / 2f).roundToInt() } ?: 0
        key = listOf(screen, density, context.resources.configuration.orientation, size.height, size.compactWidth, size.wideWidth)
    }

    val windowTop: Int get() = (centerY - size.height / 2f - margin).roundToInt()
    val compactWindow = IntSize((size.compactWidth + margin * 2).roundToInt(), (size.height + margin * 2).roundToInt())
    val wideWindow = IntSize((size.wideWidth + margin * 2).roundToInt(), (size.wideHeight + margin * 2).roundToInt())
    /** The window while a message shows: wider than the pill's usual one (only for those few seconds), same height. */
    val messageWindow = IntSize((size.messageWidth + margin * 2).roundToInt().coerceAtLeast(compactWindow.width), compactWindow.height)
    /** A second thing at once (a timer while music plays) sits in a detached circle this wide, beside the pill. */
    val detachedD: Float get() = size.height
    val detachedGap: Float get() = me.kavishdevar.librepods.services.IslandSpec.DETACHED_GAP_DP * density * look.size.factor
    /** The window while the detached circle is out: wider on both sides (it stays centred on the camera). */
    val detachedWindow = IntSize(
        (compactWindow.width + 2f * (detachedGap + detachedD)).roundToInt().coerceAtMost(maxOf(screen.width, compactWindow.width)),
        compactWindow.height,
    )
    /** The size it grows out of: the camera hole itself. */
    val seedW: Float get() = hole?.width()?.toFloat()?.coerceAtMost(size.compactWidth) ?: (size.height * 0.6f)
    val seedH: Float get() = hole?.height()?.toFloat()?.coerceAtMost(size.height) ?: (size.height * 0.6f)

    /** How wide a slot is in this pill. */
    fun slotW(slot: IslandLook.Slot): Float = IslandLook.slotWidth(slot, size.side, density, look.size.factor)

    /** The compact pill's width with [l] and [r] beside the camera (it stays centred on the camera). */
    fun widthFor(l: IslandLook.Slot, r: IslandLook.Slot, limit: Float = size.compactWidth): Float {
        val half = maxOf(slotW(l), slotW(r))
        return (size.center + 2f * (size.inset + if (half > 0f) half + size.gap else 0f)).coerceAtMost(limit)
    }

    /** The resting pill, for a pop-up to grow out of. */
    fun origin(width: Float = size.compactWidth) = GlintOverlays.MiniOrigin(
        dx = offsetX.toFloat(),
        top = centerY - size.height / 2f,
        width = width,
        height = size.height,
        side = size.side,
        lensBottom = hole?.let { (it.bottom - (centerY - size.height / 2f)).coerceIn(0f, size.height) } ?: (size.height * 0.5f),
    )
}

@Composable
internal fun MiniIslandHost(
    geometry: MiniGeometry,
    track: NowPlaying.Track,
    leaving: Boolean,
    hidden: Boolean,
    onWindowSize: (IntSize) -> Unit,
    /** What it shows: the music, or the AirPods (battery, listening mode, heart). */
    content: MiniIslandRules.Content = MiniIslandRules.Content.Music,
    pods: PodsSnapshot = PodsSnapshot(),
    heartBpm: Int? = null,
    /** The sound being shown: a non-music sound (Sounds look) or an alert popping over music. */
    heard: SoundSource.Heard? = null,
    /** The message being shown: the Messages look, or its icon popping over music. */
    message: SoundSource.Message? = null,
    /** The phone's battery moment is on over music: its ring takes the right-hand spot for a moment. */
    phoneBlip: Boolean = false,
    /** Changes with each phone battery moment (each gets its own little bounce). */
    phoneSerial: Int = 0,
    /** Swipe up: put away what's out. Returns whether there was something to put away. */
    onPutAway: () -> Boolean = { false },
    /** A mini island pop-up is up (it grew out of this pill): this one stays, softly blurred. */
    handOff: Boolean = false,
    /** Told when it's up (with its current width, for a pop-up to grow out of) or gone (null). */
    onVisible: (Float?) -> Unit = {},
    /** Conversation Awareness has the music down. */
    talking: Boolean = false,
    onTouchable: (Boolean) -> Unit,
    onGone: () -> Unit,
    /** A gesture on the pill: do what it's set to do (Settings > Islands > Gestures). */
    onAction: (IslandGestures.Action) -> Unit,
    /** What each gesture does. */
    actions: Map<IslandGestures.Gesture, IslandGestures.Action> = IslandGestures.defaults,
    /** What goes beside the camera in each situation, its size and glow. */
    look: IslandLook.Look = geometry.look,
    /** Settings' live preview and screenshots: show this situation whatever is going on. */
    forceSituation: IslandLook.Situation? = null,
    /**
     * Out of sight but still there (false) or back (true): the window goes fully transparent
     * and untouchable so it can't eat taps meant for the app below.
     */
    onPresent: (Boolean) -> Unit = {},
    /** A pull down on the see-through edge around the pill (above the status bar): notifications. */
    onPullOutside: () -> Unit = {},
    /** The notification shade or another system panel covers the top: step aside at once. */
    panelOpen: Boolean = false,
    /** Screenshots only: draw this state without animating. */
    still: Float? = null,
    stillWide: Float = 0f,
    stillPress: Float = 0f,
    stillAck: MiniAck? = null,
    /** Where you are on the phone: the app in front (its icon shows), the home screen, the lock screen. */
    place: ScreenApp.Place = ScreenApp.Place.Unknown,
    /** The island's own timer, when one is running or ringing. */
    timer: IslandTimer.State? = null,
    /** The headphones are connected (their battery is worth a glance). */
    budsUp: Boolean = false,
    /** The wallpaper's colours, for the home glyph. */
    wallpaper: List<Color> = emptyList(),
    /** A short moment with words under the camera (the phone plugged in). */
    moment: MiniMoment? = null,
    /** Screenshots only: how far the app swap (and the padlock opening) has got, 0..1. */
    stillSwap: Float? = null,
    /** A tap on the detached circle (the timer beside the music). */
    onDetachedTap: () -> Unit = {},
    /** Screenshots only: how far the detached circle has split off, 0..1. */
    stillSplit: Float? = null,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current.density
    val reduce = remember { GlintComfort.reduceMotion(context) }
    val prefs = remember { IslandPrefs.prefs(context) }
    // Settings are read live, so a change in Settings > Islands applies at once.
    val hapticsOn by rememberPref(prefs, IslandPrefs.PREF_HAPTICS, true)
    val buzz by androidx.compose.runtime.rememberUpdatedState(remember(hapticsOn) { IslandBuzz(GlintHaptics(view), hapticsOn) })
    // The iPhone look (Settings > Island > Style): opaque black in light and dark, Apple's key
    // line only on a dark background, the 44 dp corner, a detached circle for a second thing.
    val iphone by rememberPref(prefs, IslandPrefs.PREF_IPHONE_LOOK, true)
    val darkBg = androidx.compose.foundation.isSystemInDarkTheme()
    // The edge: Apple's key line (dark backgrounds only) or a Liquid Glass rim (Settings > Island > Edge).
    val edgeChoice by rememberIntPref(prefs, IslandPrefs.PREF_EDGE, me.kavishdevar.librepods.services.IslandSpec.Edge.KeyLine.ordinal)
    val glassEdge = edgeChoice == me.kavishdevar.librepods.services.IslandSpec.Edge.Glass.ordinal
    // A pop-up that opens around the camera covers this pill (it's the same black shape): no
    // blur here then, the pill's contents just make way underneath.
    val coveredByPopUp = iphone && handOff && GlintOverlays.islandAround.value
    // Full-screen apps hide it, but only after the status bar has been gone for a moment, so a
    // screen that briefly hides it (opening or closing an app) doesn't make it flicker.
    var fullScreen by remember { mutableStateOf(false) }
    LaunchedEffect(hidden) { if (hidden) { delay(FULL_SCREEN_MS); fullScreen = true } else fullScreen = false }
    // A screenshot from the island's Capture button: out of the picture at once.
    val capturing = GlintOverlays.capturing.value
    val visible = !leaving && !fullScreen && !panelOpen && !capturing

    // ---- What it shows right now ----
    val musicContent = content == MiniIslandRules.Content.Music
    val messageContent = content == MiniIslandRules.Content.Message
    // The Messages look (icon and name) is wider than the pill's window. The window grows first,
    // then the name slides in; until then the pill shows the Sounds look (icon and bars).
    val needsMessageWindow = geometry.messageWindow.width > geometry.compactWindow.width
    var msgWindowReady by remember { mutableStateOf(!needsMessageWindow || still != null || forceSituation != null) }
    val messageShown = messageContent && msgWindowReady
    val soundContent = content == MiniIslandRules.Content.Sound || (messageContent && !messageShown)
    val screenContent = content == MiniIslandRules.Content.Screen
    // Where you are on the phone, when that's what the pill shows.
    val placeKind = if (!screenContent) null else when (place) {
        is ScreenApp.Place.App -> IslandLook.Place.App
        ScreenApp.Place.Home -> IslandLook.Place.Home
        is ScreenApp.Place.Locked -> IslandLook.Place.Locked
        ScreenApp.Place.Unknown -> null
    }
    val resting = content == MiniIslandRules.Content.Rest || (screenContent && placeKind == null)
    val situation = forceSituation ?: IslandLook.situation(
        musicContent, track.playing, talking, pods.budsCharging, rest = resting, sound = soundContent, message = messageShown, place = placeKind,
    )
    val under = if (situation == IslandLook.Situation.Talking) {
        if (forceSituation != null) IslandLook.Situation.Music
        else if (resting) IslandLook.Situation.Rest
        else IslandLook.underneath(musicContent, track.playing, pods.budsCharging, sound = soundContent, message = messageShown, place = placeKind)
    } else situation
    val (baseL, baseR) = look.slots(situation, under)
    // An alert popping over music (a message ding): the app's icon takes the right-hand spot for a
    // moment, then the music's own slot comes back (the usual cross-fade and width spring).
    val blipSource = heard?.kind?.blip == true || message != null
    val blipping = (blipSource || phoneBlip) && forceSituation == null &&
        (situation == IslandLook.Situation.Music || situation == IslandLook.Situation.Paused)
    // The phone's own ring pops in only when nothing else (a message, an alert) is the news.
    val blipPhone = phoneBlip && !blipSource
    // A running or ringing timer is always on show, like the iPhone's timer: it takes the
    // right-hand spot in every situation except a moment's sound or message, or talking.
    // (Previews pass a timer only when one really runs, so they show it too.)
    val timerHere = timer != null && situation != IslandLook.Situation.Talking && situation != IslandLook.Situation.Sound &&
        situation != IslandLook.Situation.Message && baseL != IslandLook.Slot.Glance && baseR != IslandLook.Slot.Glance
    // Two things at once, the iPhone way: music stays on the island (cover and bars) and the
    // timer splits off into a detached circle beside it, instead of taking the bars' place.
    val detached = iphone && timerHere && (under == IslandLook.Situation.Music || under == IslandLook.Situation.Paused) &&
        baseL != IslandLook.Slot.Nothing
    val wantL = baseL
    val wantR = if (blipping) (if (blipPhone) IslandLook.Slot.Phone else IslandLook.Slot.App)
        else if (detached) baseR
        else if (timerHere) IslandLook.Slot.Glance else baseR
    // The music, sound and phone situations are black, at one with the camera; the AirPods ones a dark graphite.
    val musicTone = under == IslandLook.Situation.Music || under == IslandLook.Situation.Paused ||
        under == IslandLook.Situation.Sound || under == IslandLook.Situation.Message || under == IslandLook.Situation.Rest ||
        under == IslandLook.Situation.App || under == IslandLook.Situation.Home || under == IslandLook.Situation.Locked
    val showIcons by rememberPref(prefs, IslandPrefs.PREF_SOUND_ICONS, true)
    // The app behind the sound, when pro can tell (the music app's own icon during music).
    val fromMoment = messageContent || soundContent || (blipping && !blipPhone)
    val appIcon = if (!showIcons) null else if (fromMoment) (message?.icon ?: heard?.icon) else track.icon
    val appKind = if (fromMoment) (heard?.kind ?: if (message != null) SoundRules.Kind.Alert else SoundRules.Kind.Other) else SoundRules.Kind.Media
    val appActive = if (fromMoment) (heard?.active == true || message != null) else track.playing
    val phone by PhoneStatus.state.collectAsState()
    // The App slot's ring takes the app icon's own colour (not the cover's).
    val appAccent = remember(appIcon, look.accent) {
        if (look.accent == IslandLook.Accent.White) Color.White else appIcon?.let { accentOf(it) } ?: Color.White
    }
    // What's playing, for the bars and the cover's brightness: music, or a sound that's still going.
    val lively = track.playing || (soundContent && heard?.active == true)

    // ---- Where you are on the phone ----
    // Changing apps: the old icon sinks away and the new one springs up in its place (the home
    // glyph's tiles and the padlock arrive the same way).
    val screenKey: Any? = when (place) {
        is ScreenApp.Place.App -> place.pkg
        ScreenApp.Place.Home -> "home"
        is ScreenApp.Place.Locked -> "locked"
        ScreenApp.Place.Unknown -> null
    }
    val screenIcon = (place as? ScreenApp.Place.App)?.icon
    var curIcon by remember { mutableStateOf(screenIcon) }
    var prevIcon by remember { mutableStateOf<ImageBitmap?>(null) }
    val swap = remember { Animatable(stillSwap ?: 1f) }
    val appPop by rememberPref(prefs, IslandPrefs.PREF_APP_POP, true)
    LaunchedEffect(screenKey) {
        if (still != null) { curIcon = screenIcon; prevIcon = null; return@LaunchedEffect }
        prevIcon = curIcon.takeIf { it !== screenIcon }
        curIcon = screenIcon
        if (reduce || !appPop) { swap.snapTo(1f); prevIcon = null }
        else { swap.snapTo(0f); swap.animateTo(1f, spring(dampingRatio = 0.58f, stiffness = 340f)); prevIcon = null }
    }
    // The padlock's shackle springs up and over as you unlock.
    val opening = (place as? ScreenApp.Place.Locked)?.opening == true
    val lockOpen = remember { Animatable(if (stillSwap != null && opening) stillSwap else 0f) }
    LaunchedEffect(opening) {
        if (still != null) return@LaunchedEffect
        val to = if (opening) 1f else 0f
        if (reduce) lockOpen.snapTo(to) else lockOpen.animateTo(to, spring(dampingRatio = 0.5f, stiffness = 300f))
    }
    // The smart glance: the one thing worth a look (a timer, charging, headphones, low battery).
    val glanceItem = GlanceRules.pill(timer, phone, budsUp, pods.budsLevel, pods.headphones)
    // Today's date for the calendar page, kept to the day.
    var today by remember { mutableStateOf(dayNow()) }

    // Slots cross-fade when the situation (or a choice) changes, and the pill morphs to its new width.
    var shownL by remember { mutableStateOf(wantL) }
    var shownR by remember { mutableStateOf(wantR) }
    var fromL by remember { mutableStateOf(wantL) }
    var fromR by remember { mutableStateOf(wantR) }
    val mix = remember { Animatable(1f) }
    LaunchedEffect(wantL, wantR) {
        if (wantL == shownL && wantR == shownR) return@LaunchedEffect
        fromL = shownL; fromR = shownR
        shownL = wantL; shownR = wantR
        if (reduce || still != null) mix.snapTo(1f) else { mix.snapTo(0f); mix.animateTo(1f, spring(dampingRatio = 1f, stiffness = 260f)) }
    }
    // The Messages look may use the wider window that is there only while a message shows.
    val targetW = geometry.widthFor(wantL, wantR, if (messageShown) geometry.size.messageWidth else geometry.size.compactWidth)
    val bodyW = remember { Animatable(targetW) }
    LaunchedEffect(targetW) {
        if (reduce || still != null) bodyW.snapTo(targetW) else bodyW.animateTo(targetW, spring(dampingRatio = 0.78f, stiffness = 360f))
    }
    val tone = remember { Animatable(if (musicTone) 0f else 1f) }
    LaunchedEffect(musicTone) {
        val to = if (musicTone) 0f else 1f
        if (reduce || still != null) tone.snapTo(to) else tone.animateTo(to, tween(320))
    }

    val appear = remember { Animatable(still ?: 0f) }
    val wide = remember { Animatable(stillWide) }
    // While a pop-up is out: a soft glass blur, with a gentle swell as it leaves and a little
    // bounce as it comes back in.
    val soften = remember { Animatable(0f) }
    val wave = remember { Animatable(1f) }
    // Covered by a pop-up opening around the camera: the contents step back under it.
    val makeWay = remember { Animatable(0f) }
    LaunchedEffect(coveredByPopUp) {
        if (still != null) return@LaunchedEffect
        val to = if (coveredByPopUp) 1f else 0f
        // Back from a pop-up: its last frame was exactly this pill (the cover in the same place),
        // so the contents are simply there again; a fade here would blink.
        if (reduce || !coveredByPopUp) makeWay.snapTo(to) else makeWay.animateTo(to, tween(120))
    }
    LaunchedEffect(handOff) {
        if (still != null) return@LaunchedEffect
        // The iPhone's island never blurs: the pop-up either covers it or sits below it, crisp.
        if (iphone) { soften.snapTo(0f); return@LaunchedEffect }
        if (reduce) { soften.snapTo(if (handOff) 1f else 0f); return@LaunchedEffect }
        coroutineScope {
            launch { soften.animateTo(if (handOff) 1f else 0f, tween(if (handOff) 280 else 420)) }
            launch {
                wave.animateTo(if (handOff) 1.07f else 0.95f, tween(140))
                wave.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 380f))
            }
        }
    }
    val nudge = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    /** The window's real width (it changes when the pill widens for a song's name). */
    var boxW by remember { androidx.compose.runtime.mutableIntStateOf(0) }

    // Grow out of the camera, or shrink back into it.
    val currentWidth by androidx.compose.runtime.rememberUpdatedState(targetW)
    LaunchedEffect(visible) {
        if (still != null) return@LaunchedEffect
        if (visible) onPresent(true) else onTouchable(false)
        if (visible) {
            val handedBack = SystemClock.elapsedRealtime() - GlintOverlays.returnToMiniAt < 800L
            // The big island just shrank back into this spot: carry on as if it never left.
            onVisible(currentWidth) // from the start, so a pop-up arriving meanwhile grows from here
            // Grows out of the camera on the same spring as the pop-ups (Settings > Island > Motion).
            val open = IslandPrefs.motion(prefs).open
            if (reduce || handedBack) appear.snapTo(1f) else appear.animateTo(1f, spring(dampingRatio = open.damping, stiffness = open.stiffness * 0.85f))
        } else {
            onVisible(null)
            if (wide.value > 0f) wide.snapTo(0f)
            onWindowSize(geometry.compactWindow)
            // Handing over to the big island: it starts exactly here, so vanish at once.
            if (reduce || handOff || panelOpen || capturing) appear.snapTo(0f) else appear.animateTo(0f, spring(dampingRatio = 1f, stiffness = 520f))
            if (leaving) onGone() else onPresent(false)
        }
    }
    // Its width changed while up: a pop-up should grow out of the new shape.
    LaunchedEffect(targetW) { if (still == null && visible && appear.value > 0.5f) onVisible(targetW) }

    // Asked to leave while already out of sight (for example in a full-screen app): just go.
    LaunchedEffect(leaving, visible) {
        if (still == null && leaving && !visible && appear.value < 0.01f) onGone()
    }

    val detachedNow by androidx.compose.runtime.rememberUpdatedState(detached)
    /** The window to go back to after the name, a moment or a message: room for the detached circle when it's out. */
    fun restWindow() = if (detachedNow) geometry.detachedWindow else geometry.compactWindow

    // Messages: the window grows before the name slides in, and shrinks after the pill has narrowed.
    LaunchedEffect(messageContent, visible, needsMessageWindow) {
        if (still != null || forceSituation != null || !needsMessageWindow) { msgWindowReady = true; return@LaunchedEffect }
        if (messageContent && visible) {
            onWindowSize(geometry.messageWindow)
            kotlinx.coroutines.withTimeoutOrNull(250) {
                androidx.compose.runtime.snapshotFlow { boxW }.first { it >= geometry.messageWindow.width }
            }
            androidx.compose.runtime.withFrameNanos { }
            msgWindowReady = true
        } else if (!messageContent) {
            msgWindowReady = false
            delay(500) // the pill narrows first, then the window follows
            if (wide.value < 0.01f) onWindowSize(restWindow())
        }
    }

    // The detached circle: the window widens first, then the circle buds off the pill's right end
    // (a liquid neck stretches and lets go); going back, it melts in, then the window narrows.
    // Starts tucked in, so it buds off only once its wider window is there (never cut off).
    val split = remember { Animatable(stillSplit ?: 0f) }
    LaunchedEffect(detached, visible, messageContent) {
        if (still != null) return@LaunchedEffect
        if (detached && visible) {
            if (wide.value < 0.01f && !messageContent) onWindowSize(geometry.detachedWindow)
            kotlinx.coroutines.withTimeoutOrNull(250) {
                androidx.compose.runtime.snapshotFlow { boxW }.first { it >= geometry.detachedWindow.width }
            }
            androidx.compose.runtime.withFrameNanos { }
            if (reduce) split.snapTo(1f) else split.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = 240f))
        } else if (!detached && split.value > 0f) {
            if (reduce) split.snapTo(0f) else split.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 320f))
            if (visible && wide.value < 0.01f && !messageContent) onWindowSize(geometry.compactWindow)
        }
    }

    // A little bounce when something new arrives (a sound, a message, the phone's battery), so it
    // draws the eye without being loud. Not for a burst of the same thing, not with Reduce motion.
    val arrival = Triple(heard?.startedAt, message?.startedAt, phoneSerial)
    LaunchedEffect(arrival) {
        if (still != null || reduce || !visible || handOff || arrival == Triple(null, null, 0)) return@LaunchedEffect
        wave.animateTo(1.07f, tween(110))
        wave.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 380f))
    }

    // Swipe up while the song's name is out: it tucks back at once.
    var collapseTick by remember { androidx.compose.runtime.mutableIntStateOf(0) }

    // A new song (or the first one): widen for a moment with its name, then tuck back.
    val songKey = track.title to track.artist
    val names by rememberPref(prefs, IslandPrefs.PREF_MINI_NAMES, true)
    // Each song is announced once: coming back after the big island closes doesn't repeat it.
    var announced by remember { mutableStateOf<Pair<String?, String?>?>(null) }
    LaunchedEffect(songKey, visible) {
        if (still != null || forceSituation != null || !visible || !names || track.title == null || songKey == announced) return@LaunchedEffect
        announced = songKey
        delay(if (appear.value < 0.9f) 380 else 0)
        onWindowSize(geometry.wideWindow)
        // Grow only once the bigger window is really there (no flicker, nothing cut off).
        kotlinx.coroutines.withTimeoutOrNull(250) {
            androidx.compose.runtime.snapshotFlow { boxW }.first { it >= geometry.wideWindow.width }
        }
        androidx.compose.runtime.withFrameNanos { }
        if (reduce) wide.snapTo(1f) else wide.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = 300f))
        // Stays for the time chosen in Settings, or until you swipe up.
        val t0 = collapseTick
        kotlinx.coroutines.withTimeoutOrNull(IslandPrefs.nameLinger(prefs).ms) {
            androidx.compose.runtime.snapshotFlow { collapseTick }.first { it != t0 }
        }
        val swiped = collapseTick != t0
        if (reduce) wide.snapTo(0f) else wide.animateTo(0f, spring(dampingRatio = 1f, stiffness = if (swiped) 600f else 340f))
        onWindowSize(restWindow())
    }

    // Sound bars, talking dots, the song's progress and a low battery's breathing: ticked about 30
    // times a second (plenty for things this small, and half the work of every frame).
    val clock = remember { mutableLongStateOf(0L) }
    val shows = setOf(shownL, shownR)
    val lowPulse = IslandLook.Slot.Battery in shows && (pods.budsLevel ?: 100) <= 10 && !pods.budsCharging
    // A ringing timer shakes its bell (the one glance that keeps moving).
    // The timer shows in the glance spot, or in the detached circle beside the music.
    val glanceOut = IslandLook.Slot.Glance in shows || detached
    val ringing = glanceOut && glanceItem is GlanceRules.Item.Timer && glanceItem.state.ringing
    // The phone's own ring breathes when it is very low and not charging.
    val phoneLowPulse = IslandLook.Slot.Phone in shows && phone.known && phone.level <= 10 && !phone.charging
    val animated = (lively && (IslandLook.Slot.Bars in shows || IslandLook.Slot.Cover in shows)) ||
        (appActive && IslandLook.Slot.App in shows) || IslandLook.Slot.Talk in shows || lowPulse || phoneLowPulse || ringing
    val moving = visible && animated && !reduce && still == null
    // The bars follow the real music (whatever app plays it) while they're on show.
    val listening = visible && track.playing && IslandLook.Slot.Bars in shows && still == null
    androidx.compose.runtime.DisposableEffect(listening) {
        if (listening) MusicPulse.acquire(context)
        onDispose { if (listening) MusicPulse.release() }
    }
    val pulseLive by MusicPulse.live.collectAsState()
    val synced = listening && pulseLive && !reduce
    /** What the bars show (eased toward the music so they glide rather than jump). */
    val bars = remember { FloatArray(MusicPulse.BANDS) }
    LaunchedEffect(moving, synced) {
        var last = 0L
        while (moving) {
            // In step with the music: about 60 a second for smooth bars; otherwise about 30 a
            // second, plenty for the rest.
            delay(if (synced) 16L else 33L)
            val now = SystemClock.elapsedRealtime()
            val dt = if (last == 0L) 16f else (now - last).toFloat().coerceAtMost(100f)
            last = now
            if (synced) {
                val target = MusicPulse.levels.value
                for (i in bars.indices) {
                    val to = target.getOrElse(i) { 0f }
                    // Quick up on a beat, softer on the way down (like a real level meter).
                    val rate = if (to > bars[i]) 0.045f else 0.012f
                    bars[i] += (to - bars[i]) * (1f - kotlin.math.exp(-rate * dt))
                }
            }
            clock.longValue = now
        }
    }
    val level = remember { mutableFloatStateOf(if (lively) 1f else 0f) }
    LaunchedEffect(lively) {
        val a = Animatable(level.floatValue)
        // Bars rise from dots with a small springy lift, and settle back softly on pause.
        if (reduce) a.snapTo(if (lively) 1f else 0f)
        else a.animateTo(if (lively) 1f else 0f, spring(dampingRatio = if (lively) 0.7f else 1f, stiffness = 300f)) { level.floatValue = value }
        level.floatValue = a.value
    }

    // The time for the Clock slot: kept to the minute, and only while it's on show.
    val is24 = remember { android.text.format.DateFormat.is24HourFormat(context) }
    var clockText by remember { mutableStateOf(clockNow(is24)) }
    LaunchedEffect(visible, IslandLook.Slot.Clock in shows) {
        if (still != null || !visible || IslandLook.Slot.Clock !in shows) return@LaunchedEffect
        while (true) {
            clockText = clockNow(is24)
            delay(60_000L - System.currentTimeMillis() % 60_000L + 50L)
        }
    }

    // The date for the calendar page: looked at again just after midnight, only while it's on show.
    LaunchedEffect(visible, IslandLook.Slot.Date in shows) {
        if (still != null || !visible || IslandLook.Slot.Date !in shows) return@LaunchedEffect
        while (true) {
            today = dayNow()
            val c = java.util.Calendar.getInstance()
            val msToMidnight = ((24 - c.get(java.util.Calendar.HOUR_OF_DAY)) * 3_600_000L) -
                c.get(java.util.Calendar.MINUTE) * 60_000L - c.get(java.util.Calendar.SECOND) * 1_000L
            delay(msToMidnight.coerceIn(1_000L, 24 * 3_600_000L) + 500L)
        }
    }
    // A running timer in the glance spot: its number and ring move on once a second (not every
    // frame), and only while it's on show.
    val timerShown = visible && glanceOut && glanceItem is GlanceRules.Item.Timer && glanceItem.state.running
    var timerNow by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(timerShown, timer) {
        if (!timerShown || still != null) return@LaunchedEffect
        while (true) {
            timerNow = SystemClock.elapsedRealtime()
            val left = timer?.let { TimerRules.left(it, timerNow) } ?: 0L
            delay(((left % 1_000L).takeIf { it > 0L } ?: 1_000L) + 5L)
        }
    }

    // A moment (the phone plugged in): the pill widens with a line of words for a few seconds.
    var momentShown by remember { mutableStateOf<MiniMoment?>(null) }
    var momentDone by remember { mutableLongStateOf(0L) }
    LaunchedEffect(moment?.id, visible) {
        val m = moment
        if (m == null) {
            // Put away (swiped up) while out: tuck back in at once.
            if (momentShown != null && still == null) {
                if (reduce) wide.snapTo(0f) else wide.animateTo(0f, spring(dampingRatio = 1f, stiffness = 340f))
                onWindowSize(restWindow())
                momentShown = null
            }
            return@LaunchedEffect
        }
        if (still != null || !visible || m.id == momentDone) return@LaunchedEffect
        momentDone = m.id
        momentShown = m
        delay(if (appear.value < 0.9f) 380 else 0)
        onWindowSize(geometry.wideWindow)
        kotlinx.coroutines.withTimeoutOrNull(250) {
            androidx.compose.runtime.snapshotFlow { boxW }.first { it >= geometry.wideWindow.width }
        }
        androidx.compose.runtime.withFrameNanos { }
        if (reduce) wide.snapTo(1f) else wide.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = 300f))
        delay(MiniMoment.SHOW_MS)
        if (reduce) wide.snapTo(0f) else wide.animateTo(0f, spring(dampingRatio = 1f, stiffness = 340f))
        onWindowSize(restWindow())
        momentShown = null
    }

    val beat = rememberHeartBeat(heartBpm, reduce || still != null, peak = 1.18f)
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val accent = remember(track.art, appIcon, look.accent) {
        if (look.accent == IslandLook.Accent.White) Color.White
        else (track.art ?: appIcon)?.let { accentOf(it) } ?: Color.White
    }
    val paused = remember { ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) }
    val m = geometry.margin
    val s = geometry.size
    val describe = if (placeKind != null) buildString {
        append("Dynamic Island")
        when (place) {
            is ScreenApp.Place.App -> place.label?.let { append(", in ").append(it) }
            ScreenApp.Place.Home -> append(", home screen")
            is ScreenApp.Place.Locked -> append(", locked")
            else -> {}
        }
        when (glanceItem) {
            is GlanceRules.Item.Timer -> append(if (glanceItem.state.ringing) ", timer done" else ", timer " + TimerRules.format(TimerRules.left(glanceItem.state, SystemClock.elapsedRealtime())))
            is GlanceRules.Item.Charging -> append(", charging ").append(glanceItem.level).append("%")
            is GlanceRules.Item.Buds -> glanceItem.level?.let { append(", headphones ").append(it).append("%") }
            is GlanceRules.Item.LowPhone -> append(", battery low ").append(glanceItem.level).append("%")
            else -> {}
        }
        append(". Tap for what's on, hold to open pro.")
    } else if (resting) "Dynamic Island. Tap for what's on, hold to open pro." else if (messageContent) buildString {
        append("Message").append(message?.app?.let { " from $it" } ?: "").append(message?.title?.let { ": $it" } ?: "")
        append(". Tap to open it, swipe up to put it away.")
    } else if (soundContent) buildString {
        append("Sound").append(heard?.app?.let { " from $it" } ?: "")
        append(if (heard?.pkg != null) ". Tap to open it, hold to open pro." else ". Hold to open pro.")
    } else if (!musicContent) buildString {
        append(pods.name)
        pods.budsLevel?.let { append(", battery ").append(it).append("%") }
        if (pods.listeningMode in 1..4) append(", ").append(listeningModeName(pods.listeningMode))
        heartBpm?.let { append(", heart rate ").append(it) }
        append(". Tap to open, hold to open pro.")
    } else buildString {
        append(if (track.playing) "Now playing" else "Paused")
        track.title?.let { append(": ").append(it) }
        track.artist?.let { append(" by ").append(it) }
        append(". Tap to open, swipe to change song, hold to open pro.")
    }
    // Words under the camera: a phone moment's line ("Charging · 76%"), else the song.
    val textLine = momentShown?.text ?: listOfNotNull(track.title, track.artist).joinToString("  ·  ")
    // What the Title slot says: the sender or app for a message, the app for another sound, else the song.
    val titleSource = when {
        message != null && (messageContent || blipping) -> message.title ?: message.app
        soundContent || blipping -> heard?.app ?: heard?.kind?.label
        else -> track.title
    }
    val shortTitle = remember(titleSource) { titleSource?.split(' ')?.filter { it.isNotBlank() }?.take(3)?.joinToString(" ") }

    val currentAction by androidx.compose.runtime.rememberUpdatedState(onAction)
    val currentActions by androidx.compose.runtime.rememberUpdatedState(actions)
    val currentPullOutside by androidx.compose.runtime.rememberUpdatedState(onPullOutside)
    val currentPutAway by androidx.compose.runtime.rememberUpdatedState(onPutAway)
    val currentDetachedTap by androidx.compose.runtime.rememberUpdatedState(onDetachedTap)
    val playingNow by androidx.compose.runtime.rememberUpdatedState(track.playing)
    val modeNow by androidx.compose.runtime.rememberUpdatedState(pods.listeningMode)
    val offAllowed = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE).getBoolean("off_listening_mode", true) }

    // Touch feedback: the pill squishes under the finger and brightens a little where it's held,
    // springs back on release, and stretches a touch when pulled. While touched (and a few
    // seconds after) its rim follows the phone's tilt like the rest of the glass.
    val press = remember { Animatable(stillPress) }
    var pressAt by remember { mutableStateOf<Offset?>(null) }
    val lightHold = remember { LightHold(context) }
    androidx.compose.runtime.DisposableEffect(lightHold) { onDispose { lightHold.close() } }
    val pull = remember { Animatable(0f) }
    // What a gesture just did, shown for a moment in the right-hand spot (play, pause, skip, mode).
    var ack by remember { mutableStateOf(stillAck) }
    val ackIn = remember { Animatable(if (stillAck != null) 1f else 0f) }
    var ackJob by remember { mutableStateOf<Job?>(null) }

    fun fire(g: IslandGestures.Gesture) {
        val a = currentActions[g] ?: IslandGestures.defaults.getValue(g)
        if (a == IslandGestures.Action.Nothing) {
            // Nothing set for this gesture: a small shake says so.
            if (!reduce) scope.launch {
                nudge.animateTo(3f * density, tween(50)); nudge.animateTo(-3f * density, tween(70))
                nudge.animateTo(0f, spring(dampingRatio = 0.4f, stiffness = 600f))
            }
            return
        }
        buzz.confirm()
        val shown = MiniAck.of(a, playingNow, ListeningModes.next(modeNow, offAllowed))
        if (shown != null) {
            ack = shown
            ackJob?.cancel()
            ackJob = scope.launch {
                if (reduce) ackIn.snapTo(1f) else { ackIn.snapTo(0f); ackIn.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 520f)) }
                delay(ACK_SHOW_MS)
                if (reduce) ackIn.snapTo(0f) else ackIn.animateTo(0f, tween(240))
            }
        }
        currentAction(a)
    }

    /** Where the pill is drawn right now, in this window's pixels. */
    fun pillBounds(): androidx.compose.ui.geometry.Rect {
        val a = appear.value.coerceAtLeast(0f)
        val compactW = lerp(geometry.seedW, bodyW.value, a)
        val compactH = lerp(geometry.seedH, s.height, a.coerceAtMost(1.15f))
        val pillW = lerp(compactW, s.wideWidth, wide.value)
        val pillH = lerp(compactH, s.wideHeight, wide.value)
        val top = m + (s.height - compactH) / 2f
        val left = boxW / 2f - pillW / 2f + nudge.value
        return androidx.compose.ui.geometry.Rect(left, top, left + pillW, top + pillH)
    }

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { boxW = it.width }
            .semantics {
                role = Role.Button
                contentDescription = if (detached) "$describe A timer runs beside it." else describe
                onClick(label = (actions[IslandGestures.Gesture.Tap1] ?: IslandGestures.Action.Expand).label) { fire(IslandGestures.Gesture.Tap1); true }
                onLongClick(label = (actions[IslandGestures.Gesture.Hold] ?: IslandGestures.Action.OpenApp).label) { fire(IslandGestures.Gesture.Hold); true }
            }
            .pointerInput(Unit) {
                // Every touch is one gesture: taps (counted for a moment, so a double tap never
                // fires the single-tap action first), a hold (fires while still held), a swipe
                // left or right, or a pull down. Touches on the see-through edge around the pill
                // only pass a pull on to the notification shade.
                val counter = IslandGestures.TapCounter(IslandGestures.TAP_GAP_MS)
                var pending: Job? = null
                val slop = viewConfiguration.touchSlop
                val swipe = 30f * density
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // Taps count as soon as the pill is there at all, even while it's still growing.
                    val inside = appear.value > 0.15f && pillBounds().inflate(6f * density).contains(down.position)
                    // The detached circle (the timer beside the music) is its own button: a tap opens it.
                    val circleC = Offset(pillBounds().right + geometry.detachedGap + geometry.detachedD / 2f, m + s.height / 2f)
                    if (!inside && split.value > 0.6f && wide.value < 0.5f && (down.position - circleC).getDistance() <= geometry.detachedD / 2f + 6f * density) {
                        buzz.touch()
                        var strayed = false
                        while (true) {
                            val ch = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            if ((ch.position - down.position).getDistance() > slop) strayed = true
                            ch.consume()
                            if (!ch.pressed) break
                        }
                        if (!strayed) { buzz.confirm(); currentDetachedTap() }
                        return@awaitEachGesture
                    }
                    var dx = 0f
                    var dy = 0f
                    var last = down.uptimeMillis
                    var held = false
                    if (inside) {
                        buzz.touch()
                        pressAt = down.position
                        lightHold.touch(scope)
                        if (!reduce) scope.launch { press.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 900f)) }
                    }
                    while (true) {
                        val steady = abs(dx) <= slop && abs(dy) <= slop
                        val ev = if (inside && !held && steady) {
                            val left = IslandGestures.HOLD_MS - (last - down.uptimeMillis)
                            if (left <= 0L) null else withTimeoutOrNull(left) { awaitPointerEvent() }
                        } else awaitPointerEvent()
                        if (ev == null) {
                            // Held still long enough: it's a hold, acted on while still held.
                            held = true
                            pending?.cancel()
                            counter.expire()
                            scope.launch { press.animateTo(0f, spring(dampingRatio = 0.42f, stiffness = 420f)) }
                            fire(IslandGestures.Gesture.Hold)
                            continue
                        }
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        dx = ch.position.x - down.position.x
                        dy = ch.position.y - down.position.y
                        last = ch.uptimeMillis
                        if (inside) pressAt = ch.position
                        if (inside && (abs(dx) > slop || abs(dy) > slop)) {
                            ch.consume()
                            if (!reduce) scope.launch {
                                // Rubber band: follows the finger a little, never far.
                                val sideways = abs(dx) >= abs(dy)
                                nudge.snapTo(if (sideways) (dx / density).let { it / (1f + abs(it) / 40f) } * density * 0.5f else 0f)
                                pull.snapTo(if (!sideways) (dy.coerceAtLeast(0f) / density).let { it / (1f + it / 30f) } * density * 0.45f else 0f)
                            }
                        }
                        if (!ch.pressed) break
                    }
                    if (!inside) {
                        if (dy > swipe && dy > abs(dx)) currentPullOutside()
                        return@awaitEachGesture
                    }
                    scope.launch { press.animateTo(0f, if (reduce) tween(100) else spring(dampingRatio = 0.42f, stiffness = 420f)) }
                    scope.launch { nudge.animateTo(0f, spring(dampingRatio = 0.45f, stiffness = 500f)) }
                    scope.launch { pull.animateTo(0f, spring(dampingRatio = 0.5f, stiffness = 420f)) }
                    when (IslandGestures.classify(dx, dy, last - down.uptimeMillis, slop, swipe, held)) {
                        IslandGestures.Kind.Tap -> {
                            val r = counter.tap(last, IslandGestures.maxUsefulTaps(currentActions))
                            pending?.cancel()
                            if (r.final) fire(IslandGestures.taps(r.count))
                            else pending = scope.launch {
                                delay(counter.gapMs)
                                val n = counter.expire()
                                if (n > 0) fire(IslandGestures.taps(n))
                            }
                        }
                        IslandGestures.Kind.Hold -> fire(IslandGestures.Gesture.Hold)
                        IslandGestures.Kind.SwipeLeft -> fire(IslandGestures.Gesture.SwipeLeft)
                        IslandGestures.Kind.SwipeRight -> fire(IslandGestures.Gesture.SwipeRight)
                        IslandGestures.Kind.PullDown -> fire(IslandGestures.Gesture.PullDown)
                        // Back toward the camera: tuck away the song's name, or whatever the pill has out.
                        IslandGestures.Kind.SwipeUp -> {
                            if (wide.value > 0.05f) { collapseTick++; buzz.confirm() }
                            else if (currentPutAway()) buzz.confirm()
                        }
                        IslandGestures.Kind.None -> {}
                    }
                }
            }
    ) {
        // Everything moves together: the swipe nudge, the press squish, the soft blur during a pop-up.
        Box(Modifier.fillMaxSize().graphicsLayer {
            translationX = nudge.value
            val squeeze = 1f - 0.07f * press.value
            scaleX = wave.value * squeeze; scaleY = wave.value * (1f - 0.04f * press.value)
            val b = soften.value * 5f * density
            renderEffect = if (b > 0.3f) androidx.compose.ui.graphics.BlurEffect(b, b, androidx.compose.ui.graphics.TileMode.Decal) else null
            alpha = 1f - 0.18f * soften.value
        }) {
        Canvas(Modifier.fillMaxSize()) {
            val a = appear.value.coerceAtLeast(0f)
            val w = wide.value
            if (a <= 0.001f) return@Canvas
            val cx = size.width / 2f
            val compactW = lerp(geometry.seedW, bodyW.value, a)
            val compactH = lerp(geometry.seedH, s.height, a.coerceAtMost(1.15f))
            val pillW = lerp(compactW, s.wideWidth, w)
            val pillH = lerp(compactH, s.wideHeight, w) + pull.value
            // The camera line stays put; the wide pill grows downward under it.
            val top = m + (s.height - compactH) / 2f
            val left = cx - pillW / 2f
            // The iPhone's corner: 44 dp, or a full capsule while shorter than 88 dp (always, compact).
            val r = if (iphone) me.kavishdevar.librepods.services.IslandSpec.cornerPx(pillH, density) else compactH / 2f
            val glow = look.glow.amount
            // With the AirPods (no music): a touch lighter than black with a faint rim of light,
            // so it stands out slightly from the background. With music it's pure black, at one
            // with the camera. Glow Off keeps it black always. The iPhone look is always black.
            val idle = if (iphone) 0f else tone.value * minOf(1f, glow)
            val fill = androidx.compose.ui.graphics.lerp(Color.Black, Color(0xFF1D1D20), idle)
            drawRoundRect(fill, Offset(left, top), Size(pillW, pillH), CornerRadius(r, r))
            // The detached circle (a timer beside the music): buds off the pill's right end with a
            // liquid neck, then floats a small gap away. Black like the island, with the same key line.
            val sp = split.value
            val circleD = geometry.detachedD * lerp(0.55f, 1f, sp.coerceIn(0f, 1f))
            val circleC = Offset(
                left + pillW + lerp(-circleD / 2f, geometry.detachedGap + geometry.detachedD / 2f, sp),
                top + compactH / 2f,
            )
            val circleOut = sp > 0.02f && wide.value < 0.5f
            if (circleOut) {
                drawCircle(Color.Black, circleD / 2f, circleC)
                if (!reduce) me.kavishdevar.librepods.presentation.glint.metaballNeck(
                    Offset(left + pillW - r, top + compactH / 2f), minOf(r, compactH / 2f), circleC, circleD / 2f, reach = 1.3f,
                )?.let { drawPath(it, Color.Black) }
            }
            // Pressed: the glass lights up a little, most where it's held.
            if (press.value > 0.01f) {
                val p = press.value
                drawRoundRect(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.10f * p), Color.White.copy(alpha = 0.03f * p)), top, top + pillH
                    ),
                    Offset(left, top), Size(pillW, pillH), CornerRadius(r, r),
                )
                val fx = ((pressAt?.x ?: (left + pillW / 2f)) - nudge.value).coerceIn(left + r, left + pillW - r)
                val glowR = pillH * 1.4f
                clipPath(Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(left, top, left + pillW, top + pillH, CornerRadius(r, r))) }) {
                    drawCircle(
                        androidx.compose.ui.graphics.Brush.radialGradient(
                            listOf(Color.White.copy(alpha = 0.16f * p), Color.White.copy(alpha = 0f)), Offset(fx, top + pillH / 2f), glowR
                        ),
                        glowR, Offset(fx, top + pillH / 2f),
                    )
                }
            }
            if (iphone) {
                // Apple's key line (a thin edge in the content's own colour, only on a dark
                // background), or the Liquid Glass rim (light from above, in light and dark).
                // Stronger with Glow Bright. Under a pop-up that opened around the camera it
                // steps back too, so it can never show through.
                val kl = (if (glassEdge) me.kavishdevar.librepods.services.IslandSpec.glassRimAlpha(darkBg, glow)
                    else me.kavishdevar.librepods.services.IslandSpec.keylineAlpha(darkBg, glow)) * (1f - makeWay.value)
                if (kl > 0f) {
                    val tint = androidx.compose.ui.graphics.lerp(Color.White, accent, 0.55f)
                    val outline = Path().apply {
                        addRoundRect(androidx.compose.ui.geometry.RoundRect(left + 0.5f, top + 0.5f, left + pillW - 0.5f, top + pillH - 0.5f, CornerRadius(r - 0.5f, r - 0.5f)))
                    }
                    drawIslandEdge(outline, androidx.compose.ui.geometry.Rect(left, top, left + pillW, top + pillH), glassEdge, kl, tint, density)
                    if (circleOut && sp > 0.85f) {
                        val ca = kl * ((sp - 0.85f) / 0.15f).coerceIn(0f, 1f)
                        val ring = Path().apply { addOval(androidx.compose.ui.geometry.Rect(circleC, circleD / 2f - 0.5f)) }
                        drawIslandEdge(ring, androidx.compose.ui.geometry.Rect(circleC, circleD / 2f), glassEdge, ca, tint, density)
                    }
                }
            } else if (glow > 0f) {
                // The rim catches the light from above and swings a little as you tilt the
                // phone (the same light as the rest of the app's glass). Faint with music.
                val swing = Math.toRadians(me.kavishdevar.librepods.presentation.glint.GlintLight.swing.floatValue.toDouble()).toFloat()
                val cxp = left + pillW / 2f
                val reach = pillH
                val from = Offset(cxp - kotlin.math.sin(swing) * reach, top - kotlin.math.cos(swing) * reach * 0.2f)
                val to = Offset(cxp + kotlin.math.sin(swing) * reach, top + pillH)
                val rim = ((0.07f + 0.15f * tone.value) * glow + 0.05f * press.value).coerceAtMost(0.6f)
                drawRoundRect(
                    androidx.compose.ui.graphics.Brush.linearGradient(
                        listOf(Color.White.copy(alpha = rim), Color.White.copy(alpha = rim * 0.18f)), from, to
                    ),
                    Offset(left + 0.5f, top + 0.5f), Size(pillW - 1f, pillH - 1f), CornerRadius(r - 0.5f, r - 0.5f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(0.8f * density),
                )
            }

            // Contents grow in with the pill (and shrink back into the camera with it): smaller
            // and fainter while the shape is still forming, never drawn at full size in a shape
            // that isn't there yet. They step back under a pop-up that covers the pill.
            val c0 = ((a - 0.5f) / 0.4f).coerceIn(0f, 1f) * (1f - makeWay.value)
            if (c0 <= 0f) return@Canvas
            val grow = (compactH / s.height).coerceIn(0.45f, 1f)
            val lineY = m + s.height / 2f
            val right = left + pillW
            // A gesture's result shows in the right-hand spot for a moment; what's there steps back.
            val ak = if (ack != null) ackIn.value.coerceIn(0f, 1f) else 0f
            val k = mix.value

            // Everything inside stays inside the black shape (and the detached circle), and off
            // the camera itself: Apple's content never runs into the island's edge or under the lens.
            val inside = Path().apply {
                addRoundRect(androidx.compose.ui.geometry.RoundRect(left, top, right, top + pillH, CornerRadius(r, r)))
                if (circleOut) addOval(androidx.compose.ui.geometry.Rect(circleC, circleD / 2f))
            }
            val lens = geometry.hole?.let { hole ->
                val hw = hole.width() + 2f * density
                val hh = hole.height() + 2f * density
                val hx = cx - nudge.value
                val hy = hole.exactCenterY() - geometry.windowTop
                Path().apply {
                    addRoundRect(androidx.compose.ui.geometry.RoundRect(hx - hw / 2f, hy - hh / 2f, hx + hw / 2f, hy + hh / 2f, CornerRadius(minOf(hw, hh) / 2f)))
                }
            }
            val clip = if (lens != null) Path.combine(androidx.compose.ui.graphics.PathOperation.Difference, inside, lens) else inside
            clipPath(clip) {

            // Each end's spot stays concentric with the shape's corner: in the compact pill that's
            // the round end itself; when the pill widens downward (a song's name) its corners get
            // rounder, so the spots slide in to keep the same gap from the curve.
            val endInset = me.kavishdevar.librepods.services.IslandSpec.insetFromCorner(
                // Measured from the nearer of the top and bottom edges (the spots sit on the camera line, near the top).
                corner = r, fromBottom = minOf(lineY - top, (top + pillH) - lineY), r = s.side / 2f, gap = s.inset,
            ).coerceAtLeast(s.inset + s.side / 2f)
            val extra = endInset - (s.inset + s.side / 2f)
            fun slotCentre(slot: IslandLook.Slot, leftSide: Boolean): Offset {
                val sw = geometry.slotW(slot)
                val o = (s.inset + sw / 2f + extra) * grow
                return Offset(if (leftSide) left + o else right - o, lineY)
            }
            fun slot(slot: IslandLook.Slot, leftSide: Boolean, alpha: Float, at: Offset? = null) {
                if (alpha <= 0.001f) return
                val centre = at ?: slotCentre(slot, leftSide)
                val data = SlotData(
                    side = s.side, density = density, fontScale = fontScale, track = track, pods = pods, heartBpm = heartBpm, beat = beat.value,
                    level = level.floatValue, accent = accent, paused = paused, clock = clock.longValue, moving = moving,
                    lowPulse = lowPulse && !reduce, phoneLow = phoneLowPulse && !reduce, measurer = measurer, shortTitle = shortTitle,
                    appIcon = appIcon, appAccent = appAccent, coverIcon = if (showIcons) track.icon else null, appKind = appKind, appActive = appActive,
                    phone = phone, clockText = clockText,
                    bars = if (synced) bars else null,
                    progressAt = if (moving) clock.longValue else SystemClock.elapsedRealtime(),
                    screenIcon = curIcon, prevIcon = prevIcon, swap = swap.value, wallpaper = wallpaper,
                    today = today, lockOpen = lockOpen.value, glance = glanceItem,
                    timerAt = if (still != null) 0L else timerNow,
                    squareArt = iphone,
                )
                if (grow >= 0.999f || at != null) drawSlot(slot, centre, geometry.slotW(slot), leftSide, alpha, data)
                else scale(grow, grow, centre) { drawSlot(slot, centre, geometry.slotW(slot), leftSide, alpha, data) }
            }
            // The detached circle's own content: the timer, concentric inside it.
            if (circleOut) slot(IslandLook.Slot.Glance, false, c0 * ((sp - 0.45f) / 0.55f).coerceIn(0f, 1f), circleC)
            if (fromL == shownL) slot(shownL, true, c0) else { slot(fromL, true, c0 * (1f - k)); slot(shownL, true, c0 * k) }
            val rs = 1f - ak
            if (fromR == shownR) slot(shownR, false, c0 * rs) else { slot(fromR, false, c0 * (1f - k) * rs); slot(shownR, false, c0 * k * rs) }
            if (ak > 0.01f) ack?.let {
                val at = if (shownR == IslandLook.Slot.Nothing) Offset(right - endInset * grow, lineY) else slotCentre(shownR, false)
                drawAck(it, at, s.side * grow * (0.6f + 0.4f * ak), Color.White.copy(alpha = ak))
            }
            }
        }
        // The song's name under the camera line while wide.
        if (wide.value > 0.05f && textLine.isNotEmpty()) {
            val dpW = (s.wideWidth - 28f * density) / density
            // The name line has a fixed height: follow big system font sizes only up to 115%.
            val ld = LocalDensity.current
            androidx.compose.runtime.CompositionLocalProvider(
                LocalDensity provides androidx.compose.ui.unit.Density(ld.density, ld.fontScale.coerceAtMost(1.15f))
            ) {
            Text(
                textLine,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                textAlign = TextAlign.Center,
                style = TextStyle(fontFamily = glintFontFamily, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = momentShown?.color ?: Color.White),
                modifier = Modifier
                    .offset { IntOffset((m + 14f * density).roundToInt(), (m + s.height + 3f * density).roundToInt()) }
                    .width(androidx.compose.ui.unit.Dp(dpW))
                    .graphicsLayer { alpha = ((wide.value - 0.4f) / 0.6f).coerceIn(0f, 1f) }
                    .basicMarquee(iterations = 1, initialDelayMillis = 900),
            )
            }
        }
        }
    }
}

/**
 * Keeps the shared glass light ([GlassLight]) following the phone's tilt while the pill is
 * touched and for a few seconds after, then lets it rest: not all day, for the battery.
 */
private class LightHold(private val context: Context) {
    private var held = false
    private var job: Job? = null

    fun touch(scope: CoroutineScope) {
        if (!held) { held = true; me.kavishdevar.librepods.presentation.glint.GlassLight.acquire(context) }
        job?.cancel()
        job = scope.launch {
            delay(3_500)
            close()
        }
    }

    fun close() {
        job?.cancel()
        job = null
        if (held) { held = false; me.kavishdevar.librepods.presentation.glint.GlassLight.release() }
    }
}

/** Everything a slot may need to draw itself, for one frame. */
private class SlotData(
    val side: Float,
    val density: Float,
    val fontScale: Float,
    val track: NowPlaying.Track,
    val pods: PodsSnapshot,
    val heartBpm: Int?,
    val beat: Float,
    /** 1 playing, 0 paused (animated between). */
    val level: Float,
    val accent: Color,
    val paused: ColorFilter,
    val clock: Long,
    val moving: Boolean,
    val lowPulse: Boolean,
    /** The phone's own battery is very low and not charging: its ring breathes. */
    val phoneLow: Boolean = false,
    val measurer: androidx.compose.ui.text.TextMeasurer,
    val shortTitle: String?,
    val progressAt: Long,
    /** The icon of the app making the sound (null: a symbol for [appKind]). */
    val appIcon: ImageBitmap? = null,
    /** The music app's icon, standing in for a cover that has no picture. */
    val coverIcon: ImageBitmap? = null,
    val appKind: SoundRules.Kind = SoundRules.Kind.Other,
    /** The colour of the App slot's ring: the icon's own most vivid colour (white when it has none). */
    val appAccent: Color = Color.White,
    val appActive: Boolean = false,
    val phone: PhoneStatus.Info = PhoneStatus.Info(),
    val clockText: String = "",
    /** The music's real levels (bass to treble), when pro can hear it; null: their own motion. */
    val bars: FloatArray? = null,
    /** The app in front's icon, the one it replaced (fading out), and how far the swap has got. */
    val screenIcon: ImageBitmap? = null,
    val prevIcon: ImageBitmap? = null,
    val swap: Float = 1f,
    val wallpaper: List<Color> = emptyList(),
    val today: Day = dayNow(),
    /** The padlock: 0 shut, 1 sprung open. */
    val lockOpen: Float = 0f,
    val glance: GlanceRules.Item? = null,
    /** elapsedRealtime the timer is drawn for (0: its still state, for screenshots). */
    val timerAt: Long = 0L,
    /** The iPhone look: the cover is a small rounded square, as in Apple's pictures, not a circle. */
    val squareArt: Boolean = false,
)

/**
 * A rounded square's outline centred at [c] (half its width [h], corner [cr]), drawn clockwise
 * from the middle of its top edge, so a part of it reads like a clock hand's sweep.
 */
private fun squareRing(c: Offset, h: Float, cr: Float): Path = Path().apply {
    val l = c.x - h; val t = c.y - h; val r = c.x + h; val b = c.y + h
    val k = cr.coerceAtMost(h)
    moveTo(c.x, t)
    lineTo(r - k, t)
    arcTo(androidx.compose.ui.geometry.Rect(r - 2 * k, t, r, t + 2 * k), -90f, 90f, false)
    lineTo(r, b - k)
    arcTo(androidx.compose.ui.geometry.Rect(r - 2 * k, b - 2 * k, r, b), 0f, 90f, false)
    lineTo(l + k, b)
    arcTo(androidx.compose.ui.geometry.Rect(l, b - 2 * k, l + 2 * k, b), 90f, 90f, false)
    lineTo(l, t + k)
    arcTo(androidx.compose.ui.geometry.Rect(l, t, l + 2 * k, t + 2 * k), 180f, 90f, false)
    lineTo(c.x, t)
}

/** One thing beside the camera, centred at [c] in a spot [w] wide. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSlot(
    slot: IslandLook.Slot, c: Offset, w: Float, leftSide: Boolean, alpha: Float, d: SlotData,
) {
    val side = d.side
    val dp = d.density
    fun text(t: String, size: Float, weight: FontWeight = FontWeight.SemiBold) = d.measurer.measure(
        t, TextStyle(fontFamily = glintFontFamily, fontSize = (size / dp / d.fontScale).sp, fontWeight = weight, color = Color.White),
    )
    when (slot) {
        IslandLook.Slot.Cover -> {
            // Everything stays inside a circle of [fit] around the spot's centre, which is the
            // centre of the pill's round end: an even margin all round, like Apple's concentric
            // placement, so nothing pokes into the pill's curve.
            val fit = side / 2f
            val p = d.track.progress(d.progressAt)
            val ringW = 1.6f * dp
            // With the song's progress round it, the picture steps in to make room for the line.
            val artFit = if (p != null) fit - ringW - 1.8f * dp else fit
            // The beat: a soft glow in the cover's colour that swells with the bass.
            d.bars?.let { b ->
                val kick = b[0].coerceIn(0f, 1f)
                if (kick > 0.05f) drawCircle(
                    androidx.compose.ui.graphics.Brush.radialGradient(
                        listOf(d.accent.copy(alpha = 0.32f * kick * alpha), d.accent.copy(alpha = 0f)), c, fit * 1.2f
                    ),
                    fit * 1.2f, c,
                )
            }
            // The cover (or a note when there's no picture), greyed and dimmed while paused.
            val h = if (d.squareArt) me.kavishdevar.librepods.services.IslandSpec.roundedSquareHalf(artFit, SQUARE_ART_CORNER) else artFit
            val shape = Path().apply {
                if (d.squareArt) addRoundRect(androidx.compose.ui.geometry.RoundRect(c.x - h, c.y - h, c.x + h, c.y + h, CornerRadius(h * SQUARE_ART_CORNER)))
                else addOval(androidx.compose.ui.geometry.Rect(c, h))
            }
            clipPath(shape) {
                val art = d.track.art ?: d.coverIcon
                if (art != null) {
                    val (so, ss) = centreSquare(art)
                    drawImage(
                        art,
                        srcOffset = so, srcSize = ss,
                        dstOffset = IntOffset((c.x - h).roundToInt(), (c.y - h).roundToInt()),
                        dstSize = IntSize((2f * h).roundToInt(), (2f * h).roundToInt()),
                        alpha = (alpha * (0.55f + 0.45f * d.level)).coerceIn(0f, 1f),
                        colorFilter = if (d.level < 0.5f) d.paused else null,
                    )
                } else {
                    drawCircle(Color(0xFF2C2C2E), h * 1.5f, c, alpha = alpha)
                    drawNote(c, h * 1.1f, Color.White.copy(alpha = alpha * 0.9f))
                }
            }
            // How far through the song: a thin line round the cover (when the app says), on the
            // same centre as the picture, so the two curves stay parallel.
            p?.let { pr ->
                val stroke = androidx.compose.ui.graphics.drawscope.Stroke(ringW, cap = StrokeCap.Round)
                val lineFit = fit - ringW / 2f
                if (d.squareArt) {
                    val lh = me.kavishdevar.librepods.services.IslandSpec.roundedSquareHalf(lineFit, SQUARE_ART_CORNER)
                    val outline = squareRing(c, lh, lh * SQUARE_ART_CORNER)
                    drawPath(outline, Color.White.copy(alpha = 0.16f * alpha), style = stroke)
                    if (pr > 0f) {
                        val measure = androidx.compose.ui.graphics.PathMeasure().apply { setPath(outline, false) }
                        val done = Path()
                        measure.getSegment(0f, measure.length * pr.coerceIn(0f, 1f), done, true)
                        drawPath(done, d.accent.copy(alpha = 0.95f * alpha), style = stroke)
                    }
                    return@let
                }
                val tl = Offset(c.x - lineFit, c.y - lineFit)
                val ring = Size(lineFit * 2f, lineFit * 2f)
                drawArc(Color.White.copy(alpha = 0.16f * alpha), 0f, 360f, false, tl, ring, style = stroke)
                drawArc(d.accent.copy(alpha = 0.95f * alpha), -90f, 360f * pr, false, tl, ring, style = stroke)
            }
        }
        IslandLook.Slot.Bars -> {
            // Sound bars in the cover's colour; flat dots while paused.
            val t = d.clock / 1000f
            val barW = 2.6f * dp * (side / (28f * dp)).coerceIn(0.8f, 1.2f)
            val gap = barW * 0.92f
            val maxH = side * 0.62f
            val startX = c.x - (4 * barW + 3 * gap) / 2f + barW / 2f
            // Bass, low mids, high mids, treble, laid out so the middle bars lead like a real meter.
            val order = intArrayOf(1, 0, 2, 3)
            for (i in 0 until 4) {
                val wiggle = 0.35f + 0.65f * abs(sin(t * (2.3f + i * 0.73f) + i * 1.7f))
                val rest = if (i % 2 == 0) 0.62f else 0.42f // Reduce motion: steady bars
                val live = d.bars?.let { 0.22f + 0.78f * it[order[i]] }
                val hgt = lerp(barW, maxH * (live ?: if (d.moving) wiggle else rest), d.level)
                val x = startX + i * (barW + gap)
                drawLine(d.accent.copy(alpha = alpha), Offset(x, c.y - hgt / 2f), Offset(x, c.y + hgt / 2f), barW, StrokeCap.Round)
            }
        }
        IslandLook.Slot.Battery -> batteryRing(d.pods.budsLevel, d.pods.budsCharging, d.lowPulse, c, side, alpha, d)
        IslandLook.Slot.Phone -> batteryRing(d.phone.level.takeIf { d.phone.known }, d.phone.charging, d.phoneLow, c, side, alpha, d)
        IslandLook.Slot.Clock -> {
            val t = text(d.clockText, side * 0.36f)
            drawText(t, alpha = alpha, topLeft = Offset(c.x - t.size.width / 2f, c.y - t.size.height / 2f))
        }
        IslandLook.Slot.App -> {
            // The app making the sound: its icon in a circle (a symbol for the kind of sound when
            // pro can't tell which app), with a soft ring that breathes while it's still playing.
            // The breathing ring sits on the edge of the spot, the icon steps in to make room.
            val ringR = side / 2f - 0.8f * dp
            val r = if (d.appActive) ringR - 2.6f * dp else side / 2f
            val circle = Path().apply { addOval(androidx.compose.ui.geometry.Rect(c, r)) }
            val icon = d.appIcon
            // A soft glow in the app's colour while it's playing, breathing gently (the same
            // way the cover glows with the bass).
            if (d.appActive) {
                val glow = if (d.moving) 0.5f + 0.5f * sin(d.clock / 1000f * 4.2f) else 0.6f
                drawCircle(
                    androidx.compose.ui.graphics.Brush.radialGradient(
                        listOf(d.appAccent.copy(alpha = 0.30f * glow * alpha), d.appAccent.copy(alpha = 0f)), c, r * 1.3f
                    ),
                    // Stays inside the island (it used to spill past its edge onto the screen).
                    r * 1.3f, c,
                )
            }
            clipPath(circle) {
                if (icon != null) {
                    val (so, ss) = centreSquare(icon)
                    drawImage(
                        icon, srcOffset = so, srcSize = ss,
                        dstOffset = IntOffset((c.x - r).roundToInt(), (c.y - r).roundToInt()), dstSize = IntSize((2f * r).roundToInt(), (2f * r).roundToInt()),
                        alpha = alpha,
                    )
                } else {
                    drawCircle(Color(0xFF2C2C2E), r, c, alpha = alpha)
                    drawKindGlyph(d.appKind, c, side * 0.54f, Color.White.copy(alpha = alpha * 0.92f))
                }
            }
            drawCircle(Color.White.copy(alpha = 0.14f * alpha), r, c, style = androidx.compose.ui.graphics.drawscope.Stroke(0.8f * dp))
            if (d.appActive) {
                val breathe = if (d.moving) 0.5f + 0.5f * sin(d.clock / 1000f * 4.2f) else 0.7f
                drawCircle(d.appAccent.copy(alpha = alpha * (0.3f + 0.55f * breathe)), ringR, c, style = androidx.compose.ui.graphics.drawscope.Stroke(1.6f * dp))
            }
        }
        IslandLook.Slot.Buds -> if (d.pods.headphones) {
            // Headphones have one battery: one ring with a small headphones mark inside.
            val rr = side * 0.36f
            val tl = Offset(c.x - rr, c.y - rr)
            val st = androidx.compose.ui.graphics.drawscope.Stroke(1.9f * dp, cap = StrokeCap.Round)
            val lvl = d.pods.budsLevel
            drawArc(Color.White.copy(alpha = 0.18f * alpha), 0f, 360f, false, tl, Size(rr * 2, rr * 2), style = st)
            val col = when {
                lvl != null && lvl <= 10 -> Color(0xFFFF453A)
                lvl != null && lvl <= 20 -> Color(0xFFFFB340)
                else -> Color.White
            }
            if (lvl != null) drawArc(col.copy(alpha = alpha), -90f, 360f * lvl / 100f, false, tl, Size(rr * 2, rr * 2), style = st)
            drawHeadphones(c, rr * 0.62f, Color.White.copy(alpha = alpha * (if (lvl == null) 0.45f else 1f)))
        } else {
            // Left, right and case as three small rings. The outer one sits on the centre of the
            // pill's round end (concentric, like everything at the ends); the others step in.
            val rr = side * 0.29f
            val step = side * 0.75f
            val outer = w / 2f - side / 2f
            val parts = listOf(
                Triple("L", d.pods.left, d.pods.leftCharging),
                Triple("R", d.pods.right, d.pods.rightCharging),
                Triple("", d.pods.case, d.pods.caseCharging),
            )
            val st = androidx.compose.ui.graphics.drawscope.Stroke(1.8f * dp, cap = StrokeCap.Round)
            parts.forEachIndexed { i, (mark, lvl, charging) ->
                val pc = Offset(if (leftSide) c.x - outer + i * step else c.x + outer - (2 - i) * step, c.y)
                val tl = Offset(pc.x - rr, pc.y - rr)
                drawArc(Color.White.copy(alpha = 0.18f * alpha), 0f, 360f, false, tl, Size(rr * 2, rr * 2), style = st)
                val col = when {
                    charging -> Color(0xFF30D158)
                    lvl != null && lvl <= 10 -> Color(0xFFFF453A)
                    lvl != null && lvl <= 20 -> Color(0xFFFFB340)
                    else -> Color.White
                }
                if (lvl != null) drawArc(col.copy(alpha = alpha), -90f, 360f * lvl / 100f, false, tl, Size(rr * 2, rr * 2), style = st)
                if (mark.isNotEmpty()) {
                    val t = text(mark, side * 0.3f)
                    drawText(t, alpha = alpha * (if (lvl == null) 0.45f else 1f), topLeft = Offset(pc.x - t.size.width / 2f, pc.y - t.size.height / 2f))
                } else {
                    // The case: a small rounded box.
                    val bw = rr * 0.95f
                    val bh = rr * 0.72f
                    drawRoundRect(Color.White.copy(alpha = alpha * (if (lvl == null) 0.45f else 1f)), Offset(pc.x - bw / 2f, pc.y - bh / 2f), Size(bw, bh), CornerRadius(bh * 0.35f))
                }
            }
        }
        IslandLook.Slot.Mode -> if (d.pods.listeningMode in 1..4) {
            drawListeningMode(d.pods.listeningMode, Color.White.copy(alpha = alpha), c, side * 0.35f)
        } else if (d.pods.headphones) {
            drawHeadphones(c, side * 0.3f, Color.White.copy(alpha = alpha))
        }
        IslandLook.Slot.Heart -> {
            val bpm = d.heartBpm
            if (bpm == null) {
                // No real reading: the listening mode instead (an empty heart would look like it's loading).
                if (d.pods.listeningMode in 1..4) drawListeningMode(d.pods.listeningMode, Color.White.copy(alpha = alpha), c, side * 0.35f)
                // Headphones have neither: their own mark, so the side isn't blank.
                else if (d.pods.headphones) drawHeadphones(c, side * 0.3f, Color.White.copy(alpha = alpha))
            } else {
                val hs = side * 0.36f * d.beat
                val hp = heartPath(Size(hs, hs))
                translate(c.x - hs / 2f, c.y - side * 0.30f - hs / 2f + side * 0.08f) { drawPath(hp, Color.White.copy(alpha = alpha)) }
                val t = text("$bpm", side * 0.32f)
                drawText(t, alpha = alpha, topLeft = Offset(c.x - t.size.width / 2f, c.y + side * 0.02f))
            }
        }
        IslandLook.Slot.Title -> {
            val words = d.shortTitle
            if (words == null) {
                drawNote(Offset(if (leftSide) c.x - w / 2f + side / 2f else c.x + w / 2f - side / 2f, c.y), side * 0.5f, Color.White.copy(alpha = alpha))
            } else {
                // Aligned to the outer edge, kept clear of the pill's curve (Apple: never all the way
                // to the edge); anything longer ends in an ellipsis (never a hard cut).
                val pad = side * 0.24f
                val t = d.measurer.measure(
                    words,
                    TextStyle(fontFamily = glintFontFamily, fontSize = (side * 0.42f / dp / d.fontScale).sp, fontWeight = FontWeight.Medium, color = Color.White),
                    overflow = TextOverflow.Ellipsis, maxLines = 1,
                    constraints = androidx.compose.ui.unit.Constraints(maxWidth = (w - pad).toInt().coerceAtLeast(1)),
                )
                val x0 = if (leftSide) c.x - w / 2f + pad else c.x + w / 2f - pad - t.size.width
                drawText(t, alpha = alpha, topLeft = Offset(x0, c.y - t.size.height / 2f))
            }
        }
        IslandLook.Slot.Talk -> {
            // Three soft dots that ripple like speech.
            val t = d.clock / 1000f
            val dotR = 1.9f * dp
            for (i in 0 until 3) {
                val wave = if (d.moving) 0.5f + 0.5f * sin(t * 6f - i * 0.9f) else 0.6f
                drawCircle(Color.White.copy(alpha = alpha * (0.45f + 0.55f * wave)), dotR * (0.85f + 0.3f * wave), Offset(c.x + (i - 1) * 5.2f * dp, c.y))
            }
        }
        IslandLook.Slot.Screen -> {
            // The app you're in, as a rounded app tile. Changing apps: the old one sinks and fades
            // as the new one springs up past full size and settles.
            val k = d.swap
            d.prevIcon?.let { old -> appTile(old, c, side * (1f - 0.35f * k), alpha * (1f - k).coerceIn(0f, 1f)) }
            val icon = d.screenIcon
            val grow = 0.55f + 0.45f * k
            if (icon != null) appTile(icon, c, side * 0.94f * grow, alpha * k.coerceIn(0f, 1f))
            else drawHomeGrid(c, side * 0.78f * grow, d.wallpaper, 1f, alpha * k.coerceIn(0f, 1f))
        }
        IslandLook.Slot.Home -> drawHomeGrid(c, side * 0.78f, d.wallpaper, d.swap, alpha)
        IslandLook.Slot.Date -> {
            // A tiny calendar page: the weekday in red over the day's number.
            val wd = text(d.today.weekday, side * 0.24f, FontWeight.Bold)
            val num = text(d.today.day.toString(), side * 0.46f, FontWeight.SemiBold)
            val total = wd.size.height * 0.82f + num.size.height * 0.86f
            val y0 = c.y - total / 2f
            drawText(wd, color = Color(0xFFFF453A), alpha = alpha, topLeft = Offset(c.x - wd.size.width / 2f, y0 - wd.size.height * 0.08f))
            drawText(num, alpha = alpha, topLeft = Offset(c.x - num.size.width / 2f, y0 + wd.size.height * 0.72f))
        }
        IslandLook.Slot.Lock -> drawPadlock(c, side * 0.62f, d.lockOpen, Color.White.copy(alpha = alpha * (0.75f + 0.25f * d.swap)))
        IslandLook.Slot.Glance -> when (val g = d.glance) {
            is GlanceRules.Item.Timer -> if (g.state.ringing) {
                // Done: an orange bell that shakes until it's stopped (a tap on the pill stops it).
                val wob = if (d.moving) sin(d.clock / 1000f * 22f) * 14f else 0f
                drawCircle(Color(0xFFFF9F0A).copy(alpha = 0.22f * alpha), side / 2f, c)
                rotate(wob, c) { drawKindGlyph(SoundRules.Kind.Alert, c, side * 0.52f, Color(0xFFFF9F0A).copy(alpha = alpha)) }
            } else {
                // Counting down: an orange ring that empties as time runs out, the minutes (or the
                // last seconds) inside, like the iPhone's timer.
                val at = if (d.timerAt == 0L) SystemClock.elapsedRealtime() else d.timerAt
                val left = TimerRules.left(g.state, at)
                val rr = side / 2f - 1.5f * dp
                val tl = Offset(c.x - rr, c.y - rr)
                val st = androidx.compose.ui.graphics.drawscope.Stroke(2.2f * dp, cap = StrokeCap.Round)
                val orange = Color(0xFFFF9F0A)
                drawArc(orange.copy(alpha = 0.22f * alpha), 0f, 360f, false, tl, Size(rr * 2, rr * 2), style = st)
                drawArc(orange.copy(alpha = alpha * (if (g.state.paused) 0.55f else 1f)), -90f, 360f * (1f - TimerRules.progress(g.state, at)), false, tl, Size(rr * 2, rr * 2), style = st)
                if (g.state.paused) {
                    val bw = side * 0.09f; val bh = side * 0.3f
                    drawRoundRect(orange.copy(alpha = alpha), Offset(c.x - bw * 1.6f, c.y - bh / 2f), Size(bw, bh), CornerRadius(bw / 2f))
                    drawRoundRect(orange.copy(alpha = alpha), Offset(c.x + bw * 0.6f, c.y - bh / 2f), Size(bw, bh), CornerRadius(bw / 2f))
                } else {
                    val t = text(TimerRules.short(left), side * (if (left >= 600_000L) 0.3f else 0.36f))
                    drawText(t, color = orange, alpha = alpha, topLeft = Offset(c.x - t.size.width / 2f, c.y - t.size.height / 2f))
                }
            }
            is GlanceRules.Item.Charging -> {
                batteryRing(g.level, true, false, c, side, alpha * 0.35f, d, number = false)
                drawChargeBolt(c, side * 0.5f, Color(0xFF30D158).copy(alpha = alpha))
            }
            is GlanceRules.Item.Buds -> {
                // The headphones' (or AirPods') battery: a ring with their mark inside, so it can't
                // be mistaken for the phone's.
                batteryRing(g.level, false, d.lowPulse, c, side, alpha, d, number = false)
                if (g.headphones) drawHeadphones(c, side * 0.2f, Color.White.copy(alpha = alpha))
                else drawBudPair(c, side * 0.5f, Color.White.copy(alpha = alpha))
            }
            is GlanceRules.Item.LowPhone -> batteryRing(g.level, false, false, c, side, alpha, d)
            else -> {}
        }
        IslandLook.Slot.Same, IslandLook.Slot.Nothing -> {}
    }
}

/** The cover's corner, as a share of the square's half-width (about a fifth of its width, like Apple's small album art). */
private const val SQUARE_ART_CORNER = 0.42f

/**
 * The middle square of a picture, so a wide or tall cover (a video's frame, a podcast banner) is
 * cropped like a photo instead of squashed.
 */
internal fun centreSquare(img: ImageBitmap): Pair<IntOffset, IntSize> {
    val side = minOf(img.width, img.height).coerceAtLeast(1)
    return IntOffset((img.width - side) / 2, (img.height - side) / 2) to IntSize(side, side)
}

/** An app's icon as a rounded tile (the shape app icons have on a home screen), [size] across. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.appTile(icon: ImageBitmap, c: Offset, size: Float, alpha: Float) {
    if (alpha <= 0.001f || size <= 1f) return
    val r = size * 0.27f
    val tile = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(c.x - size / 2f, c.y - size / 2f, c.x + size / 2f, c.y + size / 2f, CornerRadius(r, r))) }
    val (so, ss) = centreSquare(icon)
    clipPath(tile) {
        drawImage(
            icon, srcOffset = so, srcSize = ss,
            dstOffset = IntOffset((c.x - size / 2f).roundToInt(), (c.y - size / 2f).roundToInt()),
            dstSize = IntSize(size.roundToInt(), size.roundToInt()), alpha = alpha,
        )
    }
}

/**
 * The home screen: four small rounded tiles in the wallpaper's own colours (white and greys when
 * pro can't read them). [arrive] 0..1 brings them in one after another, like apps settling onto
 * a home screen.
 */
internal fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHomeGrid(c: Offset, size: Float, wallpaper: List<Color>, arrive: Float, alpha: Float) {
    if (alpha <= 0.001f) return
    val palette = homeColors(wallpaper)
    val gap = size * 0.12f
    val tile = (size - gap) / 2f
    for (i in 0 until 4) {
        val k = ((arrive * 1.6f) - i * 0.2f).coerceIn(0f, 1f)
        if (k <= 0f) continue
        val col = i % 2; val row = i / 2
        val cx = c.x - size / 2f + tile / 2f + col * (tile + gap)
        val cy = c.y - size / 2f + tile / 2f + row * (tile + gap)
        val t = tile * (0.6f + 0.4f * k)
        drawRoundRect(
            palette[i].copy(alpha = alpha * k), Offset(cx - t / 2f, cy - t / 2f), Size(t, t), CornerRadius(t * 0.32f),
        )
    }
}

/** Four tile colours from the wallpaper, made bright enough to read on black. */
internal fun homeColors(wallpaper: List<Color>): List<Color> {
    if (wallpaper.isEmpty()) return listOf(Color.White, Color(0xFF98989F), Color(0xFF636366), Color(0xFFD1D1D6))
    fun lift(c: Color): Color {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(android.graphics.Color.argb(255, (c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt()), hsv)
        hsv[2] = hsv[2].coerceAtLeast(0.72f)
        hsv[1] = hsv[1].coerceAtMost(0.75f)
        return Color(android.graphics.Color.HSVToColor(hsv))
    }
    val a = lift(wallpaper[0])
    val b = lift(wallpaper.getOrElse(1) { wallpaper[0] })
    val t = lift(wallpaper.getOrElse(2) { wallpaper.getOrElse(1) { wallpaper[0] } })
    return listOf(a, b, t, androidx.compose.ui.graphics.lerp(a, Color.White, 0.45f))
}

/** A padlock [size] tall; [open] 0..1 lifts the shackle up and swings it aside. */
internal fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPadlock(c: Offset, size: Float, open: Float, color: Color) {
    val bw = size * 0.78f
    val bh = size * 0.56f
    val bodyTop = c.y - size / 2f + size * 0.44f
    drawRoundRect(color, Offset(c.x - bw / 2f, bodyTop), Size(bw, bh), CornerRadius(bh * 0.22f))
    // The keyhole.
    drawCircle(Color.Black.copy(alpha = color.alpha), size * 0.07f, Offset(c.x, bodyTop + bh * 0.45f))
    val sw = size * 0.11f
    val shR = bw * 0.32f
    val lift = size * 0.16f * open
    val pivot = Offset(c.x + shR, bodyTop)
    rotate(-28f * open, pivot) {
        val top = bodyTop - shR * 2f - lift + sw / 2f
        drawArc(color, 180f, 180f, false, Offset(c.x - shR, top), Size(shR * 2f, shR * 2f), style = androidx.compose.ui.graphics.drawscope.Stroke(sw, cap = StrokeCap.Round))
        // The legs down into the body (the left one comes free as it opens).
        drawLine(color, Offset(c.x + shR, top + shR), Offset(c.x + shR, bodyTop + sw * 0.4f), sw)
        drawLine(color, Offset(c.x - shR, top + shR), Offset(c.x - shR, bodyTop + sw * 0.4f - lift * 1.4f), sw)
    }
}

/** A charging bolt, [size] tall. */
internal fun androidx.compose.ui.graphics.drawscope.DrawScope.drawChargeBolt(c: Offset, size: Float, color: Color) {
    val u = size / 2f
    drawPath(Path().apply {
        moveTo(c.x + u * 0.18f, c.y - u)
        lineTo(c.x - u * 0.52f, c.y + u * 0.12f)
        lineTo(c.x - u * 0.02f, c.y + u * 0.12f)
        lineTo(c.x - u * 0.18f, c.y + u)
        lineTo(c.x + u * 0.52f, c.y - u * 0.12f)
        lineTo(c.x + u * 0.02f, c.y - u * 0.12f)
        close()
    }, color)
}

/** A pair of AirPods facing each other (round heads, stems down), [size] across: the AirPods' mark. */
internal fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBudPair(c: Offset, size: Float, color: Color) {
    val head = size * 0.17f
    for (s in listOf(-1f, 1f)) {
        val hc = Offset(c.x + s * size * 0.24f, c.y - size * 0.2f)
        drawCircle(color, head, hc)
        // The stem hangs from the outer side of the head.
        val top = Offset(hc.x + s * head * 0.55f, hc.y + head * 0.25f)
        drawLine(color, top, Offset(top.x + s * size * 0.02f, c.y + size * 0.42f), size * 0.13f, StrokeCap.Round)
    }
}

/** Today, as the calendar page shows it. */
internal data class Day(val day: Int, val weekday: String)

internal fun dayNow(): Day = java.util.Calendar.getInstance().let { cal ->
    Day(
        cal.get(java.util.Calendar.DAY_OF_MONTH),
        (cal.getDisplayName(java.util.Calendar.DAY_OF_WEEK, java.util.Calendar.SHORT, java.util.Locale.getDefault()) ?: "").uppercase(java.util.Locale.getDefault()).take(3),
    )
}

/**
 * A short moment on the Dynamic Island: it widens with a line of words under the camera for a
 * few seconds (the phone plugged in: "Charging · 76%" in green).
 */
internal data class MiniMoment(val id: Long, val text: String, val color: Color) {
    companion object {
        const val SHOW_MS = 2_600L
        fun charging(level: Int, now: Long) = MiniMoment(now, "Charging  ·  $level%", Color(0xFF30D158))
        /** The words for each phone battery moment, in the battery ring's own colour. */
        fun of(kind: PhoneStatus.Moment, level: Int, now: Long) = when (kind) {
            PhoneStatus.Moment.Charging -> charging(level, now)
            PhoneStatus.Moment.Full -> MiniMoment(now, "Fully charged", Color(0xFF30D158))
            PhoneStatus.Moment.Low -> MiniMoment(now, "Battery low  ·  $level%", Color(0xFFFFB340))
            PhoneStatus.Moment.VeryLow -> MiniMoment(now, "Battery very low  ·  $level%", Color(0xFFFF453A))
        }
    }
}

/**
 * A battery as a ring with the number: green while charging, amber at 20% and below, red at 10%
 * (where it breathes gently so it catches your eye). For the AirPods and for the phone itself.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.batteryRing(
    lvl: Int?, charging: Boolean, pulse: Boolean, c: Offset, side: Float, alpha: Float, d: SlotData, number: Boolean = true,
) {
    val dp = d.density
    val ringC = when {
        charging -> Color(0xFF30D158)
        lvl != null && lvl <= 10 -> Color(0xFFFF453A)
        lvl != null && lvl <= 20 -> Color(0xFFFFB340)
        else -> Color.White
    }
    val rr = side / 2f - 1.5f * dp
    val tl = Offset(c.x - rr, c.y - rr)
    val st = androidx.compose.ui.graphics.drawscope.Stroke(2.2f * dp, cap = StrokeCap.Round)
    drawArc(Color.White.copy(alpha = 0.18f * alpha), 0f, 360f, false, tl, Size(rr * 2, rr * 2), style = st)
    val breathe = if (pulse) 0.55f + 0.45f * (0.5f + 0.5f * sin(d.clock / 1000f * 3.2f)) else 1f
    if (lvl != null) drawArc(ringC.copy(alpha = alpha * breathe), -90f, 360f * lvl / 100f, false, tl, Size(rr * 2, rr * 2), style = st)
    if (!number) return
    val num = d.measurer.measure(
        lvl?.toString() ?: "–",
        TextStyle(fontFamily = glintFontFamily, fontSize = (side * 0.34f / dp / d.fontScale).sp, fontWeight = FontWeight.SemiBold, color = Color.White),
    )
    drawText(num, alpha = alpha, topLeft = Offset(c.x - num.size.width / 2f, c.y - num.size.height / 2f))
}

/**
 * A small symbol for a kind of sound, drawn at [c] about [size] across: used when pro can't tell
 * which app made it (or icons are switched off). A speaker, a bell, a handset, a clock, a microphone.
 */
internal fun androidx.compose.ui.graphics.drawscope.DrawScope.drawKindGlyph(kind: SoundRules.Kind, c: Offset, size: Float, color: Color) {
    val u = size / 2f
    val sw = u * 0.2f
    val line = androidx.compose.ui.graphics.drawscope.Stroke(sw, cap = StrokeCap.Round)
    when (kind) {
        SoundRules.Kind.Alert -> {
            // A bell: a dome flaring at the rim, and its clapper.
            drawPath(Path().apply {
                moveTo(c.x - u * 0.62f, c.y + u * 0.38f)
                cubicTo(c.x - u * 0.62f, c.y - u * 0.95f, c.x + u * 0.62f, c.y - u * 0.95f, c.x + u * 0.62f, c.y + u * 0.38f)
                lineTo(c.x + u * 0.86f, c.y + u * 0.58f); lineTo(c.x - u * 0.86f, c.y + u * 0.58f); close()
            }, color)
            drawCircle(color, u * 0.17f, Offset(c.x, c.y + u * 0.86f))
        }
        SoundRules.Kind.Call -> {
            // A handset: a curved bar with a heavier end on each side.
            drawArc(color, 205f, 130f, false, Offset(c.x - u * 0.8f, c.y - u * 0.45f), Size(u * 1.6f, u * 1.6f), style = androidx.compose.ui.graphics.drawscope.Stroke(sw * 1.5f, cap = StrokeCap.Round))
            drawCircle(color, u * 0.2f, Offset(c.x - u * 0.7f, c.y + u * 0.35f))
            drawCircle(color, u * 0.2f, Offset(c.x + u * 0.7f, c.y + u * 0.35f))
        }
        SoundRules.Kind.Alarm -> {
            // An alarm clock: a face with hands, and its two bells.
            drawCircle(color, u * 0.7f, Offset(c.x, c.y + u * 0.1f), style = line)
            drawLine(color, Offset(c.x, c.y + u * 0.1f), Offset(c.x, c.y - u * 0.3f), sw, StrokeCap.Round)
            drawLine(color, Offset(c.x, c.y + u * 0.1f), Offset(c.x + u * 0.28f, c.y + u * 0.26f), sw, StrokeCap.Round)
            drawCircle(color, u * 0.2f, Offset(c.x - u * 0.72f, c.y - u * 0.62f))
            drawCircle(color, u * 0.2f, Offset(c.x + u * 0.72f, c.y - u * 0.62f))
        }
        SoundRules.Kind.Voice -> {
            // A microphone: a capsule on a small stand.
            drawRoundRect(color, Offset(c.x - u * 0.28f, c.y - u * 0.85f), Size(u * 0.56f, u * 1.05f), CornerRadius(u * 0.28f))
            drawArc(color, 0f, 180f, false, Offset(c.x - u * 0.55f, c.y - u * 0.5f), Size(u * 1.1f, u * 1.1f), style = line)
            drawLine(color, Offset(c.x, c.y + u * 0.6f), Offset(c.x, c.y + u * 0.88f), sw, StrokeCap.Round)
        }
        else -> {
            // A speaker with two sound waves.
            drawPath(Path().apply {
                moveTo(c.x - u * 0.85f, c.y - u * 0.28f); lineTo(c.x - u * 0.45f, c.y - u * 0.28f)
                lineTo(c.x - u * 0.02f, c.y - u * 0.7f); lineTo(c.x - u * 0.02f, c.y + u * 0.7f)
                lineTo(c.x - u * 0.45f, c.y + u * 0.28f); lineTo(c.x - u * 0.85f, c.y + u * 0.28f); close()
            }, color)
            drawArc(color, -45f, 90f, false, Offset(c.x - u * 0.34f, c.y - u * 0.42f), Size(u * 0.84f, u * 0.84f), style = line)
            drawArc(color, -45f, 90f, false, Offset(c.x - u * 0.34f - u * 0.26f, c.y - u * 0.42f - u * 0.26f), Size(u * 1.36f, u * 1.36f), style = line)
        }
    }
}

/** The time as the Clock slot shows it. */
private fun clockNow(is24: Boolean): String = java.util.Calendar.getInstance().let {
    IslandLook.clockText(it.get(java.util.Calendar.HOUR_OF_DAY), it.get(java.util.Calendar.MINUTE), is24)
}

/**
 * The music with the music app's icon filled in when it has no cover picture of its own: the
 * app came from the sound listener (the app on screen) rather than the media session.
 */
private fun withSource(t: NowPlaying.Track, h: SoundSource.Heard?): NowPlaying.Track =
    if (h == null || !h.musicLike || !t.playing || (t.icon != null && t.pkg != null)) t
    else t.copy(app = t.app ?: h.app, pkg = t.pkg ?: h.pkg, icon = t.icon ?: h.icon)

/** A pretend app icon (a green speech bubble) for Settings' previews and screenshots. */
internal fun sampleIcon(): ImageBitmap {
    val b = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(b)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    paint.color = 0xFF30D158.toInt()
    canvas.drawRoundRect(0f, 0f, 96f, 96f, 28f, 28f, paint)
    paint.color = android.graphics.Color.WHITE
    canvas.drawRoundRect(22f, 24f, 74f, 62f, 16f, 16f, paint)
    canvas.drawPath(android.graphics.Path().apply { moveTo(34f, 58f); lineTo(30f, 74f); lineTo(48f, 60f); close() }, paint)
    return b.asImageBitmap()
}

/** What a gesture on the Dynamic Island just did, as shown for a moment in its right-hand spot. */
internal data class MiniAck(val action: IslandGestures.Action, val playingAfter: Boolean = false, val mode: Int = 0) {
    companion object {
        /** The sign for [a] (null for actions that show themselves, like opening the island). */
        fun of(a: IslandGestures.Action, playingBefore: Boolean, nextMode: Int): MiniAck? = when (a) {
            IslandGestures.Action.PlayPause -> MiniAck(a, playingAfter = !playingBefore)
            IslandGestures.Action.Next, IslandGestures.Action.Previous -> MiniAck(a)
            IslandGestures.Action.ListeningMode -> MiniAck(a, mode = nextMode)
            else -> null
        }
    }
}

/** Play, pause, next or previous, drawn small at [c] in a box [size] wide. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawAck(ack: MiniAck, c: Offset, size: Float, color: Color) {
    val h = size * 0.5f
    fun tri(x: Float, w: Float, pointRight: Boolean) = Path().apply {
        if (pointRight) { moveTo(x, c.y - h / 2f); lineTo(x + w, c.y); lineTo(x, c.y + h / 2f) }
        else { moveTo(x + w, c.y - h / 2f); lineTo(x, c.y); lineTo(x + w, c.y + h / 2f) }
        close()
    }
    when (ack.action) {
        IslandGestures.Action.PlayPause -> if (ack.playingAfter) {
            drawPath(tri(c.x - h * 0.32f, h * 0.76f, true), color)
        } else {
            val bw = h * 0.26f
            drawRoundRect(color, Offset(c.x - h * 0.34f, c.y - h / 2f), Size(bw, h), CornerRadius(bw / 3f))
            drawRoundRect(color, Offset(c.x + h * 0.08f, c.y - h / 2f), Size(bw, h), CornerRadius(bw / 3f))
        }
        IslandGestures.Action.Next, IslandGestures.Action.Previous -> {
            val next = ack.action == IslandGestures.Action.Next
            val w = h * 0.5f
            val x0 = c.x - w
            drawPath(tri(x0, w, next), color)
            drawPath(tri(x0 + w * 0.9f, w, next), color)
            val barX = if (next) x0 + w * 1.9f + h * 0.05f else x0 - h * 0.14f
            drawRoundRect(color, Offset(barX, c.y - h / 2f), Size(h * 0.12f, h), CornerRadius(h * 0.06f))
        }
        IslandGestures.Action.ListeningMode -> drawListeningMode(ack.mode, color, c, size * 0.42f)
        else -> {}
    }
}

/** How long the phone's battery moment (plugged in, full, low) stays up. */
private const val PHONE_MOMENT_MS = 4_000L

/** How long a gesture's sign stays in the pill. */
private const val ACK_SHOW_MS = 650L

/** A small music note: a stem with a flag and a round head. */
internal fun androidx.compose.ui.graphics.drawscope.DrawScope.drawNote(c: Offset, size: Float, color: Color) {
    val headR = size * 0.2f
    val head = Offset(c.x - size * 0.12f, c.y + size * 0.28f)
    drawCircle(color, headR, head)
    val stemX = head.x + headR * 0.9f
    drawLine(color, Offset(stemX, head.y), Offset(stemX, c.y - size * 0.42f), size * 0.1f, StrokeCap.Round)
    drawLine(color, Offset(stemX, c.y - size * 0.42f), Offset(stemX + size * 0.3f, c.y - size * 0.25f), size * 0.1f, StrokeCap.Round)
}

/**
 * The cover's most vivid colour, kept bright enough to read on black (white for grey
 * covers). Looks at a 12 x 12 shrunk copy, so it's quick.
 */
internal fun accentOf(art: ImageBitmap): Color = try {
    val small: Bitmap = art.asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false).scale(12, 12)
    accentOfPixels(IntArray(144).also { small.getPixels(it, 0, 12, 0, 0, 12, 12) })
} catch (_: Exception) { Color.White }

/** The pure part of [accentOf], for tests: ARGB pixels in, a readable accent out. */
internal fun accentOfPixels(pixels: IntArray): Color {
    val hsv = FloatArray(3)
    var best = -1f
    var bestHsv: FloatArray? = null
    for (p in pixels) {
        android.graphics.Color.colorToHSV(p, hsv)
        if (hsv[2] < 0.25f) continue
        val score = hsv[1] * hsv[2]
        if (score > best) { best = score; bestHsv = hsv.copyOf() }
    }
    val h = bestHsv ?: return Color.White
    if (h[1] < 0.22f) return Color.White
    h[1] = h[1].coerceAtMost(0.7f)
    h[2] = h[2].coerceAtLeast(0.9f)
    return Color(android.graphics.Color.HSVToColor(h))
}

/** The status bar must be gone this long before it counts as a full-screen app. */
private const val FULL_SCREEN_MS = 1_200L
/** The longest the Dynamic Island's leaving may take before its window is removed regardless. */
private const val LEAVE_SAFETY_MS = 1_500L
/** How long a dropped connection may last before the Dynamic Island leaves. */
private const val AIRPODS_GRACE_MS = 5_000L

/** A boolean setting that updates when it changes elsewhere (Settings > Islands). */
@Composable
internal fun rememberIntPref(prefs: SharedPreferences, key: String, default: Int): androidx.compose.runtime.State<Int> {
    val state = remember { mutableStateOf(prefs.getInt(key, default)) }
    androidx.compose.runtime.DisposableEffect(prefs, key) {
        val l = SharedPreferences.OnSharedPreferenceChangeListener { p, k -> if (k == key) state.value = p.getInt(key, default) }
        prefs.registerOnSharedPreferenceChangeListener(l)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(l) }
    }
    return state
}

@Composable
private fun rememberPref(prefs: SharedPreferences, key: String, default: Boolean): androidx.compose.runtime.State<Boolean> {
    val state = remember { mutableStateOf(prefs.getBoolean(key, default)) }
    androidx.compose.runtime.DisposableEffect(prefs, key) {
        val l = SharedPreferences.OnSharedPreferenceChangeListener { p, k -> if (k == key) state.value = p.getBoolean(key, default) }
        prefs.registerOnSharedPreferenceChangeListener(l)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(l) }
    }
    return state
}
