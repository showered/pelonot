package com.pelonot.data.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.util.Log
import com.pelonot.data.local.AppDatabase
import com.pelonot.data.service.RideInProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The whole local database as one file, out and back in (PLAN.md 19.1.3,
 * 12.4.4).
 *
 * Until accounts exist this is the **only** backup a rider has: per-ride export
 * (12.4.3) gets one ride out in a form Strava understands, and gets nothing
 * back in. A wipe, a factory reset or a downgrade costs the rider everything
 * they have ridden.
 *
 * The file written is the SQLite database itself rather than a format of our
 * own. It restores by being copied back into place, it can be opened by
 * anything that reads SQLite, and there is no serialiser to fall behind the
 * schema — which is the failure this project has already had in another form
 * (`intervals_json`).
 */
class DatabaseBackup(
    private val context: Context,
    private val database: AppDatabase
) {

    /**
     * Copies the database to [target].
     *
     * The checkpoint is the whole trick: Room runs in WAL mode, so recent
     * writes live in `pelonot_database-wal` and **not** in the file this copies.
     * Without it a backup taken right after a ride is a backup without that
     * ride in it — silently, and only discovered when it is restored.
     */
    suspend fun backupTo(target: Uri): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            database.openHelper.writableDatabase
                .query("PRAGMA wal_checkpoint(TRUNCATE)")
                .use { it.moveToFirst() }

            val source = context.getDatabasePath(DATABASE_NAME)
            context.contentResolver.openOutputStream(target)?.use { out ->
                source.inputStream().use { input -> input.copyTo(out) }
            } ?: error("could not open the file for writing")

            source.length()
        }.onFailure { Log.e(TAG, "Backup failed", it) }
    }

    /**
     * Replaces the database with [source], after checking it is one of ours.
     *
     * Everything is validated against a copy in the cache first: the moment the
     * live file is touched there is no way back, so nothing touches it until
     * the answer is already known.
     */
    suspend fun restoreFrom(source: Uri): RestoreOutcome = withContext(Dispatchers.IO) {
        // A restore under a running ride would pull the `workouts` row out from
        // beneath the service mid-write. Same family as 8.3b.
        if (RideInProgress.workoutId != null) {
            return@withContext RestoreOutcome.Refused(
                "Finish the ride you are on before restoring a backup."
            )
        }

        val staged = File(context.cacheDir, "restore-staging.db")
        try {
            context.contentResolver.openInputStream(source)?.use { input ->
                staged.outputStream().use { out -> input.copyTo(out) }
            } ?: return@withContext RestoreOutcome.Refused("Could not read that file.")

            when (val verdict = inspect(staged)) {
                is BackupFile.Verdict.Refuse -> {
                    Log.w(TAG, "Refusing that file: ${verdict.reason}")
                    return@withContext RestoreOutcome.Refused(verdict.reason)
                }
                BackupFile.Verdict.Accept -> Unit
            }

            // Closing folds the WAL back in and releases the file handles, so
            // what is replaced is one file rather than three out of step.
            database.close()

            val live = context.getDatabasePath(DATABASE_NAME)
            staged.copyTo(live, overwrite = true)
            File("${live.path}-wal").delete()
            File("${live.path}-shm").delete()

            Log.i(TAG, "Restored ${live.length()} bytes; the process must restart")
            RestoreOutcome.RestartRequired
        } catch (e: Exception) {
            Log.e(TAG, "Restore failed", e)
            RestoreOutcome.Refused("Could not restore that file: ${e.message}")
        } finally {
            staged.delete()
        }
    }

    /** Opens the staged copy read-only to ask what it actually is. */
    private fun inspect(staged: File): BackupFile.Verdict {
        val header = ByteArray(BackupFile.HEADER_BYTES)
        staged.inputStream().use { it.read(header) }
        if (!BackupFile.looksLikeSqlite(header)) {
            return BackupFile.verdictFor(header, 0, AppDatabase.SCHEMA_VERSION, emptySet())
        }

        return SQLiteDatabase.openDatabase(
            staged.path,
            null,
            SQLiteDatabase.OPEN_READONLY
        ).use { db ->
            val tables = mutableSetOf<String>()
            db.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null).use { cursor ->
                while (cursor.moveToNext()) tables += cursor.getString(0)
            }
            BackupFile.verdictFor(header, db.version, AppDatabase.SCHEMA_VERSION, tables)
        }
    }

    /** `pelonot-backup-2026-08-01.db` — sorts by date and says what it is. */
    fun suggestedFileName(now: Date = Date()): String =
        "pelonot-backup-${FILE_DATE.format(now)}.db"

    sealed interface RestoreOutcome {
        /**
         * The file is in place and this process is holding a closed database.
         * Nothing is readable until the app starts again, so the caller's job
         * is to restart it rather than to carry on.
         */
        data object RestartRequired : RestoreOutcome
        data class Refused(val reason: String) : RestoreOutcome
    }

    private companion object {
        const val TAG = "DatabaseBackup"
        const val DATABASE_NAME = "pelonot_database"
        val FILE_DATE = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    }
}
