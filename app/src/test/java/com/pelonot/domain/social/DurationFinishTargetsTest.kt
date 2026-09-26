package com.pelonot.domain.social

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DurationFinishTargetsTest {
    @Test
    fun `401 at minute 29 still chases a 420 final best`() {
        val tom = DurationFinishTarget(null, "tom", "Tom", 420.0, false)
        val chase = DurationFinishTargets.chases(listOf(tom), 401.0, 1740, 1800).single()

        assertEquals("Tom", chase.target.name)
        assertEquals(19.0, chase.remainingKj, 0.001)
        assertEquals(60, chase.remainingSec)
        assertFalse(chase.passed)
    }

    @Test
    fun `own best and nearest other final best both stay visible`() {
        val own = DurationFinishTarget(1, "you", "You", 410.0, true)
        val tom = DurationFinishTarget(null, "tom", "Tom", 420.0, false)
        val alex = DurationFinishTarget(null, "alex", "Alex", 500.0, false)

        val chases = DurationFinishTargets.chases(listOf(alex, own, tom), 401.0, 1740, 1800)
        assertEquals(listOf("You", "Tom"), chases.map { it.target.name })
        assertEquals(9.0, chases[0].remainingKj, 0.001)
        assertEquals(19.0, chases[1].remainingKj, 0.001)
        assertTrue(DurationFinishTargets.chases(listOf(tom), 421.0, 1740, 1800).single().passed)
    }

    @Test
    fun `same account on two bikes has one target with the higher total`() {
        val local = DurationFinishTarget(2, "tom", "Tom", 400.0, false)
        val remote = DurationFinishTarget(null, "tom", "Tom", 420.0, false)
        val merged = DurationFinishTargets.merge(listOf(local), listOf(remote))
        assertEquals(1, merged.size)
        assertEquals(2, merged.single().localUserId)
        assertEquals(420.0, merged.single().bestKj, 0.001)
    }
}
