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

    /**
     * Adds `profiles.household_visible`.
     *
     * The per-profile opt-out from household social (PLAN 24.2.3). `NOT NULL
     * DEFAULT 1` because every profile that already exists was created while
     * 24.1's leaderboard was already showing them, so `true` is the state they
     * are actually in — not a convenience. Defaulting to `0` would have this
     * migration remove riders from a board they are on today, which is a
     * migration deciding a preference on a rider's behalf.
     *
     * Note this is the opposite reasoning to `auth_user_id`'s, and for the same
     * underlying rule: pick the value that is *true of the rows that exist*.
     * Nobody had consented to the cloud, so null; everybody was already on the
     * household board, so 1.
     */
    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE `profiles` ADD COLUMN `household_visible` INTEGER NOT NULL DEFAULT 1"
            )
        }
    }

    /**
     * Adds `workouts.ftp_watts`.
     *
     * The FTP a ride was ridden at (PLAN 7.8) — the bug underneath the whole of
     * phase 7. `profiles.ftp_watts` is a moving number and every chart of a past
     * ride read it, so accepting one auto-FTP breakthrough redrew the zone bands
     * of the rider's entire history and gave no sign that anything had changed.
     *
     * **Nullable, no default, and deliberately not backfilled.** Setting it to
     * the profile's current FTP would freeze today's number into every past ride
     * as though it had been recorded at the time, which is worse than not
     * knowing: the reader can tell "we did not write this down" from "this is
     * what it was", and it can say so. Same reasoning as
     * `power_is_measured`'s, and the opposite of `household_visible`'s — the
     * rule underneath all three is to pick the value that is *true of the rows
     * that already exist*.
     */
    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `workouts` ADD COLUMN `ftp_watts` INTEGER")
        }
    }

    /**
     * Adds the `ftp_history` table, and seeds it (PLAN 7.9).
     *
     * FTP is the app's one genuine fitness measure, it is recomputed for free
     * after every ride, and until now the previous value was overwritten and
     * gone. A history cannot be derived afterwards from a column that was
     * destroyed on update, so it has to start being recorded — and the first
     * row of each rider's has to be **seeded here**, because otherwise every
     * existing rider's trend chart begins at their *second* FTP change (7.9.6).
     *
     * Two details in that seed are deliberate. It is dated to the profile's own
     * `created_at` rather than to the migration, because that is when the number
     * was actually true of the rider; and it is marked `Unknown` rather than
     * `ProfileCreated`, because a profile whose FTP has been edited four times
     * since is not being described accurately by either "created" or by any
     * other label this migration could invent. The value is known, the reason
     * is not, and the enum has a case for exactly that.
     *
     * `workout_id` is `ON DELETE SET NULL`: deleting a ride must not delete the
     * fact that the rider's FTP changed. `local_user_id` is `CASCADE`, because
     * an FTP history means nothing once there is no rider it is about — the
     * opposite call to `workouts.user_id`'s, and for a real difference. A ride
     * is a record of something that happened and survives its rider; a history
     * of somebody's FTP is a statement about somebody.
     */
    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `ftp_history` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `local_user_id` INTEGER NOT NULL,
                    `ftp_watts` INTEGER NOT NULL,
                    `changed_at` INTEGER NOT NULL,
                    `source` TEXT NOT NULL,
                    `workout_id` TEXT,
                    FOREIGN KEY(`local_user_id`) REFERENCES `profiles`(`local_user_id`)
                        ON UPDATE NO ACTION ON DELETE CASCADE ,
                    FOREIGN KEY(`workout_id`) REFERENCES `workouts`(`id`)
                        ON UPDATE NO ACTION ON DELETE SET NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_ftp_history_local_user_id` " +
                    "ON `ftp_history` (`local_user_id`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_ftp_history_workout_id` " +
                    "ON `ftp_history` (`workout_id`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_ftp_history_changed_at` " +
                    "ON `ftp_history` (`changed_at`)"
            )
            db.execSQL(
                """
                INSERT INTO `ftp_history` (`local_user_id`, `ftp_watts`, `changed_at`, `source`, `workout_id`)
                SELECT `local_user_id`, `ftp_watts`, `created_at`, 'Unknown', NULL FROM `profiles`
                """.trimIndent()
            )
        }
    }

    /**
     * Remembers that the rider said no to a breakthrough (PLAN 7.10.5).
     *
     * `PostRideViewModel` runs the analyser on every load, so declining cleared
     * a field in memory and nothing else: closing the summary and reopening it
     * asked again about a ride the rider had already answered for. Asked often
     * enough, "no" stops being a decision and becomes a thing to tap past.
     *
     * A column on `workouts` rather than a preference, because it is a fact
     * about a ride: it travels in the backup, and it goes away when the ride
     * does. `NOT NULL DEFAULT 0` rather than nullable — unlike
     * `power_is_measured`, "never asked" and "asked and said no" do not make
     * different claims here. Both mean the app has no answer on file, and both
     * behave the same way.
     */
    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE `workouts` ADD COLUMN `ftp_proposal_declined` " +
                    "INTEGER NOT NULL DEFAULT 0"
            )
        }
    }

    /**
     * `workouts.synced_at` — when the cloud last accepted this ride (14.2.4).
     *
     * Nullable, and **not backfilled**. Every ride already on a tablet is
     * therefore "never synced", which is right for almost all of them and
     * deliberately conservative for the handful that did go up during the
     * seventh sitting: those were uploaded anonymously, under the pre-14.2.1
     * shape, with no `user_id` on them, so a re-upload attributed to a real
     * account is the outcome we want anyway.
     *
     * The alternative — stamping `NOW()` on every existing row — would claim
     * the whole local history was backed up, which is the exact false
     * reassurance this column exists to prevent. The 7.8 rule again: a
     * backfilled guess is indistinguishable from a recorded fact.
     */
    val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `workouts` ADD COLUMN `synced_at` INTEGER DEFAULT NULL")
        }
    }

    /**
     * `workouts.resume_count` and `workouts.interrupted_sec` — a ride that was
     * picked up again after a crash says so (8.3d.2).
     *
     * Both `NOT NULL DEFAULT 0`, and here the backfill is a **fact** rather
     * than a guess: every ride already on a tablet was never resumed, because
     * resuming did not exist until this migration. That is the distinction 9→10
     * turned on — `synced_at` was left null because stamping it would have
     * claimed something untrue about rides nobody had checked. Zero here claims
     * only what is certainly the case.
     *
     * Two columns rather than one because they are two questions. The count
     * answers *was this ride continuous?*; the seconds answer *how much of it is
     * missing?*, and a resume ten seconds after a crash and one twenty minutes
     * after are the same count and very different rides.
     */
    val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE `workouts` ADD COLUMN `resume_count` INTEGER NOT NULL DEFAULT 0"
            )
            db.execSQL(
                "ALTER TABLE `workouts` ADD COLUMN `interrupted_sec` INTEGER NOT NULL DEFAULT 0"
            )
        }
    }

    /**
     * 11 → 12: what heart-rate zones are computed from (21.1.1, 21.1.3).
     *
     * Two nullable columns on `profiles`, and **nullable is the decision**, not
     * an oversight. The contrast with 10 → 11 is the whole reasoning: there,
     * `resume_count` took `NOT NULL DEFAULT 0` because zero stated a *fact* —
     * no ride already on a tablet had been resumed, since resuming did not
     * exist. There is no equivalent fact here. Any default maximum heart rate
     * would be a **guess about a rider's body**, silently prescribing zones off
     * a number nobody gave, which is the same family of mistake as defaulting
     * an absent heart rate to zero. A default is safe exactly when it states a
     * fact rather than a guess.
     *
     * So every existing profile comes out of this migration with no heart-rate
     * zones at all, and that is the correct answer until they are asked.
     */
    val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `profiles` ADD COLUMN `max_hr_bpm` INTEGER")
            db.execSQL("ALTER TABLE `profiles` ADD COLUMN `birth_date` INTEGER")
        }
    }

    /**
     * 12 → 13: the maximum heart rate a ride was ridden at (21.2.3).
     *
     * The twin of 6 → 7, which added `workouts.ftp_watts` for exactly this
     * reason: a zone drawn from a denominator that has moved since is a record
     * editing itself behind the rider. 11 → 12 gave `profiles` a maximum heart
     * rate, and the moment anything draws a heart-rate zone for a *past* ride
     * (21.4.2) that number becomes the same trap the FTP was.
     *
     * Nullable, and **not backfilled**, for 11 → 12's reason repeated one table
     * along: filling last summer's rides with the number the rider gave the app
     * this morning would look exactly like data and be a guess. A ride with no
     * maximum of its own is drawn from the rider's current one *and says so*.
     */
    val MIGRATION_12_13 = object : Migration(12, 13) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `workouts` ADD COLUMN `max_hr_bpm` INTEGER")
        }
    }

    val ALL: Array<Migration> = arrayOf(
        MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4,
        MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8,
        MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12,
        MIGRATION_12_13
    )
}
