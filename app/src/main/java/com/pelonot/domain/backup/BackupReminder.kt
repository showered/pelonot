package com.pelonot.domain.backup

/**
 * Whether to remind the rider that their rides exist in one place (PLAN
 * 23.3.1, 23.3.1a, 23.3.1b).
 *
 * Backup is the offline rider's *only* durability story. A wipe, a factory
 * reset or an APK downgrade costs every ride they have done, and the app is the
 * only thing that knows how much is at stake — so saying nothing is a choice,
 * and it is the wrong one.
 *
 * **But the item's own words are "a reminder and not a nag"**, and that is the
 * whole design. Four rules come out of it, and they are here rather than in a
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
 * 4. **A ride the cloud already holds is not at stake and is not counted**
 *    (23.3.1a). This is the rule the owner's note forced, and it changes *which*
 *    rides are counted rather than the shape of anything above it.
 *
 * **Rule 4 is also what makes the sentence true again** (23.3.1b). Until it
 * existed this object said *"They live on this tablet and nowhere else"* to
 * every rider, including one whose rides had gone up — which is not a nag but a
 * **wrong claim about where somebody's data is**, and the worst kind to be
 * wrong about, because it is the sentence a rider reads *instead of* checking.
 * 15.2.8 met the same shape on the Settings card and 23.3.2 repaired the same
 * words one screen along; this is the third and last place they were said.
 *
 * **The card does not go away for a signed-in rider**, and that is the point of
 * carrying both counts rather than replacing one with the other. Cloud backup
 * covers one profile's rides; the backup file covers the whole tablet — the
 * housemates, the profile photos, and the guest rides, which can never sync at
 * all (rule 1 of the connectivity model) and are therefore *always* on this
 * list. What changes is that the card can now say which is which, so a rider
 * who has just signed in reads evidence that it worked instead of a flat
 * contradiction of it (23.3.1c).
 *
 * @param ridesSinceMark completed rides on this tablet since the last backup —
 *   or since the last dismissal, whichever is later.
 * @param ridesOnTabletOnly how many of [ridesSinceMark] nothing else holds a
 *   copy of. Never greater than [ridesSinceMark]; equal to it on a bike where
 *   nobody has signed in, which is the offline tier and the ordinary case.
 * @param hasEverBackedUp changes the sentence, never the threshold.
 */
data class BackupReminder(
    val ridesSinceMark: Int,
    val ridesOnTabletOnly: Int,
    val hasEverBackedUp: Boolean
) {

    /** Rule 4: what is already up is not what a backup would save. */
    val isDue: Boolean get() = ridesOnTabletOnly >= RIDES_BEFORE_REMINDER

    /**
     * Whether anything at all has gone up — which is the only reason this
     * object ever mentions an account.
     *
     * Deliberately derived from the two counts rather than from a sign-in flag.
     * A rider can be signed in with nothing uploaded yet, and telling them the
     * account holds their other rides would be the same false claim in the
     * other direction.
     */
    val someRidesAreUp: Boolean get() = ridesOnTabletOnly < ridesSinceMark

    /**
     * What it says, which is a count and never an exclamation.
     *
     * The number is in the sentence because it is the argument: "eleven rides"
     * is a thing the rider can weigh, and "back up your data" is a thing they
     * can ignore.
     *
     * **The "since your last backup" framing survives only where it is true.**
     * Once [someRidesAreUp], the count is no longer *"everything you have
     * ridden since you last backed up"* — it is a filtered subset — so the
     * sentence stops claiming the backup mark is where the number came from.
     * That matters more than it looks: 2.5a.5's distance pass clears
     * `synced_at` on rides it touches, so this count can move for reasons that
     * have nothing to do with when the rider last backed up.
     */
    val message: String
        get() {
            val rides = if (ridesOnTabletOnly == 1) "1 ride" else "$ridesOnTabletOnly rides"
            return when {
                // The ordinary case, and the offline tier's whole world:
                // nothing has a copy anywhere, so the original wording is
                // still exactly true and is left alone.
                !someRidesAreUp && hasEverBackedUp ->
                    "$rides since your last backup. They live on this tablet and " +
                        "nowhere else."

                !someRidesAreUp ->
                    "$rides recorded on this tablet, and no backup yet. A backup is " +
                        "one file, and it restores onto any tablet running Pelonot."

                // Some rides are up and these are not. The second sentence is
                // the evidence half (23.3.1c): a rider who has just signed in
                // and is looking for proof it worked can read it here, on the
                // screen they are already on.
                hasEverBackedUp ->
                    "$rides live on this tablet and nowhere else. An account holds " +
                        "the others; it does not hold these."

                else ->
                    "$rides live on this tablet and nowhere else, and no backup yet. " +
                        "An account holds the others — a backup is one file, and it " +
                        "takes everything on the tablet."
            }
        }

    companion object {
        /**
         * Ten rides — about three weeks for a rider on the bike three or four
         * times a week, and long enough that nobody meets this in their first
         * fortnight with the app.
         */
        const val RIDES_BEFORE_REMINDER = 10

        val None = BackupReminder(
            ridesSinceMark = 0,
            ridesOnTabletOnly = 0,
            hasEverBackedUp = false
        )
    }
}
