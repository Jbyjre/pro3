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

import android.content.Intent
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import kotlinx.coroutines.delay
import me.kavishdevar.librepods.presentation.components.LiquidSegments
import me.kavishdevar.librepods.presentation.components.StyledList
import me.kavishdevar.librepods.presentation.components.StyledToggle
import me.kavishdevar.librepods.presentation.overlays.GlassPillButton
import me.kavishdevar.librepods.presentation.overlays.GlintOverlays
import me.kavishdevar.librepods.presentation.theme.glintFontFamily
import me.kavishdevar.librepods.services.IslandLook
import me.kavishdevar.librepods.services.IslandPrefs
import me.kavishdevar.librepods.services.PhoneStatus

/**
 * This phone: everything pro does on its own, with no headphones involved. The live Dynamic
 * Island on top (switch what it's showing to see each look), the phone's battery, the two
 * switches that matter, how sounds are handled, and what was heard lately.
 */
@Composable
fun PhoneScreen(navigateToIsland: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { IslandPrefs.prefs(context) }
    val dark = isSystemInDarkTheme()
    val ink = if (dark) Color.White else Color.Black
    val card = if (dark) Color(0xFF1C1C1E) else Color.White
    val topPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 84.dp
    val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp

    var shown by remember { mutableIntStateOf(0) }
    val previews = listOf(IslandLook.Situation.Rest, IslandLook.Situation.Sound, IslandLook.Situation.Music)
    var mini by remember { mutableStateOf(IslandPrefs.mini(prefs)) }
    var anytime by remember { mutableStateOf(IslandPrefs.miniAnytime(prefs)) }
    val phone by PhoneStatus.state.collectAsState()
    var canDraw by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    // Picks up "Display over other apps" being allowed on Android's page, and brings the island up at once.
    LaunchedEffect(Unit) {
        while (true) {
            val draw = Settings.canDrawOverlays(context)
            if (draw && !canDraw) GlintOverlays.refreshMiniIsland(context)
            canDraw = draw
            delay(1_000)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(topPadding))

        // The live Dynamic Island, in the three looks that matter without headphones.
        Column(
            Modifier.fillMaxWidth().background(card, RoundedCornerShape(28.dp)).padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            LiveIslandStrip(previews[shown], dark)
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LiquidSegments(
                    listOf("Nothing on", "A sound", "Music"), shown, { shown = it },
                    track = ink.copy(alpha = if (dark) 0.08f else 0.05f),
                )
                Text(
                    "This is how the real one looks around your camera. Change what it shows under Customize.",
                    style = TextStyle(fontFamily = glintFontFamily, fontSize = 13.sp, lineHeight = 18.sp, color = ink.copy(alpha = 0.55f)),
                )
            }
        }

        // The phone's own battery: the same ring the Dynamic Island draws.
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
                        phone.charging -> "Charging"
                        else -> "Not charging"
                    },
                    style = TextStyle(fontFamily = glintFontFamily, fontSize = 14.sp, color = ink.copy(alpha = 0.6f)),
                )
            }
        }

        StyledList(title = "Dynamic Island") {
            StyledToggle(
                label = "Dynamic Island around the camera",
                description = "On top of every app, no headphones needed",
                checked = mini,
                onCheckedChange = {
                    mini = it
                    prefs.edit { putBoolean(IslandPrefs.PREF_MINI, it) }
                    GlintOverlays.refreshMiniIsland(context)
                },
            )
            StyledToggle(
                label = "Always on",
                description = "Stays round the camera with the battery and time, even when nothing plays",
                checked = anytime,
                onCheckedChange = {
                    anytime = it
                    prefs.edit { putBoolean(IslandPrefs.PREF_MINI_ANYTIME, it) }
                    GlintOverlays.refreshMiniIsland(context)
                },
            )
        }

        if (!canDraw) {
            Hint("pro isn't allowed to draw over other apps yet, so the Dynamic Island can't show.", "Allow", ink, dark) {
                open(context, Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).setData(android.net.Uri.fromParts("package", context.packageName, null)))
            }
        }

        SoundsSection(ink, dark)
        RecentSounds(ink, dark)

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassPillButton(text = "Customize", textColor = ink, dark = dark, height = 44.dp, fontSize = 15.sp, modifier = Modifier.weight(1f), onClick = navigateToIsland)
            GlassPillButton(text = "Try a message ding", textColor = ink, dark = dark, height = 44.dp, fontSize = 15.sp, modifier = Modifier.weight(1f)) {
                GlintOverlays.previewMiniIsland(context, sound = true)
            }
        }
        Spacer(Modifier.height(bottomPadding))
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
