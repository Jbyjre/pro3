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

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import me.kavishdevar.librepods.services.GlintStatus
import me.kavishdevar.librepods.services.IslandPrefs
import me.kavishdevar.librepods.services.NowPlaying
import me.kavishdevar.librepods.presentation.glint.heartPath
import me.kavishdevar.librepods.presentation.glint.rememberHeartBeat
import androidx.compose.animation.togetherWith
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.drawText
import me.kavishdevar.librepods.services.HeartRate
import me.kavishdevar.librepods.services.HeartInsights
import me.kavishdevar.librepods.presentation.glint.HeartGlyph
import me.kavishdevar.librepods.services.LinkState
import me.kavishdevar.librepods.services.BatteryTimeLeft
import me.kavishdevar.librepods.services.BatteryWords
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
import kotlinx.coroutines.coroutineScope
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

private const val LONG_PRESS_MS = 450L

internal class IslandController(private val context: Context) {
    private val window = OverlayWindow(context, "GlintIsland", anchorTop = true)
    private val event = mutableStateOf<IslandEvent>(IslandEvent.Connected)
    private val phase = mutableStateOf(IslandPhase.Compact)
    private val generation = mutableIntStateOf(0)
    val isShowing: Boolean get() = window.isShowing

    private var shownAt = 0L

    fun show(e: IslandEvent) {
        event.value = e
        generation.intValue++
        val wantsExpanded = (e is IslandEvent.MovedToDevice && e.canTakeBack) || e is IslandEvent.Problem
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
        val geo = IslandGeometry(context)
        window.show(if (wantsExpanded) geo.expandedWindow else geo.compactWindow, geo.windowTop) {
            IslandHost(
                geometry = geo,
                event = event.value,
                phase = phase.value,
                generation = generation.intValue,
                blurAllowed = window.blurAllowed.value,
                onPhase = { phase.value = it },
                onWindowSize = { window.resize(it) },
                onGone = { window.dismiss() },
            )
        }
    }

    fun dismiss(animated: Boolean) {
        if (!window.isShowing) return
        if (animated) phase.value = IslandPhase.Leaving else window.dismiss()
    }
}

/** All island sizes in pixels, computed once per show from the screen and density. */
internal class IslandGeometry(context: Context) {
    private val density = context.resources.displayMetrics.density
    private fun dp(v: Float) = v * density
    private val screen = GlintOverlays.screenSize(context)
    val margin = dp(22f)
    val shadowDrop = dp(16f)
    val tiny = dp(34f)
    val compactH = dp(44f)
    // Wide enough for the buds, two readable lines and the bubble inside one pill.
    val compactMainW = dp(268f)
    val satD = dp(42f)
    val satGap = dp(9f)
    /**
     * Where the battery ring rests: inside the pill's right cap, so the compact island is one
     * continuous capsule (like the Dynamic Island) rather than a pill plus a separate bubble.
     * 0 = tucked deep inside, 1 = fully budded off beside the pill.
     */
    val restSplit = satD / (satGap + 2f * satD)
    val expandedW = minOf(screen.width - dp(20f), dp(430f))
    // Header, AirPods and batteries (with the heart while measuring), music, time left.
    val expandedH = dp(300f)
    val expandedRadius = dp(44f)
    val windowTop = GlintOverlays.statusBarHeight(context) + dp(6f).roundToInt() - margin.roundToInt()
    val compactWindow = IntSize(
        (compactMainW + satGap + satD + margin * 2).roundToInt(),
        (compactH + margin * 2 + shadowDrop).roundToInt()
    )
    val expandedWindow = IntSize(
        (expandedW + margin * 2).roundToInt(),
        (expandedH + margin * 2 + shadowDrop).roundToInt()
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
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    val look = remember(dark) { GlassLooks.island(density, dark) }
    val ringTrack = if (dark) Color(0x33FFFFFF) else Color(0x1F000000)

    val useBlur = blurAllowed && !reduceTransparency
    val mainBlur = remember(useBlur) { if (useBlur) SystemBlur.create(view) else null }
    val satBlur = remember(useBlur) { if (useBlur) SystemBlur.create(view) else null }
    DisposableEffect(mainBlur, satBlur) { onDispose { mainBlur?.hide(); satBlur?.hide() } }

    val appear = remember { Animatable(0f) }
    // Width and height morph on slightly different springs (width leads, height follows with
    // a little more give), so growing and shrinking reads as one liquid drop, not a box scaling.
    val expand = remember { Animatable(0f) }
    val expandH = remember { Animatable(0f) }
    val split = remember { Animatable(0f) }
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
            is IslandEvent.Charging -> BatteryRing(if (snapshot.budsCharging) snapshot.budsLevel else snapshot.case, true, size = 30.dp, stroke = 3.dp, track = ringTrack, label = look.content, labelSize = 10.sp)
            is IslandEvent.LowBattery -> BatteryRing(event.level, false, size = 30.dp, stroke = 3.dp, track = ringTrack, label = look.content, labelSize = 10.sp)
            is IslandEvent.Heart -> {
                val hr by HeartRate.state.collectAsState()
                HeartGlyph(hr.bpm, if (dark) Color(0xFFF04A50) else Color(0xFFE0303A), size = 20.dp, reduceMotion = reduceMotion)
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

    // Geometry for the current frame, shared by drawing and layout. Reading animatable
    // values here happens in the draw/layout phase, so animating never recomposes.
    fun frame(windowWidth: Float): IslandFrame {
        val a = appear.value
        val e = expand.value
        val eh = expandH.value
        val s = split.value
        val mainW0 = lerp(geometry.tiny, geometry.compactMainW, a)
        val mainH0 = lerp(geometry.tiny, geometry.compactH, a)
        val sq = squish.value
        val p = pull.value
        // Pulling down stretches the glass (with resistance), keeping its volume roughly constant.
        val stretch = (p.coerceAtLeast(0f) * 0.22f).coerceAtMost(26f * density)
        val w = lerp(mainW0, geometry.expandedW, e) * sq - stretch * 0.35f
        val h = (lerp(mainH0, geometry.expandedH, eh) * sq + stretch).coerceAtLeast(1f)
        val radius = lerp(h / 2f, geometry.expandedRadius, e).coerceAtMost(h / 2f)
        val satR = geometry.satD / 2f * a * (1f - e)
        // Satellite travels from tucked inside the pill's right cap to a small gap beside it.
        val tucked = -geometry.satD
        val protrude = lerp(tucked, geometry.satGap + geometry.satD, s) * (1f - e)
        val groupW = w + max(0f, protrude)
        val left = (windowWidth - groupW) / 2f
        val top = geometry.margin + p.coerceAtMost(0f) * 0.35f + (1f - sq) * lerp(geometry.compactH, geometry.expandedH, eh) / 2f
        val main = Rect(left, top, left + w, top + h)
        val satCenter = Offset(main.right - geometry.satD / 2f + protrude, main.top + minOf(h, geometry.compactH) / 2f)
        return IslandFrame(main, radius, satCenter, satR, a, e, s)
    }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
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
                onLongClick(label = "Open Glint") { GlintOverlays.openApp(context); onPhase(IslandPhase.Leaving); true }
            }
            .drawBehind {
                val f = frame(size.width)
                if (f.appear <= 0.001f) return@drawBehind
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
                )
            }
    ) {
        // Compact content: tiny buds on the left, title/subtitle; satellite glyph on the right.
        IslandLayout(
            expandedSize = IntSize(geometry.expandedW.roundToInt(), geometry.expandedH.roundToInt()),
            frameProvider = { frame(it) },
            compact = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val art = playingNow.art
                    if (event == IslandEvent.Music && art != null) {
                        Box(Modifier.width(42.dp), contentAlignment = Alignment.Center) { CoverArt(art, 30.dp, 8.dp) }
                    } else {
                        IslandPods(budsWidth = 42.dp, play = phase != IslandPhase.Expanded)
                    }
                    Spacer(Modifier.width(9.dp))
                    Column {
                        Text(
                            title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = TextStyle(fontFamily = glintFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 17.sp, color = look.content)
                        )
                        Text(
                            subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = TextStyle(fontFamily = glintFontFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 15.sp, color = look.contentSecondary)
                        )
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
    expandedSize: IntSize,
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
        val e = expM.map { it.measure(Constraints.fixed(expandedSize.width, expandedSize.height)) }
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
                    alpha = ((f.expand - 0.45f) / 0.55f).coerceIn(0f, 1f)
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
    onTouch: () -> Unit,
    onAction: () -> Unit,
) {
    val link by GlintStatus.link.collectAsState()
    val timeLeft by BatteryTimeLeft.estimate.collectAsState()
    val heart by HeartRate.state.collectAsState()
    var heartOpen by remember { mutableStateOf(false) }
    val heartBpm = heart.bpm.takeIf { heartShowsOnIsland(heart, System.currentTimeMillis()) }
    // A reading that stops while the explanation is open closes it.
    LaunchedEffect(heartBpm == null) { if (heartBpm == null) heartOpen = false }
    // Collapsing the island returns to the main view next time.
    LaunchedEffect(active) { if (!active) heartOpen = false }
    val actionText = when {
        event is IslandEvent.MovedToDevice && event.canTakeBack -> "Use here"
        event is IslandEvent.Problem -> "Dismiss"
        else -> null
    }
    val track2 = if (dark) Color(0x33FFFFFF) else Color(0x1F000000)
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
                onBack = { onTouch(); heartOpen = false },
            )
            return@AnimatedContent
        }
        Column(Modifier.fillMaxSize().padding(start = 22.dp, end = 20.dp, top = 18.dp, bottom = 14.dp)) {
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
                IslandPods(budsWidth = if (heartBpm != null) 96.dp else 116.dp, play = active)
                Spacer(Modifier.weight(1f))
                if (heartBpm != null) {
                    HeartBadge(
                        bpm = heartBpm, size = 66.dp, dark = dark, reduceMotion = reduceMotion, enabled = active,
                        onClick = { onTouch(); heartOpen = true },
                    )
                    Spacer(Modifier.weight(1f))
                }
                Column(verticalArrangement = Arrangement.spacedBy(7.dp), horizontalAlignment = Alignment.End) {
                    RingRow("Left", snapshot.left, snapshot.leftCharging, content, secondary, track2)
                    RingRow("Right", snapshot.right, snapshot.rightCharging, content, secondary, track2)
                    RingRow("Case", snapshot.case, snapshot.caseCharging, content, secondary, track2)
                }
            }
            MediaRow(track, content, secondary, dark, reduceMotion, active, onTouch)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().height(32.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    timeLeft?.let { BatteryWords.headline(it) } ?: "Hold to open Glint",
                    modifier = Modifier.weight(1f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = TextStyle(fontFamily = glintFontFamily, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = secondary)
                )
                if (actionText != null) {
                    GlassPillButton(text = actionText, textColor = content, dark = dark, height = 32.dp, fontSize = 14.sp, onClick = onAction)
                }
            }
        }
    }
}

/** The heart shows while a reading is current: measuring now, or resting between bursts. */
internal fun heartShowsOnIsland(state: HeartRate.State, now: Long): Boolean =
    state.bpm != null && state.status != HeartRate.Status.Off && state.status != HeartRate.Status.NotConnected &&
        now - state.lastReadingMs < 10 * 60_000L

/** Song, artist and app with previous / play-pause / next, on a soft inner panel. */
@Composable
private fun MediaRow(track: NowPlaying.Track, content: Color, secondary: Color, dark: Boolean, reduceMotion: Boolean, active: Boolean, onTouch: () -> Unit) {
    val context = LocalContext.current
    val (line1, line2) = NowPlaying.words(track)
    Row(
        Modifier
            .fillMaxWidth()
            .height(58.dp)
            .background(if (dark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f), androidx.compose.foundation.shape.RoundedCornerShape(20.dp))
            .padding(start = 9.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val art = track.art
        if (art != null) CoverArt(art, 40.dp, 10.dp) else MusicNote(content.copy(alpha = 0.75f), 40.dp, dark)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(line1, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontFamily = glintFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 17.sp, color = content))
            if (line2.isNotEmpty()) Text(line2, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontFamily = glintFontFamily, fontSize = 12.sp, lineHeight = 15.sp, color = secondary))
        }
        SkipButton(forward = false, color = content, enabled = active, onClick = { onTouch(); NowPlaying.previous(context) })
        PlayPauseButton(
            playing = track.playing, color = content, size = 44.dp, glyph = 20.dp, reduceMotion = reduceMotion, enabled = active,
            onClick = { onTouch(); NowPlaying.playPause(context) },
        )
        SkipButton(forward = true, color = content, enabled = active, onClick = { onTouch(); NowPlaying.next(context) })
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

/** A soft tile with a music note, where the cover goes when there isn't one. */
@Composable
private fun MusicNote(color: Color, size: Dp, dark: Boolean) {
    androidx.compose.foundation.Canvas(
        Modifier.size(size).background(if (dark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.06f), androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
    ) {
        val w = this.size.width
        val stem = androidx.compose.ui.graphics.drawscope.Stroke(w * 0.055f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        // Two joined eighth notes.
        drawLine(color, Offset(w * 0.40f, w * 0.66f), Offset(w * 0.40f, w * 0.30f), stem.width, androidx.compose.ui.graphics.StrokeCap.Round)
        drawLine(color, Offset(w * 0.68f, w * 0.60f), Offset(w * 0.68f, w * 0.24f), stem.width, androidx.compose.ui.graphics.StrokeCap.Round)
        drawLine(color, Offset(w * 0.40f, w * 0.30f), Offset(w * 0.68f, w * 0.24f), w * 0.09f, androidx.compose.ui.graphics.StrokeCap.Round)
        drawOval(color, Offset(w * 0.25f, w * 0.60f), Size(w * 0.17f, w * 0.13f))
        drawOval(color, Offset(w * 0.53f, w * 0.54f), Size(w * 0.17f, w * 0.13f))
    }
}

/**
 * Play and pause as one shape that morphs: the two pause bars slide into the two halves of
 * the play triangle (and back). A press squishes it; the release fires [onClick].
 */
@Composable
internal fun PlayPauseButton(playing: Boolean, color: Color, size: Dp, glyph: Dp, reduceMotion: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val morphTo by androidx.compose.animation.core.animateFloatAsState(
        if (playing) 0f else 1f, if (reduceMotion) tween(0) else spring(0.7f, 520f), label = "playPause"
    )
    PressableGlyph(size = size, description = if (playing) "Pause" else "Play", onClick = onClick, enabled = enabled) {
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

/** Two rounded triangles: next (forward) or previous. */
@Composable
private fun SkipButton(forward: Boolean, color: Color, enabled: Boolean, onClick: () -> Unit) {
    PressableGlyph(size = 38.dp, description = if (forward) "Next" else "Previous", onClick = onClick, enabled = enabled) {
        val g = 17.dp.toPx()
        val o = Offset((this.size.width - g) / 2f, (this.size.height - g) / 2f)
        fun x(v: Float) = o.x + (if (forward) v else 1f - v) * g
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(g * 0.07f, join = androidx.compose.ui.graphics.StrokeJoin.Round)
        for (start in listOf(0.04f, 0.50f)) {
            val tri = Path().apply {
                moveTo(x(start), o.y + g * 0.20f); lineTo(x(start + 0.46f), o.y + g * 0.50f); lineTo(x(start), o.y + g * 0.80f); close()
            }
            drawPath(tri, color)
            drawPath(tri, color, style = stroke)
        }
    }
}

/** A round touch target that squishes when pressed, consumes its touch, and draws [draw]. */
@Composable
private fun PressableGlyph(size: Dp, description: String, onClick: () -> Unit, enabled: Boolean = true, draw: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit) {
    val currentOnClick by rememberUpdatedState(onClick)
    var pressed by remember { mutableStateOf(false) }
    val scale by androidx.compose.animation.core.animateFloatAsState(if (pressed) 0.84f else 1f, spring(0.5f, 700f), label = "press")
    androidx.compose.foundation.Canvas(
        Modifier
            .size(size)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(if (!enabled) Modifier else Modifier.semantics {
                role = Role.Button
                contentDescription = description
                onClick { currentOnClick(); true }
            })
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    // Consumed, so the island behind doesn't also treat this as a tap or a hold.
                    awaitFirstDown().consume()
                    pressed = true
                    var inside = true
                    while (true) {
                        val ch = awaitPointerEvent().changes.firstOrNull() ?: break
                        inside = ch.position.x in 0f..this.size.width.toFloat() && ch.position.y in 0f..this.size.height.toFloat()
                        ch.consume()
                        if (!ch.pressed) break
                    }
                    pressed = false
                    if (inside) currentOnClick()
                }
            }
    ) {
        if (pressed) drawCircle(Color.Gray.copy(alpha = 0.18f))
        draw()
    }
}

/** A heart with the live number inside it, beating at that rate. Tap it for what it means. */
@Composable
private fun HeartBadge(bpm: Int, size: Dp, dark: Boolean, reduceMotion: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val beat = rememberHeartBeat(bpm, reduceMotion, peak = 1.07f)
    val red = if (dark) Color(0xFFF04A50) else Color(0xFFE0303A)
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        PressableGlyph(size = size, description = "Heart rate $bpm beats per minute. Tap for what it means", onClick = onClick, enabled = enabled) {
            val s = this.size
            scale(beat.value, beat.value) {
                val heart = heartPath(s)
                drawPath(heart, androidx.compose.ui.graphics.Brush.verticalGradient(listOf(red.copy(alpha = 0.92f), red), 0f, s.height))
                // Glass sheen across the top lobes.
                clipPath(heart) {
                    drawOval(Color.White.copy(alpha = 0.28f), Offset(s.width * 0.10f, s.height * 0.06f), Size(s.width * 0.80f, s.height * 0.34f))
                }
                val number = measurer.measure(
                    bpm.toString(),
                    TextStyle(fontFamily = glintFontFamily, fontWeight = FontWeight.Bold, fontSize = (s.width * 0.30f / density).sp, color = Color.White)
                )
                drawText(number, topLeft = Offset((s.width - number.size.width) / 2f, s.height * 0.44f - number.size.height / 2f))
            }
        }
        Text("BPM", style = TextStyle(fontFamily = glintFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 10.sp, letterSpacing = 0.6.sp, color = red))
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
                style = TextStyle(fontFamily = glintFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = bandColor)
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            HeartBadge(bpm = bpm, size = 96.dp, dark = dark, reduceMotion = reduceMotion, onClick = onBack)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(meaning.headline, maxLines = 2, style = TextStyle(fontFamily = glintFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 22.sp, color = bandColor))
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
}

/** Alerts stay a little longer. */
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

@Composable
private fun RingRow(label: String, level: Int?, charging: Boolean, content: Color, secondary: Color, track: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(label, style = TextStyle(fontFamily = glintFontFamily, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = secondary))
        Text(
            if (level != null) "$level%" else "–",
            style = TextStyle(fontFamily = glintFontFamily, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = content)
        )
        BatteryRing(level, charging, size = 22.dp, stroke = 3.dp, track = track, showLabel = false)
    }
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
}

/** Clips expanded content to the island's current (growing) shape, anchored top-left. */
private class RevealShape(private val w: Float, private val h: Float, private val r: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Rounded(RoundRect(0f, 0f, w, h, CornerRadius(r)))
}
