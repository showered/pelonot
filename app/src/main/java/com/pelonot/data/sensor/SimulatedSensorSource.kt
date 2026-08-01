package com.pelonot.data.sensor

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Generates plausible telemetry so the full ride flow can be exercised on a
 * phone or emulator, where `/dev/ttyS2` does not exist.
 *
 * The profile is a slow effort wave with per-sample jitter, plus heart rate
 * that lags power the way a real rider's does. Power is computed with the same
 * [PowerModel] the serial path uses, so anything downstream — zones, energy
 * integration, FTP estimation — sees numerically consistent data rather than a
 * separate set of fake values.
 */
class SimulatedSensorSource(
    private val seed: Int = DEFAULT_SEED
) : SensorSource {

    override val id: String = "simulated"
    override val displayName: String = "Simulated rider (no hardware)"

    /** Always available — that is the entire point of it. */
    override fun isAvailable(): Boolean = true

    override fun readings(): Flow<SensorReading> = flow {
        val random = Random(seed)
        val startMs = System.currentTimeMillis()
        // Heart rate is integrated towards a power-derived target so it ramps
        // and recovers instead of jumping.
        var heartRate = RESTING_HR.toDouble()

        while (true) {
            val nowMs = System.currentTimeMillis()
            val elapsedSec = (nowMs - startMs) / 1000.0

            // 2.7.4's failure, on demand: a source that has stopped
            // delivering without failing. Deliberately `continue` rather than
            // throw — throwing is what the real board never did, and the
            // whole point is that silence has to be noticed by something else.
            if (nowMs < silentUntilMs) {
                delay(SAMPLE_INTERVAL_MS)
                continue
            }

            val effort = effortAt(elapsedSec)
            val coasting = nowMs < coastUntilMs

            // The knob does not move when a rider gets off; only the cranks
            // stop. Resistance therefore stays where it was, which is also what
            // the board reports on the real bike.
            val cadence = if (coasting) {
                0.0
            } else {
                (BASE_CADENCE + effort * CADENCE_SWING)
                    .plus(random.nextDouble(-CADENCE_JITTER, CADENCE_JITTER))
                    .coerceIn(0.0, 130.0)
            }

            val resistance = (BASE_RESISTANCE + effort * RESISTANCE_SWING)
                .plus(random.nextDouble(-RESISTANCE_JITTER, RESISTANCE_JITTER))
                .coerceIn(0.0, 100.0)

            val power = if (coasting) 0.0 else PowerModel.estimateWatts(cadence, resistance)

            val targetHr = RESTING_HR + (power / HR_WATTS_PER_BPM)
            heartRate += (targetHr - heartRate) * HR_RESPONSIVENESS
            val hrWithNoise = heartRate + random.nextDouble(-1.5, 1.5)

            val reading = SensorReading(
                powerWatts = power,
                cadenceRpm = cadence,
                resistancePercent = resistance,
                heartRateBpm = hrWithNoise.roundToInt().coerceIn(RESTING_HR, MAX_HR),
                timestampMs = nowMs
            )

            emit(if (nowMs < corruptUntilMs) corrupt(reading, random) else reading)

            delay(SAMPLE_INTERVAL_MS)
        }
    }

    /**
     * Effort in 0..1: a warmup ramp, then interval surges over a steady base.
     * Two sinusoids of different periods keep it from looking mechanical.
     */
    private fun effortAt(elapsedSec: Double): Double {
        val warmup = (elapsedSec / WARMUP_SEC).coerceIn(0.0, 1.0)
        val slowWave = sin(2 * PI * elapsedSec / SLOW_PERIOD_SEC)
        val surge = sin(2 * PI * elapsedSec / SURGE_PERIOD_SEC)
        val combined = 0.5 + 0.3 * slowWave + 0.2 * surge
        return (warmup * combined).coerceIn(0.0, 1.0)
    }

    /**
     * Reproduces what the bike did on 1 August 2026 with the overlay up (2.7).
     *
     * The signature to match is in the plan: the three real values appearing
     * in each other's columns, and a ghost near 602 that is none of them
     * turning up in whichever column it likes. Roughly three samples in four
     * carry the ghost, as 41 of 53 did on the bike — the rest are a plain
     * rotation, which matters because **the plausibility fence cannot see
     * those**. A cadence of 33 and a resistance of 53 are both perfectly
     * possible numbers; they are simply the wrong way round. That half of the
     * defect is what [TelemetryAssembler] and the single-registration rule
     * address, and no bound will ever catch it.
     */
    private fun corrupt(reading: SensorReading, random: Random): SensorReading {
        val ghost = if (random.nextDouble() < GHOST_SHARE) {
            GHOST_VALUE + random.nextDouble(0.0, 3.0)
        } else {
            null
        }
        return when (random.nextInt(3)) {
            0 -> reading.copy(
                cadenceRpm = ghost ?: reading.powerWatts,
                resistancePercent = reading.cadenceRpm,
                powerWatts = reading.resistancePercent
            )
            1 -> reading.copy(
                cadenceRpm = reading.resistancePercent,
                resistancePercent = ghost ?: reading.cadenceRpm,
                powerWatts = reading.cadenceRpm
            )
            else -> reading.copy(
                cadenceRpm = reading.resistancePercent,
                resistancePercent = reading.powerWatts,
                powerWatts = ghost ?: reading.cadenceRpm
            )
        }
    }

    companion object {
        /**
         * Wall-clock instant the simulated rider starts pedalling again.
         *
         * The simulated rider never stops, which means **nothing about a rider
         * standing still can be exercised without a bike** — auto-pause
         * (19.1.2), the gap a stop leaves in the series, what the averages do
         * across one. This is the lever that makes those observable: the debug
         * build has a receiver that sets it, and a release build has no way to
         * reach it at all.
         *
         * Volatile because the flow reads it on its own coroutine.
         */
        @Volatile
        private var coastUntilMs: Long = 0L

        /** The simulated rider stops pedalling for [seconds]. Debug builds only. */
        fun coastFor(seconds: Int) {
            coastUntilMs = System.currentTimeMillis() + seconds * 1000L
        }

        /**
         * Wall-clock instant the fabricated corruption stops (2.7).
         *
         * The defect was found on a bike with a rider on it, which is not a
         * thing to spend on a regression. These two levers put both halves of
         * it on the emulator: values that cannot be true, and a source that
         * goes quiet without failing.
         */
        @Volatile
        private var corruptUntilMs: Long = 0L

        /** The board reports nonsense for [seconds]. Debug builds only. */
        fun corruptFor(seconds: Int) {
            corruptUntilMs = System.currentTimeMillis() + seconds * 1000L
        }

        /** Wall-clock instant the source starts reporting again. */
        @Volatile
        private var silentUntilMs: Long = 0L

        /**
         * The source stops delivering for [seconds] — without erroring, which
         * is the part that made this survivable for a whole ride. Debug builds
         * only.
         */
        fun silenceFor(seconds: Int) {
            silentUntilMs = System.currentTimeMillis() + seconds * 1000L
        }

        /** The value near 602 the bike produced that is none of the three metrics. */
        private const val GHOST_VALUE = 602.0

        /** 41 of the bike's 53 corrupted samples carried an impossible value. */
        private const val GHOST_SHARE = 41.0 / 53.0

        private const val DEFAULT_SEED = 0x50E10
        private const val SAMPLE_INTERVAL_MS = 250L

        private const val BASE_CADENCE = 62.0
        private const val CADENCE_SWING = 38.0
        private const val CADENCE_JITTER = 2.5

        private const val BASE_RESISTANCE = 28.0
        private const val RESISTANCE_SWING = 34.0
        private const val RESISTANCE_JITTER = 1.5

        private const val RESTING_HR = 62
        private const val MAX_HR = 190
        private const val HR_WATTS_PER_BPM = 2.1
        private const val HR_RESPONSIVENESS = 0.04

        private const val WARMUP_SEC = 45.0
        private const val SLOW_PERIOD_SEC = 210.0
        private const val SURGE_PERIOD_SEC = 47.0
    }
}
