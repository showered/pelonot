package com.pelonot.data.remote.dto

import com.pelonot.data.local.entity.WorkoutEntity
import com.pelonot.data.local.entity.WorkoutMetricEntity
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
        val dto = WorkoutDto.from(workout(), emptyList())

        // A bare Long here is rejected by TIMESTAMPTZ with
        // "22008 date/time field value out of range".
        assertEquals("2025-07-30T18:26:40Z", dto.recordedAt)
    }

    @Test
    fun `recorded_at does not drift with the device timezone`() {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Kiritimati")) // UTC+14
            val plusFourteen = WorkoutDto.from(workout(), emptyList()).recordedAt
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Midway"))     // UTC-11
            val minusEleven = WorkoutDto.from(workout(), emptyList()).recordedAt

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
            assertEquals("2025-07-30T18:26:40Z", WorkoutDto.from(workout(), emptyList()).recordedAt)
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test
    fun `serialises exactly the column names the workouts table has`() {
        val dto = WorkoutDto.from(workout(), emptyList())
        val keys = json.encodeToJsonElement(WorkoutDto.serializer(), dto)
            .let { it as kotlinx.serialization.json.JsonObject }.keys

        // Every key here must exist as a column, or PostgREST rejects the whole
        // insert. Verified against the live schema after migration 002.
        val columns = setOf(
            "id", "class_id", "duration_sec", "total_output_kj", "total_distance_km",
            "avg_cadence", "avg_power", "avg_hr", "intent_modifier", "rpe_rating",
            "recorded_at", "metrics_payload"
        )
        assertEquals(emptySet<String>(), keys - columns)
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
        val dto = WorkoutDto.from(workout(), listOf(metric))

        assertNull(dto.metrics.single().heartRate)

        // and survives the round trip through JSON as null, not 0
        val encoded = json.encodeToJsonElement(WorkoutDto.serializer(), dto)
            .let { it as kotlinx.serialization.json.JsonObject }
        val sample = (encoded["metrics_payload"] as kotlinx.serialization.json.JsonArray)
            .single() as kotlinx.serialization.json.JsonObject
        assertTrue(sample["heart_rate"]!!.jsonPrimitive.content == "null")
    }
}
