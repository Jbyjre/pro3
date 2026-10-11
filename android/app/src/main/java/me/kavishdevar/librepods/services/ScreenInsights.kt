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

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.provider.CalendarContract
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.view.textclassifier.TextClassificationManager
import android.view.textclassifier.TextClassifier
import android.view.textclassifier.TextLinks
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What's on the screen of the app you're in, so the opened Dynamic Island reflects the app itself,
 * not just its name (Jake, 2026-10-11: "have the app reading my screen"): what the screen is about
 * (the page, the video, the chat, the article) and the useful things on it, one tap each: a phone
 * number to call, an address to map, a link to open, a date to add to the calendar, an email, a
 * one-time code to copy.
 *
 * - Read **once, when you open the island** in an app, never in the background (no battery cost,
 *   nothing watched).
 * - On the phone only: the words are looked at and dropped; nothing is stored or sent anywhere.
 * - Password fields and what you're typing are never read; pro itself and the lock screen aren't.
 * - Finding numbers, addresses and dates uses Android's own on-device text detector (the one behind
 *   "smart text selection"), with simple patterns as a fallback.
 * Needs the "pro Dynamic Island" accessibility switch (already on for the app icon). Off in
 * Settings > Island > Read the screen.
 */
object ScreenInsights {
    private const val TAG = "ScreenInsights"
    /**
     * How much of a screen is looked at, at most: a long article fits (its off-screen paragraphs
     * too, so "min read" and Listen cover the whole piece), and the read stops after [BUDGET_MS]
     * whatever happens. One read like this costs about what one screen of scrolling does.
     */
    private const val MAX_NODES = 1_500
    private const val MAX_CHARS = 24_000
    private const val BUDGET_MS = 250L
    /** Android's detector gets the part you can see (that's where you'd tap). */
    private const val DETECT_CHARS = 4_000

    enum class Kind(val verb: String) { Code("Copy"), Phone("Call"), Address("Map"), Link("Open"), Email("Email"), Date("Add") }

    /** One useful thing on the screen: [text] as it appears, [label] for its button. */
    data class Found(val kind: Kind, val text: String, val label: String)

    data class Insight(
        val pkg: String,
        /** What the screen is about (its title, the chat's name, the video), or null. */
        val headline: String?,
        /** A line under it ("example.com · 4 min read"), or null. */
        val sub: String?,
        val found: List<Found>,
        val at: Long,
        /** The article's words, for Listen (kept in memory only while the island is open), or null when it isn't a read. */
        val body: String? = null,
    )

    private val _state = MutableStateFlow<Insight?>(null)
    val state: StateFlow<Insight?> = _state.asStateFlow()

    private val thread by lazy { HandlerThread("pro-screen-read").apply { start() } }
    private val worker by lazy { Handler(thread.looper) }
    private val main = Handler(Looper.getMainLooper())

    /** Reads [pkg]'s screen now (off the main thread); the result arrives in [state]. */
    fun read(context: Context, pkg: String) {
        if (previewing) return
        val app = context.applicationContext
        if (!IslandPrefs.readScreen(IslandPrefs.prefs(app)) || pkg == app.packageName || ScreenText.private(pkg)) { _state.value = null; return }
        val service = IslandAccess.service.value ?: run { _state.value = null; return }
        // A fresh read: what was there for another app (or a minute ago) mustn't show meanwhile.
        if (_state.value?.pkg != pkg || SystemClock.elapsedRealtime() - (_state.value?.at ?: 0L) > 30_000L) _state.value = null
        worker.removeCallbacksAndMessages(null)
        worker.post {
            val result = runCatching { look(app, service, pkg) }.onFailure { Log.w(TAG, "Couldn't read the screen", it) }.getOrNull()
            main.post { _state.value = result }
        }
    }

    /** Forgets what was read (the island closed, or the switch went off). Listen keeps going if it was asked to. */
    fun clear() { if (previewing) return; worker.removeCallbacksAndMessages(null); _state.value = null }

    /** Screenshots and tests: [i] stays put (no real read replaces it) until preview(null). */
    @Volatile private var previewing = false
    internal fun preview(i: Insight?) { previewing = i != null; _state.value = i }

    private fun look(context: Context, s: AccessibilityService, pkg: String): Insight? {
        val started = SystemClock.elapsedRealtime()
        val win = s.windows.orEmpty()
            .filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
            .firstOrNull { runCatching { it.root?.packageName?.toString() }.getOrNull() == pkg }
            ?: return null
        val root = win.root ?: return null
        val screenH = android.graphics.Rect().also { win.getBoundsInScreen(it) }.height().coerceAtLeast(1)
        val bits = ArrayList<ScreenText.Bit>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var chars = 0
        var seen = 0
        val r = android.graphics.Rect()
        while (queue.isNotEmpty() && seen < MAX_NODES && chars < MAX_CHARS && SystemClock.elapsedRealtime() - started < BUDGET_MS) {
            val n = queue.removeFirst()
            seen++
            // Never passwords, never what's being typed. A browser's address bar is the one
            // edit box read, and only while you aren't typing in it.
            val id = n.viewIdResourceName
            val url = n.isEditable && !n.isFocused && ScreenText.isAddressBar(id)
            if (n.isPassword || (n.isEditable && !url)) continue
            val t = n.text?.toString()?.trim()
            if (!t.isNullOrEmpty()) {
                n.getBoundsInScreen(r)
                bits += ScreenText.Bit(t, n.isHeading, id, r.top, screenH, visible = n.isVisibleToUser, url = url)
                chars += t.length
            }
            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
        }
        if (bits.isEmpty()) return null
        val seenNow = bits.filter { it.visible && !it.url }
        val text = seenNow.joinToString("\n") { it.text }.take(DETECT_CHARS)
        val found = (ScreenText.codes(text).map { Found(Kind.Code, it, "Copy $it") } + detect(context, text)).let(ScreenText::pick)
        val label = SoundSource.cachedInfo(pkg)?.label
        return Insight(
            pkg = pkg,
            headline = ScreenText.headline(bits.filter { it.visible }, label),
            sub = ScreenText.summary(bits),
            found = found,
            at = SystemClock.elapsedRealtime(),
            body = ScreenText.body(bits),
        )
    }

    /** Phones, addresses, links, emails and dates: Android's own detector, else simple patterns. */
    private fun detect(context: Context, text: String): List<Found> {
        val tc = runCatching { context.getSystemService(TextClassificationManager::class.java)?.textClassifier }.getOrNull()
        if (tc != null && tc !== TextClassifier.NO_OP) {
            val links = runCatching {
                val limit = minOf(text.length, tc.maxGenerateLinksTextLength)
                tc.generateLinks(TextLinks.Request.Builder(text.substring(0, limit)).build()).links
            }.getOrNull()
            if (links != null) {
                val out = links.mapNotNull { l ->
                    val type = (0 until l.entityCount).map { l.getEntity(it) }.maxByOrNull { l.getConfidenceScore(it) } ?: return@mapNotNull null
                    val s = text.substring(l.start, l.end).trim()
                    val kind = when (type) {
                        TextClassifier.TYPE_PHONE -> Kind.Phone
                        TextClassifier.TYPE_ADDRESS -> Kind.Address
                        TextClassifier.TYPE_URL -> Kind.Link
                        TextClassifier.TYPE_EMAIL -> Kind.Email
                        TextClassifier.TYPE_DATE, TextClassifier.TYPE_DATE_TIME -> Kind.Date
                        else -> return@mapNotNull null
                    }
                    ScreenText.found(kind, s)
                }
                if (out.isNotEmpty()) return out
            }
        }
        return ScreenText.patterns(text)
    }

    /** Does what a found thing's button says. */
    fun act(context: Context, f: Found) {
        when (f.kind) {
            Kind.Code -> {
                runCatching {
                    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Code", f.text))
                }
                return
            }
            Kind.Phone -> start(context, Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(f.text))))
            Kind.Address -> start(context, Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(f.text))))
            Kind.Email -> start(context, Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + f.text)))
            Kind.Link -> start(context, Intent(Intent.ACTION_VIEW, Uri.parse(ScreenText.webAddress(f.text))))
            Kind.Date -> start(context, Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
                .putExtra(CalendarContract.Events.TITLE, _state.value?.headline ?: "")
                .putExtra(CalendarContract.Events.DESCRIPTION, f.text))
        }
    }

    private fun start(context: Context, i: Intent) {
        runCatching { context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.onFailure { Log.w(TAG, "No app for ${i.action}", it) }
    }
}

/** The pure rules behind [ScreenInsights]: what a screen is about, and the useful things on it. Unit-tested. */
object ScreenText {
    /**
     * A piece of text on the screen: where it sits ([top] of [screenH]), what the app says it is,
     * whether you can see it right now ([visible]: an article's later paragraphs aren't) and
     * whether it's a browser's address bar ([url]).
     */
    data class Bit(
        val text: String, val heading: Boolean, val id: String?, val top: Int, val screenH: Int,
        val visible: Boolean = true, val url: Boolean = false,
    )

    /** Words every app shows that never say what a screen is about. */
    private val CHROME = setOf(
        "search", "back", "menu", "more", "more options", "home", "ok", "cancel", "done", "close", "share", "settings",
        "navigate up", "options", "edit", "save", "send", "next", "previous", "play", "pause", "like", "reply", "follow",
        "subscribe", "sign in", "log in", "skip", "skip ad", "accept", "accept all", "got it", "new tab", "tabs",
    )
    private val TITLE_ID = Regex("""(title|toolbar|header|headline|name|subject)""", RegexOption.IGNORE_CASE)
    private val CLOCK = Regex("""^\d{1,2}:\d{2}(\s?[ap]\.?m\.?)?$""", RegexOption.IGNORE_CASE)
    private val ADDRESS_BAR = Regex("""(url_bar|url_field|urlbar|url_view|address_bar|location_bar|mozac_browser_toolbar_url|search_box_text|omnibox)""", RegexOption.IGNORE_CASE)

    /**
     * Apps whose screens are never read: banks, payments, password managers and authenticators
     * (their names give them away; Android has no "finance" category to ask for).
     */
    private val PRIVATE = Regex("""(bank|wallet|paypal|venmo|cashapp|squarecash|revolut|monzo|wellsfargo|capitalone|coinbase|binance|password|bitwarden|lastpass|keepass|dashlane|authenticator|authy)""", RegexOption.IGNORE_CASE)
    /** Names too short to look for inside others ("citi" is in "cities"): only as a whole part of the app's id. */
    private val PRIVATE_PARTS = setOf("chase", "citi", "amex", "pay", "vault", "keeper", "crypto")

    fun private(pkg: String): Boolean = PRIVATE.containsMatchIn(pkg) || pkg.lowercase().split('.', '_').any { it in PRIVATE_PARTS }

    fun isAddressBar(id: String?): Boolean = id != null && ADDRESS_BAR.containsMatchIn(id.substringAfter('/'))

    private fun usable(t: String, label: String?) = t.length in 2..90 && t.lowercase() !in CHROME && t.any { it.isLetter() } &&
        !CLOCK.matches(t) && (label == null || !t.equals(label, ignoreCase = true))

    /**
     * The screen's headline: what the app marks as a heading, else a title by its name, else the
     * top line. Never the app's own name ([label]): the island already says that.
     */
    fun headline(bits: List<Bit>, label: String? = null): String? {
        val ok = bits.filter { !it.url && usable(it.text, label) }
        // Below the status bar's strip, in the top part of the screen.
        fun nearTop(b: Bit) = b.top > b.screenH * 0.03f && b.top < b.screenH * 0.32f
        return (ok.firstOrNull { it.heading && it.top < it.screenH * 0.6f }
            ?: ok.firstOrNull { it.id?.let { id -> TITLE_ID.containsMatchIn(id.substringAfter('/')) } == true && nearTop(it) }
            ?: ok.filter { nearTop(it) }.minByOrNull { it.top })
            ?.text?.lines()?.first()?.trim()?.take(80)
    }

    /** How many words the screen's reading holds (the article's off-screen part too). */
    fun words(bits: List<Bit>): Int = bits.filter { !it.url }.sumOf { b -> b.text.split(Regex("""\s+""")).count { it.any(Char::isLetter) } }

    /** A line under the headline: the website you're on (in a browser), and how long it takes to read, when it's a real read. */
    fun summary(bits: List<Bit>): String? {
        val site = bits.firstOrNull { it.url }?.text?.let(::site)
        val w = words(bits)
        val read = if (w >= READ_WORDS) "${(w + 219) / 220} min read" else null
        return listOfNotNull(site, read).joinToString(" · ").ifEmpty { null }
    }

    /** "https://www.bbc.co.uk/news/x" → "bbc.co.uk" (what a browser's bar shows is often already that). */
    fun site(text: String): String? {
        val host = text.trim().removePrefix("https://").removePrefix("http://").removePrefix("www.").substringBefore('/').substringBefore('?')
        return host.takeIf { it.contains('.') && ' ' !in it && it.length <= 60 }?.lowercase()
    }

    /** Words enough to call it an article (about a minute's reading). */
    const val READ_WORDS = 180

    /**
     * The article's words for Listen: its paragraphs (long lines with spaces), in order, without
     * the buttons and menus around them. Null when it isn't a read.
     */
    fun body(bits: List<Bit>, max: Int = 20_000): String? {
        if (words(bits) < READ_WORDS) return null
        val paras = bits.asSequence().filter { !it.url && (it.text.length >= 40 && ' ' in it.text || it.heading && it.text.length >= 8) }
            .map { it.text.trim() }.distinct().toList()
        if (paras.sumOf { p -> p.count { it == ' ' } + 1 } < READ_WORDS / 2) return null
        return paras.joinToString("\n\n").take(max)
    }

    private val CODE_WORDS = Regex("""(code|otp|passcode|verification|verify|pin|one-time)""", RegexOption.IGNORE_CASE)
    // Not part of a longer number, a decimal or a date; a full stop after it (end of the sentence) is fine.
    private val CODE = Regex("""(?<![\d.,:/-])(\d{4,8})(?![\d,:/-]|\.\d)""")

    /** One-time codes: 4 to 8 digits close to words like "code" or "verification". */
    fun codes(text: String): List<String> = CODE.findAll(text).mapNotNull { m ->
        val around = text.substring((m.range.first - 60).coerceAtLeast(0), (m.range.last + 40).coerceAtMost(text.length))
        m.groupValues[1].takeIf { CODE_WORDS.containsMatchIn(around) }
    }.distinct().take(2).toList()

    private val PHONE = Regex("""(?<![\w])(\+?\d[\d ()-]{7,16}\d)(?![\w])""")
    private val EMAIL = Regex("""[\w.+-]+@[\w-]+\.[\w.]{2,}""")
    private val URL = Regex("""\b((?:https?://)?(?:www\.)?[a-z0-9-]+(?:\.[a-z0-9-]+)*\.(?:com|org|net|io|co|app|dev|edu|gov|uk|de|ca|au)(?:/[^\s]*)?)\b""", RegexOption.IGNORE_CASE)

    /** The simple fallback when Android's detector isn't there: phones, emails, links. */
    fun patterns(text: String): List<ScreenInsights.Found> = buildList {
        EMAIL.findAll(text).forEach { add(found(ScreenInsights.Kind.Email, it.value)) }
        val emails = EMAIL.findAll(text).map { it.range }.toList()
        URL.findAll(text).filter { m -> emails.none { e -> m.range.first in e } }.forEach { add(found(ScreenInsights.Kind.Link, it.value)) }
        PHONE.findAll(text).filter { m -> m.value.count(Char::isDigit) in 8..15 && CODE_WORDS.containsMatchIn(text.substring((m.range.first - 30).coerceAtLeast(0), m.range.first)).not() }
            .forEach { add(found(ScreenInsights.Kind.Phone, it.value.trim())) }
    }

    /** A found thing with its button's words ("Call 555 0123", "Open example.com"). */
    fun found(kind: ScreenInsights.Kind, text: String): ScreenInsights.Found {
        val shown = when (kind) {
            ScreenInsights.Kind.Link -> text.removePrefix("https://").removePrefix("http://").removePrefix("www.").substringBefore('/')
            else -> text
        }
        return ScreenInsights.Found(kind, text, "${kind.verb} ${shown.take(28)}")
    }

    /** The few to offer: codes first (you need them now), then calls, places, links, emails, dates; no repeats. */
    fun pick(all: List<ScreenInsights.Found>): List<ScreenInsights.Found> =
        all.distinctBy { it.kind to it.text.lowercase().filter(Char::isLetterOrDigit) }
            .sortedBy { it.kind.ordinal }
            .take(3)

    /**
     * A button's words in a row of [count]: the whole thing ("Call 020 7946 0018") when there's
     * room, just the verb in a row of three ("Call"), a code always in full (that's the point).
     */
    fun pillLabel(f: ScreenInsights.Found, count: Int): String =
        if (count <= 2 || f.kind == ScreenInsights.Kind.Code) f.label else f.kind.verb

    /**
     * The line under the app's name: what kind of app it is and how long it's been open
     * ("Browser · open 12 min"), or today's date for the first minute.
     */
    fun appLine(kind: String?, openMs: Long, date: String): String {
        val min = openMs / 60_000L
        val time = when {
            min < 1 -> null
            min < 60 -> "open $min min"
            else -> "open ${min / 60} h" + (if (min % 60 > 0) " ${min % 60} min" else "")
        }
        return listOfNotNull(kind, time ?: date).joinToString(" · ")
    }

    /** Room the opened island has for what's under the app's name, away from home (GLANCE_AWAY_H_DP less its edges and header). */
    const val AWAY_ROOM_DP = 144f
    /** A row of what's live on the phone (a timer, charging), with the gap above it. */
    const val ROW_DP = 56f

    /** How tall the "on this screen" card is ([compact]: one line of headline, no line under it). */
    fun cardDp(headline: Boolean, sub: Boolean, pills: Boolean, compact: Boolean): Float =
        (if (headline) (if (compact) 20f else 40f) else 0f) +
            (if (sub && !compact) 15f else 0f) +
            (if (pills) (if (headline || (sub && !compact)) 6f else 0f) + 34f else 0f)

    /** How many of the phone's live rows still fit under [usedDp] of cards. */
    fun rowsLeft(usedDp: Float, room: Float = AWAY_ROOM_DP): Int = ((room - usedDp) / ROW_DP).toInt().coerceIn(0, 2)

    /** A link as a web address (adds https:// when the screen showed it without). */
    fun webAddress(text: String): String = if (text.startsWith("http://") || text.startsWith("https://")) text else "https://$text"
}
