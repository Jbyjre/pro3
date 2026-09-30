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

/**
 * Decides when the bottom "case opened" card may appear.
 *
 * The lid bit comes from Bluetooth adverts, which Samsung phones throttle and batch.
 * A gap in adverts used to count as "lid closed", so the next advert ("open") showed
 * the card again at random. This gate only re-arms after the lid has really stayed
 * closed for [minClosedMs], ignores the lid while both buds are in ears, and never
 * shows the card twice within [cooldownMs].
 */
class CardGate(
    private val minClosedMs: Long = 10_000L,
    private val cooldownMs: Long = 60_000L,
) {
    private var closedSince: Long? = null
    private var armed = true
    private var lastShown: Long? = null

    /** Lid reported closed (a real advert or an advert gap). */
    fun onLidClosed(now: Long) {
        if (closedSince == null) closedSince = now
    }

    /**
     * Lid reported open. Returns true when the card should be shown now.
     * [bothInEar]: the lid bit means nothing when both buds are being worn.
     */
    fun onLidOpened(now: Long, bothInEar: Boolean): Boolean {
        val since = closedSince
        closedSince = null
        if (since != null && now - since >= minClosedMs) armed = true
        if (!armed || bothInEar) return false
        val last = lastShown
        if (last != null && now - last < cooldownMs) return false
        armed = false
        lastShown = now
        return true
    }

    /** The AirPods went away for good (disconnected): the next case-open is a fresh one. */
    fun reset() {
        closedSince = null
        armed = true
    }
}
