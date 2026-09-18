package com.pelonot.domain.progress

/** Evidence about a training setting, not a certification of fitness. */
data class FtpAssessment(
    val label: String,
    val summary: String,
    val detail: String,
    val evidenceRideId: String? = null,
    val suggestedWatts: Int? = null,
    val currentWatts: Int,
    val isReduction: Boolean = false
) {
    companion object {
        // A product window for saying "recent", not a physiological expiry date.
        const val MIN_MEANINGFUL_GAIN = 1.02
        const val RECENT_MS = 90L * 24 * 60 * 60 * 1000

        fun evaluate(points: List<FtpPoint>, rides: List<FtpEvidenceRide>, now: Long, answeredAt: Long = 0L, historicalSupport: FtpEvidenceRide? = null): FtpAssessment? {
            val latest = points.lastOrNull() ?: return null
            val current = latest.watts
            if (current <= 0) return null
            val recent = rides.filter { it.recordedAt in (now - RECENT_MS)..now &&
                it.peak20MinWatts.isFinite() && it.peak20MinWatts > 0 }
                .sortedByDescending { it.recordedAt }
            val sinceSetting = recent.filter { it.recordedAt > maxOf(latest.atEpochMs, answeredAt) }
            val best = sinceSetting.maxByOrNull { it.impliedFtp }
            val reduction = FtpReductionRule.evaluate(sinceSetting, current)
            val workingShort = sinceSetting.filter { it.riderWasWorking }
                .takeWhile { it.impliedFtp <= current * FtpReductionRule.MIN_MEANINGFUL_LOSS }
                .take(FtpReductionRule.MIN_EVIDENCE_RIDES).size
            val earned = latest.source in setOf("AutoBreakthrough", "AutoReduction", "GuidedTest")
            // An unchanged starting number can be supported too. Rounding to a
            // whole watt is the display's precision, not an extra tolerance.
            val support = (recent + listOfNotNull(historicalSupport).filter {
                it.recordedAt <= now && it.peak20MinWatts.isFinite()
            }).firstOrNull { it.impliedFtp >= current - 0.5 &&
                (it.recordedAt > latest.atEpochMs || it.workoutId == latest.workoutId) }
            if (best != null && best.impliedFtp >= current * MIN_MEANINGFUL_GAIN) {
                return FtpAssessment("Ready to move up", "Your rides support ${best.impliedFtp.toInt()} W",
                    "Your strongest recent 20-minute effort suggests a higher training FTP. " +
                        "Review the ride, then choose whether to update your targets.",
                    best.workoutId, best.impliedFtp.toInt(), current)
            }
            if (reduction != null) {
                return FtpAssessment("Worth a review", "Three hard rides below your setting",
                    "Three qualifying hard rides suggest ${reduction.proposedFtp} W. " +
                        "This uses the strongest of those rides, not your worst day. " +
                        "Your targets stay unchanged unless you choose to update them.",
                    reduction.strongestRide.workoutId, reduction.proposedFtp, current, true)
            }
            val label = if (support != null || earned) "Ride-supported" else "Starting value"
            val summary = when {
                workingShort > 0 -> "$workingShort of 3 hard rides suggest a review"
                support != null && support.recordedAt >= now - RECENT_MS -> "Backed by a recent 20-minute effort"
                support != null -> "Backed by a previous 20-minute effort"
                earned -> "Earned from a ride · no recent confirmation"
                latest.source == "Estimated" -> "Estimated when you started"
                else -> "Not yet supported by a qualifying ride"
            }
            val detail = when {
                workingShort > 0 -> "One difficult day does not lower your FTP. We look for three hard " +
                    "rides with measured 20-minute efforts below your setting. Easy rides do not count against you."
                support != null -> "A measured 20-minute effort supports this setting. Ride-supported means " +
                    "there is riding evidence behind the estimate; it is not a lab measurement."
                earned -> "This setting came from an accepted ride-based estimate. There is no matching " +
                    "effort in the last 90 days. That does not mean you have lost fitness."
                else -> "Keep riding with measured power. We need a continuous 20-minute effort that supports " +
                    "this estimate; your FTP does not have to increase to earn recognition."
            }
            return FtpAssessment(label, summary, detail, support?.workoutId ?: latest.workoutId,
                currentWatts = current)
        }
    }
}
