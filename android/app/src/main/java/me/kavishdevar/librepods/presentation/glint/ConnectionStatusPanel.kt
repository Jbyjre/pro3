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

package me.kavishdevar.librepods.presentation.glint

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.imageResource
import me.kavishdevar.librepods.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.kavishdevar.librepods.presentation.overlays.GlassPillButton
import me.kavishdevar.librepods.presentation.theme.glintFontFamily
import me.kavishdevar.librepods.services.GlintActions
import me.kavishdevar.librepods.services.GlintStatus
import me.kavishdevar.librepods.services.LinkState
import me.kavishdevar.librepods.services.AirPodsService
import me.kavishdevar.librepods.utils.XposedState
import me.kavishdevar.librepods.utils.supportVerdict

/**
 * What the main screen shows while the AirPods controls aren't connected. It always says
 * what is happening and offers the one action that helps, instead of a generic "not
 * connected" message.
 */
@Composable
fun ConnectionStatusPanel(
    onTroubleshoot: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val density = LocalDensity.current.density
    val link by GlintStatus.link.collectAsState()
    val failures by GlintStatus.consecutiveFailures.collectAsState()
    val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    val verdict = remember(link, failures) {
        supportVerdict(
            sdkInt = Build.VERSION.SDK_INT,
            manufacturer = Build.MANUFACTURER,
            buildId = Build.ID,
            bypassed = prefs.getBoolean("bypass_device_check.v2", false),
            xposedHookActive = XposedState.bluetoothScopeEnabled,
            everConnected = prefs.getBoolean("connection_successful", false),
            blockedObserved = prefs.getBoolean("glint_blocked_observed", false),
        )
    }
    val look = remember(dark) { GlassLooks.card(dark, density) }
    val light by rememberTiltLight(GlintComfort.tiltLight(context))
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        0.35f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulse"
    )

    fun retry() {
        try {
            context.startForegroundService(Intent(context, AirPodsService::class.java).setAction(GlintActions.RETRY_CONNECT))
        } catch (_: Exception) {}
    }

    data class Panel(val title: String, val body: String, val action: Pair<String, () -> Unit>?, val busy: Boolean = false)

    val panel = when (val l = link) {
        LinkState.BluetoothOff -> Panel(
            "Bluetooth is off",
            "Turn Bluetooth on and Glint will find your AirPods automatically.",
            "Bluetooth settings" to { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
        )
        LinkState.NoPermission -> Panel(
            "Allow Nearby devices",
            "Glint needs the Nearby devices permission to talk to your AirPods.",
            "Open app settings" to {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
            }
        )
        is LinkState.Connecting -> Panel("Connecting…", "Opening the controls channel to ${l.deviceName}.", null, busy = true)
        is LinkState.Retrying -> Panel(
            "Still connecting…",
            "Your AirPods play audio, but the controls channel hasn't opened yet (try ${l.attempt + 1}). This usually settles within a few seconds.",
            null, busy = true
        )
        is LinkState.GaveUp -> if (verdict.canConnect) Panel(
            "Couldn't reach the controls",
            "Your AirPods play audio, but the extra channel for battery and listening modes didn't open. Taking them out and putting them back in, or turning Bluetooth off and on, usually fixes it.",
            "Try again" to { retry() }
        ) else Panel(verdict.title, verdict.message, "Try again" to { retry() })
        LinkState.Idle, is LinkState.Connected -> if (!verdict.canConnect) Panel(
            verdict.title, verdict.message, null
        ) else Panel(
            "Waiting for your AirPods",
            "Open the case near your phone or put your AirPods in. Glint connects on its own.",
            if (prefs.getBoolean("connection_successful", false)) "Reconnect" to { retry() } else null
        )
    }

    Column(
        modifier
            .fillMaxWidth()
            .drawBehind {
                val r = 34.dp.toPx()
                val rect = Rect(Offset.Zero, size)
                drawGlass(roundRectPath(rect, r), rect, r, look, light, blur = null, blurredElsewhere = true)
            }
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // LibrePods' AirPods picture; it breathes while a connection attempt is running.
        Box(Modifier.size(width = 146.dp, height = 96.dp), contentAlignment = Alignment.Center) {
            Image(
                bitmap = ImageBitmap.imageResource(R.drawable.airpods_pro_2_buds),
                contentDescription = null,
                modifier = Modifier
                    .requiredSize(150.dp)
                    .offset(y = 6.dp)
                    .graphicsLayer { alpha = if (panel.busy) 0.45f + 0.55f * pulse else 1f }
            )
        }
        Text(
            panel.title, textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
            style = TextStyle(fontFamily = glintFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, color = look.content)
        )
        Text(
            panel.body, textAlign = TextAlign.Center,
            style = TextStyle(fontFamily = glintFontFamily, fontSize = 15.sp, lineHeight = 21.sp, color = look.contentSecondary)
        )
        Spacer(Modifier.height(4.dp))
        panel.action?.let { (label, onClick) ->
            GlassPillButton(label, look.content, Modifier.fillMaxWidth(), dark = dark, onClick = onClick)
        }
        GlassPillButton("Troubleshooting", look.contentSecondary, Modifier.fillMaxWidth().padding(horizontal = 24.dp), dark = dark, onClick = onTroubleshoot)
        Spacer(Modifier.width(1.dp))
    }
}
