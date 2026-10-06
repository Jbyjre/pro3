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

import me.kavishdevar.librepods.presentation.components.AppearancePicker
import me.kavishdevar.librepods.presentation.components.AppIconPicker
import me.kavishdevar.librepods.presentation.theme.glintFontFamily

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import me.kavishdevar.librepods.BuildConfig
import me.kavishdevar.librepods.R
import me.kavishdevar.librepods.presentation.components.AppInfoCard
import me.kavishdevar.librepods.presentation.components.DeviceInfoCard
import me.kavishdevar.librepods.presentation.components.StyledBottomSheet
import me.kavishdevar.librepods.presentation.components.StyledButton
import me.kavishdevar.librepods.presentation.components.StyledIconButton
import me.kavishdevar.librepods.presentation.components.StyledInputField
import me.kavishdevar.librepods.presentation.components.ListItemOrientation
import me.kavishdevar.librepods.presentation.components.StyledList
import me.kavishdevar.librepods.presentation.components.StyledListItem
import me.kavishdevar.librepods.presentation.components.StyledSlider
import me.kavishdevar.librepods.presentation.components.StyledToggle
import me.kavishdevar.librepods.presentation.theme.DesignSystem
import me.kavishdevar.librepods.presentation.theme.LocalDesignSystem
import me.kavishdevar.librepods.presentation.theme.MaterialTypography
import me.kavishdevar.librepods.presentation.viewmodel.AppSettingsViewModel
import me.kavishdevar.librepods.utils.XposedState
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSettingsScreen(
    viewModel: AppSettingsViewModel = viewModel(),
    navigateToPurchase: () -> Unit,
    navigateToTroubleshooting: () -> Unit,
    navigateToOpenSourceLicenses: () -> Unit,
    navigateToStayConnected: () -> Unit = {},
    navigateToIsland: () -> Unit = {},
    navigateToGlintLab: () -> Unit = {},
    navigateToRename: () -> Unit = {},
    navigateToDevices: () -> Unit = {},
    navigateToPhone: () -> Unit = {},
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val state by viewModel.uiState.collectAsState()

    val backdrop = rememberLayerBackdrop()


    val m3eEnabled = LocalDesignSystem.current == DesignSystem.Material
    val topPadding = if (m3eEnabled) 16.dp else WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 84.dp
    val bottomPadding = if (m3eEnabled) 0.dp else WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 12.dp

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .layerBackdrop(backdrop)
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(topPadding))

        val isDarkTheme = isSystemInDarkTheme()

        Text(
            stringResource(R.string.appearance),
            style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = glintFontFamily, color = (if (isSystemInDarkTheme()) Color.White else Color.Black).copy(alpha = 0.6f)),
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp, top = 8.dp)
        )
        AppearancePicker()
        AppIconPicker()
        StyledToggle(
            label = stringResource(R.string.use_material3e),
            checked = state.m3eEnabled,
            onCheckedChange = viewModel::setm3eEnabled,
        )

        // Your AirPods' name (moved here from the main page). Read fresh each time this screen
        // opens, so a rename shows straight away when you come back.
        val podsName = remember { context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE).getString("name", null) }
        val followed by me.kavishdevar.librepods.services.DeviceChoice.chosen.collectAsState()
        androidx.compose.runtime.LaunchedEffect(Unit) { me.kavishdevar.librepods.services.DeviceChoice.load(context) }
        StyledList(title = "pro") {
            StyledListItem(
                name = "This phone",
                description = "Battery, sounds and the Dynamic Island, no headphones needed",
                orientation = ListItemOrientation.Vertical,
                onClick = navigateToPhone,
            )
            StyledListItem(
                name = "Your devices",
                description = "Following ${followed.name}",
                onClick = navigateToDevices,
            )
            StyledListItem(
                name = stringResource(R.string.name),
                description = podsName?.takeIf { it.isNotBlank() } ?: "AirPods",
                onClick = navigateToRename,
            )
            StyledListItem(
                name = "Dynamic Island",
                description = "Around the camera, and the mini island pop-ups",
                orientation = ListItemOrientation.Vertical,
                onClick = navigateToIsland,
            )
            StyledListItem(
                name = "Stay connected & appearance",
                description = "Samsung background setup, glass and motion",
                orientation = ListItemOrientation.Vertical,
                onClick = navigateToStayConnected,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (state.connectionSuccessful) {
            StyledToggle(
                title = stringResource(R.string.widget),
                label = stringResource(R.string.show_phone_battery_in_widget),
                description = stringResource(R.string.show_phone_battery_in_widget_description),
                checked = state.showPhoneBatteryInWidget,
                onCheckedChange = viewModel::setShowPhoneBatteryInWidget,
                enabled = state.isPremium
            )

            StyledList(title = stringResource(R.string.popup_animations)) {
                StyledToggle(
                    label = stringResource(R.string.show_bottom_sheet_popup),
                    description = stringResource(R.string.show_bottom_sheet_popup_description),
                    checked = state.showBottomSheetPopup,
                    onCheckedChange = viewModel::setShowBottomSheetPopup,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            StyledList (title = stringResource(R.string.conversational_awareness)) {
                StyledToggle(
                    label = stringResource(R.string.conversational_awareness_pause_music),
                    description = stringResource(R.string.conversational_awareness_pause_music_description),
                    checked = state.conversationalAwarenessPauseMusicEnabled,
                    onCheckedChange = viewModel::setConversationalAwarenessPauseMusicEnabled,
                    enabled = state.isPremium
                )

                StyledToggle(
                    label = stringResource(R.string.relative_conversational_awareness_volume),
                    description = stringResource(R.string.relative_conversational_awareness_volume_description),
                    checked = state.relativeConversationalAwarenessVolumeEnabled,
                    onCheckedChange = viewModel::setRelativeConversationalAwarenessVolumeEnabled,
                    enabled = state.isPremium,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            val conversationalAwarenessVolume = state.conversationalAwarenessVolume
            LaunchedEffect(conversationalAwarenessVolume) {
                viewModel.setConversationalAwarenessVolume(conversationalAwarenessVolume)
            }

            StyledSlider(
                label = stringResource(R.string.conversational_awareness_volume),
                value = conversationalAwarenessVolume,
                valueRange = 10f..85f,
                snapPoints = listOf(44f),
                startLabel = "10%",
                endLabel = "85%",
                onValueChange = { newValue ->
                    viewModel.setConversationalAwarenessVolume(
                        newValue
                    )
                },
                independent = true,
                enabled = state.isPremium
            )

//            if (!BuildConfig.PLAY_BUILD) {
//                Spacer(modifier = Modifier.height(16.dp))
//
//                StyledListItem(
//                    to = "",
//                    titleRes = stringResource(R.string.camera_control),
//                    name = stringResource(R.string.set_custom_camera_package),
//                    navController = navController,
//                    onClick = {
//                        if (state.isPremium) viewModel.setShowCameraDialog(true)
//                    },
//                    independent = true,
//                    descriptionRes = stringResource(R.string.camera_control_app_description)
//                )
//            }

            Spacer(modifier = Modifier.height(16.dp))
            if (context.checkSelfPermission("android.permission.BLUETOOTH_PRIVILEGED") == PackageManager.PERMISSION_GRANTED) {
                StyledToggle(
                    title = stringResource(R.string.ear_detection),
                    label = stringResource(R.string.disconnect_when_not_wearing),
                    description = stringResource(R.string.disconnect_when_not_wearing_description),
                    checked = state.disconnectWhenNotWearing,
                    onCheckedChange = viewModel::setDisconnectWhenNotWearing,
                    enabled = state.isPremium
                )
            }

            StyledList(title = stringResource(R.string.takeover_airpods_state)) {
                StyledToggle(
                    label = stringResource(R.string.takeover_disconnected),
                    description = stringResource(R.string.takeover_disconnected_desc),
                    checked = state.takeoverWhenDisconnected,
                    onCheckedChange = viewModel::setTakeoverWhenDisconnected,
                    enabled = state.isPremium
                )
                StyledToggle(
                    label = stringResource(R.string.takeover_idle),
                    description = stringResource(R.string.takeover_idle_desc),
                    checked = state.takeoverWhenIdle,
                    onCheckedChange = viewModel::setTakeoverWhenIdle,
                    enabled = state.isPremium
                )
                StyledToggle(
                    label = stringResource(R.string.takeover_music),
                    description = stringResource(R.string.takeover_music_desc),
                    checked = state.takeoverWhenMusic,
                    onCheckedChange = viewModel::setTakeoverWhenMusic,
                    enabled = state.isPremium
                )

                StyledToggle(
                    label = stringResource(R.string.takeover_call),
                    description = stringResource(R.string.takeover_call_desc),
                    checked = state.takeoverWhenCall,
                    onCheckedChange = viewModel::setTakeoverWhenCall,
                    enabled = state.isPremium
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            StyledList(title = stringResource(R.string.takeover_phone_state)) {
                StyledToggle(
                    label = stringResource(R.string.takeover_ringing_call),
                    description = stringResource(R.string.takeover_ringing_call_desc),
                    checked = state.takeoverWhenRingingCall,
                    onCheckedChange = viewModel::setTakeoverWhenRingingCall,
                    enabled = state.isPremium
                )
                StyledToggle(
                    label = stringResource(R.string.takeover_media_start),
                    description = stringResource(R.string.takeover_media_start_desc),
                    checked = state.takeoverWhenMediaStart,
                    onCheckedChange = viewModel::setTakeoverWhenMediaStart,
                    enabled = state.isPremium
                )
            }

            StyledToggle(
                title = stringResource(R.string.advanced_options), // shouldn't be here, but okay
                label = stringResource(R.string.use_alternate_head_tracking_packets),
                description = stringResource(R.string.use_alternate_head_tracking_packets_description),
                checked = state.useAlternateHeadTrackingPackets,
                onCheckedChange = viewModel::setUseAlternateHeadTrackingPackets,
                enabled = state.isPremium
            )
            Spacer(modifier = Modifier.height(16.dp))
        } else {
            Box(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(horizontal = 16.dp)
                    .padding(top = 16.dp, bottom = 2.dp)
            ) {
                Text(
                    text = stringResource(R.string.customizations_unavailable),
                    // A note, not a link (blue read as tappable): the same quiet grey as other notes.
                    style = MaterialTypography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(0.6f),
                    modifier = Modifier
                )
            }
        }

        if (XposedState.isAvailable && XposedState.bluetoothScopeEnabled) {
            val restartBluetoothText = stringResource(R.string.found_offset_restart_bluetooth)
            StyledToggle(
                label = stringResource(R.string.act_as_an_apple_device) + " (${
                    stringResource(
                        R.string.requires_xposed
                    )
                })",
                description = stringResource(R.string.act_as_an_apple_device_description),
                checked = state.vendorIdHook,
                onCheckedChange = { enabled ->
                    Toast.makeText(context, restartBluetoothText, Toast.LENGTH_SHORT).show()
                    viewModel.setVendorIdHook(enabled)
                }
            )
        }

        if (!BuildConfig.PLAY_BUILD) {
            Spacer(modifier = Modifier.height(16.dp))
            StyledList {
                StyledListItem(
                    name = stringResource(R.string.troubleshooting),
                    onClick = navigateToTroubleshooting,
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        StyledList(title = stringResource(R.string.contact)) {
            // Problems go to Jake's own repository.
            StyledListItem(
                name = "Report a pro problem",
                description = "Opens pro's GitHub page",
                orientation = ListItemOrientation.Vertical,
                onClick = {
                    val body = Uri.encode(
                        "pro v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n" +
                            "Device: ${Build.MANUFACTURER} ${Build.MODEL}\nAndroid: ${Build.ID} (${Build.DISPLAY})\n\nWhat happened:\n"
                    )
                    context.startActivity(Intent(Intent.ACTION_VIEW, "https://github.com/Jbyjre/pro3/issues/new?body=$body".toUri()))
                },
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
        DeviceInfoCard()
        Spacer(modifier = Modifier.height(16.dp))
        AppInfoCard(onSecretTap = navigateToGlintLab)

        Spacer(modifier = Modifier.height(16.dp))

        StyledListItem(
            name = stringResource(R.string.open_source_licenses),
            onClick = navigateToOpenSourceLicenses,
        )

        Spacer(modifier = Modifier.height(bottomPadding))

        if (state.showCameraDialog) {
            AlertDialog(onDismissRequest = { viewModel.setShowCameraDialog(false) }, title = {
                Text(
                    stringResource(R.string.set_custom_camera_package),
                    fontFamily = glintFontFamily,
                    fontWeight = FontWeight.Medium
                )
            }, text = {
                Column {
                    Text(
                        stringResource(R.string.enter_custom_camera_package),
                        fontFamily = glintFontFamily,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    OutlinedTextField(
                        value = state.cameraPackageValue,
                        onValueChange = {
                            viewModel.setCameraPackageValue(it)
                            viewModel.setCameraPackageError(null)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        isError = state.cameraPackageError != null,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Ascii,
                            capitalization = KeyboardCapitalization.None
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = if (isDarkTheme) Color(0xFF007AFF) else Color(
                                0xFF3C6DF5
                            ),
                            unfocusedBorderColor = if (isDarkTheme) Color.Gray else Color.LightGray
                        ),
                        supportingText = {
                            if (state.cameraPackageError != null) {
                                Text(
                                    state.cameraPackageError ?: "",
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        },
                        label = { Text(stringResource(R.string.custom_camera_package)) })
                }
            }, confirmButton = {
                val successText = stringResource(R.string.custom_camera_package_set_success)
                TextButton(
                    onClick = {
                        viewModel.saveCameraPackage()
                        Toast.makeText(context, successText, Toast.LENGTH_SHORT).show()
                    }) {
                    Text(
                        "Save",
                        fontFamily = glintFontFamily,
                        fontWeight = FontWeight.Medium
                    )
                }
            }, dismissButton = {
                TextButton(
                    onClick = { viewModel.setShowCameraDialog(false) }) {
                    Text(
                        "Cancel",
                        fontFamily = glintFontFamily,
                        fontWeight = FontWeight.Medium
                    )
                }
            })
        }
    }

}
