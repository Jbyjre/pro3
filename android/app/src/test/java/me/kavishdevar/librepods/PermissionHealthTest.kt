package me.kavishdevar.librepods

import android.Manifest
import android.app.Application
import android.content.ComponentName
import android.provider.Settings
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import me.kavishdevar.librepods.presentation.screens.PermissionHealth
import me.kavishdevar.librepods.presentation.screens.rememberPermissionHealth
import me.kavishdevar.librepods.services.MediaAccessService
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[36],application=Application::class)
class PermissionHealthTest {
    @get:Rule val rule=createComposeRule()
    @Test fun returningFromSettingsReflectsBothGrantsAndRevocations() {
        val c=RuntimeEnvironment.getApplication()
        val owner=object:LifecycleOwner {
            val registry=LifecycleRegistry(this)
            override val lifecycle:Lifecycle get()=registry
        }
        fun access(granted:Boolean) {
            ShadowSettings.setCanDrawOverlays(granted)
            shadowOf(c.packageManager).setCanRequestPackageInstalls(granted)
            Settings.Secure.putString(c.contentResolver,"enabled_notification_listeners",
                if (granted) ComponentName(c,MediaAccessService::class.java).flattenToString() else "")
            if (granted) shadowOf(c).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
            else shadowOf(c).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        }
        access(false)
        var observed:PermissionHealth?=null
        rule.runOnUiThread { owner.registry.currentState=Lifecycle.State.RESUMED }
        rule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                val health by rememberPermissionHealth()
                observed=health
                Text(health.toString())
            }
        }
        rule.waitUntil(5_000) { observed==PermissionHealth(false,false,false,false) }
        for (granted in listOf(true,false)) {
            rule.runOnUiThread { owner.registry.currentState=Lifecycle.State.STARTED;access(granted);owner.registry.currentState=Lifecycle.State.RESUMED }
            rule.waitUntil(5_000) { observed==PermissionHealth(granted,granted,granted,granted) }
        }
    }
}
