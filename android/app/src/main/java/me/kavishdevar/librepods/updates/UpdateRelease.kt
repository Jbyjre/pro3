package me.kavishdevar.librepods.updates

import org.json.JSONObject
import java.net.URI

/** Discovery is advisory. The APK's signed package, signer and version are authoritative. */
data class UpdateRelease(val channel: String, val version: Long, val name: String, val url: String, val sha256: String, val size: Long) {
    fun json() = JSONObject().put("channel", channel).put("versionCode", version).put("versionName", name)
        .put("url", url).put("sha256", sha256).put("size", size).toString()
    companion object {
        fun parse(json: String, channel: String): UpdateRelease {
            val o = JSONObject(json)
            val r = UpdateRelease(o.getString("channel"), o.getLong("versionCode"), o.getString("versionName"),
                o.getString("url"), o.getString("sha256").lowercase(), o.getLong("size"))
            require(r.channel == channel && channel in setOf("stable", "preview")) { "Wrong update channel" }
            require(r.version > 0 && r.size in 1..150_000_000 && Regex("[0-9a-f]{64}").matches(r.sha256)) { "Invalid update metadata" }
            val u = URI(r.url)
            require(u.scheme == "https" && u.host == "github.com" && u.userInfo == null && u.query == null && u.fragment == null &&
                Regex("/Jbyjre/pro3/releases/download/pro-${channel}-[0-9]+/pro(?:-preview)?\\.apk").matches(u.path)) { "Unexpected APK source" }
            return r
        }
    }
}

internal object ApkPolicy {
    fun check(packageName: String, expectedPackage: String, version: Long, installed: Long, manifestVersion: Long,
              installedSigners: Set<String>, verifiedSigners: Set<String>) {
        require(packageName == expectedPackage) { "This APK is for a different app" }
        require(version > installed && version == manifestVersion) { "This APK is not a newer matching build" }
        // Same signer required. Deliberately conservative: no unverified certificate rotation.
        require(installedSigners.isNotEmpty() && installedSigners == verifiedSigners) { "This APK has a different signing identity" }
    }
}

internal enum class InstallResult { CONFIRM, SUCCESS, RETRY }
internal fun installResult(status: Int) = when (status) {
    android.content.pm.PackageInstaller.STATUS_PENDING_USER_ACTION -> InstallResult.CONFIRM
    android.content.pm.PackageInstaller.STATUS_SUCCESS -> InstallResult.SUCCESS
    else -> InstallResult.RETRY
}
