/*
    Glint, a fork of LibrePods - AirPods liberated from Apple's ecosystem
    Copyright (C) 2026 Glint contributors

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

package me.kavishdevar.librepods.presentation.glint

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import me.kavishdevar.librepods.presentation.theme.glintFontFamily
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Open replacements for the handful of Apple SF Symbols LibrePods used as text. SF Pro can't
 * be shipped, so these are drawn as vectors in the same spirit (rounded strokes, one weight)
 * and keyed by the same private-use characters, so existing screens keep passing strings.
 */
object GlintSymbols {
    private const val STROKE = 2.1f

    private fun icon(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply(block).build()

    private fun ImageVector.Builder.stroke(width: Float = STROKE, block: PathBuilder.() -> Unit) = path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = width,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        pathBuilder = block,
    )

    private fun ImageVector.Builder.fill(evenOdd: Boolean = false, block: PathBuilder.() -> Unit) = path(
        fill = SolidColor(Color.Black),
        pathFillType = if (evenOdd) PathFillType.EvenOdd else PathFillType.NonZero,
        pathBuilder = block,
    )

    private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
        moveTo(cx - r, cy)
        arcTo(r, r, 0f, isMoreThanHalf = false, isPositiveArc = true, cx + r, cy)
        arcTo(r, r, 0f, isMoreThanHalf = false, isPositiveArc = true, cx - r, cy)
        close()
    }

    private fun PathBuilder.roundRect(l: Float, t: Float, r: Float, b: Float, rad: Float) {
        moveTo(l + rad, t)
        lineTo(r - rad, t)
        arcTo(rad, rad, 0f, false, true, r, t + rad)
        lineTo(r, b - rad)
        arcTo(rad, rad, 0f, false, true, r - rad, b)
        lineTo(l + rad, b)
        arcTo(rad, rad, 0f, false, true, l, b - rad)
        lineTo(l, t + rad)
        arcTo(rad, rad, 0f, false, true, l + rad, t)
        close()
    }

    /** A plus-shaped outline rotated 45 degrees: the "x" knocked out of a filled circle. */
    private fun PathBuilder.cross(cx: Float, cy: Float, arm: Float, half: Float) {
        val pts = listOf(
            -half to -arm, half to -arm, half to -half, arm to -half, arm to half, half to half,
            half to arm, -half to arm, -half to half, -arm to half, -arm to -half, -half to -half,
        )
        val c = cos(PI / 4).toFloat()
        pts.forEachIndexed { i, (x, y) ->
            val rx = cx + (x - y) * c
            val ry = cy + (x + y) * c
            if (i == 0) moveTo(rx, ry) else lineTo(rx, ry)
        }
        close()
    }

    private fun ImageVector.Builder.speakerBody() = fill {
        moveTo(2.6f, 9.6f)
        quadTo(2.6f, 8.7f, 3.5f, 8.7f)
        lineTo(6.2f, 8.7f)
        lineTo(10.1f, 5.3f)
        quadTo(11.3f, 4.4f, 11.3f, 5.8f)
        lineTo(11.3f, 18.2f)
        quadTo(11.3f, 19.6f, 10.1f, 18.7f)
        lineTo(6.2f, 15.3f)
        lineTo(3.5f, 15.3f)
        quadTo(2.6f, 15.3f, 2.6f, 14.4f)
        close()
    }

    private fun ImageVector.Builder.wave(radius: Float) = stroke(1.9f) {
        val cx = 11.6f
        val a = 0.82f
        moveTo(cx + radius * cos(-a), 12f + radius * sin(-a))
        arcTo(radius, radius, 0f, false, true, cx + radius * cos(a), 12f + radius * sin(a))
    }

    val ChevronLeft = icon("chevron.left") { stroke(2.3f) { moveTo(15f, 4.5f); lineTo(7.5f, 12f); lineTo(15f, 19.5f) } }
    val ChevronRight = icon("chevron.right") { stroke(2.3f) { moveTo(9f, 4.5f); lineTo(16.5f, 12f); lineTo(9f, 19.5f) } }
    val Checkmark = icon("checkmark") { stroke(2.3f) { moveTo(4.5f, 12.8f); lineTo(9.4f, 18f); lineTo(19.5f, 5.8f) } }
    val Xmark = icon("xmark") {
        stroke { moveTo(6.2f, 6.2f); lineTo(17.8f, 17.8f); moveTo(17.8f, 6.2f); lineTo(6.2f, 17.8f) }
    }
    val XmarkCircleFill = icon("xmark.circle.fill") {
        fill(evenOdd = true) { circle(12f, 12f, 10f); cross(12f, 12f, 4.6f, 1.05f) }
    }
    val Play = icon("play") { stroke(1.9f) { moveTo(7.5f, 5f); lineTo(18.5f, 12f); lineTo(7.5f, 19f); close() } }
    val PlayFill = icon("play.fill") {
        fill { moveTo(7f, 5.6f); quadTo(7f, 3.9f, 8.5f, 4.8f); lineTo(19f, 11f); quadTo(20.6f, 12f, 19f, 13f); lineTo(8.5f, 19.2f); quadTo(7f, 20.1f, 7f, 18.4f); close() }
    }
    val Pause = icon("pause") { stroke(2.3f) { moveTo(8.5f, 5f); lineTo(8.5f, 19f); moveTo(15.5f, 5f); lineTo(15.5f, 19f) } }
    val PauseFill = icon("pause.fill") {
        fill { roundRect(5.5f, 4.5f, 10f, 19.5f, 1.6f); roundRect(14f, 4.5f, 18.5f, 19.5f, 1.6f) }
    }
    val SpeakerFill = icon("speaker.fill") { speakerBody() }
    val SpeakerWave1 = icon("speaker.wave.1.fill") { speakerBody(); wave(3.6f) }
    val SpeakerWave3 = icon("speaker.wave.3.fill") { speakerBody(); wave(3.6f); wave(6.6f); wave(9.6f) }
    val Gear = icon("gearshape.fill") {
        fill(evenOdd = true) {
            val teeth = 8
            val outer = 10.6f
            val inner = 8.1f
            val step = 2 * PI / teeth
            for (i in 0 until teeth) {
                val base = i * step - PI / 2
                val pts = listOf(
                    base - step * 0.5 to inner, base - step * 0.22 to inner, base - step * 0.14 to outer,
                    base + step * 0.14 to outer, base + step * 0.22 to inner,
                )
                pts.forEachIndexed { j, (ang, r) ->
                    val x = 12f + r * cos(ang).toFloat()
                    val y = 12f + r * sin(ang).toFloat()
                    if (i == 0 && j == 0) moveTo(x, y) else lineTo(x, y)
                }
            }
            close()
            circle(12f, 12f, 3.4f)
        }
    }
    val PaperPlane = icon("paperplane") {
        stroke(1.8f) { moveTo(20.5f, 3.5f); lineTo(3.5f, 10.4f); lineTo(10.6f, 13.4f); lineTo(13.6f, 20.5f); close(); moveTo(20.5f, 3.5f); lineTo(10.6f, 13.4f) }
    }
    val BoltFill = icon("bolt.fill") {
        fill { moveTo(13.8f, 2f); lineTo(5f, 13.4f); lineTo(11.2f, 13.4f); lineTo(10.2f, 22f); lineTo(19f, 10.4f); lineTo(12.8f, 10.4f); close() }
    }
    val CaseFill = icon("airpods.case.fill") {
        fill(evenOdd = true) {
            roundRect(2.5f, 5f, 21.5f, 19.5f, 5.2f)
            roundRect(2.6f, 9.1f, 21.4f, 9.9f, 0f)
            circle(12f, 14.2f, 0.9f)
        }
    }

    private val byCodePoint: Map<Int, ImageVector> = mapOf(
        0x100061 to XmarkCircleFill,
        0x100184 to Xmark,
        0x100185 to Checkmark,
        0x10021F to PaperPlane,
        0x100283 to Play,
        0x100284 to PlayFill,
        0x100285 to Pause,
        0x100286 to PauseFill,
        0x1002A1 to SpeakerFill,
        0x1002A5 to SpeakerWave1,
        0x1002A9 to SpeakerWave3,
        0x1002E6 to BoltFill,
        0x10035F to Gear,
        0x100BF6 to ChevronLeft,
        0x100BFB to ChevronRight,
        0x100E6C to CaseFill,
    )

    /** Letter-in-a-circle symbols (left/right bud). */
    private val letters: Map<Int, String> = mapOf(
        0x10001B to "L", 0x1018E5 to "L",
        0x100027 to "R", 0x1018E8 to "R",
    )

    private fun singleCodePoint(text: String): Int? {
        val t = text.trim()
        if (t.isEmpty() || t.codePointCount(0, t.length) != 1) return null
        return t.codePointAt(0)
    }

    /** Spoken names, so symbol-only buttons still say what they do. */
    private val labels: Map<Int, String> = mapOf(
        0x100061 to "Clear",
        0x100184 to "Close",
        0x10021F to "Send",
        0x100283 to "Play", 0x100284 to "Play",
        0x100285 to "Pause", 0x100286 to "Pause",
        0x10035F to "Settings",
        0x100BF6 to "Back",
    )

    fun vectorFor(text: String): ImageVector? = singleCodePoint(text)?.let { byCodePoint[it] }
    fun labelFor(text: String): String? = singleCodePoint(text)?.let { labels[it] }
    fun letterFor(text: String): String? = singleCodePoint(text)?.let { letters[it] }
    fun isSymbol(text: String): Boolean = vectorFor(text) != null || letterFor(text) != null
}

/**
 * Draws [text] as a symbol when it is one of the old SF Symbol characters, otherwise as plain
 * text. The symbol is sized from the style's font size and coloured by the style (or the
 * surrounding content colour), so it drops in wherever a symbol string used to be shown.
 */
@Composable
fun SymbolText(text: String, style: TextStyle, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    val tint = when {
        color.isSpecified -> color
        style.color.isSpecified -> style.color
        else -> LocalContentColor.current
    }
    val fontSize = if (style.fontSize.isSpecified) style.fontSize else 17.sp
    val size = with(LocalDensity.current) { fontSize.toDp() } * 1.12f
    val vector = GlintSymbols.vectorFor(text)
    val letter = GlintSymbols.letterFor(text)
    when {
        vector != null -> Icon(vector, contentDescription = GlintSymbols.labelFor(text), tint = tint, modifier = modifier.size(size))
        letter != null -> LetterBadge(letter, tint, size, modifier)
        else -> Text(text, style = style, color = color, modifier = modifier)
    }
}

/** A filled circle with a letter knocked out of it, like the "L"/"R" bud badges. */
@Composable
fun LetterBadge(letter: String, color: Color, size: Dp, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val fontSize: TextUnit = with(LocalDensity.current) { (size * 0.62f).toSp() }
    Canvas(
        modifier
            .size(size)
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    ) {
        drawCircle(color, radius = this.size.minDimension / 2f * 0.92f)
        val layout = measurer.measure(
            letter,
            TextStyle(fontFamily = glintFontFamily, fontWeight = FontWeight.Bold, fontSize = fontSize)
        )
        drawText(
            layout,
            color = Color.Black,
            topLeft = Offset(
                (this.size.width - layout.size.width) / 2f,
                (this.size.height - layout.size.height) / 2f
            ),
            blendMode = BlendMode.Clear,
        )
    }
}
