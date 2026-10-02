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

    private fun island(name: String, event: IslandEvent, phase: IslandPhase, dark: Boolean = true, tall: Boolean = false, then: () -> Unit = {}) {
        if (dark) RuntimeEnvironment.setQualifiers("+night")
        GlintOverlays.updateSnapshot(demo)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val geo = IslandGeometry(RuntimeEnvironment.getApplication())
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
    ) {
        if (dark) RuntimeEnvironment.setQualifiers("+night")
        rule.mainClock.autoAdvance = false
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
                            content = if (airPods != null) me.kavishdevar.librepods.services.MiniIslandRules.Content.AirPods else me.kavishdevar.librepods.services.MiniIslandRules.Content.Music,
                            pods = airPods ?: PodsSnapshot(), heartBpm = heart, talking = talking,
                            onWindowSize = {}, onTouchable = {}, onGone = {}, onAction = {},
                            still = 1f, stillWide = wide, stillPress = press, stillAck = ack,
                            look = look, forceSituation = situation,
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
}
