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
package me.kavishdevar.librepods.services

/**
 * When the mini island (the small pill around the front camera) is on screen, and how big it
 * is. Pure functions, so they're unit-tested without a phone.
 */
object MiniIslandRules {
    /** After music pauses, the pill stays this long (so you can tap it to resume), then tucks away. */
    const val PAUSED_LINGER_MS = 30_000L
    /** How long it stays wide showing a new song's name. */
    const val NAME_SHOW_MS = 3_200L

    data class Inputs(
        val enabled: Boolean,
        val canDraw: Boolean,
        val playing: Boolean,
        /** Milliseconds since playback last stopped (0 while playing). */
        val pausedForMs: Long,
        /** True once anything has played since the phone was last unlocked or the app started. */
        val playedRecently: Boolean,
        val airPodsOnly: Boolean,
        val airPodsUp: Boolean,
        val screenUnlocked: Boolean,
        /** Stay up the whole time the AirPods are connected, music or not. */
        val alwaysWithAirPods: Boolean = false,
    )

    /** Whether the pill should exist at all. */
    fun wanted(i: Inputs): Boolean {
        if (!i.enabled || !i.canDraw || !i.screenUnlocked) return false
        if (i.airPodsOnly && !i.airPodsUp) return false
        if (i.playing) return true
        if (i.alwaysWithAirPods && i.airPodsUp) return true
        return i.playedRecently && i.pausedForMs < PAUSED_LINGER_MS
    }

    /** What the pill shows: the music, or the AirPods themselves (battery, mode, heart). */
    enum class Content { Music, AirPods }

    /**
     * Music while something plays, and for a while after it pauses (so you can see what's
     * paused); otherwise the AirPods. Without AirPods connected it can only be music.
     */
    fun content(playing: Boolean, pausedForMs: Long, playedRecently: Boolean, airPodsUp: Boolean): Content = when {
        playing -> Content.Music
        playedRecently && pausedForMs < PAUSED_LINGER_MS -> Content.Music
        airPodsUp -> Content.AirPods
        else -> Content.Music
    }

    /**
     * A different song is playing (so the pill may show its name). Needs real song names
     * (Notification access) on both sides; the same title by a different artist counts.
     */
    fun songChanged(oldTitle: String?, oldArtist: String?, newTitle: String?, newArtist: String?): Boolean =
        newTitle != null && (newTitle != oldTitle || newArtist != oldArtist)

    /**
     * Pill sizes in pixels around a camera hole of [holeW] x [holeH] (0 when the phone has
     * none): tall enough to ring the camera with a margin, and wide enough for the cover on
     * one side and the sound bars on the other without touching it.
     */
    data class Size(val height: Float, val compactWidth: Float, val wideWidth: Float, val wideHeight: Float)

    fun size(holeW: Float, holeH: Float, density: Float, screenW: Float): Size {
        val h = (holeH + 10f * density).coerceIn(28f * density, 40f * density)
        val side = h - 8f * density // the cover is a circle this wide
        val compact = maxOf(holeW, h) + 2f * (side + 7f * density)
        val fit = screenW - 16f * density
        val c = compact.coerceAtMost(fit)
        val wide = minOf(screenW - 24f * density, 280f * density).coerceIn(c, fit)
        return Size(h, c, wide, h + 26f * density)
    }

    /** A rectangle in screen pixels (Android's Rect, without needing Android in tests). */
    data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val width get() = right - left
        val height get() = bottom - top
        val centerX get() = (left + right) / 2f
        val centerY get() = (top + bottom) / 2f
    }

    /**
     * Which camera cutout to wrap, from all the cutouts the phone reports. Works for every
     * shape of phone:
     * - a punch-hole or a notch near the middle of the top edge: wrap it;
     * - a camera in a corner (or off to one side): don't wrap it, since a pill centred on it
     *   would run off the screen; the pill sits in the middle of the status bar instead;
     * - no cutout, or one on a side or the bottom (landscape, tablets): null, same as above;
     * - very wide cutouts (more than 60% of the screen) are treated as no cutout.
     */
    fun pickCamera(cutouts: List<Box>, screenW: Int, screenH: Int): Box? =
        cutouts
            .filter { it.top < screenH / 4 && it.width > 0 && it.height > 0 && it.width <= screenW * 0.6f }
            .filter { kotlin.math.abs(it.centerX - screenW / 2f) <= screenW * 0.12f }
            .minByOrNull { kotlin.math.abs(it.centerX - screenW / 2f) }

    /**
     * Where the pill's middle goes, measured from the top of the screen: on the camera, or in
     * the middle of the status bar, but never so high that the pill's top would be cut off.
     */
    fun centerY(camera: Box?, statusBarH: Int, pillH: Float, density: Float): Float {
        val wanted = camera?.centerY ?: (if (statusBarH > 0) statusBarH / 2f else 12f * density)
        return wanted.coerceAtLeast(pillH / 2f + 1f * density)
    }
}
