package com.pelonot.domain.model

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * A first FTP for a rider who has never ridden, from questions they can
 * actually answer (PLAN 20.3.2, Route B).
 *
 * The owner's words were *"nobody in their right mind would know this"* about
 * a text box labelled `FTP (Watts)`. This is what replaces it: weight, which
 * the app already asks; date of birth, which it already wants for heart-rate
 * zones (21.1.1); and one description of the rider's own riding
 * ([FitnessLevel]). Age-adjusted watts per kilogram is a published, defensible
 * mapping, and it is a *guess with a method* rather than a number for
 * everybody.
 *
 * Pure Kotlin, no Android imports — CLAUDE.md's rule, and the reason this is
 * testable at every age and weight rather than only on a tablet.
 *
 * ## The bias has a direction, and that is the whole design
 *
 * Every value here sits **below** the published mid-range for its description,
 * and it is deliberate rather than cautious. The correction mechanism this
 * estimate relies on runs in exactly one direction:
 * `PostWorkoutAnalyzer.analyze` only surfaces a proposal when it clears
 * `currentFtp × MIN_MEANINGFUL_GAIN`, so **auto-FTP can raise a rider's FTP and
 * can never lower it**.
 *
 * So the two errors are not symmetrical:
 *
 * - **An estimate that starts too low is temporary.** The rider's first hard
 *   ride reads high against it, the app proposes the real number, and the
 *   estimate is gone. It costs them a few early rides drawn a zone hot, which
 *   feels like the app being generous.
 * - **An estimate that starts too high is permanent.** Nothing in this app will
 *   ever propose bringing it down. Every ride is drawn in Zone 2, no
 *   breakthrough ever clears the threshold, and the rider concludes the app
 *   does not work — which is 20.3.4's own stated failure and the reason it asks
 *   for the number's provenance to be visible.
 *
 * A second reason points the same way: the app does not collect sex and should
 * not start, and published FTP-per-kilogram tables differ by roughly 15% on it.
 * A table pitched at the mixed mid-range would run high for a meaningful share
 * of riders in the one direction nothing can correct. Pitching low means the
 * estimate is *wrong for almost everybody by a little, in the direction that
 * fixes itself*, which is the best a number derived from three questions can
 * honestly be.
 *
 * This is why [estimate] is not merely allowed to be imprecise — it is designed
 * around the asymmetry, and any future change to these coefficients has to
 * argue against that rather than around it.
 */
object FtpEstimator {

    /**
     * The estimate, in whole watts, or null when there is not enough to
     * estimate from.
     *
     * Null is a real answer and callers must handle it rather than defaulting:
     * a rider who declined to give a weight has not given the app anything to
     * multiply, and inventing one produces a number with no method behind it,
     * which is the thing this class exists to replace.
     *
     * @param weightKg body mass; null or non-positive means *not given*
     * @param level the rider's own description; null means *not asked*
     * @param ageYears age in whole years, or null when no date of birth was
     *   given — in which case no age adjustment is applied and the estimate is
     *   the peak-age figure, which is the low end of the range for an older
     *   rider and therefore the safe direction (see the class note)
     */
    fun estimate(weightKg: Double?, level: FitnessLevel?, ageYears: Int?): Int? {
        if (weightKg == null || weightKg <= 0.0) return null
        if (level == null) return null

        val watts = weightKg * level.wattsPerKg * ageFactor(ageYears)
        // Rounded to five, because an estimate reported as 147 W claims a
        // precision three questions cannot support. Same argument as 11.6.12's
        // whole watts, one step coarser because this number is a guess and that
        // one was a measurement.
        val rounded = (watts / ROUNDING_W).roundToInt() * ROUNDING_W
        return max(rounded, MINIMUM_W)
    }

    /**
     * The age term: flat to [PEAK_AGE_END], then a slow decline.
     *
     * Aerobic capacity per kilogram is broadly flat through the twenties and
     * early thirties and declines by something like 0.5–1% a year afterwards.
     * [DECLINE_PER_YEAR] takes the lower half of that band, for the same
     * asymmetry the class note argues: over-declining an older rider's estimate
     * is recoverable and under-declining it is not.
     *
     * There is no *increase* below [PEAK_AGE_END]. A fifteen-year-old is not
     * given a higher estimate than a thirty-year-old who describes their riding
     * the same way; the age input exists to stop the app telling a
     * seventy-year-old beginner they can hold the same watts as a
     * twenty-five-year-old one, which is the case that made 20.3.2 reject
     * Route A.
     */
    internal fun ageFactor(ageYears: Int?): Double {
        if (ageYears == null || ageYears <= PEAK_AGE_END) return 1.0
        val declined = 1.0 - (ageYears - PEAK_AGE_END) * DECLINE_PER_YEAR
        return max(declined, MINIMUM_AGE_FACTOR)
    }

    /**
     * Whole years between two epoch-millisecond instants, or null when there is
     * no date of birth.
     *
     * Deliberately takes *now* rather than reading the clock, so the estimate
     * is a function of its inputs and can be tested at any age without a fake
     * clock. Returns null for a date in the future, which is a rider mistyping
     * rather than a rider who is not yet born, and null is the honest answer to
     * both.
     */
    fun ageYearsAt(birthDateMillis: Long?, nowMillis: Long): Int? {
        if (birthDateMillis == null || birthDateMillis > nowMillis) return null
        val years = (nowMillis - birthDateMillis) / MILLIS_PER_YEAR
        return years.toInt().takeIf { it in 0..MAX_PLAUSIBLE_AGE }
    }

    /** Peak-age watts per kilogram is flat up to and including this age. */
    const val PEAK_AGE_END = 35

    /** Fractional loss per year of age beyond [PEAK_AGE_END]. */
    const val DECLINE_PER_YEAR = 0.006

    /** The decline stops here rather than running to zero at age 202. */
    const val MINIMUM_AGE_FACTOR = 0.70

    /** An estimate is reported to the nearest this many watts. */
    const val ROUNDING_W = 5

    /**
     * No estimate goes below this, whatever the arithmetic says.
     *
     * A 35 kg rider describing themselves as new lands on 55 W, and zones
     * computed from a denominator that small make every pedal stroke Zone 7.
     */
    const val MINIMUM_W = 60

    /** Mean Gregorian year. Good to a day over a human lifetime. */
    private const val MILLIS_PER_YEAR = 31_556_952_000L

    private const val MAX_PLAUSIBLE_AGE = 120
}
