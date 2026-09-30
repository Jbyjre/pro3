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

package me.kavishdevar.librepods.billing

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.core.content.edit
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import me.kavishdevar.librepods.R

class FOSSBillingProvider(context: Context): BillingProvider {
    private val _isPremium = MutableStateFlow(false)
    override val isPremium: StateFlow<Boolean> = _isPremium

    private val _price = MutableStateFlow(context.getString(R.string.name_your_own_price))
    override val price: StateFlow<String> = _price

    private val sharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var purchaseJob: Job? = null

    init {
        queryPurchases()
    }

    override fun purchase(activity: Activity) {
        // Glint: remember when the sponsor page was opened, so the unlock still happens if
        // the phone closes the app in the background while the browser is open (Samsung
        // often does). queryPurchases() finishes it when the app comes back.
        sharedPreferences.edit { putLong(UNLOCK_STARTED_AT, System.currentTimeMillis()) }
        activity.startActivity(
            Intent(Intent.ACTION_VIEW, "https://github.com/sponsors/kavishdevar".toUri())
        )

        purchaseJob?.cancel()

        purchaseJob = scope.launch {
            delay(UNLOCK_DELAY_MS)
            unlock()
        }
    }

    override fun queryPurchases() {
        val startedAt = sharedPreferences.getLong(UNLOCK_STARTED_AT, 0L)
        if (!sharedPreferences.getBoolean("foss_upgraded", false) && startedAt > 0L &&
            System.currentTimeMillis() - startedAt >= UNLOCK_DELAY_MS
        ) {
            unlock()
            return
        }
        val stored = sharedPreferences.getBoolean("foss_upgraded", false)
        if (stored != _isPremium.value) {
            _isPremium.value = stored
        }
    }

    private fun unlock() {
        sharedPreferences.edit {
            putBoolean("foss_upgraded", true)
            remove(UNLOCK_STARTED_AT)
        }
        _isPremium.value = true
    }

    private companion object {
        const val UNLOCK_STARTED_AT = "glint_foss_unlock_started_at"
        const val UNLOCK_DELAY_MS = 5_000L
    }

    override fun restorePurchases() {
        unlock()
    }
}
