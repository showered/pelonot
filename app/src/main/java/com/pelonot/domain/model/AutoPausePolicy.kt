package com.pelonot.domain.model

/**
 * Decides when a ride should pause itself because the rider has stopped, and
 * when it should pick up again (PLAN.md 19.1.2).
 *
 * Every ride has a bottle stop, and a clock that keeps running through one
 * drags `avg_power` and `avg_cadence` down with it — the rider is charged for
 * the minute they spent at the tap. This is the same signal as 2.4.4 read the
 * other way round: there, a *frozen* reading had to stop being recorded as a
 * steady rider; here, a genuine zero has to stop being recorded as an easy one.
 *
 * Two rules earn their place:
 *
 * - **A stalled board is not a stopped rider.** When telemetry is not live
 *   there is no reading to call zero, so the stillness clock is held rather
 *   than run. A dropout mid-effort must not be mistaken for a stop, and an
 *   auto-pause must not be lifted on the strength of a stale number either.
 * - **Only a pause this policy caused is resumed automatically.** A rider who
 *   pressed pause and then turned the pedals while reaching for a towel has
 *   not asked to be racing again.
 */
class AutoPausePolicy(
    private val stillnessSeconds: Int = DEFAULT_STILLNESS_SEC
) {
    enum class Decision { None, Pause, Resume }

    private var stillSinceSec: Int? = null
    private var pausedByPolicy = false

    /** True while the current pause is one this policy asked for. */
    val isAutoPaused: Boolean get() = pausedByPolicy

    /**
     * @param elapsedSec the ride clock, which does not advance while paused
     * @param cadenceRpm the latest cadence, meaningful only when [telemetryLive]
     */
    fun onTick(
        elapsedSec: Int,
        cadenceRpm: Double,
        telemetryLive: Boolean,
        isPaused: Boolean
    ): Decision {
        if (!telemetryLive) {
            // No reading is not a zero reading. Hold everything where it is.
            stillSinceSec = null
            return Decision.None
        }

        val pedalling = cadenceRpm >= PEDALLING_RPM

        if (isPaused) {
            if (pausedByPolicy && pedalling) {
                pausedByPolicy = false
                stillSinceSec = null
                return Decision.Resume
            }
            return Decision.None
        }

        if (pedalling) {
            stillSinceSec = null
            return Decision.None
        }

        val since = stillSinceSec ?: elapsedSec.also { stillSinceSec = it }
        if (elapsedSec - since >= stillnessSeconds) {
            stillSinceSec = null
            pausedByPolicy = true
            return Decision.Pause
        }
        return Decision.None
    }

    /**
     * The rider worked the pause button themselves, in either direction.
     *
     * This hands ownership of the pause back to them: an auto-resume after a
     * deliberate pause would be the app arguing with the person on the bike.
     */
    fun onManualControl() {
        pausedByPolicy = false
        stillSinceSec = null
    }

    companion object {
        const val DEFAULT_STILLNESS_SEC = 20

        /**
         * Below this the wheel is coasting to a halt rather than being driven.
         * The board reports a true 0 when the cranks stop, so this only has to
         * survive the last fraction of a revolution.
         *
         * **This is the app's single definition of *pedalling*, and 19.1.2b is
         * why it is `const` rather than private.** `WorkoutSession` and
         * `WorkoutAggregates` divide `avg_power` and `avg_cadence` by the
         * seconds that clear it, so the averages and the pause agree about the
         * same word. Two thresholds would mean a ride whose average was taken
         * over seconds the app had already decided were a stop.
         */
        const val PEDALLING_RPM = 1.0
    }
}
