package me.kavishdevar.librepods.updates

import android.content.Context
import android.os.Build
import me.kavishdevar.librepods.BuildConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Everything worth knowing about an update that did not work, as plain text to copy. No personal data. */
object UpdateDiagnostics {
    fun text(c: Context): String {
        val prefs = Updater.prefs(c)
        val installer = runCatching { c.packageManager.getInstallSourceInfo(c.packageName).installingPackageName }.getOrNull()
        val clock = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        fun at(ms: Long) = if (ms > 0) clock.format(Date(ms)) else "never"
        return buildString {
            appendLine("pro ${BuildConfig.VERSION_CODE} (${Updater.channel(c)})")
            appendLine("${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Installed by: ${installer ?: "unknown"}; pro can install apps: ${c.packageManager.canRequestPackageInstalls()}; silent updates possible: ${UpdateInstaller.canInstallSilently(c)}")
            appendLine("Last check: ${at(prefs.getLong("checked", 0L))}")
            appendLine("Newer build found: ${Updater.release(c)?.version ?: "none"}; downloaded: ${Updater.apk(c).exists()}")
            if (prefs.contains("last_failure_status")) {
                appendLine("Last install failure: code ${prefs.getInt("last_failure_status", 0)} at ${at(prefs.getLong("last_failure_at", 0L))}")
                appendLine("Android's message: ${prefs.getString("last_failure_message", "").orEmpty().ifBlank { "(none)" }}")
            } else appendLine("Last install failure: none recorded")
            if (Build.MANUFACTURER.equals("samsung", ignoreCase = true)) appendLine("Samsung: Auto Blocker may block installs of updates from outside Google Play and Galaxy Store.")
        }.trim()
    }
}
