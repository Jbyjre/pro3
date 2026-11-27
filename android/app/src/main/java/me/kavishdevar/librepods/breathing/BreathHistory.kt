package me.kavishdevar.librepods.breathing

import android.content.Context

/**
 * When each app last paused, and which colour came last. A file of its own: the service watches the
 * settings file and reloads when it changes, and a pause starting must never trigger that reload in
 * the middle of its own animation.
 *
 * First access reads a small file, so call [load] off the main thread. Writes are asynchronous.
 */
object BreathHistory {
    private const val FILE = "breath_seen"
    private const val APP = "app:"
    private const val LAST_PAUSE = "last_pause"
    private const val PALETTE = "palette"
    /** Entries older than this can never matter: the longest cooldown is a day. */
    private const val KEEP_MS = 24L * 60 * 60 * 1000

    private fun prefs(c: Context) = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun load(c: Context, wallNow: Long = System.currentTimeMillis()): Map<String, Long> {
        val saved = HashMap<String, Long>()
        for ((key, value) in prefs(c).all) {
            if (key.startsWith(APP) && value is Long && wallNow - value in 0..KEEP_MS) saved[key.removePrefix(APP)] = value
        }
        return saved
    }

    /** When any pause most recently happened, so the cooldown survives a service restart. */
    fun lastPause(c: Context, wallNow: Long = System.currentTimeMillis()): Long? {
        val at = (prefs(c).all[LAST_PAUSE] as? Long)?.takeIf { wallNow - it in 0..KEEP_MS }
        return at ?: load(c, wallNow).values.maxOrNull()
    }

    fun record(c: Context, pkg: String, wallNow: Long) {
        prefs(c).edit().putLong(APP + pkg, wallNow).putLong(LAST_PAUSE, wallNow).apply()
    }

    /** The colour used by the last pause, so Random avoids repeating it and In order carries on from it. */
    fun lastPalette(c: Context): BreathPalette? = prefs(c).getString(PALETTE, null)?.let { key -> BreathPalette.entries.firstOrNull { it.key == key } }

    fun savePalette(c: Context, palette: BreathPalette) { prefs(c).edit().putString(PALETTE, palette.key).apply() }
}
