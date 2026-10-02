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

package me.kavishdevar.librepods.presentation.screens.onboarding

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.sp
import me.kavishdevar.librepods.presentation.glint.GlintSymbols
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import me.kavishdevar.librepods.bluetooth.AirPodsDetection
import me.kavishdevar.librepods.utils.CompanionLink

/**
 * Samsung phones stop background apps aggressively ("sleeping apps"), which is the usual
 * reason an AirPods app "dies" or disconnects after a while. Three steps fix it for good:
 *  1. Link the AirPods to Glint (Android's companion-device system wakes Glint on connect).
 *  2. Allow unrestricted battery use (system dialog).
 *  3. Samsung only: add Glint to "Never sleeping apps".
 * Every step can be revisited later from Settings > Stay connected.
 */
@Composable
fun StayConnectedPage(
    onFinish: () -> Unit,
    finishLabel: String = "Finish",
) {
    Box {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                (if (me.kavishdevar.librepods.utils.CompanionLink.isSamsung) "Three steps" else "Two steps") +
                    " so pro reconnects on its own, even after a restart.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            StayConnectedSteps()
            Spacer(Modifier.height(4.dp))
            Button(onClick = onFinish, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(finishLabel) }
        }
    }
}

/** The three steps, reusable from onboarding and from the app's settings. */
@SuppressLint("MissingPermission")
@Composable
fun StayConnectedSteps() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    var linked by remember { mutableStateOf(CompanionLink.isLinked(context)) }
    var unrestricted by remember { mutableStateOf(CompanionLink.isIgnoringBatteryOptimizations(context)) }
    var samsungDone by remember { mutableStateOf(prefs.getBoolean("glint_samsung_sleep_step_done", false)) }
    var linkMessage by remember { mutableStateOf<String?>(null) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                linked = CompanionLink.isLinked(context)
                unrestricted = CompanionLink.isIgnoringBatteryOptimizations(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val linkLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        linked = CompanionLink.isLinked(context)
        if (!linked) linkMessage = "Not linked yet. Make sure your AirPods are paired in Bluetooth settings, then try again."
    }

    StepRow(
        number = 1,
        title = "Link your AirPods",
        body = linkMessage ?: "Wakes pro when your AirPods connect. Pick your AirPods, then Allow.",
        done = linked,
        action = if (linked) null else "Link" to {
            val saved = prefs.getString("mac_address", "")?.takeIf { it.isNotBlank() }
            val known = saved ?: try {
                context.getSystemService(BluetoothManager::class.java)?.adapter?.bondedDevices
                    ?.firstOrNull { AirPodsDetection.looksLikeAirPods(it.name) }?.address
            } catch (_: SecurityException) { null }
            CompanionLink.requestLink(
                context = context,
                knownAddress = known,
                executor = context.mainExecutor,
                onPending = { sender -> linkLauncher.launch(IntentSenderRequest.Builder(sender).build()) },
                onLinked = { linked = true; linkMessage = null },
                onError = { linkMessage = it },
            )
        }
    )
    StepRow(
        number = 2,
        title = "Allow unrestricted battery",
        body = "Stops Android from pausing pro. It uses very little battery.",
        done = unrestricted,
        action = if (unrestricted) null else "Allow" to {
            try { context.startActivity(CompanionLink.batteryOptimizationIntent(context)) }
            catch (_: Exception) { context.startActivity(CompanionLink.appDetailsIntent(context)) }
        }
    )
    if (CompanionLink.isSamsung) {
        StepRow(
            number = 3,
            title = "Samsung: never sleep",
            body = "Battery > Background usage limits > Never sleeping apps: add pro. Then tap Done.",
            done = samsungDone,
            action = if (samsungDone) null else "Open settings" to {
                try { context.startActivity(CompanionLink.samsungBackgroundLimitsIntent()) }
                catch (_: Exception) {
                    try { context.startActivity(android.content.Intent("android.intent.action.POWER_USAGE_SUMMARY")) }
                    catch (_: Exception) { context.startActivity(CompanionLink.appDetailsIntent(context)) }
                }
            },
            secondary = if (samsungDone) null else "Done" to {
                samsungDone = true
                prefs.edit().putBoolean("glint_samsung_sleep_step_done", true).apply()
            }
        )
    }
}

@Composable
private fun StepRow(
    number: Int,
    title: String,
    body: String,
    done: Boolean,
    action: Pair<String, () -> Unit>?,
    secondary: Pair<String, () -> Unit>? = null,
) {
    // Same white/graphite card and 28dp corners as the app's settings lists.
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(28.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(26.dp)
                    .background(if (done) Color(0xFF34C759) else MaterialTheme.colorScheme.primary, CircleShape)
                    .semantics { contentDescription = if (done) "Step $number, done" else "Step $number" },
                contentAlignment = Alignment.Center
            ) {
                if (done) {
                    Icon(GlintSymbols.Checkmark, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                } else {
                    Text("$number", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
            }
            Spacer(Modifier.size(12.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        }
        Text(
            if (done) "Done." else body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (action != null || secondary != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 2.dp)) {
                action?.let { (label, onClick) -> Button(onClick = onClick) { Text(label) } }
                secondary?.let { (label, onClick) -> OutlinedButton(onClick = onClick) { Text(label) } }
            }
        }
    }
}
