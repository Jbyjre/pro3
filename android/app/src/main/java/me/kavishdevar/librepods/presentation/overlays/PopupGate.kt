package me.kavishdevar.librepods.presentation.overlays

/** Bounded duplicate suppression; repeated Bluetooth reports don't keep a pop-up alive. */
internal class PopupGate {
    private val last = LinkedHashMap<IslandEvent, Long>()
    fun accept(event: IslandEvent, now: Long): Boolean {
        val before = last[event]
        if (before != null && now - before in 0 until 2_000L) return false
        last[event] = now
        if (last.size > 24) last.remove(last.keys.first())
        return true
    }
}
