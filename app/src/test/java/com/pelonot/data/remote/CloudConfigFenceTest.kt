package com.pelonot.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Properties

/**
 * A fence around what may reach an APK (PLAN 14.10.4, 14.11.3).
 *
 * Two mistakes, both one line long, both invisible in review.
 *
 * **A key in `cloud.properties`.** The file is checked in, which is the point
 * of it — but every row-level-security policy on the project is `USING (true)`
 * today (15.5), so a publishable key committed here publishes everyone's data
 * with it. The second reason is quieter and does not go away when 15.5 lands: a
 * published endpoint is a bill, and a community project's free tier holds about
 * 13,000 rides before it fails for everyone at once. Filling this in is a
 * decision, and this test is what makes it one.
 *
 * **A third `secret()` call.** `local.properties` holds an `sbp_` personal
 * access token (14.11.1) that can create, modify and delete *every project on
 * the account* — worse than a service-role key, and one `buildConfigField` away
 * from being in an APK. The two credentials that may ever become `BuildConfig`
 * fields are the URL and the anon key.
 *
 * Same family as [CloudAccessFenceTest]: the danger is not the code that
 * exists, it is the line nobody has written yet.
 */
class CloudConfigFenceTest {

    private val repoRoot = File("..")

    private val buildScript = File("build.gradle.kts").readText()

    @Test
    fun `the checked-in cloud config carries no credentials`() {
        val defaults = Properties().apply {
            File(repoRoot, "cloud.properties").inputStream().use { load(it) }
        }

        defaults.stringPropertyNames().forEach { key ->
            assertTrue(
                "cloud.properties must ship empty until RLS is fixed (15.5) and " +
                    "somebody has decided who pays for the endpoint — $key is set",
                defaults.getProperty(key).isNullOrBlank()
            )
        }
    }

    @Test
    fun `only the url and the anon key are ever built into the app`() {
        val secrets = Regex("""secret\(\s*"([^"]+)"""").findAll(buildScript)
            .map { it.groupValues[1] }
            .toList()

        assertEquals(
            "a new secret() call reaches BuildConfig and therefore the APK; " +
                "supabase.accessToken in particular is account-wide (14.11.2)",
            listOf("supabase.url", "supabase.anonKey"),
            secrets
        )
    }

    /**
     * **The fence is the count, so a non-credential gets its own door.**
     *
     * `pelonot.webUrl` (15.6, 17.14) genuinely is not a secret — the bike draws
     * it on screen inside a QR code, which is the opposite of keeping it — and
     * it still has to be configurable, because a self-hoster's web app is not
     * ours. Widening `secret()` to admit it would have cost the property that
     * makes the test above worth having: that **any** third value in that list
     * is a mistake, since the file it is read from also holds an `sbp_` token
     * that can delete every project on the account.
     *
     * So this is the other half of the same fence rather than a hole in it.
     * Adding a fourth public value is still a decision somebody has to come
     * here and make.
     */
    @Test
    fun `the only non-credential built into the app is where the web app lives`() {
        val public = Regex("""publicConfig\(\s*"([^"]+)"""").findAll(buildScript)
            .map { it.groupValues[1] }
            .toList()

        assertEquals(
            "publicConfig() also reaches BuildConfig; a value that belongs in it " +
                "must be one the app is willing to print on the bike's screen",
            listOf("pelonot.webUrl"),
            public
        )
    }

    @Test
    fun `the access token is named nowhere in the build`() {
        assertTrue(
            "supabase.accessToken must never be read by the build (14.11.3)",
            !buildScript.contains("accessToken")
        )
    }

    @Test
    fun `an absent endpoint is still a build`() {
        // 14.10.3. The offline tier is the supported configuration, so the
        // build must fall through to an empty string rather than failing or
        // demanding a file — which is also what CI proves on every PR, with no
        // secret and no local.properties (19.1.4).
        assertTrue(
            "the empty fallback is what keeps a cloudless clone buildable",
            buildScript.contains("""firstOrNull { !it.isNullOrBlank() } ?: """"")
        )
    }
}
