package me.kavishdevar.librepods.presentation.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import me.kavishdevar.librepods.presentation.components.StyledToggle
import me.kavishdevar.librepods.presentation.navigation.LocalTabBarSpace
import me.kavishdevar.librepods.presentation.overlays.*
import me.kavishdevar.librepods.services.*

@Composable
fun PhoneScreen(navigateToIsland: () -> Unit = {}, navigateToHeadphones: (() -> Unit)? = null, navigateToApps: () -> Unit = {}) {
    val context = LocalContext.current
    val prefs = remember { IslandPrefs.prefs(context) }
    var popupOptions by remember { mutableStateOf(false) }
    var master by remember { mutableStateOf(prefs.getBoolean(IslandPrefs.PREF_MASTER, true)) }
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = object : androidx.lifecycle.DefaultLifecycleObserver {
            override fun onResume(owner: androidx.lifecycle.LifecycleOwner) { PhoneStatus.attach(context); PhoneControls.watchTorch(context) }
            override fun onPause(owner: androidx.lifecycle.LifecycleOwner) { PhoneStatus.detach(context); PhoneControls.stopWatchingTorch(context) }
        }
        owner.lifecycle.addObserver(observer)
        if (owner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) observer.onResume(owner)
        onDispose { owner.lifecycle.removeObserver(observer); PhoneStatus.detach(context); PhoneControls.stopWatchingTorch(context) }
    }
    val battery by PhoneStatus.state.collectAsState()
    val torch by PhoneControls.torch.collectAsState()
    val timer by IslandTimer.state.collectAsState()
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer)
        .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Spacer(Modifier.height(WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 84.dp))
        me.kavishdevar.librepods.presentation.components.FreezeBanner()
        Text("This phone", style = MaterialTheme.typography.headlineSmall)
        if (battery.known) Text("${battery.level}% battery${if (battery.charging) " · charging" else ""}")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { PhoneControls.toggleTorch(context) }) { Text(if (torch) "Torch off" else "Torch on") }
            OutlinedButton(onClick = { PhoneControls.toggleRing(context) }) { Text("Sound / vibrate") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (timer == null) OutlinedButton(onClick = { IslandTimer.start(context, 5 * 60_000L) }) { Text("5 minute timer") }
            else OutlinedButton(onClick = { IslandTimer.cancel(context) }) { Text("Cancel timer") }
        }
        HorizontalDivider()
        UpdatePanel()
        HorizontalDivider()
        Text("Headphone pop-ups", style = MaterialTheme.typography.headlineSmall)
        Text("Brief connection, music and headphone events. Nothing stays around the camera.")
        StyledToggle(label = "Temporary pop-ups", checked = master, onCheckedChange = {
            master = it; prefs.edit().putBoolean(IslandPrefs.PREF_MASTER, it).apply()
            if (!it) GlintOverlays.dismissAll()
        })
        if (!Settings.canDrawOverlays(context)) {
            Text("Allow pop-ups over other apps. Breathing shortcuts work without this access.")
            OutlinedButton(onClick = { context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:${context.packageName}"))) }) { Text("Allow pop-ups") }
        }
        if (!NowPlaying.hasAccess(context)) {
            Text("Optional notification access gives music pop-ups song names and media controls.")
            OutlinedButton(onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }) { Text("Allow music controls") }
        }
        TextButton(onClick = { popupOptions = !popupOptions }) { Text(if (popupOptions) "Hide pop-up options" else "Pop-up options") }
        if (popupOptions) {
        IslandPrefs.Trigger.entries.forEach { trigger ->
            var on by remember(trigger) { mutableStateOf(prefs.getBoolean(trigger.key, trigger.default)) }
            StyledToggle(label = trigger.label, checked = on, onCheckedChange = {
                on = it; prefs.edit().putBoolean(trigger.key, it).apply()
            })
        }
        }
        OutlinedButton(onClick = { GlintOverlays.showIsland(context, IslandEvent.Connected) }) { Text("Try a pop-up") }
        Spacer(Modifier.height(LocalTabBarSpace.current + 36.dp))
    }
}
