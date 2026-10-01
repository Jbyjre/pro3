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

import android.content.Intent
import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.delay
import me.kavishdevar.librepods.audio.AirPodsRecorder
import me.kavishdevar.librepods.presentation.components.InfoTip
import me.kavishdevar.librepods.presentation.theme.glintFontFamily
import me.kavishdevar.librepods.services.GlintStatus
import me.kavishdevar.librepods.services.LinkState
import me.kavishdevar.librepods.services.ServiceManager
import java.io.File

/**
 * Experimental: record from the AirPods' own microphones without switching Bluetooth to call
 * quality. Says plainly whether audio is actually arriving, since this may not work on every
 * firmware.
 */
@Composable
fun RecorderScreen() {
    val context = LocalContext.current
    val rec by AirPodsRecorder.state.collectAsState()
    val link by GlintStatus.link.collectAsState()
    val dark = isSystemInDarkTheme()
    val ink = if (dark) Color.White else Color.Black
    val muted = ink.copy(alpha = 0.6f)
    val card = if (dark) Color(0xFF1C1C1E) else Color.White
    val red = if (dark) Color(0xFFFF453A) else Color(0xFFFF3B30)
    val topPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 84.dp
    val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp
    var files by remember { mutableStateOf(AirPodsRecorder.recordings(context)) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(rec.recording) {
        while (rec.recording) { delay(250); now = System.currentTimeMillis() }
        files = AirPodsRecorder.recordings(context)
    }
    val player = remember { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember { mutableStateOf<File?>(null) }
    DisposableEffect(Unit) { onDispose { player.value?.release() } }
    val connected = link is LinkState.Connected
    val elapsed = if (rec.recording) (now - rec.startedAt) / 1000 else 0

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer)
            .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(Modifier.height(topPadding - 14.dp))
        Column(
            Modifier.fillMaxWidth().background(card, RoundedCornerShape(28.dp)).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "%d:%02d".format(elapsed / 60, elapsed % 60),
                style = TextStyle(fontFamily = glintFontFamily, fontSize = 44.sp, fontWeight = FontWeight.SemiBold, color = ink)
            )
            Text(
                when {
                    error != null -> error!!
                    !connected && !rec.recording -> "Connect your AirPods to record"
                    rec.recording && rec.frames == 0 && elapsed >= 4 -> "No audio yet. Your firmware may not support this"
                    rec.recording && rec.frames == 0 -> "Starting…"
                    rec.recording -> "Recording"
                    else -> "Uses your AirPods' microphones"
                },
                style = TextStyle(fontFamily = glintFontFamily, fontSize = 14.sp, color = muted, textAlign = TextAlign.Center)
            )
            Spacer(Modifier.height(20.dp))
            // iOS-style record button: a ring with a red dot that becomes a rounded square.
            Box(
                Modifier
                    .size(76.dp)
                    .border(4.dp, ink.copy(alpha = if (connected || rec.recording) 0.85f else 0.25f), CircleShape)
                    .semantics { role = Role.Button; contentDescription = if (rec.recording) "Stop recording" else "Start recording" }
                    .clickable {
                        val s = ServiceManager.getService()
                        error = null
                        if (rec.recording) s?.stopRecording()
                        else if (connected) {
                            player.value?.release(); player.value = null; playing = null
                            if (s?.startRecording() != true) error = AirPodsRecorder.state.value.error ?: "Couldn't start recording."
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier.size(if (rec.recording) 30.dp else 60.dp)
                        .background(red.copy(alpha = if (connected || rec.recording) 1f else 0.35f), if (rec.recording) RoundedCornerShape(8.dp) else CircleShape)
                )
            }
        }

        if (files.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().background(card, RoundedCornerShape(28.dp)).padding(vertical = 6.dp)) {
                files.forEach { f ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(f.nameWithoutExtension, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontFamily = glintFontFamily, fontSize = 15.sp, color = ink))
                            val secs = ((f.length() - 44).coerceAtLeast(0) / 128_000)
                            Text("%d:%02d".format(secs / 60, secs % 60), style = TextStyle(fontFamily = glintFontFamily, fontSize = 12.sp, color = muted))
                        }
                        Action(if (playing == f) "Stop" else "Play", ink) {
                            player.value?.release(); player.value = null
                            if (playing == f) playing = null else {
                                playing = f
                                player.value = MediaPlayer().apply {
                                    setDataSource(f.path)
                                    setOnCompletionListener { playing = null }
                                    prepare(); start()
                                }
                            }
                        }
                        Action("Share", ink) {
                            val uri = FileProvider.getUriForFile(context, context.packageName + ".provider", f)
                            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("audio/wav").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), null))
                        }
                        Action("Delete", red) {
                            if (playing == f) { player.value?.release(); player.value = null; playing = null }
                            f.delete(); files = AirPodsRecorder.recordings(context)
                        }
                    }
                }
            }
        }

        Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Experimental", style = TextStyle(fontFamily = glintFontFamily, fontSize = 13.sp, color = muted))
            InfoTip(
                "About the recorder",
                "Uses the same microphone stream Apple devices use, so music can stay in high quality while you record. " +
                    "It may not work with every AirPods firmware; if no audio arrives, the screen says so. Recordings stay on this phone."
            )
        }
        Spacer(Modifier.height(bottomPadding))
    }
}

@Composable
private fun Action(label: String, color: Color, onClick: () -> Unit) {
    Text(
        label,
        style = TextStyle(fontFamily = glintFontFamily, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = color),
        modifier = Modifier.semantics { role = Role.Button }.clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 8.dp)
    )
}
