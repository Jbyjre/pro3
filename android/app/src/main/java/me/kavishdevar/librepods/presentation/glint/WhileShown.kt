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
package me.kavishdevar.librepods.presentation.glint

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay

/**
 * Runs [block] only while pro is on screen, and again each time it comes back.
 *
 * A plain `LaunchedEffect { while (true) { ...; delay(1_000) } }` keeps going after you leave
 * pro (Android keeps the page in memory), so a page that checks Android's settings every second
 * kept checking in the background for hours. Found 2026-10-15 on the Phone tab, the Island tab,
 * the Sounds section, the Heart page and head tracking. Use this for any repeating check on a page.
 */
@Composable
fun WhileShown(vararg keys: Any?, block: suspend CoroutineScope.() -> Unit) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle, *keys) { lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED, block) }
}

/**
 * The wall clock, refreshed every [everyMs] while the page is on screen (and at once when it
 * comes back). For "2 min ago" style words; nothing ticks in the background.
 */
@Composable
fun rememberNow(everyMs: Long): MutableLongState {
    val now = remember { mutableLongStateOf(System.currentTimeMillis()) }
    WhileShown(everyMs) {
        while (true) {
            now.longValue = System.currentTimeMillis()
            delay(everyMs)
        }
    }
    return now
}
