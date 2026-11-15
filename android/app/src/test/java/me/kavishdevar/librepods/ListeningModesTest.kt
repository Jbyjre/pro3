package me.kavishdevar.librepods
import me.kavishdevar.librepods.services.ListeningModes
import org.junit.Test
import org.junit.Assert.assertEquals
class ListeningModesTest {
    @Test fun listeningModesCycleLikeTheTile() {
        assertEquals(listOf(1, 3, 4, 2), ListeningModes.cycle(offAllowed = true))
        assertEquals(3, ListeningModes.next(1, true))
        assertEquals(1, ListeningModes.next(2, true))
        assertEquals(3, ListeningModes.next(2, false))
        assertEquals(1, ListeningModes.next(0, true)) // unknown: start of the cycle
    }

}
