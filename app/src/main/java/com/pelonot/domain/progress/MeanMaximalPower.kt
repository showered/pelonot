package com.pelonot.domain.progress

/**
 * One second of a ride's power, for the personal-best arithmetic (PLAN 16.3.3).
 *
 * Its own tiny type rather than `ChartSample`, because this is the only field
 * involved and a ride is walked one bucket at a time: nothing here should carry
 * a heart rate around to decide who has the best twenty minutes.
 */
data class PowerSample(val timestampSec: Int, val watts: Double)

/**
 * The best average power a rider held for a given stretch — the cycling sense
 * of "personal best by duration" (16.3.3).
 *
 * Not *best total output for a 45-minute ride*, which is the leaderboard's
 * question (11.4) and mostly measures how long the class was. This one is
 * comparable across every ride the rider has done: five seconds is a sprint,
 * twenty minutes is what an FTP is estimated from, an hour is what a hard hour
 * actually looks like.
 *
 * **A gap breaks the window, and that is the rule this object exists for.** The
 * series has real holes in it — a stalled board leaves one (2.4.4), an
 * auto-pause leaves one (19.1.2) — and averaging across seconds nobody recorded
 * would award a twenty-minute best to a ride that stopped for four of them. So
 * a window is only counted when it sits inside a run of consecutive recorded
 * seconds. The honest consequence is that a ride with a bottle stop in the
 * middle has no twenty-minute effort in it, which is true: it has two shorter
 * ones.
 */
object MeanMaximalPower {

    /**
     * The stretches worth naming.
     *
     * Five seconds is a sprint and an hour is a hard hour; the three between
     * them are the ones a rider recognises, and 20 minutes is there because it
     * is what every FTP protocol is built on (19.2.3).
     */
    val WINDOWS = listOf(5, 60, 300, 1_200, 3_600)

    /**
     * The best mean power over [windowSec] consecutive recorded seconds, or
     * **null** when the ride never held one.
     *
     * Null rather than zero, for the reason every absence in this project is:
     * "no twenty-minute effort in this ride" and "a twenty-minute effort at
     * 0 W" are different claims, and only one of them is true of a rider who
     * went out for a quarter of an hour.
     */
    fun best(samples: List<PowerSample>, windowSec: Int): Double? {
        if (windowSec <= 0 || samples.size < windowSec) return null

        val ordered = samples.sortedBy { it.timestampSec }
        var best: Double? = null

        // Each contiguous run is its own problem: a window may never span the
        // join between two of them.
        var runStart = 0
        for (index in 1..ordered.size) {
            val broken = index == ordered.size ||
                ordered[index].timestampSec != ordered[index - 1].timestampSec + 1
            if (!broken) continue

            val run = ordered.subList(runStart, index)
            if (run.size >= windowSec) {
                var sum = run.take(windowSec).sumOf { it.watts }
                var runBest = sum
                for (i in windowSec until run.size) {
                    sum += run[i].watts - run[i - windowSec].watts
                    if (sum > runBest) runBest = sum
                }
                val mean = runBest / windowSec
                if (best == null || mean > best) best = mean
            }
            runStart = index
        }

        return best
    }

    /** Every window this ride actually held, keyed by its length in seconds. */
    fun bests(samples: List<PowerSample>, windows: List<Int> = WINDOWS): Map<Int, Double> =
        windows.mapNotNull { window -> best(samples, window)?.let { window to it } }.toMap()

    /** "5 seconds", "1 minute", "20 minutes", "1 hour" — the label a rider reads. */
    fun label(windowSec: Int): String = when {
        windowSec < 60 -> plural(windowSec, "second")
        windowSec < 3_600 -> plural(windowSec / 60, "minute")
        else -> plural(windowSec / 3_600, "hour")
    }

    private fun plural(count: Int, unit: String) =
        "$count $unit${if (count == 1) "" else "s"}"
}

/** One personal best, and the ride it came from (16.3.3). */
data class PersonalBest(
    val windowSec: Int,
    val watts: Double,
    val workoutId: String,
    val atEpochMs: Long,
    /** Null for a Just Ride, which is a perfectly good place to set a best. */
    val classTitle: String?
) {
    val label: String get() = MeanMaximalPower.label(windowSec)
}

/**
 * A rider's bests, with what they were computed from.
 *
 * [ridesSkipped] is on the face of it bookkeeping and is really the honesty:
 * these are **measured** watts only, because a personal best derived from
 * `PowerModel` — which scores RMSE 137 W — is a fiction filed as a record. A
 * rider whose bike has never reported power sees an empty list, and the empty
 * list has to be able to say why rather than implying they have never ridden.
 */
data class PersonalBests(
    val efforts: List<PersonalBest> = emptyList(),
    val ridesCounted: Int = 0,
    val ridesSkipped: Int = 0,
    val isLoading: Boolean = false
) {
    val hasAny: Boolean get() = efforts.isNotEmpty()
}
