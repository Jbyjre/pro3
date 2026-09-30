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

package me.kavishdevar.librepods.billing

import android.app.Activity
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Glint is Jake's personal app: every feature is included, nothing is sold or unlocked.
 * This build (FOSS flavour) therefore always reports full access, and the purchase and
 * restore calls do nothing.
 */
@Suppress("UNUSED_PARAMETER")
class FOSSBillingProvider(context: Context) : BillingProvider {
    override val isPremium: StateFlow<Boolean> = MutableStateFlow(true)
    override val price: StateFlow<String> = MutableStateFlow("")

    override fun purchase(activity: Activity) {}
    override fun queryPurchases() {}
    override fun restorePurchases() {}
}
