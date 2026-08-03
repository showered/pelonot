package com.pelonot.data.remote

import com.pelonot.data.local.dao.UserDao

/**
 * The one place that answers *may this profile talk to the cloud?* (PLAN
 * 23.1.1).
 *
 * The gate used to be [SupabaseModule.isConfigured], which asks whether **this
 * build** carries a URL and a key. That is a build detail, not a rider's
 * consent, and reading it as consent is how an install with no account came to
 * make two requests to Supabase — one on first launch and one after every
 * profile ride.
 *
 * The question this asks instead is **is this rider signed in?**, per profile,
 * every time. Rule 2 of the connectivity model says signing in *is* the
 * consent, so there is no second thing to agree to and no switch to find
 * before backup works.
 *
 * Three separate reasons to be offline, deliberately collapsed to one answer,
 * because a rider who is offline for any of them must see identical behaviour
 * (23.1.6):
 *
 * - the rider has no account — the default, and by far the commonest;
 * - the build has no credentials — a fresh clone of this repository;
 * - the rider has an account and has turned backup off.
 *
 * There is no `isAllowed()` without a profile on purpose. Every caller has to
 * name the rider it is acting for, which is what stops the next feature that
 * "only needs one little lookup" from reaching the network on nobody's behalf
 * — the same fencing idea as the `PowerModel` consumer test (2.2a.8), and
 * [com.pelonot.data.remote.CloudAccessFenceTest] is its enforcement.
 */
class CloudAccess(
    private val userDao: UserDao,
    /** The rider's own backup switch, meaningful only once they have an account. */
    private val backupPreference: () -> Boolean = { true },
    private val credentialsPresent: () -> Boolean = { SupabaseModule.isConfigured },
    /**
     * **Whose session this tablet is actually holding right now** (PLAN 15.2.8).
     *
     * A fourth reason to be offline, and the only one that is not about the
     * rider's own choices. The Supabase SDK holds **one** session per process,
     * so a household bike with two accounts on it can only ever be signed in as
     * one of them at a time — and `auth_user_id` on a profile records that the
     * rider *has* an account, not that this tablet is currently carrying their
     * credentials.
     *
     * Without this check the shape is specific and bad: Priya finishes a ride
     * while the tablet holds Simon's session, the gate says yes because Priya's
     * row has an `auth_user_id`, and the upload goes out under **Simon's** JWT
     * carrying **Priya's** `user_id`. Under `003`'s policies that is refused —
     * `WITH CHECK (user_id = auth.uid())` — so it is a permanent, silent
     * failure rather than a leak, which is the right way round but is still a
     * ride that never gets backed up and a rider who is told the network is at
     * fault.
     *
     * Suspending because at process start the SDK has not finished loading the
     * stored session yet, and answering "nobody" during that window would stop
     * a backlog drain that had every right to run.
     */
    private val sessionAccountId: suspend () -> String? = { null }
) {

    /**
     * **Who this rider is in the cloud, or null if they have no business being
     * there** (PLAN 14.2.1).
     *
     * The gate and the identity are one lookup because they are one question.
     * "May this profile talk to the cloud?" is answered by `auth_user_id` being
     * non-null, and `auth_user_id` is *also* the only name the rider has up
     * there — the cloud's `profiles.id` **is** the auth user id and nothing
     * else (`supabase/003_cloud_identity.sql`). Asking twice would mean two
     * lookups that can disagree, and the shape where they disagree is a call
     * that passed the gate and then wrote a row belonging to nobody. That is
     * what every ride uploaded before this method existed did.
     *
     * Null for all three reasons in the class doc, undifferentiated on purpose
     * (23.1.6): no account, no credentials in the build, or backup switched
     * off. A caller that wants to behave differently for one of them is a
     * caller about to leak which one, and the rider cannot tell the difference
     * either way.
     *
     * @param localUserId the profile being acted for. **Null is a guest**, who
     *   has no owner by definition and therefore no cloud (15.2.3), and it is
     *   also what `workouts.user_id` holds for a ride whose profile was later
     *   deleted.
     */
    suspend fun accountIdFor(localUserId: Int?): String? {
        val id = localUserId ?: return null
        if (!credentialsPresent()) return null
        val user = userDao.getUserById(id) ?: return null
        val authUserId = user.authUserId ?: return null
        if (!backupPreference()) return null
        // 15.2.8. Having an account and being signed in on this tablet are two
        // facts, and only the second one can send a request.
        return if (sessionAccountId() == authUserId) authUserId else null
    }

    /**
     * The same question with the answer thrown away, for callers that only need
     * to decide whether to *start* something — `WorkoutSyncWorker.enqueueIfAllowed`
     * has no row to write yet and no use for the id.
     */
    suspend fun isAllowedFor(localUserId: Int?): Boolean = accountIdFor(localUserId) != null

    /**
     * Whether *anyone* on this tablet is signed in.
     *
     * For deciding whether to show cloud furniture at all (23.1.5), never for
     * deciding whether a particular call may go out — an account on one profile
     * grants nothing to another.
     */
    suspend fun anyProfileHasAccount(): Boolean = userDao.getAccountProfileCount() > 0
}
