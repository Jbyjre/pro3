package me.kavishdevar.librepods.breathing

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object BreathAccess {
    internal val connection = MutableStateFlow(false)
    val connected = connection.asStateFlow()
    internal val gate = BreathAppGate()

    fun enabled(context: Context): Boolean {
        val component = ComponentName(context, BreathAccessService::class.java)
        return context.getSystemService(AccessibilityManager::class.java)
            .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { ComponentName(it.resolveInfo.serviceInfo.packageName, it.resolveInfo.serviceInfo.name) == component }
    }

    fun settings(context: Context): Intent = Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS")
        .putExtra(Intent.EXTRA_COMPONENT_NAME, ComponentName(context, BreathAccessService::class.java).flattenToString())
}
