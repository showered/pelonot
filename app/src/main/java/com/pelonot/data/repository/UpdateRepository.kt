package com.pelonot.data.repository

import android.util.Log
import com.pelonot.BuildConfig
import com.pelonot.domain.update.UpdateDecision
import com.pelonot.domain.update.UpdateManifest
import com.pelonot.domain.update.UpdatePolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

/**
 * What came of asking (PLAN 30.3).
 *
 * Every case is named, including the three that mean *nothing happened*, for
 * the reason `SyncOutcome.Disabled` exists: a caller writing a Settings screen
 * has to be able to tell *"you turned this off"* from *"the internet did not
 * answer"*, and a nullable cannot say which.
 */
sealed interface UpdateCheck {
    /** The manifest was read and [UpdatePolicy] has an answer. */
    data class Decided(val decision: UpdateDecision) : UpdateCheck

    /** The rider turned update checks off, so **no request was made**. */
    data object Disabled : UpdateCheck

    /** Checked within the last day already (30.3.3). No request was made. */
    data object NotDue : UpdateCheck

    /** This build has no web URL configured, so there is nowhere to ask. */
    data object NotConfigured : UpdateCheck

    /**
     * No network, a 404, a captive portal, a malformed file.
     *
     * **Not an error.** 30.3.4: the offline tier is the mode, and its failures
     * are not things to tell a rider about. The one place this may be visible
     * is a manual *Check for updates*, where they asked.
     */
    data object Unreachable : UpdateCheck
}

/**
 * Asks the companion web app what the newest version is (PLAN 30.3, 30.5.1).
 *
 * **Nothing about the rider goes on the wire, and that is an invariant rather
 * than a description** — no id, no name, no profile count, no ride, no
 * `User-Agent` beyond the platform's own. It is a plain GET of a static file,
 * and it is deliberately written with `HttpURLConnection` rather than the ktor
 * client the Supabase SDK drags in, so that this path shares nothing at all
 * with anything cloud-shaped and can be checked in one screenful.
 *
 * It is **not** routed through `CloudAccess`, and must not be: the question is
 * about the *app* rather than the *rider*, and a rider with no account is
 * precisely the one who has no other way to be offered an update (30.5.1).
 */
class UpdateRepository(
    private val settings: SettingsRepository,
    private val installedVersionCode: Int = BuildConfig.VERSION_CODE,
    private val baseUrl: String = BuildConfig.PELONOT_WEB_URL
) {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * The once-a-day check, called when the app is opened and nowhere else.
     *
     * @param force skips the interval and the switch is still obeyed — this is
     *   a rider pressing *Check for updates*, not a shortcut past 30.5.1.
     */
    suspend fun check(nowMs: Long = System.currentTimeMillis(), force: Boolean = false): UpdateCheck =
        check(settings.settings.first(), nowMs, force)

    /** The same decision against settings the caller already has. */
    suspend fun check(
        current: AppSettings,
        nowMs: Long,
        force: Boolean
    ): UpdateCheck {
        if (!current.updateChecksEnabled) return UpdateCheck.Disabled
        if (!force && !UpdatePolicy.isCheckDue(nowMs, current.lastUpdateCheckAtMs, enabled = true)) {
            return UpdateCheck.NotDue
        }
        if (baseUrl.isBlank()) return UpdateCheck.NotConfigured

        val manifest = fetch() ?: return UpdateCheck.Unreachable
        // Marked only when the request actually completed, so a fortnight with
        // no wifi does not silently consume the daily allowance every launch.
        settings.markUpdateCheckedAt(nowMs)
        return UpdateCheck.Decided(
            UpdatePolicy.decide(installedVersionCode, manifest, if (force) null else current.declinedUpdateVersionCode)
        )
    }

    private suspend fun fetch(): UpdateManifest? = withContext(Dispatchers.IO) {
        val url = baseUrl.trimEnd('/') + MANIFEST_PATH
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                instanceFollowRedirects = true
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext null
            // Capped, because a captive portal answers every request with a
            // login page and this must not read a megabyte of it into memory.
            val body = connection.inputStream.bufferedReader()
                .use { com.pelonot.domain.update.readBoundedManifest(it, MAX_MANIFEST_CHARS) }
            json.decodeFromString<UpdateManifest>(body)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            // Every failure here is "no update today" (30.3.4). Logged at debug
            // because a rider must never see it and a session sometimes must.
            Log.d(TAG, "Update check could not read $url: ${e.message}")
            null
        } finally {
            connection?.disconnect()
        }
    }

    private companion object {
        const val TAG = "PelonotUpdate"
        const val MANIFEST_PATH = "/update.json"
        const val TIMEOUT_MS = 8_000
        const val MAX_MANIFEST_CHARS = 8_192
    }
}
