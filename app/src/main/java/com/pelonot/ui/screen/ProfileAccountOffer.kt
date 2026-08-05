package com.pelonot.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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

    // 15.6.13. **Android back was leaving this journey entirely.** The offer is
    // inside a `Dialog`, and a dialog's own back callback dismisses it — so a
    // rider who pressed back while looking at the QR was put back on "Who's
    // riding?" with their new profile sitting there unselected, the whole way
    // to the start from one press. Back is one step, and on this screen there
    // are exactly two steps to be on.
    BackHandler {
        when (state.pairing) {
            // The two routes, one step back from the code. `cancelPairing`
            // rather than a state reset, because the bike has told the server a
            // code exists and a rider walking away from it should take it with
            // them.
            PairingState.Starting, is PairingState.Waiting,
            PairingState.Expired, is PairingState.Failed -> viewModel.cancelPairing()

            // A handover is in hand and the session is being attached. Back does
            // nothing for the second this takes: dropping out mid-adoption is
            // the one outcome here worse than a press that appears to be
            // ignored.
            PairingState.Completing -> Unit

            // Nothing to step back to — going back from the offer would re-ask a
            // question about a profile that already exists (15.8.1). So back is
            // the answer this app ships as its default, which is the one drawn
            // at the foot of the screen: *Not now*.
            PairingState.Idle -> onDone()
        }
    }

    // The offer can leave without anybody pressing anything — `onDone` above,
    // the process going away, a rider walking off. This view model outlives it
    // either way, so the code goes when the screen does. See
    // `AccountViewModel.abandonPairing` for what it cost not to.
    DisposableEffect(Unit) {
        onDispose { viewModel.abandonPairing() }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
        // 20.4.4. **The whole offer stacked in a 640 dp column on a 1280 dp
        // panel, and it did not fit.** Measured on the tablet AVD: the sign-in
        // password field ended at y 890 and *Not now* — the answer this app
        // ships as its default — was off the bottom of the screen, with the
        // only visible control at the foot of the step being a *Back* that does
        // nothing by construction (`Step.Account.previous()` returns itself).
        //
        // So a rider who had just made their first profile was looking at a
        // screen whose only apparent way onward was signing in. That is worse
        // than either fault the owner reported, and it is the same cause as
        // 22.4: a set of things a rider is *choosing between* wants the width,
        // not a reading column. The two routes go side by side and the offer
        // fits with room under it.
        modifier = Modifier.widthIn(max = OFFER_WIDTH)
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

                Row(
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraLarge),
                    // Top, not centre: the two routes are different heights and
                    // a short one floating in the middle of a tall one reads as
                    // a mistake rather than as a pair.
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // `Column`, not `Box`, and the difference is not cosmetic:
                    // [SignInForm] emits a *sequence* of siblings for a
                    // `ColumnScope` — the take-over warning, the mode tabs, two
                    // fields, the submit — so a `Box` stacks all five on top of
                    // one another. Seen on the AVD as a single illegible smear
                    // where the form should be.
                    if (state.pairingAvailable) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(
                                MaterialTheme.spacing.medium
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            ScanToSignIn(
                                onStart = { viewModel.startPairing(onSignedIn = onDone) }
                            )
                        }
                    }
                    Column(
                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
                        modifier = Modifier.weight(1f)
                    ) {
                        SignInForm(state, viewModel, onDone = onDone)
                    }
                }

                Spacer(Modifier.height(MaterialTheme.spacing.large))

                // Same weight as the buttons above it, not a grey link
                // underneath them — declining is not a failure to finish
                // (15.8.1). It only *is* first-class if a rider can see it,
                // which is what 20.4.4 above restores.
                OutlinedButton(onClick = onDone, modifier = Modifier.widthIn(min = 200.dp)) {
                    Text("Not now")
                }
            }
        }
    }
}

/**
 * How wide the two routes are allowed to run together (20.4.4).
 *
 * Not `readableWidth`: this is a set of things being *chosen between* rather
 * than prose being read, which is 22.4's distinction and the reason the stacked
 * 640 dp version overflowed a panel with 1280 dp of room on it.
 */
private val OFFER_WIDTH = 1040.dp
