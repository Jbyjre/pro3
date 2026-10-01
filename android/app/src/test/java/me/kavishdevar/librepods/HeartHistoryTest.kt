package me.kavishdevar.librepods

import android.app.Application
import android.content.Context
import me.kavishdevar.librepods.services.HeartHistory
import me.kavishdevar.librepods.services.HeartRate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class HeartHistoryTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Before fun clean() {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().clear().commit()
        java.io.File(context.filesDir, "heart").deleteRecursively()
        HeartHistory.resetCache()
    }

    private fun session(start: Long, minutes: Int, bpm: Int) = (0..minutes * 12).map { HeartRate.Sample(start + it * 5000L, bpm + it % 5) }

    @Test fun savesAllReadingsAndReplacesTheSameSession() {
        val a = session(1_000_000, 5, 70)
        HeartHistory.save(context, a.take(30))
        HeartHistory.save(context, a) // the same session, saved again later with more readings
        val list = HeartHistory.sessions(context)
        assertEquals(1, list.size)
        assertEquals(a.size, list[0].readings)
        assertEquals(a, HeartHistory.samples(context, 1_000_000))
        // Survives a restart (cache dropped, read back from the files).
        HeartHistory.resetCache()
        assertEquals(1, HeartHistory.sessions(context).size)
    }

    @Test fun keepsMoreThanTheOldFortyAndNewestFirst() {
        repeat(45) { HeartHistory.save(context, session(it * 10_000_000L, 2, 60 + it)) }
        val list = HeartHistory.sessions(context)
        assertEquals(45, list.size)
        assertTrue(list.zipWithNext().all { (a, b) -> a.startMs > b.startMs })
    }

    @Test fun movesOldSummariesInOnce() {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
            .putString("glint_hr_sessions", "5000,65000,72,60,90\n1000,61000,80,70,95").commit()
        val list = HeartHistory.sessions(context)
        assertEquals(listOf(5000L, 1000L), list.map { it.startMs })
        assertEquals(0, list[0].readings)
        assertFalse(context.getSharedPreferences("settings", Context.MODE_PRIVATE).contains("glint_hr_sessions"))
    }

    @Test fun restoreAddsMissingAndDeleteRemoves() {
        val a = session(2_000_000, 3, 66)
        val csv = me.kavishdevar.librepods.services.HeartInsights.compactCsv(a)
        assertTrue(HeartHistory.restore(context, 2_000_000, csv))
        assertFalse(HeartHistory.restore(context, 2_000_000, csv)) // already here
        assertEquals(a, HeartHistory.samples(context, 2_000_000))
        HeartHistory.delete(context, 2_000_000)
        assertTrue(HeartHistory.sessions(context).isEmpty())
        assertTrue(HeartHistory.samples(context, 2_000_000).isEmpty())
    }
}
