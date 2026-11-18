package me.kavishdevar.librepods.updates

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.PowerManager
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.*
import me.kavishdevar.librepods.breathing.BreathInteraction

/** Android schedules this sparse job; no resident updater service, timer or wake lock. */
class UpdateJob : JobService() {
    private var work: Job? = null
    override fun onStartJob(params: JobParameters): Boolean {
        val network = params.network ?: return false
        val connectivity = getSystemService(ConnectivityManager::class.java)
        val open = { url: String ->
            check(connectivity.getNetworkCapabilities(network)
                ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == true) {
                "Automatic updates will resume on an unmetered connection."
            }
            network.openConnection(URL(url)) as HttpURLConnection
        }
        work = CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                if (Updater.check(this@UpdateJob, open).isFailure) return@launch
                if (Updater.state.value.available && !Updater.state.value.ready && Updater.prefs(this@UpdateJob).getBoolean("download", true)) Updater.download(this@UpdateJob, open)
                val audio = getSystemService(AudioManager::class.java)
                if (Updater.state.value.ready && Updater.prefs(this@UpdateJob).getBoolean("install", false) &&
                    !getSystemService(PowerManager::class.java).isInteractive && !BreathInteraction.active &&
                    !audio.isMusicActive && audio.mode == AudioManager.MODE_NORMAL) UpdateInstaller.install(this@UpdateJob, automatic = true)
            } finally { if (isActive) jobFinished(params, false) }
        }
        return true
    }
    override fun onStopJob(params: JobParameters): Boolean { work?.cancel(); return true }
    companion object {
        fun schedule(c: Context) {
            val scheduler = c.getSystemService(JobScheduler::class.java)
            if (!Updater.prefs(c).getBoolean("checks", true)) { scheduler.cancel(7181); return }
            val desired = JobInfo.Builder(7181, ComponentName(c, UpdateJob::class.java))
                .setPeriodic(12 * 60 * 60_000L).setPersisted(true)
                .setRequiresDeviceIdle(true).setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED).setRequiresBatteryNotLow(true).build()
            // A job already waiting keeps its old rules forever, so it is replaced once whenever SPEC changes.
            val prefs = Updater.prefs(c)
            if (scheduler.getPendingJob(7181) != null && prefs.getInt("job_spec", 0) == SPEC) return
            scheduler.schedule(desired)
            prefs.edit().putInt("job_spec", SPEC).apply()
        }
        /** Raise this whenever the job's rules above change. */
        private const val SPEC = 2
    }
}
