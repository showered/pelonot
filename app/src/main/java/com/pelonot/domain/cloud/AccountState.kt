package com.pelonot.domain.cloud

/**
 * Whether *this tablet* is holding a session, said in the app's own words
 * rather than the SDK's (PLAN 15.1.3).
 *
 * It is deliberately a different question from "may this profile talk to the
 * cloud?", which is [com.pelonot.data.remote.CloudAccess]'s and is answered per
 * profile off `auth_user_id`. A session is a **device** fact and an account is
 * a **profile** fact, and on a household bike those come apart in a way that
 * matters: two riders may share one tablet and one of them may be signed in.
 * Collapsing the two would put the second rider's rides in the first rider's
 * cloud, which is the failure mode 15.2.4 exists to forbid.
 *
 * [Unknown] is a real state and not a placeholder. The SDK loads a stored
 * session asynchronously at startup, so there is a window — short, but long
 * enough to draw a screen in — where the app genuinely does not know. Drawing
 * "signed out" during it is how a rider comes to press *Sign in* while already
 * signed in.
 */
sealed interface AccountState {

    /** Still loading whatever was stored. Draw nothing conclusive. */
    data object Unknown : AccountState

    /**
     * No session on this tablet — including the case where the build has no
     * cloud at all, because there is nothing a rider could do about either and
     * they must look identical (23.1.6).
     */
    data object SignedOut : AccountState

    /**
     * A session, and who it belongs to.
     *
     * @param accountId the auth user id. This is also `profiles.id` in the
     *   cloud and the value that lands in `UserEntity.authUserId` — one
     *   identity, not three (14.2.1).
     */
    data class SignedIn(val accountId: String, val email: String?) : AccountState

    val accountIdOrNull: String? get() = (this as? SignedIn)?.accountId
}

/**
 * What came back from an attempt to sign in, sign up or sign out.
 *
 * `Failed` carries a *rider-facing* message rather than the exception, because
 * the alternative is what this project has done before: the failure goes into a
 * `Log.w` on a tablet whose `log.tag` is `W`, and three cloud defects survive
 * for the life of the project (14.2.3).
 */
sealed interface AuthAttempt {

    /** Signed in, and this is who. */
    data class Success(val accountId: String, val email: String?) : AuthAttempt

    /**
     * The account exists but the email has not been confirmed yet, so **there
     * is no session** (15.1.2a).
     *
     * This is not an error and must not be drawn as one. It is the ordinary
     * outcome of signing up against a project with `mailer_autoconfirm` off,
     * which is the safe default and the one this project runs. The rider has to
     * leave the app, open their email, and come back — so the screen has to
     * survive being left, and the copy has to say what to do rather than
     * apologise.
     */
    data class ConfirmationRequired(val email: String) : AuthAttempt

    /** The session on this tablet is gone. Every local ride is still here. */
    data object SignedOut : AuthAttempt

    /** No cloud in this build. Nothing to report and nothing to retry. */
    data object Disabled : AuthAttempt

    data class Failed(val message: String) : AuthAttempt
}
