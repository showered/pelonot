package com.pelonot.domain.progress

import org.junit.Assert.*
import org.junit.Test

class FtpAssessmentTest {
    private val now = 200L * 86400000
    private val start = now - 10L * 86400000
    private fun point(watts: Int = 200, source: String = "ProfileCreated", id: String? = null) =
        FtpPoint(watts, start, source, id)
    private fun ride(id: String = "ride", ftp: Double = 200.0, at: Long = now - 1000, rpe: Int? = null) =
        FtpEvidenceRide(id, at, ftp / 0.95, rpeRating = rpe)
    private fun assess(rides: List<FtpEvidenceRide>, p: FtpPoint = point(), answeredAt: Long = 0) =
        FtpAssessment.evaluate(listOf(p), rides, now, answeredAt)!!

    @Test fun `review and post ride use the same upward threshold`() {
        assertEquals(com.pelonot.data.service.PostWorkoutAnalyzer.MIN_MEANINGFUL_GAIN,
            FtpAssessment.MIN_MEANINGFUL_GAIN, 0.0)
    }
    @Test fun `a typed number is not an achievement`() {
        assertEquals("Starting value", assess(emptyList()).label)
        assertEquals("Starting value", assess(listOf(ride()), point(1000000)).label)
    }
    @Test fun `an unchanged 200 can be earned`() {
        val result = assess(listOf(ride()))
        assertEquals("Ride-supported", result.label)
        assertEquals("ride", result.evidenceRideId)
        assertNull(result.suggestedWatts)
    }
    @Test fun `breakthrough offers the measured number not a fixed increment`() {
        val result = assess(listOf(ride(ftp = 209.2)))
        assertEquals(209, result.suggestedWatts)
        assertFalse(result.isReduction)
    }
    @Test fun `small differences do not prompt a change`() {
        assertNull(assess(listOf(ride(ftp = 203.0))).suggestedWatts)
    }
    @Test fun `one bad day keeps earned status and explains the evidence`() {
        val result = assess(listOf(ride(ftp = 180.0, rpe = 10)), point(source = "AutoBreakthrough"))
        assertEquals("Ride-supported", result.label)
        assertTrue(result.summary.startsWith("1 of 3"))
        assertNull(result.suggestedWatts)
    }
    @Test fun `three hard rides offer the strongest recent estimate`() {
        val result = assess(listOf(ride("a", 180.0, rpe = 10), ride("b", 185.0, rpe = 10), ride("c", 182.0, rpe = 10)))
        assertEquals(185, result.suggestedWatts)
        assertTrue(result.isReduction)
        assertEquals("b", result.evidenceRideId)
    }
    @Test fun `easy rides are not evidence of decline`() {
        assertNull(assess((1..4).map { ride("$it", 100.0, rpe = 2) }).suggestedWatts)
    }
    @Test fun `a stronger hard ride interrupts the shortfall sequence`() {
        val result = assess(listOf(ride("a", 180.0, now - 1, 10), ride("b", 198.0, now - 2, 10), ride("c", 180.0, now - 3, 10)))
        assertNull(result.suggestedWatts)
        assertTrue(result.summary.startsWith("1 of 3"))
    }
    @Test fun `old and future rides cannot support todays typed number`() {
        assertEquals("Starting value", assess(listOf(ride(at = now - FtpAssessment.RECENT_MS - 1), ride(at = now + 1))).label)
    }
    @Test fun `a manual edit needs new evidence`() {
        assertEquals("Starting value", assess(listOf(ride(at = start - 1)), point(source = "ManualEdit")).label)
    }
    @Test fun `acceptance retains provenance even if ride has been deleted`() {
        assertEquals("Ride-supported", assess(emptyList(), point(source = "AutoReduction")).label)
    }
    @Test fun `a declined proposal is not immediately offered again`() {
        assertNull(assess(listOf(ride(ftp = 220.0)), answeredAt = now).suggestedWatts)
    }
    @Test fun `earned evidence does not disappear with age or twenty easier rides`() {
        val old = ride(at = now - FtpAssessment.RECENT_MS - 1)
        val result = FtpAssessment.evaluate(
            listOf(point().copy(atEpochMs = old.recordedAt - 1000)), emptyList(), now,
            historicalSupport = old)!!
        assertEquals("Ride-supported", result.label)
        assertEquals("Backed by a previous 20-minute effort", result.summary)
        assertNull(result.suggestedWatts)
    }

    @Test fun `invalid power never supports a setting`() {
        assertEquals("Starting value", assess(listOf(ride(ftp = Double.NaN))).label)
    }
}
