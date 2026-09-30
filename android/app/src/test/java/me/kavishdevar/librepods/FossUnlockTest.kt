package me.kavishdevar.librepods

import android.app.Application
import android.content.Context
import me.kavishdevar.librepods.billing.FOSSBillingProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Glint includes every feature: nothing is locked, before or after any purchase call. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class FossUnlockTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun everythingIsIncludedFromTheStart() {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().clear().commit()
        val provider = FOSSBillingProvider(context)
        assertTrue(provider.isPremium.value)
        provider.queryPurchases()
        provider.restorePurchases()
        assertTrue(provider.isPremium.value)
    }

    @Test
    fun screenStateStartsUnlocked() {
        assertTrue(me.kavishdevar.librepods.presentation.viewmodel.AirPodsUiState().isPremium)
    }
}
