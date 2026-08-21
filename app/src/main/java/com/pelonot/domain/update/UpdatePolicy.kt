package com.pelonot.domain.update

/**
 * What to do with a manifest once it has been fetched (PLAN 30.2.3, 30.4.5).
 *
 * Every case is named rather than collapsed into a nullable, for the same
 * reason `PowerProvenance` has an `Unknown` and `heartRateBpm` is nullable:
 * *absent* is a claim, and it is a different claim from *no*. A caller that
 * cannot tell "you are up to date" from "the manifest is malformed" cannot
 * write an honest Settings screen either.
 */
sealed interface UpdateDecision {
    /** There is a newer version and the rider has not already refused it. */
    data class Offer(val manifest: UpdateManifest) : UpdateDecision

    /** The newest version is the one already installed. */
    data object UpToDate : UpdateDecision

    /**
     * The rider said no to this version and has not been offered a newer one.
     *
     * Kept apart from [UpToDate] because a manual *Check for updates* should be
     * able to say *"you turned this one down"* rather than pretending nothing
     * exists — 30.4.5 stops the **nagging**, not the truth.
     */
    data class Declined(val manifest: UpdateManifest) : UpdateDecision

    /** The manifest cannot be trusted, and [reason] says why. */
    data class Rejected(val reason: Reason) : UpdateDecision {
        enum class Reason {
            /**
             * The manifest offers a version older than the installed one.
             *
             * **This is the dangerous case, not a curiosity.** 12.5.1 left
             * `fallbackToDestructiveMigration` in place on *downgrade*, on the
             * argument that a downgrade only ever happens on a development
             * device with an old APK in a folder. An update channel able to
             * offer an older build makes that argument false, and the rider
             * would lose their whole database through a dialog they tapped
             * *yes* on.
             */
            Downgrade,

            /** The download is not over HTTPS. */
            InsecureUrl,

            /** The checksum is not 64 lowercase hex characters. */
            MalformedChecksum
        }
    }
}

/**
 * The decision, made without a network, a clock or a `Context` (PLAN 30.3).
 *
 * Pure by convention — this is the same rule that keeps `PowerModel` and
 * `PostWorkoutAnalyzer` JVM-testable, and it matters more here than usual
 * because the thing being decided is *whether to replace the running app*.
 */
object UpdatePolicy {

    private val CHECKSUM = Regex("^[0-9a-f]{64}$")

    /** How long between automatic checks. Once a day is what 30.3.3 asks for. */
    const val CHECK_INTERVAL_MS: Long = 24L * 60 * 60 * 1000

    /**
     * @param installedVersionCode `BuildConfig.VERSION_CODE`.
     * @param declinedVersionCode the newest version the rider has said no to,
     *   or null if they never have.
     */
    fun decide(
        installedVersionCode: Int,
        manifest: UpdateManifest,
        declinedVersionCode: Int? = null
    ): UpdateDecision {
        if (!manifest.url.startsWith("https://")) {
            return UpdateDecision.Rejected(UpdateDecision.Rejected.Reason.InsecureUrl)
        }
        if (!CHECKSUM.matches(manifest.sha256)) {
            return UpdateDecision.Rejected(UpdateDecision.Rejected.Reason.MalformedChecksum)
        }
        // Equal is up to date; only strictly older is the downgrade that 12.5.1
        // makes dangerous, so the two are separated rather than both refused.
        if (manifest.versionCode < installedVersionCode) {
            return UpdateDecision.Rejected(UpdateDecision.Rejected.Reason.Downgrade)
        }
        if (manifest.versionCode == installedVersionCode) return UpdateDecision.UpToDate
        if (declinedVersionCode != null && manifest.versionCode <= declinedVersionCode) {
            return UpdateDecision.Declined(manifest)
        }
        return UpdateDecision.Offer(manifest)
    }

    /**
     * Whether an automatic check is due (PLAN 30.3.3).
     *
     * The note asks for *"when he opens the app"* and that is the whole
     * requirement, so there is no `WorkManager`, no polling and no background
     * work of any kind — one call at launch, and at most one request a day.
     *
     * @param lastCheckMs when the last check *completed*, or null if never.
     */
    fun isCheckDue(nowMs: Long, lastCheckMs: Long?, enabled: Boolean): Boolean {
        if (!enabled) return false
        if (lastCheckMs == null) return true
        // A clock that has gone backwards — a tablet correcting itself off the
        // network, which this one does — must not lock a bike out of updates
        // until the stored timestamp comes round again.
        if (nowMs < lastCheckMs) return true
        return nowMs - lastCheckMs >= CHECK_INTERVAL_MS
    }
}
