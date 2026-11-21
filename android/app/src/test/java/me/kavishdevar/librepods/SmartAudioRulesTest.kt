package me.kavishdevar.librepods

import me.kavishdevar.librepods.bluetooth.AACPManager
import me.kavishdevar.librepods.bluetooth.AACPManager.Companion.AudioSourceType
import me.kavishdevar.librepods.data.BatteryStatus
import me.kavishdevar.librepods.services.SmartAudioRules
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartAudioRulesTest {
    private val otherDevice = listOf(AACPManager.Companion.ConnectedDevice("DD:EE:FF:44:55:66", 1, 1, "MacBook"))

    @Test fun androidPlaceholderMacIsNeverTreatedAsRealLocalMac() {
        assertFalse(SmartAudioRules.isRealLocalMac(null))
        assertFalse(SmartAudioRules.isRealLocalMac(""))
        assertFalse(SmartAudioRules.isRealLocalMac("02:00:00:00:00:00"))
        assertFalse(SmartAudioRules.isRealLocalMac("not-a-mac"))
        assertTrue(SmartAudioRules.isRealLocalMac("AA:BB:CC:11:22:33"))
    }

    @Test fun voiceTypingAndUnrootedPhonesNeverFalseYieldOwnership() {
        // Unrooted Samsung S26 FE without Xposed vendor_id_hook must never disconnect on OWNS_CONNECTION=0
        assertFalse(
            SmartAudioRules.shouldYieldOwnership(
                vendorIdHook = false,
                owns = false,
                localMac = "AA:BB:CC:11:22:33",
                audioSourceMac = "DD:EE:FF:44:55:66",
                audioSourceType = AudioSourceType.MEDIA,
                connectedDevices = otherDevice,
                voiceOrCallActive = false,
            )
        )
        // Even with Xposed hook active, never yield while voice typing or in a call (HFP/SCO transition)
        assertFalse(
            SmartAudioRules.shouldYieldOwnership(
                vendorIdHook = true,
                owns = false,
                localMac = "AA:BB:CC:11:22:33",
                audioSourceMac = "DD:EE:FF:44:55:66",
                audioSourceType = AudioSourceType.CALL,
                connectedDevices = otherDevice,
                voiceOrCallActive = true,
            )
        )
        // With Android's 02:00:00:00:00:00 placeholder MAC, never yield
        assertFalse(
            SmartAudioRules.shouldYieldOwnership(
                vendorIdHook = true,
                owns = false,
                localMac = "02:00:00:00:00:00",
                audioSourceMac = "DD:EE:FF:44:55:66",
                audioSourceType = AudioSourceType.MEDIA,
                connectedDevices = otherDevice,
                voiceOrCallActive = false,
            )
        )
        // Legitimate cross-device takeover with Xposed hook, real MAC, and no active voice/call
        assertTrue(
            SmartAudioRules.shouldYieldOwnership(
                vendorIdHook = true,
                owns = false,
                localMac = "AA:BB:CC:11:22:33",
                audioSourceMac = "DD:EE:FF:44:55:66",
                audioSourceType = AudioSourceType.MEDIA,
                connectedDevices = otherDevice,
                voiceOrCallActive = false,
            )
        )
    }

    @Test fun batteryPacketsDoNotReconnectWhenAlreadyConnectedOrDuringVoiceTyping() {
        assertFalse(
            "already connected audio must not re-invoke A2DP.connect on every battery packet",
            SmartAudioRules.shouldConnectAudioOnBattery(
                leftStatus = BatteryStatus.NOT_CHARGING,
                rightStatus = BatteryStatus.NOT_CHARGING,
                alreadyConnected = true,
                voiceOrCallActive = false,
            )
        )
        assertFalse(
            "active voice typing / HFP call must not be disrupted by A2DP reconnect",
            SmartAudioRules.shouldConnectAudioOnBattery(
                leftStatus = BatteryStatus.NOT_CHARGING,
                rightStatus = BatteryStatus.NOT_CHARGING,
                alreadyConnected = false,
                voiceOrCallActive = true,
            )
        )
        assertTrue(
            "disconnected audio while at least one bud is out of case reconnects when idle",
            SmartAudioRules.shouldConnectAudioOnBattery(
                leftStatus = BatteryStatus.NOT_CHARGING,
                rightStatus = BatteryStatus.CHARGING,
                alreadyConnected = false,
                voiceOrCallActive = false,
            )
        )
        assertTrue(
            SmartAudioRules.shouldDisconnectAudioForCharging(
                leftStatus = BatteryStatus.CHARGING,
                rightStatus = BatteryStatus.CHARGING,
                leftInEar = false,
                rightInEar = false,
                voiceOrCallActive = false,
            )
        )
        assertFalse(
            SmartAudioRules.shouldDisconnectAudioForCharging(
                leftStatus = BatteryStatus.CHARGING,
                rightStatus = BatteryStatus.CHARGING,
                leftInEar = false,
                rightInEar = false,
                voiceOrCallActive = true,
            )
        )
    }

    @Test fun caseWakeupTriggersWhenCaseTransitionsFromDisconnectedToOpen() {
        assertFalse("initial report after connecting does not double-fire case open", SmartAudioRules.caseWokeUp(null, BatteryStatus.NOT_CHARGING, 80, false, false))
        assertFalse(SmartAudioRules.caseWokeUp(false, BatteryStatus.DISCONNECTED, 0, false, false))
        assertTrue("case waking while connected triggers case-open popup", SmartAudioRules.caseWokeUp(false, BatteryStatus.NOT_CHARGING, 85, true, false))
        assertTrue("charging case waking while connected triggers case-open popup", SmartAudioRules.caseWokeUp(false, BatteryStatus.CHARGING, 60, false, false))
        assertFalse("when both buds are in ear, case report does not trigger popup", SmartAudioRules.caseWokeUp(false, BatteryStatus.NOT_CHARGING, 85, true, true))
        assertFalse("subsequent battery ticks while case stays open do not re-trigger", SmartAudioRules.caseWokeUp(true, BatteryStatus.NOT_CHARGING, 84, false, false))
    }

    @Test fun conversationAwarenessIsSuppressedDuringVoiceTypingAndCalls() {
        assertTrue(SmartAudioRules.shouldAllowConversationAwareness(voiceOrCallActive = false, leftInEar = true, rightInEar = false))
        assertFalse(SmartAudioRules.shouldAllowConversationAwareness(voiceOrCallActive = true, leftInEar = true, rightInEar = true))
        assertFalse(SmartAudioRules.shouldAllowConversationAwareness(voiceOrCallActive = false, leftInEar = false, rightInEar = false))
    }

    @Test fun autoResumeAlwaysResumesEvenAfterFifteenMinutesOrHoursExceptDuringVoiceTyping() {
        val pausedAt = 10_000L
        assertTrue(
            SmartAudioRules.shouldAutoResume(
                iPausedTheMedia = true,
                pausedAtMs = pausedAt,
                nowMs = pausedAt + 60_000L,
                voiceOrCallActive = false,
            )
        )
        assertTrue(
            "putting AirPods in after 15+ minutes or hours still brings the music right back",
            SmartAudioRules.shouldAutoResume(
                iPausedTheMedia = true,
                pausedAtMs = pausedAt,
                nowMs = pausedAt + 2 * 60 * 60_000L,
                voiceOrCallActive = false,
            )
        )
        assertFalse(
            "auto-resume never starts music while voice typing or in a call",
            SmartAudioRules.shouldAutoResume(
                iPausedTheMedia = true,
                pausedAtMs = pausedAt,
                nowMs = pausedAt + 5_000L,
                voiceOrCallActive = true,
            )
        )
    }

    @Test fun unexpectedDisconnectAlertFiresOnlyWhenDroppedWhileWornOrActive() {
        assertTrue(
            SmartAudioRules.shouldAnnounceUnexpectedDisconnect(
                userRequested = false,
                wasWornOrActive = true,
                bothChargingInCase = false,
                stillConnected = false,
            )
        )
        assertFalse(
            "transient profile switch that reconnected within the settle window stays quiet",
            SmartAudioRules.shouldAnnounceUnexpectedDisconnect(
                userRequested = false,
                wasWornOrActive = true,
                bothChargingInCase = false,
                stillConnected = true,
            )
        )
        assertFalse(
            "putting buds back in the case to charge stays quiet",
            SmartAudioRules.shouldAnnounceUnexpectedDisconnect(
                userRequested = false,
                wasWornOrActive = true,
                bothChargingInCase = true,
                stillConnected = false,
            )
        )
        assertFalse(
            SmartAudioRules.shouldAnnounceUnexpectedDisconnect(
                userRequested = true,
                wasWornOrActive = true,
                bothChargingInCase = false,
                stillConnected = false,
            )
        )
    }
}
