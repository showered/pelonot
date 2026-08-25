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
    private val downloader: UpdateDownloader,
    private val installer: UpdateInstaller
) {
    private val _state = MutableStateFlow<UpdateInstallState>(UpdateInstallState.Idle)
    val state: StateFlow<UpdateInstallState> = _state

    fun unknownSourcesSettingsIntent(): Intent = installer.unknownSourcesSettingsIntent()

    /** Called again after returning from [unknownSourcesSettingsIntent] to retry. */
    suspend fun install(manifest: UpdateManifest) {
        if (!installer.canInstall()) {
            _state.value = UpdateInstallState.NeedsPermission
            return
        }
        _state.value = UpdateInstallState.Downloading
        when (val outcome = downloader.download(manifest)) {
            is DownloadOutcome.Success -> {
                _state.value = UpdateInstallState.Installing
                installer.install(outcome.file)
                _state.value = UpdateInstallState.Idle
            }
            DownloadOutcome.Unreachable -> _state.value =
                UpdateInstallState.Failed("Couldn't download that. Check the connection and try again.")
            DownloadOutcome.ChecksumMismatch -> _state.value =
                UpdateInstallState.Failed("That download didn't come through whole — try again.")
        }
    }

    fun reset() {
        _state.value = UpdateInstallState.Idle
    }
}
