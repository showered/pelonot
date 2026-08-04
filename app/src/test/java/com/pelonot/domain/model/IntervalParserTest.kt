package com.pelonot.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IntervalParserTest {

    /** The exact shape used by the bundled class assets. */
    private val realAssetJson = """
        [
          {"time_start_sec":0,"time_end_sec":240,"target_cadence_min":80,
           "target_cadence_max":90,"target_power_zone":1},
          {"time_start_sec":240,"time_end_sec":474,"target_cadence_min":85,
           "target_cadence_max":95,"target_power_zone":2},
          {"time_start_sec":474,"time_end_sec":630,"target_cadence_min":88,
           "target_cadence_max":98,"target_power_zone":3}
        ]
    """.trimIndent()

    @Test
    fun `parses the bundled asset format`() {
        // Regression: the model declared camelCase properties and duration
        // rather than start/end timestamps, so this threw on every class and
        // the caller's catch returned an empty list. No class ever showed a
        // single interval.
        val intervals = IntervalParser.parse(realAssetJson).getOrThrow()

        assertEquals(3, intervals.size)
        with(intervals.first()) {
            assertEquals(0, startSec)
            assertEquals(240, endSec)
            assertEquals(80, cadenceMin)
            assertEquals(90, cadenceMax)
            assertEquals(PowerZone.Z1, powerZone)
        }
    }

    @Test
    fun `derives duration from the start and end timestamps`() {
        val intervals = IntervalParser.parse(realAssetJson).getOrThrow()

        assertEquals(240, intervals[0].durationSec)
        assertEquals(234, intervals[1].durationSec)
        assertEquals(156, intervals[2].durationSec)
    }

    @Test
    fun `total duration matches the sum of its intervals`() {
        val intervals = IntervalParser.parse(realAssetJson).getOrThrow()

        assertEquals(630, intervals.sumOf { it.durationSec })
        assertEquals(intervals.last().endSec, intervals.sumOf { it.durationSec })
    }

    @Test
    fun `maps zone numbers onto the Coggan model`() {
        val intervals = IntervalParser.parse(realAssetJson).getOrThrow()

        assertEquals(listOf(PowerZone.Z1, PowerZone.Z2, PowerZone.Z3), intervals.map { it.powerZone })
    }

    @Test
    fun `identifies recovery intervals for coaching cues`() {
        val intervals = IntervalParser.parse(realAssetJson).getOrThrow()

        assertTrue(intervals[0].isRecovery) // Z1
        assertTrue(intervals[1].isRecovery) // Z2
        assertFalse(intervals[2].isRecovery) // Z3
    }

    @Test
    fun `containsSecond covers the interval half-openly`() {
        val interval = Interval(
            startSec = 240,
            endSec = 474,
            cadenceMin = 85,
            cadenceMax = 95,
            powerZoneNumber = 2
        )

        assertFalse(interval.containsSecond(239))
        assertTrue(interval.containsSecond(240))
        assertTrue(interval.containsSecond(473))
        // The boundary second belongs to the next interval, so a timeline
        // lookup can never match two intervals at once.
        assertFalse(interval.containsSecond(474))
    }

    @Test
    fun `consecutive intervals tile the timeline without gaps or overlap`() {
        val intervals = IntervalParser.parse(realAssetJson).getOrThrow()

        for (second in 0 until 630) {
            val matches = intervals.count { it.containsSecond(second) }
            assertEquals("second $second matched $matches intervals", 1, matches)
        }
    }

    @Test
    fun `tolerates unknown fields so the schema can grow`() {
        val json = """
            [{"time_start_sec":0,"time_end_sec":60,"target_cadence_min":80,
              "target_cadence_max":90,"target_power_zone":1,"coach_cue":"settle in"}]
        """.trimIndent()

        assertTrue(IntervalParser.parse(json).isSuccess)
    }

    @Test
    fun `reports malformed json as a failure rather than an empty class`() {
        val result = IntervalParser.parse("not json at all")

        assertTrue(result.isFailure)
        // The lenient accessor still exists for UI paths that cannot show an
        // error, but the caller now has to choose it explicitly.
        assertTrue(IntervalParser.parseOrEmpty("not json at all").isEmpty())
    }

    @Test
    fun `an unknown zone number falls back rather than throwing`() {
        val json = """
            [{"time_start_sec":0,"time_end_sec":60,"target_cadence_min":80,
              "target_cadence_max":90,"target_power_zone":99}]
        """.trimIndent()

        val interval = IntervalParser.parse(json).getOrThrow().single()

        assertEquals(PowerZone.Z1, interval.powerZone)
    }

    @Test
    fun `an empty interval list parses to an empty class`() {
        assertEquals(emptyList<Interval>(), IntervalParser.parse("[]").getOrThrow())
    }

    // ── Which metric governs (11.7.2) ───────────────────────────────

    @Test
    fun `a block that names no governor is asking for the power`() {
        // The whole point of the field being additive: 840 of the library's
        // 1071 blocks write nothing, and every class authored before it
        // existed decodes to exactly what it always meant.
        val json = """
            [{"time_start_sec":0,"time_end_sec":60,"target_cadence_min":80,
              "target_cadence_max":90,"target_power_zone":3}]
        """.trimIndent()

        assertEquals(
            GovernedBy.Power,
            IntervalParser.parse(json).getOrThrow().single().governedBy
        )
    }

    @Test
    fun `a block can say the cadence is the instruction`() {
        val json = """
            [{"time_start_sec":0,"time_end_sec":120,"target_cadence_min":50,
              "target_cadence_max":60,"target_power_zone":4,
              "governed_by":"cadence"}]
        """.trimIndent()

        assertEquals(
            GovernedBy.Cadence,
            IntervalParser.parse(json).getOrThrow().single().governedBy
        )
    }
}
