package me.kavishdevar.librepods.services

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

/** Small bounded application-art cache shared by music and the installed-app picker. IO callers only. */
object SoundSource {
    data class AppInfo(val label: String?, val icon: ImageBitmap?)
    private val cache = object : android.util.LruCache<String, AppInfo>(48) {}
    fun cachedInfo(pkg: String): AppInfo? = cache.get(pkg)
    fun appInfo(c: Context, pkg: String): AppInfo = cache.get(pkg) ?: runCatching {
        val app = c.packageManager.getApplicationInfo(pkg, 0)
        AppInfo(c.packageManager.getApplicationLabel(app).toString(), c.packageManager.getApplicationIcon(app).toBitmap(96,96).asImageBitmap())
    }.getOrDefault(AppInfo(null, null)).also { cache.put(pkg, it) }
    fun openApp(c: Context, pkg: String?) {
        if (pkg == null) return
        runCatching { c.packageManager.getLaunchIntentForPackage(pkg)?.let { c.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
    }
}
