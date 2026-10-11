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
 * Tells you whether a change you made actually reached the AirPods, and keeps every screen
 * honest about it. Listening-mode changes are confirmed: the AirPods answer with the mode they
 * switched to (docs/AAP Definitions.md, "Changing Noise Control").
 *
 * Rebuilt 2026-10-11 (Jake: ANC "intermittent failures" and "inconsistent state"):
 * - Every sender goes through here (the Dynamic Island, the tile, the widget, the settings page,
 *   the service), not only the settings page, because [AACPManager] reports each send itself.
 * - The mode you tapped shows at once ("Switching to ..."). No answer after [RETRY_MS]: it is sent
 *   once more (the link often drops the first packet right after it wakes). Still nothing after
 *   [CONFIRM_MS], or the send failed: every screen goes back to the mode the AirPods are really in
 *   ([restore]) and says so, instead of showing a mode they aren't in.
 * - Tapping several modes quickly: the AirPods' answers to the earlier taps are on their way and
 *   are not errors; only the last tap counts. Their answer with the old mode (not yet switched) is
 *   not trusted until the time runs out either.
 * - A mode nobody asked for (a squeeze of the stem) is the AirPods' own choice and is shown.
 */
object CommandFeedback {
    enum class Kind { Pending, Done, Failed }

    data class Notice(val serial: Long, val kind: Kind, val text: String)

    const val LISTENING_MODE: Byte = 0x0D
    const val RETRY_MS = 1_200L
    const val CONFIRM_MS = 3_000L

    private val _notice = MutableStateFlow<Notice?>(null)
    val notice: StateFlow<Notice?> = _notice.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var serial = 0L
    /** The modes asked for since the AirPods last confirmed one, oldest first (the last one counts). */
    private val burst = ArrayList<Int>()
    /** The mode the AirPods last reported (their truth), or null before they've said. */
    private var confirmed: Int? = null
    private var retried = false
    private var timeout: Job? = null
    @Volatile var now: () -> Long = { System.currentTimeMillis() }

    /** Sends a listening mode again without changing anything else (set by the connection). */
    @Volatile var resend: ((Int) -> Boolean)? = null

    /** Puts every screen back on the AirPods' real mode (set by the connection). */
    @Volatile var restore: ((Int) -> Unit)? = null

    fun modeName(mode: Int): String = when (mode) {
        1 -> "Off"
        2 -> "Noise Cancellation"
        3 -> "Transparency"
        4 -> "Adaptive"
        else -> "that mode"
    }

    /** The mode a switch is waiting for, or null when nothing is pending. */
    val pending: Int? @Synchronized get() = burst.lastOrNull()

    private fun post(kind: Kind, text: String) { _notice.value = Notice(++serial, kind, text) }

    /**
     * About to send [value] for [identifier]; [shown] is what the screens showed before it. The
     * first tap of a burst remembers where to go back to if it fails.
     */
    @Synchronized
    fun sending(identifier: Byte, value: ByteArray, shown: ByteArray?) {
        if (identifier != LISTENING_MODE || value.isEmpty()) return
        if (burst.isEmpty() && confirmed == null) confirmed = shown?.firstOrNull()?.let { it.toInt() and 0xFF }
    }

    /**
     * A control command was handed to the connection ([sent] false: it couldn't be sent).
     * Decisions are made under the lock; sending again and restoring happen after it's released,
     * so a stalled Bluetooth write can never hold up a caller on the main thread.
     */
    fun sent(identifier: Byte, value: ByteArray, sent: Boolean) {
        decideSent(identifier, value, sent)?.let { back -> restore?.invoke(back) }
    }

    /** Returns a mode to restore, or null. */
    @Synchronized
    private fun decideSent(identifier: Byte, value: ByteArray, sent: Boolean): Int? {
        val listening = identifier == LISTENING_MODE && value.isNotEmpty()
        if (!sent) {
            timeout?.cancel()
            val wasMode = listening || burst.isNotEmpty()
            burst.clear()
            post(Kind.Failed, "Couldn't send: controls offline")
            return if (wasMode) confirmed else null
        }
        if (!listening) return null
        val mode = value[0].toInt() and 0xFF
        burst.remove(mode)
        burst.add(mode)
        retried = false
        post(Kind.Pending, "Switching to ${modeName(mode)}…")
        timeout?.cancel()
        timeout = scope.launch {
            delay(RETRY_MS)
            if (!retryDue(mode)) return@launch
            delay(CONFIRM_MS - RETRY_MS)
            timedOut(mode)
        }
        return null
    }

    /** No answer yet for [mode]: send it once more. Returns false when there's nothing left to wait for. */
    internal fun retryDue(mode: Int): Boolean {
        val again = synchronized(this) {
            if (burst.lastOrNull() != mode) return false
            if (retried) return true
            retried = true
            resend ?: return true
        }
        if (!again(mode)) {
            // The link is gone: say so now rather than after the full wait.
            timedOut(mode)
            return false
        }
        return true
    }

    internal fun timedOut(mode: Int) {
        val back = synchronized(this) {
            if (burst.lastOrNull() != mode) return
            burst.clear()
            post(Kind.Failed, "AirPods didn't switch to ${modeName(mode)}")
            confirmed
        }
        back?.let { restore?.invoke(it) }
    }

    /**
     * The AirPods reported a control value (their answer, or a change made on the stem). Returns
     * false when it should not be shown: an answer to an earlier tap while a later one is on its
     * way, or the old mode before the switch has landed.
     */
    @Synchronized
    fun received(identifier: Byte, value: ByteArray): Boolean {
        if (identifier != LISTENING_MODE || value.isEmpty()) return true
        val got = value[0].toInt() and 0xFF
        val want = burst.lastOrNull()
        if (want == null) { confirmed = got; return true }
        if (got == want) {
            confirmed = got
            burst.clear()
            timeout?.cancel()
            post(Kind.Done, "${modeName(got)} is on")
            return true
        }
        // An earlier tap's answer, or the old mode: the last tap is still on its way.
        if (got in burst || got == confirmed) return false
        // A mode nobody asked for in this burst: the AirPods chose it (or refused ours). Show the truth.
        val before = confirmed
        confirmed = got
        burst.clear()
        timeout?.cancel()
        post(Kind.Failed, if (before == null) "AirPods stayed in ${modeName(got)}" else "AirPods switched to ${modeName(got)} instead")
        return true
    }

    /** Hides the notice (the switch itself carries on). */
    fun clear() { _notice.value = null }

    /** Forgets everything (tests, and a new connection: the AirPods report their mode again). */
    @Synchronized
    fun reset() {
        timeout?.cancel()
        burst.clear()
        confirmed = null
        retried = false
        _notice.value = null
    }
}
