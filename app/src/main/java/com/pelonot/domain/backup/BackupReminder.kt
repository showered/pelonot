package com.pelonot.domain.backup

/**
 * Whether to remind the rider that their rides exist in one place (PLAN
 * 23.3.1).
 *
 * Backup is the offline rider's *only* durability story. A wipe, a factory
 * reset or an APK downgrade costs every ride they have done, and the app is the
 * only thing that knows how much is at stake — so saying nothing is a choice,
 * and it is the wrong one.
 *
 * **But the item's own words are "a reminder and not a nag"**, and that is the
 * whole design. Three rules come out of it, and they are here rather than in a
 * composable so they can be tested against a clock and a count instead of
 * against a screenshot:
 *
 * 1. **It is counted in rides, not in days.** A rider who has not been on the
 *    bike for a fortnight has lost nothing since their last backup and does not
 *    need telling. Time passing is not risk; unbacked riding is.
 * 2. **Dismissing it moves the line, it does not silence it.** "Not now" is
 *    answered honestly — the next [RIDES_BEFORE_REMINDER] rides earn the next
 *    reminder, and the rides already recorded do not come back to ask again.
 * 3. **A rider who has never backed up is not treated as urgent.** They get the
 *    same threshold as everyone else, because a first ride is not an emergency
 *    and an app that opens with a warning is an app that gets ignored.
 *
 * @param ridesSinceMark completed rides on this tablet since the last backup —
 *   or since the last dismissal, whichever is later.
 * @param hasEverBackedUp changes the sentence, never the threshold.
 */
data class BackupReminder(
    val ridesSinceMark: Int,
    val hasEverBackedUp: Boolean
) {

    val isDue: Boolean get() = ridesSinceMark >= RIDES_BEFORE_REMINDER

    /**
     * What it says, which is a count and never an exclamation.
     *
     * The number is in the sentence because it is the argument: "eleven rides"
     * is a thing the rider can weigh, and "back up your data" is a thing they
     * can ignore.
     */
    val message: String
        get() {
            val rides = if (ridesSinceMark == 1) "1 ride" else "$ridesSinceMark rides"
            return if (hasEverBackedUp) {
                "$rides since your last backup. They live on this tablet and " +
                    "nowhere else."
            } else {
                "$rides recorded on this tablet, and no backup yet. A backup is " +
                    "one file, and it restores onto any tablet running Pelonot."
            }
        }

    companion object {
        /**
         * Ten rides — about three weeks for a rider on the bike three or four
         * times a week, and long enough that nobody meets this in their first
         * fortnight with the app.
         */
        const val RIDES_BEFORE_REMINDER = 10

        val None = BackupReminder(ridesSinceMark = 0, hasEverBackedUp = false)
    }
}
