package com.pelonot.data.remote.dto

import com.pelonot.data.local.entity.WorkoutMetricEntity
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.math.abs
import kotlin.math.floor

/**
 * A ride's per-second series on the wire, **columnar** (PLAN 14.4).
 *
 * The old shape was an array of 2,700 five-key objects, which repeats the key
 * names 13,500 times: a 45-minute ride posted **228 KB** in a single insert and
 * stored about 30 KB of it. The same data as parallel arrays is **54 KB** on
 * the wire — 49 KB before provenance joined it (14.4.7) — and about 19 KB
 * stored. The storage is the smaller half: the request body is the point, and a
 * 90-minute ride was posting 457 KB in one go.
 *
 * It is changed now because it is free now. `workouts` holds one row in the
 * cloud; the same change costs a migration with a backfill once four riders
 * have a year of history up there.
 *
 * Three decisions inside it, and the first two are the ones that matter.
 *
 * **`t` stays explicit** (14.4.2). Implying the timestamp from the array index
 * would save another 12 KB and it is exactly the wrong 12 KB: a stalled board
 * leaves a real gap in the series (2.4.4), the charts draw those gaps on
 * purpose (16.1.2, 16.2.2), and a ride with a two-minute bottle stop in it
 * genuinely has one. A cloud copy that looks continuous when the ride was not
 * is a fabricated record.
 *
 * **The version travels inside the payload**, not beside it (14.4.3). The item
 * said "on the row", and a column would be queryable — but a column is written
 * by different code to the JSON it describes, so the two can drift, and a
 * version that disagrees with its payload is worse than no version. Inside the
 * object it cannot: `payload->>'v'` is queryable in Postgres anyway, and any
 * future reader holding only the JSON — the web app (17.3) is the one that will
 * care — can still decide what shape it is looking at. **An absent `v` means
 * the pre-14.4 array-of-objects**, which is the one row already up there.
 *
 * **`hr` is omitted when nothing wore a strap.** An absent array and an array
 * of 2,700 nulls are the same claim — *nobody measured this* — and the absent
 * one costs 13 KB less. This is safe precisely because it is not the claim a
 * *zero* would make: null is unknown, 0 is a rider with no pulse, and the two
 * have been confused in this project before.
 *
 * **`pm` is per sample, not per ride** (14.4.7). The tempting version is a
 * scalar on the row, since `PowerProvenance` reduces the series to one answer
 * anyway — but it reduces it *from* the samples, and a board that drops out
 * mid-ride is [PowerProvenance.Mixed][com.pelonot.domain.model.PowerProvenance]
 * precisely because the samples disagree. A scalar would have to pick one of
 * them, which is the same fabrication `t` refuses to commit above. It follows
 * `hr`'s rule for absence — no array means every sample unknown, which is what
 * every ride recorded before the column existed is — and its own compact
 * encoding below keeps it inside the size budget.
 */
@Serializable
data class MetricsPayload(
    @SerialName("v") val version: Int = VERSION,
    /** Elapsed seconds. Explicit, and never implied from the index. */
    @SerialName("t") val timestampSec: List<Int> = emptyList(),
    @SerialName("c") val cadence: List<@Serializable(CompactDouble::class) Double> = emptyList(),
    @SerialName("r") val resistance: List<@Serializable(CompactDouble::class) Double> = emptyList(),
    @SerialName("p") val power: List<@Serializable(CompactDouble::class) Double> = emptyList(),
    /** Null where the sample had no heart rate; absent where no sample did. */
    @SerialName("hr") val heartRate: List<Int?>? = null,
    /**
     * `1` where the watts came off the board, `0` where the model made them up,
     * null where nobody wrote it down. Absent where no sample did.
     */
    @SerialName("pm")
    val powerIsMeasured: List<@Serializable(CompactBoolean::class) Boolean?>? = null
) {

    val size: Int get() = timestampSec.size

    /**
     * Back to entities, or a failure — never a repaired half-record.
     *
     * Columns of different lengths mean the payload was truncated or written by
     * something that did not understand it, and the fields no longer line up:
     * sample 900's power would be filed against sample 900's cadence only by
     * luck. That is the same failure 2.7 was, and the same answer — **reject,
     * do not clamp**. A caller gets nothing and knows it got nothing.
     */
    fun toMetrics(workoutId: String): Result<List<WorkoutMetricEntity>> {
        val lengths = buildList {
            add("t" to timestampSec.size)
            add("c" to cadence.size)
            add("r" to resistance.size)
            add("p" to power.size)
            heartRate?.let { add("hr" to it.size) }
            powerIsMeasured?.let { add("pm" to it.size) }
        }
        val disagree = lengths.filter { it.second != size }
        if (disagree.isNotEmpty()) {
            return Result.failure(
                IllegalArgumentException(
                    "metrics payload columns disagree: $size samples but " +
                        disagree.joinToString { "${it.first}=${it.second}" }
                )
            )
        }

        return Result.success(
            List(size) { index ->
                WorkoutMetricEntity(
                    workoutId = workoutId,
                    timestampSec = timestampSec[index],
                    cadence = cadence[index],
                    resistance = resistance[index],
                    power = power[index],
                    // An absent column is every sample unknown, which is what a
                    // ride with no strap is.
                    heartRate = heartRate?.get(index),
                    // Absent is *unknown*, never modelled: "the app made this
                    // up" is a different claim from "nobody wrote it down",
                    // and only one of them is safe to invent.
                    powerIsMeasured = powerIsMeasured?.get(index)
                )
            }
        )
    }

    /**
     * Writes a whole number as `80` rather than `80.0`, and everything else
     * unchanged.
     *
     * Not a rounding and not a precision decision — the value is identical
     * either way, and `80` parses back to exactly `80.0`. It is two characters
     * per sample, which sounds like nothing until you notice there are three
     * such columns: **11 KB of a 45-minute ride is trailing zeroes**, because
     * the board reports whole cadences and whole resistances (measured on the
     * bike's own database — every `cadence` and `resistance` row is integral,
     * while power genuinely arrives in tenths).
     *
     * Power keeps its tenth. `29.7` stays `29.7`, because the board really does
     * report 0.1 W and 14.4.6's question was whether those digits are data or
     * float noise. They are data: the noise — `29.2000007629395` — belonged to
     * the pre-2.7c `getFloat().toDouble()` path and is gone from every ride
     * recorded since.
     */
    object CompactDouble : KSerializer<Double> {
        override val descriptor: SerialDescriptor =
            PrimitiveSerialDescriptor("CompactDouble", PrimitiveKind.DOUBLE)

        override fun serialize(encoder: Encoder, value: Double) {
            // The magnitude guard is not defensive dressing: beyond 2^53 a
            // double cannot hold every integer, and Long would print a value
            // the Double never had.
            if (value.isFinite() && value == floor(value) && abs(value) < MAX_EXACT_INTEGER) {
                encoder.encodeLong(value.toLong())
            } else {
                encoder.encodeDouble(value)
            }
        }

        override fun deserialize(decoder: Decoder): Double = decoder.decodeDouble()

        private const val MAX_EXACT_INTEGER = 9_007_199_254_740_992.0
    }

    /**
     * Writes provenance as `1` and `0` rather than `true` and `false`.
     *
     * Three characters a sample sounds like pedantry and is the difference
     * between carrying the field and not: `true` across 2,700 samples is
     * **13 KB** on a 49 KB payload, which puts the ride over the budget the
     * round-trip test asserts; `1` is 5 KB and leaves it inside. The saving is
     * the reason the column can be per sample at all (14.4.7), so it buys
     * honesty rather than only bytes.
     *
     * Anything non-zero reads back as measured, so a future writer emitting
     * JSON's own `true` is not a decode failure — but this one writes digits.
     */
    object CompactBoolean : KSerializer<Boolean> {
        override val descriptor: SerialDescriptor =
            PrimitiveSerialDescriptor("CompactBoolean", PrimitiveKind.INT)

        override fun serialize(encoder: Encoder, value: Boolean) =
            encoder.encodeInt(if (value) 1 else 0)

        override fun deserialize(decoder: Decoder): Boolean = decoder.decodeInt() != 0
    }

    companion object {
        /**
         * 1 is the columnar shape. There is no 0 — a payload written before
         * this existed carries no `v` at all, which is how it is recognised.
         */
        const val VERSION = 1

        /**
         * What a 45-minute ride measured at, so a regression has a number to
         * fail against.
         *
         * 49 KB when the shape landed, **54 KB** once provenance joined it
         * (14.4.7) — the budget moved with it rather than the column being
         * dropped to fit, because the alternative to carrying it is a cloud
         * copy that cannot tell a bike ride from a simulated one. Written as
         * `true`/`false` it would not have fitted at all; see [CompactBoolean].
         */
        const val FORTY_FIVE_MINUTE_BUDGET_BYTES = 60 * 1024

        fun from(metrics: List<WorkoutMetricEntity>) = MetricsPayload(
            timestampSec = metrics.map { it.timestampSec },
            cadence = metrics.map { it.cadence },
            resistance = metrics.map { it.resistance },
            power = metrics.map { it.power },
            heartRate = metrics.map { it.heartRate }.takeIf { hr -> hr.any { it != null } },
            powerIsMeasured = metrics.map { it.powerIsMeasured }
                .takeIf { pm -> pm.any { it != null } }
        )
    }
}
