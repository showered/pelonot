package com.pelonot.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveHeartRateZonesTest {
    @Test fun minuteInThirtyMinuteClassUsesOneThirtieth() {
        val zones = (0..60).fold(LiveHeartRateZones()) { z, second -> z.record(second, 100, 200) }
        assertEquals(60, zones.secondsByZone[HeartRateZone.H1])
        assertEquals(1f / 30, zones.fractions(60, 1800)[HeartRateZone.H1]!!, 0.00001f)
    }

    @Test fun completeClassFillsRingAcrossZones() {
        val zones = (1..1800).fold(LiveHeartRateZones()) { z, second ->
            z.record(second, if (second <= 900) 100 else 150, 200)
        }
        assertEquals(1f, zones.fractions(1800, 1800).values.sum(), 0.00001f)
        assertEquals(0.5f, zones.fractions(1800, 1800)[HeartRateZone.H3]!!, 0.00001f)
    }

    @Test fun gapsAndMissingBoundariesStayUnfilled() {
        val zones = LiveHeartRateZones().record(1, 100, 200)
            .record(2, null, 200).record(3, 100, null).record(10, 100, 200)
        assertEquals(0.2f, zones.fractions(10, null).values.sum(), 0.00001f)
        assertTrue(LiveHeartRateZones().record(1, 100, null).secondsByZone.isEmpty())
    }

    @Test fun pauseAndReplayDoNotCountTheSameSecondAgain() {
        val zones = LiveHeartRateZones().record(1, 100, 200).record(2, 150, 200)
        assertEquals(zones, zones.record(2, 150, 200))
        assertEquals(zones, zones.record(1, 100, 200))
        assertEquals(1f, zones.fractions(2, null).values.sum(), 0.00001f)
        assertTrue(zones.fractions(0, null).isEmpty())
    }
}
