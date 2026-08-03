package com.pelonot.domain.model

import kotlin.math.roundToInt

/**
 * The whole zone ladder, ready to draw: seven segments, the watts each one
 * starts at, and where the rider is standing on it.
 *
 * Why a ladder rather than a label (PLAN.md 11.6.2a): "you are in Z2" only
 * places a rider who already holds the boundaries in their head. The scale
 * shows the boundaries *in watts*, which turns the reading into an
 * instruction — "215 W gets you into 3" — and it shows how far through a zone
 * the rider is, which Z3-just-in and Z3-nearly-out cannot otherwise be told
 * apart.
 *
 * Every watt figure here is derived from FTP, so it moves under the rider when
 * auto-FTP accepts a breakthrough. See 7.8.
 */
data class ZoneScale(
    /** One entry per [PowerZone], in order. */
    val segments: List<Segment>,
    /** The zone the rider is in, or null when there is nothing to say. */
    val current: PowerZone?,
    /** How far through [current] the rider is, 0..1. Zero when [current] is null. */
    val fractionThroughZone: Float,
    /** Live power as a percentage of FTP, or null when there is no reading. */
    val ftpPercent: Int?,
    /** The zone the class is asking for, or null on a free ride. */
    val prescribed: PowerZone?
) {
    data class Segment(
        val zone: PowerZone,
        /** The watts this zone starts at — the number drawn under its left edge. */
        val startWatts: Int,
        /** The watts the next zone starts at. [PowerZone.Z7] has no ceiling, so this is its own top. */
        val endWatts: Int
    )

    /** The watts that would put the rider into the next zone up, or null at the top. */
    val wattsToNextZone: Int?
        get() = current?.let { zone ->
            segments.getOrNull(zone.ordinal + 1)?.startWatts
        }

    /**
     * Where the rider is standing on the **whole** ladder, 0..1 — the one
     * coordinate the scale is drawn from (11.6.11).
     *
     * [fractionThroughZone] is a position within a rung, so it resets to zero
     * every time the rider crosses a boundary. Animating *that* is what made
     * the ladder recoil: the fill was driven backwards across the full width of
     * a segment before growing again inside the next one, at the exact moment
     * the rider was looking at it to see that something had changed. The rung
     * and the fraction are two coordinates for one thing a rider reads as one —
     * how hard am I going — so the drawing takes one number and a boundary
     * stops being an event.
     *
     * Rungs are drawn at equal width, so this is deliberately **not** linear in
     * watts: Z6 is twice as wide in watts as Z2 and gets the same span here,
     * which is the same compression the segments themselves already are. What
     * it *is* is **monotonic** in power — the property the per-zone fraction
     * broke twice per boundary, and the one `ZoneScaleTest` holds.
     *
     * Zero when there is no reading, which is 2.4.4's rule reaching the
     * drawing: a bar that stays where it was over a board that has gone quiet
     * is the frozen-cadence lie in another costume.
     */
    val ladderPosition: Float
        get() = current?.let { zone ->
            (zone.ordinal + fractionThroughZone) / segments.size.toFloat()
        } ?: 0f

    companion object {

        /**
         * The zone the rider is in, or null when there is nothing to say —
         * **the app's single rule for that question**.
         *
         * [PowerZone.forPower] answers Z1 for zero watts and for an unknown
         * FTP, which is true and useless: "Active Recovery" over a bike nobody
         * is pedalling, or over a board that has gone quiet, is the same class
         * of claim as a frozen cadence (2.4.4). Both the ride screen and the
         * overlay ask through here so they cannot come to different answers.
         */
        fun currentZone(ftp: Int, powerWatts: Double, telemetryLive: Boolean): PowerZone? =
            if (ftp <= 0 || powerWatts <= 0.0 || !telemetryLive) {
                null
            } else {
                PowerZone.forPower(powerWatts, ftp.toDouble())
            }

        /** The ladder for a live reading, with [currentZone]'s rule already applied. */
        fun forReading(
            ftp: Int,
            powerWatts: Double,
            telemetryLive: Boolean,
            prescribed: PowerZone? = null
        ): ZoneScale {
            val zone = currentZone(ftp, powerWatts, telemetryLive)
            return forRider(
                ftp = ftp,
                zone = zone,
                powerWatts = if (zone != null) powerWatts else null,
                prescribed = prescribed
            )
        }

        /**
         * Build the ladder for a rider with this [ftp].
         *
         * [zone] is passed in rather than derived so that the caller's rule
         * about *when there is no zone* is the only such rule in the app:
         * [PowerZone.forPower] answers Z1 for zero watts and for an unknown
         * FTP, which is true and useless — a bike nobody is pedalling would be
         * labelled Active Recovery. Same family as 2.4.4: the absence is the
         * answer.
         */
        fun forRider(
            ftp: Int,
            zone: PowerZone?,
            powerWatts: Double?,
            prescribed: PowerZone? = null
        ): ZoneScale {
            val ftpDouble = ftp.toDouble()
            val segments = PowerZone.entries.map { z ->
                val range = z.powerRange(ftpDouble)
                Segment(
                    zone = z,
                    startWatts = range.start.roundToInt(),
                    endWatts = range.endInclusive.roundToInt()
                )
            }

            val fraction = if (zone != null && powerWatts != null && ftp > 0) {
                val range = zone.powerRange(ftpDouble)
                val span = range.endInclusive - range.start
                if (span <= 0.0) 0f
                else ((powerWatts - range.start) / span).toFloat().coerceIn(0f, 1f)
            } else {
                0f
            }

            val percent = if (powerWatts != null && ftp > 0) {
                (powerWatts / ftpDouble * 100).roundToInt()
            } else {
                null
            }

            return ZoneScale(
                segments = segments,
                current = zone,
                fractionThroughZone = fraction,
                ftpPercent = percent,
                prescribed = prescribed
            )
        }
    }
}
