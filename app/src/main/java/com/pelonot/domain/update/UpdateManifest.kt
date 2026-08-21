package com.pelonot.domain.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * What the bike is told when it asks *what is the newest version* (PLAN 30.3.1).
 *
 * One small JSON file, served from the same origin the app already carries in
 * `BuildConfig.PELONOT_WEB_URL`, so an update channel costs **no new
 * configuration value and no new secret** — the URL is already printed on the
 * bike's own screen as half of a QR code (17.14).
 *
 * Snake_case on the wire, like `intervals_json` and for the same reason: it is
 * written by a tool and read by a person, and the two have to agree exactly.
 * `@SerialName` does the matching so the Kotlin side can read normally.
 *
 * [notes] is **one sentence for a rider**, not a changelog. It is the only part
 * of this file anybody sees, and it appears in a dialog beside *Install* — so
 * *"The distance a ride records is much closer to right"* rather than eleven
 * bullet points nobody reads before tapping the button they were going to tap.
 */
@Serializable
data class UpdateManifest(
    /** Compared against `BuildConfig.VERSION_CODE`. The machine's number. */
    @SerialName("version_code") val versionCode: Int,

    /** What Settings and the dialog show. The person's number. */
    @SerialName("version_name") val versionName: String,

    /** One sentence a rider can act on. */
    @SerialName("notes") val notes: String,

    /** Where the APK is. GitHub Releases, in practice — see 30.3. */
    @SerialName("url") val url: String,

    /**
     * The APK's SHA-256, lowercase hex.
     *
     * This does **not** make the channel safe — the platform's signature check
     * is what does that, and it is the whole point of 30.1. What it catches is
     * a truncated or corrupted download, which on a bike's household wifi is
     * the failure that will actually happen, and it turns that into a retry
     * rather than a failed install (30.4.3).
     */
    @SerialName("sha256") val sha256: String
)
