package me.kavishdevar.librepods

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.app.Application
import android.content.Context
import me.kavishdevar.librepods.services.FreezeReport
import me.kavishdevar.librepods.services.MiniIslandRules
import me.kavishdevar.librepods.utils.OffMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.Executor

/** The protections against "pro isn't responding": work kept off the main thread, a limit on self-checks, and the freeze report. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class FreezeProofTest {
    // ---- OffMain ----

    private class Queue : Executor {
        val jobs = ArrayDeque<Runnable>()
        override fun execute(command: Runnable) { jobs.addLast(command) }
        fun runAll() { while (jobs.isNotEmpty()) jobs.removeFirst().run() }
    }

    @Test fun fromTheMainThreadWorkIsQueuedAndTheCallerIsNotMadeToWait() {
        val q = Queue()
        val off = OffMain(onMainThread = { true }, background = q)
        val log = mutableListOf<String>()
        val answer = off.run(whenQueued = true) { log += "wrote"; false }
        // The caller got its answer at once; nothing has happened yet.
        assertTrue(answer)
        assertTrue(log.isEmpty())
        q.runAll()
        assertEquals(listOf("wrote"), log)
    }

    @Test fun queuedWorkKeepsItsOrderAndAFailureDoesNotStopTheRest() {
        val q = Queue()
        val off = OffMain(onMainThread = { true }, background = q)
        val log = mutableListOf<Int>()
        off.run(true) { log += 1; true }
        off.run(true) { error("link dropped") }
        off.run(true) { log += 3; true }
        q.runAll()
        assertEquals(listOf(1, 3), log)
    }

    @Test fun fromAnyOtherThreadItJustRunsAndReturnsTheRealAnswer() {
        val q = Queue()
        val off = OffMain(onMainThread = { false }, background = q)
        assertFalse(off.run(whenQueued = true) { false })
        assertTrue(q.jobs.isEmpty())
    }

    @Test fun theDefaultKnowsTheMainThread() {
        // Robolectric's test thread is the main thread: the work must not run inline.
        val ran = java.util.concurrent.atomic.AtomicBoolean(false)
        val answer = OffMain().run(whenQueued = "queued") { ran.set(true); "real" }
        assertEquals("queued", answer)
    }

    // ---- RefreshGuard ----

    @Test fun aRunawayCheckIsLimitedAndRecoversWhenItStops() {
        val g = MiniIslandRules.RefreshGuard(maxPerWindow = 3, windowMs = 1_000)
        assertTrue(g.allow(0)); assertTrue(g.allow(0)); assertTrue(g.allow(0))
        assertFalse(g.allow(0)); assertFalse(g.allow(999))
        // A second after the oldest one, it lets one through again.
        assertTrue(g.allow(1_000))
        // The bursts keep being limited, however many ask.
        var allowed = 0
        for (i in 0 until 10_000) if (g.allow(1_000)) allowed++
        assertEquals(2, allowed)
        // Later, normal life: a few a second are never refused.
        assertTrue(g.allow(5_000)); assertTrue(g.allow(5_300)); assertTrue(g.allow(5_600))
    }

    @Test fun theDefaultAllowsEverythingNormalAndStopsAFlood() {
        val g = MiniIslandRules.RefreshGuard()
        // One a frame for a second (60) is far more than the island ever needs, and still limited to 30.
        var allowed = 0
        for (i in 0 until 60) if (g.allow(i * 16L)) allowed++
        assertEquals(30, allowed)
        // A single change every so often always goes through.
        assertTrue(MiniIslandRules.RefreshGuard().allow(123))
    }

    // ---- FreezeReport (pure parts) ----

    @Test fun onlyFreezesAndCrashesAreWorthARecord() {
        assertEquals("Stopped responding", FreezeReport.labelFor(ApplicationExitInfo.REASON_ANR))
        assertEquals("Crashed", FreezeReport.labelFor(ApplicationExitInfo.REASON_CRASH))
        assertEquals("Crashed (native code)", FreezeReport.labelFor(ApplicationExitInfo.REASON_CRASH_NATIVE))
        assertEquals("Failed to start", FreezeReport.labelFor(ApplicationExitInfo.REASON_INITIALIZATION_FAILURE))
        assertEquals("Used too much of the phone", FreezeReport.labelFor(ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE))
        // You swiping pro away, the phone restarting, an update, the system tidying up: not news.
        for (r in intArrayOf(
            ApplicationExitInfo.REASON_USER_REQUESTED, ApplicationExitInfo.REASON_USER_STOPPED, ApplicationExitInfo.REASON_PACKAGE_UPDATED,
            ApplicationExitInfo.REASON_EXIT_SELF, ApplicationExitInfo.REASON_LOW_MEMORY, ApplicationExitInfo.REASON_UNKNOWN,
        )) assertNull(FreezeReport.labelFor(r))
    }

    private val trace = """
        ----- pid 1234 at 2026-10-06 12:00:00 -----
        Cmd line: io.github.jbyjre.glint

        "Signal Catcher" daemon prio=10 tid=2 Runnable
          native: #00 pc 0000

        "main" prio=5 tid=1 Sleeping
          | group="main" sCount=1 ucsCount=0
          at java.lang.Thread.sleep(Native method)
          at me.kavishdevar.librepods.services.Something.block(Something.kt:42)
          at android.os.Handler.dispatchMessage(Handler.java:106)

        "binder:1234_1" prio=5 tid=8 Native
          native: #00 pc 0001
    """.trimIndent()

    @Test fun theMainThreadIsPickedOutOfALongTrace() {
        val main = FreezeReport.mainThread(trace)
        assertTrue(main.startsWith("\"main\" prio=5"))
        assertTrue(main.contains("Something.block(Something.kt:42)"))
        assertFalse(main.contains("binder:1234_1"))
        assertFalse(main.contains("Signal Catcher"))
        // No main thread in it: the top of the trace instead, capped.
        assertEquals(2, FreezeReport.mainThread("a\nb\nc\nd", maxLines = 2).lines().size)
        // The cap holds for a very long main thread.
        val long = "\"main\" prio=5 tid=1\n" + (1..500).joinToString("\n") { "  at frame$it" }
        assertEquals(60, FreezeReport.mainThread(long).lines().size)
    }

    @Test fun theSavedTextSaysWhenWhatWhereAndWhichPhone() {
        val text = FreezeReport.compose(
            at = 0L, label = "Stopped responding", description = "Input dispatching timed out", process = "io.github.jbyjre.glint",
            phone = "Google Pixel 6, Android 16 (API 36)", appVersion = "1.2.3", trace = trace,
        )
        assertTrue(text.startsWith("pro report"))
        for (part in listOf("When: ", "What: Stopped responding", "Android says: Input dispatching timed out", "Phone: Google Pixel 6, Android 16 (API 36)", "App: 1.2.3", "Main thread when it happened:", "Something.block")) {
            assertTrue("missing: $part", text.contains(part))
        }
        // Without a trace (a crash) it still reads fine.
        assertFalse(FreezeReport.compose(1L, "Crashed", null, null, "x", "1", null).contains("Main thread when it happened"))
    }

    // ---- FreezeReport (the Android side, with a pretend record of an earlier freeze) ----

    private fun addExit(reason: Int, whenMs: Long, description: String? = null, trace: String? = null) {
        val am = RuntimeEnvironment.getApplication().getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val b = org.robolectric.shadows.ShadowActivityManager.ApplicationExitInfoBuilder.newBuilder()
            .setProcessName("io.github.jbyjre.glint").setPid(1234).setReason(reason).setTimestamp(whenMs)
        description?.let { b.setDescription(it) }
        trace?.let { b.setTraceInputStream(it.byteInputStream()) }
        shadowOf(am).addApplicationExitInfo(b.build())
    }

    @Test fun aFreezeSinceTheLastLookIsSavedOnceAndNothingElseIs() {
        val app = RuntimeEnvironment.getApplication()
        FreezeReport.clear(app)
        app.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().clear().commit()
        // First look ever: older history is not news, nothing is saved or flagged.
        addExit(ApplicationExitInfo.REASON_ANR, 1_000L)
        FreezeReport.check(app)
        assertTrue(FreezeReport.reports(app).isEmpty())
        assertFalse(FreezeReport.hasUnread(app))
        // A new freeze, and a swipe-away that isn't worth a report.
        addExit(ApplicationExitInfo.REASON_ANR, 2_000L, description = "Input dispatching timed out", trace = trace)
        addExit(ApplicationExitInfo.REASON_USER_REQUESTED, 3_000L)
        FreezeReport.check(app)
        val saved = FreezeReport.reports(app)
        assertEquals(1, saved.size)
        assertEquals("Stopped responding", saved[0].label)
        assertTrue(saved[0].text.contains("What: Stopped responding"))
        assertTrue(saved[0].text.contains("Android says: Input dispatching timed out"))
        // The stuck line of code from Android's own trace made it into the report.
        assertTrue(saved[0].text.contains("Something.block(Something.kt:42)"))
        assertEquals(2_000L, saved[0].at)
        assertTrue(FreezeReport.hasUnread(app))
        // Looking again changes nothing; reading it clears the flag; clearing removes it.
        FreezeReport.check(app)
        assertEquals(1, FreezeReport.reports(app).size)
        FreezeReport.markRead(app)
        assertFalse(FreezeReport.hasUnread(app))
        FreezeReport.clear(app)
        assertTrue(FreezeReport.reports(app).isEmpty())
    }
}
