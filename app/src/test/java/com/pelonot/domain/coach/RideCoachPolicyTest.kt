package com.pelonot.domain.coach

import com.pelonot.domain.model.ClassIntervalEngine
import com.pelonot.domain.model.Interval
import com.pelonot.domain.model.IntervalState
import com.pelonot.domain.model.PowerZone
import com.pelonot.domain.model.RideCue
import com.pelonot.domain.model.TargetBand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RideCoachPolicyTest {

    private val classIntervals = listOf(
        Interval(0, 60, cadenceMin = 80, cadenceMax = 90, powerZoneNumber = 1),
        Interval(60, 120, cadenceMin = 90, cadenceMax = 100, powerZoneNumber = 5),
        Interval(120, 180, cadenceMin = 70, cadenceMax = 80, powerZoneNumber = 1)
    )
    private val engine = ClassIntervalEngine(classIntervals)

    private fun input(
        second: Int,
        cadence: Double = 85.0,
        power: Double = 150.0,
        paused: Boolean = false,
        interval: IntervalState = engine.stateAt(second),
        cadenceTarget: TargetBand = interval.current?.let {
            TargetBand.of(it.cadenceMin, it.cadenceMax)
        } ?: TargetBand.NONE,
        powerTarget: TargetBand = TargetBand(100.0, 200.0)
    ) = CoachInput(
        elapsedSec = second,
        isPaused = paused,
        interval = interval,
        cadence = cadence,
        power = power,
        cadenceTarget = cadenceTarget,
        powerTarget = powerTarget
    )

    // ── Interval announcements ──────────────────────────────────────

    @Test
    fun `announces each interval exactly once`() {
        val policy = RideCoachPolicy()

        val first = policy.onTick(input(0))
        val stillFirst = policy.onTick(input(1))
        val second = policy.onTick(input(60))

        assertEquals(1, first.filterIsInstance<RideAlert.IntervalChange>().size)
        assertTrue(stillFirst.none { it is RideAlert.IntervalChange })
        assertEquals(
            PowerZone.Z5,
            second.filterIsInstance<RideAlert.IntervalChange>().single().zone
        )
    }

    @Test
    fun `the announcement names the zone and the cadence to hold`() {
        val alert = RideCoachPolicy().onTick(input(60))
            .filterIsInstance<RideAlert.IntervalChange>()
            .single()

        assertTrue(alert.speech.contains("Zone 5"))
        assertTrue(alert.speech.contains("90 to 100"))
        assertEquals(HapticStrength.Firm, alert.haptic)
    }

    // ── Countdown ───────────────────────────────────────────────────

    @Test
    fun `warns once per second through the final five`() {
        val policy = RideCoachPolicy()
        policy.onTick(input(0))

        val warnings = (55..59).flatMap { second ->
            policy.onTick(input(second)).filterIsInstance<RideAlert.ChangeWarning>()
        }

        assertEquals(listOf(5, 4, 3, 2, 1), warnings.map { it.secondsRemaining })
        assertTrue(warnings.all { it.nextZone == PowerZone.Z5 })
    }

    @Test
    fun `only the first countdown tick speaks`() {
        val policy = RideCoachPolicy()
        policy.onTick(input(0))

        val warnings = (55..59).flatMap { second ->
            policy.onTick(input(second)).filterIsInstance<RideAlert.ChangeWarning>()
        }

        // Counting down out loud over the rider's own film is intolerable.
        assertEquals(1, warnings.count { it.speech != null })
        assertEquals("Zone 5 in five", warnings.first().speech)
        assertNull(warnings.last().speech)
    }

    @Test
    fun `does not warn before the final interval, which has no successor`() {
        val policy = RideCoachPolicy()
        policy.onTick(input(120))

        val warnings = (175..179).flatMap { second ->
            policy.onTick(input(second)).filterIsInstance<RideAlert.ChangeWarning>()
        }

        assertTrue(warnings.isEmpty())
    }

    // ── Cues ────────────────────────────────────────────────────────

    @Test
    fun `speaks a class cue once when its interval starts`() {
        val policy = RideCoachPolicy()
        policy.onTick(input(0))

        val onEffort = policy.onTick(input(60))
        val stillOnEffort = policy.onTick(input(61))

        // The Z5 block is the last hard interval of this class.
        assertEquals(
            RideCue.FinalPush,
            onEffort.filterIsInstance<RideAlert.Cue>().single().cue
        )
        assertTrue(stillOnEffort.none { it is RideAlert.Cue })
    }

    @Test
    fun `announces completion once`() {
        val policy = RideCoachPolicy()

        val first = policy.onTick(input(180))
        val second = policy.onTick(input(181))

        assertTrue(first.contains(RideAlert.ClassComplete))
        assertTrue(second.isEmpty())
    }

    // ── Off target ──────────────────────────────────────────────────

    @Test
    fun `ignores brief drift out of the cadence band`() {
        val policy = RideCoachPolicy(offTargetGraceSec = 12)
        policy.onTick(input(0))

        val alerts = (1..10).flatMap { policy.onTick(input(it, cadence = 60.0)) }

        // Power and cadence wander between pedal strokes. Calling that out
        // would make the app unusable.
        assertTrue(alerts.none { it is RideAlert.OffTarget })
    }

    @Test
    fun `calls out sustained drift with actionable advice`() {
        val policy = RideCoachPolicy(offTargetGraceSec = 12)
        policy.onTick(input(0))

        val alerts = (1..20).flatMap { policy.onTick(input(it, cadence = 60.0)) }

        assertEquals(
            "Pick up the cadence",
            alerts.filterIsInstance<RideAlert.OffTarget>().single().advice
        )
    }

    @Test
    fun `does not repeat the same advice immediately`() {
        val policy = RideCoachPolicy(offTargetGraceSec = 12, offTargetRepeatSec = 45)
        policy.onTick(input(0))

        val alerts = (1..50).flatMap { policy.onTick(input(it, cadence = 60.0)) }

        assertEquals(1, alerts.count { it is RideAlert.OffTarget })
    }

    @Test
    fun `repeats once the rider has had time to act on it`() {
        val policy = RideCoachPolicy(offTargetGraceSec = 12, offTargetRepeatSec = 45)
        policy.onTick(input(0))

        val alerts = (1..120).flatMap { policy.onTick(input(it, cadence = 60.0)) }

        // Ignored advice is worth saying again — eventually.
        assertEquals(3, alerts.count { it is RideAlert.OffTarget })
    }

    @Test
    fun `the drift clock restarts when the rider comes back on target`() {
        val policy = RideCoachPolicy(offTargetGraceSec = 12)
        policy.onTick(input(0))

        val alerts = (1..40).flatMap { second ->
            // Ten seconds off, one on, repeatedly — never sustained.
            val cadence = if (second % 11 == 0) 85.0 else 60.0
            policy.onTick(input(second, cadence = cadence))
        }

        assertTrue(alerts.none { it is RideAlert.OffTarget })
    }

    @Test
    fun `a rider who has stopped pedalling is not told to pedal harder`() {
        val policy = RideCoachPolicy(offTargetGraceSec = 5)
        policy.onTick(input(0))

        val alerts = (1..30).flatMap { policy.onTick(input(it, cadence = 0.0)) }

        assertTrue(alerts.none { it is RideAlert.OffTarget })
    }

    @Test
    fun `advises on resistance when cadence is right but power is not`() {
        val policy = RideCoachPolicy(offTargetGraceSec = 5)
        policy.onTick(input(0))

        val alerts = (1..20).flatMap {
            policy.onTick(input(it, cadence = 85.0, power = 40.0))
        }

        assertEquals(
            "Add resistance",
            alerts.filterIsInstance<RideAlert.OffTarget>().first().advice
        )
    }

    @Test
    fun `a free ride prescribes nothing, so nothing is ever off target`() {
        val policy = RideCoachPolicy(offTargetGraceSec = 5)

        val alerts = (0..60).flatMap { second ->
            policy.onTick(
                input(
                    second = second,
                    cadence = 40.0,
                    power = 20.0,
                    interval = IntervalState.NONE,
                    cadenceTarget = TargetBand.NONE,
                    powerTarget = TargetBand.NONE
                )
            )
        }

        assertTrue(alerts.isEmpty())
    }

    // ── Pause ───────────────────────────────────────────────────────

    @Test
    fun `says nothing while the ride is paused`() {
        val policy = RideCoachPolicy()

        assertTrue(policy.onTick(input(0, paused = true)).isEmpty())
        assertTrue(policy.onTick(input(60, paused = true)).isEmpty())
    }

    @Test
    fun `resuming does not immediately nag about drift accrued while paused`() {
        val policy = RideCoachPolicy(offTargetGraceSec = 12)
        policy.onTick(input(0))
        (1..30).forEach { policy.onTick(input(it, cadence = 60.0, paused = true)) }

        val onResume = policy.onTick(input(31, cadence = 60.0))

        assertTrue(onResume.none { it is RideAlert.OffTarget })
    }

    @Test
    fun `reset clears everything for the next ride`() {
        val policy = RideCoachPolicy()
        policy.onTick(input(0))
        policy.reset()

        val alerts = policy.onTick(input(0))

        assertEquals(1, alerts.filterIsInstance<RideAlert.IntervalChange>().size)
    }
}
