package com.pelonot.domain.social

import com.pelonot.domain.social.RaceCompetitor.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Who ends up on the live leaderboard, and how often (PLAN 24.3.12).
 */
class RaceCompetitorTest {

    private fun row(workoutId: String, kind: Kind, name: String = kind.label) =
        RaceCompetitor(workoutId = workoutId, name = name, kind = kind, outputKj = 200.0)

    @Test
    fun `one ride that qualifies three times appears once, at its widest label`() {
        // The ordinary case rather than an exotic one: a rider whose best ever
        // ride of a class was three weeks ago has one ride answering all three
        // of their own queries. Drawn naively that is a board with the same
        // ride on it three times, level with itself — 22.5's finding exactly,
        // and it has to read as absent rather than as a rival you are dead
        // level with.
        val field = RaceCompetitor.oneRowPerRide(
            listOf(
                row("w1", Kind.YourBestEver),
                row("w1", Kind.YourBestYear),
                row("w1", Kind.YourBestMonth)
            )
        )

        assertEquals(1, field.size)
        assertEquals(Kind.YourBestEver, field.single().kind)
    }

    @Test
    fun `a rider who is improving keeps all three rows`() {
        // The case the feature exists for: an unreachable all-time best, a
        // reachable one in the last twelve months, and a very reachable one in
        // the last thirty days. "Just something to always be reaching for."
        val field = RaceCompetitor.oneRowPerRide(
            listOf(
                row("best-ever", Kind.YourBestEver),
                row("best-year", Kind.YourBestYear),
                row("best-month", Kind.YourBestMonth)
            )
        )

        assertEquals(3, field.size)
        // 24.3.12a, settled in the thirty-second sitting: every non-human row
        // is a sentence about the rider rather than a span of time. Asserted
        // through `Kind.label` rather than as literals, because the words are
        // the owner's to change and the property under test is the *dedupe*.
        assertEquals(
            listOf(Kind.YourBestEver, Kind.YourBestYear, Kind.YourBestMonth).map { it.label },
            field.map { it.name }
        )
        assertTrue(
            "a duration is not a person",
            field.none { it.name.contains("months") || it.name.contains("days") }
        )
    }

    @Test
    fun `a ride that is both this year's and this month's keeps the wider label`() {
        val field = RaceCompetitor.oneRowPerRide(
            listOf(
                row("old", Kind.YourBestEver),
                row("recent", Kind.YourBestYear),
                row("recent", Kind.YourBestMonth)
            )
        )

        assertEquals(listOf(Kind.YourBestEver, Kind.YourBestYear), field.map { it.kind })
    }

    @Test
    fun `housemates are never folded into the rider's own rows`() {
        // Different rides by definition — the household query excludes the
        // rider — so nothing here may collapse two people into one row.
        val field = RaceCompetitor.oneRowPerRide(
            listOf(
                row("mine", Kind.YourBestEver),
                row("theirs", Kind.Housemate, name = "Kilo")
            )
        )

        assertEquals(2, field.size)
    }
}
