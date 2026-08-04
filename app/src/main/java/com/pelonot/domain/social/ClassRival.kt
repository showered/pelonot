package com.pelonot.domain.social

/**
 * A finished ride of this class that can be raced live (PLAN 24.3.3).
 *
 * Offered on the class detail screen *before* the ride starts, because
 * choosing mid-ride is a menu over a rider who is pedalling — 15.1.6's rule
 * about modals during a ride applies to everything, not only to auth.
 *
 * One type for a housemate's ride and for the rider's own best, because from
 * the ghost's point of view they are the same thing: another ride of the same
 * class, under the same measured-power rule. [you] exists only because
 * "Kilo" and "your best" read very differently on a chip — and because the
 * owner's own note is that *"or yourself"* is the case that makes this work at
 * all, since most riders are the only person who has ridden a given class.
 */
data class ClassRival(
    val workoutId: String,
    val name: String,
    val outputKj: Double,
    val you: Boolean = false
)
