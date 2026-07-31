package com.pelonot.data.remote.dto

import com.pelonot.domain.model.IntervalParser
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `intervals_json` arrives in two shapes and the app has to read both.
 *
 * The cloud shape had never been decoded once: `ClassTemplateDto` typed the
 * field `String`, the cloud column is `JSONB` holding an array, and the
 * resulting `JsonDecodingException` was swallowed into `SyncOutcome.Failed`
 * and read as "cloud unavailable". The only visible symptom was a class
 * library of 5 rather than 72.
 */
class ClassTemplateDtoTest {

    private val json = Json { ignoreUnknownKeys = true }

    /** What the bundled class assets hold: the array escaped into a string. */
    private val assetShape = """
        {
          "id": "PZE-01",
          "title": "Base Builder 20",
          "category": "Endurance",
          "duration_sec": 1200,
          "intervals_json": "[{\"time_start_sec\":0,\"time_end_sec\":240,\"target_cadence_min\":80,\"target_cadence_max\":90,\"target_power_zone\":1}]"
        }
    """.trimIndent()

    /** What PostgREST returns for a `JSONB` column: the array itself. */
    private val cloudShape = """
        {
          "id": "PZE-01",
          "title": "Base Builder 20",
          "category": "Endurance",
          "duration_sec": 1200,
          "intervals_json": [
            {"time_start_sec": 0, "time_end_sec": 240, "target_cadence_min": 80,
             "target_cadence_max": 90, "target_power_zone": 1}
          ]
        }
    """.trimIndent()

    @Test
    fun `decodes the escaped-string shape the assets use`() {
        val dto = json.decodeFromString<ClassTemplateDto>(assetShape)
        assertEquals("PZE-01", dto.id)
        assertTrue(dto.intervalsJson.startsWith("["))
    }

    @Test
    fun `decodes the JSONB array shape the cloud returns`() {
        val dto = json.decodeFromString<ClassTemplateDto>(cloudShape)
        assertEquals("PZE-01", dto.id)
        assertTrue(dto.intervalsJson.trimStart().startsWith("["))
    }

    /**
     * The point of the whole exercise: whichever shape came in, what comes out
     * is something `IntervalParser` can read. Asserting on the string alone
     * would pass for a value that is still quoted and escaped.
     */
    @Test
    fun `both shapes parse to the same interval`() {
        val fromAssets = IntervalParser.parse(
            json.decodeFromString<ClassTemplateDto>(assetShape).intervalsJson
        ).getOrThrow()
        val fromCloud = IntervalParser.parse(
            json.decodeFromString<ClassTemplateDto>(cloudShape).intervalsJson
        ).getOrThrow()

        assertEquals(1, fromAssets.size)
        assertEquals(fromAssets, fromCloud)
    }

    /**
     * A value read out of a JSONB column must go back in as an array. Encoding
     * the string form would store a quoted blob that every later read breaks on
     * — the same defect one level down.
     */
    @Test
    fun `re-encodes an array as an array, not as a quoted string`() {
        val dto = json.decodeFromString<ClassTemplateDto>(cloudShape)
        val encoded = json.encodeToString(ClassTemplateDto.serializer(), dto)

        assertTrue(
            "intervals_json should re-encode as a JSON array: $encoded",
            encoded.contains("\"intervals_json\":[")
        )
        // And it survives the round trip.
        assertEquals(
            IntervalParser.parse(dto.intervalsJson).getOrThrow(),
            IntervalParser.parse(
                json.decodeFromString<ClassTemplateDto>(encoded).intervalsJson
            ).getOrThrow()
        )
    }
}
