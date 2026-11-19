package me.kavishdevar.librepods

import android.content.pm.PackageInstaller
import me.kavishdevar.librepods.updates.InstallFailure
import org.junit.Assert.*
import org.junit.Test

class InstallFailureTest {
    @Test fun aBlockedInstallOnASamsungPhonePointsAtAutoBlockerWithTheRealPath() {
        val e = InstallFailure.explain(PackageInstaller.STATUS_FAILURE_BLOCKED, null, samsung = true)
        assertTrue(e.autoBlockerLikely)
        assertTrue(e.text.contains("Auto Blocker")); assertTrue(e.text.contains("Security and privacy"))
    }
    @Test fun aBlockedInstallElsewhereSaysCheckInstallUnknownApps() {
        val e = InstallFailure.explain(PackageInstaller.STATUS_FAILURE_BLOCKED, null, samsung = false)
        assertFalse(e.autoBlockerLikely); assertTrue(e.text.contains("Install unknown apps"))
    }
    @Test fun everyKnownFailureHasItsOwnPlainExplanationAndKeepsAndroidsOwnWords() {
        val statuses = listOf(PackageInstaller.STATUS_FAILURE_ABORTED, PackageInstaller.STATUS_FAILURE_INVALID, PackageInstaller.STATUS_FAILURE_CONFLICT,
            PackageInstaller.STATUS_FAILURE_STORAGE, PackageInstaller.STATUS_FAILURE_INCOMPATIBLE, PackageInstaller.STATUS_FAILURE_TIMEOUT)
        val texts = statuses.map { InstallFailure.explain(it, "INSTALL_FAILED_X", false).text }
        assertEquals("each is different", texts.size, texts.toSet().size)
        texts.forEach { assertTrue(it.contains("INSTALL_FAILED_X")) }
    }
    @Test fun anUnknownCodeStillSaysWhatItIsAndASamsungPhoneGetsTheAutoBlockerHint() {
        val e = InstallFailure.explain(999, "weird", true)
        assertTrue(e.text.contains("999")); assertTrue(e.autoBlockerLikely)
    }
}
