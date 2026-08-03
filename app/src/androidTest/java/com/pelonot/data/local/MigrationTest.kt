package com.pelonot.data.local

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Migrations, run against real SQLite files rather than reasoned about.
 *
 * `MigrationTestHelper` creates a database at the *old* schema — read from the
 * exported JSON in `app/schemas/`, which is why that directory is checked in
 * and wired into `androidTest` assets — runs the migration, and then validates
 * the result against the new schema. A migration that produces almost the right
 * table fails here instead of on a rider's tablet.
 *
 * The point of these tests is not the SQL, which is one line. It is that the
 * rows written before the migration are still there afterwards, which is the
 * only property `fallbackToDestructiveMigration()` ever failed.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate1To2_keepsExistingRidesAndDefaultsThemToNotRecovered() {
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL(
                """
                INSERT INTO profiles (local_user_id, name, weight_kg, ftp_watts, created_at)
                VALUES (1, 'Test Rider', 72.0, 210, 1000)
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO workouts (
                    id, user_id, class_id, duration_sec, total_output_kj,
                    total_distance_km, avg_cadence, avg_power, avg_hr,
                    intent_modifier, rpe_rating, is_complete, timestamp
                ) VALUES ('w1', 1, NULL, 1800, 150.0, 10.0, 90.0, 200.0, 150.0, 1.0, 7, 1, 2000)
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO workout_metrics (workout_id, timestamp_sec, cadence, resistance, power, heart_rate)
                VALUES ('w1', 0, 90.0, 40.0, 200.0, 140)
                """.trimIndent()
            )
        }

        // Room validates the migrated schema against 2.json as part of this call.
        helper.runMigrationsAndValidate(TEST_DB, 2, true, AppMigrations.MIGRATION_1_2)

        val migrated = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
            TEST_DB
        )
            .addMigrations(*AppMigrations.ALL)
            .build()

        try {
            migrated.openHelper.readableDatabase.query(
                "SELECT total_output_kj, rpe_rating, was_recovered FROM workouts WHERE id = 'w1'"
            ).use { cursor ->
                assertTrue("the ride recorded before the migration is gone", cursor.moveToFirst())
                assertEquals(150.0, cursor.getDouble(0), 0.001)
                assertEquals(7, cursor.getInt(1))
                assertEquals("a pre-existing ride is not a recovered one", 0, cursor.getInt(2))
            }

            // The cascade relationship has to survive a table alteration too —
            // an ALTER that recreated the table would quietly drop it.
            migrated.openHelper.readableDatabase.query(
                "SELECT COUNT(*) FROM workout_metrics WHERE workout_id = 'w1'"
            ).use { cursor ->
                cursor.moveToFirst()
                assertEquals(1, cursor.getInt(0))
            }
        } finally {
            migrated.close()
        }
    }

    /**
     * The consent gate arriving over a tablet that already has riders on it.
     *
     * `auth_user_id IS NULL` for every one of them is not a convenient default:
     * none of them was ever asked, none of them ever signed in, and the column
     * has to say so. If this ever came out non-null, the migration itself would
     * have granted an account nobody created — and the first thing that would
     * happen is a ride upload on their behalf.
     */
    @Test
    fun migrate2To3_leavesEveryExistingProfileWithoutAnAccount() {
        helper.createDatabase(TEST_DB, 2).use { db ->
            db.execSQL(
                """
                INSERT INTO profiles (local_user_id, name, weight_kg, ftp_watts, created_at)
                VALUES (1, 'Test Rider', 72.0, 210, 1000), (2, 'Housemate', 64.0, 180, 1100)
                """.trimIndent()
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 3, true, AppMigrations.MIGRATION_2_3)

        val migrated = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
            TEST_DB
        )
            .addMigrations(*AppMigrations.ALL)
            .build()

        try {
            migrated.openHelper.readableDatabase.query(
                "SELECT name, ftp_watts, auth_user_id FROM profiles ORDER BY local_user_id"
            ).use { cursor ->
                assertTrue("the profiles that existed before the migration are gone", cursor.moveToFirst())
                assertEquals("Test Rider", cursor.getString(0))
                assertEquals(210, cursor.getInt(1))
                assertTrue("an existing profile must not arrive with an account", cursor.isNull(2))

                assertTrue(cursor.moveToNext())
                assertEquals("Housemate", cursor.getString(0))
                assertTrue("an existing profile must not arrive with an account", cursor.isNull(2))
            }
        } finally {
            migrated.close()
        }
    }

    /**
     * The provenance column arriving over rides that already exist.
     *
     * `NULL`, not 0 and not 1. `DEFAULT 0` would say the model produced watts
     * that came off a real bike; `DEFAULT 1` would do the reverse and worse,
     * making a simulated ride eligible to propose an FTP (7.10.7) and to rank
     * beside a real one (24.4.2). Null says the true thing: nobody wrote it
     * down.
     */
    @Test
    fun migrate3To4_leavesExistingSamplesWithNoClaimAboutTheirPower() {
        helper.createDatabase(TEST_DB, 3).use { db ->
            db.execSQL(
                """
                INSERT INTO profiles (local_user_id, name, weight_kg, ftp_watts, created_at, auth_user_id)
                VALUES (1, 'Test Rider', 72.0, 210, 1000, NULL)
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO workouts (
                    id, user_id, class_id, duration_sec, total_output_kj,
                    total_distance_km, avg_cadence, avg_power, avg_hr,
                    intent_modifier, rpe_rating, is_complete, timestamp, was_recovered
                ) VALUES ('w1', 1, NULL, 1800, 150.0, 10.0, 90.0, 200.0, 150.0, 1.0, 7, 1, 2000, 0)
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO workout_metrics (workout_id, timestamp_sec, cadence, resistance, power, heart_rate)
                VALUES ('w1', 0, 90.0, 40.0, 200.0, 140), ('w1', 1, 91.0, 40.0, 205.0, 141)
                """.trimIndent()
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 4, true, AppMigrations.MIGRATION_3_4)

        val migrated = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
            TEST_DB
        )
            .addMigrations(*AppMigrations.ALL)
            .build()

        try {
            migrated.openHelper.readableDatabase.query(
                "SELECT power, power_is_measured FROM workout_metrics WHERE workout_id = 'w1' ORDER BY timestamp_sec"
            ).use { cursor ->
                assertTrue("the samples recorded before the migration are gone", cursor.moveToFirst())
                assertEquals(200.0, cursor.getDouble(0), 0.001)
                assertTrue("an existing sample must claim nothing about its power", cursor.isNull(1))

                assertTrue(cursor.moveToNext())
                assertTrue("an existing sample must claim nothing about its power", cursor.isNull(1))
            }
        } finally {
            migrated.close()
        }
    }

    /**
     * The retirement column arriving over a tablet that has already ridden one
     * of the classes about to be retired.
     *
     * This is the bike's actual situation: `HC-01` is the class of the first
     * real ride, and 23.2.6 rebuilt the library under a new set of ids. The
     * migration itself must leave every class live — retiring is
     * `ClassTemplateSeeder`'s decision, made against the bundle, not something
     * a schema change is allowed to do on its own.
     */
    @Test
    fun migrate4To5_leavesEveryExistingClassInTheLibrary() {
        helper.createDatabase(TEST_DB, 4).use { db ->
            db.execSQL(
                """
                INSERT INTO class_templates (id, title, category, duration_sec, intervals_json, created_at)
                VALUES ('HC-01', 'Hill Grind 20', 'HIIT & Heavy Climbs', 1200, '[]', 1000)
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO workouts (
                    id, user_id, class_id, duration_sec, total_output_kj,
                    total_distance_km, avg_cadence, avg_power, avg_hr,
                    intent_modifier, rpe_rating, is_complete, timestamp, was_recovered
                ) VALUES ('w1', NULL, 'HC-01', 1200, 150.0, 10.0, 90.0, 200.0, 150.0, 1.0, 7, 1, 2000, 0)
                """.trimIndent()
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 5, true, AppMigrations.MIGRATION_4_5)

        val migrated = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
            TEST_DB
        )
            .addMigrations(*AppMigrations.ALL)
            .build()

        try {
            migrated.openHelper.readableDatabase.query(
                "SELECT title, retired_at FROM class_templates WHERE id = 'HC-01'"
            ).use { cursor ->
                assertTrue("the class that existed before the migration is gone", cursor.moveToFirst())
                assertEquals("Hill Grind 20", cursor.getString(0))
                assertTrue("a migration must not retire anything by itself", cursor.isNull(1))
            }

            // The link that retiring exists to protect.
            migrated.openHelper.readableDatabase.query(
                "SELECT class_id FROM workouts WHERE id = 'w1'"
            ).use { cursor ->
                cursor.moveToFirst()
                assertEquals("HC-01", cursor.getString(0))
            }
        } finally {
            migrated.close()
        }
    }

    /**
     * The household opt-out arriving over profiles that already exist.
     *
     * `1`, not `0`, and the reasoning is the opposite of `auth_user_id`'s while
     * the underlying rule is the same: **pick the value that is true of the
     * rows that exist**. Nobody had ever consented to the cloud, so that column
     * arrived null. Everybody was already on 24.1's household leaderboard, so
     * this one arrives visible — defaulting to hidden would be a schema change
     * quietly removing riders from a board they are on today.
     */
    @Test
    fun migrate5To6_leavesExistingProfilesVisibleToTheirHousehold() {
        helper.createDatabase(TEST_DB, 5).use { db ->
            db.execSQL(
                """
                INSERT INTO profiles (local_user_id, name, weight_kg, ftp_watts, created_at, auth_user_id)
                VALUES (1, 'Test Rider', 72.0, 210, 1000, NULL), (2, 'Housemate', 64.0, 180, 1100, NULL)
                """.trimIndent()
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 6, true, AppMigrations.MIGRATION_5_6)

        val migrated = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
            TEST_DB
        )
            .addMigrations(*AppMigrations.ALL)
            .build()

        try {
            migrated.openHelper.readableDatabase.query(
                "SELECT name, household_visible FROM profiles ORDER BY local_user_id"
            ).use { cursor ->
                assertTrue("the profiles that existed before the migration are gone", cursor.moveToFirst())
                assertEquals("Test Rider", cursor.getString(0))
                assertEquals("a rider already on the board must stay on it", 1, cursor.getInt(1))

                assertTrue(cursor.moveToNext())
                assertEquals("Housemate", cursor.getString(0))
                assertEquals("a rider already on the board must stay on it", 1, cursor.getInt(1))
            }
        } finally {
            migrated.close()
        }
    }

    /**
     * The FTP a ride was ridden at, arriving over rides that never recorded it.
     *
     * **The assertion that matters is that it is null**, and it is the whole of
     * 7.8.2: backfilling these rows with the profile's current FTP would be
     * indistinguishable afterwards from a ride that really had been recorded at
     * that number, and it would bake one particular day's guess into the
     * rider's entire history. Null is the only honest value, and it is what lets
     * the chart say "zones from your FTP today" instead of quietly implying
     * otherwise.
     */
    @Test
    fun migrate6To7_leavesExistingRidesWithNoClaimAboutTheFtpTheyWereRiddenAt() {
        helper.createDatabase(TEST_DB, 6).use { db ->
            db.execSQL(
                """
                INSERT INTO profiles (local_user_id, name, weight_kg, ftp_watts, created_at, auth_user_id, household_visible)
                VALUES (1, 'Test Rider', 72.0, 210, 1000, NULL, 1)
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO workouts (
                    id, user_id, class_id, duration_sec, total_output_kj, total_distance_km,
                    avg_cadence, avg_power, avg_hr, intent_modifier, rpe_rating,
                    is_complete, was_recovered, timestamp
                ) VALUES ('w1', 1, NULL, 1200, 180.0, 8.0, 85.0, 150.0, 140.0, 1.0, NULL, 1, 0, 1000)
                """.trimIndent()
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 7, true, AppMigrations.MIGRATION_6_7)

        val migrated = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
            TEST_DB
        )
            .addMigrations(*AppMigrations.ALL)
            .build()

        try {
            migrated.openHelper.readableDatabase.query(
                "SELECT id, ftp_watts, total_output_kj FROM workouts"
            ).use { cursor ->
                assertTrue("the ride that existed before the migration is gone", cursor.moveToFirst())
                assertEquals("w1", cursor.getString(0))
                assertTrue(
                    "a ride recorded before the column existed must not claim an FTP",
                    cursor.isNull(1)
                )
                assertEquals(180.0, cursor.getDouble(2), 0.001)
            }
        } finally {
            migrated.close()
        }
    }

    /**
     * The FTP history table arriving, and the row it seeds for each rider.
     *
     * The seeded row is the point (7.9.6). Without it, every rider who already
     * exists has a trend chart that begins at their **second** FTP change — the
     * first value they ever had is on `profiles` and nowhere else, and a history
     * cannot be derived from a column that is overwritten on update.
     *
     * Dated to the profile's own `created_at` rather than to the migration,
     * because that is when the number was true of the rider, and marked
     * `Unknown` rather than `ProfileCreated`: a profile whose FTP has been
     * edited four times since is not described by either, and the enum has a
     * case for exactly "nobody wrote it down".
     */
    @Test
    fun migrate7To8_seedsEachRidersFirstFtpFromTheProfileTheyAlreadyHave() {
        helper.createDatabase(TEST_DB, 7).use { db ->
            db.execSQL(
                """
                INSERT INTO profiles (local_user_id, name, weight_kg, ftp_watts, created_at, auth_user_id, household_visible)
                VALUES (1, 'Test Rider', 72.0, 210, 1000, NULL, 1), (2, 'Housemate', 64.0, 185, 2000, NULL, 1)
                """.trimIndent()
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 8, true, AppMigrations.MIGRATION_7_8)

        val migrated = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
            TEST_DB
        )
            .addMigrations(*AppMigrations.ALL)
            .build()

        try {
            migrated.openHelper.readableDatabase.query(
                "SELECT local_user_id, ftp_watts, changed_at, source, workout_id " +
                    "FROM ftp_history ORDER BY local_user_id"
            ).use { cursor ->
                assertTrue("no history was seeded at all", cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
                assertEquals(210, cursor.getInt(1))
                assertEquals(
                    "the seed must be dated to when the number was true, not to the migration",
                    1000L,
                    cursor.getLong(2)
                )
                assertEquals("Unknown", cursor.getString(3))
                assertTrue("nothing caused this one", cursor.isNull(4))

                assertTrue("only one rider was seeded", cursor.moveToNext())
                assertEquals(2, cursor.getInt(0))
                assertEquals(185, cursor.getInt(1))
                assertEquals(2000L, cursor.getLong(2))

                assertFalse("one row per rider, not more", cursor.moveToNext())
            }
        } finally {
            migrated.close()
        }
    }

    /**
     * 7.9.3, checked against the database rather than reasoned about: deleting a
     * ride must not delete the fact that the rider's FTP changed.
     *
     * The counterpart matters as much — the profile reference is `CASCADE` — so
     * both directions are asserted here. This project has had three
     * delete-plus-insert defects and one live one; a foreign-key action is
     * cheap to check and expensive to be wrong about.
     */
    @Test
    fun deletingTheRideThatMovedAnFtpKeepsTheFtpChange() {
        helper.createDatabase(TEST_DB, 7).use { db ->
            db.execSQL(
                """
                INSERT INTO profiles (local_user_id, name, weight_kg, ftp_watts, created_at, auth_user_id, household_visible)
                VALUES (1, 'Test Rider', 72.0, 210, 1000, NULL, 1)
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO workouts (
                    id, user_id, class_id, duration_sec, total_output_kj, total_distance_km,
                    avg_cadence, avg_power, avg_hr, intent_modifier, rpe_rating,
                    is_complete, was_recovered, timestamp, ftp_watts
                ) VALUES ('w1', 1, NULL, 1200, 180.0, 8.0, 85.0, 150.0, 140.0, 1.0, NULL, 1, 0, 1000, 200)
                """.trimIndent()
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 8, true, AppMigrations.MIGRATION_7_8)

        val migrated = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
            TEST_DB
        )
            .addMigrations(*AppMigrations.ALL)
            .build()

        try {
            val db = migrated.openHelper.writableDatabase
            db.execSQL(
                "INSERT INTO ftp_history (local_user_id, ftp_watts, changed_at, source, workout_id) " +
                    "VALUES (1, 225, 3000, 'AutoBreakthrough', 'w1')"
            )

            db.execSQL("DELETE FROM workouts WHERE id = 'w1'")

            db.query("SELECT ftp_watts, workout_id FROM ftp_history WHERE changed_at = 3000")
                .use { cursor ->
                    assertTrue(
                        "deleting the ride took the FTP change with it",
                        cursor.moveToFirst()
                    )
                    assertEquals(225, cursor.getInt(0))
                    assertTrue("the ride reference should be nulled, not kept", cursor.isNull(1))
                }

            db.execSQL("DELETE FROM profiles WHERE local_user_id = 1")

            db.query("SELECT COUNT(*) FROM ftp_history").use { cursor ->
                cursor.moveToFirst()
                assertEquals(
                    "an FTP history is a statement about a rider and goes with them",
                    0,
                    cursor.getInt(0)
                )
            }
        } finally {
            migrated.close()
        }
    }

    /**
     * The declined-proposal flag arriving (7.10.5).
     *
     * The direction that matters is the safe one: a ride recorded before the
     * column existed has **not** been declined, so a rider who has a genuine
     * breakthrough sitting in an old ride is still offered it. Defaulting the
     * other way would silently swallow every proposal the app has ever been
     * about to make.
     */
    @Test
    fun migrate8To9_leavesExistingRidesStillAbleToOfferABreakthrough() {
        helper.createDatabase(TEST_DB, 8).use { db ->
            db.execSQL(
                """
                INSERT INTO profiles (local_user_id, name, weight_kg, ftp_watts, created_at, auth_user_id, household_visible)
                VALUES (1, 'Test Rider', 72.0, 210, 1000, NULL, 1)
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO workouts (
                    id, user_id, class_id, duration_sec, total_output_kj, total_distance_km,
                    avg_cadence, avg_power, avg_hr, intent_modifier, rpe_rating,
                    is_complete, was_recovered, timestamp, ftp_watts
                ) VALUES ('w1', 1, NULL, 1200, 180.0, 8.0, 85.0, 150.0, 140.0, 1.0, NULL, 1, 0, 1000, 200)
                """.trimIndent()
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 9, true, AppMigrations.MIGRATION_8_9)

        val migrated = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
            TEST_DB
        )
            .addMigrations(*AppMigrations.ALL)
            .build()

        try {
            migrated.openHelper.readableDatabase
                .query("SELECT ftp_proposal_declined, total_output_kj FROM workouts WHERE id = 'w1'")
                .use { cursor ->
                    assertTrue("the ride that existed before the migration is gone", cursor.moveToFirst())
                    assertEquals(
                        "a ride nobody was ever asked about must not count as declined",
                        0,
                        cursor.getInt(0)
                    )
                    assertEquals(180.0, cursor.getDouble(1), 0.001)
                }
        } finally {
            migrated.close()
        }
    }

    /**
     * 9 → 10 adds `workouts.synced_at` (PLAN 14.2.4).
     *
     * **Nothing is backfilled, and that is the assertion.** Stamping the
     * migration's own clock onto every existing row would be one line and would
     * claim the rider's entire local history was safely in the cloud — the
     * exact false reassurance the column exists to prevent. Null means "the
     * cloud has never had this ride", every existing ride is one, and a couple
     * that genuinely did go up during the seventh sitting went up *anonymously*
     * under the pre-14.2.1 shape, so re-uploading them attributed to a real
     * account is the outcome wanted anyway.
     *
     * Same family as 6 → 7's `ftp_watts` and 3 → 4's `power_is_measured`: a
     * backfilled guess is indistinguishable from a recorded fact, and the
     * indistinguishable part is what makes it dangerous rather than merely
     * wrong.
     */
    @Test
    fun migrate9To10_claimsNothingAboutWhatTheCloudAlreadyHas() {
        helper.createDatabase(TEST_DB, 9).use { db ->
            db.execSQL(
                """
                INSERT INTO profiles (local_user_id, name, weight_kg, ftp_watts, created_at, auth_user_id, household_visible)
                VALUES (1, 'Test Rider', 72.0, 210, 1000, 'auth-uuid-0001', 1)
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO workouts (
                    id, user_id, class_id, duration_sec, total_output_kj, total_distance_km,
                    avg_cadence, avg_power, avg_hr, intent_modifier, rpe_rating,
                    is_complete, was_recovered, timestamp, ftp_watts, ftp_proposal_declined
                ) VALUES ('w1', 1, NULL, 1200, 180.0, 8.0, 85.0, 150.0, 140.0, 1.0, NULL, 1, 0, 1000, 200, 0)
                """.trimIndent()
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 10, true, AppMigrations.MIGRATION_9_10)

        val migrated = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
            TEST_DB
        )
            .addMigrations(*AppMigrations.ALL)
            .build()

        try {
            migrated.openHelper.readableDatabase
                .query("SELECT synced_at, total_output_kj FROM workouts WHERE id = 'w1'")
                .use { cursor ->
                    assertTrue("the ride that existed before the migration is gone", cursor.moveToFirst())
                    assertTrue(
                        "an existing ride must not claim to be backed up",
                        cursor.isNull(0)
                    )
                    assertEquals(180.0, cursor.getDouble(1), 0.001)
                }

            // And it is therefore in the backlog, which is the point of the
            // column rather than a separate fact about it.
            migrated.openHelper.readableDatabase
                .query(
                    "SELECT COUNT(*) FROM workouts " +
                        "WHERE user_id = 1 AND is_complete = 1 AND synced_at IS NULL"
                )
                .use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals(1, cursor.getInt(0))
                }
        } finally {
            migrated.close()
        }
    }

    /**
     * 10 → 11: `resume_count` and `interrupted_sec` (8.3d.2).
     *
     * The mirror image of the test above, and the contrast is the point. 9 → 10
     * left `synced_at` null because stamping it would have *claimed* something
     * about rides nobody had checked. Zero here claims only what is certainly
     * true: no ride already on a tablet was ever resumed, because resuming did
     * not exist until this migration. A default is safe exactly when it states
     * a fact rather than a guess.
     */
    @Test
    fun migrate10To11_saysEveryExistingRideWasRiddenStraightThrough() {
        helper.createDatabase(TEST_DB, 10).use { db ->
            db.execSQL(
                """
                INSERT INTO profiles (local_user_id, name, weight_kg, ftp_watts, created_at, auth_user_id, household_visible)
                VALUES (1, 'Test Rider', 72.0, 210, 1000, NULL, 1)
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO workouts (
                    id, user_id, class_id, duration_sec, total_output_kj, total_distance_km,
                    avg_cadence, avg_power, avg_hr, intent_modifier, rpe_rating,
                    is_complete, was_recovered, timestamp, ftp_watts, ftp_proposal_declined,
                    synced_at
                ) VALUES ('w1', 1, NULL, 1200, 180.0, 8.0, 85.0, 150.0, 140.0, 1.0, NULL, 1, 0, 1000, 200, 0, NULL)
                """.trimIndent()
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 11, true, AppMigrations.MIGRATION_10_11)

        val migrated = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
            TEST_DB
        )
            .addMigrations(*AppMigrations.ALL)
            .build()

        try {
            migrated.openHelper.readableDatabase
                .query(
                    "SELECT resume_count, interrupted_sec, total_output_kj " +
                        "FROM workouts WHERE id = 'w1'"
                )
                .use { cursor ->
                    assertTrue(
                        "the ride that existed before the migration is gone",
                        cursor.moveToFirst()
                    )
                    assertEquals(0, cursor.getInt(0))
                    assertEquals(0, cursor.getInt(1))
                    // The ride itself is untouched, which is the other half of
                    // what an ALTER TABLE ... ADD COLUMN has to leave true.
                    assertEquals(180.0, cursor.getDouble(2), 0.001)
                }

            // NOT NULL, so a row cannot be written that declines to answer.
            // This is the difference from synced_at stated as a constraint
            // rather than as a comment.
            migrated.openHelper.readableDatabase
                .query("SELECT COUNT(*) FROM workouts WHERE resume_count IS NULL")
                .use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals(0, cursor.getInt(0))
                }
        } finally {
            migrated.close()
        }
    }

    private companion object {
        const val TEST_DB = "migration-test"
    }
}
