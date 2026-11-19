package me.kavishdevar.librepods.breathing

import android.content.Context
import android.net.Uri
import android.provider.Settings

/**
 * Detects a grayscale screen. Android's own colour filter "Grayscale" (and "Monochromacy") is the secure
 * setting pair `accessibility_display_daltonizer_enabled = 1` with `accessibility_display_daltonizer = 0`
 * (value 0 is monochromacy, drawn as grayscale; read from AOSP-derived code and the Settings sources on
 * 19 November 2026). A grayscale mode a phone maker implements some other way, such as a wellbeing
 * schedule, may not show up here, which is why [GrayMode.On] exists as a manual choice.
 */
object BreathGray {
    private const val ENABLED = "accessibility_display_daltonizer_enabled"
    private const val MODE = "accessibility_display_daltonizer"

    /** Reads two small secure settings. Cheap, but call it off the main thread anyway. */
    fun detected(context: Context): Boolean = runCatching {
        val resolver = context.contentResolver
        Settings.Secure.getInt(resolver, ENABLED, 0) == 1 && Settings.Secure.getInt(resolver, MODE, -1) == 0
    }.getOrDefault(false)

    fun active(context: Context, mode: GrayMode): Boolean = when (mode) {
        GrayMode.On -> true
        GrayMode.Off -> false
        GrayMode.Auto -> detected(context)
    }

    /** What to watch so the layer reacts when grayscale is switched on or off. */
    val watched: List<Uri> get() = listOf(Settings.Secure.getUriFor(ENABLED), Settings.Secure.getUriFor(MODE))
}
