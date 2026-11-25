package me.kavishdevar.librepods

import me.kavishdevar.librepods.services.HeartLocalProtocol
import me.kavishdevar.librepods.services.HeartRate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.Random

class HeartLocalTest {
    private val token = "ABCDEFGHJKMNPQRSTUVWXYZ2"

    private fun request(path: String, vararg extra: String, method: String = "GET", host: String = "127.0.0.1:48731"): HeartLocalProtocol.Request {
        val head = buildString {
            append("$method $path HTTP/1.1\r\n")
            if (host.isNotEmpty()) append("Host: $host\r\n")
            extra.forEach { append(it).append("\r\n") }
            append("\r\n")
        }
        return HeartLocalProtocol.read(ByteArrayInputStream(head.toByteArray()))!!
    }

    private fun snap(samples: List<HeartRate.Sample> = emptyList(), bpm: Int? = null, age: Int = 0) =
        HeartLocalProtocol.Snapshot("live", bpm, samples.lastOrNull()?.timeMs ?: 0L, samples, age, airpods = true)

    @Test fun readsRequestLineQueryAndHeaders() {
        val r = request("/v1/hr?live=1&since=5&t=a%20b", "Origin: https://example.com", "Authorization: Bearer xyz")
        assertEquals("GET", r.method)
        assertEquals("/v1/hr", r.path)
        assertEquals("1", r.query["live"])
        assertEquals("a b", r.query["t"])
        assertEquals("https://example.com", r.headers["origin"])
        assertEquals("Bearer xyz", r.headers["authorization"])
    }

    @Test fun refusesMalformedAndOversizedRequests() {
        assertNull(HeartLocalProtocol.read(ByteArrayInputStream("garbage\r\n\r\n".toByteArray())))
        assertNull(HeartLocalProtocol.read(ByteArrayInputStream("GET nothing HTTP/1.1\r\n\r\n".toByteArray())))
        // Ends before the blank line: a client that went quiet.
        assertNull(HeartLocalProtocol.read(ByteArrayInputStream("GET /v1/hr HTTP/1.1\r\nHost: 127.0.0.1\r\n".toByteArray())))
        // A header block bigger than the limit.
        val big = "GET /v1/hr HTTP/1.1\r\nX: " + "a".repeat(9000) + "\r\n\r\n"
        assertNull(HeartLocalProtocol.read(ByteArrayInputStream(big.toByteArray())))
    }

    @Test fun onlyLoopbackHostsAreServed() {
        assertTrue(HeartLocalProtocol.hostIsLoopback("127.0.0.1:48731"))
        assertTrue(HeartLocalProtocol.hostIsLoopback("localhost:48731"))
        assertTrue(HeartLocalProtocol.hostIsLoopback("[::1]:48731"))
        assertFalse(HeartLocalProtocol.hostIsLoopback("evil.example.com:48731"))
        assertFalse(HeartLocalProtocol.hostIsLoopback("127.0.0.1.evil.com"))
        assertFalse(HeartLocalProtocol.hostIsLoopback(""))
        assertFalse(HeartLocalProtocol.hostIsLoopback(null))
        val res = HeartLocalProtocol.respond(request("/v1/hr?t=$token", host = "evil.example.com"), token, snap(), 1000L)
        assertEquals(403, res.response.status)
        assertFalse(res.authorized)
    }

    @Test fun theCodeIsRequiredAndCheckedFromHeaderOrQuery() {
        val none = HeartLocalProtocol.respond(request("/v1/hr"), token, snap(), 1000L)
        assertEquals(401, none.response.status)
        assertFalse(none.authorized)
        assertFalse(none.live)
        val wrong = HeartLocalProtocol.respond(request("/v1/hr?t=NOPE&live=1"), token, snap(), 1000L)
        assertEquals(401, wrong.response.status)
        assertFalse("a wrong code must not wake the sensor", wrong.live)
        val query = HeartLocalProtocol.respond(request("/v1/hr?t=$token"), token, snap(), 1000L)
        assertEquals(200, query.response.status)
        assertTrue(query.authorized)
        val header = HeartLocalProtocol.respond(request("/v1/hr", "Authorization: Bearer $token"), token, snap(), 1000L)
        assertEquals(200, header.response.status)
        // Same length but different: still refused.
        assertFalse(HeartLocalProtocol.tokenMatches(request("/v1/hr?t=" + token.reversed()), token))
    }

    @Test fun liveOnlyWhenAskedAndAuthorized() {
        assertFalse(HeartLocalProtocol.respond(request("/v1/hr?t=$token"), token, snap(), 1L).live)
        assertTrue(HeartLocalProtocol.respond(request("/v1/hr?t=$token&live=1"), token, snap(), 1L).live)
    }

    @Test fun helloNeedsNoCodeAndNamesPro() {
        val res = HeartLocalProtocol.respond(request("/v1/hello"), token, snap(), 1L)
        assertEquals(200, res.response.status)
        assertEquals("{\"ok\":true,\"app\":\"pro\",\"v\":1}", res.response.text())
        assertFalse("hello must not count as JWR asking", res.authorized)
    }

    @Test fun corsAllowsHttpsPagesAndChromesPrivateNetworkCheck() {
        val ok = HeartLocalProtocol.respond(request("/v1/hello", "Origin: https://frosty-grass-e9cd.jmath1110.workers.dev"), token, snap(), 1L)
        assertEquals("https://frosty-grass-e9cd.jmath1110.workers.dev", ok.response.header("Access-Control-Allow-Origin"))
        assertEquals("true", ok.response.header("Access-Control-Allow-Private-Network"))
        assertEquals("no-store", ok.response.header("Cache-Control"))
        val pre = HeartLocalProtocol.respond(
            request("/v1/hr", "Origin: https://example.com", "Access-Control-Request-Method: GET", "Access-Control-Request-Private-Network: true", method = "OPTIONS"),
            token, snap(), 1L,
        )
        assertEquals(204, pre.response.status)
        assertEquals("true", pre.response.header("Access-Control-Allow-Private-Network"))
        assertFalse(pre.authorized)
        // A plain http page on another host gets no permission header, so a browser keeps it out.
        val bad = HeartLocalProtocol.respond(request("/v1/hello", "Origin: http://evil.example"), token, snap(), 1L)
        assertNull(bad.response.header("Access-Control-Allow-Origin"))
    }

    @Test fun onlyGetIsAnsweredAndUnknownPathsAre404() {
        assertEquals(405, HeartLocalProtocol.respond(request("/v1/hr?t=$token", method = "POST"), token, snap(), 1L).response.status)
        assertEquals(404, HeartLocalProtocol.respond(request("/nothing"), token, snap(), 1L).response.status)
    }

    @Test fun snapshotJsonCarriesTheNumbersAndTheTrail() {
        val samples = listOf(HeartRate.Sample(1000L, 100), HeartRate.Sample(2000L, 110), HeartRate.Sample(3000L, 120))
        val json = HeartLocalProtocol.snapshotJson(snap(samples, bpm = 120, age = 35), 4000L, null)
        assertEquals(
            "{\"ok\":true,\"v\":1,\"status\":\"live\",\"bpm\":120,\"time\":3000,\"now\":4000,\"age\":35,\"airpods\":true," +
                "\"min\":100,\"max\":120,\"avg\":110,\"n\":3}",
            json,
        )
        val withTrail = HeartLocalProtocol.snapshotJson(snap(samples, bpm = 120), 4000L, 1500L)
        assertTrue(withTrail, withTrail.endsWith(",\"trail\":[[2000,110],[3000,120]]}"))
        assertTrue(withTrail.contains("\"age\":null"))
    }

    @Test fun anEmptySessionIsValidJsonWithNulls() {
        val json = HeartLocalProtocol.snapshotJson(HeartLocalProtocol.Snapshot("off", null, 0L, emptyList(), 0, airpods = false), 5L, null)
        assertEquals("{\"ok\":true,\"v\":1,\"status\":\"off\",\"bpm\":null,\"time\":0,\"now\":5,\"age\":null,\"airpods\":false,\"min\":null,\"max\":null,\"avg\":null,\"n\":0}", json)
    }

    @Test fun theTrailIsCapped() {
        val many = (1..2000).map { HeartRate.Sample(it * 1000L, 90) }
        val json = HeartLocalProtocol.snapshotJson(snap(many, bpm = 90), 9_999_999L, 0L)
        assertEquals(900, Regex("\\[\\d+,90\\]").findAll(json).count())
    }

    @Test fun theResponseHasCorrectLengthAndStatusLine() {
        val res = HeartLocalProtocol.respond(request("/v1/hello"), token, snap(), 1L).response
        val text = String(res.bytes(), Charsets.UTF_8)
        assertTrue(text.startsWith("HTTP/1.1 200 OK\r\n"))
        val body = text.substringAfter("\r\n\r\n")
        assertTrue(text.contains("Content-Length: ${body.toByteArray().size}\r\n"))
        assertTrue(text.contains("Connection: close\r\n"))
    }

    @Test fun generatedCodesAreLongRandomAndHaveNoLookAlikes() {
        val a = HeartLocalProtocol.generateToken(Random(1))
        val b = HeartLocalProtocol.generateToken(Random(2))
        assertEquals(24, a.length)
        assertTrue(a != b)
        assertNotNull(a)
        assertTrue(a.none { it in "01OIL" })
        // A real (secure) one works too and is not constant.
        assertTrue(HeartLocalProtocol.generateToken() != HeartLocalProtocol.generateToken())
    }
}
