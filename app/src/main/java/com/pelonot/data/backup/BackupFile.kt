package com.pelonot.data.backup

/**
 * What a restore is allowed to accept, decided without touching a file
 * (PLAN.md 12.4.4).
 *
 * A restore overwrites the rider's whole training history, so the checks in
 * front of it are the whole safety of the feature. They are kept here, pure,
 * because the alternative — deciding inside the copy — is a check that only
 * runs when someone thinks to run the copy.
 */
object BackupFile {

    /**
     * Every SQLite file begins with these 16 bytes, and the sixteenth is a
     * **NUL rather than a space**. Written as `"SQLite format 3 "` it refused
     * every genuine backup with "that file is not a Pelonot backup" — and no
     * test built out of this same constant could have caught it, which is why
     * there is one below written from the spec instead.
     */
    val SQLITE_MAGIC: ByteArray = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)

    /** The header is 100 bytes; this is all we need of it. */
    const val HEADER_BYTES = 16

    sealed interface Verdict {
        data object Accept : Verdict
        data class Refuse(val reason: String) : Verdict
    }

    fun looksLikeSqlite(header: ByteArray): Boolean =
        header.size >= SQLITE_MAGIC.size &&
            SQLITE_MAGIC.indices.all { header[it] == SQLITE_MAGIC[it] }

    /**
     * @param schemaVersion the backup's Room schema version (`PRAGMA user_version`)
     * @param currentVersion the version this build of the app understands
     * @param tables the table names the backup actually contains
     */
    fun verdictFor(
        header: ByteArray,
        schemaVersion: Int,
        currentVersion: Int,
        tables: Set<String>
    ): Verdict = when {
        !looksLikeSqlite(header) ->
            Verdict.Refuse("That file is not a Pelonot backup.")

        // A newer schema restored into an older app is the destructive-downgrade
        // path in a different costume (12.5.1): Room would answer it by wiping
        // the file it was just given. Refusing is the only honest answer, and it
        // names the fix.
        schemaVersion > currentVersion ->
            Verdict.Refuse(
                "That backup was made by a newer version of Pelonot. " +
                    "Update the app, then restore it."
            )

        // Room migrates an older backup forward on open, which is exactly what
        // migrations are for — but only if it is actually this app's database.
        REQUIRED_TABLES.any { it !in tables } ->
            Verdict.Refuse("That file is a database, but not Pelonot's.")

        else -> Verdict.Accept
    }

    private val REQUIRED_TABLES = setOf("profiles", "workouts", "workout_metrics")
}
