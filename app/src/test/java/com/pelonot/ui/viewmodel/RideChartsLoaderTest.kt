package com.pelonot.ui.viewmodel

import com.pelonot.data.local.entity.WorkoutEntity
import com.pelonot.data.local.entity.WorkoutMetricEntity
import com.pelonot.domain.model.MaxHeartRate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which denominator a ride's heart-rate zones are drawn from, and where that
 * number came from (PLAN 21.4.2a, 21.4.2c).
 *
 * The rule under test is one sentence and it is the whole of 21.4.2c: **the
 * source follows the number**. A ride carrying its own maximum is described by
 * its own source — null included, which is an honest gap rather than an
 * invitation to work it out from the profile — and only a ride drawn from
 * today's number takes today's source with it.
 *
 * The pairing is what matters rather than either field alone. A ride's number
 * with the profile's source is exactly the mistake 7.8 is named after: a claim
 * about provenance derived from a row that has moved since.
 */
class RideChartsLoaderTest {

    private fun workout(
        maxHrBpm: Int? = null,
        maxHrSource: MaxHeartRate.Source? = null
    ) = WorkoutEntity(
        id = "ride-1",
        userId = 1,
        classId = null,
        durationSec = 60,
        totalOutputKj = 10.0,
        totalDistanceKm = 0.5,
        avgCadence = 85.0,
        avgPower = 150.0,
        avgHr = 140.0,
        intentModifier = 1.0,
        maxHrBpm = maxHrBpm,
        maxHrSource = maxHrSource,
        timestamp = 1_000L
    )

    private val samples = (0 until 60).map { second ->
        WorkoutMetricEntity(
            workoutId = "ride-1",
            timestampSec = second,
            cadence = 85.0,
            resistance = 40.0,
            power = 150.0,
            heartRate = 140
        )
    }

    private fun charts(
        workout: WorkoutEntity,
        riderMax: MaxHeartRate? = null
    ) = buildRideCharts(
        workout = workout,
        metrics = samples,
        intervals = emptyList(),
        riderFtp = 200,
        riderMaxHr = riderMax
    )

    @Test
    fun `a ride's own maximum is described by the ride's own source`() {
        val result = charts(
            workout(maxHrBpm = 176, maxHrSource = MaxHeartRate.Source.Estimated),
            riderMax = MaxHeartRate(195, MaxHeartRate.Source.Measured)
        )

        assertEquals(176, result.maxHrBpm)
        assertTrue(result.maxHrIsTheRides)
        // Not the profile's, although the profile has a better answer today.
        // The zones on this screen were drawn off Tanaka and the card says so.
        assertEquals(MaxHeartRate.Source.Estimated, result.maxHrSource)
    }

    @Test
    fun `a ride recorded before the column says nothing about where its maximum came from`() {
        val result = charts(
            workout(maxHrBpm = 176, maxHrSource = null),
            riderMax = MaxHeartRate(195, MaxHeartRate.Source.Measured)
        )

        assertEquals(176, result.maxHrBpm)
        assertTrue(result.maxHrIsTheRides)
        // The honest gap. Answering it from the profile would be right for the
        // riders who have never changed theirs and a silent guess for the rest.
        assertNull(result.maxHrSource)
    }

    @Test
    fun `a ride with no maximum of its own borrows both the number and its source`() {
        val result = charts(
            workout(maxHrBpm = null),
            riderMax = MaxHeartRate(190, MaxHeartRate.Source.Estimated)
        )

        assertEquals(190, result.maxHrBpm)
        // Which the card says out loud, so the two are never confused.
        assertFalse(result.maxHrIsTheRides)
        assertEquals(MaxHeartRate.Source.Estimated, result.maxHrSource)
    }

    @Test
    fun `a rider the app has no maximum for gets no denominator and no claim`() {
        val result = charts(workout(maxHrBpm = null), riderMax = null)

        assertNull(result.maxHrBpm)
        assertNull(result.maxHrSource)
        // 21.2.4: no maximum means no zones rather than five drawn off a default.
        assertEquals(0, result.timeInHeartRateZone.totalSeconds)
    }
}
