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

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import kotlinx.coroutines.launch
import me.kavishdevar.librepods.presentation.components.InfoTip
import me.kavishdevar.librepods.presentation.components.StyledToggle
import me.kavishdevar.librepods.presentation.theme.glintFontFamily
import me.kavishdevar.librepods.services.HeartLink
import me.kavishdevar.librepods.services.HeartRate
import me.kavishdevar.librepods.services.PREF_LINK_BLE
import me.kavishdevar.librepods.services.PREF_LINK_BROADCAST
import me.kavishdevar.librepods.services.PREF_LINK_WEBHOOK
import me.kavishdevar.librepods.services.PREF_LINK_WEBHOOK_SECS
import me.kavishdevar.librepods.services.PREF_LINK_WEBHOOK_URL
import java.text.DateFormat
import java.util.Date

/**
 * Share live heart rate: a Bluetooth heart-rate sensor for watches and fitness apps, posts to
 * a web address, and a broadcast for automation apps. Everything is off until switched on and
 * only runs while you measure.
 */
@Composable
fun HeartShareScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    val status by HeartLink.status.collectAsState()
    val heart by HeartRate.state.collectAsState()
    val dark = isSystemInDarkTheme()
    val ink = if (dark) Color.White else Color.Black
    val muted = ink.copy(alpha = 0.6f)
    val card = if (dark) Color(0xFF1C1C1E) else Color.White
    val accent = if (dark) Color(0xFFF04A50) else Color(0xFFE0303A)
    val topPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 84.dp
    val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp
    val measuring = heart.status != HeartRate.Status.Off
    val scope = rememberCoroutineScope()

    var ble by remember { mutableStateOf(prefs.getBoolean(PREF_LINK_BLE, false)) }
    var hook by remember { mutableStateOf(prefs.getBoolean(PREF_LINK_WEBHOOK, false)) }
    var url by remember { mutableStateOf(prefs.getString(PREF_LINK_WEBHOOK_URL, "").orEmpty()) }
    var every by remember { mutableIntStateOf(prefs.getInt(PREF_LINK_WEBHOOK_SECS, HeartLink.DEFAULT_WEBHOOK_SECS)) }
    var cast by remember { mutableStateOf(prefs.getBoolean(PREF_LINK_BROADCAST, false)) }
    var testResult by remember { mutableStateOf<String?>(null) }

    fun setBle(on: Boolean) {
        ble = on
        prefs.edit { putBoolean(PREF_LINK_BLE, on) }
        HeartLink.refresh()
    }
    val askNearby = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        setBle(granted.values.all { it })
    }
    val label = TextStyle(fontFamily = glintFontFamily, fontSize = 13.sp, color = muted)

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(Modifier.height(topPadding - 14.dp))

        // What's happening right now.
        Row(
            Modifier.fillMaxWidth().background(card, RoundedCornerShape(28.dp)).padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(9.dp).background(if (measuring) accent else muted.copy(alpha = 0.35f), CircleShape))
            Spacer(Modifier.width(10.dp))
            Text(
                when {
                    !ble && !hook && !cast -> "Nothing is shared"
                    measuring -> "Sharing while you measure"
                    else -> "Starts when you measure"
                },
                modifier = Modifier.weight(1f),
                style = TextStyle(fontFamily = glintFontFamily, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = ink)
            )
            InfoTip(
                "When it shares",
                "Each option is off until you turn it on, and only sends while heart rate is being measured. " +
                    "When you stop, the web address and automation apps get a final \"stopped\" message."
            )
        }

        // Bluetooth heart-rate sensor.
        Column(Modifier.fillMaxWidth().background(card, RoundedCornerShape(28.dp)).padding(horizontal = 4.dp)) {
            StyledToggle(
                label = "Bluetooth sensor",
                description = when (val b = status.beacon) {
                    HeartLink.Beacon.Off -> if (ble) "Ready: starts when you measure" else "For watches, bike computers and gym apps"
                    HeartLink.Beacon.Waiting -> "Ready: starts when you measure"
                    is HeartLink.Beacon.Advertising -> if (b.listeners == 0) "Visible as a heart-rate sensor" else "${b.listeners} connected"
                    is HeartLink.Beacon.Failed -> b.reason
                },
                checked = ble,
                onCheckedChange = { on ->
                    val perms = arrayOf(Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT)
                    if (on && perms.any { context.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }) askNearby.launch(perms)
                    else setBle(on)
                }
            )
            Row(Modifier.padding(start = 14.dp, end = 6.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Pair it like a chest strap", modifier = Modifier.weight(1f), style = label)
                InfoTip(
                    "Bluetooth sensor",
                    "Your phone appears as a standard Bluetooth heart-rate sensor (the kind a chest strap is), using your phone's name. " +
                        "In the other device or app, search for a heart-rate sensor and pick your phone. " +
                        "Like a chest strap, any nearby device can connect while it's on, so turn it off when you don't need it."
                )
            }
        }

        // Web address.
        Column(Modifier.fillMaxWidth().background(card, RoundedCornerShape(28.dp)).padding(horizontal = 4.dp)) {
            StyledToggle(
                label = "Send to a web address",
                description = when {
                    !hook -> "Home Assistant, Zapier, IFTTT or your own app"
                    status.webhookError != null -> status.webhookError
                    status.webhookOkMs > 0 -> "Last sent " + DateFormat.getTimeInstance(DateFormat.MEDIUM).format(Date(status.webhookOkMs))
                    else -> "Every $every seconds while measuring"
                },
                checked = hook,
                onCheckedChange = { hook = it; prefs.edit { putBoolean(PREF_LINK_WEBHOOK, it) } }
            )
            if (hook) {
                Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val fieldBg = if (dark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f)
                    Box(Modifier.fillMaxWidth().background(fieldBg, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 12.dp)) {
                        if (url.isEmpty()) Text("https://…", style = TextStyle(fontFamily = glintFontFamily, fontSize = 15.sp, color = muted))
                        BasicTextField(
                            value = url,
                            onValueChange = { url = it.trim(); prefs.edit { putString(PREF_LINK_WEBHOOK_URL, url) }; testResult = null },
                            singleLine = true,
                            textStyle = TextStyle(fontFamily = glintFontFamily, fontSize = 15.sp, color = ink),
                            cursorBrush = SolidColor(accent),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (url.isNotEmpty() && !HeartLink.isValidUrl(url)) {
                        Text("Use an address starting with https://", style = label.copy(color = accent))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Every", style = TextStyle(fontFamily = glintFontFamily, fontSize = 16.sp, color = ink), modifier = Modifier.weight(1f))
                        Pill("−", ink, dark) { every = (every - 5).coerceAtLeast(HeartLink.MIN_WEBHOOK_SECS); prefs.edit { putInt(PREF_LINK_WEBHOOK_SECS, every) } }
                        Text("$every s", style = TextStyle(fontFamily = glintFontFamily, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = ink), modifier = Modifier.padding(horizontal = 14.dp))
                        Pill("+", ink, dark) { every = (every + 5).coerceAtMost(300); prefs.edit { putInt(PREF_LINK_WEBHOOK_SECS, every) } }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Send a test",
                            style = TextStyle(fontFamily = glintFontFamily, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = accent),
                            modifier = Modifier.clickable {
                                testResult = "Sending…"
                                scope.launch { testResult = HeartLink.test(context) ?: "It worked" }
                            }.padding(vertical = 6.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        testResult?.let { Text(it, style = label, modifier = Modifier.weight(1f)) } ?: Spacer(Modifier.weight(1f))
                        InfoTip(
                            "What gets sent",
                            "A small JSON message by POST: {\"bpm\": 72, \"time\": 1767225600000, \"status\": \"live\"}. " +
                                "time is in milliseconds since 1970 (UTC). status is \"live\", \"stopped\" (bpm empty) or \"test\". " +
                                "Only https addresses, so your heart rate is encrypted on the way."
                        )
                    }
                }
            }
        }

        // Automation apps.
        Column(Modifier.fillMaxWidth().background(card, RoundedCornerShape(28.dp)).padding(horizontal = 4.dp)) {
            StyledToggle(
                label = "Automation apps",
                description = if (cast && status.broadcasts > 0) "${status.broadcasts} updates sent" else "Tasker, MacroDroid and similar",
                checked = cast,
                onCheckedChange = { cast = it; prefs.edit { putBoolean(PREF_LINK_BROADCAST, it) } }
            )
            Row(Modifier.padding(start = 14.dp, end = 6.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(HeartLink.ACTION, modifier = Modifier.weight(1f), maxLines = 1, style = label.copy(fontSize = 11.sp))
                InfoTip(
                    "Automation apps",
                    "With every reading Glint sends an Android broadcast named ${HeartLink.ACTION}. " +
                        "Its extras: bpm (number), time (milliseconds since 1970) and status (\"live\" or \"stopped\"). " +
                        "In Tasker use the Intent Received event with that action; in MacroDroid, the Intent Received trigger."
                )
            }
        }

        Spacer(Modifier.height(bottomPadding))
    }
}

@Composable
private fun Pill(symbol: String, ink: Color, dark: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .background(if (dark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.06f), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(symbol, style = TextStyle(fontFamily = glintFontFamily, fontSize = 20.sp, fontWeight = FontWeight.Medium, color = ink))
    }
}
