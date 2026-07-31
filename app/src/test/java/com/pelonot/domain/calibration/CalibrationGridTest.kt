package com.pelonot.domain.calibration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalibrationGridTest {

    private fun sample(rpm: Double, resistance: Double, watts: Double) =
        CalibrationSample(rpm, resistance, watts)

    @Test
    fun `a fresh grid has nothing and knows it`() {
        val grid = CalibrationGrid()
        assertEquals(0, grid.sampleCount)
        assertEquals(0, grid.resistanceLevelsCovered)
        assertFalse(grid.hasEnoughCoverage)
    }

    @Test
    fun `samples in the same cell collapse to a running mean`() {
        val grid = CalibrationGrid()
            .plus(sample(82.0, 45.0, 200.0))
            .plus(sample(84.0, 45.0, 210.0))
            .plus(sample(86.0, 45.0, 220.0))

        assertEquals(1, grid.cells.size)
        val cell = grid.cells.values.single()
        assertEquals(3, cell.samples)
        assertEquals(84.0, cell.meanCadenceRpm, 0.001)
        assertEquals(210.0, cell.meanWatts, 0.001)
    }

    /**
     * The grid's reason for existing: a year of riding must not become a year
     * of stored samples.
     */
    @Test
    fun `a long ride adds cells, not rows`() {
        var grid = CalibrationGrid()
        repeat(3_000) { i ->
            grid = grid.plus(sample(80.0 + (i % 5), 40.0 + (i % 3), 200.0 + (i % 7)))
        }
        assertEquals(3_000, grid.sampleCount)
        assertTrue("cells should stay tiny, was ${grid.cells.size}", grid.cells.size <= 4)
    }

    @Test
    fun `coasting and freewheeling are not operating points`() {
        val grid = CalibrationGrid()
            .plus(sample(0.0, 40.0, 0.0))       // stopped
            .plus(sample(20.0, 40.0, 15.0))     // below the usable floor
            .plus(sample(80.0, 40.0, 0.0))      // pedalling, no power reported
            .plus(sample(80.0, 140.0, 200.0))   // impossible resistance

        assertEquals(0, grid.sampleCount)
    }

    /**
     * The filter the 31 July sweep needed applied by hand: a sample taken
     * while the knob is turning pairs one instant's resistance with the
     * flywheel's response to a different one.
     */
    @Test
    fun `samples taken mid knob-turn are dropped`() {
        val ride = listOf(
            sample(80.0, 20.0, 150.0),
            sample(80.0, 20.0, 150.0),   // steady
            sample(80.0, 30.0, 150.0),   // knob moving through here
            sample(80.0, 40.0, 220.0),
            sample(80.0, 40.0, 220.0),   // steady again
            sample(80.0, 40.0, 220.0)
        )

        val steady = CalibrationGrid.steadyStateOf(ride)
        assertTrue(steady.none { it.resistancePercent == 30.0 })
        assertTrue(steady.any { it.resistancePercent == 40.0 })
    }

    @Test
    fun `a lurching cadence is a transition, not an operating point`() {
        val ride = listOf(
            sample(60.0, 40.0, 150.0),
            sample(62.0, 40.0, 155.0),
            sample(80.0, 40.0, 200.0),   // sprinting up through here
            sample(98.0, 40.0, 260.0),
            sample(99.0, 40.0, 265.0),
            sample(99.0, 40.0, 265.0)
        )

        val steady = CalibrationGrid.steadyStateOf(ride)
        assertTrue(steady.none { it.cadenceRpm == 80.0 })
    }

    @Test
    fun `coverage needs several resistance levels each at several cadences`() {
        var grid = CalibrationGrid()
        // Seven levels, but only one cadence apiece: enough points to pin a
        // value at each level and nothing at all about the slope between them.
        for (level in 0..6) {
            grid = grid.plus(sample(80.0, level * 12.0, 100.0 + level * 20.0))
        }
        assertEquals(7, grid.resistanceLevelsCovered)
        assertFalse(grid.hasEnoughCoverage)

        for (level in 0..6) {
            grid = grid.plus(sample(60.0, level * 12.0, 80.0 + level * 15.0))
            grid = grid.plus(sample(100.0, level * 12.0, 130.0 + level * 25.0))
        }
        assertTrue(grid.hasEnoughCoverage)
    }

    @Test
    fun `decay ages cells and eventually forgets them`() {
        val grid = CalibrationGrid().plus(sample(80.0, 40.0, 200.0))
        assertEquals(1.0, grid.cells.values.single().weight, 0.001)

        var aged = grid
        repeat(10) { aged = aged.decayed() }
        assertTrue(aged.cells.values.single().weight < 1.0)

        // Far enough in the past and it stops being evidence about a
        // mechanism that has since worn.
        var forgotten = grid
        repeat(200) { forgotten = forgotten.decayed() }
        assertTrue(forgotten.cells.isEmpty())
    }
}
