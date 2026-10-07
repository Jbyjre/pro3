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
            val geo = IslandGeometry(RuntimeEnvironment.getApplication(), tall = event == IslandEvent.Glance || event == IslandEvent.TimerDone)
            Wallpaper(dark) {
                Box(Modifier.padding(top = 12.dp).size(with(androidx.compose.ui.platform.LocalDensity.current) {
                    val s = if (tall) geo.detailWindow else if (phase == IslandPhase.Expanded) geo.expandedWindow else geo.compactWindow
                    androidx.compose.ui.unit.DpSize(s.width.toDp(), s.height.toDp())
                }).align(Alignment.TopCenter)) {
                    IslandHost(geo, event, phase, 0, blurAllowed = false, onPhase = {}, onWindowSize = {}, onGone = {})
                }
            }
        }
        rule.mainClock.advanceTimeBy(1_200)
        then()
        rule.onRoot().captureRoboImage("$out/$name.png")
    }

    /**
     * The mini island around a punch-hole camera (drawn here as a dark lens, the size of a
     * typical front camera), compact or widened with the song's name.
     */
    private fun mini(
        name: String, playing: Boolean, wide: Float, art: Boolean, dark: Boolean = false,
        /** "hole" (centred punch-hole), "notch", "corner" (top-left hole) or "none". */
        shape: String = "hole",
        airPods: PodsSnapshot? = null,
        heart: Int? = null,
        talking: Boolean = false,
        press: Float = 0f,
        ack: me.kavishdevar.librepods.presentation.overlays.MiniAck? = null,
        look: me.kavishdevar.librepods.services.IslandLook.Look = me.kavishdevar.librepods.services.IslandLook.Look(),
        situation: me.kavishdevar.librepods.services.IslandLook.Situation? = null,
        /** A sound that isn't music (Sounds look), or an alert popping over the music. */
        heard: me.kavishdevar.librepods.services.SoundSource.Heard? = null,
        content: me.kavishdevar.librepods.services.MiniIslandRules.Content? = null,
        phone: me.kavishdevar.librepods.services.PhoneStatus.Info? = null,
        place: me.kavishdevar.librepods.services.ScreenApp.Place = me.kavishdevar.librepods.services.ScreenApp.Place.Unknown,
        timer: me.kavishdevar.librepods.services.IslandTimer.State? = null,
        budsUp: Boolean = false,
        wallpaper: List<Color> = emptyList(),
        swap: Float? = null,
    ) {
        if (dark) RuntimeEnvironment.setQualifiers("+night")
        rule.mainClock.autoAdvance = false
        phone?.let { me.kavishdevar.librepods.services.PhoneStatus.preview(it) }
        val app = RuntimeEnvironment.getApplication()
        val d = app.resources.displayMetrics.density
        val screenW = app.resources.displayMetrics.widthPixels
        val hole = when (shape) {
            "notch" -> android.graphics.Rect((screenW / 2 - 80 * d).toInt(), 0, (screenW / 2 + 80 * d).toInt(), (30 * d).toInt())
            "corner" -> android.graphics.Rect((16 * d).toInt(), (10 * d).toInt(), (42 * d).toInt(), (36 * d).toInt())
            "none" -> null
            else -> android.graphics.Rect((screenW / 2 - 13 * d).toInt(), (10 * d).toInt(), (screenW / 2 + 13 * d).toInt(), (36 * d).toInt())
        }
        val cover = if (art) android.graphics.Bitmap.createBitmap(144, 144, android.graphics.Bitmap.Config.ARGB_8888).also { b ->
            val c = android.graphics.Canvas(b)
            c.drawPaint(android.graphics.Paint().apply {
                shader = android.graphics.LinearGradient(0f, 0f, 144f, 144f, 0xFFFF6A3D.toInt(), 0xFF7B2CBF.toInt(), android.graphics.Shader.TileMode.CLAMP)
            })
        }.asImageBitmap() else null
        val track = me.kavishdevar.librepods.services.NowPlaying.Track(
            playing = playing, title = "Midnight City", artist = "M83", app = "Spotify", art = cover, fromSession = true,
            durationMs = 243_000L, positionMs = 90_000L, positionAtMs = android.os.SystemClock.elapsedRealtime().coerceAtLeast(1L),
        )
        rule.setContent {
            val geo = me.kavishdevar.librepods.presentation.overlays.MiniGeometry(app, listOfNotNull(hole), look)
            Wallpaper(dark) {
                val dens = androidx.compose.ui.platform.LocalDensity.current
                Box(Modifier.fillMaxWidth().height(with(dens) { (geo.wideWindow.height + geo.windowTop.coerceAtLeast(0)).toDp() + 40.dp })) {
                    Box(Modifier.offset { androidx.compose.ui.unit.IntOffset(0, geo.windowTop) }.size(with(dens) {
                        val s = if (wide > 0f) geo.wideWindow else geo.compactWindow
                        androidx.compose.ui.unit.DpSize(s.width.toDp(), s.height.toDp())
                    }).align(Alignment.TopCenter)) {
                        me.kavishdevar.librepods.presentation.overlays.MiniIslandHost(
                            geometry = geo, track = track, leaving = false, hidden = false,
                            content = content ?: if (airPods != null) me.kavishdevar.librepods.services.MiniIslandRules.Content.AirPods else me.kavishdevar.librepods.services.MiniIslandRules.Content.Music,
                            heard = heard,
                            pods = airPods ?: PodsSnapshot(), heartBpm = heart, talking = talking,
                            onWindowSize = {}, onTouchable = {}, onGone = {}, onAction = {},
                            still = 1f, stillWide = wide, stillPress = press, stillAck = ack,
                            look = look, forceSituation = situation,
                            place = place, timer = timer, budsUp = budsUp, wallpaper = wallpaper, stillSwap = swap,
                        )
                    }
                    // The camera, on top, where the real one would be.
                    if (hole != null) androidx.compose.foundation.Canvas(Modifier.matchParentSize()) {
                        val c = androidx.compose.ui.geometry.Offset(hole.exactCenterX(), hole.exactCenterY())
                        if (shape == "notch") {
                            drawRoundRect(androidx.compose.ui.graphics.Color.Black, androidx.compose.ui.geometry.Offset(hole.left.toFloat(), -20f),
                                androidx.compose.ui.geometry.Size(hole.width().toFloat(), hole.height() + 20f), androidx.compose.ui.geometry.CornerRadius(14 * d))
                        }
                        val r = minOf(hole.width(), hole.height()) / 2f
                        drawCircle(androidx.compose.ui.graphics.Color(0xFF0B0B0F), r * 0.62f, c)
                        drawCircle(androidx.compose.ui.graphics.Color(0xFF1F2A44), r * 0.26f, c)
                    }
                }
            }
        }
        rule.mainClock.advanceTimeBy(600)
        rule.onRoot().captureRoboImage("$out/$name.png")
    }

    @Test fun miniIslandPlaying() = mini("mini_island_playing", playing = true, wide = 0f, art = true)
    @Test fun miniIslandSongName() = mini("mini_island_song_name", playing = true, wide = 1f, art = true, dark = true)
    @Test fun miniIslandPausedNoCover() = mini("mini_island_paused", playing = false, wide = 0f, art = false)
    @Test fun miniIslandAirPods() = mini("mini_island_airpods", playing = false, wide = 0f, art = false, airPods = demo)
    @Test fun miniIslandAirPodsHeart() = mini("mini_island_airpods_heart", playing = false, wide = 0f, art = false, airPods = demo, heart = 74, dark = true)
    @Test fun miniIslandAirPodsDarkIdle() = mini("mini_island_airpods_dark_idle", playing = false, wide = 0f, art = false, airPods = demo, dark = true)
    @Test fun miniIslandTalking() = mini("mini_island_talking", playing = true, wide = 0f, art = true, talking = true, dark = true)
    @Test fun miniIslandAirPodsLow() = mini("mini_island_airpods_low", playing = false, wide = 0f, art = false, airPods = demo.copy(left = 9, right = 12))
    // Touch feedback: pressed (squished and lit), and the sign a gesture leaves for a moment.
    @Test fun miniIslandPressed() = mini("mini_island_pressed", playing = true, wide = 0f, art = true, press = 1f)
    @Test fun miniIslandAckPause() = mini("mini_island_ack_pause", playing = true, wide = 0f, art = true,
        ack = me.kavishdevar.librepods.presentation.overlays.MiniAck(me.kavishdevar.librepods.services.IslandGestures.Action.PlayPause, playingAfter = false))
    @Test fun miniIslandAckPlay() = mini("mini_island_ack_play", playing = false, wide = 0f, art = true,
        ack = me.kavishdevar.librepods.presentation.overlays.MiniAck(me.kavishdevar.librepods.services.IslandGestures.Action.PlayPause, playingAfter = true))
    @Test fun miniIslandAckNext() = mini("mini_island_ack_next", playing = true, wide = 0f, art = true, dark = true,
        ack = me.kavishdevar.librepods.presentation.overlays.MiniAck(me.kavishdevar.librepods.services.IslandGestures.Action.Next))
    @Test fun miniIslandAckMode() = mini("mini_island_ack_mode", playing = false, wide = 0f, art = false, airPods = demo,
        ack = me.kavishdevar.librepods.presentation.overlays.MiniAck(me.kavishdevar.librepods.services.IslandGestures.Action.ListeningMode, mode = 2))
    // Customised: a few words of the title on the left, L/R/case on the right, large, roomy, bright.
    @Test fun miniIslandCustomTitleBuds() = mini(
        "mini_island_custom_title_buds", playing = true, wide = 0f, art = true, dark = true, airPods = demo,
        look = me.kavishdevar.librepods.services.IslandLook.Look(
            slots = me.kavishdevar.librepods.services.IslandLook.DEFAULT_SLOTS + (me.kavishdevar.librepods.services.IslandLook.Situation.Music to
                (me.kavishdevar.librepods.services.IslandLook.Slot.Title to me.kavishdevar.librepods.services.IslandLook.Slot.Buds)),
            size = me.kavishdevar.librepods.services.IslandLook.Size.Large,
            width = me.kavishdevar.librepods.services.IslandLook.Width.Roomy,
            glow = me.kavishdevar.librepods.services.IslandLook.Glow.Bright,
        ),
        situation = me.kavishdevar.librepods.services.IslandLook.Situation.Music,
    )
    // Nothing either side: just a slim black ring around the camera.
    @Test fun miniIslandCustomNothing() = mini(
        "mini_island_custom_nothing", playing = true, wide = 0f, art = true,
        look = me.kavishdevar.librepods.services.IslandLook.Look(
            slots = me.kavishdevar.librepods.services.IslandLook.DEFAULT_SLOTS + (me.kavishdevar.librepods.services.IslandLook.Situation.Music to
                (me.kavishdevar.librepods.services.IslandLook.Slot.Nothing to me.kavishdevar.librepods.services.IslandLook.Slot.Nothing)),
            size = me.kavishdevar.librepods.services.IslandLook.Size.Small,
            width = me.kavishdevar.librepods.services.IslandLook.Width.Snug,
            glow = me.kavishdevar.librepods.services.IslandLook.Glow.Off,
        ),
        situation = me.kavishdevar.librepods.services.IslandLook.Situation.Music,
    )
    // Idle with the heart and mode swapped round, and the battery on the right.
    @Test fun miniIslandCustomIdle() = mini(
        "mini_island_custom_idle", playing = false, wide = 0f, art = false, airPods = demo, heart = 66,
        look = me.kavishdevar.librepods.services.IslandLook.Look(
            slots = me.kavishdevar.librepods.services.IslandLook.DEFAULT_SLOTS + (me.kavishdevar.librepods.services.IslandLook.Situation.Idle to
                (me.kavishdevar.librepods.services.IslandLook.Slot.Heart to me.kavishdevar.librepods.services.IslandLook.Slot.Battery)),
        ),
    )

    /** Settings > Islands' look editor, light and dark. */
    @Test fun islandStudioLight() {
        GlintOverlays.updateSnapshot(demo)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            Box(Modifier.fillMaxSize().background(Color(0xFFF2F2F7)).padding(16.dp)) {
                Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)) {
                    me.kavishdevar.librepods.presentation.screens.DynamicIslandStudio(Color.Black, dark = false)
                    me.kavishdevar.librepods.presentation.screens.IslandGestureSettings(Color.Black, dark = false)
                }
            }
        }
        rule.mainClock.advanceTimeBy(1_500)
        rule.onRoot().captureRoboImage("$out/island_studio_light.png")
    }

    @Test fun islandStudioDarkAirPods() {
        RuntimeEnvironment.setQualifiers("+night")
        GlintOverlays.updateSnapshot(demo)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            Box(Modifier.fillMaxSize().background(Color.Black).padding(16.dp)) {
                me.kavishdevar.librepods.presentation.screens.DynamicIslandStudio(Color.White, dark = true, preview = me.kavishdevar.librepods.services.IslandLook.Situation.Idle)
            }
        }
        rule.mainClock.advanceTimeBy(1_500)
        rule.onRoot().captureRoboImage("$out/island_studio_dark_airpods.png")
    }

    @Test fun miniIslandAirPodsCharging() = mini("mini_island_airpods_charging", playing = false, wide = 0f, art = false, airPods = demo.copy(leftCharging = true, rightCharging = true))

    /** The big island growing out of the mini island by the camera, frame by frame. */
    @Test fun islandFromMini() {
        GlintOverlays.updateSnapshot(demo)
        rule.mainClock.autoAdvance = false
        val app = RuntimeEnvironment.getApplication()
        val d = app.resources.displayMetrics.density
        val screenW = app.resources.displayMetrics.widthPixels
        val hole = android.graphics.Rect((screenW / 2 - 13 * d).toInt(), (10 * d).toInt(), (screenW / 2 + 13 * d).toInt(), (36 * d).toInt())
        val mg = me.kavishdevar.librepods.presentation.overlays.MiniGeometry(app, listOf(hole))
        val origin = GlintOverlays.MiniOrigin(mg.offsetX.toFloat(), mg.centerY - mg.size.height / 2f, mg.size.compactWidth, mg.size.height)
        rule.setContent {
            val geo = IslandGeometry(app, origin)
            Wallpaper(false) {
                val dens = androidx.compose.ui.platform.LocalDensity.current
                Box(Modifier.fillMaxWidth().height(with(dens) { (geo.compactWindow.height + geo.windowTop.coerceAtLeast(0)).toDp() + 20.dp })) {
                    Box(Modifier.offset { androidx.compose.ui.unit.IntOffset(0, geo.windowTop) }.size(with(dens) {
                        androidx.compose.ui.unit.DpSize(geo.compactWindow.width.toDp(), geo.compactWindow.height.toDp())
                    }).align(Alignment.TopCenter)) {
                        IslandHost(geo, IslandEvent.Connected, IslandPhase.Compact, 0, blurAllowed = false, onPhase = {}, onWindowSize = {}, onGone = {})
                    }
                    androidx.compose.foundation.Canvas(Modifier.matchParentSize()) {
                        val c = androidx.compose.ui.geometry.Offset(hole.exactCenterX(), hole.exactCenterY())
                        drawCircle(androidx.compose.ui.graphics.Color(0xFF0B0B0F), hole.width() / 2f * 0.62f, c)
                        drawCircle(androidx.compose.ui.graphics.Color(0xFF1F2A44), hole.width() / 2f * 0.26f, c)
                    }
                }
            }
        }
        var t = 0L
        listOf(16L, 60L, 110L, 170L, 260L, 600L).forEach { at ->
            rule.mainClock.advanceTimeBy(at - t); t = at
            rule.onRoot().captureRoboImage("$out/island_from_mini_$at.png")
        }
    }

    @Test fun miniIslandNotch() = mini("mini_island_notch", playing = true, wide = 0f, art = true, shape = "notch")
    @Test fun miniIslandNotchWide() = mini("mini_island_notch_wide", playing = true, wide = 1f, art = true, shape = "notch")
    @Test fun miniIslandCornerCamera() = mini("mini_island_corner", playing = true, wide = 0f, art = true, shape = "corner")
    @Test fun miniIslandNoCutout() = mini("mini_island_no_cutout", playing = true, wide = 1f, art = true, shape = "none")
    @Test @org.robolectric.annotation.Config(qualifiers = "w320dp-h640dp-xhdpi")
    fun miniIslandSmallPhoneBigFont() {
        RuntimeEnvironment.setFontScale(2f)
        mini("mini_island_small_big_font", playing = true, wide = 1f, art = true)
    }

    /** The opened island over time: the heart chip ("--" with no reading) morphing out of play/pause. */
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
    @Test fun miniIslandHeadphones() = withSolo4 { mini("mini_island_headphones", playing = false, wide = 0f, art = false, airPods = solo4, dark = true) }
    @Test fun miniIslandHeadphonesBuds() = withSolo4 {
        mini(
            "mini_island_headphones_buds", playing = false, wide = 0f, art = false, airPods = solo4,
            look = me.kavishdevar.librepods.services.IslandLook.Look().let { l ->
                l.copy(slots = l.slots + (me.kavishdevar.librepods.services.IslandLook.Situation.Idle to (me.kavishdevar.librepods.services.IslandLook.Slot.Buds to me.kavishdevar.librepods.services.IslandLook.Slot.Mode)))
            },
            situation = me.kavishdevar.librepods.services.IslandLook.Situation.Idle,
        )
    }
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
    private fun heartState(name: String, link: LinkState = LinkState.Connected("AirPods Pro"), note: Boolean = false, dark: Boolean = true, setup: () -> Unit) {
        GlintStatus.set(link)
        me.kavishdevar.librepods.presentation.overlays.IslandTestHooks.heartNoteOpen = note
        setup()
        try {
            withMusic { island(name, IslandEvent.Connected, IslandPhase.Expanded, dark = dark) { rule.mainClock.advanceTimeBy(2_400) } }
        } finally {
            me.kavishdevar.librepods.presentation.overlays.IslandTestHooks.heartNoteOpen = false
            HeartRate.status(HeartRate.Status.Off)
            GlintStatus.set(LinkState.Idle)
        }
    }

    @Test fun heartLive() = heartState("heart_live") {
        val now = System.currentTimeMillis()
        listOf(70, 72, 71, 73).forEachIndexed { i, b -> HeartRate.reading(b, now - (3 - i) * 1_000L) }
    }
    @Test fun heartStarting() = heartState("heart_starting") { HeartRate.starting(System.currentTimeMillis()) }
    @Test fun heartResting() = heartState("heart_resting", dark = false) {
        val now = System.currentTimeMillis()
        HeartRate.reading(64, now - 20_000)
        HeartRate.resting(now + 180_000)
    }
    @Test fun heartNoSignal() = heartState("heart_no_signal") { HeartRate.status(HeartRate.Status.NoSignal) }
    @Test fun heartNoSignalNote() = heartState("heart_no_signal_note", note = true) { HeartRate.status(HeartRate.Status.NoSignal) }
    @Test fun heartBlocked() = heartState("heart_blocked", link = LinkState.GaveUp("AirPods Pro", "refused")) { HeartRate.status(HeartRate.Status.Off) }
    @Test fun heartBlockedNote() = heartState("heart_blocked_note", link = LinkState.GaveUp("AirPods Pro", "refused"), note = true, dark = false) { HeartRate.status(HeartRate.Status.Off) }
    @Test fun heartOff() = heartState("heart_off", dark = false) { HeartRate.status(HeartRate.Status.Off) }

    /** The rim light swung by tilting the phone (left, level, right): the same swing as the app's glass. */
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
    private fun heard(kind: me.kavishdevar.librepods.services.SoundRules.Kind, icon: Boolean = true, active: Boolean = true, app: String = "Messages") =
        me.kavishdevar.librepods.services.SoundSource.Heard(
            kind = kind, pkg = if (icon) "com.example.chat" else null, app = if (icon) app else null,
            icon = if (icon) me.kavishdevar.librepods.presentation.overlays.sampleIcon() else null,
            startedAt = 1L, active = active, endedAt = if (active) 0L else 2L,
        )
    private val soundContent = me.kavishdevar.librepods.services.MiniIslandRules.Content.Sound
    private val restContent = me.kavishdevar.librepods.services.MiniIslandRules.Content.Rest
    private val alert = me.kavishdevar.librepods.services.SoundRules.Kind.Alert

    @Test fun miniIslandSoundApp() = mini("mini_island_sound_app", playing = false, wide = 0f, art = false, content = soundContent, heard = heard(alert))
    @Test fun miniIslandSoundAppDark() = mini("mini_island_sound_app_dark", playing = false, wide = 0f, art = false, dark = true, content = soundContent, heard = heard(alert))
    @Test fun miniIslandSoundEnded() = mini("mini_island_sound_ended", playing = false, wide = 0f, art = false, dark = true, content = soundContent, heard = heard(alert, active = false))
    @Test fun miniIslandSoundBell() = mini("mini_island_sound_bell", playing = false, wide = 0f, art = false, dark = true, content = soundContent, heard = heard(alert, icon = false))
    @Test fun miniIslandSoundCall() = mini("mini_island_sound_call", playing = false, wide = 0f, art = false, dark = true, content = soundContent,
        heard = heard(me.kavishdevar.librepods.services.SoundRules.Kind.Call, icon = false))
    @Test fun miniIslandSoundAlarm() = mini("mini_island_sound_alarm", playing = false, wide = 0f, art = false, dark = true, content = soundContent,
        heard = heard(me.kavishdevar.librepods.services.SoundRules.Kind.Alarm, icon = false))
    @Test fun miniIslandSoundVoice() = mini("mini_island_sound_voice", playing = false, wide = 0f, art = false, dark = true, content = soundContent,
        heard = heard(me.kavishdevar.librepods.services.SoundRules.Kind.Voice, icon = false))
    @Test fun miniIslandSoundOther() = mini("mini_island_sound_other", playing = false, wide = 0f, art = false, dark = true, content = soundContent,
        heard = heard(me.kavishdevar.librepods.services.SoundRules.Kind.Other, icon = false))
    // A message ding while music plays: the app's icon takes the right-hand spot for a moment.
    @Test fun miniIslandBlipOverMusic() = mini("mini_island_blip", playing = true, wide = 0f, art = true, dark = true, heard = heard(alert))
    // Titles: the app's name for sounds.
    @Test fun miniIslandSoundTitle() = mini(
        "mini_island_sound_title", playing = false, wide = 0f, art = false, dark = true, content = soundContent, heard = heard(alert),
        look = me.kavishdevar.librepods.services.IslandLook.Look(
            slots = me.kavishdevar.librepods.services.IslandLook.DEFAULT_SLOTS + (me.kavishdevar.librepods.services.IslandLook.Situation.Sound to
                (me.kavishdevar.librepods.services.IslandLook.Slot.App to me.kavishdevar.librepods.services.IslandLook.Slot.Title)),
        ),
    )
    // The "Colour" choice: white bars and rings whatever the cover.
    @Test fun miniIslandWhiteAccent() = mini(
        "mini_island_white_accent", playing = true, wide = 0f, art = true, dark = true,
        look = me.kavishdevar.librepods.services.IslandLook.Look(accent = me.kavishdevar.librepods.services.IslandLook.Accent.White),
    )
    // Nothing on: the phone's battery on one side and the time on the other.
    @Test fun miniIslandRest() = mini("mini_island_rest", playing = false, wide = 0f, art = false, content = restContent,
        phone = me.kavishdevar.librepods.services.PhoneStatus.Info(84, false))
    @Test fun miniIslandRestDark() = mini("mini_island_rest_dark", playing = false, wide = 0f, art = false, dark = true, content = restContent,
        phone = me.kavishdevar.librepods.services.PhoneStatus.Info(84, false))
    @Test fun miniIslandRestCharging() = mini("mini_island_rest_charging", playing = false, wide = 0f, art = false, dark = true, content = restContent,
        phone = me.kavishdevar.librepods.services.PhoneStatus.Info(57, true))
    @Test fun miniIslandRestLow() = mini("mini_island_rest_low", playing = false, wide = 0f, art = false, dark = true, content = restContent,
        phone = me.kavishdevar.librepods.services.PhoneStatus.Info(9, false))
    @Test fun miniIslandRestNoBatteryYet() = mini("mini_island_rest_nobattery", playing = false, wide = 0f, art = false, dark = true, content = restContent,
        phone = me.kavishdevar.librepods.services.PhoneStatus.Info())

    /** The sound settings and the recent-sounds list, with a few apps heard. */
    @Test fun soundsSettingsLight() = soundsPage("sounds_settings_light", dark = false)
    @Test fun soundsSettingsDark() = soundsPage("sounds_settings_dark", dark = true)
    @Test fun soundsSettingsEmpty() = soundsPage("sounds_settings_empty", dark = true, seeded = false)

    private fun soundsPage(name: String, dark: Boolean, seeded: Boolean = true) {
        if (dark) RuntimeEnvironment.setQualifiers("+night")
        me.kavishdevar.librepods.services.SoundSource.resetForTest()
        val icon = me.kavishdevar.librepods.presentation.overlays.sampleIcon()
        val now = System.currentTimeMillis()
        if (seeded) me.kavishdevar.librepods.services.SoundSource.previewRecent(
            listOf(
                me.kavishdevar.librepods.services.SoundRules.Seen("com.example.chat", alert, now - 40_000, 3),
                me.kavishdevar.librepods.services.SoundRules.Seen("com.example.music", me.kavishdevar.librepods.services.SoundRules.Kind.Media, now - 6 * 60_000, 1),
                me.kavishdevar.librepods.services.SoundRules.Seen(null, me.kavishdevar.librepods.services.SoundRules.Kind.Alarm, now - 3 * 3_600_000, 1),
            ),
            mapOf(
                "com.example.chat" to me.kavishdevar.librepods.services.SoundSource.AppInfo("Messages", icon),
                "com.example.music" to me.kavishdevar.librepods.services.SoundSource.AppInfo("Music player", null),
            ),
        )
        val ink = if (dark) Color.White else Color.Black
        rule.mainClock.autoAdvance = false
        rule.setContent {
            Box(Modifier.fillMaxSize().background(if (dark) Color.Black else Color(0xFFF2F2F7)).padding(16.dp)) {
                Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
                    me.kavishdevar.librepods.presentation.screens.SoundsSection(ink, dark)
                    me.kavishdevar.librepods.presentation.screens.RecentSounds(ink, dark)
                }
            }
        }
        rule.mainClock.advanceTimeBy(1_500)
        rule.onRoot().captureRoboImage("$out/$name.png")
        me.kavishdevar.librepods.services.SoundSource.resetForTest()
    }

    /** The This phone page, light and dark. */
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
    private val spotify by lazy { me.kavishdevar.librepods.services.ScreenApp.Place.App("com.spotify.music", "Spotify", fakeIcon(0xFF1DB954.toInt(), "S")) }
    private val claude by lazy { me.kavishdevar.librepods.services.ScreenApp.Place.App("com.anthropic.claude", "Claude", fakeIcon(0xFFD97757.toInt(), "C")) }
    private val wall = listOf(Color(0xFF3A6EA5), Color(0xFFE0A458), Color(0xFF7FB7BE))
    private val screen = me.kavishdevar.librepods.services.MiniIslandRules.Content.Screen

    private val calmPhone = me.kavishdevar.librepods.services.PhoneStatus.Info(80, false)
    @Test fun miniIslandInApp() = mini("mini_island_in_app", playing = false, wide = 0f, art = false, content = screen, place = claude, phone = calmPhone)
    @Test fun miniIslandInAppDark() = mini("mini_island_in_app_dark", playing = false, wide = 0f, art = false, dark = true, content = screen, place = spotify,
        phone = me.kavishdevar.librepods.services.PhoneStatus.Info(9, false))
    @Test fun miniIslandAppSwap() = mini("mini_island_app_swap", playing = false, wide = 0f, art = false, content = screen, place = claude, swap = 0.55f, phone = calmPhone)
    @Test fun miniIslandInAppAirPods() = mini("mini_island_in_app_airpods", playing = false, wide = 0f, art = false, content = screen, place = spotify,
        airPods = demo, budsUp = true, dark = true)
    @Test fun miniIslandHome() = mini("mini_island_home", playing = false, wide = 0f, art = false, content = screen, phone = calmPhone,
        place = me.kavishdevar.librepods.services.ScreenApp.Place.Home, wallpaper = wall)
    @Test fun miniIslandHomePlain() = mini("mini_island_home_plain", playing = false, wide = 0f, art = false, dark = true, content = screen,
        place = me.kavishdevar.librepods.services.ScreenApp.Place.Home)
    @Test fun miniIslandLocked() = mini("mini_island_locked", playing = false, wide = 0f, art = false, dark = true, content = screen, phone = me.kavishdevar.librepods.services.PhoneStatus.Info(52, true),
        place = me.kavishdevar.librepods.services.ScreenApp.Place.Locked())
    @Test fun miniIslandUnlocking() = mini("mini_island_unlocking", playing = false, wide = 0f, art = false, dark = true, content = screen, phone = calmPhone,
        place = me.kavishdevar.librepods.services.ScreenApp.Place.Locked(opening = true), swap = 1f)
    @Test fun miniIslandTimer() = mini("mini_island_timer", playing = false, wide = 0f, art = false, content = screen, place = claude, phone = calmPhone,
        timer = me.kavishdevar.librepods.services.IslandTimer.State(total = 300_000L, endsAt = android.os.SystemClock.elapsedRealtime() + 192_000L))
    @Test fun miniIslandTimerLastSeconds() = mini("mini_island_timer_seconds", playing = false, wide = 0f, art = false, dark = true, content = screen,
        place = me.kavishdevar.librepods.services.ScreenApp.Place.Home, wallpaper = wall,
        timer = me.kavishdevar.librepods.services.IslandTimer.State(total = 60_000L, endsAt = android.os.SystemClock.elapsedRealtime() + 42_000L))
    @Test fun miniIslandTimerRinging() = mini("mini_island_timer_ringing", playing = false, wide = 0f, art = false, dark = true, content = screen, place = spotify,
        timer = me.kavishdevar.librepods.services.IslandTimer.State(total = 60_000L, ringing = true))
    @Test fun miniIslandPhoneCharging() = mini("mini_island_phone_charging", playing = false, wide = 0f, art = false, content = screen, place = claude,
        phone = me.kavishdevar.librepods.services.PhoneStatus.Info(64, true))
    @Test fun miniIslandRestDate() = mini("mini_island_rest_date", playing = false, wide = 0f, art = false,
        content = me.kavishdevar.librepods.services.MiniIslandRules.Content.Rest, phone = me.kavishdevar.librepods.services.PhoneStatus.Info(81, false))

    private fun glance(name: String, dark: Boolean, place: me.kavishdevar.librepods.services.ScreenApp.Place, timer: me.kavishdevar.librepods.services.IslandTimer.State? = null,
                       phone: me.kavishdevar.librepods.services.PhoneStatus.Info = me.kavishdevar.librepods.services.PhoneStatus.Info(76, false),
                       event: IslandEvent = IslandEvent.Glance, snapshot: PodsSnapshot = PodsSnapshot()) {
        me.kavishdevar.librepods.services.ScreenApp.preview(place, wall)
        me.kavishdevar.librepods.services.IslandTimer.preview(timer)
        me.kavishdevar.librepods.services.PhoneStatus.preview(phone)
        try {
            island(name, event, IslandPhase.Expanded, dark = dark, snapshot = snapshot)
        } finally {
            me.kavishdevar.librepods.services.ScreenApp.resetForTest()
            me.kavishdevar.librepods.services.IslandTimer.preview(null)
        }
    }
    @Test fun islandGlanceDark() = glance("island_glance_dark", dark = true, place = claude)
    @Test fun islandGlanceLight() = glance("island_glance_light", dark = false, place = me.kavishdevar.librepods.services.ScreenApp.Place.Home)
    @Test fun islandGlanceTimerCharging() = glance("island_glance_timer", dark = true, place = spotify,
        timer = me.kavishdevar.librepods.services.IslandTimer.State(total = 600_000L, endsAt = android.os.SystemClock.elapsedRealtime() + 272_000L),
        phone = me.kavishdevar.librepods.services.PhoneStatus.Info(58, true))
    @Test fun islandGlanceAirPods() = connectedLink { glance("island_glance_airpods", dark = false, place = claude, snapshot = demo,
        phone = me.kavishdevar.librepods.services.PhoneStatus.Info(14, false)) }
    @Test fun islandTimerDone() = glance("island_timer_done", dark = true, place = spotify, event = IslandEvent.TimerDone,
        timer = me.kavishdevar.librepods.services.IslandTimer.State(total = 300_000L, ringing = true))
}
