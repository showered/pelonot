package com.pelonot.data.sensor

/**
 * Converts flywheel tick timestamps into a stable cadence in RPM.
 *
 * Two problems with deriving RPM from a single tick interval, both of which
 * the previous implementation had:
 *
 *  - **Jitter.** `60000 / delta` from one interval swings by several RPM
 *    between consecutive revolutions. Averaging a short window of intervals
 *    smooths that without adding noticeable lag.
 *  - **Cadence never falls to zero.** When the rider stops, ticks simply stop
 *    arriving, so the last computed value persisted forever. The HUD showed a
 *    stationary bike spinning at 90 RPM, and every downstream metric —
 *    power, distance, energy — kept accruing.
 *
 * Pure Kotlin so it can be unit-tested against a synthetic clock.
 */
class CadenceTracker(
    private val windowSize: Int = DEFAULT_WINDOW_SIZE,
    private val stopTimeoutMs: Long = DEFAULT_STOP_TIMEOUT_MS
) {

    private val intervalsMs = ArrayDeque<Long>()
    private var lastTickMs: Long? = null

    /** Records a flywheel revolution and returns the cadence at [nowMs]. */
    fun onTick(nowMs: Long): Double {
        val previous = lastTickMs
        lastTickMs = nowMs

        if (previous != null) {
            val delta = nowMs - previous
            // A delta at or below zero means a clock jump; a very long one
            // means the rider restarted after a pause. Neither is a usable
            // interval, so drop the history and start measuring afresh.
            if (delta in 1..stopTimeoutMs) {
                intervalsMs.addLast(delta)
                while (intervalsMs.size > windowSize) intervalsMs.removeFirst()
            } else {
                intervalsMs.clear()
            }
        }

        return cadenceAt(nowMs)
    }

    /**
     * Cadence at [nowMs], decaying to zero once no tick has arrived for
     * [stopTimeoutMs]. Call this on a timer as well as on ticks, otherwise a
     * stopped rider never reads as stopped.
     */
    fun cadenceAt(nowMs: Long): Double {
        val last = lastTickMs ?: return 0.0
        if (nowMs - last > stopTimeoutMs) return 0.0
        if (intervalsMs.isEmpty()) return 0.0

        val meanIntervalMs = intervalsMs.average()
        if (meanIntervalMs <= 0.0) return 0.0

        val rpm = MS_PER_MINUTE / meanIntervalMs
        return rpm.coerceIn(0.0, MAX_PLAUSIBLE_RPM)
    }

    fun reset() {
        intervalsMs.clear()
        lastTickMs = null
    }

    private companion object {
        const val DEFAULT_WINDOW_SIZE = 4
        const val DEFAULT_STOP_TIMEOUT_MS = 2_000L
        const val MS_PER_MINUTE = 60_000.0

        /** Above this, the reading is electrical noise rather than a rider. */
        const val MAX_PLAUSIBLE_RPM = 250.0
    }
}
