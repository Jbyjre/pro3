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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.inputmethod.InputMethodManager
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Hears every sound the phone plays (any app, any kind) and works out which app made it, so the
 * Dynamic Island can pop for a WhatsApp ding as well as for a song, with that app's icon. The
 * rules live in [SoundRules]; this is the part that talks to Android.
 *
 * Needs no permission to hear *that* something plays and what kind it is. The app's identity
 * comes from clues that each need an optional switch (see [SoundRules]); without them the island
 * still shows the sound, with a symbol for its type.
 */
object SoundSource {
    /** The latest sound: playing now ([active]) or ended a moment ago (for [endedAt]). */
    @Immutable
    data class Heard(
        val kind: SoundRules.Kind,
        val pkg: String?,
        val app: String?,
        val icon: ImageBitmap?,
        val startedAt: Long,
        val active: Boolean,
        /** When it ended (elapsedRealtime), 0 while it plays. */
        val endedAt: Long = 0L,
        /** Something musical plays too (so the island's music state takes over). */
        val musicLike: Boolean = false,
        val basis: SoundRules.Basis = SoundRules.Basis.None,
    )

    /** An app's name and small icon, as the island and Settings show them. */
    @Immutable
    data class AppInfo(val label: String?, val icon: ImageBitmap?)

    private val main = Handler(Looper.getMainLooper())
    private val _heard = MutableStateFlow<Heard?>(null)
    val heard: StateFlow<Heard?> = _heard.asStateFlow()

    private val _recent = MutableStateFlow<List<SoundRules.Seen>>(emptyList())
    /** The apps (or kinds of sound) heard lately, newest first, for Settings. */
    val recent: StateFlow<List<SoundRules.Seen>> = _recent.asStateFlow()

    private val notes = SoundRules.NoteLog()
    private var appContext: Context? = null
    private var lastUsages: List<Int> = emptyList()
    private var recordedStart = 0L

    /** The app on screen, from the Dynamic Island accessibility service (null: home screen or unknown). */
    @Volatile var foreground: String? = null
        private set

    private const val ICON_PX = 96
    private const val RETRY_MS = 700L
    private val infos = HashMap<String, AppInfo>()
    private val roles = HashMap<String, SoundRules.Role>()
    private var homeApps: Set<String>? = null

    /** Starts listening (safe to call often; the app's background service does it once at start). */
    fun attach(context: Context) {
        if (appContext != null) return
        val app = context.applicationContext
        appContext = app
        val audio = app.getSystemService(AudioManager::class.java) ?: return
        runCatching { audio.registerAudioPlaybackCallback(callback, main) }
        ContextCompat.registerReceiver(
            app, object : BroadcastReceiver() {
                // Screen off: nothing is in front any more, so a later alarm isn't blamed on the last app.
                override fun onReceive(c: Context, i: Intent) { foreground = null }
            },
            IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        // Something may already be playing when pro starts.
        onUsages(audio.activePlaybackConfigurations.orEmpty().map { it.audioAttributes.usage })
    }

    private val callback = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>?) {
            // Android hands apps only the sounds that are playing right now (checked in its source).
            onUsages(configs.orEmpty().map { it.audioAttributes.usage })
        }
    }

    private fun onUsages(usages: List<Int>) {
        val ctx = appContext ?: return
        lastUsages = usages
        val now = SystemClock.elapsedRealtime()
        val cur = _heard.value
        val s = SoundRules.summarize(usages)
        if (s == null) {
            if (cur != null && cur.active) _heard.value = cur.copy(active = false, endedAt = now)
            return
        }
        val keep = cur?.takeIf { it.active && it.kind == s.kind }
        var (pkg, basis) = SoundRules.attribute(s.kind, evidence(ctx, now), skipper(ctx))
        // The same sound keeps its app even if the clues thin out while it plays.
        if (pkg == null && keep?.pkg != null) { pkg = keep.pkg; basis = keep.basis }
        val info = pkg?.let { appInfo(ctx, it) }
        val next = Heard(s.kind, pkg, info?.label, info?.icon, keep?.startedAt ?: now, true, 0L, s.musicLike, basis)
        if (pkg != null) record(next)
        if (pkg != null && pkg in IslandPrefs.soundIgnored(IslandPrefs.prefs(ctx))) {
            // An app Jake switched off in Settings: it doesn't pop the island.
            if (cur != null) _heard.value = null
            return
        }
        if (next != cur) _heard.value = next
        // A notification can arrive a beat after its sound: look once more shortly.
        if (pkg == null && keep == null) {
            main.removeCallbacks(retry)
            main.postDelayed(retry, RETRY_MS)
        }
    }

    private val retry = Runnable {
        val ctx = appContext ?: return@Runnable
        val h = _heard.value ?: return@Runnable
        if (h.pkg != null) return@Runnable
        val (pkg, basis) = SoundRules.attribute(h.kind, evidence(ctx, SystemClock.elapsedRealtime()), skipper(ctx))
        if (pkg == null) {
            // Still no idea: log it by its kind so it shows in the recent list.
            record(h)
            return@Runnable
        }
        val info = appInfo(ctx, pkg)
        val next = h.copy(pkg = pkg, app = info.label, icon = info.icon, basis = basis)
        record(next)
        _heard.value = if (pkg in IslandPrefs.soundIgnored(IslandPrefs.prefs(ctx))) null else next
    }

    /** Adds a sound to the recent list once (a long sound isn't counted again and again). */
    private fun record(h: Heard) {
        if (h.startedAt == recordedStart) return
        recordedStart = h.startedAt
        _recent.value = SoundRules.remember(_recent.value, h.pkg, h.kind, System.currentTimeMillis())
    }

    private fun evidence(ctx: Context, now: Long): SoundRules.Evidence = SoundRules.Evidence(
        session = NowPlaying.playingSessionPackage(),
        notification = notes.recent(now, skip = skipper(ctx)),
        foreground = inFront(),
    )

    /**
     * The app on screen. [ScreenApp] counts only real app screens, so a toast or a pop-up from
     * another app (a WhatsApp toast over a Chrome video) doesn't take the credit for the next
     * sound; when it doesn't know, the plain window clue below is used.
     */
    private fun inFront(): String? = when (val p = ScreenApp.place.value) {
        is ScreenApp.Place.App -> p.pkg
        ScreenApp.Place.Home -> null
        else -> foreground
    }

    /** Packages that never count as "the app that made the sound": pro itself and the phone's own pieces. */
    private fun skipper(ctx: Context): (String) -> Boolean = { pkg -> pkg == ctx.packageName || roleOf(ctx, pkg) == SoundRules.Role.System }

    // ---- Clues from the rest of the app ----

    /**
     * A notification arrived. Only its app and the time are kept (nothing from inside it), and only
     * to explain a sound that follows. Called by [MediaAccessService] (Notification access).
     */
    fun notificationPosted(pkg: String?, flags: Int) {
        val ctx = appContext ?: return
        if (!SoundRules.worthNoting(flags, pkg, ctx.packageName)) return
        notes.add(pkg!!, SystemClock.elapsedRealtime())
    }

    /** A window came to the front (from [IslandAccessService]): remember the app, forget it at the home screen. */
    fun windowChanged(pkg: CharSequence?) {
        val ctx = appContext ?: return
        val p = pkg?.toString()?.takeIf { it.isNotBlank() } ?: return
        foreground = SoundRules.foregroundAfter(foreground, p, roleOf(ctx, p))
    }

    // ---- App names, icons and roles ----

    /** An app's name and icon, cached (the island draws the icon every frame it's up). */
    fun appInfo(context: Context, pkg: String): AppInfo {
        infos[pkg]?.let { return it }
        val label = NowPlaying.appName(context, pkg)
        val icon = runCatching {
            context.packageManager.getApplicationIcon(pkg).toBitmap(ICON_PX, ICON_PX).asImageBitmap()
        }.getOrNull()
        val info = AppInfo(label, icon)
        // A failed lookup isn't kept: the app may become visible a moment later.
        if (label != null || icon != null) infos[pkg] = info
        return info
    }

    internal fun roleOf(ctx: Context, pkg: String): SoundRules.Role = roleOverride?.invoke(pkg) ?: roles.getOrPut(pkg) {
        val pm = ctx.packageManager
        val home = homeApps ?: runCatching {
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0).map { it.activityInfo.packageName }.toSet()
        }.getOrDefault(emptySet()).also { homeApps = it }
        val keyboards = runCatching {
            ctx.getSystemService(InputMethodManager::class.java)?.enabledInputMethodList?.map { it.packageName }.orEmpty()
        }.getOrDefault(emptyList())
        when {
            pkg == ctx.packageName || pkg == "android" || pkg.startsWith("com.android.systemui") || pkg in keyboards -> SoundRules.Role.System
            pkg in home -> SoundRules.Role.Launcher
            runCatching { pm.getLaunchIntentForPackage(pkg) != null }.getOrDefault(false) -> SoundRules.Role.App
            else -> SoundRules.Role.System
        }
    }

    /** Opens the app that made the sound, from a tap on the island. False when it isn't known or can't open. */
    fun openApp(context: Context, pkg: String?): Boolean {
        if (pkg == null) return false
        val intent = context.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        return runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true }.getOrDefault(false)
    }

    /** Sets what's heard, for screenshots and tests only. */
    internal fun preview(h: Heard?) { _heard.value = h }

    /** Sets the recent list and an app's name and icon, for screenshots and tests only. */
    internal fun previewRecent(list: List<SoundRules.Seen>, names: Map<String, AppInfo> = emptyMap()) {
        _recent.value = list
        infos.putAll(names)
    }

    /** Starts from nothing again (tests share one process, so each begins clean). */
    internal fun resetForTest() {
        main.removeCallbacks(retry)
        appContext = null
        _heard.value = null
        _recent.value = emptyList()
        notes.clear()
        lastUsages = emptyList()
        recordedStart = 0L
        foreground = null
        infos.clear()
        roles.clear()
        roleOverride = null
    }

    /** Tests only: decide what counts as an app, without installing real ones. */
    internal var roleOverride: ((String) -> SoundRules.Role)? = null

    /** What the playback listener hears, as a list of Android usage numbers (tests call this directly). */
    internal fun usagesChanged(usages: List<Int>) = onUsages(usages)
}
