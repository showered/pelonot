package com.pelonot.domain.progress

import com.pelonot.core.Formatters
import com.pelonot.domain.chart.TimeInZone
import com.pelonot.domain.model.PowerZone
import kotlin.math.roundToInt

/**
 * Where a rider's last thirty days of riding actually went (PLAN 21.4.3).
 *
 * *Your riding* answers two questions today — **how much** (the weekly bars) and
 * **how often** (the day squares) — and neither of them is the one that changes
 * what a rider does next. The third is **how hard**, and the plan names it as
 * *"the number that actually drives a training decision — how much easy riding
 * did I do this month"*. One ride's time in zone is already drawn on ride detail
 * (16.1.4); this is the same count over a window, which is a different question
 * rather than a bigger version of the same one: a single hard ride says nothing
 * about how a month was spent, and a month is the unit at which the answer is
 * actionable.
 *
 * ### The window is 30 rolling days, and the item's own word for it is stale
 *
 * 21.4.3 says *weekly*, and it was written before 22.5 — the owner's note that a
 * week is the wrong window for somebody who rides once of them. At that cadence
 * a weekly intensity mix is one ride's shape drawn as a trend, which is exactly
 * the defect 22.5.1 was written to remove. So this uses [RECENT_WINDOW_DAYS],
 * the same rolling window the card at the top of the screen already reports, and
 * for the same reason: it never resets and it always has four or five rides in
 * it.
 *
 * ### Every ride is counted against its own FTP, and the sum is still honest
 *
 * A zone is a fraction of an FTP, so summing zone seconds across a month sums
 * counts made against **different denominators** whenever the rider's FTP moved
 * inside it — which since 7.11 it can do in both directions. That is the right
 * answer rather than a compromise: each ride's zones are what that ride *was*,
 * for the rider as they were that day, and re-counting the month against today's
 * number is 7.8's defect at thirty times the scale. The alternative also has a
 * tell — accepting one auto-FTP change would redraw a month the rider had
 * already read.
 *
 * ### A ride that cannot be counted is absent, and said out loud
 *
 * [ridesCounted] against [ridesInWindow] is not bookkeeping. A ride with no
 * per-second record behind it, or one that never wrote down the FTP it was
 * ridden at, contributes nothing — and a bar drawn from four of a rider's nine
 * rides while the card above says *nine rides* is a true picture presented as a
 * false one. That is 21.4.1's coverage caption, one level up: the same argument
 * that stopped a strap which heard eleven minutes of forty describing the ride.
 *
 * ### It observes and it does not prescribe
 *
 * 21.4.4, and it is the rule this feature is most likely to break. There is a
 * well-known target here — the polarised *80-20* — and stating it would turn
 * every honest sentence on the card into a mark out of ten for a rider who never
 * asked to be marked. So the card says where the riding went and never where it
 * should have gone. [easyFraction] and [hardFraction] are arithmetic; nothing
 * here compares them to anything.
 */
data class RidingIntensity(
    /** The rolling window these seconds were counted over, in days. */
    val days: Int = RECENT_WINDOW_DAYS,
    /** Every counted ride's seconds, added together. */
    val timeInZone: TimeInZone = TimeInZone(),
    /** Rides in the window whose zones this could be built from. */
    val ridesCounted: Int = 0,
    /** Rides in the window, counted or not. */
    val ridesInWindow: Int = 0
) {
    /** Nothing to draw, which is a rider who has not ridden and not a fault. */
    val hasAnything: Boolean get() = timeInZone.totalSeconds > 0

    /** Rides the window holds that contributed no seconds. See the note above. */
    val ridesUncounted: Int get() = (ridesInWindow - ridesCounted).coerceAtLeast(0)

    /** True when every ride in the window is in the bar. */
    val isComplete: Boolean get() = ridesUncounted == 0

    /** Seconds spent at or below [EASY_CEILING]. */
    val easySeconds: Int
        get() = secondsAtOrBelow(EASY_CEILING)

    /** Seconds spent at or above [HARD_FLOOR]. */
    val hardSeconds: Int
        get() = secondsAtOrAbove(HARD_FLOOR)

    val easyFraction: Float get() = fractionOf(easySeconds)

    val hardFraction: Float get() = fractionOf(hardSeconds)

    private fun fractionOf(seconds: Int): Float {
        val total = timeInZone.totalSeconds
        return if (total == 0) 0f else seconds.toFloat() / total
    }

    private fun secondsAtOrBelow(zone: PowerZone): Int = timeInZone.secondsByZone
        .filterKeys { it.number <= zone.number }
        .values.sum()

    private fun secondsAtOrAbove(zone: PowerZone): Int = timeInZone.secondsByZone
        .filterKeys { it.number >= zone.number }
        .values.sum()

    companion object {

        /**
         * The top of *easy*, and the bottom of *hard*, in the app's own terms.
         *
         * Neither is a new threshold invented here. [HARD_FLOOR] is
         * `EffortAgainstPlan`'s, which is [PowerZone.Z4] because that is where
         * this scale's own convention puts threshold — the same boundary, so the
         * two features cannot come to mean different things by the same word.
         * [EASY_CEILING] is the complement a rider means by *easy riding*:
         * everything below tempo, which is the endurance riding that a month is
         * supposed to be mostly made of.
         *
         * [PowerZone.Z3] is deliberately in neither, and is not given a name on
         * the card either. It is the middle, it is visible in the bar, and
         * three bands where two carry the meaning is a word spent for nothing
         * (Phase 26).
         */
        val EASY_CEILING = PowerZone.Z2
        val HARD_FLOOR = PowerZone.Z4

        /**
         * @param rides one entry per ride in the window, **null for a ride whose
         *   zones could not be counted** — no per-second record left, or no FTP
         *   to divide by. Null rather than an empty [TimeInZone], because a ride
         *   that was counted and spent nothing anywhere is a different claim
         *   from a ride nobody could count.
         */
        fun of(
            rides: List<TimeInZone?>,
            days: Int = RECENT_WINDOW_DAYS
        ): RidingIntensity {
            val counted = rides.filterNotNull()
            val byZone = mutableMapOf<PowerZone, Int>()
            counted.forEach { ride ->
                ride.secondsByZone.forEach { (zone, seconds) ->
                    byZone[zone] = (byZone[zone] ?: 0) + seconds
                }
            }
            return RidingIntensity(
                days = days,
                timeInZone = TimeInZone(
                    secondsByZone = byZone,
                    secondsStopped = counted.sumOf { it.secondsStopped }
                ),
                ridesCounted = counted.size,
                ridesInWindow = rides.size
            )
        }
    }
}

/**
 * What the intensity card says, in words (21.4.3).
 *
 * Here rather than in the composable for `RideChartSummaries`' reason: a canvas
 * is unreadable to a screen reader, so the sentence *is* the chart for some
 * riders, and a sentence that is the chart is worth a test.
 *
 * **Every sentence here is arithmetic.** Nothing compares the mix to a target,
 * recommends more of anything, or calls a month good or bad — see the note on
 * [RidingIntensity] about 80-20, which is the exact temptation this card exists
 * next to.
 */
object RidingIntensitySummary {

    /**
     * The one line under the bar.
     *
     * The plain words and the zone names are both here on purpose: the card's
     * title says *easy and hard* and the legend under the bar says `Z2
     * Endurance`, and a rider should not have to work out that those are the
     * same claim. Phase 26 allows the jargon precisely here — a caption on a
     * chart is where a measurement is being read.
     */
    fun mix(intensity: RidingIntensity): String {
        if (intensity.ridesCounted == 0) {
            return "None of these rides kept a per-second record to count."
        }
        val zone = intensity.timeInZone
        if (zone.totalSeconds == 0) {
            // Every counted ride was recorded and none of it was pedalled,
            // which is a different fact from having nothing to count (19.1.2c).
            return "Nothing was pedalled in " +
                "${spelledDuration(zone.recordedSeconds)} of recording."
        }

        val easy = (intensity.easyFraction * 100).roundToInt()
        val hard = (intensity.hardFraction * 100).roundToInt()
        return "${spelledDuration(zone.totalSeconds)} ridden: $easy% easy " +
            "(Z1–Z2) and $hard% hard (Z4 and above)."
    }

    /**
     * The facts the bar is drawn under, joined the way ride detail joins its
     * own — window first, then whatever qualifies it.
     *
     * **The coverage clause is the one that matters** and it is stated as a
     * count of rides rather than a percentage of them: *from 5 of 12 rides* is
     * checkable against the number at the top of the same screen, where "42%
     * coverage" is a figure a rider can only take on trust (21.4.1).
     */
    fun caption(intensity: RidingIntensity): String = listOfNotNull(
        "Time in zone across the last ${intensity.days} days",
        (
            "from ${intensity.ridesCounted} of " +
                Formatters.plural(intensity.ridesInWindow, "ride")
            ).takeIf { !intensity.isComplete },
        intensity.timeInZone
            .takeIf { it.isPartial }
            ?.let {
                "pedalling for ${Formatters.duration(it.totalSeconds)} of " +
                    Formatters.duration(it.recordedSeconds)
            }
    ).joinToString(" · ")

    /**
     * `4 hours 12 minutes`, and never `4:12:00`.
     *
     * A month's riding is read as a quantity rather than a clock, and the
     * seconds are noise at this scale — a rider comparing two months does not
     * care about the eleven of them. Spelled out because this is a sentence and
     * because a screen reader says `4:12:00` as three numbers.
     */
    private fun spelledDuration(totalSeconds: Int): String {
        val minutes = (totalSeconds / 60.0).roundToInt()
        if (minutes < 60) return Formatters.plural(minutes, "minute")
        val hours = minutes / 60
        val rest = minutes % 60
        val spelled = Formatters.plural(hours, "hour")
        return if (rest == 0) spelled else "$spelled ${Formatters.plural(rest, "minute")}"
    }
}
