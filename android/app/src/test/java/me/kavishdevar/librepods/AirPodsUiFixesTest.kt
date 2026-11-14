package me.kavishdevar.librepods

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.activity.ComponentActivity
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import me.kavishdevar.librepods.billing.BillingManager
import me.kavishdevar.librepods.billing.FOSSBillingProvider
import me.kavishdevar.librepods.bluetooth.AACPManager.Companion.ControlCommandIdentifiers
import me.kavishdevar.librepods.bluetooth.ATTHandles
import me.kavishdevar.librepods.data.AirPodsInstance
import me.kavishdevar.librepods.data.AirPodsModels
import me.kavishdevar.librepods.data.StemAction
import me.kavishdevar.librepods.presentation.components.NoiseControlSettings
import me.kavishdevar.librepods.presentation.glint.ConnectionStatusPanel
import me.kavishdevar.librepods.presentation.glint.PodsVideoConfig
import me.kavishdevar.librepods.presentation.screens.AirPodsSettingsScreen
import me.kavishdevar.librepods.presentation.screens.AppSettingsScreen
import me.kavishdevar.librepods.presentation.screens.DevicesContent
import me.kavishdevar.librepods.presentation.screens.RenameScreen
import me.kavishdevar.librepods.presentation.theme.LibrePodsTheme
import me.kavishdevar.librepods.presentation.viewmodel.AirPodsUiState
import me.kavishdevar.librepods.presentation.viewmodel.AirPodsViewModel
import me.kavishdevar.librepods.presentation.viewmodel.demoState
import me.kavishdevar.librepods.services.ChosenDevice
import me.kavishdevar.librepods.services.DeviceChoice
import me.kavishdevar.librepods.services.DeviceKind
import me.kavishdevar.librepods.services.GlintStatus
import me.kavishdevar.librepods.services.KnownDevice
import me.kavishdevar.librepods.services.LinkState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Tests for AirPodsViewModel pre-init/demo safety, Apple-style NoiseControlSettings single-row
 * semantics and drag interaction, Rename/AppSettings live sync, and AirPods capabilities/retry UI.
 */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class, qualifiers = "w412dp-h915dp-xxhdpi")
class AirPodsUiFixesTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val prefs get() = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    @Before fun setUp() {
        prefs.edit().clear().commit()
        DeviceChoice.reset()
        PodsVideoConfig.enabled = false
        BillingManager.provider = FOSSBillingProvider(context)
        GlintStatus.set(LinkState.Idle)
    }

    @After fun tearDown() {
        GlintStatus.set(LinkState.Idle)
        DeviceChoice.reset()
        prefs.edit().clear().commit()
    }

    @Test fun airPodsViewModelMethodsAreSafeBeforeInitAndInDemoMode() {
        val vm = AirPodsViewModel()
        assertFalse(vm.isReady)

        // Calling public methods before init() must not throw UninitializedPropertyAccessException
        // or IndexOutOfBoundsException on empty payloads.
        vm.setCustomEq(low = 60, mid = 50, high = 70)
        assertEquals(60, vm.uiState.value.customEq.low)
        vm.setCustomEqEnabled(true)
        assertEquals(2, vm.uiState.value.customEq.state)

        vm.setControlCommandValue(ControlCommandIdentifiers.LISTENING_MODE_CONFIGS, byteArrayOf())
        vm.toggleListeningMode(0x01)
        vm.setControlCommandBoolean(ControlCommandIdentifiers.ALLOW_OFF_OPTION, false)
        vm.setControlCommandInt(ControlCommandIdentifiers.LISTENING_MODE, 3)
        vm.setControlCommandByte(ControlCommandIdentifiers.AUTO_ANC_STRENGTH, 0x40)
        vm.observeControl(ControlCommandIdentifiers.LISTENING_MODE)
        vm.observeAACP()
        vm.loadCurrentStatus()

        vm.setOffListeningMode(false)
        assertFalse(vm.uiState.value.offListeningMode)
        vm.setHeadGesturesEnabled(false)
        assertFalse(vm.uiState.value.headGesturesEnabled)
        vm.setDynamicEndOfCharge(true)
        assertTrue(vm.uiState.value.dynamicEndOfCharge)

        vm.reconnectFromSavedMac()
        vm.setName("Workshop Pods")
        assertEquals("Workshop Pods", vm.uiState.value.deviceName)

        vm.startHeadTracking()
        assertTrue(vm.uiState.value.headTrackingActive)
        vm.stopHeadTracking()
        assertFalse(vm.uiState.value.headTrackingActive)

        vm.setATTCharacteristicValue(ATTHandles.LOUD_SOUND_REDUCTION, byteArrayOf())
        assertFalse(vm.uiState.value.loudSoundReductionEnabled)
        vm.setATTCharacteristicValue(ATTHandles.LOUD_SOUND_REDUCTION, byteArrayOf(0x01))
        assertTrue(vm.uiState.value.loudSoundReductionEnabled)
        vm.setATTCharacteristicValue(ATTHandles.HEARING_AID, byteArrayOf(0x02))
        vm.setATTCharacteristicValue(ATTHandles.TRANSPARENCY, byteArrayOf(0x03))
        vm.loadATT()
        vm.observeATT()

        vm.setAutomaticEarDetectionEnabled(false)
        assertFalse(vm.uiState.value.automaticEarDetectionEnabled)
        vm.setAutomaticConnectionEnabled(false)
        assertFalse(vm.uiState.value.automaticConnectionEnabled)
        vm.sendPhoneMediaEQ(floatArrayOf(0f), 0x01, 0x02)
        vm.setLongPressAction("LEFT", StemAction.PLAY_PAUSE)
        assertEquals(StemAction.PLAY_PAUSE, vm.uiState.value.leftAction)
        vm.setLongPressAction("Right", StemAction.DIGITAL_ASSISTANT)
        assertEquals(StemAction.DIGITAL_ASSISTANT, vm.uiState.value.rightAction)
        vm.disconnect()

        // Demo mode safety + disconnect updates local demo connection state cleanly.
        vm.activateDemoMode()
        assertTrue(vm.isDemoMode)
        assertTrue(vm.uiState.value.isLocallyConnected)
        vm.setName("Demo Pods")
        assertEquals("Demo Pods", vm.uiState.value.deviceName)
        vm.disconnect()
        assertFalse(vm.isDemoMode)
        assertFalse(vm.uiState.value.isLocallyConnected)
    }

    @Test fun appleNoiseControlHasSingleClickableRowSupportsClickAndDragAndRespectsOffFlag() {
        var showOff by androidx.compose.runtime.mutableStateOf(true)
        var modeValue by mutableIntStateOf(3) // Transparency
        val reported = mutableListOf<Int>()

        rule.setContent {
            LibrePodsTheme(m3eEnabled = false) {
                NoiseControlSettings(
                    showOffListeningMode = showOff,
                    noiseControlModeValue = modeValue,
                    onNoiseControlModeChanged = {
                        reported += it
                        modeValue = it
                    }
                )
            }
        }
        rule.waitForIdle()

        // Confirm duplicate lower button Row was removed: each mode has exactly one clickable node.
        val modes = listOf("Off", "Transparency", "Adaptive", "Noise Cancellation")
        for (label in modes) {
            rule.onAllNodes(hasContentDescription(label) and hasClickAction()).assertCountEquals(1)
        }

        val isSelected = SemanticsMatcher.expectValue(SemanticsProperties.Selected, true)
        val notSelected = SemanticsMatcher.expectValue(SemanticsProperties.Selected, false)
        rule.onNode(hasContentDescription("Transparency") and hasClickAction()).assert(isSelected)
        rule.onNode(hasContentDescription("Adaptive") and hasClickAction()).assert(notSelected)

        // Tapping switches to Adaptive (ordinal 3 -> value 4).
        rule.onNode(hasContentDescription("Adaptive") and hasClickAction()).performClick()
        rule.waitForIdle()
        assertEquals(4, modeValue)
        rule.onNode(hasContentDescription("Adaptive") and hasClickAction()).assert(isSelected)

        // Dragging left across the segmented control moves back toward Transparency / Off.
        rule.onNode(hasContentDescription("Adaptive") and hasClickAction()).performTouchInput {
            swipe(start = center, end = center - Offset(420f, 0f), durationMillis = 200)
        }
        rule.waitForIdle()
        assertTrue("Expected horizontal drag to change mode, history=$reported", modeValue != 4)

        // Turning Off option off removes the Off button and clamps raw OFF mode to Transparency.
        modeValue = 1
        showOff = false
        rule.waitForIdle()
        rule.onAllNodes(hasContentDescription("Off")).assertCountEquals(0)
        rule.onAllNodes(hasContentDescription("Transparency") and hasClickAction()).assertCountEquals(1)
        rule.onNode(hasContentDescription("Transparency") and hasClickAction()).assert(isSelected)
    }

    @Test fun appSettingsUpdatesNameImmediatelyWhenPreferenceChanges() {
        rule.setContent {
            LibrePodsTheme(m3eEnabled = false) {
                AppSettingsScreen(
                    navigateToPurchase = {},
                    navigateToTroubleshooting = {},
                    navigateToOpenSourceLicenses = {},
                )
            }
        }
        rule.waitForIdle()

        rule.onNode(hasText("Name")).performScrollTo()
        rule.onNode(hasText("AirPods")).assertExists()

        // Simulate RenameScreen saving a new name into settings SharedPreferences.
        rule.runOnUiThread {
            prefs.edit().putString("name", "Studio Pods").commit()
        }
        rule.waitForIdle()
        rule.onNode(hasText("Studio Pods")).assertExists()
    }

    @Test fun renameScreenDoesNotOverwriteOnOpenAndUpdatesModelOnEdit() {
        prefs.edit().putString("name", "Jake's Pods").commit()
        var nameWrites = 0
        val spy = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "name") nameWrites++
        }
        prefs.registerOnSharedPreferenceChangeListener(spy)

        val vm = AirPodsViewModel()
        try {
            rule.setContent {
                LibrePodsTheme(m3eEnabled = false) {
                    RenameScreen(viewModel = vm)
                }
            }
            rule.waitForIdle()

            // Merely opening Rename must not rewrite SharedPreferences or emit a BT rename packet.
            assertEquals(0, nameWrites)
            assertEquals("AirPods", vm.uiState.value.deviceName)

            // Typing a new name saves it and updates the ViewModel state.
            rule.onNode(hasSetTextAction()).performTextReplacement("Travel Pods")
            rule.waitForIdle()
            assertEquals("Travel Pods", prefs.getString("name", null))
            assertEquals("Travel Pods", vm.uiState.value.deviceName)
            assertTrue(nameWrites >= 1)
        } finally {
            prefs.unregisterOnSharedPreferenceChangeListener(spy)
        }
    }

    @Test fun adaptiveAudioHiddenForAirPods4WithoutAncAndGaveUpRowClickable() {
        val airPods4Model = AirPodsModels.getModelByModelNumber("A3053")!!
        val nonAncState = AirPodsUiState(
            deviceName = "AirPods 4",
            isLocallyConnected = true,
            instance = AirPodsInstance(
                name = "AirPods 4",
                model = airPods4Model,
                actualModelNumber = "A3053",
                serialNumber = null,
                leftSerialNumber = null,
                rightSerialNumber = null,
                version1 = null,
                version2 = null,
                version3 = null,
            ),
            capabilities = airPods4Model.capabilities,
            vendorIdHook = true,
            isPremium = true,
        )
        GlintStatus.set(LinkState.GaveUp("AirPods 4", "L2CAP refused"))

        rule.setContent {
            LibrePodsTheme(m3eEnabled = false) {
                AirPodsSettingsScreen(
                    state = nonAncState,
                    setControlCommandInt = { _, _ -> },
                    setControlCommandBoolean = { _, _ -> },
                    setControlCommandByte = { _, _ -> },
                    setATTCharacteristicValue = { _, _ -> },
                    onAutomaticEarDetectionChanged = {},
                    onAutomaticConnectionChanged = {},
                    setDynamicEndOfCharge = {},
                    setOffListeningMode = {},
                    disconnect = {},
                    navigateToRename = {},
                    navigateToHearingProtection = {},
                    navigateToHearingAid = {},
                    navigateToLeftLongPress = {},
                    navigateToRightLongPress = {},
                    navigateToPurchase = {},
                    navigateToAdaptiveStrength = {},
                    navigateToEqualizer = {},
                    navigateToHeadTracking = {},
                    navigateToAccessibility = {},
                    navigateToVersion = {},
                    navigateToTroubleshooting = {},
                    navigateToCallControlScreen = {},
                    navigateToMicrophoneSettings = {},
                    activateDemoMode = {},
                    reconnectFromSavedMac = {},
                )
            }
        }
        rule.waitForIdle()

        // GaveUp status line offers a direct one-tap retry.
        val retryNode = hasText("Controls offline: changes won't apply · Tap to retry") and hasClickAction()
        rule.onNode(retryNode).assertExists().performClick()

        // AirPods 4 without ANC has Personalized Volume (ADAPTIVE_VOLUME) but NOT Adaptive Audio.
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Personalized Volume"))
        rule.onNode(hasText("Adaptive Audio")).assertDoesNotExist()
    }

    @Test fun adaptiveAudioShownForDemoAncModel() {
        rule.setContent {
            LibrePodsTheme(m3eEnabled = false) {
                AirPodsSettingsScreen(
                    state = demoState,
                    setControlCommandInt = { _, _ -> },
                    setControlCommandBoolean = { _, _ -> },
                    setControlCommandByte = { _, _ -> },
                    setATTCharacteristicValue = { _, _ -> },
                    onAutomaticEarDetectionChanged = {},
                    onAutomaticConnectionChanged = {},
                    setDynamicEndOfCharge = {},
                    setOffListeningMode = {},
                    disconnect = {},
                    navigateToRename = {},
                    navigateToHearingProtection = {},
                    navigateToHearingAid = {},
                    navigateToLeftLongPress = {},
                    navigateToRightLongPress = {},
                    navigateToPurchase = {},
                    navigateToAdaptiveStrength = {},
                    navigateToEqualizer = {},
                    navigateToHeadTracking = {},
                    navigateToAccessibility = {},
                    navigateToVersion = {},
                    navigateToTroubleshooting = {},
                    navigateToCallControlScreen = {},
                    navigateToMicrophoneSettings = {},
                    activateDemoMode = {},
                    reconnectFromSavedMac = {},
                )
            }
        }
        rule.waitForIdle()
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Adaptive Audio"))
        rule.onNode(hasText("Adaptive Audio")).assertExists()
    }

    @Test fun connectionStatusPanelShowsRetryWhenGaveUp() {
        prefs.edit().putBoolean("connection_successful", true).commit()
        GlintStatus.set(LinkState.GaveUp("AirPods Pro", "timeout"))
        rule.setContent {
            LibrePodsTheme(m3eEnabled = false) {
                ConnectionStatusPanel(onTroubleshoot = {})
            }
        }
        rule.waitForIdle()
        rule.onNode(hasText("Couldn't reach the controls")).assertExists()
        rule.onNode(hasText("Try again")).assertExists().performClick()
    }

    @Test fun devicesContentRendersAndSelectsOtherDevice() {
        val list = listOf(
            KnownDevice("AA:BB:CC:DD:EE:FF", "AirPods Pro", DeviceKind.AIRPODS, connected = false),
            KnownDevice("00:11:22:33:44:55", "Beats Solo 4", DeviceKind.BEATS_SOLO_4, connected = true),
        )
        var picked: KnownDevice? = null

        rule.setContent {
            LibrePodsTheme(m3eEnabled = false) {
                DevicesContent(
                    devices = list,
                    chosen = ChosenDevice(DeviceKind.AIRPODS, "AA:BB:CC:DD:EE:FF", "AirPods Pro"),
                    onChoose = { picked = it },
                    onOpenBluetooth = {},
                )
            }
        }
        rule.waitForIdle()

        rule.onNode(hasText("Beats Solo 4")).performScrollTo().performClick()
        rule.waitForIdle()
        assertEquals("00:11:22:33:44:55", picked?.address)
    }
}
