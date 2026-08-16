package com.pelonot.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pelonot.domain.cloud.AccountState
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

    // 15.6.11. **The bike has to say the link worked, on the bike.** Set by
    // whichever route succeeded — the code or the typed password, both of which
    // reach `onSignedIn` only from `AuthAttempt.Success` — and it is what turns
    // the guard below off, because the moment a session lands
    // `signedInAsThisProfile` becomes true and the guard would otherwise sweep
    // the rider onward before the bike had said anything at all.
    var linked by remember { mutableStateOf(false) }

    // Guards rather than expected paths: NavGraph only passes this step in at
    // all when the build has a cloud (15.8.7), and a brand-new profile is
    // never already signed in. Both are here so a future caller that gets
    // either wrong fails safe — straight through — rather than stranding the
    // rider on a screen with nothing to do.
    if (!state.cloudConfigured || (state.signedInAsThisProfile && !linked)) {
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
        when {
            // A handover is in hand and the session is being attached. Back does
            // nothing for the second this takes: dropping out mid-adoption is
            // the one outcome here worse than a press that appears to be
            // ignored.
            state.pairing == PairingState.Completing -> Unit

            // **One step, and this is it.** Until 15.6.15 there were two — the
            // code, and the offer to ask for one — so back stepped from the
            // first to the second. Now the code *is* the offer and there is
            // nothing behind it: going further back would re-ask a question
            // about a profile that already exists (15.8.1). So back is the
            // answer this app ships as its default, which is the one drawn at
            // the foot of the screen: *Not now*. `onDispose` below is what
            // takes the live code away with it.
            else -> onDone()
        }
    }

    // The offer can leave without anybody pressing anything — `onDone` above,
    // the process going away, a rider walking off. This view model outlives it
    // either way, so the code goes when the screen does. See
    // `AccountViewModel.abandonPairing` for what it cost not to.
    DisposableEffect(Unit) {
        onDispose { viewModel.abandonPairing() }
    }

    // 15.6.15. The owner asked for the code to be on screen rather than behind
    // *Show me a code*, and this is the moment it matters most: a rider who has
    // just answered four questions about themselves is being offered an account
    // and should be able to point a phone at the bike without reading anything.
    //
    // **20.4.7: this was keyed on `pairingAvailable` alone and the code never
    // arrived.** The profile is written to `profiles` a moment before this step
    // composes, so the first emission carrying `pairingAvailable = true` can
    // still carry no profile — and `startPairing` returns silently without one,
    // leaving a spinner that says *"Getting a code…"* for ever because nothing
    // in the key ever changes again. `wantsPairingCode` includes the profile,
    // so the effect runs again the instant it lands.
    LaunchedEffect(state.wantsPairingCode) {
        if (state.wantsPairingCode && state.pairing == PairingState.Idle && !linked) {
            viewModel.startPairing(onSignedIn = { linked = true })
        }
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
            linked -> LinkedConfirmation(
                email = (state.session as? AccountState.SignedIn)?.email,
                onDone = onDone
            )

            state.pairing == PairingState.Completing -> PairingCompleting()

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

                Spacer(Modifier.height(MaterialTheme.spacing.medium))

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
                            // 15.6.15: the code is already on screen. It used
                            // to be a card explaining that a code could be
                            // asked for, which on the first screen a new rider
                            // meets is a paragraph in the way of a picture.
                            ScanToSignIn(
                                state = state,
                                onRetry = {
                                    viewModel.startPairing(onSignedIn = { linked = true })
                                }
                            )
                        }
                    }
                    Column(
                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
                        modifier = Modifier.weight(1f)
                    ) {
                        // 15.6.11 again: the typed route gets the same moment
                        // as the scanned one. A rider who has just put a
                        // password into a bike's touchscreen has done more work
                        // than one who scanned, not less.
                        SignInForm(state, viewModel, onDone = { linked = true })
                    }
                }

                Spacer(Modifier.height(MaterialTheme.spacing.medium))

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
 * The bike saying the link landed (PLAN 15.6.11).
 *
 * **The owner's note:** *"Make sure that after you sign in / sign up the bike
 * automatically responds to it and says 'successfully linked account' or
 * something similar/better."* Half of it was built — the poll redeems the
 * pairing and a linked bike does end up signed in — and the half that was not
 * is the *moment*. Before this, a successful redeem called `onDone`
 * immediately: the QR vanished, the dialog closed and the dashboard appeared.
 * A rider who has just typed a password into their phone is looking at the
 * **bike** waiting to be told it worked, and a screen that quietly gets out of
 * the way has answered a different question.
 *
 * **It names the account, because that is a fact the rider can check.**
 * *"Success"* is a claim they cannot: on a household bike the interesting
 * failure is signing in as the wrong person, and an address is the only thing
 * on this screen that would show it. The address can be absent — a session
 * carries one only if the provider gave one — so the fallback says what is
 * certainly true and claims nothing more.
 *
 * **One button, and no timer.** The rider may still be holding their phone two
 * steps away; a panel that counts itself down takes the message away from
 * exactly the rider who needed it. Phase 26's rule is to say less, and it also
 * says where to be explicit — this is the one moment in the app where work done
 * on another device has to be reported back.
 */
@Composable
private fun LinkedConfirmation(email: String?, onDone: () -> Unit) {
    Text(
        text = "Signed in",
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onBackground
    )
    Text(
        text = email ?: "This bike is linked to your account.",
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.readableText()
    )
    Spacer(Modifier.height(MaterialTheme.spacing.medium))
    Text(
        text = "Your rides back themselves up from here on.",
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.readableText()
    )
    Spacer(Modifier.height(MaterialTheme.spacing.large))
    Button(onClick = onDone, modifier = Modifier.widthIn(min = 200.dp)) {
        Text("Start riding")
    }
}

/**
 * How wide the two routes are allowed to run together (20.4.4).
 *
 * Not `readableWidth`: this is a set of things being *chosen between* rather
 * than prose being read, which is 22.4's distinction and the reason the stacked
 * 640 dp version overflowed a panel with 1280 dp of room on it.
 */
internal val OFFER_WIDTH = 1040.dp
