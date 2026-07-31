package com.pelonot.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.pelonot.core.Formatters
import com.pelonot.data.local.dao.WorkoutListItem
import com.pelonot.domain.model.RideDayGrouping
import com.pelonot.ui.theme.expressiveShapes
import com.pelonot.ui.theme.spacing
import com.pelonot.ui.theme.units
import com.pelonot.ui.viewmodel.HistoryViewModel
import java.text.DateFormat
import java.util.Date

/**
 * Every ride the rider has finished.
 *
 * The app has been recording a full per-second series for every ride since the
 * foreign-key fix and, until this screen existed, offered no way to look at any
 * of it. Everything downstream — charts, comparisons, export — is a view onto
 * this list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    onRideSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel = viewModel(factory = HistoryViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var confirming by remember { mutableStateOf<WorkoutListItem?>(null) }

    // The snackbar *is* the undo window: the delete has not been written yet,
    // and it lands when this returns either way. Keyed on the ride's id so a
    // second deletion raises a second snackbar rather than reusing this one.
    val pending = state.pendingDeletion
    LaunchedEffect(pending?.id) {
        if (pending == null) return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = "Deleted ${pending.displayTitle}",
            actionLabel = "Undo",
            withDismissAction = true
        )
        when (result) {
            SnackbarResult.ActionPerformed -> viewModel.undoDelete()
            SnackbarResult.Dismissed -> viewModel.confirmPendingDelete()
        }
    }

    confirming?.let { ride ->
        DeleteRideDialog(
            ride = ride,
            onConfirm = {
                confirming = null
                viewModel.requestDelete(ride)
            },
            onDismiss = { confirming = null }
        )
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Ride history") },
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
        when {
            state.isLoading -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            state.isGuest -> EmptyHistory(
                modifier = Modifier.padding(padding),
                title = "Guest rides aren't kept in a history",
                body = "A guest ride belongs to nobody by design. Pick a profile " +
                    "before you start, or file the ride against one from the " +
                    "summary screen afterwards."
            )

            state.isEmpty -> EmptyHistory(
                modifier = Modifier.padding(padding),
                title = "No rides yet",
                body = "Finish a ride and it will appear here with everything it " +
                    "recorded — second by second. Start one from the dashboard, " +
                    "or pick a class from the library."
            )

            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(
                    horizontal = MaterialTheme.spacing.large,
                    vertical = MaterialTheme.spacing.medium
                ),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)
            ) {
                state.days.forEach { day ->
                    item(key = "header-${day.startOfDayMs}") {
                        DayHeader(day)
                    }
                    items(day.rides, key = { it.id }) { ride ->
                        RideRow(
                            ride = ride,
                            onClick = { onRideSelected(ride.id) },
                            onDelete = { confirming = ride }
                        )
                    }
                }

                if (state.hasMore) {
                    item(key = "load-more") {
                        OutlinedButton(
                            onClick = viewModel::loadMore,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = MaterialTheme.spacing.large),
                            shape = MaterialTheme.expressiveShapes.pill
                        ) {
                            Text("Show older rides")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayHeader(day: RideDayGrouping.Day<WorkoutListItem>) {
    val label = when (day.relative) {
        RideDayGrouping.Relative.Today -> "Today"
        RideDayGrouping.Relative.Yesterday -> "Yesterday"
        RideDayGrouping.Relative.Earlier -> remember(day.startOfDayMs) {
            DateFormat.getDateInstance(DateFormat.FULL).format(Date(day.startOfDayMs))
        }
    }

    Text(
        text = label,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(top = MaterialTheme.spacing.large, bottom = MaterialTheme.spacing.extraSmall)
            .semantics { heading() }
    )
}

/**
 * One ride.
 *
 * Delete is an explicit button rather than a swipe. A swipe is the right
 * gesture for a mis-tap you can take back instantly, and this list is scrolled
 * far more often than it is edited — a ride is not recoverable and the row is
 * a wide target for an accidental horizontal drag (12.3.1).
 */
@Composable
private fun RideRow(
    ride: WorkoutListItem,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val time = remember(ride.timestamp) {
        DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(ride.timestamp))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.expressiveShapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = MaterialTheme.spacing.large,
                    top = MaterialTheme.spacing.medium,
                    bottom = MaterialTheme.spacing.medium,
                    end = MaterialTheme.spacing.small
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = ride.displayTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.size(MaterialTheme.spacing.extraSmall))
                Text(
                    text = buildString {
                        append(time)
                        append("  ·  ")
                        append(Formatters.duration(ride.durationSec))
                        append("  ·  ")
                        append(Formatters.kilojoules(ride.totalOutputKj))
                        append("  ·  ")
                        append(Formatters.watts(ride.avgPower ?: 0.0))
                        append("  ·  ")
                        append(Formatters.distance(ride.totalDistanceKm, MaterialTheme.units))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (ride.wasRecovered) {
                    Spacer(Modifier.size(MaterialTheme.spacing.extraSmall))
                    // Not a warning badge — the ride is real and the data is
                    // real. What it is not is complete, and a rider comparing
                    // it against their others deserves to know why it is short.
                    Text(
                        text = "Recovered after a crash — the end of this ride wasn't recorded",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .sizeIn(minWidth = MIN_TOUCH_TARGET, minHeight = MIN_TOUCH_TARGET)
                    .semantics {
                        contentDescription = "Delete ${ride.displayTitle}"
                    }
            ) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Names the ride being deleted and says what goes with it.
 *
 * "Are you sure?" is not a question anybody can answer usefully. The rider has
 * to be able to tell from this dialog alone whether the row they hit is the one
 * they meant (12.3.2).
 */
@Composable
private fun DeleteRideDialog(
    ride: WorkoutListItem,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val date = remember(ride.timestamp) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            .format(Date(ride.timestamp))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete ${ride.displayTitle}?") },
        text = {
            Text(
                "Recorded $date. Deleting it also removes everything it measured " +
                    "second by second, and that cannot be rebuilt from the totals.\n\n" +
                    "This only deletes the ride on this device."
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Delete") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Keep it") } }
    )
}

/** An empty state that says what to do, not "No data" (12.1.5). */
@Composable
private fun EmptyHistory(title: String, body: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(MaterialTheme.spacing.extraLarge),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(Modifier.size(MaterialTheme.spacing.medium))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private val MIN_TOUCH_TARGET = 48.dp
