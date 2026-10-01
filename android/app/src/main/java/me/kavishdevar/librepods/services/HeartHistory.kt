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

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Every heart-rate session, kept on the phone: one small CSV per session (all its readings)
 * in `files/heart/sessions`, plus `files/heart/index.csv` with one summary line per session
 * so the history opens without reading every file. No limit on how many are kept; a year of
 * all-day measuring is a few tens of megabytes.
 *
 * Files are written to a temporary name and renamed, so a crash never leaves half a file.
 * Sessions saved by older versions (summary only, no readings) are moved in on first use.
 */
object HeartHistory {
    private const val OLD_PREF = "glint_hr_sessions"

    private val _version = MutableStateFlow(0)
    /** Goes up whenever a session is saved, deleted or restored, so screens can reload. */
    val version: StateFlow<Int> = _version.asStateFlow()

    @Volatile private var cache: List<HeartInsights.Session>? = null

    private fun root(context: Context) = File(context.filesDir, "heart")
    internal fun sessionsDir(context: Context) = File(root(context), "sessions").apply { mkdirs() }
    internal fun indexFile(context: Context) = File(root(context), "index.csv")
    internal fun sessionFile(context: Context, startMs: Long) = File(sessionsDir(context), "$startMs.csv")

    /** All sessions, newest first. */
    @Synchronized
    fun sessions(context: Context): List<HeartInsights.Session> {
        cache?.let { return it }
        migrate(context)
        val list = indexFile(context).takeIf { it.exists() }?.readLines().orEmpty()
            .mapNotNull { HeartInsights.Session.decode(it) }
            .sortedByDescending { it.startMs }
        cache = list
        return list
    }

    /** One session's readings (empty for sessions saved before readings were kept). */
    fun samples(context: Context, startMs: Long): List<HeartRate.Sample> {
        val f = sessionFile(context, startMs)
        if (!f.exists()) return emptyList()
        return HeartInsights.parseCsv(f.readText())
    }

    /**
     * Saves [samples] as a session (replacing an earlier save of the same session) if they
     * cover at least a minute. Returns the summary, or null if too short.
     */
    @Synchronized
    fun save(context: Context, samples: List<HeartRate.Sample>): HeartInsights.Session? {
        val summary = HeartInsights.summarize(samples) ?: return null
        writeAtomically(sessionFile(context, summary.startMs), HeartInsights.compactCsv(samples))
        put(context, summary)
        return summary
    }

    /** Adds sessions restored from a backup (readings as CSV text); skips ones already here. */
    @Synchronized
    fun restore(context: Context, startMs: Long, csv: String): Boolean {
        if (sessions(context).any { it.startMs == startMs && it.readings > 0 }) return false
        val samples = HeartInsights.parseCsv(csv)
        val summary = HeartInsights.summarize(samples) ?: return false
        writeAtomically(sessionFile(context, summary.startMs), HeartInsights.compactCsv(samples))
        put(context, summary)
        return true
    }

    @Synchronized
    fun delete(context: Context, startMs: Long) {
        sessionFile(context, startMs).delete()
        writeIndex(context, sessions(context).filter { it.startMs != startMs })
    }

    private fun put(context: Context, s: HeartInsights.Session) {
        writeIndex(context, (listOf(s) + sessions(context).filter { it.startMs != s.startMs }).sortedByDescending { it.startMs })
    }

    private fun writeIndex(context: Context, list: List<HeartInsights.Session>) {
        writeAtomically(indexFile(context), list.joinToString("\n") { it.encode() })
        cache = list
        _version.value++
    }

    /** Older versions kept only summaries in settings; move them into the index once. */
    private fun migrate(context: Context) {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val old = prefs.getString(OLD_PREF, null) ?: return
        val existing = indexFile(context).takeIf { it.exists() }?.readLines().orEmpty().mapNotNull { HeartInsights.Session.decode(it) }
        val moved = old.lines().mapNotNull { HeartInsights.Session.decode(it) }.filter { o -> existing.none { it.startMs == o.startMs } }
        writeAtomically(indexFile(context), (existing + moved).sortedByDescending { it.startMs }.joinToString("\n") { it.encode() })
        prefs.edit().remove(OLD_PREF).apply()
    }

    private fun writeAtomically(target: File, text: String) {
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(target)) { target.delete(); tmp.renameTo(target) }
    }

    /** For tests: forget the in-memory copy. */
    internal fun resetCache() { cache = null }
}
