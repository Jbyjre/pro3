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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Tells you whether a change you made actually reached the AirPods.
 *
 * Listening-mode changes are confirmed: the AirPods answer with the mode they switched to
 * (docs/AAP Definitions.md, "Changing Noise Control"). If no answer comes within
 * [CONFIRM_MS], or the change couldn't even be sent (controls not connected), a notice says
 * so plainly, instead of the screen silently showing a mode the AirPods aren't in.
 */
object CommandFeedback {
    enum class Kind { Pending, Done, Failed }

    data class Notice(val serial: Long, val kind: Kind, val text: String)

    const val LISTENING_MODE: Byte = 0x0D
    const val CONFIRM_MS = 3_000L

    private val _notice = MutableStateFlow<Notice?>(null)
    val notice: StateFlow<Notice?> = _notice.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var serial = 0L
    private var pendingMode: Int? = null
    private var timeout: Job? = null
    @Volatile var now: () -> Long = { System.currentTimeMillis() }

    fun modeName(mode: Int): String = when (mode) {
        1 -> "Off"
        2 -> "Noise Cancellation"
        3 -> "Transparency"
        4 -> "Adaptive"
        else -> "that mode"
    }

    private fun post(kind: Kind, text: String) { _notice.value = Notice(++serial, kind, text) }

    /** A control command was handed to the connection ([sent] false: it couldn't be sent). */
    @Synchronized
    fun sent(identifier: Byte, value: ByteArray, sent: Boolean) {
        if (!sent) {
            timeout?.cancel(); pendingMode = null
            post(Kind.Failed, "Couldn't send that: the AirPods' controls aren't connected.")
            return
        }
        if (identifier != LISTENING_MODE || value.isEmpty()) return
        val mode = value[0].toInt() and 0xFF
        pendingMode = mode
        post(Kind.Pending, "Switching to ${modeName(mode)}…")
        timeout?.cancel()
        timeout = scope.launch {
            delay(CONFIRM_MS)
            timedOut(mode)
        }
    }

    @Synchronized
    internal fun timedOut(mode: Int) {
        if (pendingMode != mode) return
        pendingMode = null
        post(Kind.Failed, "Your AirPods didn't confirm ${modeName(mode)}. They may be connected to another device, or the controls dropped.")
    }

    /** The AirPods reported a control value (their answer, or a change made on the stem). */
    @Synchronized
    fun received(identifier: Byte, value: ByteArray) {
        if (identifier != LISTENING_MODE || value.isEmpty()) return
        val want = pendingMode ?: return
        val got = value[0].toInt() and 0xFF
        pendingMode = null
        timeout?.cancel()
        if (got == want) post(Kind.Done, "${modeName(got)} is on")
        else post(Kind.Failed, "Your AirPods stayed in ${modeName(got)}. ${modeName(want)} may be turned off in Press and hold settings.")
    }

    fun clear() { _notice.value = null }
}
