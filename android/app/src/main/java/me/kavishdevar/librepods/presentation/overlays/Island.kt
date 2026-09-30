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
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
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

internal class IslandController(private val context: Context) {
    private val window = OverlayWindow(context, "GlintIsland", anchorTop = true)
    private val event = mutableStateOf<IslandEvent>(IslandEvent.Connected)
    private val phase = mutableStateOf(IslandPhase.Compact)
    private val generation = mutableIntStateOf(0)
    val isShowing: Boolean get() = window.isShowing

    fun show(e: IslandEvent) {
        event.value = e
        generation.intValue++
        val wantsExpanded = (e is IslandEvent.MovedToDevice && e.canTakeBack) || e is IslandEvent.Problem
        if (window.isShowing) {
            phase.value = if (wantsExpanded) IslandPhase.Expanded else
                if (phase.value == IslandPhase.Leaving) IslandPhase.Compact else phase.value
            return
        }
        phase.value = if (wantsExpanded) IslandPhase.Expanded else IslandPhase.Compact
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
    val compactH = dp(40f)
    val compactMainW = dp(214f)
    val satD = dp(40f)
    val satGap = dp(9f)
    val expandedW = minOf(screen.width - dp(20f), dp(430f))
    val expandedH = dp(214f)
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
    val haptics = remember(view) { GlintHaptics(view) }
    val snapshot by GlintOverlays.snapshot.collectAsState()
    val reduceMotion = remember { GlintComfort.reduceMotion(context) }
    val reduceTransparency = remember { GlintComfort.reduceTransparency(context) }
    val light by rememberTiltLight(GlintComfort.tiltLight(context))
    val look = remember { GlassLooks.island(density) }

    val useBlur = blurAllowed && !reduceTransparency
    val mainBlur = remember(useBlur) { if (useBlur) SystemBlur.create(view) else null }
    val satBlur = remember(useBlur) { if (useBlur) SystemBlur.create(view) else null }
    DisposableEffect(mainBlur, satBlur) { onDispose { mainBlur?.hide(); satBlur?.hide() } }

    val appear = remember { Animatable(0f) }
    val expand = remember { Animatable(if (phase == IslandPhase.Expanded) 0f else 0f) }
    val split = remember { Animatable(0f) }
    var press by remember { mutableFloatStateOf(0f) }
    var touch by remember { mutableStateOf<Offset?>(null) }
    var dragY by remember { mutableFloatStateOf(0f) }
    val currentPhase by rememberUpdatedState(phase)

    val morph = if (reduceMotion) tween<Float>(160) else spring(dampingRatio = 0.72f, stiffness = 340f)
    val soft = if (reduceMotion) tween<Float>(160) else spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)

    LaunchedEffect(phase, generation) {
        when (phase) {
            IslandPhase.Compact -> {
                coroutineScope {
                    if (appear.value < 0.01f) haptics.appear()
                    launch { expand.animateTo(0f, morph) }
                    launch { appear.animateTo(1f, morph) }
                    launch { delay(if (reduceMotion) 0 else 110); split.animateTo(1f, soft) }
                }
                onWindowSize(geometry.compactWindow)
                val hold = if (event is IslandEvent.LowBattery) 5_000L else 3_600L
                delay(hold)
                onPhase(IslandPhase.Leaving)
            }
            IslandPhase.Expanded -> {
                onWindowSize(geometry.expandedWindow)
                if (appear.value < 0.01f) haptics.appear() else haptics.expand()
                coroutineScope {
                    launch { appear.animateTo(1f, morph) }
                    launch { split.animateTo(0f, soft) }
                    launch { expand.animateTo(1f, morph) }
                }
                delay(8_000)
                onPhase(IslandPhase.Leaving)
            }
            IslandPhase.Leaving -> {
                coroutineScope {
                    launch { split.animateTo(0f, soft) }
                    launch { expand.animateTo(0f, morph) }
                    launch { delay(if (reduceMotion) 0 else 90); appear.animateTo(0f, if (reduceMotion) tween(160) else spring(1f, 500f)) }
                }
                onGone()
            }
        }
    }

    val satelliteContent: @Composable () -> Unit = {
        when (event) {
            is IslandEvent.ListeningMode -> ListeningModeGlyph(event.mode, look.content, size = 22.dp)
            is IslandEvent.MovedToDevice, IslandEvent.TakingOver -> Text("⇄", style = TextStyle(color = look.content, fontSize = 17.sp, fontFamily = glintFontFamily))
            is IslandEvent.Problem -> Text("!", style = TextStyle(color = GlintColors.Amber, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = glintFontFamily))
            is IslandEvent.Charging -> BatteryRing(snapshot.case, true, size = 30.dp, stroke = 3.dp, track = Color(0x33FFFFFF), label = look.content, labelSize = 10.sp)
            is IslandEvent.LowBattery -> BatteryRing(event.level, false, size = 30.dp, stroke = 3.dp, track = Color(0x33FFFFFF), label = look.content, labelSize = 10.sp)
            else -> BatteryRing(snapshot.budsLevel, snapshot.budsCharging, size = 30.dp, stroke = 3.dp, track = Color(0x33FFFFFF), label = look.content, labelSize = 10.sp)
        }
    }
    val (title, subtitle) = islandText(event, snapshot)

    // Geometry for the current frame, shared by drawing and layout. Reading animatable
    // values here happens in the draw/layout phase, so animating never recomposes.
    fun frame(windowWidth: Float): IslandFrame {
        val a = appear.value
        val e = expand.value
        val s = split.value
        val mainW0 = lerp(geometry.tiny, geometry.compactMainW, a)
        val mainH0 = lerp(geometry.tiny, geometry.compactH, a)
        val w = lerp(mainW0, geometry.expandedW, e)
        val h = lerp(mainH0, geometry.expandedH, e)
        val radius = lerp(h / 2f, geometry.expandedRadius, e).coerceAtMost(h / 2f)
        val satR = geometry.satD / 2f * a * (1f - e)
        // Satellite travels from tucked inside the pill's right cap to a small gap beside it.
        val tucked = -geometry.satD
        val protrude = lerp(tucked, geometry.satGap + geometry.satD, s) * (1f - e)
        val groupW = w + max(0f, protrude)
        val left = (windowWidth - groupW) / 2f
        val top = geometry.margin + dragY.coerceAtMost(0f) * 0.35f
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
                    touch = down.position
                    press = 1f
                    var totalY = 0f
                    var moved = false
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull() ?: break
                        if (!ch.pressed) break
                        val dy = ch.positionChange().y
                        totalY += dy
                        if (kotlin.math.abs(totalY) > 8f * density) moved = true
                        dragY = totalY
                        touch = ch.position
                    }
                    press = 0f
                    touch = null
                    if (dragY < -28f * density) {
                        haptics.dismiss()
                        onPhase(IslandPhase.Leaving)
                    } else if (!moved && currentPhase != IslandPhase.Leaving) {
                        onPhase(if (currentPhase == IslandPhase.Expanded) IslandPhase.Compact else IslandPhase.Expanded)
                    }
                    dragY = 0f
                }
            }
            .semantics {
                role = Role.Button
                contentDescription = "$title. $subtitle"
                onClick(label = if (phase == IslandPhase.Expanded) "Collapse" else "Expand") {
                    onPhase(if (phase == IslandPhase.Expanded) IslandPhase.Compact else IslandPhase.Expanded); true
                }
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
                    IslandPods(budsWidth = 42.dp, play = phase != IslandPhase.Expanded)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = TextStyle(fontFamily = glintFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = look.content)
                        )
                        Text(
                            subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = TextStyle(fontFamily = glintFontFamily, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = look.contentSecondary)
                        )
                    }
                }
            },
            satellite = satelliteContent,
            expanded = {
                ExpandedIslandContent(event, snapshot, title, subtitle, look.content, look.contentSecondary, active = phase == IslandPhase.Expanded) {
                    haptics.expand()
                    if (event is IslandEvent.MovedToDevice && event.canTakeBack) GlintOverlays.takeBackHandler?.invoke()
                    onPhase(IslandPhase.Leaving)
                }
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
        val c = compactM.map { it.measure(Constraints(maxWidth = (f.main.width - f.main.height * 0.9f).roundToInt().coerceAtLeast(0), maxHeight = h)) }
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
                    alpha = (f.split * 1.3f - 0.3f).coerceIn(0f, 1f) * (1f - f.expand * 3f).coerceAtLeast(0f)
                    val sc = 0.6f + 0.4f * f.split
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
 * LibrePods' turning-AirPods clip, keyed off its black background so the buds float on the
 * glass. The buds fill 88% x 53% of the clip's square frame, so the frame is sized from the
 * width the buds should take and allowed to overhang its slot (the overhang is transparent).
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
    title: String,
    subtitle: String,
    content: Color,
    secondary: Color,
    active: Boolean,
    onAction: () -> Unit,
) {
    val hasAction = (event is IslandEvent.MovedToDevice && event.canTakeBack) || event is IslandEvent.Problem
    Column(Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontFamily = glintFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = content))
                Text(subtitle, maxLines = 2, overflow = TextOverflow.Ellipsis, style = TextStyle(fontFamily = glintFontFamily, fontSize = 12.sp, color = secondary))
            }
            if (event is IslandEvent.ListeningMode) ListeningModeGlyph(event.mode, content, size = 26.dp)
        }
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            IslandPods(budsWidth = if (hasAction) 106.dp else 124.dp, play = active)
            Spacer(Modifier.weight(1f))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.End) {
                RingRow("L", snapshot.left, snapshot.leftCharging, content, secondary)
                RingRow("R", snapshot.right, snapshot.rightCharging, content, secondary)
                RingRow("Case", snapshot.case, snapshot.caseCharging, content, secondary)
            }
        }
        if (hasAction) {
            GlassPillButton(
                text = if (event is IslandEvent.Problem) "Dismiss" else "Use on this phone",
                textColor = content,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                onClick = onAction,
            )
        }
    }
}

@Composable
private fun RingRow(label: String, level: Int?, charging: Boolean, content: Color, secondary: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = TextStyle(fontFamily = glintFontFamily, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = secondary))
        Text(
            if (level != null) "$level%" else "–",
            style = TextStyle(fontFamily = glintFontFamily, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = content)
        )
        BatteryRing(level, charging, size = 16.dp, stroke = 2.5.dp, track = Color(0x33FFFFFF), showLabel = false)
    }
}

internal fun islandText(event: IslandEvent, s: PodsSnapshot): Pair<String, String> = when (event) {
    IslandEvent.Connected -> s.name to "Connected"
    IslandEvent.InEar -> s.name to "In your ears"
    is IslandEvent.LowBattery -> "Low battery" to "${s.name} · ${event.level}%"
    is IslandEvent.ListeningMode -> listeningModeName(event.mode) to s.name
    is IslandEvent.MovedToDevice -> "Moved to ${event.deviceName}" to s.name
    IslandEvent.TakingOver -> "Switching to this phone" to s.name
    IslandEvent.Charging -> "Charging" to (s.case?.let { "Case $it%" } ?: s.name)
    is IslandEvent.Problem -> event.title to event.message
}

/** Clips expanded content to the island's current (growing) shape, anchored top-left. */
private class RevealShape(private val w: Float, private val h: Float, private val r: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Rounded(RoundRect(0f, 0f, w, h, CornerRadius(r)))
}
