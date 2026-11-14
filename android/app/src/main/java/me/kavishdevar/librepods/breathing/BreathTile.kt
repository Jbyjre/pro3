package me.kavishdevar.librepods.breathing
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
/** A user-added switch with no resident work. */
class BreathTile : TileService() {
    override fun onStartListening() { update() }
    override fun onClick() {
        if (isLocked) { unlockAndRun { toggle() }; return }
        toggle()
    }
    private fun toggle() {
        BreathPrefs.prefs(this).edit().putBoolean("enabled", !BreathPrefs.enabled(this)).apply()
        update()
    }
    private fun update() {
        qsTile?.apply { state = if (BreathPrefs.enabled(this@BreathTile)) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE; updateTile() }
    }
}
