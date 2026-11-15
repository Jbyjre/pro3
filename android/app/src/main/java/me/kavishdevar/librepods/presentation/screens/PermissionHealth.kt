package me.kavishdevar.librepods.presentation.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.kavishdevar.librepods.services.NowPlaying

/** Exact platform access, refreshed on resume rather than polled or assumed granted. */
data class PermissionHealth(val overlays: Boolean,val music: Boolean,val install: Boolean,val notifications: Boolean) {
    companion object {
        fun read(c: Context)=PermissionHealth(Settings.canDrawOverlays(c),NowPlaying.hasAccess(c),
            c.packageManager.canRequestPackageInstalls(),
            c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED)
    }
}

@Composable
fun rememberPermissionHealth(refreshKey: Int = 0): State<PermissionHealth?> {
    val c=LocalContext.current
    val owner=LocalLifecycleOwner.current
    val scope=rememberCoroutineScope()
    val state=remember { mutableStateOf<PermissionHealth?>(null) }
    DisposableEffect(owner,c,refreshKey) {
        var query: Job?=null
        val observer=object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                query?.cancel()
                query=scope.launch { state.value=withContext(Dispatchers.IO) { PermissionHealth.read(c) } }
            }
            override fun onPause(owner: LifecycleOwner) { query?.cancel() }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); query?.cancel() }
    }
    return state
}
