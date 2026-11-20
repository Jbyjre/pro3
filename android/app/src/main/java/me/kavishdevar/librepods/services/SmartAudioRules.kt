package me.kavishdevar.librepods.services

import me.kavishdevar.librepods.bluetooth.AACPManager
import me.kavishdevar.librepods.data.BatteryStatus

/**
 * Pure, unit-tested rules for AirPods connection stability, voice-typing/call protection,
 * case-open detection, and smarter ear-detection auto-pause / auto-play.
 */
object SmartAudioRules {
    /** Show the bottom case card when the AirPods case opens nearby (on by default). */
    const val PREF_CASE_POPUP = "show_bottom_sheet_popup"

    /**
     * Auto-play resumes media when you put an AirPod back in within 15 minutes of an
     * ear-detection auto-pause. After 15 minutes away, the pause expires so putting your
     * AirPods in hours later never blasts old music by surprise.
     */
    const val AUTO_RESUME_WINDOW_MS = 15 * 60_000L

    /**
     * How long to wait before announcing an unexpected Bluetooth disconnect while the AirPods
     * were worn or active. Short profile handovers (such as switching between A2DP and HFP/SCO
     * for voice typing) settle inside this window and reconnect quietly.
     */
    const val DISCONNECT_SETTLE_MS = 2_500L

    /** Multi-step rechecks after the control socket closes, covering 1–5 s voice-typing handovers. */
    val CONTROL_RECONNECT_CHECKS_MS = longArrayOf(800L, 2_500L, 5_000L)

    private val MAC_REGEX = Regex("([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}")
    private const val DUMMY_ANDROID_MAC = "02:00:00:00:00:00"

    /** True only when [mac] is a real Bluetooth address and not Android's unprivileged placeholder. */
    fun isRealLocalMac(mac: String?): Boolean {
        if (mac.isNullOrBlank()) return false
        val trimmed = mac.trim()
        if (trimmed.equals(DUMMY_ANDROID_MAC, ignoreCase = true)) return false
        return MAC_REGEX.matches(trimmed)
    }

    /**
     * Whether pro should yield connection ownership and disconnect audio on an AACP ownership
     * or audio-source packet.
     *
     * On non-rooted Android phones (like the Galaxy S26 FE without the Xposed Apple VendorID hook),
     * Android does not participate in Apple's TiPi multi-device ownership protocol, and the AirPods
     * can report `OWNS_CONNECTION = 0` or `AudioSourceType.CALL` when switching to the HFP/SCO
     * microphone for voice typing. Disconnecting or pausing in that situation drops the AirPods
     * mid-sentence.
     */
    fun shouldYieldOwnership(
        vendorIdHook: Boolean,
        owns: Boolean,
        localMac: String?,
        audioSourceMac: String?,
        audioSourceType: AACPManager.Companion.AudioSourceType?,
        connectedDevices: List<AACPManager.Companion.ConnectedDevice>,
        voiceOrCallActive: Boolean,
    ): Boolean {
        if (owns) return false
        if (!vendorIdHook) return false
        if (voiceOrCallActive) return false
        if (!isRealLocalMac(localMac)) return false
        val otherDevices = connectedDevices.any { !it.mac.equals(localMac, ignoreCase = true) }
        if (!otherDevices) return false
        if (audioSourceType != null) {
            if (audioSourceType == AACPManager.Companion.AudioSourceType.NONE) return false
            if (audioSourceMac.isNullOrBlank() || audioSourceMac.equals(localMac, ignoreCase = true)) return false
        }
        return true
    }

    /**
     * Whether a battery report means both AirPods are sitting in the case charging and audio
     * can be disconnected. Never disconnects while a bud is in an ear or while voice/call is active.
     */
    fun shouldDisconnectAudioForCharging(
        leftStatus: Int,
        rightStatus: Int,
        leftInEar: Boolean,
        rightInEar: Boolean,
        voiceOrCallActive: Boolean,
    ): Boolean {
        if (voiceOrCallActive || leftInEar || rightInEar) return false
        return leftStatus == BatteryStatus.CHARGING && rightStatus == BatteryStatus.CHARGING
    }

    /**
     * Whether a battery report should ask Android to connect the audio profiles.
     * When audio is already connected or voice typing / a call is using HFP/SCO, re-invoking
     * `A2DP.connect()` on every battery report is redundant and can disrupt the active microphone link.
     */
    fun shouldConnectAudioOnBattery(
        leftStatus: Int,
        rightStatus: Int,
        alreadyConnected: Boolean,
        voiceOrCallActive: Boolean,
    ): Boolean {
        if (alreadyConnected || voiceOrCallActive) return false
        return leftStatus != BatteryStatus.CHARGING || rightStatus != BatteryStatus.CHARGING
    }

    /**
     * Detects when the AirPods case wakes up and begins reporting its battery over the AACP
     * control link (for example when the lid opens while connected, or when opening the case
     * connects the AirPods before a BLE lid advert is seen).
     */
    fun caseWokeUp(
        wasCasePresent: Boolean?,
        nowCaseStatus: Int,
        nowCaseLevel: Int,
        leftInEar: Boolean,
        rightInEar: Boolean,
    ): Boolean {
        if (leftInEar && rightInEar) return false
        val nowPresent = nowCaseStatus != BatteryStatus.DISCONNECTED && nowCaseLevel in 1..100
        return wasCasePresent == false && nowPresent
    }

    /**
     * Conversation Awareness should not duck volume or send play/pause keys while the user is
     * speaking into the microphone for voice typing, a voice message, or a phone/video call,
     * nor when neither AirPod is in an ear.
     */
    fun shouldAllowConversationAwareness(
        voiceOrCallActive: Boolean,
        leftInEar: Boolean,
        rightInEar: Boolean,
    ): Boolean {
        if (voiceOrCallActive) return false
        return leftInEar || rightInEar
    }

    /**
     * Whether putting an AirPod back in should auto-resume playback that pro previously paused.
     */
    fun shouldAutoResume(
        iPausedTheMedia: Boolean,
        pausedAtMs: Long,
        nowMs: Long,
        voiceOrCallActive: Boolean,
    ): Boolean {
        if (!iPausedTheMedia || voiceOrCallActive) return false
        if (pausedAtMs <= 0L) return true
        val elapsed = nowMs - pausedAtMs
        return elapsed < 0L || elapsed <= AUTO_RESUME_WINDOW_MS
    }

    /**
     * Whether an audio/control drop should alert the user that their AirPods disconnected.
     * Fires only when the user did not ask to disconnect, the AirPods were being worn or used
     * for voice/audio (not put in the case to charge), and the link is still down after the
     * settle window.
     */
    fun shouldAnnounceUnexpectedDisconnect(
        userRequested: Boolean,
        wasWornOrActive: Boolean,
        bothChargingInCase: Boolean,
        stillConnected: Boolean,
    ): Boolean {
        if (userRequested || stillConnected || bothChargingInCase) return false
        return wasWornOrActive
    }
}
