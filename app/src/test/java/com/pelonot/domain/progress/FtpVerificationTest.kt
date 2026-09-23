package com.pelonot.domain.progress

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FtpVerificationTest {
    @Test
    fun onlyMeasuredAssessmentsVerifyAnFtp() {
        assertTrue(isVerifiedFtpSource("AutoBreakthrough"))
        assertTrue(isVerifiedFtpSource("AutoReduction"))
        assertTrue(isVerifiedFtpSource("GuidedTest"))
        assertFalse(isVerifiedFtpSource("ManualEdit"))
        assertFalse(isVerifiedFtpSource("Estimated"))
        assertFalse(isVerifiedFtpSource(null))
    }
}
