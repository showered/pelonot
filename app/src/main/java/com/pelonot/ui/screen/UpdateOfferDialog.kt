package com.pelonot.ui.screen

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import com.pelonot.data.repository.UpdateInstallState
import com.pelonot.domain.update.UpdateManifest
import com.pelonot.ui.theme.spacing

/**
 * *Pelonot has an update* (PLAN 30.4).
 *
 * One dialog, shared by the automatic prompt (30.4.4) and Settings' own
 * *Check for updates now* — [installState] is the same [UpdateInstallState]
 * either way, on the argument [com.pelonot.data.repository
 * .UpdateInstallCoordinator]'s own KDoc gives for there being one instance of
 * it.
 *
 * The *install unknown apps* grant (30.4.2) cannot be requested
 * programmatically, only asked for by sending the rider to a settings screen
 * and coming back — which is what the launcher here is for. Asked at the
 * moment it is needed, on the same rule 11.6.14 settled for the overlay
 * permission: a permission asked outside the moment it is for is a permission
 * refused.
 */
@Composable
fun UpdateOfferDialog(
    manifest: UpdateManifest,
    installState: UpdateInstallState,
    onInstall: () -> Unit,
    onNotNow: () -> Unit,
    unknownSourcesSettingsIntent: () -> Intent
) {
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // The rider may have granted it, refused it, or backed out — either
        // way, asking again is what tells the three apart, and asking again
        // is exactly what a second tap of Install already does.
        onInstall()
    }

    val busy = installState is UpdateInstallState.Downloading ||
        installState is UpdateInstallState.Installing

    AlertDialog(
        onDismissRequest = { if (!busy) onNotNow() },
        title = { Text("Pelonot ${manifest.versionName} is ready") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
                Text(manifest.notes, style = MaterialTheme.typography.bodyMedium)
                when (installState) {
                    UpdateInstallState.Downloading -> Text(
                        "Downloading…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    UpdateInstallState.Installing -> Text(
                        "Installing…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    UpdateInstallState.NeedsPermission -> Text(
                        "Pelonot needs permission to install apps. Allow it, then try again.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    is UpdateInstallState.Failed -> Text(
                        installState.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    UpdateInstallState.Idle -> Unit
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (installState is UpdateInstallState.NeedsPermission) {
                        permissionLauncher.launch(unknownSourcesSettingsIntent())
                    } else {
                        onInstall()
                    }
                },
                enabled = !busy
            ) {
                Text(
                    when {
                        installState is UpdateInstallState.NeedsPermission -> "Allow"
                        busy -> "Working…"
                        else -> "Install"
                    }
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onNotNow,
                enabled = !busy,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) { Text("Not now") }
        }
    )
}
