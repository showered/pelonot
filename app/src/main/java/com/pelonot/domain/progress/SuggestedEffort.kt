package com.pelonot.domain.progress

import com.pelonot.domain.chart.EffortAgainstPlan
import com.pelonot.domain.chart.TimeInHeartRateZone
import com.pelonot.domain.model.HeartRateZone
import com.pelonot.domain.model.PerceivedEffort

/**
 * Which of the three answers the rider's own heart rate points at (PLAN 21.6.1).
 *
 * The owner's note is the reason this exists: *"surely there's something we can
 * infer from heart rate (if connected)? … you don't even need to ask!"* 21.6.3
 * built the half of that which is free — one sentence on a screen, an
 * observation about a ride. This is the half with a rider's own record on the
 * other side of it, and the whole of its design is the rule that keeps it from
 * reaching that record.
 *
 * ### It suggests, and it never answers
 *
 * `workouts.rpe_rating` means **what the rider said**, and it has to keep
 * meaning that or every reader of it is reading something else. So nothing here
 * is written anywhere: this returns one of three [PerceivedEffort] values, a
 * screen draws a mark on the button it names, and the column stays null until a
 * thumb lands on one. That is 21.6.1's *"it must stay a prefill"* taken one step
 * further than the word — a genuine prefill (the answer already filled in,
 * saved, with the rider free to change it) would put the app's guess in the
 * rider's column for every rider who walks away without looking, and the two
 * would be indistinguishable afterwards. **The saved tap is not worth a column
 * that lies**, and 7.10.4/7.10.5 already said so for the FTP.
 *
 * A consequence worth stating plainly: this saves the rider a *decision*, not a
 * tap. That is the thing 26.3 says is expensive — *"it causes me anxiety,
 * wondering if I'm selecting the right option"* — and a suggestion answers it
 * where a shorter list only shortened it.
 *
 * ### The heart is read absolutely, not against the plan
 *
 * [EffortAgainstPlan] asks *was this harder than the class asked*, which is a
 * question about the **gap**. This asks *how hard was it*, which is a question
 * about the **ride**, because that is what the three buttons ask. The two come
 * apart on exactly the ride you would expect: twenty minutes of recovery spin
 * ridden at tempo is a large gap and still not *everything I had*.
 *
 * So the evidence is the heart's own distribution and nothing else — and the
 * owner's two examples are both stated that way as well: *"an endurance class
 * ridden mostly in HR zones 4–5 was hard; a threshold class ridden in zone 2 was
 * easy."* Both discriminate on the heart. The class is context in the sentence
 * and never the measurement.
 *
 * ### The two outer answers need evidence; the middle one is what is left
 *
 * [PerceivedEffort.Maximal] is *"nothing left at the end"* and
 * [PerceivedEffort.Easy] is *"I had more in me"*, and both are claims a rider
 * would resent having wrong on their behalf. So each is gated on an
 * unambiguous reading — half the reported ride at threshold heart rate or
 * above, or virtually none of it above zone 2 — and everything between them is
 * [PerceivedEffort.Solid], which is the answer that costs least when it is
 * wrong.
 *
 * ### Silence is the ordinary answer, and it is [EffortAgainstPlan]'s rule
 *
 * No strap, no maximum heart rate for the rider (21.2.4), under ten minutes
 * heard, or a strap that covered less than half of what the ride recorded — the
 * same four gates and the same two constants, because *when a strap's evidence
 * is worth believing* is one question and should not have two answers. 21.6.4
 * is why they are blunt: heart rate lags effort, drifts up across a long ride
 * and moves with heat, sleep and caffeine, so this is a fair signal about a ride
 * and a poor one about a minute of it.
 *
 * **It is not gated on the maximum being measured rather than estimated**, which
 * 21.6.2 does require for the FTP path. The difference is what the number
 * touches: an FTP is written into the rider's record and every zone in the app
 * is derived from it, where this is a mark on a button the rider is looking at
 * and about to overrule if it is wrong. Two guesses wearing one number is a
 * problem when nobody can see either.
 */
object SuggestedEffort {

    /**
     * The answer this ride's heart rate points at, or null when there is
     * nothing worth pointing at.
     *
     * [heart] is the ride's own counts — from `distributions_json` where the
     * ride has been condensed (23.4), so this survives a trim for the same
     * reason [EffortAgainstPlan] does.
     */
    fun of(heart: TimeInHeartRateZone): PerceivedEffort? {
        val heardSeconds = heart.totalSeconds
        if (heardSeconds < EffortAgainstPlan.MIN_HEART_SECONDS) return null
        if (heardSeconds < heart.recordedSeconds * EffortAgainstPlan.MIN_COVERAGE) return null

        val hard = secondsAtOrAbove(heart, HeartRateZone.H4).toDouble() / heardSeconds
        val gentle = secondsAtOrBelow(heart, HeartRateZone.H2).toDouble() / heardSeconds

        return when {
            hard >= MAXIMAL_HARD_FRACTION -> PerceivedEffort.Maximal
            hard <= EASY_HARD_CEILING && gentle >= EASY_GENTLE_FRACTION -> PerceivedEffort.Easy
            else -> PerceivedEffort.Solid
        }
    }

    private fun secondsAtOrAbove(heart: TimeInHeartRateZone, zone: HeartRateZone): Int =
        heart.secondsByZone.filterKeys { it.number >= zone.number }.values.sum()

    private fun secondsAtOrBelow(heart: TimeInHeartRateZone, zone: HeartRateZone): Int =
        heart.secondsByZone.filterKeys { it.number <= zone.number }.values.sum()

    /**
     * Half the reported ride at threshold heart rate or above.
     *
     * Deliberately high. Cardiac drift alone will carry a long steady ride into
     * [HeartRateZone.H4] for its last third, and *"nothing left at the end"* is
     * not the thing to say about that ride.
     */
    private const val MAXIMAL_HARD_FRACTION = 0.50

    /** Essentially nothing above tempo — a ride, not a rounding error. */
    private const val EASY_HARD_CEILING = 0.05

    /** And most of it genuinely low, not merely short of threshold. */
    private const val EASY_GENTLE_FRACTION = 0.75
}
