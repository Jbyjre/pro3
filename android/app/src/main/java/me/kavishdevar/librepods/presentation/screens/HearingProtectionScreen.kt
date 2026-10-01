/*
    LibrePods - AirPods liberated from Apple’s ecosystem
    Copyright (C) 2025 LibrePods contributors

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

import androidx.compose.foundation.background
import me.kavishdevar.librepods.services.VolumeGuard
import me.kavishdevar.librepods.services.PREF_VOLUME_LIMIT_ON
import me.kavishdevar.librepods.services.PREF_VOLUME_LIMIT
import me.kavishdevar.librepods.presentation.components.LiquidSegments
import androidx.compose.runtime.setValue
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import me.kavishdevar.librepods.R
import me.kavishdevar.librepods.bluetooth.AACPManager
import me.kavishdevar.librepods.bluetooth.ATTHandles
import me.kavishdevar.librepods.presentation.components.CommandNotice
import me.kavishdevar.librepods.presentation.components.InfoTip
import me.kavishdevar.librepods.presentation.components.StyledButton
import me.kavishdevar.librepods.services.GlintStatus
import me.kavishdevar.librepods.services.LinkState
import me.kavishdevar.librepods.presentation.components.StyledToggle
import me.kavishdevar.librepods.presentation.theme.DesignSystem
import me.kavishdevar.librepods.presentation.theme.LocalDesignSystem
import me.kavishdevar.librepods.presentation.viewmodel.AirPodsViewModel

@Composable
fun HearingProtectionScreen(viewModel: AirPodsViewModel, navigateToPurchase: () -> Unit) {
    val backdrop = rememberLayerBackdrop()
    val state by viewModel.uiState.collectAsState()
    val link by GlintStatus.link.collectAsState()
    // Changes only reach the AirPods over the control connection; say so instead of
    // letting a switch flip with nothing happening.
    val connected = link is LinkState.Connected || viewModel.isDemoMode
    val m3eEnabled = LocalDesignSystem.current == DesignSystem.Material
    val topPadding = if (m3eEnabled) 0.dp else WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 84.dp
    val bottomPadding = if (m3eEnabled) 0.dp else WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 12.dp
    val offline = "Connect your AirPods to change this"

    Box(Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .layerBackdrop(backdrop)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(topPadding))
        if (!state.isPremium) {
            StyledButton(
                onClick = navigateToPurchase,
                backdrop = rememberLayerBackdrop(),
                modifier = Modifier.fillMaxWidth(),
                maxScale = 0.05f,
                surfaceColor = MaterialTheme.colorScheme.primary
            ) {
                Text(
                    stringResource(R.string.unlock_advanced_features),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (state.vendorIdHook) {
            StyledToggle(
                title = stringResource(R.string.environmental_noise),
                label = stringResource(R.string.loud_sound_reduction),
                description = if (connected) stringResource(R.string.loud_sound_reduction_description) else offline,
                checked = state.loudSoundReductionEnabled,
                onCheckedChange = {
                    viewModel.setATTCharacteristicValue(
                        ATTHandles.LOUD_SOUND_REDUCTION,
                        byteArrayOf(if (it) 1.toByte() else 0.toByte())
                    )
                },
                enabled = state.isPremium && connected
            )

            Spacer(modifier = Modifier.height(12.dp))
        }
        val hasPPE = state.capabilities.contains(me.kavishdevar.librepods.data.Capability.PPE)
        if (hasPPE) StyledToggle(
            title = stringResource(R.string.workspace_use),
            label = stringResource(R.string.ppe),
            description = if (connected) stringResource(R.string.workspace_use_description) else offline,
            checked = state.controlStates[AACPManager.Companion.ControlCommandIdentifiers.PPE_TOGGLE_CONFIG]?.getOrNull(
                0
            )?.toInt() == 1,
            onCheckedChange = {
                viewModel.setControlCommandBoolean(
                    AACPManager.Companion.ControlCommandIdentifiers.PPE_TOGGLE_CONFIG, it
                )
            },
            enabled = state.isPremium && connected
        )
        Spacer(modifier = Modifier.height(if (hasPPE) 16.dp else 0.dp))
        VolumeLimitSection()
        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (state.vendorIdHook) "What these do" else "Loud Sound Reduction needs a rooted phone to change",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            InfoTip(
                "Hearing protection",
                "Loud Sound Reduction softens sudden loud sounds around you while you're in Transparency or Adaptive. " +
                    "Changing it on Android needs a rooted phone, so it only appears here when Glint can change it. " +
                    "Workspace Use (EN 352) is for loud workplaces: it limits your media to 82 dBA, in line with the European hearing-protector standard. " +
                    "Volume limit works on any phone: while music plays through your AirPods, Glint turns it back down whenever it goes above the level you pick. " +
                    "The level is a share of your phone's volume steps, not decibels (Glint can't measure how loud it is in your ears). " +
                    "Each AirPods switch is sent to your AirPods straight away; if it can't be sent, a notice at the bottom says so."
            )
        }
        Spacer(modifier = Modifier.height(bottomPadding))
    }
    CommandNotice(
        backdrop = backdrop,
        onReconnect = viewModel::reconnectFromSavedMac,
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = bottomPadding + 16.dp)
    )
    }
}

/**
 * Volume limit: on/off, the level as liquid segments (50–85% of the phone's media volume),
 * and when Glint last turned the volume down.
 */
@Composable
private fun VolumeLimitSection() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = androidx.compose.runtime.remember { context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE) }
    var on by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(prefs.getBoolean(PREF_VOLUME_LIMIT_ON, false)) }
    var limit by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(prefs.getInt(PREF_VOLUME_LIMIT, VolumeGuard.DEFAULT_LIMIT)) }
    val last by VolumeGuard.lastLimited.collectAsState()
    val levels = listOf(50, 60, 70, 85)
    StyledToggle(
        title = "Volume limit",
        label = "Limit media volume",
        description = when {
            !on -> "Keeps music from going above a level you choose. Works without root."
            last != null -> "Turned down to $limit% at " + java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(last!!.atMs))
            else -> "Media through your AirPods stays at or below $limit%"
        },
        checked = on,
        onCheckedChange = {
            on = it
            prefs.edit().putBoolean(PREF_VOLUME_LIMIT_ON, it).apply()
            VolumeGuard.check()
        },
    )
    androidx.compose.animation.AnimatedVisibility(on) {
        Column(Modifier.padding(top = 10.dp)) {
            LiquidSegments(
                levels.map { "$it%" },
                levels.indexOf(limit).coerceAtLeast(0),
                {
                    limit = levels[it]
                    prefs.edit().putInt(PREF_VOLUME_LIMIT, limit).apply()
                    VolumeGuard.check()
                },
            )
        }
    }
}
