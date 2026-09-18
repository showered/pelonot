package com.pelonot.data.repository

import android.content.Intent
import com.pelonot.domain.update.UpdateManifest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Where [UpdateInstallCoordinator.install] has got to, for whichever dialog is showing it. */
sealed interface UpdateInstallState {
    data object Idle : UpdateInstallState
    data object Downloading : UpdateInstallState

    /** The rider has not yet granted *install unknown apps* (PLAN 30.4.2). */
    data object NeedsPermission : UpdateInstallState
    data object Installing : UpdateInstallState
    data class Failed(val message: String) : UpdateInstallState
}

/**
 * Ties [UpdateDownloader] and [UpdateInstaller] into the one state a dialog
 * needs to draw (PLAN 30.4).
 *
 * One instance, shared between the automatic prompt and Settings' own *Check
 * for updates now* — both end up wanting to download and install the same
 * way, and a second copy of this state is how two screens come to disagree
 * about whether an install is already running.
 */
class UpdateInstallCoordinator(
    private val download: suspend (UpdateManifest) -> DownloadOutcome,
    private val commit: suspend (java.io.File) -> Unit,
    private val canInstall: () -> Boolean,
    private val permissionIntent: () -> Intent,
    private val rideActive: () -> Boolean = { com.pelonot.data.service.RideInProgress.active.value != null }
) {
    private val _state = MutableStateFlow<UpdateInstallState>(UpdateInstallState.Idle)
    val state: StateFlow<UpdateInstallState> = _state

    fun unknownSourcesSettingsIntent(): Intent = permissionIntent()

    /** Called again after returning from [unknownSourcesSettingsIntent] to retry. */
    suspend fun install(manifest: UpdateManifest) {
        if (_state.value == UpdateInstallState.Downloading ||
            _state.value == UpdateInstallState.Installing) return
        if (rideActive()) {
            fail("Finish your ride before installing an update.")
            return
        }
        try {
            if (!canInstall()) {
                _state.value = UpdateInstallState.NeedsPermission
                return
            }
            _state.value = UpdateInstallState.Downloading
            when (val outcome = download(manifest)) {
                is DownloadOutcome.Success -> {
                    try {
                        // A ride may have started while the network was busy.
                        if (rideActive()) {
                            fail("Finish your ride before installing an update.")
                            return
                        }
                        _state.value = UpdateInstallState.Installing
                        commit(outcome.file)
                        // PackageInstaller owns the result; committing is not success.
                    } finally {
                        outcome.file.delete()
                    }
                }
                DownloadOutcome.Unreachable -> fail("Couldn't download that. Check the connection and try again.")
                DownloadOutcome.ChecksumMismatch -> fail("That download didn't come through whole — try again.")
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            reset()
            throw e
        } catch (e: Exception) {
            fail("Couldn't install the update. Check free storage and try again.")
        }
    }

    fun fail(message: String) {
        _state.value = UpdateInstallState.Failed(message)
    }

    fun reset() {
        _state.value = UpdateInstallState.Idle
    }
}
