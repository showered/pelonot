package com.pelonot.domain.model

/** Counts recorded active seconds, never interpolating across missing samples. */
data class LiveHeartRateZones(
    val secondsByZone: Map<HeartRateZone, Int> = emptyMap(),
    private val lastSecond: Int = 0
) {
    fun record(second: Int, bpm: Int?, maxHrBpm: Int?): LiveHeartRateZones {
        // The sample at zero starts the ride; it has not used a second yet.
        if (second <= lastSecond) return this
        val zone = HeartRateZone.forHeartRate(bpm, maxHrBpm)
        return copy(
            secondsByZone = if (zone == null) secondsByZone else
                secondsByZone + (zone to ((secondsByZone[zone] ?: 0) + 1)),
            lastSecond = second
        )
    }

    /** A class fills over its duration; an open ride uses its elapsed time. */
    fun fractions(elapsedSec: Int, durationSec: Int?): Map<HeartRateZone, Float> {
        val horizon = durationSec?.takeIf { it > 0 } ?: elapsedSec
        if (horizon <= 0) return emptyMap()
        return secondsByZone.mapValues { (_, seconds) -> seconds.toFloat() / horizon }
    }
}
