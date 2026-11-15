package me.kavishdevar.librepods.breathing

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import androidx.core.graphics.drawable.toBitmap

object BreathShortcuts {
    /** Call on IO: both the real application label and icon come from this phone. */
    fun pin(context: Context, pkg: String): Boolean {
        val shortcuts = context.getSystemService(ShortcutManager::class.java)
        if (!shortcuts.isRequestPinShortcutSupported) return false
        val app = context.packageManager.getApplicationInfo(pkg, 0)
        val label = context.packageManager.getApplicationLabel(app).toString()
        val intent = Intent(context, BreathActivity::class.java).setAction(Intent.ACTION_MAIN)
            .putExtra(BreathActivity.EXTRA_PACKAGE, pkg).putExtra(BreathActivity.EXTRA_LABEL, label)
        val shortcut = ShortcutInfo.Builder(context, "breathe:$pkg")
            .setShortLabel(label).setLongLabel("Breathe · $label")
            .setIcon(Icon.createWithBitmap(context.packageManager.getApplicationIcon(app).toBitmap(144, 144)))
            .setIntent(intent).build()
        return shortcuts.requestPinShortcut(shortcut, null)
    }
}
