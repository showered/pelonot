package com.pelonot.data.sensor

data class SensorReading(
    val powerWatts: Double,
    val cadenceRpm: Double,
    val resistancePercent: Double,
    val heartRateBpm: Int? = null,
    val timestampMs: Long = System.currentTimeMillis()
)
