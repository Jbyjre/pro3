package me.kavishdevar.librepods.presentation.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import me.kavishdevar.librepods.breathing.*
import me.kavishdevar.librepods.presentation.components.StyledToggle
import me.kavishdevar.librepods.presentation.glint.GlintComfort
import me.kavishdevar.librepods.presentation.navigation.LocalTabBarSpace

@Composable
fun BreatheScreen(navigateToApps: () -> Unit = {}) {
    val context = LocalContext.current
    val prefs = remember { BreathPrefs.prefs(context) }
    var appearance by remember { mutableStateOf(BreathPrefs.options(context)) }
    var selected by remember { mutableStateOf(BreathPrefs.selected(context).size) }
    var allowed by remember { mutableStateOf(BreathAccess.enabled(context)) }
    val connected by BreathAccess.connected.collectAsState()
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
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
    var preview by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer)
        .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Spacer(Modifier.height(WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 84.dp))
        Text("Breathe.", style = MaterialTheme.typography.headlineLarge)
        Text("A pause before your chosen apps.", style = MaterialTheme.typography.bodyLarge)
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(when {
                    selected == 0 -> "Start with your apps"
                    !allowed -> "One step to connect"
                    !connected -> "Waiting for Android"
                    else -> "Ready when you open"
                }, style = MaterialTheme.typography.titleMedium)
                Text(when {
                    selected == 0 -> "Choose the apps where you want a moment to breathe."
                    !allowed -> "Allow pro · Breathe in Accessibility to cover your usual app icons, notifications and links."
                    !connected -> "Access is enabled, but Breathe is not connected yet. If this stays, reopen Accessibility and reconnect pro · Breathe."
                    else -> "$selected ${if (selected == 1) "app" else "apps"} · 2.5 seconds · Always on"
                }, style = MaterialTheme.typography.bodyMedium)
                Button(onClick = navigateToApps, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onSurface, contentColor = MaterialTheme.colorScheme.surface)) {
                    Text(if (selected == 0) "Choose apps" else "Edit chosen apps")
                }
                if (!allowed || !connected) {
                    OutlinedButton(onClick = {
                        if (!open(context, BreathAccess.settings(context))) open(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) { Text(if (allowed) "Check connection" else "Connect Breathe") }
                    Text("In Android Settings, choose pro · Breathe and allow access. If Android blocks it, open pro’s App info → ⋮ → Allow restricted settings, then return here.", style = MaterialTheme.typography.bodySmall)
                    TextButton(colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface), onClick = {
                        open(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${context.packageName}")))
                    }) { Text("Open pro’s App info") }
                }
            }
        }
        key(preview, appearance) { BreathPreview(appearance, Modifier.fillMaxWidth().height(280.dp).clip(RoundedCornerShape(24.dp))) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("2.5 seconds", style = MaterialTheme.typography.labelLarge)
            TextButton(colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface), onClick = { preview++ }) { Text("Replay") }
        }
        Text("Make it yours", style = MaterialTheme.typography.titleMedium)
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            BreathPalette.entries.forEach { palette ->
                val swatch = remember(palette) { Brush.horizontalGradient(palette.gradientColors.map { Color(it) }) }
                FilterChip(colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    labelColor = MaterialTheme.colorScheme.onSurface,
                    selectedContainerColor = MaterialTheme.colorScheme.onSurface,
                    selectedLabelColor = MaterialTheme.colorScheme.surface,
                ), modifier = Modifier.heightIn(min = 48.dp), selected = appearance.palette == palette, onClick = {
                    appearance = appearance.copy(palette = palette); BreathPrefs.setOptions(context, appearance)
                }, label = { Text(palette.label) }, leadingIcon = {
                    Canvas(Modifier.size(24.dp, 16.dp).clip(RoundedCornerShape(3.dp))) { drawRect(swatch) }
                })
            }
        }
        StyledToggle(label = "Still color layer", checked = appearance.still, onCheckedChange = {
            appearance = appearance.copy(still = it); BreathPrefs.setOptions(context, appearance)
        })
        Text("A calm color sweep, or a still moment. Both last 2.5 seconds and follow your system’s reduced-motion setting.", style = MaterialTheme.typography.bodySmall)
        Text("Open apps the way you always do. Breathe appears when Android reports the app’s window; a brief glimpse can still occur. Your notification or link stays exactly where it opened.", style = MaterialTheme.typography.bodyMedium)
        Text("No screen text, messages or typing are read. No Bluetooth connection is needed. Home or Back leaves the pause. Android controls Accessibility access; if it’s removed, reconnect here.", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(LocalTabBarSpace.current + 36.dp))
    }
}

/** Preview and both live entry routes share the same native paint path and duration. */
@Composable
private fun BreathPreview(appearance: BreathOptions, modifier: Modifier) {
    val context = LocalContext.current
    val renderer = remember(appearance) { BreathView(context).apply { options = appearance; systemReduced = GlintComfort.reduceMotion(context) } }
    var elapsed by remember { mutableLongStateOf(0L) }
    LaunchedEffect(renderer) {
        if (renderer.motionReduced) { elapsed = 500L; return@LaunchedEffect }
        val start = withFrameNanos { it }
        do {
            elapsed = withFrameNanos { ((it - start) / 1_000_000L).coerceIn(0, BreathSession.DURATION_MS) }
        } while (elapsed < BreathSession.DURATION_MS)
    }
    Canvas(modifier.semantics { contentDescription = "Two-and-a-half-second breathing preview. Breathe in." }) {
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
}
