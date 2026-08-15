package com.pelonot.domain.social

import com.pelonot.domain.progress.RiderLevel
import java.util.Calendar
import java.util.TimeZone

/**
 * One rider's recent riding, for the dashboard's household panel (24.2).
 *
 * **The window is the last 30 days and it used to be a week** (22.5.4). At the
 * cadence the owner stated — at most one ride each per week — a household of
 * three showed an empty board six days out of seven, so the panel whose entire
 * job is *other people are riding too* spent most of its life saying nobody
 * was.
 *
 * Note what is *not* here: anybody who has not ridden. That is 24.2.4 made
 * structural rather than remembered — "Sam hasn't ridden this week" is a
 * feature that starts arguments, and the safest way not to ship it is to have
 * no row that could carry it. A rider with no rides in the window is absent
 * from the list, not present with a zero.
 */
data class HouseholdRider(
    val localUserId: Int,
    val name: String,
    val rides: Int,
    val outputKj: Double,
    val lastRideAt: Long,
    /**
     * Consecutive **weeks** with a ride in them, or 0 (22.5.2, 22.5.4).
     *
     * Weeks rather than days for the reason the whole window changed: at one
     * ride a week a perfect year of Sundays is a day-streak of 1, which is not
     * worth showing and is not what the rider did.
     */
    val streakWeeks: Int,
    /**
     * Their riding level (26.4), which is the one figure on this row that is
     * **not** about the last 30 days.
     *
     * That mixture is the point rather than an inconsistency: the rest of the
     * row is what somebody has been doing lately and the level is who they are,
     * so a housemate who has been ill for a month is still recognisably
     * themselves on the card. It is deliberately not defaulted — a signal that
     * is optional at the call site is a signal nobody notices is missing, which
     * is the general form of the defect 7.11.6 found.
     */
    val level: RiderLevel
)

/**
 * The rows the dashboard's household panel actually draws (24.1.8, applied to
 * the panel rather than the board).
 *
 * 24.1.8 capped the *class* leaderboard and stopped there, and the note's own
 * argument carries straight over to this panel: the row count is not "how many
 * people live here". Every profile on the tablet that has ridden in the last
 * thirty days gets a row, so the panel's height is a fact about how much the
 * app is used — which is exactly the thing the owner said "has the potential to
 * really throw the screen out of alignment when it grows long". Twelve rows was
 * observed, and there was no ceiling anywhere between the query and the screen.
 *
 * **The window is not the leaderboard's, because this is not a ranking.**
 * `ClassLeaderboard.visible` keeps a podium because somebody won; the panel is
 * ordered by how much a person has ridden and explicitly refuses to rank it
 * (see `HouseholdPanelCard`), so there is no podium to protect. What it keeps
 * is the top of the list — the housemates who have actually been on the bike —
 * and **always the rider's own row**, because *am I on it* is the one question
 * they came to this card with.
 *
 * @property rows in the query's order, most active first.
 * @property hidden how many riders are not drawn at all — carried out rather
 *   than left to the caller, since a list that quietly stops at six is a false
 *   claim about the size of the household.
 * @property breakAfter index within [rows] after which the list skips riders,
 *   or null when what is drawn is contiguous.
 */
data class HouseholdPanel(
    val rows: List<HouseholdRider>,
    val hidden: Int,
    val breakAfter: Int?
) {
    companion object {
        /**
         * Above this many riders the panel is windowed rather than listed.
         *
         * Six is `ClassLeaderboard.MAX_ROWS`, deliberately: the two cards sit
         * on the same dashboard and a household that windows one at six and the
         * other at nine would look like a bug in whichever is shorter.
         */
        const val MAX_ROWS = 6

        fun of(riders: List<HouseholdRider>, youId: Int?): HouseholdPanel {
            if (riders.size <= MAX_ROWS) {
                return HouseholdPanel(riders, hidden = 0, breakAfter = null)
            }

            val youIndex = riders.indexOfFirst { it.localUserId == youId }
            // A rider already inside the window needs no special case, and a
            // guest (no profile) has no row to keep.
            val keep = if (youIndex < 0 || youIndex < MAX_ROWS) {
                (0 until MAX_ROWS).toList()
            } else {
                (0 until MAX_ROWS - 1) + youIndex
            }

            return HouseholdPanel(
                rows = keep.map { riders[it] },
                hidden = riders.size - keep.size,
                breakAfter = keep.zipWithNext()
                    .indexOfFirst { (above, below) -> below - above > 1 }
                    .takeIf { it >= 0 }
            )
        }
    }
}

/**
 * How many days in a row a rider has ridden.
 *
 * Pure and JVM-tested, with the clock and the timezone injected, for the same
 * reason `RideDayGrouping` is: day boundaries are the piece of date arithmetic
 * that is easy to get subtly wrong and impossible to notice, because an
 * off-by-one only shows around midnight, in a timezone the author does not live
 * in, or on the two days a year the offset changes.
 *
 * `java.util.Calendar` rather than `java.time` deliberately — `minSdk` is 24
 * and the project does not enable core library desugaring.
 */
object StreakCalculator {

    /**
     * The rider's current run of consecutive riding days.
     *
     * **A streak that ended yesterday still counts today**, and this is the
     * only real decision in here. Someone who rode six days running and has not
     * yet ridden today has a streak of six, not zero: telling them it is over
     * before the day is out is both wrong and the sort of thing that makes a
     * person give up. It ends when a whole day passes with no ride in it.
     */
    fun currentStreak(
        rideTimestamps: List<Long>,
        now: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): Int {
        if (rideTimestamps.isEmpty()) return 0

        val days = rideTimestamps.map { startOfDay(it, timeZone) }.toSortedSet().toList().reversed()
        val today = startOfDay(now, timeZone)
        val yesterday = previousDay(today, timeZone)

        var expected = when (days.first()) {
            today -> today
            yesterday -> yesterday
            // The most recent ride is older than yesterday: nothing is running.
            else -> return 0
        }

        var streak = 0
        for (day in days) {
            if (day != expected) break
            streak++
            expected = previousDay(expected, timeZone)
        }
        return streak
    }

    /**
     * The rider's current run of consecutive **weeks** with a ride in them
     * (PLAN 22.5.2).
     *
     * The unit matters more than the arithmetic. [currentStreak] counts days,
     * and on the owner's own stated assumption — a rider uses the bike at most
     * once a week — a perfect year of Sundays is a day-streak of **1**, which
     * the dashboard does not even show, on the grounds that calling a single
     * ride a streak is flattery. So the feature built to reward consistency was
     * blind to the most consistent rider it can have. Counted in weeks it reads
     * "7 weeks in a row", which is both true and worth keeping up.
     *
     * **A streak that ended last week still counts this week**, for exactly the
     * reason the daily one gives a rider until the end of today: somebody who
     * rode last Sunday and has not yet ridden this week has not stopped, and
     * telling them they have is how a person gives up. It ends when a whole
     * week passes with no ride in it.
     */
    fun currentWeeklyStreak(
        rideTimestamps: List<Long>,
        now: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): Int {
        if (rideTimestamps.isEmpty()) return 0

        val weeks = rideTimestamps.map { startOfWeek(it, timeZone) }
            .toSortedSet().toList().reversed()
        val thisWeek = startOfWeek(now, timeZone)
        val lastWeek = previousWeek(thisWeek, timeZone)

        var expected = when (weeks.first()) {
            thisWeek -> thisWeek
            lastWeek -> lastWeek
            else -> return 0
        }

        var streak = 0
        for (week in weeks) {
            if (week != expected) break
            streak++
            expected = previousWeek(expected, timeZone)
        }
        return streak
    }

    private fun startOfDay(epochMs: Long, timeZone: TimeZone): Long =
        Calendar.getInstance(timeZone).apply {
            timeInMillis = epochMs
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    /** The locale's first day of the week, so the boundary matches the calendar. */
    private fun startOfWeek(epochMs: Long, timeZone: TimeZone): Long {
        val calendar = Calendar.getInstance(timeZone).apply { timeInMillis = epochMs }
        val first = calendar.firstDayOfWeek
        val offset = (calendar.get(Calendar.DAY_OF_WEEK) - first + DAYS_IN_WEEK) % DAYS_IN_WEEK
        return startOfDay(calendar.timeInMillis, timeZone).let { startOfDayMs ->
            // Back one day at a time rather than by `offset × 86_400_000`, for
            // the same daylight-saving reason as `previousDay`.
            (0 until offset).fold(startOfDayMs) { day, _ -> previousDay(day, timeZone) }
        }
    }

    private fun previousWeek(startOfWeekMs: Long, timeZone: TimeZone): Long =
        (0 until DAYS_IN_WEEK).fold(startOfWeekMs) { day, _ -> previousDay(day, timeZone) }

    private const val DAYS_IN_WEEK = 7

    /**
     * Not `day - 86_400_000`: a day is 23 or 25 hours long twice a year in most
     * of the world, and subtracting a fixed 24 hours lands an hour inside the
     * wrong day on those two dates — which would break a streak that was never
     * broken, on a date nobody would think to test.
     */
    private fun previousDay(startOfDayMs: Long, timeZone: TimeZone): Long =
        startOfDay(startOfDayMs - HALF_A_DAY_MS, timeZone)

    private const val HALF_A_DAY_MS = 12L * 60 * 60 * 1000
}
