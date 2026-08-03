package com.pelonot.data.remote

import android.util.Log
import com.pelonot.domain.cloud.AccountState
import com.pelonot.domain.cloud.AuthAttempt
import com.pelonot.domain.cloud.CredentialCheck
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.SignOutScope
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * Signing in, signing up and signing out (PLAN 15.1).
 *
 * **This is the one cloud call that happens before there is an account**, and
 * that is not a hole in rule 1 of the connectivity model — it is the moment the
 * rule is satisfied. Rule 2 says *signing in is the consent*, so the request
 * that creates the consent cannot itself be gated on already having it. What
 * keeps the rule true is that nothing here can be reached except by a rider
 * pressing a button that says what it does: there is no automatic sign-in, no
 * retry loop, no background refresh of an account nobody has (the SDK's own
 * refresh only runs once a session exists), and an app whose rider never opens
 * this screen makes no request at all.
 *
 * It lives in `data/remote` because that is where the Supabase SDK is allowed
 * to be imported (`CloudAccessFenceTest`), and it returns the app's own types
 * rather than the SDK's so that the rest of the app cannot come to depend on
 * them. `AuthAttempt` and `AccountState` are in `domain/`, pure, and testable
 * without a network.
 *
 * **What it deliberately does not do is touch the database.** Attaching an
 * account to a local profile is a separate step with its own rules (15.2), and
 * fusing the two here would make "signed in on this tablet" and "this profile
 * has an account" the same fact — which on a shared household bike they are
 * emphatically not.
 */
class AuthRepository(
    private val credentialsPresent: () -> Boolean = { SupabaseModule.isConfigured }
) {

    private val auth: Auth? get() = SupabaseModule.client?.auth

    /**
     * The session this tablet is holding, as it changes.
     *
     * [AccountState.Unknown] until the SDK has finished loading whatever was
     * stored, which is a real window and not a formality — a screen drawn
     * during it that says "signed out" invites a rider to sign in twice.
     */
    val accountState: Flow<AccountState> =
        flow {
            val auth = auth
            if (auth == null) {
                emit(AccountState.SignedOut)
                return@flow
            }
            emitAll(
                auth.sessionStatus.map { status ->
                    when (status) {
                        is SessionStatus.Authenticated -> AccountState.SignedIn(
                            accountId = status.session.user?.id.orEmpty(),
                            email = status.session.user?.email
                        )

                        is SessionStatus.NotAuthenticated -> AccountState.SignedOut
                        // Loading from storage, or refreshing a session that has
                        // not failed yet. Both are "we do not know", and neither
                        // is "signed out".
                        else -> AccountState.Unknown
                    }
                }
            )
        }

    /** The account id right now, for the paths that cannot wait for a flow. */
    fun currentAccountId(): String? = auth?.currentSessionOrNull()?.user?.id

    /**
     * The same answer, but only once the SDK has finished looking in storage.
     *
     * This is what [CloudAccess] asks (15.2.8), and it has to wait: a sync
     * triggered at process start would otherwise see "no session", conclude the
     * rider is offline, and stop a backlog drain that had every right to run —
     * a bug that appears only on a cold start and therefore only on the tablet
     * that has been left alone overnight, which is every real bike.
     */
    suspend fun settledAccountId(): String? {
        val auth = auth ?: return null
        auth.awaitInitialization()
        return auth.currentSessionOrNull()?.user?.id
    }

    fun currentEmail(): String? = auth?.currentSessionOrNull()?.user?.email

    /**
     * Creates an account.
     *
     * Returns [AuthAttempt.ConfirmationRequired] when the project requires a
     * confirmed email — which this one does — because in that case **there is
     * no session and the rider is not signed in**. The SDK reports it by
     * returning a user with no session rather than by failing, which is exactly
     * the shape a caller gets wrong: it looks like success at the call site and
     * every subsequent cloud call quietly returns `Disabled`.
     */
    suspend fun signUp(email: String, password: String): AuthAttempt {
        val auth = auth ?: return AuthAttempt.Disabled
        val address = CredentialCheck.normaliseEmail(email)
        return attempt {
            auth.signUpWith(Email) {
                this.email = address
                this.password = password
            }
            val session = auth.currentSessionOrNull()
            if (session == null) {
                AuthAttempt.ConfirmationRequired(address)
            } else {
                AuthAttempt.Success(session.user?.id.orEmpty(), session.user?.email)
            }
        }
    }

    suspend fun signIn(email: String, password: String): AuthAttempt {
        val auth = auth ?: return AuthAttempt.Disabled
        val address = CredentialCheck.normaliseEmail(email)
        return attempt {
            auth.signInWith(Email) {
                this.email = address
                this.password = password
            }
            val session = auth.currentSessionOrNull()
            if (session == null) {
                // Should not happen for a password grant; if it ever does, say
                // so rather than reporting a success the app cannot act on.
                AuthAttempt.Failed("Signed in, but no session came back")
            } else {
                AuthAttempt.Success(session.user?.id.orEmpty(), session.user?.email)
            }
        }
    }

    /**
     * Sends the confirmation email again (15.1.2a).
     *
     * Worth its own method because the rate limit is low and the rider's
     * instinct when nothing arrives is to sign up again — which fails with
     * "already registered" and leaves them stuck between two errors that both
     * sound final.
     */
    suspend fun resendConfirmation(email: String): AuthAttempt {
        val auth = auth ?: return AuthAttempt.Disabled
        return attempt {
            auth.resendEmail(
                type = OtpType.Email.SIGNUP,
                email = CredentialCheck.normaliseEmail(email)
            )
            AuthAttempt.ConfirmationRequired(CredentialCheck.normaliseEmail(email))
        }
    }

    /**
     * Drops the session on this tablet.
     *
     * Local scope on purpose: a household may have this account on a phone and
     * a bike, and signing out of the bike is not a request to sign out of the
     * phone. Clearing the profile's `auth_user_id` is the caller's job (15.4.1)
     * and so is keeping every local ride, which is the promise this action is
     * allowed to make only because nothing here deletes anything.
     */
    suspend fun signOut(): AuthAttempt {
        val auth = auth ?: return AuthAttempt.Disabled
        return attempt {
            auth.signOut(SignOutScope.LOCAL)
            AuthAttempt.SignedOut
        }
    }

    private inline fun attempt(block: () -> AuthAttempt): AuthAttempt = try {
        block()
    } catch (e: Exception) {
        Log.w(TAG, "auth call failed", e)
        AuthAttempt.Failed(riderFacing(e))
    }

    /**
     * The rider's version of what went wrong.
     *
     * Every branch here is a sentence somebody on a bike can act on. The
     * default deliberately keeps the raw message rather than replacing it with
     * "something went wrong": an unrecognised failure with its own words is how
     * the next defect gets diagnosed, and this project's history is three cloud
     * bugs that survived because their messages were swallowed (14.2.3).
     */
    private fun riderFacing(e: Exception): String {
        val rest = e as? AuthRestException ?: return e.message ?: e::class.java.simpleName
        return when (rest.errorCode) {
            AuthErrorCode.InvalidCredentials -> "That email and password do not match an account"
            AuthErrorCode.EmailNotConfirmed ->
                "Confirm your email address first — check your inbox for the link"

            AuthErrorCode.UserAlreadyExists, AuthErrorCode.EmailExists ->
                "There is already an account with that email. Sign in instead"

            AuthErrorCode.WeakPassword -> "That password is too easy to guess"
            AuthErrorCode.OverEmailSendRateLimit ->
                "Too many emails have been sent to that address. Wait an hour and try again"

            AuthErrorCode.OverRequestRateLimit -> "Too many attempts. Wait a minute and try again"
            AuthErrorCode.SignupDisabled -> "This Pelonot cloud is not accepting new accounts"
            AuthErrorCode.ValidationFailed -> "Check the email address and try again"
            else -> rest.message ?: "Sign-in failed"
        }
    }

    /** Whether to draw any of this at all (23.1.5). */
    val cloudConfigured: Boolean get() = credentialsPresent()

    private companion object {
        const val TAG = "PelonotAuth"
    }
}
