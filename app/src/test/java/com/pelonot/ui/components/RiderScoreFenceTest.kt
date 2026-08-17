package com.pelonot.ui.components

import com.pelonot.domain.progress.RiderLevel
import com.pelonot.domain.progress.RidingTotals
import com.pelonot.ui.viewmodel.AppUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * A fence around the level badge's four rules, not a test of how it draws
 * (PLAN 26.4.10).
 *
 * **This is 2.2a.8 arriving at a second component**, and it was found the same
 * way: building 26.4.9 and noticing that `RiderScore`'s rule 2 is stated four
 * times in prose — *"Nothing else may follow either. Not the greeting, not the
 * household panel, not the static class board, not the overlay"* — and enforced
 * by nobody. Two screens may draw a level on a rider's face and five other call
 * sites of [RiderAvatar] exist; what keeps the level off those five is that
 * nobody has passed the argument. That is a hope rather than a rule, in exactly
 * the sense the previous sitting named it.
 *
 * The cost of the hope failing is not cosmetic. One `level = rider.level` on the
 * household panel's row is a plausible three-word diff and it would put a level
 * beside a housemate's FTP on a **presence** card, which is the reading 26.4.5
 * exists to forbid: *"someone could be lvl 20 but only 50 FTP, so not a very
 * good rider"*.
 *
 * **A JVM source scan rather than a Compose UI test, deliberately.** This
 * project has no Compose test infrastructure at all — twelve instrumented tests
 * and every one of them Room — and adding a whole test category to hold one
 * component's rules is a bigger decision than the rules need.
 * [com.pelonot.data.sensor.PowerModelFenceTest] and
 * [com.pelonot.data.remote.CloudAccessFenceTest] established this shape.
 *
 * **What this cannot check is 26.4.9a**, and saying so beats a scan that
 * pretends. That the *compact* badge draws no word is a fact about which branch
 * a string literal sits in, and a text scan claiming to know would pass for the
 * wrong reason. What it gets instead is the count — one `LVL` in one file —
 * which catches the regression that actually happens.
 */
class RiderScoreFenceTest {

    private val sourceRoot = File("src/main/java/com/pelonot")

    /**
     * The two screens that may draw a level on a rider's face, and the reason
     * each is allowed — because the reason is what a third entry has to supply.
     *
     * Adding a file here is a claim that the surface is one every rider on it
     * has opted into being read on, and that the FTP beside it is on a
     * different row at a different weight (26.4.8's three conditions). A
     * *presence* card is not that and neither is the overlay.
     */
    private val mayPutALevelOnAFace = mapOf(
        "ui/screen/ProfileSelectorScreen.kt" to
            "the tile whose only question is which of you is it (20.6.4)",
        "ui/screen/RideScreen.kt" to
            "the live leaderboard, where household_visible is the consent (24.3.19b)"
    )

    /**
     * The three surfaces rule 2 names as forbidden, checked from the other end.
     *
     * The allowlist above already fails if one of these acquires a level, so
     * this is belt and braces — and it is worth the duplication because these
     * three are named *by name* in the KDoc and in 26.4.8, so a reader who
     * breaks the rule will have read the sentence forbidding it. A test that
     * says the same thing back is the difference between a convention and a
     * rule.
     */
    private val mayNotPutALevelOnAFace = listOf(
        "ui/components/HouseholdPanelCard.kt",
        "ui/screen/MainDashboardScreen.kt",
        "ui/screen/ProfileCreationScreen.kt"
    )

    /**
     * Every file allowed to draw the badge at all, in either form.
     *
     * Wider than [mayPutALevelOnAFace] on purpose: the *pill* beside a name is
     * not the thing rule 2 restricts, and the greeting and the household panel
     * both draw one legitimately. [RiderAvatar] is here because it owns the
     * compact form.
     */
    private val mayDrawTheBadge = setOf(
        "ui/components/RiderAvatar.kt",
        "ui/components/HouseholdPanelCard.kt",
        "ui/screen/MainDashboardScreen.kt"
    )

    private fun sources(): List<Pair<String, String>> = sourceRoot.walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .map {
            it.path.replace(File.separatorChar, '/').substringAfter("com/pelonot/") to it.readText()
        }
        .toList()

    /**
     * Comments stripped, for the reason the other two fences give: the names
     * this forbids are exactly the ones a KDoc has to say out loud to explain
     * why they are forbidden, and a fence that punishes the explanation teaches
     * the next person to delete it.
     */
    private fun code(text: String): String = text
        .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("""//[^\n]*"""), "")

    /**
     * The argument list of every `RiderAvatar(...)` call in one file.
     *
     * Balanced-paren rather than a regex over the next few lines, because a
     * call site is formatted across five lines today and could be one tomorrow,
     * and a fence whose correctness depends on somebody's line breaks is the
     * kind that stops matching silently.
     */
    private fun avatarCallArguments(text: String): List<String> {
        val calls = mutableListOf<String>()
        val marker = "RiderAvatar("
        var from = 0
        while (true) {
            val start = text.indexOf(marker, from)
            if (start < 0) return calls
            // `fun RiderAvatar(` is the declaration, not a call.
            val isDeclaration = text.lastIndexOf("fun ", start).let {
                it >= 0 && text.substring(it, start).isBlank().not() &&
                    text.substring(it + 4, start).isBlank()
            }
            var depth = 0
            var i = start + marker.length - 1
            while (i < text.length) {
                when (text[i]) {
                    '(' -> depth++
                    ')' -> {
                        depth--
                        if (depth == 0) break
                    }
                }
                i++
            }
            if (!isDeclaration) calls += text.substring(start + marker.length, i.coerceAtMost(text.length))
            from = start + marker.length
        }
    }

    private val passesALevel = Regex("""(^|[,(\s])level\s*=""")

    /**
     * Rule 2. **A third screen putting a level on a face fails the build**, and
     * so does a stale entry — a reason nobody relies on is the same kind of
     * claim this project keeps finding gone off.
     */
    @Test
    fun `only the named screens may draw a level on a rider's face`() {
        val callers = sources()
            .filterNot { (path, _) -> path == "ui/components/RiderAvatar.kt" }
            .filter { (_, text) ->
                avatarCallArguments(code(text)).any { passesALevel.containsMatchIn(it) }
            }
            .map { (path, _) -> path }
            .toSet()

        assertEquals(
            "the set of screens drawing a level on a rider's face has changed; a new " +
                "one must be added to mayPutALevelOnAFace with the reason its surface " +
                "is one every rider on it has opted into (PLAN 26.4.8, RiderScore rule 2)",
            mayPutALevelOnAFace.keys,
            callers
        )
    }

    /**
     * Rule 2 from the other end, and the three files are the ones the KDoc
     * names out loud.
     */
    @Test
    fun `the presence surfaces draw a face and never a level on it`() {
        val byPath = sources().toMap()
        mayNotPutALevelOnAFace.forEach { path ->
            val text = byPath[path]
            assertNotNull("$path has moved; this fence is now checking nothing", text)
            val calls = avatarCallArguments(code(text!!))
            assertTrue("$path no longer draws a face at all; the fence is stale", calls.isNotEmpty())
            calls.forEach { arguments ->
                assertTrue(
                    "$path passes a level to RiderAvatar. A level beside a housemate's " +
                        "name on a presence card is what RiderScore rule 2 forbids " +
                        "(26.4.5); the pill beside the name is the shape these surfaces use",
                    !passesALevel.containsMatchIn(arguments)
                )
            }
        }
    }

    /**
     * Rule 2's other half — who may draw the badge at all, in either form.
     *
     * This is the looser of the two and still worth having: the static class
     * board and the overlay are both named as forbidden, and both are surfaces
     * a future item might reach for.
     */
    @Test
    fun `only the named files may draw the badge`() {
        val callers = sources()
            .filterNot { (path, _) -> path == "ui/components/RiderScore.kt" }
            .filter { (_, text) -> code(text).contains("RiderScore(") }
            .map { (path, _) -> path }
            .toSet()

        assertEquals(
            "the set of files drawing the level badge has changed; the overlay and the " +
                "static class board are named as forbidden in RiderScore's rule 2",
            mayDrawTheBadge,
            callers
        )
    }

    /**
     * Rule 1, and it is also the fence against the private copy this component
     * was extracted to prevent.
     *
     * Before `RiderScore` existed the only avatar in the app lived inside
     * `ProfileSelectorScreen` as a private `Box`, which is how it came to be
     * drawn off the power-zone palette. A second `"LVL"` anywhere in `src/main`
     * is that happening again — and it is the check 26.4.9a's real regression
     * trips over, which is somebody adding the word back rather than somebody
     * moving which branch it is in.
     */
    @Test
    fun `the word LVL is spelled in exactly one file`() {
        val spellers = sources()
            .filter { (_, text) -> code(text).contains("\"LVL\"") }
            .map { (path, _) -> path }

        assertEquals(
            "LVL is the one word this badge is allowed (26.4.2) and RiderScore is the " +
                "one component that draws it; a second speller is a private copy of the " +
                "badge, which is what this component exists to prevent",
            listOf("ui/components/RiderScore.kt"),
            spellers
        )
    }

    /**
     * Rule 3. **Amber is this app's off-target signal** (11.8.3) and a rider's
     * own identity must not wear the colour that means *you are wrong*.
     *
     * Checked as *no amber token reachable from this file* rather than as the
     * exact colour it does use, because the rule is about what it must not be:
     * a future restyle onto some other calm container colour is fine and should
     * not fail a build.
     */
    @Test
    fun `the badge is never amber`() {
        val badge = File(sourceRoot, "ui/components/RiderScore.kt").readText()
        val amber = Regex("""Amber|colorScheme\.tertiary""")

        assertTrue(
            "RiderScore reaches for an amber token. Amber means off target (11.8.3) and " +
                "a rider's level is a thing that simply is — RiderScore rule 3",
            !amber.containsMatchIn(code(badge))
        )
    }

    /**
     * Rule 4, and it is the one that is behaviour rather than structure — **and
     * it had no test at all.**
     *
     * The two absences must stay different claims. A profile that has never
     * ridden is level 1, because level 1 is *the start* and a badge appearing
     * out of nowhere after the first ride is a badge nobody was working
     * towards. A guest gets no badge, because a guest's rides are filed against
     * nobody and a guest can never leave level 1 however much they ride —
     * promising a ladder that does not exist is worse than no badge. Same
     * family as nullable `heartRateBpm`: absent is a claim, and it is a
     * different claim from 1.
     */
    @Test
    fun `a guest has no level and a rider who has never ridden is level 1`() {
        val state = AppUiState(riderLevels = mapOf(7 to RiderLevel.of(RidingTotals(rides = 41))))

        assertNull(
            "a guest must have no level: their rides are filed against nobody, so the " +
                "ladder the badge promises does not exist for them — RiderScore rule 4",
            state.levelFor(null)
        )

        val unridden = state.levelFor(3)
        assertNotNull("a profile with no rides must still have a level", unridden)
        assertEquals(
            "a profile that has never ridden is level 1 — the start, not an achievement",
            RiderLevel.FIRST_LEVEL,
            unridden!!.level
        )
        assertTrue(
            "and it must know it has ridden nothing, so nothing captions it as progress",
            unridden.isUnstarted
        )

        assertEquals(
            "a profile with rides gets the level its riding earned",
            RiderLevel.of(RidingTotals(rides = 41)).level,
            state.levelFor(7)!!.level
        )
    }
}
