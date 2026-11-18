package me.kavishdevar.librepods

import android.app.Application
import android.content.Intent
import android.content.pm.PackageInstaller
import kotlinx.coroutines.runBlocking
import me.kavishdevar.librepods.updates.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** The real discovery/download and result receiver, against controlled HTTP/file fixtures. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class UpdateFlowTest {
    private val c get() = RuntimeEnvironment.getApplication()
    private val original = Updater.connectionFactory
    private val replies = mutableMapOf<String, Pair<Int,ByteArray>>()
    private val metadata = "https://github.com/Jbyjre/pro3/releases/download/glint-latest/update.json"
    private fun release(bytes: ByteArray = "not an apk".toByteArray()) = UpdateRelease("stable",901,"1.0.0",
        "https://github.com/Jbyjre/pro3/releases/download/pro-stable-901/pro.apk",Updater.digest(bytes),bytes.size.toLong())
    private fun reply(url:String,text:String,status:Int=200) { replies[url]=status to text.toByteArray() }
    private fun publish(r: UpdateRelease=release()) {
        reply(metadata,r.json())
    }
    @Before fun setup() {
        Updater.prefs(c).edit().clear().commit()
        Updater.dir(c).listFiles()?.forEach { it.delete() }
        Updater.state.value=UpdateState()
        Updater.connectionFactory = { url ->
            val (status,body) = replies[url] ?: throw IOException("Offline")
            object : HttpURLConnection(URL(url)) {
                override fun disconnect() {}
                override fun usingProxy()=false
                override fun connect() {}
                override fun getResponseCode()=status
                override fun getInputStream()=ByteArrayInputStream(body)
            }
        }
    }
    @After fun cleanup() { Updater.connectionFactory=original; UpdateInstaller.cancel(c) }
    @Test fun newerBuildIsFoundDespiteUnchangedVersionNameAndMissingOrOfflineReleaseKeepsIt() = runBlocking {
        publish()
        Updater.check(c)
        assertTrue(Updater.state.value.available)
        assertEquals(901L,Updater.release(c)!!.version)
        replies.clear()
        Updater.check(c)
        assertTrue(Updater.state.value.available)
        assertEquals(901L,Updater.release(c)!!.version)
        reply(metadata, "", 404)
        Updater.check(c)
        assertTrue(Updater.state.value.message.contains("404"))
        assertEquals(901L,Updater.release(c)!!.version)
    }
    @Test fun rateLimitedAndWrongChannelCannotReplaceDiscovery() = runBlocking {
        publish(); Updater.check(c)
        reply(metadata,"{}",429); Updater.check(c)
        assertTrue(Updater.state.value.message.contains("limiting"))
        publish(); reply(metadata,release().copy(channel="preview").json()); Updater.check(c)
        assertEquals("stable",Updater.release(c)!!.channel)
    }
    @Test fun cancelledDownloadRestoresRetryAction() = runBlocking {
        publish()
        Updater.check(c)
        Updater.connectionFactory = { throw kotlinx.coroutines.CancellationException() }
        try {
            Updater.download(c)
            fail("Expected cancellation")
        } catch (_: kotlinx.coroutines.CancellationException) {
            assertFalse(Updater.state.value.busy)
            assertTrue(Updater.state.value.available)
        }
    }
    @Test fun blockedJobNetworkPreservesOfflineDownloadWithoutUsingDefaultRoute() = runBlocking {
        publish()
        Updater.check(c)
        Updater.apk(c).writeText("retained")
        Updater.state.value = UpdateState() // Android recreated the process
        val failedCheck = Updater.check(c) { throw IOException("Job network lost") }
        assertTrue(failedCheck.isFailure)
        assertTrue(Updater.state.value.ready)
        var requested: String? = null
        Updater.download(c) { url ->
            requested = url
            throw IOException("Job network lost")
        }
        assertEquals(release().url, requested)
        assertEquals("retained", Updater.apk(c).readText())
        assertTrue(Updater.state.value.ready)
    }

    @Test fun republishedMetadataInvalidatesOldDownload() = runBlocking {
        publish(); Updater.check(c)
        Updater.apk(c).writeText("old")
        publish(release("changed".toByteArray())); Updater.check(c)
        assertFalse(Updater.apk(c).exists())
    }
    @Test fun interruptedAndInvalidApksAreNeverReadyAndRetryCleansPartialFile() = runBlocking {
        val r=release()
        publish(r); Updater.check(c)
        replies[r.url]=200 to byteArrayOf(1)
        Updater.download(c)
        assertFalse(Updater.state.value.ready)
        assertFalse(java.io.File(Updater.dir(c),"update.apk.part").exists())
        replies[r.url]=404 to byteArrayOf()
        Updater.download(c)
        assertFalse(Updater.apk(c).exists())
        replies[r.url]=200 to "not an apk".toByteArray()
        Updater.download(c)
        assertFalse(Updater.state.value.ready)
        assertFalse(Updater.apk(c).exists())
    }
    @Test fun resultReceiverRetainsPendingConfirmationAndDownloadForFailedInstall() {
        Updater.prefs(c).edit().putInt("session",7).commit()
        Updater.apk(c).writeText("retained")
        val pending=Intent().putExtra(PackageInstaller.EXTRA_SESSION_ID,7)
            .putExtra(PackageInstaller.EXTRA_STATUS,PackageInstaller.STATUS_PENDING_USER_ACTION)
            .putExtra(Intent.EXTRA_INTENT,Intent("system.confirm.install"))
        InstallReceiver().onReceive(c,pending)
        assertEquals(7,Updater.prefs(c).getInt("session",-1))
        assertNotNull(UpdateInstaller.confirmation(c))
        assertTrue(Updater.apk(c).exists())
        InstallReceiver().onReceive(c,Intent().putExtra(PackageInstaller.EXTRA_SESSION_ID,7)
            .putExtra(PackageInstaller.EXTRA_STATUS,PackageInstaller.STATUS_FAILURE_STORAGE))
        assertEquals(-1,Updater.prefs(c).getInt("session",-1))
        assertTrue(Updater.apk(c).exists())
    }
    @Test fun expiredSessionRestoresTheCachedUpdateAction() = runBlocking {
        publish()
        Updater.check(c)
        Updater.apk(c).writeText("retained")
        Updater.prefs(c).edit().putInt("session", 7).commit()
        UpdateInstaller.refresh(c)
        assertEquals(-1, Updater.prefs(c).getInt("session", -1))
        assertTrue(Updater.state.value.ready)
    }
    @Test fun unrelatedOrOldInstallCallbackCannotModifyPendingSession() {
        Updater.prefs(c).edit().putInt("session",7).commit()
        InstallReceiver().onReceive(c,Intent().putExtra(PackageInstaller.EXTRA_SESSION_ID,6)
            .putExtra(PackageInstaller.EXTRA_STATUS,PackageInstaller.STATUS_SUCCESS))
        assertEquals(7,Updater.prefs(c).getInt("session",-1))
    }

    private val sixHours = Updater.CHECK_ON_OPEN_MS
    @Test fun openingProChecksAtMostEverySixHoursAndTheClockGoingBackDoesNotBlockIt() = runBlocking {
        publish()
        assertTrue(Updater.checkOnOpen(c, unmetered = { false }))
        assertTrue(Updater.state.value.available)
        assertFalse("just checked", Updater.checkOnOpen(c, unmetered = { false }))
        val checked = Updater.prefs(c).getLong("checked", 0)
        assertTrue(checked > 0)
        assertFalse(Updater.checkOnOpen(c, now = checked + sixHours - 1, unmetered = { false }))
        assertTrue(Updater.checkOnOpen(c, now = checked + sixHours, unmetered = { false }))
        assertTrue("clock set backwards", Updater.checkOnOpen(c, now = checked - 5_000, unmetered = { false }))
    }
    @Test fun aFailedCheckOnOpenIsNotCountedSoTheNextOpenTriesAgain() = runBlocking {
        assertTrue("offline attempt", Updater.checkOnOpen(c, unmetered = { false }))
        assertEquals(0L, Updater.prefs(c).getLong("checked", 0))
        publish()
        assertTrue(Updater.checkOnOpen(c, unmetered = { false }))
        assertTrue(Updater.state.value.available)
    }
    @Test fun openingProNeverChecksWhenAutomaticChecksAreOff() = runBlocking {
        publish()
        Updater.prefs(c).edit().putBoolean("checks", false).commit()
        assertFalse(Updater.checkOnOpen(c, unmetered = { true }))
        assertFalse(Updater.state.value.available)
    }
    @Test fun openingProStartsADownloadOnlyOnAnUnmeteredConnectionWhenDownloadsAreOn() = runBlocking {
        val bytes = "not an apk".toByteArray()
        publish(release(bytes)); reply(release(bytes).url, "not an apk")
        val base = Updater.connectionFactory
        val opened = ArrayList<String>()
        Updater.connectionFactory = { url -> opened += url; base(url) }
        assertTrue(Updater.checkOnOpen(c, unmetered = { false }))
        assertEquals("metered: only the tiny metadata file is read", listOf(metadata), opened)
        opened.clear(); Updater.prefs(c).edit().remove("checked").putBoolean("download", false).commit()
        assertTrue(Updater.checkOnOpen(c, unmetered = { true }))
        assertEquals("downloads off: only the metadata file is read", listOf(metadata), opened)
        opened.clear(); Updater.prefs(c).edit().remove("checked").putBoolean("download", true).commit()
        assertTrue(Updater.checkOnOpen(c, unmetered = { true }))
        assertEquals("unmetered and on: the update file is fetched too", listOf(metadata, release(bytes).url), opened)
    }
}
