package me.kavishdevar.librepods

import me.kavishdevar.librepods.breathing.BreathAppGate
import org.junit.Assert.*
import org.junit.Test

class BreathAppGateTest {
    @Test fun pausesOnEntryAndReentryButNotInternalActivityTransitions() {
        val gate = BreathAppGate()
        val selected = setOf("chat", "chess")
        assertTrue(gate.enter("chat", selected, 0))
        assertFalse(gate.enter("chat", selected, 3000))
        assertFalse(gate.enter("home", selected, 3100))
        assertTrue(gate.enter("chat", selected, 3200))
        assertTrue(gate.enter("chess", selected, 3300))
    }
    @Test fun shortcutExemptionIsConsumedAndExpires() {
        val gate = BreathAppGate()
        val selected = setOf("chat")
        gate.shortcutCompleted("chat", 0)
        assertFalse(gate.enter("chat", selected, 10))
        gate.enter("home", selected, 20)
        assertTrue(gate.enter("chat", selected, 30))
        gate.reset()
        gate.shortcutCompleted("chat", 0)
        assertTrue(gate.enter("chat", selected, 5000))
    }
}
