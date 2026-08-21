package com.pelonot.domain.update

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The decision that replaces the running app, checked without one (PLAN 30.2.3,
 * 30.3.3, 30.4.5).
 */
class UpdatePolicyTest {

    private val goodSha = "a".repeat(64)

    private fun manifest(
        code: Int,
        url: String = "https://example.invalid/pelonot.apk",
        sha: String = goodSha
    ) = UpdateManifest(
        versionCode = code,
        versionName = "9.9.9",
        notes = "Something a rider can read.",
        url = url,
        sha256 = sha
    )

    @Test
    fun `a newer version is offered`() {
        val decision = UpdatePolicy.decide(installedVersionCode = 4, manifest = manifest(5))
        assertTrue(decision is UpdateDecision.Offer)
    }

    @Test
    fun `the same version is up to date`() {
        assertEquals(
            UpdateDecision.UpToDate,
            UpdatePolicy.decide(installedVersionCode = 5, manifest = manifest(5))
        )
    }

    /**
     * The one that would cost a rider their whole history: 12.5.1 leaves the
     * destructive fallback in place on downgrade, so an offer of an older APK
     * is a wipe behind a *yes* button.
     */
    @Test
    fun `an older version is refused as a downgrade, not merely ignored`() {
        val decision = UpdatePolicy.decide(installedVersionCode = 5, manifest = manifest(4))
        assertEquals(
            UpdateDecision.Rejected(UpdateDecision.Rejected.Reason.Downgrade),
            decision
        )
    }

    @Test
    fun `a plain-http download is refused`() {
        val decision = UpdatePolicy.decide(
            installedVersionCode = 1,
            manifest = manifest(2, url = "http://example.invalid/pelonot.apk")
        )
        assertEquals(
            UpdateDecision.Rejected(UpdateDecision.Rejected.Reason.InsecureUrl),
            decision
        )
    }

    @Test
    fun `a checksum that is not 64 lowercase hex characters is refused`() {
        listOf("", "abc", goodSha.uppercase(), goodSha + "a", "z".repeat(64)).forEach { bad ->
            assertEquals(
                "checksum '$bad' should have been refused",
                UpdateDecision.Rejected(UpdateDecision.Rejected.Reason.MalformedChecksum),
                UpdatePolicy.decide(installedVersionCode = 1, manifest = manifest(2, sha = bad))
            )
        }
    }

    /**
     * The manifest is checked before the version is, so a malformed file that
     * happens to name an older build is reported as malformed rather than as a
     * downgrade — which is what a Settings screen has to be able to say.
     */
    @Test
    fun `a bad url is reported even when the version is also wrong`() {
        val decision = UpdatePolicy.decide(
            installedVersionCode = 9,
            manifest = manifest(2, url = "ftp://example.invalid/pelonot.apk")
        )
        assertEquals(
            UpdateDecision.Rejected(UpdateDecision.Rejected.Reason.InsecureUrl),
            decision
        )
    }

    @Test
    fun `a version the rider refused is not offered again`() {
        val decision = UpdatePolicy.decide(
            installedVersionCode = 4,
            manifest = manifest(5),
            declinedVersionCode = 5
        )
        assertTrue(decision is UpdateDecision.Declined)
    }

    /** 30.4.5 stops the nagging, not the feature: a *newer* one still asks. */
    @Test
    fun `refusing one version does not refuse the next`() {
        val decision = UpdatePolicy.decide(
            installedVersionCode = 4,
            manifest = manifest(6),
            declinedVersionCode = 5
        )
        assertTrue(decision is UpdateDecision.Offer)
    }

    @Test
    fun `the first check is always due and the switch is obeyed`() {
        assertTrue(UpdatePolicy.isCheckDue(nowMs = 0, lastCheckMs = null, enabled = true))
        assertTrue(!UpdatePolicy.isCheckDue(nowMs = 0, lastCheckMs = null, enabled = false))
    }

    @Test
    fun `a check is due once a day and not before`() {
        val day = UpdatePolicy.CHECK_INTERVAL_MS
        assertTrue(!UpdatePolicy.isCheckDue(nowMs = day - 1, lastCheckMs = 0, enabled = true))
        assertTrue(UpdatePolicy.isCheckDue(nowMs = day, lastCheckMs = 0, enabled = true))
    }

    /**
     * A tablet correcting its clock off the network — which this one does at
     * every boot — must not lock itself out of updates until the stored
     * timestamp comes round again.
     */
    @Test
    fun `a clock that has gone backwards does not block the check`() {
        assertTrue(UpdatePolicy.isCheckDue(nowMs = 1_000, lastCheckMs = 9_999_999, enabled = true))
    }

    /**
     * The wire format is snake_case and this is what stops it drifting — the
     * same trap `intervals_json` set, where a rename on the Kotlin side throws
     * at runtime and is easy to swallow into "no update today".
     */
    @Test
    fun `the manifest parses the shape the tool writes`() {
        val json = """
            {
              "version_code": 7,
              "version_name": "1.2.0",
              "notes": "The distance a ride records is much closer to right.",
              "url": "https://example.invalid/pelonot-7.apk",
              "sha256": "$goodSha"
            }
        """.trimIndent()
        val parsed = Json.decodeFromString<UpdateManifest>(json)
        assertEquals(7, parsed.versionCode)
        assertEquals("1.2.0", parsed.versionName)
        assertEquals("https://example.invalid/pelonot-7.apk", parsed.url)
    }
}
