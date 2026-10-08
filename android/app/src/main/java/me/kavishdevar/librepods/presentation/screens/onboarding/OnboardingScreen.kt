/*
    Glint - AirPods on Android
    Copyright (C) 2025 LibrePods contributors, 2026 Glint contributors

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

package me.kavishdevar.librepods.presentation.screens.onboarding

import me.kavishdevar.librepods.presentation.glint.GlintLight
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import me.kavishdevar.librepods.R
import me.kavishdevar.librepods.presentation.components.StyledButton
import me.kavishdevar.librepods.presentation.glint.GlintComfort
import me.kavishdevar.librepods.presentation.glint.glassShadow
import me.kavishdevar.librepods.presentation.theme.LibrePodsTheme
import me.kavishdevar.librepods.presentation.theme.glintFontFamily
import me.kavishdevar.librepods.utils.XposedState
import me.kavishdevar.librepods.utils.bypassDeviceCheck
import me.kavishdevar.librepods.utils.isSupported
import me.kavishdevar.librepods.utils.removeDeviceCheckBypass

private const val STEP_WELCOME = 0
private const val STEP_PHONE = 1
private const val STEP_PERMISSIONS = 2
private const val STEP_STAY_CONNECTED = 3
private const val STEP_COUNT = 4

/**
 * First-run set-up, in Liquid Glass. The AirPods sit on a soft studio backdrop; every step is
 * shown on one glass sheet that really refracts that backdrop (Kyant's backdrop lens: blur,
 * vibrancy and edge refraction on the GPU), with a rim highlight and a two-layer shadow.
 * Steps: welcome, "can this phone connect", permissions, stay connected. No agreement step.
 */
@Composable
fun OnboardingScreen(
    /** Step to open on; the screenshot tests use it to show each step. */
    startStep: Int = STEP_WELCOME,
    onOnboardingComplete: () -> Unit,
) {
    val context = LocalContext.current
    val sharedPreferences = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    val isSupported = isSupported(sharedPreferences) || XposedState.bluetoothScopeEnabled
    var step by rememberSaveable { mutableIntStateOf(startStep) }

    BackHandler(enabled = step > STEP_WELCOME) {
        if (step == STEP_PERMISSIONS && !isSupported) removeDeviceCheckBypass(sharedPreferences)
        step--
    }

    LibrePodsTheme(m3eEnabled = sharedPreferences.getBoolean("m3e_enabled", false)) {
        val dark = isSystemInDarkTheme()
        val reduceTransparency = remember { GlintComfort.reduceTransparency(context) }
        val reduceMotion = remember { GlintComfort.reduceMotion(context) }
        val backdrop = rememberLayerBackdrop()
        val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

        BoxWithConstraints(Modifier.fillMaxSize().background(if (dark) Color(0xFF0B0B0D) else Color(0xFFEDEEF2))) {
            val screenH = maxHeight
            val sheetH by animateDpAsState(
                if (step == STEP_WELCOME) 300.dp else (screenH * 0.79f).coerceAtMost(760.dp),
                if (reduceMotion) tween(150) else spring(0.86f, 380f),
                label = "sheet"
            )
            // Where the sheet's top edge sits; the AirPods rest just behind it so the glass edge
            // visibly bends them.
            val sheetTop = screenH - bottom - 12.dp - sheetH

            StudioBackdrop(dark = dark, sheetTop = sheetTop, welcome = step == STEP_WELCOME, reduceMotion = reduceMotion, backdrop = backdrop)

            Column(
                Modifier.fillMaxSize().padding(top = top + 12.dp, bottom = bottom + 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                StepDots(step = step, dark = dark)
                Spacer(Modifier.weight(1f))
                GlassSheet(
                    backdrop = backdrop,
                    dark = dark,
                    reduceTransparency = reduceTransparency,
                    modifier = Modifier
                        .padding(horizontal = 10.dp)
                        .fillMaxWidth()
                        .height(sheetH)
                ) {
                    AnimatedContent(
                        targetState = step,
                        transitionSpec = {
                            if (reduceMotion) fadeIn(tween(150)) togetherWith fadeOut(tween(150))
                            else {
                                val dir = if (targetState > initialState) 1 else -1
                                (slideInHorizontally(spring(0.9f, 420f)) { it / 5 * dir } + fadeIn(tween(220))) togetherWith
                                    (slideOutHorizontally(spring(0.9f, 420f)) { -it / 5 * dir } + fadeOut(tween(160)))
                            }
                        },
                        label = "step"
                    ) { s ->
                        when (s) {
                            STEP_WELCOME -> WelcomeStep(backdrop) { step = STEP_PHONE }
                            STEP_PHONE -> StepFrame("This phone") {
                                NotSupportedPage(onContinue = { bypass ->
                                    if (bypass && !isSupported) bypassDeviceCheck(sharedPreferences)
                                    step = STEP_PERMISSIONS
                                })
                            }
                            STEP_PERMISSIONS -> StepFrame("Permissions") {
                                PermissionsPage(
                                    onBackward = {
                                        if (!isSupported) removeDeviceCheckBypass(sharedPreferences)
                                        step = STEP_PHONE
                                    },
                                    onForward = { step = STEP_STAY_CONNECTED }
                                )
                            }
                            else -> StepFrame("Stay connected") {
                                StayConnectedPage(onFinish = onOnboardingComplete)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Product-photo sweep (light or graphite) with the AirPods, drifting up as the sheet grows. */
@Composable
private fun StudioBackdrop(dark: Boolean, sheetTop: Dp, welcome: Boolean, reduceMotion: Boolean, backdrop: LayerBackdrop) {
    val buds = ImageBitmap.imageResource(R.drawable.airpods_pro_2_buds)
    val case = ImageBitmap.imageResource(R.drawable.airpods_pro_2_case)
    // Larger hero on the welcome step, smaller once the sheet grows for the other steps.
    val productScale by animateFloatAsState(
        if (welcome) 1.0f else 0.9f,
        if (reduceMotion) tween(150) else spring(0.86f, 300f),
        label = "product"
    )
    val productH = 170.dp
    // The product's lower 40dp tucks under the glass.
    val productTop = (sheetTop - productH + 40.dp).coerceAtLeast(24.dp)
    Box(
        Modifier
            .fillMaxSize()
            .layerBackdrop(backdrop)
            .drawBehind {
                // A soft vertical sweep, like a seamless studio backdrop, and one overhead key light.
                drawRect(
                    Brush.verticalGradient(
                        if (dark) listOf(Color(0xFF26272B), Color(0xFF121214), Color(0xFF08080A))
                        else listOf(Color(0xFFFFFFFF), Color(0xFFF0F1F4), Color(0xFFDCDEE4))
                    )
                )
                drawCircle(
                    Brush.radialGradient(
                        listOf(Color.White.copy(alpha = if (dark) 0.10f else 0.75f), Color.Transparent),
                        center = Offset(size.width / 2f, size.height * 0.22f),
                        radius = size.width * 0.9f
                    ),
                    radius = size.width * 0.9f,
                    center = Offset(size.width / 2f, size.height * 0.22f)
                )
                // The status light's green, very faint, low in the frame: something for the
                // glass edge to pick up without being a decorative blob.
                drawCircle(
                    Brush.radialGradient(
                        listOf(Color(0xFF30D158).copy(alpha = if (dark) 0.16f else 0.10f), Color.Transparent),
                        center = Offset(size.width * 0.5f, size.height * 0.62f),
                        radius = size.width * 0.55f
                    ),
                    radius = size.width * 0.55f,
                    center = Offset(size.width * 0.5f, size.height * 0.62f)
                )
            },
        contentAlignment = Alignment.TopCenter
    ) {
        Row(
            Modifier
                .offset(y = productTop)
                .height(productH)
                .graphicsLayer {
                    scaleX = productScale; scaleY = productScale
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.8f)
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // The buds picture has empty space above and below the buds; scale it up so the
            // buds and case read at a similar size.
            Image(buds, contentDescription = null, modifier = Modifier.fillMaxHeight().width(200.dp).graphicsLayer { scaleX = 1.25f; scaleY = 1.25f })
            Image(case, contentDescription = null, modifier = Modifier.fillMaxHeight().width(150.dp))
        }
    }
}

/**
 * The shared glass surface: backdrop blur + vibrancy + GPU edge refraction (lens), a tint so
 * text stays legible, a rim highlight, and a two-layer elevation shadow. With "reduce
 * transparency" it becomes a solid sheet with no blur or refraction.
 */
@Composable
private fun GlassSheet(
    backdrop: LayerBackdrop,
    dark: Boolean,
    reduceTransparency: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(40.dp)
    val tint = when {
        reduceTransparency -> if (dark) Color(0xFF1C1C1E) else Color(0xFFF7F7F9)
        dark -> Color(0xFF1C1C1E).copy(alpha = 0.55f)
        else -> Color.White.copy(alpha = 0.55f)
    }
    Box(
        modifier
            .glassShadow(shape, dark)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    if (!reduceTransparency) {
                        vibrancy()
                        blur(22f.dp.toPx())
                        lens(
                            refractionHeight = 22f.dp.toPx(),
                            refractionAmount = 40f.dp.toPx(),
                            depthEffect = true,
                            chromaticAberration = true
                        )
                    }
                },
                highlight = { GlintLight.rim(if (reduceTransparency) 0f else if (dark) 0.6f else 0.9f) },
                onDrawSurface = { drawRect(tint) }
            )
    ) {
        // Inside the glass, cards become frosted rather than solid so it reads as one surface.
        val base = MaterialTheme.colorScheme
        MaterialTheme(
            colorScheme = base.onGlass(dark, reduceTransparency),
            typography = MaterialTheme.typography,
        ) {
            content()
        }
    }
}

private fun ColorScheme.onGlass(dark: Boolean, solid: Boolean): ColorScheme = copy(
    surfaceContainer = Color.Transparent,
    surface = if (solid) surface else if (dark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.62f),
)

@Composable
private fun StepFrame(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Text(
            title,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 22.dp).semantics { heading() },
            style = TextStyle(
                fontFamily = glintFontFamily, fontWeight = FontWeight.SemiBold,
                fontSize = 28.sp, letterSpacing = (-0.4).sp, color = MaterialTheme.colorScheme.onSurface
            )
        )
        Box(Modifier.weight(1f)) { content() }
    }
}

@Composable
private fun WelcomeStep(backdrop: LayerBackdrop, onStart: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "pro",
            modifier = Modifier.semantics { heading() },
            style = TextStyle(
                fontFamily = glintFontFamily, fontWeight = FontWeight.Bold,
                fontSize = 44.sp, letterSpacing = (-1.2).sp, color = MaterialTheme.colorScheme.onSurface
            )
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "A Dynamic Island around your camera, and your AirPods or Beats fully at home on this phone. Music, messages, timers and battery, all in one place.",
            textAlign = TextAlign.Center,
            style = TextStyle(
                fontFamily = glintFontFamily, fontSize = 15.sp, lineHeight = 21.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
        Spacer(Modifier.weight(1f))
        StyledButton(
            onClick = onStart,
            backdrop = backdrop,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            surfaceColor = MaterialTheme.colorScheme.primary,
            maxScale = 0.05f,
        ) {
            Text(
                "Get started",
                style = TextStyle(
                    fontFamily = glintFontFamily, fontWeight = FontWeight.SemiBold,
                    fontSize = 17.sp, color = Color.White
                )
            )
        }
    }
}

@Composable
private fun StepDots(step: Int, dark: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        for (i in 0 until STEP_COUNT) {
            val width by animateDpAsState(if (i == step) 20.dp else 7.dp, spring(0.8f, 500f), label = "dot")
            Box(
                Modifier
                    .size(width = width, height = 7.dp)
                    .background(
                        if (i == step) (if (dark) Color.White else Color(0xFF1C1C1E))
                        else (if (dark) Color.White.copy(alpha = 0.28f) else Color.Black.copy(alpha = 0.18f)),
                        CircleShape
                    )
            )
        }
    }
}
