package com.pelonot.domain.model

/**
 * Where a ride's watts came from, taken over the whole ride.
 *
 * On the bike the board reports power directly and the number is a
 * measurement (2.1a). On a simulated ride it is `PowerModel`'s output, and that
 * model scores RMSE 137 W against real samples — a plausible-looking fiction.
 * The project's one non-negotiable rule about power is that a modelled watt is
 * never presented as a measured one, and until now the database could not tell
 * them apart at all.
 *
 * [Mixed] is a real state rather than a defensive branch: a board that drops
 * out mid-ride and comes back leaves exactly that behind.
 *
 * [Unknown] is every sample recorded before `power_is_measured` existed. It is
 * deliberately not folded into [Modelled] — "we did not write it down" and "the
 * app made these up" are different claims about a rider's record — but for
 * every decision in the app it is treated the same way, because
 * [isTrustworthyAsMeasured] is the question everything actually asks.
 */
enum class PowerProvenance {
    /** Every sample came off the bike's own board. */
    Measured,

    /** Every sample came out of `PowerModel`. */
    Modelled,

    /** Both, in one ride — usually a sensor dropout. */
    Mixed,

    /** Recorded before the app kept track. */
    Unknown;

    /**
     * True only for a ride that is measurement all the way through.
     *
     * The gate on anything that turns a ride into a *fact*: an FTP proposal
     * (7.10.7), a place on a leaderboard beside somebody else's real ride
     * (24.4.2). [Mixed] fails it on purpose — half a ride of invented watts is
     * enough to move a twenty-minute peak, and the rider cannot see which half.
     */
    val isTrustworthyAsMeasured: Boolean get() = this == Measured

    companion object {
        /**
         * @param measured samples whose power came off the board.
         * @param modelled samples whose power came out of the model.
         * @param unknown samples recorded before the column existed.
         */
        fun of(measured: Int, modelled: Int, unknown: Int): PowerProvenance = when {
            // A ride with no samples at all: nothing was measured, so nothing
            // may claim to have been.
            measured + modelled + unknown == 0 -> Unknown
            // One unrecorded sample makes the whole ride unknown: it can no
            // longer be shown to be measurement all the way through, which is
            // the only property anything downstream cares about.
            unknown > 0 -> Unknown
            modelled == 0 -> Measured
            measured == 0 -> Modelled
            else -> Mixed
        }
    }
}
