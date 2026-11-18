package me.kavishdevar.librepods.presentation.screens

import android.app.ActivityOptions
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.kavishdevar.librepods.BuildConfig
import me.kavishdevar.librepods.presentation.components.StyledToggle
import me.kavishdevar.librepods.updates.*

@Composable
fun UpdatePanel(health: PermissionHealth? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by Updater.state.collectAsState()
    val prefs = remember { Updater.prefs(context) }
    var channel by remember { mutableStateOf(Updater.channel(context)) }
    var checks by remember { mutableStateOf(prefs.getBoolean("checks", true)) }
    var downloads by remember { mutableStateOf(prefs.getBoolean("download", true)) }
    var options by remember { mutableStateOf(false) }
    var installs by remember { mutableStateOf(prefs.getBoolean("install", false)) }
    LaunchedEffect(Unit) { withContext(Dispatchers.IO) { UpdateInstaller.refresh(context); UpdateJob.schedule(context) } }
    Text("App updates", style = MaterialTheme.typography.headlineSmall)
    Text("Installed build ${BuildConfig.VERSION_CODE} · $channel", style = MaterialTheme.typography.bodyMedium)
    Text(state.message)
    if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
        OutlinedButton(enabled = !state.busy, onClick = { scope.launch { Updater.check(context) } }) { Text("Check now") }
        if (state.available && !state.ready) Button(enabled = !state.busy, onClick = { scope.launch { Updater.download(context) } }) { Text("Download") }
    }
    if (state.ready && prefs.getInt("session", -1) < 0) {
        Button(enabled = !state.busy && health != null, onClick = {
            if (!context.packageManager.canRequestPackageInstalls()) open(context,Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, android.net.Uri.parse("package:${context.packageName}")))
            else scope.launch { Updater.state.value = state.copy(busy = true); UpdateInstaller.install(context) }
        }) { Text(if (health?.install == true) "Update" else if (health == null) "Checking access…" else "Allow pro updates") }
    }
    if (prefs.getInt("session", -1) >= 0) {
        OutlinedButton(onClick = {
            runCatching { UpdateInstaller.confirmation(context)?.send(context, 0, null, null, null, null,
                ActivityOptions.makeBasic().apply {
                    if (android.os.Build.VERSION.SDK_INT >= 36) setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE)
                    else if (android.os.Build.VERSION.SDK_INT >= 34) setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                }.toBundle()) }
        }) { Text("Finish installation") }
        TextButton(onClick = { UpdateInstaller.cancel(context) }) { Text("Cancel pending installation") }
    }
    val automatic = checks && downloads && installs
    fun allowUpdates() = open(context, Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
        android.net.Uri.parse("package:${context.packageName}")))
    StyledToggle(label = "Automatic updates", checked = automatic,
        description = "Download on an unmetered connection and install while your phone is idle. Android may ask you to confirm.",
        onCheckedChange = { enabled ->
            installs = enabled
            val edit = prefs.edit().putBoolean("install", enabled)
            if (enabled) {
                checks = true
                downloads = true
                edit.putBoolean("checks", true).putBoolean("download", true)
            }
            edit.apply()
            UpdateJob.schedule(context)
            if (enabled && !context.packageManager.canRequestPackageInstalls()) allowUpdates()
        })
    if (automatic && health?.install == false && !state.ready) {
        OutlinedButton(onClick = { allowUpdates() }) { Text("Allow pro updates") }
    }
    TextButton(onClick = { options = !options }) { Text(if (options) "Hide update options" else "Update options") }
    if (options) {
    StyledToggle(label = "Check automatically on Wi-Fi", checked = checks, onCheckedChange = {
        checks = it
        if (!it) installs = false
        prefs.edit().putBoolean("checks", it).putBoolean("install", installs).apply()
        UpdateJob.schedule(context)
    })
    StyledToggle(label = "Download automatically", checked = downloads, onCheckedChange = {
        downloads = it
        if (!it) installs = false
        prefs.edit().putBoolean("download", it).putBoolean("install", installs).apply()
    })
    TextButton(enabled = !state.busy, onClick = {
        channel = if (channel == "stable") "preview" else "stable"
        prefs.edit().putString("channel", channel).apply()
        scope.launch { withContext(Dispatchers.IO) { Updater.apk(context).delete(); Updater.restore(context) }; Updater.check(context) }
    }) { Text(if (channel == "stable") "Use preview releases" else "Use stable releases") }    }

}
