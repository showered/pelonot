package com.pelonot.domain.retention

/**
 * Which seconds of a ride survive being trimmed (PLAN 23.4.2).
 *
 * Pure, generic and free of Room on purpose: the repository hands it whole
 * `workout_metrics` rows and gets a shorter list of the **same rows** back, so
 * nothing here has to know what a sample is made of beyond its clock and its
 * watts.
 *
 * **Nothing is averaged, and that is the whole design.** 16.2.2 already made
 * this argument for drawing — collapsing nine seconds into their mean turns a
 * 600 W spike into a 250 W bump, and the sprint is the single thing a rider most
 * wants to see afterwards. It matters more here than it does there, because a
 * chart redraws and a trim does not: a mean written back over the samples is a
 * number the bike never measured, filed permanently in the place the
 * measurements used to be. So a trimmed ride is made only of seconds the rider
 * really rode, and the *record* stays a record at lower resolution rather than
 * becoming a reconstruction.
 *
 * What is kept per bucket is the **lowest and highest power**, which is
 * [com.pelonot.domain.chart.TraceBucket]'s min and max arriving as real rows.
 * The consequence to know is that the other three columns come along for the
 * ride: the second holding the bucket's peak watts carries its own cadence and
 * heart rate, and a cadence peak that happened in an easy second of a hard
 * bucket is gone. Power is the metric every other reader gates on (the boards,
 * the bests, the FTP proposal), so it is the one the sampling is built around,
 * and `workouts.metrics_detail_sec` says out loud that what is left is an
 * outline (23.4.3).
 */
object MetricTrim {

    /**
     * Ten seconds, which is 16.2.2's own bucket arriving in storage.
     *
     * A 45-minute ride is ~2,700 rows and comes back as ~540: two per bucket
     * rather than one, so the reduction is about five-fold rather than the
     * ten-fold a mean would give. That is the price of the paragraph above and
     * it is worth paying — and it is measured rather than promised, because
     * a ride with long steady stretches keeps fewer (min and max of a bucket
     * are often the same second, and it is only kept once).
     */
    const val BUCKET_SEC = 10

    /**
     * The rows to keep, in their original order.
     *
     * The first and last seconds of the ride are always among them: they are
     * the axis every chart is drawn on, and a trace whose ends had been sampled
     * away would draw a ride that started late and finished early.
     *
     * Buckets are cut by **elapsed time from the first sample**, never by
     * index — a ride with a bottle stop in it has a gap, and bucketing by index
     * would quietly close the gap up, which is `RideChartBuilder`'s rule and
     * `MeanMaximalPower`'s for the same reason.
     */
    fun <T> keep(
        samples: List<T>,
        bucketSec: Int = BUCKET_SEC,
        timestampSec: (T) -> Int,
        watts: (T) -> Double
    ): List<T> {
        if (samples.size <= 2 || bucketSec <= 1) return samples

        val ordered = samples.sortedBy(timestampSec)
        val start = timestampSec(ordered.first())

        val kept = HashSet<Int>(ordered.size)
        kept += timestampSec(ordered.first())
        kept += timestampSec(ordered.last())

        ordered
            .groupBy { (timestampSec(it) - start) / bucketSec }
            .forEach { (_, bucket) ->
                kept += timestampSec(bucket.minBy(watts))
                kept += timestampSec(bucket.maxBy(watts))
            }

        return ordered.filter { timestampSec(it) in kept }
    }
}
