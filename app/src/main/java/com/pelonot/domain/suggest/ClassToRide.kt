package com.pelonot.domain.suggest

import kotlin.math.abs

/**
 * One class in the library, reduced to what choosing between them needs
 * (PLAN 22.8.6).
 *
 * Deliberately not `ClassPlan`: this file is the *rule*, and a rule that can see
 * the intervals will sooner or later start reasoning about them. What choosing
 * needs is how long a class is, what kind it is, and how hard it gets — three
 * facts, all derived once by the caller.
 */
data class SuggestableClass(
    val id: String,
    val title: String,
    val category: String,
    val durationSec: Int,
    /**
     * The hardest power zone the class asks for (1..7), or null when it asks for
     * nothing at all — a malformed template, which the library browser already
     * survives and this must too.
     */
    val hardestZone: Int?
)

/** How much of a given class this rider has ridden, and when they last did. */
data class ClassRideCount(
    val classId: String,
    val rides: Int,
    val lastRiddenAtMs: Long
)

/** A completed ride, reduced to the three things the rule reads off it. */
data class RecentRide(
    /** Null for a Just Ride, and for a ride of a class since retired. */
    val classId: String?,
    val atEpochMs: Long,
    val durationSec: Int
)

/**
 * Everything the rule knows about the rider — and it is all `workouts`.
 *
 * No `workout_metrics`: this feeds the first screen anybody sees, and 22.1.8's
 * rule is that the dashboard does not wait on a per-sample scan. It also means
 * the suggestion needs no measured power and therefore works identically on the
 * emulator, on a simulated ride and on a bike — unlike every comparison in the
 * app that ranks two rides against each other.
 */
data class RiderRides(
    /** Every class this rider has ridden, in any order. */
    val perClass: List<ClassRideCount> = emptyList(),
    /** The rider's most recent completed rides, **newest first**. */
    val recent: List<RecentRide> = emptyList()
)

/**
 * The class the dashboard offers, and why it is offering it.
 *
 * The reason is a type rather than a string because it is the part that has to
 * be **true**, and a phrase assembled in a composable is a claim nothing can
 * test. Rendering it is the screen's job; deciding it is this file's.
 */
data class ClassSuggestion(
    val classId: String,
    val title: String,
    val category: String,
    val durationSec: Int,
    val reason: Reason
) {
    val minutes: Int get() = (durationSec + 30) / 60

    sealed interface Reason {
        /** The rider has ridden nothing at all: this is a place to start. */
        data object FirstRide : Reason

        /**
         * The last ride was hard and was recent, so this one is not.
         *
         * Outranks [NewToYou] when both are true, because it is the reason that
         * explains a *surprising* choice — a rider offered a recovery class
         * wants to know why, and a rider offered an unridden threshold class
         * does not.
         */
        data object EasyAfterHard : Reason

        /** The rider has never ridden this class. The ordinary case. */
        data object NewToYou : Reason

        /** They have ridden it, and not since [atEpochMs]. */
        data class NotSince(val atEpochMs: Long) : Reason
    }
}

/**
 * *What should I ride?* — the second half of the dashboard's own question
 * (22.1.1), which nothing on that screen answered until this (PLAN 22.8.6).
 *
 * ## The rule, in one sentence
 *
 * **Something the length you usually ride, that you have ridden least — and an
 * easy one if you rode hard in the last day.**
 *
 * ## What it deliberately is not
 *
 * It is **not a training plan.** This app has no periodisation, no fatigue
 * model and no model of the rider's form beyond one FTP number that moves in
 * one direction (7.11). A card that said *"today is your interval day"* would
 * be inventing all three, and it would be inventing them on the screen a rider
 * trusts most. The only advisory claim made anywhere here is *don't stack two
 * hard days*, which is the same rule Phase 28 states from the other end when it
 * forbids an achievement that rewards what a coach would advise against.
 *
 * Everything else it says is an **observation about the rider's own history**:
 * you have not ridden this one; you have not ridden it since March; you rode
 * hard yesterday. Those are facts on this device, and the card can be argued
 * with rather than merely believed.
 *
 * ## Why "ridden least" is the ranking
 *
 * The library is 72 authored classes (23.2.6) and a rider settles on three.
 * Breadth is the one thing an offline app with a fixed library is unusually
 * well placed to offer — it needs no account, no network and no measurement —
 * and it is the same argument Phase 28 makes for its breadth family.
 *
 * ## Why it is deterministic
 *
 * Nothing here is random. A card that re-rolls on every glance is noise: the
 * rider cannot ask *why this one* and get an answer, and they cannot come back
 * to the class they were half-decided about. Two riders with the same history
 * get the same suggestion, and so does the same rider twice — which is also
 * what makes it testable at all.
 */
object ClassToRide {

    /**
     * Zone 4 and above is *hard*. Z4 is threshold: the first zone a rider
     * cannot hold a conversation in, and the level above which the advice not
     * to stack two days running is uncontroversial.
     */
    private const val HARD_ZONE = 4

    /**
     * Zone 3 and below is *easy enough to follow a hard day*. That is Recovery
     * and Endurance whole, and the tempo end of Sweet Spot — deliberately read
     * off the blocks rather than off the category name, because the category is
     * a label and the blocks are the workout.
     */
    private const val EASY_MAX_ZONE = 3

    /**
     * How recent a hard ride has to be to change the suggestion: one day.
     *
     * Not two. At the once-a-week riding this app assumes (22.5) a 48-hour
     * window would still almost never fire, and at four rides a week it would
     * fire most days — turning the honest claim *"you rode hard last night"*
     * into a permanent fixture that says nothing. A day is the span in which
     * the rider themselves would say *"I rode hard yesterday"*.
     */
    private const val HARD_RECOVERY_MS = 24L * 60 * 60 * 1000

    /**
     * How many rides "the length you usually ride" is taken over.
     *
     * A median rather than a mean, over the recent window rather than all of
     * history: one 60-minute epic should not move a 20-minute rider, and a
     * rider who has moved from 20 to 45 minutes should be offered 45.
     */
    private const val USUAL_WINDOW = 10

    /**
     * The category a rider who has never ridden is started in.
     *
     * Endurance rather than Recovery: a recovery class is a class that only
     * makes sense *after* something, and offering one as a first ever ride
     * describes a workout nobody has earned. Endurance is a real ride at an
     * effort a first-timer can finish, which is what makes it a place to start
     * rather than a test.
     */
    private const val STARTER_CATEGORY = "Endurance"

    /**
     * Three, and it is a judgement about the screen rather than about riding.
     *
     * Two is not a range and four is a browse — and the library door is already
     * on this screen for anybody who wants one. 22.9.3's ceiling is the fold,
     * so the row that fills it has to stop somewhere on its own.
     */
    private const val STARTING_POINTS = 3

    /**
     * @param nowMs read once by the caller. This is a decision, not a clock:
     *   nobody's suggestion should change under them while they look at it.
     * @return null only when there is no library at all — a class is always
     *   suggestable, including to a rider with no history (that is [FirstRide]).
     */
    fun suggest(
        library: List<SuggestableClass>,
        rides: RiderRides,
        nowMs: Long
    ): ClassSuggestion? {
        if (library.isEmpty()) return null

        // Nobody has ridden anything. Everything below reads the rider's own
        // history and there is none, so this branch is not a fallback — it is
        // the first-run case, and it is the case where *what should I ride* is
        // hardest to answer.
        if (rides.recent.isEmpty()) {
            val starter = library.filter { it.category == STARTER_CATEGORY }
                .ifEmpty { library }
                .minWithOrNull(compareBy({ it.durationSec }, { it.id }))
                ?: return null
            return starter.toSuggestion(ClassSuggestion.Reason.FirstRide)
        }

        val usualSec = usualDurationSec(rides.recent)
        val easyWanted = rodeHardRecently(library, rides.recent.first(), nowMs)

        // Filtering to the easy end can empty the pool — a library with no easy
        // class at all is not a state this library is in, but a filter that can
        // return nothing must say what it does then. Suggesting *something* is
        // better than suggesting nothing: the fallback loses the reason, not
        // the card.
        val pool = if (easyWanted) {
            library.filter { (it.hardestZone ?: 1) <= EASY_MAX_ZONE }.ifEmpty { library }
        } else {
            library
        }

        // The length the rider actually rides, snapped to a length the library
        // actually has. Ties (25 minutes, between a 20 and a 30) leave both in
        // the running rather than picking one, and the ranking below decides.
        val nearest = pool.minOf { abs(it.durationSec - usualSec) }
        val candidates = pool.filter { abs(it.durationSec - usualSec) == nearest }

        val ridden = rides.perClass.associateBy { it.classId }
        val perCategory = rides.perClass
            .mapNotNull { count -> library.firstOrNull { it.id == count.classId }?.category?.to(count.rides) }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, counts) -> counts.sum() }

        val choice = candidates.minWithOrNull(
            compareBy(
                // Least ridden first — breadth, and the whole reason the
                // suggestion is worth having on a 72-class library.
                { ridden[it.id]?.rides ?: 0 },
                // Then the category they have ridden least, so the card walks
                // around the library rather than down one corner of it.
                { perCategory[it.category] ?: 0 },
                // Then longest since. Never-ridden classes are all 0 here and
                // are already ahead on the first key.
                { ridden[it.id]?.lastRiddenAtMs ?: 0L },
                // And finally the id, so the answer is a function of the data
                // rather than of the order a query happened to return.
                { it.id }
            )
        ) ?: return null

        val reason = when {
            easyWanted -> ClassSuggestion.Reason.EasyAfterHard
            ridden[choice.id] == null -> ClassSuggestion.Reason.NewToYou
            else -> ClassSuggestion.Reason.NotSince(ridden.getValue(choice.id).lastRiddenAtMs)
        }
        return choice.toSuggestion(reason)
    }

    /**
     * A few more places to start, for the one rider the dashboard cannot fill
     * a screen for (PLAN 22.9.5).
     *
     * **The question it answers is *how long have you got*, and that is the
     * only question a rider with no history can answer about themselves.**
     * Everything else this file decides is read off the rider's own riding —
     * the length they usually ride, the classes they have ridden least, whether
     * yesterday was hard — and a rider at ride zero has none of it. What the app
     * does have at ride zero is 72 authored classes, so the honest way to fill
     * a first-run screen is with more of the library rather than with a card
     * about the rider (22.2.3, 22.9.1).
     *
     * **Spread across the range rather than the [count] shortest**, which is
     * the whole of why this is not two lines. Endurance classes sorted by
     * length and cut at three offers 15, 20 and 20 minutes — three answers to a
     * question nobody asked. Taking the shortest, the longest and the middle
     * makes the row a choice about *time*, which is the choice a first-timer is
     * actually making.
     *
     * One class per distinct length, because two 20-minute classes side by side
     * ask a rider with no history to pick on a distinction they cannot yet
     * make. And [exclude] is the class the primary card is already offering:
     * the same class named twice on one screen is the screen disagreeing with
     * itself about what to do next.
     *
     * Empty for anybody with a ride behind them — the caller decides that, and
     * the dashboard's rule is the same one that gates the honest-empty branch.
     * Deterministic for the same reason [suggest] is.
     */
    fun startingPoints(
        library: List<SuggestableClass>,
        exclude: String? = null,
        count: Int = STARTING_POINTS
    ): List<SuggestableClass> {
        if (count <= 0) return emptyList()

        val pool = library.filter { it.category == STARTER_CATEGORY }
            .ifEmpty { library }
            .filter { it.id != exclude }

        // One representative per length, chosen by id so the row is a function
        // of the library rather than of the order a query returned it in.
        val byLength = pool
            .groupBy { it.durationSec }
            .toSortedMap()
            .values
            .mapNotNull { classes -> classes.minByOrNull { it.id } }

        if (byLength.size <= count) return byLength
        if (count == 1) return listOf(byLength.first())

        // Shortest, longest, and evenly spaced between, rounded to the nearest
        // rather than truncated — with four lengths and a count of three,
        // truncation picks the second of four for the middle and rounding picks
        // the third, which is the one a rider would call the middle. `distinct`
        // rather than an assumption that the arithmetic never collides.
        val last = byLength.size - 1
        val steps = count - 1
        return (0..steps)
            .map { i -> (i * last + steps / 2) / steps }
            .distinct()
            .map { byLength[it] }
    }

    /**
     * The middle of the rider's recent ride lengths, taking the **lower** middle
     * of an even count.
     *
     * Lower rather than averaging the two: a rider whose time varies should be
     * offered the length they can definitely finish, and a suggestion that is
     * ten minutes too long is a suggestion that gets abandoned at 20 minutes.
     */
    private fun usualDurationSec(recent: List<RecentRide>): Int {
        val lengths = recent.take(USUAL_WINDOW).map { it.durationSec }.sorted()
        return lengths[(lengths.size - 1) / 2]
    }

    /**
     * Was the rider's last ride hard, and was it in the last day?
     *
     * Hardness is read off the class's own blocks. A **Just Ride tells us
     * nothing** — its intensity lives in `workout_metrics`, which this rule may
     * not read (22.1.8) and which would need measured power to mean anything
     * (`PowerProvenance`) — so it is treated as unknown rather than as easy. The
     * same goes for a ride of a class since retired, which is not in the
     * library any more. Unknown makes no claim, which is the right answer: the
     * card simply suggests the ordinary way instead of saying *"easy after a
     * hard one"* about a ride it cannot see.
     */
    private fun rodeHardRecently(
        library: List<SuggestableClass>,
        last: RecentRide,
        nowMs: Long
    ): Boolean {
        if (nowMs - last.atEpochMs > HARD_RECOVERY_MS) return false
        val classId = last.classId ?: return false
        val zone = library.firstOrNull { it.id == classId }?.hardestZone ?: return false
        return zone >= HARD_ZONE
    }

    private fun SuggestableClass.toSuggestion(reason: ClassSuggestion.Reason) =
        ClassSuggestion(
            classId = id,
            title = title,
            category = category,
            durationSec = durationSec,
            reason = reason
        )
}
