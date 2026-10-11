package me.kavishdevar.librepods

import android.app.Notification
import me.kavishdevar.librepods.services.LiveActivities
import me.kavishdevar.librepods.services.LiveActivities.Kind
import me.kavishdevar.librepods.services.LiveRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 2026-10-11: what apps are doing live, in their own words (Jake: "if I have Google Maps going"). */
class LiveActivitiesTest {
    private val own = "io.github.jbyjre.glint"
    private val ongoing = Notification.FLAG_ONGOING_EVENT

    private fun kind(pkg: String, flags: Int = ongoing, category: String? = null, template: String? = null, progress: Boolean = false, chrono: Boolean = false, promoted: Boolean = false) =
        LiveRules.kind(pkg, own, flags, category, template, progress, chrono, promoted)

    @Test fun onlyOngoingThingsAreLive() {
        assertEquals(Kind.Navigation, kind("com.google.android.apps.maps"))
        assertEquals(Kind.Navigation, kind("com.example.nav", category = Notification.CATEGORY_NAVIGATION))
        assertEquals(Kind.Ride, kind("com.ubercab"))
        assertEquals(Kind.Progress, kind("com.example.files", progress = true))
        assertEquals(Kind.Recording, kind("com.example.recorder", chrono = true))
        assertEquals(Kind.Call, kind("com.whatsapp", category = Notification.CATEGORY_CALL))
        // Android 16's Live Updates count even without "ongoing".
        assertEquals(Kind.Other, kind("com.example.app", flags = 0, promoted = true))
        // A plain notification, a message, the music player, pro itself: never.
        assertNull(kind("com.google.android.apps.maps", flags = 0))
        assertNull(kind("com.whatsapp", category = Notification.CATEGORY_MESSAGE))
        assertNull(kind("com.spotify.music", template = "android.app.Notification\$MediaStyle"))
        assertNull(kind(own))
        // "Running in the background" with nothing live in it: not one.
        assertNull(kind("com.example.vpn"))
    }

    @Test fun theOneLiveNumberComesFromTheAppsOwnWords() {
        assertEquals("200 m", LiveRules.short(Kind.Navigation, "Turn right onto Main St", "200 m · 12 min", null))
        assertEquals("1.2 km", LiveRules.short(Kind.Navigation, "In 1,2 km", null, null))
        assertEquals("12 min", LiveRules.short(Kind.Navigation, "Continue on A1", "12 min to go", null))
        assertEquals("4 min", LiveRules.short(Kind.Ride, "Your driver is 4 mins away", "Toyota Prius", null))
        assertEquals("45%", LiveRules.short(Kind.Progress, "Downloading", null, 0.45f))
        assertEquals("80%", LiveRules.short(Kind.Other, "Backing up 80%", null, null))
        // "5 min" is never read as five miles.
        assertEquals("5 min", LiveRules.short(Kind.Other, "5 min left", null, null))
        // Nothing short to say: the icon alone (balanced, never a long line squeezed in).
        assertNull(LiveRules.short(Kind.Navigation, "Head north", null, null))
    }

    @Test fun aRunningClockCountsUpOrDown() {
        assertEquals("4:05", LiveRules.clock(0L, 245_000L, countDown = false))
        assertEquals("1:02:05", LiveRules.clock(0L, 3_725_000L, countDown = false))
        assertEquals("0:30", LiveRules.clock(60_000L, 30_000L, countDown = true))
        assertEquals("0:00", LiveRules.clock(10_000L, 20_000L, countDown = true))
    }

    private fun live(pkg: String, kind: Kind, at: Long) = LiveActivities.Live(
        pkg = pkg, key = "$pkg:$at", kind = kind, title = null, text = null, short = null, progress = null,
        clockFrom = null, countDown = false, icon = null, actions = emptyList(), open = null, postedAt = at,
    )

    @Test fun theAppInFrontWinsThenNavigationThenTheNewest() {
        val maps = live("com.google.android.apps.maps", Kind.Navigation, 1L)
        val files = live("com.example.files", Kind.Progress, 5L)
        assertEquals(files, LiveRules.pick(listOf(maps, files), front = "com.example.files"))
        assertEquals(maps, LiveRules.pick(listOf(maps, files), front = "com.whatsapp"))
        assertEquals(files, LiveRules.pick(listOf(files), front = null))
        assertNull(LiveRules.pick(emptyList(), front = null))
    }
}
