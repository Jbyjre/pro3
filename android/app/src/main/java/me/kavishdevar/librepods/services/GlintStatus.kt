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

package me.kavishdevar.librepods.services

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * One in-process source of truth for "what is happening with the AirPods right now", read by
 * the app screens, overlays and notification. The older broadcast intents are still sent for
 * existing screens; this adds a state that can't be missed by a late subscriber.
 */
sealed interface LinkState {
    /** Nothing connected over Bluetooth audio. */
    data object Idle : LinkState

    data object BluetoothOff : LinkState

    /** Nearby-devices permission (BLUETOOTH_CONNECT) is missing. */
    data object NoPermission : LinkState

    /** Audio link is up and we are opening the control channel. */
    data class Connecting(val deviceName: String, val attempt: Int) : LinkState

    data class Connected(val deviceName: String) : LinkState

    /** Audio link up, control channel keeps failing; [lastError] is for the diagnostics screen. */
    data class Retrying(val deviceName: String, val attempt: Int, val lastError: String) : LinkState

    /** We stopped retrying (phone blocks the channel, or retry budget used up). */
    data class GaveUp(val deviceName: String, val lastError: String) : LinkState
}

object GlintStatus {
    private val _link = MutableStateFlow<LinkState>(LinkState.Idle)
    val link: StateFlow<LinkState> = _link.asStateFlow()

    /** Consecutive control-channel failures while the audio link was up, never reset by audio drop. */
    private val _consecutiveFailures = MutableStateFlow(0)
    val consecutiveFailures: StateFlow<Int> = _consecutiveFailures.asStateFlow()

    fun set(state: LinkState) {
        _link.value = state
    }

    fun recordFailure() = _consecutiveFailures.update { it + 1 }

    fun recordSuccess() {
        _consecutiveFailures.value = 0
    }
}

/** Intent actions understood by AirPodsService.onStartCommand. */
object GlintActions {
    const val DEVICE_APPEARED = "io.github.jbyjre.glint.DEVICE_APPEARED"
    const val RETRY_CONNECT = "io.github.jbyjre.glint.RETRY_CONNECT"
    const val EXTRA_ADDRESS = "address"
}
