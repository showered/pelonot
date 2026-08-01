package com.pelonot.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.pelonot.data.local.entity.UserEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    /**
     * **`@Upsert`, never `@Insert(onConflict = REPLACE)`.**
     *
     * SQLite implements REPLACE as a delete followed by an insert, and the
     * delete fires foreign-key actions. `workouts.user_id` is `ON DELETE SET
     * NULL`, so re-inserting an existing profile detached **every ride that
     * rider had ever done** — and this is the path every FTP change, weight
     * change and rename goes through. It was doing it silently, and the rides
     * survived as unattributed rows, so nothing looked broken until somebody
     * noticed a dashboard that had gone empty.
     *
     * Measured on the tablet AVD: one settings toggle, seven rides orphaned.
     * `UserDaoTest` holds the line. Same defect as the one `ClassTemplateDao`
     * carried (PLAN 23.2.6c).
     */
    @Upsert
    suspend fun insertUser(user: UserEntity): Long

    @Update
    suspend fun updateUser(user: UserEntity)

    @Query("SELECT * FROM profiles WHERE local_user_id = :userId")
    suspend fun getUserById(userId: Int): UserEntity?

    @Query("SELECT * FROM profiles WHERE local_user_id = :userId")
    fun getUserByIdFlow(userId: Int): Flow<UserEntity?>

    @Query("SELECT * FROM profiles ORDER BY created_at ASC")
    fun getAllUsers(): Flow<List<UserEntity>>

    @Query("SELECT COUNT(*) FROM profiles")
    suspend fun getUserCount(): Int

    /** How many riders on this tablet are signed in — see `CloudAccess`. */
    @Query("SELECT COUNT(*) FROM profiles WHERE auth_user_id IS NOT NULL")
    suspend fun getAccountProfileCount(): Int

    @Query("DELETE FROM profiles WHERE local_user_id = :userId")
    suspend fun deleteUser(userId: Int)
}