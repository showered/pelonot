package com.pelonot.domain.backup

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The line between a reminder and a nag (PLAN 23.3.1), and the line between a
 * reminder and a false claim (23.3.1a, 23.3.1b).
 *
 * Most of these are about *not* speaking up, which is the harder half: an app
 * that warns on launch is an app whose warnings stop being read, and by then it
 * has spent the one warning that mattered. The rest are about what it is
 * allowed to say when it does speak — the sentence *"they live on this tablet
 * and nowhere else"* was told to every rider, including riders it was untrue
 * of, and that is a wrong claim about where somebody's data is rather than an
 * over-eager reminder.
 */
class BackupReminderTest {

    /** The offline tier: nothing is anywhere else, so both counts agree. */
    private fun offline(rides: Int, everBackedUp: Boolean = false) =
        BackupReminder(
            ridesSinceMark = rides,
            ridesOnTabletOnly = rides,
            hasEverBackedUp = everBackedUp
        )

    @Test
    fun `a new rider is not warned about anything`() {
        assertFalse(BackupReminder.None.isDue)
        assertFalse(offline(1).isDue)
        assertFalse(offline(9).isDue)
    }

    @Test
    fun `the tenth unbacked ride earns the reminder`() {
        assertTrue(offline(10).isDue)
        assertTrue(offline(40, everBackedUp = true).isDue)
    }

    @Test
    fun `never having backed up does not lower the bar`() {
        // The temptation is to treat it as more urgent. It is not: a rider two
        // rides into the app has nothing to lose yet, and an app that opens
        // with a warning is one whose warnings are ignored by ride ten.
        assertFalse(offline(3).isDue)
    }

    @Test
    fun `the sentence counts rides rather than telling the rider off`() {
        val first = offline(12).message
        assertTrue(first, first.contains("12 rides"))
        assertTrue(first, first.contains("no backup yet"))

        val since = offline(11, everBackedUp = true).message
        assertTrue(since, since.contains("11 rides since your last backup"))
    }

    @Test
    fun `one ride is one ride`() {
        // Not "1 rides". It cannot be reached at the current threshold, and it
        // will be the moment anyone lowers it.
        assertTrue(offline(1, everBackedUp = true).message.startsWith("1 ride "))
    }

    // ── 23.3.1a: a ride the cloud already holds is not at stake ──────────

    @Test
    fun `rides already up do not count towards the reminder`() {
        // Forty rides since the mark, thirty-eight of them safely in an
        // account. Two rides is not the risk the threshold was chosen for, and
        // the old count would have shown the card on all forty.
        val mostlyUp = BackupReminder(
            ridesSinceMark = 40,
            ridesOnTabletOnly = 2,
            hasEverBackedUp = true
        )
        assertFalse(mostlyUp.isDue)
    }

    @Test
    fun `a bike full of guest rides still earns the reminder however much is up`() {
        // A guest ride can never sync, so it is on this list forever. This is
        // the case that keeps the card alive on a fully signed-in bike, and it
        // is the direct answer to the owner's question: yes, and deliberately.
        val guests = BackupReminder(
            ridesSinceMark = 100,
            ridesOnTabletOnly = 11,
            hasEverBackedUp = true
        )
        assertTrue(guests.isDue)
        assertTrue(guests.message, guests.message.startsWith("11 rides"))
    }

    // ── 23.3.1b: the sentence has to be true of the rider reading it ─────

    @Test
    fun `nowhere else is only claimed of the rides it is true of`() {
        val mixed = BackupReminder(
            ridesSinceMark = 30,
            ridesOnTabletOnly = 12,
            hasEverBackedUp = true
        )
        // The count in the sentence is the unprotected one, never the total:
        // "30 rides live on this tablet and nowhere else" would be the same
        // false claim with a different number in it.
        assertTrue(mixed.message, mixed.message.startsWith("12 rides"))
        assertFalse(mixed.message, mixed.message.contains("30"))
    }

    @Test
    fun `the last-backup framing is dropped once the count is a subset`() {
        // 2.5a.5 clears `synced_at` on rides it touches, so this number can
        // move for reasons that have nothing to do with the backup mark. It
        // must not go on claiming that is where it came from.
        val mixed = BackupReminder(
            ridesSinceMark = 30,
            ridesOnTabletOnly = 12,
            hasEverBackedUp = true
        )
        assertFalse(mixed.message, mixed.message.contains("since your last backup"))
    }

    @Test
    fun `an account is mentioned only when something is actually up there`() {
        // A rider signed in with nothing uploaded yet is the case this guards.
        // Telling them an account holds the others is the same false claim
        // pointing the other way.
        val nothingUp = offline(12, everBackedUp = true)
        assertFalse(nothingUp.someRidesAreUp)
        assertFalse(nothingUp.message, nothingUp.message.contains("account"))

        val someUp = BackupReminder(
            ridesSinceMark = 30,
            ridesOnTabletOnly = 12,
            hasEverBackedUp = true
        )
        assertTrue(someUp.someRidesAreUp)
        assertTrue(someUp.message, someUp.message.contains("account"))
    }

    @Test
    fun `never having backed up still changes the sentence when some rides are up`() {
        // All four branches say something different, and the one a rider is
        // most likely to meet first is this one: signed in, rides climbing,
        // no file ever written.
        val neverBackedUp = BackupReminder(
            ridesSinceMark = 30,
            ridesOnTabletOnly = 12,
            hasEverBackedUp = false
        )
        assertTrue(neverBackedUp.message, neverBackedUp.message.contains("no backup yet"))
        assertTrue(neverBackedUp.message, neverBackedUp.message.contains("account"))
    }
}
