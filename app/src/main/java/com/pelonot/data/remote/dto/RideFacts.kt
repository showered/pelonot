package com.pelonot.data.remote.dto

import com.pelonot.data.local.entity.WorkoutEntity
import com.pelonot.domain.chart.RideDistributions
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The facts about a ride that the cloud has no column for (PLAN 15.3.2).
 *
 * **This exists because a restore is the moment every one of them is needed and
 * the moment none of them is there.** `workouts` in Postgres has the twelve
 * columns 14.4 settled on, and everything this project has learnt since — the
 * FTP a ride was ridden at (7.8), the maximum heart rate its zones were judged
 * against (21.2.3), what its seconds counted before they were thrown away
 * (23.4.2), whether it was ridden straight through (8.3d.2) — lives in a column
 * the wire has never carried. Uploading was the only direction, so nobody
 * noticed. Bringing a ride back down without them is not a smaller ride: it is a
 * ride whose zone bands are silently redrawn from today's FTP and whose time in
 * zone, if it came down as an outline, is **recomputed from a fifth of its
 * seconds** — 7.8 and 23.4.2, the two defects this project has fixed most often,
 * arriving together through a new door.
 *
 * **It goes inside `metrics_payload` rather than into new columns**, and that is
 * a deliberate trade rather than laziness. Columns would be queryable, which
 * would be nicer; they would also be a cloud migration, which only the owner can
 * apply, on an endpoint that already has rides on it (17.16.2 is the standing
 * complaint about exactly this). The payload is versioned inside itself
 * precisely so a reader can be handed a shape it does not know (14.4.3), and
 * `d` was added the same way in 23.4.14 with no migration and nothing for
 * anybody to run. So this is the same door, one field wider.
 *
 * Every field defaults to what its absence honestly means, because Postgrest
 * serialises with its own `Json` and omits a property equal to its default (the
 * `v` lesson, 14.4.3): a ride uploaded before this existed decodes to
 * [RideFacts] with nothing in it, which is *"nobody wrote these down"* — never a
 * zero and never a guess.
 */
@Serializable
data class RideFacts(
    /** `workouts.ftp_watts` (7.8) — null means the ride never recorded one. */
    @SerialName("ftp") val ftpWatts: Int? = null,
    /** `workouts.max_hr_bpm` (21.2.3). Null is the same claim as above. */
    @SerialName("mhr") val maxHrBpm: Int? = null,
    /** `workouts.resume_count` (8.3d.2). Zero is a fact: it was not interrupted. */
    @SerialName("res") val resumeCount: Int = 0,
    /** `workouts.interrupted_sec` (8.3d.2). */
    @SerialName("int") val interruptedSec: Int = 0,
    /** `workouts.was_recovered` — rebuilt from its samples after a crash (8.3). */
    @SerialName("rec") val wasRecovered: Boolean = false,
    /**
     * What this ride's seconds counted, for after they are gone (23.4.2).
     *
     * Absent for an intact ride, which is right rather than a gap: its own
     * samples come down with it and are a better answer than any summary. It is
     * a condensed ride that cannot do without this — `RideChartBuilder` falls
     * back to counting whatever rows it has, and counting an outline's rows says
     * a 25-minute ride pedalled for five.
     */
    @SerialName("dist") val distributions: RideDistributions? = null
) {
    companion object {
        /**
         * Null when there is nothing to say, so an ordinary ride's payload is
         * byte-for-byte what it was before this existed.
         */
        fun of(workout: WorkoutEntity): RideFacts? = RideFacts(
            ftpWatts = workout.ftpWatts,
            maxHrBpm = workout.maxHrBpm,
            resumeCount = workout.resumeCount,
            interruptedSec = workout.interruptedSec,
            wasRecovered = workout.wasRecovered,
            distributions = RideDistributions.decode(workout.distributionsJson)
        ).takeIf { it != RideFacts() }
    }
}
