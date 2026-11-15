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
    private val api = "https://api.github.com/repos/Jbyjre/pro3/releases/tags/glint-latest"
    private fun release(bytes: ByteArray = "not an apk".toByteArray()) = UpdateRelease("stable",901,"1.0.0",
        "https://github.com/Jbyjre/pro3/releases/download/pro-stable-901/pro.apk",Updater.digest(bytes),bytes.size.toLong())
    private fun reply(url:String,text:String,status:Int=200) { replies[url]=status to text.toByteArray() }
    private fun publish(r: UpdateRelease=release()) {
        reply(api,"""{"draft":false,"prerelease":false,"assets":[{"name":"update.json","browser_download_url":"$metadata"}]}""")
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
        reply(api,"""{"draft":false,"prerelease":false,"assets":[]}""")
        Updater.check(c)
        assertTrue(Updater.state.value.message.contains("no update metadata"))
        assertEquals(901L,Updater.release(c)!!.version)
    }
    @Test fun rateLimitedAndWrongChannelCannotReplaceDiscovery() = runBlocking {
        publish(); Updater.check(c)
        reply(api,"{}",429); Updater.check(c)
        assertTrue(Updater.state.value.message.contains("limiting"))
        publish(); reply(metadata,release().copy(channel="preview").json()); Updater.check(c)
        assertEquals("stable",Updater.release(c)!!.channel)
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
    @Test fun unrelatedOrOldInstallCallbackCannotModifyPendingSession() {
        Updater.prefs(c).edit().putInt("session",7).commit()
        InstallReceiver().onReceive(c,Intent().putExtra(PackageInstaller.EXTRA_SESSION_ID,6)
            .putExtra(PackageInstaller.EXTRA_STATUS,PackageInstaller.STATUS_SUCCESS))
        assertEquals(7,Updater.prefs(c).getInt("session",-1))
    }
}
