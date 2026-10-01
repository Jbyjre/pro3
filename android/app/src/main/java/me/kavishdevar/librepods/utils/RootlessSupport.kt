/*
    LibrePods - AirPods liberated from Apple’s ecosystem
    Copyright (C) 2025 LibrePods contributors

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

import android.content.SharedPreferences
import android.os.Build
import androidx.core.content.edit

fun isSupported(sharedPreferences: SharedPreferences): Boolean {
    if (Build.VERSION.SDK_INT >= 37) return true

    val isBypassFlagActive = sharedPreferences.getBoolean("bypass_device_check.v2", false)
    if (isBypassFlagActive) return true

    val isPixel = Build.MANUFACTURER.lowercase() == "google"
    val isOppoFamily = Build.MANUFACTURER.lowercase() in listOf("oneplus", "oppo", "realme")

    if (isPixel && Build.VERSION.SDK_INT == 36) {
        return Build.ID.startsWith("CP1A")
    } else if (isOppoFamily) {
        return Build.VERSION.SDK_INT >= 36
    }
    return false
}

/**
 * How likely this phone is to reach the AirPods control channel without root, in words a
 * non-technical person can act on. Pure so it can be unit tested; the service feeds it
 * what it has actually observed, which always wins over guesses from the Android version.
 */
enum class SupportLevel {
    /** We have connected on this phone before. */
    CONFIRMED,

    /** The Android version should carry Google's Bluetooth fix; the first connection will tell. */
    EXPECTED,

    /** Samsung before One UI 9 (Android 17): the fix is not on the phone yet. */
    NEEDS_ONE_UI_9,

    /** Unknown phone and version without the fix; root/Xposed or an update is needed. */
    NEEDS_UPDATE_OR_ROOT,

    /** The audio link was up, the control channel failed repeatedly, and it never worked here. */
    BLOCKED_ON_THIS_PHONE,
}

data class SupportVerdict(val level: SupportLevel, val title: String, val message: String) {
    val canConnect: Boolean
        get() = level == SupportLevel.CONFIRMED || level == SupportLevel.EXPECTED
}

fun supportVerdict(
    sdkInt: Int,
    manufacturer: String,
    buildId: String,
    bypassed: Boolean,
    xposedHookActive: Boolean,
    everConnected: Boolean,
    blockedObserved: Boolean,
): SupportVerdict {
    val maker = manufacturer.lowercase()
    val isSamsung = maker == "samsung"
    if (everConnected && !blockedObserved) {
        return SupportVerdict(
            SupportLevel.CONFIRMED,
            "Ready",
            "Your phone has connected to your AirPods before, so everything that doesn't need root is available.",
        )
    }
    if (blockedObserved) {
        return if (isSamsung && sdkInt < 37) SupportVerdict(
            SupportLevel.NEEDS_ONE_UI_9,
            "Waiting for One UI 9",
            "Your AirPods play audio, but this Samsung phone's Bluetooth is still blocking the extra channel Glint uses for battery, listening modes and ear detection. Samsung includes Google's fix starting with One UI 9 (Android 17). Once it reaches your phone (Settings, Software update), Glint will connect on its own.",
        ) else SupportVerdict(
            SupportLevel.BLOCKED_ON_THIS_PHONE,
            "Your phone is blocking the connection",
            "Your AirPods play audio, but the phone's Bluetooth keeps refusing the extra channel Glint needs. This can't be fixed from inside an app without root. A future system update may fix it; Glint keeps trying in the background and will pick it up automatically.",
        )
    }
    if (xposedHookActive || bypassed) {
        return SupportVerdict(
            SupportLevel.EXPECTED,
            "Should work",
            if (xposedHookActive) "The root (Xposed) Bluetooth fix is active." else "You chose to try anyway. Glint will tell you if the phone blocks the connection.",
        )
    }
    if (sdkInt >= 37) {
        return SupportVerdict(
            SupportLevel.EXPECTED,
            "Should work",
            "This phone runs Android 17 or newer, which should include Google's Bluetooth fix. Glint will confirm on the first connection" +
                if (isSamsung) " (Samsung has not published whether One UI 9 includes it, so this is checked live)." else ".",
        )
    }
    val isPixel = maker == "google"
    val isOppoFamily = maker in listOf("oneplus", "oppo", "realme")
    if ((isPixel && sdkInt == 36 && buildId.startsWith("CP1A")) || (isOppoFamily && sdkInt >= 36)) {
        return SupportVerdict(SupportLevel.EXPECTED, "Should work", "This phone's software includes the Bluetooth fix Glint needs.")
    }
    if (isSamsung) {
        return SupportVerdict(
            SupportLevel.NEEDS_ONE_UI_9,
            "Needs One UI 9",
            "Glint needs One UI 9 (Android 17) to talk to AirPods without root; you're on Android $sdkInt. Your AirPods still work as normal headphones.",
        )
    }
    return SupportVerdict(
        SupportLevel.NEEDS_UPDATE_OR_ROOT,
        "Needs an update or root",
        "This phone's Bluetooth needs Android 17 for Glint's features. Your AirPods still work as normal headphones.",
    )
}

fun bypassDeviceCheck(sharedPreferences: SharedPreferences) {
    sharedPreferences.edit{ putBoolean("bypass_device_check.v2", true) }
}

fun removeDeviceCheckBypass(sharedPreferences: SharedPreferences) {
    sharedPreferences.edit{ putBoolean("bypass_device_check.v2", false) }
}
