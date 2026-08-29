package com.pelonot.data.repository

import com.pelonot.data.local.dao.ClassTemplateDao
import com.pelonot.data.local.dao.RiderAlertDao
import com.pelonot.data.local.dao.WorkoutDao
import com.pelonot.data.local.dao.WorkoutPowerBestDao
import com.pelonot.data.local.entity.RiderAlertEntity
import com.pelonot.domain.alerts.AlertEvidence
import com.pelonot.domain.alerts.AlertKind
import com.pelonot.domain.alerts.AlertRules
import com.pelonot.domain.alerts.RiderAlert
import com.pelonot.domain.model.PowerProvenance
import com.pelonot.domain.social.StreakCalculator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Judges a finished ride once, and remembers what it found (PLAN 27.1.1).
 *
 * **One place, called from the finalise**, which is the same argument
 * `recordPowerFacts` makes one method along: there are three ways a ride is
 * finalised — the service's `stopWorkout`, the crash recovery's, and the second
 * finalise of a ride resumed under 12.6.2 — and a ride that missed this would
 * never be judged at all, because nothing else in the app ever looks at a ride
 * again.
 *
 * **The switch is honoured here and not at the screens** (27.4.2). A rider who
 * has turned this off is not asking for a quieter version of being graded, so
 * nothing is detected and nothing is written; the cost is that turning it back
 * on starts from that moment rather than back-filling, and that is the honest
 * side of the trade — the records themselves are computed from `workouts` and
 * `workout_power_bests` either way, so nothing about them is *wrong* afterwards,
 * there is simply no history of having been told.
 *
 * **A guest ride is judged against nobody and therefore not at all.** Its rides
 * are filed against no profile, which is the same fact `AppUiState.levelFor`
 * refuses to draw a level from: a record is a claim about a person, and a guest
 * ride has not named one.
 */
class AlertRepository(
    private val alertDao: RiderAlertDao,
    private val workoutDao: WorkoutDao,
    private val powerBestDao: WorkoutPowerBestDao,
    private val classTemplateDao: ClassTemplateDao,
    private val alertsEnabled: suspend () -> Boolean,
    private val now: () -> Long = { System.currentTimeMillis() }
) {

    /**
     * Works out what one finished ride earned and writes it down.
     *
     * Returns what was written, ranked, or an empty list — which is the
     * ordinary outcome and the feature depends on it being so.
     *
     * Deliberately tolerant of being called twice on one ride: the unique index
     * on `rider_alerts` drops the repeat, and [RiderAlertDao.countForWorkout]
     * saves the six queries in the ordinary case where a resume re-finalises a
     * ride that was already judged.
     */
    suspend fun judge(workoutId: String): List<RiderAlert> {
        if (!alertsEnabled()) return emptyList()

        val workout = workoutDao.getWorkoutById(workoutId) ?: return emptyList()
        if (!workout.isComplete) return emptyList()
        val userId = workout.userId ?: return emptyList()
        if (alertDao.countForWorkout(workoutId) > 0) return emptyList()

        val history = workoutDao.historyBefore(userId, workoutId)
        val classHistory = workout.classId?.let {
            workoutDao.classHistoryBefore(userId, it, workoutId)
        }
        val timestampsBefore = workoutDao.rideTimestampsExcluding(userId, workoutId)
        val at = now()

        val evidence = AlertEvidence(
            workoutId = workoutId,
            classId = workout.classId,
            classTitle = workout.classId?.let { classTemplateDao.getTemplateById(it)?.title },
            durationSec = workout.durationSec,
            totalOutputKj = workout.totalOutputKj,
            powerIsMeasured =
                (workout.powerProvenance ?: PowerProvenance.Unknown).isTrustworthyAsMeasured,
            ridesBests = powerBestDao.bestsOf(workoutId).associate { it.windowSec to it.watts },
            previousBests = powerBestDao.bestsBefore(userId, workoutId)
                .associate { it.windowSec to it.watts },
            priorMeasuredRides = history.measuredRides,
            priorRides = history.rides,
            priorClassRides = classHistory?.rides ?: 0,
            bestClassOutputKj = classHistory?.bestOutputKj,
            bestOutputKj = history.bestOutputKj,
            longestRideSec = history.longestSec,
            // The ride's own timestamp goes on the front rather than the list
            // being re-read: the row is already on disk, so re-querying would
            // return the same set plus one and cost a query to say so.
            weeklyStreak = StreakCalculator.currentWeeklyStreak(
                rideTimestamps = timestampsBefore + workout.timestamp,
                now = at
            ),
            previousWeeklyStreak = StreakCalculator.currentWeeklyStreak(
                rideTimestamps = timestampsBefore,
                now = at
            )
        )

        val alerts = AlertRules.detect(evidence)
        if (alerts.isEmpty()) return emptyList()

        alertDao.insertAll(
            alerts.map { alert ->
                RiderAlertEntity(
                    userId = userId,
                    workoutId = workoutId,
                    kind = alert.kind.name,
                    subjectKey = alert.subjectKey,
                    subjectTitle = alert.subjectTitle,
                    value = alert.value,
                    previousValue = alert.previousValue,
                    recordedAt = at
                )
            }
        )
        return alerts
    }

    /**
     * Un-judges a ride that turned out not to be over (12.6.2).
     *
     * **The one place an alert is deleted, and it is deliberately allowed to
     * take back something the rider has already been told.** A ride ended by
     * accident has been judged and its headline shown; carrying on makes it a
     * longer ride, whose twenty-minute effort can only be as good or better, so
     * the second finalise says the same thing or a bigger one. The rider is
     * therefore told **once about one ride**, which is 27.1.5, rather than told
     * about the first half and never about the whole.
     *
     * It is not 28.1.1's "never revoked" in miniature. That rule is about a
     * ride the rider actually finished; this is about a ride that has not
     * finished yet, and it is the same clause the resume already applies to
     * `synced_at`, `power_bests_at` and the stored efforts.
     */
    suspend fun clearFor(workoutId: String) = alertDao.clearFor(workoutId)

    /**
     * The one line a rider is shown after a ride, and the act of showing it
     * (27.1.5, 27.3.1).
     *
     * The mark is written here rather than by the screen so that "shown" means
     * the same thing everywhere — the summary can be reopened from history, and
     * an alert marked seen twice is still one alert seen.
     */
    suspend fun headlineFor(workoutId: String): StoredAlert? {
        val stored = alertDao.forWorkout(workoutId).firstOrNull()?.toStored() ?: return null
        alertDao.markSeen(stored.id, now())
        return stored
    }

    /** Everything this rider has ever earned, newest first (27.4.1). */
    fun observeFor(userId: Int): Flow<List<StoredAlert>> =
        alertDao.observeFor(userId).map { rows -> rows.mapNotNull { it.toStored() } }

    /** How many they have never been shown, for the dashboard's card. */
    fun observeUnseenCount(userId: Int): Flow<Int> = alertDao.observeUnseenCount(userId)

    /**
     * A row that is not one of [AlertKind]'s is skipped rather than crashing.
     *
     * The case is a rider moving *backwards* through versions — 12.5's
     * downgrade path is the one place this app admits an older APK over a newer
     * one — and a congratulation from the future is the cheapest possible thing
     * to lose.
     */
    private fun RiderAlertEntity.toStored(): StoredAlert? {
        val parsed = AlertKind.entries.firstOrNull { it.name == kind } ?: return null
        return StoredAlert(
            id = id,
            workoutId = workoutId,
            recordedAt = recordedAt,
            wasSeen = seenAt != null,
            alert = RiderAlert(
                kind = parsed,
                subjectKey = subjectKey,
                value = value,
                previousValue = previousValue,
                subjectTitle = subjectTitle
            )
        )
    }
}

/** One row of [AlertRepository]'s ledger, as the screens read it. */
data class StoredAlert(
    val id: Long,
    val workoutId: String?,
    val recordedAt: Long,
    val wasSeen: Boolean,
    val alert: RiderAlert
)
