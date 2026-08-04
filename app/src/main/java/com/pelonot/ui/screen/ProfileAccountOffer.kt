package com.pelonot.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pelonot.domain.cloud.PairingState
import com.pelonot.ui.theme.readableText
import com.pelonot.ui.theme.spacing
import com.pelonot.ui.viewmodel.AccountViewModel

/**
 * *"Back this up? Scan this with your phone."* — the offer at profile
 * creation, after the profile already exists (PLAN 15.8.1).
 *
 * **Never shown before the local profile is written.** [ProfileCreationScreen]
 * persists the profile the moment the rider leaves the result step, whether
 * or not this offer follows, so a rider who walks away mid-offer — closes the
 * app, force-stops it, anything — still has a rideable bike with a real
 * profile on it. That is what makes the offer safe to interrupt.
 *
 * **Linking is automatic (15.8.3).** This composable never asks which profile
 * it is signing in — it reuses [AccountViewModel], which already scopes
 * itself to whichever profile `SettingsRepository.lastProfileId` names, and
 * `ProfileCreationScreen` sets that to the profile just created before this
 * step ever composes. The same mechanism [AccountScreen] uses from Settings,
 * not a second copy of it.
 *
 * **Skip is a first-class answer** (15.8.1), sized and placed the same as the
 * button that starts pairing — offline is the mode this app ships in, not a
 * failure to finish signing up.
 */
@Composable
fun ProfileAccountOfferStep(
    onDone: () -> Unit,
    viewModel: AccountViewModel = viewModel(factory = AccountViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Guards rather than expected paths: NavGraph only passes this step in at
    // all when the build has a cloud (15.8.7), and a brand-new profile is
    // never already signed in. Both are here so a future caller that gets
    // either wrong fails safe — straight through — rather than stranding the
    // rider on a screen with nothing to do.
    if (!state.cloudConfigured || state.signedInAsThisProfile) {
        LaunchedEffect(Unit) { onDone() }
        return
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
        modifier = Modifier.widthIn(max = 640.dp)
    ) {
        when {
            state.pairing != PairingState.Idle -> PairingSection(
                state = state,
                onCancel = viewModel::cancelPairing,
                onRetry = { viewModel.startPairing(onSignedIn = onDone) }
            )

            else -> {
                Text(
                    // 15.8.6: the cost, in one line, at the moment of asking.
                    text = "Your rides get copied to your account. Everything keeps " +
                        "working without one.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.readableText()
                )

                Spacer(Modifier.height(MaterialTheme.spacing.large))

                if (state.pairingAvailable) {
                    ScanToSignIn(onStart = { viewModel.startPairing(onSignedIn = onDone) })
                }

                Spacer(Modifier.height(MaterialTheme.spacing.medium))
                SignInForm(state, viewModel, onDone = onDone)

                Spacer(Modifier.height(MaterialTheme.spacing.large))

                // Same weight as the buttons above it, not a grey link
                // underneath them — declining is not a failure to finish.
                OutlinedButton(onClick = onDone, modifier = Modifier.widthIn(min = 200.dp)) {
                    Text("Not now")
                }
            }
        }
    }
}
