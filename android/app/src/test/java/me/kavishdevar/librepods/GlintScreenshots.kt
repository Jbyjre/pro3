package me.kavishdevar.librepods

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

    private fun island(name: String, event: IslandEvent, phase: IslandPhase, dark: Boolean = true) {
        GlintOverlays.updateSnapshot(demo)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val geo = IslandGeometry(RuntimeEnvironment.getApplication())
            Wallpaper(dark) {
                Box(Modifier.padding(top = 12.dp).size(with(androidx.compose.ui.platform.LocalDensity.current) {
                    val s = if (phase == IslandPhase.Expanded) geo.expandedWindow else geo.compactWindow
                    androidx.compose.ui.unit.DpSize(s.width.toDp(), s.height.toDp())
                }).align(Alignment.TopCenter)) {
                    IslandHost(geo, event, phase, 0, blurAllowed = false, onPhase = {}, onWindowSize = {}, onGone = {})
                }
            }
        }
        rule.mainClock.advanceTimeBy(1_200)
        rule.onRoot().captureRoboImage("$out/$name.png")
    }

    @Test fun islandConnected() = island("island_connected", IslandEvent.Connected, IslandPhase.Compact)
    @Test fun islandLowBattery() = island("island_low_battery", IslandEvent.LowBattery(9), IslandPhase.Compact, dark = false)
    @Test fun islandMode() = island("island_mode", IslandEvent.ListeningMode(2), IslandPhase.Compact)
    @Test fun islandExpanded() = island("island_expanded", IslandEvent.MovedToDevice("iPad", canTakeBack = true), IslandPhase.Expanded, dark = false)

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
            Row(Modifier.background(Color(0xFF9FB4D8)).padding(24.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Box(Modifier.size(160.dp).androidx_clip()) {
                    androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(R.drawable.ic_launcher_background), null, Modifier.fillMaxSize())
                    androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(R.drawable.ic_launcher_foreground), null, Modifier.fillMaxSize())
                }
                Box(Modifier.size(160.dp).androidx_clip().background(Color(0xFF2B3A55))) {
                    androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(R.drawable.ic_launcher_monochrome), null, Modifier.fillMaxSize())
                }
            }
        }
        rule.onRoot().captureRoboImage("$out/icon.png")
    }

    private fun Modifier.androidx_clip() = this.clip(androidx.compose.foundation.shape.CircleShape)

    @Suppress("unused") private val keep = IntSize.Zero
}
