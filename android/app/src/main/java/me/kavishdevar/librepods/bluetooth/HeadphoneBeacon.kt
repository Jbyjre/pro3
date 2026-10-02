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

package me.kavishdevar.librepods.bluetooth

/**
 * Apple's "nearby" Bluetooth beacon from single-battery headphones such as the Beats Solo 4.
 * Pure logic so it can be tested.
 *
 * Layout (Apple manufacturer data, company 0x004C, without the company id): byte 0 is the
 * message type 0x07, byte 1 its length 0x19, bytes 3-4 the model code (0x2520 = Beats Solo 4),
 * and the low half of byte 6 the battery in tens of percent (15 = unknown). This matches
 * CAPod's decoder and its Beats Solo 4 sample ("07 19 01 25 20 62 04 ..." = 40%).
 *
 * The beacon's address changes every few minutes and, unlike AirPods, pro has no key to prove
 * which headphones sent it. So a reading is only trusted through [Gate]: the right model,
 * very close to the phone, while the chosen headphones are connected, and with no second
 * headphones of the same model close by.
 */
object HeadphoneBeacon {
    data class Reading(val address: String, val model: Int, val battery: Int?, val rssi: Int, val at: Long)

    /** Decodes [data] (Apple manufacturer data) seen from [address], or null if it isn't a beacon. */
    fun decode(address: String, data: ByteArray, rssi: Int, at: Long): Reading? {
        if (data.size < 7) return null
        if (data[0].toInt() != 0x07 || (data[1].toInt() and 0xFF) != 0x19) return null
        val model = ((data[3].toInt() and 0xFF) shl 8) or (data[4].toInt() and 0xFF)
        val nibble = data[6].toInt() and 0x0F
        val battery = when (nibble) {
            in 0..10 -> nibble * 10
            in 11..14 -> 100
            else -> null
        }
        return Reading(address, model, battery, rssi, at)
    }

    /**
     * Decides which beacon readings belong to the chosen headphones. Not thread safe; call it
     * from one thread (the main thread).
     */
    class Gate(
        /** Weaker than this (in dBm) is too far away to be sure it's on your head or in your hand. */
        private val minRssi: Int = -62,
        /** How long another same-model beacon nearby keeps readings untrusted. */
        private val ambiguityMs: Long = 30_000L,
    ) {
        private val recent = LinkedHashMap<String, Reading>()

        /**
         * Returns the battery level to use from [r], or null to ignore it. [model] is the chosen
         * device's beacon model; [connected] whether it is connected right now.
         */
        fun accept(r: Reading, model: Int?, connected: Boolean): Int? {
            if (model == null || r.model != model) return null
            recent.entries.removeAll { r.at - it.value.at > ambiguityMs }
            recent[r.address] = r
            if (!connected || r.rssi < minRssi) return null
            // Two headphones of the same model close by (a friend's, a shop shelf): can't tell
            // which is yours, so trust neither until one leaves. Addresses rotate too, so an old
            // address of the same headphones looks like a second one for a little while; only
            // count others that are also close.
            val closeOthers = recent.values.count { it.address != r.address && it.rssi >= minRssi }
            if (closeOthers > 0) return null
            return r.battery
        }

        fun clear() = recent.clear()
    }
}
