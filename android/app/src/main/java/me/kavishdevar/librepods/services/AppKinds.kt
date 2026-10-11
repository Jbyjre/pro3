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

/**
 * What kind of app is in front, for every app on the phone, so the Dynamic Island can offer
 * controls that fit it (a game gets mute, Do Not Disturb and its stats; a music app with nothing
 * playing gets Resume, Search and your recent songs) instead of the same card everywhere.
 *
 * One rule for all apps, in this order (the first that knows wins):
 * 1. [KNOWN]: well-known apps by their exact package name (a few hundred of the most used).
 * 2. [MAKERS]: game and app makers by the start of their package names (every Supercell,
 *    Tencent, Activision, EA, Gameloft, King ... game, thousands of titles between them).
 * 3. Android's own category, which each app declares for itself (`android:appCategory`, read by
 *    the caller from ApplicationInfo.category), and the "is a game" flag.
 * 4. Words in the package name ("music", "radio", "podcast", "video", "maps", "browser" ...).
 * 5. Otherwise [Kind.Other], which keeps the phone's own controls.
 *
 * Pure, so it is unit-tested. Package names in the tables are the public Play Store ones as
 * known on 2026-10-11; they were not each checked on a phone (not verified), and a wrong or
 * missing entry only means step 3 or 4 decides instead.
 */
object AppKinds {
    enum class Kind(val label: String) {
        Game("Game"),
        Music("Music"),
        /** Podcasts and audiobooks. */
        Listen("Podcasts and books"),
        Video("Video"),
        Chat("Messages"),
        Social("Social"),
        Browser("Browser"),
        Maps("Maps"),
        Camera("Camera"),
        Photos("Photos"),
        Work("Work"),
        Reading("Reading"),
        Fitness("Fitness"),
        Shopping("Shopping"),
        Other("App");

        /** Apps that play music or spoken audio: their idle card offers Resume, Search and recent songs. */
        val plays: Boolean get() = this == Music || this == Listen
    }

    /** Android's ApplicationInfo.CATEGORY_* values (stable public constants since Android 8). */
    object AndroidCategory {
        const val UNDEFINED = -1
        const val GAME = 0
        const val AUDIO = 1
        const val VIDEO = 2
        const val IMAGE = 3
        const val SOCIAL = 4
        const val NEWS = 5
        const val MAPS = 6
        const val PRODUCTIVITY = 7
        const val ACCESSIBILITY = 8
    }

    /**
     * The kind of [pkg]. [category] is the app's declared Android category (or
     * [AndroidCategory.UNDEFINED]); [isGame] its "is a game" flag.
     */
    fun classify(pkg: String, category: Int = AndroidCategory.UNDEFINED, isGame: Boolean = false): Kind {
        KNOWN[pkg]?.let { return it }
        MAKERS.firstOrNull { (prefix, _) -> pkg.startsWith(prefix, ignoreCase = true) }?.let { return it.second }
        if (isGame) return Kind.Game
        when (category) {
            AndroidCategory.GAME -> return Kind.Game
            AndroidCategory.AUDIO -> return Kind.Music
            AndroidCategory.VIDEO -> return Kind.Video
            AndroidCategory.IMAGE -> return Kind.Photos
            AndroidCategory.SOCIAL -> return Kind.Social
            AndroidCategory.NEWS -> return Kind.Reading
            AndroidCategory.MAPS -> return Kind.Maps
            AndroidCategory.PRODUCTIVITY -> return Kind.Work
        }
        return byWords(pkg)
    }

    /** Step 4: words in the package name, most telling first. */
    internal fun byWords(pkg: String): Kind {
        val p = pkg.lowercase()
        val parts = p.split('.', '_', '-')
        fun has(vararg w: String) = w.any { p.contains(it) }
        fun part(vararg w: String) = w.any { it in parts }
        return when {
            has("podcast", "audiobook", "audible") -> Kind.Listen
            has("videoplayer", "video", "movie", "stream") || part("tv", "iptv") -> Kind.Video
            has("music", "radio", "mp3", "audioplayer") || part("fm") -> Kind.Music
            has("game", "gaming") || part("arcade", "puzzle", "racing", "rpg", "casino") -> Kind.Game
            has("browser") -> Kind.Browser
            has("navigation", "maps") || part("navi", "gps") -> Kind.Maps
            has("camera") -> Kind.Camera
            has("gallery", "photo") -> Kind.Photos
            has("messenger", "messaging", "chat", "message") -> Kind.Chat
            has("mail", "office", "docs", "calendar", "notes") -> Kind.Work
            has("fitness", "workout", "running", "health") -> Kind.Fitness
            // Whole words only where a part would mislead ("facebook", "small").
            has("news", "reader", "kindle", "comic", "manga") || part("book", "books", "ebook") -> Kind.Reading
            has("shop") || part("store", "mall") -> Kind.Shopping
            else -> Kind.Other
        }
    }

    /** Step 2: makers whose package names start with these (checked in order). */
    internal val MAKERS: List<Pair<String, Kind>> = listOf(
        // Games
        "com.activision." to Kind.Game, "com.supercell." to Kind.Game, "com.king." to Kind.Game,
        "com.gameloft." to Kind.Game, "com.ea.gp." to Kind.Game, "com.ea.game." to Kind.Game, "com.ea.games." to Kind.Game,
        "com.tencent.ig" to Kind.Game, "com.tencent.tmgp" to Kind.Game, "com.tencent.lolm" to Kind.Game, "com.tencent.ig." to Kind.Game,
        "com.pubg." to Kind.Game, "com.krafton." to Kind.Game, "com.dts.freefire" to Kind.Game, "com.garena.game" to Kind.Game,
        "com.miHoYo." to Kind.Game, "com.HoYoverse." to Kind.Game, "com.hoyoverse." to Kind.Game, "com.cognosphere." to Kind.Game,
        "com.netease." to Kind.Game, "com.riotgames." to Kind.Game, "com.epicgames." to Kind.Game, "com.mojang." to Kind.Game,
        "com.roblox." to Kind.Game, "com.rovio." to Kind.Game, "com.zynga." to Kind.Game, "com.playrix." to Kind.Game,
        "com.miniclip." to Kind.Game, "com.outfit7." to Kind.Game, "com.kiloo." to Kind.Game, "com.imangi." to Kind.Game,
        "com.halfbrick." to Kind.Game, "com.nianticlabs." to Kind.Game, "com.nintendo." to Kind.Game, "com.square_enix." to Kind.Game,
        "com.bandainamco" to Kind.Game, "jp.co.bandainamco" to Kind.Game, "com.sega." to Kind.Game, "com.konami." to Kind.Game,
        "com.ubisoft." to Kind.Game, "com.blizzard." to Kind.Game, "com.scopely." to Kind.Game, "com.moonactive." to Kind.Game,
        "com.playtika." to Kind.Game, "com.voodoo." to Kind.Game, "com.ketchapp." to Kind.Game, "com.sybogames." to Kind.Game,
        "com.lilithgame." to Kind.Game, "com.lilithgames." to Kind.Game, "com.igg." to Kind.Game, "com.funplus." to Kind.Game,
        "com.mobile.legends" to Kind.Game, "com.moonton." to Kind.Game, "com.innersloth." to Kind.Game, "com.levelinfinite." to Kind.Game,
        "com.proximabeta." to Kind.Game, "com.nexon." to Kind.Game, "com.netmarble." to Kind.Game, "com.ncsoft." to Kind.Game,
        "com.com2us." to Kind.Game, "com.gravity." to Kind.Game, "com.yostar." to Kind.Game, "com.YoStarEN." to Kind.Game,
        "com.bethsoft." to Kind.Game, "com.feralinteractive." to Kind.Game, "com.rockstargames." to Kind.Game, "com.chess." to Kind.Game,
        "com.fingersoft." to Kind.Game, "com.ludo.king" to Kind.Game, "com.dreamgames." to Kind.Game, "com.peak." to Kind.Game,
        "com.tripledot." to Kind.Game, "com.unity3d." to Kind.Game, "com.gamedevltd." to Kind.Game, "com.axlebolt." to Kind.Game,
        "com.madfingergames." to Kind.Game, "com.wb.goog." to Kind.Game, "com.wb.mk" to Kind.Game, "com.firsttouchgames." to Kind.Game,
        "com.nekki." to Kind.Game, "com.naturalmotion." to Kind.Game, "com.hutchgames." to Kind.Game, "com.pixonic." to Kind.Game,
        "com.wargaming." to Kind.Game, "net.wargaming." to Kind.Game, "com.plarium." to Kind.Game, "com.huuuge." to Kind.Game,
        // Video makers
        "com.netflix." to Kind.Video, "com.disney." to Kind.Video, "com.hbo." to Kind.Video, "com.wbd." to Kind.Video,
        "com.amazon.avod" to Kind.Video, "tv.twitch." to Kind.Video, "com.plexapp." to Kind.Video, "com.crunchyroll." to Kind.Video,
        "com.hulu." to Kind.Video, "com.peacocktv." to Kind.Video, "com.cbs." to Kind.Video, "com.paramount." to Kind.Video,
        // Music makers
        "com.spotify." to Kind.Music, "com.soundcloud." to Kind.Music, "com.aspiro." to Kind.Music, "deezer." to Kind.Music,
        "com.pandora." to Kind.Music, "com.shazam." to Kind.Music, "com.iheartradio." to Kind.Music, "com.clearchannel.iheartradio" to Kind.Music,
    )

    /** Step 1: well-known apps by exact package name. */
    internal val KNOWN: Map<String, Kind> = buildMap {
        fun add(kind: Kind, vararg pkgs: String) = pkgs.forEach { put(it, kind) }
        add(
            Kind.Game,
            "com.activision.callofduty.shooter", "com.activision.callofduty.warzone", "com.tencent.ig", "com.pubg.imobile",
            "com.pubg.krmobile", "com.dts.freefireth", "com.dts.freefiremax", "com.supercell.clashofclans", "com.supercell.clashroyale",
            "com.supercell.brawlstars", "com.supercell.squad", "com.king.candycrushsaga", "com.king.candycrushsodasaga",
            "com.roblox.client", "com.mojang.minecraftpe", "com.miHoYo.GenshinImpact", "com.HoYoverse.hkrpgoversea",
            "com.innersloth.spacemafia", "com.mobile.legends", "com.riotgames.league.wildrift", "com.riotgames.legendsofruneterra",
            "com.kiloo.subwaysurf", "com.imangi.templerun2", "com.epicgames.fortnite", "com.nianticlabs.pokemongo",
            "com.ea.gp.fifamobile", "com.ea.gp.apexlegendsmobilefps", "com.ea.game.pvzfree_row", "com.rovio.angrybirds",
            "com.gameloft.android.ANMP.GloftA9HM", "com.ludo.king", "com.chess", "com.playrix.homescapes", "com.playrix.gardenscapes",
            "com.dreamgames.royalmatch", "com.outfit7.mytalkingtom2", "com.fingersoft.hillclimb", "com.halfbrick.fruitninjafree",
            "com.miniclip.eightballpool", "com.YoStarEN.Arknights", "com.axlebolt.standoff2", "com.garena.game.codm",
            "com.tencent.lolm", "com.levelinfinite.hotta.gp", "com.netease.newspike", "com.proximabeta.nikke",
            "com.nintendo.zaka", "com.nintendo.zara", "com.rockstargames.gtasa", "com.sega.sonicdash", "com.konami.pesam",
            "com.madfingergames.legends", "com.wb.goog.mkx", "com.firsttouchgames.dls7", "com.nekki.shadowfight3",
        )
        add(
            Kind.Music,
            "com.spotify.music", "com.spotify.lite", "com.google.android.apps.youtube.music", "com.apple.android.music",
            "com.amazon.mp3", "deezer.android.app", "com.aspiro.tidal", "com.soundcloud.android", "com.pandora.android",
            "com.shazam.android", "com.audiomack", "com.anghami", "com.jio.media.jiobeats", "com.gaana", "com.qobuz.music",
            "com.bandcamp.android", "tunein.player", "radiotime.player", "com.sec.android.app.music", "com.miui.player",
            "com.google.android.music", "in.krosbits.musicolet", "com.maxmpz.audioplayer", "com.kodarkooperativet.blackplayerfree",
            "com.foobar2000.foobar2000", "org.videolan.vlc.music", "com.napster.android", "com.melodis.midomiMusicIdentifier.freemium",
        )
        add(
            Kind.Listen,
            "au.com.shiftyjelly.pocketcasts", "fm.castbox.audiobook.radio.podcast", "com.google.android.apps.podcasts",
            "com.bambuna.podcastaddict", "app.antennapod", "de.danoeh.antennapod", "com.audible.application", "com.storytel",
            "com.overdrive.mobile.android.libby", "fm.player", "com.podbean.app.podcast", "com.stitcher.app",
        )
        add(
            Kind.Video,
            "com.google.android.youtube", "com.google.android.apps.youtube.kids", "com.netflix.mediaclient",
            "com.amazon.avod.thirdpartyclient", "com.disney.disneyplus", "com.wbd.stream", "com.hbo.hbonow", "tv.twitch.android.app",
            "com.hulu.plus", "com.plexapp.android", "com.crunchyroll.crunchyroid", "org.videolan.vlc", "com.mxtech.videoplayer.ad",
            "com.mxtech.videoplayer.pro", "com.peacocktv.peacockandroid", "com.cbs.app", "com.apple.atve.androidtv.appletv",
            "com.vimeo.android.videoapp", "com.dailymotion.dailymotion", "tv.pluto.android", "com.tubitv", "com.google.android.videos",
            "com.zhiliaoapp.musically.go", "is.xyz.mpv", "com.brouken.player",
        )
        add(
            Kind.Chat,
            "com.whatsapp", "com.whatsapp.w4b", "org.telegram.messenger", "org.telegram.messenger.web", "org.thoughtcrime.securesms",
            "com.facebook.orca", "com.discord", "com.Slack", "com.microsoft.teams", "com.google.android.apps.messaging",
            "com.samsung.android.messaging", "jp.naver.line.android", "com.viber.voip", "com.tencent.mm", "com.kakao.talk",
            "com.skype.raider", "com.google.android.apps.dynamite", "us.zoom.videomeetings", "com.google.android.apps.tachyon",
            "im.vector.app", "com.wire", "ch.threema.app", "com.imo.android.imoim", "org.telegram.plus",
        )
        add(
            Kind.Social,
            "com.instagram.android", "com.facebook.katana", "com.facebook.lite", "com.snapchat.android", "com.twitter.android",
            "com.reddit.frontpage", "com.pinterest", "com.linkedin.android", "com.zhiliaoapp.musically", "com.ss.android.ugc.trill",
            "com.instagram.barcelona", "com.tumblr", "com.bereal.ft", "xyz.blueskyweb.app", "org.joinmastodon.android",
        )
        add(
            Kind.Browser,
            "com.android.chrome", "com.chrome.beta", "org.mozilla.firefox", "org.mozilla.focus", "com.brave.browser",
            "com.microsoft.emmx", "com.sec.android.app.sbrowser", "com.opera.browser", "com.opera.mini.native",
            "com.duckduckgo.mobile.android", "com.vivaldi.browser", "com.kiwibrowser.browser", "com.google.android.googlequicksearchbox",
        )
        add(
            Kind.Maps,
            "com.google.android.apps.maps", "com.waze", "com.here.app.maps", "com.citymapper.app.release", "com.sygic.aura",
            "net.osmand", "net.osmand.plus", "app.organicmaps", "com.mapswithme.maps.pro", "com.ubercab", "com.lyft.android",
            "com.bolt.client", "com.transit", "com.moovit",
        )
        add(
            Kind.Camera,
            "com.google.android.GoogleCamera", "com.sec.android.app.camera", "com.android.camera", "com.android.camera2",
            "com.oneplus.camera", "com.motorola.camera3", "com.huawei.camera", "com.sonyericsson.android.camera",
        )
        add(
            Kind.Photos,
            "com.google.android.apps.photos", "com.sec.android.gallery3d", "com.miui.gallery", "com.android.gallery3d",
            "com.adobe.lrmobile", "com.vsco.cam", "com.picsart.studio", "com.lemon.lvoverseas",
        )
        add(
            Kind.Work,
            "com.google.android.gm", "com.microsoft.office.outlook", "com.google.android.apps.docs", "com.google.android.apps.docs.editors.docs",
            "com.google.android.apps.docs.editors.sheets", "com.google.android.apps.docs.editors.slides", "com.google.android.calendar",
            "com.google.android.keep", "notion.id", "com.microsoft.office.word", "com.microsoft.office.excel", "com.microsoft.office.officehubrow",
            "com.todoist", "com.ticktick.task", "com.evernote", "com.dropbox.android", "com.microsoft.skydrive", "com.trello",
            "com.anthropic.claude", "com.openai.chatgpt", "com.google.android.apps.bard", "com.microsoft.copilot", "com.github.android",
            "com.samsung.android.app.notes", "com.samsung.android.calendar", "com.samsung.android.email.provider",
        )
        add(
            Kind.Reading,
            "com.amazon.kindle", "com.google.android.apps.books", "com.google.android.apps.magazines", "flipboard.app",
            "com.medium.reader", "com.nytimes.android", "bbc.mobile.news.ww", "com.cnn.mobile.android.phone", "com.wattpad",
            "com.kobobooks.android", "com.scribd.app.reader0", "com.webtoons.android",
        )
        add(
            Kind.Fitness,
            "com.strava", "com.nike.plusgps", "com.google.android.apps.fitness", "com.sec.android.app.shealth", "com.fitbit.FitbitMobile",
            "com.myfitnesspal.android", "com.garmin.android.apps.connectmobile", "com.adidas.app", "com.peloton.callisto",
            "com.calm.android", "com.getsomeheadspace.android",
        )
        add(
            Kind.Shopping,
            "com.amazon.mShop.android.shopping", "com.ebay.mobile", "com.alibaba.aliexpresshd", "com.einnovation.temu",
            "com.zzkko", "com.etsy.android", "com.walmart.android", "com.target.ui", "com.ubercab.eats", "com.dd.doordash",
            "com.grubhub.android", "com.instacart.client", "com.contextlogic.wish",
        )
    }
}
