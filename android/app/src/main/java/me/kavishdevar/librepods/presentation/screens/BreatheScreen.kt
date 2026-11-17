package me.kavishdevar.librepods.presentation.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import me.kavishdevar.librepods.breathing.*
import me.kavishdevar.librepods.presentation.navigation.LocalTabBarSpace

@Composable
fun BreatheScreen(navigateToApps: () -> Unit = {}) {
    val context = LocalContext.current
    val colors = breatheColors()
    val reduced = rememberBreatheReducedMotion()
    val haptics = LocalHapticFeedback.current
    val prefs = remember { BreathPrefs.prefs(context) }
    var appearance by remember { mutableStateOf(BreathPrefs.options(context)) }
    var selected by remember { mutableStateOf(BreathPrefs.selected(context).size) }
    var allowed by remember { mutableStateOf(BreathAccess.enabled(context)) }
    val connected by BreathAccess.connected.collectAsState()
    val needsAttention by BreathAccess.needsAttention.collectAsState()
    val owner = LocalLifecycleOwner.current
    DisposableEffect(prefs, owner) {
        fun refresh() {
            appearance = BreathPrefs.options(context)
            selected = BreathPrefs.selected(context).size
            allowed = BreathAccess.enabled(context)
        }
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> refresh() }
        val observer = object : androidx.lifecycle.DefaultLifecycleObserver {
            override fun onResume(owner: androidx.lifecycle.LifecycleOwner) { refresh() }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        owner.lifecycle.addObserver(observer)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener); owner.lifecycle.removeObserver(observer) }
    }
    val ready = selected > 0 && allowed && connected && !needsAttention
    val status = when {
        selected == 0 -> "Start with your apps"
        !allowed -> "One step to connect"
        !connected -> "Waiting for Android"
        needsAttention -> "Breathe needs attention"
        else -> "Ready when you open"
    }
    Column(Modifier.fillMaxSize().background(colors.background)
        .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Spacer(Modifier.height(WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 64.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("A MOMENT FOR YOU", color = colors.secondary, style = MaterialTheme.typography.labelSmall, letterSpacing = 1.8.sp)
            Text("Breathe.", color = colors.ink, style = MaterialTheme.typography.headlineLarge.copy(fontSize = 44.sp, lineHeight = 48.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-1.5).sp))
            Text("A little space before your apps.", color = colors.secondary, style = MaterialTheme.typography.bodyLarge)
        }
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .then(if (ready) Modifier else Modifier.clickable(role = Role.Button) {
                if (selected == 0) navigateToApps()
                else if (!open(context, BreathAccess.settings(context))) open(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }).heightIn(min = 48.dp).semantics { liveRegion = LiveRegionMode.Polite }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Box(Modifier.size(7.dp).background(if (ready) colors.ink else colors.secondary, RoundedCornerShape(50)))
            Text(status, color = colors.ink, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            if (!ready) Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = colors.ink, modifier = Modifier.size(18.dp))
        }
        BreathPreviewCard(appearance, reduced, colors)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Your everyday pause", color = colors.ink, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Surface(modifier = Modifier.animateContentSize(tween(if (reduced) 0 else 180)), color = colors.surface, shape = RoundedCornerShape(22.dp)) {
                Column {
                    BreatheActionRow(if (selected == 0) "Choose apps" else "Edit chosen apps",
                        if (selected == 0) "Start with the apps you reach for most."
                        else if (ready) "$selected ${if (selected == 1) "app" else "apps"} · 2.5 seconds · Always on"
                        else "$selected ${if (selected == 1) "app" else "apps"} chosen · 2.5-second pause",
                        colors, navigateToApps)
                    if (!ready) {
                        HorizontalDivider(Modifier.padding(horizontal = 18.dp), color = colors.line)
                        BreatheActionRow(if (allowed) "Check connection" else "Connect Breathe",
                            when {
                                !allowed -> "One permission in Android Settings."
                                !connected -> "Access is on. Waiting for Android to connect."
                                needsAttention -> "The pause couldn’t appear. Open a chosen app to retry."
                                else -> "Connected. Choose an app to finish setup."
                            }, colors) {
                            if (!open(context, BreathAccess.settings(context))) open(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        }
                    }
                }
            }
            if (!allowed || !connected || needsAttention) {
                BreatheDisclosure("Connection help", colors, reduced) {
                    Text("In Android Settings, choose pro · Breathe and allow access. If Android blocks it, open pro’s App info → ⋮ → Allow restricted settings, then return here.", color = colors.secondary, style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { open(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${context.packageName}"))) },
                        colors = ButtonDefaults.textButtonColors(contentColor = colors.ink)) { Text("Open pro’s App info") }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Make it yours", color = colors.ink, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Saved automatically", color = colors.secondary, style = MaterialTheme.typography.labelSmall)
            }
            BoxWithConstraints(Modifier.fillMaxWidth().selectableGroup()) {
                val stacked = maxWidth < 340.dp || LocalDensity.current.fontScale > 1.3f
                val choose: (BreathPalette) -> Unit = { palette ->
                    if (appearance.palette != palette) {
                        haptics.performHapticFeedback(HapticFeedbackType.ToggleOn)
                        appearance = appearance.copy(palette = palette)
                        BreathPrefs.setOptions(context, appearance)
                    }
                }
                if (stacked) Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    BreathPalette.entries.forEach { PaletteChoice(it, appearance.palette == it, colors, reduced, true, Modifier.fillMaxWidth()) { choose(it) } }
                } else Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BreathPalette.entries.forEach { PaletteChoice(it, appearance.palette == it, colors, reduced, false, Modifier.weight(1f)) { choose(it) } }
                }
            }
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(colors.surface)
                .toggleable(appearance.still, role = Role.Switch, onValueChange = {
                    haptics.performHapticFeedback(if (it) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
                    appearance = appearance.copy(still = it); BreathPrefs.setOptions(context, appearance)
                }).padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Still color layer", color = colors.ink, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    Text(if (reduced) "Reduced motion is on in your settings." else "A steady color, without the sweep.", color = colors.secondary, style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = appearance.still, onCheckedChange = null, colors = SwitchDefaults.colors(checkedTrackColor = colors.ink, checkedThumbColor = colors.surface))
            }
        }
        BreatheDisclosure("How Breathe works", colors, reduced) {
            Text("Keep your usual app icons, notifications and links. Breathe adds a 2.5-second pause without changing where they open.", color = colors.secondary, style = MaterialTheme.typography.bodyMedium)
            Text("Android reports an app after its window starts opening, so a brief glimpse can occur. Home or Back leaves the pause.", color = colors.secondary, style = MaterialTheme.typography.bodyMedium)
            Text("No screen text, messages or typing are read. No Bluetooth connection is needed. Android controls Accessibility access; reconnect here if it is removed.", color = colors.secondary, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(LocalTabBarSpace.current + 20.dp))
    }
}

@Composable
private fun BreatheActionRow(title: String, detail: String, colors: BreatheColors, action: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = action).padding(18.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, color = colors.ink, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(detail, color = colors.secondary, style = MaterialTheme.typography.bodySmall)
        }
        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = colors.ink, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun PaletteChoice(palette: BreathPalette, chosen: Boolean, colors: BreatheColors, reduced: Boolean, stacked: Boolean, modifier: Modifier, choose: () -> Unit) {
    val outline by animateColorAsState(if (chosen) colors.ink else colors.line, tween(if (reduced) 0 else 160), label = "Palette outline")
    val gradient = remember(palette) { Brush.linearGradient(palette.gradientColors.map { Color(it) }) }
    Column(modifier.clip(RoundedCornerShape(18.dp)).border(if (chosen) 2.dp else 1.dp, outline, RoundedCornerShape(18.dp))
        .background(colors.surface).selectable(chosen, role = Role.RadioButton, onClick = choose).padding(8.dp)) {
        Box(Modifier.fillMaxWidth().height(if (stacked) 56.dp else 78.dp).clip(RoundedCornerShape(11.dp)).background(gradient))
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(palette.label, color = colors.ink, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
            if (chosen) Icon(Icons.Default.Check, null, tint = colors.ink, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun BreatheDisclosure(title: String, colors: BreatheColors, reduced: Boolean, content: @Composable ColumnScope.() -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, tween(if (reduced) 0 else 160), label = "Help disclosure")
    Column(Modifier.fillMaxWidth().animateContentSize(tween(if (reduced) 0 else 180))) {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button) { expanded = !expanded }
            .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }.heightIn(min = 52.dp).padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = colors.ink, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Icon(Icons.Default.KeyboardArrowDown, null, tint = colors.secondary, modifier = Modifier.rotate(rotation))
        }
        if (expanded) Column(Modifier.padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

/** On-demand, finite playback. No auto-playing loop or background frame requests. */
@Composable
private fun BreathPreviewCard(appearance: BreathOptions, reduced: Boolean, colors: BreatheColors) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var replay by remember { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf(false) }
    var elapsed by remember { mutableLongStateOf(BreathSession.DURATION_MS) }
    val renderer = remember(appearance, reduced) { BreathView(context).apply { options = appearance; systemReduced = reduced } }
    LaunchedEffect(replay, renderer, lifecycle) {
        elapsed = BreathSession.DURATION_MS
        playing = false
        if (replay == 0 || renderer.motionReduced) return@LaunchedEffect
        var played = false
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (!played) {
                played = true; playing = true; elapsed = 0L
                try {
                    val start = withFrameNanos { it }
                    do { elapsed = withFrameNanos { ((it - start) / 1_000_000L).coerceIn(0, BreathSession.DURATION_MS) } }
                    while (elapsed < BreathSession.DURATION_MS)
                } finally { elapsed = BreathSession.DURATION_MS; playing = false }
            }
        }
    }
    Surface(color = colors.surface, shape = RoundedCornerShape(28.dp)) {
        Column {
            Canvas(Modifier.fillMaxWidth().height(230.dp).semantics { contentDescription = "Two-and-a-half-second breathing preview. Breathe in." }) {
                drawIntoCanvas { canvas ->
                    renderer.layout(0, 0, size.width.toInt(), size.height.toInt())
                    renderer.renderAt = elapsed
                    val native = canvas.nativeCanvas
                    val save = native.save()
                    native.clipRect(0f, 0f, size.width, size.height)
                    renderer.renderFrame(native)
                    native.restoreToCount(save)
                }
            }
            LinearProgressIndicator(progress = { elapsed / BreathSession.DURATION_MS.toFloat() }, modifier = Modifier.fillMaxWidth().height(3.dp).clearAndSetSemantics {}, color = if (playing) colors.ink else colors.line, trackColor = colors.line, drawStopIndicator = {})
            Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp, top = 5.dp, bottom = 5.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("2.5 seconds", color = colors.ink, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(if (renderer.motionReduced) "Still, by design" else "A gentle color sweep", color = colors.secondary, style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = { replay++ }, enabled = !playing, colors = ButtonDefaults.textButtonColors(contentColor = colors.ink, disabledContentColor = colors.secondary)) {
                    Icon(Icons.Default.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(if (playing) "Playing" else "Replay")
                }
            }
        }
    }
}
