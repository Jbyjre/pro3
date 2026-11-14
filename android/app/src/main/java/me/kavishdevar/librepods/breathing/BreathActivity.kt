package me.kavishdevar.librepods.breathing

import android.app.Activity
import android.app.KeyguardManager
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.WindowManager
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.activity.ComponentActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.kavishdevar.librepods.presentation.overlays.GlintOverlays

/** A launcher destination owned by pro. The target is started ONLY after the first-frame deadline. */
class BreathActivity : ComponentActivity() {
    private val main = Handler(Looper.getMainLooper())
    private var session = BreathSession()
    private var target: Intent? = null
    private var ready = false
    private var pkg: String? = null
    private val cancelInteraction: () -> Unit = { cancelPending(); finish() }
    private var requestGeneration = 0
    private var launched = false
    private val finishPause = object : Runnable {
        override fun run() {
            if (!ready || session.cancelled || launched) return
            val left = session.remaining(SystemClock.elapsedRealtime())
            if (left > 0) { main.postDelayed(this, left); return }
            if (session.complete(SystemClock.elapsedRealtime())) openTarget()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        window.setWindowAnimations(0)
        if (savedInstanceState != null) { finish(); return } // process death never starts an app later
        begin(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getStringExtra(EXTRA_PACKAGE) == pkg && !session.cancelled) return
        cancelPending()
        setIntent(intent)
        begin(intent)
    }

    private fun begin(request: Intent) {
        val generation = ++requestGeneration
        pkg = request.getStringExtra(EXTRA_PACKAGE)
        val chosenPackage = pkg
        session = BreathSession()
        ready = false
        target = null
        launched = false
        val view = BreathView(this).apply {
            destination = request.getStringExtra(EXTRA_LABEL).orEmpty()
            onFirstFrame = {
                session.firstFrame(SystemClock.elapsedRealtime())
                main.postDelayed(finishPause, session.remaining(SystemClock.elapsedRealtime()))
            }
        }
        setContentView(view)
        BreathInteraction.cancel = cancelInteraction
        GlintOverlays.dismissAll()
        lifecycleScope.launch {
            // Package queries and preference loading happen while the first frame is being drawn.
            val result = withContext(Dispatchers.IO) {
                val chosen = chosenPackage
                if (chosen == null || chosen == packageName || chosen !in BreathPrefs.selected(this@BreathActivity)) null
                else packageManager.getLaunchIntentForPackage(chosen)
            }
            if (isFinishing || session.cancelled || generation != requestGeneration) return@launch
            if (result == null) {
                Toast.makeText(this@BreathActivity, "This app is no longer selected or installed.", Toast.LENGTH_SHORT).show()
                finish()
                return@launch
            }
            target = result // Launcher flags restore the existing task; no CLEAR_TASK / CLEAR_TOP.
            ready = true
            if (!BreathPrefs.enabled(this@BreathActivity) || BreathPrefs.paused(this@BreathActivity)) openTarget()
            else finishPause.run()
        }
    }

    private fun openTarget() {
        if (launched || isFinishing || getSystemService(KeyguardManager::class.java).isKeyguardLocked) { finish(); return }
        launched = true
        runCatching {
            startActivity(target, android.app.ActivityOptions.makeCustomAnimation(this, 0, 0).toBundle())
        }.onFailure { Toast.makeText(this, "Couldn't open this app. Try its original icon.", Toast.LENGTH_SHORT).show() }
        finish()
    }

    private fun cancelPending() {
        session.cancel()
        main.removeCallbacksAndMessages(null)
        if (BreathInteraction.cancel === cancelInteraction) BreathInteraction.cancel = null
    }
    override fun onPause() { cancelPending(); if (!isFinishing) finish(); super.onPause() }
    override fun onDestroy() { cancelPending(); super.onDestroy() }
    companion object {
        const val EXTRA_PACKAGE = "breath_package"
        const val EXTRA_LABEL = "breath_label"
    }
}
