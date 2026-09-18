package com.pelonot.data.repository

import android.content.Context
import android.util.Log
import com.pelonot.domain.update.UpdateManifest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * What came of trying to fetch the APK a manifest pointed at (PLAN 30.4.3).
 */
sealed interface DownloadOutcome {
    /** [file] is in the cache dir, and its SHA-256 already matched. */
    data class Success(val file: File) : DownloadOutcome

    /** No network, a 404, a captive portal — the same nothing-happened shape as [UpdateCheck.Unreachable]. */
    data object Unreachable : DownloadOutcome

    /**
     * The bytes arrived but the hash does not match the manifest's.
     *
     * A bike on household wifi truncating a 24 MB download is the likely way
     * this happens, not tampering — the platform's own signature check is what
     * actually makes the channel safe (30.1). This turns a truncated file into
     * something a rider can be told to simply try again.
     */
    data object ChecksumMismatch : DownloadOutcome
}

/**
 * Downloads the APK a manifest names and checks it against the hash the
 * manifest carries (PLAN 30.4.3).
 *
 * Streamed and hashed in the same pass rather than hashed after, so a 24 MB
 * file is never held twice over in memory. Written on `HttpURLConnection` for
 * the same reason `UpdateRepository` is: nothing about the update channel
 * should share code with anything cloud-shaped.
 */
class UpdateDownloader(private val context: Context) {

    suspend fun download(manifest: UpdateManifest): DownloadOutcome = withContext(Dispatchers.IO) {
        val target = File(context.cacheDir, TARGET_FILE_NAME)
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(manifest.url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                instanceFollowRedirects = true
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext DownloadOutcome.Unreachable
            }

            val digest = MessageDigest.getInstance("SHA-256")
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(BUFFER_BYTES)
                    var total = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        require(total <= MAX_APK_BYTES) { "Update is too large" }
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                    }
                }
            }

            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            if (!hash.equals(manifest.sha256, ignoreCase = true)) {
                target.delete()
                return@withContext DownloadOutcome.ChecksumMismatch
            }
            DownloadOutcome.Success(target)
        } catch (e: kotlinx.coroutines.CancellationException) {
            target.delete()
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "Update download failed: ${e.message}")
            target.delete()
            DownloadOutcome.Unreachable
        } finally {
            connection?.disconnect()
        }
    }

    private companion object {
        const val TAG = "PelonotUpdate"
        const val TARGET_FILE_NAME = "pelonot-update.apk"
        const val TIMEOUT_MS = 15_000
        const val MAX_APK_BYTES = 100L * 1024 * 1024
        const val BUFFER_BYTES = 8192
    }
}
