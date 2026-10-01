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

import me.kavishdevar.librepods.presentation.glint.rememberHeartBeat
import me.kavishdevar.librepods.presentation.glint.heartPath
import me.kavishdevar.librepods.presentation.glint.listeningModeName
import me.kavishdevar.librepods.presentation.glint.ListeningModeGlyph
import androidx.compose.ui.text.drawText
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.onSizeChanged
import android.app.KeyguardManager
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
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
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
import me.kavishdevar.librepods.presentation.glint.GlintComfort
import me.kavishdevar.librepods.presentation.glint.GlintHaptics
import me.kavishdevar.librepods.presentation.glint.lerp
import me.kavishdevar.librepods.presentation.theme.glintFontFamily
import me.kavishdevar.librepods.services.GlintStatus
import me.kavishdevar.librepods.services.IslandPrefs
import me.kavishdevar.librepods.services.LinkState
import me.kavishdevar.librepods.services.MiniIslandRules
import me.kavishdevar.librepods.services.NowPlaying
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The mini island: a small black pill wrapped around the front camera while something plays,
 * like the iPhone's Dynamic Island in its music state. The song's cover sits on one side of
 * the camera and sound bars (in the cover's own colour) on the other; it widens for a moment
 * to show each new song's name.
 *
 * Touch: tap opens the full island, swipe left/right skips to the next/previous song, hold
 * opens pro. It steps aside while the big island is up, in landscape, in full-screen apps, on
 * the lock screen, and 30 seconds after the music stops.
 */
internal class MiniIslandController(private val context: Context) {
    private val window = OverlayWindow(context, "GlintMiniIsland", anchorTop = true)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val prefs = IslandPrefs.prefs(context)
    private var started = false
    private var wasPlaying = false
    private var stoppedAt = 0L
    private var playedRecently = false
    private var unlocked = true
    private var lingerJob: Job? = null
    private var shownFor: Any? = null

    /** True while the pill should animate away (then the window is removed). */
    private val leaving = mutableStateOf(false)
    /** Sample music for Settings > Island > Try it. */
    private val sample = mutableStateOf<NowPlaying.Track?>(null)
    /** Music or the AirPods themselves. */
    private val content = mutableStateOf(MiniIslandRules.Content.Music)

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            unlocked = when (i.action) {
                Intent.ACTION_SCREEN_OFF -> false
                Intent.ACTION_USER_PRESENT -> true
                else -> isUnlocked()
            }
            refresh()
        }
    }

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == IslandPrefs.PREF_MINI || key == IslandPrefs.PREF_MINI_AIRPODS_ONLY || key == IslandPrefs.PREF_MINI_ALWAYS) refresh()
    }

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
        unlocked = isUnlocked()
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
        context.registerComponentCallbacks(rotation)
        scope.launch { NowPlaying.state.collect { onTrack(it) } }
        scope.launch { GlintStatus.link.collect { refresh() } }
    }

    private fun onTrack(t: NowPlaying.Track) {
        if (t.playing) {
            playedRecently = true
            stoppedAt = 0L
        } else if (wasPlaying) {
            stoppedAt = SystemClock.elapsedRealtime()
        }
        wasPlaying = t.playing
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

    fun refresh() {
        val previewing = sample.value != null
        val playing = previewing || NowPlaying.state.value.playing
        val now = SystemClock.elapsedRealtime()
        val pausedFor = if (playing) 0L else if (stoppedAt == 0L) Long.MAX_VALUE else now - stoppedAt
        val want = MiniIslandRules.wanted(
            MiniIslandRules.Inputs(
                enabled = previewing || IslandPrefs.mini(prefs),
                canDraw = Settings.canDrawOverlays(context),
                playing = playing,
                pausedForMs = pausedFor,
                playedRecently = previewing || playedRecently,
                airPodsOnly = !previewing && IslandPrefs.miniAirPodsOnly(prefs),
                airPodsUp = GlintStatus.link.value is LinkState.Connected,
                screenUnlocked = previewing || unlocked,
                alwaysWithAirPods = !previewing && IslandPrefs.miniAlways(prefs),
            )
        ) && portrait()
        content.value = if (previewing) MiniIslandRules.Content.Music else MiniIslandRules.content(
            playing = playing, pausedForMs = pausedFor, playedRecently = playedRecently,
            airPodsUp = GlintStatus.link.value is LinkState.Connected,
        )
        if (!want) GlintOverlays.miniOrigin = null
        if (want) {
            leaving.value = false
            if (!window.isShowing) show()
        } else if (window.isShowing) {
            leaving.value = true
        }
        // Paused: check again when the 30 seconds are up.
        lingerJob?.cancel()
        if (want && !playing && pausedFor != Long.MAX_VALUE) {
            lingerJob = scope.launch {
                delay(MiniIslandRules.PAUSED_LINGER_MS - pausedFor + 100)
                refresh()
            }
        }
    }

    private fun show() {
        val geo = MiniGeometry(context)
        shownFor = geo.key
        window.onShownChanged = { if (!it) GlintOverlays.miniOrigin = null }
        window.show(geo.compactWindow, geo.windowTop, geo.offsetX) {
            val live by NowPlaying.state.collectAsState()
            val pods by GlintOverlays.snapshot.collectAsState()
            val heart by me.kavishdevar.librepods.services.HeartRate.state.collectAsState()
            MiniIslandHost(
                geometry = geo,
                track = sample.value ?: live,
                content = content.value,
                pods = pods,
                heartBpm = heart.bpm.takeIf { heartShowsOnIsland(heart, System.currentTimeMillis()) },
                leaving = leaving.value,
                hidden = !window.statusBarVisible.value,
                handOff = GlintOverlays.islandVisible.value,
                onVisible = { up ->
                    // Tells the big island where to grow from while the pill is up.
                    GlintOverlays.miniOrigin = if (up) GlintOverlays.MiniOrigin(
                        dx = geo.offsetX.toFloat(),
                        top = geo.centerY - geo.size.height / 2f,
                        width = geo.size.compactWidth,
                        height = geo.size.height,
                    ) else null
                },
                onWindowSize = { window.resize(it) },
                onTouchable = { window.setTouchable(it) },
                onGone = { window.dismiss() },
                onOpen = {
                    val airPods = GlintStatus.link.value is LinkState.Connected
                    if (content.value == MiniIslandRules.Content.AirPods) {
                        GlintOverlays.showIsland(context, IslandEvent.Connected, expand = true)
                    } else {
                        GlintOverlays.showIsland(context, IslandEvent.Music, expand = airPods)
                    }
                },
                onHold = { GlintOverlays.openApp(context) },
                onSkip = { next -> if (sample.value == null) NowPlaying.skip(context, next) },
            )
        }
    }

    private fun portrait() = context.resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE

    private fun isUnlocked(): Boolean {
        val pm = context.getSystemService(PowerManager::class.java)
        val km = context.getSystemService(KeyguardManager::class.java)
        return (pm?.isInteractive ?: true) && !(km?.isKeyguardLocked ?: false)
    }
}

/** Where the front camera is, and the pill's sizes around it, in pixels. */
internal class MiniGeometry(context: Context, testCutouts: List<android.graphics.Rect>? = null) {
    private val density = context.resources.displayMetrics.density
    private val screen = GlintOverlays.screenSize(context)
    /** Room around the pill for the swipe nudge and the springy overshoot. */
    val margin = 14f * density
    val hole: android.graphics.Rect?
    val size: MiniIslandRules.Size
    /** The camera's centre, measured from the top of the screen. */
    val centerY: Float
    val offsetX: Int

    /** What this was measured for: measure again when any of it changes (rotation, fold, display size). */
    val key = Triple(screen, density, context.resources.configuration.orientation)

    init {
        val wm = context.getSystemService(android.view.WindowManager::class.java)
        val cutouts = (testCutouts ?: wm.currentWindowMetrics.windowInsets.displayCutout?.boundingRects.orEmpty())
            .map { MiniIslandRules.Box(it.left, it.top, it.right, it.bottom) }
        // The camera to wrap (punch-hole or notch near the middle); corner cameras and phones
        // without a cutout get the pill in the middle of the status bar instead.
        val cam = MiniIslandRules.pickCamera(cutouts, screen.width, screen.height)
        hole = cam?.let { android.graphics.Rect(it.left, it.top, it.right, it.bottom) }
        size = MiniIslandRules.size(cam?.width?.toFloat() ?: 0f, cam?.height?.toFloat() ?: 0f, density, screen.width.toFloat())
        centerY = MiniIslandRules.centerY(cam, GlintOverlays.statusBarHeight(context), size.height, density)
        offsetX = cam?.let { (it.centerX - screen.width / 2f).roundToInt() } ?: 0
    }

    val windowTop: Int get() = (centerY - size.height / 2f - margin).roundToInt()
    val compactWindow = IntSize((size.compactWidth + margin * 2).roundToInt(), (size.height + margin * 2).roundToInt())
    val wideWindow = IntSize((size.wideWidth + margin * 2).roundToInt(), (size.wideHeight + margin * 2).roundToInt())
    /** The size it grows out of: the camera hole itself. */
    val seedW: Float get() = hole?.width()?.toFloat()?.coerceAtMost(size.compactWidth) ?: (size.height * 0.6f)
    val seedH: Float get() = hole?.height()?.toFloat()?.coerceAtMost(size.height) ?: (size.height * 0.6f)
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
    /** The big island is up: it grew out of this pill, so this one steps aside at once. */
    handOff: Boolean = false,
    onVisible: (Boolean) -> Unit = {},
    onTouchable: (Boolean) -> Unit,
    onGone: () -> Unit,
    onOpen: () -> Unit,
    onHold: () -> Unit,
    onSkip: (next: Boolean) -> Unit,
    /** Screenshots only: draw this state without animating. */
    still: Float? = null,
    stillWide: Float = 0f,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current.density
    val reduce = remember { GlintComfort.reduceMotion(context) }
    val prefs = remember { IslandPrefs.prefs(context) }
    val buzz = remember { IslandBuzz(GlintHaptics(view), IslandPrefs.haptics(prefs)) }
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val visible = !leaving && !hidden && !handOff && !landscape

    val appear = remember { Animatable(still ?: 0f) }
    val wide = remember { Animatable(stillWide) }
    val nudge = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    // Grow out of the camera, or shrink back into it.
    LaunchedEffect(visible) {
        if (still != null) return@LaunchedEffect
        onTouchable(visible)
        if (visible) {
            val handedBack = SystemClock.elapsedRealtime() - GlintOverlays.returnToMiniAt < 800L
            // The big island just shrank back into this spot: carry on as if it never left.
            if (reduce || handedBack) appear.snapTo(1f) else appear.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = 320f))
            onVisible(true)
        } else {
            onVisible(false)
            if (wide.value > 0f) wide.snapTo(0f)
            onWindowSize(geometry.compactWindow)
            // Handing over to the big island: it starts exactly here, so vanish at once.
            if (reduce || handOff) appear.snapTo(0f) else appear.animateTo(0f, spring(dampingRatio = 1f, stiffness = 520f))
            if (leaving) onGone()
        }
    }

    // A new song (or the first one): widen for a moment with its name, then tuck back.
    val songKey = track.title to track.artist
    val names = remember { IslandPrefs.miniNames(prefs) }
    // Each song is announced once: coming back after the big island closes doesn't repeat it.
    var announced by remember { mutableStateOf<Pair<String?, String?>?>(null) }
    LaunchedEffect(songKey, visible) {
        if (still != null || !visible || !names || track.title == null || songKey == announced) return@LaunchedEffect
        announced = songKey
        delay(if (appear.value < 0.9f) 380 else 0)
        onWindowSize(geometry.wideWindow)
        // Let the bigger window reach the screen before growing into it (no flicker).
        androidx.compose.runtime.withFrameNanos { }
        if (reduce) wide.snapTo(1f) else wide.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = 300f))
        delay(MiniIslandRules.NAME_SHOW_MS)
        if (reduce) wide.snapTo(0f) else wide.animateTo(0f, spring(dampingRatio = 1f, stiffness = 340f))
        onWindowSize(geometry.compactWindow)
    }

    // Sound bars: a gentle, continuous wiggle while playing, ticked about 30 times a second
    // (plenty for bars this small, and half the work of every frame).
    val clock = remember { mutableLongStateOf(0L) }
    val moving = visible && track.playing && !reduce && still == null
    LaunchedEffect(moving) {
        while (moving) {
            clock.longValue = SystemClock.elapsedRealtime()
            delay(33)
        }
    }
    val level = remember { mutableFloatStateOf(if (track.playing) 1f else 0f) }
    LaunchedEffect(track.playing) {
        val a = Animatable(level.floatValue)
        a.animateTo(if (track.playing) 1f else 0f, tween(if (reduce) 0 else 260)) { level.floatValue = value }
    }

    // Music and AirPods views cross-fade into each other.
    val music = remember { Animatable(if (content == MiniIslandRules.Content.Music) 1f else 0f) }
    LaunchedEffect(content) {
        val to = if (content == MiniIslandRules.Content.Music) 1f else 0f
        if (reduce || still != null) music.snapTo(to) else music.animateTo(to, tween(320))
    }
    val beat = rememberHeartBeat(heartBpm, reduce || still != null, peak = 1.18f)
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val accent = remember(track.art) { track.art?.let { accentOf(it) } ?: Color.White }
    val paused = remember { ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) }
    val m = geometry.margin
    val s = geometry.size
    val describe = if (content == MiniIslandRules.Content.AirPods) buildString {
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
    val swipeOn by androidx.compose.runtime.rememberUpdatedState(content == MiniIslandRules.Content.Music)
    val textLine = listOfNotNull(track.title, track.artist).joinToString("  ·  ")

    var boxW by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { boxW = it.width }
            .semantics { role = Role.Button; contentDescription = describe }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { buzz.tick(); onOpen() },
                    onLongPress = { buzz.expand(); onHold() },
                )
            }
            .pointerInput(Unit) {
                var dx = 0f
                detectHorizontalDragGestures(
                    onDragStart = { dx = 0f },
                    onDragEnd = {
                        val far = abs(dx) > 34f * density
                        if (far && swipeOn) {
                            buzz.tick()
                            onSkip(dx < 0f) // swipe left = next song
                        }
                        scope.launch { nudge.animateTo(0f, spring(dampingRatio = 0.45f, stiffness = 500f)) }
                    },
                    onDragCancel = { scope.launch { nudge.animateTo(0f, spring(dampingRatio = 0.45f, stiffness = 500f)) } },
                    onHorizontalDrag = { change, amount ->
                        change.consume()
                        dx += amount
                        // Rubber band: follows the finger a little, never far.
                        scope.launch { nudge.snapTo((dx / density).let { it / (1f + abs(it) / 40f) } * density * 0.5f) }
                    },
                )
            }
    ) {
        Canvas(Modifier.fillMaxSize().graphicsLayer { translationX = nudge.value }) {
            val a = appear.value.coerceAtLeast(0f)
            val w = wide.value
            if (a <= 0.001f) return@Canvas
            val cx = size.width / 2f
            val compactW = lerp(geometry.seedW, s.compactWidth, a)
            val compactH = lerp(geometry.seedH, s.height, a.coerceAtMost(1.15f))
            val pillW = lerp(compactW, s.wideWidth, w)
            val pillH = lerp(compactH, s.wideHeight, w)
            // The camera line stays put; the wide pill grows downward under it.
            val top = m + (s.height - compactH) / 2f
            val left = cx - pillW / 2f
            val r = compactH / 2f
            drawRoundRect(Color.Black, Offset(left, top), Size(pillW, pillH), CornerRadius(r, r))

            // Contents fade in once the pill is mostly formed.
            val c0 = ((a - 0.55f) / 0.45f).coerceIn(0f, 1f)
            if (c0 <= 0f) return@Canvas
            val c = c0 * music.value // the music view
            val pc = c0 * (1f - music.value) // the AirPods view
            val side = s.height - 8f * density
            val lineY = m + s.height / 2f
            val artC = Offset(left + 4f * density + side / 2f, lineY)
            val barsC = Offset(left + pillW - 4f * density - side / 2f, lineY)

            // AirPods view: the buds' battery as a ring with the number (green while charging,
            // amber at 20% and below, red at 10%), and on the right the heart while measuring
            // or else the listening mode.
            if (pc > 0.001f) {
                val lvl = pods.budsLevel
                val ringC = when {
                    pods.budsCharging -> Color(0xFF30D158)
                    lvl != null && lvl <= 10 -> Color(0xFFFF453A)
                    lvl != null && lvl <= 20 -> Color(0xFFFFB340)
                    else -> Color.White
                }
                val rr = side / 2f - 1.5f * density
                val tl = Offset(artC.x - rr, artC.y - rr)
                val st = androidx.compose.ui.graphics.drawscope.Stroke(2.2f * density, cap = StrokeCap.Round)
                drawArc(Color.White.copy(alpha = 0.18f * pc), 0f, 360f, false, tl, Size(rr * 2, rr * 2), style = st)
                if (lvl != null) drawArc(ringC.copy(alpha = pc), -90f, 360f * lvl / 100f, false, tl, Size(rr * 2, rr * 2), style = st)
                val num = measurer.measure(
                    lvl?.toString() ?: "–",
                    TextStyle(fontFamily = glintFontFamily, fontSize = (side * 0.34f / density / fontScale).sp, fontWeight = FontWeight.SemiBold, color = Color.White),
                )
                drawText(num, alpha = pc, topLeft = Offset(artC.x - num.size.width / 2f, artC.y - num.size.height / 2f))
                if (heartBpm != null) {
                    val hs = side * 0.36f * beat.value
                    val hp = heartPath(Size(hs, hs))
                    translate(barsC.x - hs / 2f, barsC.y - side * 0.30f - hs / 2f + side * 0.08f) { drawPath(hp, Color.White.copy(alpha = pc)) }
                    val bpmText = measurer.measure(
                        "$heartBpm",
                        TextStyle(fontFamily = glintFontFamily, fontSize = (side * 0.32f / density / fontScale).sp, fontWeight = FontWeight.SemiBold, color = Color.White),
                    )
                    drawText(bpmText, alpha = pc, topLeft = Offset(barsC.x - bpmText.size.width / 2f, barsC.y + side * 0.02f))
                }
            }

            // The cover (or a note when there's no picture), greyed and dimmed while paused.
            val circle = Path().apply { addOval(androidx.compose.ui.geometry.Rect(artC, side / 2f)) }
            if (c > 0.001f) clipPath(circle) {
                val art = track.art
                if (art != null) {
                    drawImage(
                        art,
                        srcOffset = IntOffset.Zero, srcSize = IntSize(art.width, art.height),
                        dstOffset = IntOffset((artC.x - side / 2f).roundToInt(), (artC.y - side / 2f).roundToInt()),
                        dstSize = IntSize(side.roundToInt(), side.roundToInt()),
                        alpha = c * (0.55f + 0.45f * level.floatValue),
                        colorFilter = if (level.floatValue < 0.5f) paused else null,
                    )
                } else {
                    drawCircle(Color(0xFF2C2C2E), side / 2f, artC, alpha = c)
                    drawNote(artC, side * 0.5f, Color.White.copy(alpha = c * 0.9f))
                }
            }

            // How far through the song: a thin ring round the cover (when the app says).
            if (c > 0.001f) track.progress(if (moving) clock.longValue else SystemClock.elapsedRealtime())?.let { p ->
                val ringR = side / 2f + 2f * density
                val tl = Offset(artC.x - ringR, artC.y - ringR)
                val ring = Size(ringR * 2f, ringR * 2f)
                val stroke = androidx.compose.ui.graphics.drawscope.Stroke(1.6f * density, cap = StrokeCap.Round)
                drawArc(Color.White.copy(alpha = 0.16f * c), 0f, 360f, false, tl, ring, style = stroke)
                drawArc(accent.copy(alpha = 0.95f * c), -90f, 360f * p, false, tl, ring, style = stroke)
            }

            // Sound bars in the cover's colour; flat dots while paused.
            val t = clock.longValue / 1000f
            val barW = 2.6f * density
            val gap = 2.4f * density
            val maxH = side * 0.62f
            val startX = barsC.x - (4 * barW + 3 * gap) / 2f + barW / 2f
            if (c > 0.001f) for (i in 0 until 4) {
                val wiggle = 0.35f + 0.65f * abs(sin(t * (2.3f + i * 0.73f) + i * 1.7f))
                val rest = if (i % 2 == 0) 0.62f else 0.42f // Reduce motion: steady bars
                val hgt = lerp(barW, maxH * (if (moving) wiggle else rest), level.floatValue)
                val x = startX + i * (barW + gap)
                drawLine(accent.copy(alpha = c), Offset(x, barsC.y - hgt / 2f), Offset(x, barsC.y + hgt / 2f), barW, StrokeCap.Round)
            }
        }
        // AirPods view, right side: the listening mode symbol (when not measuring heart rate).
        if (music.value < 0.999f && heartBpm == null && pods.listeningMode in 1..4) {
            val side = s.height - 8f * density
            val glyph = side * 0.7f
            ListeningModeGlyph(
                pods.listeningMode, Color.White,
                modifier = Modifier
                    .offset {
                        // Centre of the pill's right-hand spot (where the bars go for music).
                        val pillW = lerp(s.compactWidth, s.wideWidth, wide.value)
                        val barsX = boxW / 2f + pillW / 2f - 4f * density - side / 2f
                        IntOffset((barsX - glyph / 2f + nudge.value).roundToInt(), (m + s.height / 2f - glyph / 2f).roundToInt())
                    }
                    .graphicsLayer {
                        alpha = ((appear.value - 0.55f) / 0.45f).coerceIn(0f, 1f) * (1f - music.value)
                    },
                size = androidx.compose.ui.unit.Dp(glyph / density),
            )
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
                style = TextStyle(fontFamily = glintFontFamily, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color.White),
                modifier = Modifier
                    .offset { IntOffset((m + 14f * density + nudge.value).roundToInt(), (m + s.height + 3f * density).roundToInt()) }
                    .width(androidx.compose.ui.unit.Dp(dpW))
                    .graphicsLayer { alpha = ((wide.value - 0.4f) / 0.6f).coerceIn(0f, 1f) }
                    .basicMarquee(iterations = 1, initialDelayMillis = 900),
            )
            }
        }
    }
}

/** A small music note: a stem with a flag and a round head. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawNote(c: Offset, size: Float, color: Color) {
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
