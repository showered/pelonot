package com.pelonot.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Every schema change the database has ever been through, in order.
 *
 * Until now `AppDatabase` used `fallbackToDestructiveMigration()`, which means
 * *any* schema change drops the database and recreates it. That was a
 * reasonable pre-release convenience right up until the moment a build with a
 * real training history was installed on the bike's tablet, after which it is
 * a data-loss bug that has already happened by the time anyone notices.
 *
 * The rule from here on: **a change to any `@Entity` needs a version bump, a
 * `Migration` in this list, and a test in `MigrationTest`.** Room validates the
 * migrated schema against the exported JSON in `app/schemas/`, so a migration
 * that does not produce exactly the declared shape fails loudly rather than
 * leaving a subtly wrong table behind.
 *
 * One consequence worth stating plainly: a device still holding a database from
 * *before* this change — a pre-release build whose version 1 had a different
 * shape — will now fail to open rather than silently wiping itself. That is the
 * trade being made deliberately. Uninstall and reinstall on a development
 * device; no shipped build has ever existed, so no rider is affected.
 */
object AppMigrations {

    /**
     * Adds `workouts.was_recovered`.
     *
     * A ride rebuilt by `WorkoutRepository.recoverWorkout` after the app was
     * killed mid-workout is not quite the same object as one that finished
     * normally: its totals are reconstructed from whatever samples reached the
     * database, and the tail of the ride — however long the rider kept pedalling
     * after the process died — is simply missing. History has to be able to say
     * so, rather than presenting a truncated ride as a complete one.
     *
     * `DEFAULT 0` because every ride recorded before this column existed came
     * through the normal path, or came through recovery at a time when nothing
     * recorded the difference. Claiming otherwise would be inventing a fact
     * about the rider's own record.
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE `workouts` ADD COLUMN `was_recovered` INTEGER NOT NULL DEFAULT 0"
            )
        }
    }

    /**
     * Adds `profiles.auth_user_id`.
     *
     * The consent gate (PLAN 23.1.1). Every profile that already exists was
     * created by a rider who was never asked and never signed in, so `NULL` is
     * not a default chosen for convenience — it is the true answer for all of
     * them, and it is what makes rule 1 of the connectivity model retroactive
     * as well as prospective.
     *
     * Nullable rather than `NOT NULL DEFAULT ''`: the absence of an account is
     * the state the app reasons about, and an empty string is a value that
     * every future `!= null` check would get wrong.
     */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `profiles` ADD COLUMN `auth_user_id` TEXT")
        }
    }

    /**
     * Adds `workout_metrics.power_is_measured`.
     *
     * The column three separate features were waiting on: the chart axis that
     * cannot say what it is drawing (16.1.6), the FTP proposal that a simulated
     * ride can currently make (7.10.7), and the household leaderboard that must
     * not rank fiction beside fact (24.4.2). `SensorReading.powerIsMeasured`
     * has always existed; it was thrown away at the database boundary, so a
     * ride off the real board was indistinguishable on disk from one the app
     * invented.
     *
     * **Nullable, and no default.** Every sample already on a tablet was
     * recorded when nothing knew the answer. `DEFAULT 0` would say "the model
     * produced these" of rides that came off a real bike, and `DEFAULT 1` would
     * do the reverse and worse. Null says what is true: nobody wrote it down.
     */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `workout_metrics` ADD COLUMN `power_is_measured` INTEGER")
        }
    }

    /**
     * Adds `class_templates.retired_at`.
     *
     * The class library was rebuilt (PLAN 23.2.6) under a new set of ids,
     * because `workouts.class_id` points at the old ones and the bike already
     * holds a real twenty-minute ride on `HC-01`. Changing what `HC-01` *is*
     * would silently rewrite what that ride was — the same trap as 7.8 and
     * 16.1.6, a derived fact read from a source that has since moved.
     *
     * So the old classes are retired rather than replaced or deleted. Nullable
     * with no default: a class that is still in the library has no retirement
     * date, and null is that, not a zero standing in for it.
     */
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `class_templates` ADD COLUMN `retired_at` INTEGER")
        }
    }

    val ALL: Array<Migration> =
        arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
}
