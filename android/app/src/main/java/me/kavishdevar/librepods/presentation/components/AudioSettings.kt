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

@file:OptIn(ExperimentalEncodingApi::class)

package me.kavishdevar.librepods.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import me.kavishdevar.librepods.R
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import me.kavishdevar.librepods.services.PREF_CA_ADAPTIVE_ONLY
import me.kavishdevar.librepods.services.ServiceManager
import kotlin.io.encoding.ExperimentalEncodingApi

@Composable
fun AudioSettings(
    adaptiveVolumeCapability: Boolean,
    conversationalAwarenessCapability: Boolean,
    loudSoundReductionCapability: Boolean,
    adaptiveAudioCapability: Boolean,
    customEqCapability: Boolean,

    adaptiveVolumeChecked: Boolean,
    onAdaptiveVolumeCheckedChange: (Boolean) -> Unit,

    conversationalAwarenessChecked: Boolean,
    onConversationalAwarenessCheckedChange: (Boolean) -> Unit,

    loudSoundReductionChecked: Boolean,
    onLoudSoundReductionCheckedChange: (Boolean) -> Unit,

    navigateToAdaptiveStrength: () -> Unit,
    navigateToEqualizer: () -> Unit,

    vendorIdHook: Boolean,
    isPremium: Boolean
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = androidx.compose.runtime.remember { context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE) }
    var adaptiveOnly by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(prefs.getBoolean(PREF_CA_ADAPTIVE_ONLY, false)) }
    fun setAdaptiveOnly(on: Boolean) {
        adaptiveOnly = on
        prefs.edit().putBoolean(PREF_CA_ADAPTIVE_ONLY, on).apply()
        if (on) ServiceManager.getService()?.applyConversationAwarenessRule()
    }
    if (adaptiveVolumeCapability || conversationalAwarenessCapability || loudSoundReductionCapability || adaptiveAudioCapability) {
        StyledList(title = stringResource(R.string.audio)) {
            if (adaptiveVolumeCapability) {
                StyledToggle(
                    label = stringResource(R.string.personalized_volume),
                    description = lockedPrefix(isPremium) + stringResource(R.string.personalized_volume_description),
                    checked = adaptiveVolumeChecked,
                    onCheckedChange = onAdaptiveVolumeCheckedChange,
                    enabled = isPremium,
                )
            }

            if (conversationalAwarenessCapability) {
                StyledToggle(
                    label = stringResource(R.string.conversational_awareness),
                    description = lockedPrefix(isPremium) + if (adaptiveOnly) "Follows Adaptive: on in Adaptive, off in other modes" else stringResource(R.string.conversational_awareness_description),
                    checked = conversationalAwarenessChecked,
                    onCheckedChange = {
                        // Switching it by hand takes over from "Only in Adaptive".
                        if (adaptiveOnly) setAdaptiveOnly(false)
                        onConversationalAwarenessCheckedChange(it)
                    },
                    enabled = isPremium,
                )
                StyledToggle(
                    label = "Only in Adaptive",
                    description = if (adaptiveOnly) "Turns on when you switch to Adaptive, off for the other modes"
                        else "Talking lowers your music only while in Adaptive",
                    checked = adaptiveOnly,
                    onCheckedChange = { setAdaptiveOnly(it) },
                    enabled = isPremium,
                )
            }

            if (loudSoundReductionCapability && vendorIdHook) {
                StyledToggle(
                    label = stringResource(R.string.loud_sound_reduction),
                    description = lockedPrefix(isPremium) + stringResource(R.string.loud_sound_reduction_description),
                    checked = loudSoundReductionChecked,
                    onCheckedChange = onLoudSoundReductionCheckedChange,
                    enabled = isPremium,
                )
            }

            if (adaptiveAudioCapability) {
                StyledListItem(
                    name = stringResource(R.string.adaptive_audio),
                    onClick = navigateToAdaptiveStrength,
                )
            }

            if (customEqCapability) {
                StyledListItem(
                    name = stringResource(R.string.equalizer),
                    onClick = navigateToEqualizer,
                )
            }
        }
    }
}

/** Makes locked items say so, instead of only looking greyed out. */
fun lockedPrefix(isPremium: Boolean): String = if (isPremium) "" else "Locked \u00B7 Unlock advanced features to use. "
