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
package me.kavishdevar.librepods.presentation.screens

import android.content.SharedPreferences
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.kavishdevar.librepods.presentation.components.LiquidSegments
import me.kavishdevar.librepods.presentation.glint.drawListeningMode
import me.kavishdevar.librepods.presentation.glint.heartPath
import me.kavishdevar.librepods.presentation.glint.pressable
import me.kavishdevar.librepods.presentation.overlays.GlassPillButton
import me.kavishdevar.librepods.presentation.overlays.GlintOverlays
import me.kavishdevar.librepods.presentation.overlays.MiniGeometry
import me.kavishdevar.librepods.presentation.overlays.MiniIslandHost
import me.kavishdevar.librepods.presentation.overlays.PodsSnapshot
import me.kavishdevar.librepods.presentation.overlays.drawKindGlyph
import me.kavishdevar.librepods.presentation.overlays.drawHomeGrid
import me.kavishdevar.librepods.presentation.overlays.drawPadlock
import me.kavishdevar.librepods.presentation.overlays.sampleIcon
import me.kavishdevar.librepods.presentation.theme.glintFontFamily
import me.kavishdevar.librepods.services.IslandGestures
import me.kavishdevar.librepods.services.IslandLook
import me.kavishdevar.librepods.services.MiniIslandRules
import me.kavishdevar.librepods.services.NowPlaying
import me.kavishdevar.librepods.services.SoundRules
import me.kavishdevar.librepods.services.SoundSource

/**
 * Settings > Islands: what the Dynamic Island shows and how it looks. A live preview (the real
 * Dynamic Island, with sample music and your AirPods), the situation to edit, what goes left and
 * right of the camera, size, width and glow, and a reset. Every choice is saved and applied at
 * once to the real one.
 */
@Composable
fun DynamicIslandStudio(ink: Color, dark: Boolean, preview: IslandLook.Situation? = null) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE) }
    var look by remember { mutableStateOf(IslandLook.read(prefs)) }
    var actions by remember { mutableStateOf(IslandGestures.all(prefs)) }
    // Changes made elsewhere (reset, another screen) show up here too.
    DisposableEffect(prefs) {
        val l = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
            if (key == null || key in IslandLook.keys) look = IslandLook.read(p)
            if (key == null || IslandGestures.Gesture.entries.any { it.key == key }) actions = IslandGestures.all(p)
        }
        prefs.registerOnSharedPreferenceChangeListener(l)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(l) }
    }
    var situation by remember { mutableStateOf(preview ?: IslandLook.Situation.App) }
    val card = if (dark) Color(0xFF1C1C1E) else Color.White
    val muted = ink.copy(alpha = 0.55f)

    Column(
        Modifier.fillMaxWidth().background(card, RoundedCornerShape(28.dp)).padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IslandPreview(look, actions, situation, dark)

        // Which situation you're editing (the preview follows).
        ChipRow(
            IslandLook.Situation.entries, situation, { situation = it }, ink, dark,
            label = { it.label }, icon = { s, c -> drawSituation(s, c) },
        )

        val (left, right) = look.slots[situation] ?: IslandLook.DEFAULT_SLOTS.getValue(situation)
        SideLabel("Left", ink)
        ChipRow(
            IslandLook.options(situation), left, { IslandLook.setSlot(prefs, situation, true, it); look = IslandLook.read(prefs) }, ink, dark,
            label = { it.label }, icon = { s, c -> drawSlotIcon(s, c) },
        )
        SideLabel("Right", ink)
        ChipRow(
            IslandLook.options(situation), right, { IslandLook.setSlot(prefs, situation, false, it); look = IslandLook.read(prefs) }, ink, dark,
            label = { it.label }, icon = { s, c -> drawSlotIcon(s, c) },
        )

        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            LookRow("Size", ink) {
                LiquidSegments(IslandLook.Size.entries.map { it.label }, look.size.ordinal, {
                    IslandLook.setSize(prefs, IslandLook.Size.entries[it]); look = IslandLook.read(prefs)
                }, track = ink.copy(alpha = if (dark) 0.08f else 0.05f))
            }
            LookRow("Width", ink) {
                LiquidSegments(IslandLook.Width.entries.map { it.label }, look.width.ordinal, {
                    IslandLook.setWidth(prefs, IslandLook.Width.entries[it]); look = IslandLook.read(prefs)
                }, track = ink.copy(alpha = if (dark) 0.08f else 0.05f))
            }
            LookRow("Glow", ink) {
                LiquidSegments(IslandLook.Glow.entries.map { it.label }, look.glow.ordinal, {
                    IslandLook.setGlow(prefs, IslandLook.Glow.entries[it]); look = IslandLook.read(prefs)
                }, track = ink.copy(alpha = if (dark) 0.08f else 0.05f))
            }
            LookRow("Colour of bars and rings", ink) {
                LiquidSegments(IslandLook.Accent.entries.map { it.label }, look.accent.ordinal, {
                    IslandLook.setAccent(prefs, IslandLook.Accent.entries[it]); look = IslandLook.read(prefs)
                }, track = ink.copy(alpha = if (dark) 0.08f else 0.05f))
            }
        }

        AnimatedVisibility(
            !look.isDefault || actions != IslandGestures.defaults,
            enter = fadeIn() + expandVertically(spring(0.85f, 300f)), exit = fadeOut() + shrinkVertically(),
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text2("Changed from the original", muted, 13, Modifier.weight(1f))
                GlassPillButton(text = "Reset", textColor = ink, dark = dark, height = 36.dp, fontSize = 14.sp) {
                    IslandLook.reset(prefs)
                    IslandGestures.reset(prefs)
                    look = IslandLook.read(prefs)
                    actions = IslandGestures.all(prefs)
                }
            }
        }
    }
}

/** The live Dynamic Island strip with your own look and gestures, kept up to date as they change. */
@Composable
internal fun LiveIslandStrip(situation: IslandLook.Situation, dark: Boolean) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE) }
    var look by remember { mutableStateOf(IslandLook.read(prefs)) }
    var actions by remember { mutableStateOf(IslandGestures.all(prefs)) }
    DisposableEffect(prefs) {
        val l = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
            if (key == null || key in IslandLook.keys) look = IslandLook.read(p)
            if (key == null || IslandGestures.Gesture.entries.any { it.key == key }) actions = IslandGestures.all(p)
        }
        prefs.registerOnSharedPreferenceChangeListener(l)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(l) }
    }
    IslandPreview(look, actions, situation, dark)
}

/**
 * The real Dynamic Island on a strip like the top of a phone: sample music, your AirPods (or a
 * demo pair), the chosen situation. Touch it: gestures show their sign here without doing
 * anything to your music.
 */
@Composable
private fun IslandPreview(look: IslandLook.Look, actions: Map<IslandGestures.Gesture, IslandGestures.Action>, situation: IslandLook.Situation, dark: Boolean) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val live by GlintOverlays.snapshot.collectAsState()
    val pods0 = if (live.budsLevel != null) live else PodsSnapshot(
        name = "AirPods Pro", left = 82, right = 78, case = 54, caseCharging = true, leftInEar = true, rightInEar = true, listeningMode = 4,
    )
    val pods = if (situation == IslandLook.Situation.Charging) pods0.copy(leftCharging = true, rightCharging = true) else pods0
    val art = remember {
        android.graphics.Bitmap.createBitmap(96, 96, android.graphics.Bitmap.Config.ARGB_8888).also { b ->
            android.graphics.Canvas(b).drawPaint(android.graphics.Paint().apply {
                shader = android.graphics.LinearGradient(0f, 0f, 96f, 96f, 0xFFFF6A3D.toInt(), 0xFF7B2CBF.toInt(), android.graphics.Shader.TileMode.CLAMP)
            })
        }.asImageBitmap()
    }
    val playing = situation == IslandLook.Situation.Music || situation == IslandLook.Situation.Talking
    val icon = remember { sampleIcon() }
    val heard = if (situation == IslandLook.Situation.Sound) SoundSource.Heard(
        kind = SoundRules.Kind.Alert, pkg = null, app = "Messages", icon = icon, startedAt = 1L, active = true,
    ) else null
    val track = NowPlaying.Track(
        playing = playing, title = "Midnight City", artist = "M83", app = "pro", art = art, fromSession = true,
        durationMs = 243_000L, positionMs = 90_000L, positionAtMs = android.os.SystemClock.elapsedRealtime().coerceAtLeast(1L),
    )
    val geo = remember(look) { MiniGeometry(context, look = look) }
    val livePlace by me.kavishdevar.librepods.services.ScreenApp.place.collectAsState()
    val liveWallpaper by me.kavishdevar.librepods.services.ScreenApp.wallpaper.collectAsState()
    val liveTimer by me.kavishdevar.librepods.services.IslandTimer.state.collectAsState()
    val wallpaper = if (dark) listOf(Color(0xFF0B1A33), Color(0xFF3A1446)) else listOf(Color(0xFFFFD6A5), Color(0xFFBDE0FE))
    Box(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .height(with(density) { geo.compactWindow.height.toDp() } + 28.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(wallpaper)),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(with(density) { geo.compactWindow.width.toDp() }, with(density) { geo.compactWindow.height.toDp() })) {
            MiniIslandHost(
                geometry = geo, track = track, leaving = false, hidden = false,
                content = when (situation) {
                    IslandLook.Situation.Idle, IslandLook.Situation.Charging -> MiniIslandRules.Content.AirPods
                    IslandLook.Situation.Rest -> MiniIslandRules.Content.Rest
                    IslandLook.Situation.Sound -> MiniIslandRules.Content.Sound
                    IslandLook.Situation.App, IslandLook.Situation.Home, IslandLook.Situation.Locked -> MiniIslandRules.Content.Screen
                    else -> MiniIslandRules.Content.Music
                },
                // The app you're really in when pro knows it (pro itself, here), otherwise a sample.
                place = when (situation) {
                    IslandLook.Situation.App -> (livePlace as? me.kavishdevar.librepods.services.ScreenApp.Place.App)
                        ?: me.kavishdevar.librepods.services.ScreenApp.Place.App("sample", "Messages", icon)
                    IslandLook.Situation.Home -> me.kavishdevar.librepods.services.ScreenApp.Place.Home
                    IslandLook.Situation.Locked -> me.kavishdevar.librepods.services.ScreenApp.Place.Locked()
                    else -> me.kavishdevar.librepods.services.ScreenApp.Place.Unknown
                },
                wallpaper = liveWallpaper.ifEmpty { wallpaper },
                // A running timer shows in the preview as it does around the camera.
                timer = liveTimer.takeIf { situation != IslandLook.Situation.Sound && situation != IslandLook.Situation.Talking },
                budsUp = live.budsLevel != null,
                heard = heard,
                pods = pods,
                heartBpm = 72,
                talking = situation == IslandLook.Situation.Talking,
                look = look, actions = actions, forceSituation = situation,
                onWindowSize = {}, onTouchable = {}, onGone = {}, onAction = {},
            )
            // The camera, where the real one sits.
            Canvas(Modifier.matchParentSize()) {
                val r = minOf(geo.size.height * 0.36f, 13.dp.toPx())
                drawCircle(Color(0xFF0B0B0F), r * 0.62f, center)
                drawCircle(Color(0xFF1F2A44), r * 0.26f, center)
            }
        }
    }
}

/** What each touch on the Dynamic Island does. */
@Composable
fun IslandGestureSettings(ink: Color, dark: Boolean) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE) }
    var actions by remember { mutableStateOf(IslandGestures.all(prefs)) }
    DisposableEffect(prefs) {
        val l = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
            if (key == null || IslandGestures.Gesture.entries.any { it.key == key }) actions = IslandGestures.all(p)
        }
        prefs.registerOnSharedPreferenceChangeListener(l)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(l) }
    }
    var open by remember { mutableStateOf<IslandGestures.Gesture?>(null) }
    val card = if (dark) Color(0xFF1C1C1E) else Color.White
    val view = LocalView.current
    Column(Modifier.fillMaxWidth().background(card, RoundedCornerShape(28.dp)).padding(vertical = 6.dp)) {
        IslandGestures.Gesture.entries.forEachIndexed { i, g ->
            val a = actions[g] ?: IslandGestures.defaults.getValue(g)
            Row(
                Modifier
                    .fillMaxWidth()
                    .pressable { open = if (open == g) null else g }
                    .semantics { role = Role.Button; contentDescription = "${g.label}: ${a.label}" }
                    .padding(horizontal = 16.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(30.dp).background(ink.copy(alpha = if (dark) 0.13f else 0.07f), RoundedCornerShape(9.dp)),
                    contentAlignment = Alignment.Center,
                ) { Canvas(Modifier.size(20.dp)) { drawGesture(g, ink) } }
                Spacer(Modifier.width(12.dp))
                Text2(g.label, ink, 16, Modifier.weight(1f))
                Text2(a.label, if (a == IslandGestures.Action.Nothing) ink.copy(alpha = 0.4f) else ink.copy(alpha = 0.6f), 14)
            }
            AnimatedVisibility(open == g, enter = fadeIn() + expandVertically(spring(0.85f, 320f)), exit = fadeOut() + shrinkVertically()) {
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    Modifier.fillMaxWidth().padding(start = 58.dp, end = 12.dp, bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    IslandGestures.Action.entries.forEach { option ->
                        Chip(option.label, option == a, ink, dark) {
                            view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                            IslandGestures.set(prefs, g, option)
                            actions = IslandGestures.all(prefs)
                            open = null
                        }
                    }
                }
            }
            if (i < IslandGestures.Gesture.entries.lastIndex) Box(
                Modifier.padding(start = 58.dp).fillMaxWidth().height(0.5.dp).background(ink.copy(alpha = 0.08f))
            )
        }
    }
}

@Composable
private fun SideLabel(text: String, ink: Color) = Text2(text, ink.copy(alpha = 0.55f), 13, Modifier.padding(start = 20.dp), FontWeight.SemiBold)

@Composable
private fun LookRow(label: String, ink: Color, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text2(label, ink.copy(alpha = 0.55f), 13, Modifier.padding(start = 4.dp), FontWeight.SemiBold)
        content()
    }
}

/** A horizontally scrolling row of choices, each a small picture and a word; the chosen one lit. */
@Composable
private fun <T> ChipRow(
    options: List<T>, selected: T, onSelect: (T) -> Unit, ink: Color, dark: Boolean,
    label: (T) -> String, icon: DrawScope.(T, Color) -> Unit,
) {
    val view = LocalView.current
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { o ->
            val on = o == selected
            Chip(label(o), on, ink, dark, icon = { c -> icon(o, c) }) {
                if (!on) view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                onSelect(o)
            }
        }
    }
}

@Composable
private fun Chip(text: String, on: Boolean, ink: Color, dark: Boolean, icon: (DrawScope.(Color) -> Unit)? = null, onClick: () -> Unit) {
    val bg by animateColorAsState(
        if (on) ink.copy(alpha = if (dark) 0.2f else 0.1f) else ink.copy(alpha = if (dark) 0.06f else 0.035f), label = "chipBg",
    )
    val lit by animateFloatAsState(if (on) 1f else 0f, spring(dampingRatio = 0.6f, stiffness = 500f), label = "chipLit")
    val color = ink.copy(alpha = 0.6f + 0.4f * lit)
    Row(
        Modifier
            .graphicsLayer { val s = 1f + 0.04f * lit; scaleX = s; scaleY = s }
            .clip(RoundedCornerShape(50))
            .background(bg)
            .border(0.75.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = if (dark) 0.22f * lit else 0.7f * lit), Color.Transparent)), RoundedCornerShape(50))
            .semantics { role = Role.RadioButton; selected = on }
            .pressable(onClick)
            .padding(start = if (icon != null) 10.dp else 14.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Canvas(Modifier.size(18.dp)) { icon(color) }
            Spacer(Modifier.width(7.dp))
        }
        Text2(text, color, 14, weight = if (on) FontWeight.SemiBold else FontWeight.Medium)
    }
}

@Composable
private fun Text2(text: String, color: Color, size: Int, modifier: Modifier = Modifier, weight: FontWeight = FontWeight.Normal) =
    androidx.compose.material3.Text(text, modifier = modifier, maxLines = 1, style = TextStyle(fontFamily = glintFontFamily, fontSize = size.sp, fontWeight = weight, color = color))

// ---- Small pictures ----

private fun DrawScope.drawSlotIcon(slot: IslandLook.Slot, c: Color) {
    val r = size.minDimension / 2f
    val m = center
    val st = Stroke(r * 0.18f, cap = StrokeCap.Round)
    when (slot) {
        IslandLook.Slot.Cover -> {
            drawCircle(Brush.linearGradient(listOf(Color(0xFFFF6A3D), Color(0xFF7B2CBF)), Offset(0f, 0f), Offset(size.width, size.height)), r * 0.72f, m)
            drawArc(c, -90f, 230f, false, Offset(m.x - r * 0.92f, m.y - r * 0.92f), Size(r * 1.84f, r * 1.84f), style = Stroke(r * 0.12f, cap = StrokeCap.Round))
        }
        IslandLook.Slot.App -> {
            // A round app icon: a green disc with a small speech bubble.
            drawCircle(Color(0xFF30D158), r * 0.82f, m)
            drawRoundRect(Color.White, Offset(m.x - r * 0.46f, m.y - r * 0.4f), Size(r * 0.92f, r * 0.62f), CornerRadius(r * 0.22f))
            drawLine(Color.White, Offset(m.x - r * 0.2f, m.y + r * 0.2f), Offset(m.x - r * 0.3f, m.y + r * 0.5f), r * 0.2f, StrokeCap.Round)
        }
        IslandLook.Slot.Phone -> {
            // The phone: a ring round a small rounded rectangle.
            drawCircle(c.copy(alpha = c.alpha * 0.3f), r * 0.8f, m, style = st)
            drawArc(c, -90f, 250f, false, Offset(m.x - r * 0.8f, m.y - r * 0.8f), Size(r * 1.6f, r * 1.6f), style = st)
            drawRoundRect(c, Offset(m.x - r * 0.22f, m.y - r * 0.38f), Size(r * 0.44f, r * 0.76f), CornerRadius(r * 0.1f), style = Stroke(r * 0.12f))
        }
        IslandLook.Slot.Clock -> {
            // A clock face with its two hands.
            drawCircle(c, r * 0.78f, m, style = Stroke(r * 0.16f))
            drawLine(c, m, Offset(m.x, m.y - r * 0.5f), r * 0.14f, StrokeCap.Round)
            drawLine(c, m, Offset(m.x + r * 0.34f, m.y + r * 0.2f), r * 0.14f, StrokeCap.Round)
        }
        IslandLook.Slot.Bars -> listOf(0.5f, 0.9f, 0.65f, 0.8f).forEachIndexed { i, h ->
            val x = m.x + (i - 1.5f) * r * 0.45f
            drawLine(c, Offset(x, m.y - r * h * 0.7f), Offset(x, m.y + r * h * 0.7f), r * 0.24f, StrokeCap.Round)
        }
        IslandLook.Slot.Battery -> {
            drawCircle(c.copy(alpha = c.alpha * 0.3f), r * 0.8f, m, style = st)
            drawArc(c, -90f, 280f, false, Offset(m.x - r * 0.8f, m.y - r * 0.8f), Size(r * 1.6f, r * 1.6f), style = st)
        }
        IslandLook.Slot.Buds -> for (i in 0 until 3) {
            val p = Offset(m.x + (i - 1) * r * 0.68f, m.y)
            drawCircle(c, r * 0.28f, p, style = Stroke(r * 0.13f))
        }
        IslandLook.Slot.Mode -> drawListeningMode(4, c, m, r * 0.9f)
        IslandLook.Slot.Heart -> {
            val s = r * 1.5f
            val p = heartPath(Size(s, s))
            translate(m.x - s / 2f, m.y - s / 2f) { drawPath(p, c) }
        }
        IslandLook.Slot.Title -> {
            // "Aa": two strokes of text.
            drawLine(c, Offset(m.x - r * 0.85f, m.y - r * 0.25f), Offset(m.x + r * 0.85f, m.y - r * 0.25f), r * 0.2f, StrokeCap.Round)
            drawLine(c, Offset(m.x - r * 0.85f, m.y + r * 0.3f), Offset(m.x + r * 0.35f, m.y + r * 0.3f), r * 0.2f, StrokeCap.Round)
        }
        IslandLook.Slot.Talk -> for (i in 0 until 3) drawCircle(c, r * 0.16f, Offset(m.x + (i - 1) * r * 0.55f, m.y))
        IslandLook.Slot.Same -> {
            drawArc(c, 40f, 280f, false, Offset(m.x - r * 0.7f, m.y - r * 0.7f), Size(r * 1.4f, r * 1.4f), style = st)
            drawLine(c, Offset(m.x + r * 0.62f, m.y - r * 0.05f), Offset(m.x + r * 0.95f, m.y - r * 0.4f), r * 0.18f, StrokeCap.Round)
            drawLine(c, Offset(m.x + r * 0.62f, m.y - r * 0.05f), Offset(m.x + r * 0.25f, m.y - r * 0.3f), r * 0.18f, StrokeCap.Round)
        }
        IslandLook.Slot.Screen -> {
            // A rounded app tile.
            drawRoundRect(Color(0xFF0A84FF), Offset(m.x - r * 0.72f, m.y - r * 0.72f), Size(r * 1.44f, r * 1.44f), CornerRadius(r * 0.4f))
            drawCircle(Color.White, r * 0.3f, m)
        }
        IslandLook.Slot.Home -> drawHomeGrid(m, r * 1.5f, listOf(Color(0xFF64D2FF), Color(0xFFFF9F0A), Color(0xFFBF5AF2)), 1f, c.alpha)
        IslandLook.Slot.Date -> {
            drawRoundRect(c, Offset(m.x - r * 0.7f, m.y - r * 0.7f), Size(r * 1.4f, r * 1.4f), CornerRadius(r * 0.28f), style = Stroke(r * 0.12f))
            drawLine(Color(0xFFFF453A), Offset(m.x - r * 0.55f, m.y - r * 0.35f), Offset(m.x + r * 0.55f, m.y - r * 0.35f), r * 0.2f, StrokeCap.Round)
            drawCircle(c, r * 0.16f, Offset(m.x, m.y + r * 0.22f))
        }
        IslandLook.Slot.Lock -> drawPadlock(m, r * 1.5f, 0f, c)
        IslandLook.Slot.Glance -> {
            // A ring with a spark: whatever's worth a glance.
            drawCircle(c.copy(alpha = c.alpha * 0.3f), r * 0.8f, m, style = st)
            drawArc(Color(0xFFFF9F0A), -90f, 200f, false, Offset(m.x - r * 0.8f, m.y - r * 0.8f), Size(r * 1.6f, r * 1.6f), style = st)
            drawCircle(c, r * 0.18f, m)
        }
        IslandLook.Slot.Nothing -> {
            drawCircle(c.copy(alpha = c.alpha * 0.5f), r * 0.7f, m, style = Stroke(r * 0.12f))
            drawLine(c.copy(alpha = c.alpha * 0.5f), Offset(m.x - r * 0.5f, m.y + r * 0.5f), Offset(m.x + r * 0.5f, m.y - r * 0.5f), r * 0.12f, StrokeCap.Round)
        }
    }
}

private fun DrawScope.drawSituation(s: IslandLook.Situation, c: Color) {
    val r = size.minDimension / 2f
    val m = center
    when (s) {
        IslandLook.Situation.Music -> {
            // A play triangle.
            val p = androidx.compose.ui.graphics.Path().apply {
                moveTo(m.x - r * 0.45f, m.y - r * 0.65f); lineTo(m.x + r * 0.65f, m.y); lineTo(m.x - r * 0.45f, m.y + r * 0.65f); close()
            }
            drawPath(p, c)
        }
        IslandLook.Situation.Paused -> {
            drawRoundRect(c, Offset(m.x - r * 0.55f, m.y - r * 0.65f), Size(r * 0.38f, r * 1.3f), CornerRadius(r * 0.1f))
            drawRoundRect(c, Offset(m.x + r * 0.17f, m.y - r * 0.65f), Size(r * 0.38f, r * 1.3f), CornerRadius(r * 0.1f))
        }
        // A speaker with sound waves: any other sound.
        IslandLook.Situation.Sound -> drawKindGlyph(SoundRules.Kind.Other, m, r * 1.7f, c)
        IslandLook.Situation.Idle -> {
            // Two little buds.
            for (side in listOf(-1, 1)) {
                val x = m.x + side * r * 0.42f
                drawCircle(c, r * 0.3f, Offset(x, m.y - r * 0.35f))
                drawLine(c, Offset(x, m.y - r * 0.2f), Offset(x, m.y + r * 0.75f), r * 0.2f, StrokeCap.Round)
            }
        }
        IslandLook.Situation.Charging -> {
            val p = androidx.compose.ui.graphics.Path().apply {
                moveTo(m.x + r * 0.15f, m.y - r * 0.9f); lineTo(m.x - r * 0.5f, m.y + r * 0.1f); lineTo(m.x, m.y + r * 0.1f)
                lineTo(m.x - r * 0.15f, m.y + r * 0.9f); lineTo(m.x + r * 0.5f, m.y - r * 0.1f); lineTo(m.x, m.y - r * 0.1f); close()
            }
            drawPath(p, c)
        }
        IslandLook.Situation.Talking -> for (i in 0 until 3) drawCircle(c, r * 0.17f, Offset(m.x + (i - 1) * r * 0.55f, m.y))
        // An app tile, the home grid and a padlock: where you are on the phone.
        IslandLook.Situation.App -> drawRoundRect(c, Offset(m.x - r * 0.6f, m.y - r * 0.6f), Size(r * 1.2f, r * 1.2f), CornerRadius(r * 0.34f))
        IslandLook.Situation.Home -> drawHomeGrid(m, r * 1.35f, listOf(c, c, c), 1f, c.alpha)
        IslandLook.Situation.Locked -> drawPadlock(m, r * 1.4f, 0f, c)
        // A small empty pill: nothing on.
        IslandLook.Situation.Rest -> drawRoundRect(
            c, Offset(m.x - r * 0.75f, m.y - r * 0.32f), Size(r * 1.5f, r * 0.64f), CornerRadius(r * 0.32f), style = Stroke(r * 0.16f),
        )
    }
}

private fun DrawScope.drawGesture(g: IslandGestures.Gesture, c: Color) {
    val r = size.minDimension / 2f
    val m = center
    val sw = r * 0.16f
    fun arrow(dx: Float, dy: Float) {
        val a = Offset(m.x - dx * r * 0.7f, m.y - dy * r * 0.7f)
        val b = Offset(m.x + dx * r * 0.7f, m.y + dy * r * 0.7f)
        drawLine(c, a, b, sw, StrokeCap.Round)
        // Head.
        val hx = -dx * r * 0.4f
        val hy = -dy * r * 0.4f
        drawLine(c, b, Offset(b.x + hx - dy * r * 0.35f, b.y + hy - dx * r * 0.35f), sw, StrokeCap.Round)
        drawLine(c, b, Offset(b.x + hx + dy * r * 0.35f, b.y + hy + dx * r * 0.35f), sw, StrokeCap.Round)
    }
    when (g) {
        IslandGestures.Gesture.Tap1, IslandGestures.Gesture.Tap2, IslandGestures.Gesture.Tap3 -> {
            val n = g.ordinal + 1
            for (i in 0 until n) drawCircle(c, r * 0.2f, Offset(m.x + (i - (n - 1) / 2f) * r * 0.62f, m.y))
        }
        IslandGestures.Gesture.Hold -> {
            drawCircle(c, r * 0.3f, m)
            drawCircle(c, r * 0.72f, m, style = Stroke(sw * 0.8f))
        }
        IslandGestures.Gesture.SwipeLeft -> arrow(-1f, 0f)
        IslandGestures.Gesture.SwipeRight -> arrow(1f, 0f)
        IslandGestures.Gesture.PullDown -> arrow(0f, 1f)
    }
}
