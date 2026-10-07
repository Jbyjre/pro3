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

package me.kavishdevar.librepods.utils

import android.os.Looper
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * Keeps blocking work (writing to a Bluetooth connection, which can stall when the link does)
 * off the main thread, so the app can never freeze waiting for it. Android shows "pro isn't
 * responding" when the main thread is stuck for about five seconds.
 *
 * From the main thread the work is queued on one background thread (so it still happens in the
 * order it was asked) and the caller gets [whenQueued] straight away. From any other thread it
 * just runs, and the caller gets the real answer. Pure apart from the two things passed in, so
 * it is unit-tested without a phone.
 */
class OffMain(
    private val onMainThread: () -> Boolean = { Looper.myLooper() === Looper.getMainLooper() },
    private val background: Executor = Executors.newSingleThreadExecutor { r -> Thread(r, "pro-offmain").apply { isDaemon = true } },
) {
    fun <T> run(whenQueued: T, task: () -> T): T =
        if (onMainThread()) {
            background.execute { runCatching { task() } }
            whenQueued
        } else task()
}
