/*
    Glint, a fork of LibrePods - AirPods liberated from Apple's ecosystem
    Copyright (C) 2025 LibrePods contributors, 2026 Glint contributors

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

import java.util.Locale

/**
 * Decides whether a classic Bluetooth device that just connected is a pair of AirPods we
 * should open the Apple accessory protocol (AAP) channel to.
 *
 * Pure logic so it can be unit tested. The original code only accepted devices whose
 * cached SDP records already listed the AAP UUID. On a fresh connection Android often has
 * no cached UUIDs yet (or a stale list), which is one way AirPods Pro 3 ended up "connected
 * but not detected" (upstream issue 595). We now also accept the saved address and, as a
 * weaker signal, a device whose name looks like AirPods; a wrong guess only costs one
 * failed socket attempt, so it is cheap to try.
 */
object AirPodsDetection {
    const val AAP_UUID = "74ec2172-0bad-4d01-8f77-997b2be0722a"

    enum class Match {
        /** The device advertises the AAP service or is the AirPods we connected to before. */
        DEFINITE,

        /** Name suggests AirPods but we have no hard proof yet; attempt, then verify. */
        LIKELY,

        NONE
    }

    fun match(
        address: String?,
        name: String?,
        uuids: Collection<String>?,
        savedAddress: String?,
    ): Match {
        if (address.isNullOrBlank()) return Match.NONE
        if (uuids?.any { it.equals(AAP_UUID, ignoreCase = true) } == true) return Match.DEFINITE
        if (!savedAddress.isNullOrBlank() && address.equals(savedAddress, ignoreCase = true)) {
            return Match.DEFINITE
        }
        if (looksLikeAirPods(name)) return Match.LIKELY
        return Match.NONE
    }

    /** True for names like "AirPods Pro", "Jake's AirPods Pro 3", "AirPods Max". */
    fun looksLikeAirPods(name: String?): Boolean {
        if (name.isNullOrBlank()) return false
        val normalized = name.lowercase(Locale.ROOT).replace('’', '\'')
        return "airpods" in normalized || "airpod" in normalized
    }
}
