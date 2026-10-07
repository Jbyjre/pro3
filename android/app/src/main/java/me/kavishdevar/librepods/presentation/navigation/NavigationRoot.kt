package me.kavishdevar.librepods.presentation.navigation

import android.util.Log
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.kyant.backdrop.backdrops.LayerBackdrop
import me.kavishdevar.librepods.R
import me.kavishdevar.librepods.presentation.MaterialIcons
import me.kavishdevar.librepods.presentation.components.StyledIconButton
import me.kavishdevar.librepods.presentation.components.StyledScaffold
import me.kavishdevar.librepods.presentation.theme.DesignSystem
import me.kavishdevar.librepods.presentation.theme.LocalDesignSystem
import me.kavishdevar.librepods.presentation.viewmodel.AirPodsViewModel

@Composable
fun NavigationRoot(
    showOnboarding: Boolean = false,
    onboardingComplete: () -> Unit = {},
    airPodsViewModel: AirPodsViewModel,
    /** Screens to open on top of the first one; used by the screenshot tour in tests. */
    initialStack: List<Screen> = emptyList(),
    /** The tab the app opens on (the Phone tab, the main page; tests pick others). */
    initialTab: AppTab = AppTab.Phone,
) {
    // Which main page is showing: Phone (first), Island, or the headphones.
    var tab by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(initialTab) }
    val backStack = remember {
        mutableStateListOf(
            when {
                showOnboarding -> Screen.Onboarding
                else -> Screen.AirPodsSettings
            }
        ).apply { addAll(initialStack) }
    }

    // A pop-up asked for a page (for example "turn on taps" opens Settings > Islands).
    androidx.compose.runtime.LaunchedEffect(Unit) {
        AppLinks.pending.collect { target ->
            if (target == null) return@collect
            AppLinks.pending.value = null
            if (backStack.firstOrNull() == Screen.Onboarding) return@collect
            // The Dynamic Island's page is its own tab: back to the top, on that tab.
            if (target == AppLinks.ISLAND_TAB || target == AppLinks.ISLANDS) {
                while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
                tab = AppTab.Island
                return@collect
            }
            val page = when (target) {
                AppLinks.HEART -> listOf(Screen.HeartRate)
                else -> emptyList()
            }
            if (page.isNotEmpty() && backStack.last() != page.last()) {
                while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
                backStack.addAll(page)
            }
        }
    }

    val currentScreen = backStack.last()
    // Mark the moment a screen opens (before its content composes) so its cards rise in.
    androidx.compose.runtime.remember(currentScreen) { me.kavishdevar.librepods.presentation.glint.GlintEnter.screenOpened(); 0 }

    val state by airPodsViewModel.uiState.collectAsState()
    val chosenDevice by me.kavishdevar.librepods.services.DeviceChoice.chosen.collectAsState()

    val m3eEnabled = LocalDesignSystem.current == DesignSystem.Material

    val title = when (currentScreen) {
        Screen.Onboarding -> ""
        Screen.AirPodsSettings -> when (tab) {
            AppTab.Phone -> "pro"
            AppTab.Island -> "Dynamic Island"
            AppTab.Headphones -> when {
                !chosenDevice.isAirPods -> chosenDevice.name
                state.isLocallyConnected -> state.deviceName
                else -> "AirPods"
            }
        }
        Screen.Devices -> "Your devices"
        Screen.Accessibility -> stringResource(R.string.accessibility)
        Screen.AdaptiveStrength -> stringResource(R.string.customize_adaptive_audio)
        Screen.AppSettings -> stringResource(R.string.settings)
//        Screen.CameraControl -> stringResource(R.string.camera_control)
        Screen.Equalizer -> stringResource(R.string.equalizer)
        Screen.HeadTracking -> stringResource(R.string.head_tracking)
        Screen.HearingAid -> stringResource(R.string.hearing_aid)
        Screen.HearingAidAdjustments -> stringResource(R.string.adjustments)
        Screen.HearingProtection -> stringResource(R.string.hearing_protection)
        is Screen.LongPress -> currentScreen.bud
        Screen.OpenSourceLicenses -> stringResource(R.string.open_source_licenses)
        Screen.Purchase -> stringResource(R.string.unlock_advanced_features)
        Screen.Rename -> stringResource(R.string.name)
        Screen.TransparencyCustomization -> stringResource(R.string.customize_transparency_mode)
        Screen.Troubleshooting -> stringResource(R.string.troubleshooting)
        Screen.UpdateHearingTest -> stringResource(R.string.update_hearing_test)
        Screen.VersionInfo -> stringResource(R.string.version)
        is Screen.CallControl -> currentScreen.action
        Screen.MicrophoneSettings -> stringResource(R.string.microphone_mode)
        Screen.StayConnected -> "Stay connected"
        Screen.IslandSettings -> "Islands"
        Screen.Phone -> "This phone"
        Screen.GlintLab -> "pro Lab"
        Screen.HeartRate -> "Heart rate"
        Screen.Recorder -> "Recorder"
        Screen.HeartShare -> "Share live"
        Screen.HeartHistory -> "History"
        is Screen.HeartSession -> "Session"
    }

    // is this a bad idea? probably. I can't think of a better way without having to pass around a shouldShowBackButton to each screen to pass to each scaffold
    val settingsButton: @Composable (LayerBackdrop) -> Unit = { scaffoldBackdrop ->
        if (m3eEnabled) {
            FilledTonalIconButton(
                onClick = { backStack.add(Screen.AppSettings) },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .size(IconButtonDefaults.mediumContainerSize(IconButtonDefaults.IconButtonWidthOption.Uniform)),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = "settings",
                    modifier = Modifier.size(IconButtonDefaults.mediumIconSize)
                )
            }
        } else {
            StyledIconButton(
                onClick = { backStack.add(Screen.AppSettings) },
                icon = "􀍟",
                backdrop = scaffoldBackdrop
            )
        }
    }
    // The Phone and Island tabs: just Settings. The headphones tab adds Your devices.
    val actionButtons = if (currentScreen == Screen.AirPodsSettings && tab != AppTab.Headphones) listOf(settingsButton) else when (currentScreen) {
        Screen.AirPodsSettings -> listOf<@Composable (backdrop: LayerBackdrop) -> Unit>(
                { scaffoldBackdrop ->
                    // Your devices: which headphones pro follows (AirPods, Beats Solo 4, others).
                    if (m3eEnabled) {
                        FilledTonalIconButton(
                            onClick = { backStack.add(Screen.Devices) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .size(IconButtonDefaults.mediumContainerSize(IconButtonDefaults.IconButtonWidthOption.Uniform)),
                        ) {
                            Icon(
                                imageVector = me.kavishdevar.librepods.presentation.glint.GlintSymbols.Headphones,
                                contentDescription = "Your devices",
                                modifier = Modifier.size(IconButtonDefaults.mediumIconSize)
                            )
                        }
                    } else {
                        StyledIconButton(
                            onClick = { backStack.add(Screen.Devices) },
                            icon = String(Character.toChars(me.kavishdevar.librepods.presentation.glint.GlintSymbols.HEADPHONES_CHAR)),
                            backdrop = scaffoldBackdrop
                        )
                    }
                },
                { scaffoldBackdrop ->
                    if (m3eEnabled) {
                        FilledTonalIconButton(
                            onClick = { backStack.add(Screen.AppSettings) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .size(IconButtonDefaults.mediumContainerSize(IconButtonDefaults.IconButtonWidthOption.Uniform)),

                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Settings,
                                contentDescription = "settings",
                                modifier = Modifier.size(IconButtonDefaults.mediumIconSize)
                            )
                        }
                    } else {
                        StyledIconButton(
                            onClick = { backStack.add(Screen.AppSettings) },
                            icon = "􀍟",
                            backdrop = scaffoldBackdrop
                        )
                    }
                }
            )
        Screen.HeadTracking -> listOf<@Composable (backdrop: LayerBackdrop) -> Unit>(
            { scaffoldBackdrop ->
                if (m3eEnabled) {
                    FilledTonalIconToggleButton(
                        checked = state.headTrackingActive,
                        onCheckedChange = { if (it) airPodsViewModel.startHeadTracking() else airPodsViewModel.stopHeadTracking() },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .size(IconButtonDefaults.mediumContainerSize(IconButtonDefaults.IconButtonWidthOption.Uniform)),
                        shape = IconButtonDefaults.mediumRoundShape
                    ) {
                        Icon(
                            imageVector = if (state.headTrackingActive) MaterialIcons.pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause",
                            modifier = Modifier.size(IconButtonDefaults.mediumIconSize)
                        )
                    }
                } else {
                    StyledIconButton(
                        onClick = {
                            if (!state.headTrackingActive) {
                                airPodsViewModel.startHeadTracking()
                                Log.d("HeadTrackingScreen", "Head tracking started")
                            } else {
                                airPodsViewModel.stopHeadTracking()
                                Log.d("HeadTrackingScreen", "Head tracking stopped")
                            }
                        },
                        icon = if (state.headTrackingActive) "􀊅" else "􀊃",
                        backdrop = scaffoldBackdrop
                    )
                }
            }
        )
        else -> listOf()
    }

    // The tab bar shows on the main pages only (not over a page opened from them, nor during setup).
    val tabsVisible = backStack.size == 1 && currentScreen == Screen.AirPodsSettings
    // Back on the Island or headphones tab goes to the Phone tab first, then out of the app.
    androidx.activity.compose.BackHandler(enabled = tabsVisible && tab != AppTab.Phone) { tab = AppTab.Phone }
    val navBottom = androidx.compose.foundation.layout.WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    StyledScaffold(
        visible = currentScreen.showTopBar,
        title = title,
        showBackButton = backStack.size > 1,
        onNavigateBack = { backStack.removeAt(backStack.lastIndex) },
        actionButtons = actionButtons,
        bottomBar = { backdrop ->
            androidx.compose.animation.AnimatedVisibility(
                visible = tabsVisible,
                enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically { it / 2 },
                exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically { it / 2 },
            ) {
                GlassTabBar(
                    selected = tab,
                    onSelect = { tab = it },
                    backdrop = backdrop,
                    device = chosenDevice,
                    modifier = Modifier.padding(bottom = navBottom + TAB_BAR_GAP),
                )
            }
        },
    ) {
        androidx.compose.runtime.CompositionLocalProvider(LocalTabBarSpace provides if (tabsVisible) TAB_BAR_HEIGHT + TAB_BAR_GAP + 8.dp else 0.dp) {
            AppNavGraph(
                showOnboarding = showOnboarding,
                onboardingComplete = onboardingComplete,
                backStack = backStack,
                airPodsViewModel = airPodsViewModel,
                tab = { tab },
                onTab = { tab = it },
            )
        }
    }
}
