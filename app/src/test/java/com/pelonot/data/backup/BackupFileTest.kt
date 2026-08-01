package com.pelonot.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupFileTest {

    private val ours = setOf("profiles", "workouts", "workout_metrics", "class_templates")
    private val header = BackupFile.SQLITE_MAGIC.copyOf()

    @Test
    fun `the magic is the real sixteen bytes, NUL and all`() {
        // Written from the spec rather than from the constant under test: the
        // first version of it ended in a space, which refused every genuine
        // backup, and every other case here would have passed regardless
        // because they all build their header out of the same constant.
        val realHeader = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)

        assertTrue(BackupFile.looksLikeSqlite(realHeader))
        assertEquals(16, realHeader.size)
    }

    @Test
    fun `accepts this app's own database at the current schema`() {
        assertEquals(
            BackupFile.Verdict.Accept,
            BackupFile.verdictFor(header, schemaVersion = 2, currentVersion = 2, tables = ours)
        )
    }

    @Test
    fun `accepts an older backup, because migrations exist for exactly this`() {
        assertEquals(
            BackupFile.Verdict.Accept,
            BackupFile.verdictFor(header, schemaVersion = 1, currentVersion = 2, tables = ours)
        )
    }

    @Test
    fun `refuses a backup from a newer app`() {
        // The destructive-downgrade path in another costume (12.5.1): Room's
        // answer to a database from the future is to empty it.
        val verdict = BackupFile.verdictFor(header, 3, 2, ours)

        assertTrue(verdict is BackupFile.Verdict.Refuse)
        assertTrue((verdict as BackupFile.Verdict.Refuse).reason.contains("newer version"))
    }

    @Test
    fun `refuses a file that is not a database at all`() {
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())

        assertFalse(BackupFile.looksLikeSqlite(jpeg))
        assertTrue(BackupFile.verdictFor(jpeg, 2, 2, ours) is BackupFile.Verdict.Refuse)
    }

    @Test
    fun `refuses somebody else's SQLite database`() {
        val verdict = BackupFile.verdictFor(header, 2, 2, setOf("android_metadata", "messages"))

        assertTrue(verdict is BackupFile.Verdict.Refuse)
        assertTrue((verdict as BackupFile.Verdict.Refuse).reason.contains("not Pelonot's"))
    }

    @Test
    fun `refuses a Pelonot database missing the table the rides live in`() {
        val verdict = BackupFile.verdictFor(header, 2, 2, setOf("profiles", "workouts"))

        assertTrue(verdict is BackupFile.Verdict.Refuse)
    }

    @Test
    fun `a truncated header is not a database`() {
        assertFalse(BackupFile.looksLikeSqlite("SQLite".toByteArray()))
    }
}
