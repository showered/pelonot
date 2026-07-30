package com.pelonot.data.sensor

/** A decoded event from the Peloton sensor board. */
sealed interface SerialEvent {
    /** One flywheel revolution detected by the hall-effect sensor. */
    data object CadenceTick : SerialEvent

    /** Resistance knob position, already clamped to 0–100. */
    data class ResistanceUpdate(val percent: Double) : SerialEvent
}

/**
 * Decodes the byte stream from the sensor board on `/dev/ttyS*`.
 *
 * The protocol is single-byte commands, where `R` is followed by one value
 * byte:
 * ```
 * 'C'          -> cadence tick
 * 'R' <value>  -> resistance update
 * ```
 *
 * This is a stateful stream parser rather than a per-buffer one because reads
 * are not framed: an `R` can be the last byte of one read with its value
 * arriving in the next. The previous implementation treated each buffer
 * independently and dropped the trailing `R`, losing that resistance update
 * entirely — so resistance appeared to stick at a stale value whenever the
 * knob was turned near a read boundary.
 *
 * Pure Kotlin, no Android dependencies, so it is directly unit-testable.
 */
class SerialProtocolParser {

    private var awaitingResistanceValue = false

    /**
     * Decodes the first [length] bytes of [buffer] into events, carrying any
     * incomplete command over to the next call.
     */
    fun parse(buffer: ByteArray, length: Int): List<SerialEvent> {
        val events = mutableListOf<SerialEvent>()
        var index = 0

        while (index < length) {
            val byte = buffer[index].toInt() and 0xFF

            if (awaitingResistanceValue) {
                events += SerialEvent.ResistanceUpdate(
                    byte.toDouble().coerceIn(0.0, MAX_RESISTANCE)
                )
                awaitingResistanceValue = false
                index++
                continue
            }

            when (byte.toChar()) {
                CADENCE_TICK -> {
                    events += SerialEvent.CadenceTick
                    index++
                }

                RESISTANCE_PREFIX -> {
                    if (index + 1 < length) {
                        val raw = buffer[index + 1].toInt() and 0xFF
                        events += SerialEvent.ResistanceUpdate(
                            raw.toDouble().coerceIn(0.0, MAX_RESISTANCE)
                        )
                        index += 2
                    } else {
                        // Value byte lands in the next read.
                        awaitingResistanceValue = true
                        index++
                    }
                }

                // Line noise and framing bytes are expected on a raw UART.
                else -> index++
            }
        }

        return events
    }

    /** Discards any partially-read command. Call after a reconnect. */
    fun reset() {
        awaitingResistanceValue = false
    }

    private companion object {
        const val CADENCE_TICK = 'C'
        const val RESISTANCE_PREFIX = 'R'
        const val MAX_RESISTANCE = 100.0
    }
}
