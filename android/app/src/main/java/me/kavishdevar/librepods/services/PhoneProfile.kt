/*
    pro, a fork of LibrePods - AirPods liberated from Apple's ecosystem
    Copyright (C) 2026 pro contributors

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

import android.content.Context
import android.os.Build
import android.os.PowerManager
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * pro tuned for Jake's two phones (Jake, 2026-10-15: "two toggle versions of the app in settings,
 * one meant for the Samsung S26 FE and one for the Google Pixel 6 Pro ... make it work better for
 * each phone"). Settings > Your phone. Automatic by default: it recognises the phone it's on.
 *
 * Only facts that were checked go into a tuning (sources in DECISIONS 49):
 * - Galaxy S26 FE (model SM-S741*): flat 6.7-inch FHD+ (1080 x 2340) screen, 120 Hz adaptive,
 *   centred punch-hole, Exynos 2500, 4,900 mAh, ships with One UI 9 (Android 17). Samsung puts
 *   unused apps to sleep unless they're excepted.
 * - Pixel 6 Pro: 6.7-inch QHD+ (1440 x 3120) LTPO screen, 10 to 120 Hz, curved into the left and
 *   right edges, centred punch-hole, Tensor, 5,003 mAh. Tensor-era Pixels run warm (AnandTech
 *   measured up to 45 °C next to the chip, with weak heat spreading).
 *
 * The camera's place and size are never taken from here: Android reports them exactly
 * ([MiniIslandRules.pickCamera]), on these two phones and any other.
 */
object PhoneProfile {
    enum class Phone(val label: String) {
        Other("This phone"),
        GalaxyS26FE("Galaxy S26 FE"),
        Pixel6Pro("Pixel 6 Pro"),
    }

    /** What's stored: follow the phone, or always use one phone's tuning. */
    enum class Choice { Auto, GalaxyS26FE, Pixel6Pro }

    const val PREF = "glint_phone_profile"

    /** Away from the screen's sides on a flat screen: the stretched Dynamic Island's original margin. */
    const val FLAT_EDGE_DP = 10f
    /**
     * Away from the sides on the Pixel 6 Pro's curved screen: the curve starts a few millimetres
     * in, and anything on it looks bent and is easy to touch by accident while holding the phone.
     */
    const val CURVED_EDGE_DP = 18f

    data class Tuning(
        val phone: Phone,
        /** How close the stretched Dynamic Island and the opened islands come to the screen's sides, dp. */
        val edgeDp: Float,
        /** Android's heat level (PowerManager.THERMAL_STATUS_*) from which pro starts saving. */
        val warmFrom: Int,
        /** Where Android hides the setting that keeps pro running in the background. */
        val keepAwake: KeepAwake,
    )

    enum class KeepAwake {
        /** Samsung's own battery page: "Never sleeping apps" / Unrestricted. */
        Samsung,
        /** Android's own: battery optimisation off (Pixel: App battery usage > Unrestricted). */
        Android,
    }

    /** Which of the two phones this is, from Android's own maker and model names. */
    fun detect(manufacturer: String, model: String): Phone = when {
        manufacturer.equals("google", ignoreCase = true) && model.trim().equals("Pixel 6 Pro", ignoreCase = true) -> Phone.Pixel6Pro
        manufacturer.equals("samsung", ignoreCase = true) && model.trim().uppercase().startsWith("SM-S741") -> Phone.GalaxyS26FE
        else -> Phone.Other
    }

    fun resolve(choice: Choice, detected: Phone): Phone = when (choice) {
        Choice.Auto -> detected
        Choice.GalaxyS26FE -> Phone.GalaxyS26FE
        Choice.Pixel6Pro -> Phone.Pixel6Pro
    }

    fun tuning(phone: Phone, samsung: Boolean = Build.MANUFACTURER.equals("samsung", ignoreCase = true)): Tuning = when (phone) {
        Phone.GalaxyS26FE -> Tuning(phone, FLAT_EDGE_DP, PowerManager.THERMAL_STATUS_MODERATE, KeepAwake.Samsung)
        Phone.Pixel6Pro -> Tuning(phone, CURVED_EDGE_DP, PowerManager.THERMAL_STATUS_LIGHT, KeepAwake.Android)
        // Any other phone: what pro did before profiles, unchanged.
        Phone.Other -> Tuning(phone, FLAT_EDGE_DP, PowerManager.THERMAL_STATUS_MODERATE, if (samsung) KeepAwake.Samsung else KeepAwake.Android)
    }

    /** The phone this really is (never changes while pro runs). */
    val detected: Phone by lazy { detect(Build.MANUFACTURER ?: "", Build.MODEL ?: "") }

    private val _choice = MutableStateFlow(Choice.Auto)
    val choice: StateFlow<Choice> = _choice.asStateFlow()

    private val _current = MutableStateFlow(tuning(Phone.Other, samsung = false))
    /** The tuning in use. Read anywhere; pure code takes its values as parameters. */
    val current: StateFlow<Tuning> = _current.asStateFlow()

    /** The edge margin in use, for drawing code that doesn't collect [current]. */
    val edgeDp: Float get() = _current.value.edgeDp
    /** The heat level in use, for [PowerPlan] and the glass. */
    val warmFrom: Int get() = _current.value.warmFrom

    @Volatile private var loaded = false

    /** Reads the stored choice once (cheap to call again). */
    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val stored = context.getSharedPreferences("settings", Context.MODE_PRIVATE).getInt(PREF, 0)
        apply(Choice.entries.getOrElse(stored) { Choice.Auto })
    }

    fun choose(context: Context, c: Choice) {
        // The new tuning first: saving the choice tells the Dynamic Island, which measures itself
        // again with the new margin (MiniIsland's settings listener).
        loaded = true
        apply(c)
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit { putInt(PREF, c.ordinal) }
        // The heat level may now count differently: let the battery plan and the glass look again.
        PowerPlan.recheck(context)
    }

    private fun apply(c: Choice) {
        _choice.value = c
        _current.value = tuning(resolve(c, detected))
    }

    /** Tests and screenshots. */
    internal fun preview(c: Choice, phone: Phone = Phone.Other) {
        _choice.value = c
        _current.value = tuning(if (c == Choice.Auto) phone else resolve(c, phone), samsung = false)
    }
}
