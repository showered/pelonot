package com.pelonot.data.repository

import com.pelonot.data.local.dao.UserDao
import com.pelonot.data.local.entity.UserEntity
import com.pelonot.data.remote.SupabaseSyncRepository
import kotlinx.coroutines.flow.Flow

/**
 * Rider profiles. Room is the source of truth; the cloud is a best-effort
 * mirror, so a sync failure never blocks a local change.
 */
class UserRepository(
    private val userDao: UserDao,
    private val syncRepository: SupabaseSyncRepository
) {

    val allUsers: Flow<List<UserEntity>> = userDao.getAllUsers()

    fun observeUser(userId: Int): Flow<UserEntity?> = userDao.getUserByIdFlow(userId)

    suspend fun getUser(userId: Int): UserEntity? = userDao.getUserById(userId)

    /** Inserts or updates a profile, returning it with its assigned id. */
    suspend fun save(user: UserEntity): UserEntity {
        val rowId = userDao.insertUser(user)
        // A new profile has localUserId 0 until Room autogenerates one.
        val saved = if (user.localUserId == 0) user.copy(localUserId = rowId.toInt()) else user
        syncRepository.syncProfile(saved)
        return saved
    }

    suspend fun updateFtp(userId: Int, ftpWatts: Int) {
        val user = userDao.getUserById(userId) ?: return
        save(user.copy(ftpWatts = ftpWatts))
    }

    suspend fun updateWeight(userId: Int, weightKg: Double) {
        val user = userDao.getUserById(userId) ?: return
        save(user.copy(weightKg = weightKg))
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
