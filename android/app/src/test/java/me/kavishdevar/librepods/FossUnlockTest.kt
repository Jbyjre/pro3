package me.kavishdevar.librepods

import android.app.Application
import android.content.Context
import me.kavishdevar.librepods.billing.FOSSBillingProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The sponsor unlock must still complete if the phone closed the app while the browser was open. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class FossUnlockTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val prefs get() = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    @Before
    fun clean() {
        prefs.edit().clear().commit()
    }

    @Test
    fun unlockFinishesAfterTheAppWasClosed() {
        // Sponsor page opened 6 s ago by a process that no longer exists.
        prefs.edit().putLong("glint_foss_unlock_started_at", System.currentTimeMillis() - 6_000).commit()
        val provider = FOSSBillingProvider(context)
        assertTrue(provider.isPremium.value)
        assertTrue(prefs.getBoolean("foss_upgraded", false))
        assertFalse(prefs.contains("glint_foss_unlock_started_at"))
    }

    @Test
    fun notUnlockedBeforeTheUsualDelay() {
        prefs.edit().putLong("glint_foss_unlock_started_at", System.currentTimeMillis() - 1_000).commit()
        assertFalse(FOSSBillingProvider(context).isPremium.value)
    }

    @Test
    fun unlockIsRemembered() {
        prefs.edit().putBoolean("foss_upgraded", true).commit()
        assertTrue(FOSSBillingProvider(context).isPremium.value)
    }

    @Test
    fun restoreUnlocksRightAway() {
        val provider = FOSSBillingProvider(context)
        assertFalse(provider.isPremium.value)
        provider.restorePurchases()
        assertTrue(provider.isPremium.value)
        assertTrue(prefs.getBoolean("foss_upgraded", false))
    }
}
