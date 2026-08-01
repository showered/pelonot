package com.pelonot.data.sensor

/**
 * What happened to one value handed to [TelemetryAssembler].
 *
 * Three outcomes rather than a nullable reading, because the caller has
 * something different to say about each: [Emit] is telemetry, [Rejected] is
 * evidence worth logging, and [Held] is the ordinary case of a triple that is
 * not complete yet.
 */
sealed interface Intake {

    /** The triple is complete and coherent; this is what the rider is doing. */
    data class Emit(val reading: SensorReading) : Intake

    /** Accepted, but the other two fields are missing or too old to stand beside it. */
    data object Held : Intake

    /** The fence turned it away. Nothing was updated, so the field ages. */
    data class Rejected(val value: ImplausibleValue) : Intake

    /**
     * Accepted and stored, but the stream is not trusted at all right now
     * because an impossible value arrived recently.
     *
     * Distinct from [Held] because it means something different: Held is a
     * triple that is not ready, Quarantined is a triple that may be complete
     * and coherent and is still a lie.
     */
    data object Quarantined : Intake
}

/**
 * Turns the board's three independent event streams into one reading — and
 * refuses to invent one (2.7.1, 2.7.6).
 *
 * The sensor board does not report a triple. It reports cadence, then power,
 * then resistance, each on its own message, each whenever it feels like it.
 * The code this replaces kept three `var`s and emitted all three every time
 * *any one* of them changed, which has two consequences that only became
 * visible when a real rider got on the bike:
 *
 *  - **Every reading mixed instants.** Cadence from now, power from 200 ms
 *    ago, resistance from whenever the knob was last polled — published as one
 *    observation and recorded as one row.
 *  - **A field never seen was published as zero.** The three `var`s started at
 *    `0.0`, so the first message of every ride emitted a reading claiming two
 *    measured zeroes, `powerIsMeasured = true` and all.
 *
 * This class holds each field with the instant it arrived and hands back a
 * reading only when all three are present *and* mutually fresh. A stream that
 * dies takes the whole reading down with it rather than being carried along
 * inside one that looks live — which is the same argument as
 * [SensorReading.isStaleAt], one level further up.
 *
 * Pure Kotlin and free of Android imports, because the defect it exists for
 * needs a test and the class that used to hold this logic could not have one.
 */
class TelemetryAssembler(
    /**
     * How far apart two fields may be and still describe the same moment.
     *
     * All three registrations are *repeating* polls, so on a healthy board
     * every field refreshes several times a second and this never bites. Two
     * and a half seconds is therefore several missed polls rather than one
     * unlucky one — and it is deliberately shorter than
     * [SensorReading.MAX_AGE_MS], so a half-dead board shows up as an
     * incoherent triple before it shows up as a stale one.
     */
    private val coherenceWindowMs: Long = DEFAULT_COHERENCE_WINDOW_MS,

    /**
     * How long the whole stream stays untrusted after one impossible value
     * (2.7.1a).
     *
     * The reason this exists is the shape of the real defect. The board's
     * events are labelled by whoever sends them, and on the ride of 1 August
     * 2026 a **fourth value entered the cycle** — the raw resistance reading,
     * `≈ 11.13 × resistance% + 229` — so three fields were being filled from a
     * four-value stream and every label after the intruder was wrong. The
     * intruder is out of range and easy to catch. **Its neighbours are not**:
     * a power of 37 W filed as a resistance of 37% breaks no bound anyone can
     * write.
     *
     * So an impossible value is not treated as one bad sample. It is treated
     * as *evidence that the labelling is wrong*, and everything near it is
     * suspect. Four seconds comfortably bridges the gaps between sightings
     * within both recorded bursts (the longest was three), and the cost of
     * being wrong is a gap, which is the honest answer anyway.
     */
    private val resyncQuietMs: Long = DEFAULT_RESYNC_QUIET_MS,

    /**
     * Whether the watts this assembles were measured. True for the bike's own
     * board, which is the only thing that reports the three streams
     * separately; the parameter exists so the flag is set where the fact is
     * known rather than copied on afterwards.
     */
    private val powerIsMeasured: Boolean = true
) {

    private val values = HashMap<TelemetryField, Double>(3)
    private val seenAtMs = HashMap<TelemetryField, Long>(3)

    /** How many values the fence has turned away since [reset]. */
    var rejectedCount: Int = 0
        private set

    /** How many readings were withheld because the stream was untrusted. */
    var quarantinedCount: Int = 0
        private set

    private var quarantinedUntilMs: Long = Long.MIN_VALUE

    /** True while an impossible value is still close enough to distrust. */
    fun isQuarantinedAt(atMs: Long): Boolean = atMs < quarantinedUntilMs

    /**
     * Offers one field's value, measured at [atMs].
     *
     * @return [Intake.Emit] when this completes a coherent triple.
     */
    fun onValue(field: TelemetryField, value: Double, atMs: Long): Intake {
        if (!TelemetryBounds.accepts(field, value)) {
            // Deliberately does not touch the stored value or its timestamp:
            // an impossible reading must not refresh the field it lands in, or
            // a dead stream sending nonsense would look like a live one.
            rejectedCount++
            // And everything already stored is now suspect too. If a value
            // landed in the wrong field, so did the ones on either side of it,
            // and those are in range. Throwing the lot away is what makes the
            // burst a gap rather than a stretch of plausible fiction.
            values.clear()
            seenAtMs.clear()
            quarantinedUntilMs = atMs + resyncQuietMs
            return Intake.Rejected(ImplausibleValue(field, value))
        }

        values[field] = value
        seenAtMs[field] = atMs

        if (isQuarantinedAt(atMs)) {
            quarantinedCount++
            return Intake.Quarantined
        }

        val oldest = seenAtMs.values.minOrNull() ?: return Intake.Held
        if (seenAtMs.size < TelemetryField.entries.size) return Intake.Held
        if (atMs - oldest > coherenceWindowMs) return Intake.Held

        return Intake.Emit(
            SensorReading(
                powerWatts = values.getValue(TelemetryField.Power),
                cadenceRpm = values.getValue(TelemetryField.Cadence),
                resistancePercent = values.getValue(TelemetryField.Resistance),
                powerIsMeasured = powerIsMeasured,
                // The instant of the *oldest* field, not of this message. The
                // reading is only as fresh as its stalest part, and saying
                // otherwise is how a half-frozen triple passes a staleness
                // check it should fail.
                timestampMs = oldest
            )
        )
    }

    /** Forgets everything, for a source being rebuilt. */
    fun reset() {
        values.clear()
        seenAtMs.clear()
        rejectedCount = 0
        quarantinedCount = 0
        quarantinedUntilMs = Long.MIN_VALUE
    }

    companion object {
        const val DEFAULT_COHERENCE_WINDOW_MS = 2_500L
        const val DEFAULT_RESYNC_QUIET_MS = 4_000L
    }
}
