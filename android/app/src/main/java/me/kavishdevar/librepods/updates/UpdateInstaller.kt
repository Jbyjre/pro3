package me.kavishdevar.librepods.updates

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
import me.kavishdevar.librepods.breathing.BreathInteraction

object UpdateInstaller {
    const val CONFIRM_REQUEST = 7181
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
            Updater.prefs(c).edit().putInt("session", id).putBoolean("manual_session", !automatic).putString("result", "Installation started").commit()
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
