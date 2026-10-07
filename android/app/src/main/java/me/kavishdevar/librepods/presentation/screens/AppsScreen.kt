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
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.kavishdevar.librepods.presentation.components.StyledInputField
import me.kavishdevar.librepods.presentation.components.StyledSwitch
import me.kavishdevar.librepods.presentation.overlays.GlintOverlays
import me.kavishdevar.librepods.presentation.theme.glintFontFamily
import me.kavishdevar.librepods.services.IslandPrefs
import me.kavishdevar.librepods.services.SoundRules
import me.kavishdevar.librepods.services.SoundSource

/** An app you can open, as listed on the Apps page. */
data class AppEntry(val pkg: String, val label: String)

/**
 * Every app on the phone that has an icon on the home screen, by name (not pro itself). Asks
 * Android once, so call it off the main thread.
 */
internal fun launchableApps(context: Context): List<AppEntry> {
    val pm = context.packageManager
    val found = runCatching {
        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
    }.getOrDefault(emptyList())
    return found.asSequence()
        .map { it.activityInfo.packageName to runCatching { it.loadLabel(pm).toString() }.getOrDefault(it.activityInfo.packageName) }
        .filter { (pkg, _) -> pkg != context.packageName }
        .distinctBy { it.first }
        .map { (pkg, label) -> AppEntry(pkg, label) }
        .sortedBy { it.label.lowercase() }
        .toList()
}

/**
 * Settings > Islands > Apps: the apps on this phone, each with a switch for whether its sounds
 * and messages may pop the Dynamic Island. Same switches as "Heard lately" (turning WhatsApp
 * off there is turning it off here). The list is whatever is installed, so it reflects this
 * phone, and loads in the background.
 */
@Composable
fun AppsScreen(preset: List<AppEntry>? = null) {
    val context = LocalContext.current
    val prefs = remember { IslandPrefs.prefs(context) }
    val dark = isSystemInDarkTheme()
    val ink = if (dark) Color.White else Color.Black
    val card = if (dark) Color(0xFF1C1C1E) else Color.White
    val topPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 84.dp
    val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp

    val apps by produceState(initialValue = preset ?: emptyList(), preset) {
        if (preset == null) value = withContext(Dispatchers.IO) { launchableApps(context) }
    }
    var ignored by remember { mutableStateOf(IslandPrefs.soundIgnored(prefs)) }
    val query = rememberTextFieldState()
    val focus = remember { FocusRequester() }
    val text = query.text.toString().trim()
    val shown = remember(apps, text) { if (text.isEmpty()) apps else apps.filter { it.label.contains(text, ignoreCase = true) } }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(topPadding))
        Text(
            "Choose which apps can pop the Dynamic Island with a sound or a message. Switch one off and it stays quiet.",
            style = TextStyle(fontFamily = glintFontFamily, fontSize = 14.sp, lineHeight = 19.sp, color = ink.copy(alpha = 0.7f)),
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
        )
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth()) { StyledInputField(query, focus, placeholder = "Search apps", forceApple = true) }
        Spacer(Modifier.height(10.dp))

        if (apps.isEmpty()) {
            Text(
                "Looking at your apps…",
                style = TextStyle(fontFamily = glintFontFamily, fontSize = 14.sp, color = ink.copy(alpha = 0.55f)),
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        } else if (shown.isEmpty()) {
            Text(
                "No app called \"$text\".",
                style = TextStyle(fontFamily = glintFontFamily, fontSize = 14.sp, color = ink.copy(alpha = 0.55f)),
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        // One rounded card, like the other lists: rows sit inside it with hairlines between.
        LazyColumn(
            Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .clip(RoundedCornerShape(28.dp))
                .background(card),
            contentPadding = PaddingValues(vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            itemsIndexed(shown, key = { _, app -> app.pkg }) { index, app ->
                // The icon is read in the background the first time it scrolls into view.
                val icon by produceState<ImageBitmap?>(null, app.pkg) {
                    value = withContext(Dispatchers.Default) { SoundSource.appInfo(context, app.pkg).icon }
                }
                val on = app.pkg !in ignored
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 9.dp)
                        .semantics { contentDescription = "${app.label}: ${if (on) "can pop the island" else "switched off"}" },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppBadge(icon, SoundRules.Kind.Other, 38.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        app.label, maxLines = 1,
                        style = TextStyle(fontFamily = glintFontFamily, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = ink),
                        modifier = Modifier.weight(1f),
                    )
                    StyledSwitch(
                        checked = on,
                        onCheckedChange = { show ->
                            IslandPrefs.setSoundIgnored(prefs, app.pkg, !show)
                            ignored = IslandPrefs.soundIgnored(prefs)
                            GlintOverlays.refreshMiniIsland(context)
                        },
                    )
                }
                if (index < shown.lastIndex) Box(Modifier.padding(start = 66.dp).fillMaxWidth().height(0.5.dp).background(ink.copy(alpha = 0.08f)))
            }
        }
        Spacer(Modifier.height(bottomPadding))
    }
}
