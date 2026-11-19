package me.kavishdevar.librepods.updates

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
import me.kavishdevar.librepods.breathing.BreathInteraction
import androidx.core.content.FileProvider

object UpdateInstaller {
    const val CONFIRM_REQUEST = 7181
    /** A confirmation Android has waited this long for is treated as stale: start a fresh install instead. */
    const val STALE_MS = 10 * 60_000L

    /**
     * True only when pro installed itself last time. Android lets an installer update an app without asking only
     * when that installer is the app's installer of record, so only then is an install while you are away worth
     * starting. Otherwise it would sit waiting for a tap and go stale, which is what made "didn't work".
     */
    fun canInstallSilently(c: Context): Boolean = android.os.Build.VERSION.SDK_INT >= 30 && runCatching {
        c.packageManager.getInstallSourceInfo(c.packageName).installingPackageName == c.packageName
    }.getOrDefault(false)

    fun sessionIsStale(c: Context, now: Long = System.currentTimeMillis()): Boolean {
        val started = Updater.prefs(c).getLong("session_at", 0L)
        return started > 0 && (now - started < 0 || now - started >= STALE_MS)
    }

    /**
     * Another way in: Android's own installer for the same, already verified file. Works when the session route
     * fails for reasons of its own. Call off the main thread; start the returned intent from the screen.
     */
    suspend fun anotherWay(c: Context): Intent? = withContext(Dispatchers.IO) {
        runCatching {
            val release = Updater.release(c) ?: return@runCatching null
            val file = Updater.apk(c)
            if (!file.exists()) return@runCatching null
            Updater.validate(c, file, release)
            val uri = FileProvider.getUriForFile(c, "${c.packageName}.provider", file)
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }.getOrNull()
    }
    suspend fun install(c: Context, automatic: Boolean = false) = withContext(Dispatchers.IO) { Updater.mutex.withLock {
        runCatching {
            check(!BreathInteraction.active) { "Finish the breathing pause first" }
            check(c.packageManager.canRequestPackageInstalls()) { "Allow pro to install updates first" }
            val pi = c.packageManager.packageInstaller
            val old = Updater.prefs(c).getInt("session", -1)
            if (old >= 0 && pi.getSessionInfo(old) != null) error("An installation is already pending. Finish it or cancel and retry.")
            val release = Updater.release(c) ?: error("Check for an update first")
            val file = Updater.apk(c)
            Updater.validate(c, file, release) // Re-check just before every install, off main.
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(c.packageName)
                setSize(file.length())
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                setPackageSource(PackageInstaller.PACKAGE_SOURCE_DOWNLOADED_FILE)
            }
            val id = pi.createSession(params)
            Updater.prefs(c).edit().putInt("session", id).putLong("session_at", System.currentTimeMillis()).putBoolean("manual_session", !automatic).putString("result", "Installation started").commit()
            try {
                pi.openSession(id).use { session ->
                    file.inputStream().use { input -> session.openWrite("base.apk", 0, file.length()).use { output -> input.copyTo(output); session.fsync(output) } }
                    val callback = PendingIntent.getBroadcast(c, id,
                        Intent(c, InstallReceiver::class.java).setAction("${c.packageName}.INSTALL_RESULT"),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
                    check(!BreathInteraction.active) { "Finish the breathing pause first" }
                    if (automatic && android.os.Build.VERSION.SDK_INT >= 34) {
                        val constraints = PackageInstaller.InstallConstraints.Builder()
                            .setAppNotForegroundRequired().setAppNotInteractingRequired()
                            .setDeviceIdleRequired().setNotInCallRequired().build()
                        pi.commitSessionAfterInstallConstraintsAreMet(id, callback.intentSender, constraints, 60_000L)
                    } else session.commit(callback.intentSender)
                }
                Updater.state.value = UpdateState("Installation started. Android may ask for confirmation.")
            } catch (e: Exception) { pi.abandonSession(id); Updater.prefs(c).edit().remove("session").apply(); throw e }
        }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it; Updater.state.value = Updater.state.value.copy(message = it.message ?: "Couldn't install. Retry.", busy = false) }
    }
    }
    fun confirmation(c: Context): PendingIntent? = prefsIntent(c)?.let { PendingIntent.getActivity(c, CONFIRM_REQUEST, it, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE) }
    private fun prefsIntent(c: Context): Intent? = Updater.prefs(c).getString("confirmation_filter", null)?.let { runCatching { Intent.parseUri(it, 0) }.getOrNull() }
    suspend fun refresh(c: Context) = withContext(Dispatchers.IO) {
        Updater.mutex.withLock {
            val prefs = Updater.prefs(c)
            val session = prefs.getInt("session", -1)
            if (session >= 0 && c.packageManager.packageInstaller.getSessionInfo(session) == null) {
                confirmation(c)?.cancel()
                prefs.edit().remove("session").remove("confirmation_filter").remove("result").apply()
                if (Updater.release(c) == null) Updater.apk(c).delete()
            }
            Updater.restore(c)
        }
    }
    fun cancel(c: Context) {
        val id = Updater.prefs(c).getInt("session", -1)
        if (id >= 0) runCatching { c.packageManager.packageInstaller.abandonSession(id) }
        confirmation(c)?.cancel()
        Updater.prefs(c).edit().remove("session").remove("result").apply()
        Updater.restore(c)
    }
}
