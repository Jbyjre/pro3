package me.kavishdevar.librepods

import me.kavishdevar.librepods.services.ScreenInsights.Kind
import me.kavishdevar.librepods.services.ScreenText
import me.kavishdevar.librepods.services.ScreenText.Bit
import me.kavishdevar.librepods.services.ScreenVoiceRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The island reflecting the app's screen (Jake, 2026-10-11): what it's about, and what's useful on it. */
class ScreenInsightsTest {
    private val h = 2_400
    private fun bit(t: String, top: Int, heading: Boolean = false, id: String? = null, visible: Boolean = true, url: Boolean = false) =
        Bit(t, heading, id, top, h, visible, url)

    @Test fun theHeadingIsWhatTheScreenIsAbout() {
        val bits = listOf(bit("9:41", 10), bit("Search", 150), bit("Chrome", 160), bit("Why cats sleep so much", 400, heading = true), bit("Body text here", 700))
        assertEquals("Why cats sleep so much", ScreenText.headline(bits, "Chrome"))
    }

    @Test fun aTitleByItsNameNearTheTopIsNextAndNeverTheAppsOwnName() {
        val bits = listOf(bit("WhatsApp", 120, id = "com.whatsapp:id/toolbar_title"), bit("Mum", 160, id = "com.whatsapp:id/conversation_contact_name"), bit("See you at 6", 1200))
        assertEquals("Mum", ScreenText.headline(bits, "WhatsApp"))
    }

    @Test fun chromeWordsAndClocksAreNeverHeadlines() {
        assertNull(ScreenText.headline(listOf(bit("Back", 150), bit("10:24 PM", 160), bit("More options", 170))))
    }

    @Test fun aRealReadGetsItsSiteAndReadingTime() {
        fun para(n: Int) = "Paragraph $n " + List(28) { "word" }.joinToString(" ")
        // Ten paragraphs of 30 words: 300 words, most of them below the screen (still counted).
        val bits = listOf(bit("www.theverge.com/2026/apple", 120, url = true)) + List(10) { bit(para(it), 600 + it * 300, visible = it < 3) }
        assertEquals("theverge.com · 2 min read", ScreenText.summary(bits))
        assertTrue(ScreenText.body(bits)!!.startsWith("Paragraph 0 word"))
        assertFalse(ScreenText.body(bits)!!.contains("theverge"))
    }

    @Test fun aShortScreenIsNoArticle() {
        val bits = listOf(bit("Settings", 150, heading = true), bit("Wi-Fi", 400), bit("Bluetooth", 500))
        assertNull(ScreenText.summary(bits))
        assertNull(ScreenText.body(bits))
    }

    @Test fun oneTimeCodesNeedTheirWords() {
        assertEquals(listOf("482913"), ScreenText.codes("Your verification code is 482913. Don't share it."))
        assertEquals(listOf("5521"), ScreenText.codes("OTP: 5521"))
        // A year, a price and an order number aren't codes.
        assertTrue(ScreenText.codes("Order 20261011 placed in 2026 for 1999 dollars").isEmpty())
    }

    @Test fun theFallbackFindsEmailsLinksAndPhones() {
        val found = ScreenText.patterns("Mail jake@example.com or see example.org/help, or call +44 20 7946 0018.")
        assertEquals(listOf(Kind.Email, Kind.Link, Kind.Phone), found.map { it.kind })
        assertEquals("Open example.org", found[1].label)
        // The email's own domain isn't offered again as a link.
        assertEquals(1, found.count { it.kind == Kind.Link })
    }

    @Test fun aCodeIsntAlsoAPhone() {
        assertTrue(ScreenText.patterns("Your code: 48291355").none { it.kind == Kind.Phone })
    }

    @Test fun codesComeFirstNoRepeatsThreeAtMost() {
        val all = listOf(
            ScreenText.found(Kind.Link, "a.com"), ScreenText.found(Kind.Phone, "555 0100 1234"), ScreenText.found(Kind.Phone, "555-0100-1234"),
            ScreenText.found(Kind.Email, "x@y.com"), ScreenText.found(Kind.Code, "1234"),
        )
        assertEquals(listOf(Kind.Code, Kind.Phone, Kind.Link), ScreenText.pick(all).map { it.kind })
    }

    @Test fun buttonsShortenInARowOfThreeExceptCodes() {
        val call = ScreenText.found(Kind.Phone, "020 7946 0018")
        assertEquals("Call 020 7946 0018", ScreenText.pillLabel(call, 2))
        assertEquals("Call", ScreenText.pillLabel(call, 3))
        assertEquals("Copy 1234", ScreenText.pillLabel(ScreenText.found(Kind.Code, "1234").copy(label = "Copy 1234"), 3))
    }

    @Test fun banksAndPasswordAppsAreNeverRead() {
        assertTrue(ScreenText.private("com.chase.sig.android"))
        assertTrue(ScreenText.private("com.x8bit.bitwarden"))
        assertTrue(ScreenText.private("com.google.android.apps.authenticator2"))
        assertTrue(ScreenText.private("uk.co.hsbc.hsbcukmobilebanking"))
        assertFalse(ScreenText.private("com.citymapper.app.release"))
        assertFalse(ScreenText.private("com.android.chrome"))
        assertFalse(ScreenText.private("com.purchase.tracker"))
    }

    @Test fun addressBarsAreKnownByName() {
        assertTrue(ScreenText.isAddressBar("com.android.chrome:id/url_bar"))
        assertTrue(ScreenText.isAddressBar("org.mozilla.firefox:id/mozac_browser_toolbar_url_view"))
        assertFalse(ScreenText.isAddressBar("com.whatsapp:id/entry"))
        assertEquals("bbc.co.uk", ScreenText.site("https://www.bbc.co.uk/news/x?y=1"))
        assertNull(ScreenText.site("Search or type web address"))
    }

    @Test fun theAppLineSaysHowLongYouveBeenIn() {
        assertEquals("Browser · Saturday 11 October", ScreenText.appLine("Browser", 20_000L, "Saturday 11 October"))
        assertEquals("Browser · open 12 min", ScreenText.appLine("Browser", 12 * 60_000L, "x"))
        assertEquals("open 1 h 5 min", ScreenText.appLine(null, 65 * 60_000L, "x"))
        assertEquals("Game · open 2 h", ScreenText.appLine("Game", 120 * 60_000L, "x"))
    }

    @Test fun theCardAndRowsFitTheOpenedIsland() {
        val full = ScreenText.cardDp(headline = true, sub = true, pills = true, compact = false)
        assertEquals(95f, full, 0.01f)
        assertEquals(0, ScreenText.rowsLeft(full + 6f))
        // One line of headline leaves room for a running timer.
        assertEquals(1, ScreenText.rowsLeft(ScreenText.cardDp(true, true, true, compact = true) + 6f))
        assertEquals(2, ScreenText.rowsLeft(0f))
        assertTrue(full + 6f <= ScreenText.AWAY_ROOM_DP)
    }

    @Test fun listenHandsTheVoicePiecesItAccepts() {
        val text = List(400) { "This is sentence number $it." }.joinToString(" ")
        val parts = ScreenVoiceRules.parts(text, 4_000)
        assertTrue(parts.size > 1)
        assertTrue(parts.all { it.length <= 3_900 })
        // Nothing lost, cut between sentences.
        assertEquals(text.replace(" ", ""), parts.joinToString("").replace(" ", ""))
        assertTrue(parts.all { it.endsWith(".") })
        // One endless "sentence" is cut at spaces, never mid-word.
        val long = List(2_000) { "word" }.joinToString(" ")
        assertTrue(ScreenVoiceRules.parts(long, 4_000).all { it.length <= 3_900 && !it.startsWith("ord") })
    }
}
