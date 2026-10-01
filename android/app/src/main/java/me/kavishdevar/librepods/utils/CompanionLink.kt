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

package me.kavishdevar.librepods.utils

import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.CompanionDeviceManager
import android.companion.ObservingDevicePresenceRequest
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import java.util.concurrent.Executor
import java.util.regex.Pattern

/**
 * Companion-device link between Glint and the AirPods.
 *
 * Once the user approves the system "Allow pro to access your AirPods" dialog, Android:
 *  - binds CompanionPresenceService whenever the AirPods connect, even if the app was killed
 *    (this is what survives Samsung's "sleeping apps" and a long idle phone), and
 *  - lets Glint start its foreground service from the background.
 */
object CompanionLink {
    private const val TAG = "CompanionLink"

    private fun manager(context: Context): CompanionDeviceManager? =
        if (context.packageManager.hasSystemFeature("android.software.companion_device_setup"))
            context.getSystemService(CompanionDeviceManager::class.java)
        else null

    fun isSupported(context: Context): Boolean = manager(context) != null

    fun associations(context: Context): List<AssociationInfo> = try {
        manager(context)?.myAssociations.orEmpty()
    } catch (e: Exception) {
        Log.w(TAG, "Could not read associations: ${e.message}")
        emptyList()
    }

    fun isLinked(context: Context, address: String? = null): Boolean {
        val list = associations(context)
        if (address.isNullOrBlank()) return list.isNotEmpty()
        return list.any { it.deviceMacAddress?.toString().equals(address, ignoreCase = true) }
    }

    fun associationFor(context: Context, address: String): AssociationInfo? =
        associations(context).firstOrNull { it.deviceMacAddress?.toString().equals(address, ignoreCase = true) }

    /**
     * Starts the system chooser. [onPending] receives the IntentSender to launch from an
     * Activity; [onLinked] is called with the address once the user approves.
     */
    fun requestLink(
        context: Context,
        knownAddress: String?,
        executor: Executor,
        onPending: (IntentSender) -> Unit,
        onLinked: (String?) -> Unit,
        onError: (String) -> Unit,
    ) {
        val cdm = manager(context) ?: return onError("This phone doesn't support companion devices.")
        val filter = BluetoothDeviceFilter.Builder().apply {
            if (!knownAddress.isNullOrBlank()) setAddress(knownAddress)
            else setNamePattern(Pattern.compile("(?i).*airpods.*"))
        }.build()
        val request = AssociationRequest.Builder()
            .addDeviceFilter(filter)
            .setSingleDevice(!knownAddress.isNullOrBlank())
            .build()
        try {
            cdm.associate(request, executor, object : CompanionDeviceManager.Callback() {
                override fun onAssociationPending(intentSender: IntentSender) = onPending(intentSender)

                override fun onAssociationCreated(associationInfo: AssociationInfo) {
                    val address = associationInfo.deviceMacAddress?.toString()
                    if (address != null) ensureObserving(context, address)
                    onLinked(address)
                }

                override fun onFailure(error: CharSequence?) {
                    onError(error?.toString() ?: "Linking was cancelled.")
                }
            })
        } catch (e: Exception) {
            onError(e.message ?: "Linking failed.")
        }
    }

    /** Ask Android to wake us when this device connects. Safe to call repeatedly. */
    fun ensureObserving(context: Context, address: String) {
        val cdm = manager(context) ?: return
        val association = associationFor(context, address) ?: return
        try {
            if (Build.VERSION.SDK_INT >= 36) {
                cdm.startObservingDevicePresence(
                    ObservingDevicePresenceRequest.Builder().setAssociationId(association.id).build()
                )
            } else {
                @Suppress("DEPRECATION")
                cdm.startObservingDevicePresence(address)
            }
            Log.d(TAG, "Observing presence of $address")
        } catch (e: Exception) {
            Log.w(TAG, "startObservingDevicePresence failed: ${e.message}")
        }
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true

    /** System dialog that sets the app's battery usage to "Unrestricted". */
    fun batteryOptimizationIntent(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))

    fun appDetailsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))

    /**
     * Samsung's "Background usage limits" screen (Never sleeping apps). The component name is
     * not a public API; callers must fall back to [appDetailsIntent] if it fails to start.
     */
    fun samsungBackgroundLimitsIntent(): Intent = Intent().setClassName(
        "com.samsung.android.lool",
        "com.samsung.android.sm.battery.ui.BatteryActivity"
    )

    val isSamsung: Boolean get() = Build.MANUFACTURER.equals("samsung", ignoreCase = true)
}
