package com.pelonot.domain.progress

/** A measured assessment verifies an FTP; a value typed, estimated or restored does not. */
fun isVerifiedFtpSource(source: String?): Boolean = source in VERIFIED_FTP_SOURCES

private val VERIFIED_FTP_SOURCES = setOf("AutoBreakthrough", "AutoReduction", "GuidedTest")
