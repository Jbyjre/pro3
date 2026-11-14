package me.kavishdevar.librepods.updates

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.android.apksig.ApkVerifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.kavishdevar.librepods.BuildConfig
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class UpdateState(val message: String = "Check for a new build", val busy: Boolean = false, val ready: Boolean = false, val available: Boolean = false)

object Updater {
    val state = MutableStateFlow(UpdateState())
    internal val mutex = Mutex()
    fun prefs(c: Context) = c.getSharedPreferences("updates", Context.MODE_PRIVATE)
    fun channel(c: Context) = prefs(c).getString("channel", BuildConfig.UPDATE_CHANNEL).let { if (it == "preview") "preview" else "stable" }
    fun dir(c: Context) = File(c.filesDir, "updates").apply { mkdirs() }
    fun apk(c: Context) = File(dir(c), "update.apk")
    fun installed(c: Context) = c.packageManager.getPackageInfo(c.packageName, 0).longVersionCode
    fun release(c: Context): UpdateRelease? = runCatching {
        UpdateRelease.parse(File(dir(c), "release.json").readText(), channel(c))
    }.getOrNull()?.takeIf { it.version > installed(c) }
    fun restore(c: Context) {
        val r = release(c)
        state.value = UpdateState(prefs(c).getString("result", null) ?: if (r != null) "Build ${r.version} available" else "Check for a new build",
            ready = r != null && apk(c).exists(), available = r != null)
    }
    suspend fun check(c: Context) = withContext(Dispatchers.IO) { mutex.withLock {
        state.value = state.value.copy(message = "Checking…", busy = true)
        runCatching {
            val channel = channel(c)
            val tag = if (channel == "stable") "glint-latest" else "glint-preview"
            val releaseJson = JSONObject(readJson("https://api.github.com/repos/Jbyjre/pro3/releases/tags/$tag"))
            require(!releaseJson.optBoolean("draft") && releaseJson.optBoolean("prerelease") == (channel == "preview")) { "Wrong release channel" }
            val assets = releaseJson.getJSONArray("assets")
            val metadata = (0 until assets.length()).map { assets.getJSONObject(it) }.firstOrNull { it.getString("name") == "update.json" }
                ?: error("This release has no update metadata yet. Try again later.")
            val url = metadata.getString("browser_download_url")
            require(url == "https://github.com/Jbyjre/pro3/releases/download/$tag/update.json") { "Unexpected metadata source" }
            val found = UpdateRelease.parse(readJson(url), channel)
            val prior = release(c)
            if (prior?.sha256 != found.sha256) apk(c).delete()
            File(dir(c), "release.json.part").apply { writeText(found.json()); require(renameTo(File(dir(c), "release.json"))) }
            prefs(c).edit().putLong("checked", System.currentTimeMillis()).remove("result").apply()
            val newer = found.version > installed(c)
            state.value = UpdateState(if (newer) "Build ${found.version} available" else "You're up to date · build ${installed(c)}",
                ready = newer && apk(c).exists(), available = newer)
        }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it; state.value = state.value.copy(message = friendly(it), busy = false) }
    } }
    suspend fun download(c: Context) = withContext(Dispatchers.IO) { mutex.withLock {
        state.value = state.value.copy(message = "Downloading…", busy = true)
        val part = File(dir(c), "update.apk.part")
        runCatching {
            val r = release(c) ?: error("Check for an update first")
            require(dir(c).usableSpace > r.size * 3 + 20_000_000) { "Not enough storage. Free some space and retry." }
            val connection = connect(r.url)
            try {
                require(connection.responseCode == 200) { "Download unavailable (${connection.responseCode}). Check again and retry." }
                var total = 0L
                connection.inputStream.use { input -> part.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        kotlinx.coroutines.currentCoroutineContext().ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        total += n
                        require(total <= r.size) { "Download size changed. Check again." }
                        output.write(buffer, 0, n)
                    }
                } }
                require(total == r.size) { "Download interrupted. Retry." }
            } finally { connection.disconnect() }
            validate(c, part, r)
            check(part.renameTo(apk(c))) { "Couldn't save the download" }
            state.value = UpdateState("Ready · build ${r.version}", ready = true, available = true)
        }.onFailure {
            part.delete()
            if (it is kotlinx.coroutines.CancellationException) throw it
            state.value = state.value.copy(message = friendly(it), busy = false, ready = false)
        }
    } }
    fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    fun validate(c: Context, file: File, r: UpdateRelease) {
        require(file.length() == r.size) { "Incomplete APK" }
        val hash = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input -> val b = ByteArray(65536); while (true) { val n = input.read(b); if (n < 0) break; hash.update(b, 0, n) } }
        require(hash.digest().joinToString("") { "%02x".format(it) } == r.sha256) { "Download verification failed. Check again." }
        val verified = ApkVerifier.Builder(file).setMinCheckedPlatformVersion(Build.VERSION.SDK_INT).build().verify()
        require(verified.isVerified) { "APK signature verification failed" }
        val pm = c.packageManager
        val archive = pm.getPackageArchiveInfo(file.path, 0) ?: error("Invalid APK")
        val current = pm.getPackageInfo(c.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        ApkPolicy.check(archive.packageName, c.packageName, archive.longVersionCode, current.longVersionCode, r.version,
            current.signingInfo?.apkContentsSigners.orEmpty().map { digest(it.toByteArray()) }.toSet(),
            verified.signerCertificates.map { digest(it.encoded) }.toSet())
    }
    internal var connectionFactory: (String) -> HttpURLConnection = { URL(it).openConnection() as HttpURLConnection }
    private fun connect(url: String): HttpURLConnection = connectionFactory(url).apply {
        connectTimeout = 15_000; readTimeout = 30_000
        setRequestProperty("User-Agent", "pro/${BuildConfig.VERSION_CODE}")
        setRequestProperty("Accept", "application/vnd.github+json")
    }
    private fun readJson(url: String): String {
        val c = connect(url)
        try {
            val status = c.responseCode
            if (status == 403 || status == 429) error("GitHub is limiting checks. Try again later.")
            require(status == 200) { "Release unavailable ($status). Try again later." }
            return c.inputStream.use { String(it.readNBytes(1_000_001)).also { text -> require(text.length <= 1_000_000) } }
        } finally { c.disconnect() }
    }
    private fun friendly(t: Throwable): String = when (t) {
        is java.io.IOException -> "Couldn't reach the release. Your downloaded update is kept; retry when online."
        else -> t.message ?: "Update failed. Try again."
    }
}
