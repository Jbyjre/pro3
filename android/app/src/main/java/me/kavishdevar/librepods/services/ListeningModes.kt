package me.kavishdevar.librepods.services

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
