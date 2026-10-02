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
package me.kavishdevar.librepods.presentation.glint

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Small pictures for list rows, so each setting is recognisable at a glance instead of by
 * reading its name. Drawn here as vectors in the same hand as [GlintSymbols]: one rounded
 * stroke weight on a 24-unit grid, written as SVG path strings.
 *
 * [forName] picks one from the row's (English) name; rows without a match simply show no
 * picture, so nothing looks broken in other languages.
 */
object RowIcons {
    private const val W = 1.9f

    private class P(val d: String, val filled: Boolean = false)

    private fun icon(name: String, vararg paths: P): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            paths.forEach { p ->
                addPath(
                    pathData = addPathNodes(p.d),
                    fill = if (p.filled) SolidColor(Color.Black) else null,
                    stroke = if (p.filled) null else SolidColor(Color.Black),
                    strokeLineWidth = if (p.filled) 0f else W,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    private fun circle(cx: Float, cy: Float, r: Float) = "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0z"

    val Heart = icon("heart", P("M12 19.6C6 15.6 3.6 12.5 3.6 9.3c0-2.5 1.9-4.5 4.3-4.5 1.7 0 3.2.9 4.1 2.5.9-1.6 2.4-2.5 4.1-2.5 2.4 0 4.3 2 4.3 4.5 0 3.2-2.4 6.3-8.4 10.3z"))
    val Island = icon("island", P("M7 8h10a4 4 0 0 1 0 8H7a4 4 0 0 1 0-8z"), P(circle(16.4f, 12f, 1.7f), true))
    val Bell = icon("bell", P("M6.2 16.4V11a5.8 5.8 0 0 1 11.6 0v5.4l1.6 1.6H4.6z"), P("M10 20.4c1.1.9 2.9.9 4 0"))
    val Bluetooth = icon("bluetooth", P("M7.2 7.6l9.6 8.8L12 20.8V3.2l4.8 4.4-9.6 8.8"))
    val Battery = icon("battery", P("M5 7.5h12a2.5 2.5 0 0 1 2.5 2.5v4a2.5 2.5 0 0 1-2.5 2.5H5A2.5 2.5 0 0 1 2.5 14v-4A2.5 2.5 0 0 1 5 7.5z"), P("M6 10.2h7v3.6H6z", true), P("M21.2 10.6v2.8"))
    val Wrench = icon("wrench", P("M14.6 3.8a4.6 4.6 0 0 0-4.3 6.2l-6 6a1.9 1.9 0 0 0 2.7 2.7l6-6a4.6 4.6 0 0 0 6.2-4.3l-2.6 2.6-2.4-.6-.6-2.4 2.6-2.6c-.5-.2-1-.3-1.6-.3z"))
    val Accessibility = icon("accessibility", P(circle(12f, 4.6f, 1.7f), true), P("M5 8.4l7 1.2 7-1.2"), P("M12 9.6v4.6l-3 6.2"), P("M12 14.2l3 6.2"))
    val Phone = icon("phone", P("M6.8 3.6h2.4l1.4 4-2 1.5a11.8 11.8 0 0 0 6.3 6.3l1.5-2 4 1.4v2.4a2 2 0 0 1-2.2 2A16.3 16.3 0 0 1 4.8 5.8a2 2 0 0 1 2-2.2z"))
    val Camera = icon("camera", P("M4 9a2 2 0 0 1 2-2h2l1.5-2h5L16 7h2a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2z"), P(circle(12f, 12.8f, 3.3f)))
    val Mic = icon("mic", P("M12 3.2a3 3 0 0 1 3 3v5a3 3 0 0 1-6 0v-5a3 3 0 0 1 3-3z"), P("M5.6 11a6.4 6.4 0 0 0 12.8 0"), P("M12 17.4v3.4"))
    val Record = icon("record", P(circle(12f, 12f, 8.4f)), P(circle(12f, 12f, 4.4f), true))
    val Sliders = icon("sliders", P("M4 7h8.6M17.4 7H20M4 17h2.6M11.4 17H20"), P(circle(15f, 7f, 2.4f)), P(circle(9f, 17f, 2.4f)))
    val Ear = icon("ear", P("M7 9.6a5 5 0 0 1 10 0c0 2.6-1.5 3.6-2.5 4.7-.9 1-1 2.2-1 3.1a2.7 2.7 0 0 1-5 1.5"), P("M9.9 9.8a2.1 2.1 0 0 1 4.2 0c0 1.1-1.1 1.6-1.5 2.5"))
    val Shield = icon("shield", P("M12 3.2l7 2.7v5.4c0 4.4-2.9 7.7-7 9.5-4.1-1.8-7-5.1-7-9.5V5.9z"), P("M9 12.1l2.1 2.1 3.9-4"))
    val Head = icon("head", P(circle(12f, 10.4f, 4.6f)), P("M7 20.6a5 5 0 0 1 10 0"), P("M3.6 7.4a9.6 9.6 0 0 0 0 6.2"), P("M20.4 7.4a9.6 9.6 0 0 1 0 6.2"))
    val Link = icon("link", P("M10.2 13.8a4 4 0 0 0 5.6 0l3-3a4 4 0 0 0-5.6-5.6l-1.1 1.1"), P("M13.8 10.2a4 4 0 0 0-5.6 0l-3 3a4 4 0 0 0 5.6 5.6l1.1-1.1"))
    val Info = icon("info", P(circle(12f, 12f, 8.6f)), P("M12 11v5.4"), P(circle(12f, 7.7f, 1.2f), true))
    val Document = icon("document", P("M8 3.2h6l4.6 4.6V19a1.8 1.8 0 0 1-1.8 1.8H8A1.8 1.8 0 0 1 6.2 19V5A1.8 1.8 0 0 1 8 3.2z"), P("M13.8 3.4v4.6h4.6"), P("M9.4 12.6h5.2M9.4 16h5.2"))
    val Pencil = icon("pencil", P("M4.2 19.8l1-4.1L15.6 5.3a2 2 0 0 1 2.8 0l.3.3a2 2 0 0 1 0 2.8L8.3 18.8z"), P("M13.8 7.1l3.1 3.1"))
    val Press = icon("press", P(circle(12f, 12f, 2.6f), true), P(circle(12f, 12f, 5.8f)), P("M5.2 6.4a9 9 0 0 0 0 11.2M18.8 6.4a9 9 0 0 1 0 11.2"))
    val Sparkle = icon("sparkle", P("M11 3.4c.6 4.4 2.3 6.1 6.7 6.7-4.4.6-6.1 2.3-6.7 6.7-.6-4.4-2.3-6.1-6.7-6.7 4.4-.6 6.1-2.3 6.7-6.7z"), P("M18 15.2c.2 1.4.8 2 2.2 2.2-1.4.2-2 .8-2.2 2.2-.2-1.4-.8-2-2.2-2.2 1.4-.2 2-.8 2.2-2.2z", true))
    val Waves = icon("waves", P("M4 10.5v3M8 7v10M12 4.5v15M16 7v10M20 10.5v3"))
    val Display = icon("display", P("M5.2 4.6h13.6a2 2 0 0 1 2 2v8.6a2 2 0 0 1-2 2H5.2a2 2 0 0 1-2-2V6.6a2 2 0 0 1 2-2z"), P("M8.6 20.4h6.8M12 17.2v3.2"))
    val Speech = icon("speech", P("M12 4.2c4.9 0 8.8 3 8.8 6.8s-3.9 6.8-8.8 6.8c-1 0-1.9-.1-2.8-.4L5 19.4l1.1-3.5C4.3 14.6 3.2 12.9 3.2 11c0-3.8 3.9-6.8 8.8-6.8z"))
    val Contrast = icon("contrast", P(circle(12f, 12f, 8.4f)), P("M12 3.6a8.4 8.4 0 0 1 0 16.8z", true))
    val Report = icon("report", P("M12 4.2c4.9 0 8.8 3 8.8 6.8s-3.9 6.8-8.8 6.8c-1 0-1.9-.1-2.8-.4L5 19.4l1.1-3.5C4.3 14.6 3.2 12.9 3.2 11c0-3.8 3.9-6.8 8.8-6.8z"), P("M12 8v3.6"), P(circle(12f, 14.2f, 1.1f), true))
    val Star = icon("star", P("M12 3.6l2.6 5.2 5.8.9-4.2 4.1 1 5.7L12 16.8l-5.2 2.7 1-5.7-4.2-4.1 5.8-.9z"))
    val Speaker = icon("speaker", P("M4 9.4h3.2L11.6 5.6v12.8L7.2 14.6H4z"), P("M15 9a4.2 4.2 0 0 1 0 6M17.8 6.6a7.6 7.6 0 0 1 0 10.8"))
    val CloudUp = icon("cloud.up", P("M7.4 18.4a4.4 4.4 0 0 1-.6-8.8 5.6 5.6 0 0 1 10.8 1.2 3.8 3.8 0 0 1-.4 7.6"), P("M12 20.6v-7.2M9.4 15.8l2.6-2.6 2.6 2.6"))
    val CloudDown = icon("cloud.down", P("M7.4 17.6a4.4 4.4 0 0 1-.6-8.8 5.6 5.6 0 0 1 10.8 1.2 3.8 3.8 0 0 1-.4 7.6"), P("M12 12.2v8.2M9.4 17.8l2.6 2.6 2.6-2.6"))
    val Power = icon("power", P("M12 3.6v7.2"), P("M7.4 6.6a7.6 7.6 0 1 0 9.2 0"))
    val Share = icon("share", P("M12 14.2V3.8M8.6 7.2L12 3.8l3.4 3.4"), P("M8.4 10.6H7a1.8 1.8 0 0 0-1.8 1.8v6.2A1.8 1.8 0 0 0 7 20.4h10a1.8 1.8 0 0 0 1.8-1.8v-6.2a1.8 1.8 0 0 0-1.8-1.8h-1.4"))
    val Key = icon("key", P(circle(8f, 15.6f, 4f)), P("M10.9 12.7l8.3-8.3M16.2 7.4l2.4 2.4M14 9.6l1.9 1.9"))
    val Close = icon("close", P("M6.4 6.4l11.2 11.2M17.6 6.4L6.4 17.6"))
    val Plus = icon("plus", P("M12 5v14M5 12h14"))
    val Play = icon("play", P("M8.4 5.6c0-.8.9-1.3 1.6-.9l9 5.6c.7.4.7 1.4 0 1.8l-9 5.6c-.7.4-1.6-.1-1.6-.9z", true))
    val Stop = icon("stop", P("M8 6.4h8a1.6 1.6 0 0 1 1.6 1.6v8a1.6 1.6 0 0 1-1.6 1.6H8A1.6 1.6 0 0 1 6.4 16V8A1.6 1.6 0 0 1 8 6.4z", true))
    val Trash = icon("trash", P("M4.6 6.6h14.8M9.6 6.4V4.8a1.2 1.2 0 0 1 1.2-1.2h2.4a1.2 1.2 0 0 1 1.2 1.2v1.6"), P("M6.4 6.8l.9 12.1a1.8 1.8 0 0 0 1.8 1.7h5.8a1.8 1.8 0 0 0 1.8-1.7l.9-12.1"), P("M10.2 10.6v6M13.8 10.6v6"))
    val LetterL = icon("letter.l", P("M9.2 6v12h6"))
    val LetterR = icon("letter.r", P("M8.8 18V6h4.2a3.3 3.3 0 0 1 0 6.6H8.8M12.6 12.6l3.4 5.4"))
    val Note = icon("note", P("M9.4 17.2V5.6l9.4-2v11.4"), P(circle(7.2f, 17.2f, 2.3f), true), P(circle(16.6f, 15.0f, 2.3f), true), P("M9.4 9l9.4-2"))
    val Headphones = icon(
        "headphones",
        P("M4.6 14.2v-2.4a7.4 7.4 0 0 1 14.8 0v2.4"),
        P("M3.2 14.6a1.4 1.4 0 0 1 1.4-1.4h1.8a1.2 1.2 0 0 1 1.2 1.2v5a1.2 1.2 0 0 1-1.2 1.2H5a1.8 1.8 0 0 1-1.8-1.8z", true),
        P("M20.8 14.6a1.4 1.4 0 0 0-1.4-1.4h-1.8a1.2 1.2 0 0 0-1.2 1.2v5a1.2 1.2 0 0 0 1.2 1.2H19a1.8 1.8 0 0 0 1.8-1.8z", true),
    )
    val History = icon("history", P("M4.6 12a7.4 7.4 0 1 0 2.2-5.2"), P("M4.4 4.4v3.6H8"), P("M12 8.2V12l2.6 1.8"))

    /**
     * The picture for a row, from words in its name. Order matters: the first match wins, so
     * specific phrases ("hearing protection") come before general ones ("hearing").
     */
    private val rules: List<Pair<List<String>, ImageVector>> = listOf(
        listOf("heart") to Heart,
        listOf("devices", "headphone", "beats") to Headphones,
        listOf("island") to Island,
        listOf("report") to Report,
        listOf("troubleshoot") to Wrench,
        listOf("notification") to Bell,
        listOf("bluetooth") to Bluetooth,
        listOf("display over") to Display,
        listOf("battery") to Battery,
        listOf("accessib") to Accessibility,
        listOf("camera") to Camera,
        listOf("record") to Record,
        listOf("microphone") to Mic,
        listOf("protection", "loud sound", "volume limit") to Shield,
        listOf("hearing", "ear detection", "ear tip") to Ear,
        listOf("conversation") to Speech,
        listOf("head") to Head,
        listOf("stay connected", "automatic connection", "connection", "airpods connected") to Link,
        listOf("phone", "call") to Phone,
        listOf("equalizer", "adjustment", "customiz", "advanced", "transparency") to Sliders,
        listOf("noise") to Waves,
        listOf("adaptive", "assistant", "siri") to Sparkle,
        listOf("press") to Press,
        listOf("license") to Document,
        listOf("rename") to Pencil,
        listOf("appearance", "app icon", "theme") to Contrast,
        listOf("song", "music") to Note,
        listOf("audio", "volume", "sound") to Speaker,
        listOf("support", "everything essential") to Star,
        listOf("about") to Info,
        listOf("hang up") to Phone,
        listOf("mute") to Mic,
        listOf("material") to Contrast,
        listOf("name") to Pencil,
        listOf("left") to LetterL,
        listOf("right") to LetterR,
    )

    fun forName(name: String): ImageVector? {
        val n = name.lowercase()
        return rules.firstOrNull { (words, _) -> words.any { it in n } }?.second
    }
}

/**
 * A row's picture on a small glass tile: a soft fill, a light catching the top edge and a
 * hairline rim, so it reads as a little piece of the same glass as the rest of the app (but
 * drawn cheaply: tiles repeat down long lists, where real blur would cost frames).
 * [pressed] makes the picture bounce a little under your finger.
 */
@Composable
fun RowIconTile(icon: ImageVector, ink: Color, dark: Boolean, pressed: Boolean = false, size: Dp = 30.dp) {
    val pop by animateFloatAsState(if (pressed) 1.14f else 1f, spring(dampingRatio = 0.45f, stiffness = 520f), label = "tilePop")
    val shape = RoundedCornerShape(size * 0.3f)
    Box(
        Modifier
            .size(size)
            .background(ink.copy(alpha = if (dark) 0.13f else 0.07f), shape)
            .drawBehind {
                drawRoundRect(
                    Brush.verticalGradient(listOf(Color.White.copy(alpha = if (dark) 0.14f else 0.55f), Color.Transparent), 0f, this.size.height * 0.6f),
                    cornerRadius = CornerRadius(this.size.minDimension * 0.3f)
                )
            }
            .border(0.6.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = if (dark) 0.22f else 0.9f), ink.copy(alpha = 0.08f))), shape),
        contentAlignment = Alignment.Center
    ) {
        Image(
            icon,
            contentDescription = null,
            colorFilter = ColorFilter.tint(ink.copy(alpha = 0.88f)),
            modifier = Modifier.size(size * 0.62f).graphicsLayer { scaleX = pop; scaleY = pop }
        )
    }
}
