package com.pelonot.domain.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What Settings is allowed to say about a rider's backup (PLAN 14.2.3).
 *
 * The rule under all of these: **never imply the rider is covered when they are
 * not.** Every ambiguous case has to resolve towards saying less.
 */
class CloudSyncStatusTest {

    private fun status(
        hasAccount: Boolean = true,
        pending: Int = 0,
        oldest: Long? = null,
        lastSync: Long? = null,
        error: String? = null,
        errorAt: Long? = null
    ) = CloudSyncStatus.from(hasAccount, pending, oldest, lastSync, error, errorAt)

    /**
     * The commonest state in the app, and it must not look like a fault.
     *
     * An offline rider is on the middle rung of the identity ladder, where most
     * riders will live. Drawing "no account" as a failure is how signing in
     * becomes the way to make a warning go away, which is the opposite of rule
     * 2: signing in *is* the consent, and consent extracted by nagging is not
     * consent.
     */
    @Test
    fun `no account is not a failure state`() {
        assertEquals(CloudSyncStatus.Off, status(hasAccount = false))
        // Not even with rides waiting and an error on file — an offline rider
        // has no backlog, they have a history.
        assertEquals(
            CloudSyncStatus.Off,
            status(hasAccount = false, pending = 12, error = "boom", errorAt = 1)
        )
    }

    @Test
    fun `nothing waiting is up to date`() {
        assertEquals(CloudSyncStatus.UpToDate(lastSyncAtMs = 500), status(lastSync = 500))
    }

    /**
     * **A failure the app has since recovered from is not news.**
     *
     * The drain clears the record when it finishes with an empty backlog, so
     * this is the second line of defence rather than the first — but the safe
     * direction to be wrong in is this one. A red line on a screen with nothing
     * wrong behind it is how a rider learns to ignore the line, and then it is
     * worth nothing on the day it matters.
     */
    @Test
    fun `an error with nothing stranded behind it is not reported`() {
        assertEquals(
            CloudSyncStatus.UpToDate(lastSyncAtMs = 500),
            status(pending = 0, lastSync = 500, error = "401 permission denied", errorAt = 900)
        )
    }

    @Test
    fun `rides waiting with no error is ordinary, not alarming`() {
        val result = status(pending = 3, oldest = 1_000, lastSync = 500)
        assertEquals(CloudSyncStatus.Pending(3, 1_000, 500), result)
    }

    /**
     * A failure carries the backlog with it, because "it failed" and "and here
     * is what is stranded because of it" are one sentence to a rider. Three
     * rides waiting since this morning and three waiting since March are the
     * same count and completely different news.
     */
    @Test
    fun `a failure says what is stranded as well as what went wrong`() {
        val result = status(
            pending = 4,
            oldest = 1_000,
            lastSync = 500,
            error = "401 permission denied for table workouts",
            errorAt = 2_000
        ) as CloudSyncStatus.Failing

        assertEquals(4, result.rides)
        assertEquals(1_000L, result.oldestRideAtMs)
        assertEquals(500L, result.lastSyncAtMs)
        assertEquals(2_000L, result.failedAtMs)
        assertTrue(result.message.startsWith("401"))
    }

    /**
     * **A rider who has never synced has a null last-sync, not a zero.**
     *
     * Formatting 0 as a date puts their last backup in January 1970, which is
     * both absurd and — worse — a *specific* claim where the truth is "never".
     * Same family as `heartRateBpm` and `MIN()` over an empty backlog.
     *
     * Settings says *"Nothing is waiting to go up"* for this state rather than
     * *"no rides have gone up yet"*, which was the first wording and is a claim
     * the app cannot support: an empty backlog with no recorded sync is **also**
     * what a rider sees after restoring a backup file made on another tablet,
     * where the rides arrive already marked and the DataStore mark does not
     * travel with them. Found by driving the tablet AVD, not by reading it.
     */
    @Test
    fun `never having synced is absent, not the epoch`() {
        assertNull((status(lastSync = null) as CloudSyncStatus.UpToDate).lastSyncAtMs)
        assertNull((status(pending = 2, lastSync = null) as CloudSyncStatus.Pending).lastSyncAtMs)
    }

    /**
     * An error is only trusted with a time on it. A message with no timestamp
     * cannot be placed against the last success, so it cannot be turned into an
     * honest sentence — and the honest thing to do with a fact you cannot state
     * is not state it.
     */
    @Test
    fun `an error with no time on it is not reported as a failure`() {
        assertEquals(
            CloudSyncStatus.Pending(2, 1_000, 500),
            status(pending = 2, oldest = 1_000, lastSync = 500, error = "boom", errorAt = null)
        )
    }
}
