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
    @SerialName("target_position") val position: RidePosition? = null
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
