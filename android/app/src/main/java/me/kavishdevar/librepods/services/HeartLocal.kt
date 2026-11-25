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
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.InputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.Semaphore

const val PREF_LINK_LOCAL = "glint_link_local"
const val PREF_LINK_LOCAL_TOKEN = "glint_link_local_token"

/**
 * Hands the live heart rate to the JWR workout app on this same phone, with nothing leaving
 * the phone. It is a tiny web server that listens only on the phone's own loopback address
 * (127.0.0.1): Wi-Fi, mobile data and other devices can never reach it, only apps running on
 * this phone. Off until you turn it on, and every request must carry the pairing code.
 *
 * JWR (a web app in Chrome) asks `GET /v1/hr?live=1` every couple of seconds while a workout
 * runs. `live=1` also asks pro to measure for the next 90 seconds, so the AirPods only run
 * their sensor while a workout is actually asking, and stop by themselves afterwards.
 */
object HeartLocal {
    private const val TAG = "HeartLocal"

    /** Tried in order; JWR scans the same short list. A busy port just means the next one. */
    val PORTS = intArrayOf(48731, 48732, 48733)

    /** Where the pairing button opens JWR (its address, from JWR's own notes). */
    const val JWR_URL = "https://frosty-grass-e9cd.jmath1110.workers.dev/"

    /** One request keeps the sensor going this long; JWR renews it with every poll. */
    const val LIVE_MS = 90_000L

    /** At most this many requests are handled at once; more are dropped (a phone-only server). */
    private const val MAX_CLIENTS = 6

    data class Status(
        val running: Boolean = false,
        val port: Int = 0,
        /** Plain-words reason when it can't run. */
        val error: String? = null,
        /** When JWR (or anything with the code) last asked for the heart rate. */
        val lastAskMs: Long = 0L,
        val asks: Int = 0,
    )

    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status.asStateFlow()

    /** What the server needs from the service; kept as an interface so it can be tested. */
    interface Host {
        /** Measure for [ms] (the island's glance mechanism, renewed by each call). */
        fun requestLive(ms: Long)
        fun airpodsConnected(): Boolean
    }

    private var appContext: Context? = null
    private var scope: CoroutineScope? = null
    private var host: Host? = null
    private var job: Job? = null
    @Volatile private var server: ServerSocket? = null
    private var lastLiveCallMs = 0L
    private val clients = Semaphore(MAX_CLIENTS)

    /** Called once by the service. */
    fun attach(context: Context, scope: CoroutineScope, host: Host) {
        appContext = context.applicationContext
        this.scope = scope
        this.host = host
        refresh()
    }

    fun detach() {
        stop()
        host = null
    }

    /** Re-reads the switch (after a change on the share screen). */
    fun refresh() {
        val ctx = appContext ?: return
        if (ctx.getSharedPreferences("settings", Context.MODE_PRIVATE).getBoolean(PREF_LINK_LOCAL, false)) start() else stop()
    }

    @Synchronized
    private fun start() {
        if (job?.isActive == true) return
        val sc = scope ?: return
        job = sc.launch(Dispatchers.IO) { serve() }
    }

    @Synchronized
    private fun stop() {
        try { server?.close() } catch (_: Exception) {}
        server = null
        job?.cancel()
        job = null
        _status.update { Status(lastAskMs = it.lastAskMs, asks = it.asks) }
    }

    private fun serve() {
        val loopback = InetAddress.getByName("127.0.0.1")
        var ss: ServerSocket? = null
        var busy = false
        for (port in PORTS) {
            val candidate = ServerSocket()
            try {
                candidate.reuseAddress = true
                candidate.bind(InetSocketAddress(loopback, port), 8)
                ss = candidate
                break
            } catch (e: Exception) {
                busy = true
                try { candidate.close() } catch (_: Exception) {}
            }
        }
        if (ss == null) {
            _status.update { it.copy(running = false, port = 0, error = if (busy) "Another app is using the link's ports" else "Couldn't start the link") }
            return
        }
        server = ss
        _status.update { it.copy(running = true, port = ss.localPort, error = null) }
        Log.i(TAG, "listening on 127.0.0.1:${ss.localPort}")
        try {
            val sc = scope ?: return
            while (sc.isActive) {
                val socket = ss.accept()
                if (!clients.tryAcquire()) { try { socket.close() } catch (_: Exception) {}; continue }
                sc.launch(Dispatchers.IO) {
                    try { handle(socket) } finally { clients.release() }
                }
            }
        } catch (_: SocketException) {
            // Closed by stop().
        } catch (e: Exception) {
            Log.w(TAG, "server ended: ${e.message}")
            _status.update { it.copy(running = false, error = "The link stopped") }
        } finally {
            try { ss.close() } catch (_: Exception) {}
            if (server === ss) server = null
        }
    }

    private fun handle(socket: Socket) {
        socket.use { s ->
            try {
                s.soTimeout = 3_000
                val request = HeartLocalProtocol.read(s.getInputStream()) ?: return
                val ctx = appContext ?: return
                val now = System.currentTimeMillis()
                val state = HeartRate.state.value
                val snapshot = HeartLocalProtocol.Snapshot(
                    status = state.status.name.lowercase(),
                    bpm = state.bpm,
                    lastReadingMs = state.lastReadingMs,
                    samples = state.samples,
                    age = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE).getInt(PREF_HR_AGE, 0),
                    airpods = host?.airpodsConnected() == true,
                )
                val result = HeartLocalProtocol.respond(request, token(ctx), snapshot, now)
                if (result.authorized) {
                    _status.update { it.copy(lastAskMs = now, asks = it.asks + 1) }
                    // Renewing the sensor on every poll would churn it; every 10 s is plenty.
                    if (result.live && now - lastLiveCallMs >= 10_000L) {
                        lastLiveCallMs = now
                        host?.requestLive(LIVE_MS)
                    }
                }
                s.getOutputStream().apply { write(result.response.bytes()); flush() }
            } catch (e: Exception) {
                Log.w(TAG, "request failed: ${e.message}")
            }
        }
    }

    /** The pairing code: created the first time it's needed, kept until you ask for a new one. */
    @Synchronized
    fun token(context: Context): String {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val saved = prefs.getString(PREF_LINK_LOCAL_TOKEN, null)
        if (!saved.isNullOrEmpty()) return saved
        return newToken(context)
    }

    /** A fresh pairing code; the old one stops working at once. */
    @Synchronized
    fun newToken(context: Context): String {
        val fresh = HeartLocalProtocol.generateToken()
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putString(PREF_LINK_LOCAL_TOKEN, fresh).apply()
        return fresh
    }

    /** The address that opens JWR already paired: it reads the code and port from the address. */
    fun pairingUrl(context: Context): String {
        val port = _status.value.port.takeIf { it != 0 } ?: PORTS[0]
        return JWR_URL + "?hrlink=" + URLEncoder.encode(token(context), "UTF-8") + "&hrport=" + port
    }
}

/**
 * The link's wire format, with no Android in it so it can be tested: how a request is read and
 * how each answer is built. Only a small piece of HTTP is supported on purpose.
 */
object HeartLocalProtocol {
    const val VERSION = 1
    private const val MAX_HEAD = 8 * 1024
    private const val MAX_TRAIL = 900
    private const val TOKEN_LENGTH = 24

    /** No look-alikes (0/O, 1/I/L) so a code read aloud or typed by hand is not misread. */
    private const val TOKEN_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"

    class Request(
        val method: String,
        val path: String,
        val query: Map<String, String>,
        /** Header names in lower case. */
        val headers: Map<String, String>,
    )

    class Response(val status: Int, private val reason: String, private val headers: Map<String, String>, private val body: String) {
        fun bytes(): ByteArray {
            val bodyBytes = body.toByteArray(Charsets.UTF_8)
            val head = StringBuilder("HTTP/1.1 ").append(status).append(' ').append(reason).append("\r\n")
            headers.forEach { (k, v) -> head.append(k).append(": ").append(v).append("\r\n") }
            head.append("Content-Length: ").append(bodyBytes.size).append("\r\n")
            head.append("Connection: close\r\n\r\n")
            return head.toString().toByteArray(Charsets.UTF_8) + bodyBytes
        }

        fun text(): String = body
        fun header(name: String): String? = headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
    }

    class Result(val response: Response, val authorized: Boolean, val live: Boolean)

    /** What the answer is built from: the heart-rate state, copied so it can't change midway. */
    class Snapshot(
        val status: String,
        val bpm: Int?,
        val lastReadingMs: Long,
        val samples: List<HeartRate.Sample>,
        /** The age saved in pro (0 = not set); JWR uses it for effort zones when it has none. */
        val age: Int,
        val airpods: Boolean,
    )

    fun generateToken(random: java.util.Random = SecureRandom()): String =
        buildString { repeat(TOKEN_LENGTH) { append(TOKEN_ALPHABET[random.nextInt(TOKEN_ALPHABET.length)]) } }

    /** Reads the request line and headers; null for anything malformed, too big or too slow. */
    fun read(input: InputStream): Request? {
        val buf = java.io.ByteArrayOutputStream()
        var matched = 0
        val end = byteArrayOf('\r'.code.toByte(), '\n'.code.toByte(), '\r'.code.toByte(), '\n'.code.toByte())
        while (buf.size() < MAX_HEAD) {
            val b = input.read()
            if (b < 0) return null
            buf.write(b)
            matched = if (b.toByte() == end[matched]) matched + 1 else if (b.toByte() == end[0]) 1 else 0
            if (matched == end.size) return parseHead(buf.toString(Charsets.ISO_8859_1.name()))
        }
        return null
    }

    fun parseHead(head: String): Request? {
        val lines = head.split("\r\n")
        val parts = lines.firstOrNull()?.split(' ') ?: return null
        if (parts.size != 3 || !parts[2].startsWith("HTTP/")) return null
        val method = parts[0].uppercase()
        val target = parts[1]
        if (!target.startsWith("/")) return null
        val path = target.substringBefore('?')
        val query = LinkedHashMap<String, String>()
        target.substringAfter('?', "").split('&').filter { it.isNotEmpty() }.forEach { pair ->
            val k = pair.substringBefore('=')
            val v = pair.substringAfter('=', "")
            try { query[URLDecoder.decode(k, "UTF-8")] = URLDecoder.decode(v, "UTF-8") } catch (_: Exception) { /* skip a bad pair */ }
        }
        val headers = LinkedHashMap<String, String>()
        lines.drop(1).forEach { line ->
            val i = line.indexOf(':')
            if (i > 0) headers[line.substring(0, i).trim().lowercase()] = line.substring(i + 1).trim()
        }
        return Request(method, path, query, headers)
    }

    /**
     * The Host header must name this phone's own loopback address. A web page on the internet
     * can be tricked into pointing a made-up name at 127.0.0.1 ("DNS rebinding"); refusing any
     * other Host closes that door.
     */
    fun hostIsLoopback(host: String?): Boolean {
        if (host.isNullOrEmpty()) return false
        val name = if (host.startsWith("[")) host.substringBefore(']') + "]" else host.substringBefore(':')
        return name == "127.0.0.1" || name == "localhost" || name == "[::1]"
    }

    /** The code, from `Authorization: Bearer ...` or `?t=...`, compared without timing leaks. */
    fun tokenMatches(req: Request, token: String): Boolean {
        val given = req.headers["authorization"]?.removePrefix("Bearer ")?.trim() ?: req.query["t"] ?: return false
        return MessageDigest.isEqual(given.toByteArray(Charsets.UTF_8), token.toByteArray(Charsets.UTF_8))
    }

    /**
     * Pages on the internet (https) may read the answer in a browser; a few local origins too,
     * for testing. Anything else gets no permission header, so a browser keeps it out.
     */
    fun allowedOrigin(origin: String?): String? {
        if (origin.isNullOrEmpty()) return null
        return if (origin.startsWith("https://") || origin.startsWith("http://localhost") || origin.startsWith("http://127.0.0.1")) origin else null
    }

    fun respond(req: Request, token: String, snap: Snapshot, nowMs: Long): Result {
        val cors = LinkedHashMap<String, String>()
        allowedOrigin(req.headers["origin"])?.let {
            cors["Access-Control-Allow-Origin"] = it
            cors["Vary"] = "Origin"
            cors["Access-Control-Allow-Methods"] = "GET, OPTIONS"
            cors["Access-Control-Allow-Headers"] = "authorization, content-type"
            // Chrome asks permission for a web page to reach the phone's own address.
            cors["Access-Control-Allow-Private-Network"] = "true"
            cors["Access-Control-Max-Age"] = "600"
        }
        cors["Cache-Control"] = "no-store"
        cors["Content-Type"] = "application/json; charset=utf-8"

        fun reply(status: Int, reason: String, body: String, authorized: Boolean = false, live: Boolean = false) =
            Result(Response(status, reason, cors, body), authorized, live)

        if (!hostIsLoopback(req.headers["host"])) return reply(403, "Forbidden", "{\"ok\":false,\"error\":\"host\"}")
        if (req.method == "OPTIONS") return reply(204, "No Content", "")
        if (req.method != "GET") return reply(405, "Method Not Allowed", "{\"ok\":false,\"error\":\"method\"}")
        return when (req.path) {
            // No code needed: lets JWR tell "pro isn't listening" apart from "wrong code".
            "/v1/hello" -> reply(200, "OK", "{\"ok\":true,\"app\":\"pro\",\"v\":$VERSION}")
            "/v1/hr" -> {
                if (!tokenMatches(req, token)) return reply(401, "Unauthorized", "{\"ok\":false,\"error\":\"code\"}")
                reply(200, "OK", snapshotJson(snap, nowMs, req.query["since"]?.toLongOrNull()), authorized = true, live = req.query["live"] == "1")
            }
            else -> reply(404, "Not Found", "{\"ok\":false,\"error\":\"path\"}")
        }
    }

    /**
     * {"ok":true,"v":1,"status":"live","bpm":72,"time":..,"now":..,"age":35,"airpods":true,
     *  "min":..,"max":..,"avg":..,"n":..,"trail":[[time,bpm],..]}
     * `trail` (only with ?since=) holds the readings after that time, so a page that was asleep
     * for a while can fill in what it missed.
     */
    fun snapshotJson(snap: Snapshot, nowMs: Long, since: Long?): String {
        val bpms = snap.samples.map { it.bpm }
        val sb = StringBuilder("{\"ok\":true,\"v\":").append(VERSION)
        sb.append(",\"status\":\"").append(snap.status).append('"')
        sb.append(",\"bpm\":").append(snap.bpm ?: "null")
        sb.append(",\"time\":").append(snap.lastReadingMs)
        sb.append(",\"now\":").append(nowMs)
        sb.append(",\"age\":").append(if (snap.age > 0) snap.age.toString() else "null")
        sb.append(",\"airpods\":").append(snap.airpods)
        sb.append(",\"min\":").append(bpms.minOrNull() ?: "null")
        sb.append(",\"max\":").append(bpms.maxOrNull() ?: "null")
        sb.append(",\"avg\":").append(if (bpms.isEmpty()) "null" else Math.round(bpms.average()).toString())
        sb.append(",\"n\":").append(bpms.size)
        if (since != null) {
            val trail = snap.samples.filter { it.timeMs > since }.takeLast(MAX_TRAIL)
            sb.append(",\"trail\":[")
            trail.forEachIndexed { i, s -> if (i > 0) sb.append(','); sb.append('[').append(s.timeMs).append(',').append(s.bpm).append(']') }
            sb.append(']')
        }
        return sb.append('}').toString()
    }
}
