package com.pelonot.domain.model

/**
 * How hard a ride felt, in three answers rather than ten (PLAN 26.3).
 *
 * **The owner's words, because the reason is the whole item:** *"It's 1-10
 * which is good but honestly it causes me anxiety, wondering if I'm selecting
 * the right option."* A ten-point scale asks a rider who has just got off the
 * bike out of breath to make a distinction — 6 or 7? — that they cannot make
 * and that nothing downstream uses. Borg's scale earns its resolution in a lab
 * where it is calibrated against a rider who has been taught it. On a bike in a
 * living room it manufactures a decision and then a doubt about it.
 *
 * **The column does not change.** `workouts.rpe_rating` is still 1–10, still
 * nullable, and still means what it always meant, for two reasons: rides
 * already recorded keep their exact answer rather than being reinterpreted, and
 * the cloud payload's field is unchanged (14.4).
 *
 * **This KDoc used to give a third reason and it was false** — that [Easy]
 * storing 3 kept `PostWorkoutAnalyzer.EASY_RPE_THRESHOLD` working, *"so a hard
 * class that felt easy still proposes an FTP bump exactly as before."* Nothing
 * has ever proposed an FTP bump from an RPE: the parameter carrying it had a
 * default and the one call site never passed it. The mapping reasoning was
 * sound and the claim about its effect was not, which is worth leaving on the
 * record because it is the same shape as every other stale claim this project
 * keeps finding, this time in a source comment. **Nothing reads the number
 * today** — it is shown back to the rider and it is carried to the cloud —
 * and 7.11.6 is where that was decided rather than discovered.
 *
 * Each level stores the **middle** of its band, and reads back anything inside
 * it. That is what lets a ride rated 7 on the old scale come back as *A good
 * workout* without anything having been rewritten on disk.
 */
enum class PerceivedEffort(
    /** What is written to `rpe_rating`: the middle of this level's band. */
    val rating: Int,
    /** The band this level answers for, when reading a stored value back. */
    val band: IntRange,
    val label: String,
    /**
     * One line under the label, and only where there is room for it.
     *
     * It is what makes the three answers distinguishable without a numeric
     * scale to lean on — "a good workout" is not self-evidently different from
     * "comfortable" until you say what each felt like.
     */
    val detail: String
) {
    Easy(
        rating = 3,
        band = 1..4,
        label = "Comfortable",
        detail = "I had more in me"
    ),
    Solid(
        rating = 6,
        band = 5..7,
        label = "A good workout",
        detail = "Working, and in control"
    ),
    Maximal(
        rating = 9,
        band = 8..10,
        label = "Everything I had",
        detail = "Nothing left at the end"
    );

    companion object {
        /**
         * The level a stored rating falls in, or null for a ride nobody
         * answered for.
         *
         * Null is *unanswered* and never *easy* — the same rule as
         * `heartRateBpm`. A ride the rider walked away from without saying how
         * it felt has no answer, and filling one in would be the app writing
         * into the rider's own record.
         */
        fun of(rating: Int?): PerceivedEffort? =
            rating?.let { value -> entries.firstOrNull { value in it.band } }
    }
}
