package com.pelonot.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pelonot.R
import androidx.compose.ui.res.stringResource
import com.pelonot.domain.cloud.AccountState
import com.pelonot.domain.cloud.PairingState
import com.pelonot.ui.components.QrCode
import com.pelonot.ui.theme.readableColumn
import com.pelonot.ui.theme.spacing
import com.pelonot.ui.viewmodel.AccountMode
import com.pelonot.ui.viewmodel.AccountUiState
import com.pelonot.ui.viewmodel.AccountViewModel

/**
 * Backing up a rider's rides (PLAN 15.1).
 *
 * **The copy calls it what it does** (15.1.5). A rider on a bike is not looking
 * for an account, they are deciding whether the last two years of their riding
 * is safe — so the screen is called *Back up my rides* and the word "account"
 * appears only where it is unavoidable.
 *
 * Its own destination rather than a dialog (15.1.6), because Settings is
 * reachable from inside a ride and a modal over a class somebody is pedalling
 * is the one shape this must not take.
 *
 * Four states, and the third is the one that gets forgotten:
 *
 * 1. no cloud in this build — the screen says so and offers nothing;
 * 2. signed out — the form;
 * 3. **signed up but not confirmed** — no session, an email sitting in an
 *    inbox, and a rider who will otherwise conclude it failed;
 * 4. signed in — what is backed up, and the way out.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AccountViewModel = viewModel(factory = AccountViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Back up my rides") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(padding)
                .readableColumn()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MaterialTheme.spacing.large),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.large)
        ) {
            when {
                !state.cloudConfigured -> NoCloudHere()
                state.isGuest -> GuestCannotBackUp()
                state.session == AccountState.Unknown -> Loading()
                state.signedInAsThisProfile -> SignedIn(state, viewModel::signOut)
                state.awaitingConfirmationFor != null -> ConfirmYourEmail(
                    state = state,
                    onResend = viewModel::resendConfirmation,
                    onBackToSignIn = { viewModel.setMode(AccountMode.SignIn) }
                )

                state.pairing != PairingState.Idle -> PairingSection(
                    state = state,
                    onCancel = viewModel::cancelPairing,
                    onRetry = { viewModel.startPairing(onSignedIn = onBack) }
                )

                else -> {
                    // 15.6. Offered above the form rather than below it: for
                    // anybody holding a phone this is the better path, and the
                    // typed form is the one that exists for riders who are not.
                    if (state.pairingAvailable) {
                        ScanToSignIn(onStart = { viewModel.startPairing(onSignedIn = onBack) })
                    }
                    SignInForm(state, viewModel, onDone = onBack)
                }
            }
        }
    }
}

/**
 * A build with no endpoint compiled into it (23.1.5, 14.10.3).
 *
 * It says the app has no cloud rather than that *this rider* has no account,
 * because those are different facts and only one of them is the rider's to fix.
 * Nothing here mentions Supabase, keys or `local.properties`: a fact about
 * somebody's build configuration is not a sentence for a person on a bike.
 */
@Composable
private fun NoCloudHere() {
    Card(colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(MaterialTheme.spacing.large)) {
            Text("This copy of Pelonot has no cloud", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.size(MaterialTheme.spacing.small))
            Text(
                text = "It records everything on this tablet and nothing leaves it. " +
                    "Your rides are still safe to move — Settings can write the whole " +
                    "history out to a file.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 15.2.3 — a guest ride has no owner, so there is nothing to back up. */
@Composable
private fun GuestCannotBackUp() {
    Text("You're riding as a guest", style = MaterialTheme.typography.titleMedium)
    Text(
        text = "A guest ride isn't attached to anybody, so there is nothing for an " +
            "account to look after. Create a profile first and its rides can be backed up.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/**
 * The session is still being loaded from storage.
 *
 * A real state and not a formality (see `AccountState.Unknown`): drawing the
 * sign-in form during it invites a rider who is already signed in to sign in
 * again.
 */
@Composable
private fun Loading() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        Spacer(Modifier.size(MaterialTheme.spacing.medium))
        Text("Checking…", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SignedIn(state: AccountUiState, onSignOut: () -> Unit) {
    val email = (state.session as? AccountState.SignedIn)?.email
    Text("Your rides are backed up", style = MaterialTheme.typography.titleLarge)
    Text(
        text = buildString {
            append(state.profile?.name ?: "This profile")
            append(" is signed in")
            if (email != null) append(" as $email")
            append(".")
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    if (state.ridesWaiting > 0) {
        Text(
            text = if (state.ridesWaiting == 1) {
                "1 ride is still on its way up."
            } else {
                "${state.ridesWaiting} rides are still on their way up."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    Spacer(Modifier.size(MaterialTheme.spacing.small))

    // 15.4.1/15.4.4: the fear this button raises is that signing out deletes
    // the rider's history, and the natural fear is exactly backwards. Say so on
    // the button's own screen rather than in a dialog they may never open.
    Text(
        text = "Signing out keeps every ride on this bike. Your history, your " +
            "dashboard and the household leaderboard are unchanged — you simply stop " +
            "sending new rides up.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    OutlinedButton(onClick = onSignOut, enabled = !state.busy) { Text("Sign out") }
    state.problem?.let { ProblemLine(it) }
}

/**
 * Signed up, no session, and an email in an inbox (15.1.2a).
 *
 * The state that must not be drawn as a failure or as a success. What makes it
 * survivable is that the rider has to *leave the app* to finish it, so the
 * screen has to say plainly what to do and what to do when nothing arrives —
 * the mailer's rate limit is low and "sign up again" is the instinct that gets
 * them stuck between two errors that both sound final.
 */
@Composable
private fun ConfirmYourEmail(
    state: AccountUiState,
    onResend: () -> Unit,
    onBackToSignIn: () -> Unit
) {
    Text("Check your email", style = MaterialTheme.typography.titleLarge)
    Text(
        text = "We've sent a link to ${state.awaitingConfirmationFor}. Open it on any " +
            "device to confirm the address, then come back here and sign in.",
        style = MaterialTheme.typography.bodyMedium
    )
    Text(
        text = "Nothing arrives instantly, and it may be in a spam folder. " +
            "Don't sign up again — that address is already taken now, by you.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)) {
        Button(onClick = onBackToSignIn, enabled = !state.busy) { Text("I've confirmed it") }
        TextButton(onClick = onResend, enabled = !state.busy) { Text("Send it again") }
    }
    state.problem?.let { ProblemLine(it) }
}

@Composable
private fun SignInForm(
    state: AccountUiState,
    viewModel: AccountViewModel,
    onDone: () -> Unit
) {
    val signingUp = state.mode == AccountMode.SignUp

    // 15.2.8. Somebody else's session is on this tablet. Said before the form
    // rather than after a failure, because the failure it produces otherwise
    // ("that account is already backing up Priya") arrives after the rider has
    // typed a password on a touchscreen.
    if (state.signedInAsSomebodyElse) {
        Card(colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(MaterialTheme.spacing.large)) {
                Text(
                    text = "This bike is signed in as somebody else",
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(Modifier.size(MaterialTheme.spacing.small))
                Text(
                    text = "A tablet can only carry one account at a time. Signing in " +
                        "here as ${state.profile?.name ?: "you"} will take over from them — " +
                        "their rides stay on the bike and start waiting again until they " +
                        "sign back in.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    Text(
        text = if (signingUp) {
            "Your rides live on this tablet only. An account keeps a copy somewhere " +
                "else, so a dropped tablet is not a lost history."
        } else {
            "Sign in and this profile's rides start backing themselves up."
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    if (state.ridesWaiting > 0) {
        Text(
            text = if (state.ridesWaiting == 1) {
                "1 ride on this bike would go up."
            } else {
                "${state.ridesWaiting} rides on this bike would go up."
            },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }

    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = !signingUp,
            onClick = { viewModel.setMode(AccountMode.SignIn) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
        ) { Text("I have an account") }
        SegmentedButton(
            selected = signingUp,
            onClick = { viewModel.setMode(AccountMode.SignUp) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
        ) { Text("Create one") }
    }

    OutlinedTextField(
        value = state.email,
        onValueChange = viewModel::setEmail,
        label = { Text("Email") },
        singleLine = true,
        isError = state.emailProblem != null,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Next
        ),
        modifier = Modifier.fillMaxWidth()
    )

    OutlinedTextField(
        value = state.password,
        onValueChange = viewModel::setPassword,
        label = { Text("Password") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = if (signingUp) ImeAction.Next else ImeAction.Done
        ),
        modifier = Modifier.fillMaxWidth()
    )

    // Only when signing up. Signing in wrong costs one more attempt; signing
    // *up* wrong creates an account whose password nobody knows, on an address
    // that is now taken.
    if (signingUp) {
        OutlinedTextField(
            value = state.repeatedPassword,
            onValueChange = viewModel::setRepeatedPassword,
            label = { Text("Password again") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }

    state.problem?.let { ProblemLine(it) }

    Button(
        onClick = { viewModel.submit(onSignedIn = onDone) },
        enabled = state.canSubmit,
        modifier = Modifier.fillMaxWidth()
    ) {
        if (state.busy) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        } else {
            Text(if (signingUp) "Create my account" else "Sign in")
        }
    }

    Text(
        text = "Pelonot only ever sends up your own rides and your own profile. " +
            "It never reads anybody else's, and signing out leaves every ride here.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/**
 * The offer, before any of it has started (15.6).
 *
 * It leads with what the rider gets rather than with how it works: no typing on
 * the bike, and their password manager doing the job it exists for. "Scan a
 * code" is a mechanism; "use your phone" is the reason.
 */
@Composable
private fun ScanToSignIn(onStart: () -> Unit) {
    Card(colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(MaterialTheme.spacing.large)) {
            Text("Use your phone instead", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.size(MaterialTheme.spacing.small))
            Text(
                text = "The bike shows a code, you scan it, and you sign in on your " +
                    "phone — where your password manager works and the keyboard is " +
                    "the right size. Nothing is typed on the bike.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.size(MaterialTheme.spacing.medium))
            Button(onClick = onStart) { Text("Show me a code") }
        }
    }
}

/**
 * The code, the clock, and the way out (15.6.6).
 *
 * Everything here is sized for somebody standing next to the bike holding a
 * phone: the QR big enough to scan from half a metre, the eight characters
 * underneath it big enough to type from the same distance, and the URL small
 * because it is the third fallback rather than the first.
 *
 * The countdown is honest about the five minutes and the expiry is a state of
 * its own rather than a silent stop — a code that quietly died while the rider
 * went to fetch their phone is the most confusing thing this flow can do.
 */
@Composable
private fun PairingSection(
    state: AccountUiState,
    onCancel: () -> Unit,
    onRetry: () -> Unit
) {
    when (val pairing = state.pairing) {
        PairingState.Idle -> Unit

        PairingState.Starting -> Loading()

        PairingState.Completing -> {
            Text("Signing you in…", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "Your phone has handed this bike a session.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        PairingState.Expired -> {
            Text("That code has expired", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "Codes last five minutes, so one left on screen cannot be used " +
                    "by somebody who saw it earlier.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = onRetry) { Text("Show me a new one") }
            OutlinedButton(onClick = onCancel) { Text("Type it on the bike instead") }
        }

        is PairingState.Failed -> {
            Text("That did not work", style = MaterialTheme.typography.titleLarge)
            ProblemLine(pairing.message)
            OutlinedButton(onClick = onCancel) { Text("Back") }
        }

        is PairingState.Waiting -> {
            Text("Scan this with your phone", style = MaterialTheme.typography.titleLarge)

            Card(colors = CardDefaults.cardColors(Color.White)) {
                QrCode(
                    content = pairing.url,
                    modifier = Modifier
                        .padding(MaterialTheme.spacing.medium)
                        .size(QR_SIZE)
                )
            }

            Text(
                text = pairing.formattedCode,
                style = MaterialTheme.typography.displaySmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Or open ${pairing.url.substringBefore("#")} on any device and " +
                    "type the code.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            val secondsLeft = pairing.secondsLeft(state.nowMs)
            Text(
                text = "This code works for another " +
                    "${secondsLeft / 60}:${(secondsLeft % 60).toString().padStart(2, '0')}.",
                style = MaterialTheme.typography.bodyMedium
            )

            OutlinedButton(onClick = onCancel) { Text("Type it on the bike instead") }
        }
    }
}

/**
 * Big enough to scan from where a rider stands, and no bigger.
 *
 * The tablet is 1280 × 720 dp and this screen is capped at `readableWidth`, so
 * 260 dp is a comfortable third of the column rather than a wall.
 */
private val QR_SIZE = 260.dp

@Composable
private fun ProblemLine(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error
    )
}
