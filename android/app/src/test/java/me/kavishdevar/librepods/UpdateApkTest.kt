package me.kavishdevar.librepods

import android.app.Application
import android.content.pm.PackageInfo
import android.content.pm.Signature
import android.content.pm.SigningInfo
import com.android.apksig.ApkVerifier
import me.kavishdevar.librepods.updates.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import java.io.File

/** Real signed APK bytes and the production verifier; only PackageManager's binder boundary is mocked. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class UpdateApkTest {
    private val c get() = RuntimeEnvironment.getApplication()
    private lateinit var file: File
    private lateinit var release: UpdateRelease
    @Before fun fixture() {
        file=File(c.cacheDir,"signed-fixture.apk")
        file.writeBytes(javaClass.getResourceAsStream("/update-valid.apk")!!.use { it.readBytes() })
        val signer=ApkVerifier.Builder(file).build().verify().signerCertificates.single()
        val signing=Shadow.newInstanceOf(SigningInfo::class.java)
        shadowOf(signing).setSignatures(arrayOf(Signature(signer.encoded)))
        val current=c.packageManager.getPackageInfo(c.packageName,0)
        current.setLongVersionCode(900)
        current.signingInfo=signing
        shadowOf(c.packageManager).installPackage(current)
        shadowOf(c.packageManager).setPackageArchiveInfo(file.path,PackageInfo().apply { packageName=c.packageName; setLongVersionCode(901) })
        release=UpdateRelease("stable",901,"1.0.0","https://github.com/Jbyjre/pro3/releases/download/pro-stable-901/pro.apk",
            Updater.digest(file.readBytes()),file.length())
    }
    @Test fun actualSignedApkPassesEveryProductionCheck() { Updater.validate(c,file,release) }
    @Test fun tamperingFailsEvenWithRecomputedDownloadHash() {
        val bytes=file.readBytes();bytes[100]=(bytes[100].toInt() xor 1).toByte();file.writeBytes(bytes)
        assertThrows(Exception::class.java) { Updater.validate(c,file,release.copy(sha256=Updater.digest(bytes))) }
    }
    @Test fun correctlySignedApkFromAnotherSigningIdentityIsRejected() {
        val current=c.packageManager.getPackageInfo(c.packageName,0)
        val signing=Shadow.newInstanceOf(SigningInfo::class.java)
        shadowOf(signing).setSignatures(arrayOf(Signature(byteArrayOf(1,2,3))))
        current.signingInfo=signing;shadowOf(c.packageManager).installPackage(current)
        assertThrows(IllegalArgumentException::class.java) { Updater.validate(c,file,release) }
    }
    @Test fun correctlySignedOldApkIsRejected() {
        val current=c.packageManager.getPackageInfo(c.packageName,0)
        current.setLongVersionCode(901);shadowOf(c.packageManager).installPackage(current)
        assertThrows(IllegalArgumentException::class.java) { Updater.validate(c,file,release) }
    }
}
