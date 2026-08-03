package com.pelonot.domain.cloud

/**
 * Signing in by showing a code (PLAN 15.6).
 *
 * The states a pairing can be in, kept here and pure so the screen's logic —
 * which is mostly about *when to stop* — can be argued with in a test rather
 * than on a bike with a phone in the other hand.
 */
sealed interface PairingState {

    /** Nothing asked for yet. The rider is looking at the ordinary form. */
    data object Idle : PairingState

    /** Asking the server for a code. */
    data object Starting : PairingState

    /**
     * A code on screen, and a clock running.
     *
     * @param code the eight characters, unformatted. [formattedCode] is what a
     *   rider reads.
     * @param url what the QR encodes — the pairing page with the code in its
     *   **fragment**, so it never reaches a web server's access log.
     * @param expiresAtMs when the server will stop honouring it.
     */
    data class Waiting(
        val code: String,
        val url: String,
        val expiresAtMs: Long
    ) : PairingState {

        /** `ABCD 2345` — two groups, because eight characters in a row is a blur. */
        val formattedCode: String
            get() = if (code.length == CODE_LENGTH) {
                "${code.take(4)} ${code.drop(4)}"
            } else {
                code
            }

        fun secondsLeft(nowMs: Long): Int =
            ((expiresAtMs - nowMs) / 1_000L).coerceAtLeast(0L).toInt()

        fun hasExpired(nowMs: Long): Boolean = nowMs >= expiresAtMs
    }

    /** The phone has handed over and the bike is signing itself in. */
    data object Completing : PairingState

    /**
     * The five minutes ran out.
     *
     * Its own state rather than a return to [Idle], because a code that has
     * quietly stopped working while the rider was fetching their phone is the
     * single most confusing thing this flow can do. It offers a new one.
     */
    data object Expired : PairingState

    data class Failed(val message: String) : PairingState

    companion object {
        const val CODE_LENGTH = 8
    }
}

/**
 * What the phone handed over.
 *
 * Two shapes, and the app understands both because the good one needs an Edge
 * Function deployed and the fallback does not (15.6.4, 15.6.9). Keeping the
 * fallback representable is what stops a deployment without that function from
 * being a deployment without QR sign-in.
 */
sealed interface PairingHandover {

    /**
     * A one-time code minted for this rider by the server, which the bike
     * exchanges for **a session of its own**.
     *
     * The preferred shape, and the reason is refresh-token rotation: two
     * devices sharing one token family invalidate each other, and a detected
     * reuse can revoke both.
     */
    data class OneTimeCode(val email: String, val otp: String) : PairingHandover

    /**
     * The phone's own refresh token (15.6.9).
     *
     * Works with nothing deployed, and costs the phone its session — which the
     * web page says out loud before it does it, and then acts on by signing
     * itself out rather than racing the bike for the token.
     */
    data class RefreshToken(val token: String) : PairingHandover
}
