package com.pelonot.domain.suggest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * *What should I ride?* (PLAN 22.8.6).
 *
 * The rule is four decisions — the length, the kind, the class and the reason —
 * and every one of them is a claim shown to the rider on the first screen of the
 * app. The cases that matter are the ones where being wrong would put a
 * plausible lie on that card: a recovery class offered as a first ever ride, a
 * hard class offered an hour after a hard class, or *"new to you"* on something
 * they rode last week.
 */
class ClassToRideTest {

    private val hour = 60L * 60 * 1000
    private val day = 24 * hour
    private val now = 1_760_000_000_000L

    private fun klass(
        id: String,
        category: String,
        minutes: Int,
        hardest: Int?
    ) = SuggestableClass(
        id = id,
        title = "$id title",
        category = category,
        durationSec = minutes * 60,
        hardestZone = hardest
    )

    /** A small library with the same shape as the real one: 15–60 min, easy to hard. */
    private val library = listOf(
        klass("REC-01", "Recovery", 20, 2),
        klass("REC-02", "Recovery", 30, 2),
        klass("END-01", "Endurance", 20, 3),
        klass("END-02", "Endurance", 30, 3),
        klass("END-03", "Endurance", 45, 3),
        klass("CLB-01", "Climbs", 30, 5),
        klass("CLB-02", "Climbs", 45, 5),
        klass("SWT-01", "Sweet Spot", 30, 4),
        klass("THR-01", "Threshold", 30, 4),
        klass("VMX-01", "VO2 Max", 20, 6)
    )

    private fun rode(id: String?, minutesAgo: Long, minutes: Int = 30) =
        RecentRide(classId = id, atEpochMs = now - minutesAgo * 60 * 1000, durationSec = minutes * 60)

    // ── The rider who has ridden nothing ────────────────────────────────

    @Test
    fun `a rider with no rides is started on the shortest endurance class`() {
        val suggestion = ClassToRide.suggest(library, RiderRides(), now)

        assertEquals("END-01", suggestion?.classId)
        assertEquals(ClassSuggestion.Reason.FirstRide, suggestion?.reason)
    }

    @Test
    fun `a first ride is never a recovery class`() {
        // REC-01 is both the easiest and joint-shortest thing in the library, so
        // any rule that reached for "easiest" would land on it. A recovery class
        // is a class that only makes sense after something.
        val suggestion = ClassToRide.suggest(library, RiderRides(), now)

        assertEquals("Endurance", suggestion?.category)
    }

    @Test
    fun `no library means no suggestion, rather than an empty card`() {
        assertNull(ClassToRide.suggest(emptyList(), RiderRides(), now))
    }

    // ── The length ──────────────────────────────────────────────────────

    @Test
    fun `the suggestion is the length the rider usually rides`() {
        val rides = RiderRides(
            recent = listOf(rode("END-03", 60 * 24 * 7, 45), rode("END-03", 60 * 24 * 14, 45))
        )

        assertEquals(45, ClassToRide.suggest(library, rides, now)?.minutes)
    }

    @Test
    fun `one long ride does not move a twenty-minute rider`() {
        val rides = RiderRides(
            recent = listOf(
                rode("END-03", 60, 60),
                rode("END-01", 60 * 24 * 3, 20),
                rode("END-01", 60 * 24 * 6, 20)
            )
        )

        // The median, not the mean: 20, and not the 33 minutes an average would
        // have produced from the same three rides.
        assertEquals(20, ClassToRide.suggest(library, rides, now)?.minutes)
    }

    @Test
    fun `an even number of rides takes the shorter middle`() {
        val rides = RiderRides(recent = listOf(rode("END-01", 60, 20), rode("END-02", 120, 30)))

        assertEquals(20, ClassToRide.suggest(library, rides, now)?.minutes)
    }

    // ── Not two hard days running ───────────────────────────────────────

    @Test
    fun `a hard class ridden an hour ago produces an easy one`() {
        val rides = RiderRides(recent = listOf(rode("THR-01", 60)))

        val suggestion = ClassToRide.suggest(library, rides, now)

        assertEquals(ClassSuggestion.Reason.EasyAfterHard, suggestion?.reason)
        assertTrue(
            "expected an easy class, got ${suggestion?.classId}",
            suggestion?.classId in listOf("REC-01", "REC-02", "END-01", "END-02", "END-03")
        )
    }

    @Test
    fun `the same hard class two days ago does not`() {
        val rides = RiderRides(recent = listOf(rode("THR-01", 2 * 24 * 60)))

        assertEquals(ClassSuggestion.Reason.NewToYou, ClassToRide.suggest(library, rides, now)?.reason)
    }

    @Test
    fun `an easy ride an hour ago makes no claim about the next one`() {
        val rides = RiderRides(recent = listOf(rode("END-02", 60)))

        assertEquals(ClassSuggestion.Reason.NewToYou, ClassToRide.suggest(library, rides, now)?.reason)
    }

    @Test
    fun `a just ride an hour ago is unknown rather than easy`() {
        // A free ride's intensity lives in workout_metrics, which this rule may
        // not read and which needs measured power to mean anything. Unknown
        // makes no claim — so the ordinary suggestion, not a recovery one.
        val rides = RiderRides(recent = listOf(rode(null, 60)))

        assertEquals(ClassSuggestion.Reason.NewToYou, ClassToRide.suggest(library, rides, now)?.reason)
    }

    @Test
    fun `a ride of a class no longer in the library makes no claim either`() {
        val rides = RiderRides(recent = listOf(rode("GONE-99", 60)))

        assertEquals(ClassSuggestion.Reason.NewToYou, ClassToRide.suggest(library, rides, now)?.reason)
    }

    // ── Which class ─────────────────────────────────────────────────────

    @Test
    fun `the least ridden class of the right length wins`() {
        val thirtyMinuteRides = listOf(
            ClassRideCount("REC-02", rides = 3, lastRiddenAtMs = now - 3 * day),
            ClassRideCount("END-02", rides = 2, lastRiddenAtMs = now - 4 * day),
            ClassRideCount("CLB-01", rides = 2, lastRiddenAtMs = now - 5 * day),
            ClassRideCount("SWT-01", rides = 1, lastRiddenAtMs = now - 6 * day),
            ClassRideCount("THR-01", rides = 1, lastRiddenAtMs = now - 30 * day)
        )
        val rides = RiderRides(
            perClass = thirtyMinuteRides,
            // Two days ago, so the "easy after a hard one" rule is not in play.
            recent = listOf(rode("REC-02", 2 * 24 * 60))
        )

        val suggestion = ClassToRide.suggest(library, rides, now)

        // THR-01 and SWT-01 are level on rides; THR-01 was ridden a month ago
        // and SWT-01 last week, and Threshold has been ridden less than Sweet
        // Spot besides.
        assertEquals("THR-01", suggestion?.classId)
        assertEquals(ClassSuggestion.Reason.NotSince(now - 30 * day), suggestion?.reason)
    }

    @Test
    fun `a class already ridden is never called new`() {
        val rides = RiderRides(
            perClass = library.filter { it.durationSec == 30 * 60 }.map {
                ClassRideCount(it.id, rides = 1, lastRiddenAtMs = now - 10 * day)
            },
            recent = listOf(rode("END-02", 5 * 24 * 60))
        )

        val reason = ClassToRide.suggest(library, rides, now)?.reason

        assertTrue("$reason", reason is ClassSuggestion.Reason.NotSince)
    }

    @Test
    fun `breadth beats familiarity — the category ridden least breaks the tie`() {
        val rides = RiderRides(
            perClass = listOf(
                // Neither of these is a candidate — they are 45 and 20 minutes
                // against a rider who rides 30 — so every candidate below is
                // unridden and level on the first key.
                ClassRideCount("CLB-02", rides = 4, lastRiddenAtMs = now - 40 * day),
                ClassRideCount("END-01", rides = 2, lastRiddenAtMs = now - 20 * day)
            ),
            recent = listOf(rode("CLB-02", 5 * 24 * 60), rode("END-01", 9 * 24 * 60))
        )

        // CLB-01 and END-02 both sort ahead of REC-02 by id, and both lose:
        // this rider has done four Climbs and two Endurance classes and no
        // Recovery at all. That is the whole point of the card on a 72-class
        // library — it walks the rider around it.
        assertEquals("REC-02", ClassToRide.suggest(library, rides, now)?.classId)
        assertEquals(ClassSuggestion.Reason.NewToYou, ClassToRide.suggest(library, rides, now)?.reason)
    }

    @Test
    fun `the same rider twice gets the same answer`() {
        val rides = RiderRides(
            perClass = listOf(ClassRideCount("END-02", 2, now - day)),
            recent = listOf(rode("END-02", 60 * 24))
        )

        assertEquals(
            ClassToRide.suggest(library, rides, now),
            ClassToRide.suggest(library.reversed(), rides, now)
        )
    }

    @Test
    fun `a malformed class is treated as asking for nothing rather than crashing`() {
        val broken = listOf(klass("BAD-01", "Endurance", 30, null))
        val rides = RiderRides(recent = listOf(rode("THR-01", 60)))

        // The only class there is, and the easy filter keeps it: a class whose
        // blocks could not be decoded asks for nothing, which is not hard.
        assertEquals("BAD-01", ClassToRide.suggest(broken, rides, now)?.classId)
    }
}
