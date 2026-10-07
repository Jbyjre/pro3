package me.kavishdevar.librepods.presentation.navigation

import androidx.activity.BackEventCompat.Companion.EDGE_LEFT
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.ui.unit.IntOffset
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import me.kavishdevar.librepods.BuildConfig
import me.kavishdevar.librepods.bluetooth.AACPManager
import me.kavishdevar.librepods.presentation.screens.AccessibilitySettingsScreen
import me.kavishdevar.librepods.presentation.screens.AdaptiveStrengthScreen
import me.kavishdevar.librepods.presentation.screens.AirPodsSettingsRoute
import me.kavishdevar.librepods.presentation.screens.AppSettingsScreen
import me.kavishdevar.librepods.presentation.screens.CallControlScreen
import me.kavishdevar.librepods.presentation.screens.EqualizerRoute
import me.kavishdevar.librepods.presentation.screens.HeadTrackingScreen
import me.kavishdevar.librepods.presentation.screens.HearingAidAdjustmentsScreen
import me.kavishdevar.librepods.presentation.screens.HearingAidScreen
import me.kavishdevar.librepods.presentation.screens.HearingProtectionScreen
import me.kavishdevar.librepods.presentation.screens.LoadingScreen
import me.kavishdevar.librepods.presentation.screens.LongPress
import me.kavishdevar.librepods.presentation.screens.MicrophoneSettingsRoute
import me.kavishdevar.librepods.presentation.screens.OpenSourceLicensesScreen
import me.kavishdevar.librepods.presentation.screens.PurchaseScreen
import me.kavishdevar.librepods.presentation.screens.RenameScreen
import me.kavishdevar.librepods.presentation.screens.TransparencySettingsScreen
import me.kavishdevar.librepods.presentation.screens.TroubleshootingScreen
import me.kavishdevar.librepods.presentation.screens.UpdateHearingTestRoute
import me.kavishdevar.librepods.presentation.screens.VersionScreen
import me.kavishdevar.librepods.presentation.screens.GlintLabScreen
import me.kavishdevar.librepods.presentation.screens.IslandSettingsScreen
import me.kavishdevar.librepods.presentation.screens.PhoneScreen
import me.kavishdevar.librepods.presentation.screens.DevicesScreen
import me.kavishdevar.librepods.presentation.screens.HeartRateScreen
import me.kavishdevar.librepods.presentation.screens.HeartShareScreen
import me.kavishdevar.librepods.presentation.screens.HeartHistoryScreen
import me.kavishdevar.librepods.presentation.screens.HeartSessionScreen
import me.kavishdevar.librepods.presentation.screens.RecorderScreen
import me.kavishdevar.librepods.presentation.screens.StayConnectedScreen
import me.kavishdevar.librepods.presentation.screens.onboarding.OnboardingScreen
import me.kavishdevar.librepods.presentation.theme.DesignSystem
import me.kavishdevar.librepods.presentation.theme.LocalDesignSystem
import me.kavishdevar.librepods.presentation.viewmodel.AirPodsViewModel
import me.kavishdevar.librepods.presentation.viewmodel.AppSettingsViewModel
import me.kavishdevar.librepods.presentation.viewmodel.PurchaseViewModel

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun AppNavGraph(
    showOnboarding: Boolean = false,
    onboardingComplete: () -> Unit = {},
    backStack: SnapshotStateList<Screen>,
    airPodsViewModel: AirPodsViewModel,
    /**
     * The main page showing (Phone, Island, headphones), read when needed, and how to switch it. A
     * function, not a value: the navigation library keeps each page's content from when it was first
     * made, so a plain value would stay stuck on the first tab while the tab bar moved on.
     */
    tab: () -> AppTab = { AppTab.Headphones },
    onTab: (AppTab) -> Unit = {},
) {
    val navigate: (Screen) -> Unit = { screen ->
        backStack.add(screen)
    }

    // Glint: the foss build includes everything, so there is no purchase page to open.
    fun navigateToPurchase() {
        if (BuildConfig.PLAY_BUILD) navigate(Screen.Purchase)
    }

    val m3eEnabled = LocalDesignSystem.current == DesignSystem.Material
    val context = androidx.compose.ui.platform.LocalContext.current

    SharedTransitionLayout {
        NavDisplay(
            sharedTransitionScope = this,
            backStack = backStack,
            onBack = {
                if (backStack.size > 1) {
                    backStack.removeAt(backStack.lastIndex)
                }
            },
            entryProvider = { screen ->
                when (screen) {
                    Screen.Onboarding ->
                        NavEntry(screen) {
                            OnboardingScreen {
                                onboardingComplete()
                                navigate(Screen.AirPodsSettings)
                                backStack.remove(screen)
                                // Setup done: start on the Phone tab, the main page.
                                onTab(AppTab.Phone)
                            }
                        }
                    Screen.AirPodsSettings ->
                        NavEntry(screen) {
                            // The three main pages, one per tab: they cross-fade and settle on a
                            // spring rather than slide (sliding means going deeper, a tab is a peer).
                            val reduce = androidx.compose.runtime.remember(Unit) { me.kavishdevar.librepods.presentation.glint.GlintComfort.reduceMotion(context) }
                            androidx.compose.animation.AnimatedContent(
                                targetState = tab(),
                                transitionSpec = {
                                    if (reduce) fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                                    else (fadeIn(spring(dampingRatio = 1f, stiffness = 420f)) + scaleIn(spring(dampingRatio = 0.9f, stiffness = 420f), initialScale = 0.985f)) togetherWith
                                        fadeOut(spring(dampingRatio = 1f, stiffness = 700f))
                                },
                                label = "tabs",
                            ) { shown ->
                                when (shown) {
                                    AppTab.Phone -> PhoneScreen(
                                        navigateToIsland = { onTab(AppTab.Island) },
                                        navigateToHeadphones = { onTab(AppTab.Headphones) },
                                    )
                                    AppTab.Island -> IslandSettingsScreen()
                                    AppTab.Headphones -> {
                                        if (!airPodsViewModel.isReady) LoadingScreen()
                                        AirPodsSettingsRoute(
                                            viewModel = airPodsViewModel,
                                            navigateToRename = { navigate(Screen.Rename) },
                                            navigateToHearingProtection = { navigate(Screen.HearingProtection) },
                                            navigateToHearingAid = { navigate(Screen.HearingAid) },
                                            navigateToLeftLongPress = {
                                                navigate(
                                                    Screen.LongPress("Left")
                                                )
                                            },
                                            navigateToRightLongPress = {
                                                navigate(
                                                    Screen.LongPress("Right")
                                                )
                                            },
                                            navigateToPurchase = ::navigateToPurchase,
                                            navigateToAdaptiveStrength = { navigate(Screen.AdaptiveStrength) },
                                            navigateToEqualizer = { navigate(Screen.Equalizer) },
                                            navigateToHeadTracking = { navigate(Screen.HeadTracking) },
                                            navigateToAccessibility = { navigate(Screen.Accessibility) },
                                            navigateToVersion = { navigate(Screen.VersionInfo) },
                                            navigateToTroubleshooting = { navigate(Screen.Troubleshooting) },
                                            navigateToCallControlScreen = { navigate(Screen.CallControl(it)) },
                                            navigateToMicrophoneSettings = { navigate(Screen.MicrophoneSettings) },
                                            navigateToHeartRate = { navigate(Screen.HeartRate) },
                                            navigateToRecorder = { navigate(Screen.Recorder) },
                                            navigateToDevices = { navigate(Screen.Devices) },
                                            navigateToIsland = { onTab(AppTab.Island) },
                                            navigateToPhone = { onTab(AppTab.Phone) },
                                        )
                                    }
                                }
                            }
                        }

                    Screen.Rename ->
                        NavEntry(screen) {
                            if (!airPodsViewModel.isReady) LoadingScreen()
                            RenameScreen(airPodsViewModel)
                        }

                    Screen.AppSettings ->
                        NavEntry(screen) {
                            val vm: AppSettingsViewModel = viewModel()
                            AppSettingsScreen(
                                viewModel = vm,
                                navigateToPurchase = ::navigateToPurchase,
                                navigateToTroubleshooting = { navigate(Screen.Troubleshooting) },
                                navigateToOpenSourceLicenses = { navigate(Screen.OpenSourceLicenses) },
                                navigateToStayConnected = { navigate(Screen.StayConnected) },
                                navigateToIsland = { navigate(Screen.IslandSettings) },
                                navigateToDevices = { navigate(Screen.Devices) },
                                navigateToPhone = { navigate(Screen.Phone) },
                                navigateToGlintLab = { navigate(Screen.GlintLab) },
                                navigateToRename = { navigate(Screen.Rename) },
                            )
                        }

                    Screen.Devices ->
                        NavEntry(screen) {
                            DevicesScreen(onChosen = {
                                // Back to the main page, which now shows the chosen device.
                                if (backStack.size > 1 && backStack.last() == Screen.Devices) backStack.removeAt(backStack.lastIndex)
                            })
                        }
                    Screen.StayConnected ->
                        NavEntry(screen) { StayConnectedScreen() }

                    Screen.IslandSettings ->
                        NavEntry(screen) { IslandSettingsScreen() }

                    Screen.Phone ->
                        NavEntry(screen) { PhoneScreen(navigateToIsland = { navigate(Screen.IslandSettings) }, navigateToHeadphones = null) }

                    Screen.GlintLab ->
                        NavEntry(screen) { GlintLabScreen() }

                    Screen.HeartRate ->
                        NavEntry(screen) {
                            HeartRateScreen(
                                navigateToShare = { navigate(Screen.HeartShare) },
                                navigateToHistory = { navigate(Screen.HeartHistory) },
                            )
                        }

                    Screen.HeartHistory ->
                        NavEntry(screen) { HeartHistoryScreen(openSession = { navigate(Screen.HeartSession(it)) }) }

                    is Screen.HeartSession ->
                        NavEntry(screen) { HeartSessionScreen(screen.startMs, onDeleted = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) }) }

                    Screen.HeartShare ->
                        NavEntry(screen) { HeartShareScreen() }

                    Screen.Recorder ->
                        NavEntry(screen) { RecorderScreen() }

                    Screen.Troubleshooting ->
                        NavEntry(screen) {
                            TroubleshootingScreen()
                        }

                    Screen.HeadTracking ->
                        NavEntry(screen) {
                            if (!airPodsViewModel.isReady) LoadingScreen()
                            HeadTrackingScreen(airPodsViewModel, ::navigateToPurchase)
                        }

                    Screen.Accessibility ->
                        NavEntry(screen) {
                            if (!airPodsViewModel.isReady) LoadingScreen()
                            AccessibilitySettingsScreen(
                                viewModel = airPodsViewModel,
                                navigateToPurchase = ::navigateToPurchase,
                                navigateToTransparencyCustomization = { navigate(Screen.TransparencyCustomization) }
                            )
                        }

                    Screen.TransparencyCustomization ->
                        NavEntry(screen) {
                            if (!airPodsViewModel.isReady) LoadingScreen()
                            TransparencySettingsScreen(airPodsViewModel)
                        }

                    Screen.HearingAid ->
                        NavEntry(screen) {
                            if (!airPodsViewModel.isReady) LoadingScreen()
                            HearingAidScreen(
                                viewModel = airPodsViewModel,
                                onNavigateHearingAidAdjustments = { navigate(Screen.HearingAidAdjustments) },
                                onNavigateHearingTest = { navigate(Screen.UpdateHearingTest) },
                            )
                        }

                    Screen.HearingAidAdjustments ->
                        NavEntry(screen) {
                            if (!airPodsViewModel.isReady) LoadingScreen()
                            HearingAidAdjustmentsScreen(airPodsViewModel)
                        }

                    Screen.AdaptiveStrength ->
                        NavEntry(screen) {
                            if (!airPodsViewModel.isReady) LoadingScreen()
                            AdaptiveStrengthScreen(airPodsViewModel, ::navigateToPurchase)
                        }

//                Screen.CameraControl ->
//                    NavEntry(screen) {
//                        CameraControlScreen(airPodsViewModel)
//                    }

                    Screen.OpenSourceLicenses ->
                        NavEntry(screen) {
                            OpenSourceLicensesScreen()
                        }

                    Screen.UpdateHearingTest ->
                        NavEntry(screen) {
                            UpdateHearingTestRoute(airPodsViewModel)
                        }

                    Screen.VersionInfo ->
                        NavEntry(screen) {
                            if (!airPodsViewModel.isReady) LoadingScreen()
                            VersionScreen(airPodsViewModel)
                        }

                    Screen.HearingProtection ->
                        NavEntry(screen) {
                            if (!airPodsViewModel.isReady) LoadingScreen()
                            HearingProtectionScreen(
                                viewModel = airPodsViewModel,
                                navigateToPurchase = ::navigateToPurchase
                            )
                        }

                    Screen.Purchase ->
                        NavEntry(screen) {
                            val vm: PurchaseViewModel = viewModel()
                            PurchaseScreen(vm, backStack)
                        }

                    Screen.Equalizer ->
                        NavEntry(screen) {
                            if (!airPodsViewModel.isReady) LoadingScreen()
                            EqualizerRoute(airPodsViewModel)
                        }

                    is Screen.LongPress ->
                        NavEntry(screen) {
                            if (!airPodsViewModel.isReady) LoadingScreen()
                            LongPress(
                                viewModel = airPodsViewModel,
                                name = screen.bud,
                                navigateToPurchase = ::navigateToPurchase
                            )
                        }

                    is Screen.CallControl ->
                        NavEntry(screen) {
                            if (!airPodsViewModel.isReady) LoadingScreen()
                            CallControlScreen(
                                viewModel = airPodsViewModel,
                                action = screen.action,
                                onCallControlValueChanged = { flipped ->
                                    airPodsViewModel.setControlCommandValue(
                                        AACPManager.Companion.ControlCommandIdentifiers.CALL_MANAGEMENT_CONFIG,
                                        if (flipped) byteArrayOf(0x00, 0x02) else byteArrayOf(
                                            0x00,
                                            0x03
                                        )
                                    )
                                }
                            )
                        }

                    is Screen.MicrophoneSettings ->
                        NavEntry(screen) {
                            if (!airPodsViewModel.isReady) LoadingScreen()
                            MicrophoneSettingsRoute(viewModel = airPodsViewModel)
                        }

                }
            },
            // Physics, not a fixed timer: a critically damped spring starts fast and settles
            // softly, and a swipe-back that's let go mid-way carries on smoothly from there.
            // The screen underneath dims and sinks back a little, like a card being covered.
            transitionSpec = {
                (slideInHorizontally(screenSlide) { it } + fadeIn(screenFade, 0.6f)) togetherWith
                    (slideOutHorizontally(screenSlide) { -it / 4 } + fadeOut(screenFade, 0.55f) + scaleOut(screenScale, 0.96f))
            },
            popTransitionSpec = {
                (slideInHorizontally(screenSlide) { -it / 4 } + fadeIn(screenFade, 0.55f) + scaleIn(screenScale, 0.96f)) togetherWith
                    slideOutHorizontally(screenSlide) { it }
            },
            predictivePopTransitionSpec = { swipeEdge ->
                if (m3eEnabled) {
                    val enterOffset: (Int) -> Int =
                        if (swipeEdge == EDGE_LEFT) {
                            { -it / 6 }
                        } else {
                            { it / 6 }
                        }

                    val exitOffset: (Int) -> Int =
                        if (swipeEdge == EDGE_LEFT) {
                            { it / 8 }
                        } else {
                            { -it / 8 }
                        }

                    fadeIn(
                        animationSpec = tween(250)
                    ) +
                        slideInHorizontally(
                            initialOffsetX = enterOffset,
                            animationSpec = tween(250)
                        ) togetherWith
                        fadeOut(
                            targetAlpha = 0.75f,
                            animationSpec = tween(250)
                        ) +
                        scaleOut(
                            targetScale = 0.85f,
                            animationSpec = tween(250)
                        ) +
                        slideOutHorizontally(
                            targetOffsetX = exitOffset,
                            animationSpec = tween(250)
                        )
                } else {
                    (slideInHorizontally(screenSlide) { -it / 4 } + fadeIn(screenFade, 0.55f) + scaleIn(screenScale, 0.96f)) togetherWith
                        slideOutHorizontally(screenSlide) { it }
                }
            },
        )
    }
}

private val screenSlide = spring(dampingRatio = 1f, stiffness = 420f, visibilityThreshold = IntOffset.VisibilityThreshold)
private val screenFade = spring<Float>(dampingRatio = 1f, stiffness = 420f)
private val screenScale = spring<Float>(dampingRatio = 1f, stiffness = 420f)
