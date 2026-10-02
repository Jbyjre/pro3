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

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import kotlinx.coroutines.delay
import me.kavishdevar.librepods.presentation.components.LiquidSegments
import me.kavishdevar.librepods.presentation.components.StyledList
import me.kavishdevar.librepods.presentation.components.StyledToggle
import me.kavishdevar.librepods.presentation.overlays.GlassPillButton
import me.kavishdevar.librepods.presentation.overlays.GlintOverlays
import me.kavishdevar.librepods.presentation.overlays.IslandEvent
import me.kavishdevar.librepods.presentation.theme.glintFontFamily
import me.kavishdevar.librepods.services.IslandAccess
import me.kavishdevar.librepods.services.IslandPrefs
import me.kavishdevar.librepods.services.NowPlaying

/**
 * Settings > Islands: the Dynamic Island around the camera, which moments make the mini island appear, music controls and song names,
 * how long it stays, haptics, and buttons to try each look.
 */
@Composable
fun IslandSettingsScreen() {
    val context = LocalContext.current
    val prefs = remember { IslandPrefs.prefs(context) }
    val dark = isSystemInDarkTheme()
    val ink = if (dark) Color.White else Color.Black
    val topPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 84.dp
    val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp

    var master by remember { mutableStateOf(prefs.getBoolean(IslandPrefs.PREF_MASTER, true)) }
    val triggers = remember {
        mutableStateMapOf<IslandPrefs.Trigger, Boolean>().apply {
            IslandPrefs.Trigger.entries.forEach { put(it, prefs.getBoolean(it.key, it.default)) }
        }
    }
    var songNames by remember { mutableStateOf(IslandPrefs.songNames(prefs)) }
    var access by remember { mutableStateOf(NowPlaying.hasAccess(context)) }
    var duration by remember { mutableIntStateOf(IslandPrefs.duration(prefs).ordinal) }
    var haptics by remember { mutableStateOf(IslandPrefs.haptics(prefs)) }
    var mini by remember { mutableStateOf(IslandPrefs.mini(prefs)) }
    var miniNames by remember { mutableStateOf(IslandPrefs.miniNames(prefs)) }
    var miniAirPodsOnly by remember { mutableStateOf(IslandPrefs.miniAirPodsOnly(prefs)) }
    var miniAlways by remember { mutableStateOf(IslandPrefs.miniAlways(prefs)) }
    var canDraw by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    val tapsAvailable = remember { IslandAccess.isAvailable(context) }
    var tapsEnabled by remember { mutableStateOf(IslandAccess.isEnabled(context)) }
    val tapsService by IslandAccess.service.collectAsState()

    // Picks up changes made on Android's settings pages while this screen is open or returning.
    LaunchedEffect(Unit) {
        while (true) {
            val now = NowPlaying.hasAccess(context)
            if (now != access) { access = now; NowPlaying.attach(context) }
            val draw = Settings.canDrawOverlays(context)
            // Just allowed "Display over other apps": the mini island can appear now.
            if (draw && !canDraw) GlintOverlays.refreshMiniIsland(context)
            canDraw = draw
            tapsEnabled = IslandAccess.isEnabled(context)
            delay(1_000)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(Modifier.height(topPadding))

        if (tapsAvailable) TapAccessCard(enabled = tapsEnabled, running = tapsService != null, ink = ink, dark = dark)

        StyledList(title = "Mini island") {
            StyledToggle(
                label = "Show the mini island",
                description = if (canDraw) "Pop-ups when something changes (connected, music paused, low battery), growing out of the Dynamic Island"
                else "Needs \"Display over other apps\" for pro",
                checked = master,
                onCheckedChange = { master = it; prefs.edit { putBoolean(IslandPrefs.PREF_MASTER, it) } },
            )
        }
        if (!canDraw) {
            Hint(
                "Android hasn't allowed pro to draw over other apps yet, so the islands can't appear.",
                "Allow", ink, dark,
            ) { open(context, Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).setData(android.net.Uri.fromParts("package", context.packageName, null))) }
        }

        StyledList(title = "Dynamic Island") {
            StyledToggle(
                label = "Dynamic Island around the camera",
                description = "A small pill around the front camera. With music: the cover and moving bars (swipe to change song). " +
                    "With AirPods: their battery and listening mode, or your heart rate. Tap once to expand, twice to play/pause, three times to skip; hold to open pro. " +
                    "The mini island pop-ups grow out of it and shrink back into it.",
                checked = mini,
                onCheckedChange = {
                    mini = it
                    prefs.edit { putBoolean(IslandPrefs.PREF_MINI, it) }
                    GlintOverlays.refreshMiniIsland(context)
                },
            )
            StyledToggle(
                label = "Always while AirPods are connected",
                description = "Off: only while music plays",
                checked = miniAlways,

                onCheckedChange = {
                    miniAlways = it
                    prefs.edit { putBoolean(IslandPrefs.PREF_MINI_ALWAYS, it) }
                    GlintOverlays.refreshMiniIsland(context)
                },
            )
            StyledToggle(
                label = "Show each new song's name",
                description = if (songNames && access) "It widens for a moment when a song starts or changes"
                else "Needs \"Show song names\" and Notification access (under Music)",
                checked = miniNames,
                onCheckedChange = { miniNames = it; prefs.edit { putBoolean(IslandPrefs.PREF_MINI_NAMES, it) } },
            )
            StyledToggle(
                label = "Only with AirPods connected",
                description = "Off: shows for music on any speaker or headphones",
                checked = miniAirPodsOnly,

                onCheckedChange = {
                    miniAirPodsOnly = it
                    prefs.edit { putBoolean(IslandPrefs.PREF_MINI_AIRPODS_ONLY, it) }
                    GlintOverlays.refreshMiniIsland(context)
                },
            )
        }

        StyledList(title = "When the mini island appears") {
            IslandPrefs.Trigger.entries.forEach { t ->
                // Every switch can always be changed (greyed-out switches looked stuck); a note says
                // when one also needs something else to work.
                StyledToggle(
                    label = t.label,
                    description = when {
                        t == IslandPrefs.Trigger.SongChanges && !(songNames && access) -> "Needs song names and Notification access (under Music)"
                        else -> t.description
                    },
                    checked = triggers[t] == true,
                    enabled = master,
                    onCheckedChange = { on -> triggers[t] = on; prefs.edit { putBoolean(t.key, on) } },
                )
            }
        }

        StyledList(title = "Music") {
            StyledToggle(
                label = "Show song names",
                description = when {
                    !songNames -> "Play and pause still work, without song names"
                    access -> "Song, artist and cover from your music app"
                    else -> "Needs Notification access (below)"
                },
                checked = songNames,
                onCheckedChange = {
                    songNames = it
                    prefs.edit { putBoolean(IslandPrefs.PREF_SONG_NAMES, it) }
                    if (it) NowPlaying.attach(context) else NowPlaying.detach()
                },
            )
        }
        if (songNames && !access) {
            Hint(
                "To show what's playing, Android needs you to give pro Notification access. " +
                    "pro only uses it to read the song and use your music app's controls.",
                "Allow", ink, dark,
            ) {
                if (!open(context, NowPlaying.accessSettingsIntent(context))) {
                    open(context, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }
            }
            Hint(
                "If Android says it's a \"Restricted setting\": open App info, tap the ⋮ menu, " +
                    "choose \"Allow restricted settings\", then tap Allow above again.",
                "App info", ink, dark,
            ) { open(context, NowPlaying.appInfoIntent(context)) }
        }

        SectionLabel("Stays on screen for", ink)
        LiquidSegments(
            IslandPrefs.Duration.entries.map { it.label }, duration,
            { duration = it; prefs.edit { putInt(IslandPrefs.PREF_DURATION, it) } },
        )
        Text(
            IslandPrefs.Duration.entries[duration].let { "About ${it.compactMs / 1000.0} s, or ${it.expandedMs / 1000} s when opened. Touching it keeps it up." }
                .replace(".0 s", " s"),
            style = TextStyle(fontFamily = glintFontFamily, fontSize = 13.sp, color = ink.copy(alpha = 0.55f)),
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        StyledList(title = "Feel") {
            StyledToggle(
                label = "Haptics",
                description = "A soft tap when it appears and when you touch it",
                checked = haptics,
                onCheckedChange = { haptics = it; prefs.edit { putBoolean(IslandPrefs.PREF_HAPTICS, it) } },
            )
        }

        SectionLabel("Try it", ink)
        @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
        FlowRow(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val tries = listOf(
                "Connected" to IslandEvent.Connected,
                "AirPod out" to IslandEvent.BudOut(remaining = 1, paused = true),
                "Music" to IslandEvent.Music,
                "Charging" to IslandEvent.Charging,
                "Low battery" to IslandEvent.LowBattery(18),
            )
            tries.forEach { (label, event) ->
                GlassPillButton(text = label, textColor = ink, dark = dark, height = 40.dp, fontSize = 15.sp) {
                    GlintOverlays.showIsland(context, event)
                }
            }
            GlassPillButton(text = "Dynamic Island", textColor = ink, dark = dark, height = 40.dp, fontSize = 15.sp) {
                GlintOverlays.previewMiniIsland(context)
            }
        }
        Text(
            "Tap a pop-up to open it, hold it to open pro, swipe it up to put it away. " +
                "The Dynamic Island stays while your AirPods are connected (also sideways and on the lock screen, if your phone allows it there); it only steps aside in full-screen videos and games. " +
                "Tap it once to expand, twice to play or pause, three times for the next song. " +
                "While you measure heart rate, the heart shows inside the opened island; tap it for what the number means.",
            style = TextStyle(fontFamily = glintFontFamily, fontSize = 13.sp, color = ink.copy(alpha = 0.55f)),
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(bottomPadding))
    }
}

/**
 * Whether the Dynamic Island can be tapped, and the one switch that makes it so: Android gives
 * every touch around the camera to its status bar unless the island sits above it, which only
 * an accessibility service may do.
 */
@Composable
private fun TapAccessCard(enabled: Boolean, running: Boolean, ink: Color, dark: Boolean) {
    val context = LocalContext.current
    val on = enabled && running
    val dot by androidx.compose.animation.animateColorAsState(
        when {
            on -> Color(0xFF30D158)
            enabled -> Color(0xFFFFB340)
            else -> Color(0xFFFF9F0A)
        },
        label = "tapDot",
    )
    Column(
        Modifier
            .fillMaxWidth()
            .background(if (dark) Color(0xFF1C1C1E) else Color.White, RoundedCornerShape(24.dp))
            .padding(16.dp)
            .animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            me.kavishdevar.librepods.presentation.glint.RowIconTile(me.kavishdevar.librepods.presentation.glint.RowIcons.Press, ink, dark, size = 38.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Tap the Dynamic Island", style = TextStyle(fontFamily = glintFontFamily, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = ink))
                androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    androidx.compose.foundation.layout.Box(Modifier.size(8.dp).background(dot, androidx.compose.foundation.shape.CircleShape))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        when {
                            on -> "On"
                            enabled -> "Starting…"
                            else -> "Off"
                        },
                        style = TextStyle(fontFamily = glintFontFamily, fontSize = 13.sp, color = ink.copy(alpha = 0.6f)),
                    )
                }
            }
            if (!on) GlassPillButton(text = "Turn on", textColor = ink, dark = dark, height = 40.dp, fontSize = 15.sp) {
                open(context, IslandAccess.settingsIntent(context))
            }
        }
        if (!on) {
            Text(
                "Android gives taps around the camera to its status bar. Turn on \"pro Dynamic Island\" in Accessibility and they reach the island.",
                style = TextStyle(fontFamily = glintFontFamily, fontSize = 13.sp, lineHeight = 18.sp, color = ink.copy(alpha = 0.7f)),
            )
            androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(
                    "\"Restricted setting\"? App info › ⋮ › Allow restricted settings, then Turn on again.",
                    style = TextStyle(fontFamily = glintFontFamily, fontSize = 12.sp, lineHeight = 16.sp, color = ink.copy(alpha = 0.55f)),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                GlassPillButton(text = "App info", textColor = ink, dark = dark, height = 34.dp, fontSize = 13.sp) {
                    open(context, NowPlaying.appInfoIntent(context))
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String, ink: Color) {
    Text(
        text,
        style = TextStyle(fontFamily = glintFontFamily, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ink.copy(alpha = 0.6f)),
        modifier = Modifier.padding(start = 16.dp, top = 8.dp),
    )
}

/** A short explanation on a soft card with one button. */
@Composable
private fun Hint(text: String, button: String, ink: Color, dark: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(if (dark) Color(0xFF1C1C1E) else Color.White, RoundedCornerShape(24.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text, style = TextStyle(fontFamily = glintFontFamily, fontSize = 14.sp, lineHeight = 19.sp, color = ink.copy(alpha = 0.8f)))
        GlassPillButton(text = button, textColor = ink, dark = dark, height = 40.dp, fontSize = 15.sp, onClick = onClick)
    }
}

private fun open(context: Context, intent: Intent): Boolean = try {
    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
} catch (_: Exception) { false }
