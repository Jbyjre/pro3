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
    var permissionsRevision by remember { mutableIntStateOf(0) }
    val health by rememberPermissionHealth(permissionsRevision)
    val notes = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { permissionsRevision++ }
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
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            OutlinedButton(onClick = { PhoneControls.toggleTorch(context) }) { Text(if (torch) "Torch off" else "Torch on") }
            OutlinedButton(onClick = { PhoneControls.toggleRing(context) }) { Text("Sound / vibrate") }
        }
        PhoneTimer(timer)
        HorizontalDivider()
        UpdatePanel(health)
        HorizontalDivider()
        Text("Headphone pop-ups", style = MaterialTheme.typography.headlineSmall)
        Text("Brief connection, music and headphone events. Nothing stays around the camera.")
        StyledToggle(label = "Temporary pop-ups", checked = master, onCheckedChange = {
            master = it; prefs.edit().putBoolean(IslandPrefs.PREF_MASTER, it).apply()
            if (!it) GlintOverlays.dismissAll()
        })
        if (health?.overlays == false) {
            Text("Allow pop-ups over other apps. Breathing shortcuts work without this access.")
            OutlinedButton(onClick = { open(context,Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:${context.packageName}"))) }) { Text("Allow pop-ups") }
        }
        if (health?.music == false) {
            Text("Optional notification access gives music pop-ups song names and media controls.")
            OutlinedButton(onClick = { open(context,NowPlaying.accessSettingsIntent(context)) }) { Text("Allow music controls") }
        }
        if (health?.music == false) {
            Text("If Android blocks this switch, open pro’s App info and allow restricted settings first.",style=MaterialTheme.typography.bodySmall)
            TextButton(onClick={ open(context,NowPlaying.appInfoIntent(context)) }) { Text("pro’s App info") }
        }
        if (health?.notifications == false) {
            Text("Notifications let pro tell you when an update needs confirmation.",style=MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick={ notes.launch(android.Manifest.permission.POST_NOTIFICATIONS) }) { Text("Allow notifications") }
            TextButton(onClick={ open(context,Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName)) }) { Text("Notification settings") }
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

@Composable
private fun PhoneTimer(timer: IslandTimer.State?) {
    val context = LocalContext.current
    if (timer == null) {
        OutlinedButton(onClick = { IslandTimer.start(context, 300_000L) }) { Text("5 minute timer") }
        return
    }
    var left by remember(timer) { mutableLongStateOf(TimerRules.left(timer, android.os.SystemClock.elapsedRealtime())) }
    if (timer.running) {
        me.kavishdevar.librepods.presentation.glint.WhileShown(timer) {
            do {
                left = TimerRules.left(timer, android.os.SystemClock.elapsedRealtime())
                if (left > 0) kotlinx.coroutines.delay((left - 1) % 1000 + 1)
            } while (left > 0)
        }
    }
    Text(when {
        timer.ringing -> "Timer done"
        timer.paused -> "Paused · ${TimerRules.format(left)}"
        else -> "Timer · ${TimerRules.format(left)}"
    })
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!timer.ringing) OutlinedButton(onClick = {
            if (timer.paused) IslandTimer.resume(context) else IslandTimer.pause(context)
        }) { Text(if (timer.paused) "Resume" else "Pause") }
        OutlinedButton(onClick = { IslandTimer.addMinute(context) }) { Text("+1 minute") }
        OutlinedButton(onClick = { IslandTimer.cancel(context) }) { Text(if (timer.ringing) "Stop" else "Cancel timer") }
    }
}
