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

package me.kavishdevar.librepods.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.kavishdevar.librepods.R
import me.kavishdevar.librepods.presentation.glint.GlintComfort
import me.kavishdevar.librepods.presentation.theme.AppIcon
import me.kavishdevar.librepods.presentation.theme.glintFontFamily

/** Settings > App icon: Black, White or Graphite, each shown as it looks on the home screen. */
@Composable
fun AppIconPicker(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val ink = if (dark) Color.White else Color.Black
    val reduce = remember { GlintComfort.reduceMotion(context) }
    var current by remember { mutableStateOf(AppIcon.current(context)) }
    var changed by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp)) {
        Text(
            "App icon",
            style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = glintFontFamily, color = ink.copy(alpha = 0.6f)),
            modifier = Modifier.padding(start = 16.dp, bottom = 10.dp)
        )
        Row(
            Modifier
                .fillMaxWidth()
                .background(if (dark) Color(0xFF1C1C1E) else Color.White, RoundedCornerShape(28.dp))
                .padding(vertical = 14.dp)
                .selectableGroup(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            AppIcon.entries.forEach { icon ->
                val selected = icon == current
                val lift by animateFloatAsState(if (selected) 1f else 0f, if (reduce) tween(0) else spring(0.6f, 420f), label = "icon")
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .selectable(selected = selected, role = Role.RadioButton) {
                            if (icon != current && AppIcon.select(context, icon)) { current = icon; changed = true }
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Box(
                        Modifier
                            .size(66.dp)
                            .graphicsLayer { val s = 0.94f + 0.06f * lift; scaleX = s; scaleY = s }
                            .border(2.5.dp, (if (dark) Color.White else Color(0xFF0A84FF)).copy(alpha = lift), CircleShape)
                            .padding(5.dp),
                        contentAlignment = Alignment.Center,
                    ) { IconPreview(icon, 56.dp) }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        icon.label,
                        style = TextStyle(fontFamily = glintFontFamily, fontSize = 14.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, color = ink.copy(alpha = 0.6f + 0.4f * lift)),
                    )
                }
            }
        }
        if (changed) {
            Text(
                "Your home screen updates in a moment. If the icon leaves your home screen, add it again from the app list.",
                style = TextStyle(fontFamily = glintFontFamily, fontSize = 13.sp, color = ink.copy(alpha = 0.55f)),
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
            )
        }
    }
}

/** The icon's two layers in a circle, zoomed to the part launchers show (72 of 108). */
@Composable
fun IconPreview(icon: AppIcon, size: Dp) {
    val (bg, fg) = when (icon) {
        AppIcon.Black -> R.drawable.ic_launcher_background to R.drawable.ic_launcher_foreground
        AppIcon.White -> R.drawable.ic_launcher_white_background to R.drawable.ic_launcher_white_foreground
        AppIcon.Graphite -> R.drawable.ic_launcher_graphite_background to R.drawable.ic_launcher_graphite_foreground
    }
    Box(Modifier.size(size).clip(CircleShape)) {
        val zoom = Modifier.fillMaxSize().graphicsLayer { scaleX = 1.5f; scaleY = 1.5f }
        Image(painterResource(bg), null, zoom)
        Image(painterResource(fg), null, zoom)
    }
}
