package com.pelonot.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pelonot.R
import com.pelonot.ui.components.RideSummaryCard
import com.pelonot.ui.theme.expressiveShapes
import com.pelonot.ui.theme.spacing
import com.pelonot.ui.viewmodel.RideDetailViewModel
import java.text.DateFormat
import java.util.Date

/**
 * A ride from history.
 *
 * The same figures as the post-ride summary, minus everything that belongs to
 * the minute after a ride: no FTP breakthrough dialog, no guest filing. RPE
 * stays, because it is the one number riders get wrong while still out of
 * breath (12.2.4).
 *
 * Charts land here first (12.2.3 / phase 16).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RideDetailScreen(
    workoutId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RideDetailViewModel = viewModel(factory = RideDetailViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(workoutId) { viewModel.load(workoutId) }

    val workout = state.workout

    if (confirmingDelete && workout != null) {
        val date = remember(workout.timestamp) {
            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                .format(Date(workout.timestamp))
        }
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text("Delete ${state.displayTitle}?") },
            text = {
                Text(
                    "Recorded $date. Deleting it also removes everything it measured " +
                        "second by second, and that cannot be rebuilt from the totals.\n\n" +
                        "This only deletes the ride on this device."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingDelete = false
                        viewModel.delete(onBack)
                    }
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) { Text("Keep it") }
            }
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(state.displayTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                },
                actions = {
                    if (workout != null) {
                        IconButton(
                            onClick = { confirmingDelete = true },
                            modifier = Modifier.semantics {
                                contentDescription = "Delete this ride"
                            }
                        ) {
                            Icon(Icons.Outlined.Delete, contentDescription = null)
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (state.isLoading) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MaterialTheme.spacing.large)
        ) {
            if (workout == null) {
                Text(
                    text = "This ride is no longer here. It may have been deleted.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                return@Column
            }

            val recorded = remember(workout.timestamp) {
                DateFormat.getDateTimeInstance(DateFormat.FULL, DateFormat.SHORT)
                    .format(Date(workout.timestamp))
            }
            Text(
                text = recorded,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (workout.wasRecovered) {
                Spacer(Modifier.size(MaterialTheme.spacing.small))
                Text(
                    text = "Pelonot was closed part-way through this ride. Everything " +
                        "below was rebuilt from the samples that reached the database, " +
                        "so however long the ride carried on after that is not in it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }

            Spacer(Modifier.size(MaterialTheme.spacing.large))

            RideSummaryCard(workout)

            Spacer(Modifier.size(MaterialTheme.spacing.extraLarge))

            RpeEditor(selected = workout.rpeRating, onSelect = viewModel::setRpe)

            Spacer(Modifier.size(MaterialTheme.spacing.extraLarge))
        }
    }
}

/**
 * RPE, after the fact.
 *
 * Same control as the post-ride screen but framed as a correction rather than a
 * question, and it saves on each tap — there is no "Done" button on this screen
 * to hang a save off.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RpeEditor(selected: Int?, onSelect: (Int) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "How hard did it feel?",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            text = if (selected == null) {
                "You didn't rate this one — you still can"
            } else {
                "Tap a different number to change your rating"
            },
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

private val MIN_TOUCH_TARGET = 48.dp
