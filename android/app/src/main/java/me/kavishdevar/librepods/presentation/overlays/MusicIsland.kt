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
package me.kavishdevar.librepods.presentation.overlays

import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import me.kavishdevar.librepods.presentation.glint.drawHeadphones
import me.kavishdevar.librepods.presentation.glint.lerp
import me.kavishdevar.librepods.presentation.glint.roundRectPath
import me.kavishdevar.librepods.presentation.theme.glintFontFamily
import me.kavishdevar.librepods.services.GlintStatus
import me.kavishdevar.librepods.services.HeartRate
import me.kavishdevar.librepods.services.HeartView
import me.kavishdevar.librepods.services.IslandSpec
import me.kavishdevar.librepods.services.LinkState
import me.kavishdevar.librepods.services.MusicPulse
import me.kavishdevar.librepods.services.NowPlaying
import me.kavishdevar.librepods.services.SoundSource
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * The opened music island, the iPhone way (Apple's Now Playing in the Dynamic Island): the cover
 * in the top-left corner beside the camera, the song's name and artist next to it, the sound bars
 * in the cover's colour top right, the song bar (if switched on) and the controls below. The
 * cover and the bars are drawn by [SharedMusicArt], so they can be the Dynamic Island's own,
 * growing into place; this file's content leaves their spots empty.
 */

/** Where the opened cover and sound bars sit, in the opened content's own (unscaled) pixels. */
internal class MusicSpots(geometry: IslandGeometry, density: Float) {
    val art = Rect(geometry.artInset, geometry.artTop, geometry.artInset + geometry.artSize, geometry.artTop + geometry.artSize)
    /** The sound bars: a square the height of a line of text, as far in from the right as the cover is from the left. */
    val barsSide = 28f * density
    val bars = Rect(
        geometry.expandedW - geometry.artInset - barsSide, art.center.y - barsSide / 2f,
        geometry.expandedW - geometry.artInset, art.center.y + barsSide / 2f,
    )
}

/**
 * The cover and the sound bars of the music island. At every moment they sit somewhere between
 * three places: the Dynamic Island's own cover and bars (when the island has just grown out of it,
 * or is about to shrink back in), the compact pop-up's cover (a new song), and the opened island's.
 */
@Composable
internal fun SharedMusicArt(
    geometry: IslandGeometry,
    track: NowPlaying.Track,
    phase: IslandPhase,
    reduceMotion: Boolean,
    frame: (Float) -> IslandFrame,
    /** How far the heart's page has grown (0..1): the cover steps back for it. */
    detail: () -> Float,
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val art = track.art ?: track.icon
    val accent = remember(art) { art?.let { accentOf(it) } ?: Color.White }
    val spots = remember(geometry, density) { MusicSpots(geometry, density) }
    val grey = remember { ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) }

    // The bars follow the real music while it plays (the same listener as the Dynamic Island's).
    val listening = phase != IslandPhase.Leaving && track.playing
    DisposableEffect(listening) {
        if (listening) MusicPulse.acquire(context)
        onDispose { if (listening) MusicPulse.release() }
    }
    val pulseLive by MusicPulse.live.collectAsState()
    val bars = remember { FloatArray(MusicPulse.BANDS) }
    var clock by remember { mutableLongStateOf(0L) }
    LaunchedEffect(listening, reduceMotion, pulseLive) {
        var last = 0L
        while (listening && !reduceMotion) {
            delay(16L)
            val now = SystemClock.elapsedRealtime()
            val dt = if (last == 0L) 16f else (now - last).toFloat().coerceAtMost(100f)
            last = now
            if (pulseLive) {
                val target = MusicPulse.levels.value
                for (i in bars.indices) {
                    val to = target.getOrElse(i) { 0f }
                    val rate = if (to > bars[i]) 0.045f else 0.012f
                    bars[i] += (to - bars[i]) * (1f - kotlin.math.exp(-rate * dt))
                }
            }
            clock = now
        }
    }
    val level by animateFloatAsState(if (track.playing) 1f else 0f, if (reduceMotion) tween(0) else spring(0.8f, 300f), label = "musicLevel")

    Canvas(Modifier.fillMaxSize()) {
        val f = frame(size.width)
        if (f.appear <= 0.001f && geometry.origin == null) return@Canvas
        val o = geometry.origin
        val around = geometry.around && o != null
        val a = f.appear.coerceIn(0f, 1f)
        val e = f.expand.coerceIn(0f, 1f)
        // How much of each place to take (they add up to 1).
        val wE = e
        val wC = (a - e).coerceIn(0f, 1f - wE)
        val wP = 1f - wE - wC

        // 1. The Dynamic Island's own cover and bars (fixed: the pill doesn't move).
        val pillH = o?.height ?: 0f
        val pillL = if (o != null) (size.width - o.width) / 2f + o.dx else 0f
        val pillT = geometry.seedTop
        val ring = track.progress(SystemClock.elapsedRealtime()) != null
        val pillFit = (o?.side ?: 0f) / 2f
        val pHalf = IslandSpec.roundedSquareHalf(if (ring) pillFit - 3.4f * density else pillFit, 0.42f)
        val pCover = Offset(pillL + pillH / 2f, pillT + pillH / 2f)
        val pBars = Offset(pillL + (o?.width ?: 0f) - pillH / 2f, pillT + pillH / 2f)
        // 2. The compact pop-up's cover (a new song).
        val band = minOf(geometry.band, f.main.height)
        val rowH = f.main.height - band
        val cCover = Offset(f.main.left + rowH * 0.32f + 21f * density, f.main.top + band + rowH / 2f)
        val cHalf = 15f * density
        // 3. The opened island's, at the size the content is drawn right now.
        val fullH = lerp(geometry.expandedH, geometry.detailH, detail())
        val fit = minOf(1f, f.main.width / geometry.expandedW, (f.main.height / fullH).coerceAtLeast(0f)).coerceAtLeast(0.3f)
        val x0 = f.main.center.x - geometry.expandedW * fit / 2f
        val y0 = f.main.top
        val eCover = Offset(x0 + spots.art.center.x * fit, y0 + spots.art.center.y * fit)
        val eHalf = geometry.artSize / 2f * fit
        val eBars = Offset(x0 + spots.bars.center.x * fit, y0 + spots.bars.center.y * fit)

        // Without the Dynamic Island to grow out of, they grow from a dot in the middle instead.
        val startCover = if (around) pCover else f.main.center
        val startHalf = if (around) pHalf else 2f * density
        val startBars = if (around) pBars else f.main.center
        val fade = if (around) 1f else ((a - 0.3f) / 0.5f).coerceIn(0f, 1f)
        val away = (1f - detail() * 3f).coerceIn(0f, 1f)

        val cover = Offset(
            startCover.x * wP + cCover.x * wC + eCover.x * wE,
            startCover.y * wP + cCover.y * wC + eCover.y * wE,
        )
        val half = startHalf * wP + cHalf * wC + eHalf * wE
        val corner = startHalf * 0.42f * wP + 8f * density * wC + geometry.artCorner * fit * wE

        clipPath(roundRectPath(f.main, f.radius)) {
            // The cover: the same picture all the way, cropped to a square.
            if (half > 0.5f && fade * away > 0.001f) {
                val alpha = fade * away
                val shape = Path().apply { addRoundRect(RoundRect(cover.x - half, cover.y - half, cover.x + half, cover.y + half, CornerRadius(corner))) }
                clipPath(shape) {
                    if (art != null) {
                        val (so, ss) = centreSquare(art)
                        drawImage(
                            art, srcOffset = so, srcSize = ss,
                            dstOffset = IntOffset((cover.x - half).roundToInt(), (cover.y - half).roundToInt()),
                            dstSize = IntSize((2f * half).roundToInt(), (2f * half).roundToInt()),
                            alpha = alpha,
                            // Greyed while paused only where the Dynamic Island greys it (its own look).
                            colorFilter = if (level < 0.5f && wP > 0.5f) grey else null,
                        )
                    } else {
                        drawRect(Color(0xFF2C2C2E), Offset(cover.x - half, cover.y - half), Size(2f * half, 2f * half), alpha = alpha)
                        drawNote(cover, half * 1.1f, Color.White.copy(alpha = alpha * 0.9f))
                    }
                }
            }
            // The sound bars, in the cover's colour.
            val barsAlpha = (wP + wE) * fade * away
            if (barsAlpha > 0.001f) {
                val side = (o?.side ?: spots.barsSide) * wP + spots.barsSide * fit * wE + spots.barsSide * 0.5f * wC
                val c = Offset(startBars.x * wP + eBars.x * wE + eBars.x * wC, startBars.y * wP + eBars.y * wE + eBars.y * wC)
                drawSoundBars(c, side, accent.copy(alpha = barsAlpha), level, if (pulseLive && !reduceMotion) bars else null, clock, moving = listening && !reduceMotion, density)
            }
        }
    }
}

/** Four sound bars centred at [c] in a spot [side] across, like the Dynamic Island's. */
internal fun DrawScope.drawSoundBars(c: Offset, side: Float, color: Color, level: Float, live: FloatArray?, clock: Long, moving: Boolean, density: Float) {
    val t = clock / 1000f
    val barW = 2.6f * density * (side / (28f * density)).coerceIn(0.5f, 1.4f)
    val gap = barW * 0.92f
    val maxH = side * 0.62f
    val startX = c.x - (4 * barW + 3 * gap) / 2f + barW / 2f
    val order = intArrayOf(1, 0, 2, 3)
    for (i in 0 until 4) {
        val wiggle = 0.35f + 0.65f * abs(sin(t * (2.3f + i * 0.73f) + i * 1.7f))
        val rest = if (i % 2 == 0) 0.62f else 0.42f
        val l = live?.let { 0.22f + 0.78f * it[order[i]] }
        val h = lerp(barW, maxH * (l ?: if (moving) wiggle else rest), level)
        val x = startX + i * (barW + gap)
        drawLine(color, Offset(x, c.y - h / 2f), Offset(x, c.y + h / 2f), barW, StrokeCap.Round)
    }
}

/** The opened music island's own content (the cover and bars are [SharedMusicArt]'s). */
@Composable
internal fun MusicIslandContent(
    geometry: IslandGeometry,
    track: NowPlaying.Track,
    snapshot: PodsSnapshot,
    content: Color,
    secondary: Color,
    dark: Boolean,
    active: Boolean,
    reduceMotion: Boolean,
    age: Int,
    heartOpen: Boolean,
    onHeartOpen: (Boolean) -> Unit,
    onTouch: () -> Unit,
) {
    val context = LocalContext.current
    val dens = LocalDensity.current
    fun Float.px() = with(dens) { this@px.toDp() }
    val link by GlintStatus.link.collectAsState()
    val heart by HeartRate.state.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(5_000); now = System.currentTimeMillis() } }
    val heartView = remember(heart, link, now) { HeartView.of(heart, link, GlintOverlays.airPodsAudio(context), maxOf(now, System.currentTimeMillis())) }
    val prefs = remember { me.kavishdevar.librepods.services.IslandPrefs.prefs(context) }
    val chipOn = remember { prefs.getBoolean(me.kavishdevar.librepods.services.PREF_HR_CHIP, true) } && heartView.kind != HeartView.Kind.Away
    LaunchedEffect(heartView.bpm == null) { if (heartView.bpm == null) onHeartOpen(false) }
    // The headphones are on: their battery is one tap away, where Apple puts the audio output.
    val budsUp = snapshot.budsLevel != null && (link is LinkState.Connected || GlintOverlays.chosenAudio(context))

    AnimatedContent(
        targetState = heartOpen && heartView.bpm != null,
        transitionSpec = {
            val spec = if (reduceMotion) tween<Float>(150) else spring(0.86f, 420f)
            (fadeIn(spec) + scaleIn(spec, initialScale = 0.94f)) togetherWith (fadeOut(tween(110)) + scaleOut(tween(110), targetScale = 0.97f))
        },
        modifier = Modifier.fillMaxSize(),
        label = "musicPage",
    ) { showHeart ->
        if (showHeart) {
            Box(Modifier.fillMaxSize().padding(top = geometry.band.px())) {
                HeartDetail(
                    bpm = heartView.bpm ?: 0, age = age, content = content, secondary = secondary, dark = dark, reduceMotion = reduceMotion,
                    onBack = { onTouch(); onHeartOpen(false) },
                )
            }
            return@AnimatedContent
        }
        val spots = remember(geometry) { MusicSpots(geometry, dens.density) }
        val openMusicApp = { onTouch(); SoundSource.openApp(context, track.pkg); Unit }
        Box(Modifier.fillMaxSize()) {
            // The cover's spot opens the music app (Apple: a tap on the activity opens its app).
            Box(
                Modifier
                    .offset { IntOffset(spots.art.left.roundToInt(), spots.art.top.roundToInt()) }
                    .size(spots.art.width.px(), spots.art.height.px())
                    .islandPress(active && track.pkg != null, "Open ${track.app ?: "the music app"}", openMusicApp, {}),
            )
            // The song's name and artist beside the cover, just under the camera.
            val textLeft = spots.art.right + 12f * dens.density
            val textRight = spots.bars.left - 10f * dens.density
            Column(
                Modifier
                    .offset { IntOffset(textLeft.roundToInt(), geometry.textTop.roundToInt()) }
                    .width((textRight - textLeft).coerceAtLeast(0f).px())
                    .height(40.dp)
                    .islandPress(active && track.pkg != null, "Open ${track.app ?: "the music app"}", openMusicApp, {}),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Bottom,
            ) {
                val (title, sub) = NowPlaying.words(track)
                Text(
                    title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = TextStyle(fontFamily = glintFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 20.sp, color = content),
                    modifier = if (active && !reduceMotion) Modifier.basicMarquee(iterations = 1, initialDelayMillis = 1_400) else Modifier,
                )
                Text(
                    track.artist ?: sub, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = TextStyle(fontFamily = glintFontFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 18.sp, color = secondary),
                )
            }
            if (geometry.showBar) SongBar(
                track = track, content = content, secondary = secondary, active = active,
                modifier = Modifier
                    .offset { IntOffset(geometry.artInset.roundToInt(), geometry.barTop.roundToInt()) }
                    .width((geometry.expandedW - 2f * geometry.artInset).px())
                    .height(22.dp),
                onTouch = onTouch,
            )
            // The controls: back, play/pause and next in the middle, like Apple's; the heart on
            // the left (when the AirPods measure it), the headphones or the music app on the right.
            Box(
                Modifier
                    .offset { IntOffset(0, geometry.controlsTop.roundToInt()) }
                    .fillMaxWidth()
                    .height(44.dp)
                    .padding(horizontal = geometry.artInset.px()),
            ) {
                // Three parts: the heart (left), the controls (centred: both sides get the same
                // room), the headphones or the app (right). Narrow phones close the gaps a little.
                val narrow = geometry.expandedW / dens.density < 360f
                val gap = if (narrow) 8.dp else 16.dp
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (chipOn) MusicHeart(
                            view = heartView, content = content, dark = dark, reduceMotion = reduceMotion,
                            enabled = active,
                            onClick = {
                                onTouch()
                                val service = me.kavishdevar.librepods.services.ServiceManager.getService()
                                when (heartView.tap) {
                                    HeartView.Tap.Explain -> onHeartOpen(true)
                                    HeartView.Tap.Start -> service?.glanceHeartRate(me.kavishdevar.librepods.services.HR_GLANCE_TAP_MS)
                                        ?: GlintOverlays.openApp(context, me.kavishdevar.librepods.presentation.navigation.AppLinks.HEART)
                                    // What's wrong and its fix live on the AirPods page.
                                    else -> GlintOverlays.showIsland(context, IslandEvent.Connected, expand = true)
                                }
                            },
                        )
                    }
                    TransportGlyph(next = false, color = content, dark = dark, enabled = active) { onTouch(); NowPlaying.skip(context, next = false) }
                    Spacer(Modifier.width(gap))
                    PlayPauseButton(
                        playing = track.playing, color = content, size = 48.dp, glyph = 22.dp, reduceMotion = reduceMotion,
                        enabled = active, dark = dark,
                        onClick = { onTouch(); NowPlaying.playPause(context) },
                    )
                    Spacer(Modifier.width(gap))
                    TransportGlyph(next = true, color = content, dark = dark, enabled = active) { onTouch(); NowPlaying.skip(context, next = true) }
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                        if (budsUp) OutputButton(snapshot, content, dark, active) {
                            onTouch(); GlintOverlays.showIsland(context, IslandEvent.Connected, expand = true)
                        } else track.icon?.let { icon ->
                            AppButton(icon, track.app ?: "the music app", dark, active && track.pkg != null, openMusicApp)
                        }
                    }
                }
            }
        }
    }
}

/**
 * The heart, small enough for the music page: a round glass button with the heart (filled and
 * beating with a live reading, an outline otherwise) and the number beside it when there is one.
 * In the island's own white, never red. What it says is read out for TalkBack.
 */
@Composable
private fun MusicHeart(view: HeartView.View, content: Color, dark: Boolean, reduceMotion: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val live = view.kind == HeartView.Kind.Live
    val beat = me.kavishdevar.librepods.presentation.glint.rememberHeartBeat(view.bpm.takeIf { live }, reduceMotion, peak = 1.14f)
    Row(verticalAlignment = Alignment.CenterVertically) {
        PressableGlyph(size = 40.dp, description = "Heart rate: ${view.short}", onClick = onClick, enabled = enabled, dark = dark) {
            drawGlassCapsule(dark)
            val hs = 16.dp.toPx() * (if (live) beat.value else 1f)
            val hp = me.kavishdevar.librepods.presentation.glint.heartPath(Size(hs, hs))
            translate(center.x - hs / 2f, center.y - hs / 2f + 0.5f.dp.toPx()) {
                if (view.bpm != null) drawPath(hp, content)
                else drawPath(hp, content, style = Stroke(1.6.dp.toPx()))
            }
        }
        view.bpm?.let {
            Spacer(Modifier.width(6.dp))
            Text("$it", maxLines = 1, style = TextStyle(fontFamily = glintFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = content))
        }
    }
}

/** Back or next as Apple draws them in the island: two plain triangles, no button behind. */
@Composable
private fun TransportGlyph(next: Boolean, color: Color, dark: Boolean, enabled: Boolean, onClick: () -> Unit) {
    PressableGlyph(size = 44.dp, description = if (next) "Next song" else "Previous song", onClick = onClick, enabled = enabled, dark = dark) {
        val g = 18.dp.toPx()
        val cy = size.height / 2f
        val cx = size.width / 2f
        val dir = if (next) 1f else -1f
        val effect = androidx.compose.ui.graphics.PathEffect.cornerPathEffect(g * 0.12f)
        for (k in 0..1) {
            val baseX = cx + dir * (k * g * 0.5f - g * 0.5f)
            val p = Path().apply {
                moveTo(baseX, cy - g * 0.4f)
                lineTo(baseX + dir * g * 0.5f, cy)
                lineTo(baseX, cy + g * 0.4f)
                close()
            }
            drawPath(p, color)
            drawPath(p, color, style = Stroke(g * 0.08f, pathEffect = effect))
        }
    }
}

/** The headphones' battery as a small ring with their mark inside: tap for the AirPods page. */
@Composable
private fun OutputButton(snapshot: PodsSnapshot, content: Color, dark: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val lvl = snapshot.budsLevel
    PressableGlyph(
        size = 40.dp, description = "${snapshot.name}${lvl?.let { ", battery $it%" } ?: ""}. Show details",
        onClick = onClick, enabled = enabled, dark = dark,
    ) {
        val c = center
        val rr = 14.dp.toPx()
        val st = Stroke(2.4.dp.toPx(), cap = StrokeCap.Round)
        val tl = Offset(c.x - rr, c.y - rr)
        drawArc(content.copy(alpha = 0.2f), 0f, 360f, false, tl, Size(rr * 2, rr * 2), style = st)
        val col = when {
            snapshot.budsCharging -> Color(0xFF30D158)
            lvl != null && lvl <= 10 -> Color(0xFFFF453A)
            lvl != null && lvl <= 20 -> Color(0xFFFFB340)
            else -> content
        }
        if (lvl != null) drawArc(col, -90f, 360f * lvl / 100f, false, tl, Size(rr * 2, rr * 2), style = st)
        if (snapshot.headphones) drawHeadphones(c, rr * 0.55f, content) else drawBudPair(c, rr * 0.95f, content)
    }
}

/** The music app's own icon (when no headphones are on): tap to open it. */
@Composable
private fun AppButton(icon: ImageBitmap, app: String, dark: Boolean, enabled: Boolean, onClick: () -> Unit) {
    PressableGlyph(size = 40.dp, description = "Open $app", onClick = onClick, enabled = enabled, dark = dark) {
        val s = 26.dp.toPx()
        val c = center
        val tile = Path().apply { addRoundRect(RoundRect(c.x - s / 2f, c.y - s / 2f, c.x + s / 2f, c.y + s / 2f, CornerRadius(s * 0.27f))) }
        val (so, ss) = centreSquare(icon)
        clipPath(tile) {
            drawImage(icon, srcOffset = so, srcSize = ss, dstOffset = IntOffset((c.x - s / 2f).roundToInt(), (c.y - s / 2f).roundToInt()), dstSize = IntSize(s.roundToInt(), s.roundToInt()))
        }
    }
}

/**
 * The song bar: time gone on the left, time left on the right, and a bar between that fills as
 * the song plays. When the music app allows it, drag along it to move in the song: the bar thickens
 * under the finger (as Apple's does) and the song moves when it lets go.
 */
@Composable
private fun SongBar(track: NowPlaying.Track, content: Color, secondary: Color, active: Boolean, modifier: Modifier, onTouch: () -> Unit) {
    var nowMs by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(active, track.playing, track.positionAtMs) {
        nowMs = SystemClock.elapsedRealtime()
        while (active && track.playing) { delay(250L); nowMs = SystemClock.elapsedRealtime() }
    }
    var drag by remember { mutableStateOf<Float?>(null) }
    val thick = remember { Animatable(0f) }
    LaunchedEffect(drag != null) { thick.animateTo(if (drag != null) 1f else 0f, spring(0.8f, 600f)) }
    val pos = track.positionAt(nowMs)
    val known = pos != null && track.durationMs > 0L
    val shown = drag ?: track.progress(nowMs) ?: 0f
    val timeStyle = TextStyle(fontFamily = glintFontFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp, color = secondary)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (!known) {
            // The app doesn't share where it is in the song: say where it plays instead.
            Text(
                track.app?.let { "Playing on $it" } ?: "Playing", maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = timeStyle.copy(textAlign = TextAlign.Center), modifier = Modifier.fillMaxWidth(),
            )
            return@Row
        }
        val at = drag?.let { (it * track.durationMs).toLong() } ?: pos!!
        Text(NowPlaying.clock(at), style = timeStyle, maxLines = 1, modifier = Modifier.width(44.dp))
        var widthPx by remember { mutableFloatStateOf(1f) }
        Canvas(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .semantics {
                    contentDescription = "Song position"
                    progressBarRangeInfo = ProgressBarRangeInfo(shown, 0f..1f)
                    if (track.canSeek && active) setProgress { v -> NowPlaying.seekTo(v); true }
                }
                .pointerInput(track.canSeek, active) {
                    if (!track.canSeek || !active) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        onTouch()
                        widthPx = size.width.toFloat().coerceAtLeast(1f)
                        drag = (down.position.x / widthPx).coerceIn(0f, 1f)
                        while (true) {
                            val ch = awaitPointerEvent().changes.firstOrNull() ?: break
                            ch.consume()
                            drag = (ch.position.x / widthPx).coerceIn(0f, 1f)
                            if (!ch.pressed) break
                        }
                        drag?.let { NowPlaying.seekTo(it) }
                        drag = null
                    }
                },
        ) {
            val h = lerp(4f, 7f, thick.value) * density
            val y = size.height / 2f
            drawRoundRect(content.copy(alpha = 0.22f), Offset(0f, y - h / 2f), Size(size.width, h), CornerRadius(h / 2f))
            drawRoundRect(content, Offset(0f, y - h / 2f), Size((size.width * shown).coerceAtLeast(h), h), CornerRadius(h / 2f))
        }
        Text(
            "-" + NowPlaying.clock(track.durationMs - at), style = timeStyle.copy(textAlign = TextAlign.End), maxLines = 1,
            modifier = Modifier.width(48.dp),
        )
    }
}
