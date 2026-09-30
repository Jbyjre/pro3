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

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import me.kavishdevar.librepods.presentation.theme.glintFontFamily
import me.kavishdevar.librepods.presentation.overlays.PodsSnapshot

/**
 * The top of the main screen: the AirPods on a softly lit glass stage, with battery chips
 * made of real refracting glass (the chips bend the artwork behind them; inside our own app
 * that content is ours to sample, unlike the system overlays). Tilting the phone or touching
 * the stage moves the light.
 */
@Composable
fun GlintHero(snapshot: PodsSnapshot, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val density = LocalDensity.current.density
    val tilt by rememberTiltLight(GlintComfort.tiltLight(context))
    var touchLight by remember { mutableStateOf<Offset?>(null) }
    val light = touchLight ?: tilt
    val reduceTransparency = remember { GlintComfort.reduceTransparency(context) }
    val backdrop = rememberLayerBackdrop()
    val stageLook = remember(dark) { GlassLooks.card(dark, density) }

    val description = buildString {
        append("Left ${snapshot.left?.let { "$it percent" } ?: "not available"}. ")
        append("Right ${snapshot.right?.let { "$it percent" } ?: "not available"}. ")
        append("Case ${snapshot.case?.let { "$it percent" } ?: "not available"}.")
    }

    Box(
        modifier
            .fillMaxWidth()
            .height(276.dp)
            .semantics { contentDescription = description }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    fun toLight(p: Offset) = Offset(
                        ((size.width / 2f - p.x) / (size.width / 2f)).coerceIn(-1f, 1f),
                        ((size.height / 3f - p.y) / (size.height / 2f)).coerceIn(-1f, 1f)
                    )
                    touchLight = toLight(down.position)
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull() ?: break
                        if (!ch.pressed) break
                        touchLight = toLight(ch.position)
                    }
                    touchLight = null
                }
            }
    ) {
        // Stage: the art, captured as the backdrop the glass chips refract.
        Box(
            Modifier
                .fillMaxWidth()
                .height(276.dp)
                .layerBackdrop(backdrop)
                .drawBehind {
                    val r = 34.dp.toPx()
                    val rect = Rect(Offset.Zero, size)
                    drawGlass(roundRectPath(rect, r), rect, r, stageLook, light, blur = null, blurredElsewhere = !reduceTransparency)
                }
        ) {
            Row(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 26.dp)
                    .graphicsLayer {
                        rotationY = light.x * 6f
                        rotationX = -light.y * 4f
                        cameraDistance = 14f * density
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy((-12).dp), verticalAlignment = Alignment.Bottom) {
                    PodBud(Modifier.height(168.dp), mirrored = false, light = light, visual = BudVisual(snapshot.left != null, snapshot.leftCharging, snapshot.leftInEar))
                    PodBud(Modifier.height(168.dp), mirrored = true, light = light, visual = BudVisual(snapshot.right != null, snapshot.rightCharging, snapshot.rightInEar))
                }
                PodCase(Modifier.width(128.dp), light = light, visual = CaseVisual(snapshot.case != null, snapshot.caseCharging, snapshot.case, snapshot.lidOpen))
            }
        }
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            BatteryChip("L", snapshot.left, snapshot.leftCharging, backdrop, dark)
            BatteryChip("R", snapshot.right, snapshot.rightCharging, backdrop, dark)
            BatteryChip("Case", snapshot.case, snapshot.caseCharging, backdrop, dark)
        }
    }
}

@Composable
private fun BatteryChip(
    label: String,
    level: Int?,
    charging: Boolean,
    backdrop: com.kyant.backdrop.backdrops.LayerBackdrop,
    dark: Boolean,
) {
    val fg = if (dark) Color(0xFFF5F5F7) else Color(0xFF1C1C1E)
    Row(
        Modifier
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedCornerShape(50) },
                effects = {
                    vibrancy()
                    blur(3f.dp.toPx())
                    lens(
                        refractionHeight = 10f.dp.toPx(),
                        refractionAmount = 18f.dp.toPx(),
                        depthEffect = true,
                        chromaticAberration = true
                    )
                },
                highlight = { Highlight.Ambient.copy(alpha = 0.9f) },
                onDrawSurface = {
                    drawRect(if (dark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.35f))
                }
            )
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BatteryRing(level, charging, size = 18.dp, stroke = 2.5.dp, track = fg.copy(alpha = 0.15f), showLabel = false)
        Spacer(Modifier.width(6.dp))
        Column {
            Text(label, style = TextStyle(fontFamily = glintFontFamily, fontSize = 10.sp, fontWeight = FontWeight.Medium, color = fg.copy(alpha = 0.6f)))
            Text(
                level?.let { "$it%" } ?: "–",
                style = TextStyle(fontFamily = glintFontFamily, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = fg)
            )
        }
    }
}
