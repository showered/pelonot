package com.pelonot.domain.cloud

/**
 * Whether a cloud failure is worth trying again (PLAN 14.2.7).
 *
 * Pure, and separated from the SDK so it can be asked questions in a test
 * rather than against a live endpoint. The whole of it is one distinction that
 * the app did not draw for its entire history, and the day it first mattered it
 * cost five rides:
 *
 * - **Transient** — the network is gone, the endpoint is briefly unwell, a
 *   timeout. Stop the batch, keep the backlog, try later. Nothing is lost.
 * - **Permanent** — the cloud understood the row and refused it, and will
 *   refuse it identically next week. Retrying is a radio wakeup for the same
 *   answer, and worse, **stopping the batch means every ride behind it never
 *   goes up either**.
 */
object SyncFailure {

    /**
     * @param statusCode the HTTP status the endpoint answered, or null when the
     *   request never got one (which is itself the clearest transient signal
     *   there is — nothing answered at all).
     */
    fun isPermanent(statusCode: Int?): Boolean = when (statusCode) {
        null -> false

        // 401/403 — the session expired or the policy said no. Both look
        // permanent for this request and are not: refreshing a token or signing
        // in fixes them, and 003's policies refuse a row only while the gate is
        // out of step with the session (15.2.8), which is a state that ends.
        401, 403 -> false

        // 408, 429 and every 5xx are the endpoint asking to be left alone.
        408, 429 -> false

        // The row is wrong, and it will be just as wrong tomorrow: a malformed
        // uuid, a column the schema does not have, a payload too large, a
        // constraint it violates.
        in 400..499 -> true

        else -> false
    }

    /**
     * The first useful line of an SDK error, for a rider to read.
     *
     * The raw message is a Ktor exception carrying the full request URL and the
     * header list, which on the tablet rendered as **eight lines of red
     * including `columns=id%2Cuser_id%2Cduration_sec…`**. That is not a
     * sentence anybody can act on, and it pushed the button that fixes the
     * problem off the bottom of the card.
     *
     * It keeps the first line rather than replacing the message wholesale,
     * because an unrecognised failure with its own words is how the next defect
     * gets diagnosed — the same rule as `AuthRepository.riderFacing`.
     */
    fun riderFacing(message: String?): String {
        val first = message
            ?.lineSequence()
            ?.map { it.trim() }
            ?.firstOrNull { it.isNotEmpty() }
            ?: return "The cloud refused it and did not say why"

        // Ktor puts the URL on the first line for some failures. Cut at the
        // marker rather than at a length, so a short honest message survives
        // intact and a long one loses only the part nobody reads.
        val trimmed = first.substringBefore(" URL:").substringBefore(", URL:").trim()
        return if (trimmed.length > MAX_LENGTH) trimmed.take(MAX_LENGTH).trimEnd() + "…" else trimmed
    }

    private const val MAX_LENGTH = 160
}
