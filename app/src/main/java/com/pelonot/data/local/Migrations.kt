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

    /**
     * 13 → 14: how the rider describes their own riding (20.3.7).
     *
     * The last input the FTP estimate needed and the only one that was not
     * already being collected for something else — which is 20.3.9's point:
     * `birth_date` arrived in 11 → 12 for heart-rate zones, `weight_kg` has
     * been here since the beginning, so Route B cost one column rather than
     * the two the item budgeted for.
     *
     * Nullable and not backfilled, for 11 → 12's reason once more. Every
     * profile that already exists was never asked this question, and writing
     * `occasional` onto all of them would put an answer in the rider's mouth
     * — which matters more here than usual, because 20.3.4 makes this column
     * *quotable*: the app is meant to be able to say "you told us you ride
     * regularly", and it must not say that to somebody who told it nothing.
     *
     * Note that `FitnessLevel.DEFAULT` exists and is *not* what this migration
     * writes. The default is what an estimate assumes in the absence of an
     * answer; null is the record of there having been no answer. Those are
     * different facts and the same distinction as `power_is_measured` being
     * nullable — a value nobody wrote down is not a value.
     */
    val MIGRATION_13_14 = object : Migration(13, 14) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `profiles` ADD COLUMN `fitness_level` TEXT")
        }
    }

    /**
     * 14 → 15: whether this profile has said not to be asked about an
     * account again (PLAN 15.8.4).
     *
     * Per profile rather than per tablet, on the same argument as
     * `household_visible` and unlike `hasEverBackedUp` (23.3.1, a device-wide
     * DataStore flag) — a household bike has several riders and only one of
     * them dismissing the offer must not silence it for the others.
     *
     * `NOT NULL DEFAULT 0`: every profile that already exists has never seen
     * the offer, so *not dismissed* is a fact rather than a guess, the same
     * shape as `household_visible`'s backfill.
     */
    val MIGRATION_14_15 = object : Migration(14, 15) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE `profiles` ADD COLUMN `account_offer_dismissed` " +
                    "INTEGER NOT NULL DEFAULT 0"
            )
        }
    }

    /**
     * 15 → 16: which rival a ride in progress is racing (PLAN 24.3.8).
     *
     * A new table rather than a column on `workouts`, for the reason written
     * out on [com.pelonot.data.local.entity.ActiveRideRivalEntity]: anything
     * added to `workouts` for this would be silently reverted by
     * `stopWorkout`'s fresh rebuild (8.3d.4), and this choice specifically has
     * to survive the opposite event — a crash *before* the ride ends. No
     * backfill: no ride in progress at migration time can have a row, because
     * the app was not running to write one.
     */
    val MIGRATION_15_16 = object : Migration(15, 16) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `active_ride_rival` (
                    `workout_id` TEXT NOT NULL,
                    `rival_workout_id` TEXT NOT NULL,
                    PRIMARY KEY(`workout_id`),
                    FOREIGN KEY(`workout_id`) REFERENCES `workouts`(`id`)
                        ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(`rival_workout_id`) REFERENCES `workouts`(`id`)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_active_ride_rival_rival_workout_id` " +
                    "ON `active_ride_rival` (`rival_workout_id`)"
            )
        }
    }

    /**
     * 16 → 17: what a class is *for* (PLAN 23.2.7).
     *
     * `NOT NULL DEFAULT ''` rather than nullable, because there is no third
     * claim to make: a class either carries a description or does not, and both
     * draw as nothing. Not backfilled, and it does not need to be —
     * `ClassTemplateSeeder` upserts every bundled class whenever the bundle's
     * fingerprint moves, and adding 72 descriptions moved it. So an existing
     * tablet gets them on its next launch, from the assets it already has.
     *
     * A **retired** class keeps its empty string for ever, which is correct:
     * it is out of the library and only ever resolved by id from history, where
     * nothing draws a description.
     */
    val MIGRATION_16_17 = object : Migration(16, 17) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE `class_templates` ADD COLUMN `description` " +
                    "TEXT NOT NULL DEFAULT ''"
            )
        }
    }

    /**
     * 17 → 18: a ride's mean-maximal efforts, kept rather than re-derived
     * (PLAN 16.3.3a).
     *
     * `workout_power_bests` holds one row per stretch a ride actually held, and
     * `workouts.power_bests_at` says when that scan ran — null for every ride
     * that predates this, which is correct and is why there is **no backfill in
     * SQL**. Mean-maximal power is a sliding window over a series with gaps in
     * it; it is not expressible here, and inventing an approximation for old
     * rides would fill the rider's record with numbers no version of the app
     * would ever compute again. `WorkoutRepository.personalBests` scans the
     * unscanned ones on its next run instead, which is exactly the work the old
     * shape did on *every* run.
     *
     * The cascade is deliberate: an effort is meaningless without the ride it
     * was ridden in, and `discardWorkout` already relies on that for
     * `workout_metrics`.
     */
    val MIGRATION_17_18 = object : Migration(17, 18) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `workouts` ADD COLUMN `power_bests_at` INTEGER")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `workout_power_bests` (
                    `workout_id` TEXT NOT NULL,
                    `window_sec` INTEGER NOT NULL,
                    `watts` REAL NOT NULL,
                    PRIMARY KEY(`workout_id`, `window_sec`),
                    FOREIGN KEY(`workout_id`) REFERENCES `workouts`(`id`)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_workout_power_bests_workout_id` " +
                    "ON `workout_power_bests` (`workout_id`)"
            )
        }
    }

    /**
     * 18 → 19: where a ride's watts came from, written on the ride (PLAN
     * 23.4.12).
     *
     * The column and nothing else. **The backfill is deliberately not here**,
     * and for the opposite reason to 17 → 18's: not because SQL cannot express
     * it — a reduction over `power_is_measured` is four `CASE` branches and is
     * exactly expressible — but because there is nowhere else to put a pass that
     * has to be able to run *again*. A ride finalised by a build that crashed
     * between the update and the write has a null here, and so does every ride
     * still being pedalled; a migration runs once and can answer for neither.
     * `WorkoutDao.backfillPowerProvenance` is one statement, runs at launch,
     * and finds nothing after the first time — so it is the migration's
     * backfill *and* the self-heal, in one place rather than two copies of one
     * `CASE`.
     *
     * That matters more here than it did for the bests, because this column
     * **gates six leaderboards**. A ride the backfill has not reached yet is
     * off all of them, so the pass being re-runnable is the difference between
     * a gap that closes itself and a rider's history quietly shrinking.
     */
    val MIGRATION_18_19 = object : Migration(18, 19) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `workouts` ADD COLUMN `power_provenance` TEXT")
        }
    }

    /**
     * 19 → 20: a ride that has been trimmed says so, and keeps what its seconds
     * counted (PLAN 23.4.2, 23.4.3).
     *
     * Two columns, both null for every ride that exists — which is the correct
     * backfill and needs no pass at all, unlike 18 → 19's. Null on
     * `metrics_detail_sec` means *one row per second, as recorded*, and no ride
     * on any tablet today is anything else: trimming is off by default and has
     * to be turned on by the rider before a single row is dropped (23.4.4).
     *
     * `distributions_json` is written by the trimmer at the same instant, and
     * only by it. An untrimmed ride has none because its own samples are a
     * better answer than a summary of them.
     */
    val MIGRATION_19_20 = object : Migration(19, 20) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `workouts` ADD COLUMN `metrics_detail_sec` INTEGER")
            db.execSQL("ALTER TABLE `workouts` ADD COLUMN `distributions_json` TEXT")
        }
    }

    /**
     * 20 → 21: where a ride's maximum heart rate came from (PLAN 21.4.2c).
     *
     * `max_hr_bpm` arrived in 12 → 13 and stored the **number alone**, so a zone
     * drawn off Tanaka and a zone drawn off the rider's own measurement have
     * been presented with one authority ever since — the thing 7.8, 21.2.3 and
     * 23.4.12 each exist to refuse. `MaxHeartRate` has carried a `source` since
     * the day it was written; only the row did not.
     *
     * Nullable and **not backfilled**, for 12 → 13's reason exactly: the wrong
     * fix is available and tempting. Resolving the source from the rider's
     * *current* profile would be right for the rides that have no maximum of
     * their own — they are already drawn from today's number and say so — and a
     * silent guess for every ride that carries one, since the profile may have
     * moved between then and now. So an old ride's provenance stays
     * unrecoverable and the screens say nothing about it, which is honest.
     */
    val MIGRATION_20_21 = object : Migration(20, 21) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `workouts` ADD COLUMN `max_hr_source` TEXT")
        }
    }

    val ALL: Array<Migration> = arrayOf(
        MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4,
        MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8,
        MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12,
        MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16,
        MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20,
        MIGRATION_20_21
    )
}
