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
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.kavishdevar.librepods.breathing.AppRule
import me.kavishdevar.librepods.breathing.BreathOptions
import me.kavishdevar.librepods.breathing.BreathPrefs
import me.kavishdevar.librepods.breathing.PauseMode
import me.kavishdevar.librepods.breathing.BreathWebApps
import kotlin.math.roundToInt
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
        // Websites installed from Chrome are never paused, so they are not offered at all.
        .filter { (pkg, _) -> pkg != context.packageName && !BreathWebApps.isWebAppPackage(pkg) }
        .distinctBy { it.first }
        .map { (pkg, label) -> AppEntry(pkg, label) }
        .sortedBy { it.label.lowercase() }
        .toList()
}

/** Installed apps for the event-driven breathing pause. */
@Composable
fun AppsScreen(preset: List<AppEntry>? = null, onDone: () -> Unit = {}) {
    val context = LocalContext.current
    val colors = breatheColors()
    val reduced = rememberBreatheReducedMotion()
    val haptics = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current
    val topPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 84.dp

    val loadedApps by produceState<List<AppEntry>?>(initialValue = preset, preset) {
        if (preset == null) value = withContext(Dispatchers.IO) { launchableApps(context) }
    }
    val apps = loadedApps.orEmpty().filterNot { BreathWebApps.isWebAppPackage(it.pkg) }
    var selected by remember { mutableStateOf(BreathPrefs.selected(context)) }
    var rules by remember { mutableStateOf(BreathPrefs.appRules(context)) }
    var editing by remember { mutableStateOf<AppEntry?>(null) }
    val query = rememberTextFieldState()
    val text = query.text.toString().trim()
    var onlyChosen by rememberSaveable { mutableStateOf(false) }
    val chosenCount = if (loadedApps == null) selected.size else apps.count { it.pkg in selected }
    val shown = remember(apps, text, selected, onlyChosen) {
        apps.filter { (!onlyChosen || it.pkg in selected) && (text.isEmpty() || it.label.contains(text, ignoreCase = true)) }
    }

    Column(Modifier.fillMaxSize().background(colors.background).imePadding()) {
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp),
            contentPadding = PaddingValues(top = topPadding, bottom = 20.dp),
        ) {
            item(key = "instructions") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Choose your apps.", color = colors.ink, style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.8).sp))
                    val seconds = remember { BreathPrefs.options(context).seconds.roundToInt() }
                    Text("Keep your usual icons. Add a $seconds-second pause the first time you open each one. Websites you installed from Chrome never pause.", color = colors.secondary, style = MaterialTheme.typography.bodyMedium)
                }
            }
            item(key = "search") {
                TextField(
                    state = query, lineLimits = TextFieldLineLimits.SingleLine,
                    placeholder = { Text("Search apps") },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = colors.secondary) },
                    trailingIcon = { if (query.text.isNotEmpty()) IconButton(onClick = { query.clearText() }) {
                        Icon(Icons.Default.Close, "Clear search", tint = colors.ink)
                    } },
                    shape = RoundedCornerShape(20.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = colors.surface, unfocusedContainerColor = colors.surface,
                        focusedTextColor = colors.ink, unfocusedTextColor = colors.ink,
                        focusedPlaceholderColor = colors.secondary, unfocusedPlaceholderColor = colors.secondary,
                        cursorColor = colors.ink, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                    ),
                    modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 12.dp),
                )
            }
            item(key = "filters") {
                FlowRow(Modifier.padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((chosenOnly, label) in listOf(false to "All apps", true to "Chosen ($chosenCount)")) {
                        FilterChip(
                            selected = onlyChosen == chosenOnly, onClick = { onlyChosen = chosenOnly },
                            shape = RoundedCornerShape(50), label = { Text(label) }, modifier = Modifier.heightIn(min = 48.dp),
                            colors = FilterChipDefaults.filterChipColors(containerColor = colors.surface, labelColor = colors.ink,
                                selectedContainerColor = colors.ink, selectedLabelColor = colors.surface),
                        )
                    }
                }
            }
            if (shown.isEmpty()) {
                item(key = "empty") {
                    Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            when {
                                loadedApps == null -> "Looking at your apps…"
                                apps.isEmpty() -> "No launchable apps were found."
                                onlyChosen && chosenCount == 0 -> "Choose apps from All apps to see them here."
                                else -> "No matches for \"$text\"."
                            }, color = colors.secondary, style = MaterialTheme.typography.bodyMedium,
                        )
                        if (text.isNotEmpty()) TextButton(onClick = { query.clearText() }, colors = ButtonDefaults.textButtonColors(contentColor = colors.ink)) { Text("Reset search") }
                    }
                }
            }
            itemsIndexed(shown, key = { _, app -> app.pkg }) { index, app ->
                val icon by produceState<ImageBitmap?>(null, app.pkg) {
                    value = withContext(Dispatchers.Default) { SoundSource.appInfo(context, app.pkg).icon }
                }
                val on = app.pkg in selected
                val fill by animateColorAsState(if (on) colors.ink.copy(alpha = 0.055f) else Color.Transparent,
                    tween(if (reduced) 0 else 160), label = "Chosen app")
                val first = if (index == 0) 22.dp else 0.dp
                val last = if (index == shown.lastIndex) 22.dp else 0.dp
                val movement = if (reduced) Modifier else Modifier.animateItem(
                    fadeInSpec = tween(160), placementSpec = tween(180), fadeOutSpec = tween(120))
                Column(movement.fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = first, topEnd = first, bottomStart = last, bottomEnd = last))
                    .background(colors.surface)) {
                    Row(Modifier.fillMaxWidth().background(fill).heightIn(min = 72.dp), verticalAlignment = Alignment.CenterVertically) {
                        Row(Modifier.weight(1f).heightIn(min = 72.dp)
                            .toggleable(value = on, role = Role.Checkbox, onValueChange = { chosen ->
                                haptics.performHapticFeedback(if (chosen) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
                                BreathPrefs.select(context, app.pkg, chosen)
                                selected = BreathPrefs.selected(context)
                            }).padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = if (on) 4.dp else 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (icon != null) androidx.compose.foundation.Image(bitmap = icon!!, contentDescription = null, modifier = Modifier.size(40.dp))
                            else Box(Modifier.size(40.dp).background(colors.line, RoundedCornerShape(12.dp)))
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(app.label, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = colors.ink)
                                rules[app.pkg]?.takeIf { on }?.let { Text(ruleSummary(it), color = colors.secondary, style = MaterialTheme.typography.bodySmall) }
                            }
                            Checkbox(checked = on, onCheckedChange = null,
                                colors = CheckboxDefaults.colors(checkedColor = colors.ink, checkmarkColor = colors.surface, uncheckedColor = colors.secondary))
                        }
                        // A chosen app can have its own rule: how strict, and how long.
                        if (on) IconButton(onClick = { editing = app }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Default.Settings, "Rules for ${app.label}", tint = colors.secondary)
                        }
                    }
                    if (index < shown.lastIndex) HorizontalDivider(Modifier.padding(start = 70.dp), color = colors.line, thickness = 0.5.dp)
                }
            }
        }
        Surface(color = colors.surface, shadowElevation = 0.dp) {
            Column {
                HorizontalDivider(color = colors.line)
                Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("$chosenCount chosen", color = colors.ink, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        Text("Saved automatically", color = colors.secondary, style = MaterialTheme.typography.bodySmall)
                    }
                    Button(onClick = { focusManager.clearFocus(); onDone() }, modifier = Modifier.heightIn(min = 48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.ink, contentColor = colors.surface)) { Text("Done") }
                }
            }
        }
    }
    editing?.let { app ->
        val general = remember { BreathPrefs.options(context) }
        AppRuleDialog(app.label, rules[app.pkg] ?: AppRule(), general, colors, reduced,
            onChange = { rule ->
                BreathPrefs.setAppRule(context, app.pkg, rule)
                rules = BreathPrefs.appRules(context)
            }, onDismiss = { editing = null })
    }
}

private fun ruleSummary(rule: AppRule): String = listOfNotNull(rule.mode?.label, rule.durationMs?.let { "${it / 1000} s" }).joinToString(" · ")

/** One app's own rule. Anything left on "Default" follows the general Breathe settings. */
@Composable
private fun AppRuleDialog(label: String, rule: AppRule, general: BreathOptions, colors: BreatheColors, reduced: Boolean,
                          onChange: (AppRule) -> Unit, onDismiss: () -> Unit) {
    var current by remember { mutableStateOf(rule) }
    fun update(next: AppRule) { current = next; onChange(next) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface, titleContentColor = colors.ink, textContentColor = colors.ink,
        title = { Text(label) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("When to pause", style = MaterialTheme.typography.labelLarge, color = colors.secondary)
                BreatheChoices(listOf<PauseMode?>(null) + PauseMode.entries, current.mode, colors, reduced,
                    { it?.label ?: "Default (${general.mode.label})" }) { update(current.copy(mode = it)) }
                Text(current.mode?.note ?: general.mode.note, style = MaterialTheme.typography.bodySmall, color = colors.secondary)
                Text("Length", style = MaterialTheme.typography.labelLarge, color = colors.secondary)
                BreatheChoices(listOf<Long?>(null, 5_000L, 8_000L, 10_000L, 15_000L, 20_000L, 30_000L), current.durationMs, colors, reduced,
                    { if (it == null) "Default (${general.seconds.roundToInt()} s)" else "${it / 1000} s" }) { update(current.copy(durationMs = it)) }
                Text("A longer pause and \"Every time\" make one app much harder to open than the rest.", style = MaterialTheme.typography.bodySmall, color = colors.secondary)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss, colors = ButtonDefaults.textButtonColors(contentColor = colors.ink)) { Text("Done") } },
    )
}
