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

import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import me.kavishdevar.librepods.presentation.components.LiquidSegments
import me.kavishdevar.librepods.presentation.glint.pressable
import me.kavishdevar.librepods.presentation.navigation.LocalTabBarSpace
import me.kavishdevar.librepods.presentation.overlays.GlassPillButton
import me.kavishdevar.librepods.presentation.overlays.GlintOverlays
import me.kavishdevar.librepods.presentation.overlays.IslandEvent
import me.kavishdevar.librepods.presentation.overlays.drawBudPair
import me.kavishdevar.librepods.presentation.overlays.drawKindGlyph
import me.kavishdevar.librepods.presentation.glint.drawHeadphones
import me.kavishdevar.librepods.presentation.overlays.drawStopwatch
import me.kavishdevar.librepods.presentation.overlays.drawTorch
import me.kavishdevar.librepods.presentation.theme.glintFontFamily
import me.kavishdevar.librepods.services.DeviceChoice
import me.kavishdevar.librepods.services.GlintStatus
import me.kavishdevar.librepods.services.HeadphoneLink
import me.kavishdevar.librepods.services.IslandAccess
import me.kavishdevar.librepods.services.IslandLook
import me.kavishdevar.librepods.services.IslandPrefs
import me.kavishdevar.librepods.services.IslandTimer
import me.kavishdevar.librepods.services.LinkState
import me.kavishdevar.librepods.services.MusicPulse
import me.kavishdevar.librepods.services.NowPlaying
import me.kavishdevar.librepods.services.PhoneControls
import me.kavishdevar.librepods.services.PhoneRules
import me.kavishdevar.librepods.services.PhoneStatus
import me.kavishdevar.librepods.services.ScreenApp
import me.kavishdevar.librepods.services.TimerRules

/**
 * The Phone tab, pro's main page: the phone itself, with the Dynamic Island in charge.
 *
 * Top to bottom: the live Dynamic Island (see each look, open the real glance), the few switches
 * that make it work fully (each ticked off once done, gone when all are), the phone's own controls
 * (torch, sound or vibrate, a timer that lives on the island), the battery with what's coming up
 * (time to full, the next alarm), and your headphones as a small card that opens their tab.
 */
@Composable
fun PhoneScreen(navigateToIsland: () -> Unit, navigateToHeadphones: (() -> Unit)? = null, navigateToApps: () -> Unit = {}) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val ink = if (dark) Color.White else Color.Black
    val card = if (dark) Color(0xFF1C1C1E) else Color.White
    val topPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 84.dp
    val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp + LocalTabBarSpace.current

    val phone by PhoneStatus.state.collectAsState()
    val place by ScreenApp.place.collectAsState()
    val timer by IslandTimer.state.collectAsState()
    val torch by PhoneControls.torch.collectAsState()
    LaunchedEffect(Unit) { PhoneControls.watchTorch(context) }

    // What's allowed, looked at again every second (the switches live on Android's own pages).
    var canDraw by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var tapsOn by remember { mutableStateOf(IslandAccess.isEnabled(context)) }
    var notes by remember { mutableStateOf(NowPlaying.hasAccess(context)) }
    var hear by remember { mutableStateOf(MusicPulse.allowed(context)) }
    var ring by remember { mutableStateOf(PhoneControls.ring(context)) }
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    var wall by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            val draw = Settings.canDrawOverlays(context)
            if (draw && !canDraw) GlintOverlays.refreshMiniIsland(context)
            canDraw = draw
            tapsOn = IslandAccess.isEnabled(context)
            val n = NowPlaying.hasAccess(context)
            if (n && !notes) NowPlaying.attach(context)
            notes = n
            val h = MusicPulse.allowed(context)
            if (h && !hear) MusicPulse.retry(context)
            hear = h
            ring = PhoneControls.ring(context)
            now = SystemClock.elapsedRealtime(); wall = System.currentTimeMillis()
            delay(if (timer?.running == true) 250L else 1_000L)
        }
    }
    val nextAlarm = remember(wall / 60_000L) { PhoneControls.nextAlarm(context) }
    val toFull = remember(wall / 60_000L, phone.charging) { if (phone.charging) PhoneControls.timeToFull(context) else null }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(topPadding))

        // pro froze or crashed last time: what happened, and a way to send it (only when it did).
        me.kavishdevar.librepods.presentation.components.FreezeBanner()

        // ---- The Dynamic Island, live ----
        var shown by remember { mutableIntStateOf(0) }
        val looks = listOf(IslandLook.Situation.App, IslandLook.Situation.Home, IslandLook.Situation.Locked, IslandLook.Situation.Music)
        Column(
            Modifier.fillMaxWidth().background(card, RoundedCornerShape(28.dp)).padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            LiveIslandStrip(looks[shown], dark)
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LiquidSegments(
                    listOf("In an app", "Home", "Locked", "Music"), shown, { shown = it },
                    track = ink.copy(alpha = if (dark) 0.08f else 0.05f),
                )
                Text(
                    when {
                        !canDraw -> "Allow pro to draw over other apps (below) and the island appears around your camera."
                        !tapsOn -> "Around your camera right now. Turn on \"See your apps\" below and it shows the app you're in."
                        place is ScreenApp.Place.App -> "Around your camera right now, showing the app you're in. Tap it for what's on."
                        else -> "Around your camera right now. Tap it for what's on: timers, charging, your headphones and quick controls."
                    },
                    style = TextStyle(fontFamily = glintFontFamily, fontSize = 13.sp, lineHeight = 18.sp, color = ink.copy(alpha = 0.6f)),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassPillButton(text = "Open it", textColor = ink, dark = dark, height = 40.dp, fontSize = 15.sp, modifier = Modifier.weight(1f)) {
                        GlintOverlays.showIsland(context, IslandEvent.Glance, expand = true)
                    }
                    GlassPillButton(text = "Customize", textColor = ink, dark = dark, height = 40.dp, fontSize = 15.sp, modifier = Modifier.weight(1f), onClick = navigateToIsland)
                }
            }
        }

        // ---- What it needs (only what's missing) ----
        val missing = listOf(!canDraw, !tapsOn && IslandAccess.isAvailable(context), !notes, !hear).count { it }
        if (missing > 0) {
            Column(Modifier.fillMaxWidth().background(card, RoundedCornerShape(24.dp)).padding(vertical = 6.dp)) {
                Text(
                    "Make it work fully",
                    style = TextStyle(fontFamily = glintFontFamily, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = ink),
                    modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 2.dp),
                )
                SetupRow("Show over other apps", "So the island can sit around the camera", canDraw, ink, dark) {
                    open(context, Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).setData(android.net.Uri.fromParts("package", context.packageName, null)))
                }
                if (IslandAccess.isAvailable(context)) SetupRow(
                    "See your apps, and tap the island", "Turn on \"pro Dynamic Island\". It only learns which app is open, never what's in it",
                    tapsOn, ink, dark,
                ) { open(context, IslandAccess.settingsIntent(context)) }
                SetupRow("Song names and which app made a sound", "Notification access. Nothing in your notifications is read", notes, ink, dark) {
                    if (!open(context, NowPlaying.accessSettingsIntent(context))) open(context, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }
                SetupRow("Sound bars that follow the music", "Android calls it the microphone. Nothing is recorded", hear, ink, dark) {
                    open(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.fromParts("package", context.packageName, null)))
                }
                Text(
                    "\"Restricted setting\"? Open App info, tap ⋮ at the top, Allow restricted settings, then try again.",
                    style = TextStyle(fontFamily = glintFontFamily, fontSize = 12.sp, lineHeight = 16.sp, color = ink.copy(alpha = 0.5f)),
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 10.dp),
                )
            }
        }

        // ---- The phone's own controls ----
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val hasTorch = remember { PhoneControls.hasTorch(context) }
            ControlTile("Torch", if (!hasTorch) "None" else if (torch) "On" else "Off", torch, hasTorch, ink, dark, Modifier.weight(1f), {
                PhoneControls.toggleTorch(context)
            }) { c, on -> drawTorch(c, size.minDimension * 0.5f, if (on) Color.Black else ink) }
            ControlTile(ring.label, if (ring == PhoneControls.Ring.Sound) "Rings" else "Quiet", ring != PhoneControls.Ring.Sound, true, ink, dark, Modifier.weight(1f), {
                PhoneControls.toggleRing(context)?.let { ring = it }
            }) { c, on ->
                drawKindGlyph(me.kavishdevar.librepods.services.SoundRules.Kind.Alert, c, size.minDimension * 0.46f, if (on) Color.Black else ink)
            }
            ControlTile("Island", if (canDraw && IslandPrefs.mini(IslandPrefs.prefs(context))) "On" else "Off", false, true, ink, dark, Modifier.weight(1f), {
                GlintOverlays.showIsland(context, IslandEvent.Glance, expand = true)
            }) { c, _ ->
                val w = size.minDimension * 0.62f; val h = w * 0.42f
                drawRoundRect(ink, Offset(c.x - w / 2f, c.y - h / 2f), Size(w, h), androidx.compose.ui.geometry.CornerRadius(h / 2f))
            }
        }

        // ---- A timer that lives on the Dynamic Island ----
        Column(
            Modifier.fillMaxWidth().background(card, RoundedCornerShape(24.dp)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val t = timer
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(44.dp)) {
                    val orange = Color(0xFFFF9F0A)
                    val r = size.minDimension / 2f - 3.dp.toPx()
                    val st = Stroke(4.dp.toPx(), cap = StrokeCap.Round)
                    drawArc(orange.copy(alpha = 0.22f), 0f, 360f, false, Offset(center.x - r, center.y - r), Size(r * 2, r * 2), style = st)
                    if (t != null && !t.ringing) drawArc(orange, -90f, 360f * (1f - TimerRules.progress(t, now)), false, Offset(center.x - r, center.y - r), Size(r * 2, r * 2), style = st)
                    drawStopwatch(center, r * 0.95f, orange)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        when {
                            t == null -> "Timer"
                            t.ringing -> "Timer done"
                            else -> TimerRules.format(TimerRules.left(t, now))
                        },
                        style = TextStyle(fontFamily = glintFontFamily, fontSize = if (t != null && !t.ringing) 26.sp else 17.sp, fontWeight = FontWeight.SemiBold, color = if (t != null) Color(0xFFFF9F0A) else ink),
                    )
                    Text(
                        when {
                            t == null -> "Pick the minutes. It counts down on the Dynamic Island, whatever app you're in"
                            t.ringing -> "Ringing"
                            t.paused -> "Paused"
                            else -> "On the Dynamic Island now"
                        },
                        style = TextStyle(fontFamily = glintFontFamily, fontSize = 13.sp, lineHeight = 17.sp, color = ink.copy(alpha = 0.6f)),
                    )
                }
            }
            if (t == null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TimerRules.PRESETS.forEach { min ->
                        GlassPillButton(text = "$min", textColor = ink, dark = dark, height = 38.dp, fontSize = 15.sp, horizontalPadding = 0.dp, modifier = Modifier.weight(1f)) {
                            IslandTimer.start(context, min * 60_000L)
                        }
                    }
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (t.ringing) {
                        GlassPillButton(text = "Stop", textColor = ink, dark = dark, height = 40.dp, fontSize = 15.sp, modifier = Modifier.weight(1f)) { IslandTimer.cancel(context) }
                    } else {
                        GlassPillButton(text = if (t.paused) "Resume" else "Pause", textColor = ink, dark = dark, height = 40.dp, fontSize = 15.sp, modifier = Modifier.weight(1f)) {
                            if (t.paused) IslandTimer.resume(context) else IslandTimer.pause(context)
                        }
                        GlassPillButton(text = "Cancel", textColor = ink, dark = dark, height = 40.dp, fontSize = 15.sp, modifier = Modifier.weight(1f)) { IslandTimer.cancel(context) }
                    }
                    GlassPillButton(text = "+1 min", textColor = ink, dark = dark, height = 40.dp, fontSize = 15.sp, modifier = Modifier.weight(1f)) { IslandTimer.addMinute(context) }
                }
            }
        }

        // ---- The battery, and what's coming up ----
        Row(
            Modifier
                .fillMaxWidth()
                .background(card, RoundedCornerShape(24.dp))
                .padding(16.dp)
                .semantics {
                    contentDescription = if (phone.known) "Phone battery ${phone.level} percent" + (if (phone.charging) ", charging" else "") else "Phone battery not read yet"
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BatteryRing(phone, ink, 58.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("This phone", style = TextStyle(fontFamily = glintFontFamily, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = ink))
                Text(
                    when {
                        !phone.known -> "Reading the battery…"
                        phone.charging && phone.level >= 100 -> "Charged"
                        phone.charging && toFull != null -> "Charging, full ${PhoneRules.inWords(toFull)}"
                        phone.charging -> "Charging"
                        else -> "Not charging"
                    },
                    style = TextStyle(fontFamily = glintFontFamily, fontSize = 14.sp, color = ink.copy(alpha = 0.6f)),
                )
                Text(
                    nextAlarm?.let { "Next alarm ${java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(it))}, ${PhoneRules.inWords(it - wall)}" }
                        ?: "No alarm set",
                    style = TextStyle(fontFamily = glintFontFamily, fontSize = 14.sp, color = ink.copy(alpha = 0.6f)),
                )
            }
        }

        // ---- Your headphones (their own tab has everything) ----
        if (navigateToHeadphones != null) HeadphonesCard(ink, card, navigateToHeadphones)

        // Which apps may pop the island (every app on the phone), and a pretend message to see the look.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassPillButton(text = "Your apps", textColor = ink, dark = dark, height = 44.dp, fontSize = 15.sp, modifier = Modifier.weight(1f), onClick = navigateToApps)
            GlassPillButton(text = "Try a message", textColor = ink, dark = dark, height = 44.dp, fontSize = 15.sp, modifier = Modifier.weight(1f)) {
                GlintOverlays.previewMiniIsland(context, message = true)
            }
        }
        Spacer(Modifier.height(bottomPadding))
    }
}

/** One thing the island needs: ticked once it's done, otherwise a button to the right page. */
@Composable
private fun SetupRow(title: String, description: String, done: Boolean, ink: Color, dark: Boolean, onFix: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(24.dp).background(if (done) Color(0xFF30D158) else ink.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (done) Canvas(Modifier.size(12.dp)) {
                drawLine(Color.White, Offset(size.width * 0.1f, size.height * 0.55f), Offset(size.width * 0.4f, size.height * 0.85f), 2.2.dp.toPx(), StrokeCap.Round)
                drawLine(Color.White, Offset(size.width * 0.4f, size.height * 0.85f), Offset(size.width * 0.92f, size.height * 0.15f), 2.2.dp.toPx(), StrokeCap.Round)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = TextStyle(fontFamily = glintFontFamily, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = ink.copy(alpha = if (done) 0.55f else 1f)))
            if (!done) Text(description, style = TextStyle(fontFamily = glintFontFamily, fontSize = 12.sp, lineHeight = 16.sp, color = ink.copy(alpha = 0.55f)))
        }
        if (!done) {
            Spacer(Modifier.width(8.dp))
            GlassPillButton(text = "Allow", textColor = ink, dark = dark, height = 34.dp, fontSize = 14.sp, onClick = onFix)
        }
    }
}

/** A square control: its picture (filled white while on), a name and a state. */
@Composable
private fun ControlTile(
    title: String, state: String, on: Boolean, enabled: Boolean, ink: Color, dark: Boolean, modifier: Modifier,
    onClick: () -> Unit, glyph: androidx.compose.ui.graphics.drawscope.DrawScope.(Offset, Boolean) -> Unit,
) {
    val card = if (dark) Color(0xFF1C1C1E) else Color.White
    Column(
        modifier
            .clip(RoundedCornerShape(24.dp))
            .background(card)
            .then(if (enabled) Modifier.pressable(onClick) else Modifier)
            .semantics { role = Role.Button; contentDescription = "$title, $state" }
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Canvas(Modifier.size(42.dp)) {
            drawCircle(if (on) Color.White else ink.copy(alpha = 0.08f), size.minDimension / 2f)
            if (on && !dark) drawCircle(Color.Black.copy(alpha = 0.1f), size.minDimension / 2f, style = Stroke(1.dp.toPx()))
            glyph(center, on)
        }
        Column {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontFamily = glintFontFamily, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = ink.copy(alpha = if (enabled) 1f else 0.45f)))
            Text(state, maxLines = 1, style = TextStyle(fontFamily = glintFontFamily, fontSize = 13.sp, color = ink.copy(alpha = 0.55f)))
        }
    }
}

/** Your headphones, small: their battery, and a tap to their tab. */
@Composable
private fun HeadphonesCard(ink: Color, card: Color, onOpen: () -> Unit) {
    val chosen by DeviceChoice.chosen.collectAsState()
    val pods by GlintOverlays.snapshot.collectAsState()
    val link by GlintStatus.link.collectAsState()
    val hp by HeadphoneLink.state.collectAsState()
    val connected = if (chosen.isAirPods) link is LinkState.Connected || link is LinkState.GaveUp else hp.connected
    val level = if (chosen.isAirPods) pods.budsLevel else hp.battery
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(card)
            .pressable(onOpen)
            .semantics { role = Role.Button; contentDescription = "${chosen.name}. ${if (connected) "Connected" else "Not connected"}. Opens their tab." }
            .padding(start = 14.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(38.dp)) {
            val r = size.minDimension / 2f - 2.dp.toPx()
            val st = Stroke(3.dp.toPx(), cap = StrokeCap.Round)
            drawArc(ink.copy(alpha = 0.12f), 0f, 360f, false, Offset(center.x - r, center.y - r), Size(r * 2, r * 2), style = st)
            if (connected && level != null) drawArc(ink, -90f, 360f * level / 100f, false, Offset(center.x - r, center.y - r), Size(r * 2, r * 2), style = st)
            if (chosen.isAirPods) drawBudPair(center, r * 1.1f, ink.copy(alpha = if (connected) 1f else 0.4f))
            else drawHeadphones(center, r * 0.5f, ink.copy(alpha = if (connected) 1f else 0.4f))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(chosen.name.ifBlank { "Headphones" }, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontFamily = glintFontFamily, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = ink))
            Text(
                when {
                    !connected -> "Not connected"
                    level != null -> "Connected · $level%"
                    else -> "Connected"
                },
                style = TextStyle(fontFamily = glintFontFamily, fontSize = 13.sp, color = ink.copy(alpha = 0.6f)),
            )
        }
    }
}

/** A battery as a ring with its number in the middle (green while charging, amber low, red very low). */
@Composable
internal fun BatteryRing(info: PhoneStatus.Info, ink: Color, size: Dp) {
    val color = when {
        info.charging -> Color(0xFF30D158)
        info.known && info.level <= 10 -> Color(0xFFFF453A)
        info.known && info.level <= 20 -> Color(0xFFFFB340)
        else -> ink
    }
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val w = this.size.minDimension * 0.11f
            val tl = Offset(w / 2f, w / 2f)
            val sz = Size(this.size.minDimension - w, this.size.minDimension - w)
            drawArc(ink.copy(alpha = 0.12f), 0f, 360f, false, tl, sz, style = Stroke(w, cap = StrokeCap.Round))
            if (info.known) drawArc(color, -90f, 360f * info.level / 100f, false, tl, sz, style = Stroke(w, cap = StrokeCap.Round))
        }
        Text(
            if (info.known) "${info.level}" else "–",
            style = TextStyle(fontFamily = glintFontFamily, fontSize = (size.value * 0.3f).sp, fontWeight = FontWeight.SemiBold, color = ink),
        )
    }
}
