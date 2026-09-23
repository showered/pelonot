package com.pelonot.domain.achievements

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AchievementCatalogueTest {

    @Test
    fun volumeMilestonesAreExactThresholdsAndNeedNoPowerClaim() {
        assertTrue(AchievementCatalogue.earnedBy(AchievementEvidence(1, 0)).map { it.id }
            .contains("first_ride"))
        assertFalse(AchievementCatalogue.earnedBy(AchievementEvidence(9, 0)).map { it.id }
            .contains("ten_rides"))
        assertTrue(AchievementCatalogue.earnedBy(AchievementEvidence(10, 0)).map { it.id }
            .contains("ten_rides"))

        assertFalse(AchievementCatalogue.earnedBy(AchievementEvidence(0, 3_599)).map { it.id }
            .contains("first_hour"))
        assertTrue(AchievementCatalogue.earnedBy(AchievementEvidence(0, 3_600)).map { it.id }
            .contains("first_hour"))
    }

    @Test
    fun catalogueIsFiniteAndHasStableUniqueIds() {
        val definitions = AchievementCatalogue.definitions

        assertEquals(definitions.size, definitions.map { it.id }.distinct().size)
        assertEquals(
            listOf(
                "first_ride", "ten_rides", "twenty_five_rides", "fifty_rides", "hundred_rides",
                "first_hour", "ten_hours", "day_in_the_saddle", "hundred_hours"
            ),
            definitions.map { it.id }
        )
    }
}
