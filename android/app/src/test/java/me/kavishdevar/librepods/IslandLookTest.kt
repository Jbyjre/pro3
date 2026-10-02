package me.kavishdevar.librepods

import android.app.Application
import android.content.Context
import me.kavishdevar.librepods.services.IslandGestures
import me.kavishdevar.librepods.services.IslandLook
import me.kavishdevar.librepods.services.IslandLook.Situation
import me.kavishdevar.librepods.services.IslandLook.Slot
import me.kavishdevar.librepods.services.MiniIslandRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The Dynamic Island's customisation: defaults, storage, what each situation shows, sizes. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class IslandLookTest {
    private val prefs get() = RuntimeEnvironment.getApplication().getSharedPreferences("settings", Context.MODE_PRIVATE)

    @Test fun defaultsAreTheOriginalLook() {
        val look = IslandLook.read(prefs)
        assertTrue(look.isDefault)
        assertEquals(Slot.Cover to Slot.Bars, look.slots(Situation.Music))
        assertEquals(Slot.Cover to Slot.Bars, look.slots(Situation.Paused))
        assertEquals(Slot.Battery to Slot.Heart, look.slots(Situation.Idle))
        // Talking keeps whatever was on the left and shows the dots on the right.
        assertEquals(Slot.Cover to Slot.Talk, look.slots(Situation.Talking, Situation.Music))
        assertEquals(Slot.Battery to Slot.Talk, look.slots(Situation.Talking, Situation.Idle))
    }

    @Test fun defaultSizeIsUnchanged() {
        val d = 3f
        val old = 26 * d + 2 * ((26 * d + 10 * d).coerceIn(28 * d, 40 * d) - 8 * d + 7 * d) // the formula before customisation
        val s = MiniIslandRules.size(26 * d, 26 * d, d, 412 * d)
        assertEquals(36 * d, s.height, 0.01f)
        assertEquals(36 * d, s.center, 0.01f) // hole is narrower than the pill is tall
        assertEquals(36 * d + 2 * (36 * d - 8 * d + 7 * d), s.compactWidth, 0.01f)
        assertTrue(old <= s.compactWidth + 0.01f)
    }

    @Test fun choicesAreSavedAndReadBack() {
        IslandLook.setSlot(prefs, Situation.Music, true, Slot.Title)
        IslandLook.setSlot(prefs, Situation.Idle, false, Slot.Buds)
        IslandLook.setSize(prefs, IslandLook.Size.Large)
        IslandLook.setWidth(prefs, IslandLook.Width.Roomy)
        IslandLook.setGlow(prefs, IslandLook.Glow.Off)
        val look = IslandLook.read(prefs)
        assertEquals(Slot.Title to Slot.Bars, look.slots(Situation.Music))
        assertEquals(Slot.Battery to Slot.Buds, look.slots(Situation.Idle))
        assertEquals(IslandLook.Size.Large, look.size)
        assertEquals(IslandLook.Width.Roomy, look.width)
        assertEquals(IslandLook.Glow.Off, look.glow)
        assertFalse(look.isDefault)
        IslandLook.reset(prefs)
        assertTrue(IslandLook.read(prefs).isDefault)
    }

    @Test fun keepIsOnlyForTalkingAndNeverLoops() {
        assertTrue(Slot.Same in IslandLook.options(Situation.Talking))
        assertFalse(Slot.Same in IslandLook.options(Situation.Music))
        // A stored "Keep" outside talking (from a bad write) falls back to the default.
        prefs.edit().putString("glint_di_slot_music_l", "same").apply()
        assertEquals(Slot.Cover, IslandLook.read(prefs).slots(Situation.Music).first)
        // Keep that points at Keep resolves to nothing rather than looping.
        val look = IslandLook.Look(slots = IslandLook.DEFAULT_SLOTS + (Situation.Talking to (Slot.Same to Slot.Same)))
        assertEquals(Slot.Cover to Slot.Bars, look.slots(Situation.Talking, Situation.Music))
    }

    @Test fun situations() {
        assertEquals(Situation.Talking, IslandLook.situation(music = true, playing = true, talking = true, charging = false))
        assertEquals(Situation.Music, IslandLook.situation(music = true, playing = true, talking = false, charging = true))
        assertEquals(Situation.Paused, IslandLook.situation(music = true, playing = false, talking = false, charging = false))
        assertEquals(Situation.Charging, IslandLook.situation(music = false, playing = false, talking = false, charging = true))
        assertEquals(Situation.Idle, IslandLook.situation(music = false, playing = false, talking = false, charging = false))
        assertEquals(Situation.Idle, IslandLook.underneath(music = false, playing = false, charging = false))
        assertEquals(Situation.Rest, IslandLook.situation(music = false, playing = false, talking = false, charging = false, rest = true))
        assertEquals(Situation.Talking, IslandLook.situation(music = false, playing = false, talking = true, charging = false, rest = true))
    }

    @Test fun widerChoicesMakeARoomierPillThatStillFits() {
        val d = 3f
        val screen = 412 * d
        val base = MiniIslandRules.size(26 * d, 26 * d, d, screen)
        val titled = MiniIslandRules.size(26 * d, 26 * d, d, screen,
            IslandLook.Look(slots = IslandLook.DEFAULT_SLOTS + (Situation.Music to (Slot.Title to Slot.Buds)), size = IslandLook.Size.Large, width = IslandLook.Width.Roomy))
        assertTrue(titled.compactWidth > base.compactWidth)
        assertTrue(titled.compactWidth <= screen - 16 * d)
        assertTrue(titled.compactFor(Situation.Music) > titled.compactFor(Situation.Idle))
        // Nothing either side: a ring just around the camera.
        val bare = MiniIslandRules.size(26 * d, 26 * d, d, screen,
            IslandLook.Look(slots = IslandLook.Situation.entries.associateWith { Slot.Nothing to Slot.Nothing }))
        assertEquals(bare.center + 2 * bare.inset, bare.compactWidth, 0.01f)
        // Small and large stay around the camera hole.
        val small = MiniIslandRules.size(26 * d, 26 * d, d, screen, IslandLook.Look(size = IslandLook.Size.Small))
        assertTrue(small.height >= 26 * d + 4 * d)
    }

    @Test fun gestureChoicesAreSavedToo() {
        IslandGestures.set(prefs, IslandGestures.Gesture.Tap1, IslandGestures.Action.PlayPause)
        assertEquals(IslandGestures.Action.PlayPause, IslandGestures.action(prefs, IslandGestures.Gesture.Tap1))
        IslandGestures.reset(prefs)
        assertEquals(IslandGestures.defaults, IslandGestures.all(prefs))
    }
}
