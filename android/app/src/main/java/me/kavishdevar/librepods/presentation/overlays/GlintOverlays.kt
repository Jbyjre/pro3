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

    fun showIsland(context: Context, event: IslandEvent) = main.post {
        val c = island ?: IslandController(context.applicationContext).also { island = it }
        c.show(event)
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
    fun openApp(context: Context) {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        try { context.startActivity(intent) } catch (e: Exception) { Log.w("GlintOverlays", "Couldn't open Glint", e) }
    }

    val isIslandShowing: Boolean get() = island?.isShowing == true
    val isCardShowing: Boolean get() = card?.isShowing == true

    internal fun statusBarHeight(context: Context): Int {
        val wm = context.getSystemService(WindowManager::class.java)
        return wm.currentWindowMetrics.windowInsets.getInsets(WindowInsets.Type.statusBars() or WindowInsets.Type.displayCutout()).top
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
