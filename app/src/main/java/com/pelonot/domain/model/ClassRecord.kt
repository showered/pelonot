package com.pelonot.domain.model

/** An all-time result on this bike, never a live or cross-bike standing. */
data class ClassRecord(val outputKj: Double, val isYours: Boolean, val rank: Int?, val riders: Int) {
    companion object {
        fun from(bests: Map<Int, Double>, youId: Int?): ClassRecord? {
            val best = bests.values.maxOrNull() ?: return null
            val yours = bests[youId]
            return ClassRecord(
                outputKj = yours ?: best,
                isYours = yours != null,
                rank = if (yours != null && bests.size > 1) 1 + bests.values.count { it > yours } else null,
                riders = bests.size
            )
        }
    }
}
