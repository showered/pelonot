package com.pelonot.domain.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * A fence around the one request an account-less bike makes (PLAN 30.5.1).
 *
 * The owner allowed this on a specific promise — that it asks *what is the
 * newest version* and sends **nothing about the rider**: no id, no name, no
 * profile count, no ride. A promise like that is exactly the kind that decays,
 * because the shape of the decay is somebody adding one harmless parameter to
 * help with a support question. `RiderScore`'s rule 2 was stated four times in
 * prose and enforced by nobody (26.4.10), and that is the lesson this copies.
 *
 * So the structure is checked rather than the behaviour:
 *
 * 1. exactly one file makes the request;
 * 2. it shares nothing with the cloud path — no Supabase, no ktor, no
 *    `CloudAccess`;
 * 3. it names no rider and the URL has nowhere to put one.
 *
 * Nothing here opens a socket, for `CloudAccessFenceTest`'s reason: a
 * behavioural test would have to be trusted not to reach the real host at
 * exactly the moment it was failing.
 */
class UpdateChannelFenceTest {

    private val sourceRoot = File("src/main/java/com/pelonot")

    private val repositoryFile = File(sourceRoot, "data/repository/UpdateRepository.kt")

    /**
     * The file with its comments stripped.
     *
     * The rules below forbid certain names, and the KDoc has to say those names
     * out loud to explain why. Scanning raw text would make documenting a rule
     * break it, which teaches the next person to delete the explanation instead
     * of keeping the rule.
     */
    private val repositoryCode = repositoryFile.readText()
        .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("//[^\n]*"), "")

    /** One file asks, so there is one place to read to know what is sent. */
    @Test
    fun `only UpdateRepository knows where the manifest lives`() {
        val offenders = sourceRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { it.readText().contains("update.json") }
            .map { it.path }
            .toList()

        assertEquals(
            "more than one file knows where the update manifest lives: $offenders",
            listOf(repositoryFile.path),
            offenders
        )
    }

    /**
     * The update path must not become a second door into the cloud tier. It is
     * allowed to run for a rider with no account precisely because it is not
     * that — and `CloudAccess` appearing here would mean somebody had gated it,
     * which is the mistake in the other direction and withholds updates from
     * the rider the feature exists for.
     */
    @Test
    fun `the update check shares nothing with the cloud path`() {
        listOf("io.github.jan.supabase", "io.ktor", "CloudAccess", "SupabaseSyncRepository")
            .forEach { forbidden ->
                assertTrue(
                    "UpdateRepository mentions '" + forbidden + "'; the update check is about " +
                        "the app, not the rider, and must share nothing with the cloud tier",
                    !repositoryCode.contains(forbidden)
                )
            }
    }

    /** The promise itself, in the two places it could be broken. */
    @Test
    fun `nothing about the rider goes on the wire`() {
        listOf("profileId", "userId", "auth_user_id", "riderName", "UserRepository", "profiles")
            .forEach { forbidden ->
                assertTrue(
                    "UpdateRepository contains '" + forbidden + "' — 30.5.1 was granted on the " +
                        "promise that this request says nothing about who is riding",
                    !repositoryCode.contains(forbidden)
                )
            }

        // The URL is a base plus a constant path and nothing else. A query
        // string is how a harmless parameter arrives, and a rider's identity is
        // what it would carry — so there is nowhere to put one.
        assertTrue(
            "the manifest URL is built from something other than a constant path",
            repositoryCode.contains("baseUrl.trimEnd('/') + MANIFEST_PATH")
        )
        val path = Regex("MANIFEST_PATH = \"([^\"]*)\"")
            .find(repositoryCode)?.groupValues?.get(1)
        assertEquals("/update.json", path)
        listOf("?", "&", "=").forEach { forbidden ->
            assertTrue(
                "the manifest path carries a query string: " + path,
                !path!!.contains(forbidden)
            )
        }
    }

    /** A GET, so that nothing can be sent even by accident. */
    @Test
    fun `the request is a GET and cannot carry a body`() {
        assertTrue(
            "the update check must be a plain GET",
            repositoryCode.contains("requestMethod = \"GET\"")
        )
        listOf("POST", "PUT", "PATCH", "doOutput", "outputStream").forEach { forbidden ->
            assertTrue(
                "UpdateRepository mentions '" + forbidden + "'",
                !repositoryCode.contains(forbidden)
            )
        }
    }
}
