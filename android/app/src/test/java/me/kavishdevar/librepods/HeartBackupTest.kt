package me.kavishdevar.librepods

import me.kavishdevar.librepods.services.HeartBackup
import me.kavishdevar.librepods.services.HeartInsights
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HeartBackupTest {
    @Test fun remotePathIsReadableAndRoundTrips() {
        val start = 1_759_309_925_123L // 2025-10-01T09:12:05Z
        val path = HeartBackup.remotePath(start)
        assertEquals("heart/2025-10/2025-10-01T09-12-05Z_1759309925123.csv", path)
        assertEquals(start, HeartBackup.parseRemotePath(path))
        assertNull(HeartBackup.parseRemotePath("heart/index.csv"))
        assertNull(HeartBackup.parseRemotePath("README.md"))
    }

    @Test fun pendingSkipsSentAndThrottlesTheLiveSession() {
        fun s(start: Long, readings: Int) = HeartInsights.Session(start, start + 120_000, 70, 60, 80, readings, 0)
        val done = s(1_000, 100)
        val summaryOnly = s(2_000, 0)
        val live = s(3_000, 50)
        val now = 10_000_000L
        val sent = mutableMapOf(done.startMs to HeartBackup.Sent("a", 0, 100))
        // Already sent with the same readings: skipped. Summary-only: nothing to upload.
        assertEquals(listOf(live), HeartBackup.pending(listOf(done, summaryOnly, live), sent, liveStart = 3_000, now = now, force = false))
        // The live session was uploaded 5 minutes ago and has grown: wait (every 10 minutes), unless forced.
        sent[live.startMs] = HeartBackup.Sent("b", now - 300_000, 20)
        assertTrue(HeartBackup.pending(listOf(live), sent, 3_000, now, false).isEmpty())
        assertEquals(listOf(live), HeartBackup.pending(listOf(live), sent, 3_000, now, true))
        // Once it has ended (no longer live), it goes up straight away.
        assertEquals(listOf(live), HeartBackup.pending(listOf(live), sent, 0, now, false))
    }

    @Test fun sentRecordsRoundTrip() {
        val m = mapOf(5L to HeartBackup.Sent("abc", 9, 3), -1L to HeartBackup.Sent("def", 10, 0))
        assertEquals(m, HeartBackup.decodeSent(HeartBackup.encodeSent(m)))
        assertTrue(HeartBackup.decodeSent("").isEmpty())
    }

    @Test fun repositoryNames() {
        assertTrue(HeartBackup.validRepoName("glint-heart-backup"))
        assertTrue(HeartBackup.validRepoName("my_data.v2"))
        assertFalse(HeartBackup.validRepoName("a/b"))
        assertFalse(HeartBackup.validRepoName("has space"))
        assertFalse(HeartBackup.validRepoName(""))
        assertFalse(HeartBackup.validRepoName(".."))
    }

    @Test fun repoInputAcceptsNameOwnerSlashNameOrLink() {
        assertEquals(null to "pro3", HeartBackup.splitRepo("pro3"))
        assertEquals("Jbyjre" to "pro3", HeartBackup.splitRepo("Jbyjre/pro3"))
        assertEquals("Jbyjre" to "pro3", HeartBackup.splitRepo(" https://github.com/Jbyjre/pro3.git "))
        assertEquals("Jbyjre" to "pro3", HeartBackup.splitRepo("github.com/Jbyjre/pro3/"))
        assertEquals("pro3", HeartBackup.DEFAULT_REPO)
        assertEquals("heart-backup", HeartBackup.BRANCH)
    }
}
