package com.pelonot.domain.calibration

import kotlin.math.abs
import kotlin.math.pow

/**
 * Why a fit was or was not adopted.
 *
 * A calibration that silently does nothing is indistinguishable from one that
 * works, which is the failure this whole plan section exists to avoid — so the
 * outcome is a value the UI can show, never a null and a log line.
 */
sealed interface FitOutcome {

    /** Not enough of the range ridden yet (2.2a.4). */
    data class NotEnoughCoverage(
        val levelsCovered: Int,
        val levelsNeeded: Int
    ) : FitOutcome

    /**
     * A fit was found and it is not good enough to replace the shipped curve,
     * so the shipped curve stays (2.2a.3).
     *
     * This is the outcome the 31 July sweep produced, and shipping its fit
     * anyway would have looked like progress and been none.
     */
    data class Rejected(
        val candidateErrorPercent: Double,
        val shippedErrorPercent: Double,
        val reason: Reason
    ) : FitOutcome {
        enum class Reason {
            /** It did not beat the curve it would be replacing. */
            NoBetterThanShipped,

            /**
             * It beat the shipped curve and is still not good enough to act on.
             *
             * A necessary second gate, and one the first test of this fitter
             * found the hard way: the shipped coefficients score a **median
             * 66% error** on real data, so "better than shipped" is a bar that
             * a fit to pure noise clears. Relative improvement alone would have
             * adopted a curve describing nothing.
             */
            TooInaccurateToUse
        }
    }

    /** The fit predicts held-out resistance levels better than the shipped curve. */
    data class Adopted(
        val curve: CalibratedPowerCurve,
        val candidateErrorPercent: Double,
        val shippedErrorPercent: Double
    ) : FitOutcome
}

/**
 * Fits `P = (a + b·R^k)·rpm + c·rpm³` to one bike's own measured rides.
 *
 * **The exponent is searched, the rest is solved.** For a fixed `k` the model
 * is linear in `a`, `b` and `c`, so those come from a 3×3 weighted normal-
 * equations solve; `k` is swept over a plausible range and the best-scoring
 * one kept. That avoids shipping an optimiser, and it keeps the fit
 * deterministic — the same grid always produces the same curve, which matters
 * because this runs unattended after a ride.
 *
 * **Nothing is adopted without beating the shipped curve out of sample.** The
 * scoring holds out one resistance level at a time, fits on the rest, and
 * predicts the level it never saw. A model that cannot predict a level it did
 * not see is interpolating between the levels that happened to get ridden, not
 * describing the machine — which is precisely what the manual sweep in 2.2.5
 * turned out to be doing.
 */
object PowerCurveFitter {

    fun fit(grid: CalibrationGrid): FitOutcome {
        if (!grid.hasEnoughCoverage) {
            return FitOutcome.NotEnoughCoverage(
                levelsCovered = grid.wellSampledResistanceLevels,
                levelsNeeded = CalibrationGrid.MIN_RESISTANCE_LEVELS
            )
        }

        val cells = grid.cells.values.toList()
        val levels = cells.map { it.resistanceBin }.distinct()

        // Leave one resistance level out at a time, and score both curves on
        // the level neither of them was allowed to see.
        val candidateErrors = mutableListOf<Double>()
        val shippedErrors = mutableListOf<Double>()

        for (heldOut in levels) {
            val train = cells.filter { it.resistanceBin != heldOut }
            val test = cells.filter { it.resistanceBin == heldOut }
            if (train.size < MIN_TRAINING_CELLS || test.isEmpty()) continue

            val curve = solve(train) ?: continue

            test.forEach { cell ->
                candidateErrors += percentError(curve, cell)
                shippedErrors += percentError(ShippedPowerCurve, cell)
            }
        }

        if (candidateErrors.size < MIN_SCORED_POINTS) {
            return FitOutcome.NotEnoughCoverage(
                levelsCovered = grid.wellSampledResistanceLevels,
                levelsNeeded = CalibrationGrid.MIN_RESISTANCE_LEVELS
            )
        }

        val candidateError = median(candidateErrors)
        val shippedError = median(shippedErrors)

        // A margin rather than a bare comparison: a fit that ties is not
        // evidence, and swapping the curve for a coin flip would churn the
        // rider's prescribed resistance band for nothing.
        if (candidateError > shippedError * WIN_MARGIN) {
            return FitOutcome.Rejected(
                candidateError,
                shippedError,
                FitOutcome.Rejected.Reason.NoBetterThanShipped
            )
        }

        // And an absolute floor, because the relative test above is a much
        // weaker gate than it looks: the shipped curve's median error on real
        // data is 66%, so beating it is something a fit to noise manages. Only
        // a curve that predicts a resistance level it never saw to within
        // MAX_ACCEPTABLE_ERROR is worth putting in front of a rider as
        // "set the knob about here".
        if (candidateError > MAX_ACCEPTABLE_ERROR_PERCENT) {
            return FitOutcome.Rejected(
                candidateError,
                shippedError,
                FitOutcome.Rejected.Reason.TooInaccurateToUse
            )
        }

        // Only now fit on everything, which is the curve that actually ships
        // to this bike. Scoring above answered "is this form learnable here";
        // this answers "what is the best estimate of it".
        val finalCurve = solve(cells) ?: return FitOutcome.Rejected(
            candidateError,
            shippedError,
            FitOutcome.Rejected.Reason.TooInaccurateToUse
        )

        return FitOutcome.Adopted(finalCurve, candidateError, shippedError)
    }

    /** Sweeps the exponent, solving the linear part exactly at each step. */
    private fun solve(cells: List<CalibrationCell>): CalibratedPowerCurve? {
        var best: CalibratedPowerCurve? = null
        var bestError = Double.MAX_VALUE

        var k = MIN_EXPONENT
        while (k <= MAX_EXPONENT + 1e-9) {
            val curve = solveLinearPart(cells, k)
            if (curve != null) {
                val error = median(cells.map { percentError(curve, it) })
                if (error < bestError) {
                    bestError = error
                    best = curve
                }
            }
            k += EXPONENT_STEP
        }
        return best
    }

    /**
     * Weighted least squares for `a`, `b` and `c` with `k` held fixed.
     *
     * Design row is `[rpm, R^k·rpm, rpm³]` against measured watts, with each
     * cell weighted by how much evidence it holds and how recent it is. No
     * intercept: zero cadence is zero watts, and letting the fit invent an
     * offset there is how a curve ends up predicting power from a stationary
     * flywheel.
     */
    private fun solveLinearPart(cells: List<CalibrationCell>, k: Double): CalibratedPowerCurve? {
        val ata = Array(3) { DoubleArray(3) }
        val atb = DoubleArray(3)

        cells.forEach { cell ->
            val rpm = cell.meanCadenceRpm
            val resistance = cell.meanResistancePercent
            val w = cell.weight.coerceAtLeast(0.0)
            if (rpm <= 0.0 || w <= 0.0) return@forEach

            val row = doubleArrayOf(rpm, resistance.pow(k) * rpm, rpm.pow(3))
            for (i in 0..2) {
                for (j in 0..2) ata[i][j] += w * row[i] * row[j]
                atb[i] += w * row[i] * cell.meanWatts
            }
        }

        // Ridge term: without it a grid whose cadences barely vary makes the
        // rpm and rpm³ columns near-collinear and the solve blows up into
        // enormous cancelling coefficients that fit the grid and nothing else.
        for (i in 0..2) ata[i][i] += RIDGE

        val solution = solve3x3(ata, atb) ?: return null
        val (a, b, c) = solution

        // Reject anything that is not monotone in resistance. A curve where
        // turning the knob up predicts less power would make
        // resistanceForWatts meaningless, and no amount of in-sample accuracy
        // is worth a prescription that tells a rider to ease off to work
        // harder.
        if (b <= 0.0 || !a.isFinite() || !b.isFinite() || !c.isFinite()) return null

        return CalibratedPowerCurve(a = a, b = b, k = k, c = c)
    }

    /** Gaussian elimination with partial pivoting; null when singular. */
    private fun solve3x3(matrix: Array<DoubleArray>, rhs: DoubleArray): Triple<Double, Double, Double>? {
        val m = Array(3) { i -> DoubleArray(4) { j -> if (j < 3) matrix[i][j] else rhs[i] } }

        for (col in 0..2) {
            var pivot = col
            for (row in col + 1..2) {
                if (abs(m[row][col]) > abs(m[pivot][col])) pivot = row
            }
            if (abs(m[pivot][col]) < SINGULAR_TOLERANCE) return null
            val tmp = m[col]; m[col] = m[pivot]; m[pivot] = tmp

            for (row in 0..2) {
                if (row == col) continue
                val factor = m[row][col] / m[col][col]
                for (j in col..3) m[row][j] -= factor * m[col][j]
            }
        }

        val x = DoubleArray(3) { m[it][3] / m[it][it] }
        return if (x.all { it.isFinite() }) Triple(x[0], x[1], x[2]) else null
    }

    /**
     * Absolute percentage error, which is the right scale here: a 20 W miss at
     * 60 W is a different mistake from a 20 W miss at 300 W, and the shipped
     * curve's headline failure is a *median 66%*, not a wattage.
     */
    private fun percentError(curve: PowerCurve, cell: CalibrationCell): Double {
        val predicted = curve.watts(cell.meanCadenceRpm, cell.meanResistancePercent)
        val actual = cell.meanWatts
        if (actual <= 0.0) return 0.0
        return abs(predicted - actual) / actual * 100.0
    }

    private fun median(values: List<Double>): Double {
        if (values.isEmpty()) return Double.MAX_VALUE
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) {
            sorted[mid]
        } else {
            (sorted[mid - 1] + sorted[mid]) / 2.0
        }
    }

    private const val MIN_EXPONENT = 0.4
    private const val MAX_EXPONENT = 2.4
    private const val EXPONENT_STEP = 0.05

    private const val MIN_TRAINING_CELLS = 6
    private const val MIN_SCORED_POINTS = 6

    /** The candidate must be at least this much better, not merely no worse. */
    private const val WIN_MARGIN = 0.85

    /**
     * Out-of-sample median error a curve must be inside to be used at all.
     *
     * Chosen against what the number is *for*: this drives the prescribed
     * resistance band (11.2.1), and a band 25% out still puts a rider in the
     * right part of the knob. It is deliberately far tighter than the shipped
     * curve's 66% and deliberately looser than the 10.7% the 31 July sweep
     * reached in sample — because in-sample is not the test.
     */
    private const val MAX_ACCEPTABLE_ERROR_PERCENT = 25.0

    private const val RIDGE = 1e-6
    private const val SINGULAR_TOLERANCE = 1e-12
}
