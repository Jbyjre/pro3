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
package me.kavishdevar.librepods.presentation.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import me.kavishdevar.librepods.presentation.glint.CapsuleShape
import me.kavishdevar.librepods.presentation.glint.GlassTier
import me.kavishdevar.librepods.presentation.glint.GlintComfort
import me.kavishdevar.librepods.presentation.glint.GlintHaptics
import me.kavishdevar.librepods.presentation.glint.PodsSpinner
import me.kavishdevar.librepods.presentation.glint.SpinView
import me.kavishdevar.librepods.presentation.glint.drawStudio
import me.kavishdevar.librepods.presentation.glint.glintGlass
import me.kavishdevar.librepods.presentation.theme.glintFontFamily
import androidx.compose.ui.platform.LocalView

/**
 * The AirPods in 3D: drag to turn them, flick to spin, and switch between the earbuds, the
 * case, or both, on a studio backdrop under Liquid Glass controls.
 */
@Composable
fun PodsViewerScreen(initial: SpinView = SpinView.Buds) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val solid = remember { GlintComfort.reduceTransparency(context) }
    val reduceMotion = remember { GlintComfort.reduceMotion(context) }
    val hostView = LocalView.current
    val haptics = remember(hostView) { GlintHaptics(hostView) }
    var selected by rememberSaveable { mutableIntStateOf(initial.ordinal) }
    val view = SpinView.entries[selected]
    val backdrop = rememberLayerBackdrop()
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val ink = if (dark) Color.White else Color.Black

    Box(Modifier.fillMaxSize()) {
        // Everything the glass can see and bend: the studio and the AirPods themselves.
        Box(
            Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
                .drawBehind { drawStudio(dark, keyY = 0.36f, glowY = 0.78f) },
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = view,
                transitionSpec = {
                    if (reduceMotion) fadeIn(tween(150)) togetherWith fadeOut(tween(150))
                    else (fadeIn(tween(260)) + scaleIn(tween(320), initialScale = 0.92f)) togetherWith fadeOut(tween(160))
                },
                label = "view"
            ) { v ->
                BoxWithConstraints(Modifier.fillMaxSize().padding(top = top + 64.dp, bottom = bottom + 150.dp), contentAlignment = Alignment.Center) {
                    // As large as fits, keeping each clip's shape.
                    val w = minOf(maxWidth - 32.dp, maxHeight * v.aspect, v.maxWidthDp.dp)
                    PodsSpinner(
                        view = v,
                        dark = dark,
                        modifier = Modifier.width(w).aspectRatio(v.aspect)
                    )
                }
            }
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = bottom + 24.dp, start = 16.dp, end = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                if (view.loops) "Drag to turn · flick to spin" else "Drag to turn",
                style = TextStyle(fontSize = 13.sp, fontFamily = glintFontFamily, color = ink.copy(alpha = 0.55f), textAlign = TextAlign.Center)
            )
            Spacer(Modifier.height(14.dp))
            GlassSegmented(
                options = SpinView.entries.map { it.label },
                selected = selected,
                onSelect = { if (it != selected) { selected = it; haptics.tick() } },
                backdrop = backdrop,
                dark = dark,
                solid = solid,
                reduceMotion = reduceMotion,
                modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth()
            )
        }
    }
}

/**
 * A Liquid Glass segmented control: one glass capsule with a sliding thumb inside it
 * (concentric: 4dp inset, so the thumb's curve shares the capsule's centre).
 */
@Composable
fun GlassSegmented(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    backdrop: Backdrop,
    dark: Boolean,
    solid: Boolean,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val ink = if (dark) Color.White else Color.Black
    BoxWithConstraints(
        modifier
            .height(52.dp)
            .glintGlass(backdrop, dark, solid, CapsuleShape, GlassTier.Floating)
            .padding(4.dp)
    ) {
        val segment = maxWidth / options.size
        val x by animateDpAsState(segment * selected, if (reduceMotion) tween(0) else spring(0.78f, 420f), label = "thumb")
        Box(
            Modifier
                .offset { androidx.compose.ui.unit.IntOffset(x.roundToPx(), 0) }
                .width(segment)
                .fillMaxHeight()
                .background(if (dark) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = if (solid) 1f else 0.85f), CapsuleShape)
        )
        Row(Modifier.fillMaxSize().selectableGroup(), horizontalArrangement = Arrangement.Center) {
            options.forEachIndexed { i, label ->
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CapsuleShape)
                        .selectable(selected = i == selected, role = Role.Tab, onClick = { onSelect(i) }),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        style = TextStyle(
                            fontSize = 15.sp,
                            fontFamily = glintFontFamily,
                            fontWeight = if (i == selected) FontWeight.SemiBold else FontWeight.Medium,
                            color = ink.copy(alpha = if (i == selected) 1f else 0.7f)
                        )
                    )
                }
            }
        }
    }
}
