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

import androidx.compose.ui.platform.LocalView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.Image
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.ColorFilter
import me.kavishdevar.librepods.presentation.glint.IconAction
import me.kavishdevar.librepods.presentation.glint.RowIcons
import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.delay
import me.kavishdevar.librepods.presentation.components.InfoTip
import me.kavishdevar.librepods.presentation.glint.pressable
import me.kavishdevar.librepods.presentation.glint.riseIn
import me.kavishdevar.librepods.presentation.components.LiquidSegments
import me.kavishdevar.librepods.presentation.components.StyledToggle
import me.kavishdevar.librepods.presentation.glint.CapsuleShape
import me.kavishdevar.librepods.presentation.glint.GlassTier
import me.kavishdevar.librepods.presentation.glint.GlintComfort
import me.kavishdevar.librepods.presentation.glint.glintGlass
import me.kavishdevar.librepods.presentation.glint.heartPath
import me.kavishdevar.librepods.presentation.glint.rememberHeartBeat
import me.kavishdevar.librepods.services.GlintStatus
import me.kavishdevar.librepods.services.HeartBackup
import me.kavishdevar.librepods.services.HeartHistory
import me.kavishdevar.librepods.services.HeartInsights
import me.kavishdevar.librepods.services.HeartLink
import me.kavishdevar.librepods.services.HeartPace
import me.kavishdevar.librepods.services.HeartRate
import me.kavishdevar.librepods.services.LinkState
import me.kavishdevar.librepods.services.PREF_HR_AGE
import me.kavishdevar.librepods.services.PREF_HR_ALERT
import me.kavishdevar.librepods.services.PREF_HR_ALERT_BPM
import me.kavishdevar.librepods.services.PREF_HR_ALWAYS
import me.kavishdevar.librepods.services.PREF_HR_ISLAND
import me.kavishdevar.librepods.services.PREF_HR_PACE
import me.kavishdevar.librepods.services.PREF_LINK_BLE
import me.kavishdevar.librepods.services.PREF_LINK_BROADCAST
import me.kavishdevar.librepods.services.PREF_LINK_WEBHOOK
import me.kavishdevar.librepods.services.ServiceManager

/**
 * Heart rate from the AirPods Pro 3's sensor: a live card (rolling number, a heart that
 * beats at your rate inside a glass orb, an ECG-style sweep, colour that swells with each
 * beat), what the reading means on a scale, this session's line and trend, your history,
 * and the measuring options. Longer explanations sit behind small "i" buttons.
 */
@Composable
fun HeartRateScreen(navigateToShare: () -> Unit = {}, navigateToHistory: () -> Unit = {}) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    val state by HeartRate.state.collectAsState()
    val link by GlintStatus.link.collectAsState()
    val dark = isSystemInDarkTheme()
    val ink = if (dark) Color.White else Color.Black
    val muted = ink.copy(alpha = 0.6f)
    val card = if (dark) Color(0xFF1C1C1E) else Color.White
    val accent = HeartColors.accent(dark)
    val topPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 84.dp
    val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp
    val connected = link is LinkState.Connected
    val measuring = state.status != HeartRate.Status.Off
    val live = state.status == HeartRate.Status.Live
    var age by remember { mutableIntStateOf(prefs.getInt(PREF_HR_AGE, 0)) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(5_000); now = System.currentTimeMillis() } }
    val historyVersion by HeartHistory.version.collectAsState()
    val history = remember(historyVersion) { HeartRate.history(context) }
    val usual = remember(history) { HeartInsights.usualResting(history, System.currentTimeMillis()) ?: 0 }
    var index = 0

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(Modifier.height(topPadding - 14.dp))

        LiveCard(
            state = state, connected = connected, measuring = measuring, dark = dark, card = card, ink = ink, muted = muted, accent = accent,
            modifier = Modifier.riseIn(index++),
            onToggle = {
                val service = ServiceManager.getService()
                if (measuring) service?.stopHeartRate() else service?.startHeartRate()
            },
        )

        // What the reading means.
        val shownBpm = state.bpm?.takeIf { live || state.status == HeartRate.Status.Resting || state.status == HeartRate.Status.Starting }
        AnimatedVisibility(shownBpm != null, enter = fadeIn() + expandVertically(spring(0.85f, 300f)), exit = fadeOut() + shrinkVertically()) {
            val bpm = shownBpm ?: state.bpm ?: 70
            val m = HeartInsights.meaning(bpm, age, usual)
            Column(Modifier.fillMaxWidth().background(card, RoundedCornerShape(28.dp)).padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("What it means", modifier = Modifier.weight(1f), style = heartText(13, muted, FontWeight.Medium))
                    InfoTip(
                        "Reading your heart rate",
                        "For most adults a resting heart rate between 60 and 100 BPM is normal (American Heart Association). " +
                            "Lower is common when you're relaxed, asleep or very fit. It rises with movement, stress, caffeine, heat and illness. " +
                            "\"Usual\" is the middle of your resting estimates from the last 30 days. " +
                            "With your age set, readings above the light range show your effort zone instead. For fitness, not medical use."
                    )
                }
                AnimatedContent(m, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) }, label = "meaning") { mm ->
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(10.dp).background(HeartColors.band(mm.band, dark), CircleShape))
                            Spacer(Modifier.width(8.dp))
                            Text(mm.headline, style = heartText(18, ink, FontWeight.SemiBold))
                        }
                        Text(mm.detail, modifier = Modifier.padding(top = 4.dp), style = heartText(14, muted).copy(lineHeight = 19.sp))
                    }
                }
                Spacer(Modifier.height(12.dp))
                HeartScale(bpm, usual, age, dark, ink)
            }
        }

        // This session.
        if (state.samples.size >= 2) {
            Column(Modifier.riseIn(index++).fillMaxWidth().background(card, RoundedCornerShape(28.dp)).padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("This session", modifier = Modifier.weight(1f), style = heartText(17, ink, FontWeight.SemiBold))
                    HeartInsights.trend(state.samples, now)?.takeIf { live }?.let {
                        Text(it.words, style = heartText(13, muted), modifier = Modifier.padding(end = 12.dp))
                    }
                    IconAction(RowIcons.Share, "Export readings", ink, dark, size = 36.dp) { exportCsv(context, state.samples) }
                    Spacer(Modifier.width(8.dp))
                    IconAction(RowIcons.History, "Reset this session", ink, dark, tint = accent, size = 36.dp) { HeartRate.clear() }
                }
                Spacer(Modifier.height(10.dp))
                HeartChart(state.samples, accent, ink, card, dark, zoneAge = age, average = state.average)
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
                            "Resting, est.: your lowest 3-minute average this session (10-minute with a battery-saving pace); closest to your true resting rate when you've sat still for a while. " +
                                "1-min recovery: how far your heart rate fell in the minute after this session's highest reading (shown once that reading is 100 BPM or more). " +
                                "Bigger drops generally go with better fitness. In a well-known 1999 study, a drop of 12 BPM or less one minute after a treadmill test was linked with higher health risk. " +
                                "These are estimates, not a medical test."
                        )
                    }
                }
                if (age > 0) {
                    Spacer(Modifier.height(16.dp))
                    ZoneBar(HeartInsights.timeInZones(state.samples, age), accent, ink, muted)
                }
            }
        }

        // Today.
        TodayCard(history, state, usual, card, ink, muted, accent, Modifier.riseIn(index++))

        // History.
        HistoryCard(history, card, ink, muted, accent, dark, Modifier.riseIn(index++), navigateToHistory)

        // Share live.
        val shareStatus by HeartLink.status.collectAsState()
        val sharing = listOf(PREF_LINK_BLE, PREF_LINK_WEBHOOK, PREF_LINK_BROADCAST).count { prefs.getBoolean(it, false) }
        Row(
            Modifier.riseIn(index++).fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(card).pressable(navigateToShare).padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Share live heart rate", style = heartText(16, ink))
                Text(
                    when {
                        sharing == 0 -> "Watches, fitness apps, automations"
                        shareStatus.beacon is HeartLink.Beacon.Advertising && measuring -> "Live as a Bluetooth sensor"
                        else -> "$sharing on · while measuring"
                    },
                    style = heartText(13, muted)
                )
            }
            Text("›", style = heartText(24, muted))
        }

        // Effort zones need an age.
        Column(Modifier.riseIn(index++).fillMaxWidth().background(card, RoundedCornerShape(28.dp)).padding(horizontal = 18.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Effort zones", style = heartText(16, ink))
                    Text(
                        if (age > 0) "Age $age · max about ${HeartInsights.maxHeartRate(age)} BPM" else "Add your age to see them",
                        style = heartText(13, muted)
                    )
                }
                InfoTip(
                    "Effort zones",
                    "Based on the American Heart Association's guidance: your maximum heart rate is about 220 minus your age. " +
                        "Moderate effort is 50–70% of it, vigorous 70–85%. A normal resting rate for most adults is 60–100 BPM."
                )
                Stepper("−", ink, dark, "Younger") { age = if (age == 0) 30 else (age - 1).coerceAtLeast(13); prefs.edit { putInt(PREF_HR_AGE, age) } }
                Spacer(Modifier.width(8.dp))
                Stepper("+", ink, dark, "Older") { age = if (age == 0) 30 else (age + 1).coerceAtMost(90); prefs.edit { putInt(PREF_HR_AGE, age) } }
            }
        }

        // Always-on, its pace, and the island.
        var always by remember { mutableStateOf(prefs.getBoolean(PREF_HR_ALWAYS, false)) }
        var pace by remember { mutableIntStateOf(prefs.getInt(PREF_HR_PACE, 0)) }
        var island by remember { mutableStateOf(prefs.getBoolean(PREF_HR_ISLAND, true)) }
        Column(Modifier.riseIn(index++).fillMaxWidth().background(card, RoundedCornerShape(28.dp)).padding(horizontal = 4.dp)) {
            StyledToggle(
                label = "Measure whenever worn",
                description = when {
                    !always -> "Even with pro closed, no notification"
                    state.background && state.status == HeartRate.Status.Resting -> "Resting the sensor between readings"
                    state.background && live -> paceWords(HeartPace.of(pace))
                    else -> "Starts when you put your AirPods in"
                },
                checked = always,
                onCheckedChange = {
                    always = it
                    prefs.edit { putBoolean(PREF_HR_ALWAYS, it) }
                    ServiceManager.getService()?.autoHeartRate()
                }
            )
            AnimatedVisibility(always, enter = fadeIn() + expandVertically(spring(0.85f, 300f)), exit = fadeOut() + shrinkVertically()) {
                Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Pace", modifier = Modifier.weight(1f), style = heartText(13, muted, FontWeight.Medium))
                        InfoTip(
                            "Measuring pace",
                            "Every 5 s: the sensor stays on while you wear your AirPods, with a reading about every 5 seconds (every second if your AirPods don't accept that). " +
                                "Balanced: one minute of readings every 5 minutes, so the sensor is on about 80% less. " +
                                "Saver: one minute every 15 minutes, about 93% less. " +
                                "Fewer readings mean less battery on your AirPods and less Bluetooth traffic; the exact saving hasn't been measured. " +
                                "Manual sessions (Start measuring) always read continuously. A new pace applies from the next time measuring starts."
                        )
                    }
                    LiquidSegments(
                        listOf("Every 5 s", "Balanced", "Saver"), pace,
                        { pace = it; prefs.edit { putInt(PREF_HR_PACE, it) } },
                        track = if (dark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.04f),
                    )
                    Text(paceWords(HeartPace.of(pace)), modifier = Modifier.padding(top = 6.dp, start = 4.dp), style = heartText(13, muted))
                }
            }
            StyledToggle(
                label = "High heart rate on the island",
                description = if (island) "Pops up for alerts. Otherwise the heart shows when you open the island" else "Only when you open the island",
                checked = island,
                onCheckedChange = { island = it; prefs.edit { putBoolean(PREF_HR_ISLAND, it) } }
            )
        }

        // Alert.
        var alert by remember { mutableStateOf(prefs.getBoolean(PREF_HR_ALERT, false)) }
        var limit by remember { mutableIntStateOf(prefs.getInt(PREF_HR_ALERT_BPM, 140)) }
        Column(Modifier.riseIn(index++).fillMaxWidth().background(card, RoundedCornerShape(28.dp)).padding(horizontal = 4.dp)) {
            StyledToggle(
                label = "High heart rate alert",
                description = if (alert) "Above $limit BPM, at most every 10 minutes" else null,
                checked = alert,
                onCheckedChange = { alert = it; prefs.edit { putBoolean(PREF_HR_ALERT, it) } }
            )
            AnimatedVisibility(alert, enter = fadeIn() + expandVertically(spring(0.85f, 300f)), exit = fadeOut() + shrinkVertically()) {
                Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Limit", style = heartText(16, ink), modifier = Modifier.weight(1f))
                    Stepper("−", ink, dark, "Lower limit") { limit = (limit - 5).coerceAtLeast(90); prefs.edit { putInt(PREF_HR_ALERT_BPM, limit) } }
                    Box(Modifier.width(96.dp), contentAlignment = Alignment.Center) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            RollingNumber("$limit", heartText(16, ink, FontWeight.SemiBold))
                            Text(" BPM", style = heartText(16, ink, FontWeight.SemiBold))
                        }
                    }
                    Stepper("+", ink, dark, "Raise limit") { limit = (limit + 5).coerceAtMost(200); prefs.edit { putInt(PREF_HR_ALERT_BPM, limit) } }
                }
            }
        }

        Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("For fitness, not medical use", style = heartText(13, muted))
            InfoTip(
                "About these readings",
                "Measured by the sensor in AirPods Pro 3 while you wear them; a snug fit in both ears gives the steadiest readings. " +
                    "Readings stay on this phone unless you turn on sharing, export them, or back them up to your own private GitHub repository (History)."
            )
        }
        Spacer(Modifier.height(bottomPadding))
    }
}

internal fun paceWords(p: HeartPace) = when (p) {
    HeartPace.Continuous -> "A reading about every 5 seconds"
    HeartPace.Balanced -> "1 minute of readings every 5 minutes"
    HeartPace.Saver -> "1 minute of readings every 15 minutes"
}

/**
 * The live reading, on a field of colour that drifts and swells with each beat. The heart
 * sits in a glass orb and the Start/Stop button is a glass capsule: both bend and blur the
 * colour behind them (real refraction on the graphics chip). Solid with Reduce transparency.
 */
@Composable
private fun LiveCard(
    state: HeartRate.State,
    connected: Boolean,
    measuring: Boolean,
    dark: Boolean,
    card: Color,
    ink: Color,
    muted: Color,
    accent: Color,
    modifier: Modifier,
    onToggle: () -> Unit,
) {
    val context = LocalContext.current
    val reduceMotion = remember { GlintComfort.reduceMotion(context) }
    val solid = remember { GlintComfort.reduceTransparency(context) }
    val live = state.status == HeartRate.Status.Live
    val bpm = state.bpm?.takeIf { live }
    val shown = state.bpm?.takeIf { live || state.status == HeartRate.Status.Resting || state.status == HeartRate.Status.Starting }
    val beat = rememberHeartBeat(bpm, reduceMotion)
    val time = rememberFrameTime(running = bpm != null && !reduceMotion)
    val backdrop = rememberLayerBackdrop()
    Box(modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))) {
        Canvas(Modifier.matchParentSize().layerBackdrop(backdrop)) {
            val b = ((beat.value - 1f) / 0.14f).coerceIn(0f, 1f)
            drawAurora(card, accent, dark, time.longValue, b, live)
        }
        Column(Modifier.fillMaxWidth().padding(vertical = 22.dp, horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            // The heart in a glass orb. It's also a button: like Apple's interactive glass it
            // swells and lights up under your finger, then settles back with a soft bounce.
            val orbSource = remember { MutableInteractionSource() }
            val orbPressed by orbSource.collectIsPressedAsState()
            val orbEnabled = connected || measuring
            val swell by animateFloatAsState(if (orbPressed && !reduceMotion && orbEnabled) 1.1f else 1f, spring(dampingRatio = 0.42f, stiffness = 420f), label = "orbSwell")
            val view = LocalView.current
            Box(
                Modifier
                    .size(72.dp)
                    .graphicsLayer { scaleX = swell; scaleY = swell }
                    .glintGlass(
                        backdrop, dark, solid, CircleShape, GlassTier.Floating,
                        tint = if (orbPressed) (if (dark) Color.White.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.55f))
                        else if (dark) Color.White.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.35f)
                    )
                    .clip(CircleShape)
                    .semantics { role = Role.Button; contentDescription = if (measuring) "Stop measuring" else "Start measuring" }
                    .clickable(interactionSource = orbSource, indication = null, enabled = orbEnabled) {
                        view.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
                        onToggle()
                    },
                contentAlignment = Alignment.Center
            ) {
                Canvas(Modifier.size(36.dp).graphicsLayer { scaleX = beat.value; scaleY = beat.value }) {
                    val p = heartPath(size)
                    drawPath(p, if (bpm == null) accent.copy(alpha = 0.4f) else accent)
                    // A little gloss on the heart itself.
                    drawPath(p, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.35f), Color.Transparent), 0f, size.height * 0.55f))
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
                if (shown != null) {
                    RollingNumber(
                        "$shown",
                        heartText(60, if (live) ink else ink.copy(alpha = 0.55f), FontWeight.SemiBold),
                        Modifier.alignByBaseline()
                    )
                } else {
                    Text("--", modifier = Modifier.alignByBaseline(), style = heartText(60, ink, FontWeight.SemiBold))
                }
                Spacer(Modifier.width(6.dp))
                Text("BPM", modifier = Modifier.alignByBaseline(), style = heartText(17, muted, FontWeight.Medium))
            }
            Text(statusLine(state, connected), style = heartText(14, muted).copy(textAlign = TextAlign.Center))
            Spacer(Modifier.height(10.dp))
            EkgTrace(bpm, accent, time)
            Spacer(Modifier.height(14.dp))
            val enabled = connected || measuring
            Box(
                Modifier
                    .glintGlass(backdrop, dark, solid, CapsuleShape, GlassTier.Inline, tint = if (dark) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.5f))
                    .clip(CapsuleShape)
                    .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
                    .semantics { role = Role.Button; contentDescription = if (measuring) "Stop measuring" else "Start measuring" }
                    .then(if (enabled) Modifier.pressable(onToggle) else Modifier)
                    .padding(horizontal = 30.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                // A play or stop symbol instead of words; it spins and swaps like a morph.
                AnimatedContent(
                    measuring,
                    transitionSpec = {
                        (fadeIn(tween(160)) + scaleIn(spring(0.55f, 600f), 0.4f)) togetherWith (fadeOut(tween(100)) + scaleOut(tween(120), 0.4f))
                    },
                    label = "toggle"
                ) { m ->
                    Image(
                        if (m) RowIcons.Stop else RowIcons.Play,
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(if (m) accent else ink),
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
    }
}

private fun statusLine(state: HeartRate.State, connected: Boolean): String = when (state.status) {
    HeartRate.Status.Off -> if (connected) "Not measuring" else "Connect your AirPods first"
    HeartRate.Status.Starting -> "Starting…"
    HeartRate.Status.Live -> if (state.background) "Live · measuring while worn" else "Live"
    HeartRate.Status.NoSignal -> "No reading. Check both buds fit snugly"
    HeartRate.Status.NotConnected -> "Waiting for the AirPods"
    HeartRate.Status.Resting -> {
        val mins = ((state.nextBurstMs - System.currentTimeMillis()) / 60_000L + 1).coerceAtLeast(1)
        "Last reading · next in about $mins min"
    }
}

/** History at a glance: the last 14 days as range bars, totals, and the backup status. */
@Composable
private fun HistoryCard(
    history: List<HeartInsights.Session>,
    card: Color,
    ink: Color,
    muted: Color,
    accent: Color,
    dark: Boolean,
    modifier: Modifier,
    onOpen: () -> Unit,
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { HeartBackup.refresh(context) }
    val backup by HeartBackup.status.collectAsState()
    val days = remember(history) { HeartInsights.days(history) }
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(card).pressable(onOpen).padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("History", style = heartText(17, ink, FontWeight.SemiBold))
                Text(
                    if (history.isEmpty()) "Sessions of a minute or more appear here"
                    else "${history.size} sessions · ${durationWords(history.sumOf { it.minutes })} measured",
                    style = heartText(13, muted)
                )
            }
            Text("›", style = heartText(24, muted))
        }
        if (days.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            DayBars(days, 14, accent, ink, dark, selected = null, onSelect = { onOpen() }, height = 110.dp)
        }
        Spacer(Modifier.height(6.dp))
        Text(backupLine(backup), style = heartText(12, muted))
    }
}

internal fun backupLine(s: HeartBackup.Status): String = when (s) {
    HeartBackup.Status.Off -> "Not backed up · set up GitHub backup in History"
    HeartBackup.Status.Idle -> "GitHub backup on"
    is HeartBackup.Status.Working -> s.what
    is HeartBackup.Status.Done -> if (s.atMs > 0) "Backed up " + android.text.format.DateUtils.getRelativeTimeSpanString(s.atMs, System.currentTimeMillis(), 60_000L) else "GitHub backup on"
    is HeartBackup.Status.Problem -> s.message
}

/**
 * Today at a glance: time measured, lowest to highest, the day's average, and today's resting
 * estimate against your usual. Counts saved sessions plus the one being measured now.
 */
@Composable
private fun TodayCard(
    history: List<HeartInsights.Session>,
    state: HeartRate.State,
    usual: Int,
    card: Color,
    ink: Color,
    muted: Color,
    accent: Color,
    modifier: Modifier,
) {
    val zone = java.time.ZoneId.systemDefault()
    val today = java.time.LocalDate.now(zone)
    val live = HeartInsights.summarize(state.samples)
    val sessions = (history.filter { java.time.Instant.ofEpochMilli(it.startMs).atZone(zone).toLocalDate() == today } +
        listOfNotNull(live?.takeIf { java.time.Instant.ofEpochMilli(it.startMs).atZone(zone).toLocalDate() == today }))
        .distinctBy { it.startMs }
    if (sessions.isEmpty()) return
    val day = HeartInsights.days(sessions, zone).firstOrNull() ?: return
    Column(modifier.fillMaxWidth().background(card, RoundedCornerShape(28.dp)).padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Today", modifier = Modifier.weight(1f), style = heartText(17, ink, FontWeight.SemiBold))
            Text("${durationWords(day.minutes.coerceAtLeast(1))} measured", style = heartText(13, muted))
        }
        Spacer(Modifier.height(12.dp))
        RangeBar(day.min, day.max, day.average, 40, 200, accent, ink, Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth()) {
            Stat("Lowest", day.min, ink, muted, Modifier.weight(1f))
            Stat("Average", day.average, ink, muted, Modifier.weight(1f))
            Stat("Highest", day.max, ink, muted, Modifier.weight(1f))
        }
        if (day.resting > 0) {
            Spacer(Modifier.height(10.dp))
            val d = if (usual > 0) day.resting - usual else 0
            Text(
                "Resting today ${day.resting} BPM" + when {
                    usual <= 0 -> ""
                    d >= 3 -> " · $d above your usual"
                    d <= -3 -> " · ${-d} below your usual"
                    else -> " · right around your usual"
                },
                style = heartText(13, muted).copy(lineHeight = 18.sp)
            )
        }
    }
}

