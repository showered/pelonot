package com.pelonot.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pelonot.core.Formatters
import com.pelonot.data.local.entity.UserEntity
import com.pelonot.ui.components.ClassLeaderboardCard
import com.pelonot.ui.components.EffortQuestion
import com.pelonot.ui.components.RideChartsSection
import com.pelonot.ui.components.RideFigures
import com.pelonot.ui.theme.expressiveShapes
import com.pelonot.ui.theme.loneCard
import com.pelonot.ui.theme.spacing
import com.pelonot.ui.viewmodel.PostRideViewModel
import java.text.DateFormat
import java.util.Date

/**
 * What the rider sees the moment they stop pedalling.
 *
 * **Rebuilt in the twenty-third sitting (22.4.6 and 26.1.2).** It was a centred
 * column of six label-value rows on a screen 1280 dp wide — the moment the app
 * has the rider's full attention, saying the least it ever says with the most
 * room it ever has. Three things changed and they are all one decision:
 *
 * - The figures **tile** rather than stack (`RideFigures`), because they are
 *   looked at rather than read.
 * - The two things a rider does here — say how hard it felt, and see how it
 *   compares — sit **side by side** instead of one below the fold. The RPE
 *   answer rate is the point: a question a rider has to scroll to is a question
 *   most riders do not answer.
 * - **Done and Discard do not scroll.** They are pinned below the content, so
 *   getting off the bike never involves finding the button first.
 *
 * The heading is the class the rider chose rather than the words *Ride
 * Summary*: they know they just rode, and a screen that spends its largest
 * type telling them so is spending it on the one thing they already know.
 *
 * **How this differs from `RideDetailScreen`, stated once (12.6.3).** They are
 * the same ride and, since 12.6.1, the same figures and the same charts. The
 * whole difference is three things that are only true tonight:
 *
 * 1. The FTP breakthrough dialog, which is right ninety seconds after a ride
 *    and bizarre on a ride from March (12.2.1).
 * 2. *Discard* and *Carry on riding* (11.1a.4, 12.6.2) — both about a ride that
 *    has only just happened.
 * 3. The guest destination (8.4), which is the last moment a guest ride can be
 *    filed against a profile.
 *
 * Ride detail has its own two — export and *ride against* — and everything else
 * belongs on **both**, through a shared component. They are deliberately not
 * merged: it is the behaviour that differs, not the presentation.
 */
@Composable
fun PostRideSummaryScreen(
    workoutId: String,
    isGuest: Boolean,
    onDone: () -> Unit,
    /** Back into the ride that was ended by accident (12.6.2). */
    onResume: (workoutId: String, classId: String?) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
    viewModel: PostRideViewModel = viewModel(factory = PostRideViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showProfileDialog by rememberSaveable { mutableStateOf(false) }
    var confirmingDiscard by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(workoutId) { viewModel.load(workoutId) }

    // 11.1a.4. Guests have had this since 8.4; riders with a profile had to
    // finish, leave, open history and delete. Same weight as the delete in
    // 12.3.2, because it destroys the same thing: not just the totals but the
    // per-second series underneath them, which nothing can rebuild.
    if (confirmingDiscard) {
        DiscardRideDialog(
            durationSec = state.workout?.durationSec ?: 0,
            onConfirm = {
                confirmingDiscard = false
                viewModel.discard(onDone)
            },
            onDismiss = { confirmingDiscard = false }
        )
    }

    if (showProfileDialog) {
        ProfileCreationDialog(
            onProfileCreated = { newProfile ->
                showProfileDialog = false
                viewModel.saveToNewProfile(context, newProfile, onDone)
            },
            onDismiss = { showProfileDialog = false }
        )
    }

    if (state.isLoading) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    if (state.hasBreakthrough) {
        FtpBreakthroughDialog(
            currentFtp = state.currentFtp,
            estimatedFtp = state.proposedFtp?.toDouble() ?: 0.0,
            onAccept = viewModel::acceptFtpProposal,
            onDecline = viewModel::declineFtpProposal
        )
    }

    val workout = state.workout

    Column(modifier.fillMaxSize()) {
        SummaryHeader(
            title = if (workout == null) "Ride Summary" else state.displayTitle,
            rider = state.riderName,
            isGuest = isGuest,
            atEpochMs = workout?.timestamp
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MaterialTheme.spacing.large)
        ) {
            if (workout == null) {
                Text(
                    text = "This ride could not be found. It may already have been discarded.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // A guest has no pinned row, so this card is their only way off
                // the screen — including when there is no ride left to file.
                if (isGuest) {
                    Spacer(Modifier.size(MaterialTheme.spacing.large))
                    GuestDestination(
                        profiles = state.profiles,
                        onSaveToProfile = { userId ->
                            viewModel.saveToProfile(context, userId, onDone)
                        },
                        onCreateProfile = { showProfileDialog = true },
                        onKeepAsGuest = onDone,
                        onDiscard = { viewModel.discard(onDone) },
                        modifier = Modifier.loneCard()
                    )
                }
            } else {
                RideFigures(workout)

                Spacer(Modifier.size(MaterialTheme.spacing.large))

                // The rider's two jobs, beside each other while there is room.
                // 24.1.2's board draws nothing for a free ride, a household of
                // one, or a household whose rides were all simulated — so on
                // most nights this is the RPE card and half a screen of air,
                // which is why it is left-aligned rather than centred.
                BoxWithConstraints {
                    // `isWorthShowing`, not merely non-null: a board of one
                    // row draws nothing at all, and asking the wrong question
                    // here would leave the RPE card at half width beside an
                    // empty half of screen.
                    val board = state.leaderboard?.takeIf { it.isWorthShowing }
                    val sideBySide = maxWidth >= SIDE_BY_SIDE_BREAKPOINT && board != null

                    if (sideBySide) {
                        Row(
                            // 24.1.8. Two cards of different heights beside
                            // each other read as a mistake rather than as a
                            // layout — 22.7.2 found the same thing one screen
                            // along and grew `equalHeightRows` for it. The
                            // board is bounded now (`ClassLeaderboard.visible`),
                            // so matching them costs a card's worth of air
                            // rather than an unbounded amount of it.
                            modifier = Modifier.height(IntrinsicSize.Min),
                            horizontalArrangement = Arrangement.spacedBy(
                                MaterialTheme.spacing.medium
                            )
                        ) {
                            EffortQuestion(
                                selected = state.rpe,
                                onSelect = viewModel::setRpe,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                isTonight = true
                            )
                            board?.let {
                                ClassLeaderboardCard(it, Modifier.weight(1f).fillMaxHeight())
                            }
                        }
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(
                                MaterialTheme.spacing.medium
                            )
                        ) {
                            // 22.6: alone, so it stops at a column's width
                            // instead of banding across the panel.
                            EffortQuestion(
                                selected = state.rpe,
                                onSelect = viewModel::setRpe,
                                modifier = Modifier.loneCard(),
                                isTonight = true
                            )
                            board?.let { ClassLeaderboardCard(it, Modifier.fillMaxWidth()) }
                        }
                    }
                }

                // 12.7.3. Above the charts, and below the effort question by
                // one place: this card is how a guest gets *off* this screen —
                // there is no pinned row for them — and every button on it
                // ends the screen, where the question above it is one tap and
                // stays. Below the charts it was about 1,300 dp down, which
                // put the only decision on the screen and the only way to
                // leave it behind two charts and two zone cards.
                if (isGuest) {
                    Spacer(Modifier.size(MaterialTheme.spacing.large))
                    GuestDestination(
                        profiles = state.profiles,
                        onSaveToProfile = { userId ->
                            viewModel.saveToProfile(context, userId, onDone)
                        },
                        onCreateProfile = { showProfileDialog = true },
                        onKeepAsGuest = onDone,
                        onDiscard = { viewModel.discard(onDone) },
                        // 22.6, and it was breaking that rule below the fold
                        // where nobody could see it band across the panel.
                        modifier = Modifier.loneCard()
                    )
                }

                Spacer(Modifier.size(MaterialTheme.spacing.extraLarge))

                // 12.6.1. The owner's note: *"this should be pretty much the
                // same as when you view it from history, right? It currently
                // doesn't contain any graphs."* It is the same ride and the
                // same component — and it is **last**, which is 12.7.4: a
                // chart is the tallest thing this screen draws, so anything
                // appended after one is below the fold by construction.
                //
                // No ghost and no rivals: those are a comparison, and this is
                // the one screen where the rider has not asked for one.
                RideChartsSection(
                    charts = state.charts,
                    isGuestRide = workout.userId == null
                )
            }

            Spacer(Modifier.size(MaterialTheme.spacing.large))
        }

        // Pinned. A rider who has just stopped pedalling should not have to
        // scroll to leave, and *Discard* being off-screen is what made 11.1a.4
        // worth doing in the first place.
        if (!isGuest) {
            SummaryActions(
                canDiscard = workout != null,
                canResume = state.canResume,
                onResume = { viewModel.resume(onResume) },
                onDiscard = { confirmingDiscard = true },
                onDone = onDone
            )
        }
    }
}

/**
 * The class, whose it was, and when — in that order of size.
 *
 * Full width and deliberately outside any cap: this is the line that says what
 * the numbers below it are about, and the numbers below it use the whole panel.
 */
@Composable
private fun SummaryHeader(
    title: String,
    rider: String?,
    isGuest: Boolean,
    atEpochMs: Long?
) {
    val when_ = remember(atEpochMs) {
        atEpochMs?.let { DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it)) }
    }
    val subtitle = listOfNotNull(
        rider ?: "Guest ride".takeIf { isGuest },
        when_
    ).joinToString(" · ")

    Column(
        Modifier.padding(
            start = MaterialTheme.spacing.large,
            end = MaterialTheme.spacing.large,
            top = MaterialTheme.spacing.large,
            bottom = MaterialTheme.spacing.large
        )
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() }
        )
        if (subtitle.isNotEmpty()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Done, and the two quieter ways out beside it.
 *
 * *Done* is the button a rider reaches for without looking, so it is on the
 * right where the thumb already is and *Discard* is as far from it as the row
 * allows — the same reasoning as before the rebuild, kept.
 *
 * *Carry on riding* (12.6.2) sits with Discard rather than with Done, for the
 * same reason: it is the answer to a mistake, not the ordinary way off this
 * screen. It says the same words the crash-recovery dialog does (8.3d), because
 * it does the same thing to the same ride.
 */
@Composable
private fun SummaryActions(
    canDiscard: Boolean,
    canResume: Boolean,
    onResume: () -> Unit,
    onDiscard: () -> Unit,
    onDone: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(MaterialTheme.spacing.large),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)) {
            OutlinedButton(
                onClick = onDiscard,
                enabled = canDiscard,
                modifier = Modifier.sizeIn(minHeight = MIN_TOUCH_TARGET),
                shape = MaterialTheme.expressiveShapes.pill
            ) {
                Text("Discard this ride")
            }
            // Absent rather than disabled when the window has closed: a
            // greyed-out button is a promise the app is not keeping, and the
            // reason it cannot be kept takes a paragraph to explain.
            if (canResume) {
                TextButton(
                    onClick = onResume,
                    modifier = Modifier.sizeIn(minHeight = MIN_TOUCH_TARGET)
                ) {
                    Text("Carry on riding")
                }
            }
        }
        Button(
            onClick = onDone,
            modifier = Modifier
                .width(DONE_WIDTH)
                .sizeIn(minHeight = MIN_TOUCH_TARGET),
            shape = MaterialTheme.expressiveShapes.pill
        ) {
            Text("Done")
        }
    }
}

/**
 * Named, and as hard to hit by accident as the delete in 12.3.2.
 *
 * It says what is going rather than asking "are you sure": a rider who has
 * just got off the bike is reading this out of breath, and "10:32 of riding"
 * is the fact that decides it.
 */
@Composable
private fun DiscardRideDialog(
    durationSec: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Throw this ride away?") },
        text = {
            Text(
                "${Formatters.duration(durationSec)} of riding goes, along with " +
                    "everything it measured second by second. That cannot be " +
                    "rebuilt from the totals, and it will not appear in your history."
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Throw it away") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Keep it") } }
    )
}

/**
 * What happens to a ride nobody was signed in for.
 *
 * Previously this was keep-or-discard, which quietly loses the more useful
 * outcome: a guest ride is very often the household's other rider, or someone
 * who has just decided they want their history kept. Filing it against a
 * profile only rewrites the ride's owner — the metric series it already
 * recorded stays exactly as it is.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GuestDestination(
    profiles: List<UserEntity>,
    onSaveToProfile: (Int) -> Unit,
    onCreateProfile: () -> Unit,
    onKeepAsGuest: () -> Unit,
    onDiscard: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.expressiveShapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(Modifier.padding(MaterialTheme.spacing.large)) {
            Text(
                text = "Whose ride was this?",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                text = "You rode as a guest. Filing it against a profile keeps it in " +
                    "that rider's history and counts towards their FTP.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.size(MaterialTheme.spacing.medium))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)
            ) {
                profiles.forEach { profile ->
                    FilledTonalButton(
                        onClick = { onSaveToProfile(profile.localUserId) },
                        modifier = Modifier.sizeIn(minHeight = MIN_TOUCH_TARGET),
                        shape = MaterialTheme.expressiveShapes.pill
                    ) {
                        Text(profile.name)
                    }
                }
                OutlinedButton(
                    onClick = onCreateProfile,
                    modifier = Modifier.sizeIn(minHeight = MIN_TOUCH_TARGET),
                    shape = MaterialTheme.expressiveShapes.pill
                ) {
                    Text("New profile…")
                }
            }

            Spacer(Modifier.size(MaterialTheme.spacing.large))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)
            ) {
                OutlinedButton(
                    onClick = onDiscard,
                    modifier = Modifier
                        .weight(1f)
                        .sizeIn(minHeight = MIN_TOUCH_TARGET),
                    shape = MaterialTheme.expressiveShapes.pill
                ) {
                    Text("Discard")
                }
                Button(
                    onClick = onKeepAsGuest,
                    modifier = Modifier
                        .weight(1f)
                        .sizeIn(minHeight = MIN_TOUCH_TARGET),
                    shape = MaterialTheme.expressiveShapes.pill
                ) {
                    Text("Keep as a guest ride")
                }
            }
        }
    }
}

private val MIN_TOUCH_TARGET = 48.dp

/** Wide enough to be the obvious target, narrow enough not to be a banner. */
private val DONE_WIDTH = 220.dp

/**
 * Below this the RPE pills and a leaderboard cannot both hold their shape, so
 * they stack. 900 dp is the same breakpoint the ride detail charts use.
 */
private val SIDE_BY_SIDE_BREAKPOINT = 900.dp
