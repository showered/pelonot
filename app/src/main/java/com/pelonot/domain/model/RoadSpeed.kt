package com.pelonot.domain.model

import kotlin.math.cbrt
import kotlin.math.sqrt

/**
 * How fast the watts a rider is producing would carry them along a flat road
 * (PLAN 2.5a).
 *
 * A stationary bike has no wheel and covers no ground, so every distance this
 * app has ever shown is a fiction. What this changes is *which* fiction. The
 * old one integrated **cadence** at 2.1 m a revolution and never once looked at
 * how hard the rider was pushing: half an hour at 85 rpm was 5.4 km whether it
 * was a recovery spin or a standing climb, and the owner met it as *"I rode at
 * about 130W for 30 minutes and only clocked something like 5km. It's surely
 * WAY off."* They were right — a Peloton says about 13 km for that ride.
 *
 * This one is the standard flat-road power equation, solved for speed:
 *
 * ```
 * P · η = v · (Crr · m · g + ½ · ρ · CdA · v²)
 * ```
 *
 * rolling resistance plus aerodynamic drag, which above about 20 km/h is most
 * of it. It is a real equation rather than a fudge factor, and nothing in it was
 * tuned to match Peloton — it lands within a few percent of them on its own.
 *
 * **The rider is nominal, and that is the one decision here that could
 * reasonably have gone the other way.** This app knows the rider's real weight
 * and using it would be more physical: a heavier rider genuinely is slower for
 * the same watts. It must not, because distance is a **race metric**
 * ([RaceMetric.Distance]) and a leaderboard where two riders producing identical
 * watts show different distances is a leaderboard comparing bodies rather than
 * efforts. One curve for everybody. Peloton's own speed is a function of output
 * alone for the same reason.
 *
 * Nothing here is measured and nothing here is recorded: `workout_metrics` keeps
 * the watts, and a distance is re-derivable from them at any time.
 */
object RoadSpeed {

    /** Rider plus bike. Nominal for everybody — see the class KDoc. */
    private const val MASS_KG = 84.0

    private const val GRAVITY = 9.80665

    /** Coefficient of rolling resistance: a road tyre on tarmac. */
    private const val CRR = 0.005

    /** Drag area, m². A rider on the hoods, neither aero nor sitting up. */
    private const val CDA = 0.40

    /** Air density at sea level, 15 °C. */
    private const val AIR_DENSITY = 1.225

    /** Drivetrain efficiency: a chain loses a few percent. */
    private const val DRIVETRAIN_EFFICIENCY = 0.97

    /** The `v³` coefficient — ½ρCdA. */
    private const val DRAG = 0.5 * AIR_DENSITY * CDA

    /** The `v` coefficient — Crr·m·g. */
    private const val ROLLING = CRR * MASS_KG * GRAVITY

    /**
     * Metres per second for [watts], zero for anything at or below nothing.
     *
     * Solved exactly rather than searched: `v³ + pv = q` is a depressed cubic
     * with `p > 0`, so its discriminant is always positive and Cardano's formula
     * gives the single real root in two cube roots. Iterating would be slower
     * and would need a tolerance nobody could justify — this runs once per
     * sample on the recording path.
     */
    fun metresPerSecond(watts: Double): Double {
        if (watts <= 0.0 || watts.isNaN()) return 0.0

        val q = watts * DRIVETRAIN_EFFICIENCY / DRAG
        val p = ROLLING / DRAG
        val half = q / 2.0
        val root = sqrt(half * half + p * p * p / 27.0)
        return cbrt(half + root) + cbrt(half - root)
    }

    /** The same answer in km/h, which is what a speed is read in. */
    fun kmPerHour(watts: Double): Double = metresPerSecond(watts) * 3.6

    /** Kilometres covered in [seconds] at a steady [watts]. */
    fun kilometres(watts: Double, seconds: Double): Double =
        metresPerSecond(watts) * seconds / 1000.0
}
