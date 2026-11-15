package me.kavishdevar.librepods

import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxWidth
import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import androidx.compose.material3.Text
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import me.kavishdevar.librepods.data.Battery
import me.kavishdevar.librepods.data.BatteryComponent
import me.kavishdevar.librepods.data.BatteryStatus
import me.kavishdevar.librepods.presentation.components.BatteryView
import me.kavishdevar.librepods.presentation.glint.BatteryRing
import me.kavishdevar.librepods.presentation.glint.PodsVideoConfig
import me.kavishdevar.librepods.presentation.glint.SymbolText
import org.junit.Before
import me.kavishdevar.librepods.presentation.overlays.CardGeometry
import me.kavishdevar.librepods.presentation.overlays.CardHost
import me.kavishdevar.librepods.presentation.overlays.GlintOverlays
import me.kavishdevar.librepods.presentation.overlays.IslandEvent
import me.kavishdevar.librepods.services.BatteryEstimate
import me.kavishdevar.librepods.services.BatteryEstimator
import me.kavishdevar.librepods.services.BatteryTimeLeft
import me.kavishdevar.librepods.services.GlintStatus
import me.kavishdevar.librepods.services.HeartRate
import me.kavishdevar.librepods.services.LinkState
import me.kavishdevar.librepods.services.NowPlaying
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import me.kavishdevar.librepods.presentation.overlays.IslandGeometry
import me.kavishdevar.librepods.presentation.overlays.IslandHost
import me.kavishdevar.librepods.presentation.overlays.IslandPhase
import me.kavishdevar.librepods.presentation.overlays.PodsSnapshot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders Glint's artwork and overlays to PNGs (android/app/build/screenshots) so the visuals
 * can be reviewed without a phone. Robolectric has no compositor, so the glass is drawn in its
 * no-blur fallback here; on a phone with window blur enabled the fill is more translucent.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = Application::class, qualifiers = "w412dp-h915dp-xxhdpi")
class GlintScreenshots {
    @get:Rule val rule = createComposeRule()

    private val out = "build/screenshots"

    private val demo = PodsSnapshot(
        name = "Jake's AirPods Pro", left = 82, right = 78, case = 54,
        leftCharging = false, rightCharging = false, caseCharging = true,
        leftInEar = true, rightInEar = true, lidOpen = false, listeningMode = 4,
    )

    @Composable
    private fun Wallpaper(dark: Boolean, content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit) {
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    drawRect(
                        Brush.linearGradient(
                            if (dark) listOf(Color(0xFF0B1A33), Color(0xFF3A1446), Color(0xFF071F1C))
                            else listOf(Color(0xFFFFD6A5), Color(0xFFBDE0FE), Color(0xFFCDB4DB)),
                            Offset.Zero, Offset(size.width, size.height)
                        )
                    )
                    // Some shapes so glass legibility can be judged against busy content.
                    for (i in 0 until 7) {
                        drawCircle(
                            Color(if (dark) 0x33FFFFFF else 0x55FFFFFF),
                            radius = size.width * (0.08f + i * 0.03f),
                            center = Offset(size.width * ((i * 37 % 100) / 100f), size.height * ((i * 53 % 100) / 100f))
                        )
                    }
                }
        ) { content() }
    }

    @Before
    fun stillFramesOnly() {
        me.kavishdevar.librepods.services.DeviceChoice.reset()
        // No video decoder under Robolectric: overlays show the clips' still frames.
        PodsVideoConfig.enabled = false
    }

    /** Every replacement for the old SF Symbols, large and at text size, plus battery rings. */
    @Test
    fun symbols() {
        val all = listOf(
            "􀁡", "􀆄", "􀆅", "􀈟", "􀊃", "􀊄",
            "􀊅", "􀊆", "􀊡", "􀊥", "􀊩", "􀋦",
            "􀍟", "􀯶", "􀯻", "􀹬", "􁣥", "􁣨",
        )
        rule.setContent {
            Column(
                Modifier.fillMaxSize().background(Color(0xFFF2F2F7)).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                all.chunked(6).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        row.forEach { SymbolText(it, TextStyle(fontSize = 34.sp, color = Color.Black)) }
                    }
                }
                all.chunked(9).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Aa", style = TextStyle(fontSize = 17.sp))
                        row.forEach { SymbolText(it, TextStyle(fontSize = 17.sp, color = Color(0xFF0A84FF))) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    BatteryRing(82, false, size = 48.dp, label = Color.Black, track = Color(0x1F000000))
                    BatteryRing(18, false, size = 48.dp, label = Color.Black, track = Color(0x1F000000))
                    BatteryRing(8, false, size = 48.dp, label = Color.Black, track = Color(0x1F000000))
                    BatteryRing(54, true, size = 48.dp, label = Color.Black, track = Color(0x1F000000))
                }
            }
        }
        rule.onRoot().captureRoboImage("$out/symbols.png")
    }

    private fun island(name: String, event: IslandEvent, phase: IslandPhase, dark: Boolean = true, tall: Boolean = false, snapshot: PodsSnapshot = demo, then: () -> Unit = {}) {
        if (dark) RuntimeEnvironment.setQualifiers("+night")
        GlintOverlays.updateSnapshot(snapshot)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val geo = IslandGeometry(RuntimeEnvironment.getApplication(), tall = event == IslandEvent.TimerDone)
            Wallpaper(dark) {
                Box(Modifier.padding(top = 12.dp).size(with(androidx.compose.ui.platform.LocalDensity.current) {
                    val s = if (tall) geo.detailWindow else if (phase == IslandPhase.Expanded) geo.expandedWindow else geo.compactWindow
                    androidx.compose.ui.unit.DpSize(s.width.toDp(), s.height.toDp())
                }).align(Alignment.TopCenter)) {
                    IslandHost(geo, event, phase, 0, blurAllowed = false, onPhase = {}, onWindowSize = {}, onGone = {})
                }
            }
        }
        // Long enough for the heart chip to finish budding out of play/pause (it starts at 1 s),
        // so the pictures show the settled island rather than the middle of that animation.
        rule.mainClock.advanceTimeBy(3_200)
        then()
        rule.onRoot().captureRoboImage("$out/$name.png")
    }

    /**
     * The mini island around a punch-hole camera (drawn here as a dark lens, the size of a
     * typical front camera), compact or widened with the song's name.
     */


    // ---- Off: the tiny ring round the camera (music playing underneath, nothing shown) ----

    // ---- The iPhone look (Apple's numbers: services/IslandSpec.kt) ----

    @Test fun islandHeartChipMorph() {
        me.kavishdevar.librepods.services.GlintStatus.set(me.kavishdevar.librepods.services.LinkState.Connected("AirPods Pro"))
        GlintOverlays.updateSnapshot(demo)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val geo = IslandGeometry(RuntimeEnvironment.getApplication())
            Wallpaper(true) {
                Box(Modifier.padding(top = 12.dp).size(with(androidx.compose.ui.platform.LocalDensity.current) {
                    androidx.compose.ui.unit.DpSize(geo.expandedWindow.width.toDp(), geo.expandedWindow.height.toDp())
                }).align(Alignment.TopCenter)) {
                    IslandHost(geo, IslandEvent.Music, IslandPhase.Expanded, 0, blurAllowed = false, onPhase = {}, onWindowSize = {}, onGone = {})
                }
            }
        }
        var t = 0L
        listOf(1_000L, 1_100L, 1_220L, 1_330L, 1_450L, 1_600L, 3_000L).forEach { at ->
            rule.mainClock.advanceTimeBy(at - t); t = at
            rule.onRoot().captureRoboImage("$out/island_chip_morph_$at.png")
        }
    }

    /** A small phone with the biggest system font: the opened island still fits. */
    @Test @org.robolectric.annotation.Config(qualifiers = "w320dp-h640dp-xhdpi")
    fun islandSmallPhoneBigFont() = connectedLink {
        RuntimeEnvironment.setFontScale(2f)
        island("island_small_big_font", IslandEvent.Connected, IslandPhase.Expanded, dark = false)
    }

    @Test fun islandConnected() = island("island_connected", IslandEvent.Connected, IslandPhase.Compact)

    // Beats Solo 4 (or any other headphones) chosen: drawn headphones and one battery ring,
    // no heart chip, no listening mode.
    private val solo4 = PodsSnapshot(name = "Beats Solo 4", left = 70, right = 70, case = null, headphones = true)
    private fun withSolo4(block: () -> Unit) {
        val app = RuntimeEnvironment.getApplication()
        val device = me.kavishdevar.librepods.services.ChosenDevice(me.kavishdevar.librepods.services.DeviceKind.BEATS_SOLO_4, "00:11:22:33:44:55", "Beats Solo 4")
        me.kavishdevar.librepods.services.DeviceChoice.choose(app, device)
        me.kavishdevar.librepods.services.HeadphoneLink.preview(me.kavishdevar.librepods.services.HeadphoneState(device, connected = true, battery = 70))
        try { block() } finally {
            me.kavishdevar.librepods.services.DeviceChoice.choose(app, me.kavishdevar.librepods.services.ChosenDevice(me.kavishdevar.librepods.services.DeviceKind.AIRPODS, "", "AirPods"))
            me.kavishdevar.librepods.services.HeadphoneLink.preview(me.kavishdevar.librepods.services.HeadphoneState())
        }
    }
    @Test fun islandHeadphonesConnected() = withSolo4 { island("island_headphones_connected", IslandEvent.Connected, IslandPhase.Compact, snapshot = solo4) }
    @Test fun islandHeadphonesExpandedDark() = withSolo4 { island("island_headphones_expanded_dark", IslandEvent.Connected, IslandPhase.Expanded, snapshot = solo4) }
    @Test fun islandHeadphonesExpandedLight() = withSolo4 { island("island_headphones_expanded_light", IslandEvent.Connected, IslandPhase.Expanded, dark = false, snapshot = solo4) }
    @Test fun islandHeadphonesLow() = withSolo4 { island("island_headphones_low", IslandEvent.LowBattery(10), IslandPhase.Compact, dark = false, snapshot = solo4.copy(left = 10, right = 10)) }
    @Test fun islandLowBattery() = island("island_low_battery", IslandEvent.LowBattery(9), IslandPhase.Compact, dark = false)
    @Test fun islandMode() = island("island_mode", IslandEvent.ListeningMode(2), IslandPhase.Compact)
    @Test fun islandExpanded() = island("island_expanded", IslandEvent.MovedToDevice("iPad", canTakeBack = true), IslandPhase.Expanded, dark = false)
    @Test fun islandExpandedConnected() {
        me.kavishdevar.librepods.services.GlintStatus.set(me.kavishdevar.librepods.services.LinkState.Connected("AirPods Pro"))
        me.kavishdevar.librepods.services.BatteryTimeLeft.publish(
            me.kavishdevar.librepods.services.BatteryEstimate(197, me.kavishdevar.librepods.services.BatteryEstimator.Confidence.MEASURED, worn = true, charging = false, caseCharges = 1.5)
        )
        island("island_expanded_connected", IslandEvent.Connected, IslandPhase.Expanded)
        me.kavishdevar.librepods.services.BatteryTimeLeft.publish(null)
        me.kavishdevar.librepods.services.GlintStatus.set(me.kavishdevar.librepods.services.LinkState.Idle)
    }

    // ---- Round 19: more moments, music controls, the heart ----

    private val demoArt by lazy {
        val bmp = android.graphics.Bitmap.createBitmap(144, 144, android.graphics.Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(bmp)
        val p = android.graphics.Paint().apply {
            shader = android.graphics.LinearGradient(0f, 0f, 144f, 144f, 0xFF2D6CDF.toInt(), 0xFFE0607E.toInt(), android.graphics.Shader.TileMode.CLAMP)
        }
        c.drawRect(0f, 0f, 144f, 144f, p)
        c.drawCircle(72f, 72f, 34f, android.graphics.Paint().apply { color = 0x66FFFFFF })
        bmp.asImageBitmap()
    }

    private fun withMusic(art: Boolean = true, playing: Boolean = true, block: () -> Unit) {
        NowPlaying.preview(NowPlaying.Track(playing = playing, title = "Midnight City", artist = "M83", app = "Spotify", art = if (art) demoArt else null, fromSession = true))
        try { block() } finally { NowPlaying.preview(NowPlaying.Track()) }
    }

    private fun withHeart(bpm: Int, block: () -> Unit) {
        val now = System.currentTimeMillis()
        listOf(-14, -6, 9, 0).forEachIndexed { i, d -> HeartRate.reading(bpm + d, now - (3 - i) * 1_000L) }
        try { block() } finally { HeartRate.status(HeartRate.Status.Off) }
    }

    private fun connectedLink(block: () -> Unit) {
        GlintStatus.set(LinkState.Connected("AirPods Pro"))
        BatteryTimeLeft.publish(BatteryEstimate(197, BatteryEstimator.Confidence.MEASURED, worn = true, charging = false, caseCharges = 1.5))
        try { block() } finally { BatteryTimeLeft.publish(null); GlintStatus.set(LinkState.Idle) }
    }

    /** The opened island's heart in each honest state (rendered after the chip has settled). */


    private fun rimSwing(name: String, degrees: Float) {
        me.kavishdevar.librepods.presentation.glint.GlintLight.swing.floatValue = degrees
        try { island(name, IslandEvent.Connected, IslandPhase.Compact, dark = false) }
        finally { me.kavishdevar.librepods.presentation.glint.GlintLight.swing.floatValue = 0f }
    }
    @Test fun islandRimSwingLeft() = rimSwing("island_rim_swing_left", -14f)
    @Test fun islandRimSwingLevel() = rimSwing("island_rim_swing_level", 0f)
    @Test fun islandRimSwingRight() = rimSwing("island_rim_swing_right", 14f)

    /** A finger on the opened island's play button: it swells and lights up under the finger. */
    @Test fun islandButtonPressed() = connectedLink {
        withMusic {
            island("island_button_pressed", IslandEvent.Connected, IslandPhase.Expanded) {
                rule.mainClock.advanceTimeBy(2_400)
                rule.onNodeWithContentDescription("Pause").performTouchInput { down(center) }
                rule.mainClock.advanceTimeBy(180)
            }
        }
    }

    @Test fun islandBudOut() = withMusic(playing = false) { island("island_bud_out", IslandEvent.BudOut(remaining = 1, paused = true), IslandPhase.Compact) }
    @Test fun islandBothOutLight() = island("island_both_out_light", IslandEvent.BudOut(remaining = 0, paused = false), IslandPhase.Compact, dark = false)
    @Test fun islandMusic() = withMusic { island("island_music", IslandEvent.Music, IslandPhase.Compact) }
    @Test fun islandMusicNoAccess() {
        NowPlaying.preview(NowPlaying.Track(playing = true))
        island("island_music_no_names_light", IslandEvent.Music, IslandPhase.Compact, dark = false)
        NowPlaying.preview(NowPlaying.Track())
    }
    @Test fun islandCharging() = island("island_charging", IslandEvent.Charging, IslandPhase.Compact)
    @Test fun islandExpandedMusicHeart() = connectedLink { withMusic { withHeart(72) { island("island_expanded_music_heart", IslandEvent.Connected, IslandPhase.Expanded) } } }
    @Test fun islandExpandedMusicLight() = connectedLink { withMusic(art = false, playing = false) { island("island_expanded_music_light", IslandEvent.BudOut(1, true), IslandPhase.Expanded, dark = false) } }
    @Test fun islandExpandedHeartLight() = connectedLink { withMusic { withHeart(128) { island("island_expanded_heart_light", IslandEvent.Connected, IslandPhase.Expanded, dark = false) } } }

    @Test fun islandHeartDetail() = connectedLink {
        withHeart(72) {
            island("island_heart_detail", IslandEvent.Connected, IslandPhase.Expanded, tall = true) {
                rule.mainClock.advanceTimeBy(1_000) // the chip lands about 1.6 s after opening
                rule.onNodeWithContentDescription("Heart rate 72", substring = true).performClick()
                rule.mainClock.advanceTimeBy(900)
            }
        }
    }

    @Test fun islandHeartDetailLight() = connectedLink {
        withHeart(128) {
            island("island_heart_detail_light", IslandEvent.Connected, IslandPhase.Expanded, dark = false, tall = true) {
                rule.mainClock.advanceTimeBy(1_000) // the chip lands about 1.6 s after opening
                rule.onNodeWithContentDescription("Heart rate 128", substring = true).performClick()
                rule.mainClock.advanceTimeBy(900)
            }
        }
    }

    private fun card(name: String, dark: Boolean) {
        if (dark) RuntimeEnvironment.setQualifiers("+night")
        GlintOverlays.updateSnapshot(demo)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val geo = CardGeometry(RuntimeEnvironment.getApplication())
            Wallpaper(dark) {
                Box(Modifier.align(Alignment.BottomCenter).size(with(androidx.compose.ui.platform.LocalDensity.current) {
                    androidx.compose.ui.unit.DpSize(geo.window.width.toDp(), geo.window.height.toDp())
                })) {
                    CardHost(geo, leaving = false, generation = 0, blurAllowed = false, onLeave = {}, onGone = {})
                }
            }
        }
        rule.mainClock.advanceTimeBy(1_500)
        rule.onRoot().captureRoboImage("$out/$name.png")
    }

    @Test fun cardLight() = card("card_light", dark = false)
    @Test fun cardDark() = card("card_dark", dark = true)

    @Test
    fun heroAndStatus() {
        GlintOverlays.updateSnapshot(demo)
        me.kavishdevar.librepods.services.GlintStatus.set(me.kavishdevar.librepods.services.LinkState.Idle)
        rule.setContent {
            me.kavishdevar.librepods.presentation.theme.LibrePodsTheme(m3eEnabled = false) {
                Column(
                    Modifier.fillMaxSize().background(androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainer).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    BatteryView(
                        batteryList = listOf(
                            Battery(BatteryComponent.LEFT, 82, BatteryStatus.NOT_CHARGING),
                            Battery(BatteryComponent.RIGHT, 64, BatteryStatus.CHARGING),
                            Battery(BatteryComponent.CASE, 54, BatteryStatus.NOT_CHARGING),
                        ),
                        budsRes = R.drawable.airpods_pro_2_buds,
                        caseRes = R.drawable.airpods_pro_2_case,
                    )
                    me.kavishdevar.librepods.presentation.glint.ConnectionStatusPanel(onTroubleshoot = {})
                }
            }
        }
        rule.onRoot().captureRoboImage("$out/main_and_status.png")
    }

    @Test
    fun icon() {
        rule.setContent {
            Column(Modifier.background(Color(0xFF9FB4D8)).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    me.kavishdevar.librepods.presentation.theme.AppIcon.entries.forEach {
                        me.kavishdevar.librepods.presentation.components.IconPreview(it, 112.dp)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    me.kavishdevar.librepods.presentation.theme.AppIcon.entries.forEach {
                        me.kavishdevar.librepods.presentation.components.IconPreview(it, 48.dp)
                    }
                    Box(Modifier.size(48.dp).androidx_clip().background(Color(0xFF2B3A55))) {
                        androidx.compose.foundation.Image(
                            androidx.compose.ui.res.painterResource(R.drawable.ic_launcher_monochrome), null,
                            Modifier.fillMaxSize().graphicsLayer { scaleX = 1.5f; scaleY = 1.5f }
                        )
                    }
                }
            }
        }
        rule.onRoot().captureRoboImage("$out/icon.png")
    }

    private fun Modifier.androidx_clip() = this.clip(androidx.compose.foundation.shape.CircleShape)

    @Suppress("unused") private val keep = IntSize.Zero

    // ---- Any sound, with the app's icon; nothing on shows the phone's battery and the time ----
    private val alert = me.kavishdevar.librepods.services.SoundRules.Kind.Alert

    @Test fun phonePageLight() = phonePage("phone_page_light", dark = false)
    @Test fun phonePageDark() = phonePage("phone_page_dark", dark = true)

    private fun phonePage(name: String, dark: Boolean) {
        if (dark) RuntimeEnvironment.setQualifiers("+night")
        me.kavishdevar.librepods.services.PhoneStatus.preview(me.kavishdevar.librepods.services.PhoneStatus.Info(72, false))
        rule.mainClock.autoAdvance = false
        rule.setContent {
            me.kavishdevar.librepods.presentation.theme.LibrePodsTheme(m3eEnabled = false) {
                me.kavishdevar.librepods.presentation.screens.PhoneScreen(navigateToIsland = {})
            }
        }
        rule.mainClock.advanceTimeBy(1_500)
        rule.onRoot().captureRoboImage("$out/$name.png")
    }

    // ---- The phone-first Dynamic Island (session 2026-10-07) ----

    /** A made-up app icon: a coloured rounded square with a white mark (Robolectric has no real apps). */
    private fun fakeIcon(color: Int, mark: String): androidx.compose.ui.graphics.ImageBitmap {
        val b = android.graphics.Bitmap.createBitmap(96, 96, android.graphics.Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(b)
        val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        c.drawRect(0f, 0f, 96f, 96f, p)
        p.color = android.graphics.Color.WHITE
        p.textSize = 54f; p.textAlign = android.graphics.Paint.Align.CENTER; p.isFakeBoldText = true
        c.drawText(mark, 48f, 66f, p)
        return b.asImageBitmap()
    }
    private val wall = listOf(Color(0xFF3A6EA5), Color(0xFFE0A458), Color(0xFF7FB7BE))
    private val calmPhone = me.kavishdevar.librepods.services.PhoneStatus.Info(80, false)

    // ---- 2026-10-11: everything fits (Jake's item 9): long names, a narrow phone, big text ----
    private val longTitle = "Pneumonoultramicroscopic Silicovolcanoconiosis (Extended Club Mix)"
    private val longArtist = "The Very Long Band Name featuring Several Other Artists"
    private fun stress(block: () -> Unit) {
        RuntimeEnvironment.setQualifiers("w320dp-h640dp")
        RuntimeEnvironment.setFontScale(1.3f)
        try { block() } finally { RuntimeEnvironment.setFontScale(1f) }
    }
    @Test fun fitHeadphonesLongName() = stress { withSolo4 { island("fit_headphones", IslandEvent.Connected, IslandPhase.Expanded, snapshot = solo4.copy(name = "Jake's Beats Solo 4 Wireless Headphones")) } }
    private fun arrow(): androidx.compose.ui.graphics.ImageBitmap {
        val b = android.graphics.Bitmap.createBitmap(96, 96, android.graphics.Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(b)
        val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1A73E8.toInt() }
        c.drawRoundRect(0f, 0f, 96f, 96f, 26f, 26f, p)
        p.color = android.graphics.Color.WHITE; p.style = android.graphics.Paint.Style.STROKE; p.strokeWidth = 10f; p.strokeCap = android.graphics.Paint.Cap.ROUND
        c.drawPath(android.graphics.Path().apply { moveTo(34f, 76f); lineTo(34f, 46f); lineTo(60f, 46f) }, p)
        p.style = android.graphics.Paint.Style.FILL
        c.drawPath(android.graphics.Path().apply { moveTo(58f, 32f); lineTo(76f, 46f); lineTo(58f, 60f); close() }, p)
        return b.asImageBitmap()
    }

    @Test fun freezeCardAndBanner() {
        RuntimeEnvironment.setQualifiers("+night")
        rule.mainClock.autoAdvance = false
        val report = me.kavishdevar.librepods.services.FreezeReport.Report(
            1_790_000_000_000L, "Stopped responding", "pro report\nWhen: 2026-10-06 12:00:00\nWhat: Stopped responding",
        )
        rule.setContent {
            Box(Modifier.fillMaxSize().background(Color.Black).padding(16.dp)) {
                Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
                    me.kavishdevar.librepods.presentation.components.FreezeBanner(preset = true)
                    me.kavishdevar.librepods.presentation.screens.FreezeCard(Color.White, true, preset = listOf(report))
                    me.kavishdevar.librepods.presentation.screens.FreezeCard(Color.White, true, preset = emptyList())
                }
            }
        }
        rule.mainClock.advanceTimeBy(1_500)
        rule.onRoot().captureRoboImage("$out/freeze_card_dark.png")
    }
}
