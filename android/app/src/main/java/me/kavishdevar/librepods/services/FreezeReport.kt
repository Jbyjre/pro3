/*
    Glint, a fork of LibrePods - AirPods liberated from Apple's ecosystem
    Copyright (C) 2026 Glint contributors

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>.
*/

package me.kavishdevar.librepods.services

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.compose.runtime.Immutable
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * If pro ever freezes ("pro isn't responding") or crashes, Android keeps a short record of why
 * the app's process stopped, and for a freeze it keeps the main thread's stack: the exact line
 * of code that was stuck. pro reads that record the next time it starts, saves a readable copy,
 * and Troubleshooting can copy it so it can be pasted to Claude. Nothing is sent anywhere.
 *
 * The record is Android's own (ApplicationExitInfo), so it survives the very freeze it describes.
 */
object FreezeReport {
    private const val TAG = "FreezeReport"
    private const val PREF_SEEN = "glint_freeze_seen_ts"
    private const val PREF_UNREAD = "glint_freeze_unread"
    private const val KEEP = 5
    private const val MAX_TRACE_BYTES = 256 * 1024

    @Immutable
    data class Report(val at: Long, val label: String, val text: String)

    // ---- Pure parts (unit-tested) ----

    /** A short name for the exit reasons worth recording (numbers checked in the Android SDK), null for the rest. */
    fun labelFor(reason: Int): String? = when (reason) {
        6 -> "Stopped responding"            // REASON_ANR
        4 -> "Crashed"                       // REASON_CRASH
        5 -> "Crashed (native code)"         // REASON_CRASH_NATIVE
        7 -> "Failed to start"               // REASON_INITIALIZATION_FAILURE
        9 -> "Used too much of the phone"    // REASON_EXCESSIVE_RESOURCE_USAGE
        else -> null
    }

    /**
     * The main thread's part of a freeze trace: from its heading ("main" prio=...) to the next
     * blank line, at most [maxLines] lines. When it can't be found, the first [maxLines] lines.
     */
    fun mainThread(trace: String, maxLines: Int = 60): String {
        val lines = trace.lines()
        val start = lines.indexOfFirst { it.startsWith("\"main\" prio=") }
        if (start < 0) return lines.take(maxLines).joinToString("\n").trim()
        val end = (start until lines.size).firstOrNull { it > start && lines[it].isBlank() } ?: lines.size
        return lines.subList(start, minOf(end, start + maxLines)).joinToString("\n")
    }

    /** The saved text: when, what, Android's own words, which phone, and where the main thread was stuck. */
    fun compose(
        at: Long, label: String, description: String?, process: String?, phone: String, appVersion: String, trace: String?,
    ): String {
        val whenText = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(at))
        return buildString {
            appendLine("pro report")
            appendLine("When: $whenText")
            appendLine("What: $label")
            if (!description.isNullOrBlank()) appendLine("Android says: $description")
            if (!process.isNullOrBlank()) appendLine("Process: $process")
            appendLine("App: $appVersion")
            appendLine("Phone: $phone")
            if (!trace.isNullOrBlank()) {
                appendLine()
                appendLine("Main thread when it happened:")
                appendLine(mainThread(trace))
            }
        }.trimEnd()
    }

    // ---- Android side ----

    private fun dir(context: Context) = File(context.filesDir, "freeze")
    private fun prefs(context: Context) = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /**
     * Reads Android's record of why pro last stopped and saves a report for anything new that
     * counts. Quick, but not for the main thread (it asks Android and may read a trace). Safe to call at every start.
     */
    fun check(context: Context) {
        val app = context.applicationContext
        val infos = runCatching {
            app.getSystemService(ActivityManager::class.java).getHistoricalProcessExitReasons(app.packageName, 0, 10)
        }.getOrNull().orEmpty()
        val p = prefs(app)
        val seen = p.getLong(PREF_SEEN, 0L)
        val fresh = infos.filter { it.timestamp > seen && labelFor(it.reason) != null }
        // The very first time, only look forward: older records aren't news.
        if (seen == 0L) {
            p.edit().putLong(PREF_SEEN, infos.maxOfOrNull { it.timestamp } ?: 1L).apply()
            return
        }
        if (fresh.isEmpty()) return
        val version = runCatching { app.packageManager.getPackageInfo(app.packageName, 0).versionName }.getOrNull() ?: "?"
        val phone = "${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        val folder = dir(app).apply { mkdirs() }
        for (info in fresh.sortedBy { it.timestamp }) {
            val trace = if (info.reason == ApplicationExitInfo.REASON_ANR) readTrace(info) else null
            val text = compose(info.timestamp, labelFor(info.reason)!!, info.description, info.processName, phone, version, trace)
            runCatching { File(folder, "freeze-${info.timestamp}.txt").writeText(text) }
                .onFailure { Log.w(TAG, "Couldn't save the report", it) }
        }
        // Keep only the latest few.
        folder.listFiles()?.sortedByDescending { it.name }?.drop(KEEP)?.forEach { it.delete() }
        p.edit().putLong(PREF_SEEN, fresh.maxOf { it.timestamp }).putBoolean(PREF_UNREAD, true).apply()
        Log.w(TAG, "Saved ${fresh.size} new report(s): ${fresh.map { labelFor(it.reason) }}")
    }

    private fun readTrace(info: ApplicationExitInfo): String? = runCatching {
        info.traceInputStream?.use { s -> String(s.readNBytes(MAX_TRACE_BYTES), Charsets.UTF_8) }
    }.getOrNull()

    /** The saved reports, newest first. */
    fun reports(context: Context): List<Report> =
        dir(context).listFiles()?.filter { it.name.startsWith("freeze-") }?.sortedByDescending { it.name }?.mapNotNull { f ->
            runCatching {
                val text = f.readText()
                val at = f.name.removePrefix("freeze-").removeSuffix(".txt").toLongOrNull() ?: f.lastModified()
                Report(at, text.lineSequence().firstOrNull { it.startsWith("What: ") }?.removePrefix("What: ") ?: "Report", text)
            }.getOrNull()
        }.orEmpty()

    fun hasUnread(context: Context): Boolean = prefs(context).getBoolean(PREF_UNREAD, false)

    fun markRead(context: Context) = prefs(context).edit().putBoolean(PREF_UNREAD, false).apply()

    fun clear(context: Context) {
        dir(context).listFiles()?.forEach { it.delete() }
        markRead(context)
    }
}
