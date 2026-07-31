package com.pelonot.ui.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pelonot.R
import com.pelonot.core.Formatters
import com.pelonot.data.repository.ClassPlan
import com.pelonot.data.service.RideSnapshot
import com.pelonot.domain.model.RideCue
import com.pelonot.domain.model.RideIntent
import com.pelonot.domain.model.TargetBand
import com.pelonot.ui.components.CountdownBanner
import com.pelonot.ui.components.IntervalTimeline
import com.pelonot.ui.components.MetricReadout
import com.pelonot.ui.components.NextUpPreview
import com.pelonot.ui.components.ProgressArc
import com.pelonot.ui.components.UpcomingIntervals
import com.pelonot.ui.components.ZoneGlyph
import com.pelonot.ui.components.attentionBounce
import com.pelonot.ui.components.rememberPulse
import com.pelonot.ui.overlay.AppForeground
import com.pelonot.ui.theme.MetricCadenceCyan
import com.pelonot.ui.theme.MetricHeartRateGreen
import com.pelonot.ui.theme.MetricPowerCoral
import com.pelonot.ui.theme.MetricResistanceViolet
import com.pelonot.ui.theme.color
import com.pelonot.ui.theme.expressiveShapes
import com.pelonot.ui.theme.spacing
import com.pelonot.ui.theme.units
import com.pelonot.ui.viewmodel.RideUiState
import com.pelonot.ui.viewmodel.RideViewModel

/**
 * The live ride screen, laid out for the 21.5" landscape tablet the app
 * actually runs on.
 *
 * It shows the same information as the floating HUD and in the same order, so
 * a rider who switches to their video app is not relearning a layout — the
 * strip they get there is this screen compressed to one edge.
 *
 * Navigation away from the ride is driven by the service's own state rather
 * than by this screen's buttons: a ride ended from the HUD, or by the class
 * timer running out, takes exactly the same path as one ended here.
 */
@Composable
fun RideScreen(
    plan: ClassPlan?,
    intent: RideIntent,
    ftp: Int,
    userId: Int? = null,
    onEndRide: (workoutId: String?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RideViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(plan?.id, intent, ftp) {
        viewModel.startRide(
            userId = userId,
            classId = plan?.id,
            intent = intent,
            ftpWatts = ftp
        )
    }

    // The overlay stands down while this screen is on top and comes back the
    // moment the rider switches to whatever they are watching.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.setScreenVisible(true)
                Lifecycle.Event.ON_STOP -> viewModel.setScreenVisible(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.setScreenVisible(false)
        }
    }

    LaunchedEffect(state.isFinished) {
        if (state.isFinished) onEndRide(viewModel.consumeFinishedWorkoutId())
    }

    // 19.1.1. A tablet that sleeps mid-class is experienced as the app being
    // broken, and this screen is exactly the case the HUD's own
    // FLAG_KEEP_SCREEN_ON does not cover: while the ride screen is on top the
    // overlay has stood down, so nothing else is holding the screen awake.
    //
    // Held through a pause as well as through the ride. A rider refilling a
    // bottle has not left — coming back to a black tablet and having to wake it
    // with wet hands is the same defect in a smaller costume.
    val view = LocalView.current
    DisposableEffect(view, state.isFinished) {
        view.keepScreenOn = !state.isFinished
        onDispose { view.keepScreenOn = false }
    }

    if (state.overlayPermissionNeeded) {
        OverlayPermissionDialog(
            onGrant = viewModel::requestOverlayPermission,
            onNotNow = viewModel::dismissOverlayPrompt,
            onNever = viewModel::disableHud
        )
    }

    // 11.1a.2: the other half of the door. Sending the task to the back returns
    // the rider to whatever they were watching, and the HUD comes back with it
    // — the overlay stands down only while this screen is on top.
    val context = LocalContext.current

    RideContent(
        state = state,
        fallbackTitle = plan?.title ?: "Just Ride",
        subtitle = plan?.let { "${Formatters.minutes(it.durationSec)} · ${it.category}" }
            ?: "Free ride — no intervals, no pressure",
        onPause = viewModel::pause,
        onResume = viewModel::resume,
        onEnd = viewModel::endRide,
        onBackToHud = { AppForeground.sendToBack(context) },
        modifier = modifier
    )
}

/**
 * Asked at ride start, because the alternative is a rider discovering
 * mid-class that the HUD they expected over their film is simply absent.
 */
@Composable
private fun OverlayPermissionDialog(
    onGrant: () -> Unit,
    onNotNow: () -> Unit,
    onNever: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onNotNow,
        title = { Text("Show your ride over other apps?") },
        text = {
            Text(
                "Pelonot can dock your metrics, targets and interval countdown to " +
                    "the edge of the screen while you watch something else. " +
                    "Android needs your permission to draw over other apps first."
            )
        },
        confirmButton = { TextButton(onClick = onGrant) { Text("Open settings") } },
        dismissButton = {
            Row {
                TextButton(onClick = onNever) { Text("Don't use the HUD") }
                TextButton(onClick = onNotNow) { Text("Not now") }
            }
        }
    )
}

@Composable
private fun RideContent(
    state: RideUiState,
    fallbackTitle: String,
    subtitle: String,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onEnd: () -> Unit,
    onBackToHud: () -> Unit,
    modifier: Modifier = Modifier
) {
    val snapshot = state.snapshot
    val interval = snapshot.interval
    val zone = interval.targetZone

    val accent by animateColorAsState(
        targetValue = if (interval.hasClass) zone.color else MaterialTheme.colorScheme.primary,
        animationSpec = spring(stiffness = Spring.StiffnessVeryLow),
        label = "RideAccent"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            // The whole screen carries the effort: a wash of the current zone's
            // colour, deep enough to notice from across a room and shallow
            // enough to keep white text legible.
            .background(
                Brush.verticalGradient(
                    listOf(
                        accent.copy(alpha = 0.16f),
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.background
                    )
                )
            )
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val availableWidth = maxWidth
            val landscape = availableWidth > maxHeight && availableWidth >= 720.dp

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(MaterialTheme.spacing.large)
            ) {
                RideHeader(state, snapshot, fallbackTitle, subtitle)

                if (interval.hasClass) {
                    Spacer(Modifier.height(MaterialTheme.spacing.medium))
                    IntervalTimeline(
                        intervals = snapshot.intervals,
                        elapsedSec = interval.classElapsedSec,
                        durationSec = interval.classDurationSec,
                        currentIndex = interval.index,
                        height = 14.dp
                    )
                }

                Spacer(Modifier.height(MaterialTheme.spacing.large))

                if (landscape) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.large)
                    ) {
                        EffortColumn(
                            state = state,
                            accent = accent,
                            modifier = Modifier
                                .width(if (availableWidth >= 1000.dp) 360.dp else 300.dp)
                                .fillMaxHeight()
                        )
                        MetricGrid(
                            state = state,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                        UpNextColumn(
                            state = state,
                            onPause = onPause,
                            onResume = onResume,
                            onEnd = onEnd,
                            onBackToHud = onBackToHud,
                            modifier = Modifier
                                .width(if (availableWidth >= 1000.dp) 320.dp else 260.dp)
                                .fillMaxHeight()
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)
                    ) {
                        EffortColumn(state, accent, Modifier.fillMaxWidth())
                        MetricGrid(state, Modifier.fillMaxWidth().weight(1f))
                        UpNextColumn(
                            state = state,
                            onPause = onPause,
                            onResume = onResume,
                            onEnd = onEnd,
                            onBackToHud = onBackToHud,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RideHeader(
    state: RideUiState,
    snapshot: RideSnapshot,
    fallbackTitle: String,
    subtitle: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = snapshot.classTitle ?: fallbackTitle,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        TelemetryChip(state)
    }
}

/**
 * What the numbers on this screen actually are (2.4.5).
 *
 * Three states, and only one of them used to be shown. The bike going quiet
 * was rendered as nothing at all: `SensorStatus.Reconnecting` was constructed,
 * logged and read by no one, so a rider in Hardware mode with a dead board saw
 * four frozen numbers and no reason for them. Silence is the state that most
 * needs saying, because it is the one where the screen looks fine.
 *
 * It says what is happening to the *record* as well as to the screen — "not
 * recording" is the part a rider will want to know afterwards, and it is the
 * truth as of 2.4.4.
 */
@Composable
private fun TelemetryChip(state: RideUiState) {
    val message = when {
        !state.snapshot.telemetryLive -> "No signal from the bike — not recording"
        state.isReconnecting -> "Reconnecting to the bike…"
        state.isSimulated -> "Simulated telemetry — no bike connected"
        else -> null
    } ?: return

    // Amber, never red: 8.11.82's argument applies here too — this is "look at
    // this", not "something is broken beyond repair", and the ride goes on.
    AssistChip(
        onClick = {},
        enabled = false,
        label = { Text(message) },
        colors = AssistChipDefaults.assistChipColors(
            disabledLabelColor = MaterialTheme.colorScheme.onTertiaryContainer,
            disabledContainerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    )
}

/** Clock, zone and how long is left of this effort. */
@Composable
private fun EffortColumn(
    state: RideUiState,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val snapshot = state.snapshot
    val interval = snapshot.interval

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)
    ) {
        Column {
            Text(
                text = Formatters.duration(snapshot.elapsedSeconds),
                fontSize = 84.sp,
                lineHeight = 84.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-4).sp,
                color = if (state.isPaused) {
                    MaterialTheme.colorScheme.tertiary
                } else {
                    MaterialTheme.colorScheme.onBackground
                },
                maxLines = 1
            )
            Text(
                text = when {
                    state.isPaused -> "PAUSED"
                    interval.hasClass ->
                        "${Formatters.duration(interval.classRemainingSec)} REMAINING"
                    else -> "ELAPSED"
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (state.isPaused) {
                    MaterialTheme.colorScheme.tertiary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }

        if (interval.hasClass) {
            Card(
                shape = MaterialTheme.expressiveShapes.container,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .attentionBounce(trigger = interval.index)
            ) {
                Row(
                    modifier = Modifier.padding(MaterialTheme.spacing.large),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ProgressArc(
                        // Drains rather than fills: the rider wants to know how
                        // much of this effort is left, not how much is done.
                        progress = 1f - interval.intervalProgress,
                        color = accent,
                        strokeWidth = 8.dp,
                        modifier = Modifier.size(112.dp)
                    ) {
                        ZoneGlyph(
                            zone = interval.targetZone,
                            modifier = Modifier.size(78.dp),
                            rotating = interval.targetZone.number >= 5
                        ) {
                            Text(
                                text = "${interval.targetZone.number}",
                                fontSize = 34.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.Black.copy(alpha = 0.8f)
                            )
                        }
                    }

                    Spacer(Modifier.width(MaterialTheme.spacing.large))

                    Column {
                        Text(
                            text = interval.targetZone.displayName.uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            color = accent,
                            fontWeight = FontWeight.Black,
                            maxLines = 2
                        )
                        Text(
                            text = Formatters.duration(interval.remainingInIntervalSec),
                            fontSize = 40.sp,
                            lineHeight = 42.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = (-2).sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "INTERVAL ${interval.index + 1} OF ${interval.intervalCount}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            CueBanner(interval.cue, accent)
        }

        Spacer(Modifier.weight(1f))

        RideTotals(state, Modifier.fillMaxWidth())
    }
}

@Composable
private fun CueBanner(cue: RideCue, accent: Color) {
    AnimatedVisibility(
        visible = cue != RideCue.None,
        enter = fadeIn() + scaleIn(initialScale = 0.9f),
        exit = fadeOut() + scaleOut(targetScale = 0.9f)
    ) {
        val pulse = if (cue == RideCue.FinalPush) {
            rememberPulse(periodMs = 1100, from = 0.5f, to = 1f)
        } else {
            1f
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    accent.copy(alpha = 0.18f * pulse),
                    MaterialTheme.expressiveShapes.large
                )
                .padding(MaterialTheme.spacing.medium),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = cue.message.uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = accent,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp
            )
        }
    }
}

/**
 * The three live numbers, each with the band it is being measured against.
 *
 * All three get equal weight and the full height of the screen. Totals live in
 * the left column instead: output and distance are numbers a rider looks at
 * once at the end, and giving them the same visual weight as cadence would be
 * a lie about how the screen is used.
 */
@Composable
private fun MetricGrid(state: RideUiState, modifier: Modifier = Modifier) {
    val snapshot = state.snapshot
    val hasTargets = snapshot.interval.hasClass

    // 2.4.5. When the board stops reporting, the last reading it sent stays on
    // the flow and these tiles go on showing it, 104 sp tall, indistinguishable
    // from a rider holding a perfectly steady 88 rpm. Unknown gets the dashes
    // that heart rate has always had.
    val live = snapshot.telemetryLive

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)
    ) {
        // The two inputs, given the most room: cadence and resistance are the
        // only things the rider can actually change.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)
        ) {
            RideMetricTile(
                label = "CADENCE",
                value = if (live) state.reading.cadenceRpm.toInt().toString() else NO_READING,
                unit = "rpm",
                accent = MetricCadenceCyan,
                band = if (hasTargets) snapshot.cadenceTarget else TargetBand.NONE,
                rawValue = state.reading.cadenceRpm,
                modifier = Modifier.weight(1f)
            )
            RideMetricTile(
                label = "RESISTANCE",
                value = if (live) state.reading.resistancePercent.toInt().toString() else NO_READING,
                unit = "%",
                accent = MetricResistanceViolet,
                band = if (hasTargets) snapshot.resistanceTarget else TargetBand.NONE,
                rawValue = state.reading.resistancePercent,
                modifier = Modifier.weight(1f)
            )
        }

        // And the two outputs.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.72f),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)
        ) {
            RideMetricTile(
                label = "POWER",
                value = if (live) state.reading.powerWatts.toInt().toString() else NO_READING,
                unit = "watts",
                accent = MetricPowerCoral,
                band = if (hasTargets) snapshot.powerTarget else TargetBand.NONE,
                rawValue = state.reading.powerWatts,
                valueSize = 76.sp,
                modifier = Modifier.weight(1f)
            )
            RideMetricTile(
                label = "HEART RATE",
                // Null means no strap, never a measured zero. The strap is its
                // own radio, so it is not silenced by the bike going quiet.
                value = state.reading.heartRateBpm?.toString() ?: NO_READING,
                unit = "bpm",
                accent = MetricHeartRateGreen,
                band = TargetBand.NONE,
                rawValue = (state.reading.heartRateBpm ?: 0).toDouble(),
                valueSize = 76.sp,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** Ride totals — glanced at once at the end, sized accordingly. */
@Composable
private fun RideTotals(state: RideUiState, modifier: Modifier = Modifier) {
    val snapshot = state.snapshot
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)
    ) {
        SmallStat(
            label = "OUTPUT",
            value = String.format(java.util.Locale.US, "%.1f", snapshot.totalOutputKj),
            unit = "kJ",
            accent = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f)
        )
        SmallStat(
            label = "DISTANCE",
            value = Formatters.distanceValue(snapshot.distanceKm, MaterialTheme.units),
            unit = MaterialTheme.units.distanceLabel,
            accent = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f)
        )
        SmallStat(
            label = "AVG POWER",
            value = (state.session?.avgPower ?: 0.0).toInt().toString(),
            unit = "W",
            accent = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun RideMetricTile(
    label: String,
    value: String,
    unit: String,
    accent: Color,
    band: TargetBand,
    rawValue: Double,
    modifier: Modifier = Modifier,
    valueSize: androidx.compose.ui.unit.TextUnit = 104.sp
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.expressiveShapes.container,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(MaterialTheme.spacing.large),
            contentAlignment = Alignment.CenterStart
        ) {
            MetricReadout(
                label = label,
                value = value,
                unit = unit,
                accent = accent,
                band = band,
                rawValue = rawValue,
                // Sized for a 21-inch screen read from a metre away, mid-effort.
                valueSize = valueSize,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun SmallStat(
    label: String,
    value: String,
    unit: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.expressiveShapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MaterialTheme.spacing.medium)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    fontSize = 34.sp,
                    lineHeight = 36.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-1).sp,
                    color = accent,
                    maxLines = 1,
                    // Weighted, and `fill = false` so it still only takes what
                    // it needs. A Row measures its unweighted children first,
                    // so an unweighted "0.20" claimed the whole tile and left
                    // the label beside it clipped to "m" — a distance in miles
                    // reading as metres, which is worse than no label at all.
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = unit,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    // "km" was wrapping to "k / m" in a narrow tile.
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(bottom = 5.dp)
                )
            }
        }
    }
}

/** What is coming, and the controls. */
@Composable
private fun UpNextColumn(
    state: RideUiState,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onEnd: () -> Unit,
    onBackToHud: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interval = state.snapshot.interval
    val next = interval.next

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)
    ) {
        if (next != null) {
            AnimatedContent(
                targetState = interval.isChangeImminent,
                transitionSpec = {
                    (fadeIn() + scaleIn(initialScale = 0.85f))
                        .togetherWith(fadeOut() + scaleOut(targetScale = 0.85f))
                },
                label = "RideNextOrCountdown"
            ) { imminent ->
                if (imminent) {
                    CountdownBanner(
                        secondsRemaining = interval.remainingInIntervalSec,
                        nextZone = next.powerZone
                    )
                } else {
                    NextUpPreview(
                        next = next,
                        secondsUntil = interval.remainingInIntervalSec,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        if (interval.hasClass) {
            UpcomingIntervals(
                intervals = state.snapshot.intervals,
                fromIndex = interval.index,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.weight(1f))

        // 11.1a.2. Above pause and end because it is by far the most frequent
        // of the three during a class — the rider is going back to their film,
        // not stopping. Hidden when there is no HUD to go back to, since it
        // would then just be a button that hides the app.
        if (state.hudAvailable) {
            OutlinedButton(
                onClick = onBackToHud,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp),
                shape = MaterialTheme.expressiveShapes.pill
            ) {
                Icon(imageVector = Icons.Default.PictureInPictureAlt, contentDescription = null)
                Spacer(Modifier.width(MaterialTheme.spacing.small))
                Text(
                    text = stringResource(R.string.ride_back_to_hud),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }

        Button(
            onClick = if (state.isPaused) onResume else onPause,
            modifier = Modifier
                .fillMaxWidth()
                // Pressed with sweaty hands, while moving.
                .heightIn(min = 72.dp),
            shape = MaterialTheme.expressiveShapes.pill,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
        ) {
            Icon(
                imageVector = if (state.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                contentDescription = null
            )
            Spacer(Modifier.width(MaterialTheme.spacing.small))
            Text(
                text = stringResource(
                    if (state.isPaused) R.string.cd_resume_ride else R.string.cd_pause_ride
                ),
                style = MaterialTheme.typography.titleMedium
            )
        }

        Button(
            onClick = onEnd,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp),
            shape = MaterialTheme.expressiveShapes.pill,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            )
        ) {
            Icon(imageVector = Icons.Default.Stop, contentDescription = null)
            Spacer(Modifier.width(MaterialTheme.spacing.small))
            Text(
                text = stringResource(R.string.cd_end_ride),
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

/**
 * Shown in place of a metric the bike is no longer reporting (2.4.5) — the
 * same two dashes an absent heart-rate strap has always used.
 */
private const val NO_READING = "--"
