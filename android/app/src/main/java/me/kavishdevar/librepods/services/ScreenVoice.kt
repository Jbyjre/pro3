/*
    pro, a fork of LibrePods - AirPods liberated from Apple's ecosystem
    Copyright (C) 2026 pro contributors

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
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Listen: the article on the screen read aloud, into your AirPods (or whatever is playing sound),
 * with the phone's own voice (Android's text-to-speech, on the phone). Started from the opened
 * island ([ScreenInsights.Insight.body]); tap again to stop. The voice engine is only woken for
 * this and let go as soon as it finishes, so it costs nothing the rest of the time.
 */
object ScreenVoice {
    private const val TAG = "ScreenVoice"
    private const val PART = "pro-listen-"

    private val _speaking = MutableStateFlow(false)
    /** True while an article is being read aloud. */
    val speaking: StateFlow<Boolean> = _speaking.asStateFlow()

    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    @Volatile private var last = ""
    private var audio: AudioManager? = null
    private var focus: AudioFocusRequest? = null
    private val SPEECH = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()

    /** Reads [text] aloud from the start (stopping anything it was reading). */
    fun speak(context: Context, text: String) {
        main.post {
            stopNow()
            val parts = ScreenVoiceRules.parts(text, TextToSpeech.getMaxSpeechInputLength())
            if (parts.isEmpty()) return@post
            _speaking.value = true
            audio = context.applicationContext.getSystemService(AudioManager::class.java)
            var engine: TextToSpeech? = null
            // Android can answer before the engine is even handed back (no voice installed), so the
            // answer is always taken on the next turn of the main thread, once [tts] is set.
            engine = TextToSpeech(context.applicationContext) { status -> main.post { ready(engine, status, parts) } }
            tts = engine
        }
    }

    private fun ready(e: TextToSpeech?, status: Int, parts: List<String>) {
        // The answer can come after a stop (or a newer Listen): only the current engine speaks.
        if (e == null || tts !== e) return
        if (status != TextToSpeech.SUCCESS) { Log.w(TAG, "No voice engine ($status)"); stopNow(); return }
        e.setAudioAttributes(SPEECH)
        e.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) { if (utteranceId == last) main.post { if (tts === e) stopNow() } }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { main.post { if (tts === e) stopNow() } }
            override fun onError(utteranceId: String?, errorCode: Int) { main.post { if (tts === e) stopNow() } }
        })
        // The music pauses while it reads, and carries on after (as Android does for directions).
        focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(SPEECH)
            .setOnAudioFocusChangeListener { change -> if (change == AudioManager.AUDIOFOCUS_LOSS) main.post { if (tts === e) stopNow() } }
            .build().also { f -> audio?.requestAudioFocus(f) }
        last = "$PART${parts.lastIndex}"
        parts.forEachIndexed { i, p -> e.speak(p, TextToSpeech.QUEUE_ADD, null, "$PART$i") }
    }

    /** Stops reading and lets the voice engine go. */
    fun stop() { main.post { stopNow() } }

    private fun stopNow() {
        tts?.let { runCatching { it.stop(); it.shutdown() } }
        tts = null
        focus?.let { f -> runCatching { audio?.abandonAudioFocusRequest(f) } }
        focus = null
        _speaking.value = false
    }
}

/** How a long article is handed to the voice: in pieces the engine accepts, cut between sentences. Unit-tested. */
object ScreenVoiceRules {
    fun parts(text: String, max: Int): List<String> {
        val limit = (max - 100).coerceAtLeast(200)
        val out = ArrayList<String>()
        val sb = StringBuilder()
        // Sentences (and paragraphs) are the pieces; one longer than the limit is cut at a space.
        val sentences = text.split(Regex("""(?<=[.!?])\s+|\n+""")).map { it.trim() }.filter { it.isNotEmpty() }
        for (raw in sentences) {
            var s = raw
            while (s.length > limit) {
                val cut = s.lastIndexOf(' ', limit).takeIf { it > limit / 2 } ?: limit
                flush(sb, out); out += s.substring(0, cut).trim(); s = s.substring(cut).trim()
            }
            if (sb.length + s.length + 1 > limit) flush(sb, out)
            if (sb.isNotEmpty()) sb.append(' ')
            sb.append(s)
        }
        flush(sb, out)
        return out
    }

    private fun flush(sb: StringBuilder, out: MutableList<String>) {
        if (sb.isNotBlank()) out += sb.toString()
        sb.setLength(0)
    }
}
