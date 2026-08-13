package com.pelonot.data.local

import com.pelonot.data.remote.dto.ClassTemplateDto
import com.pelonot.domain.model.GovernedBy
import com.pelonot.domain.model.Interval
import com.pelonot.domain.model.IntervalParser
import com.pelonot.domain.model.RidePosition
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * The bundled class library, checked at build time.
 *
 * The library used to live in the cloud with five classes in `assets/` as a
 * fallback, and the gap went unnoticed for the whole project because nothing
 * ever looked at what was actually shipped. Now that the assets *are* the
 * library (PLAN 23.2, and rule 1 of the connectivity model), a malformed or
 * missing class is a defect in the product rather than in a fallback — so it
 * gets a test rather than a log line at seed time.
 *
 * This reads the asset tree off disk rather than through `Context.assets`,
 * which keeps it a JVM test: the seeder's directory walk is the same either
 * way, and what is being checked here is the *contents*.
 */
class ClassLibraryAssetsTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val assetRoot = File("src/main/assets/classes")

    private val classFiles: List<File>
        get() = assetRoot.walkTopDown().filter { it.isFile && it.extension == "json" }.toList()

    private fun templates() = classFiles.map { file ->
        file to json.decodeFromString<ClassTemplateDto>(file.readText())
    }

    @Test
    fun `the whole library ships in the apk`() {
        assertTrue("assets/classes is missing entirely", assetRoot.isDirectory)
        // Not an exact count: adding a class should not break the build. The
        // floor is there because the number this guards against is five.
        assertTrue(
            "only ${classFiles.size} classes are bundled; the library is 72",
            classFiles.size >= 72
        )
    }

    @Test
    fun `every class decodes and has a unique id`() {
        val ids = templates().map { (file, dto) ->
            assertTrue("$file has no title", dto.title.isNotBlank())
            assertTrue("$file has no category", dto.category.isNotBlank())
            assertTrue("${dto.id} has a non-positive duration", dto.durationSec > 0)
            dto.id
        }

        val duplicates = ids.groupingBy { it }.eachCount().filterValues { it > 1 }
        assertTrue("duplicate class ids: $duplicates", duplicates.isEmpty())
    }

    /**
     * A class whose intervals do not parse renders as a class with no
     * structure in it — the exact failure `Interval`'s `@SerialName` block
     * describes, which hid behind a `catch { emptyList() }` for months.
     */
    @Test
    fun `every class has intervals that parse`() {
        for ((file, dto) in templates()) {
            val intervals = IntervalParser.parse(dto.intervalsJson)
                .getOrElse { throw AssertionError("$file has unreadable intervals_json", it) }
            assertTrue("${dto.id} has no intervals", intervals.isNotEmpty())
        }
    }

    /**
     * The engine looks up "which interval covers second N", so a gap leaves the
     * rider with no prescription at all and an overlap makes the answer depend
     * on iteration order. Neither is visible on screen until someone rides
     * through the seam.
     */
    @Test
    fun `intervals are contiguous and cover the stated duration`() {
        for ((file, dto) in templates()) {
            val intervals = IntervalParser.parse(dto.intervalsJson).getOrThrow()
            var expectedStart = 0
            for (interval in intervals) {
                assertEquals(
                    "$file: interval starting at ${interval.startSec} leaves a gap or overlap",
                    expectedStart,
                    interval.startSec
                )
                assertTrue(
                    "$file: interval at ${interval.startSec} has no length",
                    interval.endSec > interval.startSec
                )
                expectedStart = interval.endSec
            }
            assertEquals(
                "${dto.id}: intervals end at $expectedStart but duration_sec says ${dto.durationSec}",
                dto.durationSec,
                expectedStart
            )
        }
    }

    @Test
    fun `every interval asks for a real zone and a sane cadence`() {
        for ((file, dto) in templates()) {
            for (interval in IntervalParser.parse(dto.intervalsJson).getOrThrow()) {
                assertTrue(
                    "$file: zone ${interval.powerZoneNumber} is not one of the seven",
                    interval.powerZoneNumber in 1..7
                )
                assertTrue(
                    "$file: cadence ${interval.cadenceMin}-${interval.cadenceMax} is inverted",
                    interval.cadenceMin <= interval.cadenceMax
                )
                assertTrue(
                    "$file: cadence ${interval.cadenceMin}-${interval.cadenceMax} is off the board",
                    interval.cadenceMin in 30..140 && interval.cadenceMax in 30..140
                )
            }
        }
    }

    // -----------------------------------------------------------------------
    // The design rules — PLAN 23.2.6, written out in `classlibrary/README.md`.
    //
    // `build.py` checks these before it writes, which is where an author wants
    // to hear about them. They are repeated here because the *assets* are what
    // ships: a generator nobody runs cannot vouch for a file somebody edited by
    // hand, and the first 72 were exactly that — bad data that compiled.
    // -----------------------------------------------------------------------

    private fun plans() = templates().map { (file, dto) ->
        Triple(file, dto, IntervalParser.parse(dto.intervalsJson).getOrThrow())
    }

    /** R1. An explicit vocabulary, so that 20/10 is sayable and 97 s is not. */
    @Test
    fun `every block is a length a rider can hold`() {
        val shortLengths = setOf(10, 15, 20, 30, 40, 45, 60, 75, 90, 105)
        for ((file, _, intervals) in plans()) {
            for (interval in intervals) {
                val seconds = interval.durationSec
                val allowed =
                    if (seconds < 120) seconds in shortLengths else seconds % 60 == 0
                assertTrue(
                    "$file: a block of $seconds s is a slice of a percentage, not an effort",
                    allowed
                )
            }
        }
    }

    /**
     * R2. A class that never leaves Z2 is exempt: it has no work to be warm
     * for, which is what makes Recovery exempt rather than special-cased.
     */
    @Test
    fun `the warmup warms up`() {
        for ((file, _, intervals) in plans()) {
            if (intervals.maxOf { it.powerZoneNumber } < 3) continue

            val first = intervals.first()
            assertTrue(
                "$file: opens at Z${first.powerZoneNumber} for ${first.durationSec} s",
                first.powerZoneNumber == 1 && first.durationSec >= 120
            )

            val warmup = intervals.takeWhile { it.powerZoneNumber < 4 }
            assertTrue(
                "$file: rides ${warmup.map { it.powerZoneNumber }.distinct().size} zone(s) " +
                    "before the first hard block; a warmup is progressive",
                warmup.map { it.powerZoneNumber }.distinct().size >= 3
            )
            assertTrue(
                "$file: gives ${warmup.sumOf { it.durationSec }} s before the first hard " +
                    "block; five minutes is the floor",
                warmup.sumOf { it.durationSec } >= 300
            )
        }
    }

    /**
     * R2, the second half. Primers, stated as arithmetic: if the first thing a
     * rider does at Z5 is a three-minute effort, that effort is the warmup and
     * they pay for it in the next one.
     */
    @Test
    fun `nothing opens its hard work with a full effort`() {
        for ((file, _, intervals) in plans()) {
            val firstHard = intervals.firstOrNull { it.powerZoneNumber >= 5 } ?: continue
            assertTrue(
                "$file: opens its Z5+ work with a ${firstHard.durationSec} s effort; prime it",
                firstHard.durationSec <= 45
            )
        }
    }

    /** R3. */
    @Test
    fun `every class cools down`() {
        for ((file, _, intervals) in plans()) {
            assertEquals(
                "$file: does not end at Z1",
                1,
                intervals.last().powerZoneNumber
            )
            val tail = intervals.reversed()
                .takeWhile { it.powerZoneNumber <= 2 }
                .sumOf { it.durationSec }
            assertTrue("$file: has $tail s of cooldown; three minutes is the floor", tail >= 180)
        }
    }

    /**
     * R4. Only recovery *between* two efforts — the block after the last
     * interval is the cooldown, and holding it to this ratio would say nothing
     * about the session. Efforts under a minute are exempt: 20 s on / 10 s off
     * is a protocol, and what bounds it is the dose rule below.
     */
    @Test
    fun `recovery is proportionate to the effort it follows`() {
        for ((file, _, intervals) in plans()) {
            for (i in 1 until intervals.size - 1) {
                val rest = intervals[i]
                val effort = intervals[i - 1]
                if (rest.powerZoneNumber > 2) continue
                if (effort.powerZoneNumber < 4 || effort.durationSec < 60) continue
                if (intervals[i + 1].powerZoneNumber < 3) continue

                val needed =
                    if (effort.powerZoneNumber >= 5) effort.durationSec else effort.durationSec / 2
                assertTrue(
                    "$file: ${rest.durationSec} s of recovery after a ${effort.durationSec} s " +
                        "Z${effort.powerZoneNumber} effort; needs $needed s",
                    rest.durationSec >= needed
                )
            }
        }
    }

    /**
     * R5. The old library's cadence was a lookup from the zone — Z1 was always
     * 80-90, Z4 always 90-100 — so it could not express the difference between
     * a Z5 at 85 rpm and a Z5 at 105 rpm, which are different workouts.
     */
    @Test
    fun `cadence is a separate axis from zone`() {
        val bands = mutableMapOf<Int, MutableSet<Pair<Int, Int>>>()
        for ((_, _, intervals) in plans()) {
            for (interval in intervals) {
                bands.getOrPut(interval.powerZoneNumber) { mutableSetOf() }
                    .add(interval.cadenceMin to interval.cadenceMax)
            }
        }
        val varied = bands.count { (_, cadences) -> cadences.size >= 3 }
        assertTrue(
            "only $varied zone(s) are ridden at three or more cadences; cadence is " +
                "behaving like a lookup from the zone",
            varied >= 4
        )
    }

    /** R6, the ceilings. */
    @Test
    fun `the dose matches what the zone is for`() {
        val singleBlockCap = mapOf(4 to 20 * 60, 5 to 8 * 60, 6 to 90, 7 to 30)
        val totalCap = mapOf(4 to 40 * 60, 5 to 20 * 60, 6 to 10 * 60, 7 to 3 * 60)

        for ((file, _, intervals) in plans()) {
            for ((zone, cap) in singleBlockCap) {
                val longest = intervals.filter { it.powerZoneNumber == zone }
                    .maxOfOrNull { it.durationSec } ?: 0
                assertTrue("$file: has a $longest s block at Z$zone; the cap is $cap s", longest <= cap)
            }
            for ((zone, cap) in totalCap) {
                val total = intervals.filter { it.powerZoneNumber == zone }.sumOf { it.durationSec }
                assertTrue("$file: spends $total s at Z$zone; the cap is $cap s", total <= cap)
            }
        }
    }

    /**
     * R6, the stacking cap. This is the rule the old `TB-01` broke: sixteen
     * consecutive 20/10 rounds with no break between sets. The sixteenth round
     * of sixteen is not a Z6 effort, it is a rider soft-pedalling while the
     * prescription claims otherwise.
     */
    @Test
    fun `no class stacks more than eight bursts without a break`() {
        for ((file, _, intervals) in plans()) {
            var run = 0
            for (interval in intervals) {
                if (interval.powerZoneNumber >= 6) {
                    run++
                } else if (interval.powerZoneNumber <= 2 && interval.durationSec >= 45) {
                    run = 0
                }
                assertTrue("$file: stacks $run bursts at Z6+ without a real break", run <= 8)
            }
        }
    }

    /** R6, the floors: a category has to deliver what its name promises. */
    @Test
    fun `every category delivers what its name promises`() {
        val fractionFloor = mapOf(
            "Sweet Spot" to (4 to 0.25),
            "Threshold" to (4 to 0.25),
            "Climbs" to (4 to 0.25),
            "VO2 Max" to (5 to 0.15)
        )
        for ((file, dto, intervals) in plans()) {
            val floor = fractionFloor[dto.category] ?: continue
            val (zone, fraction) = floor
            val got = intervals.filter { it.powerZoneNumber >= zone }.sumOf { it.durationSec }
            assertTrue(
                "$file: is a ${dto.category} class with $got s at Z$zone+; " +
                    "needs ${(dto.durationSec * fraction).toInt()} s",
                got >= dto.durationSec * fraction
            )
        }
        for ((file, dto, intervals) in plans()) {
            if (dto.category != "Sprints") continue
            val got = intervals.filter { it.powerZoneNumber >= 6 }.sumOf { it.durationSec }
            assertTrue("$file: has $got s at Z6+; needs 180 s", got >= 180)
        }
    }

    /**
     * R7. A recovery class that spends half its time at Z2 is an endurance
     * class with a soothing name, which is how a rider ends up never actually
     * recovering. The old `RC-01` was a twenty-minute recovery ride with a
     * 6½-minute Z2 block in the middle of it.
     */
    @Test
    fun `a recovery class recovers`() {
        for ((file, dto, intervals) in plans()) {
            if (dto.category != "Recovery") continue
            val top = intervals.maxOf { it.powerZoneNumber }
            assertTrue("$file: is a Recovery class that reaches Z$top", top <= 2)
            val atZ2 = intervals.filter { it.powerZoneNumber == 2 }.sumOf { it.durationSec }
            assertTrue(
                "$file: spends $atZ2 s of ${dto.durationSec} s at Z2",
                atZ2 * 2 < dto.durationSec
            )
        }
    }

    /**
     * R11 — standing is an instruction, and it has to be a possible one
     * (PLAN 25.1.3). Nobody rides out of the saddle for five minutes, and
     * "stand up" at 120 rpm is an instruction with no way to follow it.
     */
    @Test
    fun `standing is asked for in a way a rider can do`() {
        for ((file, _, intervals) in plans()) {
            for (interval in intervals.filter { it.position == RidePosition.Standing }) {
                assertTrue(
                    "$file: asks the rider to stand for ${interval.durationSec} s",
                    interval.durationSec <= 180
                )
                assertTrue(
                    "$file: asks the rider to stand at ${interval.cadenceMin}-${interval.cadenceMax} rpm",
                    interval.cadenceMax <= 110
                )
            }
        }
    }

    /**
     * R11, the other half, and the reason the field is nullable: **absent is
     * the default and means the rider chooses** (25.1.1). A class that says
     * what to do with every block is nagging rather than coaching, so most of
     * a class must say nothing.
     */
    @Test
    fun `most of a class leaves the position to the rider`() {
        var withAPosition = 0
        for ((file, dto, intervals) in plans()) {
            val prescribed = intervals.filter { it.position != null }.sumOf { it.durationSec }
            assertTrue(
                "$file: prescribes a position for $prescribed s of ${dto.durationSec} s",
                prescribed * 2 <= dto.durationSec
            )
            if (prescribed > 0) withAPosition++
        }
        // And the field has to be used, or it is a column nobody filled in.
        assertTrue("no class in the library prescribes a position at all", withAPosition > 0)
    }

    /**
     * R12 — one instruction at a time, and it has to be one worth giving
     * (PLAN 11.7.2).
     *
     * A block claiming to be governed by 80–90 rpm is claiming nothing: that
     * is the library's default seated cadence, and prescribing it is exactly
     * how a rider spinning a perfectly good 92 rpm through a threshold effort
     * came to be shown amber for it.
     */
    @Test
    fun `a cadence only governs where the cadence is the point`() {
        for ((file, _, intervals) in plans()) {
            for (interval in intervals.filter { it.governedBy == GovernedBy.Cadence }) {
                assertTrue(
                    "$file: says cadence governs a ${interval.cadenceMin}-" +
                        "${interval.cadenceMax} rpm block, which is the neutral " +
                        "seated range",
                    interval.cadenceMin < 75 || interval.cadenceMax > 95
                )
            }
        }
    }

    /**
     * R12, the other two halves. A category named after the pedalling has to
     * say so on at least one block, and cadence stays the **exception** across
     * the library — the failure mode being the field spreading until "one
     * instruction at a time" means "always the cadence".
     */
    @Test
    fun `the cadence governs where the category promises it, and stays the exception`() {
        for ((file, dto, intervals) in plans()) {
            if (dto.category !in setOf("Climbs", "Sprints")) continue
            assertTrue(
                "$file: is a ${dto.category} class where the cadence never governs",
                intervals.any { it.governedBy == GovernedBy.Cadence }
            )
        }

        val all = plans().flatMap { (_, _, intervals) -> intervals }
        val governed = all.count { it.governedBy == GovernedBy.Cadence }
        assertTrue("no block anywhere is governed by the cadence", governed > 0)
        assertTrue("every block is governed by the cadence", governed < all.size)
        assertTrue(
            "cadence governs $governed of ${all.size} blocks; it is meant to be " +
                "the exception",
            governed * 3 <= all.size
        )
    }

    /** R9. Seventy-two classes and twelve shapes was the old library. */
    @Test
    fun `no two classes are the same class`() {
        val signatures = mutableMapOf<String, String>()
        for ((_, dto, intervals) in plans()) {
            val signature = intervals.joinToString("|") {
                "${it.durationSec}:${it.powerZoneNumber}:${it.cadenceMin}-" +
                    "${it.cadenceMax}:${it.governedBy}"
            }
            val existing = signatures.put(signature, dto.id)
            assertTrue("${dto.id} is the same class as $existing", existing == null)
        }

        val slots = plans().groupBy { (_, dto, _) -> dto.category to dto.durationSec }
        for ((slot, group) in slots) {
            for (i in group.indices) {
                for (j in i + 1 until group.size) {
                    val a = group[i].third
                    val b = group[j].third
                    if (a.size != b.size) continue
                    val differing = a.indices.count { a[it] != b[it] }
                    assertTrue(
                        "${group[i].second.id} and ${group[j].second.id} are both $slot " +
                            "and differ in $differing block(s)",
                        differing > 1
                    )
                }
            }
        }
    }

    /**
     * R13 — every class says what it is for, and says it without restating the
     * screen around it (PLAN 23.2.7).
     *
     * `build.py` enforces this too, and this test exists for the same reason
     * every other rule here is duplicated: **the assets are what ships, and a
     * generator nobody runs cannot vouch for them.** A description edited
     * straight into a JSON file — which CLAUDE.md forbids and somebody will
     * eventually do anyway — is caught here and nowhere else.
     *
     * What it deliberately does not check is whether a sentence is *true*.
     * Nothing can. The position clause is the one exception: a description
     * promising a position is making a claim the blocks have to keep, which is
     * the only way an authored sentence here can be wrong arithmetically. It
     * used to read `standing` only, and **both classes that broke it broke it
     * the other way** — `CLB-04` said "seated rises" with no position on any of
     * its seventeen blocks, and `SPR-05` promised a seated set in a class whose
     * only positioned blocks ask the rider to stand up (PLAN 23.2.8).
     */
    @Test
    fun `every class says what it is for, in the app's own voice`() {
        for ((file, dto, intervals) in plans()) {
            val text = dto.description
            assertTrue("${dto.id} has no description", text.isNotBlank())
            assertTrue(
                "${dto.id} description is ${text.length} characters; " +
                    "the band is $DESCRIPTION_MIN-$DESCRIPTION_MAX (${file.name})",
                text.length in DESCRIPTION_MIN..DESCRIPTION_MAX
            )

            val minutes = dto.durationSec / 60
            assertFalse(
                "${dto.id} description names its own length ($minutes), which is " +
                    "drawn beside it",
                Regex("\\b$minutes\\b").containsMatchIn(text)
            )
            assertFalse(
                "${dto.id} description names its own category (${dto.category}), " +
                    "which is drawn beside it",
                Regex("\\b${Regex.escape(dto.category)}\\b", RegexOption.IGNORE_CASE)
                    .containsMatchIn(text)
            )

            for (word in JARGON) {
                assertFalse(
                    "${dto.id} description says \"$word\"; a rider choosing a class " +
                        "is not reading a measurement (Phase 26)",
                    Regex("\\b$word\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)
                )
            }

            for (broken in brokenPositionPromises(text, intervals)) {
                fail("${dto.id} description promises $broken riding and no block asks for it")
            }
        }
    }

    /**
     * R10 — the title names the shape and the demand (PLAN 23.2.6, 25.4.2).
     *
     * The whole of R10 lived in `build.py` and nowhere else, which is a poor
     * place for it to live: it is the one rule in this library with a history
     * of being written down and broken by **all 72 classes at once**, and the
     * position half of it — the rule the owner's rename in 25.4.2 produced —
     * was enforced on no surface at all. The four classes it renamed were found
     * by reading the list.
     */
    @Test
    fun `the title says what the ride is`() {
        val plans = plans()
        for ((_, dto, intervals) in plans) {
            val minutes = dto.durationSec / 60
            assertFalse(
                "${dto.id} \"${dto.title}\" ends in its own length; the duration is " +
                    "already on every surface that shows the title",
                Regex("(^|\\s)$minutes$").containsMatchIn(dto.title)
            )
            for (broken in brokenPositionPromises(dto.title, intervals)) {
                fail("${dto.id} \"${dto.title}\" promises $broken riding and no block asks for it")
            }
        }
        // Titles have to be unique on their own: stripping the duration took
        // away the thing that was quietly doing it.
        val titles = plans.map { it.second.title }
        for (title in titles.toSet()) {
            assertEquals("\"$title\" is the name of more than one class", 1, titles.count { it == title })
        }
    }

    /**
     * The positions a piece of authored text claims that no interval prescribes.
     *
     * One helper for the title and the description, because they make the
     * identical claim — and "big gear" is in it because in cycling usage it
     * means seated torque and reads as that instruction, which is what three of
     * the four classes renamed in 25.4.2 actually said.
     */
    private fun brokenPositionPromises(text: String, intervals: List<Interval>): List<String> {
        val prescribed = intervals.mapNotNull { it.position }.toSet()
        return POSITION_WORDS
            .filter { (position, pattern) ->
                Regex(pattern, RegexOption.IGNORE_CASE).containsMatchIn(text) &&
                    position !in prescribed
            }
            .map { (position, _) -> position.name.lowercase() }
    }

    private companion object {
        /** Long enough to say something, short enough to be read over a bike. */
        const val DESCRIPTION_MIN = 80
        const val DESCRIPTION_MAX = 320

        /**
         * Units and acronyms. The zone *vocabulary* is fine — "threshold
         * effort" describes a feeling — so this bans the way a measurement is
         * written, not the way riding is talked about.
         */
        val JARGON = listOf("FTP", "watts?", "kilojoules?", "kJ", "W/kg", "VO2", "rpm")

        /**
         * A position word is a promise the blocks have to keep, and it is the
         * same promise whichever surface says it (PLAN 25.4.2, 23.2.8).
         */
        val POSITION_WORDS = mapOf(
            RidePosition.Standing to "out of the saddle|standing|stand up",
            RidePosition.Seated to "seated|big gear",
        )
    }
}
