package com.pelonot.domain.model

import org.junit.Assert.*
import org.junit.Test

class RideGoalTest {
    @Test fun goalsFinishOnlyAtTheirOwnThreshold() {
        val time = RideGoal.Time(1800)
        assertFalse(time.reached(1799, 100.0))
        assertTrue(time.reached(1800, 0.0))
        val distance = RideGoal.Distance(10.0)
        assertFalse(distance.reached(9999, 9.99))
        assertTrue(distance.reached(1, 10.01))
    }
    @Test fun milesAreStoredAsKilometres() {
        val goal = RideGoal.distance(10.0, UnitSystem.IMPERIAL)
        assertEquals(10.0, UnitSystem.IMPERIAL.distanceFromKm(goal.km), 0.000001)
        assertFalse(goal.reached(0, 10.0))
        assertTrue(goal.reached(0, 16.1))
    }
    @Test fun goalsRoundTripAndUnknownValuesStayOpenEnded() {
        listOf(RideGoal.Time(1800), RideGoal.Distance(16.09344)).forEach {
            assertEquals(it, RideGoal.decode(it.encode()))
        }
        listOf(null, "", "time:0", "distance:NaN", "distance:Infinity", "distance:-1", "watts:250", "time:5:6").forEach {
            assertNull(RideGoal.decode(it))
        }
    }
}
