package com.pelonot.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
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
import com.pelonot.ui.theme.columnsFor
import com.pelonot.ui.theme.spacing
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
                // The last clause is 12.4.1's: this screen has been telling
                // riders the summary screen was the only chance, and it was
                // right until the unclaimed section existed.
                body = "A guest ride belongs to nobody by design. Pick a profile " +
                    "before you start, or file the ride against one afterwards — " +
                    "from the summary screen, or from the top of that profile's " +
                    "history."
            )

            state.isEmpty -> EmptyHistory(
                modifier = Modifier.padding(padding),
                title = "No rides yet",
                body = "Finish a ride and it will appear here with everything it " +
                    "recorded — second by second. Start one from the dashboard, " +
                    "or pick a class from the library."
            )

            // 22.7.6, and it overturns 22.4.3's verdict on this one screen.
            // History was a grid — two ride cards across the bike's panel — and
            // the owner's note is that a **list** should not change shape with
            // how much is in it: *"keep it all constrained to one narrower grid
            // column in the middle, rather than expanding widthways for days
            // with large numbers of workouts."*
            //
            // So the column is one grid cell wide, centred, and every day is
            // the same shape as every other. It is the same arithmetic as
            // before — `columnsFor` still decides how wide a cell is on this
            // screen — with one column drawn instead of all of them, so a phone
            // is unchanged and the bike gets the width a single-ride day
            // already had.
            //
            // Two things fall out of it and both were separate items before.
            // 22.7.1's centring is now structural rather than per-row: nothing
            // can be hard against the left with 600 dp of nothing beside it,
            // because the whole list is in the middle. And 22.7.4's heading is
            // over its day by construction — one left edge for the labels and
            // the cards, with no inset to keep in step.
            else -> BoxWithConstraints(
                Modifier
                    // `fillMaxWidth` is load-bearing, not decoration: without it
                    // the box wraps its one child and there is nothing left to
                    // centre the column *in*, so it sits hard against the left
                    // edge at exactly the right width — which looks like the
                    // centring failing rather than the box shrinking.
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .padding(padding)
            ) {
                val available = maxWidth - MaterialTheme.spacing.large * 2
                val columns = columnsFor(
                    available = available,
                    minCellWidth = RIDE_CARD_MIN_WIDTH,
                    spacing = MaterialTheme.spacing.small
                )
                val columnWidth =
                    (available - MaterialTheme.spacing.small * (columns - 1)) / columns

                LazyColumn(
                    modifier = Modifier
                        .width(columnWidth)
                        .align(Alignment.TopCenter),
                    contentPadding = PaddingValues(
                        vertical = MaterialTheme.spacing.medium
                    ),
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)
                ) {
                    // 12.4.1, above the rider's own rides and never inside
                    // them. These belong to nobody: a guest ride records a null
                    // `user_id`, so it appears in no profile's history at all
                    // and — until this section existed — could not be reached
                    // again once the post-ride summary closed. Filing it in
                    // with this rider's days would answer the question the
                    // section is here to ask.
                    if (state.unclaimed.isNotEmpty()) {
                        item(key = "unclaimed-header") { UnclaimedHeader() }
                        items(state.unclaimed, key = { ride -> "unclaimed-${ride.id}" }) { ride ->
                            RideRow(
                                ride = ride,
                                onClick = { onRideSelected(ride.id) },
                                onDelete = { confirming = ride }
                            )
                        }
                    }

                    state.days.forEach { day ->
                        item(key = "header-${day.startOfDayMs}") {
                            DayHeader(day)
                        }
                        items(day.rides, key = { ride -> ride.id }) { ride ->
                            RideRow(
                                ride = ride,
                                onClick = { onRideSelected(ride.id) },
                                onDelete = { confirming = ride }
                            )
                        }
                    }

                    if (state.hasMore) {
                        item(key = "load-more") {
                            // The same rule as the cards above it: one control
                            // does not band across the column it ends.
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = MaterialTheme.spacing.large),
                                contentAlignment = Alignment.Center
                            ) {
                                OutlinedButton(
                                    onClick = viewModel::loadMore,
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
    }
}

/**
 * The heading over rides nobody has claimed (12.4.1).
 *
 * It carries a line of explanation where [DayHeader] carries none, because a
 * date needs none and this does: a rider looking at their own history has no
 * reason to expect somebody else's ride at the top of it, and the sentence has
 * to say both what these are and that opening one is how they are dealt with.
 */
@Composable
private fun UnclaimedHeader(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(
            top = MaterialTheme.spacing.large,
            bottom = MaterialTheme.spacing.extraSmall
        )
    ) {
        Text(
            text = "Not filed against anyone",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            text = "Ridden as a guest. Open one to say whose it was.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DayHeader(day: RideDayGrouping.Day<WorkoutListItem>, modifier: Modifier = Modifier) {
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
        modifier = modifier
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
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val time = remember(ride.timestamp) {
        DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(ride.timestamp))
    }

    Card(
        modifier = modifier.fillMaxWidth(),
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
                    // 26.1.3. This row used to carry five facts — the time, the
                    // duration, the kilojoules, the average watts and the
                    // distance. **The screen's only question is which ride was
                    // that**, and the class name above answers most of it; when
                    // and how long answer the rest. The other three are
                    // measurements on a row where a *choice* is being made,
                    // which is the failure case CLAUDE.md names for a profile
                    // tile reading `150 W FTP` under a name. All three are one
                    // tap away on the ride itself, where they are being read.
                    text = "$time  ·  ${Formatters.duration(ride.durationSec)}",
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

/**
 * Two ride cards across the bike's panel, one on a phone.
 *
 * 480 rather than something narrower: the metrics line — time, duration,
 * output, average power and distance — is what makes this list scannable
 * without opening anything, and at three across it wraps and stops being one
 * line to read.
 */
private val RIDE_CARD_MIN_WIDTH = 480.dp
