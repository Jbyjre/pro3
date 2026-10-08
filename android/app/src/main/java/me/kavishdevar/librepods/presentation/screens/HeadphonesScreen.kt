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

package me.kavishdevar.librepods.presentation.screens

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import me.kavishdevar.librepods.presentation.components.StyledList
import me.kavishdevar.librepods.presentation.components.StyledListItem
import me.kavishdevar.librepods.presentation.components.StyledSlider
import me.kavishdevar.librepods.presentation.glint.BatteryRing
import me.kavishdevar.librepods.presentation.glint.CapsuleShape
import me.kavishdevar.librepods.presentation.glint.GlassTier
import me.kavishdevar.librepods.presentation.glint.GlintColors
import me.kavishdevar.librepods.presentation.glint.GlintComfort
import me.kavishdevar.librepods.presentation.glint.GlintSymbols
import me.kavishdevar.librepods.presentation.glint.drawHeadphones
import me.kavishdevar.librepods.presentation.glint.drawStudio
import me.kavishdevar.librepods.presentation.glint.glintGlass
import me.kavishdevar.librepods.presentation.glint.pressable
import me.kavishdevar.librepods.presentation.glint.riseIn
import me.kavishdevar.librepods.presentation.overlays.PlayPauseButton
import me.kavishdevar.librepods.presentation.overlays.SkipButton
import me.kavishdevar.librepods.presentation.theme.glintFontFamily
import me.kavishdevar.librepods.services.DeviceKind
import me.kavishdevar.librepods.services.HeadphoneLink
import me.kavishdevar.librepods.services.HeadphoneState
import me.kavishdevar.librepods.services.NowPlaying
import me.kavishdevar.librepods.services.VolumeGuard

/**
 * The main page while headphones other than AirPods are chosen (Beats Solo 4 or any other).
 * Only controls that really work for them: battery, music, volume and its limit, the islands.
 * The Solo 4 also gets a guide to its own buttons, which work by themselves.
 */
@Composable
fun HeadphonesRoute(
    topPadding: Dp,
    bottomPadding: Dp,
    navigateToDevices: () -> Unit,
    navigateToIsland: () -> Unit,
) {
    val state by HeadphoneLink.state.collectAsState()
    val track by NowPlaying.state.collectAsState()
    val context = LocalContext.current
    HeadphonesScreen(
        state = state,
        track = track,
        topPadding = topPadding,
        bottomPadding = bottomPadding,
        onRefresh = { HeadphoneLink.refresh(context) },
        onOpenBluetooth = {
            runCatching {
                context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        },
        navigateToDevices = navigateToDevices,
        navigateToIsland = navigateToIsland,
    )
}

@Composable
fun HeadphonesScreen(
    state: HeadphoneState,
    track: NowPlaying.Track,
    topPadding: Dp = 16.dp,
    bottomPadding: Dp = 16.dp,
    onRefresh: () -> Unit = {},
    onOpenBluetooth: () -> Unit = {},
    navigateToDevices: () -> Unit = {},
    navigateToIsland: () -> Unit = {},
) {
    val device = state.device
    val solo4 = device?.kind == DeviceKind.BEATS_SOLO_4
    val name = device?.name ?: "Headphones"
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 16.dp)
    ) {
        item(key = "top") { Spacer(Modifier.height(topPadding)) }
        item(key = "hero") {
            HeadphonesHero(state, onRefresh = onRefresh, onOpenBluetooth = onOpenBluetooth)
        }
        if (state.connected) {
            item(key = "music") {
                Spacer(Modifier.height(16.dp))
                NowPlayingCard(track)
            }
            item(key = "volume") {
                Spacer(Modifier.height(16.dp))
                MediaVolume()
            }
        }
        item(key = "limit") {
            Spacer(Modifier.height(16.dp))
            VolumeLimitSection(deviceLabel = "your $name")
        }
        if (solo4) {
            item(key = "buttons") {
                Spacer(Modifier.height(16.dp))
                Solo4Buttons()
            }
        }
        item(key = "more") {
            Spacer(Modifier.height(16.dp))
            StyledList(title = "pro") {
                StyledListItem(
                    name = "Your devices",
                    description = "Following $name. Tap to switch",
                    onClick = navigateToDevices,
                )
                StyledListItem(
                    name = "Dynamic Island",
                    description = "Battery and music around the camera",
                    onClick = navigateToIsland,
                )
            }
        }
        item(key = "note") {
            val dark = isSystemInDarkTheme()
            Text(
                "Noise control, ear detection and heart rate are AirPods features, so they're hidden here. " +
                    when (state.source) {
                        HeadphoneState.BatterySource.Android -> "Battery comes from Android."
                        HeadphoneState.BatterySource.Beacon -> "Battery comes from the headphones' own Bluetooth signal."
                        HeadphoneState.BatterySource.None -> "Battery shows once the headphones report it."
                    },
                style = TextStyle(fontFamily = glintFontFamily, fontSize = 13.sp, lineHeight = 18.sp, color = (if (dark) Color.White else Color.Black).copy(alpha = 0.55f)),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
            )
        }
        item(key = "bottom") { Spacer(Modifier.height(bottomPadding)) }
    }
}

/**
 * The hero: your headphones inside a glass pearl with the battery ring around it (the same
 * ring-around-a-pearl as pro's icon), on a soft studio light the glass can bend. Pressing the
 * pearl checks the headphones again.
 */
@Composable
private fun HeadphonesHero(state: HeadphoneState, onRefresh: () -> Unit, onOpenBluetooth: () -> Unit) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val reduceMotion = remember { GlintComfort.reduceMotion(context) }
    val solid = remember { GlintComfort.reduceTransparency(context) }
    val ink = if (dark) Color.White else Color.Black
    val muted = ink.copy(alpha = 0.6f)
    val backdrop = rememberLayerBackdrop()
    val level = state.battery
    Box(Modifier.fillMaxWidth().riseIn().clip(RoundedCornerShape(32.dp))) {
        // Neutral light only (black, white and grey): the studio's green trace sits off the card.
        Canvas(Modifier.matchParentSize().layerBackdrop(backdrop)) { drawStudio(dark, keyY = 0.18f, glowY = 1.6f) }
        Column(
            Modifier.fillMaxWidth().padding(top = 26.dp, bottom = 22.dp, start = 20.dp, end = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val source = remember { MutableInteractionSource() }
            val pressed by source.collectIsPressedAsState()
            val swell by animateFloatAsState(if (pressed && !reduceMotion) 1.06f else 1f, spring(dampingRatio = 0.42f, stiffness = 420f), label = "pearl")
            val view = LocalView.current
            Box(Modifier.size(196.dp), contentAlignment = Alignment.Center) {
                BatteryRing(
                    level = if (state.connected) level else null,
                    charging = false,
                    size = 196.dp,
                    stroke = 6.dp,
                    track = ink.copy(alpha = if (dark) 0.14f else 0.08f),
                    showLabel = false,
                    centerBolt = false,
                )
                Box(
                    Modifier
                        .size(152.dp)
                        .graphicsLayer { scaleX = swell; scaleY = swell }
                        .glintGlass(
                            backdrop, dark, solid, CircleShape, GlassTier.Floating,
                            tint = if (dark) Color.White.copy(alpha = if (pressed) 0.12f else 0.05f) else Color.White.copy(alpha = if (pressed) 0.6f else 0.38f)
                        )
                        .clip(CircleShape)
                        .semantics { role = Role.Button; contentDescription = "Check the headphones again" }
                        .clickable(interactionSource = source, indication = null) {
                            view.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
                            onRefresh()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    val art by animateFloatAsState(if (state.connected) 1f else 0.45f, if (reduceMotion) tween(0) else spring(1f, 200f), label = "artAlpha")
                    Canvas(Modifier.size(84.dp)) {
                        drawHeadphones(center, size.minDimension * 0.46f, ink.copy(alpha = 0.86f * art))
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            AnimatedContent(
                targetState = if (state.connected) level else -1,
                transitionSpec = {
                    (fadeIn(if (reduceMotion) tween(0) else tween(220, 60))) togetherWith fadeOut(tween(if (reduceMotion) 0 else 120))
                },
                label = "battery",
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            ) { shown ->
                Text(
                    when {
                        shown == null -> "–"
                        shown < 0 -> "–"
                        else -> "$shown%"
                    },
                    style = TextStyle(fontFamily = glintFontFamily, fontSize = 44.sp, fontWeight = FontWeight.SemiBold, color = ink, fontFeatureSettings = "tnum")
                )
            }
            Text(
                state.device?.name ?: "Headphones",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(fontFamily = glintFontFamily, fontSize = 17.sp, fontWeight = FontWeight.Medium, color = ink, textAlign = TextAlign.Center)
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(if (state.connected) GlintColors.Green else GlintColors.Amber, CircleShape))
                Spacer(Modifier.width(8.dp))
                Text(
                    when {
                        !state.connected -> "Not connected"
                        level == null -> "Connected · battery not reported yet"
                        else -> "Connected"
                    },
                    style = TextStyle(fontFamily = glintFontFamily, fontSize = 13.sp, color = muted)
                )
            }
            AnimatedVisibility(!state.connected) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(16.dp))
                    Box(
                        Modifier
                            .glintGlass(backdrop, dark, solid, CapsuleShape, GlassTier.Inline, tint = if (dark) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.5f))
                            .clip(CapsuleShape)
                            .semantics { role = Role.Button }
                            .pressable(onOpenBluetooth)
                            .padding(horizontal = 22.dp, vertical = 11.dp),
                    ) {
                        Text("Open Bluetooth settings", style = TextStyle(fontFamily = glintFontFamily, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = ink))
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Turn them on: pro picks them up by itself.",
                        style = TextStyle(fontFamily = glintFontFamily, fontSize = 13.sp, color = muted, textAlign = TextAlign.Center)
                    )
                }
            }
        }
    }
}

/** What's playing, with back, play/pause and next (media keys, so any music app follows). */
@Composable
private fun NowPlayingCard(track: NowPlaying.Track) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val reduceMotion = remember { GlintComfort.reduceMotion(context) }
    val ink = if (dark) Color.White else Color.Black
    val (title, subtitle) = remember(track) { NowPlaying.words(track) }
    Column(
        Modifier
            .fillMaxWidth()
            .riseIn()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(28.dp))
            .padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val art = track.art
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(ink.copy(alpha = 0.07f)),
                contentAlignment = Alignment.Center
            ) {
                if (art != null) {
                    Image(art, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.matchParentSize())
                } else {
                    Image(
                        me.kavishdevar.librepods.presentation.glint.RowIcons.Note, contentDescription = null,
                        colorFilter = ColorFilter.tint(ink.copy(alpha = 0.6f)), modifier = Modifier.size(24.dp)
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontFamily = glintFontFamily, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = ink))
                Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontFamily = glintFontFamily, fontSize = 13.sp, color = ink.copy(alpha = 0.6f)))
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            SkipButton(next = false, color = ink, dark = dark, enabled = true, onClick = { NowPlaying.skip(context, next = false) })
            Spacer(Modifier.width(22.dp))
            PlayPauseButton(
                playing = track.playing, color = ink, size = 48.dp, glyph = 18.dp, reduceMotion = reduceMotion,
                glass = true, dark = dark, onClick = { NowPlaying.playPause(context) }
            )
            Spacer(Modifier.width(22.dp))
            SkipButton(next = true, color = ink, dark = dark, enabled = true, onClick = { NowPlaying.skip(context, next = true) })
        }
    }
}

/** The phone's media volume (what the headphones play at), kept in step with the volume keys. */
@Composable
private fun MediaVolume() {
    val context = LocalContext.current
    val am = remember { context.getSystemService(AudioManager::class.java) }
    val max = remember { am?.getStreamMaxVolume(AudioManager.STREAM_MUSIC)?.coerceAtLeast(1) ?: 15 }
    var volume by remember { mutableIntStateOf(am?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0) }
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                volume = am?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: volume
            }
        }
        context.registerReceiver(receiver, IntentFilter("android.media.VOLUME_CHANGED_ACTION"), Context.RECEIVER_EXPORTED)
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    StyledSlider(
        label = "Volume",
        value = volume.toFloat(),
        onValueChange = { v ->
            val step = v.toInt().coerceIn(0, max)
            if (step != volume) {
                volume = step
                runCatching { am?.setStreamVolume(AudioManager.STREAM_MUSIC, step, 0) }
                VolumeGuard.check()
            }
        },
        valueRange = 0f..max.toFloat(),
        startIcon = String(Character.toChars(0x1002A1)),
        endIcon = String(Character.toChars(0x1002A9)),
        independent = true,
    )
}

/**
 * The Beats Solo 4's own buttons. They work on their own (Beats sends the phone ordinary media
 * commands); this only shows what each press does. From Apple's Solo 4 guide: the "b" button
 * plays or pauses with one press, skips forward with two and back with three; pressing above
 * or below it changes the volume.
 */
@Composable
private fun Solo4Buttons() {
    val dark = isSystemInDarkTheme()
    val ink = if (dark) Color.White else Color.Black
    val rows = listOf(
        Triple(1, "Press the \"b\" button once", "Play or pause"),
        Triple(2, "Press the \"b\" button twice", "Next song"),
        Triple(3, "Press it three times", "Previous song"),
        Triple(0, "Press above or below it", "Volume up or down"),
    )
    StyledList(title = "Buttons on your Solo 4") {
        rows.forEach { (presses, name, what) ->
            StyledListItem(
                name = name,
                description = what,
                leadingContent = { PressPips(presses, ink, dark) },
                trailingContent = {},
            )
        }
    }
}

/** A small tile with one dot per press (or an up/down pair for the volume), like a tap rhythm. */
@Composable
private fun PressPips(presses: Int, ink: Color, dark: Boolean) {
    Box(
        Modifier.size(30.dp).background(ink.copy(alpha = if (dark) 0.13f else 0.07f), RoundedCornerShape(9.dp)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(20.dp)) {
            val c = ink.copy(alpha = 0.85f)
            if (presses == 0) {
                val w = size.width * 0.28f
                val cx = size.width / 2f
                fun chevron(y: Float, up: Boolean) {
                    val d = if (up) -1f else 1f
                    drawLine(c, Offset(cx - w, y - d * w * 0.5f), Offset(cx, y + d * w * 0.5f), 2.dp.toPx(), androidx.compose.ui.graphics.StrokeCap.Round)
                    drawLine(c, Offset(cx, y + d * w * 0.5f), Offset(cx + w, y - d * w * 0.5f), 2.dp.toPx(), androidx.compose.ui.graphics.StrokeCap.Round)
                }
                chevron(size.height * 0.3f, up = true)
                chevron(size.height * 0.72f, up = false)
            } else {
                val r = size.minDimension * 0.11f
                val gap = r * 3.1f
                val start = size.width / 2f - gap * (presses - 1) / 2f
                for (i in 0 until presses) drawCircle(c, r, Offset(start + i * gap, size.height / 2f))
            }
        }
    }
}

/** The headphones glyph as a list-row tile (used by the device list). */
@Composable
internal fun HeadphonesTile(ink: Color, dark: Boolean, size: Dp = 30.dp) {
    Box(
        Modifier.size(size).background(ink.copy(alpha = if (dark) 0.13f else 0.07f), RoundedCornerShape(size * 0.3f)),
        contentAlignment = Alignment.Center
    ) {
        Image(GlintSymbols.Headphones, contentDescription = null, colorFilter = ColorFilter.tint(ink.copy(alpha = 0.88f)), modifier = Modifier.size(size * 0.62f))
    }
}
