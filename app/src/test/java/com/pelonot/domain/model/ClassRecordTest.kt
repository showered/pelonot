package com.pelonot.domain.model

import org.junit.Assert.*
import org.junit.Test

class ClassRecordTest {
    @Test fun emptyBoardIsAbsent() { assertNull(ClassRecord.from(emptyMap(), 1)) }
    @Test fun loneRiderHasARecordWithoutRank() {
        assertEquals(ClassRecord(200.0, true, null, 1), ClassRecord.from(mapOf(1 to 200.0), 1))
    }
    @Test fun ownBestRanksAgainstOtherRidersAndSharesTies() {
        val bests = mapOf(1 to 200.0, 2 to 300.0, 3 to 200.0)
        assertEquals(ClassRecord(200.0, true, 2, 3), ClassRecord.from(bests, 1))
        assertEquals(2, ClassRecord.from(bests, 3)!!.rank)
    }
    @Test fun guestOrRiderWithoutResultSeesBikeBest() {
        val bests = mapOf(1 to 200.0, 2 to 300.0)
        assertEquals(ClassRecord(300.0, false, null, 2), ClassRecord.from(bests, null))
        assertEquals(ClassRecord(300.0, false, null, 2), ClassRecord.from(bests, 3))
    }
}
