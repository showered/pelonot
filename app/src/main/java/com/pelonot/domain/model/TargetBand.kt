package com.pelonot.domain.model

/**
 * A prescribed range — cadence or power — and everything the UI needs to draw
 * "am I on target?" as a gauge rather than a number the rider has to compare
 * against another number while breathing hard.
 *
 * The displayed window is deliberately wider than the band itself. A gauge that
 * spans only the target range pins its marker to an end stop the moment the
 * rider drifts out of it, which is exactly when it needs to say *how far* out
 * they are.
 */
data class TargetBand(val min: Double, val max: Double) {

    val width: Double get() = (max - min).coerceAtLeast(0.0)

    /** False for a free ride, where nothing is prescribed. */
    val isDefined: Boolean get() = max > min && max > 0.0

    operator fun contains(value: Double): Boolean = value in min..max

    fun statusFor(value: Double): TargetStatus = when {
        !isDefined -> TargetStatus.Unset
        value < min -> TargetStatus.Below
        value > max -> TargetStatus.Above
        else -> TargetStatus.OnTarget
    }

    /** Lower edge of the drawn gauge. Never negative — no metric here can be. */
    val displayMin: Double get() = (min - margin).coerceAtLeast(0.0)

    val displayMax: Double get() = max + margin

    /** Where [value] sits along the drawn gauge, 0f–1f. */
    fun fractionOf(value: Double): Float {
        val span = displayMax - displayMin
        if (span <= 0.0) return 0f
        return ((value - displayMin) / span).toFloat().coerceIn(0f, 1f)
    }

    /** Where the target band itself starts and ends along the gauge. */
    val bandStartFraction: Float get() = fractionOf(min)
    val bandEndFraction: Float get() = fractionOf(max)

    private val margin: Double
        get() = (width * MARGIN_RATIO).coerceAtLeast(MIN_MARGIN)

    companion object {
        /** Headroom either side of the band, as a fraction of its width. */
        private const val MARGIN_RATIO = 0.6

        /** Floor for the headroom, so a one-unit band still has a gauge. */
        private const val MIN_MARGIN = 5.0

        val NONE = TargetBand(0.0, 0.0)

        fun of(range: ClosedFloatingPointRange<Double>) =
            TargetBand(range.start, range.endInclusive)

        fun of(min: Int, max: Int) = TargetBand(min.toDouble(), max.toDouble())
    }
}

/** Where a live value sits relative to its prescribed band. */
enum class TargetStatus {
    /** Nothing prescribed — a free ride. */
    Unset,
    Below,
    OnTarget,
    Above;

    val isOffTarget: Boolean get() = this == Below || this == Above
}

/** The cadence band prescribed by this interval. */
val Interval.cadenceBand: TargetBand get() = TargetBand.of(cadenceMin, cadenceMax)

/** The power band prescribed by this interval, scaled by the rider's intent. */
fun Interval.powerBand(ftp: Double, intent: RideIntent): TargetBand =
    TargetBand.of(targetPowerRange(ftp, intent))
