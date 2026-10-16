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
package me.kavishdevar.librepods.presentation.screens

import android.content.Context
import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import me.kavishdevar.librepods.presentation.components.LiquidSegments
import me.kavishdevar.librepods.presentation.components.StyledList
import me.kavishdevar.librepods.presentation.components.StyledListItem
import me.kavishdevar.librepods.presentation.glint.riseIn
import me.kavishdevar.librepods.presentation.theme.glintFontFamily
import me.kavishdevar.librepods.services.PhoneProfile
import me.kavishdevar.librepods.utils.CompanionLink

/**
 * Your phone: pro tuned for the Galaxy S26 FE or the Pixel 6 Pro (Jake, 2026-10-15). Automatic
 * by default. The drawing at the top is the phone's top edge with the Dynamic Island stretched
 * as far as it goes on that phone: flat sides for the S26 FE, the curve shaded on the Pixel.
 */
@Composable
fun PhoneProfileScreen() {
    val context = LocalContext.current
    val view = LocalView.current
    LaunchedEffect(Unit) { PhoneProfile.load(context) }
    val choice by PhoneProfile.choice.collectAsState()
    val tuning by PhoneProfile.current.collectAsState()
    var awake by remember { mutableStateOf(keptAwake(context)) }
    // Coming back from Android's battery page: show the new state.
    val lifecycle = LocalLifecycleOwner.current
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { awake = keptAwake(context) }
    }
    PhoneProfileContent(
        choice = choice,
        tuning = tuning,
        detected = PhoneProfile.detected,
        keptAwake = awake,
        onChoose = {
            view.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
            PhoneProfile.choose(context, it)
        },
        onKeepAwake = { openKeepAwake(context, it) },
    )
}

@Composable
fun PhoneProfileContent(
    choice: PhoneProfile.Choice,
    tuning: PhoneProfile.Tuning,
    detected: PhoneProfile.Phone,
    keptAwake: Boolean,
    onChoose: (PhoneProfile.Choice) -> Unit,
    /** Opens Android's battery setting, or with true Samsung's own sleeping-apps page. */
    onKeepAwake: (samsung: Boolean) -> Unit,
) {
    val dark = isSystemInDarkTheme()
    val ink = if (dark) Color.White else Color.Black
    val muted = ink.copy(alpha = 0.6f)
    val card = if (dark) Color(0xFF1C1C1E) else Color.White
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 84.dp
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp
    val phone = tuning.phone
    var index = 0
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(top))
        Column(Modifier.riseIn(index++).fillMaxWidth().background(card, RoundedCornerShape(28.dp)).padding(18.dp)) {
            PhoneEdgeDiagram(tuning, ink, dark, card)
            Spacer(Modifier.height(14.dp))
            Text(phone.label.takeIf { phone != PhoneProfile.Phone.Other } ?: "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}",
                style = TextStyle(fontFamily = glintFontFamily, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = ink))
            Text(specLine(phone), style = TextStyle(fontFamily = glintFontFamily, fontSize = 13.sp, lineHeight = 18.sp, color = muted),
                modifier = Modifier.padding(top = 2.dp))
        }

        SectionLabel("Tune pro for", ink)
        val choices = PhoneProfile.Choice.entries
        LiquidSegments(
            listOf("Automatic", "Galaxy S26 FE", "Pixel 6 Pro"), choices.indexOf(choice),
            { onChoose(choices[it]) },
            modifier = Modifier.riseIn(index++),
        )
        Text(
            when {
                choice != PhoneProfile.Choice.Auto && detected != PhoneProfile.Phone.Other && detected != phone ->
                    "This phone is a ${detected.label}, but it's set to the ${phone.label}'s tuning. Automatic would pick the ${detected.label}."
                choice != PhoneProfile.Choice.Auto && detected == PhoneProfile.Phone.Other ->
                    "Set by you. This phone isn't one of the two, so Automatic would use pro's general settings."
                choice != PhoneProfile.Choice.Auto -> "Set by you; it matches this phone."
                detected == PhoneProfile.Phone.Other -> "Automatic: this phone isn't a Galaxy S26 FE or a Pixel 6 Pro, so pro uses its general settings."
                else -> "Automatic: this is a ${detected.label}."
            },
            style = TextStyle(fontFamily = glintFontFamily, fontSize = 13.sp, lineHeight = 18.sp, color = ink.copy(alpha = 0.55f)),
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        SectionLabel("What changes", ink)
        Column(Modifier.riseIn(index++).fillMaxWidth().background(card, RoundedCornerShape(28.dp)).padding(horizontal = 18.dp, vertical = 8.dp)) {
            val notes = notes(phone)
            notes.forEachIndexed { i, (title, body) ->
                Column(Modifier.padding(vertical = 10.dp)) {
                    Text(title, style = TextStyle(fontFamily = glintFontFamily, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = ink))
                    Text(body, style = TextStyle(fontFamily = glintFontFamily, fontSize = 13.sp, lineHeight = 18.sp, color = muted), modifier = Modifier.padding(top = 2.dp))
                }
                if (i < notes.lastIndex) Spacer(Modifier.fillMaxWidth().height(1.dp).background(ink.copy(alpha = if (dark) 0.10f else 0.07f)))
            }
        }

        StyledList(title = "Keep pro running", modifier = Modifier.riseIn(index++)) {
            StyledListItem(
                name = if (keptAwake) "Battery use: Unrestricted" else "Set battery use to Unrestricted",
                description = if (keptAwake) "Done. Android won't pause pro in the background"
                    else "Without it, Android may pause pro in the background, which stops the island and the AirPods link",
                orientation = me.kavishdevar.librepods.presentation.components.ListItemOrientation.Vertical,
                onClick = { onKeepAwake(false) },
            )
            // Samsung has its own list on top of Android's setting, and other apps can't read it.
            if (tuning.keepAwake == PhoneProfile.KeepAwake.Samsung) StyledListItem(
                name = "Add pro to Never sleeping apps",
                description = "Samsung's own list (Battery > Background usage limits), needed as well. pro can't see this list, so check it once",
                orientation = me.kavishdevar.librepods.presentation.components.ListItemOrientation.Vertical,
                onClick = { onKeepAwake(true) },
            )
        }
        Text(
            "Specs were checked against Samsung's and Google's published figures and launch reviews. " +
                "How each setting feels on your own phone is still for you to judge.",
            style = TextStyle(fontFamily = glintFontFamily, fontSize = 12.sp, lineHeight = 16.sp, color = ink.copy(alpha = 0.45f)),
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(bottom))
    }
}

/**
 * The phone's top edge, drawn: its frame, the camera and the Dynamic Island stretched as far
 * as pro lets it on this phone, with the distance it keeps from each side marked. Changing the
 * phone eases the curve and the margin across on a spring.
 */
@Composable
private fun PhoneEdgeDiagram(tuning: PhoneProfile.Tuning, ink: Color, dark: Boolean, card: Color) {
    val curve by animateFloatAsState(if (tuning.phone == PhoneProfile.Phone.Pixel6Pro) 1f else 0f, spring(0.85f, 260f), label = "curve")
    val edge by animateFloatAsState(tuning.edgeDp, spring(0.8f, 260f), label = "edge")
    val measurer = rememberTextMeasurer()
    val label = TextStyle(fontFamily = glintFontFamily, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = ink.copy(alpha = 0.6f))
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(132.dp)
            .semantics {
                contentDescription = "The Dynamic Island stretched across the top of the screen, " +
                    "${tuning.edgeDp.toInt()} dp from each side" + if (tuning.phone == PhoneProfile.Phone.Pixel6Pro) ", clear of the curved edges" else ""
            }
    ) {
        clipRect { drawPhoneTop(dark, curve, edge, measurer, label) }
        // The phone fades into the card below its top edge (only the top matters here).
        drawRect(Brush.verticalGradient(listOf(card.copy(alpha = 0f), card), size.height * 0.62f, size.height))
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPhoneTop(
    dark: Boolean,
    curve: Float,
    edge: Float,
    measurer: androidx.compose.ui.text.TextMeasurer,
    label: TextStyle,
) {
        // The phone is about 411 dp wide on both phones; draw it at the card's width less a frame.
        val frame = 7.dp.toPx()
        val phoneW = size.width
        val scale = (phoneW - 2 * frame) / 411f
        val corner = 44.dp.toPx()
        val body = if (dark) Color(0xFF2C2C2E) else Color(0xFFE6E6EA)
        val glass = if (dark) Color(0xFF0B0B0D) else Color(0xFF16161A)
        // Frame and screen, cropped by the card's bottom (only the top edge matters here).
        drawRoundRect(body, Offset.Zero, Size(phoneW, size.height + corner), CornerRadius(corner + frame))
        val sx = frame; val sy = frame
        val sw = phoneW - 2 * frame
        drawRoundRect(glass, Offset(sx, sy), Size(sw, size.height + corner), CornerRadius(corner))
        // The Pixel's curve: the glass rolls away at both sides (light catches the roll).
        if (curve > 0f) {
            val roll = 14.dp.toPx()
            val lit = Color.White.copy(alpha = 0.16f * curve)
            drawRect(Brush.horizontalGradient(listOf(lit, Color.Transparent), sx, sx + roll), Offset(sx, sy + corner * 0.6f), Size(roll, size.height))
            drawRect(Brush.horizontalGradient(listOf(Color.Transparent, lit), sx + sw - roll, sx + sw), Offset(sx + sw - roll, sy + corner * 0.6f), Size(roll, size.height))
        }
        // The stretched Dynamic Island, centred on the camera, edge dp from each side.
        val pillH = 30f * scale
        val pillY = sy + 10f * scale
        val pillL = sx + edge * scale
        val pillW = sw - 2 * edge * scale
        drawRoundRect(Color.Black, Offset(pillL, pillY), Size(pillW, pillH), CornerRadius(pillH / 2))
        drawRoundRect(Color.White.copy(alpha = if (dark) 0.14f else 0.10f), Offset(pillL, pillY), Size(pillW, pillH), CornerRadius(pillH / 2), style = Stroke(1.dp.toPx()))
        // The camera, then a cover on the left and sound bars on the right, as on the real thing.
        val cx = sx + sw / 2; val cy = pillY + pillH / 2
        drawCircle(Color(0xFF1A1A22), pillH * 0.30f, Offset(cx, cy))
        drawCircle(Color(0xFF3A3A55), pillH * 0.12f, Offset(cx - pillH * 0.06f, cy - pillH * 0.06f))
        val cover = pillH * 0.62f
        drawRoundRect(Brush.linearGradient(listOf(Color(0xFFB8B8C8), Color(0xFF6C6C80))), Offset(pillL + (pillH - cover) / 2, cy - cover / 2), Size(cover, cover), CornerRadius(cover / 2))
        val barW = 2.5.dp.toPx()
        listOf(0.45f, 0.8f, 0.6f, 0.95f).forEachIndexed { i, h ->
            val bh = pillH * 0.5f * h
            val bx = pillL + pillW - pillH * 0.5f - (3 - i) * (barW * 1.9f)
            drawRoundRect(Color.White.copy(alpha = 0.85f), Offset(bx, cy - bh / 2), Size(barW, bh), CornerRadius(barW / 2))
        }
        // The distance kept from each side: a dashed guide and its number.
        val dash = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))
        val gy = pillY + pillH + 18.dp.toPx()
        for (side in listOf(-1f, 1f)) {
            val outer = if (side < 0) sx else sx + sw
            val inner = if (side < 0) pillL else pillL + pillW
            drawLine(Color.White.copy(alpha = 0.55f), Offset(inner, pillY + pillH + 4.dp.toPx()), Offset(inner, gy + 4.dp.toPx()), 1.dp.toPx(), pathEffect = dash)
            drawLine(Color.White.copy(alpha = 0.55f), Offset(outer, gy), Offset(inner, gy), 1.dp.toPx())
        }
        val words = measurer.measure("${edge.toInt()} dp from each side" + if (curve > 0.5f) " · off the curve" else "", label.copy(color = Color.White.copy(alpha = 0.75f)))
        drawText(words, topLeft = Offset(cx - words.size.width / 2f, gy + 8.dp.toPx()))
}

private fun specLine(phone: PhoneProfile.Phone): String = when (phone) {
    PhoneProfile.Phone.GalaxyS26FE -> "6.7-inch flat FHD+ screen · 120 Hz adaptive · Exynos 2500 · 4,900 mAh · One UI 9"
    PhoneProfile.Phone.Pixel6Pro -> "6.7-inch curved QHD+ LTPO screen · 10 to 120 Hz · Google Tensor · 5,003 mAh"
    PhoneProfile.Phone.Other -> "Not one of the two phones pro is tuned for: general settings, which suit most phones."
}

/** What pro does differently on [phone], in plain words. Every claim here is in DECISIONS 49. */
internal fun notes(phone: PhoneProfile.Phone): List<Pair<String, String>> = when (phone) {
    PhoneProfile.Phone.GalaxyS26FE -> listOf(
        "Island to the edges" to "The screen is flat, so the stretched Dynamic Island and the pop-ups come to 10 dp from the sides: room for longer song names.",
        "120 Hz when it moves" to "pro asks for 120 Hz while you touch it and for 2.5 seconds after, then hands back to One UI's adaptive rate so a still page costs less.",
        "Kept awake" to "Samsung puts apps it thinks are unused to sleep, which stops the island and the AirPods link. Below: Android's battery setting and Samsung's Never sleeping apps (both are needed).",
        "Saves when warm" to "Lighter glass, calmer sound bars and heart spot checks from Android's \"moderate\" heat level, the same as before.",
    )
    PhoneProfile.Phone.Pixel6Pro -> listOf(
        "Off the curved edges" to "The screen curves into its sides, so the stretched Dynamic Island and the pop-ups stay 18 dp in, where nothing looks bent or gets touched by your palm.",
        "LTPO screen, 10 to 120 Hz" to "pro asks for 120 Hz while you touch it and for 2.5 seconds after; when the page is still, the phone can drop as low as 10 Hz.",
        "Tensor runs warm" to "pro starts saving (lighter glass, calmer sound bars, heart spot checks) at Android's first heat level, \"light\", instead of waiting for \"moderate\".",
        "Kept awake" to "Set pro's battery use to Unrestricted below, so the island and heart readings carry on with the screen off.",
    )
    PhoneProfile.Phone.Other -> listOf(
        "Island to the edges" to "The stretched Dynamic Island comes to 10 dp from the sides.",
        "120 Hz when it moves" to "pro asks for the screen's fastest rate while you touch it and for 2.5 seconds after, then hands back to the phone.",
        "Saves when warm" to "Lighter glass and calmer animations from Android's \"moderate\" heat level.",
    )
}

private fun keptAwake(context: Context): Boolean = CompanionLink.isIgnoringBatteryOptimizations(context)

private fun openKeepAwake(context: Context, samsung: Boolean) {
    val tries = buildList {
        if (samsung) add(CompanionLink.samsungBackgroundLimitsIntent())
        add(CompanionLink.batteryOptimizationIntent(context))
        add(CompanionLink.appDetailsIntent(context))
    }
    for (i in tries) if (open(context, i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))) return
}
