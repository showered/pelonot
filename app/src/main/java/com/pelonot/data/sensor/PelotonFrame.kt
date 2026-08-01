package com.pelonot.data.sensor

/**
 * One decoded frame straight off the sensor board.
 *
 * @property field null for a frame this app has no use for — notably the raw
 *   resistance report, which is a real reading and simply not one of ours.
 */
data class PelotonFrame(
    val id: Int,
    val field: TelemetryField?,
    val value: Double
)

/**
 * Decodes the board's own wire frames (2.7.1b).
 *
 * **This exists because `msg.what` is not trustworthy**, and the frames are.
 * Peloton's `SensorService` hands every reply to a `Messenger` with a `what`
 * naming the metric — but that label is assigned by position in the service's
 * own request cycle, and when anything disturbs that cycle the labels slide
 * along while the payloads do not. Measured on the bike, 1 August 2026: with a
 * second sensor app starting and stopping, **55 of 204 messages carried a
 * payload that disagreed with their own label**, including a cadence of 544
 * with a rider standing still.
 *
 * Every reply also carries `responseHexString`, the untouched frame, and the
 * frame says what it is. Reading that instead of the label is not a
 * workaround; it is using the identifier the board actually sends.
 *
 * ```
 * F1 <id> <len> <len ASCII digits, least significant first> <checksum> F6
 *
 * F1 49 03 33 33 30 D3 F6   resistance  "330" -> 033 -> 33 %
 * F1 41 03 30 35 37 ...     cadence     reversed likewise, whole rpm
 * F1 44 05 30 38 33 30 30   power       five digits, tenths of a watt
 * F1 4A 04 33 34 35 30 0B   RAW resistance — the intruder of 2.7b
 * ```
 *
 * The checksum is the sum of every byte from `F1` up to the last digit, mod
 * 256, and is verified: a frame that fails it is not decoded at all, because
 * the whole point of this class is that it is the last word on what a value
 * means.
 *
 * Pure Kotlin and free of Android imports, so the 158 distinct frames captured
 * off the real board can be replayed against it in a JVM test.
 */
object PelotonFrameParser {

    /**
     * @return null when [hex] is absent, malformed, or fails its checksum.
     *   Never guesses: a frame that cannot be trusted is not a frame.
     */
    fun parse(hex: String?): PelotonFrame? {
        val bytes = bytesOf(hex) ?: return null
        // F1, id, len, at least one digit, checksum, F6.
        if (bytes.size < 6) return null
        if (bytes.first() != START || bytes.last() != END) return null

        val length = bytes[2]
        val digitsEnd = 3 + length
        // The checksum sits immediately after the digits, and F6 after that.
        if (digitsEnd + 2 != bytes.size) return null

        val digits = bytes.subList(3, digitsEnd)
        if (digits.any { it < ASCII_ZERO || it > ASCII_NINE }) return null

        val expected = bytes.subList(0, digitsEnd).sum() and 0xFF
        if (expected != bytes[digitsEnd]) return null

        // Least significant digit first, so "330" is 33 and not 330.
        val magnitude = digits.reversed()
            .fold(0L) { acc, b -> acc * 10 + (b - ASCII_ZERO) }

        val id = bytes[1]
        return PelotonFrame(
            id = id,
            field = fieldOf(id),
            value = if (id == ID_POWER) magnitude / 10.0 else magnitude.toDouble()
        )
    }

    /** True for a frame that is a real reading this app has no column for. */
    fun isKnownButUnused(id: Int): Boolean = id == ID_RAW_RESISTANCE

    private fun fieldOf(id: Int): TelemetryField? = when (id) {
        ID_CADENCE -> TelemetryField.Cadence
        ID_POWER -> TelemetryField.Power
        ID_RESISTANCE -> TelemetryField.Resistance
        else -> null
    }

    private fun bytesOf(hex: String?): List<Int>? {
        if (hex.isNullOrBlank()) return null
        return hex.trim().split(" ").map {
            it.toIntOrNull(16) ?: return null
        }
    }

    private const val START = 0xF1
    private const val END = 0xF6
    private const val ASCII_ZERO = 0x30
    private const val ASCII_NINE = 0x39

    const val ID_CADENCE = 0x41
    const val ID_POWER = 0x44
    const val ID_RESISTANCE = 0x49

    /**
     * The raw resistance reading — the fourth value of 2.7b.
     *
     * `≈ 11.05 × resistance% + 233` across five sightings on two rides and one
     * bench capture, spanning 233 at 0% to 1338 at 100%: a potentiometer's ADC
     * range, one byte longer than the scaled reading beside it. Recognising it
     * is most of the fix, because an unrecognised extra value is exactly what
     * pushed three fields out of step with a four-value stream.
     */
    const val ID_RAW_RESISTANCE = 0x4A
}
