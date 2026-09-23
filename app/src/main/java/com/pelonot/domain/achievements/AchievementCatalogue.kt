package com.pelonot.domain.achievements

/**
 * The finite, equipment-free beginning of the achievement collection (PLAN 28.1).
 *
 * These milestones are deliberately about turning up, not about producing a
 * particular wattage. A simulated ride is still a ride and an hour in the
 * saddle is still an hour, so no rider needs a power meter, account, or cloud
 * connection to earn one. The catalogue stays data-shaped: its definitions are
 * immutable values rather than UI strings scattered through screens.
 */
object AchievementCatalogue {

    val definitions: List<AchievementDefinition> = listOf(
        AchievementDefinition.Rides("first_ride", "First ride", "One ride recorded", 1),
        AchievementDefinition.Rides("ten_rides", "Ten rides", "Ten rides recorded", 10),
        AchievementDefinition.Rides("twenty_five_rides", "Twenty-five rides", "Twenty-five rides recorded", 25),
        AchievementDefinition.Rides("fifty_rides", "Fifty rides", "Fifty rides recorded", 50),
        AchievementDefinition.Rides("hundred_rides", "A hundred rides", "A hundred rides recorded", 100),
        AchievementDefinition.Hours("first_hour", "First hour", "One hour in the saddle", 1),
        AchievementDefinition.Hours("ten_hours", "Ten hours", "Ten hours in the saddle", 10),
        AchievementDefinition.Hours("day_in_the_saddle", "A day in the saddle", "Twenty-four hours in the saddle", 24),
        AchievementDefinition.Hours("hundred_hours", "A hundred hours", "One hundred hours in the saddle", 100)
    )

    /** The immutable facts that can earn the initial volume collection. */
    fun earnedBy(evidence: AchievementEvidence): List<AchievementDefinition> =
        definitions.filter { it.isEarnedBy(evidence) }
}

/** Facts from completed rides only. An unfinished ride has not earned anything. */
data class AchievementEvidence(
    val completedRides: Int,
    val completedDurationSec: Long
)

/** One stable definition in the collection. Its id is what the future ledger stores. */
sealed interface AchievementDefinition {
    val id: String
    val title: String
    val detail: String

    fun isEarnedBy(evidence: AchievementEvidence): Boolean

    data class Rides(
        override val id: String,
        override val title: String,
        override val detail: String,
        val threshold: Int
    ) : AchievementDefinition {
        override fun isEarnedBy(evidence: AchievementEvidence): Boolean =
            evidence.completedRides >= threshold
    }

    data class Hours(
        override val id: String,
        override val title: String,
        override val detail: String,
        val thresholdHours: Int
    ) : AchievementDefinition {
        override fun isEarnedBy(evidence: AchievementEvidence): Boolean =
            evidence.completedDurationSec >= thresholdHours * SECONDS_PER_HOUR
    }

    private companion object {
        const val SECONDS_PER_HOUR = 60L * 60L
    }
}
