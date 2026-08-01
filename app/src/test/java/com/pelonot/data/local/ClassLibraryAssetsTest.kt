package com.pelonot.data.local

import com.pelonot.data.remote.dto.ClassTemplateDto
import com.pelonot.domain.model.IntervalParser
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
}
