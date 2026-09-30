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

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import me.kavishdevar.librepods.presentation.components.StyledList
import me.kavishdevar.librepods.presentation.components.StyledToggle
import me.kavishdevar.librepods.presentation.glint.GlintComfort
import me.kavishdevar.librepods.presentation.theme.sectionHeader
import me.kavishdevar.librepods.presentation.screens.onboarding.StayConnectedSteps

/** Settings > Stay connected & appearance: the Samsung setup steps plus glass/motion comfort. */
@Composable
fun StayConnectedScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    val topPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 84.dp
    val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(Modifier.height(topPadding))
        SectionHeader("Keep Glint running")
        StayConnectedSteps()
        Spacer(Modifier.height(8.dp))
        var tilt by remember { mutableStateOf(prefs.getBoolean(GlintComfort.PREF_TILT_LIGHT, true)) }
        var motion by remember { mutableStateOf(prefs.getBoolean(GlintComfort.PREF_REDUCE_MOTION, false)) }
        var transparency by remember { mutableStateOf(prefs.getBoolean(GlintComfort.PREF_REDUCE_TRANSPARENCY, false)) }
        var modeIsland by remember { mutableStateOf(prefs.getBoolean(PREF_MODE_ISLAND, true)) }
        // The app's own list and green switches, so this page matches the rest of Settings.
        StyledList(title = "Glass & motion") {
            StyledToggle(
                label = "Light follows tilt",
                description = "Highlights shift as you tilt the phone. Off saves a little battery while pop-ups show.",
                checked = tilt,
                onCheckedChange = { tilt = it; prefs.edit { putBoolean(GlintComfort.PREF_TILT_LIGHT, it) } },
            )
            StyledToggle(
                label = "Reduce motion",
                description = "Simple fades and still pictures instead of springy shapes and videos. Also on automatically when the phone's animations are off.",
                checked = motion,
                onCheckedChange = { motion = it; prefs.edit { putBoolean(GlintComfort.PREF_REDUCE_MOTION, it) } },
            )
            StyledToggle(
                label = "Reduce transparency",
                description = "Solid backgrounds behind pop-up text instead of see-through glass.",
                checked = transparency,
                onCheckedChange = { transparency = it; prefs.edit { putBoolean(GlintComfort.PREF_REDUCE_TRANSPARENCY, it) } },
            )
            StyledToggle(
                label = "Show listening mode changes",
                description = "A small island appears when you switch modes on the AirPods themselves.",
                checked = modeIsland,
                onCheckedChange = { modeIsland = it; prefs.edit { putBoolean(PREF_MODE_ISLAND, it) } },
            )
        }
        Spacer(Modifier.height(bottomPadding))
    }
}

private const val PREF_MODE_ISLAND = "glint_island_mode_changes"

@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmallEmphasized,
        color = MaterialTheme.colorScheme.sectionHeader,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
    )
}
