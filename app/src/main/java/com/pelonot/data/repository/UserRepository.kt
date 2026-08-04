package com.pelonot.data.repository

import androidx.room.withTransaction
import com.pelonot.data.local.AppDatabase
import com.pelonot.data.local.dao.FtpHistoryDao
import com.pelonot.data.local.dao.UserDao
import com.pelonot.data.local.entity.FtpChangeSource
import com.pelonot.data.local.entity.FtpHistoryEntity
import com.pelonot.data.local.entity.UserEntity
import com.pelonot.data.remote.SupabaseSyncRepository
import kotlinx.coroutines.flow.Flow

/**
 * Rider profiles. Room is the source of truth; the cloud is a best-effort
 * mirror, so a sync failure never blocks a local change.
 */
class UserRepository(
    private val database: AppDatabase,
    private val userDao: UserDao,
    private val ftpHistoryDao: FtpHistoryDao,
    private val syncRepository: SupabaseSyncRepository,
    private val clock: () -> Long = System::currentTimeMillis
) {

    val allUsers: Flow<List<UserEntity>> = userDao.getAllUsers()

    /** A rider's FTP over time, oldest first (7.9). */
    fun observeFtpHistory(userId: Int): Flow<List<FtpHistoryEntity>> =
        ftpHistoryDao.observeForUser(userId)

    suspend fun ftpHistory(userId: Int): List<FtpHistoryEntity> = ftpHistoryDao.forUser(userId)

    fun observeUser(userId: Int): Flow<UserEntity?> = userDao.getUserByIdFlow(userId)

    suspend fun getUser(userId: Int): UserEntity? = userDao.getUserById(userId)

    /**
     * Inserts or updates a profile, returning it with its assigned id.
     *
     * **This is the one funnel (7.9.4).** Every path that changes FTP ends up
     * here — Settings, the auto-breakthrough dialog, profile creation, a guest
     * keeping their ride, and whatever 15 and 19.2.3 add next — so the history
     * row is written *here*, in the same transaction as the profile, rather
     * than at each call site. A history that depends on every new path
     * remembering to append to it is a history that will be wrong within two
     * features, and it will be wrong silently.
     *
     * The consequence worth stating: a caller that changes FTP without naming
     * a [ftpSource] still gets a row, marked [FtpChangeSource.Unknown]. Losing
     * the reason is survivable; losing the change is not.
     */
    suspend fun save(
        user: UserEntity,
        ftpSource: FtpChangeSource = FtpChangeSource.Unknown,
        ftpWorkoutId: String? = null
    ): UserEntity {
        val previous = if (user.localUserId == 0) null else userDao.getUserById(user.localUserId)

        val saved = database.withTransaction {
            val rowId = userDao.insertUser(user)
            // A new profile has localUserId 0 until Room autogenerates one.
            val stored =
                if (user.localUserId == 0) user.copy(localUserId = rowId.toInt()) else user

            // 7.9.5. A change to the same value is not a change. Without this,
            // a re-save in Settings, a rename, or an idempotent pull from
            // another device fills the trend chart with vertical noise at the
            // same height.
            if (previous?.ftpWatts != stored.ftpWatts) {
                ftpHistoryDao.insert(
                    FtpHistoryEntity(
                        localUserId = stored.localUserId,
                        ftpWatts = stored.ftpWatts,
                        changedAt = clock(),
                        source = (if (previous == null) creationSource(ftpSource)
                        else ftpSource).name,
                        workoutId = ftpWorkoutId
                    )
                )
            }
            stored
        }

        // Outside the transaction: the cloud is a best-effort mirror and a
        // network call has no business holding a database lock.
        syncRepository.syncProfile(saved)
        return saved
    }

    /**
     * Which source a **brand-new** profile's first FTP is filed under (20.3.4).
     *
     * This used to be `ProfileCreated` unconditionally, and the comment said
     * *"whatever the caller said about it"* — which was correct for as long as
     * typing a number into a text box was the only way a profile could acquire
     * one. Since 20.3 it is not: most riders never see a watt at signup and the
     * app estimates one from their weight, age and their own description of
     * their riding.
     *
     * Those are different facts and 20.3.4 requires the trend to tell them
     * apart, so the caller's word is honoured **when it is one of the two
     * things a creation can actually be**. Anything else — including the
     * `Unknown` a caller gets for saying nothing — falls back to
     * `ProfileCreated`, which keeps the funnel's own guarantee intact: a path
     * that forgets to name a source still produces a truthful row rather than
     * an inherited one from some other feature.
     */
    private fun creationSource(requested: FtpChangeSource): FtpChangeSource =
        if (requested == FtpChangeSource.Estimated) requested
        else FtpChangeSource.ProfileCreated

    /**
     * @param source why it moved (7.9.2) — the distinction the trend chart
     *   draws, because an FTP the rider typed is a claim and one the app
     *   measured is evidence.
     * @param workoutId the ride that caused it, where there was one.
     */
    suspend fun updateFtp(
        userId: Int,
        ftpWatts: Int,
        source: FtpChangeSource = FtpChangeSource.Unknown,
        workoutId: String? = null
    ) {
        val user = userDao.getUserById(userId) ?: return
        save(user.copy(ftpWatts = ftpWatts), ftpSource = source, ftpWorkoutId = workoutId)
    }

    suspend fun updateWeight(userId: Int, weightKg: Double) {
        val user = userDao.getUserById(userId) ?: return
        save(user.copy(weightKg = weightKg))
    }

    /**
     * Whether this rider appears on the screens the rest of the house sees
     * (PLAN 24.2.3).
     *
     * One switch, one meaning: it gates the per-class leaderboard and the
     * dashboard's week by the same column, because a rider who does not want to
     * be seen has not asked to be seen on half of it. It takes nothing away
     * from them — their own history, dashboard and trends are untouched.
     */
    suspend fun setHouseholdVisible(userId: Int, visible: Boolean) {
        val user = userDao.getUserById(userId) ?: return
        save(user.copy(householdVisible = visible))
    }

    /**
     * Renames a profile (20.1.5).
     *
     * The one field Settings cannot change — it edits FTP and weight — and the
     * one most likely to have been typed in a hurry on a bike.
     */
    suspend fun updateName(userId: Int, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val user = userDao.getUserById(userId) ?: return
        save(user.copy(name = trimmed))
    }

    /**
     * Removes a profile. **Their rides survive**: `workouts.user_id` is
     * `ON DELETE SET NULL`, so the history becomes unattributed rather than
     * being destroyed. Whatever calls this has to say so.
     */
    suspend fun delete(userId: Int) = userDao.deleteUser(userId)

    suspend fun count(): Int = userDao.getUserCount()
}
