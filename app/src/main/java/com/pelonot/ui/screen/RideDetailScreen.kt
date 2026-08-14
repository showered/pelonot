package com.pelonot.ui.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pelonot.R
import com.pelonot.core.Formatters
import com.pelonot.data.local.entity.UserEntity
import com.pelonot.domain.chart.RideCharts
import com.pelonot.domain.chart.RideIntegrity
import com.pelonot.domain.export.ExportFormat
import com.pelonot.domain.model.PerceivedEffort
import com.pelonot.ui.components.RideChartsSection
import com.pelonot.ui.components.RideFigures
import com.pelonot.ui.theme.expressiveShapes
import com.pelonot.ui.theme.loneCard
import com.pelonot.ui.theme.readableText
import com.pelonot.ui.theme.spacing
import com.pelonot.ui.viewmodel.RideDetailUiState
import com.pelonot.ui.viewmodel.RideDetailViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

/**
 * A ride from history.
 *
 * **The same figures and the same charts as the post-ride summary** — one
 * `RideFigures` (12.2.2) and one `RideChartsSection` (12.6.1), so a rider
 * cannot be shown two pictures of one ride. What is missing here is everything
 * that belongs to the minute after a ride: no FTP breakthrough dialog, no
 * *Discard* or *Carry on riding*, no guest filing.
 *
 * What is here and not there: **export** (12.4.3), and **ride against** — the
 * housemate or the rider's own previous best drawn behind the power trace
 * (24.3.1, 16.3.4), which is a comparison somebody has come back to make rather
 * than one to put in front of them while they are still breathing hard.
 *
 * RPE stays on both, because it is the one number riders get wrong while still
 * out of breath (12.2.4). The two screens are deliberately not merged: it is
 * the behaviour that differs, not the presentation (12.2.1, 12.6.3).
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

    // 12.4.3. Through the system's own file picker rather than a share sheet:
    // the rider says where it goes, no FileProvider is involved, and on the
    // bike's tablet — which has almost nothing installed to share *to* — a
    // share sheet would be an empty list.
    val scope = rememberCoroutineScope()
    val snackbarHost = remember { SnackbarHostState() }
    var pendingExport by remember { mutableStateOf<Pair<String, String>?>(null) }
    val context = LocalContext.current

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        val export = pendingExport
        pendingExport = null
        // A cancelled picker is not a failure and does not deserve a message.
        if (uri == null || export == null) return@rememberLauncherForActivityResult

        scope.launch {
            val written = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        stream.write(export.second.toByteArray())
                    } ?: error("could not open $uri for writing")
                }
            }
            // Said out loud either way. A silent export is indistinguishable
            // from a successful one, which is the failure shape this project
            // has shipped eleven times.
            snackbarHost.showSnackbar(
                written.fold(
                    onSuccess = { "Saved ${export.first}" },
                    onFailure = { "Could not save the file: ${it.message}" }
                )
            )
        }
    }

    val export: (ExportFormat) -> Unit = { format ->
        scope.launch {
            val built = viewModel.buildExport(format)
            if (built == null) {
                snackbarHost.showSnackbar("There is nothing to export from this ride.")
            } else {
                pendingExport = built
                saveLauncher.launch(built.first)
            }
        }
    }

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
        snackbarHost = { SnackbarHost(snackbarHost) },
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

        // 22.4.2. **Not** `readableColumn()` any more. This screen is figures,
        // charts and a set of cards — things that are looked at — and capping
        // the whole of it at 760 dp was the failure 22.4 describes: four charts
        // stacked in one narrow column on a panel with room for all of them,
        // and the figures grid pushing the first chart off the bottom of the
        // screen. What is genuinely *read* here is prose, and prose keeps the
        // cap for itself — `Modifier.readableText()` below.
        Column(
            modifier = Modifier
                .fillMaxHeight()
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.readableText()
            )

            if (workout.wasRecovered) {
                Spacer(Modifier.size(MaterialTheme.spacing.small))
                Text(
                    text = "Pelonot was closed part-way through this ride. Everything " +
                        "below was rebuilt from the samples that reached the database, " +
                        "so however long the ride carried on after that is not in it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.readableText()
                )
            }

            Spacer(Modifier.size(MaterialTheme.spacing.large))

            RideFigures(workout)

            state.charts?.integrity?.takeIf { it.isSuspect }?.let { integrity ->
                Spacer(Modifier.size(MaterialTheme.spacing.medium))
                SuspectSamplesNotice(
                    integrity = integrity,
                    storedAvgCadence = workout.avgCadence,
                    storedAvgPower = workout.avgPower
                )
            }

            // 12.4.1, and it sits here on purpose: under the figures, because
            // *whose ride was this?* is answered by looking at the ride, and
            // above everything else, because on an unclaimed ride it is the
            // only thing on this screen anybody can act on.
            if (state.isUnclaimed) {
                Spacer(Modifier.size(MaterialTheme.spacing.extraLarge))
                ClaimRideSection(
                    profiles = state.profiles,
                    onClaim = { userId -> viewModel.claimFor(context, userId) },
                    modifier = Modifier.loneCard()
                )
            }

            Spacer(Modifier.size(MaterialTheme.spacing.extraLarge))

            // 16.1. Every ride since the foreign-key fix has written a full
            // per-second series and no screen had ever drawn one, which is what
            // makes recording it worth doing in the first place.
            RideChartsSection(
                charts = state.charts,
                rivals = state.rivals,
                ghost = state.ghost,
                onPickRival = viewModel::showGhost,
                isGuestRide = state.workout?.userId == null
            )

            Spacer(Modifier.size(MaterialTheme.spacing.extraLarge))

            RpeEditor(
                selected = workout.rpeRating,
                onSelect = viewModel::setRpe,
                modifier = Modifier.loneCard()
            )

            Spacer(Modifier.size(MaterialTheme.spacing.extraLarge))

            ExportSection(
                onExport = export,
                detailSec = state.workout?.metricsDetailSec ?: 1,
                modifier = Modifier.loneCard()
            )

            Spacer(Modifier.size(MaterialTheme.spacing.extraLarge))
        }
    }
}

/**
 * The ride is not entirely trustworthy, and this says so (2.7.5).
 *
 * Shown only for the two rides recorded on the bike before the frame fix, and
 * for nothing recorded after it. Three things it deliberately does:
 *
 * - **Says nothing has been changed.** The samples and the stored averages are
 *   exactly as recorded. A rider who exports this ride gets every row of it.
 * - **Puts both averages side by side** rather than replacing one with the
 *   other. The stored figure is what the app told the rider on the day and it
 *   is part of the record; the corrected one is what the surviving samples say.
 * - **Names it as the app's fault**, because it was. The bike was fine.
 */
@Composable
private fun SuspectSamplesNotice(
    integrity: RideIntegrity,
    storedAvgCadence: Double?,
    storedAvgPower: Double?
) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = MaterialTheme.expressiveShapes.large,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(MaterialTheme.spacing.medium)) {
            Text(
                text = "Some of this ride's samples are impossible",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.size(MaterialTheme.spacing.small))
            Text(
                text = "${integrity.impossibleSamples} of ${integrity.totalSamples} " +
                    "seconds (${integrity.percentImpossible.roundToInt()}%) hold values no " +
                    "bike can produce. Pelonot recorded this ride while it still trusted " +
                    "the labels on the sensor stream, and those labels can slide — so " +
                    "cadence, resistance and power ended up in each other's columns. " +
                    "Rides recorded since read the sensor board's own frames instead, " +
                    "and cannot do this.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.size(MaterialTheme.spacing.small))
            Text(
                text = "Nothing has been altered: every sample and every total above is " +
                    "exactly as it was recorded. The charts below are drawn from the " +
                    "${integrity.cleanSamples} seconds that survive.",
                style = MaterialTheme.typography.bodySmall
            )

            val corrected = listOfNotNull(
                correctedAverage("Cadence", storedAvgCadence, integrity.cleanAvgCadenceRpm, "rpm"),
                correctedAverage("Power", storedAvgPower, integrity.cleanAvgPowerWatts, "W")
            )
            if (corrected.isNotEmpty()) {
                Spacer(Modifier.size(MaterialTheme.spacing.small))
                corrected.forEach { line ->
                    Text(text = line, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

/** `Cadence: 109 rpm recorded, 61 rpm over the samples that survive.` */
private fun correctedAverage(
    label: String,
    stored: Double?,
    clean: Double?,
    unit: String
): String? {
    if (stored == null || clean == null) return null
    // A ride whose corrupted samples happened not to move the average is not
    // worth two numbers; the count above already says they are there.
    if (stored.roundToInt() == clean.roundToInt()) return null
    return "$label: ${stored.roundToInt()} $unit recorded, " +
        "${clean.roundToInt()} $unit over the samples that survive."
}

/**
 * Taking your own data with you (12.4.3).
 *
 * This is an open-source app, and not being able to get your ride out of it is
 * the thing the subscription product does.
 */
@Composable
private fun ExportSection(
    onExport: (ExportFormat) -> Unit,
    /** See [com.pelonot.domain.chart.RideCharts.detailSec] — 1 for an intact ride. */
    detailSec: Int = 1,
    modifier: Modifier = Modifier
) {
  Column(modifier) {
    Text(
        text = "Take it with you",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.semantics { heading() }
    )
    Spacer(Modifier.size(MaterialTheme.spacing.small))
    ExportFormat.entries.forEach { format ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = format.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    // 23.4.3. *"Every recorded second"* stops being true the
                    // moment a ride is condensed, and this is the button that
                    // takes the file out of the app — the one place a wrong
                    // claim cannot be corrected later.
                    text = if (detailSec > 1 && format == ExportFormat.Csv) {
                        "Every stored point — this ride is a $detailSec-second outline"
                    } else {
                        format.description
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedButton(
                onClick = { onExport(format) },
                shape = MaterialTheme.expressiveShapes.pill
            ) {
                Text("Save")
            }
        }
        Spacer(Modifier.size(MaterialTheme.spacing.small))
    }
  }
}

/**
 * Whose ride was this? — asked again, long after the summary closed (12.4.1).
 *
 * The post-ride screen asks it once, and a rider who walked away without
 * answering could not get back to it: a guest ride records a null `user_id`,
 * every query on `workouts` is filtered to a profile, and the ride therefore
 * appeared on no screen in the app. This is the second chance, reached from the
 * *Not filed against anyone* section at the top of history.
 *
 * **It does not offer to create a profile, and the summary screen does.** That
 * is deliberate rather than an omission: on the summary screen a guest is
 * deciding whether to become a rider, which is the moment for it; here the ride
 * is already safe, and every rider who could claim it already has a profile. So
 * there is one copy of *create a profile and file this ride to it* rather than
 * two, and a bike with no profiles at all is told to make one rather than shown
 * an empty row.
 *
 * There is no *Keep it as a guest ride* either. Doing nothing already is that,
 * and a button whose effect is to leave the screen exactly as it was is a
 * decision the rider is being asked to make twice.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClaimRideSection(
    profiles: List<UserEntity>,
    onClaim: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        Text(
            text = "Whose ride was this?",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            text = if (profiles.isEmpty()) {
                "It was ridden as a guest, so it belongs to nobody. Create a " +
                    "profile and you can file it against one."
            } else {
                "It was ridden as a guest. Filing it against a profile keeps it " +
                    "in that rider's history and counts towards their FTP."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.readableText()
        )

        if (profiles.isNotEmpty()) {
            Spacer(Modifier.size(MaterialTheme.spacing.medium))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)
            ) {
                profiles.forEach { profile ->
                    FilledTonalButton(
                        onClick = { onClaim(profile.localUserId) },
                        modifier = Modifier.sizeIn(minHeight = MIN_TOUCH_TARGET),
                        shape = MaterialTheme.expressiveShapes.pill
                    ) {
                        Text(profile.name)
                    }
                }
            }
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
private fun RpeEditor(
    selected: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    // 26.3. The same three answers as the post-ride summary, on purpose: a
    // rider who answered "A good workout" on the night must not come back a
    // month later to a screen offering them a 7 instead. `PerceivedEffort.of`
    // is what lets a ride rated on the old ten-point scale read back as one of
    // the three without anything having been rewritten on disk.
    val chosen = PerceivedEffort.of(selected)

    Column(modifier) {
        Text(
            text = "How did it feel?",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            text = if (selected == null) {
                "You didn't answer for this one — you still can"
            } else {
                "Tap a different answer to change it"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.size(MaterialTheme.spacing.medium))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)
        ) {
            PerceivedEffort.entries.forEach { effort ->
                val isSelected = chosen == effort
                FilledTonalButton(
                    onClick = { onSelect(effort.rating) },
                    modifier = Modifier
                        .weight(1f)
                        .sizeIn(minHeight = EFFORT_BUTTON_HEIGHT)
                        .semantics {
                            contentDescription = "${effort.label}. ${effort.detail}"
                        },
                    shape = MaterialTheme.expressiveShapes.pill,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 12.dp,
                        vertical = 12.dp
                    ),
                    colors = if (isSelected) {
                        ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        ButtonDefaults.filledTonalButtonColors()
                    }
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = effort.label,
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = effort.detail,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            color = LocalContentColor.current.copy(alpha = 0.75f)
                        )
                    }
                }
            }
        }
    }
}

/** Two lines of text and a comfortable target. Matches the summary screen's. */
private val EFFORT_BUTTON_HEIGHT = 72.dp

private val MIN_TOUCH_TARGET = 48.dp
