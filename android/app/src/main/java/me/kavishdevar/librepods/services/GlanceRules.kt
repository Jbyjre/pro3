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
 * What's worth a glance, in order of importance: on the small pill (the "Smart glance" spot
 * beside the camera) and on the opened island (its "live" rows). Pure, so it's unit-tested.
 *
 * The order: a timer that's ringing, a running timer, the phone charging, your headphones (while
 * connected), a low phone battery, then the next alarm (opened island only, within a day).
 */
object GlanceRules {
    /** At or below this the phone's battery is worth a glance even when not charging. */
    const val LOW_PHONE = 20
    /** The next alarm shows on the opened island when it's this close. */
    const val ALARM_AHEAD_MS = 24 * 60 * 60 * 1000L

    sealed interface Item {
        data class Timer(val state: IslandTimer.State) : Item
        data class Charging(val level: Int) : Item
        /** Headphones or AirPods connected: [level] is the buds' (or headphones') battery. */
        data class Buds(val level: Int?, val headphones: Boolean) : Item
        data class LowPhone(val level: Int) : Item
        /** The next alarm rings at [at] (wall-clock ms). */
        data class Alarm(val at: Long) : Item
        /** Nothing else to say: the phone's battery, so the opened island is never empty. */
        data class Battery(val level: Int, val charging: Boolean) : Item
    }

    /** Everything live right now, most important first (the opened island shows the first two). */
    fun live(
        timer: IslandTimer.State?,
        phone: PhoneStatus.Info,
        budsUp: Boolean,
        budsLevel: Int?,
        headphones: Boolean,
        nextAlarm: Long?,
        wallNow: Long,
    ): List<Item> = buildList {
        if (timer != null) add(Item.Timer(timer))
        if (phone.known && phone.charging) add(Item.Charging(phone.level))
        if (budsUp) add(Item.Buds(budsLevel, headphones))
        if (phone.known && !phone.charging && phone.level <= LOW_PHONE) add(Item.LowPhone(phone.level))
        if (nextAlarm != null && nextAlarm > wallNow && nextAlarm - wallNow <= ALARM_AHEAD_MS) add(Item.Alarm(nextAlarm))
        if (isEmpty() && phone.known) add(Item.Battery(phone.level, phone.charging))
    }

    /** The one thing for the small "Smart glance" spot beside the camera, or null for nothing. */
    fun pill(timer: IslandTimer.State?, phone: PhoneStatus.Info, budsUp: Boolean, budsLevel: Int?, headphones: Boolean): Item? =
        live(timer, phone, budsUp, budsLevel, headphones, nextAlarm = null, wallNow = 0L)
            .firstOrNull { it !is Item.Alarm && it !is Item.Battery }

    /** The rows the opened island shows: the two most important, with the phone's battery when there's nothing. */
    fun rows(items: List<Item>): List<Item> = items.take(2)
}
