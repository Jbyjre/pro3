package me.kavishdevar.librepods

import android.app.Application
import android.content.pm.PackageInstaller
import me.kavishdevar.librepods.updates.*
import me.kavishdevar.librepods.presentation.overlays.*
import me.kavishdevar.librepods.services.FeatureMigration
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class UpdatePolicyTest {
    private fun manifest(channel: String="stable", version: Long=901) = UpdateRelease(channel,version,"1.0.0","https://github.com/Jbyjre/pro3/releases/download/pro-$channel-$version/pro.apk","a".repeat(64),100)
    @Test fun manifestRequiresMatchingChannelAndImmutableApkSource() {
        val r = manifest()
        assertEquals(r, UpdateRelease.parse(r.json(),"stable"))
        assertThrows(IllegalArgumentException::class.java) { UpdateRelease.parse(r.json(),"preview") }
        assertThrows(IllegalArgumentException::class.java) { UpdateRelease.parse(r.copy(url="https://example.com/pro.apk").json(),"stable") }
        assertThrows(IllegalArgumentException::class.java) { UpdateRelease.parse(r.copy(size=0).json(),"stable") }
    }
    @Test fun signedPackageVersionAndSignerMustMatch() {
        fun check(pkg: String="io.github.jbyjre.glint", v: Long=901, sign: Set<String> = setOf("ours")) =
            ApkPolicy.check(pkg,"io.github.jbyjre.glint",v,900,901,setOf("ours"),sign)
        check()
        assertThrows(IllegalArgumentException::class.java) { check(pkg="other") }
        assertThrows(IllegalArgumentException::class.java) { check(v=900) }
        assertThrows(IllegalArgumentException::class.java) { check(v=902) }
        assertThrows(IllegalArgumentException::class.java) { check(sign=setOf("someone else")) }
    }
    @Test fun pendingConfirmationIsNotSuccessAndFailuresAreRetryable() {
        assertEquals(InstallResult.CONFIRM, installResult(PackageInstaller.STATUS_PENDING_USER_ACTION))
        assertEquals(InstallResult.SUCCESS, installResult(PackageInstaller.STATUS_SUCCESS))
        for (status in listOf(PackageInstaller.STATUS_FAILURE_ABORTED,PackageInstaller.STATUS_FAILURE_STORAGE,PackageInstaller.STATUS_FAILURE_CONFLICT))
            assertEquals(InstallResult.RETRY, installResult(status))
    }
    @Test fun migrationKeepsHeadphoneAndPopupChoices() {
        val c = RuntimeEnvironment.getApplication()
        val p=c.getSharedPreferences("settings",0)
        p.edit().clear().putString("glint_di_mode","off").putBoolean("glint_mini_anytime",true)
            .putString("mac_address","saved").putBoolean("glint_island_music",false).commit()
        FeatureMigration.retireCamera(c)
        assertFalse(p.contains("glint_di_mode")); assertFalse(p.contains("glint_mini_anytime"))
        assertEquals("saved",p.getString("mac_address",null)); assertFalse(p.getBoolean("glint_island_music",true))
        FeatureMigration.retireCamera(c)
        assertEquals("saved",p.getString("mac_address",null))
    }
    @Test fun duplicatesDontExtendPopupButNewEventsCanShow() {
        val gate = PopupGate()
        assertTrue(gate.accept(IslandEvent.Connected,0))
        assertFalse(gate.accept(IslandEvent.Connected,100))
        assertFalse(gate.accept(IslandEvent.Connected,1999))
        assertTrue(gate.accept(IslandEvent.Music,100))
        assertTrue(gate.accept(IslandEvent.Connected,2000))
    }
    @Test fun removedAccessibilityComponentIsNotDeclared() {
        val c=RuntimeEnvironment.getApplication()
        assertThrows(android.content.pm.PackageManager.NameNotFoundException::class.java) {
            c.packageManager.getServiceInfo(android.content.ComponentName(c.packageName,"me.kavishdevar.librepods.services.IslandAccessService"),0)
        }
    }

}
