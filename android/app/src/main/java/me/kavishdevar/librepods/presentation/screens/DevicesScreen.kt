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

package me.kavishdevar.librepods.presentation.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import me.kavishdevar.librepods.R
import me.kavishdevar.librepods.presentation.components.StyledList
import me.kavishdevar.librepods.presentation.components.StyledListItem
import me.kavishdevar.librepods.presentation.theme.DesignSystem
import me.kavishdevar.librepods.presentation.theme.LocalDesignSystem
import me.kavishdevar.librepods.presentation.theme.glintFontFamily
import me.kavishdevar.librepods.services.ChosenDevice
import me.kavishdevar.librepods.services.DeviceChoice
import me.kavishdevar.librepods.services.DeviceKind
import me.kavishdevar.librepods.services.KnownDevice

/**
 * Your devices: the phone's paired headphones, and which one pro follows. Picking one is kept
 * until you pick another (restarts and updates included). Only the chosen device's controls
 * run, so AirPods features never reach the Beats and the other way round.
 */
@Composable
fun DevicesScreen(onChosen: () -> Unit = {}) {
    val context = LocalContext.current
    val chosen by DeviceChoice.chosen.collectAsState()
    LaunchedEffect(Unit) { DeviceChoice.load(context) }
    var devices by remember { mutableStateOf(DeviceChoice.pairedAudioDevices(context)) }
    // Connection dots change as headphones come and go: look again while the page is open.
    val lifecycle = LocalLifecycleOwner.current
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                devices = DeviceChoice.pairedAudioDevices(context)
                delay(3_000)
            }
        }
    }
    val view = LocalView.current
    DevicesContent(
        devices = devices,
        chosen = chosen,
        onChoose = { d ->
            view.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
            DeviceChoice.choose(context, ChosenDevice(d.kind, d.address, d.name))
            devices = DeviceChoice.pairedAudioDevices(context)
            onChosen()
        },
        onOpenBluetooth = {
            runCatching { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        },
    )
}

@Composable
fun DevicesContent(
    devices: List<KnownDevice>,
    chosen: ChosenDevice,
    onChoose: (KnownDevice) -> Unit,
    onOpenBluetooth: () -> Unit,
    topPadding: Dp? = null,
) {
    val dark = isSystemInDarkTheme()
    val ink = if (dark) Color.White else Color.Black
    val m3e = LocalDesignSystem.current == DesignSystem.Material
    val top = topPadding ?: if (m3e) 16.dp else WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 84.dp
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(top))
        Text(
            "pro follows one device at a time and stays with it until you pick another. " +
                "Only that device's controls run.",
            style = TextStyle(fontFamily = glintFontFamily, fontSize = 14.sp, lineHeight = 19.sp, color = ink.copy(alpha = 0.6f)),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        Spacer(Modifier.height(8.dp))
        if (devices.isEmpty()) {
            StyledList(title = "Paired with this phone") {
                StyledListItem(
                    name = "No headphones paired yet",
                    description = "Pair them in Bluetooth settings, then come back here",
                    onClick = onOpenBluetooth,
                )
            }
        } else {
            StyledList(title = "Paired with this phone") {
                devices.forEach { d ->
                    val isChosen = d.kind == chosen.kind && d.address.equals(chosen.address, ignoreCase = true)
                    StyledListItem(
                        name = d.name,
                        description = listOfNotNull(
                            d.kind.label.takeIf { d.kind != DeviceKind.HEADPHONES && !d.name.contains(it, ignoreCase = true) },
                            if (d.connected) "Connected" else null,
                            if (isChosen) "pro follows this one" else null,
                        ).joinToString(" · ").ifEmpty { if (d.connected) "Connected" else "Not connected" },
                        onClick = { if (!isChosen) onChoose(d) },
                        selected = isChosen,
                        leadingContent = { DeviceTile(d, ink, dark) },
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        StyledList(title = "Something missing?") {
            StyledListItem(
                name = "Open Bluetooth settings",
                description = "Pair new headphones there; they show up here",
                onClick = onOpenBluetooth,
            )
        }
        Spacer(Modifier.height(bottom))
    }
}

/** AirPods show LibrePods' own AirPods picture; everything else the drawn headphones. */
@Composable
private fun DeviceTile(d: KnownDevice, ink: Color, dark: Boolean) {
    if (d.kind == DeviceKind.AIRPODS) {
        Box(
            Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(ink.copy(alpha = if (dark) 0.13f else 0.07f)),
            contentAlignment = Alignment.Center
        ) {
            Image(painterResource(R.drawable.airpods_pro_2_buds), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.size(24.dp))
        }
    } else {
        HeadphonesTile(ink, dark)
    }
}
