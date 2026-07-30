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

            val effort = effortAt(elapsedSec)

            val cadence = (BASE_CADENCE + effort * CADENCE_SWING)
                .plus(random.nextDouble(-CADENCE_JITTER, CADENCE_JITTER))
                .coerceIn(0.0, 130.0)

            val resistance = (BASE_RESISTANCE + effort * RESISTANCE_SWING)
                .plus(random.nextDouble(-RESISTANCE_JITTER, RESISTANCE_JITTER))
                .coerceIn(0.0, 100.0)

            val power = PowerModel.estimateWatts(cadence, resistance)

            val targetHr = RESTING_HR + (power / HR_WATTS_PER_BPM)
            heartRate += (targetHr - heartRate) * HR_RESPONSIVENESS
            val hrWithNoise = heartRate + random.nextDouble(-1.5, 1.5)

            emit(
                SensorReading(
                    powerWatts = power,
                    cadenceRpm = cadence,
                    resistancePercent = resistance,
                    heartRateBpm = hrWithNoise.roundToInt().coerceIn(RESTING_HR, MAX_HR),
                    timestampMs = nowMs
                )
            )

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

    private companion object {
        const val DEFAULT_SEED = 0x50E10
        const val SAMPLE_INTERVAL_MS = 250L

        const val BASE_CADENCE = 62.0
        const val CADENCE_SWING = 38.0
        const val CADENCE_JITTER = 2.5

        const val BASE_RESISTANCE = 28.0
        const val RESISTANCE_SWING = 34.0
        const val RESISTANCE_JITTER = 1.5

        const val RESTING_HR = 62
        const val MAX_HR = 190
        const val HR_WATTS_PER_BPM = 2.1
        const val HR_RESPONSIVENESS = 0.04

        const val WARMUP_SEC = 45.0
        const val SLOW_PERIOD_SEC = 210.0
        const val SURGE_PERIOD_SEC = 47.0
    }
}
