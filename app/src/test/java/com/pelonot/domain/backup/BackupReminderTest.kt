package com.pelonot.domain.backup

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The line between a reminder and a nag (PLAN 23.3.1).
 *
 * Every one of these is about *not* speaking up, which is the harder half: an
 * app that warns on launch is an app whose warnings stop being read, and by
 * then it has spent the one warning that mattered.
 */
class BackupReminderTest {

    @Test
    fun `a new rider is not warned about anything`() {
        assertFalse(BackupReminder.None.isDue)
        assertFalse(BackupReminder(ridesSinceMark = 1, hasEverBackedUp = false).isDue)
        assertFalse(BackupReminder(ridesSinceMark = 9, hasEverBackedUp = false).isDue)
    }

    @Test
    fun `the tenth unbacked ride earns the reminder`() {
        assertTrue(BackupReminder(ridesSinceMark = 10, hasEverBackedUp = false).isDue)
        assertTrue(BackupReminder(ridesSinceMark = 40, hasEverBackedUp = true).isDue)
    }

    @Test
    fun `never having backed up does not lower the bar`() {
        // The temptation is to treat it as more urgent. It is not: a rider two
        // rides into the app has nothing to lose yet, and an app that opens
        // with a warning is one whose warnings are ignored by ride ten.
        assertFalse(BackupReminder(ridesSinceMark = 3, hasEverBackedUp = false).isDue)
    }

    @Test
    fun `the sentence counts rides rather than telling the rider off`() {
        val first = BackupReminder(ridesSinceMark = 12, hasEverBackedUp = false).message
        assertTrue(first, first.contains("12 rides"))
        assertTrue(first, first.contains("no backup yet"))

        val since = BackupReminder(ridesSinceMark = 11, hasEverBackedUp = true).message
        assertTrue(since, since.contains("11 rides since your last backup"))
    }

    @Test
    fun `one ride is one ride`() {
        // Not "1 rides". It cannot be reached at the current threshold, and it
        // will be the moment anyone lowers it.
        assertTrue(BackupReminder(1, hasEverBackedUp = true).message.startsWith("1 ride "))
    }
}
