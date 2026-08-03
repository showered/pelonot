package com.pelonot.data.service

import com.pelonot.domain.model.RideIntent
import com.pelonot.domain.model.WorkoutAggregates
import kotlin.math.roundToInt

/**
 * An in-flight or just-finished workout.
 *
 * Fully immutable. The previous version declared `var elapsedSeconds` on a
 * data class, incremented it in place and then published `session.copy()` to
 * force a `StateFlow` emission. Because the mutation had already been applied
 * to the instance the flow was holding, `copy()` produced an equal object and
 * `StateFlow`'s equality-based conflation dropped the update — the HUD timer
 * updated only when some other field happened to differ.
 *
 * @property elapsedSeconds Ride time excluding paused periods.
 */
data class WorkoutSession(
    val workoutId: String,
    val userId: Int?,
    val classId: String?,
    val startedAtEpochMs: Long,
    val elapsedSeconds: Int = 0,
    val intent: RideIntent = RideIntent.DEFAULT,
    val ftpWatts: Int = DEFAULT_FTP,
    val totalOutputKj: Double = 0.0,
    val distanceKm: Double = 0.0,
    val avgPower: Double = 0.0,
    val avgCadence: Double = 0.0,
    /**
     * Kept as a `Double` and rounded only for display.
     *
     * This used to be an `Int` updated by rounding the running mean on every
     * tick, which froze it: once the ride is long enough that one new sample
     * moves the mean by less than 1 bpm, truncation discards the whole
     * increment and the average can never change again. A real ride recorded
     * 79 bpm — the lowest sample of the warm-up — against a true mean of 105.
     */
    val avgHeartRateBpm: Double? = null,
    /**
     * Counted separately from [sampleCount] because heart rate is optional.
     * A strap that connects thirty seconds into a ride contributes to far
     * fewer ticks than the bike does, and dividing by the tick count would
     * weight each of its samples far too lightly.
     */
    val heartRateSampleCount: Int = 0,
    val sampleCount: Int = 0,
    /**
     * How many times this ride has been interrupted and picked up again, and
     * for how long in total (8.3d.2).
     *
     * **Carried on the session because the session is what writes the row.**
     * `resumeInterruptedWorkout` stamps these on `workouts` at the moment of
     * resuming, and `stopWorkout` then finalises the ride by building a *fresh*
     * entity out of this session — so without them here the finalise writes the
     * defaults back over the facts, and a ride resumed twice ends up claiming
     * it was ridden straight through. Measured, not reasoned about: the row
     * said `resume_count = 0` after two observed resumes.
     *
     * It is the same shape as the defect in 7.10.3 — two writers, one row, the
     * later one carrying a stale copy of a field it does not know about.
     */
    val resumeCount: Int = 0,
    val interruptedSec: Int = 0
) {
    /** True for a guest ride, which the user is asked to save or discard. */
    val isGuestRide: Boolean get() = userId == null

    /** Whole bpm for display; null still means *no strap*, never zero. */
    val avgHeartRate: Int? get() = avgHeartRateBpm?.roundToInt()

    /**
     * Folds one sensor sample into the running means.
     *
     * Pure and here rather than in [WorkoutService] so it can be tested on the
     * JVM: the defect this replaces — a heart-rate average that stuck at the
     * lowest reading of the warm-up — was invisible from every screen and only
     * showed up by comparing `workouts.avg_hr` against the mean of the ride's
     * own `workout_metrics` rows.
     *
     * @param heartRateBpm null means *no reading*, and contributes nothing.
     *   It must never be treated as zero.
     */
    fun withSample(
        powerWatts: Double,
        cadenceRpm: Double,
        heartRateBpm: Int?
    ): WorkoutSession {
        val samples = sampleCount + 1
        val hrSamples = heartRateSampleCount + if (heartRateBpm == null) 0 else 1

        return copy(
            avgPower = runningMean(avgPower, powerWatts, samples),
            avgCadence = runningMean(avgCadence, cadenceRpm, samples),
            avgHeartRateBpm = heartRateBpm?.let { bpm ->
                runningMean(avgHeartRateBpm ?: bpm.toDouble(), bpm.toDouble(), hrSamples)
            } ?: avgHeartRateBpm,
            heartRateSampleCount = hrSamples,
            sampleCount = samples
        )
    }

    /**
     * Reopens this session on the totals a ride had already accumulated before
     * it was interrupted (8.3d).
     *
     * Without it a resumed ride's `workouts` row would describe only the part
     * ridden *after* the crash, while its `workout_metrics` series described
     * all of it — the same class of defect as `avg_hr`, where the row and the
     * samples it summarises disagreed for the project's whole history and
     * nothing on any screen looked wrong.
     *
     * The means are restored with the sample counts they were built at, so
     * subsequent readings are weighted correctly against them; [elapsedSeconds]
     * is restored so the class clock and the metric series both continue from
     * the last second that recorded rather than from zero (8.3d.1).
     */
    fun restoredWith(aggregates: WorkoutAggregates): WorkoutSession = copy(
        elapsedSeconds = aggregates.durationSec,
        totalOutputKj = aggregates.totalOutputKj,
        distanceKm = aggregates.distanceKm,
        avgPower = aggregates.avgPower,
        avgCadence = aggregates.avgCadence,
        avgHeartRateBpm = aggregates.avgHeartRateExact,
        heartRateSampleCount = aggregates.heartRateSampleCount,
        sampleCount = aggregates.sampleCount
    )

    companion object {
        const val DEFAULT_FTP = 150

        /**
         * Welford's incremental mean, so a ride never has to hold every
         * sample in memory. [count] must be the number of samples that have
         * contributed to [previousMean] — passing a larger count (the total
         * tick count, say, for an optional signal like heart rate) silently
         * under-weights each new sample.
         */
        internal fun runningMean(previousMean: Double, sample: Double, count: Int): Double =
            previousMean + (sample - previousMean) / count
    }
}

/** Lifecycle states for a workout. */
enum class WorkoutState {
    Idle,
    Active,
    Paused,

    /** Finished and persisted; the summary screen can read final figures. */
    Completed
}
