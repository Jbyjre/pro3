package me.kavishdevar.librepods.updates

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

/**
 * "An update is ready": a notification that opens pro, where one tap starts a fresh install while you are
 * there to confirm it. Used when Android would not let pro install in the background anyway.
 */
object UpdateNotice {
    private const val NOTIFICATION_ID = 7182

    /** Says it once per downloaded build, and only if notifications are allowed. Never throws. */
    fun readyToTap(c: Context) {
        runCatching {
            val release = Updater.release(c) ?: return
            val prefs = Updater.prefs(c)
            if (prefs.getLong("notified_version", 0L) == release.version) return
            val manager = c.getSystemService(NotificationManager::class.java)
            if (!manager.areNotificationsEnabled()) return
            manager.createNotificationChannel(NotificationChannel("updates", "App updates", NotificationManager.IMPORTANCE_DEFAULT))
            val open = PendingIntent.getActivity(c, NOTIFICATION_ID, (c.packageManager.getLaunchIntentForPackage(c.packageName) ?: Intent()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            manager.notify(NOTIFICATION_ID, NotificationCompat.Builder(c, "updates").setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("pro update ready").setContentText("Open pro > Phone > App updates and tap Update")
                .setContentIntent(open).setAutoCancel(true).build())
            prefs.edit().putLong("notified_version", release.version).apply()
        }
    }
}
