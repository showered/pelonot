package com.pelonot.domain.model

/** A free ride's finish line. Null means open-ended; stored distance is always km. */
sealed interface RideGoal {
    data class Time(val seconds: Int) : RideGoal {
        init { require(seconds > 0) }
    }
    data class Distance(val km: Double) : RideGoal {
        init { require(km.isFinite() && km > 0) }
    }

    fun reached(elapsedSec: Int, distanceKm: Double): Boolean = when (this) {
        is Time -> elapsedSec >= seconds
        is Distance -> distanceKm >= km
    }

    fun encode(): String = when (this) {
        is Time -> "time:$seconds"
        is Distance -> "distance:$km"
    }

    companion object {
        fun decode(spec: String?): RideGoal? = runCatching {
            val parts = spec?.split(':') ?: return null
            if (parts.size != 2) return null
            when (parts[0]) {
                "time" -> Time(parts[1].toInt())
                "distance" -> Distance(parts[1].toDouble())
                else -> null
            }
        }.getOrNull()

        fun distance(value: Double, units: UnitSystem): Distance =
            Distance(if (units == UnitSystem.IMPERIAL) value / UnitSystem.MILES_PER_KM else value)
    }
}
