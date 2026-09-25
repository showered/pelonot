package com.pelonot.data.remote.dto

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityFeedDtoTest {
    @Test
    fun `the server's feed row carries the ride and kudos action state`() {
        val row = Json.decodeFromString<ActivityFeedDto>(
            """{
                "workout_id":"89c5a006-9e20-42fd-8c22-0d6c1ca40230",
                "account_id":"b5ba2bd9-b07a-465b-9354-9d6b26e7b714",
                "name":"Alex",
                "is_you":false,
                "title":null,
                "class_title":"Zone 2 Steady",
                "recorded_at":"2026-09-25T11:30:00Z",
                "kudos_count":2,
                "you_gave_kudos":true
            }""".trimIndent()
        )

        assertEquals("89c5a006-9e20-42fd-8c22-0d6c1ca40230", row.workoutId)
        assertEquals(2, row.kudosCount)
        assertTrue(row.youGaveKudos)
        assertFalse(row.isYou)
    }
}
