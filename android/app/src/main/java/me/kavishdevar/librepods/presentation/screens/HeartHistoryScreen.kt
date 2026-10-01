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
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.kavishdevar.librepods.presentation.components.InfoTip
import me.kavishdevar.librepods.presentation.glint.pressable
import me.kavishdevar.librepods.presentation.glint.riseIn
import me.kavishdevar.librepods.services.HeartBackup
import me.kavishdevar.librepods.services.HeartHistory
import me.kavishdevar.librepods.services.HeartInsights
import java.text.DateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date

/**
 * Every saved session at once: an overview of the last 30 days (range per day, tap a day to
 * filter), totals and your usual resting rate, the GitHub backup, and all sessions grouped
 * by day. Tap a session to open it.
 */
@Composable
fun HeartHistoryScreen(openSession: (Long) -> Unit = {}) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val ink = if (dark) Color.White else Color.Black
    val muted = ink.copy(alpha = 0.6f)
    val card = if (dark) Color(0xFF1C1C1E) else Color.White
    val accent = HeartColors.accent(dark)
    val topPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 84.dp
    val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp
    val version by HeartHistory.version.collectAsState()
    val sessions = remember(version) { HeartHistory.sessions(context) }
    val days = remember(sessions) { HeartInsights.days(sessions) }
    val usual = remember(sessions) { HeartInsights.usualResting(sessions, System.currentTimeMillis()) }
    var selectedDay by remember { mutableStateOf<Long?>(null) }
    val zone = ZoneId.systemDefault()
    val shown = remember(sessions, selectedDay) {
        selectedDay?.let { d -> sessions.filter { Instant.ofEpochMilli(it.startMs).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli() == d } } ?: sessions
    }
    val grouped = remember(shown) { shown.groupBy { Instant.ofEpochMilli(it.startMs).atZone(zone).toLocalDate() }.toList() }
    val lo = remember(sessions) { (sessions.minOfOrNull { it.min } ?: 50) - 5 }
    val hi = remember(sessions) { (sessions.maxOfOrNull { it.max } ?: 150) + 5 }
    LaunchedEffect(Unit) {
        HeartBackup.refresh(context)
        HeartBackup.onSessionSaved(context) // catch up on anything that didn't upload yet
    }

    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Spacer(Modifier.height(topPadding - 14.dp)) }

        // Overview.
        item {
            Column(Modifier.riseIn(0).fillMaxWidth().background(card, RoundedCornerShape(28.dp)).padding(18.dp)) {
                Text("Last 30 days", style = heartText(17, ink, FontWeight.SemiBold))
                Text(if (selectedDay == null) "Each bar: lowest to highest, dot at the average. Tap a day." else "Showing one day · tap it again for all", style = heartText(13, muted))
                Spacer(Modifier.height(12.dp))
                if (days.isEmpty()) {
                    Text("No sessions yet. Measure for a minute or more and they'll appear here.", style = heartText(14, muted))
                } else {
                    DayBars(days, 30, accent, ink, dark, selectedDay, { selectedDay = it })
                }
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    val recent = sessions.filter { System.currentTimeMillis() - it.startMs <= 30L * 86_400_000 }
                    Insight("Sessions", "${recent.size}", ink, muted, Modifier.weight(1f), unit = "")
                    Insight("Measured", durationWords(recent.sumOf { it.minutes }), ink, muted, Modifier.weight(1f), unit = "")
                    Insight("Usual resting", usual?.toString() ?: "--", ink, muted, Modifier.weight(1f))
                    InfoTip(
                        "Usual resting",
                        "The middle of the resting estimates from your sessions in the last 30 days (it needs at least 3). " +
                            "A resting rate that drifts up over days can mean tiredness, stress, illness or heat; one that drifts down often goes with better fitness."
                    )
                }
                restingTrend(sessions)?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, style = heartText(13, muted))
                }
            }
        }

        // Resting trend and personal bests.
        if (sessions.any { it.resting > 0 }) item {
            val points = remember(sessions) { HeartInsights.restingSeries(sessions, System.currentTimeMillis()) }
            Column(Modifier.riseIn(1).fillMaxWidth().background(card, RoundedCornerShape(28.dp)).padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Resting heart rate", modifier = Modifier.weight(1f), style = heartText(17, ink, FontWeight.SemiBold))
                    InfoTip(
                        "Resting heart rate over time",
                        "One dot per day: that day's lowest resting estimate. Comparing it with your own usual rate tells you more than any single number. " +
                            "It tends to drift down with regular exercise and good rest, and up with stress, poor sleep, heat, alcohol or illness. For fitness, not medical use."
                    )
                }
                Spacer(Modifier.height(8.dp))
                RestingTrendChart(points, usual, accent, ink, dark, System.currentTimeMillis())
            }
        }
        if (sessions.isNotEmpty()) item {
            val r = remember(sessions) { HeartInsights.records(sessions) }
            val fmt = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
            Column(Modifier.riseIn(2).fillMaxWidth().background(card, RoundedCornerShape(28.dp)).padding(18.dp)) {
                Text("Personal bests", style = heartText(17, ink, FontWeight.SemiBold))
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth()) {
                    r.lowestResting?.let { Insight("Lowest resting · ${fmt.format(Date(it.startMs))}", "${it.resting}", ink, muted, Modifier.weight(1f).pressable { if (it.readings > 0) openSession(it.startMs) }) }
                    r.highestPeak?.let { Insight("Highest · ${fmt.format(Date(it.startMs))}", "${it.max}", ink, muted, Modifier.weight(1f).pressable { if (it.readings > 0) openSession(it.startMs) }) }
                }
                r.longest?.let {
                    Spacer(Modifier.height(10.dp))
                    Insight("Longest session · ${fmt.format(Date(it.startMs))}", durationWords(it.minutes.coerceAtLeast(1)), ink, muted, Modifier.pressable { if (it.readings > 0) openSession(it.startMs) }, unit = "")
                }
            }
        }

        // Backup.
        item { BackupCard(card, ink, muted, accent, dark, Modifier.riseIn(3)) }

        // All sessions, by day.
        grouped.forEachIndexed { g, (date, list) ->
            val row = g + 4
            item(key = "d$date") {
                Text(
                    dayTitle(date),
                    modifier = Modifier.riseIn(row).padding(start = 8.dp, top = 4.dp),
                    style = heartText(13, muted, FontWeight.SemiBold)
                )
            }
            item(key = "l$date") {
                val timeFmt = remember { DateFormat.getTimeInstance(DateFormat.SHORT) }
                Column(Modifier.riseIn(row).fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(card)) {
                    list.forEachIndexed { i, s ->
                        Row(
                            Modifier.fillMaxWidth().pressable { if (s.readings > 0) openSession(s.startMs) }.padding(horizontal = 18.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("${timeFmt.format(Date(s.startMs))} · ${durationWords(s.minutes.coerceAtLeast(1))}", style = heartText(15, ink))
                                Spacer(Modifier.height(6.dp))
                                RangeBar(s.min, s.max, s.average, lo, hi, accent, ink, Modifier.fillMaxWidth(0.85f))
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "${s.min}–${s.max} BPM" + (if (s.resting > 0) " · resting ${s.resting}" else "") + (if (s.readings == 0) " · summary only" else ""),
                                    style = heartText(12, muted)
                                )
                            }
                            Text("${s.average}", style = heartText(22, ink, FontWeight.SemiBold))
                            Text(" avg", style = heartText(13, muted))
                        }
                        if (i < list.lastIndex) Box(Modifier.padding(start = 18.dp).fillMaxWidth().height(0.5.dp).background(ink.copy(alpha = 0.12f)))
                    }
                }
            }
        }
        item { Spacer(Modifier.height(bottomPadding)) }
    }
}

private fun dayTitle(date: java.time.LocalDate): String {
    val today = java.time.LocalDate.now()
    return when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> date.format(DateTimeFormatter.ofPattern("EEEE d MMMM"))
    }
}

/** Resting rate this week against the three weeks before, when there's enough of both. */
private fun restingTrend(sessions: List<HeartInsights.Session>): String? {
    val now = System.currentTimeMillis()
    val week = sessions.filter { it.resting > 0 && now - it.startMs <= 7L * 86_400_000 }.map { it.resting }
    val before = sessions.filter { it.resting > 0 && now - it.startMs in (7L * 86_400_000 + 1)..(28L * 86_400_000) }.map { it.resting }
    if (week.size < 3 || before.size < 3) return null
    val d = week.sorted()[week.size / 2] - before.sorted()[before.size / 2]
    return when {
        d >= 3 -> "Resting is $d BPM higher this week than the three weeks before."
        d <= -3 -> "Resting is ${-d} BPM lower this week than the three weeks before."
        else -> "Resting is steady compared with the three weeks before."
    }
}

/**
 * Back up to GitHub: off, a short set-up (get a token, paste it, pick a name), or on with
 * Back up now / Restore / Turn off.
 */
@Composable
private fun BackupCard(card: Color, ink: Color, muted: Color, accent: Color, dark: Boolean, modifier: Modifier) {
    val context = LocalContext.current
    val status by HeartBackup.status.collectAsState()
    val on = status !is HeartBackup.Status.Off
    var setup by remember { mutableStateOf(false) }
    var token by remember { mutableStateOf("") }
    var name by remember { mutableStateOf(HeartBackup.DEFAULT_REPO) }
    var message by remember { mutableStateOf<String?>(null) }
    val fieldBg = if (dark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f)
    Column(modifier.fillMaxWidth().background(card, RoundedCornerShape(28.dp)).padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Back up to GitHub", style = heartText(17, ink, FontWeight.SemiBold))
                Text(if (on) (HeartBackup.repo(context) ?: "") + " · private" else "Keeps your history if you lose your phone", style = heartText(13, muted))
            }
            InfoTip(
                "How the backup works",
                "Each session is saved as a small spreadsheet file (CSV) in a private repository on your GitHub account, plus a summary of all sessions. " +
                    "Glint uploads each session as soon as it ends, and a session that is still going every 10 minutes. On a new phone, connect the same repository and tap Restore. " +
                    "Your token is locked with a key in this phone's secure hardware and is only sent to GitHub. " +
                    "Glint refuses public repositories, since heart data is health data."
            )
        }
        Spacer(Modifier.height(10.dp))
        if (on) {
            Text(backupLine(status), style = heartText(14, if (status is HeartBackup.Status.Problem) accent else ink))
            message?.let { Text(it, modifier = Modifier.padding(top = 4.dp), style = heartText(13, muted)) }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionPill("Back up now", ink, dark) { message = null; HeartBackup.backUpNow(context) }
                ActionPill("Restore", ink, dark) {
                    message = "Restoring…"
                    HeartBackup.restore(context) { n, err -> message = err ?: if (n == 0) "Nothing new to restore" else "Restored $n sessions" }
                }
                ActionPill("Turn off", accent, dark) { HeartBackup.disconnect(context); message = null }
            }
        } else {
            AnimatedVisibility(!setup) {
                ActionPill("Set up", ink, dark) { setup = true }
            }
            AnimatedVisibility(setup, enter = fadeIn() + expandVertically(spring(0.85f, 300f)), exit = fadeOut() + shrinkVertically()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("1. Create a token on GitHub. The page opens with the right box (\"repo\") ticked: scroll down and tap Generate token, then copy it.", style = heartText(14, ink).copy(lineHeight = 19.sp))
                    ActionPill("Open GitHub", ink, dark) {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(HeartBackup.TOKEN_PAGE)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    }
                    Text("2. Paste it here.", style = heartText(14, ink))
                    Field(token, "ghp_…", fieldBg, ink, muted, accent, secret = true) { token = it.trim() }
                    Text("3. Name for the private repository (Glint creates it if needed).", style = heartText(14, ink))
                    Field(name, HeartBackup.DEFAULT_REPO, fieldBg, ink, muted, accent) { name = it.trim() }
                    message?.let { Text(it, style = heartText(13, accent)) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ActionPill(if (status is HeartBackup.Status.Working) "Connecting…" else "Connect", ink, dark) {
                            if (token.isBlank()) { message = "Paste your token first."; return@ActionPill }
                            message = null
                            HeartBackup.connect(context, token, name) { err ->
                                message = err
                                if (err == null) { setup = false; token = "" }
                            }
                        }
                        ActionPill("Cancel", muted, dark) { setup = false; token = ""; message = null }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionPill(text: String, color: Color, dark: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (dark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.06f))
            .pressable(onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(text, style = heartText(15, color, FontWeight.Medium))
    }
}

@Composable
private fun Field(value: String, hint: String, bg: Color, ink: Color, muted: Color, accent: Color, secret: Boolean = false, onChange: (String) -> Unit) {
    Box(Modifier.fillMaxWidth().background(bg, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 12.dp)) {
        if (value.isEmpty()) Text(hint, style = heartText(15, muted))
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = heartText(15, ink),
            cursorBrush = SolidColor(accent),
            visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = if (secret) KeyboardType.Password else KeyboardType.Ascii, imeAction = ImeAction.Done, autoCorrectEnabled = false),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** One saved session in full: its line, numbers, zones, export and delete. */
@Composable
fun HeartSessionScreen(startMs: Long, onDeleted: () -> Unit = {}) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE) }
    val dark = isSystemInDarkTheme()
    val ink = if (dark) Color.White else Color.Black
    val muted = ink.copy(alpha = 0.6f)
    val card = if (dark) Color(0xFF1C1C1E) else Color.White
    val accent = HeartColors.accent(dark)
    val topPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 84.dp
    val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp
    var samples by remember { mutableStateOf<List<me.kavishdevar.librepods.services.HeartRate.Sample>?>(null) }
    LaunchedEffect(startMs) { samples = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { HeartHistory.samples(context, startMs) } }
    val summary = remember(startMs) { HeartHistory.sessions(context).firstOrNull { it.startMs == startMs } }
    val age = remember { prefs.getInt(me.kavishdevar.librepods.services.PREF_HR_AGE, 0) }
    var confirm by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(Modifier.height(topPadding - 14.dp))
        val list = samples
        Column(Modifier.riseIn(0).fillMaxWidth().background(card, RoundedCornerShape(28.dp)).padding(18.dp)) {
            val fmt = remember { DateFormat.getDateTimeInstance(DateFormat.FULL, DateFormat.SHORT) }
            Text(fmt.format(Date(startMs)), style = heartText(17, ink, FontWeight.SemiBold))
            summary?.let { Text("${durationWords(it.minutes.coerceAtLeast(1))} · ${it.readings} readings", style = heartText(13, muted)) }
            Spacer(Modifier.height(10.dp))
            if (list == null) Text("Loading…", style = heartText(14, muted))
            else if (list.size < 2) Text("No readings saved for this session.", style = heartText(14, muted))
            else HeartChart(list, accent, ink, card, dark, height = 200.dp, zoneAge = age, average = summary?.average)
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth()) {
                Stat("Lowest", summary?.min, ink, muted, Modifier.weight(1f))
                Stat("Average", summary?.average, ink, muted, Modifier.weight(1f))
                Stat("Highest", summary?.max, ink, muted, Modifier.weight(1f))
            }
            if (list != null && list.size >= 2) {
                val recovery = remember(list) { HeartInsights.recovery(list) }
                val peak5 = remember(list) { HeartInsights.peakAverage(list) }
                val above100 = remember(list) { HeartInsights.secondsAbove(list, 100) }
                if (peak5 != null || above100 >= 60) {
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        if (peak5 != null) Insight("Hardest 5 minutes", "$peak5", ink, muted, Modifier.weight(1f))
                        if (above100 >= 60) Insight("Above 100 BPM", durationWords(above100 / 60), ink, muted, Modifier.weight(1f), unit = "")
                        InfoTip(
                            "Effort in this session",
                            "Hardest 5 minutes: your highest average over any 5 minutes, a steadier measure of effort than the single highest reading. " +
                                "Above 100 BPM: how long your heart rate was over 100, the top of the typical resting range."
                        )
                    }
                }
                if ((summary?.resting ?: 0) > 0 || recovery != null) {
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth()) {
                        if ((summary?.resting ?: 0) > 0) Insight("Resting, est.", "${summary?.resting}", ink, muted, Modifier.weight(1f))
                        if (recovery != null) Insight("1-min recovery", if (recovery > 0) "−$recovery" else "$recovery", ink, muted, Modifier.weight(1f))
                    }
                }
                if (age > 0) {
                    Spacer(Modifier.height(16.dp))
                    ZoneBar(HeartInsights.timeInZones(list, age), accent, ink, muted)
                }
            }
        }
        Row(Modifier.riseIn(1), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            list?.takeIf { it.isNotEmpty() }?.let { s -> ActionPill("Export", ink, dark) { exportCsv(context, s) } }
            ActionPill(if (confirm) "Tap again to delete" else "Delete", accent, dark) {
                if (!confirm) confirm = true else { HeartHistory.delete(context, startMs); onDeleted() }
            }
        }
        if (confirm) Text("Deleting removes it from this phone. A copy already backed up to GitHub stays there.", modifier = Modifier.padding(horizontal = 8.dp), style = heartText(13, muted))
        Spacer(Modifier.height(bottomPadding))
    }
}
