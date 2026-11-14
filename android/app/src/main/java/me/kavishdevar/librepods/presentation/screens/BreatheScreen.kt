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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
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
            AndroidView(factory = { BreathView(it) }, modifier = Modifier.fillMaxWidth().height(280.dp).clip(RoundedCornerShape(28.dp)))
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
