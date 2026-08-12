package com.pelonot.domain.cloud

/**
 * What deleting the cloud copy actually removed (PLAN 15.4.2).
 *
 * Counted from the rows the endpoint handed back rather than assumed from a
 * status code, which is the lesson `supabase/verify_rls.py` writes down at
 * length: PostgREST answers a `DELETE` with `204` and an empty body whether it
 * touched a row or not, so a delete that quietly matched nothing looks exactly
 * like one that worked. A rider being told *"your cloud copy is gone"* on the
 * strength of a 204 is the same class of claim as a backup the app never took.
 *
 * @param rides how many of the rider's rides the cloud let go of.
 * @param profileRemoved whether the profile row went with them. False is not a
 *   failure — a rider who signed in and never finished a ride has no profile row
 *   up there to remove.
 */
data class CloudDeletion(
    val rides: Int,
    val profileRemoved: Boolean
)
