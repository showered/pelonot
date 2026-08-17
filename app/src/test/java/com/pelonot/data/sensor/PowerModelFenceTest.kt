package com.pelonot.data.sensor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * A fence around the power curve, not a test of it (PLAN 2.2a.8).
 *
 * **The scope argument is the entire safety case for calibrating at all**, and
 * until this file existed it held only because a handful of call sites happened
 * not to have grown. PLAN 2.2a says it plainly: calibration governs *simulated
 * rides* and the *prescribed resistance band* — a fiction and a suggestion —
 * and it is allowed to exist because nothing derives a **recorded** number from
 * a curve this project measured at RMSE 137 W and 66% median error. A future
 * feature that "just needs a watt here" would not look wrong in review and would
 * quietly move that number into a rider's permanent record.
 *
 * So this checks the *structure* that makes the rule hold rather than the
 * behaviour at one call site:
 *
 * 1. exactly three files may ask the curve for a number, and each says why;
 * 2. a file that models a watt never labels it measured;
 * 3. the curve in force is installed from the two places that own it.
 *
 * Nothing here calls the model. `PowerModelTest` carries the arithmetic; this
 * carries the blast radius — the same idea as [com.pelonot.data.remote.CloudAccessFenceTest],
 * whose KDoc has cited this fence since before it was written.
 *
 * **One thing writing it found: the scope argument named two consumers and
 * there are three.** [SerialSensorSource] models watts too, and it is not a
 * fourth kind of thing — it is [SimulatedSensorSource]'s twin, a sensor source
 * on a board that does not report power. What makes both safe is rule 2 rather
 * than rule 1: they flag `powerIsMeasured = false`, so `PowerProvenance` calls
 * their rides `Modelled` and every gate that matters — the FTP proposal
 * (7.10.7), the household board (24.4.2) — already refuses them.
 */
class PowerModelFenceTest {

    private val sourceRoot = File("src/main/java/com/pelonot")

    /**
     * Where a curve is *implemented*, as against where one is *consulted*.
     *
     * `PowerModel` is the door and `domain/calibration/` is the room behind it:
     * the fitter has to evaluate a candidate curve to score it, which is the
     * one place asking for a watt is the work rather than a use of it.
     */
    private val implementation = setOf(
        "data/sensor/PowerModel.kt",
        "domain/calibration/"
    )

    /**
     * The three files that may turn a curve into a number, and the reason each
     * is allowed — because the reason is what a fourth entry has to supply.
     *
     * A file added here is a claim that its number is a fiction or a
     * suggestion. Anything that would be *recorded* belongs on the other side
     * of this list, and 2.2a.3's 25% absolute bar is not a bar a recorded watt
     * should ever be held to.
     */
    private val allowedConsumers = mapOf(
        "data/sensor/SimulatedSensorSource.kt" to
            "a simulated ride is fiction by construction (2.2a.7)",
        "data/sensor/SerialSensorSource.kt" to
            "the rooted-tablet fallback, on a board that does not report watts",
        "data/service/RideSnapshot.kt" to
            "the prescribed resistance band, which is a suggestion (11.2.1)"
    )

    /** Every way of getting a number out of a curve, by whatever route. */
    private val asksTheCurve = Regex(
        """\b(estimateWatts|resistanceForWatts)\(|\.watts\("""
    )

    private fun sources(): List<Pair<String, String>> = sourceRoot.walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .map { it.path.replace(File.separatorChar, '/').substringAfter("com/pelonot/") to it.readText() }
        .toList()

    /**
     * Comments stripped, for the reason [com.pelonot.data.remote.CloudAccessFenceTest]
     * gives: the names this fence forbids are exactly the ones a KDoc has to say
     * out loud to explain why they are forbidden, and a fence that punishes the
     * explanation teaches the next person to delete it.
     */
    private fun code(text: String): String = text
        .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("""//[^\n]*"""), "")

    /**
     * Rule 1. **A fourth consumer fails the build**, and so does a stale entry
     * on the list above — a reason nobody is relying on is the same kind of
     * claim this project keeps finding gone off.
     */
    @Test
    fun `only the named consumers may ask the curve for a number`() {
        val callers = sources()
            .filterNot { (path, _) -> implementation.any { path.startsWith(it) } }
            .filter { (_, text) -> asksTheCurve.containsMatchIn(code(text)) }
            .map { (path, _) -> path }
            .toSet()

        assertEquals(
            "the set of files deriving a number from the power curve has changed; " +
                "a new one must be added to allowedConsumers with the reason its " +
                "number is a fiction or a suggestion and never a record (PLAN 2.2a)",
            allowedConsumers.keys,
            callers
        )
    }

    /**
     * Rule 2, and it is the one that actually protects the record.
     *
     * `powerIsMeasured` defaults to false, so a modelled reading is honest by
     * omission — but a source that both models a watt and asserts it was
     * measured would put an uncalibrated number past every gate in the app at
     * once, and `PowerProvenance.isTrustworthyAsMeasured` would wave it through
     * because the column says so.
     */
    @Test
    fun `nothing that models a watt claims it was measured`() {
        val liars = sources()
            .filter { (path, _) -> path in allowedConsumers }
            .filter { (_, text) ->
                Regex("""powerIsMeasured\s*=\s*true""").containsMatchIn(code(text))
            }
            .map { (path, _) -> path }

        assertTrue(
            "these files model a watt and then label it measured: $liars",
            liars.isEmpty()
        )
    }

    /**
     * And the other direction: the one source that reports *real* watts must
     * never fall back to the model.
     *
     * On the bike the board reports power directly, and a source that filled a
     * gap from the curve would produce a ride whose samples are partly measured
     * and partly invented with nothing on the row saying which second was
     * which. The honest answer to a gap is a gap (2.4.4).
     */
    @Test
    fun `the measured source never falls back to the model`() {
        val measured = sources()
            .single { (path, _) -> path.endsWith("PelotonSensorServiceSource.kt") }

        assertTrue(
            "PelotonSensorServiceSource is deriving watts from the curve; " +
                "a gap in measured power is a gap, not a modelled sample",
            !asksTheCurve.containsMatchIn(code(measured.second))
        )
    }

    /**
     * Rule 3. [PowerModel.curve] is process-global mutable state, so *which*
     * curve is in force depends on who wrote it last.
     *
     * Two places own that: `WorkoutService` installs this bike's own curve as a
     * ride starts and resumes, and Settings restores the shipped one when the
     * rider throws the calibration away. A third writer is how a bike comes to
     * be running a curve nobody can account for.
     */
    @Test
    fun `the curve in force is installed from the two places that own it`() {
        val installers = sources()
            .filterNot { (path, _) -> implementation.any { path.startsWith(it) } }
            .filter { (_, text) ->
                Regex("""PowerModel\.(curve\s*=|useShippedCurve\()""")
                    .containsMatchIn(code(text))
            }
            .map { (path, _) -> path }
            .toSet()

        assertEquals(
            "the set of files installing a power curve has changed",
            setOf(
                "data/service/WorkoutService.kt",
                "ui/viewmodel/SettingsViewModel.kt"
            ),
            installers
        )
    }
}
