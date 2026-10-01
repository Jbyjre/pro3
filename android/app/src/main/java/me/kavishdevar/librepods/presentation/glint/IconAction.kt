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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import kotlinx.coroutines.delay

/**
 * A round button that shows a picture instead of words. Screen readers still hear [label];
 * pressing and holding shows [label] in a small bubble above it (then it fades), so the
 * meaning is never more than a long-press away. Squishes under the finger, ticks on press.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun IconAction(
    icon: ImageVector,
    label: String,
    ink: Color,
    dark: Boolean,
    modifier: Modifier = Modifier,
    tint: Color = ink,
    size: Dp = 44.dp,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val reduce = remember { GlintComfort.reduceMotion(context) }
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val hint = remember { MutableTransitionState(false) }
    LaunchedEffect(pressed) { if (pressed && enabled) GlintHaptics(view).tick() }
    LaunchedEffect(hint.targetState) {
        if (hint.targetState) { delay(1600); hint.targetState = false }
    }
    val squish by animateFloatAsState(if (pressed && !reduce) 0.88f else 1f, spring(dampingRatio = 0.42f, stiffness = 650f), label = "squish")
    Box(
        modifier
            .size(size)
            .graphicsLayer { scaleX = squish; scaleY = squish; alpha = if (enabled) 1f else 0.4f }
            .clip(CircleShape)
            .background(if (dark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.06f))
            .touchGlow(ink, enabled)
            .semantics { role = Role.Button; contentDescription = label }
            .combinedClickable(
                interactionSource = source,
                indication = null,
                enabled = enabled,
                onLongClick = { hint.targetState = true; GlintHaptics(view).expand() },
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center
    ) {
        Image(icon, contentDescription = null, colorFilter = ColorFilter.tint(tint), modifier = Modifier.size(size * 0.5f))
    }
    if (hint.currentState || hint.targetState) {
        val lift = with(LocalDensity.current) { (size + 8.dp).roundToPx() }
        Popup(alignment = Alignment.TopCenter, offset = IntOffset(0, -lift)) {
            AnimatedVisibility(
                hint,
                enter = fadeIn() + scaleIn(spring(0.6f, 500f), 0.7f, TransformOrigin(0.5f, 1f)),
                exit = fadeOut() + scaleOut(targetScale = 0.85f, transformOrigin = TransformOrigin(0.5f, 1f))
            ) {
                Text(
                    label,
                    style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = if (dark) Color.Black else Color.White),
                    modifier = Modifier
                        .background(if (dark) Color.White.copy(alpha = 0.92f) else Color(0xEE1C1C1E), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}
