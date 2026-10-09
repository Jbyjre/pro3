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

package me.kavishdevar.librepods.presentation.overlays

import android.content.Intent
import android.util.Log
import me.kavishdevar.librepods.MainActivity
import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.WindowInsets
import android.view.WindowManager
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.kavishdevar.librepods.data.Battery
import me.kavishdevar.librepods.data.BatteryComponent
import me.kavishdevar.librepods.data.BatteryStatus

/** Everything the overlays show about the AirPods, in one immutable value. */
@Immutable
data class PodsSnapshot(
    val name: String = "AirPods Pro",
    val left: Int? = null,
    val right: Int? = null,
    val case: Int? = null,
    val leftCharging: Boolean = false,
    val rightCharging: Boolean = false,
    val caseCharging: Boolean = false,
    val leftInEar: Boolean = false,
    val rightInEar: Boolean = false,
    val lidOpen: Boolean = false,
    /** 1 off, 2 noise cancellation, 3 transparency, 4 adaptive, 0 unknown. */
    val listeningMode: Int = 0,
    /**
     * Headphones (Beats Solo 4 or others) instead of AirPods: one battery (in [left] and
     * [right] alike), no case, no ears, no listening mode. The islands draw one ring for it.
     */
    val headphones: Boolean = false,
) {
    /** The number people care about: the lower of the two buds that report. */
    val budsLevel: Int? get() = listOfNotNull(left, right).minOrNull()
    val budsCharging: Boolean get() = leftCharging || rightCharging

    companion object {
        fun from(
            name: String,
            battery: List<Battery>,
            earStatus: List<Byte>?,
            listeningMode: Int,
            lidOpen: Boolean = false,
        ): PodsSnapshot {
            fun find(c: Int) = battery.find { it.component == c }?.takeIf { it.status != BatteryStatus.DISCONNECTED && it.level > 0 }
            fun charging(b: Battery?) = b != null && (b.status == BatteryStatus.CHARGING || b.status == BatteryStatus.OPTIMIZED_CHARGING)
            val l = find(BatteryComponent.LEFT)
            val r = find(BatteryComponent.RIGHT)
            val c = find(BatteryComponent.CASE)
            return PodsSnapshot(
                name = name,
                left = l?.level, right = r?.level, case = c?.level,
                leftCharging = charging(l), rightCharging = charging(r), caseCharging = charging(c),
                leftInEar = earStatus?.getOrNull(0) == 0x00.toByte(),
                rightInEar = earStatus?.getOrNull(1) == 0x00.toByte(),
                lidOpen = lidOpen,
                listeningMode = listeningMode,
            )
        }
    }
}

/** Legacy island reasons still used by the takeover logic in AirPodsService. */
enum class IslandType {
    CONNECTED,
    TAKING_OVER,
    MOVED_TO_REMOTE,
    MOVED_TO_OTHER_DEVICE,
}

/** What the island is announcing. */
sealed interface IslandEvent {
    data object Connected : IslandEvent
    data object InEar : IslandEvent
    data class LowBattery(val level: Int) : IslandEvent
    data class ListeningMode(val mode: Int) : IslandEvent
    data class MovedToDevice(val deviceName: String, val canTakeBack: Boolean) : IslandEvent
    data object TakingOver : IslandEvent
    data object Charging : IslandEvent
    data class Problem(val title: String, val message: String) : IslandEvent
    /** Heart rate: [alert] for a reading above the limit (the only time heart pops the island). */
    data class Heart(val alert: Boolean) : IslandEvent
    /**
     * An AirPod came out. [remaining] buds are still in (0 or 1); [paused] when music was
     * playing and Glint paused it. (The AirPods report the primary and secondary bud, not
     * left and right, so the island doesn't say which side.)
     */
    data class BudOut(val remaining: Int, val paused: Boolean) : IslandEvent
    /** The second AirPod went back in. */
    data object BothIn : IslandEvent
    /** Something started playing on the AirPods, or a new song started. Words come live from NowPlaying. */
    data object Music : IslandEvent
    /** The Dynamic Island can't be tapped yet: one switch in pro fixes it (tap opens that page). */
    data object TapSetup : IslandEvent
    /**
     * The opened Dynamic Island when nothing is playing: the app you're in, what's live right now
     * (a timer, charging, your headphones, the next alarm) and quick controls for the phone.
     */
    data object Glance : IslandEvent
    /** The island's timer ended and is ringing: Stop, or one more minute. */
    data object TimerDone : IslandEvent
}

/**
 * Entry point for showing Glint's overlays. Safe to call from any thread; work is posted to
 * the main thread. Uses the live [snapshot] so batteries update while an overlay is visible.
 */
object GlintOverlays {
    private val main = Handler(Looper.getMainLooper())
    private val _snapshot = MutableStateFlow(PodsSnapshot())
    val snapshot: StateFlow<PodsSnapshot> = _snapshot.asStateFlow()

    /** Called when the user taps "Use on this phone" on the island. Set by AirPodsService. */
    @Volatile var takeBackHandler: (() -> Unit)? = null

    // Both controllers only ever hold the application context, so this is not a leak.
    @SuppressLint("StaticFieldLeak")
    private var island: IslandController? = null
    @SuppressLint("StaticFieldLeak")
    private var card: CardController? = null

    fun updateSnapshot(value: PodsSnapshot) {
        _snapshot.value = value
    }

    fun showIsland(context: Context, event: IslandEvent, expand: Boolean = false) = main.post {
        val c = island ?: IslandController(context.applicationContext).also { island = it }
        c.show(event, expand)
    }

    /**
     * True for the moment a screenshot is taken from the island's Capture button: the Dynamic
     * Island vanishes at once and comes back after, so it isn't in the picture (as on an iPhone).
     */
    val capturing = androidx.compose.runtime.mutableStateOf(false)

    /** True while the big island is on screen (the mini island hides meanwhile). */
    val islandVisible = androidx.compose.runtime.mutableStateOf(false)
    /** The pop-up that's up opened around the camera, over the Dynamic Island (the iPhone style). */
    val islandAround = androidx.compose.runtime.mutableStateOf(false)

    /**
     * Where the mini island is right now (null when it isn't showing), so the big island can
     * grow out of it: [dx] is its centre's distance from the screen's middle, [top] its top
     * edge from the top of the screen, all in pixels.
     */
    data class MiniOrigin(val dx: Float, val top: Float, val width: Float, val height: Float)

    @Volatile var miniOrigin: MiniOrigin? = null

    /** When a big island that grew out of the mini island shrank back into it (elapsedRealtime). */
    @Volatile internal var returnToMiniAt = 0L

    @SuppressLint("StaticFieldLeak")
    private var mini: MiniIslandController? = null

    /**
     * Starts the mini island: the small pill around the front camera while something plays.
     * It then follows the music, the setting and the screen by itself. Safe to call often.
     */
    fun startMiniIsland(context: Context) = main.post {
        val c = mini ?: MiniIslandController(context.applicationContext).also { mini = it }
        c.start()
    }

    /** Where a pop-up should grow from: the pill by the camera, now or about to appear. */
    internal fun plannedMiniOrigin(): MiniOrigin? = miniOrigin ?: mini?.plannedOrigin()

    /** Re-checks whether the mini island should be up (after a settings change). */
    fun refreshMiniIsland(context: Context) = main.post { mini?.refresh() ?: startMiniIsland(context) }

    /**
     * Shows the mini island for a few seconds with sample music (or, with [sound], a pretend
     * message ding with an app icon), for Settings > Island > Try it.
     */
    fun previewMiniIsland(context: Context, sound: Boolean = false, message: Boolean = false, phone: Boolean = false) = main.post {
        val c = mini ?: MiniIslandController(context.applicationContext).also { mini = it }
        when {
            message -> c.previewMessage()
            phone -> c.previewPhone()
            sound -> c.previewSound()
            else -> c.preview()
        }
    }

    fun showCard(context: Context) = main.post {
        val c = card ?: CardController(context.applicationContext).also { card = it }
        c.show()
    }

    fun dismissAll() = main.post {
        island?.dismiss(animated = true)
        card?.dismiss(animated = true)
    }

    /** Opens Glint from an overlay (allowed: the overlay is visible and was just tapped). */
    @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
    fun openApp(context: Context, page: String? = null) {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        if (page != null) intent.putExtra(me.kavishdevar.librepods.presentation.navigation.AppLinks.EXTRA, page)
        try { context.startActivity(intent) } catch (e: Exception) { Log.w("GlintOverlays", "Couldn't open pro", e) }
    }

    /**
     * AirPods among Android's current audio outputs (by the saved address or the name): they're
     * there for sound even when the phone refuses pro's control link.
     */
    fun airPodsAudio(context: Context): Boolean = runCatching {
        // Other headphones chosen: the AirPods (and their heart chip) are out of the picture.
        if (!me.kavishdevar.librepods.services.DeviceChoice.followsAirPods(context)) return false
        val mac = context.getSharedPreferences("settings", Context.MODE_PRIVATE).getString("mac_address", null)?.takeIf { it.isNotBlank() }
        val audio = context.getSystemService(android.media.AudioManager::class.java)
        audio?.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS).orEmpty().any { d ->
            val bt = d.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                d.type == android.media.AudioDeviceInfo.TYPE_BLE_HEADSET ||
                d.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO
            bt && ((mac != null && d.address.equals(mac, ignoreCase = true)) ||
                d.productName?.toString()?.contains("AirPods", ignoreCase = true) == true)
        }
    }.getOrDefault(false)

    /**
     * The chosen device among Android's audio outputs: the AirPods check above, or for other
     * headphones their own address only (never "anything Bluetooth", so a car or speaker
     * doesn't bring the islands up).
     */
    fun chosenAudio(context: Context): Boolean {
        val chosen = me.kavishdevar.librepods.services.DeviceChoice.current(context)
        return if (chosen.isAirPods) airPodsAudio(context)
        else me.kavishdevar.librepods.services.HeadphoneLink.audioOutputHas(context, chosen.address)
    }

    /** Switches the AirPods to the next listening mode, in the same order as the Quick Settings tile. */
    fun cycleListeningMode(context: Context) {
        // Listening modes are an AirPods control: never sent while other headphones are chosen.
        if (!me.kavishdevar.librepods.services.DeviceChoice.followsAirPods(context)) return
        val service = me.kavishdevar.librepods.services.ServiceManager.getService() ?: return
        val offAllowed = context.getSharedPreferences("settings", Context.MODE_PRIVATE).getBoolean("off_listening_mode", true)
        val next = me.kavishdevar.librepods.services.ListeningModes.next(service.getANC(), offAllowed)
        runCatching {
            service.aacpManager.sendControlCommand(
                me.kavishdevar.librepods.bluetooth.AACPManager.Companion.ControlCommandIdentifiers.LISTENING_MODE.value, next
            )
        }.onFailure { Log.w("GlintOverlays", "Couldn't change listening mode", it) }
    }

    val isIslandShowing: Boolean get() = island?.isShowing == true
    val isCardShowing: Boolean get() = card?.isShowing == true

    /**
     * The status bar's height (where the phone draws its clock and icons, always above our
     * pop-ups). Asked three ways and the largest wins: from a background service Android can
     * answer the window question with 0 on newer versions, which put the island under the clock.
     */
    @SuppressLint("DiscouragedApi", "InternalInsetResource")
    internal fun statusBarHeight(context: Context): Int {
        val fromInsets = runCatching {
            val wm = context.getSystemService(WindowManager::class.java)
            wm.currentWindowMetrics.windowInsets.getInsets(WindowInsets.Type.statusBars() or WindowInsets.Type.displayCutout()).top
        }.getOrDefault(0)
        val id = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        val fromResources = if (id > 0) runCatching { context.resources.getDimensionPixelSize(id) }.getOrDefault(0) else 0
        val screenH = screenSize(context).height
        val fromCutout = cameraCutouts(context).filter { it.top < screenH / 4 }.maxOfOrNull { it.bottom } ?: 0
        return maxOf(fromInsets, fromResources, fromCutout)
    }

    /** The camera cutouts, from the window if Android says, otherwise from the display itself. */
    internal fun cameraCutouts(context: Context): List<android.graphics.Rect> {
        val fromWindow = runCatching {
            context.getSystemService(WindowManager::class.java).currentWindowMetrics.windowInsets.displayCutout?.boundingRects
        }.getOrNull().orEmpty()
        if (fromWindow.isNotEmpty()) return fromWindow
        return runCatching {
            context.getSystemService(android.hardware.display.DisplayManager::class.java)
                ?.getDisplay(android.view.Display.DEFAULT_DISPLAY)?.cutout?.boundingRects
        }.getOrNull().orEmpty()
    }

    internal fun navigationBarHeight(context: Context): Int {
        val wm = context.getSystemService(WindowManager::class.java)
        return wm.currentWindowMetrics.windowInsets.getInsets(WindowInsets.Type.navigationBars()).bottom
    }

    internal fun screenSize(context: Context): IntSize {
        val b = context.getSystemService(WindowManager::class.java).currentWindowMetrics.bounds
        return IntSize(b.width(), b.height())
    }
}
