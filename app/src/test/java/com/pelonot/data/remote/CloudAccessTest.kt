package com.pelonot.data.remote

import com.pelonot.data.local.dao.UserDao
import com.pelonot.data.local.entity.UserEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the gate says, and to whom (PLAN 23.1.1).
 *
 * Rule 1 of the connectivity model in one sentence: a rider with no account
 * makes no request to Supabase. Everything here is that sentence, asked from
 * each of the states a real tablet can be in.
 */
class CloudAccessTest {

    private val offlineRider = UserEntity(localUserId = 1, name = "Local only")
    private val signedInRider = UserEntity(
        localUserId = 2,
        name = "Has an account",
        authUserId = "auth-uuid-0001"
    )

    private fun gate(
        vararg users: UserEntity,
        credentials: Boolean = true,
        backup: Boolean = true
    ) = CloudAccess(
        userDao = FakeUserDao(users.toList()),
        backupPreference = { backup },
        credentialsPresent = { credentials }
    )

    @Test
    fun `a profile with no account is refused`() = runBlocking {
        assertFalse(gate(offlineRider).isAllowedFor(offlineRider.localUserId))
    }

    @Test
    fun `a profile with an account is allowed`() = runBlocking {
        assertTrue(gate(signedInRider).isAllowedFor(signedInRider.localUserId))
    }

    /**
     * A guest has no owner, so there is nobody whose account could permit the
     * call. `workouts.user_id` is also null for a ride whose profile was later
     * deleted, and the same answer is right for both.
     */
    @Test
    fun `a guest ride is refused`() = runBlocking {
        assertFalse(gate(signedInRider).isAllowedFor(null))
    }

    /**
     * The consequence that had to be got right: one signed-in rider on the
     * tablet does not put their housemate's rides in the cloud (15.2.4).
     */
    @Test
    fun `an account on one profile grants nothing to another`() = runBlocking {
        val household = gate(offlineRider, signedInRider)
        assertTrue(household.isAllowedFor(signedInRider.localUserId))
        assertFalse(household.isAllowedFor(offlineRider.localUserId))
    }

    @Test
    fun `a profile that no longer exists is refused`() = runBlocking {
        assertFalse(gate(signedInRider).isAllowedFor(404))
    }

    /**
     * 23.1.6: a build with no credentials and a rider with no account are two
     * different reasons to be offline and must produce one behaviour.
     */
    @Test
    fun `a build with no credentials refuses even a signed-in rider`() = runBlocking {
        assertFalse(
            gate(signedInRider, credentials = false).isAllowedFor(signedInRider.localUserId)
        )
    }

    @Test
    fun `a signed-in rider who turned backup off is refused`() = runBlocking {
        assertFalse(gate(signedInRider, backup = false).isAllowedFor(signedInRider.localUserId))
    }

    @Test
    fun `nobody signed in is the default state of a tablet`() = runBlocking {
        assertFalse(gate(offlineRider, offlineRider.copy(localUserId = 3)).anyProfileHasAccount())
        assertTrue(gate(offlineRider, signedInRider).anyProfileHasAccount())
    }

    private class FakeUserDao(private val users: List<UserEntity>) : UserDao {
        override suspend fun insertUser(user: UserEntity): Long = 0
        override suspend fun updateUser(user: UserEntity) = Unit
        override suspend fun getUserById(userId: Int): UserEntity? =
            users.firstOrNull { it.localUserId == userId }

        override fun getUserByIdFlow(userId: Int): Flow<UserEntity?> =
            flowOf(users.firstOrNull { it.localUserId == userId })

        override fun getAllUsers(): Flow<List<UserEntity>> = flowOf(users)
        override suspend fun getUserCount(): Int = users.size
        override suspend fun getAccountProfileCount(): Int = users.count { it.hasAccount }
        override suspend fun deleteUser(userId: Int) = Unit
    }
}
