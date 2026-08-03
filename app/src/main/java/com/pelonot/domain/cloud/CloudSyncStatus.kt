package com.pelonot.domain.cloud

/**
 * What Settings is allowed to tell the rider about their cloud backup (PLAN
 * 14.2.3).
 *
 * **The item exists because `SyncOutcome.Failed` has always died in a
 * `Log.w`** — on a tablet whose `log.tag` is `W` device-wide, so even the log
 * was mostly theoretical. That silence is exactly how three separate cloud
 * defects survived the whole project: a missing `GRANT`, a timestamp Postgres
 * could not parse, and a decode that threw on every class fetch. All three
 * would have been one glance at this.
 *
 * Pure, and deliberately so. Deciding *what is true* about a backup is the part
 * worth testing, and a `@Composable` cannot be asked "what would you say if the
 * last success were older than the oldest waiting ride?".
 *
 * The rule the whole type is built around: **it must never imply the rider is
 * covered when they are not.** Every ambiguous case resolves towards saying
 * less rather than reassuring.
 */
sealed interface CloudSyncStatus {

    /**
     * No account on this profile, or backup switched off, or a build with no
     * credentials — the three reasons `CloudAccess` collapses into one (23.1.6).
     *
     * Not an error and not a problem to solve. It is the middle rung of the
     * identity ladder, where most riders will live, and Settings must not draw
     * it as a failure state.
     */
    data object Off : CloudSyncStatus

    /** Signed in, and the cloud has every ride this profile has finished. */
    data class UpToDate(val lastSyncAtMs: Long?) : CloudSyncStatus

    /**
     * Rides are waiting and nothing has gone wrong — the ordinary state
     * between finishing a ride and the network being available.
     */
    data class Pending(
        val rides: Int,
        val oldestRideAtMs: Long?,
        val lastSyncAtMs: Long?
    ) : CloudSyncStatus

    /**
     * The last attempt failed, and this is what it said.
     *
     * Carries the backlog too, because "it failed" and "and here is what is
     * stranded because of it" are one sentence to a rider. A failure with an
     * empty backlog is not reported at all — see [from]: a transient error the
     * app has since recovered from is not news, and showing it teaches the
     * rider to ignore this line.
     */
    data class Failing(
        val rides: Int,
        val oldestRideAtMs: Long?,
        val lastSyncAtMs: Long?,
        val message: String,
        val failedAtMs: Long
    ) : CloudSyncStatus

    companion object {
        /**
         * @param hasAccount whether this profile's cloud is available at all —
         *   `CloudAccess`'s answer, not `SupabaseModule.isConfigured`.
         * @param pending rides this profile has that the cloud has not.
         * @param oldestRideAtMs the oldest of those, or null if there are none.
         *   Null and 0 are different claims and the second is a lie about 1970.
         */
        fun from(
            hasAccount: Boolean,
            pending: Int,
            oldestRideAtMs: Long?,
            lastSyncAtMs: Long?,
            lastError: String?,
            lastErrorAtMs: Long?
        ): CloudSyncStatus {
            if (!hasAccount) return Off

            // An error with nothing stranded behind it is over. The drain clears
            // the record when it finishes empty, so this is belt and braces
            // against the two writes racing — but it is the safe direction to be
            // wrong in, and the alternative is a red line on a screen with
            // nothing wrong behind it.
            if (lastError != null && lastErrorAtMs != null && pending > 0) {
                return Failing(
                    rides = pending,
                    oldestRideAtMs = oldestRideAtMs,
                    lastSyncAtMs = lastSyncAtMs,
                    message = lastError,
                    failedAtMs = lastErrorAtMs
                )
            }

            return if (pending == 0) {
                UpToDate(lastSyncAtMs)
            } else {
                Pending(pending, oldestRideAtMs, lastSyncAtMs)
            }
        }
    }
}
