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

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
}
