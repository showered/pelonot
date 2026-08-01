package com.pelonot.data.local

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
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

    private companion object {
        const val TEST_DB = "migration-test"
    }
}
