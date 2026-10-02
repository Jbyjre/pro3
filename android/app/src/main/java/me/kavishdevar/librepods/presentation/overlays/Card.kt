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
import android.content.res.Configuration
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.Layout
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
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import me.kavishdevar.librepods.R
import me.kavishdevar.librepods.presentation.glint.BatteryRing
import me.kavishdevar.librepods.presentation.glint.GlassLook
import me.kavishdevar.librepods.presentation.glint.GlassLooks
import me.kavishdevar.librepods.presentation.glint.GlintComfort
import me.kavishdevar.librepods.presentation.glint.GlintHaptics
import me.kavishdevar.librepods.presentation.glint.PodsVideo
import me.kavishdevar.librepods.presentation.glint.PodsVideoConfig
import me.kavishdevar.librepods.presentation.glint.SystemBlur
import me.kavishdevar.librepods.presentation.glint.drawFloatingShadow
import me.kavishdevar.librepods.presentation.glint.drawGlass
import me.kavishdevar.librepods.presentation.glint.lerp
import me.kavishdevar.librepods.presentation.glint.rememberTiltLight
import me.kavishdevar.librepods.presentation.glint.roundRectPath
import me.kavishdevar.librepods.presentation.theme.glintFontFamily
import kotlin.math.roundToInt

internal class CardController(private val context: Context) {
    private val window = OverlayWindow(context, "GlintCard", anchorTop = false, fastFrames = true)
    private val leaving = mutableStateOf(false)
    private val generation = mutableIntStateOf(0)
    val isShowing: Boolean get() = window.isShowing

    fun show() {
        generation.intValue++
        leaving.value = false
        if (window.isShowing) return
        val geo = CardGeometry(context)
        window.show(geo.window, geo.windowBottom) {
            CardHost(
                geometry = geo,
                leaving = leaving.value,
                generation = generation.intValue,
                blurAllowed = window.blurAllowed.value,
                onLeave = { leaving.value = true },
                onGone = { window.dismiss() },
            )
        }
    }

    fun dismiss(animated: Boolean) {
        if (!window.isShowing) return
        if (animated) leaving.value = true else window.dismiss()
    }
}

internal class CardGeometry(context: Context) {
    private val density = context.resources.displayMetrics.density
    private fun dp(v: Float) = v * density
    private val screen = GlintOverlays.screenSize(context)
    val margin = dp(26f)
    val cardW = minOf(screen.width - dp(24f), dp(440f))
    val cardH = dp(376f)
    val cardRadius = dp(RADIUS_DP)
    val pillW = dp(156f)
    val pillH = dp(46f)
    val window = IntSize((cardW + margin * 2).roundToInt(), (cardH + margin * 2).roundToInt())
    val windowBottom = (GlintOverlays.navigationBarHeight(context) + dp(12f) - margin).roundToInt().coerceAtLeast(0)
    val rise = dp(90f)

    companion object {
        const val RADIUS_DP = 42f
    }
}

@Composable
internal fun CardHost(
    geometry: CardGeometry,
    leaving: Boolean,
    generation: Int,
    blurAllowed: Boolean,
    onLeave: () -> Unit,
    onGone: () -> Unit,
) = CappedFontScale { CardHostContent(geometry, leaving, generation, blurAllowed, onLeave, onGone) }

@Composable
private fun CardHostContent(
    geometry: CardGeometry,
    leaving: Boolean,
    generation: Int,
    blurAllowed: Boolean,
    onLeave: () -> Unit,
    onGone: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current.density
    val haptics = remember(view) { GlintHaptics(view) }
    val snapshot by GlintOverlays.snapshot.collectAsState()
    val dark = (LocalConfiguration.current.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    val look = remember(dark) { GlassLooks.card(dark, density) }
    val reduceMotion = remember { GlintComfort.reduceMotion(context) }
    val reduceTransparency = remember { GlintComfort.reduceTransparency(context) }
    val light by rememberTiltLight(GlintComfort.tiltLight(context))
    val blur = remember(blurAllowed, reduceTransparency) { if (blurAllowed && !reduceTransparency) SystemBlur.create(view) else null }
    DisposableEffect(blur) { onDispose { blur?.hide() } }

    val appear = remember { Animatable(0f) }
    val morph = remember { Animatable(0f) }
    var dragY by remember { mutableFloatStateOf(0f) }
    var touch by remember { mutableStateOf<Offset?>(null) }
    var press by remember { mutableFloatStateOf(0f) }
    var lastTouch by remember { mutableIntStateOf(0) }

    LaunchedEffect(leaving, generation, lastTouch) {
        if (!leaving) {
            coroutineScope {
                launch { appear.animateTo(1f, if (reduceMotion) tween(180) else spring(0.8f, 420f)) }
                launch {
                    if (morph.value < 0.5f) {
                        delay(if (reduceMotion) 0 else 70)
                        haptics.appear()
                    }
                    morph.animateTo(1f, if (reduceMotion) tween(200) else spring(0.74f, 300f))
                }
            }
            delay(9_000)
            onLeave()
        } else {
            coroutineScope {
                launch { morph.animateTo(0f, if (reduceMotion) tween(160) else spring(0.9f, 520f)) }
                launch {
                    delay(if (reduceMotion) 0 else 120)
                    appear.animateTo(0f, if (reduceMotion) tween(160) else spring(1f, 420f))
                }
            }
            onGone()
        }
    }

    fun frame(w: Float, h: Float): Pair<Rect, Float> {
        val m = morph.value
        val cw = lerp(geometry.pillW, geometry.cardW, m)
        val ch = lerp(geometry.pillH, geometry.cardH, m)
        val r = lerp(geometry.pillH / 2f, geometry.cardRadius, m).coerceAtMost(ch / 2f)
        val bottom = h - geometry.margin + (1f - appear.value) * geometry.rise + dragY.coerceAtLeast(0f) * 0.6f
        return Rect(w / 2f - cw / 2f, bottom - ch, w / 2f + cw / 2f, bottom) to r
    }

    val title = snapshot.name
    val subtitle = when {
        snapshot.lidOpen -> "Case open"
        snapshot.leftInEar || snapshot.rightInEar -> "In your ears"
        else -> "Connected"
    }

    Box(
        Modifier
            .fillMaxSize()
            .semantics { contentDescription = "$title, $subtitle"; role = Role.Button }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    touch = down.position
                    press = 1f
                    lastTouch++
                    var total = 0f
                    var handled = false
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull() ?: break
                        if (ch.isConsumed) handled = true
                        if (!ch.pressed) break
                        total += ch.positionChange().y
                        dragY = total
                        touch = ch.position
                    }
                    press = 0f
                    touch = null
                    if (total > 60f * density) {
                        haptics.dismiss()
                        onLeave()
                    } else if (!handled && kotlin.math.abs(total) < 8f * density) {
                        // A plain tap on the card opens Glint.
                        GlintOverlays.openApp(context)
                        onLeave()
                    }
                    dragY = 0f
                }
            }
            .drawBehind {
                if (appear.value <= 0.001f) return@drawBehind
                val (rect, r) = frame(size.width, size.height)
                drawFloatingShadow(rect, r, look.opaqueBottom, look.dark, appear.value.coerceIn(0f, 1f))
                drawGlass(
                    outline = roundRectPath(rect, r),
                    bounds = rect,
                    cornerRadius = r,
                    look = look,
                    light = light,
                    blur = blur,
                    touch = touch,
                    touchStrength = press * 0.6f,
                    alpha = appear.value.coerceIn(0f, 1f),
                )
            }
    ) {
        Layout(
            contents = listOf(
                { PillLabel(look) },
                {
                    CardContent(title, subtitle, snapshot, look) {
                        haptics.expand()
                        onLeave()
                    }
                },
            ),
            modifier = Modifier.fillMaxSize()
        ) { (pillM, cardM), constraints ->
            val w = constraints.maxWidth
            val h = constraints.maxHeight
            val (rect, r) = frame(w.toFloat(), h.toFloat())
            val p = pillM.map { it.measure(Constraints()) }
            val c = cardM.map { it.measure(Constraints.fixed(geometry.cardW.roundToInt(), geometry.cardH.roundToInt())) }
            layout(w, h) {
                val m = morph.value
                p.forEach {
                    it.placeWithLayer((rect.center.x - it.width / 2f).roundToInt(), (rect.center.y - it.height / 2f).roundToInt()) {
                        alpha = (1f - m * 4f).coerceIn(0f, 1f) * appear.value.coerceIn(0f, 1f)
                    }
                }
                c.forEach {
                    // Content is laid out at full size and revealed by the growing shape.
                    val x = (rect.center.x - geometry.cardW / 2f)
                    val y = rect.bottom - geometry.cardH
                    it.placeWithLayer(x.roundToInt(), y.roundToInt()) {
                        alpha = ((m - 0.4f) / 0.6f).coerceIn(0f, 1f)
                        clip = true
                        shape = CenteredReveal(rect.width, rect.height, r)
                    }
                }
            }
        }
    }
}

/** Clip to a width x height rounded rect centred horizontally and anchored to the bottom. */
private class CenteredReveal(private val w: Float, private val h: Float, private val r: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val left = (size.width - w) / 2f
        return Outline.Rounded(RoundRect(left, size.height - h, left + w, size.height, CornerRadius(r)))
    }
}

@Composable
private fun PillLabel(look: GlassLook) {
    Text(
        "AirPods",
        style = TextStyle(fontFamily = glintFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = look.content)
    )
}

@Composable
private fun CardContent(
    title: String,
    subtitle: String,
    s: PodsSnapshot,
    look: GlassLook,
    onDone: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // The 3D clip sits straight on the glass: its edges fade into the card (no inner
        // panel), and the card's own tint matches the clip's backdrop, so the card reads as
        // one continuous surface instead of a box inside a box.
        PodsVideo(
            video = R.raw.connected,
            poster = R.drawable.connected_poster,
            aspectRatio = PodsVideoConfig.CONNECTED_ASPECT,
            loopFromMs = PodsVideoConfig.CONNECTED_LOOP_FROM_MS,
            modifier = Modifier
                .padding(top = 18.dp)
                .fillMaxWidth()
                .featherEdges(),
        )
        Spacer(Modifier.height(14.dp))
        Text(
            title, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 22.dp),
            style = TextStyle(fontFamily = glintFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, letterSpacing = (-0.2).sp, color = look.content)
        )
        Text(
            subtitle, textAlign = TextAlign.Center,
            style = TextStyle(fontFamily = glintFontFamily, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = look.contentSecondary)
        )
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            RingLabel("Left", s.left, s.leftCharging, look)
            RingLabel("Right", s.right, s.rightCharging, look)
            RingLabel("Case", s.case, s.caseCharging, look)
        }
        Spacer(Modifier.weight(1f))
        GlassPillButton("Done", look.content, Modifier.fillMaxWidth().padding(horizontal = 20.dp), dark = look.dark, onClick = onDone)
    }
}


/**
 * Softly fades the clip's outer edges into the stage, hiding any one-level colour difference
 * a phone's video decoder might add at the frame border.
 */
private fun Modifier.featherEdges(): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val fx = size.width * 0.08f
        val fy = size.height * 0.10f
        drawRect(
            Brush.horizontalGradient(0f to Color.Transparent, fx / size.width to Color.Black, 1f - fx / size.width to Color.Black, 1f to Color.Transparent),
            blendMode = BlendMode.DstIn
        )
        drawRect(
            Brush.verticalGradient(0f to Color.Transparent, fy / size.height to Color.Black, 1f - fy / size.height to Color.Black, 1f to Color.Transparent),
            blendMode = BlendMode.DstIn
        )
    }

@Composable
private fun RingLabel(label: String, level: Int?, charging: Boolean, look: GlassLook) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        BatteryRing(
            level, charging, size = 34.dp, stroke = 3.5.dp,
            track = if (look.dark) Color(0x33FFFFFF) else Color(0x1F000000),
            label = look.content, labelSize = 11.sp
        )
        Spacer(Modifier.height(4.dp))
        Text(
            if (charging) "$label · charging" else label,
            style = TextStyle(fontFamily = glintFontFamily, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = look.contentSecondary)
        )
    }
}

/** A glass capsule button with a springy press and a touch glint. */
@Composable
fun GlassPillButton(
    text: String,
    textColor: Color,
    modifier: Modifier = Modifier,
    dark: Boolean = true,
    height: Dp = 48.dp,
    fontSize: TextUnit = 16.sp,
    onClick: () -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }
    val currentOnClick by rememberUpdatedState(onClick)
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, spring(0.55f, 600f), label = "press")
    Box(
        modifier
            .height(height)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .drawBehind {
                val r = size.height / 2f
                drawRoundRect(
                    if (dark) Color.White.copy(alpha = if (pressed) 0.24f else 0.16f)
                    else Color.White.copy(alpha = if (pressed) 0.55f else 0.85f),
                    cornerRadius = CornerRadius(r)
                )
                // iOS-style grey fill so the button still reads on white glass.
                if (!dark) drawRoundRect(Color(0xFF787880).copy(alpha = if (pressed) 0.24f else 0.14f), cornerRadius = CornerRadius(r))
                drawRoundRect(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = if (dark) 0.40f else 1f), Color.White.copy(alpha = if (dark) 0.06f else 0.4f))
                    ),
                    cornerRadius = CornerRadius(r),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx())
                )
                if (!dark) drawRoundRect(
                    Color.Black.copy(alpha = 0.08f),
                    cornerRadius = CornerRadius(r),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(0.6.dp.toPx())
                )
            }
            .semantics {
                role = Role.Button
                contentDescription = text
                onClick { currentOnClick(); true }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    pressed = true
                    var inside = true
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull() ?: break
                        val p = ch.position
                        inside = p.x in 0f..size.width.toFloat() && p.y in 0f..size.height.toFloat()
                        // Consume the release too, so the island/card behind doesn't also treat it as a tap.
                        ch.consume()
                        if (!ch.pressed) break
                    }
                    pressed = false
                    if (inside) currentOnClick()
                }
            }
            .padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = TextStyle(fontFamily = glintFontFamily, fontWeight = FontWeight.SemiBold, fontSize = fontSize, color = textColor)
        )
    }
}
