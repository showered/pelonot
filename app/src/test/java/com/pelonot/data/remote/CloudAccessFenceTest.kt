package com.pelonot.data.remote

import kotlin.coroutines.Continuation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * A fence around the network, not a test of it (PLAN 23.1.3).
 *
 * Rule 1 of the connectivity model is the one most likely to be broken by
 * accident — by a future feature that "only needs one little lookup", added by
 * somebody who has not read the plan, in a file nowhere near this one. The
 * shape of that mistake is known because the app has already made it twice: the
 * class seeder reached for Supabase on first launch, and `WorkoutService`
 * enqueued an upload after every profile ride.
 *
 * So this checks the *structure* that makes the rule hold rather than the
 * behaviour at one call site:
 *
 * 1. the Supabase SDK is reachable from one package only;
 * 2. the client is dereferenced at exactly one place, behind the gate;
 * 3. every way out through that place names the rider it is acting for.
 *
 * Nothing here opens a socket. A behavioural test would have to be trusted not
 * to reach the real project when it fails, which is precisely when it would.
 * [CloudAccessTest] carries the semantics; this carries the blast radius.
 *
 * The same idea as the `PowerModel` consumer fence (2.2a.8): the danger is not
 * the code that exists, it is the third caller nobody has written yet.
 */
class CloudAccessFenceTest {

    private val sourceRoot = File("src/main/java/com/pelonot")

    private val repositorySource =
        File(sourceRoot, "data/remote/SupabaseSyncRepository.kt").readText()

    /**
     * The same file with its comments removed.
     *
     * The fences below assert that certain names do **not** appear, and the
     * names they forbid are exactly the ones the KDoc has to say out loud in
     * order to explain why they are forbidden. Scanning the raw text makes
     * documenting a rule break it, which teaches the next person to delete the
     * explanation rather than keep the rule.
     */
    private val repositoryCode = repositorySource
        .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("""//[^\n]*"""), "")

    /**
     * Anything that imports the SDK can talk to the cloud without asking
     * anybody. Keeping that ability inside `data/remote` is what makes points 2
     * and 3 below worth checking at all.
     */
    @Test
    fun `only data-remote may touch the supabase sdk`() {
        val offenders = sourceRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { it.readText().contains("io.github.jan.supabase") }
            .filterNot { it.path.replace(File.separatorChar, '/').contains("/data/remote/") }
            .map { it.path }
            .toList()

        assertTrue(
            "these files reach the Supabase SDK directly, bypassing the consent gate: $offenders",
            offenders.isEmpty()
        )
    }

    /**
     * One door, and the gate is in front of it. If the client can be resolved
     * anywhere else in the file, a new method can go out without asking.
     */
    @Test
    fun `the client is resolved once, after the gate is consulted`() {
        val gateAt = repositorySource.indexOf("cloudAccess.accountIdFor")
        val clientAt = repositorySource.indexOf("val supabase = client")

        assertTrue("the gate is not consulted at all", gateAt >= 0)
        assertTrue("the client is never resolved; has the door moved?", clientAt >= 0)
        assertTrue(
            "the client is resolved before the gate is asked",
            gateAt < clientAt
        )
        assertEquals(
            "the client is resolved in more than one place",
            1,
            Regex("""=\s*client\b""").findAll(repositorySource).count()
        )
    }

    /**
     * The old gate asked whether *this build* had credentials, which is a fact
     * about a developer's `local.properties` and not a rider's consent. It must
     * not come back as the answer to "may we?" — `CloudAccess` still consults
     * it, as one of three reasons to be offline, and that is the only place it
     * belongs.
     */
    @Test
    fun `the build-time flag is not used as consent`() {
        assertTrue(
            "SupabaseSyncRepository is gating on SupabaseModule.isConfigured again",
            !repositorySource.contains("isConfigured")
        )
    }

    /**
     * Every public entry point has to name a rider. A call with no profile
     * attached is the exact thing rule 1 forbids, and `fetchClassTemplates()`
     * used to be one — which is how the class library ended up being fetched
     * before there was a rider on the tablet at all.
     *
     * Reflection rather than reading the source, so a method added through any
     * syntax is caught. Nothing is invoked.
     */
    @Test
    fun `every entry point names the rider it acts for`() {
        val riderTypes = setOf(
            Int::class.java,
            Integer::class.java,
            com.pelonot.data.local.entity.UserEntity::class.java,
            com.pelonot.data.local.entity.WorkoutEntity::class.java
        )

        val unfenced = SupabaseSyncRepository::class.java.declaredMethods
            .filter { java.lang.reflect.Modifier.isPublic(it.modifiers) }
            .filterNot { it.isSynthetic }
            .filter { it.parameterTypes.lastOrNull() == Continuation::class.java }
            .filterNot { method -> method.parameterTypes.any { it in riderTypes } }
            .map { it.name }

        assertTrue(
            "these cloud calls do not say which rider they are for: $unfenced",
            unfenced.isEmpty()
        )
    }

    /**
     * **A row cannot be written under an identity the gate did not just
     * approve** (PLAN 14.2.1).
     *
     * The fence above makes a call name the rider it is *acting for*; this one
     * makes the row it writes say so. The failure it exists to catch is not
     * hypothetical — `WorkoutDto` had no `user_id` field at all for the whole
     * project, so every ride that ever left a tablet arrived in the cloud
     * anonymous, and nothing anywhere noticed because the column was nullable
     * and the insert succeeded.
     *
     * The structural guarantee is that the account id arrives *from the gate*,
     * as a parameter of the block, rather than being read off an entity a
     * caller happened to be holding. Reading `authUserId` inside this file
     * would re-open the two-lookups-that-can-disagree shape.
     */
    @Test
    fun `the account id comes from the gate, not from an entity`() {
        assertTrue(
            "SupabaseSyncRepository reads authUserId directly; it must use the id the gate returned",
            !repositoryCode.contains("authUserId")
        )
        assertTrue(
            "the gate's answer is no longer threaded to the calls that write rows",
            repositoryCode.contains("accountId")
        )
    }

    /**
     * `local_user_id` is a per-device autoincrement — every bike's first
     * profile is `1`. It was the cloud's `UNIQUE` natural key and the upsert
     * target, which means the second tablet to sync would have overwritten the
     * first rider's row rather than creating its own. It must not travel.
     */
    @Test
    fun `the local profile id never leaves the tablet`() {
        val dtoSource = File(sourceRoot, "data/remote/dto/Dtos.kt").readText()
        val wireNames = Regex("""@SerialName\("([^"]+)"\)""")
            .findAll(dtoSource)
            .map { it.groupValues[1] }
            .toList()

        assertTrue(
            "a DTO is sending local_user_id: $wireNames",
            "local_user_id" !in wireNames
        )
        assertTrue(
            "SupabaseSyncRepository is upserting on local_user_id again",
            !repositoryCode.contains("local_user_id")
        )
    }
}
