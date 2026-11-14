package me.kavishdevar.librepods.presentation.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import me.kavishdevar.librepods.breathing.*
import me.kavishdevar.librepods.presentation.components.StyledToggle
import me.kavishdevar.librepods.presentation.navigation.LocalTabBarSpace

@Composable
fun BreatheScreen(navigateToApps: () -> Unit = {}) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(BreathPrefs.enabled(context)) }
    var paused by remember { mutableStateOf(BreathPrefs.paused(context)) }
    var preview by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer)
        .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Spacer(Modifier.height(WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 84.dp))
        Text("A little space.", style = MaterialTheme.typography.headlineLarge)
        Text("One breath before the apps you choose.", style = MaterialTheme.typography.bodyLarge)
        StyledToggle(label = "Breathing pause", checked = enabled, onCheckedChange = {
            enabled = it; BreathPrefs.prefs(context).edit().putBoolean("enabled", it).apply()
        })
        key(preview) {
            BreathPreview(Modifier.fillMaxWidth().height(280.dp))
        }
        OutlinedButton(onClick = { preview++ }) { Text("Preview one second") }
        Button(onClick = navigateToApps, modifier = Modifier.fillMaxWidth()) { Text("Choose apps & add shortcuts") }
        Text("Add a breathing shortcut for each chosen app, then use that icon on your home screen. Your launcher stays the same. Android may ask you to confirm adding it.", style = MaterialTheme.typography.bodyMedium)
        Text("Only these shortcuts pause before opening. Original icons, the app drawer, Recents, notifications and links open directly. No Accessibility access or Bluetooth is needed.", style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = {
            paused = !paused
            BreathPrefs.prefs(context).edit().putLong("paused_until", if (paused) System.currentTimeMillis() + 15 * 60_000L else 0).apply()
        }) { Text(if (paused) "Resume now" else "Pause for 15 minutes") }
        Text("Home or Back cancels. The pause follows your system’s reduced-motion setting and never grows longer with animation speed settings.", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(LocalTabBarSpace.current + 36.dp))
    }
}


/** Shares the production paint path; only the preview frame clock belongs to Compose. */
@Composable
private fun BreathPreview(modifier: Modifier) {
    val context = LocalContext.current
    val renderer = remember { BreathView(context) }
    var elapsed by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        do {
            elapsed = withFrameNanos { ((it-start)/1_000_000L).coerceIn(0,1_000) }
        } while (elapsed < 1_000L)
    }
    Canvas(modifier.semantics { contentDescription = "One-second breathing preview. Breathe in." }) {
        drawIntoCanvas { canvas ->
            renderer.layout(0,0,size.width.toInt(),size.height.toInt())
            renderer.renderAt = elapsed
            val native = canvas.nativeCanvas
            val save = native.save()
            native.clipRect(0f,0f,size.width,size.height)
            renderer.renderFrame(native)
            native.restoreToCount(save)
        }
    }
}
