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
    private val credentialsPresent: () -> Boolean = { SupabaseModule.isConfigured }
) {

    /**
     * @param localUserId the profile being acted for. **Null is a guest**, who
     *   has no owner by definition and therefore no cloud (15.2.3), and it is
     *   also what `workouts.user_id` holds for a ride whose profile was later
     *   deleted.
     */
    suspend fun isAllowedFor(localUserId: Int?): Boolean {
        val id = localUserId ?: return false
        if (!credentialsPresent()) return false
        val user = userDao.getUserById(id) ?: return false
        if (!user.hasAccount) return false
        return backupPreference()
    }

    /**
     * Whether *anyone* on this tablet is signed in.
     *
     * For deciding whether to show cloud furniture at all (23.1.5), never for
     * deciding whether a particular call may go out — an account on one profile
     * grants nothing to another.
     */
    suspend fun anyProfileHasAccount(): Boolean = userDao.getAccountProfileCount() > 0
}
