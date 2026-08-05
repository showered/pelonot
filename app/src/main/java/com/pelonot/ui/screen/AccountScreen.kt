package com.pelonot.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pelonot.R
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

    // 15.6.15. The code is asked for as the screen opens, not by a button —
    // and only on the one branch that draws it. A rider who is already signed
    // in, riding as a guest, or waiting on a confirmation email is on a
    // different screen, and minting a pairing code for them is a request to the
    // server that nothing on the tablet will ever show.
    val wantsCode = state.pairingAvailable &&
        state.cloudConfigured &&
        !state.isGuest &&
        state.session != AccountState.Unknown &&
        !state.signedInAsThisProfile &&
        state.awaitingConfirmationFor == null

    LaunchedEffect(wantsCode) {
        if (wantsCode && state.pairing == PairingState.Idle) {
            viewModel.startPairing(onSignedIn = onBack)
        }
    }

    // The pairing outlives this screen otherwise, and a live code with nothing
    // showing it is what 15.6.13 was. Same call the offer step makes.
    DisposableEffect(Unit) {
        onDispose { viewModel.abandonPairing() }
    }

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
                .fillMaxWidth()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MaterialTheme.spacing.large),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.large)
        ) {
            when {
                !state.cloudConfigured -> ReadablePanel { NoCloudHere() }
                state.isGuest -> ReadablePanel { GuestCannotBackUp() }
                state.session == AccountState.Unknown -> ReadablePanel { Loading() }
                state.signedInAsThisProfile ->
                    ReadablePanel { SignedIn(state, viewModel::signOut) }

                state.awaitingConfirmationFor != null -> ReadablePanel {
                    ConfirmYourEmail(
                        state = state,
                        onResend = viewModel::resendConfirmation,
                        onBackToSignIn = { viewModel.setMode(AccountMode.SignIn) }
                    )
                }

                state.pairing == PairingState.Completing -> ReadablePanel { PairingCompleting() }

                // **Two routes, side by side** — 20.4.4's fix arriving on the
                // second screen with the same problem. Stacked in a 760 dp
                // reading column the QR is 600 px tall and the form it is meant
                // to be an alternative *to* begins below the fold, so the screen
                // reads as one route with something under it. A set of things a
                // rider is choosing between wants the width (22.4); prose and
                // form fields are what the cap is for, which is why every other
                // branch here keeps it.
                state.pairingAvailable -> Row(
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraLarge),
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier.widthIn(max = OFFER_WIDTH)
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
                        modifier = Modifier.weight(1f)
                    ) {
                        ScanToSignIn(
                            state = state,
                            onRetry = { viewModel.startPairing(onSignedIn = onBack) }
                        )
                    }
                    // `Column`, not `Box`: SignInForm emits a sequence of
                    // siblings for a ColumnScope, and a Box stacks all of them
                    // on top of one another (seen on the AVD, 20.4.4).
                    Column(
                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
                        modifier = Modifier.weight(1f)
                    ) {
                        SignInForm(state, viewModel, onDone = onBack)
                    }
                }

                else -> ReadablePanel { SignInForm(state, viewModel, onDone = onBack) }
            }
        }
    }
}

/**
 * Everything on this screen except the two routes is prose and form fields, and
 * `readableWidth` is what those are for (22.2.6).
 */
@Composable
private fun ReadablePanel(content: @Composable ColumnScope.() -> Unit) = Column(
    modifier = Modifier.readableColumn(),
    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.large),
    content = content
)

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
internal fun Loading() {
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
internal fun SignInForm(
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
 * The QR, the code and the clock — drawn without being asked for (15.6.6,
 * 15.6.15).
 *
 * **It used to be a button.** *"Use your phone instead"*, a paragraph about why,
 * and *Show me a code*; the owner's note is one line — *"Can it just be shown by
 * default please. Would be a better UX than having to click 'Show me a code'"* —
 * and it is right for a reason worth keeping: the paragraph was selling a route
 * the picture sells by itself, and a rider holding a phone has already decided.
 * So the screen mints a code as it opens and the explanation is gone. The typed
 * form is not behind anything either; both routes are on the screen at once and
 * neither is a mode.
 *
 * Everything here is sized for somebody standing next to the bike holding a
 * phone: the QR big enough to scan from half a metre, the eight characters
 * underneath it big enough to type from the same distance, and the URL small
 * because it is the third fallback rather than the first.
 *
 * The countdown is honest about the five minutes, and the code replaces itself
 * five more times before the screen gives up — a rider who went to their inbox
 * to confirm an address comes back to a live code rather than to a dead one and
 * a button.
 */
@Composable
internal fun ScanToSignIn(
    state: AccountUiState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) = Column(
    modifier = modifier.fillMaxWidth(),
    // The one part of the app that is centred, and it earns it: this is not
    // read, it is *pointed a camera at* from a metre away, and a QR hugging the
    // left edge of a 1280 dp tablet makes the rider work out where to stand.
    horizontalAlignment = Alignment.CenterHorizontally,
    // Tighter than the rest of the app on purpose. These five things are one
    // object — a picture with its own caption — and on the profile-creation
    // step the 12 dp version pushed *Not now* off the bottom of the screen,
    // which is precisely the fault 20.4.4 exists to prevent.
    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)
) {
    when (val pairing = state.pairing) {
        // Idle is the half-second before the first code is asked for, and
        // Starting is every subsequent one. Both are the same to a rider.
        PairingState.Idle, PairingState.Starting -> Loading()

        // The caller draws this one: it is a moment rather than a route, and it
        // belongs to the whole screen.
        PairingState.Completing -> Unit

        PairingState.Expired -> {
            Text("That code has expired", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "Codes last five minutes, so one left on screen cannot be used " +
                    "by somebody who saw it earlier.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = onRetry) { Text("Show me a new one") }
        }

        // **One line, and no headline.** Nobody asked for this code, so a
        // failure to mint it is not the rider's error to be told about in bold
        // — the form beside it works and is what they should use.
        is PairingState.Failed -> Text(
            text = pairing.message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

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
        }
    }
}

/**
 * The second between the phone letting go and the bike being signed in.
 *
 * It takes the screen rather than sitting in a column, because for that second
 * it is the only thing happening and the rider is looking at the bike.
 */
@Composable
internal fun PairingCompleting() = Column(
    modifier = Modifier.fillMaxWidth(),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)
) {
    Text("Signing you in…", style = MaterialTheme.typography.titleLarge)
    Text(
        text = "Your phone has handed this bike a session.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/**
 * Big enough to scan from where a rider stands, and no bigger.
 *
 * The tablet is 1280 × 720 dp and this screen is capped at `readableWidth`, so
 * 260 dp is a comfortable third of the column rather than a wall.
 */
internal val QR_SIZE = 260.dp

@Composable
internal fun ProblemLine(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error
    )
}
