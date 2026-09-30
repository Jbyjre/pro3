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

import android.companion.AssociationInfo
import android.companion.CompanionDeviceService
import android.companion.DevicePresenceEvent
import android.content.Intent
import android.util.Log
import androidx.annotation.RequiresApi
import me.kavishdevar.librepods.utils.CompanionLink

/**
 * Android binds this service when the linked AirPods connect, even if Glint's process was
 * killed. We only hand the event to AirPodsService, which does the real work.
 */
class CompanionPresenceService : CompanionDeviceService() {

    override fun onDeviceAppeared(associationInfo: AssociationInfo) {
        wake(associationInfo.deviceMacAddress?.toString())
    }

    @Deprecated("Replaced by the AssociationInfo overload on Android 13+")
    override fun onDeviceAppeared(address: String) {
        wake(address)
    }

    @RequiresApi(36)
    override fun onDevicePresenceEvent(event: DevicePresenceEvent) {
        if (event.event == DevicePresenceEvent.EVENT_BT_CONNECTED ||
            event.event == DevicePresenceEvent.EVENT_BLE_APPEARED
        ) {
            val address = CompanionLink.associations(this)
                .firstOrNull { it.id == event.associationId }
                ?.deviceMacAddress?.toString()
            wake(address)
        }
    }

    private fun wake(address: String?) {
        Log.d("CompanionPresence", "AirPods appeared: $address")
        try {
            startForegroundService(
                Intent(this, AirPodsService::class.java)
                    .setAction(GlintActions.DEVICE_APPEARED)
                    .putExtra(GlintActions.EXTRA_ADDRESS, address)
            )
        } catch (e: Exception) {
            Log.w("CompanionPresence", "Could not start AirPodsService: ${e.message}")
        }
    }
}
