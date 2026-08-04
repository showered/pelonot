package com.pelonot.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * One prescribed segment of a class.
 *
 * The field names here must match the on-disk asset format exactly. The
 * previous model declared camelCase properties (`durationSec`,
 * `targetPowerMin`, …) against assets written in snake_case with
 * start/end timestamps, so every decode threw and was swallowed by a
 * `catch { emptyList() }` — meaning no class ever displayed any intervals.
 */
/**
 * Whether an interval asks to be ridden standing or seated (PLAN 25.1).
 *
 * The one instruction a bike class gives that neither zone nor cadence can
 * express: a 60 rpm effort at Z4 seated and the same effort out of the saddle
 * are different workouts in the legs.
 */
@Serializable
enum class RidePosition {
    @SerialName("standing") Standing,
    @SerialName("seated") Seated;

    /** What the rider is told. Short, because it is read at two metres. */
    val displayName: String
        get() = when (this) {
            Standing -> "Stand"
            Seated -> "Sit"
        }

    /** The long form, for the class detail list and the spoken coach. */
    val instruction: String
        get() = when (this) {
            Standing -> "Out of the saddle"
            Seated -> "Stay seated"
        }
}

/**
 * Which of a block's two axes is *the instruction* (PLAN 11.7.2).
 *
 * The owner's complaint, riding: *"what do i do? do i focus on zone, cadence,
 * or resistance?"* It was never three instructions. Power is not something a
 * rider does — it is what happens when you turn the pedals at some cadence
 * against some resistance, which is literally [com.pelonot.data.sensor
 * .PowerModel]. One outcome and two controls, drawn as three tiles of equal
 * weight and equal off-target signalling.
 *
 * Resistance is deliberately not a value here. No class prescribes it; the
 * band is `PowerModel` inverted, and that curve scores RMSE 137 W against the
 * board's own measured watts. It is the knob, not a target.
 */
@Serializable
enum class GovernedBy {
    @SerialName("power") Power,
    @SerialName("cadence") Cadence
}

@Serializable
data class Interval(
    @SerialName("time_start_sec") val startSec: Int,
    @SerialName("time_end_sec") val endSec: Int,
    @SerialName("target_cadence_min") val cadenceMin: Int,
    @SerialName("target_cadence_max") val cadenceMax: Int,
    @SerialName("target_power_zone") val powerZoneNumber: Int,

    /**
     * Standing, seated, or **absent — which is the default and means the rider
     * chooses**.
     *
     * Optional in the schema as well as in spirit, so every class written
     * before 25.1 decodes unchanged. Nullable rather than defaulted to
     * `Seated` for the reason `heartRateBpm`, `power_is_measured` and
     * `retired_at` are each nullable: **absent is a claim, and it is a
     * different claim from either value**. A class that prescribes a position
     * for every one of its blocks is nagging rather than coaching, and a
     * default would make every such class look like that.
     */
    @SerialName("target_position") val position: RidePosition? = null,

    /**
     * Which metric this block is actually asking for — **and absent means
     * power**, which is why the field is defaulted rather than nullable.
     *
     * Deliberately *not* the shape `position` takes above. There absent is a
     * third claim ("the rider chooses") and a default would erase it; here
     * absent is simply the ordinary case, and 840 of the library's 1071 blocks
     * write nothing. Optional and additive either way, so every class authored
     * before the field decodes unchanged.
     */
    @SerialName("governed_by") val governedBy: GovernedBy = GovernedBy.Power
) {
    val durationSec: Int get() = (endSec - startSec).coerceAtLeast(0)

    val powerZone: PowerZone get() = PowerZone.forNumber(powerZoneNumber)

    /** True for warmup/recovery segments, used to phrase coaching cues. */
    val isRecovery: Boolean get() = powerZoneNumber <= 2

    fun containsSecond(elapsedSec: Int): Boolean =
        elapsedSec >= startSec && elapsedSec < endSec

    fun targetPowerRange(ftp: Double, intent: RideIntent) =
        powerZone.targetPowerRange(ftp, intent)
}

/**
 * Parses the `intervals_json` column of a class template.
 *
 * Templates are authored by hand, so a malformed one is a data problem worth
 * seeing rather than silently rendering an empty class. [parseOrEmpty] keeps
 * the lenient behaviour for UI paths that cannot surface an error.
 */
object IntervalParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun parse(intervalsJson: String): Result<List<Interval>> = runCatching {
        json.decodeFromString<List<Interval>>(intervalsJson)
    }

    fun parseOrEmpty(intervalsJson: String): List<Interval> =
        parse(intervalsJson).getOrDefault(emptyList())
}
