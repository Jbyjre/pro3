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
fun UpdatePanel() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by Updater.state.collectAsState()
    val prefs = remember { Updater.prefs(context) }
    var channel by remember { mutableStateOf(Updater.channel(context)) }
    var checks by remember { mutableStateOf(prefs.getBoolean("checks", true)) }
    var downloads by remember { mutableStateOf(prefs.getBoolean("download", true)) }
    var options by remember { mutableStateOf(false) }
    var installs by remember { mutableStateOf(prefs.getBoolean("install", false)) }
    LaunchedEffect(Unit) { withContext(Dispatchers.IO) { Updater.restore(context); UpdateJob.schedule(context) } }
    Text("App updates", style = MaterialTheme.typography.headlineSmall)
    Text("Installed build ${BuildConfig.VERSION_CODE} · $channel", style = MaterialTheme.typography.bodyMedium)
    Text(state.message)
    if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(enabled = !state.busy, onClick = { scope.launch { Updater.check(context) } }) { Text("Check now") }
        if (state.available && !state.ready) Button(enabled = !state.busy, onClick = { scope.launch { Updater.download(context) } }) { Text("Download") }
    }
    if (state.ready && prefs.getInt("session", -1) < 0) {
        Button(enabled = !state.busy, onClick = {
            if (!context.packageManager.canRequestPackageInstalls()) context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, android.net.Uri.parse("package:${context.packageName}")))
            else scope.launch { Updater.state.value = state.copy(busy = true); UpdateInstaller.install(context) }
        }) { Text(if (context.packageManager.canRequestPackageInstalls()) "Update" else "Allow pro updates") }
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
    TextButton(onClick = { options = !options }) { Text(if (options) "Hide update options" else "Update options") }
    if (options) {
    StyledToggle(label = "Check automatically on Wi-Fi", checked = checks, onCheckedChange = {
        checks = it; prefs.edit().putBoolean("checks", it).apply(); UpdateJob.schedule(context)
    })
    StyledToggle(label = "Download automatically", checked = downloads, onCheckedChange = { downloads = it; prefs.edit().putBoolean("download", it).apply() })
    StyledToggle(label = "Install while phone is idle", checked = installs, onCheckedChange = {
        installs = it; prefs.edit().putBoolean("install", it).apply()
        if (it && !context.packageManager.canRequestPackageInstalls()) context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, android.net.Uri.parse("package:${context.packageName}")))
    })
    Text("Android may require installation confirmation. Samsung security settings may also require approval. An update restarts pro; your settings stay.", style = MaterialTheme.typography.bodySmall)
    TextButton(enabled = !state.busy, onClick = {
        channel = if (channel == "stable") "preview" else "stable"
        prefs.edit().putString("channel", channel).apply()
        scope.launch { withContext(Dispatchers.IO) { Updater.apk(context).delete(); Updater.restore(context) }; Updater.check(context) }
    }) { Text(if (channel == "stable") "Use preview releases" else "Use stable releases") }    }

}
