package com.pelonot.data.remote.dto

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonPrimitive

/**
 * Reads `intervals_json` whichever of its two shapes turns up.
 *
 * The bundled assets hold it as an **escaped JSON string**
 * (`"intervals_json": "[{\"time_start_sec\":0,…}]"`). The cloud column is
 * **`JSONB` holding the array itself** (`"intervals_json": [{…}]`). A DTO field
 * typed `String` reads the first and throws on the second:
 *
 * ```
 * JsonDecodingException: Expected beginning of the string, but got [
 *   at path: $[0].intervals_json
 * ```
 *
 * That exception is caught in `SupabaseSyncRepository` and turned into
 * `SyncOutcome.Failed`, which `ClassTemplateSeeder` treats as "cloud
 * unavailable" and falls back to assets. So the failure was completely silent
 * and its only symptom was a **class library with 5 classes in it instead of
 * 72** — the cloud's 72 seeded templates had never once been read.
 *
 * The app treats this field as an opaque string it hands to `IntervalParser`,
 * so accepting both shapes and always yielding the string form is what it
 * actually means. PLAN 14.2.2 still has to settle the column type on both
 * sides; this makes the app correct either way in the meantime, and correct
 * against anyone else's project whichever way they set theirs up.
 */
object IntervalsJsonSerializer : KSerializer<String> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("intervals_json", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String {
        // Not JSON at all (a hypothetical CBOR or protobuf backend): there is
        // no element to inspect, so take the primitive.
        val input = decoder as? JsonDecoder ?: return decoder.decodeString()

        return when (val element = input.decodeJsonElement()) {
            // `content` rather than `toString()`: the latter re-adds the quotes
            // and the escaping, and IntervalParser would then be handed a JSON
            // string literal where it expects an array.
            is JsonPrimitive -> if (element.isString) element.content else element.toString()
            else -> element.toString()
        }
    }

    override fun serialize(encoder: Encoder, value: String) {
        val output = encoder as? JsonEncoder ?: return encoder.encodeString(value)
        // Written back as an array where it parses as one, so a value read from
        // a JSONB column round-trips into a JSONB column unchanged. Nothing
        // uploads class templates today, but writing the string form into JSONB
        // would quietly store a quoted blob that every later read would break on.
        val element = runCatching { Json.parseToJsonElement(value) }.getOrNull()
        if (element is JsonArray) output.encodeJsonElement(element) else output.encodeString(value)
    }
}
