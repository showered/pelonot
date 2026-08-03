package com.pelonot.data.repository

import android.util.Log
import com.pelonot.data.local.dao.UserDao
import com.pelonot.data.local.dao.WorkoutDao
import com.pelonot.data.remote.AuthRepository
import com.pelonot.domain.cloud.AccountState
import com.pelonot.domain.cloud.AuthAttempt
import kotlinx.coroutines.flow.Flow

/**
 * Signing in **on behalf of a profile** (PLAN 15.2).
 *
 * [AuthRepository] knows how to get a session; this knows what a session means
 * to this tablet. They are separate because on a household bike they answer
 * different questions — *"is anyone signed in here?"* is about the device, and
 * *"whose rides are backed up?"* is about a profile — and the whole of 15.2 is
 * the consequence of not collapsing them.
 *
 * Nothing here imports the Supabase SDK, which is a rule
 * (`CloudAccessFenceTest`) and also the reason this class is testable at all.
 */
class AccountRepository(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val userDao: UserDao,
    private val workoutDao: WorkoutDao
) {

    val accountState: Flow<AccountState> = authRepository.accountState

    val cloudConfigured: Boolean get() = authRepository.cloudConfigured

    /**
     * Signs in and attaches the account to [localUserId].
     *
     * The two steps are one action from the rider's point of view and must not
     * come apart: a session with no attached profile is a tablet that is signed
     * in to a cloud nothing will ever be sent to, which is indistinguishable
     * from being signed out except that it looks like it worked.
     */
    suspend fun signIn(localUserId: Int, email: String, password: String): AuthAttempt =
        attachAfter(localUserId) { authRepository.signIn(email, password) }

    /**
     * Creates an account and attaches it, when the project hands back a session.
     *
     * When it does not — `mailer_autoconfirm` is off here, so it usually does
     * not — the result is [AuthAttempt.ConfirmationRequired] and **nothing is
     * attached**, because there is nothing to attach to yet. The rider comes
     * back and signs in, and that path does the attaching.
     */
    suspend fun signUp(localUserId: Int, email: String, password: String): AuthAttempt =
        attachAfter(localUserId) { authRepository.signUp(email, password) }

    /**
     * Adopts a session this tablet was handed rather than one it typed —
     * the QR flow's landing point (15.6).
     *
     * It is the same attach with a different way in, which is the point: if
     * pairing had its own copy of these rules, one of the two would drift.
     */
    suspend fun adoptSession(localUserId: Int, attempt: AuthAttempt): AuthAttempt =
        attach(localUserId, attempt)

    private suspend inline fun attachAfter(
        localUserId: Int,
        block: () -> AuthAttempt
    ): AuthAttempt = attach(localUserId, block())

    private suspend fun attach(localUserId: Int, attempt: AuthAttempt): AuthAttempt {
        val success = attempt as? AuthAttempt.Success ?: return attempt

        // 15.2.7. One account, one local profile. Two profiles pointing at one
        // account is not a household — it is one rider's history split in half,
        // with both halves upserting the same cloud row and their rides pooling
        // under one owner. It is a plausible mistake on a shared bike (sign in
        // while the wrong profile is selected), so it is checked rather than
        // trusted, and checked *before* anything is written.
        val alreadyHeldBy = userDao.getUserByAuthId(success.accountId)
        if (alreadyHeldBy != null && alreadyHeldBy.localUserId != localUserId) {
            authRepository.signOut()
            return AuthAttempt.Failed(
                "That account is already backing up ${alreadyHeldBy.name} on this bike"
            )
        }

        val user = userDao.getUserById(localUserId)
            ?: return AuthAttempt.Failed("That profile is no longer on this bike")

        userRepository.save(user.copy(authUserId = success.accountId))

        // 15.3.1. Attaching an account is the moment the whole history becomes
        // a backlog. See `WorkoutDao.clearSyncedFor` for why this is
        // unconditional rather than only-when-the-account-changed.
        workoutDao.clearSyncedFor(localUserId)

        Log.i(TAG, "Profile $localUserId is now backing up; its history is queued")
        return success
    }

    /**
     * Signs out and detaches, keeping every local ride (15.4.1).
     *
     * The rider drops a rung down the identity ladder — from Account to Local
     * profile — rather than out of the app. Their history, their dashboard,
     * their trends and their place on the household leaderboard are all
     * untouched, because none of those ever needed the cloud.
     */
    suspend fun signOut(localUserId: Int): AuthAttempt {
        val outcome = authRepository.signOut()
        val user = userDao.getUserById(localUserId)
        if (user?.authUserId != null) {
            userRepository.save(user.copy(authUserId = null))
        }
        return outcome
    }

    /**
     * The profile on this tablet that the current session belongs to, if any.
     *
     * Settings uses it to answer the question a shared bike actually raises:
     * *this tablet is signed in, but is it signed in as **you**?*
     */
    suspend fun profileHoldingSession(): Int? {
        val accountId = authRepository.settledAccountId() ?: return null
        return userDao.getUserByAuthId(accountId)?.localUserId
    }

    private companion object {
        const val TAG = "PelonotAccount"
    }
}
