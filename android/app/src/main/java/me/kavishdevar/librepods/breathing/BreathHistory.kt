package me.kavishdevar.librepods.breathing

import android.content.Context

/**
 * When each app last paused, and which saying came last. A file of its own: the service watches the
 * settings file and reloads when it changes, and a pause starting must never trigger that reload in
 * the middle of its own animation.
 *
 * First access reads a small file, so call [load] off the main thread. Writes are asynchronous.
 */
object BreathHistory {
    private const val FILE = "breath_seen"
    private const val APP = "app:"
    private const val SAYING = "saying"
    /** Entries older than this can never matter: the longest grace time is an hour. */
    private const val KEEP_MS = 24L * 60 * 60 * 1000

    private fun prefs(c: Context) = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun load(c: Context, wallNow: Long = System.currentTimeMillis()): Map<String, Long> {
        val saved = HashMap<String, Long>()
        for ((key, value) in prefs(c).all) {
            if (key.startsWith(APP) && value is Long && wallNow - value in 0..KEEP_MS) saved[key.removePrefix(APP)] = value
        }
        return saved
    }

    fun record(c: Context, pkg: String, wallNow: Long) { prefs(c).edit().putLong(APP + pkg, wallNow).apply() }

    fun lastSaying(c: Context): Int = prefs(c).getInt(SAYING, -1)

    fun saveSaying(c: Context, index: Int) { prefs(c).edit().putInt(SAYING, index).apply() }
}
