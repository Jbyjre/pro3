package me.kavishdevar.librepods

import me.kavishdevar.librepods.services.AppActions
import me.kavishdevar.librepods.services.AppKinds
import me.kavishdevar.librepods.services.AppKinds.Kind
import me.kavishdevar.librepods.services.GameStats
import me.kavishdevar.librepods.services.MusicMemory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 2026-10-11: the island knows what kind of app you're in, and offers what fits (Jake's item 3). */
class AppAwareTest {
    @Test fun wellKnownAppsAreKnownByName() {
        assertEquals(Kind.Game, AppKinds.classify("com.activision.callofduty.shooter"))
        assertEquals(Kind.Music, AppKinds.classify("com.spotify.music"))
        assertEquals(Kind.Video, AppKinds.classify("com.netflix.mediaclient"))
        assertEquals(Kind.Chat, AppKinds.classify("com.whatsapp"))
        assertEquals(Kind.Maps, AppKinds.classify("com.google.android.apps.maps"))
        assertEquals(Kind.Work, AppKinds.classify("com.anthropic.claude"))
    }

    @Test fun aMakersOtherGamesAreGamesToo() {
        // Never listed one by one: the maker's prefix covers them.
        assertEquals(Kind.Game, AppKinds.classify("com.supercell.hayday"))
        assertEquals(Kind.Game, AppKinds.classify("com.gameloft.android.ANMP.GloftDMHM"))
        assertEquals(Kind.Game, AppKinds.classify("com.ea.gp.nfsnl"))
    }

    @Test fun anyAppFallsBackToWhatItTellsAndroid() {
        assertEquals(Kind.Game, AppKinds.classify("com.unknown.studio.title", isGame = true))
        assertEquals(Kind.Game, AppKinds.classify("com.unknown.studio.title", AppKinds.AndroidCategory.GAME))
        assertEquals(Kind.Music, AppKinds.classify("com.unknown.x", AppKinds.AndroidCategory.AUDIO))
        assertEquals(Kind.Maps, AppKinds.classify("com.unknown.x", AppKinds.AndroidCategory.MAPS))
        // The table wins over a wrong category.
        assertEquals(Kind.Chat, AppKinds.classify("com.whatsapp", AppKinds.AndroidCategory.SOCIAL))
    }

    @Test fun thenItsNameDecides() {
        assertEquals(Kind.Music, AppKinds.classify("com.example.radio.fm"))
        assertEquals(Kind.Listen, AppKinds.classify("com.example.podcasts"))
        assertEquals(Kind.Video, AppKinds.classify("com.example.videoplayer"))
        assertEquals(Kind.Game, AppKinds.classify("com.tiny.games.chess2"))
        // "facebook" isn't a book, "small" isn't a mall.
        assertEquals(Kind.Other, AppKinds.classify("com.facebook.unknownthing"))
        assertEquals(Kind.Other, AppKinds.classify("com.small.tools"))
        assertEquals(Kind.Other, AppKinds.classify("com.example.calculator"))
    }

    @Test fun everyKindGetsFiveButtonsAndGamesGetGameOnes() {
        for (k in Kind.entries) assertEquals("$k", 5, AppActions.row(k).size)
        assertEquals(5, AppActions.row(null).size)
        val game = AppActions.row(Kind.Game)
        assertTrue(AppActions.Action.Mute in game && AppActions.Action.Quiet in game && AppActions.Action.Chat in game)
        assertEquals(AppActions.PHONE, AppActions.row(Kind.Other))
        assertEquals(AppActions.Action.Resume, AppActions.row(Kind.Music).first())
    }

    @Test fun aMusicAppWithNothingPlayingGetsItsOwnCard() {
        assertTrue(AppActions.musicIdle(Kind.Music, playing = false))
        assertTrue(AppActions.musicIdle(Kind.Listen, playing = false))
        assertFalse(AppActions.musicIdle(Kind.Music, playing = true))
        assertFalse(AppActions.musicIdle(Kind.Game, playing = false))
        assertFalse(AppActions.musicIdle(null, playing = false))
    }

    @Test fun discordLeadsWhileGamingButNeverTheAppYoureIn() {
        val installed = setOf("com.discord", "com.whatsapp")
        assertEquals("com.discord", AppActions.chatApp({ it in installed }, forGame = true, front = "com.supercell.clashroyale"))
        assertEquals("com.whatsapp", AppActions.chatApp({ it in installed }, forGame = false, front = null))
        assertEquals("com.whatsapp", AppActions.chatApp({ it in installed }, forGame = true, front = "com.discord"))
        assertNull(AppActions.chatApp({ false }, forGame = true, front = null))
        assertEquals("Discord", AppActions.chatLabel("com.discord"))
        assertEquals("Chat", AppActions.chatLabel(null))
    }

    @Test fun gameStatsInPlainWords() {
        assertEquals("Cool · 36.2°C · 64%", GameStats.line(0, 362, 64))
        assertEquals("Hot · 64%", GameStats.line(2, null, 64))
        assertTrue(GameStats.throttling(2))
        assertFalse(GameStats.throttling(1))
        assertEquals("Just started", GameStats.played(30_000L))
        assertEquals("Playing for 42 min", GameStats.played(42 * 60_000L))
        assertEquals("Playing for 1 h 05 min", GameStats.played(65 * 60_000L))
        assertNull(GameStats.temperature(5_000))
    }

    @Test fun recentSongsKeepTheNewestFewOnce() {
        var list = emptyList<MusicMemory.Song>()
        for (t in listOf("A", "B", "C", "B", "D", "E")) list = MusicMemory.add(list, MusicMemory.Song(t, "X"))
        assertEquals(listOf("E", "D", "B", "C"), list.map { it.title })
        assertEquals(list, MusicMemory.decode(MusicMemory.encode(list)))
        assertEquals(listOf(MusicMemory.Song("Solo", null)), MusicMemory.decode(MusicMemory.encode(listOf(MusicMemory.Song("Solo", null)))))
        assertTrue(MusicMemory.decode(null).isEmpty())
    }
}
