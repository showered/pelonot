package com.pelonot.data.local

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pelonot.domain.model.PowerProvenance
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    /**
     * 11 → 12: `max_hr_bpm` and `birth_date` (21.1.1, 21.1.3).
     *
     * The third case in the pair above, and it lands on the other side from
     * 10 → 11. There is no fact to state here: a default maximum heart rate is
     * a guess about a rider's body, and it would silently prescribe zones off a
     * number nobody gave. So every profile that already exists comes out with
     * both columns null and **no heart-rate zones at all**, which is the honest
     * answer until the rider is asked.
     */
    @Test
    fun migrate11To12_leavesExistingRidersWithNoMaximumRatherThanAGuess() {
        helper.createDatabase(TEST_DB, 11).use { db ->
            db.execSQL(
                """
                INSERT INTO profiles (local_user_id, name, weight_kg, ftp_watts, created_at, auth_user_id, household_visible)
                VALUES (1, 'Test Rider', 72.0, 210, 1000, NULL, 1)
                """.trimIndent()
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 12, true, AppMigrations.MIGRATION_11_12)

        val migrated = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
            TEST_DB
        )
            .addMigrations(*AppMigrations.ALL)
            .build()

        try {
            migrated.openHelper.readableDatabase
                .query("SELECT max_hr_bpm, birth_date, ftp_watts FROM profiles WHERE local_user_id = 1")
                .use { cursor ->
                    assertTrue("the profile that existed before the migration is gone", cursor.moveToFirst())
                    assertTrue("a maximum heart rate was invented", cursor.isNull(0))
                    assertTrue("a date of birth was invented", cursor.isNull(1))
                    // The rider is otherwise untouched, which is the other half
                    // of what an ADD COLUMN has to leave true.
                    assertEquals(210, cursor.getInt(2))
                }
        } finally {
            migrated.close()
        }
    }

    /**
     * 12 → 13: `workouts.max_hr_bpm` (21.2.3).
     *
     * The twin of 6 → 7 and the same claim as 11 → 12 one table along: a ride
     * recorded before this column existed comes out **null**, not filled with
     * whatever maximum the rider has today. Backfilling would put this
     * morning's number on last summer's rides while looking exactly like a
     * measurement — which is the trap 7.8 exists to describe, and the reason
     * `RideCharts.maxHrIsTheRides` says which of the two is being drawn.
     */
    @Test
    fun migrate12To13_leavesOldRidesWithNoMaximumRatherThanTodaysGuess() {
        helper.createDatabase(TEST_DB, 12).use { db ->
            db.execSQL(
                """
                INSERT INTO profiles (local_user_id, name, weight_kg, ftp_watts, created_at,
                                      auth_user_id, household_visible, max_hr_bpm, birth_date)
                VALUES (1, 'Test Rider', 72.0, 210, 1000, NULL, 1, 186, 500)
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO workouts (id, user_id, timestamp, duration_sec, total_output_kj,
                                      total_distance_km, avg_cadence, avg_power, avg_hr,
                                      intent_modifier, is_complete, was_recovered,
                                      ftp_proposal_declined, resume_count, interrupted_sec)
                VALUES ('ride-1', 1, 1000, 1200, 180.0, 8.0, 85.0, 150.0, 132.0,
                        1.0, 1, 0, 0, 0, 0)
                """.trimIndent()
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 13, true, AppMigrations.MIGRATION_12_13)

        val migrated = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
            TEST_DB
        )
            .addMigrations(*AppMigrations.ALL)
            .build()

        try {
            migrated.openHelper.readableDatabase
                .query("SELECT max_hr_bpm, avg_hr FROM workouts WHERE id = 'ride-1'")
                .use { cursor ->
                    assertTrue("the ride that existed before the migration is gone", cursor.moveToFirst())
                    assertTrue(
                        "the rider's maximum today was written onto an old ride",
                        cursor.isNull(0)
                    )
                    // Untouched otherwise, which is the other half of what an
                    // ADD COLUMN has to leave true.
                    assertEquals(132.0, cursor.getDouble(1), 0.001)
                }
        } finally {
            migrated.close()
        }
    }

    /**
     * 13 → 14: `profiles.fitness_level` (20.3.7).
     *
     * 11 → 12's argument once more, and it bites harder here. A profile that
     * existed before this column was never asked how it rides, so it comes out
     * **null** rather than `occasional` — and the reason null matters is that
     * 20.3.4 makes this column *quotable*: the app is meant to be able to say
     * "you told us you ride regularly" beside an estimated FTP. Writing
     * `FitnessLevel.DEFAULT` here would let it say that to somebody who told it
     * nothing.
     *
     * The default and the absence are different facts — the same distinction as
     * a nullable `power_is_measured`, where `Unknown` and `Modelled` are not
     * the same claim.
     */
    @Test
    fun migrate13To14_leavesAnExistingProfileWithNoSelfAssessmentRatherThanADefault() {
        helper.createDatabase(TEST_DB, 13).use { db ->
            db.execSQL(
                """
                INSERT INTO profiles (local_user_id, name, weight_kg, ftp_watts, created_at,
                                      auth_user_id, household_visible, max_hr_bpm, birth_date)
                VALUES (1, 'Test Rider', 72.0, 210, 1000, NULL, 1, 186, 500)
                """.trimIndent()
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 14, true, AppMigrations.MIGRATION_13_14)

        val migrated = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
            TEST_DB
        )
            .addMigrations(*AppMigrations.ALL)
            .build()

        try {
            migrated.openHelper.readableDatabase
                .query("SELECT fitness_level, ftp_watts, birth_date FROM profiles WHERE local_user_id = 1")
                .use { cursor ->
                    assertTrue("the profile that existed before the migration is gone", cursor.moveToFirst())
                    assertTrue(
                        "an answer was put in the rider's mouth",
                        cursor.isNull(0)
                    )
                    // And nothing else moved. The estimate is a creation-time
                    // event (20.3.5) and a migration must not re-run it over a
                    // rider who has been riding on their own number for months.
                    assertEquals(210, cursor.getInt(1))
                    assertEquals(500L, cursor.getLong(2))
                }
        } finally {
            migrated.close()
        }
    }

    /**
     * 14 → 15: `profiles.account_offer_dismissed` (PLAN 15.8.4).
     *
     * `household_visible`'s backfill argument rather than `fitness_level`'s:
     * a profile that existed before this column was never *asked* whether to
     * link an account, so *not dismissed* is a true fact about it rather than
     * a guess — the dashboard's offer is free to show on the very next
     * launch, which is what the migration has to leave in place.
     */
    @Test
    fun migrate14To15_leavesAnExistingProfileNotDismissed() {
        helper.createDatabase(TEST_DB, 14).use { db ->
            db.execSQL(
                """
                INSERT INTO profiles (local_user_id, name, weight_kg, ftp_watts, created_at,
                                      auth_user_id, household_visible, max_hr_bpm, birth_date,
                                      fitness_level)
                VALUES (1, 'Test Rider', 72.0, 210, 1000, NULL, 1, 186, 500, 'regular')
                """.trimIndent()
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 15, true, AppMigrations.MIGRATION_14_15)

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
                    "SELECT account_offer_dismissed, ftp_watts FROM profiles " +
                        "WHERE local_user_id = 1"
                )
                .use { cursor ->
                    assertTrue("the profile that existed before the migration is gone", cursor.moveToFirst())
                    assertFalse(
                        "a rider who was never asked came out as having declined",
                        cursor.getInt(0) != 0
                    )
                    // And nothing else moved.
                    assertEquals(210, cursor.getInt(1))
                }
        } finally {
            migrated.close()
        }
    }

    /**
     * 15 → 16: the `active_ride_rival` table (PLAN 24.3.8).
     *
     * A new table rather than a column, so there is no backfill to check —
     * what this asserts instead is that the rides that already existed are
     * untouched, and that the new table's cascade actually fires. The cascade
     * is the part worth a test: `active_ride_rival` points at `workouts`
     * twice, and this project has had three separate defects from
     * foreign-key actions firing where nobody expected them (CLAUDE.md's
     * REPLACE rule).
     */
    @Test
    fun migrate15To16_addsTheRivalTableAndCascadesFromTheRideItPointsAt() {
        helper.createDatabase(TEST_DB, 15).use { db ->
            db.execSQL(
                """
                INSERT INTO profiles (local_user_id, name, weight_kg, ftp_watts, created_at,
                                      auth_user_id, household_visible, max_hr_bpm, birth_date,
                                      fitness_level, account_offer_dismissed)
                VALUES (1, 'Test Rider', 72.0, 210, 1000, NULL, 1, 186, 500, 'regular', 0)
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO workouts (id, user_id, class_id, duration_sec, total_output_kj,
                                      total_distance_km, avg_cadence, avg_power, avg_hr,
                                      intent_modifier, is_complete, was_recovered, timestamp,
                                      ftp_proposal_declined, resume_count, interrupted_sec)
                VALUES ('rival', 1, 'CLB-02', 1800, 240.0, 12.0, 90.0, 200.0, 150.0, 1.0,
                        1, 0, 2000, 0, 0, 0)
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO workouts (id, user_id, class_id, duration_sec, total_output_kj,
                                      total_distance_km, avg_cadence, avg_power, avg_hr,
                                      intent_modifier, is_complete, was_recovered, timestamp,
                                      ftp_proposal_declined, resume_count, interrupted_sec)
                VALUES ('live', 1, 'CLB-02', 0, 0.0, 0.0, 0.0, 0.0, NULL, 1.0,
                        0, 0, 3000, 0, 0, 0)
                """.trimIndent()
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 16, true, AppMigrations.MIGRATION_15_16)

        val migrated = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
            TEST_DB
        )
            .addMigrations(*AppMigrations.ALL)
            .build()

        try {
            val db = migrated.openHelper.writableDatabase
            // Both rides survived the migration.
            db.query("SELECT COUNT(*) FROM workouts").use { cursor ->
                cursor.moveToFirst()
                assertEquals(2, cursor.getInt(0))
            }

            db.execSQL("PRAGMA foreign_keys = ON")
            db.execSQL(
                "INSERT INTO active_ride_rival (workout_id, rival_workout_id) " +
                    "VALUES ('live', 'rival')"
            )
            db.query("SELECT rival_workout_id FROM active_ride_rival WHERE workout_id = 'live'")
                .use { cursor ->
                    assertTrue("the rival choice was not written", cursor.moveToFirst())
                    assertEquals("rival", cursor.getString(0))
                }

            // Deleting the rival's ride takes the comparison with it rather
            // than leaving a row pointing at a ride that no longer exists.
            db.execSQL("DELETE FROM workouts WHERE id = 'rival'")
            db.query("SELECT COUNT(*) FROM active_ride_rival").use { cursor ->
                cursor.moveToFirst()
                assertEquals(0, cursor.getInt(0))
            }
        } finally {
            migrated.close()
        }
    }

    /**
     * 16 → 17: `class_templates.description` (PLAN 23.2.7).
     *
     * Two things are worth asserting and only one of them is the column. The
     * other is that **a retired class survives** — `class_templates` is the one
     * table in this database that a launch-time reconcile rewrites, and a ride
     * pointing at a retired class is the case 23.2.6c exists to protect. A
     * migration that took the retired row with it would turn a rider's ride
     * into a ride of nothing, and it would do it silently.
     */
    @Test
    fun migrate16To17_addsTheDescriptionAndLeavesRetiredClassesAlone() {
        helper.createDatabase(TEST_DB, 16).use { db ->
            db.execSQL(
                """
                INSERT INTO class_templates (id, title, category, duration_sec,
                                             intervals_json, created_at, retired_at)
                VALUES ('CLB-01', 'Torque Repeats', 'Climbs', 1200, '[]', 1000, NULL)
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO class_templates (id, title, category, duration_sec,
                                             intervals_json, created_at, retired_at)
                VALUES ('HC-01', 'An Old Class', 'Climbs', 1200, '[]', 1000, 5000)
                """.trimIndent()
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 17, true, AppMigrations.MIGRATION_16_17)

        val migrated = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
            TEST_DB
        )
            .addMigrations(*AppMigrations.ALL)
            .build()

        try {
            val db = migrated.openHelper.writableDatabase

            // Both classes are still here, retired one included.
            db.query("SELECT COUNT(*) FROM class_templates").use { cursor ->
                cursor.moveToFirst()
                assertEquals(2, cursor.getInt(0))
            }

            // Empty, not null: there is no third claim to make about a class
            // that has not been given a description yet.
            db.query("SELECT description FROM class_templates WHERE id = 'CLB-01'").use { cursor ->
                assertTrue("the class went missing", cursor.moveToFirst())
                assertEquals("", cursor.getString(0))
            }

            // The retired class kept its retirement date, which is the link
            // history resolves through.
            db.query("SELECT retired_at FROM class_templates WHERE id = 'HC-01'").use { cursor ->
                assertTrue("the retired class went missing", cursor.moveToFirst())
                assertEquals(5000, cursor.getLong(0))
            }
        } finally {
            migrated.close()
        }
    }

    @Test
    fun migrate17To18_addsTheEffortsTableAndLeavesEveryRideUnscanned() {
        helper.createDatabase(TEST_DB, 17).use { db ->
            db.execSQL(
                """
                INSERT INTO profiles (local_user_id, name, weight_kg, ftp_watts,
                                      created_at, household_visible,
                                      account_offer_dismissed)
                VALUES (1, 'Test Rider', 72.0, 210, 1000, 1, 0)
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO workouts (
                    id, user_id, class_id, duration_sec, total_output_kj,
                    total_distance_km, avg_cadence, avg_power, avg_hr,
                    intent_modifier, rpe_rating, is_complete, was_recovered,
                    timestamp, ftp_proposal_declined, resume_count, interrupted_sec
                ) VALUES ('w1', 1, NULL, 1800, 150.0, 10.0, 90.0, 200.0, 150.0,
                          1.0, 7, 1, 0, 2000, 0, 0, 0)
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO workout_metrics (workout_id, timestamp_sec, cadence,
                                             resistance, power, heart_rate)
                VALUES ('w1', 1, 90.0, 40.0, 200.0, 150)
                """.trimIndent()
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 18, true, AppMigrations.MIGRATION_17_18)

        val migrated = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
            TEST_DB
        )
            .addMigrations(*AppMigrations.ALL)
            .build()

        try {
            val db = migrated.openHelper.writableDatabase

            // Not backfilled, and it cannot be: mean-maximal power is a sliding
            // window over a series with gaps in it, so an existing ride is
            // *unscanned* rather than given an approximation nothing would ever
            // compute again. `personalBests` walks it on its next run.
            db.query("SELECT power_bests_at FROM workouts WHERE id = 'w1'").use { cursor ->
                assertTrue("the ride went missing", cursor.moveToFirst())
                assertTrue("a pre-existing ride must not claim a scan", cursor.isNull(0))
            }

            // The cascade is the reason the table can be discarded with the
            // ride and never orphan a best onto a workout that is gone.
            db.execSQL(
                "INSERT INTO workout_power_bests (workout_id, window_sec, watts) " +
                    "VALUES ('w1', 5, 320.0)"
            )
            db.execSQL("DELETE FROM workouts WHERE id = 'w1'")
            db.query("SELECT COUNT(*) FROM workout_power_bests").use { cursor ->
                cursor.moveToFirst()
                assertEquals(0, cursor.getInt(0))
            }
        } finally {
            migrated.close()
        }
    }

    /**
     * 18 → 19: the column arrives empty, and the pass that fills it is the same
     * pass that keeps filling it (PLAN 23.4.12).
     *
     * Three rides seeded at 18 — measured, modelled, and one from before
     * `power_is_measured` existed — because the interesting assertion is not
     * that a column appeared but that **an upgrade puts every existing ride back
     * on the boards it was already on.** The column gates six of them, so a
     * migration that added it and stopped would silently empty the household
     * leaderboard for every rider on the tablet until each rode again.
     */
    @Test
    fun migrate18To19_addsProvenanceEmptyAndTheLaunchPassFillsIt() {
        helper.createDatabase(TEST_DB, 18).use { db ->
            db.execSQL(
                """
                INSERT INTO profiles (local_user_id, name, weight_kg, ftp_watts,
                                      created_at, household_visible,
                                      account_offer_dismissed)
                VALUES (1, 'Test Rider', 72.0, 210, 1000, 1, 0)
                """.trimIndent()
            )
            listOf("measured", "modelled", "historic").forEach { id ->
                db.execSQL(
                    """
                    INSERT INTO workouts (
                        id, user_id, class_id, duration_sec, total_output_kj,
                        total_distance_km, avg_cadence, avg_power, avg_hr,
                        intent_modifier, rpe_rating, is_complete, was_recovered,
                        timestamp, ftp_proposal_declined, resume_count,
                        interrupted_sec
                    ) VALUES ('$id', 1, NULL, 1800, 150.0, 10.0, 90.0, 200.0,
                              150.0, 1.0, NULL, 1, 0, 2000, 0, 0, 0)
                    """.trimIndent()
                )
            }
            db.execSQL(
                "INSERT INTO workout_metrics (workout_id, timestamp_sec, cadence, " +
                    "resistance, power, heart_rate, power_is_measured) " +
                    "VALUES ('measured', 1, 90.0, 40.0, 200.0, 150, 1)"
            )
            db.execSQL(
                "INSERT INTO workout_metrics (workout_id, timestamp_sec, cadence, " +
                    "resistance, power, heart_rate, power_is_measured) " +
                    "VALUES ('modelled', 1, 90.0, 40.0, 200.0, 150, 0)"
            )
            db.execSQL(
                "INSERT INTO workout_metrics (workout_id, timestamp_sec, cadence, " +
                    "resistance, power, heart_rate) " +
                    "VALUES ('historic', 1, 90.0, 40.0, 200.0, 150)"
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 19, true, AppMigrations.MIGRATION_18_19)

        val migrated = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
            TEST_DB
        )
            .addMigrations(*AppMigrations.ALL)
            .build()

        try {
            val dao = migrated.workoutDao()

            // Straight after the migration nothing claims anything, which is
            // the state the pass exists to fix rather than a state to ship.
            assertEquals(3, runBlocking { dao.completeRidesWithoutProvenance() })

            assertEquals(3, runBlocking { dao.backfillPowerProvenance() })
            assertEquals(0, runBlocking { dao.completeRidesWithoutProvenance() })

            assertEquals(
                PowerProvenance.Measured,
                runBlocking { dao.getWorkoutById("measured") }?.powerProvenance
            )
            assertEquals(
                PowerProvenance.Modelled,
                runBlocking { dao.getWorkoutById("modelled") }?.powerProvenance
            )
            // Not `Modelled`: nobody wrote it down, and that is a different
            // claim about the rider's record.
            assertEquals(
                PowerProvenance.Unknown,
                runBlocking { dao.getWorkoutById("historic") }?.powerProvenance
            )

            // And it is idempotent, which is what makes it safe to run on every
            // launch rather than once.
            assertEquals(0, runBlocking { dao.backfillPowerProvenance() })
        } finally {
            migrated.close()
        }
    }

    /**
     * 19 → 20: every existing ride's record is intact, and says so (23.4.3).
     *
     * The interesting assertion is the *absence* of a backfill. Both columns
     * arrive null and null is the truth — nothing on any tablet has been
     * trimmed, because trimming is off until a rider turns it on — so unlike
     * 18 → 19 there is no pass to run afterwards and nothing here should ever
     * grow one. A migration that guessed `metrics_detail_sec = 1` would be
     * writing a claim where an honest absence belongs, and 23.4.3's whole job
     * is that this column is never a guess.
     */
    @Test
    fun migrate19To20_leavesEveryExistingRideAtFullResolution() {
        helper.createDatabase(TEST_DB, 19).use { db ->
            db.execSQL(
                """
                INSERT INTO profiles (local_user_id, name, weight_kg, ftp_watts,
                                      created_at, household_visible,
                                      account_offer_dismissed)
                VALUES (1, 'Test Rider', 72.0, 210, 1000, 1, 0)
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO workouts (
                    id, user_id, class_id, duration_sec, total_output_kj,
                    total_distance_km, avg_cadence, avg_power, avg_hr,
                    intent_modifier, rpe_rating, is_complete, was_recovered,
                    timestamp, ftp_proposal_declined, resume_count,
                    interrupted_sec, power_provenance
                ) VALUES ('old', 1, NULL, 1800, 150.0, 10.0, 90.0, 200.0,
                          150.0, 1.0, NULL, 1, 0, 2000, 0, 0, 0, 'Measured')
                """.trimIndent()
            )
            db.execSQL(
                "INSERT INTO workout_metrics (workout_id, timestamp_sec, cadence, " +
                    "resistance, power, heart_rate, power_is_measured) " +
                    "VALUES ('old', 1, 90.0, 40.0, 200.0, 150, 1)"
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 20, true, AppMigrations.MIGRATION_19_20)

        val migrated = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
            TEST_DB
        )
            .addMigrations(*AppMigrations.ALL)
            .build()

        try {
            val row = runBlocking { migrated.workoutDao().getWorkoutById("old") }
            assertNull("an existing ride's record is intact", row?.metricsDetailSec)
            assertNull("and it has no summary because it does not need one", row?.distributionsJson)
            // The ride it already was, unchanged by the upgrade.
            assertEquals(PowerProvenance.Measured, row?.powerProvenance)
            assertEquals(
                1,
                runBlocking { migrated.workoutMetricDao().getMetricCountForWorkout("old") }
            )
        } finally {
            migrated.close()
        }
    }

    private companion object {
        const val TEST_DB = "migration-test"
    }
}
