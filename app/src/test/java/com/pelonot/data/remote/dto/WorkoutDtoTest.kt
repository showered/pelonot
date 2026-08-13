package com.pelonot.data.remote.dto

import com.pelonot.data.local.entity.WorkoutEntity
import com.pelonot.data.local.entity.WorkoutMetricEntity
import com.pelonot.domain.chart.RideDistributions
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
    /**
     * **The wire copy of a ride's resolution is the ride's own** (23.4.14).
     *
     * Not inferred from the spacing of the samples attached to it, because a
     * gap in a series is a rider who stopped (2.4.4) and reading one as a
     * resolution files a bottle stop as housekeeping. Same argument as the
     * provenance above: the row knows, so ask the row.
     */
    @Test
    fun `a condensed ride's payload says so, and an intact one does not`() {
        val metric = WorkoutMetricEntity(
            workoutId = "11111111-2222-3333-4444-555555555555",
            timestampSec = 0,
            cadence = 80.0,
            resistance = 30.0,
            power = 110.0,
            powerIsMeasured = true
        )
        val condensed = workout().copy(metricsDetailSec = 10)

        assertEquals(
            10,
            WorkoutDto.from(condensed, listOf(metric), ACCOUNT_ID).metrics.detailSec
        )
        assertNull(WorkoutDto.from(workout(), listOf(metric), ACCOUNT_ID).metrics.detailSec)
    }

    /**
     * **A ride comes back the ride it was** (PLAN 15.3.2).
     *
     * The round trip is the assertion here, and the fields it is about are
     * precisely the ones that have no cloud column: the FTP the ride was judged
     * against (7.8), the maximum heart rate its zones were drawn from (21.2.3),
     * whether it was ridden straight through (8.3d.2), and what its seconds
     * counted before a trim took them (23.4.2). Without them a restored ride is
     * not a smaller ride — it is a ride whose zones are silently redrawn from
     * today's numbers.
     */
    @Test
    fun `the row's own facts survive the round trip`() {
        val ridden = workout().copy(
            ftpWatts = 214,
            maxHrBpm = 181,
            resumeCount = 2,
            interruptedSec = 340,
            wasRecovered = true,
            metricsDetailSec = 10,
            distributionsJson = RideDistributions(
                secondsByZone = mapOf("Z4" to 464),
                secondsByCadenceBand = mapOf(80 to 300),
                ftpWatts = 214
            ).encode()
        )

        val encoded = json.encodeToString(
            WorkoutDto.serializer(),
            WorkoutDto.from(ridden, emptyList(), ACCOUNT_ID)
        )
        val back = Json { ignoreUnknownKeys = true }
            .decodeFromString(WorkoutDto.serializer(), encoded)
            .toEntity(
                localUserId = 7,
                classId = "TB-01",
                syncedAt = 5_000L,
                recordedAtMs = ridden.timestamp
            )

        assertEquals(214, back.ftpWatts)
        assertEquals(181, back.maxHrBpm)
        assertEquals(2, back.resumeCount)
        assertEquals(340, back.interruptedSec)
        assertTrue(back.wasRecovered)
        assertEquals(10, back.metricsDetailSec)
        assertEquals(464, RideDistributions.decode(back.distributionsJson)?.secondsByZone?.get("Z4"))

        // And the three the tablet decides rather than the cloud.
        assertEquals(7, back.userId)
        assertEquals(5_000L, back.syncedAt)
        assertTrue(back.isComplete)
        // Derived, never carried: 16.3.3a rescans the restored samples itself.
        assertNull(back.powerBestsAt)
    }

    /**
     * A ride uploaded before 15.3.2 existed still restores — every one of those
     * fields is absent, and absent means *nobody wrote it down* rather than a
     * zero. The ride comes back with its zones falling back to the rider's
     * current FTP, which is what 7.8.4 already draws a caption for.
     */
    @Test
    fun `a payload with no ride facts restores rather than failing`() {
        val old = """
            {"id":"11111111-2222-3333-4444-555555555555",
             "user_id":"$ACCOUNT_ID","duration_sec":600,"total_output_kj":90.0,
             "total_distance_km":4.0,"intent_modifier":1.0,
             "recorded_at":"2025-07-30T18:26:40Z",
             "metrics_payload":{"v":1,"t":[0],"c":[80],"r":[30],"p":[110]}}
        """.trimIndent()

        val back = Json { ignoreUnknownKeys = true }
            .decodeFromString(WorkoutDto.serializer(), old)
            .toEntity(localUserId = 3, classId = null, syncedAt = 1L, recordedAtMs = 2L)

        assertNull(back.ftpWatts)
        assertNull(back.maxHrBpm)
        assertNull(back.distributionsJson)
        assertNull(back.metricsDetailSec)
        assertEquals(0, back.resumeCount)
    }

    /**
     * The three shapes Postgres actually answers with (15.3.2).
     *
     * `SimpleDateFormat` is not asked to do this: its `S` pattern reads all six
     * of a `TIMESTAMPTZ`'s fractional digits as milliseconds and lands the ride
     * eight minutes late, which is a wrong date that looks like a right one.
     */
    @Test
    fun `recorded_at parses back to the millisecond it left as`() {
        assertEquals(1_753_900_000_000L, "2025-07-30T18:26:40Z".fromIso8601())
        assertEquals(1_753_900_000_481L, "2025-07-30T18:26:40.481293+00:00".fromIso8601())
        // An endpoint whose session zone is not UTC: the offset is applied,
        // not assumed away.
        assertEquals(1_753_900_000_000L, "2025-07-30T20:26:40+02:00".fromIso8601())
        // And a date that cannot be read is null, so the caller skips the ride
        // rather than filing it in January 1970.
        assertNull("not a date".fromIso8601())
    }
}
