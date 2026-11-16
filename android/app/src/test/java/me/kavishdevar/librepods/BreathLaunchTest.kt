package me.kavishdevar.librepods

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.ViewGroup
import me.kavishdevar.librepods.breathing.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.time.Duration

/** Integration of the actual activity, first draw, Android handler and launcher intent. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class BreathLaunchTest {
    private val c: Context get() = RuntimeEnvironment.getApplication()
    private val pkg = "test.ordinary.chess"
    @Before fun setup() {
        BreathPrefs.prefs(c).edit().clear().commit()
        BreathPrefs.select(c, pkg, true)
        val app = ApplicationInfo().apply { packageName = pkg; enabled = true }
        val activity = ActivityInfo().apply { packageName = pkg; name = "$pkg.Main"; applicationInfo = app; exported = true; enabled = true }
        val info = PackageInfo().apply { packageName = pkg; applicationInfo = app; activities = arrayOf(activity) }
        shadowOf(c.packageManager).installPackage(info)
        shadowOf(c.packageManager).addResolveInfoForIntent(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(pkg), ResolveInfo().apply { activityInfo = activity })
    }
    private fun request() = Intent(c, BreathActivity::class.java).putExtra(BreathActivity.EXTRA_PACKAGE, pkg)
    private fun resolve() { repeat(40) { Thread.sleep(5); ShadowLooper.idleMainLooper() } }
    private fun draw(a: BreathActivity) {
        val view = a.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
        view.layout(0,0,412,915)
        view.draw(Canvas(Bitmap.createBitmap(412,915,Bitmap.Config.ARGB_8888)))
    }
    @Test fun targetCannotStartBeforeBreathingDeadline() {
        val controller = Robolectric.buildActivity(BreathActivity::class.java, request()).create().start().resume()
        val a = controller.get()
        resolve()
        assertNull(shadowOf(a).nextStartedActivity)
        draw(a)
        ShadowLooper.idleMainLooper(2499, java.util.concurrent.TimeUnit.MILLISECONDS)
        assertNull(shadowOf(a).nextStartedActivity)
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.MILLISECONDS)
        val opened = shadowOf(a).nextStartedActivity
        assertEquals(pkg, opened.component!!.packageName)
        assertEquals(0, opened.flags and (Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
        ShadowLooper.idleMainLooper(2, java.util.concurrent.TimeUnit.SECONDS)
        assertNull(shadowOf(a).nextStartedActivity)
        controller.pause().stop().destroy()
    }
    @Test fun savedCustomizationReachesTheRealLaunchWithoutChangingItsDeadline() {
        val chosen=BreathOptions(BreathPalette.Coral,still=true)
        BreathPrefs.setOptions(c,chosen)
        val controller=Robolectric.buildActivity(BreathActivity::class.java,request()).create().start().resume()
        val a=controller.get();resolve()
        val view=a.findViewById<ViewGroup>(android.R.id.content).getChildAt(0) as BreathView
        assertEquals(chosen,view.options)
        assertTrue(view.motionReduced)
        draw(a)
        val darkBars=android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
            android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
        assertEquals("the warm still layer needs dark Android icons",darkBars,a.window.insetsController!!.systemBarsAppearance and darkBars)
        ShadowLooper.idleMainLooper(2499,java.util.concurrent.TimeUnit.MILLISECONDS)
        assertNull(shadowOf(a).nextStartedActivity)
        ShadowLooper.idleMainLooper(1,java.util.concurrent.TimeUnit.MILLISECONDS)
        assertEquals(pkg,shadowOf(a).nextStartedActivity.component!!.packageName)
        controller.pause().stop().destroy()
    }
    @Test fun homeOrLockCancellationDoesNotLaunchLater() {
        val controller = Robolectric.buildActivity(BreathActivity::class.java, request()).create().start().resume()
        val a = controller.get(); resolve(); draw(a)
        controller.pause()
        ShadowLooper.idleMainLooper(5, java.util.concurrent.TimeUnit.SECONDS)
        assertNull(shadowOf(a).nextStartedActivity)
        assertFalse(BreathInteraction.active)
        controller.stop().destroy()
    }
    @Test fun oldDisabledAndPausedPreferencesCannotBypassAndUnselectedOnesDoNotOpen() {
        BreathPrefs.prefs(c).edit().putBoolean("enabled", false).putLong("paused_until", Long.MAX_VALUE).commit()
        val controller = Robolectric.buildActivity(BreathActivity::class.java, request()).create().start().resume()
        resolve()
        val a = controller.get()
        draw(a)
        ShadowLooper.idleMainLooper(2499, java.util.concurrent.TimeUnit.MILLISECONDS)
        assertNull(shadowOf(a).nextStartedActivity)
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.MILLISECONDS)
        assertEquals(pkg, shadowOf(a).nextStartedActivity.component!!.packageName)
        controller.pause().stop().destroy()
        BreathPrefs.select(c, pkg, false)
        val rejected = Robolectric.buildActivity(BreathActivity::class.java, request()).create().start().resume()
        resolve(); draw(rejected.get()); ShadowLooper.idleMainLooper(2, java.util.concurrent.TimeUnit.SECONDS)
        assertNull(shadowOf(rejected.get()).nextStartedActivity)
        rejected.pause().stop().destroy()
    }
    @Test fun duplicateShortcutDoesNotRestartDeadline() {
        val controller = Robolectric.buildActivity(BreathActivity::class.java, request()).create().start().resume()
        val a = controller.get(); resolve(); draw(a)
        ShadowLooper.idleMainLooper(600, java.util.concurrent.TimeUnit.MILLISECONDS)
        controller.newIntent(request())
        ShadowLooper.idleMainLooper(1900, java.util.concurrent.TimeUnit.MILLISECONDS)
        assertEquals(pkg, shadowOf(a).nextStartedActivity.component!!.packageName)
        controller.pause().stop().destroy()
    }
    @Test fun lockOrProcessRestoreCannotLaunchAnAppLater() {
        val controller = Robolectric.buildActivity(BreathActivity::class.java, request()).create().start().resume()
        val a = controller.get(); resolve(); draw(a)
        shadowOf(c.getSystemService(android.app.KeyguardManager::class.java)).setKeyguardLocked(true)
        ShadowLooper.idleMainLooper(2500, java.util.concurrent.TimeUnit.MILLISECONDS)
        assertNull(shadowOf(a).nextStartedActivity)
        controller.pause().stop().destroy()
        shadowOf(c.getSystemService(android.app.KeyguardManager::class.java)).setKeyguardLocked(false)
        val restored = Robolectric.buildActivity(BreathActivity::class.java, request()).create(android.os.Bundle())
        assertTrue(restored.get().isFinishing)
        assertNull(shadowOf(restored.get()).nextStartedActivity)
        restored.destroy()
    }

    @Test fun oldActivityDestructionCannotReleaseNewPauseOwnership() {
        val old = Robolectric.buildActivity(BreathActivity::class.java, request()).create().start().resume()
        old.pause().stop()
        val next = Robolectric.buildActivity(BreathActivity::class.java, request()).create().start().resume()
        assertTrue(BreathInteraction.active)
        old.destroy()
        assertTrue(BreathInteraction.active)
        next.pause().stop().destroy()
        assertFalse(BreathInteraction.active)
    }

    @Test fun systemAnimationSpeedCannotMultiplyTheIntervention() {
        for (scale in listOf(0f,10f)) {
            android.provider.Settings.Global.putFloat(c.contentResolver,android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,scale)
            val controller=Robolectric.buildActivity(BreathActivity::class.java,request()).create().start().resume()
            val a=controller.get();resolve();draw(a)
            ShadowLooper.idleMainLooper(2499,java.util.concurrent.TimeUnit.MILLISECONDS)
            assertNull(shadowOf(a).nextStartedActivity)
            ShadowLooper.idleMainLooper(1,java.util.concurrent.TimeUnit.MILLISECONDS)
            assertEquals(pkg,shadowOf(a).nextStartedActivity.component!!.packageName)
            controller.pause().stop().destroy()
        }
        android.provider.Settings.Global.putFloat(c.contentResolver,android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,1f)
    }

}
