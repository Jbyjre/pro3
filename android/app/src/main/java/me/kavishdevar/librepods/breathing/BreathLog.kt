package me.kavishdevar.librepods.breathing

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The last few decisions, in plain words, so "why did it not pause?" can be answered from the phone
 * instead of guessed. Kept in memory only (a new process starts empty) and never leaves the phone
 * unless you copy it yourself. It holds app names and window names, never screen text or messages.
 */
object BreathLog {
    data class Entry(val at: Long, val app: String, val text: String)

    private const val MAX = 60
    private val state = MutableStateFlow<List<Entry>>(emptyList())
    val entries = state.asStateFlow()

    fun add(app: String, text: String, at: Long = BreathAccess.wallClock()) {
        state.update { (listOf(Entry(at, app, text)) + it).take(MAX) }
    }

    fun clear() { state.value = emptyList() }

    fun asText(entries: List<Entry> = state.value): String {
        val clock = SimpleDateFormat("HH:mm:ss", Locale.US)
        return entries.joinToString("\n") { "${clock.format(Date(it.at))}  ${it.app}: ${it.text}" }
    }
}
