package com.pelonot.data.repository

import com.pelonot.data.local.dao.RiderAchievementDao
import com.pelonot.data.local.dao.WorkoutDao
import com.pelonot.data.local.entity.RiderAchievementEntity
import com.pelonot.domain.achievements.AchievementCatalogue
import com.pelonot.domain.achievements.AchievementEvidence

/** Awards the offline volume collection once a ride has become a completed fact. */
class AchievementRepository(
    private val achievementDao: RiderAchievementDao,
    private val workoutDao: WorkoutDao,
    private val now: () -> Long = { System.currentTimeMillis() }
) {
    /**
     * A guest has no collection: achievements belong to an identified rider,
     * not whichever person happened to be using the tablet at the time.
     */
    suspend fun awardFor(workoutId: String) {
        val workout = workoutDao.getWorkoutById(workoutId) ?: return
        val userId = workout.userId ?: return
        if (!workout.isComplete) return

        val totals = workoutDao.achievementTotalsFor(userId)
        val alreadyEarned = achievementDao.idsFor(userId).toSet()
        val newlyEarned = AchievementCatalogue.earnedBy(
            AchievementEvidence(totals.rides, totals.durationSec)
        ).filterNot { it.id in alreadyEarned }
        if (newlyEarned.isEmpty()) return

        val at = now()
        achievementDao.insertAll(newlyEarned.map { definition ->
            RiderAchievementEntity(
                userId = userId,
                workoutId = workoutId,
                achievementId = definition.id,
                earnedAt = at
            )
        })
    }
}
