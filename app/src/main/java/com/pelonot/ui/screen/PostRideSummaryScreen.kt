package com.pelonot.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pelonot.core.Formatters
import com.pelonot.data.local.entity.UserEntity
import com.pelonot.ui.theme.expressiveShapes
import com.pelonot.ui.theme.spacing
import com.pelonot.ui.viewmodel.PostRideViewModel

/**
 * Post-ride summary with RPE capture and FTP breakthrough handling.
 */
@Composable
fun PostRideSummaryScreen(
    workoutId: String,
    isGuest: Boolean,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PostRideViewModel = viewModel(factory = PostRideViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showProfileDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(workoutId) { viewModel.load(workoutId) }

    if (showProfileDialog) {
        ProfileCreationDialog(
            onProfileCreated = { name, weightKg, ftpWatts ->
                showProfileDialog = false
                viewModel.saveToNewProfile(context, name, weightKg, ftpWatts, onDone)
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(MaterialTheme.spacing.large),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Ride Summary",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() }
        )

        Spacer(Modifier.size(MaterialTheme.spacing.extraLarge))

        if (workout == null) {
            Text(
                text = "This ride could not be found. It may already have been discarded.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.expressiveShapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Column(Modifier.padding(MaterialTheme.spacing.large)) {
                    SummaryRow("Duration", Formatters.duration(workout.durationSec))
                    SummaryRow("Total output", Formatters.kilojoules(workout.totalOutputKj))
                    SummaryRow("Average power", Formatters.watts(workout.avgPower ?: 0.0))
                    SummaryRow("Average cadence", Formatters.rpm(workout.avgCadence ?: 0.0))
                    SummaryRow("Average heart rate", Formatters.bpm(workout.avgHr?.toInt()))
                    SummaryRow("Distance", Formatters.kilometres(workout.totalDistanceKm))
                }
            }

            Spacer(Modifier.size(MaterialTheme.spacing.extraLarge))

            RpeSelector(
                selected = state.rpe,
                onSelect = viewModel::setRpe
            )
        }

        Spacer(Modifier.size(MaterialTheme.spacing.extraLarge))

        if (isGuest) {
            GuestDestination(
                profiles = state.profiles,
                onSaveToProfile = { userId ->
                    viewModel.saveToProfile(context, userId, onDone)
                },
                onCreateProfile = { showProfileDialog = true },
                onKeepAsGuest = onDone,
                onDiscard = { viewModel.discard(onDone) }
            )
        } else {
            Button(
                onClick = onDone,
                modifier = Modifier
                    .fillMaxWidth()
                    .sizeIn(minHeight = MIN_TOUCH_TARGET),
                shape = MaterialTheme.expressiveShapes.pill
            ) {
                Text("Done")
            }
        }
    }
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
    onDiscard: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
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

/**
 * Rate of Perceived Exertion, 1–10.
 *
 * Uses [FlowRow] rather than a single [Row]: ten 48dp buttons plus spacing
 * needs roughly 520dp, so on any phone the previous row pushed the higher
 * numbers off-screen where they could not be tapped at all.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RpeSelector(
    selected: Int?,
    onSelect: (Int) -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "How hard did that feel?",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "1 = very easy, 10 = maximal",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.size(MaterialTheme.spacing.medium))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(
                MaterialTheme.spacing.small,
                Alignment.CenterHorizontally
            ),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)
        ) {
            for (rating in 1..10) {
                val isSelected = selected == rating
                FilledTonalButton(
                    onClick = { onSelect(rating) },
                    modifier = Modifier
                        .sizeIn(minWidth = MIN_TOUCH_TARGET, minHeight = MIN_TOUCH_TARGET)
                        .semantics {
                            contentDescription = "Rate this effort $rating out of 10"
                        },
                    shape = MaterialTheme.expressiveShapes.pill,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                    colors = if (isSelected) {
                        ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        ButtonDefaults.filledTonalButtonColors()
                    }
                ) {
                    Text("$rating")
                }
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = MaterialTheme.spacing.small)
            // Read as one phrase by a screen reader rather than two
            // disconnected fragments.
            .clearAndSetSemantics { contentDescription = "$label: $value" },
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private val MIN_TOUCH_TARGET = 48.dp
