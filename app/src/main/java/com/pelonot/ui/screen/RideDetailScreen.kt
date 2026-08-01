package com.pelonot.ui.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pelonot.R
import com.pelonot.domain.chart.RideChartSummaries
import com.pelonot.domain.chart.RideCharts
import com.pelonot.domain.chart.RideIntegrity
import com.pelonot.domain.export.ExportFormat
import com.pelonot.domain.model.PowerProvenance
import com.pelonot.ui.components.CadenceDistributionChart
import com.pelonot.ui.components.ChartCard
import com.pelonot.ui.components.HeartRateTraceChart
import com.pelonot.ui.components.PowerTraceChart
import com.pelonot.ui.components.RideSummaryCard
import com.pelonot.ui.components.TimeInZoneBar
import com.pelonot.ui.theme.expressiveShapes
import com.pelonot.ui.theme.spacing
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

            state.charts?.integrity?.takeIf { it.isSuspect }?.let { integrity ->
                Spacer(Modifier.size(MaterialTheme.spacing.medium))
                SuspectSamplesNotice(
                    integrity = integrity,
                    storedAvgCadence = workout.avgCadence,
                    storedAvgPower = workout.avgPower
                )
            }

            Spacer(Modifier.size(MaterialTheme.spacing.extraLarge))

            // 16.1. Every ride since the foreign-key fix has written a full
            // per-second series and no screen had ever drawn one, which is what
            // makes recording it worth doing in the first place.
            RideChartsSection(state.charts)

            Spacer(Modifier.size(MaterialTheme.spacing.extraLarge))

            RpeEditor(selected = workout.rpeRating, onSelect = viewModel::setRpe)

            Spacer(Modifier.size(MaterialTheme.spacing.extraLarge))

            ExportSection(onExport = export)

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
 * The four charts, laid out for the width this app actually has.
 *
 * Two columns on the tablet — 1280 dp is a great deal of width to spend on one
 * 140 dp chart at a time — and a single column anywhere narrower.
 */
@Composable
private fun RideChartsSection(charts: RideCharts?) {
    if (charts == null) {
        // Distinguished from "this ride recorded nothing", which is a
        // different sentence and a permanent one.
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.size(MaterialTheme.spacing.small))
            Text(
                text = "Working out how the ride went…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    if (!charts.hasAnything) {
        Text(
            text = "This ride has no second-by-second record — only its totals. " +
                "Rides recorded before the app started keeping one look like this.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    BoxWithConstraints {
        val twoUp = maxWidth >= TWO_COLUMN_BREAKPOINT

        Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)) {
            if (twoUp) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)
                ) {
                    PowerCard(charts, Modifier.weight(1f))
                    HeartCard(charts, Modifier.weight(1f))
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)
                ) {
                    CadenceCard(charts, Modifier.weight(1f))
                    ZoneCard(charts, Modifier.weight(1f))
                }
            } else {
                PowerCard(charts, Modifier.fillMaxWidth())
                HeartCard(charts, Modifier.fillMaxWidth())
                CadenceCard(charts, Modifier.fillMaxWidth())
                ZoneCard(charts, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun PowerCard(charts: RideCharts, modifier: Modifier) = ChartCard(
    title = "Power",
    // 16.1.6, and it now reads from where the watts actually came from:
    // `workout_metrics.power_is_measured` records it per sample. A ride from
    // before that column existed says "estimated", which is the safe
    // direction — the rule this project cares about is never presenting a
    // modelled watt as a measured one, and "we did not write it down" is not
    // grounds for claiming a measurement.
    caption = listOfNotNull(
        when (charts.powerProvenance) {
            PowerProvenance.Measured -> "Measured by the bike"
            PowerProvenance.Mixed ->
                "Partly measured — the bike's sensor dropped out during this ride"
            else -> "Estimated from cadence and resistance — see Settings"
        },
        // Only said when there are blocks to explain. On a free ride there is
        // no prescription and no legend for one.
        "blocks are what the class asked for".takeUnless { charts.prescribed.isEmpty }
    ).joinToString(" · "),
    summary = listOf(
        RideChartSummaries.power(charts.power, charts.powerProvenance),
        RideChartSummaries.prescribed(charts.prescribed)
    ).filter { it.isNotEmpty() }.joinToString(" "),
    modifier = modifier
) {
    PowerTraceChart(
        trace = charts.power,
        ftpWatts = charts.ftpWatts,
        prescribed = charts.prescribed
    )
}

@Composable
private fun HeartCard(charts: RideCharts, modifier: Modifier) = ChartCard(
    title = "Heart rate",
    summary = RideChartSummaries.heartRate(charts.heartRate),
    modifier = modifier
) {
    HeartRateTraceChart(trace = charts.heartRate)
}

@Composable
private fun CadenceCard(charts: RideCharts, modifier: Modifier) = ChartCard(
    title = "Cadence",
    caption = "How long was spent at each cadence",
    summary = RideChartSummaries.cadence(charts.cadence),
    modifier = modifier
) {
    CadenceDistributionChart(distribution = charts.cadence)
}

@Composable
private fun ZoneCard(charts: RideCharts, modifier: Modifier) = ChartCard(
    title = "Time in zone",
    summary = RideChartSummaries.timeInZone(charts.timeInZone),
    modifier = modifier
) {
    TimeInZoneBar(timeInZone = charts.timeInZone)
}

/**
 * Taking your own data with you (12.4.3).
 *
 * This is an open-source app, and not being able to get your ride out of it is
 * the thing the subscription product does.
 */
@Composable
private fun ExportSection(onExport: (ExportFormat) -> Unit) {
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
                    text = format.description,
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

private val TWO_COLUMN_BREAKPOINT = 900.dp

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
