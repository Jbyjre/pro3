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
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.kavishdevar.librepods.presentation.components.StyledInputField
import me.kavishdevar.librepods.presentation.theme.glintFontFamily
import me.kavishdevar.librepods.breathing.BreathPrefs
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

/** Installed apps for the event-driven breathing pause. */
@Composable
fun AppsScreen(preset: List<AppEntry>? = null) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val ink = if (dark) Color.White else Color.Black
    val card = if (dark) Color(0xFF1C1C1E) else Color.White
    val topPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 84.dp
    val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp

    val loadedApps by produceState<List<AppEntry>?>(initialValue = preset, preset) {
        if (preset == null) value = withContext(Dispatchers.IO) { launchableApps(context) }
    }
    val apps = loadedApps.orEmpty()
    var selected by remember { mutableStateOf(BreathPrefs.selected(context)) }
    val query = rememberTextFieldState()
    val focus = remember { FocusRequester() }
    val text = query.text.toString().trim()
    var onlyChosen by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    val chosenCount = apps.count { it.pkg in selected }
    val shown = remember(apps, text, selected, onlyChosen) {
        apps.filter { (!onlyChosen || it.pkg in selected) && (text.isEmpty() || it.label.contains(text, ignoreCase = true)) }
    }

    // Controls and results share one scroll surface, so large text or the keyboard
    // cannot consume all the space above a separately constrained app list.
    LazyColumn(
        Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .imePadding()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = topPadding, bottom = bottomPadding),
    ) {
        item(key = "instructions") {
            Text(
                "Choose where you want a 2.5-second pause. Keep using your usual app icons, notifications and links.",
                style = TextStyle(fontFamily = glintFontFamily, fontSize = 14.sp, lineHeight = 19.sp, color = ink.copy(alpha = 0.7f)),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            )
        }
        item(key = "search") {
            Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 10.dp)) {
                StyledInputField(query, focus, placeholder = "Search apps", forceApple = true)
            }
        }
        item(key = "filters") {
            FlowRow(Modifier.padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((chosenOnly, label) in listOf(false to "All apps", true to "Chosen ($chosenCount)")) {
                    FilterChip(
                        selected = onlyChosen == chosenOnly,
                        onClick = { onlyChosen = chosenOnly },
                        label = { Text(label) }, modifier = Modifier.heightIn(min = 48.dp),
                        colors = FilterChipDefaults.filterChipColors(containerColor = card, labelColor = ink,
                            selectedContainerColor = ink, selectedLabelColor = card),
                    )
                }
            }
        }
        if (shown.isEmpty()) {
            item(key = "empty") {
                Text(
                    when {
                        loadedApps == null -> "Looking at your apps…"
                        apps.isEmpty() -> "No launchable apps were found."
                        onlyChosen && chosenCount == 0 -> "Choose apps from All apps to see them here."
                        else -> "No matches for \"$text\"."
                    },
                    style = TextStyle(fontFamily = glintFontFamily, fontSize = 14.sp, color = ink.copy(alpha = 0.55f)),
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
        itemsIndexed(shown, key = { _, app -> app.pkg }) { index, app ->
            // The icon is read in the background the first time it scrolls into view.
            val icon by produceState<ImageBitmap?>(null, app.pkg) {
                value = withContext(Dispatchers.Default) { SoundSource.appInfo(context, app.pkg).icon }
            }
            val on = app.pkg in selected
            val first = if (index == 0) 28.dp else 0.dp
            val last = if (index == shown.lastIndex) 28.dp else 0.dp
            Column(Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(topStart = first, topEnd = first, bottomStart = last, bottomEnd = last))
                .background(card)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp)
                        .toggleable(value = on, role = Role.Checkbox, onValueChange = { chosen ->
                            BreathPrefs.select(context, app.pkg, chosen)
                            selected = BreathPrefs.selected(context)
                        })
                        .padding(horizontal = 16.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (icon != null) androidx.compose.foundation.Image(bitmap = icon!!, contentDescription = null,
                        modifier = Modifier.size(38.dp))
                    else Box(Modifier.size(38.dp).background(ink.copy(alpha = 0.06f), RoundedCornerShape(10.dp)))
                    Spacer(Modifier.width(12.dp))
                    Text(
                        app.label, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        style = TextStyle(fontFamily = glintFontFamily, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = ink),
                        modifier = Modifier.weight(1f),
                    )
                    Checkbox(checked = on, onCheckedChange = null,
                        colors = CheckboxDefaults.colors(checkedColor = ink, checkmarkColor = card, uncheckedColor = ink.copy(alpha = 0.6f)))
                }
                if (index < shown.lastIndex) Box(Modifier.padding(start = 66.dp).fillMaxWidth().height(0.5.dp).background(ink.copy(alpha = 0.08f)))
            }
        }
    }
}
