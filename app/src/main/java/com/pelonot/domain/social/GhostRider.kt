package com.pelonot.domain.social

import com.pelonot.domain.model.Interval
import com.pelonot.domain.model.LiveLeaderboard

import com.pelonot.domain.model.RaceMetric
import com.pelonot.domain.model.RideIntent
import com.pelonot.domain.model.RivalTrace
import com.pelonot.domain.model.targetPowerRange
import kotlin.math.ceil

/**
 * Competitors nobody rode (PLAN 24.3.18).
 *
 * The owner's note: *"There should always be some target … no matter how high
 * you go there should always be a target ahead of you."* Every row on the live
 * board until now was a ride that actually happened, which means the board is
 * empty on any class this household has not ridden — and with 72 classes and a
 * four-person population, **that is the ordinary case, not the edge one**.
 *
 * **The honesty rule is 24.3.18a and it shapes the whole file.** A ghost is a
 * *target*, never a rival: it cannot make an empty board worth showing, it
 * never reaches the post-ride summary, it is never synced, and it is marked on
 * screen so a rider can never come away thinking a housemate did 300 kJ when
 * 300 was a number this app made up. That is why [LiveLeaderboard.Ghost] now
 * carries a [GhostKind] rather than this being inferred from the name.
 *
 * Everything here is pure and JVM-tested. Nothing reads the clock, the
 * database, or `PowerModel` — see [prescribedTotalKj] for why that last one
 * matters.
 */
object GhostRider {

    /**
     * How many generated rows may share the board (24.3.18c).
     *
     * The owner's worry, verbatim: *"if there are too many CPU targets then we
     * might lose sight of the (more exciting) human targets."* Three of six is
     * the answer they chose: humans are placed first and ghosts take only what
     * is left, so a busy class quietly has no ghosts on it at all and a class
     * nobody has ridden is all ghosts. Half is the most a board can be
     * invented and still be a leaderboard.
     */
    const val MAX_GHOSTS = 3

    /** The rungs of the milestone ladder, in kilojoules. */
    const val MILESTONE_STEP_KJ = 50.0

    /** How much harder than the rider's own best the stretch ghost rides. */
    const val STRETCH_FACTOR = 1.05

    /**
     * Below this many of the rider's own rides, *your usual* is not a usual.
     *
     * A median of two is one of the two rides, and calling a rider's second
     * ever attempt at a class their "usual" is a claim about a habit that does
     * not exist yet.
     */
    const val USUAL_MIN_RIDES = 3

    /**
     * Before this many seconds a ladder does not chase the rider's pace.
     *
     * Projecting a finishing total from the first few seconds of a ride is
     * arithmetic on noise — a rider who is ten seconds in and briefly at 300 W
     * projects a total nobody has ever ridden, and the target would leap and
     * then sag for the rest of the class. Until then the ladder sits on
     * whatever the board's own best is.
     */
    const val LADDER_SETTLES_AFTER_SEC = 60

    /**
     * A ghost holding one steady pace to [total] over [durationSec].
     *
     * One point per ten seconds rather than per second: this is a straight
     * line, `RivalTrace` reads it by binary search, and a 45-minute class at 1
     * Hz would be 2,700 points describing a line that two points describe
     * exactly. The last point lands on [durationSec] so the ghost *finishes*
     * with the class rather than a few seconds early — 24.3.6 makes a finished
     * competitor stop, and a target that stopped nine seconds before the line
     * would hand the rider a win they did not get.
     */
    fun paceTrace(
        total: Double,
        durationSec: Int,
        metric: RaceMetric = RaceMetric.Output
    ): RivalTrace {
        if (durationSec <= 0 || total <= 0) return RivalTrace(emptyList(), metric)
        val step = 10
        val points = buildList {
            var second = 0
            while (second < durationSec) {
                add(second to total * second / durationSec)
                second += step
            }
            add(durationSec to total)
        }
        return RivalTrace(points, metric)
    }

    /**
     * The kilojoules a rider would finish with by riding every block at the
     * **middle of the band the class prescribes** — the class as it was
     * written.
     *
     * This is the one ghost that is not an arbitrary number, and the argument
     * for building it: catching it means *"I rode what the class asked for"*,
     * which is a training goal rather than a score. It needs no history, so it
     * is on the board for the very first ride of a class nobody has touched,
     * which is the empty-board case this whole item exists for.
     *
     * **It does not touch `PowerModel`, and that is not an accident.** The
     * curve is measurably wrong (RMSE 137 W) and fenced to two consumers;
     * PLAN 2.2a.8 makes adding a third a test failure. Nothing here needs it:
     * a zone's target *is* watts, derived from the rider's own FTP, so this is
     * `watts × seconds ÷ 1000` and no guess about the bike enters into it.
     *
     * The rider's [intent] scales the bands exactly as it scales what they are
     * shown mid-ride, so the ghost rides the class the rider actually chose
     * rather than the one in the catalogue.
     */
    fun prescribedTotalKj(
        intervals: List<Interval>,
        ftpWatts: Double,
        intent: RideIntent = RideIntent.DEFAULT
    ): Double {
        if (ftpWatts <= 0) return 0.0
        return intervals.sumOf { interval ->
            val seconds = interval.durationSec
            if (seconds <= 0) return@sumOf 0.0
            val band = interval.powerZone.targetPowerRange(ftpWatts, intent)
            val watts = (band.start + band.endInclusive) / 2.0
            watts * seconds / 1000.0
        }
    }

    /**
     * The smallest rung of the ladder strictly above [above].
     *
     * Strictly, so a rider sitting exactly on 300 is given 350 rather than the
     * number they have already reached. That is the whole of *"no matter how
     * high you go"*.
     */
    fun nextMilestone(above: Double, step: Double = MILESTONE_STEP_KJ): Double =
        (ceil(above / step) * step).let { if (it <= above) it + step else it }

    /**
     * The median of the rider's own totals on this class, or null when they
     * have not ridden it enough times for the word *usual* to be true.
     *
     * The only ghost deliberately placed **behind** a rider on form, and that
     * is its job: on a bad day every other row on the board is a rebuke, and
     * this one is catchable. Same argument as 22.5.4's — a motivational
     * feature that only ever says *you are behind* is one a person switches
     * off.
     */
    fun usualTotal(ownTotals: List<Double>): Double? {
        val sorted = ownTotals.filter { it > 0 }.sorted()
        if (sorted.size < USUAL_MIN_RIDES) return null
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) {
            sorted[middle]
        } else {
            (sorted[middle - 1] + sorted[middle]) / 2.0
        }
    }

    /**
     * The generated rows for this ride, best target first and capped at
     * [MAX_GHOSTS].
     *
     * **Ordered by how much each one means rather than by size**, because the
     * cap bites on a class the rider knows well: *the class as written* is the
     * only non-arbitrary target and survives first; the stretch on their own
     * best is the most personal; *your usual* is the safety net. The milestone
     * ladder is not in this list at all — it is [LiveLeaderboard.pacer],
     * because unlike these three it has to move when the rider does.
     *
     * @param personalBestKj the rider's best ever on this class, or null.
     * @param ownTotalsKj every total this rider has recorded on this class.
     */
    fun ghostsFor(
        intervals: List<Interval>,
        durationSec: Int,
        ftpWatts: Double,
        intent: RideIntent = RideIntent.DEFAULT,
        personalBestKj: Double? = null,
        ownTotalsKj: List<Double> = emptyList()
    ): List<LiveLeaderboard.Ghost> {
        if (durationSec <= 0) return emptyList()

        val candidates = buildList {
            prescribedTotalKj(intervals, ftpWatts, intent)
                .takeIf { it > 0 }
                ?.let { add(GhostKind.Prescribed to it) }

            personalBestKj
                ?.takeIf { it > 0 }
                ?.let { add(GhostKind.Stretch to it * STRETCH_FACTOR) }

            usualTotal(ownTotalsKj)?.let { add(GhostKind.Usual to it) }
        }

        return candidates.take(MAX_GHOSTS).map { (kind, total) ->
            LiveLeaderboard.Ghost(
                name = kind.label,
                trace = paceTrace(total, durationSec),
                kind = kind
            )
        }
    }
}

/**
 * What a row on the live board *is* (PLAN 24.3.18a, 24.3.18e).
 *
 * Carried rather than inferred from the name, so nothing on the drawing side
 * ever has to pattern-match a string to decide whether a row is a person.
 *
 * **[label] is where 24.3.12a is settled**, and the two questions had to be
 * answered together: ghosts arriving would otherwise have put a second family
 * of invented name on a board that already had `12 MONTHS` and `30 DAYS`
 * sitting among real people. The owner's brief was *"self-explanatory but also
 * personal and not too geeky"*, so every non-human row is now written from the
 * rider's point of view — *Your best*, *Your recent best*, *Your usual* — and
 * the two that were durations are people-shaped sentences about the rider
 * rather than spans of time.
 */
enum class GhostKind(
    val label: String,
    /**
     * Whether this row is **somebody else**.
     *
     * False for the rider's own past rides as well as for generated targets:
     * both are things to chase, neither is a person in the race. It is what
     * 24.3.18c's *humans take precedence* counts.
     */
    val isPerson: Boolean,
    /**
     * Whether **this app made the number up** (24.3.18a).
     *
     * Orthogonal to [isPerson], and the pair has to be two flags rather than
     * one: the rider's own best is not a person and is not invented either.
     * Conflating them would either mark a real ride of theirs as fictional or
     * let a generated target be counted as a rival — and the second is exactly
     * the failure the honesty rule exists to prevent.
     */
    val isGenerated: Boolean
) {
    /** Somebody who actually rode it — a housemate, or a friend (18.5). */
    Human("", isPerson = true, isGenerated = false),

    /** The rider's own best ever on this class. A real ride of theirs. */
    YourBest("Your best", isPerson = false, isGenerated = false),

    /** Their best of the last twelve months — `12 MONTHS` until 24.3.12a. */
    YourBestThisYear("Your best this year", isPerson = false, isGenerated = false),

    /** Their best of the last thirty days — `30 DAYS` until 24.3.12a. */
    YourRecentBest("Your recent best", isPerson = false, isGenerated = false),

    /** The class ridden exactly as prescribed (24.3.18b, candidate 3). */
    Prescribed("Class target", isPerson = false, isGenerated = true),

    /** Their own best plus five per cent (24.3.18b, candidate 2). */
    Stretch("Just past your best", isPerson = false, isGenerated = true),

    /** The median of their own rides of this class (24.3.18b, candidate 4). */
    Usual("Your usual", isPerson = false, isGenerated = true),

    /** A round number, and the rung moves (24.3.18b, candidate 1). */
    Milestone("", isPerson = false, isGenerated = true);

    /** Whether this row was generated rather than ridden (24.3.18a). */
    val isGhost: Boolean get() = isGenerated
}
