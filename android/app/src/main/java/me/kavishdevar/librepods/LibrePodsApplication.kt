package me.kavishdevar.librepods

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import kotlinx.coroutines.launch
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import me.kavishdevar.librepods.billing.BillingManager
import me.kavishdevar.librepods.billing.BillingProviderFactory
import me.kavishdevar.librepods.utils.XposedServiceHolder
import me.kavishdevar.librepods.utils.XposedState

class LibrePodsApplication: Application(), XposedServiceHelper.OnServiceListener, DefaultLifecycleObserver {

    private val updateScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)

    override fun onCreate() {
        XposedServiceHelper.registerListener(this)
        BillingManager.provider = BillingProviderFactory.create(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)

        super<Application>.onCreate()
        // The battery plan follows the phone from the start (PowerPlan): no setting, always on.
        me.kavishdevar.librepods.services.PowerPlan.attach(this)

        // Android keeps a note of why pro last stopped (a freeze, a crash): save a readable copy
        // for Troubleshooting. Off the main thread: it asks Android and may read a trace.
        Thread({
            runCatching { me.kavishdevar.librepods.updates.UpdateJob.schedule(this) }
            runCatching { me.kavishdevar.librepods.services.FeatureMigration.retireCamera(this) }
            runCatching { me.kavishdevar.librepods.services.IslandTimer.attach(this) }
            runCatching { me.kavishdevar.librepods.services.FreezeReport.check(this) }
        }, "pro-startup-check").apply { isDaemon = true }.start()
    }

    override fun onResume(owner: LifecycleOwner) {
        // Opening pro is the moment a new build is most useful to hear about: one tiny, throttled check.
        updateScope.launch { runCatching { me.kavishdevar.librepods.updates.Updater.checkOnOpen(this@LibrePodsApplication) } }
        BillingManager.provider.queryPurchases()
        XposedState.isAvailable = XposedServiceHolder.service != null
        XposedState.bluetoothScopeEnabled = XposedServiceHolder.service?.scope?.contains("com.google.android.bluetooth") == true || XposedServiceHolder.service?.scope?.contains("com.android.bluetooth") == true
    }

    override fun onServiceBind(service: XposedService) {
        XposedServiceHolder.service = service
        XposedState.isAvailable = true
        XposedState.bluetoothScopeEnabled = XposedServiceHolder.service?.scope?.contains("com.google.android.bluetooth") == true || XposedServiceHolder.service?.scope?.contains("com.android.bluetooth") == true
    }

    override fun onServiceDied(p0: XposedService) {
        XposedServiceHolder.service = null
        XposedState.isAvailable = false
    }
}
