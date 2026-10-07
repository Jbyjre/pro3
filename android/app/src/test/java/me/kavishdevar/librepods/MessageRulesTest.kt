package me.kavishdevar.librepods

import android.app.Application
import me.kavishdevar.librepods.services.IslandGestures
import me.kavishdevar.librepods.services.IslandLook
import me.kavishdevar.librepods.services.IslandPrefs
import me.kavishdevar.librepods.services.MiniIslandRules
import me.kavishdevar.librepods.services.PhoneStatus
import me.kavishdevar.librepods.services.SoundRules
import me.kavishdevar.librepods.services.SoundRules.Posted
import me.kavishdevar.librepods.services.SoundRules.Role
import me.kavishdevar.librepods.services.SoundSource
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Messages and other moments: what earns one, what the island stays quiet for, and the swipe that puts it away. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class MessageRulesTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private val prefs get() = IslandPrefs.prefs(app)

    private fun post(
        pkg: String? = "com.chat", key: String = "k1", flags: Int = 0, category: String? = "msg", importance: Int = 4,
        conversation: Boolean = false, interrupts: Boolean = true, title: String? = null,
    ) = Posted(pkg, key, flags, category, importance, conversation, interrupts, title)

    private fun worth(p: Posted, messages: Boolean = true, others: Boolean = false, dnd: Boolean = true) =
        SoundRules.worthAMoment(p, own = "pro", messagesOn = messages, othersOn = others, respectDnd = dnd)

    // ---- Which notifications earn a moment ----

    @Test fun aTextMessageEarnsAMoment() {
        assertTrue(worth(post()))
        // A conversation counts even when the app didn't label it a message.
        assertTrue(worth(post(category = null, conversation = true)))
    }

    @Test fun theIslandFollowsThePhone_silentAndMutedStayQuiet() {
        // A muted chat or a silent channel has a low importance: the phone isn't alerting, so neither is the island.
        for (imp in intArrayOf(0, 1, 2)) assertFalse("importance $imp", worth(post(importance = imp)))
        // Default and high alert. Unknown importance (the phone didn't say) still counts for a message.
        assertTrue(worth(post(importance = 3)))
        assertTrue(worth(post(importance = -1)))
    }

    @Test fun doNotDisturbHoldsItBackWhenAskedTo() {
        val held = post(interrupts = false)
        assertFalse(worth(held, dnd = true))
        assertTrue(worth(held, dnd = false))
    }

    @Test fun ongoingServiceSummaryAndOwnNotificationsNeverCount() {
        assertFalse(worth(post(flags = SoundRules.FLAG_ONGOING_EVENT)))
        assertFalse(worth(post(flags = SoundRules.FLAG_FOREGROUND_SERVICE)))
        assertFalse(worth(post(flags = SoundRules.FLAG_GROUP_SUMMARY)))
        assertFalse(worth(post(pkg = "pro")))
        assertFalse(worth(post(pkg = null)))
    }

    @Test fun otherNotificationsOnlyWhenSwitchedOnAndOnlyIfTheyAlert() {
        val promo = post(category = "promo", importance = 4)
        assertFalse(worth(promo, others = false))
        assertTrue(worth(promo, others = true))
        // Even switched on, a silent one or one of unknown importance doesn't pop.
        assertFalse(worth(post(category = "promo", importance = 2), others = true))
        assertFalse(worth(post(category = "promo", importance = -1), others = true))
        // Turning messages off turns off messages, not the rest.
        assertFalse(worth(post(), messages = false))
        assertTrue(worth(promo, messages = false, others = true))
    }

    // ---- One message, one pop ----

    @Test fun theSameMessageTwiceInAMomentPopsOnce() {
        val d = SoundRules.Dedupe()
        assertTrue(d.fresh("chat1", 0, 1_000))
        assertFalse(d.fresh("chat1", 0, 2_500))
        // A new message in the same chat a few seconds later is news.
        assertTrue(d.fresh("chat1", 0, 7_000))
        // A different chat is its own news.
        assertTrue(d.fresh("chat2", 0, 7_100))
    }

    @Test fun anUpdateFlaggedAlertOnceIsNotNewsAgainForAWhile() {
        val d = SoundRules.Dedupe()
        assertTrue(d.fresh("dl", SoundRules.FLAG_ONLY_ALERT_ONCE, 0))
        assertFalse(d.fresh("dl", SoundRules.FLAG_ONLY_ALERT_ONCE, 60_000))
        assertTrue(d.fresh("dl", SoundRules.FLAG_ONLY_ALERT_ONCE, 60_000 + 600_001))
    }

    @Test fun theMemoryOfSeenNotificationsIsCapped() {
        val d = SoundRules.Dedupe(keep = 3)
        for (i in 1..10) d.fresh("k$i", 0, i * 10_000L)
        // The oldest are forgotten, so they count as new again; the newest are remembered.
        assertTrue(d.fresh("k1", 0, 200_000))
        assertFalse(d.fresh("k10", 0, 100_001))
    }

    // ---- The phone's own moments ----

    private fun info(level: Int, charging: Boolean) = PhoneStatus.Info(level, charging)

    @Test fun pluggingInGettingFullAndRunningLowEarnMoments() {
        val m = PhoneStatus::momentBetween
        assertEquals(PhoneStatus.Moment.Charging, m(info(50, false), info(50, true)))
        assertEquals(PhoneStatus.Moment.Full, m(info(99, true), info(100, true)))
        assertEquals(PhoneStatus.Moment.Low, m(info(21, false), info(20, false)))
        assertEquals(PhoneStatus.Moment.VeryLow, m(info(11, false), info(10, false)))
        // Dropping from 25 straight to 9 is the more serious one.
        assertEquals(PhoneStatus.Moment.VeryLow, m(info(25, false), info(9, false)))
    }

    @Test fun ordinaryChangesFirstReadingsAndChargingDipsAreNotNews() {
        val m = PhoneStatus::momentBetween
        assertNull(m(null, info(50, true)))
        assertNull(m(info(-1, false), info(50, true)))
        assertNull(m(info(60, false), info(59, false)))
        assertNull(m(info(15, false), info(14, false))) // already below 20: no second warning
        assertNull(m(info(15, true), info(16, true)))   // charging up through the low zone
        assertNull(m(info(100, true), info(100, true)))
        assertNull(m(info(50, true), info(50, false)))  // unplugging isn't announced
    }

    // ---- The look and the pill's rules ----

    @Test fun messagesHaveTheirOwnLookAndTheWindowOnlyGrowsForThem() {
        assertEquals(IslandLook.Slot.App to IslandLook.Slot.Title, IslandLook.read(prefs).slots(IslandLook.Situation.Message))
        assertEquals(
            IslandLook.Situation.Message,
            IslandLook.situation(music = false, playing = false, talking = false, charging = false, sound = true, message = true),
        )
        val d = 3f
        val s = MiniIslandRules.size(26 * d, 26 * d, d, 412 * d)
        // The usual window is the original size; the Messages look needs more, only while a message shows.
        assertEquals(36 * d + 2 * (36 * d - 8 * d + 7 * d), s.compactWidth, 0.01f)
        assertTrue(s.messageWidth > s.compactWidth)
        assertTrue(s.messageWidth <= 412 * d - 16 * d)
        // With nothing wider than a ring chosen for messages, nothing extra is needed.
        val plain = MiniIslandRules.size(
            26 * d, 26 * d, d, 412 * d,
            IslandLook.Look(slots = IslandLook.DEFAULT_SLOTS + (IslandLook.Situation.Message to (IslandLook.Slot.App to IslandLook.Slot.Nothing))),
        )
        assertEquals(plain.compactWidth, plain.messageWidth, 0.01f)
    }

    @Test fun contentOrderIsMusicThenMessageThenSoundThenPhoneThenTheRest() {
        fun content(playing: Boolean = false, sound: Boolean = false, message: Boolean = false, phone: Boolean = false, airPods: Boolean = false) =
            MiniIslandRules.content(playing, Long.MAX_VALUE, false, airPods, sound = sound, message = message, phoneMoment = phone)
        assertEquals(MiniIslandRules.Content.Music, content(playing = true, sound = true, message = true, phone = true))
        assertEquals(MiniIslandRules.Content.Message, content(sound = true, message = true, phone = true))
        assertEquals(MiniIslandRules.Content.Sound, content(sound = true, phone = true))
        // The phone's battery moment shows the phone even with AirPods connected, for its few seconds.
        assertEquals(MiniIslandRules.Content.Rest, content(phone = true, airPods = true))
        assertEquals(MiniIslandRules.Content.AirPods, content(airPods = true))
        assertEquals(MiniIslandRules.Content.Rest, content())
    }

    @Test fun swipingUpIsItsOwnGestureAndNothingElseChanged() {
        val slop = 8f; val swipe = 30f
        assertEquals(IslandGestures.Kind.SwipeUp, IslandGestures.classify(2f, -60f, 120, slop, swipe))
        assertEquals(IslandGestures.Kind.PullDown, IslandGestures.classify(2f, 60f, 120, slop, swipe))
        assertEquals(IslandGestures.Kind.SwipeLeft, IslandGestures.classify(-60f, -10f, 120, slop, swipe))
        assertEquals(IslandGestures.Kind.SwipeRight, IslandGestures.classify(60f, 20f, 120, slop, swipe))
        // A mostly sideways diagonal is a swipe sideways, a short upward drift is nothing, a tap is a tap.
        assertEquals(IslandGestures.Kind.SwipeLeft, IslandGestures.classify(-50f, -40f, 120, slop, swipe))
        assertEquals(IslandGestures.Kind.None, IslandGestures.classify(0f, -20f, 120, slop, swipe))
        assertEquals(IslandGestures.Kind.Tap, IslandGestures.classify(1f, -2f, 80, slop, swipe))
    }

    @Test fun messageAndSmartRuleSettingsDefaultToTheKindOnes() {
        assertTrue(IslandPrefs.messages(prefs))
        assertFalse(IslandPrefs.messagesOthers(prefs))
        assertFalse(IslandPrefs.messageSender(prefs)) // nothing inside a notification is read unless asked
        assertTrue(IslandPrefs.messagesRespectDnd(prefs))
        assertTrue(IslandPrefs.skipInUse(prefs))
        assertTrue(IslandPrefs.phoneMoments(prefs))
        assertEquals(IslandPrefs.NameLinger.Normal, IslandPrefs.nameLinger(prefs))
        assertEquals(3_200L, IslandPrefs.nameLinger(prefs).ms) // as long as the song's name always stayed
        prefs.edit().putInt(IslandPrefs.PREF_NAME_LINGER, 42).apply()
        assertEquals(IslandPrefs.NameLinger.Normal, IslandPrefs.nameLinger(prefs))
        assertTrue(IslandPrefs.NameLinger.Short.ms < IslandPrefs.NameLinger.Normal.ms && IslandPrefs.NameLinger.Normal.ms < IslandPrefs.NameLinger.Long.ms)
    }

    // ---- The Android side: a message in, a moment out ----

    @Before fun setUp() {
        SoundSource.resetForTest()
        prefs.edit().clear().commit()
        SoundSource.roleOverride = { Role.App }
        SoundSource.attach(app)
    }

    @After fun tearDown() = SoundSource.resetForTest()

    @Test fun aMessageBecomesAMomentWithTheAppAndTheSenderOnlyIfAsked() {
        SoundSource.notificationPosted(post(title = "Alex"))
        val m = SoundSource.message.value
        assertNotNull(m)
        assertEquals("com.chat", m!!.pkg)
        assertEquals("Alex", m.title)
        assertTrue(m.at > 0L)
    }

    @Test fun theMessageAlsoExplainsASoundThatFollows() {
        SoundSource.notificationPosted(post())
        SoundSource.usagesChanged(listOf(android.media.AudioAttributes.USAGE_NOTIFICATION))
        assertEquals("com.chat", SoundSource.heard.value?.pkg)
    }

    @Test fun anAppSwitchedOffOrInUseOrUnknownEarnsNoMoment() {
        IslandPrefs.setSoundIgnored(prefs, "com.quiet", true)
        SoundSource.notificationPosted(post(pkg = "com.quiet", key = "q"))
        assertNull(SoundSource.message.value)
        // The app on screen right now: nothing new to tell you.
        SoundSource.windowChanged("com.chat")
        SoundSource.notificationPosted(post(key = "inuse"))
        assertNull(SoundSource.message.value)
        // With the rule off, it pops.
        prefs.edit().putBoolean(IslandPrefs.PREF_SKIP_IN_USE, false).apply()
        SoundSource.notificationPosted(post(key = "inuse2"))
        assertNotNull(SoundSource.message.value)
    }

    @Test fun theSwitchesAreHonoured() {
        prefs.edit().putBoolean(IslandPrefs.PREF_MSG, false).apply()
        SoundSource.notificationPosted(post())
        assertNull(SoundSource.message.value)
        prefs.edit().putBoolean(IslandPrefs.PREF_MSG, true).apply()
        SoundSource.notificationPosted(post(interrupts = false, key = "dnd"))
        assertNull("held back by Do Not Disturb", SoundSource.message.value)
        SoundSource.notificationPosted(post(importance = 2, key = "muted"))
        assertNull("a muted chat", SoundSource.message.value)
    }

    @Test fun aBurstFromOneChatPopsOnceAndKeepsTheSameStart() {
        SoundSource.notificationPosted(post(key = "a"))
        val first = SoundSource.message.value!!
        // The same notification updated a moment later (a second message in the chat): same news.
        SoundSource.notificationPosted(post(key = "a"))
        assertEquals(first, SoundSource.message.value)
        // Another chat right after: a new message, but the same burst, so the island doesn't re-announce itself.
        SoundSource.notificationPosted(post(key = "b", pkg = "com.other"))
        val second = SoundSource.message.value!!
        assertEquals("com.other", second.pkg)
        assertEquals(first.startedAt, second.startedAt)
    }
}
