package com.pelonot.data.remote

import android.os.Build
import android.util.Log
import com.pelonot.BuildConfig
import com.pelonot.domain.cloud.AuthAttempt
import com.pelonot.domain.cloud.PairingHandover
import com.pelonot.domain.cloud.PairingState
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * The bike's half of signing in by scanning a code (PLAN 15.6).
 *
 * Three calls, in order: ask for a code, show it, poll until the phone has
 * handed something over. The server side is `supabase/004_device_link.sql`, and
 * the reasoning for the shape lives there.
 *
 * **The bike never types a credential in this flow, and never holds one.** What
 * it holds is a random device secret it invented, which is the thing that makes
 * a code safe to display: the server stores only the secret's SHA-256, so a
 * code photographed off the bike's screen from across the room collects
 * nothing. Everything else — the email, the password, the password manager — is
 * on a phone.
 *
 * Like [AuthRepository], this is a cloud call made before there is an account,
 * and for the same reason it does not break rule 1 of the connectivity model:
 * nothing here runs except from a rider pressing a button that says what it
 * does.
 */
class DeviceLinkRepository(
    private val authRepository: AuthRepository,
    private val deviceLabel: () -> String = { defaultDeviceLabel() },
    private val webUrl: () -> String = { BuildConfig.PELONOT_WEB_URL }
) {

    private val client get() = SupabaseModule.client

    /** The secret for the pairing currently on screen. Never leaves this app. */
    private var deviceSecret: String? = null

    /** Whether to offer the QR at all (15.6.8). */
    val pairingAvailable: Boolean
        get() = SupabaseModule.isConfigured && webUrl().isNotBlank()

    /**
     * Asks the server for a pairing code.
     *
     * The secret is minted here, 32 bytes from [SecureRandom], and only its
     * digest is sent. The server cannot hand the session to anything but the
     * device that started the pairing, and it cannot do so twice.
     */
    suspend fun begin(): PairingState {
        val supabase = client ?: return PairingState.Failed("This build has no cloud")
        val target = webUrl()
        if (target.isBlank()) {
            return PairingState.Failed("This build has no pairing page to point at")
        }

        val secret = randomSecret()
        return try {
            val result = supabase.postgrest.rpc(
                function = "device_link_begin",
                parameters = buildJsonObject {
                    put("p_secret_hash", sha256Hex(secret))
                    put("p_label", deviceLabel())
                }
            ).data.let { SupabaseModule.json.parseToJsonElement(it).jsonObject }

            val code = result["code"]?.jsonPrimitive?.content
                ?: return PairingState.Failed("The server did not send a code back")

            deviceSecret = secret

            PairingState.Waiting(
                code = code,
                // The code rides in the **fragment**: a fragment is never sent
                // to a server, so it stays out of access logs and referrers.
                url = "${target.trimEnd('/')}/link.html#$code",
                // Trusting our own clock for the countdown rather than parsing
                // the server's timestamp. The two can disagree by minutes on a
                // tablet whose time is wrong, and the honest failure is the
                // bike giving up slightly early — never slightly late, which
                // would leave a rider staring at a code the server has already
                // forgotten.
                expiresAtMs = System.currentTimeMillis() + TTL_MS
            )
        } catch (e: Exception) {
            Log.w(TAG, "device_link_begin failed", e)
            PairingState.Failed(e.message ?: "Could not ask for a code")
        }
    }

    /**
     * Has the phone handed anything over yet?
     *
     * Returns null while the answer is "not yet", which is the overwhelmingly
     * common case and is not news. A collected hand-off deletes its row on the
     * server, so this can succeed exactly once.
     */
    suspend fun poll(code: String): PairingHandover? {
        val supabase = client ?: return null
        val secret = deviceSecret ?: return null

        return try {
            val result = supabase.postgrest.rpc(
                function = "device_link_poll",
                parameters = buildJsonObject {
                    put("p_code", code)
                    put("p_secret", secret)
                }
            ).data.let { SupabaseModule.json.parseToJsonElement(it).jsonObject }

            when (result["status"]?.jsonPrimitive?.content) {
                "linked" -> readHandover(result["payload"]?.jsonObject)
                else -> null
            }
        } catch (e: Exception) {
            // A failed poll is not a failed pairing: this runs every two
            // seconds and a dropped request is ordinary. The countdown is what
            // ends this flow, not one bad response.
            Log.w(TAG, "device_link_poll failed", e)
            null
        }
    }

    /**
     * Turns what the phone handed over into a session on this tablet.
     *
     * The two shapes are handled here rather than by the caller because the
     * caller's job — attaching the account to a profile — is identical either
     * way, and a screen that had to know which kind of hand-off it received
     * would be a screen that could get it wrong.
     */
    suspend fun adopt(handover: PairingHandover): AuthAttempt {
        val supabase = client ?: return AuthAttempt.Disabled
        return try {
            when (handover) {
                is PairingHandover.OneTimeCode -> supabase.auth.verifyEmailOtp(
                    type = OtpType.Email.MAGIC_LINK,
                    email = handover.email,
                    token = handover.otp
                )

                is PairingHandover.RefreshToken -> {
                    val session = supabase.auth.refreshSession(handover.token)
                    supabase.auth.importSession(session)
                }
            }
            val id = authRepository.currentAccountId()
                ?: return AuthAttempt.Failed("The bike was handed a session it could not use")
            AuthAttempt.Success(id, authRepository.currentEmail())
        } catch (e: Exception) {
            Log.w(TAG, "adopting the handover failed", e)
            AuthAttempt.Failed(e.message ?: "That sign-in could not be completed")
        }
    }

    /** Forgets the secret, so a stale poll cannot collect anything later. */
    fun forget() {
        deviceSecret = null
    }

    private fun readHandover(payload: JsonObject?): PairingHandover? {
        payload ?: return null
        return when (payload["kind"]?.jsonPrimitive?.content) {
            "otp" -> {
                val email = payload["email"]?.jsonPrimitive?.content
                val otp = payload["otp"]?.jsonPrimitive?.content
                if (email != null && otp != null) {
                    PairingHandover.OneTimeCode(email, otp)
                } else {
                    null
                }
            }

            "refresh" ->
                payload["token"]?.jsonPrimitive?.content?.let(PairingHandover::RefreshToken)

            // An unrecognised kind is a newer web app talking to an older bike.
            // Nothing sensible can be done with it and guessing would be worse.
            else -> null
        }
    }

    private fun randomSecret(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun sha256Hex(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    companion object {
        private const val TAG = "PelonotDeviceLink"

        /** Matches the server's own TTL in `004_device_link.sql`. */
        const val TTL_MS = 5 * 60 * 1000L

        /** How often the bike asks. Two seconds is a human interval, not a spin. */
        const val POLL_INTERVAL_MS = 2_000L

        /**
         * What the rider sees named on their phone (15.6.5).
         *
         * The device's own model name, which on the bike's tablet is
         * `PLTN-RB1VQ` — recognisably *the bike* to somebody standing in front
         * of it, and meaningless to anybody else, which is the right balance
         * for a string that appears on a web page.
         */
        private fun defaultDeviceLabel(): String =
            listOf(Build.MODEL, Build.DEVICE)
                .firstOrNull { !it.isNullOrBlank() }
                ?: "a Pelonot bike"
    }
}
