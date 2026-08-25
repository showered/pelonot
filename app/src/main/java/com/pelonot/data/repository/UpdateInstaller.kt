package com.pelonot.data.repository

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import java.io.File

/**
 * Commits a downloaded APK through `PackageInstaller`'s session API (PLAN
 * 30.4).
 *
 * Deliberately the session API rather than the older `ACTION_INSTALL_PACKAGE`
 * intent: that one needs the file to exist somewhere a second process can
 * read, which on Android 11's scoped storage means a `FileProvider` and a
 * grant, and a session takes a plain `OutputStream` instead. The commit
 * itself is asynchronous — the system raises a confirmation UI through a
 * `PendingIntent`, which [UpdateInstallReceiver] forwards.
 */
class UpdateInstaller(private val context: Context) {

    /**
     * Whether the one-off *install unknown apps* grant has already been given
     * (PLAN 30.4.2). Cannot be requested programmatically — only asked for, by
     * sending the rider to [unknownSourcesSettingsIntent].
     */
    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    /** The settings screen that grants it, scoped to this app's package. */
    fun unknownSourcesSettingsIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))

    fun install(apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            apk.inputStream().use { input ->
                session.openWrite(SESSION_NAME, 0, apk.length()).use { out ->
                    input.copyTo(out)
                    session.fsync(out)
                }
            }

            val receiverIntent = Intent(context, UpdateInstallReceiver::class.java)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PendingIntent.FLAG_MUTABLE
                } else {
                    0
                }
            val pendingIntent = PendingIntent.getBroadcast(context, sessionId, receiverIntent, flags)
            session.commit(pendingIntent.intentSender)
        }
    }

    private companion object {
        const val SESSION_NAME = "pelonot-update"
    }
}
