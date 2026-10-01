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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.core.content.edit
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.delay
import me.kavishdevar.librepods.presentation.components.StyledButton
import me.kavishdevar.librepods.presentation.components.StyledToggle
import me.kavishdevar.librepods.presentation.glint.GlintComfort
import me.kavishdevar.librepods.presentation.theme.glintFontFamily
import me.kavishdevar.librepods.services.GlintStatus
import me.kavishdevar.librepods.services.HeartRate
import me.kavishdevar.librepods.services.HeartInsights
import me.kavishdevar.librepods.services.HeartLink
import me.kavishdevar.librepods.services.PREF_LINK_BLE
import me.kavishdevar.librepods.services.PREF_LINK_BROADCAST
import me.kavishdevar.librepods.services.PREF_LINK_WEBHOOK
import me.kavishdevar.librepods.presentation.components.InfoTip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.mutableLongStateOf
import me.kavishdevar.librepods.services.LinkState
import me.kavishdevar.librepods.services.PREF_HR_ALERT
import me.kavishdevar.librepods.services.PREF_HR_ALWAYS
import me.kavishdevar.librepods.services.PREF_HR_ALERT_BPM
import me.kavishdevar.librepods.services.ServiceManager
import java.text.DateFormat
import java.util.Date
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

private const val PREF_HR_AGE = "glint_hr_age"

/** Heart-rate line colours, checked for contrast and lightness on the light and dark cards. */
private val HeartLight = Color(0xFFE0303A)
private val HeartDark = Color(0xFFF04A50)

/**
 * Heart rate from the AirPods Pro 3's sensor: the live number with a heart that beats at that
 * rate, what it means (effort zone), this session's line and trend, time in each zone, earlier
 * sessions, and an optional alert. Longer explanations sit behind small "i" buttons.
 */
@Composable
fun HeartRateScreen(navigateToShare: () -> Unit = {}) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    val state by HeartRate.state.collectAsState()
    val link by GlintStatus.link.collectAsState()
    val dark = isSystemInDarkTheme()
    val ink = if (dark) Color.White else Color.Black
    val muted = ink.copy(alpha = 0.6f)
    val card = if (dark) Color(0xFF1C1C1E) else Color.White
    val accent = if (dark) HeartDark else HeartLight
    val topPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 84.dp
    val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp
    val connected = link is LinkState.Connected
    val measuring = state.status != HeartRate.Status.Off
    val live = state.status == HeartRate.Status.Live
    var age by remember { mutableIntStateOf(prefs.getInt(PREF_HR_AGE, 0)) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(5_000); now = System.currentTimeMillis() } }
    val history = remember(state.status) { HeartRate.history(context) }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(Modifier.height(topPadding - 14.dp))

        // Live reading.
        Column(
            Modifier.fillMaxWidth().background(card, RoundedCornerShape(28.dp)).padding(vertical = 24.dp, horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            BeatingHeart(bpm = state.bpm.takeIf { live }, color = accent)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
                Text(
                    state.bpm?.takeIf { live }?.toString() ?: "--",
                    modifier = Modifier.alignByBaseline(),
                    style = TextStyle(fontFamily = glintFontFamily, fontSize = 60.sp, fontWeight = FontWeight.SemiBold, color = ink)
                )
                Spacer(Modifier.width(6.dp))
                Text("BPM", modifier = Modifier.alignByBaseline(), style = TextStyle(fontFamily = glintFontFamily, fontSize = 17.sp, fontWeight = FontWeight.Medium, color = muted))
            }
            val bpm = state.bpm
            if (live && bpm != null && age > 0) {
                val z = HeartInsights.zone(bpm, age)
                Text("${z.label} effort", style = TextStyle(fontFamily = glintFontFamily, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = accent))
                Text(z.meaning, style = TextStyle(fontFamily = glintFontFamily, fontSize = 13.sp, color = muted, textAlign = TextAlign.Center))
            } else {
                Text(statusLine(state.status, connected), style = TextStyle(fontFamily = glintFontFamily, fontSize = 14.sp, color = muted, textAlign = TextAlign.Center))
            }
            Spacer(Modifier.height(16.dp))
            StyledButton(
                onClick = {
                    val service = ServiceManager.getService()
                    if (measuring) service?.stopHeartRate() else service?.startHeartRate()
                },
                backdrop = rememberLayerBackdrop(),
                maxScale = 0.06f,
                enabled = connected || measuring,
            ) {
                Text(
                    if (measuring) "Stop" else "Start measuring",
                    style = TextStyle(fontFamily = glintFontFamily, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = ink),
                    modifier = Modifier.padding(horizontal = 10.dp)
                )
            }
        }

        // This session.
        if (state.samples.size >= 2) {
            Column(Modifier.fillMaxWidth().background(card, RoundedCornerShape(28.dp)).padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("This session", modifier = Modifier.weight(1f), style = TextStyle(fontFamily = glintFontFamily, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = ink))
                    HeartInsights.trend(state.samples, now)?.takeIf { live }?.let {
                        Text(it.words, style = TextStyle(fontFamily = glintFontFamily, fontSize = 13.sp, color = muted), modifier = Modifier.padding(end = 12.dp))
                    }
                    Text(
                        "Reset",
                        style = TextStyle(fontFamily = glintFontFamily, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = accent),
                        modifier = Modifier.clickable { HeartRate.clear() }.padding(6.dp)
                    )
                }
                Spacer(Modifier.height(10.dp))
                HeartChart(state.samples, accent, ink, card, dark)
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth()) {
                    Stat("Lowest", state.min, ink, muted, Modifier.weight(1f))
                    Stat("Average", state.average, ink, muted, Modifier.weight(1f))
                    Stat("Highest", state.max, ink, muted, Modifier.weight(1f))
                }
                val resting = remember(state.samples.size / 30) { HeartInsights.restingEstimate(state.samples) }
                val recovery = remember(state.samples.size / 5) { HeartInsights.recovery(state.samples) }
                if (resting != null || recovery != null) {
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        if (resting != null) Insight("Resting, est.", "$resting", ink, muted, Modifier.weight(1f))
                        if (recovery != null) Insight("1-min recovery", if (recovery > 0) "−$recovery" else "$recovery", ink, muted, Modifier.weight(1f))
                        InfoTip(
                            "Resting and recovery",
                            "Resting, est.: your lowest 3-minute average this session; closest to your true resting rate when you've sat still for a while. " +
                                "1-min recovery: how far your heart rate fell in the minute after this session's highest reading (shown once that reading is 100 BPM or more). " +
                                "Bigger drops generally go with better fitness. In a well-known 1999 study, a drop of 12 BPM or less one minute after a treadmill test was linked with higher health risk. " +
                                "These are estimates, not a medical test."
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Export readings",
                    style = TextStyle(fontFamily = glintFontFamily, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = accent),
                    modifier = Modifier.clickable { exportCsv(context, state.samples) }.padding(vertical = 6.dp)
                )
                if (age > 0) {
                    Spacer(Modifier.height(16.dp))
                    ZoneBar(HeartInsights.timeInZones(state.samples, age), accent, ink, muted)
                }
            }
        }

        // Share live.
        val shareStatus by HeartLink.status.collectAsState()
        val sharing = listOf(PREF_LINK_BLE, PREF_LINK_WEBHOOK, PREF_LINK_BROADCAST).count { prefs.getBoolean(it, false) }
        Row(
            Modifier.fillMaxWidth().background(card, RoundedCornerShape(28.dp)).clickable(onClick = navigateToShare).padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Share live heart rate", style = TextStyle(fontFamily = glintFontFamily, fontSize = 16.sp, color = ink))
                Text(
                    when {
                        sharing == 0 -> "Watches, fitness apps, automations"
                        shareStatus.beacon is HeartLink.Beacon.Advertising && measuring -> "Live as a Bluetooth sensor"
                        else -> "$sharing on · while measuring"
                    },
                    style = TextStyle(fontFamily = glintFontFamily, fontSize = 13.sp, color = muted)
                )
            }
            Text("›", style = TextStyle(fontFamily = glintFontFamily, fontSize = 24.sp, color = muted))
        }

        // Effort zones need an age.
        Column(Modifier.fillMaxWidth().background(card, RoundedCornerShape(28.dp)).padding(horizontal = 18.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Effort zones", style = TextStyle(fontFamily = glintFontFamily, fontSize = 16.sp, color = ink))
                    Text(
                        if (age > 0) "Age $age · max about ${HeartInsights.maxHeartRate(age)} BPM" else "Add your age to see them",
                        style = TextStyle(fontFamily = glintFontFamily, fontSize = 13.sp, color = muted)
                    )
                }
                InfoTip(
                    "Effort zones",
                    "Based on the American Heart Association's guidance: your maximum heart rate is about 220 minus your age. " +
                        "Moderate effort is 50–70% of it, vigorous 70–85%. A normal resting rate for most adults is 60–100 BPM."
                )
                Stepper("−", ink, dark) { age = if (age == 0) 30 else (age - 1).coerceAtLeast(13); prefs.edit { putInt(PREF_HR_AGE, age) } }
                Spacer(Modifier.width(8.dp))
                Stepper("+", ink, dark) { age = if (age == 0) 30 else (age + 1).coerceAtMost(90); prefs.edit { putInt(PREF_HR_AGE, age) } }
            }
        }

        // Always-on.
        var always by remember { mutableStateOf(prefs.getBoolean(PREF_HR_ALWAYS, false)) }
        Column(Modifier.fillMaxWidth().background(card, RoundedCornerShape(28.dp)).padding(horizontal = 4.dp)) {
            StyledToggle(
                label = "Measure whenever worn",
                description = when {
                    !always -> "Even with Glint closed, no notification"
                    state.background && live -> "On now, about every 5 seconds"
                    else -> "Starts when you put your AirPods in"
                },
                checked = always,
                onCheckedChange = {
                    always = it
                    prefs.edit { putBoolean(PREF_HR_ALWAYS, it) }
                    ServiceManager.getService()?.autoHeartRate()
                }
            )
            Row(Modifier.padding(start = 14.dp, end = 6.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("How it saves battery", modifier = Modifier.weight(1f), style = TextStyle(fontFamily = glintFontFamily, fontSize = 13.sp, color = muted))
                InfoTip(
                    "Measure whenever worn",
                    "Glint starts measuring when a bud goes in and stops a minute after both come out; each stretch is saved under Earlier. " +
                        "It asks your AirPods for a reading about every 5 seconds instead of every second, to save battery (if they don't accept that, it uses every second). " +
                        "Pressing Stop pauses it until you next put your AirPods in. The extra battery use on the AirPods hasn't been measured."
                )
            }
        }

        // Alert.
        var alert by remember { mutableStateOf(prefs.getBoolean(PREF_HR_ALERT, false)) }
        var limit by remember { mutableIntStateOf(prefs.getInt(PREF_HR_ALERT_BPM, 140)) }
        Column(Modifier.fillMaxWidth().background(card, RoundedCornerShape(28.dp)).padding(horizontal = 4.dp)) {
            StyledToggle(
                label = "High heart rate alert",
                description = if (alert) "Above $limit BPM, at most every 10 minutes" else null,
                checked = alert,
                onCheckedChange = { alert = it; prefs.edit { putBoolean(PREF_HR_ALERT, it) } }
            )
            if (alert) {
                Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Limit", style = TextStyle(fontFamily = glintFontFamily, fontSize = 16.sp, color = ink), modifier = Modifier.weight(1f))
                    Stepper("−", ink, dark) { limit = (limit - 5).coerceAtLeast(90); prefs.edit { putInt(PREF_HR_ALERT_BPM, limit) } }
                    Text("$limit BPM", style = TextStyle(fontFamily = glintFontFamily, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = ink, textAlign = TextAlign.Center), modifier = Modifier.width(96.dp))
                    Stepper("+", ink, dark) { limit = (limit + 5).coerceAtMost(200); prefs.edit { putInt(PREF_HR_ALERT_BPM, limit) } }
                }
            }
        }

        // Earlier sessions.
        if (history.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().background(card, RoundedCornerShape(28.dp)).padding(vertical = 8.dp)) {
                Text("Earlier", modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp), style = TextStyle(fontFamily = glintFontFamily, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = ink))
                val dateFmt = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
                history.take(5).forEach { h ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(dateFmt.format(Date(h.startMs)), style = TextStyle(fontFamily = glintFontFamily, fontSize = 15.sp, color = ink))
                            Text("${(h.endMs - h.startMs) / 60_000} min · ${h.min}–${h.max} BPM", style = TextStyle(fontFamily = glintFontFamily, fontSize = 13.sp, color = muted))
                        }
                        Text("${h.average}", style = TextStyle(fontFamily = glintFontFamily, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = ink))
                        Text(" avg", style = TextStyle(fontFamily = glintFontFamily, fontSize = 13.sp, color = muted))
                    }
                }
            }
        }

        Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("For fitness, not medical use", style = TextStyle(fontFamily = glintFontFamily, fontSize = 13.sp, color = muted))
            InfoTip(
                "About these readings",
                "Measured by the sensor in AirPods Pro 3 while you wear them; a snug fit in both ears gives the steadiest readings. " +
                    "Readings stay on this phone unless you turn on sharing or export them. Measuring continues in the background until you stop it."
            )
        }
        Spacer(Modifier.height(bottomPadding))
    }
}

/** Time in each effort zone this session, as one bar from light to peak with labels. */
@Composable
private fun ZoneBar(times: Map<HeartInsights.Zone, Long>, accent: Color, ink: Color, muted: Color) {
    val total = times.values.sum().coerceAtLeast(1)
    val shades = listOf(0.25f, 0.5f, 0.75f, 1f)
    Text("Time in zones", style = TextStyle(fontFamily = glintFontFamily, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = muted))
    Spacer(Modifier.height(6.dp))
    Row(Modifier.fillMaxWidth().height(10.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        HeartInsights.Zone.entries.forEachIndexed { i, z ->
            val secs = times[z] ?: 0
            if (secs > 0) Box(Modifier.weight(secs.toFloat() / total).fillMaxSize().background(accent.copy(alpha = shades[i]), RoundedCornerShape(4.dp)))
        }
    }
    Spacer(Modifier.height(8.dp))
    // Only the zones you were in, so the legend stays short.
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        HeartInsights.Zone.entries.forEachIndexed { i, z ->
            val secs = times[z] ?: 0
            if (secs >= 30) Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(accent.copy(alpha = shades[i]), CircleShape))
                Spacer(Modifier.width(5.dp))
                Text("${z.label} ${(secs + 30) / 60} min", maxLines = 1, style = TextStyle(fontFamily = glintFontFamily, fontSize = 12.sp, color = ink.copy(alpha = 0.75f)))
            }
        }
    }
}

private fun statusLine(status: HeartRate.Status, connected: Boolean): String = when (status) {
    HeartRate.Status.Off -> if (connected) "Not measuring" else "Connect your AirPods first"
    HeartRate.Status.Starting -> "Starting…"
    HeartRate.Status.Live -> "Live"
    HeartRate.Status.NoSignal -> "No reading. Check both buds fit snugly"
    HeartRate.Status.NotConnected -> "Waiting for the AirPods"
}

@Composable
private fun Insight(label: String, value: String, ink: Color, muted: Color, modifier: Modifier) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, modifier = Modifier.alignByBaseline(), style = TextStyle(fontFamily = glintFontFamily, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = ink))
            Text(" BPM", modifier = Modifier.alignByBaseline(), style = TextStyle(fontFamily = glintFontFamily, fontSize = 12.sp, color = muted))
        }
        Text(label, style = TextStyle(fontFamily = glintFontFamily, fontSize = 12.sp, color = muted))
    }
}

/** Writes this session's readings to a CSV file and opens the share sheet. */
private fun exportCsv(context: Context, samples: List<HeartRate.Sample>) {
    if (samples.isEmpty()) return
    val dir = java.io.File(context.filesDir, "exports").apply { mkdirs() }
    dir.listFiles()?.forEach { it.delete() } // keep only the latest export
    val stamp = java.text.SimpleDateFormat("yyyy-MM-dd-HHmm", java.util.Locale.US).format(Date(samples.first().timeMs))
    val file = java.io.File(dir, "heart-rate-$stamp.csv")
    file.writeText(HeartInsights.csv(samples))
    val uri = androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".provider", file)
    val send = android.content.Intent(android.content.Intent.ACTION_SEND)
        .setType("text/csv")
        .putExtra(android.content.Intent.EXTRA_STREAM, uri)
        .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(android.content.Intent.createChooser(send, "Export heart rate"))
}

@Composable
private fun Stat(label: String, value: Int?, ink: Color, muted: Color, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value?.toString() ?: "--", style = TextStyle(fontFamily = glintFontFamily, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = ink))
        Text(label, style = TextStyle(fontFamily = glintFontFamily, fontSize = 12.sp, color = muted))
    }
}

@Composable
private fun Stepper(symbol: String, ink: Color, dark: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .background(if (dark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.06f), RoundedCornerShape(20.dp))
            .semantics { contentDescription = if (symbol == "+") "Raise limit" else "Lower limit" }
            .pointerInput(Unit) { detectTapGestures { onClick() } },
        contentAlignment = Alignment.Center
    ) {
        Text(symbol, style = TextStyle(fontFamily = glintFontFamily, fontSize = 20.sp, fontWeight = FontWeight.Medium, color = ink))
    }
}

/** A heart that beats once per measured beat (still with reduce motion or no reading). */
@Composable
private fun BeatingHeart(bpm: Int?, color: Color) {
    val context = LocalContext.current
    val reduceMotion = remember { GlintComfort.reduceMotion(context) }
    val scale = remember { Animatable(1f) }
    LaunchedEffect(bpm, reduceMotion) {
        if (bpm == null || reduceMotion) { scale.snapTo(1f); return@LaunchedEffect }
        while (true) {
            val period = (60_000 / bpm).toLong().coerceIn(240L, 2_400L)
            scale.animateTo(1.14f, tween(110, easing = FastOutSlowInEasing))
            scale.animateTo(1f, tween(220, easing = FastOutSlowInEasing))
            delay((period - 330).coerceAtLeast(0))
        }
    }
    // A soft glow that swells with each beat, so the reading feels alive behind the glass.
    Box(Modifier.size(width = 120.dp, height = 56.dp), contentAlignment = Alignment.Center) {
    if (bpm != null) Canvas(Modifier.size(120.dp, 56.dp)) {
        val beat = ((scale.value - 1f) / 0.14f).coerceIn(0f, 1f)
        drawCircle(
            androidx.compose.ui.graphics.Brush.radialGradient(
                listOf(color.copy(alpha = 0.16f + 0.18f * beat), Color.Transparent),
                center = center, radius = size.height * (0.85f + 0.25f * beat)
            ),
            radius = size.height * (0.85f + 0.25f * beat)
        )
    }
    Canvas(Modifier.size(44.dp).graphicsLayer { scaleX = scale.value; scaleY = scale.value }) {
        val w = size.width
        val h = size.height
        val p = Path().apply {
            moveTo(w * 0.5f, h * 0.92f)
            cubicTo(w * 0.1f, h * 0.62f, -w * 0.02f, h * 0.30f, w * 0.22f, h * 0.14f)
            cubicTo(w * 0.36f, h * 0.04f, w * 0.48f, h * 0.14f, w * 0.5f, h * 0.24f)
            cubicTo(w * 0.52f, h * 0.14f, w * 0.64f, h * 0.04f, w * 0.78f, h * 0.14f)
            cubicTo(w * 1.02f, h * 0.30f, w * 0.9f, h * 0.62f, w * 0.5f, h * 0.92f)
            close()
        }
        drawPath(p, if (bpm == null) color.copy(alpha = 0.35f) else color)
    }
    }
}

/**
 * The session as one 2dp line on a recessive grid. Touch and drag to read any point: a thin
 * crosshair and the value and time appear above the line.
 */
@Composable
private fun HeartChart(samples: List<HeartRate.Sample>, line: Color, ink: Color, surface: Color, dark: Boolean) {
    val measurer = rememberTextMeasurer()
    var probe by remember { mutableStateOf<Float?>(null) }
    val lo = (floor((samples.minOf { it.bpm } - 5) / 10.0) * 10).toInt()
    val hi = (ceil((samples.maxOf { it.bpm } + 5) / 10.0) * 10).toInt().coerceAtLeast(lo + 20)
    val t0 = samples.first().timeMs
    val t1 = samples.last().timeMs.coerceAtLeast(t0 + 1)
    val timeFmt = remember { DateFormat.getTimeInstance(DateFormat.SHORT) }
    val grid = ink.copy(alpha = if (dark) 0.12f else 0.08f)
    val label = TextStyle(fontFamily = glintFontFamily, fontSize = 11.sp, color = ink.copy(alpha = 0.55f))
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(170.dp)
            .semantics { contentDescription = "Heart rate this session, from ${samples.minOf { it.bpm }} to ${samples.maxOf { it.bpm }} beats per minute" }
            .pointerInput(samples.size) {
                detectDragGestures(
                    onDragStart = { probe = it.x },
                    onDragEnd = { probe = null },
                    onDragCancel = { probe = null },
                ) { change, _ -> probe = change.position.x }
            }
            .pointerInput(samples.size) { detectTapGestures(onPress = { probe = it.x; tryAwaitRelease(); probe = null }) }
    ) {
        val left = 34.dp.toPx()
        val top = 22.dp.toPx()
        val bottom = size.height - 18.dp.toPx()
        val right = size.width - 6.dp.toPx()
        fun x(t: Long) = left + (right - left) * ((t - t0).toFloat() / (t1 - t0))
        fun y(b: Int) = bottom - (bottom - top) * ((b - lo).toFloat() / (hi - lo))
        // Grid: three recessive levels with their values.
        for (v in listOf(lo, (lo + hi) / 2, hi)) {
            val gy = y(v)
            drawLine(grid, Offset(left, gy), Offset(right, gy), strokeWidth = 1.dp.toPx())
            val t = measurer.measure(v.toString(), label)
            drawText(t, topLeft = Offset(0f, gy - t.size.height / 2f))
        }
        // Time labels at both ends.
        val start = measurer.measure(timeFmt.format(Date(t0)), label)
        drawText(start, topLeft = Offset(left, bottom + 3.dp.toPx()))
        val end = measurer.measure(timeFmt.format(Date(t1)), label)
        drawText(end, topLeft = Offset(right - end.size.width, bottom + 3.dp.toPx()))
        // The line (thinned to about one point per pixel column).
        val step = (samples.size / (right - left)).toInt().coerceAtLeast(1)
        val path = Path()
        samples.forEachIndexed { i, s ->
            if (i % step != 0 && i != samples.lastIndex) return@forEachIndexed
            if (i == 0) path.moveTo(x(s.timeMs), y(s.bpm)) else path.lineTo(x(s.timeMs), y(s.bpm))
        }
        drawPath(path, line, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        // Latest reading: an 8dp dot with a 2dp surface ring.
        val lastP = Offset(x(samples.last().timeMs), y(samples.last().bpm))
        drawCircle(surface, 6.dp.toPx(), lastP)
        drawCircle(line, 4.dp.toPx(), lastP)
        // Crosshair and readout.
        probe?.let { px ->
            val target = t0 + ((px.coerceIn(left, right) - left) / (right - left) * (t1 - t0)).roundToInt()
            val s = samples.minBy { kotlin.math.abs(it.timeMs - target) }
            val sx = x(s.timeMs)
            drawLine(ink.copy(alpha = 0.35f), Offset(sx, top), Offset(sx, bottom), strokeWidth = 1.dp.toPx())
            drawCircle(surface, 6.dp.toPx(), Offset(sx, y(s.bpm)))
            drawCircle(line, 4.dp.toPx(), Offset(sx, y(s.bpm)))
            val read = measurer.measure("${s.bpm} BPM · ${timeFmt.format(Date(s.timeMs))}", label.copy(color = ink, fontWeight = FontWeight.Medium))
            val rx = (sx - read.size.width / 2f).coerceIn(left, right - read.size.width)
            drawText(read, topLeft = Offset(rx, 0f))
        }
    }
}
