package com.pelonot.data.remote.dto

import com.pelonot.data.local.entity.WorkoutEntity
import com.pelonot.data.local.entity.WorkoutMetricEntity
import com.pelonot.domain.model.PowerProvenance
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale
import java.util.TimeZone

/**
 * The wire format against the live Supabase schema.
 *
 * These exist because the serialised shape is the one thing `assembleDebug`
 * cannot check and every previous sync defect lived in: the DTOs compiled
 * perfectly while emitting a column the table did not have, and a timestamp
 * Postgres could not parse.
 */
class WorkoutDtoTest {

    private val json = Json { encodeDefaults = true }

    /** An auth user id — what `profiles.id` is in the cloud (14.2.1). */
    private val ACCOUNT_ID = "9f8e7d6c-5b4a-3210-9876-543210fedcba"

    private fun workout(timestamp: Long = 1_753_900_000_000L) = WorkoutEntity(
        id = "11111111-2222-3333-4444-555555555555",
        userId = 1,
        classId = "TB-01",
        durationSec = 1200,
        totalOutputKj = 142.5,
        totalDistanceKm = 8.1,
        avgCadence = 86.2,
        avgPower = 118.7,
        avgHr = null,
        intentModifier = 1.0,
        rpeRating = 6,
        timestamp = timestamp
    )

    @Test
    fun `recorded_at is ISO-8601 UTC, not epoch millis`() {
        val dto = WorkoutDto.from(workout(), emptyList(), ACCOUNT_ID)

        // A bare Long here is rejected by TIMESTAMPTZ with
        // "22008 date/time field value out of range".
        assertEquals("2025-07-30T18:26:40Z", dto.recordedAt)
    }

    @Test
    fun `recorded_at does not drift with the device timezone`() {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Kiritimati")) // UTC+14
            val plusFourteen = WorkoutDto.from(workout(), emptyList(), ACCOUNT_ID).recordedAt
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Midway"))     // UTC-11
            val minusEleven = WorkoutDto.from(workout(), emptyList(), ACCOUNT_ID).recordedAt

            assertEquals(plusFourteen, minusEleven)
        } finally {
            TimeZone.setDefault(original)
        }
    }

    @Test
    fun `recorded_at is not localised`() {
        val original = Locale.getDefault()
        try {
            // A locale with a non-Gregorian default calendar formats the year
            // differently unless the formatter pins its locale.
            Locale.setDefault(Locale.forLanguageTag("th-TH-u-ca-buddhist"))
            assertEquals("2025-07-30T18:26:40Z", WorkoutDto.from(workout(), emptyList(), ACCOUNT_ID).recordedAt)
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test
    fun `serialises exactly the column names the workouts table has`() {
        val dto = WorkoutDto.from(workout(), emptyList(), ACCOUNT_ID)
        val keys = json.encodeToJsonElement(WorkoutDto.serializer(), dto)
            .let { it as kotlinx.serialization.json.JsonObject }.keys

        // Every key here must exist as a column, or PostgREST rejects the whole
        // insert — and it rejects the *whole* insert, so one wrong name loses
        // the ride rather than the field. Verified against the live schema
        // after migration 007.
        val columns = setOf(
            "id", "user_id", "class_id", "duration_sec", "total_output_kj",
            "total_distance_km", "avg_cadence", "avg_power", "avg_hr",
            "intent_modifier", "rpe_rating", "recorded_at", "metrics_payload",
            // 18.5. Added by `007_everyone_leaderboard.sql`, which is what a
            // failure here means: the DTO grew a field and the schema did not.
            "power_provenance"
        )
        assertEquals(emptySet<String>(), keys - columns)
    }

    /**
     * **A ride arrives owned** (14.2.1).
     *
     * `user_id` was a column on the table and never a field on the DTO, so
     * every ride this app has ever uploaded landed with it NULL: unattributable,
     * unrestorable onto a second bike, and invisible to any RLS policy written
     * against `auth.uid()`. Nothing failed — the column is nullable and the
     * insert returned 201 — which is why it survived a whole phase of work on
     * this exact path.
     *
     * It carries the **auth user id**, not `local_user_id`. The local one is a
     * per-device autoincrement and every bike's first profile is `1`.
     */
    @Test
    fun `a ride is attributed to its rider's account, not to a local profile number`() {
        val dto = WorkoutDto.from(workout(), emptyList(), ACCOUNT_ID)
        val encoded = json.encodeToJsonElement(WorkoutDto.serializer(), dto)
            .let { it as kotlinx.serialization.json.JsonObject }

        assertEquals(ACCOUNT_ID, encoded["user_id"]?.jsonPrimitive?.content)
        // The entity's own userId is 1 — the number that must not travel.
        assertEquals(1, workout().userId)
        assertTrue("local_user_id reached the wire", "local_user_id" !in encoded.keys)
    }

    @Test
    fun `an absent heart rate stays null rather than becoming zero`() {
        val metric = WorkoutMetricEntity(
            workoutId = "11111111-2222-3333-4444-555555555555",
            timestampSec = 0,
            cadence = 80.0,
            resistance = 30.0,
            power = 110.0,
            heartRate = null
        )
        val dto = WorkoutDto.from(workout(), listOf(metric), ACCOUNT_ID)

        // Columnar since 14.4: a ride nobody wore a strap for has no `hr`
        // column at all, which is the same claim as a column of nulls and not
        // remotely the claim a zero would make. `MetricsPayloadTest` holds the
        // rest of the round trip.
        assertNull(dto.metrics.heartRate)
        assertEquals(1, dto.metrics.size)

        val encoded = json.encodeToJsonElement(WorkoutDto.serializer(), dto)
            .let { it as kotlinx.serialization.json.JsonObject }
        val payload = encoded["metrics_payload"] as kotlinx.serialization.json.JsonObject
        assertEquals("null", payload["hr"].toString())
        assertEquals("1", payload["v"].toString())
    }

    /**
     * 23.4.12: the ride's own answer travels, not a re-count of whatever samples
     * happen to be attached.
     *
     * The fixture is the state a trimmed ride is in — a row saying `Measured`
     * with nothing under it — and it is the one the old shape got wrong: the
     * reduction of an empty series is `Unknown`, so the cloud would have been
     * handed the wrong word about a ride the bike really did measure, and 18.7's
     * cross-bike board filters on exactly that word.
     */
    @Test
    fun `the provenance on the wire is the ride's own, not a re-count of its samples`() {
        val measured = workout().copy(powerProvenance = PowerProvenance.Measured)

        assertEquals("Measured", WorkoutDto.from(measured, emptyList(), ACCOUNT_ID).powerProvenance)
    }

    /**
     * And a ride recorded before the column existed still says something: the
     * sample reduction is the fallback, so an upload that beats the backfill to
     * it is no worse than it used to be.
     */
    @Test
    fun `a ride with no provenance written falls back to counting its samples`() {
        val metric = WorkoutMetricEntity(
            workoutId = "11111111-2222-3333-4444-555555555555",
            timestampSec = 0,
            cadence = 80.0,
            resistance = 30.0,
            power = 110.0,
            powerIsMeasured = false
        )

        assertNull(workout().powerProvenance)
        assertEquals(
            "Modelled",
            WorkoutDto.from(workout(), listOf(metric), ACCOUNT_ID).powerProvenance
        )
    }
}
