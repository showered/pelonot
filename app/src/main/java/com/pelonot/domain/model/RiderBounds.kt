package com.pelonot.domain.model

/**
 * What the app will believe about a rider's body (PLAN 20.5.1).
 *
 * **Found by walking the first-run path with fresh eyes.** `68` typed into a
 * field labelled `Weight (lb)` — 31 kg — produced *"Here's where we'll start
 * you: 65 W"*, said with exactly the confidence of any other estimate, and the
 * rider rode away on it. The arithmetic was right: [FtpEstimator] is linear in
 * weight and had nothing to disbelieve. Nothing else in the app would have
 * disbelieved it either — the number goes onto the profile, onto every ride,
 * and into the denominator of the whole zone system.
 *
 * **It matters most in the direction that cannot be corrected.**
 * [FtpEstimator]'s own note explains why: auto-FTP only ever proposes a number
 * *upward*, so a weight typo that starts a rider low costs them a few hot rides
 * and one that starts them high is permanent. A fence catches both.
 *
 * **Reject, never clamp**, which is CLAUDE.md's rule and not a style choice
 * here either: silently correcting `6` to `30` writes a plausible lie into the
 * rider's own record where a refusal would have made them look at the field. So
 * this answers *whether*, and the screen says so; nothing here substitutes a
 * value.
 *
 * The bounds are deliberately wide. This is not a plausibility model of who
 * rides bikes — it is the line past which a number is certainly a typo or a
 * unit mix-up, and the two failures it exists for are a missing digit and lb
 * typed into a kg field.
 */
object RiderBounds {

    /** Below this, a weight is a slip rather than a small person. */
    const val MIN_WEIGHT_KG = 25.0

    /** Above this, likewise — and 250 kg is 551 lb. */
    const val MAX_WEIGHT_KG = 250.0

    /**
     * True when [weightKg] is a weight the app is willing to build an FTP on.
     *
     * Null is *not* out of range: the weight is optional at profile creation,
     * and an absent answer is a different claim from a wrong one — the same
     * distinction `heartRateBpm` and `target_position` are built on. It is the
     * screen's business whether it will continue without one.
     */
    fun weightIsPlausible(weightKg: Double?): Boolean =
        weightKg == null || weightKg in MIN_WEIGHT_KG..MAX_WEIGHT_KG

    /**
     * What to say under the field, in the rider's own unit, or null when there
     * is nothing to say.
     *
     * It quotes the range rather than saying "that's wrong", because the whole
     * failure this catches is a rider who does not realise which unit the field
     * is in — and the range in *their* unit is the sentence that shows it.
     */
    fun weightProblem(weightKg: Double?, units: UnitSystem): String? {
        if (weightIsPlausible(weightKg)) return null
        val low = units.weightFromKg(MIN_WEIGHT_KG).toInt()
        val high = units.weightFromKg(MAX_WEIGHT_KG).toInt()
        return "That should be between $low and $high ${units.weightLabel}."
    }
}
