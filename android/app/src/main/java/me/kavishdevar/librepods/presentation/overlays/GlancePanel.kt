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

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import me.kavishdevar.librepods.presentation.glint.drawHeadphones
import me.kavishdevar.librepods.presentation.navigation.AppLinks
import me.kavishdevar.librepods.presentation.theme.glintFontFamily
import me.kavishdevar.librepods.services.GlanceRules
import me.kavishdevar.librepods.services.GlintStatus
import me.kavishdevar.librepods.services.HeadphoneLink
import me.kavishdevar.librepods.services.IslandTimer
import me.kavishdevar.librepods.services.LinkState
import me.kavishdevar.librepods.services.PhoneControls
import me.kavishdevar.librepods.services.PhoneRules
import me.kavishdevar.librepods.services.PhoneStatus
import me.kavishdevar.librepods.services.ScreenApp
import me.kavishdevar.librepods.services.TimerRules

/**
 * The opened Dynamic Island when nothing is playing, like an iPhone's expanded island: the app
 * you're in (or home, or locked) with today's date, the two things that matter right now (a
 * timer, charging, your headphones, a low battery, the next alarm) and five controls for the phone
 * itself: torch, timer, sound or vibrate, screenshot, lock.
 *
 * Everything here is live: the timer counts, the torch follows the Quick Settings tile, the rows
 * change as things start and stop. [onTouch] restarts the island's stay timer; [onClose] tucks it away.
 */
@Composable
internal fun GlancePanel(
    content: Color,
    secondary: Color,
    dark: Boolean,
    active: Boolean,
    reduceMotion: Boolean,
    onTouch: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val place by ScreenApp.place.collectAsState()
    val timer by IslandTimer.state.collectAsState()
    val phone by PhoneStatus.state.collectAsState()
    val pods by GlintOverlays.snapshot.collectAsState()
    val link by GlintStatus.link.collectAsState()
    val hp by HeadphoneLink.state.collectAsState()
    val torch by PhoneControls.torch.collectAsState()
    val wallpaper by ScreenApp.wallpaper.collectAsState()
    LaunchedEffect(Unit) { PhoneControls.watchTorch(context) }

    // The clock for everything that changes with time here: once a second while a timer runs,
    // otherwise once a minute (the alarm's "in 8 h", the time to full).
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    var wall by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val ticking = timer?.running == true
    LaunchedEffect(ticking, timer) {
        while (true) {
            now = SystemClock.elapsedRealtime(); wall = System.currentTimeMillis()
            delay(if (ticking) (timer?.let { (TimerRules.left(it, now) % 1_000L).takeIf { l -> l > 0L } } ?: 1_000L) + 5L else 30_000L)
        }
    }
    val nextAlarm = remember(wall / 60_000L) { PhoneControls.nextAlarm(context) }
    val toFull = remember(wall / 60_000L, phone.charging) { if (phone.charging) PhoneControls.timeToFull(context) else null }
    val budsUp = pods.budsLevel != null && (link is LinkState.Connected || hp.connected || GlintOverlays.chosenAudio(context))
    val rows = GlanceRules.rows(GlanceRules.live(timer, phone, budsUp, pods.budsLevel, pods.headphones, nextAlarm, wall))

    var choosingTimer by remember { mutableStateOf(false) }
    LaunchedEffect(active) { if (!active) choosingTimer = false }

    Column(Modifier.fillMaxSize().padding(start = 20.dp, end = 16.dp, top = 16.dp, bottom = 14.dp)) {
        // ---- Where you are, and today ----
        Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(36.dp)) {
                val c = center
                when (val p = place) {
                    is ScreenApp.Place.App -> p.icon?.let { appTileFill(it, c, size.minDimension) }
                        ?: drawHomeGrid(c, size.minDimension * 0.8f, wallpaper, 1f, 1f)
                    is ScreenApp.Place.Locked -> {
                        drawCircle(content.copy(alpha = 0.12f), size.minDimension / 2f, c)
                        drawPadlock(c, size.minDimension * 0.56f, 0f, content)
                    }
                    else -> drawHomeGrid(c, size.minDimension * 0.82f, wallpaper, 1f, 1f)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                val title = when (val p = place) {
                    is ScreenApp.Place.App -> p.label ?: "This app"
                    ScreenApp.Place.Home -> "Home screen"
                    is ScreenApp.Place.Locked -> "Locked"
                    ScreenApp.Place.Unknown -> "This phone"
                }
                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontFamily = glintFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 21.sp, color = content))
                Text(longDate(wall), maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontFamily = glintFontFamily, fontSize = 13.sp, lineHeight = 16.sp, color = secondary))
            }
            // Straight to the Island tab in pro, to change what the island shows and does.
            PressableGlyph(size = 34.dp, description = "Customize the Dynamic Island", enabled = active, dark = dark, onClick = {
                onTouch(); GlintOverlays.openApp(context, AppLinks.ISLAND_TAB); onClose()
            }) {
                drawGlassCapsule(dark)
                drawSliders(center, 14.dp.toPx(), content)
            }
        }
        Spacer(Modifier.height(10.dp))

        // ---- What's live ----
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // One thing going on: it gets the room (bigger ring, bigger words), not a lonely line.
            val hero = rows.size == 1
            rows.forEach { item ->
                if (hero && item is GlanceRules.Item.Timer && item.state.ringing) {
                    // Ringing on its own: big words, and Stop / one more minute in a row under them.
                    LiveRow(item, now, wall, toFull, pods, content, secondary, dark, active, onTouch, context, hero = true, buttons = false, heightDp = 64)
                    Row(Modifier.fillMaxWidth().padding(start = 72.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlassPillButton(text = "+1 min", textColor = content, dark = dark, height = 38.dp, fontSize = 15.sp, onClick = { onTouch(); IslandTimer.addMinute(context) })
                        StopPill(active, wide = true) { onTouch(); IslandTimer.cancel(context) }
                    }
                } else LiveRow(item, now, wall, toFull, pods, content, secondary, dark, active, onTouch, context, hero)
            }
        }
        Spacer(Modifier.weight(1f))

        // ---- The phone's controls (or the timer's lengths) ----
        AnimatedContent(
            targetState = choosingTimer,
            transitionSpec = {
                val spec = if (reduceMotion) tween<Float>(120) else spring(0.82f, 420f)
                (fadeIn(spec) + scaleIn(spec, initialScale = 0.94f)) togetherWith (fadeOut(tween(100)) + scaleOut(tween(100), targetScale = 0.97f))
            },
            label = "glanceControls",
        ) { choosing ->
            if (choosing) {
                Row(Modifier.fillMaxWidth().height(66.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    PressableGlyph(size = 38.dp, description = "Back", enabled = active, dark = dark, onClick = { onTouch(); choosingTimer = false }) {
                        drawGlassCapsule(dark)
                        val u = 6.dp.toPx()
                        drawLine(content, Offset(center.x + u * 0.4f, center.y - u), Offset(center.x - u * 0.6f, center.y), 2.dp.toPx(), StrokeCap.Round)
                        drawLine(content, Offset(center.x - u * 0.6f, center.y), Offset(center.x + u * 0.4f, center.y + u), 2.dp.toPx(), StrokeCap.Round)
                    }
                    TimerRules.PRESETS.forEach { min ->
                        PressableGlyph(size = 40.dp, description = "Start a $min minute timer", enabled = active, dark = dark, onClick = {
                            onTouch()
                            IslandTimer.start(context, min * 60_000L)
                            choosingTimer = false
                            onClose()
                        }) {
                            drawGlassCapsule(dark)
                            drawCenteredLabel("$min", 15.sp.toPx(), content)
                        }
                    }
                }
            } else {
                Row(Modifier.fillMaxWidth().height(66.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    val system = PhoneControls.canUseSystemActions()
                    Control("Torch", on = torch, enabled = active && PhoneControls.hasTorch(context), content, secondary, dark, {
                        onTouch(); PhoneControls.toggleTorch(context)
                    }) { c, on -> drawTorch(c, size.minDimension * 0.5f, if (on) Color.Black else content) }
                    Control("Timer", on = timer != null, enabled = active, content, secondary, dark, {
                        onTouch(); choosingTimer = true
                    }) { c, on -> drawStopwatch(c, size.minDimension * 0.46f, if (on) Color.Black else content) }
                    var ring by remember { mutableStateOf(PhoneControls.ring(context)) }
                    Control(ring.label, on = ring != PhoneControls.Ring.Sound, enabled = active, content, secondary, dark, {
                        onTouch(); PhoneControls.toggleRing(context)?.let { ring = it }
                    }) { c, on -> if (ring == PhoneControls.Ring.Sound) drawRinger(c, size.minDimension * 0.46f, content) else drawVibrate(c, size.minDimension * 0.46f, if (on) Color.Black else content) }
                    Control("Capture", on = false, enabled = active, content, secondary, dark, {
                        onTouch()
                        // Needs the accessibility switch; without it, straight to where it's turned on.
                        if (system) { onClose(); PhoneControls.screenshot() } else { GlintOverlays.openApp(context, AppLinks.ISLANDS); onClose() }
                    }, dim = !system) { c, _ -> drawScreenshot(c, size.minDimension * 0.46f, content) }
                    Control("Lock", on = false, enabled = active, content, secondary, dark, {
                        onTouch()
                        if (system) { onClose(); PhoneControls.lockScreen() } else { GlintOverlays.openApp(context, AppLinks.ISLANDS); onClose() }
                    }, dim = !system) { c, _ -> drawPadlock(c, size.minDimension * 0.46f, 0f, content) }
                }
            }
        }
    }
}

/** One of the island's live rows: a round sign, two lines of words, and its own buttons. */
@Composable
private fun LiveRow(
    item: GlanceRules.Item, now: Long, wall: Long, toFull: Long?, pods: PodsSnapshot,
    content: Color, secondary: Color, dark: Boolean, active: Boolean, onTouch: () -> Unit, context: Context,
    hero: Boolean = false,
    /** Its own buttons at the end of the row (off when they're laid out under it instead). */
    buttons: Boolean = true,
    heightDp: Int = if (hero) 104 else 50,
) {
    val orange = Color(0xFFFF9F0A)
    val green = Color(0xFF30D158)
    val (title, sub) = rowText(item, now, wall, toFull, pods)
    Row(
        Modifier.fillMaxWidth().height(heightDp.dp).semantics { contentDescription = "$title. $sub" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(if (hero) 58.dp else 36.dp)) {
            val c = center
            val r = size.minDimension / 2f - (if (hero) 3.dp else 2.dp).toPx()
            val st = Stroke((if (hero) 4.5.dp else 3.dp).toPx(), cap = StrokeCap.Round)
            val tl = Offset(c.x - r, c.y - r); val sz = Size(r * 2, r * 2)
            when (item) {
                is GlanceRules.Item.Timer -> {
                    drawArc(orange.copy(alpha = 0.25f), 0f, 360f, false, tl, sz, style = st)
                    if (!item.state.ringing) drawArc(orange, -90f, 360f * (1f - TimerRules.progress(item.state, now)), false, tl, sz, style = st)
                    drawStopwatch(c, r * 0.95f, orange)
                }
                is GlanceRules.Item.Charging -> {
                    drawArc(green.copy(alpha = 0.25f), 0f, 360f, false, tl, sz, style = st)
                    drawArc(green, -90f, 360f * item.level / 100f, false, tl, sz, style = st)
                    drawChargeBolt(c, r * 0.95f, green)
                }
                is GlanceRules.Item.Buds -> {
                    drawArc(content.copy(alpha = 0.18f), 0f, 360f, false, tl, sz, style = st)
                    item.level?.let { drawArc(levelColor(it, content), -90f, 360f * it / 100f, false, tl, sz, style = st) }
                    if (item.headphones) drawHeadphones(c, r * 0.48f, content)
                    else drawBudPair(c, r * 1.05f, content)
                }
                is GlanceRules.Item.LowPhone -> {
                    drawArc(content.copy(alpha = 0.18f), 0f, 360f, false, tl, sz, style = st)
                    drawArc(levelColor(item.level, content), -90f, 360f * item.level / 100f, false, tl, sz, style = st)
                }
                is GlanceRules.Item.Alarm -> drawKindGlyph(me.kavishdevar.librepods.services.SoundRules.Kind.Alarm, c, r * 1.2f, content)
                is GlanceRules.Item.Battery -> {
                    drawArc(content.copy(alpha = 0.18f), 0f, 360f, false, tl, sz, style = st)
                    drawArc(if (item.charging) green else levelColor(item.level, content), -90f, 360f * item.level / 100f, false, tl, sz, style = st)
                }
            }
        }
        Spacer(Modifier.width(if (hero) 14.dp else 12.dp))
        Column(Modifier.weight(1f)) {
            val big = item is GlanceRules.Item.Timer && !item.state.ringing
            val titleSize = when {
                hero && big -> 32.sp
                hero -> 21.sp
                big -> 20.sp
                else -> 15.sp
            }
            Text(
                title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = TextStyle(
                    fontFamily = glintFontFamily, fontWeight = FontWeight.SemiBold, fontSize = titleSize,
                    lineHeight = titleSize * 1.18f, color = if (item is GlanceRules.Item.Timer) orange else content,
                ),
            )
            Text(sub, maxLines = if (hero) 2 else 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontFamily = glintFontFamily, fontSize = if (hero) 13.sp else 12.sp, lineHeight = if (hero) 17.sp else 15.sp, color = secondary))
        }
        // The row's own buttons.
        if (buttons) when (item) {
            is GlanceRules.Item.Timer -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (item.state.ringing) {
                    GlassPillButton(text = "+1 min", textColor = content, dark = dark, height = 32.dp, fontSize = 13.sp, onClick = { onTouch(); IslandTimer.addMinute(context) })
                    StopPill(active) { onTouch(); IslandTimer.cancel(context) }
                } else {
                    PressableGlyph(size = 34.dp, description = if (item.state.paused) "Resume timer" else "Pause timer", enabled = active, dark = dark, onClick = {
                        onTouch(); if (item.state.paused) IslandTimer.resume(context) else IslandTimer.pause(context)
                    }) {
                        drawGlassCapsule(dark)
                        if (item.state.paused) drawPlayTriangle(center, 11.dp.toPx(), orange) else drawPauseBars(center, 11.dp.toPx(), orange)
                    }
                    PressableGlyph(size = 34.dp, description = "One more minute", enabled = active, dark = dark, onClick = { onTouch(); IslandTimer.addMinute(context) }) {
                        drawGlassCapsule(dark)
                        drawCenteredLabel("+1", 13.sp.toPx(), content)
                    }
                    PressableGlyph(size = 34.dp, description = "Cancel timer", enabled = active, dark = dark, onClick = { onTouch(); IslandTimer.cancel(context) }) {
                        drawGlassCapsule(dark)
                        val u = 4.5.dp.toPx()
                        drawLine(content, Offset(center.x - u, center.y - u), Offset(center.x + u, center.y + u), 2.dp.toPx(), StrokeCap.Round)
                        drawLine(content, Offset(center.x + u, center.y - u), Offset(center.x - u, center.y + u), 2.dp.toPx(), StrokeCap.Round)
                    }
                }
            }
            is GlanceRules.Item.Buds -> GlassPillButton(text = "Open", textColor = content, dark = dark, height = 32.dp, fontSize = 13.sp, onClick = {
                onTouch(); GlintOverlays.showIsland(context, IslandEvent.Connected, expand = true)
            })
            is GlanceRules.Item.LowPhone -> GlassPillButton(text = "Saver", textColor = content, dark = dark, height = 32.dp, fontSize = 13.sp, onClick = {
                onTouch()
                runCatching { context.startActivity(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            })
            else -> {}
        }
    }
}

/** The words for one live row. */
internal fun rowText(item: GlanceRules.Item, now: Long, wall: Long, toFull: Long?, pods: PodsSnapshot): Pair<String, String> = when (item) {
    is GlanceRules.Item.Timer -> when {
        item.state.ringing -> "Timer done" to "Stop, or one more minute"
        item.state.paused -> TimerRules.format(TimerRules.left(item.state, now)) to "Timer paused"
        else -> TimerRules.format(TimerRules.left(item.state, now)) to "Timer · ${TimerRules.format(item.state.total)} in all"
    }
    is GlanceRules.Item.Charging -> "Charging · ${item.level}%" to when {
        item.level >= 100 -> "Full"
        toFull != null -> "Full ${PhoneRules.inWords(toFull)}"
        else -> "Plugged in"
    }
    is GlanceRules.Item.Buds -> pods.name.ifBlank { if (item.headphones) "Headphones" else "AirPods" } to
        if (item.headphones || (pods.left == null && pods.right == null)) item.level?.let { "Battery $it%" } ?: "Connected"
        else listOfNotNull(pods.left?.let { "L $it%" }, pods.right?.let { "R $it%" }, pods.case?.let { "Case $it%" }).joinToString("  ·  ")
    is GlanceRules.Item.LowPhone -> "Battery low · ${item.level}%" to "Battery Saver makes it last longer"
    is GlanceRules.Item.Alarm -> "Alarm ${clockOf(item.at)}" to "Rings ${PhoneRules.inWords(item.at - wall)}"
    is GlanceRules.Item.Battery -> "Battery · ${item.level}%" to if (item.charging) "Charging" else "Not charging"
}

private fun clockOf(wallMs: Long): String = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(wallMs))

private fun longDate(wallMs: Long): String =
    java.text.SimpleDateFormat("EEEE d MMMM", java.util.Locale.getDefault()).format(java.util.Date(wallMs))

private fun levelColor(level: Int, normal: Color): Color = when {
    level <= 10 -> Color(0xFFFF453A)
    level <= 20 -> Color(0xFFFFB340)
    else -> normal
}

/** One control: a round glass button (filled white while on) with its name under it. */
@Composable
private fun Control(
    label: String, on: Boolean, enabled: Boolean, content: Color, secondary: Color, dark: Boolean,
    onClick: () -> Unit, dim: Boolean = false, glyph: DrawScope.(Offset, Boolean) -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(56.dp)) {
        PressableGlyph(size = 46.dp, description = label + if (on) ", on" else "", enabled = enabled, dark = dark, onClick = onClick) {
            if (on) drawCircle(Color.White, size.minDimension / 2f, center)
            else drawGlassCapsule(dark)
            val a = if (dim) 0.4f else 1f
            if (a < 1f) {
                drawContext.canvas.saveLayer(androidx.compose.ui.geometry.Rect(Offset.Zero, size), androidx.compose.ui.graphics.Paint().apply { alpha = a })
                glyph(center, on)
                drawContext.canvas.restore()
            } else glyph(center, on)
        }
        Spacer(Modifier.height(4.dp))
        Text(label, maxLines = 1, style = TextStyle(fontFamily = glintFontFamily, fontSize = 10.5.sp, fontWeight = FontWeight.Medium, color = if (dim) secondary.copy(alpha = secondary.alpha * 0.6f) else secondary, textAlign = TextAlign.Center))
    }
}

/** An orange "Stop" pill (the timer's ringing). */
@Composable
private fun StopPill(enabled: Boolean, wide: Boolean = false, onClick: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val current by androidx.compose.runtime.rememberUpdatedState(onClick)
    Box(
        Modifier
            .height(if (wide) 38.dp else 32.dp)
            .width(if (wide) 96.dp else 64.dp)
            .graphicsLayer { val sc = if (pressed) 0.94f else 1f; scaleX = sc; scaleY = sc }
            .background(Color(0xFFFF9F0A), androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
            .islandPress(enabled, "Stop the timer", { current() }, { pressed = it }),
        contentAlignment = Alignment.Center,
    ) {
        Text("Stop", style = TextStyle(fontFamily = glintFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color.Black))
    }
}

// ---- Small drawings for the controls ----

private fun DrawScope.appTileFill(icon: androidx.compose.ui.graphics.ImageBitmap, c: Offset, size: Float) {
    val r = size * 0.27f
    val tile = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(c.x - size / 2f, c.y - size / 2f, c.x + size / 2f, c.y + size / 2f, CornerRadius(r, r))) }
    clipPath(tile) {
        drawImage(icon, dstOffset = androidx.compose.ui.unit.IntOffset((c.x - size / 2f).toInt(), (c.y - size / 2f).toInt()), dstSize = androidx.compose.ui.unit.IntSize(size.toInt(), size.toInt()))
    }
}

private fun DrawScope.drawCenteredLabel(text: String, px: Float, color: Color) {
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        textSize = px; this.color = android.graphics.Color.argb((color.alpha * 255).toInt(), (color.red * 255).toInt(), (color.green * 255).toInt(), (color.blue * 255).toInt())
        textAlign = android.graphics.Paint.Align.CENTER; isFakeBoldText = true
    }
    val y = center.y - (paint.descent() + paint.ascent()) / 2f
    drawContext.canvas.nativeCanvas.drawText(text, center.x, y, paint)
}

private fun DrawScope.drawSliders(c: Offset, size: Float, color: Color) {
    val w = size; val sw = size * 0.12f
    for (i in 0..2) {
        val y = c.y - size * 0.36f + i * size * 0.36f
        drawLine(color, Offset(c.x - w / 2f, y), Offset(c.x + w / 2f, y), sw, StrokeCap.Round)
        val kx = c.x + (if (i == 1) -0.2f else 0.22f) * w
        drawCircle(color, sw * 1.6f, Offset(kx, y))
    }
}

internal fun DrawScope.drawTorch(c: Offset, size: Float, color: Color) {
    // A torch from the side, pointing up: a wide head, a body, and its switch.
    val u = size / 2f
    drawPath(Path().apply {
        moveTo(c.x - u * 0.62f, c.y - u)
        lineTo(c.x + u * 0.62f, c.y - u)
        lineTo(c.x + u * 0.62f, c.y - u * 0.62f)
        lineTo(c.x + u * 0.32f, c.y - u * 0.18f)
        lineTo(c.x + u * 0.32f, c.y + u)
        lineTo(c.x - u * 0.32f, c.y + u)
        lineTo(c.x - u * 0.32f, c.y - u * 0.18f)
        lineTo(c.x - u * 0.62f, c.y - u * 0.62f)
        close()
    }, color, style = Stroke(u * 0.16f, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    drawCircle(color, u * 0.11f, Offset(c.x, c.y + u * 0.28f))
}

internal fun DrawScope.drawStopwatch(c: Offset, size: Float, color: Color) {
    val u = size / 2f
    val sw = u * 0.16f
    val face = Offset(c.x, c.y + u * 0.12f)
    drawCircle(color, u * 0.78f, face, style = Stroke(sw))
    drawLine(color, Offset(c.x - u * 0.26f, c.y - u * 0.92f), Offset(c.x + u * 0.26f, c.y - u * 0.92f), sw, StrokeCap.Round)
    drawLine(color, Offset(c.x, c.y - u * 0.92f), Offset(c.x, c.y - u * 0.66f), sw, StrokeCap.Round)
    drawLine(color, face, Offset(face.x + u * 0.3f, face.y - u * 0.34f), sw, StrokeCap.Round)
}

private fun DrawScope.drawRinger(c: Offset, size: Float, color: Color) = drawKindGlyph(me.kavishdevar.librepods.services.SoundRules.Kind.Alert, c, size, color)

private fun DrawScope.drawVibrate(c: Offset, size: Float, color: Color) {
    // A phone with a buzz on each side.
    val u = size / 2f
    val sw = u * 0.15f
    drawRoundRect(color, Offset(c.x - u * 0.38f, c.y - u * 0.78f), Size(u * 0.76f, u * 1.56f), CornerRadius(u * 0.16f), style = Stroke(sw))
    for (s in listOf(-1f, 1f)) {
        val x = c.x + s * u * 0.66f
        drawPath(Path().apply {
            moveTo(x, c.y - u * 0.5f); lineTo(x + s * u * 0.16f, c.y - u * 0.25f); lineTo(x, c.y); lineTo(x + s * u * 0.16f, c.y + u * 0.25f); lineTo(x, c.y + u * 0.5f)
        }, color, style = Stroke(sw * 0.85f, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    }
}

private fun DrawScope.drawScreenshot(c: Offset, size: Float, color: Color) {
    // Four corner brackets, like a viewfinder.
    val u = size / 2f
    val l = u * 0.42f
    val sw = u * 0.15f
    for (sx in listOf(-1f, 1f)) for (sy in listOf(-1f, 1f)) {
        val x = c.x + sx * u * 0.85f; val y = c.y + sy * u * 0.72f
        drawLine(color, Offset(x, y), Offset(x - sx * l, y), sw, StrokeCap.Round)
        drawLine(color, Offset(x, y), Offset(x, y - sy * l), sw, StrokeCap.Round)
    }
}

private fun DrawScope.drawPauseBars(c: Offset, size: Float, color: Color) {
    val bw = size * 0.28f
    drawRoundRect(color, Offset(c.x - size * 0.38f, c.y - size / 2f), Size(bw, size), CornerRadius(bw / 3f))
    drawRoundRect(color, Offset(c.x + size * 0.1f, c.y - size / 2f), Size(bw, size), CornerRadius(bw / 3f))
}

private fun DrawScope.drawPlayTriangle(c: Offset, size: Float, color: Color) {
    drawPath(Path().apply {
        moveTo(c.x - size * 0.32f, c.y - size / 2f); lineTo(c.x + size * 0.48f, c.y); lineTo(c.x - size * 0.32f, c.y + size / 2f); close()
    }, color)
}
