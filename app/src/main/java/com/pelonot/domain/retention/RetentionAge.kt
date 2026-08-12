package com.pelonot.domain.retention

/**
 * How old a ride has to be before its second-by-second record is trimmed
 * (PLAN 23.4.4).
 *
 * **[Never] is the default and it is the whole ethics of the feature.** An app
 * that quietly deletes the seconds of a rider's best ever ride has done the one
 * thing this project exists not to do, so the rider chooses an age or the app
 * keeps everything — there is no "sensible default" here that is not somebody
 * else deciding what a rider's record is worth.
 *
 * **Three answers, and 26.3 is why there are not six.** Months are the unit a
 * rider thinks in about their own history, a week is the wrong window for
 * somebody who rides once of them (22.5), and the difference between 90 and 120
 * days is not a decision anybody can make about a ride they have not done yet.
 */
enum class RetentionAge(val days: Int?, val label: String) {
    Never(null, "Never"),
    SixMonths(183, "After 6 months"),
    OneYear(365, "After a year");

    val isOn: Boolean get() = days != null

    /** The instant a ride has to be older than to be eligible, or null for [Never]. */
    fun cutoffMs(nowMs: Long): Long? = days?.let { nowMs - it * MS_PER_DAY }

    companion object {
        val DEFAULT = Never

        /** Unknown names read back as [Never] — the answer that destroys nothing. */
        fun fromName(name: String?): RetentionAge =
            entries.firstOrNull { it.name == name } ?: DEFAULT

        private const val MS_PER_DAY = 24L * 60 * 60 * 1_000
    }
}
