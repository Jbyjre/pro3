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

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import me.kavishdevar.librepods.presentation.glint.listeningModeName
import me.kavishdevar.librepods.presentation.overlays.GlintOverlays
import me.kavishdevar.librepods.presentation.overlays.IslandEvent
import me.kavishdevar.librepods.presentation.overlays.PodsSnapshot
import me.kavishdevar.librepods.services.GlintStatus
import me.kavishdevar.librepods.services.LinkState
import kotlin.math.roundToInt

/**
 * Hidden "Glint Lab": fire every overlay state with made-up data so the visuals can be judged
 * without AirPods. Open it from Settings by tapping the version row seven times.
 */
@Composable
fun GlintLabScreen() {
    val context = LocalContext.current
    val topPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 84.dp
    val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp

    var canOverlay by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var blurOn by remember { mutableStateOf(blurEnabled(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val o = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) {
                canOverlay = Settings.canDrawOverlays(context)
                blurOn = blurEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(o)
        onDispose { lifecycleOwner.lifecycle.removeObserver(o) }
    }

    var left by remember { mutableFloatStateOf(82f) }
    var right by remember { mutableFloatStateOf(78f) }
    var case by remember { mutableFloatStateOf(54f) }
    var leftCharging by remember { mutableStateOf(false) }
    var rightCharging by remember { mutableStateOf(false) }
    var caseCharging by remember { mutableStateOf(true) }
    var leftInEar by remember { mutableStateOf(true) }
    var rightInEar by remember { mutableStateOf(true) }
    var lidOpen by remember { mutableStateOf(false) }
    var mode by remember { mutableIntStateOf(2) }

    fun snapshot() = PodsSnapshot(
        name = "Jake's AirPods Pro",
        left = left.roundToInt(), right = right.roundToInt(), case = case.roundToInt(),
        leftCharging = leftCharging, rightCharging = rightCharging, caseCharging = caseCharging,
        leftInEar = leftInEar, rightInEar = rightInEar, lidOpen = lidOpen, listeningMode = mode,
    )

    fun island(e: IslandEvent) {
        GlintOverlays.updateSnapshot(snapshot())
        GlintOverlays.showIsland(context, e)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(Modifier.height(topPadding))
        LabSection("Status") {
            Text(
                if (canOverlay) "Overlay permission: allowed" else "Overlay permission: not allowed (needed to preview)",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                if (blurOn) "Window blur: available (real glass blur behind overlays)"
                else "Window blur: off right now (battery saver, reduced transparency, or not supported). Overlays use the frosted fallback.",
                style = MaterialTheme.typography.bodyMedium
            )
            if (!canOverlay) {
                FilledTonalButton(onClick = {
                    context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
                }) { Text("Allow display over other apps") }
            }
        }
        LabSection("Fake AirPods data") {
            LabSlider("Left", left) { left = it }
            LabSlider("Right", right) { right = it }
            LabSlider("Case", case) { case = it }
            LabToggle("Left charging", leftCharging) { leftCharging = it }
            LabToggle("Right charging", rightCharging) { rightCharging = it }
            LabToggle("Case charging", caseCharging) { caseCharging = it }
            LabToggle("Left in ear", leftInEar) { leftInEar = it }
            LabToggle("Right in ear", rightInEar) { rightInEar = it }
            LabToggle("Case lid open", lidOpen) { lidOpen = it }
            Text("Listening mode: ${listeningModeName(mode)}", style = MaterialTheme.typography.bodyMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1, 2, 3, 4).forEach { m -> FilledTonalButton(onClick = { mode = m }) { Text(listeningModeName(m)) } }
            }
            FilledTonalButton(onClick = { GlintOverlays.updateSnapshot(snapshot()) }) { Text("Apply to open overlays") }
        }
        LabSection("Connection card") {
            FilledTonalButton(onClick = {
                GlintOverlays.updateSnapshot(snapshot().copy(lidOpen = true))
                GlintOverlays.showCard(context)
            }) { Text("Show connect card (case opened)") }
        }
        LabSection("Island") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FilledTonalButton(onClick = { island(IslandEvent.Connected) }) { Text("Connected") }
                FilledTonalButton(onClick = { island(IslandEvent.InEar) }) { Text("In ear") }
                FilledTonalButton(onClick = {
                    left = 9f; right = 11f
                    island(IslandEvent.LowBattery(9))
                }) { Text("Low battery") }
                FilledTonalButton(onClick = { island(IslandEvent.ListeningMode(mode)) }) { Text("Listening mode") }
                FilledTonalButton(onClick = { island(IslandEvent.MovedToDevice("iPad", canTakeBack = true)) }) { Text("Moved to iPad") }
                FilledTonalButton(onClick = { island(IslandEvent.TakingOver) }) { Text("Taking over") }
                FilledTonalButton(onClick = { island(IslandEvent.Charging) }) { Text("Case charging") }
                FilledTonalButton(onClick = {
                    island(IslandEvent.Problem("Couldn't reach the controls", "Audio works. Open Glint for details."))
                }) { Text("Problem") }
            }
            Text("Tip: tap the island to expand it, swipe it up to dismiss. Swipe the card down to dismiss.", style = MaterialTheme.typography.bodySmall)
            FilledTonalButton(onClick = { GlintOverlays.dismissAll() }) { Text("Dismiss all") }
        }
        LabSection("Main screen states (preview)") {
            Text(
                "Changes what the main screen shows while AirPods are not connected. Real Bluetooth events replace it.",
                style = MaterialTheme.typography.bodySmall
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { GlintStatus.set(LinkState.BluetoothOff) }) { Text("Bluetooth off") }
                FilledTonalButton(onClick = { GlintStatus.set(LinkState.Connecting("AirPods Pro", 0)) }) { Text("Connecting") }
                FilledTonalButton(onClick = { GlintStatus.set(LinkState.Retrying("AirPods Pro", 3, "read failed")) }) { Text("Retrying") }
                FilledTonalButton(onClick = { GlintStatus.set(LinkState.GaveUp("AirPods Pro", "read failed")) }) { Text("Couldn't connect") }
                FilledTonalButton(onClick = { GlintStatus.set(LinkState.Idle) }) { Text("Waiting") }
            }
        }
        Spacer(Modifier.height(bottomPadding))
    }
}

private fun blurEnabled(context: Context): Boolean = try {
    context.getSystemService(WindowManager::class.java)?.isCrossWindowBlurEnabled == true
} catch (_: Throwable) { false }

@Composable
private fun LabSection(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(26.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}

@Composable
private fun LabSlider(label: String, value: Float, onChange: (Float) -> Unit) {
    Column {
        Text("$label: ${value.roundToInt()}%", style = MaterialTheme.typography.bodyMedium)
        Slider(value = value, onValueChange = onChange, valueRange = 1f..100f)
    }
}

@Composable
private fun LabToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
