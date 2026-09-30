/*
    Glint, a fork of LibrePods - AirPods liberated from Apple's ecosystem
    Copyright (C) 2026 Glint contributors

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>.
*/

package me.kavishdevar.librepods.services

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Estimates how much listening time is left on the AirPods.
 *
 * Only the AirPods' own link (AACP) is used: it reports each bud in 1% steps. Bluetooth
 * adverts only give 10% steps, far too coarse to measure a drain rate, so they never feed
 * this estimator.
 *
 * How it works:
 * 1. Before there is enough data, it uses a starting rate: Apple's rated 8 hours per full
 *    charge for AirPods Pro 3 (with noise cancellation), or this pair's own learned rate
 *    from earlier sessions in the same listening mode once one exists.
 * 2. While a bud is being worn, it times each 1% drop. After [MIN_DROPS] drops it
 *    measures the real rate over the last [WINDOW_MS] and blends towards it, fully
 *    trusting it after [TRUST_MS] of measured drain.
 * 3. Each bud is measured on its own; the one that runs out first sets the answer.
 * 4. A mode change, taking the buds out or charging starts a fresh measurement.
 *    A finished measurement updates the learned rate for that listening mode.
 */
class BatteryEstimator(
    private val store: RateStore = RateStore.None,
) {
    /** Where learned drain rates (% per hour, per listening mode) are kept between sessions. */
    interface RateStore {
        fun load(mode: Int): Float?
        fun save(mode: Int, percentPerHour: Float)
        object None : RateStore {
            override fun load(mode: Int): Float? = null
            override fun save(mode: Int, percentPerHour: Float) = Unit
        }
    }

    data class Reading(
        val timeMs: Long,
        val left: Int?, val right: Int?, val case: Int?,
        val leftCharging: Boolean, val rightCharging: Boolean,
        val leftInEar: Boolean, val rightInEar: Boolean,
        val mode: Int,
    )

    private class BudRun(var level: Int, val drops: ArrayDeque<Long> = ArrayDeque(), var startMs: Long)
    private class ChargeRun(var level: Int, val rises: ArrayDeque<Long> = ArrayDeque())

    private val runs = arrayOfNulls<BudRun>(2)
    private val charge = arrayOfNulls<ChargeRun>(2)
    private var runMode = -1

    fun reset() {
        finishRuns()
        charge.fill(null)
    }

    fun update(r: Reading): BatteryEstimate? {
        val levels = arrayOf(r.left, r.right)
        val charging = booleanArrayOf(r.leftCharging, r.rightCharging)
        val worn = booleanArrayOf(r.leftInEar, r.rightInEar)
        if (levels.all { it == null }) { reset(); return null }

        if (r.mode != runMode) { finishRuns(); runMode = r.mode }

        for (i in 0..1) {
            val level = levels[i]
            if (level == null || charging[i] || !worn[i]) {
                finishRun(i)
            } else {
                val run = runs[i]
                if (run == null) runs[i] = BudRun(level, startMs = r.timeMs)
                else if (level < run.level) {
                    repeat(run.level - level) { run.drops.addLast(r.timeMs) }
                    run.level = level
                } else if (level > run.level) {
                    finishRun(i); runs[i] = BudRun(level, startMs = r.timeMs)
                }
            }
            if (level != null && charging[i]) {
                val c = charge[i]
                if (c == null || level < c.level) charge[i] = ChargeRun(level)
                else if (level > c.level) { repeat(level - c.level) { c.rises.addLast(r.timeMs) }; c.level = level }
            } else charge[i] = null
        }

        val anyCharging = (0..1).any { levels[it] != null && charging[it] }
        if (anyCharging && (0..1).none { levels[it] != null && !charging[it] && worn[it] }) {
            return chargingEstimate(levels, r.timeMs, r.case)
        }

        // The buds that matter: those being worn, or if none is worn, every bud out of the charger.
        val inUse = (0..1).filter { levels[it] != null && !charging[it] && worn[it] }
            .ifEmpty { (0..1).filter { levels[it] != null && !charging[it] } }
        if (inUse.isEmpty()) return null

        val prior = priorRate(r.mode)
        var bestMinutes = Double.MAX_VALUE
        var measured = 0.0
        for (i in inUse) {
            val level = levels[i]!!
            val run = runs[i]
            val (rate, weight) = measuredRate(run, r.timeMs)?.let { (m, w) -> (w * m + (1 - w) * prior) to w } ?: (prior to 0.0)
            val minutes = level / rate * 60.0
            if (minutes < bestMinutes) { bestMinutes = minutes; measured = weight }
        }
        return BatteryEstimate(
            minutesLeft = bestMinutes.roundToInt(),
            confidence = when {
                measured >= 1.0 -> Confidence.MEASURED
                measured > 0.0 -> Confidence.LEARNING
                else -> Confidence.RATED
            },
            worn = (0..1).any { levels[it] != null && worn[it] },
            charging = false,
            caseCharges = r.case?.let { caseCharges(it) },
        )
    }

    /** Measured %/hour and how far to trust it (0..1), or null before [MIN_DROPS] drops. */
    private fun measuredRate(run: BudRun?, now: Long): Pair<Double, Double>? {
        if (run == null) return null
        while (run.drops.size > MIN_DROPS && now - run.drops.first() > WINDOW_MS) run.drops.removeFirst()
        if (run.drops.size < MIN_DROPS) return null
        val first = run.drops.first()
        val last = run.drops.last()
        val n = run.drops.size - 1 // intervals between the timed drops
        if (last <= first) return null
        val interval = (last - first).toDouble() / n
        // If it has been longer than a usual interval since the last drop, the drain has slowed.
        val span = max((last - first).toDouble(), (now - first) - interval)
        val perHour = n / (span / HOUR_MS)
        val weight = ((last - first).toDouble() / TRUST_MS).coerceIn(0.0, 1.0)
        return perHour to weight
    }

    private fun chargingEstimate(levels: Array<Int?>, now: Long, case: Int?): BatteryEstimate {
        var worst: Int? = null
        for (i in 0..1) {
            val c = charge[i] ?: continue
            val level = levels[i] ?: continue
            if (level >= 100) continue
            while (c.rises.size > MIN_DROPS && now - c.rises.first() > CHARGE_WINDOW_MS) c.rises.removeFirst()
            if (c.rises.size < MIN_DROPS) { worst = null; break }
            val n = c.rises.size - 1
            val perMin = n / ((c.rises.last() - c.rises.first()).toDouble() / 60_000.0)
            if (perMin <= 0 || perMin.isInfinite()) { worst = null; break }
            val m = ((100 - level) / perMin).roundToInt()
            worst = max(worst ?: 0, m)
        }
        return BatteryEstimate(
            minutesLeft = null, minutesToFull = worst,
            confidence = if (worst != null) Confidence.MEASURED else Confidence.RATED,
            worn = false, charging = true,
            caseCharges = case?.let { caseCharges(it) },
        )
    }

    private fun priorRate(mode: Int): Double =
        store.load(mode)?.toDouble()?.takeIf { it in 5.0..60.0 } ?: (100.0 / RATED_HOURS)

    private fun finishRuns() { finishRun(0); finishRun(1) }

    private fun finishRun(i: Int) {
        val run = runs[i] ?: return
        runs[i] = null
        if (run.drops.size < LEARN_DROPS || runMode < 0) return
        val hours = (run.drops.last() - run.drops.first()).toDouble() / HOUR_MS
        if (hours <= 0) return
        val observed = ((run.drops.size - 1) / hours).toFloat()
        if (observed !in 5f..60f) return
        val old = store.load(runMode)
        store.save(runMode, if (old == null) observed else old + LEARN_ALPHA * (observed - old))
    }

    enum class Confidence { RATED, LEARNING, MEASURED }

    companion object {
        /** Apple: AirPods Pro 3, up to 8 hours of listening with noise cancellation (apple.com specs). */
        const val RATED_HOURS = 8.0
        /** Apple: up to 24 hours with the case, so the case holds about two more full charges. */
        const val CASE_FULL_CHARGES = 2.0
        const val MIN_DROPS = 3
        const val LEARN_DROPS = 8
        const val LEARN_ALPHA = 0.3f
        const val HOUR_MS = 3_600_000.0
        const val WINDOW_MS = 45 * 60_000L
        const val TRUST_MS = 30 * 60_000.0
        const val CHARGE_WINDOW_MS = 15 * 60_000L

        fun caseCharges(casePercent: Int): Double = casePercent / 100.0 * CASE_FULL_CHARGES
    }
}

data class BatteryEstimate(
    /** Listening minutes left on the bud that runs out first; null while charging. */
    val minutesLeft: Int?,
    val confidence: BatteryEstimator.Confidence,
    val worn: Boolean,
    val charging: Boolean,
    val caseCharges: Double?,
    val minutesToFull: Int? = null,
)

/** Plain-language wording for an estimate. Rounded so it never claims more precision than it has. */
object BatteryWords {
    fun duration(minutes: Int): String = when {
        minutes < 10 -> "under 10 min"
        minutes < 60 -> "${((minutes + 2) / 5) * 5} min"
        else -> {
            val rounded = ((minutes + 5) / 10) * 10
            val h = rounded / 60
            val m = rounded % 60
            if (m == 0) "$h h" else "$h h $m min"
        }
    }

    fun headline(e: BatteryEstimate): String = when {
        e.charging -> e.minutesToFull?.let { "Full in about ${duration(it)}" } ?: "Charging"
        e.minutesLeft == null -> "Estimating…"
        else -> "About ${duration(e.minutesLeft)} of listening left"
    }

    fun detail(e: BatteryEstimate): String {
        val basis = when {
            e.charging -> if (e.minutesToFull == null) "Timing the charge, a few minutes more" else "From how fast they are charging now"
            e.confidence == BatteryEstimator.Confidence.MEASURED -> "Measured from your last half hour of listening"
            e.confidence == BatteryEstimator.Confidence.LEARNING -> "Measuring your use; getting more exact"
            e.worn -> "Based on Apple's rating; measuring as you listen"
            else -> "If you start listening now, based on Apple's rating"
        }
        val case = e.caseCharges?.let { c ->
            val rounded = (c * 2).roundToInt() / 2.0
            when {
                rounded < 0.5 -> " · Case: less than half a charge left"
                else -> " · Case: about ${if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()} more full charge${if (rounded == 1.0) "" else "s"}"
            }
        } ?: ""
        return basis + case
    }
}

/** Shared, live estimate the screens read. Null when the AirPods' own link isn't connected. */
object BatteryTimeLeft {
    private val _estimate = MutableStateFlow<BatteryEstimate?>(null)
    val estimate: StateFlow<BatteryEstimate?> = _estimate
    fun publish(e: BatteryEstimate?) { _estimate.value = e }
}
