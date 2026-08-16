package com.pelonot.domain.progress

import com.pelonot.domain.model.PerceivedEffort
import kotlin.math.roundToInt

/**
 * One ride's worth of evidence that a rider's FTP is set too high (PLAN 7.11).
 *
 * Every field is something the ride wrote down at the time rather than
 * something recomputed on read, which is 7.8's rule and 23.4's: a ride's own
 * maximum heart rate is on the row (`workouts.max_hr_bpm`) because the rider's
 * current one may have moved since, and the twenty-minute effort comes off
 * `workout_power_bests`, computed at finalise while the seconds still existed.
 * **Nothing here can be derived from `workout_metrics` afterwards** — 23.4's
 * trimmer would answer it from a fifth of the samples and be believed.
 */
data class FtpEvidenceRide(
    val workoutId: String,
    val recordedAt: Long,
    /** The best twenty-minute mean power this ride held, in watts. */
    val peak20MinWatts: Double,
    /** The whole ride's mean heart rate, or null if nobody wore a strap. */
    val avgHr: Double? = null,
    /** The maximum this ride's zones were judged against (21.4.2a). */
    val rideMaxHrBpm: Int? = null,
    /** The rider's own answer on `workouts.rpe_rating`, 1–10, or null. */
    val rpeRating: Int? = null
) {
    /**
     * What this ride says the rider's FTP is, by the same arithmetic the
     * upward path uses — Coggan's twenty-minute correction and nothing else.
     *
     * **This is the reason 7.11 needs no new estimator and no calibration.**
     * The number a downward proposal offers is not modelled, inferred from a
     * heart rate or scaled by a guess: it is the same measurement
     * `PostWorkoutAnalyzer.estimateFtpFrom20MinPeak` makes, on measured watts,
     * pointed at a ride that came in under the rider's number instead of over
     * it. What 7.11 adds is a rule about *when that measurement is allowed to
     * be believed*, which is a different thing from a new way of computing it.
     */
    val impliedFtp: Double get() = peak20MinWatts * FtpReductionRule.FTP_FROM_20_MIN

    /** The rider's own verdict, in the three answers 26.3.3 settled on. */
    val effort: PerceivedEffort? get() = PerceivedEffort.of(rpeRating)

    /**
     * Whether this ride is evidence about the rider's *fitness* at all, as
     * opposed to evidence that they took it easy (7.11.1, 7.11.2).
     *
     * The owner's own sentence is the rule: *"If your BPM is unusually high
     * and/or you mark a workout as 'really difficult' and despite this your
     * scores are going down."* A twenty-minute peak below a rider's FTP is the
     * ordinary result of a recovery spin and means nothing on its own — which
     * is precisely why the upward path is safe to fire off one ride and this
     * one is not. So a ride only speaks here when something says the rider was
     * working:
     *
     * - **With a heart rate, the measurement leads and the rider can veto.**
     *   The ride's mean heart rate must be at or above
     *   [FtpReductionRule.WORKING_HR_FRACTION] of the maximum *that ride* was
     *   judged against, and if the rider answered *Comfortable* the ride is
     *   discarded however high the trace went.
     * - **With no heart rate, only the strongest self-report counts**, which is
     *   the owner's *"really difficult"* and nothing weaker. 7.11.2's rule is
     *   that RPE alone must not move an FTP; here it never does — the shortfall
     *   is the claim and this is only permission to read it — but a rider who
     *   answers *Everything I had* to every ride would otherwise turn every
     *   easy spin into evidence, so the bar for the unaided case is the top of
     *   the scale.
     *
     * A ride with neither signal is **not** counter-evidence. It is silent, and
     * [FtpReductionRule.evaluate] skips it rather than letting it break a
     * streak: absent is a claim, and the claim it makes is *nobody knows*.
     */
    val riderWasWorking: Boolean
        get() {
            val max = rideMaxHrBpm
            val mean = avgHr
            return if (max != null && max > 0 && mean != null) {
                mean >= max * FtpReductionRule.WORKING_HR_FRACTION &&
                    effort != PerceivedEffort.Easy
            } else {
                effort == PerceivedEffort.Maximal
            }
        }
}

/**
 * A proposal to lower a rider's FTP, with the rides it was read off (7.11).
 *
 * [evidence] travels with it because the dialog has to be able to say what it
 * is looking at (7.11.4). A downward claim about somebody's body that cannot
 * show its working is the one thing this must not ship as.
 */
data class FtpReduction(
    val currentFtp: Int,
    val proposedFtp: Int,
    val evidence: List<FtpEvidenceRide>
) {
    /** How far down, in whole watts — the number the dialog leads with. */
    val dropWatts: Int get() = currentFtp - proposedFtp

    /** The ride that produced the number being offered. */
    val strongestRide: FtpEvidenceRide get() = evidence.maxBy { it.impliedFtp }
}

/**
 * When the app is allowed to say a rider's FTP has gone **down** (PLAN 7.11).
 *
 * The owner's note, verbatim: *"Can it go down? It should go down. If your BPM
 * is unusually high and/or you mark a workout as 'really difficult' and despite
 * this your scores are going down, it should probably adjust downwards too?"*
 * Until this existed, nothing anywhere moved an FTP down by itself: the
 * breakthrough gate is `proposal >= currentFtp × 1.02`, which is above the
 * current number by construction, so a computed peak *below* it produced no
 * proposal rather than a downward one.
 *
 * ## Why this is not the breakthrough check with its sign flipped
 *
 * A twenty-minute peak *is* direct evidence of what a rider can produce, so one
 * strong ride can raise an FTP with nothing interpreting it. The mirror is not
 * true: an off day, a cold coming on, poor sleep, heat, an unfamiliar class or
 * simple under-fuelling all produce a disappointing twenty minutes with fitness
 * untouched, and lowering an FTP edits a number the rider is actively training
 * against — every zone on the ride screen and the overlay moves with it. So
 * three things are asked of the evidence rather than one:
 *
 * 1. **It is a trend, not a ride.** [MIN_EVIDENCE_RIDES] consecutive rides at
 *    which the rider was working must *all* have come in short. One good ride
 *    among them ends it.
 * 2. **The rider must have been trying** — [FtpEvidenceRide.riderWasWorking],
 *    which is the owner's own two-signal sentence.
 * 3. **The shortfall must be bigger than the one that raises an FTP.**
 *    [MIN_MEANINGFUL_LOSS] is 5% where `MIN_MEANINGFUL_GAIN` is 2%, and the
 *    asymmetry is the point rather than an oversight: a rider offered a number
 *    they did not earn can simply decline it, and a rider told their fitness
 *    has dropped on shaky evidence has been told something about themselves.
 *
 * ## The number offered is measured, not chosen
 *
 * It is the **best** twenty-minute effort across the whole evidence window,
 * scaled by Coggan's 0.95 — so it is something the rider has demonstrably done
 * in the last few rides, not an average of their worst days and not a
 * percentage step. That is what keeps 7.11 out of the trap 2.2a's calibration
 * work exists to name: **nothing here derives a recorded number from
 * `PowerModel`.** The evidence rides are measured-power rides only (7.11.2),
 * which is the same bar Phase 27 holds a *record* to.
 *
 * ## What is a guess here, said plainly (7.11.1)
 *
 * 7.11.1 asked for the window and the bar to be *measured* rather than picked,
 * the way 2.2a's curve was, and that measurement does not exist — it would need
 * a corpus of real riders' measured rides across a genuine loss of fitness,
 * which this project has one bike's worth of. So the three constants below are
 * **chosen to be conservative in the direction of not making the claim**, they
 * are named and gathered in one place rather than spread through the logic, and
 * 7.11.7 says what would settle each of them. Two of the three are not new
 * numbers at all: [FTP_FROM_20_MIN] and [WORKING_HR_FRACTION] are already in
 * `PostWorkoutAnalyzer`, and `FtpReductionRuleTest` asserts they have not
 * drifted apart from it.
 */
object FtpReductionRule {

    /**
     * Coggan's twenty-minute correction, and deliberately the same constant
     * `PostWorkoutAnalyzer.FTP_FROM_20_MIN` uses. Two copies of one number that
     * can disagree is how a rider gets raised on one arithmetic and lowered on
     * another; the test fences them together.
     */
    const val FTP_FROM_20_MIN = 0.95

    /**
     * At or above this fraction of the ride's own maximum heart rate, the rider
     * was working.
     *
     * The same 0.80 as `PostWorkoutAnalyzer.HR_THRESHOLD_FRACTION`, and it is
     * the same line read from the other side: there, a heart rate *below* it at
     * threshold power is evidence an FTP is set too **low**; here, a mean at or
     * above it says a short ride was a real effort rather than a spin. One
     * number for one question — *was this hard?* — rather than two thresholds
     * that can disagree about the same ride.
     */
    const val WORKING_HR_FRACTION = 0.80

    /**
     * A proposal must be at least 5% below the rider's current FTP.
     *
     * Against `MIN_MEANINGFUL_GAIN`'s 2%, which exists to clear the noise of
     * the measurement. This one has to clear something larger and less
     * tractable — the day-to-day spread of a rider's own twenty-minute power,
     * which is real, is a few percent, and is not a fitness change. It is the
     * least defensible number in this file and it is the one 7.11.7 would most
     * like measured.
     */
    const val MIN_MEANINGFUL_LOSS = 0.95

    /**
     * How many consecutive working rides must come in short.
     *
     * 7.11.1's own candidate was five to eight, and it was sized for a weaker
     * per-ride test — *heart rate at a given power has drifted up* across a
     * window that included easy rides. Each ride here has already had to be a
     * measured twenty-minute effort, at a heart rate at or above 80% of the
     * rider's maximum, coming in more than 5% short, so three of them in a row
     * is a great deal more evidence than eight of the other kind. Lowering the
     * count is not a relaxation when every ride counted has been made harder to
     * count. **Rides in between at which the rider was not working are skipped,
     * not counted against**: they say nothing either way.
     */
    const val MIN_EVIDENCE_RIDES = 3

    /**
     * The twenty-minute window, in seconds — `MeanMaximalPower.WINDOWS`' entry
     * and `PostWorkoutAnalyzer.TWENTY_MINUTES_SEC`.
     */
    const val EVIDENCE_WINDOW_SEC = 20 * 60

    /**
     * How many rides back the search for working ones goes.
     *
     * A bound on the work rather than a rule about fitness, and it is here
     * beside the rules so that it is visible rather than buried in a query. The
     * consequence is real and worth stating: a rider whose last three hard
     * rides sit more than twenty rides back gets no proposal from them. That is
     * the right answer anyway — twenty rides of not working since is a change
     * in what somebody is doing, not a decline they are in the middle of.
     */
    const val EVIDENCE_SCAN_LIMIT = 20

    /**
     * A proposal, or null when the evidence does not reach the bar.
     *
     * [rides] is **newest first** and is expected to be already limited to what
     * counts: this rider's finished, measured-power rides that hold a
     * twenty-minute effort, recorded *since* the last time their FTP moved or
     * they answered a proposal. That cutoff is the cooldown the upward path has
     * never had, and it matters far more here — accepting, editing or declining
     * all say *this number is right now*, and the evidence has to be rebuilt
     * from that moment rather than the same three rides asking again after
     * every subsequent ride.
     */
    fun evaluate(rides: List<FtpEvidenceRide>, currentFtp: Int): FtpReduction? {
        if (currentFtp <= 0) return null

        // Only rides the rider was working on speak at all; the rest are
        // silent rather than contradictory.
        val working = rides.filter { it.riderWasWorking }
        if (working.size < MIN_EVIDENCE_RIDES) return null

        val window = working.take(MIN_EVIDENCE_RIDES)
        val bar = currentFtp * MIN_MEANINGFUL_LOSS
        if (window.any { it.impliedFtp > bar }) return null

        // The best of the window, not the mean and not the latest: whatever is
        // offered has to be something the rider has actually ridden.
        val proposed = window.maxOf { it.impliedFtp }.roundToInt()
        if (proposed <= 0 || proposed >= currentFtp) return null

        return FtpReduction(
            currentFtp = currentFtp,
            proposedFtp = proposed,
            evidence = window
        )
    }
}
