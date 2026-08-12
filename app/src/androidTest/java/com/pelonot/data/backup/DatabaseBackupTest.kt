package com.pelonot.data.backup

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pelonot.data.local.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The backup a build writes must be a backup that same build will take back.
 *
 * This is the test that was missing when the check drifted. `BackupFile
 * .verdictFor` refuses a file whose schema is newer than the app's, and the
 * app's number was a `const` kept equal to `@Database(version = …)` by a
 * comment — so when the version went 16 → 17 without it, **every backup this
 * build wrote was refused on restore**, with a message telling the rider to
 * update an app that was already the newest one. `BackupFileTest` was green
 * throughout: the arithmetic was right and one of its arguments was wrong.
 *
 * Deliberately stops at the verdict. [DatabaseBackup.restoreFrom] closes the
 * shared database and overwrites the live file, and the interesting half is
 * over by then.
 */
@RunWith(AndroidJUnit4::class)
class DatabaseBackupTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun aBackupThisBuildWrote_isAcceptedByThisBuild() = runBlocking {
        val database = AppDatabase.getInstance(context)
        val backup = DatabaseBackup(context, database)
        val target = File(context.cacheDir, "backup-round-trip.db")

        try {
            val written = backup.backupTo(Uri.fromFile(target))
            assertEquals(true, written.isSuccess)

            assertEquals(BackupFile.Verdict.Accept, backup.inspect(target))
        } finally {
            target.delete()
        }
    }
}
