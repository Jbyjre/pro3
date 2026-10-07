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

import android.content.SharedPreferences
import kotlin.math.abs

/**
 * What each touch on the Dynamic Island does (Settings > Islands > Gestures), and the pure
 * logic that turns a finger's path into one of those gestures. No Android here, so all of it
 * is unit-tested.
 */
object IslandGestures {
    enum class Gesture(val key: String, val label: String) {
        Tap1("glint_di_g_tap1", "Tap"),
        Tap2("glint_di_g_tap2", "Double tap"),
        Tap3("glint_di_g_tap3", "Triple tap"),
        Hold("glint_di_g_hold", "Hold"),
        SwipeLeft("glint_di_g_left", "Swipe left"),
        SwipeRight("glint_di_g_right", "Swipe right"),
        PullDown("glint_di_g_down", "Pull down"),
    }

    enum class Action(val key: String, val label: String) {
        Expand("expand", "Open the island"),
        PlayPause("play", "Play / pause"),
        Next("next", "Next song"),
        Previous("previous", "Previous song"),
        OpenApp("app", "Open pro"),
        ListeningMode("mode", "Next listening mode"),
        Notifications("shade", "Notifications"),
        Nothing("none", "Nothing"),
    }

    /** The behaviour before customisation existed, kept as the defaults. */
    val defaults: Map<Gesture, Action> = mapOf(
        Gesture.Tap1 to Action.Expand,
        Gesture.Tap2 to Action.PlayPause,
        Gesture.Tap3 to Action.Next,
        Gesture.Hold to Action.OpenApp,
        Gesture.SwipeLeft to Action.Next,
        Gesture.SwipeRight to Action.Previous,
        Gesture.PullDown to Action.Expand,
    )

    fun action(prefs: SharedPreferences, g: Gesture): Action =
        fromKey(prefs.getString(g.key, null)) ?: defaults.getValue(g)

    fun all(prefs: SharedPreferences): Map<Gesture, Action> = Gesture.entries.associateWith { action(prefs, it) }

    fun fromKey(key: String?): Action? = Action.entries.firstOrNull { it.key == key }

    fun set(prefs: SharedPreferences, g: Gesture, a: Action) {
        prefs.edit().putString(g.key, a.key).apply()
    }

    fun reset(prefs: SharedPreferences) {
        prefs.edit().apply { Gesture.entries.forEach { remove(it.key) } }.apply()
    }

    /** The tap count for a tap gesture (1..3). */
    fun taps(n: Int): Gesture = when (n) {
        1 -> Gesture.Tap1
        2 -> Gesture.Tap2
        else -> Gesture.Tap3
    }

    /**
     * The most taps that do something: once this many have landed there's nothing to wait for,
     * so the action fires at once (with double and triple tap set to Nothing, a single tap
     * responds instantly instead of after the double-tap wait).
     */
    fun maxUsefulTaps(actions: Map<Gesture, Action>): Int = when {
        actions[Gesture.Tap3] != Action.Nothing -> 3
        actions[Gesture.Tap2] != Action.Nothing -> 2
        else -> 1
    }

    /**
     * Counts taps that land close together. Each [tap] says how many have landed and whether
     * that's final (nothing more to wait for); otherwise the caller waits [gapMs] and calls
     * [expire]. Rapid tapping can never queue up more than the useful number: the count starts
     * again after a final tap.
     */
    class TapCounter(val gapMs: Long = TAP_GAP_MS) {
        private var count = 0
        private var lastAt = Long.MIN_VALUE

        data class Result(val count: Int, val final: Boolean)

        fun tap(now: Long, maxUseful: Int): Result {
            if (count > 0 && now - lastAt > gapMs) count = 0
            count++
            lastAt = now
            val n = count
            val final = n >= maxUseful.coerceIn(1, 3)
            if (final) count = 0
            return Result(n, final)
        }

        /** The wait after the last tap is over: how many taps there were (0 if already handled). */
        fun expire(): Int {
            val n = count
            count = 0
            return n
        }

        val pending: Int get() = count
    }

    /** What a finished touch was. */
    enum class Kind { Tap, Hold, SwipeLeft, SwipeRight, PullDown, SwipeUp, None }

    /**
     * A finger went down and came up [dx], [dy] pixels away after [ms] milliseconds. [slop] is
     * how far a finger may wander and still be a tap; [swipe] how far a swipe or pull must go.
     * Holds are decided while the finger is still down (see [isHold]); here a long, still touch
     * that already counted as a hold returns None.
     */
    fun classify(dx: Float, dy: Float, ms: Long, slop: Float, swipe: Float, heldAlready: Boolean = false): Kind {
        val ax = abs(dx)
        val ay = abs(dy)
        if (ax <= slop && ay <= slop) return if (heldAlready) Kind.None else if (ms >= HOLD_MS) Kind.Hold else Kind.Tap
        return when {
            ax >= ay && ax >= swipe -> if (dx < 0) Kind.SwipeLeft else Kind.SwipeRight
            dy >= swipe && ay > ax -> Kind.PullDown
            // Back toward the camera: puts away whatever the pill has opened out (a song's name, a message).
            -dy >= swipe && ay > ax -> Kind.SwipeUp
            else -> Kind.None
        }
    }

    /** Still down, not moved beyond [slop], for at least [HOLD_MS]: a hold (fires while held). */
    fun isHold(dx: Float, dy: Float, ms: Long, slop: Float): Boolean = abs(dx) <= slop && abs(dy) <= slop && ms >= HOLD_MS

    /** Waits this long after a tap for another one. */
    const val TAP_GAP_MS = 300L
    /** A still touch this long is a hold. */
    const val HOLD_MS = 450L
}

/** The four listening modes in the order a tap cycles them (as the Quick Settings tile does). */
object ListeningModes {
    /** Mode numbers as the AirPods report them: 1 off, 2 noise cancellation, 3 transparency, 4 adaptive. */
    fun cycle(offAllowed: Boolean): List<Int> = buildList {
        if (offAllowed) add(1)
        add(3); add(4); add(2)
    }

    fun next(current: Int, offAllowed: Boolean): Int {
        val modes = cycle(offAllowed)
        val i = modes.indexOf(current)
        return modes[(i + 1).mod(modes.size)]
    }
}
