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
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
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
import me.kavishdevar.librepods.presentation.components.StyledSwitch
import me.kavishdevar.librepods.presentation.components.StyledToggle
import me.kavishdevar.librepods.presentation.glint.RowIconTile
import me.kavishdevar.librepods.presentation.glint.RowIcons
import me.kavishdevar.librepods.presentation.overlays.GlassPillButton
import me.kavishdevar.librepods.presentation.overlays.GlintOverlays
import me.kavishdevar.librepods.presentation.overlays.drawKindGlyph
import me.kavishdevar.librepods.presentation.theme.glintFontFamily
import me.kavishdevar.librepods.services.IslandAccess
import me.kavishdevar.librepods.services.IslandPrefs
import me.kavishdevar.librepods.services.NowPlaying
import me.kavishdevar.librepods.services.SoundRules
import me.kavishdevar.librepods.services.SoundSource

/**
 * Settings for "any sound pops the Dynamic Island": the two switches, how long a short sound
 * stays, and an honest card on how well pro can tell which app made a sound (it depends on two
 * optional switches). Used in Settings > Islands and on the This phone page.
 */
@Composable
fun SoundsSection(ink: Color, dark: Boolean) {
    val context = LocalContext.current
    val prefs = remember { IslandPrefs.prefs(context) }
    var anySound by remember { mutableStateOf(IslandPrefs.anySound(prefs)) }
    var icons by remember { mutableStateOf(IslandPrefs.soundIcons(prefs)) }
    var linger by remember { mutableIntStateOf(IslandPrefs.soundLinger(prefs).ordinal) }
    var notifications by remember { mutableStateOf(NowPlaying.hasAccess(context)) }
    val screenAvailable = remember { IslandAccess.isAvailable(context) }
    var screenOn by remember { mutableStateOf(IslandAccess.isEnabled(context)) }
    // Picks up the switches being flipped on Android's own pages while this is open.
    LaunchedEffect(Unit) {
        while (true) {
            val n = NowPlaying.hasAccess(context)
            if (n != notifications) { notifications = n; NowPlaying.attach(context) }
            screenOn = IslandAccess.isEnabled(context)
            delay(1_000)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        StyledList(title = "Sounds") {
            StyledToggle(
                label = "Pop for any sound",
                description = "Message dings, voice notes, calls and alarms, not only music",
                checked = anySound,
                onCheckedChange = { anySound = it; prefs.edit { putBoolean(IslandPrefs.PREF_MINI_ANY_SOUND, it) } },
            )
            StyledToggle(
                label = "Show the app's icon",
                description = "Off: a small symbol for the kind of sound",
                checked = icons,
                onCheckedChange = { icons = it; prefs.edit { putBoolean(IslandPrefs.PREF_SOUND_ICONS, it) } },
            )
        }

        SectionLabel("Short sounds stay for", ink)
        LiquidSegments(
            SoundRules.Linger.entries.map { it.label }, linger,
            { linger = it; prefs.edit { putInt(IslandPrefs.PREF_SOUND_LINGER, it) } },
        )
        Text(
            "Even a half-second ding keeps the Dynamic Island up for ${SoundRules.Linger.entries[linger].ms / 1000.0} s".replace(".0 s", " s"),
            style = TextStyle(fontFamily = glintFontFamily, fontSize = 13.sp, color = ink.copy(alpha = 0.55f)),
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        // How well pro can tell which app it was.
        Column(
            Modifier
                .fillMaxWidth()
                .background(if (dark) Color(0xFF1C1C1E) else Color.White, RoundedCornerShape(24.dp))
                .padding(16.dp)
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Which app made the sound?", style = TextStyle(fontFamily = glintFontFamily, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = ink))
            Text(
                SoundRules.clueSummary(notifications, screenOn),
                style = TextStyle(fontFamily = glintFontFamily, fontSize = 14.sp, lineHeight = 19.sp, color = ink.copy(alpha = 0.75f)),
            )
            ClueRow(
                "Notification access", if (notifications) "On" else "Off", notifications, ink, dark,
                button = if (notifications) null else "Allow",
            ) {
                if (!open(context, NowPlaying.accessSettingsIntent(context))) open(context, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
            if (screenAvailable) ClueRow(
                "Dynamic Island switch", if (screenOn) "On" else "Off", screenOn, ink, dark,
                button = if (screenOn) null else "Turn on",
            ) { open(context, IslandAccess.settingsIntent(context)) }
            Text(
                "pro only notes which app posted a notification and when, and which app is open. It never reads what's inside a notification or on your screen.",
                style = TextStyle(fontFamily = glintFontFamily, fontSize = 12.sp, lineHeight = 16.sp, color = ink.copy(alpha = 0.55f)),
            )
        }
    }
}

@Composable
private fun ClueRow(title: String, status: String, on: Boolean, ink: Color, dark: Boolean, button: String?, onClick: () -> Unit) {
    val dot by animateColorAsState(if (on) Color(0xFF30D158) else Color(0xFFFF9F0A), label = "clueDot")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(dot, CircleShape))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = TextStyle(fontFamily = glintFontFamily, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = ink))
            Text(status, style = TextStyle(fontFamily = glintFontFamily, fontSize = 13.sp, color = ink.copy(alpha = 0.6f)))
        }
        if (button != null) GlassPillButton(text = button, textColor = ink, dark = dark, height = 36.dp, fontSize = 14.sp, onClick = onClick)
    }
}

/**
 * The apps (or kinds of sound) pro has heard lately, newest first, each with a switch: turn an
 * app off and its sounds stop popping the Dynamic Island. Also how Jake can see that detection
 * works: play something or get a message and it appears here.
 */
@Composable
fun RecentSounds(ink: Color, dark: Boolean) {
    val context = LocalContext.current
    val prefs = remember { IslandPrefs.prefs(context) }
    val recent by SoundSource.recent.collectAsState()
    var ignored by remember { mutableStateOf(IslandPrefs.soundIgnored(prefs)) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(20_000); now = System.currentTimeMillis() } }

    Column(
        Modifier
            .fillMaxWidth()
            .background(if (dark) Color(0xFF1C1C1E) else Color.White, RoundedCornerShape(28.dp))
            .padding(vertical = 8.dp)
            .animateContentSize(),
    ) {
        Text(
            "Heard lately",
            style = TextStyle(fontFamily = glintFontFamily, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ink.copy(alpha = 0.6f)),
            modifier = Modifier.padding(start = 18.dp, top = 10.dp, bottom = 4.dp),
        )
        if (recent.isEmpty()) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                RowIconTile(RowIcons.Speaker, ink, dark, size = 38.dp)
                Spacer(Modifier.width(12.dp))
                Text(
                    "Nothing yet. Play something or get a message and it shows up here, with a switch to keep that app quiet.",
                    style = TextStyle(fontFamily = glintFontFamily, fontSize = 14.sp, lineHeight = 19.sp, color = ink.copy(alpha = 0.7f)),
                )
            }
        }
        recent.forEachIndexed { i, seen ->
            // Looked up once per app, not on every redraw.
            val info = remember(seen.pkg) { seen.pkg?.let { SoundSource.appInfo(context, it) } }
            val name = info?.label ?: seen.pkg ?: "${seen.kind.label} sound"
            val times = if (seen.count > 1) " · ${seen.count} times" else ""
            val sub = if (seen.pkg == null) "App unknown · ${SoundRules.ago(now - seen.at)}$times"
            else "${seen.kind.label} · ${SoundRules.ago(now - seen.at)}$times"
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 9.dp)
                    .semantics { contentDescription = "$name. $sub" },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppBadge(info?.icon, seen.kind, 38.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, maxLines = 1, style = TextStyle(fontFamily = glintFontFamily, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = ink))
                    Text(sub, maxLines = 1, style = TextStyle(fontFamily = glintFontFamily, fontSize = 13.sp, color = ink.copy(alpha = 0.6f)))
                }
                // Only an app pro can name can be switched off.
                if (seen.pkg != null) StyledSwitch(
                    checked = seen.pkg !in ignored,
                    onCheckedChange = { show ->
                        IslandPrefs.setSoundIgnored(prefs, seen.pkg, !show)
                        ignored = IslandPrefs.soundIgnored(prefs)
                        GlintOverlays.refreshMiniIsland(context)
                    },
                )
            }
            if (i < recent.lastIndex) Box(Modifier.padding(start = 66.dp).fillMaxWidth().height(0.5.dp).background(ink.copy(alpha = 0.08f)))
        }
    }
}

/** An app's icon in a circle (a symbol for the kind of sound when the app isn't known). */
@Composable
internal fun AppBadge(icon: ImageBitmap?, kind: SoundRules.Kind, size: Dp) {
    val shape = CircleShape
    Box(
        Modifier.size(size).clip(shape).background(Color(0xFF2C2C2E), shape),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Image(icon, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(size))
        } else {
            Canvas(Modifier.size(size * 0.62f)) { drawKindGlyph(kind, center, this.size.minDimension, Color.White.copy(alpha = 0.92f)) }
        }
    }
}
