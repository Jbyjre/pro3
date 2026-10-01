/*
    Glint, a fork of LibrePods - AirPods liberated from Apple's ecosystem
    Copyright (C) 2025 LibrePods contributors, 2026 Glint contributors

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

import android.content.Context
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import me.kavishdevar.librepods.presentation.components.AppInfoCard
import me.kavishdevar.librepods.presentation.components.DeviceInfoCard
import me.kavishdevar.librepods.utils.XposedState
import me.kavishdevar.librepods.utils.supportVerdict

/**
 * "Can this phone connect?" Shown to everyone: it explains, in plain words, what the phone's
 * Android version means for Glint instead of silently blocking. Continuing never hurts: audio
 * keeps working and Glint connects on its own once the phone's Bluetooth allows it.
 */
@Composable
fun NotSupportedPage(
    onContinue: (bypass: Boolean) -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    val verdict = remember {
        supportVerdict(
            sdkInt = Build.VERSION.SDK_INT,
            manufacturer = Build.MANUFACTURER,
            buildId = Build.ID,
            bypassed = false,
            xposedHookActive = XposedState.bluetoothScopeEnabled,
            everConnected = prefs.getBoolean("connection_successful", false),
            blockedObserved = prefs.getBoolean("glint_blocked_observed", false),
        )
    }
    Box {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(verdict.title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(verdict.message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!verdict.canConnect) {
                Text(
                    "You can still continue. Glint connects on its own once your phone allows it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(8.dp))
            if (verdict.canConnect) {
                Button(onClick = { onContinue(false) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Text("Continue")
                }
            } else {
                Button(onClick = { onContinue(true) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Text("Continue anyway")
                }
                OutlinedButton(
                    onClick = {
                        try {
                            context.startActivity(android.content.Intent("android.settings.SYSTEM_UPDATE_SETTINGS"))
                        } catch (_: Exception) {
                            context.startActivity(android.content.Intent(android.provider.Settings.ACTION_DEVICE_INFO_SETTINGS))
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) { Text("Check for a system update") }
            }
        }
    }
}
