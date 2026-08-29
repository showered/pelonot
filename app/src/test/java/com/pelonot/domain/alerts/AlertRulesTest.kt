package com.pelonot.domain.alerts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a finished ride is worth telling the rider (PLAN 27.1, 27.2).
 *
 * The properties worth holding are the two the phase is most likely to fail on:
 * **an alert that fires every time is a caption**, and **a modelled watt is
 * never congratulated**.
 */
class AlertRulesTest {

    private fun ride(
        measured: Boolean = true,
        durationSec: Int = 1_800,
        outputKj: Double = 300.0,
        classId: String? = "CLB-04",
        priorRides: Int = 20,
        priorMeasured: Int = 20,
        priorClassRides: Int = 5,
        bestClassOutputKj: Double? = 250.0,
        bestOutputKj: Double? = 250.0,
        longestSec: Int? = 1_800,
        ridesBests: Map<Int, Double> = emptyMap(),
        previousBests: Map<Int, Double> = emptyMap(),
        weeklyStreak: Int = 0,
        previousWeeklyStreak: Int = 0
    ) = AlertEvidence(
        workoutId = "ride-1",
        classId = classId,
        classTitle = "Rolling Climbs",
        durationSec = durationSec,
        totalOutputKj = outputKj,
        powerIsMeasured = measured,
        ridesBests = ridesBests,
        previousBests = previousBests,
        priorMeasuredRides = priorMeasured,
        priorRides = priorRides,
        priorClassRides = priorClassRides,
        bestClassOutputKj = bestClassOutputKj,
        bestOutputKj = bestOutputKj,
        longestRideSec = longestSec,
        weeklyStreak = weeklyStreak,
        previousWeeklyStreak = previousWeeklyStreak
    )

    // ── The honesty gate (27.1.2) ───────────────────────────────────

    @Test
    fun `a modelled ride earns no record however good it looks`() {
        val alerts = AlertRules.detect(
            ride(
                measured = false,
                outputKj = 900.0,
                ridesBests = mapOf(1_200 to 400.0),
                previousBests = mapOf(1_200 to 200.0)
            )
        )
        assertTrue("nothing derived from watts may fire", alerts.none {
            it.kind == AlertKind.ClassOutput ||
                it.kind == AlertKind.PowerWindow ||
                it.kind == AlertKind.RideOutput
        })
    }

    /**
     * The clock is the same on a simulated ride as on a real one, so the one
     * alert that is not about watts is not gated — which is what makes the
     * feature checkable on an emulator at all.
     */
    @Test
    fun `but the longest ride is a clock reading and survives the gate`() {
        val alerts = AlertRules.detect(
            ride(measured = false, durationSec = 3_600, longestSec = 1_800)
        )
        assertEquals(listOf(AlertKind.RideDuration), alerts.map { it.kind })
    }

    // ── The floors (27.1.4) ─────────────────────────────────────────

    @Test
    fun `a rider's first rides are all bests and none of them is an alert`() {
        val alerts = AlertRules.detect(
            ride(
                priorRides = 2,
                priorMeasured = 2,
                priorClassRides = 0,
                bestClassOutputKj = null,
                outputKj = 900.0,
                durationSec = 7_200,
                ridesBests = mapOf(1_200 to 400.0),
                previousBests = mapOf(1_200 to 100.0)
            )
        )
        assertTrue("six alerts on ride three is how alerts stop meaning anything", alerts.isEmpty())
    }

    @Test
    fun `beating a record by a hair is not beating it`() {
        val alerts = AlertRules.detect(
            ride(
                outputKj = 251.0,
                bestClassOutputKj = 250.0,
                bestOutputKj = 250.0,
                ridesBests = mapOf(1_200 to 201.0),
                previousBests = mapOf(1_200 to 200.0)
            )
        )
        assertTrue("half a percent is a rider's day-to-day variation", alerts.isEmpty())
    }

    @Test
    fun `two percent is`() {
        val alerts = AlertRules.detect(ride(outputKj = 255.0, bestOutputKj = 250.0))
        assertTrue(alerts.any { it.kind == AlertKind.ClassOutput })
    }

    /**
     * A window the rider has never held is the first time they rode that long,
     * not a record — there is nothing to have beaten.
     */
    @Test
    fun `a window with no previous best fires nothing`() {
        val alerts = AlertRules.detect(
            ride(
                bestClassOutputKj = null,
                priorClassRides = 0,
                bestOutputKj = null,
                ridesBests = mapOf(3_600 to 240.0),
                previousBests = emptyMap()
            )
        )
        assertTrue(alerts.none { it.kind == AlertKind.PowerWindow })
    }

    // ── The ranking (27.1.5) ────────────────────────────────────────

    @Test
    fun `everything that happened is written down and only the best is the headline`() {
        val alerts = AlertRules.detect(
            ride(
                outputKj = 400.0,
                bestClassOutputKj = 250.0,
                bestOutputKj = 250.0,
                durationSec = 3_600,
                longestSec = 1_800,
                ridesBests = mapOf(60 to 400.0, 1_200 to 300.0),
                previousBests = mapOf(60 to 300.0, 1_200 to 200.0),
                weeklyStreak = 8,
                previousWeeklyStreak = 7
            )
        )
        assertEquals(
            "a stack of congratulations is how a good moment is made tedious",
            listOf(
                AlertKind.WeeklyStreak,
                AlertKind.ClassOutput,
                AlertKind.PowerWindow,
                AlertKind.PowerWindow,
                AlertKind.RideOutput,
                AlertKind.RideDuration
            ),
            alerts.map { it.kind }
        )
        assertEquals(AlertKind.WeeklyStreak, AlertRules.headline(alerts)?.kind)
    }

    @Test
    fun `the longer window is the bigger claim`() {
        val alerts = AlertRules.detect(
            ride(
                bestClassOutputKj = null,
                priorClassRides = 0,
                bestOutputKj = null,
                ridesBests = mapOf(5 to 900.0, 1_200 to 300.0),
                previousBests = mapOf(5 to 700.0, 1_200 to 200.0)
            )
        )
        assertEquals(
            listOf("1200", "5"),
            alerts.filter { it.kind == AlertKind.PowerWindow }.map { it.subjectKey }
        )
    }

    // ── Streaks (27.2.2) ────────────────────────────────────────────

    @Test
    fun `a streak fires on the crossing and not on the weeks after it`() {
        assertEquals(
            4.0,
            AlertRules.detect(ride(weeklyStreak = 4, previousWeeklyStreak = 3))
                .first { it.kind == AlertKind.WeeklyStreak }.value,
            0.0
        )
        assertTrue(
            "week five is not a milestone and says nothing",
            AlertRules.detect(ride(weeklyStreak = 5, previousWeeklyStreak = 4))
                .none { it.kind == AlertKind.WeeklyStreak }
        )
    }

    /**
     * A second ride in the same week does not extend the run, so the streak
     * counted with it and without it are equal and nothing fires — which is the
     * whole reason the evidence carries both numbers.
     */
    @Test
    fun `riding twice in one week is not two weeks`() {
        assertTrue(
            AlertRules.detect(ride(weeklyStreak = 4, previousWeeklyStreak = 4))
                .none { it.kind == AlertKind.WeeklyStreak }
        )
    }

    @Test
    fun `the ladder carries on in whole years`() {
        assertEquals(
            104.0,
            AlertRules.detect(ride(weeklyStreak = 104, previousWeeklyStreak = 103))
                .first { it.kind == AlertKind.WeeklyStreak }.value,
            0.0
        )
        assertTrue(
            "and says nothing in the eleven months between",
            AlertRules.detect(ride(weeklyStreak = 70, previousWeeklyStreak = 69))
                .none { it.kind == AlertKind.WeeklyStreak }
        )
    }

    /**
     * A rider whose streak passed a milestone while the app was off — or on a
     * guest ride, which is filed against nobody — is told at the next crossing
     * rather than never.
     */
    @Test
    fun `a milestone jumped over is still reached`() {
        val alert = AlertRules.detect(ride(weeklyStreak = 14, previousWeeklyStreak = 3))
            .first { it.kind == AlertKind.WeeklyStreak }
        assertEquals("the highest one crossed, not the lowest", 13.0, alert.value, 0.0)
    }

    // ── Free rides ──────────────────────────────────────────────────

    @Test
    fun `a Just Ride has no class to have a record on`() {
        val alerts = AlertRules.detect(ride(classId = null, outputKj = 400.0))
        assertNull(alerts.firstOrNull { it.kind == AlertKind.ClassOutput })
        assertTrue("but it can still be the biggest", alerts.any { it.kind == AlertKind.RideOutput })
    }

    // ── The words (27.3.1) ──────────────────────────────────────────

    @Test
    fun `the headline carries no unit and the list carries the number`() {
        val record = RiderAlert(
            kind = AlertKind.PowerWindow,
            subjectKey = "1200",
            value = 261.0,
            previousValue = 240.0
        )
        assertEquals("Your best 20 minutes.", AlertWording.headline(record))
        assertEquals("261 W, up from 240 W", AlertWording.detail(record))

        val streak = RiderAlert(AlertKind.WeeklyStreak, "8", 8.0)
        assertEquals("Eight weeks in a row.", AlertWording.headline(streak))
        assertNull("nothing to have beaten", AlertWording.detail(streak))

        assertEquals(
            "A whole year, every week.",
            AlertWording.headline(RiderAlert(AlertKind.WeeklyStreak, "52", 52.0))
        )
        assertEquals(
            "Your best ride of Rolling Climbs.",
            AlertWording.headline(
                RiderAlert(AlertKind.ClassOutput, "CLB-04", 300.0, 250.0, "Rolling Climbs")
            )
        )
    }

    /**
     * The dashboard's card is a label under `Your records`, sitting beside
     * `Last ride` → `Zone 2 Steady` and `Last 30 days` → `13 rides · 340 min`,
     * neither of which is punctuated. One card with a full stop among three
     * without reads as a mistake before it reads as anything else — seen on the
     * AVD, which is the only place it could have been.
     */
    @Test
    fun `the card gets the same words without the sentence`() {
        val record = RiderAlert(AlertKind.RideDuration, "", 3_600.0, 1_800.0)
        assertEquals("Your longest ride yet", AlertWording.phrase(record))
        assertEquals("Your longest ride yet.", AlertWording.headline(record))
    }
}
