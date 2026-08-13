package com.pelonot.domain.cloud

/**
 * What the account holds, and how much of it this tablet is missing (15.3.2).
 *
 * Both numbers rather than the difference, because they answer different
 * questions and a rider asks both: *is my history up there at all* and *is any
 * of it missing here*. [missingHere] of 0 with [inCloud] of 40 is the ordinary,
 * reassuring case and reads as nothing at all if only the difference is carried.
 */
data class RestoreSurvey(
    val inCloud: Int,
    val missingHere: Int
) {
    val hasSomethingToBringDown: Boolean get() = missingHere > 0
}

/**
 * What a restore actually brought down (PLAN 15.3.2).
 *
 * Counted as it goes rather than reported from a status, for
 * [CloudDeletion]'s reason: a restore that matched nothing and a restore that
 * brought back two years of riding both finish quietly.
 *
 * @param rides rides written to this tablet that were not on it before.
 * @param samples their seconds, which is the number that says whether a
 *   *record* came back or only a row of totals.
 * @param unreadable rides skipped whole because their date or their series
 *   could not be read. Reported rather than swallowed — a rider missing three
 *   rides is entitled to know that three were refused rather than absent.
 * @param classesNotHere rides restored as free rides because the class they
 *   were ridden to is not in this build's library. The ride is intact; what is
 *   lost is the name at the top of it and the blocks under its chart.
 * @param profileAdopted whether the rider's name, weight and FTP came down too,
 *   which only happens for a profile that has never ridden on this bike.
 */
data class RestoreOutcome(
    val rides: Int,
    val samples: Int,
    val unreadable: Int = 0,
    val classesNotHere: Int = 0,
    val profileAdopted: Boolean = false
)
