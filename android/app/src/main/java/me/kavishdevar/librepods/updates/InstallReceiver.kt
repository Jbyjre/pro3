package me.kavishdevar.librepods.updates

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import androidx.core.app.NotificationCompat

/** Explicit, non-exported callback. Pending user action is kept for the next deliberate UI tap. */
class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, intent: Intent) {
        if (intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -2) != Updater.prefs(c).getInt("session", -1)) return
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = when (installResult(status)) {
            InstallResult.CONFIRM -> {
                val confirmation = intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                if (confirmation != null) {
                    PendingIntent.getActivity(c, UpdateInstaller.CONFIRM_REQUEST, confirmation,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                    Updater.prefs(c).edit().putString("confirmation_filter", confirmation.cloneFilter().toUri(0)).apply()
                    val foreground = runCatching { androidx.lifecycle.ProcessLifecycleOwner.get().lifecycle.currentState
                        .isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED) }.getOrDefault(false)
                    if (Updater.prefs(c).getBoolean("manual_session", false) && foreground &&
                        !me.kavishdevar.librepods.breathing.BreathInteraction.active) {
                        runCatching { c.startActivity(confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    }
                    val nm = c.getSystemService(NotificationManager::class.java)
                    nm.createNotificationChannel(NotificationChannel("updates", "App updates", NotificationManager.IMPORTANCE_DEFAULT))
                    runCatching { nm.notify(7181, NotificationCompat.Builder(c, "updates").setSmallIcon(android.R.drawable.stat_sys_download_done)
                        .setContentTitle("pro update ready").setContentText("Tap to confirm installation")
                        .setContentIntent(UpdateInstaller.confirmation(c)).setAutoCancel(true).build()) }
                }
                "Android needs confirmation. Tap Finish installation."
            }
            InstallResult.SUCCESS -> {
                Updater.prefs(c).edit().remove("session").apply()
                Updater.apk(c).delete()
                "Update installed"
            }
            InstallResult.RETRY -> {
                val sessionId = Updater.prefs(c).getInt("session", -1)
                if (sessionId >= 0) runCatching { c.packageManager.packageInstaller.abandonSession(sessionId) }
                Updater.prefs(c).edit().remove("session").apply()
                UpdateInstaller.confirmation(c)?.cancel()
                when (status) {
                    PackageInstaller.STATUS_FAILURE_STORAGE -> "Not enough storage to install. Free some space, then retry."
                    PackageInstaller.STATUS_FAILURE_BLOCKED -> "Android or Samsung blocked installation. Check Install unknown apps and Auto Blocker, then retry."
                    PackageInstaller.STATUS_FAILURE_ABORTED -> "Installation cancelled. The download is kept; retry when ready."
                    else -> "Installation failed ($status). The download is kept; retry."
                }
            }
        }
        Updater.prefs(c).edit().putString("result", message).apply()
        Updater.state.value = UpdateState(message, ready = Updater.apk(c).exists(), available = Updater.release(c) != null)
    }
}
