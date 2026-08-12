package com.pelonot.data.remote.dto

import com.pelonot.data.local.entity.WorkoutMetricEntity
import com.pelonot.domain.model.PowerProvenance
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The columnar wire format (PLAN 14.4).
 *
 * The round trip is the point (14.4.4). A format change is the one kind of
 * change that can lose a rider's data silently — the insert succeeds, the row
 * is there, and what comes back years later is not what went up. Both
 * properties asserted below are failures this project has already had
 * somewhere else: a null heart rate becoming zero, and a gapped series being
 * drawn as continuous.
 */
class MetricsPayloadTest {

    private val json = Json { encodeDefaults = true }

    /**
     * **How the payload is actually encoded on the wire**, which is not the
     * same thing as how this test file encodes it.
     *
     * `encodeDefaults` is off by default in kotlinx.serialization, and
     * Postgrest serialises with its own `Json` rather than
     * `SupabaseModule.json` — so production has always encoded with it off
     * while every test above encoded with it on. That gap is not academic: it
     * is exactly how `v` came to be missing from every ride in the cloud
     * (14.4.3), invisible to 158 passing tests and visible in one
     * `select metrics_payload->>'v'`.
     */
    private val wireJson = Json { }

    /**
     * **The version has to survive the encoder the SDK actually uses** (14.4.3,
     * and 17.12 depends on it).
     *
     * Measured in the cloud rather than reasoned about: three rides uploaded
     * with `metrics_payload->>'v'` NULL. The cause was a default value —
     * `version: Int = VERSION` — which the encoder is entitled to omit as
     * redundant. The fix is that there is no default any more, so there is
     * nothing for it to consider redundant, and this test fails if one ever
     * comes back.
     */
    @Test
    fun `the version survives an encoder that omits defaults`() {
        val encoded = wireJson.encodeToString(
            MetricsPayload.serializer(),
            MetricsPayload.from(
                listOf(
                    WorkoutMetricEntity(
                        workoutId = "ride-1", timestampSec = 0,
                        cadence = 80.0, resistance = 30.0, power = 100.0
                    )
                )
            )
        )

        assertTrue("the payload went out with no version: $encoded", "\"v\":1" in encoded)
    }

    private fun sample(
        second: Int,
        cadence: Double = 80.0,
        resistance: Double = 30.0,
        power: Double = 120.0,
        heartRate: Int? = null,
        powerIsMeasured: Boolean? = null
    ) = WorkoutMetricEntity(
        workoutId = "ride-1",
        timestampSec = second,
        cadence = cadence,
        resistance = resistance,
        power = power,
        heartRate = heartRate,
        powerIsMeasured = powerIsMeasured
    )

    private fun provenanceOf(metrics: List<WorkoutMetricEntity>) = PowerProvenance.of(
        measured = metrics.count { it.powerIsMeasured == true },
        modelled = metrics.count { it.powerIsMeasured == false },
        unknown = metrics.count { it.powerIsMeasured == null }
    )

    @Test
    fun `a ride survives the round trip sample for sample`() {
        val ride = (0 until 300).map { second ->
            sample(
                second,
                cadence = 80.0 + second % 7,
                power = 100.0 + second,
                heartRate = 120 + second % 5,
                powerIsMeasured = true
            )
        }

        val restored = MetricsPayload.from(ride).toMetrics("ride-1").getOrThrow()

        assertEquals(ride.size, restored.size)
        ride.forEachIndexed { index, original ->
            val back = restored[index]
            assertEquals(original.timestampSec, back.timestampSec)
            assertEquals(original.cadence, back.cadence, 0.0)
            assertEquals(original.resistance, back.resistance, 0.0)
            assertEquals(original.power, back.power, 0.0)
            assertEquals(original.heartRate, back.heartRate)
            assertEquals(original.powerIsMeasured, back.powerIsMeasured)
        }
    }

    @Test
    fun `a measured ride does not come back as an unknown one`() {
        // 14.4.7. Without the column every restored ride is Unknown, which
        // fails `isTrustworthyAsMeasured` — so a cloud copy of a real bike ride
        // could not propose an FTP (7.10.7) or stand on the household
        // leaderboard (24.4.2) that the original ride qualified for.
        val ride = (0 until 20).map { sample(it, powerIsMeasured = true) }

        val restored = MetricsPayload.from(ride).toMetrics("ride-1").getOrThrow()

        assertEquals(PowerProvenance.Measured, provenanceOf(restored))
    }

    @Test
    fun `a ride the board dropped out of stays mixed rather than picking a side`() {
        // The argument against putting provenance on the row as one scalar:
        // `PowerProvenance` reduces the series to a single answer, but it
        // reduces it *from* the samples, and these samples disagree. A scalar
        // would have to invent agreement in one direction or the other.
        val ride = listOf(
            sample(0, powerIsMeasured = true),
            sample(1, powerIsMeasured = false),
            sample(2, powerIsMeasured = true)
        )

        val restored = MetricsPayload.from(ride).toMetrics("ride-1").getOrThrow()

        assertEquals(listOf(true, false, true), restored.map { it.powerIsMeasured })
        assertEquals(PowerProvenance.Mixed, provenanceOf(restored))
    }

    @Test
    fun `a ride from before the column existed omits it and comes back unknown`() {
        val payload = MetricsPayload.from((0 until 10).map { sample(it) })

        assertNull(payload.powerIsMeasured)

        val encoded = json.encodeToJsonElement(MetricsPayload.serializer(), payload) as JsonObject
        assertTrue("pm must not be written when nothing recorded it", encoded["pm"]!!.toString() == "null")

        // Unknown, and specifically not Modelled: "nobody wrote it down" and
        // "the app made these up" are different claims about a rider's record.
        val restored = payload.toMetrics("ride-1").getOrThrow()
        assertTrue(restored.all { it.powerIsMeasured == null })
        assertEquals(PowerProvenance.Unknown, provenanceOf(restored))
    }

    @Test
    fun `provenance is written as digits and reads back as itself`() {
        val payload = MetricsPayload.from(
            listOf(
                sample(0, powerIsMeasured = true),
                sample(1, powerIsMeasured = false),
                sample(2, powerIsMeasured = null)
            )
        )
        val encoded = json.encodeToString(MetricsPayload.serializer(), payload)

        // `true` costs three characters more per sample, which is 13 KB across
        // a 45-minute ride and the difference between fitting the budget below
        // and not.
        assertTrue(encoded, encoded.contains(""""pm":[1,0,null]"""))

        val decoded = json.decodeFromString(MetricsPayload.serializer(), encoded)
            .toMetrics("ride-1").getOrThrow()
        assertEquals(listOf(true, false, null), decoded.map { it.powerIsMeasured })
    }

    @Test
    fun `a short provenance column is rejected too`() {
        val broken = MetricsPayload(
            version = MetricsPayload.VERSION,
            timestampSec = listOf(0, 1, 2),
            cadence = listOf(80.0, 81.0, 82.0),
            resistance = listOf(30.0, 30.0, 30.0),
            power = listOf(100.0, 101.0, 102.0),
            powerIsMeasured = listOf(true, true)
        )

        val result = broken.toMetrics("ride-1")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("pm=2"))
    }

    @Test
    fun `a null heart rate comes back null and never zero`() {
        // The failure that has been fixed twice already in other places. A
        // strap that was never paired is not a rider with no pulse, and a zero
        // here drags the ride's average down for ever.
        val ride = listOf(
            sample(0, heartRate = 140),
            sample(1, heartRate = null),
            sample(2, heartRate = 142)
        )

        val restored = MetricsPayload.from(ride).toMetrics("ride-1").getOrThrow()

        assertEquals(140, restored[0].heartRate)
        assertNull(restored[1].heartRate)
        assertEquals(142, restored[2].heartRate)
    }

    @Test
    fun `a ride nobody wore a strap for omits the column entirely`() {
        val payload = MetricsPayload.from((0 until 10).map { sample(it) })

        // Absent, because 2,700 nulls and no array at all are the same claim
        // and one of them costs 13 KB.
        assertNull(payload.heartRate)

        val encoded = json.encodeToJsonElement(MetricsPayload.serializer(), payload) as JsonObject
        assertTrue("hr must not be written when nothing measured it", encoded["hr"]!!.toString() == "null")

        // And it comes back as unknown for every sample, not as zero.
        val restored = payload.toMetrics("ride-1").getOrThrow()
        assertTrue(restored.all { it.heartRate == null })
    }

    @Test
    fun `a gap in the ride is still a gap on the other side`() {
        // 2.4.4 leaves a real hole where the board went quiet, and 16.1.2 draws
        // it. Implying the second from the array index would close it silently
        // and invent 118 seconds of riding that did not happen.
        val ride = listOf(sample(0), sample(1), sample(120), sample(121))

        val payload = MetricsPayload.from(ride)
        assertEquals(listOf(0, 1, 120, 121), payload.timestampSec)

        val restored = payload.toMetrics("ride-1").getOrThrow()
        assertEquals(listOf(0, 1, 120, 121), restored.map { it.timestampSec })
    }

    @Test
    fun `columns of different lengths are rejected rather than repaired`() {
        // Truncation, or a writer that did not understand the shape. Filing
        // sample 900's power against sample 900's cadence would then be luck,
        // and a half-record is worse than none — the same argument as the
        // telemetry fence.
        val broken = MetricsPayload(
            version = MetricsPayload.VERSION,
            timestampSec = listOf(0, 1, 2),
            cadence = listOf(80.0, 81.0, 82.0),
            resistance = listOf(30.0, 30.0),
            power = listOf(100.0, 101.0, 102.0)
        )

        val result = broken.toMetrics("ride-1")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("r=2"))
    }

    @Test
    fun `a short heart rate column is rejected too`() {
        val broken = MetricsPayload(
            version = MetricsPayload.VERSION,
            timestampSec = listOf(0, 1, 2),
            cadence = listOf(80.0, 81.0, 82.0),
            resistance = listOf(30.0, 30.0, 30.0),
            power = listOf(100.0, 101.0, 102.0),
            heartRate = listOf(140, 141)
        )

        assertTrue(broken.toMetrics("ride-1").isFailure)
    }

    @Test
    fun `the payload says which shape it is`() {
        val encoded = json.encodeToJsonElement(
            MetricsPayload.serializer(),
            MetricsPayload.from(listOf(sample(0)))
        ) as JsonObject

        assertEquals("1", encoded["v"].toString())
        // An absent `v` is how the one pre-14.4 row already in the cloud is
        // recognised, so the field must always be written.
        assertTrue(encoded.containsKey("v"))
    }

    /**
     * **An outline says so on the wire too** (PLAN 23.4.3, 23.4.14).
     *
     * The rule is that anything drawing or exporting a trimmed trace says what
     * resolution it is, and an upload is an export. Without `d`, the cloud copy
     * of a 25-minute ride condensed to every tenth second is indistinguishable
     * from one recorded second by second — and the reader that will care most
     * does not exist yet (23.4.13), which is exactly when a missing field is
     * expensive.
     */
    @Test
    fun `a condensed ride says how far apart its seconds are`() {
        val encoded = wireJson.encodeToJsonElement(
            MetricsPayload.serializer(),
            MetricsPayload.from(listOf(sample(0), sample(10)), detailSec = 10)
        ) as JsonObject

        assertEquals("10", encoded["d"].toString())
    }

    /**
     * And an intact one claims nothing, in the encoder production actually
     * uses. Absent is *the record is intact* — the same claim null makes on
     * `workouts.metrics_detail_sec`, and the reason 23.4.3 refuses to let that
     * column default to 1.
     */
    @Test
    fun `an intact ride carries no resolution at all`() {
        val encoded = wireJson.encodeToJsonElement(
            MetricsPayload.serializer(),
            MetricsPayload.from(listOf(sample(0), sample(1)))
        ) as JsonObject

        assertTrue("an intact ride is claiming a resolution", !encoded.containsKey("d"))
    }

    @Test
    fun `a 45-minute ride is a fraction of what it used to be`() {
        // The measured reason for the whole item, and the guard against
        // drifting back to a shape that cannot pass it. The old shape is built
        // here rather than remembered, so the comparison is evidence: an array
        // of 2,700 five-key objects repeats the key names 13,500 times.
        val ride = (0 until 2_700).map { second ->
            sample(
                second,
                cadence = 80.0 + second % 20,
                power = 100.0 + second % 200,
                heartRate = 120 + second % 40,
                // A bike ride, so every sample is measured — the shape that
                // costs the new column the most (14.4.7).
                powerIsMeasured = true
            )
        }

        val columnar = json.encodeToString(MetricsPayload.serializer(), MetricsPayload.from(ride))
        val perSample = ride.joinToString(",", "[", "]") {
            """{"timestamp_sec":${it.timestampSec},"cadence":${it.cadence},""" +
                """"resistance":${it.resistance},"power":${it.power},"heart_rate":${it.heartRate},""" +
                """"power_is_measured":${it.powerIsMeasured}}"""
        }

        assertTrue(
            "columnar ${columnar.length / 1024} KB against ${perSample.length / 1024} KB per-sample",
            columnar.length < perSample.length / 3
        )
        assertTrue(
            "a 45-minute ride serialised to ${columnar.length} bytes",
            columnar.length < MetricsPayload.FORTY_FIVE_MINUTE_BUDGET_BYTES
        )
    }

    @Test
    fun `a whole number is written without its trailing zero, and reads back the same`() {
        val payload = MetricsPayload.from(
            listOf(sample(0, cadence = 84.0, resistance = 19.0, power = 29.7))
        )
        val encoded = json.encodeToString(MetricsPayload.serializer(), payload)

        assertTrue(encoded, encoded.contains(""""c":[84]"""))
        assertTrue(encoded, encoded.contains(""""r":[19]"""))
        // The board reports power in tenths and those digits are real (14.4.6),
        // so this one keeps its decimal.
        assertTrue(encoded, encoded.contains(""""p":[29.7]"""))

        val decoded = json.decodeFromString(MetricsPayload.serializer(), encoded)
            .toMetrics("ride-1").getOrThrow().single()
        assertEquals(84.0, decoded.cadence, 0.0)
        assertEquals(19.0, decoded.resistance, 0.0)
        assertEquals(29.7, decoded.power, 0.0)
    }
}
