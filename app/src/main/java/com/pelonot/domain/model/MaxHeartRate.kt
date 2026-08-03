package com.pelonot.domain.model

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.roundToInt

/**
 * The maximum heart rate the app will compute zones from, and **where it came
 * from** (21.1).
 *
 * The ordering is the whole design: **ask for the measured number first and the
 * date of birth second** (21.1.3 before 21.1.2). Every age-based formula has a
 * between-individual spread of roughly 10–12 bpm, which is wider than a zone —
 * so for a meaningful fraction of riders the estimated zones are simply the
 * wrong zones. Asking for the real number is both more accurate *and* asks less
 * about the person, which is a rare combination.
 *
 * [source] travels with the number rather than being recoverable from it,
 * because a zone prescribed off a formula is a different claim from one
 * prescribed off a measurement, and the screen showing it has to be able to
 * say so (21.5.5).
 */
data class MaxHeartRate(
    val bpm: Int,
    val source: Source
) {
    enum class Source {
        /** The rider told the app their own number. */
        Measured,

        /** Derived from date of birth, and to be labelled as an estimate. */
        Estimated
    }

    val isEstimate: Boolean get() = source == Source.Estimated

    companion object {

        /**
         * Plausible bounds for a number typed by a rider. Outside these it is a
         * typo rather than a heart, and the same argument as `TelemetryBounds`
         * applies — **reject, never clamp**: 1900 silently becoming 220 would
         * put a rider in Recovery for an entire class with nothing on screen
         * looking wrong.
         */
        const val MIN_BPM = 120
        const val MAX_BPM = 230

        fun isPlausible(bpm: Int): Boolean = bpm in MIN_BPM..MAX_BPM

        /**
         * **Tanaka: 208 − 0.7 × age**, not the folk formula 220 − age.
         *
         * 220 − age has no published derivation, overestimates for younger
         * riders and underestimates for older ones — around 10 bpm out at 60,
         * which is a whole zone. Tanaka is the regression from the meta-analysis
         * that replaced it. It is still an estimate with a 10–12 bpm spread and
         * every screen showing it says so.
         */
        fun tanaka(ageYears: Int): Int = (208 - 0.7 * ageYears).roundToInt()

        /**
         * The number to compute zones from, or null when the app has neither
         * input — in which case **nothing draws a zone at all** (21.3.3).
         *
         * A rider who gave neither gets no heart-rate zones rather than wrong
         * ones. That is a real state with a real screen, not an oversight.
         */
        fun resolve(
            measuredBpm: Int?,
            birthDateMs: Long?,
            now: Long = System.currentTimeMillis()
        ): MaxHeartRate? {
            // The measured number wins wherever both exist: it is the rider's
            // own body against a regression line drawn through other people.
            if (measuredBpm != null && isPlausible(measuredBpm)) {
                return MaxHeartRate(measuredBpm, Source.Measured)
            }
            val age = ageInYears(birthDateMs, now) ?: return null
            return MaxHeartRate(tanaka(age), Source.Estimated)
        }

        /**
         * Whole years between [birthDateMs] and [now], or null if unknown or
         * implausible.
         *
         * `java.util.Calendar` rather than `java.time` because `minSdk` is 24
         * and core library desugaring is not enabled — the same reason
         * `RideDayGrouping` and `HouseholdWeek` give.
         *
         * A **date** rather than a stored age, so nobody's zones go quietly
         * stale on their birthday (21.1.1).
         */
        fun ageInYears(birthDateMs: Long?, now: Long = System.currentTimeMillis()): Int? {
            if (birthDateMs == null) return null

            val utc = TimeZone.getTimeZone("UTC")
            val born = Calendar.getInstance(utc).apply { timeInMillis = birthDateMs }
            val today = Calendar.getInstance(utc).apply { timeInMillis = now }

            var years = today.get(Calendar.YEAR) - born.get(Calendar.YEAR)
            // Not yet had this year's birthday.
            if (today.get(Calendar.DAY_OF_YEAR) < born.get(Calendar.DAY_OF_YEAR)) years -= 1

            return years.takeIf { it in MIN_AGE..MAX_AGE }
        }

        /**
         * Ages the formula is allowed to speak for. Below 10 and above 100 it
         * is extrapolating past its own data, and the likeliest cause of either
         * is a mis-set date picker.
         */
        const val MIN_AGE = 10
        const val MAX_AGE = 100
    }
}
