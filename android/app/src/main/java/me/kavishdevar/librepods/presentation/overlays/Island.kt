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

package me.kavishdevar.librepods.presentation.overlays

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import me.kavishdevar.librepods.services.GlintStatus
import me.kavishdevar.librepods.services.IslandPrefs
import me.kavishdevar.librepods.services.NowPlaying
import me.kavishdevar.librepods.presentation.glint.heartPath
import me.kavishdevar.librepods.presentation.glint.rememberHeartBeat
import androidx.compose.animation.togetherWith
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.drawWithCache
import me.kavishdevar.librepods.services.HeartRate
import me.kavishdevar.librepods.services.HeartInsights
import me.kavishdevar.librepods.presentation.glint.HeartGlyph
import me.kavishdevar.librepods.services.LinkState
import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.animateContentSize
import me.kavishdevar.librepods.services.HeartView
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.unit.Dp
import me.kavishdevar.librepods.R
import me.kavishdevar.librepods.presentation.glint.BatteryRing
import me.kavishdevar.librepods.presentation.glint.GlassLooks
import me.kavishdevar.librepods.presentation.glint.GlintColors
import me.kavishdevar.librepods.presentation.glint.GlintComfort
import me.kavishdevar.librepods.presentation.glint.GlintHaptics
import me.kavishdevar.librepods.presentation.glint.ListeningModeGlyph
import me.kavishdevar.librepods.presentation.glint.PodsVideo
import me.kavishdevar.librepods.presentation.glint.PodsVideoConfig
import me.kavishdevar.librepods.presentation.glint.SystemBlur
import me.kavishdevar.librepods.presentation.glint.drawBolt
import me.kavishdevar.librepods.presentation.glint.drawGlass
import me.kavishdevar.librepods.presentation.glint.drawSystemBlur
import me.kavishdevar.librepods.presentation.glint.glassShadow
import me.kavishdevar.librepods.presentation.glint.lerp
import me.kavishdevar.librepods.presentation.glint.listeningModeName
import me.kavishdevar.librepods.presentation.glint.metaballNeck
import me.kavishdevar.librepods.presentation.glint.rememberTiltLight
import me.kavishdevar.librepods.presentation.glint.roundRectPath
import me.kavishdevar.librepods.presentation.theme.glintFontFamily
import kotlin.math.max
import kotlin.math.roundToInt

internal enum class IslandPhase { Compact, Expanded, Leaving }

/** Screenshots only: start the opened island with the heart's note open. */
internal object IslandTestHooks {
    @Volatile var heartNoteOpen = false
}

private const val LONG_PRESS_MS = 450L
/** The longest a closing island may take before its window is removed regardless. */
private const val LEAVE_TIMEOUT_MS = 1_500L
/** How long after the island opens before the heart chip morphs out of the play/pause button. */
internal const val CHIP_DELAY_MS = 1_000L
/** Back, play/pause and skip side by side (34 + 6 + 40 + 6 + 34). */
private val CONTROLS_W = 120.dp

internal class IslandController(private val context: Context) {
    private val window = OverlayWindow(context, "GlintIsland", anchorTop = true, aboveStatusBar = true)
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main.immediate)
    private val event = mutableStateOf<IslandEvent>(IslandEvent.Connected)
    private val phase = mutableStateOf(IslandPhase.Compact)
    private val generation = mutableIntStateOf(0)
    val isShowing: Boolean get() = window.isShowing

    private var geometry: IslandGeometry? = null

    init {
        // The mini island around the camera steps aside while this one is up; if this one grew
        // out of it, it takes over again in the same spot the moment this one is gone.
        window.onShownChanged = { shown ->
            if (!shown && geometry?.origin != null) GlintOverlays.returnToMiniAt = android.os.SystemClock.elapsedRealtime()
            GlintOverlays.islandVisible.value = shown
        }
        // Moved above or below the status bar (pro's accessibility service started or stopped):
        // a window placed through a stopped service is gone, so let go of it.
        scope.launch {
            me.kavishdevar.librepods.services.IslandAccess.service.collect { if (window.misplaced) window.dismiss() }
        }
        // Turned or folded: it was measured for the old screen, so go (the next event shows a
        // fresh one).
        context.registerComponentCallbacks(object : android.content.ComponentCallbacks {
            private var key = context.resources.configuration.let { Triple(it.orientation, it.screenWidthDp, it.screenHeightDp) }
            override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
                val now = Triple(newConfig.orientation, newConfig.screenWidthDp, newConfig.screenHeightDp)
                if (now != key) { key = now; dismiss(animated = false) }
            }
            @Deprecated("Deprecated in Java")
            override fun onLowMemory() {}
        })
        // Notifications pulled down over it: step aside (above the status bar it would float
        // over the shade).
        scope.launch {
            me.kavishdevar.librepods.services.IslandAccess.panelOpen.collect { open -> if (open) dismiss(animated = true) }
        }
    }

    private var shownAt = 0L

    /** [expand]: open straight into the big view (a tap on the mini island asked for it). */
    fun show(e: IslandEvent, expand: Boolean = false) {
        event.value = e
        generation.intValue++
        val wantsExpanded = expand || (e is IslandEvent.MovedToDevice && e.canTakeBack) || e is IslandEvent.Problem
        // Safety net: an island can't legitimately stay up this long (it leaves after 10 s at
        // most). If one is stuck, for example its animation froze while the screen was off,
        // remove it so this event still appears instead of being swallowed.
        if (window.isShowing && android.os.SystemClock.elapsedRealtime() - shownAt > 25_000L) window.dismiss()
        if (window.isShowing) {
            // Measured from the latest event, so a busy island isn't mistaken for a stuck one.
            shownAt = android.os.SystemClock.elapsedRealtime()
            phase.value = if (wantsExpanded) IslandPhase.Expanded else
                if (phase.value == IslandPhase.Leaving) IslandPhase.Compact else phase.value
            return
        }
        phase.value = if (wantsExpanded) IslandPhase.Expanded else IslandPhase.Compact
        shownAt = android.os.SystemClock.elapsedRealtime()
        // Grow out of the mini island when it's up (one shape, like the Dynamic Island).
        val geo = IslandGeometry(context, GlintOverlays.plannedMiniOrigin())
        geometry = geo
        window.show(if (wantsExpanded) geo.expandedWindow else geo.compactWindow, geo.windowTop) {
            IslandHost(
                geometry = geo,
                event = event.value,
                phase = phase.value,
                generation = generation.intValue,
                blurAllowed = window.blurAllowed.value,
                onPhase = { setPhase(it) },
                onWindowSize = { window.resize(it) },
                onGone = { window.dismiss() },
            )
        }
    }

    fun dismiss(animated: Boolean) {
        if (!window.isShowing) return
        if (animated) setPhase(IslandPhase.Leaving) else window.dismiss()
    }

    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    /**
     * Leaving always ends with the window removed: if the closing animation stalls (the screen
     * turned off mid-way, the phone was busy), an invisible window would otherwise linger over
     * the top of the screen and swallow taps, including on the Dynamic Island.
     */
    private fun setPhase(p: IslandPhase) {
        phase.value = p
        if (p != IslandPhase.Leaving) return
        val gen = generation.intValue
        main.postDelayed({
            if (window.isShowing && phase.value == IslandPhase.Leaving && generation.intValue == gen) window.dismiss()
        }, LEAVE_TIMEOUT_MS)
    }
}

/** All island sizes in pixels, computed once per show from the screen and density. */
internal class IslandGeometry(context: Context, val origin: GlintOverlays.MiniOrigin? = null) {
    private val density = context.resources.displayMetrics.density
    private fun dp(v: Float) = v * density
    private val screen = GlintOverlays.screenSize(context)
    val margin = dp(22f)
    val shadowDrop = dp(16f)
    val tiny = dp(34f)
    val compactH = dp(44f)
    // Wide enough for the buds, two readable lines and the bubble inside one pill.
    // Narrow phones: never wider than the screen (with a small margin each side).
    val compactMainW = minOf(dp(268f), screen.width - dp(24f))
    val satD = dp(42f)
    val satGap = dp(9f)
    /**
     * Where the battery ring rests: inside the pill's right cap, so the compact island is one
     * continuous capsule (like the Dynamic Island) rather than a pill plus a separate bubble.
     * 0 = tucked deep inside, 1 = fully budded off beside the pill.
     */
    val restSplit = satD / (satGap + 2f * satD)
    val expandedW = minOf(screen.width - dp(28f), dp(368f))
    // Header, AirPods and batteries (with the heart while measuring), time left and play/pause.
    val expandedH = dp(196f)
    // The heart's explanation needs more room: tapping the heart grows the island to this.
    val detailH = dp(292f)
    val expandedRadius = dp(40f)
    // Rests just under the status bar, and never over the camera (some phones report no status
    // bar height, for example while a full-screen app is open).
    private val restScreenTop = maxOf(
        GlintOverlays.statusBarHeight(context).toFloat(),
        origin?.let { it.top + it.height } ?: 0f,
    ).roundToInt() + dp(6f).roundToInt()
    /**
     * With the Dynamic Island up, this window starts right under it, so it never covers the
     * pill (which stays tappable) or the status bar (pulling down notifications still works);
     * the pop-up drops out of the pill's underside. Otherwise it has its usual margin above.
     */
    val windowTop: Int = origin?.let { (it.top + it.height + dp(1f)).roundToInt().coerceAtMost(restScreenTop) }
        ?: (restScreenTop - margin.roundToInt())
    /** The pill's top edge at rest, inside the window. */
    val restTop = (restScreenTop - windowTop).toFloat()
    // Where the shape starts (and ends when it leaves): a drop from the Dynamic Island's
    // underside, or a small dot.
    val seedW = origin?.width ?: tiny
    val seedH = if (origin != null) dp(8f) else tiny
    val seedTop = if (origin != null) 0f else restTop
    val seedDx = origin?.dx ?: 0f
    private val below = margin + shadowDrop
    val compactWindow = IntSize(
        (compactMainW + satGap + satD + margin * 2).roundToInt(),
        (restTop + compactH + below).roundToInt()
    )
    val expandedWindow = IntSize(
        (expandedW + margin * 2).roundToInt(),
        (restTop + expandedH + below).roundToInt()
    )
    val detailWindow = IntSize(
        (expandedW + margin * 2).roundToInt(),
        (restTop + detailH + below).roundToInt()
    )
}

@Composable
internal fun IslandHost(
    geometry: IslandGeometry,
    event: IslandEvent,
    phase: IslandPhase,
    generation: Int,
    blurAllowed: Boolean,
    onPhase: (IslandPhase) -> Unit,
    onWindowSize: (IntSize) -> Unit,
    onGone: () -> Unit,
) = CappedFontScale { IslandHostContent(geometry, event, phase, generation, blurAllowed, onPhase, onWindowSize, onGone) }

@Composable
private fun IslandHostContent(
    geometry: IslandGeometry,
    event: IslandEvent,
    phase: IslandPhase,
    generation: Int,
    blurAllowed: Boolean,
    onPhase: (IslandPhase) -> Unit,
    onWindowSize: (IntSize) -> Unit,
    onGone: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current.density
    val prefs = remember { IslandPrefs.prefs(context) }
    val buzz = remember { IslandPrefs.haptics(prefs) }
    val haptics = remember(view, buzz) { IslandBuzz(GlintHaptics(view), buzz) }
    val duration = remember { IslandPrefs.duration(prefs) }
    val snapshot by GlintOverlays.snapshot.collectAsState()
    val playingNow by NowPlaying.state.collectAsState()
    // Every touch inside the island restarts its stay timer, so it never leaves mid-use.
    var touches by remember { mutableIntStateOf(0) }
    val reduceMotion = remember { GlintComfort.reduceMotion(context) }
    val reduceTransparency = remember { GlintComfort.reduceTransparency(context) }
    val light by rememberTiltLight(GlintComfort.tiltLight(context))
    // The same lighter glass as the app on Battery Saver or a hot phone (checked as it appears).
    LaunchedEffect(Unit) { me.kavishdevar.librepods.presentation.glint.GlassBudget.update(context) }
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    val look = remember(dark) { GlassLooks.island(density, dark) }
    val ringTrack = if (dark) Color(0x33FFFFFF) else Color(0x1F000000)

    val useBlur = blurAllowed && !reduceTransparency
    val mainBlur = remember(useBlur) { if (useBlur) SystemBlur.create(view) else null }
    val satBlur = remember(useBlur) { if (useBlur) SystemBlur.create(view) else null }
    DisposableEffect(mainBlur, satBlur) { onDispose { mainBlur?.hide(); satBlur?.hide() } }

    // The window's real height, to wait for a resize to land before growing into it.
    var windowH by remember { mutableIntStateOf(0) }
    suspend fun awaitWindow(height: Int) {
        if (windowH >= height) return
        withTimeoutOrNull(250) { androidx.compose.runtime.snapshotFlow { windowH }.first { it >= height } }
    }
    val appear = remember { Animatable(0f) }
    // Width and height morph on slightly different springs (width leads, height follows with
    // a little more give), so growing and shrinking reads as one liquid drop, not a box scaling.
    val expand = remember { Animatable(0f) }
    val expandH = remember { Animatable(0f) }
    val split = remember { Animatable(0f) }
    // The heart's explanation page, and how far the island has grown to fit it (0..1).
    var heartOpen by remember { mutableStateOf(false) }
    val grow = remember { Animatable(0f) }
    var press by remember { mutableFloatStateOf(0f) }
    var touch by remember { mutableStateOf<Offset?>(null) }
    var dragY by remember { mutableFloatStateOf(0f) }
    // Touch response, like Apple's interactive glass: squish on press, bounce on release,
    // and a rubbery stretch that follows the finger and springs back.
    val squish = remember { Animatable(1f) }
    val pull = remember { Animatable(0f) }
    val touchScope = rememberCoroutineScope()
    val currentPhase by rememberUpdatedState(phase)

    val morph = if (reduceMotion) tween<Float>(160) else spring(dampingRatio = 0.72f, stiffness = 340f)
    val morphH = if (reduceMotion) tween<Float>(160) else spring(dampingRatio = 0.66f, stiffness = 260f)
    val soft = if (reduceMotion) tween<Float>(160) else spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)

    LaunchedEffect(phase, generation) {
        when (phase) {
            IslandPhase.Compact -> {
                coroutineScope {
                    if (appear.value < 0.01f) haptics.appear()
                    launch { expand.animateTo(0f, morph) }
                    launch { expandH.animateTo(0f, morphH) }
                    launch { appear.animateTo(1f, morph) }
                    launch { delay(if (reduceMotion) 0 else 110); split.animateTo(geometry.restSplit, soft) }
                }
                onWindowSize(geometry.compactWindow)
            }
            IslandPhase.Expanded -> {
                onWindowSize(geometry.expandedWindow)
                if (appear.value < 0.01f) haptics.appear() else haptics.expand()
                // Grow only once the bigger window is really there, so no frame is cut off.
                awaitWindow(geometry.expandedWindow.height)
                coroutineScope {
                    launch { appear.animateTo(1f, morph) }
                    launch { split.animateTo(0f, soft) }
                    launch { expand.animateTo(1f, morph) }
                    launch { delay(if (reduceMotion) 0 else 40); expandH.animateTo(1f, morphH) }
                }
            }
            IslandPhase.Leaving -> {
                coroutineScope {
                    launch { split.animateTo(0f, soft) }
                    launch { expand.animateTo(0f, morph) }
                    launch { expandH.animateTo(0f, morphH) }
                    launch { delay(if (reduceMotion) 0 else 90); appear.animateTo(0f, if (reduceMotion) tween(160) else spring(1f, 500f)) }
                }
                onGone()
            }
        }
    }

    // Opening the heart page grows the window first, then the glass; closing shrinks the glass
    // first, then the window, so the shape is never cut off.
    LaunchedEffect(phase) { if (phase != IslandPhase.Expanded) heartOpen = false }
    LaunchedEffect(heartOpen) {
        if (heartOpen) {
            onWindowSize(geometry.detailWindow)
            awaitWindow(geometry.detailWindow.height)
            grow.animateTo(1f, morphH)
        } else if (grow.value > 0f) {
            grow.animateTo(0f, morphH)
            if (currentPhase == IslandPhase.Expanded) onWindowSize(geometry.expandedWindow)
        }
    }
    // The pill's turning AirPods start once the island has settled, so starting the video never
    // competes with the morph for the same frames (the still frame shows until then).
    var pillVideo by remember { mutableStateOf(false) }
    LaunchedEffect(phase) {
        pillVideo = false
        if (phase == IslandPhase.Compact) { delay(if (reduceMotion) 0 else 340); pillVideo = true }
    }

    // How long it stays (Settings > Island), counted from the last touch.
    LaunchedEffect(phase, generation, touches) {
        val hold = when (phase) {
            IslandPhase.Compact -> duration.compactMs + if (event.isAlert()) 1_500L else 0L
            IslandPhase.Expanded -> duration.expandedMs
            IslandPhase.Leaving -> return@LaunchedEffect
        }
        delay(hold)
        onPhase(IslandPhase.Leaving)
    }

    val satelliteContent: @Composable () -> Unit = {
        when (event) {
            is IslandEvent.ListeningMode -> ListeningModeGlyph(event.mode, look.content, size = 22.dp)
            is IslandEvent.MovedToDevice, IslandEvent.TakingOver -> Text("⇄", style = TextStyle(color = look.content, fontSize = 17.sp, fontFamily = glintFontFamily))
            is IslandEvent.Problem -> Text("!", style = TextStyle(color = GlintColors.Amber, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = glintFontFamily))
            IslandEvent.TapSetup -> TapGlyph(look.content, reduceMotion)
            is IslandEvent.Charging -> BatteryRing(if (snapshot.budsCharging) snapshot.budsLevel else snapshot.case, true, size = 30.dp, stroke = 3.dp, track = ringTrack, label = look.content, labelSize = 10.sp)
            is IslandEvent.LowBattery -> BatteryRing(event.level, false, size = 30.dp, stroke = 3.dp, track = ringTrack, label = look.content, labelSize = 10.sp)
            is IslandEvent.Heart -> {
                val hr by HeartRate.state.collectAsState()
                HeartGlyph(hr.bpm, look.content, size = 20.dp, reduceMotion = reduceMotion)
            }
            // Only a button while the island is a pill: once it opens, the bubble fades out over
            // the listening-mode symbol and must not catch taps there.
            is IslandEvent.Music, is IslandEvent.BudOut, IslandEvent.BothIn -> PlayPauseButton(
                playing = playingNow.playing, color = look.content, size = 36.dp, glyph = 17.dp, reduceMotion = reduceMotion,
                enabled = phase == IslandPhase.Compact,
                onClick = { touches++; haptics.tick(); NowPlaying.playPause(context) },
            )
            else -> BatteryRing(snapshot.budsLevel, snapshot.budsCharging, size = 30.dp, stroke = 3.dp, track = ringTrack, label = look.content, labelSize = 10.sp)
        }
    }
    val heartNow by HeartRate.state.collectAsState()
    val age = remember { context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE).getInt(me.kavishdevar.librepods.services.PREF_HR_AGE, 0) }
    val (title, subtitle) = when (event) {
        is IslandEvent.Heart -> heartIslandText(event, heartNow.bpm, age)
        IslandEvent.Music -> NowPlaying.words(playingNow)
        else -> islandText(event, snapshot)
    }

    /** The opened island's height right now: the main page, or growing toward the heart page. */
    fun expandedHeight() = lerp(geometry.expandedH, geometry.detailH, grow.value)

    // Geometry for the current frame, shared by drawing and layout. Reading animatable
    // values here happens in the draw/layout phase, so animating never recomposes.
    fun frame(windowWidth: Float): IslandFrame {
        val a = appear.value
        val e = expand.value
        val eh = expandH.value
        val s = split.value
        val mainW0 = lerp(geometry.seedW, geometry.compactMainW, a)
        val mainH0 = lerp(geometry.seedH, geometry.compactH, a)
        val sq = squish.value
        val p = pull.value
        // Pulling down stretches the glass (with resistance), keeping its volume roughly constant.
        val stretch = (p.coerceAtLeast(0f) * 0.22f).coerceAtMost(26f * density)
        val fullH = expandedHeight()
        val w = lerp(mainW0, geometry.expandedW, e) * sq - stretch * 0.35f
        val h = (lerp(mainH0, fullH, eh) * sq + stretch).coerceAtLeast(1f)
        val radius = lerp(h / 2f, geometry.expandedRadius, e).coerceAtMost(h / 2f)
        val satR = geometry.satD / 2f * a * (1f - e)
        // Satellite travels from tucked inside the pill's right cap to a small gap beside it.
        val tucked = -geometry.satD
        val protrude = lerp(tucked, geometry.satGap + geometry.satD, s) * (1f - e)
        val groupW = w + max(0f, protrude)
        // From the seed (the mini island by the camera, or a dot) down to the resting place.
        val left = (windowWidth - groupW) / 2f + geometry.seedDx * (1f - a).coerceIn(0f, 1f)
        val top = lerp(geometry.seedTop, geometry.restTop, a) + p.coerceAtMost(0f) * 0.35f + (1f - sq) * lerp(geometry.compactH, fullH, eh) / 2f
        val main = Rect(left, top, left + w, top + h)
        val satCenter = Offset(main.right - geometry.satD / 2f + protrude, main.top + minOf(h, geometry.compactH) / 2f)
        return IslandFrame(main, radius, satCenter, satR, a, e, s)
    }

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { windowH = it.height }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    // Only the island itself counts: a touch in the see-through margin around it
                    // (room for its shadow and springy overshoot) does nothing.
                    val f = frame(size.width.toFloat())
                    val slop = 10f * density
                    val onMain = f.main.inflate(slop).contains(down.position)
                    val onSat = f.satR > 1f && (down.position - f.satCenter).getDistance() <= f.satR + slop
                    if (!onMain && !onSat) return@awaitEachGesture
                    touches++
                    touch = down.position
                    press = 1f
                    if (!reduceMotion) touchScope.launch { squish.animateTo(0.965f, spring(0.8f, 900f)) }
                    var totalY = 0f
                    var totalX = 0f
                    var moved = false
                    // Follows the finger until it lifts; returns when it does.
                    suspend fun AwaitPointerEventScope.track() {
                        while (true) {
                            val ev = awaitPointerEvent()
                            val ch = ev.changes.firstOrNull() ?: return
                            // A button inside handled this touch.
                            if (ch.isConsumed) moved = true
                            if (!ch.pressed) return
                            totalY += ch.positionChange().y
                            totalX += ch.positionChange().x
                            if (kotlin.math.abs(totalY) > 8f * density || kotlin.math.abs(totalX) > 8f * density) moved = true
                            dragY = totalY
                            if (!reduceMotion) touchScope.launch { pull.snapTo(totalY) }
                            touch = ch.position
                        }
                    }
                    // Hold (half a second, without moving) opens Glint.
                    val lifted = withTimeoutOrNull(LONG_PRESS_MS) { track(); true } ?: false
                    var opened = false
                    if (!lifted && !moved) {
                        opened = true
                        haptics.expand()
                        GlintOverlays.openApp(context)
                        onPhase(IslandPhase.Leaving)
                    }
                    if (!lifted) track()
                    press = 0f
                    touch = null
                    if (opened) {
                        // Already handled.
                    } else if (dragY < -28f * density) {
                        haptics.dismiss()
                        onPhase(IslandPhase.Leaving)
                    } else if (dragY > 36f * density && currentPhase == IslandPhase.Compact) {
                        // Pulling the island down opens it, like pulling a drop of glass.
                        onPhase(IslandPhase.Expanded)
                    } else if (!moved && event == IslandEvent.TapSetup) {
                        // "Turn it on in pro": straight to that page.
                        haptics.expand()
                        GlintOverlays.openApp(context, me.kavishdevar.librepods.presentation.navigation.AppLinks.ISLANDS)
                        onPhase(IslandPhase.Leaving)
                    } else if (!moved && currentPhase != IslandPhase.Leaving) {
                        onPhase(if (currentPhase == IslandPhase.Expanded) IslandPhase.Compact else IslandPhase.Expanded)
                    }
                    dragY = 0f
                    touchScope.launch { squish.animateTo(1f, if (reduceMotion) tween(120) else spring(0.42f, 420f)) }
                    touchScope.launch { pull.animateTo(0f, if (reduceMotion) tween(120) else spring(0.5f, 380f)) }
                }
            }
            .semantics {
                role = Role.Button
                contentDescription = "$title. $subtitle"
                onClick(label = if (phase == IslandPhase.Expanded) "Collapse" else "Expand") {
                    onPhase(if (phase == IslandPhase.Expanded) IslandPhase.Compact else IslandPhase.Expanded); true
                }
                onLongClick(label = "Open pro") { GlintOverlays.openApp(context); onPhase(IslandPhase.Leaving); true }
            }
            .drawBehind {
                val f = frame(size.width)
                // Grown from the mini island: the black pill is there from the very first frame,
                // so the handover never shows a gap.
                if (f.appear <= 0.001f && geometry.origin == null) return@drawBehind
                val alpha = (f.appear * 1.4f).coerceAtMost(1f)
                val mainPath = roundRectPath(f.main, f.radius)
                val satRect = Rect(f.satCenter.x - f.satR, f.satCenter.y - f.satR, f.satCenter.x + f.satR, f.satCenter.y + f.satR)
                val satVisible = f.satR > 1f && f.satCenter.x + f.satR > f.main.right + 0.5f
                val outline = if (satVisible) {
                    val union = Path.combine(PathOperation.Union, mainPath, roundRectPath(satRect, f.satR))
                    val neck = metaballNeck(
                        Offset(f.main.right - f.main.height / 2f, f.main.center.y), f.main.height / 2f,
                        f.satCenter, f.satR, reach = 1.35f
                    )
                    if (neck != null && !reduceMotion) Path.combine(PathOperation.Union, union, neck) else union
                } else mainPath
                // One blur region per rounded shape (the platform blur is rounded-rect only),
                // then a single glass paint over the liquid union so the tint never doubles.
                if (mainBlur != null) drawSystemBlur(mainBlur, f.main, f.radius, look, alpha)
                if (satBlur != null) {
                    if (satVisible) drawSystemBlur(satBlur, satRect, f.satR, look, alpha)
                    else satBlur.update(0, 0f, 0)
                }
                drawGlass(
                    outline = outline,
                    bounds = if (satVisible) Rect(f.main.left, f.main.top, satRect.right, f.main.bottom) else f.main,
                    cornerRadius = f.radius,
                    look = look,
                    light = light,
                    blur = null,
                    touch = touch,
                    touchStrength = press,
                    alpha = alpha,
                    blurredElsewhere = mainBlur != null,
                    solid = reduceTransparency,
                )
                // Grown out of the mini island: it starts as that black pill and turns to glass
                // as it grows (and back to black as it shrinks home), so it reads as one shape.
                if (geometry.origin != null) {
                    val ink = ((1f - f.appear) * 1.7f).coerceIn(0f, 1f)
                    if (ink > 0.001f) drawPath(outline, Color.Black, alpha = ink)
                }
            }
    ) {
        // Compact content: tiny buds on the left, title/subtitle; satellite glyph on the right.
        IslandLayout(
            expandedWidth = geometry.expandedW.roundToInt(),
            expandedHeight = { expandedHeight().roundToInt() },
            frameProvider = { frame(it) },
            compact = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val art = playingNow.art
                    if (event == IslandEvent.Music && art != null) {
                        Box(Modifier.width(42.dp), contentAlignment = Alignment.Center) { CoverArt(art, 30.dp, 8.dp) }
                    } else {
                        IslandPods(budsWidth = 42.dp, play = pillVideo)
                    }
                    Spacer(Modifier.width(9.dp))
                    androidx.compose.animation.AnimatedContent(
                        targetState = title to subtitle,
                        transitionSpec = {
                            val move = if (reduceMotion) tween<androidx.compose.ui.unit.IntOffset>(0) else spring(0.9f, 420f)
                            (androidx.compose.animation.fadeIn(tween(200, 60)) + androidx.compose.animation.slideInVertically(move) { it / 3 }) togetherWith
                                (androidx.compose.animation.fadeOut(tween(120)) + androidx.compose.animation.slideOutVertically(move) { -it / 3 })
                        },
                        label = "pillWords",
                    ) { (t, sub) ->
                        Column {
                            Text(
                                t, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = TextStyle(fontFamily = glintFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 17.sp, color = look.content)
                            )
                            Text(
                                sub, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = TextStyle(fontFamily = glintFontFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 15.sp, color = look.contentSecondary)
                            )
                        }
                    }
                }
            },
            satellite = satelliteContent,
            expanded = {
                ExpandedIslandContent(
                    event, snapshot, playingNow, title, look.content, look.contentSecondary, look.dark,
                    active = phase == IslandPhase.Expanded,
                    reduceMotion = reduceMotion,
                    age = age,
                    heartOpen = heartOpen,
                    onHeartOpen = { heartOpen = it },
                    onTouch = { touches++; haptics.tick() },
                    onAction = {
                        haptics.expand()
                        if (event is IslandEvent.MovedToDevice && event.canTakeBack) GlintOverlays.takeBackHandler?.invoke()
                        onPhase(IslandPhase.Leaving)
                    },
                )
            },
        )
    }
}

private data class IslandFrame(
    val main: Rect,
    val radius: Float,
    val satCenter: Offset,
    val satR: Float,
    val appear: Float,
    val expand: Float,
    val split: Float,
)

/** Places the three content groups exactly where the current frame's shapes are. */
@Composable
private fun IslandLayout(
    expandedWidth: Int,
    expandedHeight: () -> Int,
    frameProvider: (Float) -> IslandFrame,
    compact: @Composable () -> Unit,
    satellite: @Composable () -> Unit,
    expanded: @Composable () -> Unit,
) {
    Layout(
        contents = listOf(compact, satellite, expanded),
        modifier = Modifier.fillMaxSize()
    ) { (compactM, satM, expM), constraints ->
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        val f = frameProvider(w.toFloat())
        val loose = Constraints(maxWidth = w, maxHeight = h)
        val c = compactM.map { it.measure(Constraints(maxWidth = (f.main.width - f.main.height * 0.9f - f.main.height).roundToInt().coerceAtLeast(0), maxHeight = h)) }
        val s = satM.map { it.measure(loose) }
        val e = expM.map { it.measure(Constraints.fixed(expandedWidth, expandedHeight())) }
        layout(w, h) {
            val compactAlpha = (f.appear - 0.55f).coerceAtLeast(0f) / 0.45f * (1f - f.expand * 2.5f).coerceAtLeast(0f)
            c.forEach {
                it.placeWithLayer(
                    (f.main.left + f.main.height * 0.32f).roundToInt(),
                    (f.main.center.y - it.height / 2f).roundToInt()
                ) { alpha = compactAlpha }
            }
            s.forEach {
                it.placeWithLayer(
                    (f.satCenter.x - it.width / 2f).roundToInt(),
                    (f.satCenter.y - it.height / 2f).roundToInt()
                ) {
                    alpha = ((f.appear - 0.55f) / 0.45f).coerceIn(0f, 1f) * (1f - f.expand * 3f).coerceAtLeast(0f)
                    val sc = 0.7f + 0.3f * ((f.appear - 0.4f) / 0.6f).coerceIn(0f, 1f)
                    scaleX = sc; scaleY = sc
                }
            }
            e.forEach {
                it.placeWithLayer(f.main.left.roundToInt(), f.main.top.roundToInt()) {
                    // Fades in on a smooth S-curve while settling from slightly smaller and higher,
                    // so the content arrives with the glass instead of popping in at the end.
                    val t = ((f.expand - 0.30f) / 0.70f).coerceIn(0f, 1f).let { x -> x * x * (3f - 2f * x) }
                    alpha = t
                    val sc = 0.965f + 0.035f * t
                    scaleX = sc; scaleY = sc
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f)
                    translationY = (1f - t) * -6f * density
                    clip = true
                    shape = RevealShape(f.main.width, f.main.height, f.radius)
                }
            }
        }
    }
}

/**
 * The turning-AirPods clip, keyed off its black background so the buds float on the glass.
 * Played as the original video (a smooth, seamless 25 fps turn). The buds fill 88% x 53% of
 * the clip's square frame, so the frame is sized from the width the buds should take and
 * allowed to overhang its slot (the overhang is transparent).
 */
@Composable
private fun IslandPods(budsWidth: Dp, play: Boolean) {
    val frame = budsWidth / 0.88f
    Box(Modifier.size(budsWidth, frame * 0.56f), contentAlignment = Alignment.Center) {
        PodsVideo(
            video = R.raw.island,
            poster = R.drawable.island_poster,
            aspectRatio = PodsVideoConfig.ISLAND_ASPECT,
            keyBlack = true,
            play = play,
            modifier = Modifier.requiredSize(frame),
        )
    }
}

@Composable
private fun ExpandedIslandContent(
    event: IslandEvent,
    snapshot: PodsSnapshot,
    track: NowPlaying.Track,
    title: String,
    content: Color,
    secondary: Color,
    dark: Boolean,
    active: Boolean,
    reduceMotion: Boolean,
    age: Int,
    heartOpen: Boolean,
    onHeartOpen: (Boolean) -> Unit,
    onTouch: () -> Unit,
    onAction: () -> Unit,
) {
    val context = LocalContext.current
    val link by GlintStatus.link.collectAsState()
    val heart by HeartRate.state.collectAsState()
    // Readings go stale with time even when nothing else changes: look again every few seconds.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(5_000); now = System.currentTimeMillis() } }
    val heartView = remember(heart, link, now) { HeartView.of(heart, link, GlintOverlays.airPodsAudio(context), maxOf(now, System.currentTimeMillis())) }
    val heartBpm = heartView.bpm
    val islandPrefs = remember { IslandPrefs.prefs(context) }
    // The heart can be hidden altogether (Settings > Heart rate); with no AirPods there's nothing to show.
    val chipOn = remember { islandPrefs.getBoolean(me.kavishdevar.librepods.services.PREF_HR_CHIP, true) } && heartView.kind != HeartView.Kind.Away
    // A reading that stops while the explanation is open closes it.
    LaunchedEffect(heartBpm == null) { if (heartBpm == null) onHeartOpen(false) }
    // Opening the island measures for a moment (unless turned off), so the heart has something
    // real to say instead of waiting forever.
    LaunchedEffect(active) {
        if (active && chipOn && islandPrefs.getBoolean(me.kavishdevar.librepods.services.PREF_HR_GLANCE, true) &&
            HeartRate.state.value.status == HeartRate.Status.Off
        ) me.kavishdevar.librepods.services.ServiceManager.getService()?.glanceHeartRate()
    }
    // The heart's one-line note (what's wrong and the one-tap fix) in place of the controls.
    var note by remember { mutableStateOf(IslandTestHooks.heartNoteOpen) }
    LaunchedEffect(note, heartView.kind) {
        if (!note) return@LaunchedEffect
        // Fixed, or left alone for a while: back to the controls.
        if (heartView.kind == HeartView.Kind.Live) { note = false; return@LaunchedEffect }
        delay(7_000)
        note = false
    }
    LaunchedEffect(active) { if (!active) note = false }
    val noteK by androidx.compose.animation.core.animateFloatAsState(
        if (note) 1f else 0f, if (reduceMotion) tween(150) else spring(dampingRatio = 0.8f, stiffness = 380f), label = "heartNote",
    )
    fun heartTap() {
        onTouch()
        val service = me.kavishdevar.librepods.services.ServiceManager.getService()
        when (heartView.tap) {
            HeartView.Tap.Explain -> onHeartOpen(true)
            HeartView.Tap.Start -> service?.glanceHeartRate(me.kavishdevar.librepods.services.HR_GLANCE_TAP_MS)
                ?: GlintOverlays.openApp(context, me.kavishdevar.librepods.presentation.navigation.AppLinks.HEART)
            HeartView.Tap.Retry, HeartView.Tap.Reconnect, HeartView.Tap.None -> note = true
        }
    }
    fun heartFix() {
        onTouch()
        val service = me.kavishdevar.librepods.services.ServiceManager.getService()
        when (heartView.tap) {
            HeartView.Tap.Retry -> service?.retryHeartRate()
            HeartView.Tap.Reconnect -> service?.retryConnectionNow()
            HeartView.Tap.Start -> service?.glanceHeartRate(me.kavishdevar.librepods.services.HR_GLANCE_TAP_MS)
            else -> {}
        }
        note = false
    }
    // The turning AirPods start once the opening has settled (the still frame shows until then).
    var podsVideo by remember { mutableStateOf(false) }
    LaunchedEffect(active) {
        podsVideo = false
        if (active) { delay(if (reduceMotion) 0 else 420); podsVideo = true }
    }
    val actionText = when {
        event is IslandEvent.MovedToDevice && event.canTakeBack -> "Use here"
        event is IslandEvent.Problem -> "Dismiss"
        else -> null
    }
    val track2 = if (dark) Color(0x33FFFFFF) else Color(0x1F000000)
    // The heart chip's entrance: a second after the island opens it grows out from under the
    // play/pause button and glides over to its place on the left. Kept out here so returning
    // from the heart page doesn't replay it.
    val chipBud = remember { androidx.compose.animation.core.Animatable(0f) }
    val chipIn = remember { androidx.compose.animation.core.Animatable(0f) }
    val view = androidx.compose.ui.platform.LocalView.current
    val hapticsOn = remember { IslandPrefs.haptics(IslandPrefs.prefs(context)) }
    LaunchedEffect(active) {
        if (!active) { chipBud.snapTo(0f); chipIn.snapTo(0f); return@LaunchedEffect }
        if (reduceMotion) { chipBud.snapTo(1f); chipIn.snapTo(1f); return@LaunchedEffect }
        if (chipIn.value >= 1f) return@LaunchedEffect
        delay(CHIP_DELAY_MS)
        // First a bubble buds out from under the button, then it stretches and glides over.
        chipBud.animateTo(1f, tween(220, easing = androidx.compose.animation.core.FastOutSlowInEasing))
        chipIn.animateTo(1f, spring(dampingRatio = 0.78f, stiffness = 230f))
        if (hapticsOn) view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
    }
    androidx.compose.animation.AnimatedContent(
        targetState = heartOpen && heartBpm != null,
        transitionSpec = {
            val spec = if (reduceMotion) tween<Float>(150) else spring(0.86f, 420f)
            (androidx.compose.animation.fadeIn(spec) + androidx.compose.animation.scaleIn(spec, initialScale = 0.94f)) togetherWith
                (androidx.compose.animation.fadeOut(tween(110)) + androidx.compose.animation.scaleOut(tween(110), targetScale = 0.97f))
        },
        modifier = Modifier.fillMaxSize(),
        label = "islandPage",
    ) { showHeart ->
        if (showHeart) {
            HeartDetail(
                bpm = heartBpm ?: 0, age = age, content = content, secondary = secondary, dark = dark, reduceMotion = reduceMotion,
                onBack = { onTouch(); onHeartOpen(false) },
            )
            return@AnimatedContent
        }
        Column(Modifier.fillMaxSize().padding(start = 22.dp, end = 18.dp, top = 16.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontFamily = glintFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 22.sp, color = content))
                    Spacer(Modifier.height(3.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val (dot, text) = linkSummary(link, snapshot, event)
                        Box(Modifier.size(8.dp).background(dot, CircleShape))
                        Spacer(Modifier.width(6.dp))
                        Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontFamily = glintFontFamily, fontSize = 13.sp, color = secondary))
                    }
                }
                if (snapshot.listeningMode in 1..4) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(72.dp)) {
                        ListeningModeGlyph(snapshot.listeningMode, content, size = 24.dp)
                        Spacer(Modifier.height(3.dp))
                        Text(shortModeName(snapshot.listeningMode), maxLines = 1, style = TextStyle(fontFamily = glintFontFamily, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = secondary, textAlign = TextAlign.Center))
                    }
                }
            }
            Row(Modifier.weight(1f).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IslandPods(budsWidth = 104.dp, play = podsVideo)
                Spacer(Modifier.weight(1f))
                // Left, right and case as three rings marked L, R and a case symbol; % under each.
                // Narrow phones get a little less space between the rings.
                val narrow = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 360
                Row(horizontalArrangement = Arrangement.spacedBy(if (narrow) 9.dp else 14.dp)) {
                    PartRing(PartMark.Left, snapshot.left, snapshot.leftCharging, content, secondary, track2)
                    PartRing(PartMark.Right, snapshot.right, snapshot.rightCharging, content, secondary, track2)
                    PartRing(PartMark.Case, snapshot.case, snapshot.caseCharging, content, secondary, track2)
                }
            }
            Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
                // The heart chip, in the island's own colours: what the heart is honestly doing
                // (a live number, starting, resting, no signal, blocked, or "Measure"). It morphs
                // out of the play/pause button on the right, so it starts at this slot's right
                // edge. Tapped when something's wrong, it widens into a one-line note with the fix.
                var slotW by remember { mutableIntStateOf(0) }
                Box(
                    Modifier.weight(1f).fillMaxHeight().onSizeChanged { slotW = it.width },
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (chipOn) androidx.compose.animation.AnimatedContent(
                        targetState = note,
                        transitionSpec = {
                            val spec = if (reduceMotion) tween<Float>(120) else spring(0.82f, 420f)
                            (androidx.compose.animation.fadeIn(spec) + androidx.compose.animation.scaleIn(spec, initialScale = 0.92f, transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f))) togetherWith
                                (androidx.compose.animation.fadeOut(tween(100)) + androidx.compose.animation.scaleOut(tween(100), targetScale = 0.96f, transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)))
                        },
                        label = "heartChipNote",
                    ) { showNote ->
                        if (showNote) HeartNote(heartView, content, secondary, dark, reduceMotion, onFix = { heartFix() }, onClose = { onTouch(); note = false })
                        else HeartChip(
                            view = heartView, content = content, secondary = secondary, dark = dark, reduceMotion = reduceMotion,
                            enabled = active && chipIn.value > 0.9f,
                            morph = chipIn.value,
                            bud = chipBud.value,
                            fromX = slotW + with(androidx.compose.ui.platform.LocalDensity.current) { 80.dp.toPx() },
                            onClick = { heartTap() },
                        )
                    }
                }
                if (actionText != null) {
                    GlassPillButton(text = actionText, textColor = content, dark = dark, height = 32.dp, fontSize = 14.sp, onClick = onAction)
                    Spacer(Modifier.width(8.dp))
                }
                // Music controls, no song bar (the opened island stays small). At first there's
                // only play/pause, at the right edge; with the heart chip, back and skip bud out
                // from under it and spread apart as play/pause glides into the middle. While the
                // heart's note is open they step aside to give it the row.
                val m = chipIn.value
                val bud = chipBud.value
                val ready = active && m > 0.9f && !note
                Box(
                    Modifier
                        .width(CONTROLS_W * (1f - noteK))
                        .height(40.dp)
                        .graphicsLayer { alpha = 1f - noteK; val sc = 1f - 0.15f * noteK; scaleX = sc; scaleY = sc }
                        .drawBehind {
                            // Liquid: while back and skip bud out of play/pause, a neck of the same
                            // glass joins them, stretching thinner until it lets go.
                            val mm = chipIn.value
                            val bb = chipBud.value
                            if (reduceMotion || bb <= 0.01f || mm >= 0.98f) return@drawBehind
                            val dp1 = 1.dp.toPx()
                            val play = Offset((60f + (1f - mm) * 40f) * dp1, 20f * dp1)
                            val pr = 20f * dp1
                            val sr = 17f * dp1 * (0.55f + 0.45f * bb)
                            val back = Offset((17f + (1f - mm) * 83f) * dp1, 20f * dp1)
                            val next = Offset((103f - (1f - mm) * 3f) * dp1, 20f * dp1)
                            val fill = if (dark) Color.White.copy(alpha = 0.13f) else Color.Black.copy(alpha = 0.06f)
                            val circles = Path().apply {
                                addOval(Rect(play, pr)); addOval(Rect(back, sr)); addOval(Rect(next, sr))
                            }
                            listOf(back, next).forEach { c ->
                                val neck = metaballNeck(play, pr, c, sr, v = 0.45f, reach = 1.6f) ?: return@forEach
                                // Only the bridge itself: the buttons draw their own glass on top.
                                drawPath(Path.combine(PathOperation.Difference, neck, circles), fill, alpha = bb)
                            }
                        }
                ) {
                    val travel = with(androidx.compose.ui.platform.LocalDensity.current) { 40.dp.toPx() }
                    SkipButton(
                        next = false, color = content, dark = dark, enabled = ready,
                        modifier = Modifier.graphicsLayer {
                            translationX = (1f - m) * (travel * 2f + 3.dp.toPx())
                            val sc = 0.55f + 0.45f * bud; scaleX = sc; scaleY = sc; alpha = bud
                        },
                        glyphAlpha = { ((chipIn.value - 0.3f) / 0.4f).coerceIn(0f, 1f) },
                        onClick = { onTouch(); NowPlaying.skip(context, next = false) },
                    )
                    SkipButton(
                        next = true, color = content, dark = dark, enabled = ready,
                        modifier = Modifier.offset(x = 86.dp).graphicsLayer {
                            translationX = -(1f - m) * 3.dp.toPx()
                            val sc = 0.55f + 0.45f * bud; scaleX = sc; scaleY = sc; alpha = bud
                        },
                        glyphAlpha = { ((chipIn.value - 0.3f) / 0.4f).coerceIn(0f, 1f) },
                        onClick = { onTouch(); NowPlaying.skip(context, next = true) },
                    )
                    Box(Modifier.offset(x = 40.dp).graphicsLayer { translationX = (1f - m) * travel }) {
                        PlayPauseButton(
                            playing = track.playing, color = content, size = 40.dp, glyph = 16.dp, reduceMotion = reduceMotion,
                            enabled = active, glass = true, dark = dark,
                            onClick = { onTouch(); NowPlaying.playPause(context) },
                        )
                    }
                }
            }
        }
    }
}


@Composable
private fun CoverArt(art: androidx.compose.ui.graphics.ImageBitmap, size: Dp, corner: Dp) {
    androidx.compose.foundation.Image(
        art, contentDescription = null,
        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
        modifier = Modifier.size(size).clip(androidx.compose.foundation.shape.RoundedCornerShape(corner)),
    )
}

/**
 * Play and pause as one shape that morphs: the two pause bars slide into the two halves of
 * the play triangle (and back). A press squishes it; the release fires [onClick].
 */
@Composable
internal fun PlayPauseButton(
    playing: Boolean, color: Color, size: Dp, glyph: Dp, reduceMotion: Boolean,
    enabled: Boolean = true, glass: Boolean = false, dark: Boolean = true, onClick: () -> Unit,
) {
    val morphTo by androidx.compose.animation.core.animateFloatAsState(
        if (playing) 0f else 1f, if (reduceMotion) tween(0) else spring(0.7f, 520f), label = "playPause"
    )
    PressableGlyph(size = size, description = if (playing) "Pause" else "Play", onClick = onClick, enabled = enabled, dark = dark) {
        if (glass) drawGlassCapsule(dark)
        val g = glyph.toPx()
        val o = Offset((this.size.width - g) / 2f, (this.size.height - g) / 2f)
        fun pt(px: Float, py: Float, qx: Float, qy: Float) = Offset(o.x + lerp(px, qx, morphTo) * g, o.y + lerp(py, qy, morphTo) * g)
        val effect = androidx.compose.ui.graphics.PathEffect.cornerPathEffect(g * 0.09f)
        val left = Path().apply {
            val a = pt(0.18f, 0.12f, 0.20f, 0.10f); val b = pt(0.40f, 0.12f, 0.55f, 0.30f)
            val c = pt(0.40f, 0.88f, 0.55f, 0.70f); val d = pt(0.18f, 0.88f, 0.20f, 0.90f)
            moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y); lineTo(d.x, d.y); close()
        }
        val right = Path().apply {
            val a = pt(0.60f, 0.12f, 0.53f, 0.29f); val b = pt(0.82f, 0.12f, 0.90f, 0.50f)
            val c = pt(0.82f, 0.88f, 0.90f, 0.50f); val d = pt(0.60f, 0.88f, 0.53f, 0.71f)
            moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y); lineTo(d.x, d.y); close()
        }
        drawPath(left, color, style = androidx.compose.ui.graphics.drawscope.Fill)
        drawPath(right, color, style = androidx.compose.ui.graphics.drawscope.Fill)
        // Rounded corners on top of the fill: same path, stroked with the corner effect.
        drawPath(left, color, style = androidx.compose.ui.graphics.drawscope.Stroke(g * 0.06f, pathEffect = effect, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        drawPath(right, color, style = androidx.compose.ui.graphics.drawscope.Stroke(g * 0.06f, pathEffect = effect, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    }
}

/**
 * A tap target for the island's own buttons: consumes its touch (so the island behind doesn't
 * also treat it as a tap or a hold), reports [onPressed] for the squish, and fires [onClick]
 * when the finger lifts inside.
 */
private fun Modifier.islandPress(enabled: Boolean, description: String, onClick: () -> Unit, onPressed: (Boolean) -> Unit, onAt: (Offset) -> Unit = {}): Modifier {
    if (!enabled) return this
    return this
        .semantics {
            role = Role.Button
            contentDescription = description
            onClick { onClick(); true }
        }
        .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown()
                down.consume()
                onAt(down.position)
                onPressed(true)
                var inside = true
                while (true) {
                    val ch = awaitPointerEvent().changes.firstOrNull() ?: break
                    onAt(ch.position)
                    inside = ch.position.x in 0f..size.width.toFloat() && ch.position.y in 0f..size.height.toFloat()
                    ch.consume()
                    if (!ch.pressed) break
                }
                onPressed(false)
                if (inside) onClick()
            }
        }
}

/** A round button that squishes softly when pressed and draws [draw]. */
/** Back or skip: two small rounded triangles, on the island's glass. */
@Composable
private fun SkipButton(next: Boolean, color: Color, dark: Boolean, enabled: Boolean, modifier: Modifier = Modifier, glyphAlpha: () -> Float = { 1f }, onClick: () -> Unit) {
    Box(modifier.padding(top = 3.dp).size(34.dp)) {
        PressableGlyph(size = 34.dp, description = if (next) "Next song" else "Previous song", onClick = onClick, enabled = enabled, dark = dark) {
            drawGlassCapsule(dark)
            val g = 12.dp.toPx()
            val cy = size.height / 2f
            val dir = if (next) 1f else -1f
            val cx = size.width / 2f
            val effect = androidx.compose.ui.graphics.PathEffect.cornerPathEffect(g * 0.12f)
            for (k in 0..1) {
                val baseX = cx + dir * (k * g * 0.5f - g * 0.5f)
                val p = Path().apply {
                    moveTo(baseX, cy - g * 0.42f)
                    lineTo(baseX + dir * g * 0.5f, cy)
                    lineTo(baseX, cy + g * 0.42f)
                    close()
                }
                // The arrows appear once the button has come out from under play/pause.
                val a = glyphAlpha()
                drawPath(p, color, alpha = a, style = androidx.compose.ui.graphics.drawscope.Fill)
                drawPath(p, color, alpha = a, style = androidx.compose.ui.graphics.drawscope.Stroke(g * 0.08f, pathEffect = effect))
            }
        }
    }
}

@Composable
private fun PressableGlyph(size: Dp, description: String, onClick: () -> Unit, enabled: Boolean = true, dark: Boolean = true, draw: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit) {
    val currentOnClick by rememberUpdatedState(onClick)
    var pressed by remember { mutableStateOf(false) }
    var at by remember { mutableStateOf<Offset?>(null) }
    // Glass answers a finger: it swells a little and lights up where it's held, then settles
    // back with a soft bounce (the same everywhere, see GlassPress).
    val press by androidx.compose.animation.core.animateFloatAsState(if (pressed) 1f else 0f, me.kavishdevar.librepods.presentation.glint.GlassPress.spec(pressed), label = "press")
    androidx.compose.foundation.Canvas(
        Modifier
            .size(size)
            .graphicsLayer { val sc = 1f + (me.kavishdevar.librepods.presentation.glint.GlassPress.SWELL - 1f) * press; scaleX = sc; scaleY = sc }
            .islandPress(enabled, description, { currentOnClick() }, { pressed = it }, { at = it })
    ) {
        draw()
        if (press > 0.01f) clipPath(Path().apply { addOval(Rect(Offset.Zero, this@Canvas.size)) }) {
            with(me.kavishdevar.librepods.presentation.glint.GlassPress) { drawFingerGlow(at, press.coerceIn(0f, 1f), dark) }
        }
    }
}

/**
 * A heart with the live number inside it, beating smoothly at that rate, with a soft glow that
 * swells on each beat. Tap it for what it means. The shape and its shading are built once per
 * size and the beat only scales the layer, so beating never redraws or re-measures anything.
 */
@Composable
private fun HeartBadge(bpm: Int, size: Dp, dark: Boolean, reduceMotion: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val peak = 1.06f
    val beat = rememberHeartBeat(bpm, reduceMotion, peak = peak)
    // Like the icon's pearl: a white heart on the dark island, a graphite one on the light island.
    val top = if (dark) Color(0xFFFFFFFF) else Color(0xFF5A5E66)
    val bottom = if (dark) Color(0xFFC3C7CE) else Color(0xFF1E2024)
    val ink = if (dark) Color(0xFF16171A) else Color.White
    val label = if (dark) Color(0xB3FFFFFF) else Color(0x99000000)
    val currentOnClick by rememberUpdatedState(onClick)
    var pressed by remember { mutableStateOf(false) }
    val press by androidx.compose.animation.core.animateFloatAsState(if (pressed) 1f else 0f, spring(0.62f, 620f), label = "heartPress")
    val numberSize = with(LocalDensity.current) { (size * 0.29f).toSp() }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(size)
                .islandPress(enabled, "Heart rate $bpm beats per minute. Tap for what it means", { currentOnClick() }, { pressed = it }),
            contentAlignment = Alignment.Center,
        ) {
            // Glow: brightens and spreads a little with each beat.
            Spacer(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val k = ((beat.value - 1f) / (peak - 1f)).coerceIn(0f, 1f)
                        alpha = (if (dark) 0.30f else 0.20f) + 0.45f * k
                        val sc = 1.02f + 0.10f * k
                        scaleX = sc; scaleY = sc
                    }
                    .drawWithCache {
                        val glow = androidx.compose.ui.graphics.Brush.radialGradient(
                            listOf(bottom.copy(alpha = if (dark) 0.30f else 0.22f), bottom.copy(alpha = 0f)),
                            center = Offset(this.size.width / 2f, this.size.height * 0.52f), radius = this.size.minDimension * 0.62f
                        )
                        onDrawBehind { drawCircle(glow, this.size.minDimension * 0.62f, Offset(this.size.width / 2f, this.size.height * 0.52f)) }
                    }
            )
            // The heart: a top-to-bottom red gradient, a soft light in the upper left, gentle
            // depth toward the tip and a faint rim. No hard-edged shine.
            Spacer(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { val sc = beat.value * (1f - 0.08f * press); scaleX = sc; scaleY = sc }
                    .drawWithCache {
                        val s = this.size
                        val heart = heartPath(s)
                        val body = androidx.compose.ui.graphics.Brush.verticalGradient(listOf(top, bottom), s.height * 0.07f, s.height * 0.93f)
                        val light = androidx.compose.ui.graphics.Brush.radialGradient(
                            listOf(Color.White.copy(alpha = 0.34f), Color.White.copy(alpha = 0f)),
                            center = Offset(s.width * 0.30f, s.height * 0.26f), radius = s.width * 0.42f
                        )
                        val depth = androidx.compose.ui.graphics.Brush.radialGradient(
                            listOf(Color.Black.copy(alpha = 0.14f), Color.Transparent),
                            center = Offset(s.width * 0.5f, s.height), radius = s.width * 0.6f
                        )
                        val rim = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx())
                        onDrawBehind {
                            drawPath(heart, body)
                            drawPath(heart, depth)
                            drawPath(heart, light)
                            drawPath(heart, Color.White.copy(alpha = 0.20f), style = rim)
                        }
                    }
            )
            // The number rolls to each new reading instead of jumping.
            androidx.compose.animation.AnimatedContent(
                targetState = bpm,
                transitionSpec = {
                    val up = targetState > initialState
                    val spec = if (reduceMotion) tween<androidx.compose.ui.unit.IntOffset>(0) else spring(0.86f, 500f)
                    (androidx.compose.animation.slideInVertically(spec) { if (up) it / 2 else -it / 2 } + androidx.compose.animation.fadeIn(tween(160))) togetherWith
                        (androidx.compose.animation.slideOutVertically(spec) { if (up) -it / 2 else it / 2 } + androidx.compose.animation.fadeOut(tween(120)))
                },
                modifier = Modifier
                    .offset(y = size * -0.025f)
                    .graphicsLayer { val sc = beat.value * (1f - 0.08f * press); scaleX = sc; scaleY = sc },
                label = "bpm",
            ) { value ->
                Text(
                    value.toString(),
                    style = TextStyle(
                        fontFamily = glintFontFamily, fontWeight = FontWeight.Bold, fontSize = numberSize, color = ink,
                        fontFeatureSettings = "tnum",
                    ),
                )
            }
        }
        Text("BPM", style = TextStyle(fontFamily = glintFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 10.sp, letterSpacing = 0.6.sp, color = label))
    }
}

/** The bigger explanation: a large heart, what the number means, and where it sits on a scale. */
@Composable
private fun HeartDetail(bpm: Int, age: Int, content: Color, secondary: Color, dark: Boolean, reduceMotion: Boolean, onBack: () -> Unit) {
    val context = LocalContext.current
    val usual by androidx.compose.runtime.produceState(0) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            HeartInsights.usualResting(HeartRate.history(context), System.currentTimeMillis()) ?: 0
        }
    }
    val meaning = HeartInsights.meaning(bpm, age, usual)
    val bandColor = me.kavishdevar.librepods.presentation.screens.HeartColors.band(meaning.band, dark)
    Column(Modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PressableGlyph(size = 34.dp, description = "Back", onClick = onBack) {
                val w = this.size.width
                val stroke = androidx.compose.ui.graphics.drawscope.Stroke(w * 0.07f, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
                drawPath(Path().apply { moveTo(w * 0.58f, w * 0.30f); lineTo(w * 0.40f, w * 0.50f); lineTo(w * 0.58f, w * 0.70f) }, content, style = stroke)
            }
            Text("Heart rate", style = TextStyle(fontFamily = glintFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = content))
            Spacer(Modifier.weight(1f))
            val hr by HeartRate.state.collectAsState()
            Text(
                if (hr.status == HeartRate.Status.Live) "Live" else "Last reading",
                style = TextStyle(fontFamily = glintFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = secondary)
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            HeartBadge(bpm = bpm, size = 96.dp, dark = dark, reduceMotion = reduceMotion, onClick = onBack)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // The scale's colour for this reading, as a small dot, so the headline stays calm.
                    Box(Modifier.size(9.dp).background(bandColor, CircleShape))
                    Spacer(Modifier.width(7.dp))
                    Text(meaning.headline, maxLines = 2, style = TextStyle(fontFamily = glintFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 22.sp, color = content))
                }
                Spacer(Modifier.height(4.dp))
                Text(meaning.detail, maxLines = 5, overflow = TextOverflow.Ellipsis, style = TextStyle(fontFamily = glintFontFamily, fontSize = 13.sp, lineHeight = 17.sp, color = content.copy(alpha = 0.86f)))
            }
        }
        Spacer(Modifier.weight(1f))
        // This session so far, for context: is the number normal for today?
        val session by HeartRate.state.collectAsState()
        val lo = session.min
        val avg = session.average
        val hi = session.max
        if (lo != null && avg != null && hi != null && session.samples.size >= 2) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                listOf("Lowest" to lo, "Average" to avg, "Highest" to hi).forEach { (label, v) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$v", style = TextStyle(fontFamily = glintFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = content))
                        Text(label, style = TextStyle(fontFamily = glintFontFamily, fontSize = 11.sp, color = secondary))
                    }
                }
            }
            Spacer(Modifier.weight(1f))
        }
        me.kavishdevar.librepods.presentation.screens.HeartScale(bpm = bpm, usual = usual, age = age, dark = dark, ink = content)
        Text(
            "Typical adult resting: 60–100 BPM · For fitness, not medical use",
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = TextStyle(fontFamily = glintFontFamily, fontSize = 11.sp, color = secondary, textAlign = TextAlign.Center),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** The island's haptics, silenced by Settings > Island > Haptics. */
internal class IslandBuzz(private val h: GlintHaptics, private val on: Boolean) {
    fun appear() { if (on) h.appear() }
    fun expand() { if (on) h.expand() }
    fun dismiss() { if (on) h.dismiss() }
    fun tick() { if (on) h.tick() }
    /** A finger landed on it: a light, immediate tap so you feel it registered. */
    fun touch() { if (on) h.touch() }
    /** A gesture did something: a firmer click. */
    fun confirm() { if (on) h.confirm() }
}

/** Alerts stay a little longer. */
/** A fingertip with two rings rippling out: "tap here". */
@Composable
private fun TapGlyph(color: Color, reduceMotion: Boolean) {
    val t = if (reduceMotion) remember { mutableFloatStateOf(0.5f) } else {
        androidx.compose.animation.core.rememberInfiniteTransition(label = "tap")
            .animateFloat(0f, 1f, androidx.compose.animation.core.infiniteRepeatable(tween(1400)), label = "ripple")
    }
    androidx.compose.foundation.Canvas(Modifier.size(24.dp)) {
        val c = center
        val r = size.minDimension / 2f
        drawCircle(color, r * 0.22f, c)
        for (i in 0 until 2) {
            val k = (t.value + i * 0.5f) % 1f
            drawCircle(color.copy(alpha = (1f - k) * 0.8f), r * (0.35f + 0.6f * k), c, style = androidx.compose.ui.graphics.drawscope.Stroke(1.6.dp.toPx()))
        }
    }
}

private fun IslandEvent.isAlert() = this is IslandEvent.LowBattery || (this is IslandEvent.Heart && alert) || this is IslandEvent.Problem

/** A coloured dot and a plain line saying what the connection can do right now. */
private fun linkSummary(link: LinkState, s: PodsSnapshot, event: IslandEvent): Pair<Color, String> {
    val where = when {
        s.leftInEar && s.rightInEar -> "in your ears"
        s.leftInEar || s.rightInEar -> "one in your ear"
        s.lidOpen -> "case open"
        else -> null
    }
    val base = when (link) {
        is LinkState.Connected -> GlintColors.Green to "Connected"
        is LinkState.Connecting -> GlintColors.Amber to "Connecting…"
        is LinkState.Retrying -> GlintColors.Amber to "Reconnecting controls…"
        is LinkState.GaveUp -> GlintColors.Red to "Audio only"
        LinkState.BluetoothOff -> GlintColors.Red to "Bluetooth is off"
        LinkState.NoPermission -> GlintColors.Red to "Needs permission"
        LinkState.Idle -> if (event is IslandEvent.MovedToDevice) GlintColors.Amber to "Playing on another device" else GlintColors.Amber to "Not connected"
    }
    return if (where != null && link is LinkState.Connected) base.first to "Connected · $where" else base
}

private fun shortModeName(mode: Int): String = when (mode) {
    1 -> "Off"
    2 -> "Noise Canc."
    3 -> "Transparency"
    4 -> "Adaptive"
    else -> ""
}

internal enum class PartMark(val spoken: String) { Left("Left"), Right("Right"), Case("Case") }

/**
 * One battery as a ring with its part marked inside (L, R, or a small case), and the level
 * under it. Charging turns the ring green with a soft light running round it, and adds a
 * small bolt before the level.
 */
@Composable
private fun PartRing(mark: PartMark, level: Int?, charging: Boolean, content: Color, secondary: Color, track: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.semantics(mergeDescendants = true) {
            contentDescription = "${mark.spoken} ${level?.let { "$it percent" } ?: "unknown"}${if (charging) ", charging" else ""}"
        },
    ) {
        Box(contentAlignment = Alignment.Center) {
            BatteryRing(level, charging, size = 38.dp, stroke = 3.5.dp, track = track, showLabel = false, centerBolt = false)
            when (mark) {
                PartMark.Case -> androidx.compose.foundation.Image(
                    me.kavishdevar.librepods.presentation.glint.GlintSymbols.CaseFill, null,
                    colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(content.copy(alpha = if (level == null) 0.4f else 0.9f)),
                    modifier = Modifier.size(15.dp),
                )
                else -> Text(
                    if (mark == PartMark.Left) "L" else "R",
                    style = TextStyle(fontFamily = glintFontFamily, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = content.copy(alpha = if (level == null) 0.4f else 0.9f))
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (charging && level != null) {
                androidx.compose.foundation.Canvas(Modifier.size(9.dp)) { drawBolt(center, size.minDimension * 0.5f, GlintColors.Green) }
                Spacer(Modifier.width(2.dp))
            }
            Text(
                if (level != null) "$level%" else "–",
                maxLines = 1,
                softWrap = false, // never "54" over "%" on narrow phones
                style = TextStyle(fontFamily = glintFontFamily, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (level != null) content else secondary, fontFeatureSettings = "tnum")
            )
        }
    }
}

/**
 * A small glass chip with a heart in the island's own colour, beating at the live rate, and the
 * number. Sits beside the play button; tap it for the explanation.
 */
@Composable
private fun HeartChip(
    view: HeartView.View,
    content: Color,
    secondary: Color,
    dark: Boolean,
    reduceMotion: Boolean,
    enabled: Boolean,
    /** 0 = a round bubble hidden under the play/pause button, 1 = the chip in its place. */
    morph: Float = 1f,
    /** How far right the play/pause button is from the chip's place, in pixels. */
    fromX: Float = 0f,
    /** 0..1 before [morph]: the bubble peeking out from under the play/pause button. */
    bud: Float = 1f,
    onClick: () -> Unit,
) {
    val live = view.kind == HeartView.Kind.Live
    val beat = rememberHeartBeat(view.bpm.takeIf { live }, reduceMotion, peak = 1.16f)
    val currentOnClick by rememberUpdatedState(onClick)
    var pressed by remember { mutableStateOf(false) }
    var at by remember { mutableStateOf<Offset?>(null) }
    val press by androidx.compose.animation.core.animateFloatAsState(if (pressed) 1f else 0f, me.kavishdevar.librepods.presentation.glint.GlassPress.spec(pressed), label = "chipPress")
    val m = morph.coerceAtLeast(0f)
    // Words show once the bubble has mostly become a chip.
    val inside = ((m - 0.45f) / 0.55f).coerceIn(0f, 1f)
    // Only "starting" and "linking" move on their own: something is really happening then.
    val busy = (view.kind == HeartView.Kind.Starting || (view.kind == HeartView.Kind.Blocked && view.tap == HeartView.Tap.None)) && !reduceMotion
    val spin = if (busy) {
        androidx.compose.animation.core.rememberInfiniteTransition(label = "heartBusy")
            .animateFloat(0f, 1f, androidx.compose.animation.core.infiniteRepeatable(tween(1100, easing = androidx.compose.animation.core.LinearEasing)), label = "orbit")
    } else remember { mutableFloatStateOf(0f) }
    val spoken = when (view.kind) {
        HeartView.Kind.Live -> "Heart rate ${view.bpm} beats per minute. Tap for what it means"
        HeartView.Kind.Resting -> "Last heart rate ${view.bpm}, resting the sensor to save battery. Tap for what it means"
        HeartView.Kind.Off -> "Heart rate not measuring. Tap to measure"
        else -> "Heart rate: ${view.short}. ${view.line}"
    }
    Row(
        Modifier
            .graphicsLayer {
                // Glides from the button (its left edge at fromX) to the start of the row; the
                // spring's small overshoot is kept, so it settles like it has weight.
                val far = 1f - m
                translationX = far * (fromX - bud * 14.dp.toPx())
                val sc = (1f + 0.04f * press) * (0.55f + 0.45f * bud)
                scaleX = sc; scaleY = sc
                alpha = bud
                clip = true
                shape = androidx.compose.foundation.shape.RoundedCornerShape(percent = 50)
            }
            // Drawn and touched at the growing size, not the full one.
            .drawBehind {
                drawGlassCapsule(dark)
                with(me.kavishdevar.librepods.presentation.glint.GlassPress) { drawFingerGlow(at, press.coerceIn(0f, 1f), dark) }
            }
            .islandPress(enabled, spoken, { currentOnClick() }, { pressed = it }, { at = it })
            .layout { measurable, constraints ->
                // Measured at its full size, shown at a width growing from a circle the size of
                // the play/pause button (40) to the full chip, and a height easing from 40 to 34.
                val full = measurable.measure(constraints.copy(minWidth = 0))
                val ball = 40.dp.roundToPx()
                val w = lerp(ball.toFloat(), full.width.toFloat(), m.coerceIn(0f, 1f)).roundToInt()
                val h = lerp(ball.toFloat(), 34.dp.toPx(), m.coerceIn(0f, 1f)).roundToInt()
                layout(w, h) { full.placeRelative(0, (h - full.height) / 2) }
            }
            .height(34.dp)
            .padding(start = 11.dp, end = 13.dp)
            .graphicsLayer { alpha = inside }
            .animateContentSize(if (reduceMotion) tween(0) else spring(dampingRatio = 0.85f, stiffness = 500f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(
            Modifier
                .size(14.dp)
                .graphicsLayer { scaleX = beat.value; scaleY = beat.value }
                .drawWithCache {
                    val heart = heartPath(size)
                    val stroke = androidx.compose.ui.graphics.drawscope.Stroke(1.4.dp.toPx(), join = androidx.compose.ui.graphics.StrokeJoin.Round)
                    val dashed = androidx.compose.ui.graphics.drawscope.Stroke(
                        1.4.dp.toPx(), cap = StrokeCap.Round,
                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(2.2.dp.toPx(), 2.dp.toPx())),
                    )
                    onDrawBehind {
                        when (view.kind) {
                            HeartView.Kind.Live -> drawPath(heart, content.copy(alpha = 0.92f))
                            HeartView.Kind.Resting -> drawPath(heart, content.copy(alpha = 0.42f))
                            HeartView.Kind.NoSignal -> drawPath(heart, secondary, style = dashed)
                            HeartView.Kind.Blocked -> {
                                drawPath(heart, secondary, style = stroke)
                                if (view.tap != HeartView.Tap.None) drawLine(
                                    secondary, Offset(size.width * 0.05f, size.height * 0.95f), Offset(size.width * 0.95f, size.height * 0.05f),
                                    1.5.dp.toPx(), StrokeCap.Round,
                                )
                            }
                            else -> drawPath(heart, if (view.kind == HeartView.Kind.Off) content.copy(alpha = 0.75f) else secondary, style = stroke)
                        }
                        if (busy) {
                            // A small dot circling the heart while the sensor (or link) starts.
                            val a = spin.value * 2f * Math.PI.toFloat()
                            val r = size.minDimension * 0.62f
                            drawCircle(content, 1.6.dp.toPx(), Offset(center.x + kotlin.math.cos(a) * r, center.y + kotlin.math.sin(a) * r))
                        }
                    }
                }
        )
        Spacer(Modifier.width(7.dp))
        androidx.compose.animation.AnimatedContent(
            targetState = view.kind to (view.bpm ?: 0),
            transitionSpec = {
                val (fromKind, from) = initialState
                val (toKind, to) = targetState
                val up = to > from
                val spec = if (reduceMotion) tween<androidx.compose.ui.unit.IntOffset>(0) else spring(0.86f, 500f)
                if (fromKind == toKind) {
                    (androidx.compose.animation.slideInVertically(spec) { if (up) it / 2 else -it / 2 } + androidx.compose.animation.fadeIn(tween(160))) togetherWith
                        (androidx.compose.animation.slideOutVertically(spec) { if (up) -it / 2 else it / 2 } + androidx.compose.animation.fadeOut(tween(120)))
                } else {
                    androidx.compose.animation.fadeIn(tween(200, 60)) togetherWith androidx.compose.animation.fadeOut(tween(120))
                }
            },
            label = "chipWords",
        ) { (kind, value) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                when (kind) {
                    HeartView.Kind.Live, HeartView.Kind.Resting -> {
                        Text(
                            "$value",
                            style = TextStyle(fontFamily = glintFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = if (kind == HeartView.Kind.Live) content else secondary, fontFeatureSettings = "tnum")
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("BPM", style = TextStyle(fontFamily = glintFontFamily, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.4.sp, color = secondary))
                        if (kind == HeartView.Kind.Resting) {
                            // Resting between battery-saving bursts: a small moon.
                            Spacer(Modifier.width(5.dp))
                            androidx.compose.foundation.Canvas(Modifier.size(9.dp)) {
                                val moon = Path.combine(
                                    PathOperation.Difference,
                                    Path().apply { addOval(Rect(Offset.Zero, size)) },
                                    Path().apply { addOval(Rect(Offset(size.width * 0.38f, -size.height * 0.18f), size)) },
                                )
                                drawPath(moon, secondary)
                            }
                        }
                    }
                    else -> Text(
                        view.short,
                        maxLines = 1,
                        style = TextStyle(fontFamily = glintFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = if (kind == HeartView.Kind.Off) content else secondary)
                    )
                }
            }
        }
    }
}

/**
 * The heart chip widened into one plain line about what's wrong, with the one-tap fix (when
 * there is one). Tapping outside the button closes it.
 */
@Composable
private fun HeartNote(view: HeartView.View, content: Color, secondary: Color, dark: Boolean, reduceMotion: Boolean, onFix: () -> Unit, onClose: () -> Unit) {
    val fix = when (view.tap) {
        HeartView.Tap.Retry, HeartView.Tap.Reconnect -> "Try again"
        HeartView.Tap.Start -> "Measure"
        else -> null
    }
    var pressed by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(percent = 50))
            .drawBehind { drawGlassCapsule(dark) }
            .islandPress(true, view.line + ". Tap to close", onClose, { pressed = it })
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            view.line,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(fontFamily = glintFontFamily, fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.Medium, color = secondary),
        )
        if (fix != null) {
            Spacer(Modifier.width(6.dp))
            GlassPillButton(text = fix, textColor = content, dark = dark, height = 28.dp, fontSize = 12.sp, onClick = onFix)
        }
    }
}

/**
 * The island's small glass surface (play button, heart chip): a soft fill and a light rim that
 * fades downward from the top, like the island's own edge. Round ends at any width.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGlassCapsule(dark: Boolean) {
    val r = CornerRadius(size.height / 2f)
    drawRoundRect(if (dark) Color.White.copy(alpha = 0.13f) else Color.Black.copy(alpha = 0.06f), cornerRadius = r)
    val inset = 0.5.dp.toPx()
    drawRoundRect(
        androidx.compose.ui.graphics.Brush.verticalGradient(
            listOf(Color.White.copy(alpha = if (dark) 0.42f else 0.95f), Color.White.copy(alpha = if (dark) 0.05f else 0.35f))
        ),
        topLeft = Offset(inset, inset), size = Size(size.width - 2 * inset, size.height - 2 * inset),
        cornerRadius = CornerRadius(size.height / 2f - inset), style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx())
    )
    if (!dark) drawRoundRect(Color.Black.copy(alpha = 0.07f), cornerRadius = r, style = androidx.compose.ui.graphics.drawscope.Stroke(0.6.dp.toPx()))
}

/** The heart island's lines, with the live reading. */
internal fun heartIslandText(event: IslandEvent.Heart, bpm: Int?, age: Int = 0): Pair<String, String> = when {
    event.alert -> "High heart rate" to (bpm?.let { "$it BPM" } ?: "Above your limit")
    bpm == null -> "Heart rate" to "Measuring…"
    else -> "Heart rate" to "$bpm BPM · ${HeartInsights.meaning(bpm, age, 0).headline}"
}

internal fun islandText(event: IslandEvent, s: PodsSnapshot): Pair<String, String> = when (event) {
    IslandEvent.Connected -> s.name to "Connected"
    IslandEvent.InEar -> s.name to "In your ears"
    is IslandEvent.LowBattery -> "Low battery" to "${event.level}% left"
    is IslandEvent.ListeningMode -> listeningModeName(event.mode) to s.name
    is IslandEvent.MovedToDevice -> "Moved to ${event.deviceName}" to s.name
    IslandEvent.TakingOver -> "Switching to this phone" to s.name
    IslandEvent.Charging -> when {
        s.budsCharging -> "Charging" to listOfNotNull(s.budsLevel?.let { "AirPods $it%" }, s.case?.let { "Case $it%" }).joinToString(" · ").ifEmpty { s.name }
        else -> "Case charging" to (s.case?.let { "Case $it%" } ?: s.name)
    }
    is IslandEvent.Problem -> event.title to event.message
    is IslandEvent.Heart -> heartIslandText(event, null)
    is IslandEvent.BudOut -> (if (event.remaining == 0) "AirPods out" else "One AirPod out") to when {
        event.paused -> "Music paused"
        event.remaining == 1 -> "Still in one ear"
        else -> s.name
    }
    IslandEvent.BothIn -> "Both AirPods in" to s.name
    IslandEvent.Music -> "Now playing" to s.name
    IslandEvent.TapSetup -> "Tap the Dynamic Island" to "Turn it on in pro"
}

/** Clips expanded content to the island's current (growing) shape, anchored top-left. */
private class RevealShape(private val w: Float, private val h: Float, private val r: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Rounded(RoundRect(0f, 0f, w, h, CornerRadius(r)))
}
