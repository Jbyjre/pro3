package me.kavishdevar.librepods.presentation.screens

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.luminance
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import me.kavishdevar.librepods.presentation.glint.GlintComfort

/** Quiet neutral surfaces let the saved breathing color carry the visual identity. */
internal data class BreatheColors(val background: Color, val surface: Color, val ink: Color, val secondary: Color, val line: Color)

@Composable
internal fun breatheColors(): BreatheColors = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) {
    BreatheColors(Color(0xFF111418), Color(0xFF1D2228), Color(0xFFF5F3ED), Color(0xFFB2B8BE), Color(0xFF353B42))
} else {
    BreatheColors(Color(0xFFF5F3EE), Color(0xFFFFFFFF), Color(0xFF20252A), Color(0xFF62696F), Color(0xFFE1E2DE))
}

/** Observe comfort changes instead of leaving a remembered animation setting stale. */
@Composable
internal fun rememberBreatheReducedMotion(): Boolean {
    val context = LocalContext.current
    var reduced by remember { mutableStateOf(GlintComfort.reduceMotion(context)) }
    DisposableEffect(context) {
        val prefs = context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == GlintComfort.PREF_REDUCE_MOTION) reduced = GlintComfort.reduceMotion(context)
        }
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { reduced = GlintComfort.reduceMotion(context) }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        context.contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
            context.contentResolver.unregisterContentObserver(observer)
        }
    }
    return reduced
}
