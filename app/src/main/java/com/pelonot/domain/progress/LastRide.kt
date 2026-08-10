package com.pelonot.domain.progress

/**
 * The rider's most recent finished ride, reduced to what the dashboard says
 * about it (PLAN 22.1.5).
 *
 * **Not a total.** The card this feeds replaced one reading *"Recent Ride
 * 73 kJ"*, beside another reading *"Today's Output 73 kJ"* — two cards, one
 * number, twice — and a kilojoule figure is the answer to a question nobody
 * standing in front of a bike is asking. What the dashboard is for (22.1.1) is
 * *should I ride today, and what should I ride*, and the useful thing about the
 * last ride is **what it was, when it was, and whether it went well**.
 *
 * No output and no watts on purpose (Phase 26): a unit belongs where a
 * measurement is being read, and this is a choice being made. The kilojoules
 * are two taps away on the ride itself, where they are a measurement again.
 */
data class LastRide(
    /** Opens the ride (12.2). */
    val workoutId: String,
    /** The class it was, or null for a free ride. */
    val classTitle: String? = null,
    val atEpochMs: Long,
    val durationSec: Int,
    val standing: RideStanding = RideStanding.Unclaimed
) {
    val minutes: Int get() = (durationSec + 30) / 60
}

/**
 * How the last ride stood against the rider's own earlier rides of the same
 * class (22.1.5) — or why no such claim is being made (22.1.7).
 *
 * [NotBest] and [Unclaimed] draw the same thing today and are still different
 * claims, which is the same argument `PowerProvenance` makes about [Unknown]
 * against `Modelled`: *"they did not beat it"* and *"there is nothing here that
 * can be compared"* are not one state, and folding them together is how a
 * screen ends up asserting the first when it only knows the second.
 */
enum class RideStanding {
    /** Beat every earlier ride of the same class, measured watts on both sides. */
    Best,

    /** Ridden before, and not beaten. */
    NotBest,

    /**
     * No honest comparison available.
     *
     * A free ride (nothing to be a repeat of), a first ride of a class (a
     * "best" computed from one ride is noise wearing a trophy — 22.1.6's own
     * argument), or watts that were not measured on one side or the other.
     */
    Unclaimed
}

/**
 * Whether the last ride was the best the rider has ridden of that class.
 *
 * Pure, because the interesting part is the **refusals** rather than the
 * comparison: three of the four branches decline to make a claim, and each one
 * is a rule this project has already paid for somewhere else.
 */
object LastRideStanding {

    /**
     * @param classId the class the ride was of, or null for a free ride.
     * @param outputKj this ride's total.
     * @param isMeasured whether this ride's watts came off the board all the
     *   way through — `PowerProvenance.isTrustworthyAsMeasured`, and nothing
     *   weaker (22.1.7, 24.4.2).
     * @param earlierMeasuredTotals every **other** measured ride of the same
     *   class by the same rider.
     */
    fun of(
        classId: String?,
        outputKj: Double,
        isMeasured: Boolean,
        earlierMeasuredTotals: List<Double>
    ): RideStanding {
        // A free ride is not a repeat of anything: two Just Rides of different
        // lengths on different days are not the same effort compared twice.
        if (classId == null) return RideStanding.Unclaimed

        // 22.1.7, and the rule the whole app runs on: `PowerModel` scores
        // RMSE 137 W, so a modelled ride placed above a measured one is the
        // app inventing a personal best. The fact that both rides are the same
        // rider's makes it more misleading rather than less — they have no
        // second opinion to check it against.
        if (!isMeasured) return RideStanding.Unclaimed

        val best = earlierMeasuredTotals.maxOrNull() ?: return RideStanding.Unclaimed

        return if (outputKj > best) RideStanding.Best else RideStanding.NotBest
    }
}
