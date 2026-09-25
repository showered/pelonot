package com.pelonot.domain.social

import com.pelonot.domain.identity.Avatar
import org.junit.Assert.assertEquals
import org.junit.Test

class HouseholdActivityMergeTest {
    @Test
    fun `a housemate's local activity wins over the same cloud account`() {
        val local = activity("Alex", 2_000, localId = 2)
        val duplicate = activity("Alex", 3_000, accountId = "alex")
        val remote = activity("Tom", 1_000, accountId = "tom")

        assertEquals(
            listOf(local, remote),
            mergeHouseholdActivity(listOf(local), listOf(duplicate, remote), setOf("alex"))
        )
    }

    private fun activity(
        name: String,
        at: Long,
        localId: Int? = null,
        accountId: String? = null
    ) = HouseholdActivity(
        localUserId = localId,
        name = name,
        avatar = Avatar.defaultFor(localId ?: 1),
        classTitle = "Zone 2 Steady",
        completedAt = at,
        event = null,
        cloudAccountId = accountId
    )
}
