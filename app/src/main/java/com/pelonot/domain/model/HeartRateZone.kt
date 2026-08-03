package com.pelonot.domain.model

import kotlin.math.roundToInt

/**
 * Five heart-rate zones as a percentage of maximum heart rate (21.2.1).
 *
 * **Deliberately not a mirror of [PowerZone].** Five zones is the usual heart
 * rate convention against seven for power, and the boundaries are not the same
 * boundaries — telling a rider that HR zone 4 and power zone 4 are the same
 * thing would be false, so they do not share a palette either (the colours live
 * beside the power ones in the theme and are a different ramp on purpose).
 *
 * The basis is **%HRmax** (21.2.2), chosen because it is the only one that
 * needs a single number from the rider. %HRR (Karvonen) wants a resting heart
 * rate and %LTHR wants a threshold test; both are better and both are gated on
 * data the app does not have, and 21.1.4 is explicit that a field nothing reads
 * must not be collected. Wherever these zones are shown, say which basis they
 * are on.
 *
 * Bounds are **contiguous and half-open**, the same fix [PowerZone] carries: the
 * published tables quote "60–70%, 70–80%" and leave a rider at 70.4% matching
 * nothing.
 *
 * [H1] starts at zero rather than at the conventional 50%, and the reason is
 * worth keeping straight, because it looks like the mistake this project keeps
 * making. It is not. Zero watts means *nobody is pedalling* — an empty bike,
 * which is why `ZoneScale` refuses to draw a zone for it — whereas 48% of
 * maximum is a real measurement of a real rider sitting on the saddle. The
 * absence that matters here is a **missing reading**, and that is handled where
 * it belongs: [forHeartRate] answers null, and null bpm never reaches a zone at
 * all (21.2.4).
 */
enum class HeartRateZone(
    val number: Int,
    val displayName: String,
    /** Lower bound as a fraction of maximum heart rate, inclusive. */
    val lowerBound: Double,
    /** Upper bound as a fraction of maximum heart rate, exclusive. [H5] is unbounded. */
    val upperBound: Double
) {
    H1(1, "Recovery", 0.00, 0.60),
    H2(2, "Aerobic", 0.60, 0.70),
    H3(3, "Tempo", 0.70, 0.80),
    H4(4, "Threshold", 0.80, 0.90),
    H5(5, "Maximum", 0.90, Double.POSITIVE_INFINITY);

    /**
     * The beats per minute this zone covers for a rider with this [maxHr].
     *
     * [H5] is reported up to [maxHr] itself rather than to infinity: a rider
     * can exceed the number they gave the app, but a range printed as "171–∞"
     * helps nobody.
     */
    fun bpmRange(maxHr: Int): IntRange {
        val low = (maxHr * lowerBound).roundToInt()
        val high = if (upperBound.isInfinite()) maxHr else (maxHr * upperBound).roundToInt() - 1
        return low..high.coerceAtLeast(low)
    }

    companion object {

        /**
         * The zone [bpm] falls in, or **null when there is nothing to say**.
         *
         * Null for a missing reading and null for a rider whose maximum heart
         * rate the app does not know — 21.2.4, and the same rule as
         * `ZoneScale.currentZone`. This project has twice corrupted a rider's
         * record by treating a missing heart rate as a number; a zone invented
         * from a maximum nobody supplied would be the same mistake wearing a
         * percentage sign.
         */
        fun forHeartRate(bpm: Int?, maxHr: Int?): HeartRateZone? {
            if (bpm == null || bpm <= 0 || maxHr == null || maxHr <= 0) return null
            val fraction = bpm.toDouble() / maxHr
            return entries.lastOrNull { fraction >= it.lowerBound } ?: H1
        }
    }
}
