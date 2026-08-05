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
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import com.pelonot.domain.model.IntervalState
import com.pelonot.domain.model.RideCue
import com.pelonot.domain.model.RideIntent
import com.pelonot.domain.model.LiveStanding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import com.pelonot.domain.model.LiveLeaderboard
import com.pelonot.domain.model.LiveStandings
import com.pelonot.domain.model.RaceMetric
import com.pelonot.domain.model.RivalStatus
import com.pelonot.domain.model.TargetBand
import com.pelonot.domain.model.TargetEmphasis
import com.pelonot.ui.components.BeatingHeart
import com.pelonot.ui.components.CountdownBanner
import com.pelonot.ui.components.IntervalTimeline
import com.pelonot.ui.components.RidePositionCall
import com.pelonot.ui.components.MetricIcons
import com.pelonot.ui.components.MetricReadout
import com.pelonot.ui.components.NextUpPreview
import com.pelonot.ui.components.PowerZoneScale
import com.pelonot.ui.components.ProgressArc
import com.pelonot.ui.components.ShrinkToFitText
import com.pelonot.ui.components.UpcomingIntervals
import com.pelonot.ui.components.ZoneGlyph
import com.pelonot.ui.components.attentionBounce
import com.pelonot.ui.components.rememberPulse
import com.pelonot.ui.overlay.AppForeground
import com.pelonot.ui.permission.RequestRideNotificationPermission
import com.pelonot.ui.theme.MetricCadenceCyan
import com.pelonot.ui.theme.MetricHeartRateGreen
import com.pelonot.ui.theme.MetricPowerCoral
import com.pelonot.ui.theme.MetricResistanceViolet
import com.pelonot.ui.theme.color
import com.pelonot.ui.theme.expressiveShapes
import com.pelonot.ui.overlay.RideSettingsRequest
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
    /** Non-null re-enters an interrupted ride instead of starting a new one (8.3d). */
    resumeWorkoutId: String? = null,
    /** The ride being raced live, chosen before the class started (24.3.3). */
    rivalWorkoutId: String? = null,
    onEndRide: (workoutId: String?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RideViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // 11.6.13. The countdown is a gate in front of the ride, not a curtain over
    // one: nothing below runs until it clears, so the clock, the first interval
    // and the recorder all start when the rider is actually on the bike.
    //
    // A resume skips it. Somebody re-entering a ride they were thrown out of is
    // already on the bike, and 8.3d's whole argument is that the ride never
    // really stopped.
    var countdownCleared by rememberSaveable { mutableStateOf(resumeWorkoutId != null) }
    if (!countdownCleared) {
        // 11.6.14. The overlay permission is asked for *inside* the countdown
        // rather than by `startRide` on the far side of it. The owner's note is
        // the whole reasoning: the ten seconds a rider spends clipping in used
        // to buy them a modal and a trip to Android's settings, and a class
        // whose clock had already started.
        //
        // Asked here and re-asked on every return, because granting it happens
        // in another app entirely — and this branch returns before the
        // lifecycle observer below, so it needs its own.
        LaunchedEffect(Unit) { viewModel.refreshOverlayPermission() }
        OnResume { viewModel.refreshOverlayPermission() }

        if (state.overlayPermissionNeeded) {
            OverlayPermissionDialog(
                onGrant = viewModel::requestOverlayPermission,
                onNotNow = viewModel::dismissOverlayPrompt,
                onNever = viewModel::disableHud
            )
        }

        RideCountdownScreen(
            plan = plan,
            // The count stops while the question is outstanding — through the
            // dialog *and* through the trip out of the app to answer it.
            // Coming back to "2" and then straight into a class is the same
            // defect wearing the other costume.
            paused = state.overlayPermissionNeeded || state.awaitingOverlayGrant,
            onStart = { countdownCleared = true },
            modifier = modifier
        )
        return
    }

    LaunchedEffect(plan?.id, intent, ftp, resumeWorkoutId) {
        // A resume must never fall through to startRide: that would mint a
        // second ride while the first is still sitting incomplete, which is the
        // orphan this whole flow exists to clear up.
        if (resumeWorkoutId != null) {
            viewModel.resumeRide(resumeWorkoutId)
        } else {
            viewModel.startRide(
                userId = userId,
                classId = plan?.id,
                intent = intent,
                ftpWatts = ftp,
                rivalWorkoutId = rivalWorkoutId
            )
        }
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

    // 11.1a.6. The ongoing ride notification is the route back into a class the
    // Activity has been destroyed under, and on API 33+ it is never posted
    // until this is asked for. Asked here rather than at launch because a rider
    // who has never started a ride has nothing to say yes to, and held back
    // while the overlay prompt is up so the two do not stack.
    RequestRideNotificationPermission(deferred = state.overlayPermissionNeeded)

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
 * Runs [block] each time this screen comes back to the foreground.
 *
 * Its one caller is the countdown branch (11.6.14), which returns before the
 * ride's own lifecycle observer is registered and so has no other way to notice
 * a rider coming back from Android's overlay settings.
 */
@Composable
private fun OnResume(block: () -> Unit) {
    val current by rememberUpdatedState(block)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) current()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

/**
 * Asked during the pre-ride countdown, because the alternative is a rider
 * discovering mid-class that the HUD they expected over their film is simply
 * absent — and, before 11.6.14, being asked at the instant the clock started.
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
        // 11.6.5. Names the thing the same way the button and Settings do.
        text = {
            Text(
                "Pelonot can dock your metrics, targets and interval countdown to " +
                    "the edge of the screen while you watch something else. Android " +
                    "needs your permission to draw over other apps first."
            )
        },
        confirmButton = { TextButton(onClick = onGrant) { Text("Open settings") } },
        dismissButton = {
            Row {
                TextButton(onClick = onNever) { Text("Don't use the overlay") }
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

    // 11.6.6. One tap on a 72 dp pill, pressed with sweaty hands while moving,
    // and the class is over — there is no resume. Asked here and not in the
    // service, so a class that runs out of intervals still ends by itself
    // without a dialog nobody is there to answer.
    var confirmingEnd by rememberSaveable { mutableStateOf(false) }

    // 11.6.10. A sheet over the ride, not a navigation away from it: every
    // route to these three settings used to cost the rider their class.
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    if (settingsOpen) {
        RideSettingsSheet(onDismiss = { settingsOpen = false })
    }

    // The overlay's half of the same door. It has no navigation of its own, so
    // it raises a flag and brings the app forward; this is where that lands.
    val settingsRequested by RideSettingsRequest.pending.collectAsStateWithLifecycle()
    LaunchedEffect(settingsRequested) {
        if (settingsRequested) {
            settingsOpen = true
            RideSettingsRequest.consume()
        }
    }

    if (confirmingEnd) {
        EndRideDialog(
            elapsedSec = snapshot.elapsedSeconds,
            remainingSec = if (interval.hasClass) interval.classRemainingSec else null,
            onConfirm = {
                confirmingEnd = false
                onEnd()
            },
            onDismiss = { confirmingEnd = false }
        )
    }

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
                RideHeader(
                    state = state,
                    snapshot = snapshot,
                    fallbackTitle = fallbackTitle,
                    subtitle = subtitle,
                    onOpenSettings = { settingsOpen = true }
                )

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
                            onPairHeartRate = { settingsOpen = true },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                        UpNextColumn(
                            state = state,
                            onPause = onPause,
                            onResume = onResume,
                            onEnd = { confirmingEnd = true },
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
                        MetricGrid(
                            state = state,
                            onPairHeartRate = { settingsOpen = true },
                            modifier = Modifier.fillMaxWidth().weight(1f)
                        )
                        UpNextColumn(
                            state = state,
                            onPause = onPause,
                            onResume = onResume,
                            onEnd = { confirmingEnd = true },
                            onBackToHud = onBackToHud,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

/**
 * Asked before a ride ends, because ending one cannot be taken back (11.6.6).
 *
 * The ride itself survives — it is saved, and the summary comes up — so this is
 * not the same danger as the delete in 12.3.2. What a mis-tap destroys is the
 * *rest of the class*: there is no resume, so a thumb landing here at minute 20
 * of 45 ends it at 20.
 *
 * Deliberately dismissible by tapping anywhere outside. It is raised mid-effort
 * and the overwhelmingly likely answer is "I did not mean that", so the cheap
 * gesture has to be the safe one — the opposite of the recovery prompt, which
 * refuses to go away because it genuinely needs an answer.
 */
@Composable
private fun EndRideDialog(
    elapsedSec: Int,
    remainingSec: Int?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("End the ride?") },
        text = {
            Text(
                buildString {
                    append("You're ${Formatters.duration(elapsedSec)} in. ")
                    // Naming what is left is the whole argument for asking: it
                    // is the part the rider is about to lose, and it is the
                    // number they do not have in their head mid-effort.
                    if (remainingSec != null && remainingSec > 0) {
                        append("There's ${Formatters.duration(remainingSec)} of the class left. ")
                    }
                    // 12.6.2 made the old second half of this sentence false —
                    // it said a ride can't be restarted, and now the summary
                    // offers to carry on. A dialog that overstates what is at
                    // stake is the kind riders learn to tap past.
                    append("Everything so far is saved either way.")
                }
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("End ride") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Keep riding") } }
    )
}

@Composable
private fun RideHeader(
    state: RideUiState,
    snapshot: RideSnapshot,
    fallbackTitle: String,
    subtitle: String,
    onOpenSettings: () -> Unit
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

        // 11.6.10. Small and out of the way — this is not a thing a rider
        // reaches for often, and the numbers are what the screen is for.
        IconButton(onClick = onOpenSettings) {
            Icon(
                imageVector = Icons.Outlined.Settings,
                contentDescription = "Settings, without ending the ride"
            )
        }
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
                    // 19.1.2. Why it stopped, and what lifts it. A ride that
                    // pauses itself without saying so reads as a bug.
                    state.isPaused && snapshot.autoPaused ->
                        "PAUSED — START PEDALLING TO RESUME"
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

        // 25.2.2. Only when the class asks for one, and loud only at the moment
        // it changes. Most intervals prescribe nothing and this draws nothing.
        RidePositionCall(position = interval.current?.position)

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

            // 11.6.1. What I am doing, and what I have to be ready for, are the
            // two things a rider reads together — and they used to sit at
            // opposite ends of a 1280 dp screen with the whole metric grid
            // between them, with nothing saying they were related. The next
            // effort now hangs directly off the current one.
            NextUpBlock(interval, Modifier.fillMaxWidth())

            // 24.3.13b, and it is 11.6.1's argument carried one step further:
            // *now*, *next* and *then* are one thought, and the rest of the
            // class was the only part of it still living in another column.
            // The owner's own reasoning for moving it — *"this then frees up
            // space for leaderboard which is where your eyes are naturally
            // drawn to anyway"*.
            // 11.6.18 / 11.6.16. It scrolls, it holds the whole rest of the
            // class rather than three blocks of it, and — the part that
            // matters most — **it is the weighted child of this column**.
            // Everything above it can grow: the countdown swaps in for the
            // next-up preview at every interval boundary, a position call
            // appears, a cue banner appears. That growth used to come off the
            // bottom, taking OUTPUT, DISTANCE and AVG POWER off the screen
            // without a word, because a Column clips rather than complaining.
            // Now it comes off this list.
            UpcomingIntervals(
                intervals = snapshot.intervals,
                fromIndex = interval.index,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        } else {
            // A free ride has no rest of the class, so the slack has nowhere
            // else to go and the totals still sit at the bottom.
            Spacer(Modifier.weight(1f))
        }

        RideTotals(state, Modifier.fillMaxWidth())
    }
}

/**
 * The live ghost: one number, and nothing else (24.3.4).
 *
 * **Not a position, not a percentage, not a list.** A leaderboard of two is a
 * number, and the number is the gap in the unit the board already ranks on.
 *
 * Drawn in the output colour rather than green-for-ahead and red-for-behind,
 * which is the decision worth keeping: a rider behind a stronger housemate is
 * not doing anything wrong, and colouring it as a failure would say they were.
 *
 * The output colour is `primary`, the same one the OUTPUT tile directly below
 * this uses — because kJ is what it is, and a gap in kJ that is not the colour
 * of kJ is a second colour language on one column. It was `MetricPowerCoral`
 * on the first pass and that was wrong for a reason only the tablet showed:
 * against a dark background coral reads as *red*, so a rider two kilojoules
 * down was being told in the colour of an error that they were losing.
 */
@Composable
private fun RivalGap(rival: RivalStatus?, modifier: Modifier = Modifier) {
    if (rival == null) return

    val ahead = rival.gap >= 0
    val magnitude = kotlin.math.abs(rival.gap)
    // 24.3.14. The gap is whatever the race is measured in, and only this
    // layer knows how to say it — the domain deliberately carries a number and
    // a metric rather than a formatted string.
    val gap = when (rival.metric) {
        RaceMetric.Output -> Formatters.kilojoulesValue(magnitude)
        RaceMetric.Distance -> Formatters.distanceValue(magnitude, MaterialTheme.units)
    }

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
                // 24.3.6. A rival whose ride has ended says so, once, and the
                // gap beside it stops moving — never a line extrapolated
                // forward and never a comparison that silently freezes.
                text = if (rival.rivalFinished) {
                    "${rival.rivalName.uppercase()} · FINISHED"
                } else {
                    rival.rivalName.uppercase()
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = if (ahead) "+$gap" else "−$gap",
                    fontSize = 34.sp,
                    lineHeight = 36.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-1).sp,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "kJ",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(bottom = 5.dp)
                )
            }
        }
    }
}

/**
 * The live leaderboard: three rows, ranked, moving under the rider (24.3.10,
 * 24.3.13).
 *
 * **Three rows and not the whole board**, which is what makes the owner's word
 * *leaderboard* and 24.3.4's *"not a list"* stop contradicting each other. The
 * board can have any number of people on it; a rider at 90 rpm can read the
 * one they are chasing, themselves, and the one chasing them. It is Peloton's
 * own behaviour and it is why their leaderboard is legible at all.
 *
 * **One number space, no unit and no rank** (24.3.17). Every row carries its
 * own total, in the same unit, and says nothing else. The first draft gave the
 * rider's row their total and every other row the signed gap to it, which the
 * owner rejected for the reason that survives restating: *a gap is arithmetic
 * the rider did not ask for*. `+12` is one subtraction away from the two
 * totals it came from, and it only means anything to somebody holding their
 * own number in their head at 90 rpm. Four totals in a column are compared by
 * eye. `LiveStanding.gapToYou` is still on the model, for the tests and for
 * the overlay (24.3.16), where a single gap may well still be the right shape.
 *
 * **The rank is gone too, and that is a claim about the product rather than
 * about the pixels.** Four of the row kinds on this board are the rider's own
 * past rides (24.3.12), so *4th of 6* describes a field that is mostly one
 * person. The ranking still orders the board and still picks the window — it
 * is simply not drawn. What is lost with it is the one thing the window hid:
 * a rider seeing three rows cannot tell whether there are two more or twenty,
 * and on the owner's reading that is fine, because only the rows next to them
 * were ever actionable.
 *
 * **Nothing is coloured for winning or losing**, which is the decision this
 * feature has now made twice. Being behind a stronger housemate is not a
 * mistake, and the ride screen's red is the colour of an error. The whole card
 * is in the output colour, because the score is kilojoules and a kJ that is
 * not the colour of kJ is a second colour language on one column.
 */
@Composable
private fun LiveLeaderboardCard(standings: LiveStandings?, modifier: Modifier = Modifier) {
    if (standings == null) return

    Card(
        // 24.3.13's consequence, drawn rather than described: your row moves
        // between the other two as you pass and are passed, so the two
        // neighbours change identity mid-ride. A scale bounce marks that the
        // board moved — it is a `graphicsLayer`, so nothing reflows and 11.6.8
        // stays fixed.
        modifier = modifier.attentionBounce(trigger = standings.yourRank),
        shape = MaterialTheme.expressiveShapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        // 24.3.18c. Six rows without moving a hand, and the rest of the field
        // by scrolling — the owner's *"there is space … the more the merrier
        // within reason"*, measured rather than felt: rows are 44 dp, the card
        // starts below the zone ladder and *View in Overlay Mode* caps it, so
        // six fit and a seventh collides with the button.
        //
        // The scroll starts on the rider's own row rather than at the top,
        // because being able to see yourself is the one thing this card is
        // for. It is a `LazyColumn` for the same reason `NextUpBlock` became
        // one (11.6.18): the field is bounded at 16 but the rows are cheap and
        // the list must not measure all of them every tick.
        val yourIndex = standings.all.indexOfFirst { it.isYou }.coerceAtLeast(0)
        val listState = rememberLazyListState()
        LaunchedEffect(standings.yourRank) {
            // Follows the rider as they pass and are passed, so the card never
            // ends up showing a stretch of board they are not on.
            listState.animateScrollToItem(
                (yourIndex - LiveLeaderboard.WINDOW / 2).coerceAtLeast(0)
            )
        }

        // The padding is `contentPadding` rather than a modifier, and the cap
        // adds it back: `heightIn(6 rows).padding(large)` bounds the *outer*
        // box, so the padding comes out of the rows and the card draws five of
        // the six it was sized for. Measured on the AVD, where it did exactly
        // that.
        val edge = MaterialTheme.spacing.large
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(edge),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = LEADERBOARD_ROW_HEIGHT * LiveLeaderboard.WINDOW + edge * 2)
        ) {
            // No header at all (24.3.17c). It said `4TH OF 6`, and a position
            // over a field that is mostly the rider's own past rides is a
            // category error rather than a small overstatement. Nothing has
            // replaced it: the rows are names and numbers, best first, and
            // that is the whole card.
            items(standings.all, key = { it.name }) { row ->
                LeaderboardRow(row, standings.metric)
            }
        }
    }
}

/**
 * One row's height, and the unit the card's ceiling is counted in (24.3.18c).
 *
 * Named rather than inlined because it appears twice — the row and the six-row
 * cap — and a board that scrolled at five and a half rows would look broken in
 * a way that is hard to attribute.
 */
private val LEADERBOARD_ROW_HEIGHT = 44.dp

@Composable
private fun LeaderboardRow(row: LiveStanding, metric: RaceMetric) {
    val units = MaterialTheme.units
    // 24.3.17a. Their total, not their distance from yours — on every row,
    // including the rider's own, so there is one number space on the card and
    // nothing to tell apart.
    val number = when (metric) {
        RaceMetric.Output -> Formatters.kilojoulesValue(row.value)
        RaceMetric.Distance -> Formatters.distanceValue(row.value, units)
    }
    // Three treatments, not two (24.3.18a, 24.3.18d). The rider is the accent;
    // **their own past rides are the accent dimmed**, because a row that is
    // also them should not read as an opponent; and everything else — people
    // and generated targets alike — is the ordinary variant. A generated row is
    // told apart by its glyph rather than by its colour, so that colour can go
    // on saying *is this me*.
    val colour = when {
        row.isYou -> MaterialTheme.colorScheme.primary
        row.kind.isPerson -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(LEADERBOARD_ROW_HEIGHT),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                // 24.3.18a's visible half. A generated target carries a mark so
                // a rider can never come away thinking a housemate did 300 kJ
                // when 300 is a number this app made up. A ring rather than a
                // word: the row is 44 dp in a 360 dp column read at two metres,
                // and it is the same glyph 18.7 already uses for *this row is
                // not quite what the others are*.
                text = if (row.isGhost) "○ ${row.name.uppercase()}" else row.name.uppercase(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (row.isYou) FontWeight.Black else FontWeight.Medium,
                color = colour,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            // 24.3.6. A competitor whose own ride has run out says so, once,
            // and their number stops moving — never a line extrapolated
            // forward. On its own line rather than appended to the name,
            // because a name is as long as somebody's name is and this must
            // not be the thing that gets ellipsed away.
            if (row.finished) {
                Text(
                    text = "FINISHED",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
        Spacer(Modifier.width(MaterialTheme.spacing.small))
        // 24.3.17b. No unit, on any row. Every number here is in the same one
        // and the OUTPUT tile below the board spells it out in full, so the
        // label was the third statement of one fact.
        //
        // **This is safe only while `Output` is the only reachable metric**
        // (24.3.14a). 24.3.15 would make the board's unit selectable, and a
        // number whose meaning has silently changed is worse than one that is
        // missing — so that item has to find somewhere on this card to say
        // which race it is, and the note that closed 24.3.17c took the header
        // away.
        Text(
            text = number,
            fontSize = 26.sp,
            lineHeight = 28.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = (-1).sp,
            color = if (row.isYou) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            maxLines = 1,
            softWrap = false
        )
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
private fun MetricGrid(
    state: RideUiState,
    onPairHeartRate: () -> Unit,
    modifier: Modifier = Modifier
) {
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
        // 11.6.2 / 11.6.2a. Over the numbers rather than beside the prescribed
        // zone: the zone a rider is *in* is a reading of their live power, and
        // this is the column live power lives in. The class's own target is
        // marked on the same ladder, so "where I am" and "where I was asked to
        // be" are one comparison across one object.
        //
        // Shown on a free ride too, where nothing is marked: "which zone am I
        // in" is a question a class does not have to have asked.
        PowerZoneScale(
            scale = state.zoneScale,
            modifier = Modifier.fillMaxWidth()
        )

        // 11.7. The class asks for one of these and the rest are context. Which
        // one is the block's own answer (`governed_by` in the catalogue), not a
        // rider preference and not inferred from the numbers: a 55 rpm grind is
        // about the legs, a threshold effort at 80-90 is about the watts, and
        // before this the screen said both at once with equal weight.
        val cadenceEmphasis = if (hasTargets) snapshot.cadenceEmphasis else TargetEmphasis.None
        val powerEmphasis = if (hasTargets) snapshot.powerEmphasis else TargetEmphasis.None

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
                icon = MetricIcons.Cadence,
                value = if (live) state.reading.cadenceRpm.toInt().toString() else NO_READING,
                unit = "rpm",
                accent = MetricCadenceCyan,
                band = snapshot.cadenceTarget,
                emphasis = cadenceEmphasis,
                rawValue = state.reading.cadenceRpm,
                modifier = Modifier.weight(1f)
            )
            RideMetricTile(
                label = "RESISTANCE",
                icon = MetricIcons.Resistance,
                value = if (live) state.reading.resistancePercent.toInt().toString() else NO_READING,
                unit = "%",
                accent = MetricResistanceViolet,
                // 11.7.3, and it is the whole of the owner's *"if the target is
                // powerzone, then no resistance target is required"*. No class
                // in the library prescribes resistance; the band was PowerModel
                // inverted, and that curve is 66% out at the median against the
                // board's own watts. It was the least trustworthy number on the
                // screen and it was drawn with the same authority as the two
                // that are measured. It is the knob, not a target.
                band = TargetBand.NONE,
                emphasis = TargetEmphasis.None,
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
                icon = MetricIcons.Power,
                value = if (live) state.reading.powerWatts.toInt().toString() else NO_READING,
                unit = "watts",
                accent = MetricPowerCoral,
                band = snapshot.powerTarget,
                emphasis = powerEmphasis,
                rawValue = state.reading.powerWatts,
                valueSize = 76.sp,
                modifier = Modifier.weight(1f)
            )
            val noStrap = state.reading.heartRateBpm == null

            // 21.3.1. The number itself takes the zone's colour — the cheapest
            // form of "which zone am I in", and the one that needs no extra
            // room on a screen that has none to spare. It falls back to the
            // metric's own green rather than to grey, because a rider with no
            // maximum recorded must not be shown a *duller* heart rate than one
            // who has: absent zones are absent, not worse.
            //
            // This is the one tile where colouring by zone is free, and the
            // reason is worth knowing before copying it: `MetricReadout`
            // already recolours a value amber when it is off target, and heart
            // rate is the only live metric with no target band. On cadence the
            // two signals would fight, which is 11.7.1a seen from the far side.
            val hrZone = state.heartRateZone
            val hrAccent by animateColorAsState(
                targetValue = hrZone?.color ?: MetricHeartRateGreen,
                animationSpec = spring(stiffness = Spring.StiffnessLow),
                label = "HeartRateZoneColour"
            )

            RideMetricTile(
                label = hrZone?.let { "HEART RATE · H${it.number} ${it.displayName.uppercase()}" }
                    ?: "HEART RATE",
                icon = MetricIcons.HeartRate,
                // Null means no strap, never a measured zero. The strap is its
                // own radio, so it is not silenced by the bike going quiet.
                value = state.reading.heartRateBpm?.toString() ?: NO_READING,
                unit = "bpm",
                accent = hrAccent,
                band = TargetBand.NONE,
                rawValue = (state.reading.heartRateBpm ?: 0).toDouble(),
                valueSize = 76.sp,
                // 11.6.9. The dashes were a dead end: the one metric measured
                // identically for every rider, whatever the power model does,
                // and tapping it did nothing. Now it is the way in to pairing —
                // which only became possible once there was a way into Settings
                // that does not end the ride (11.6.10).
                onClick = if (noStrap) onPairHeartRate else null,
                footnote = if (noStrap) "Tap to pair a heart-rate strap" else null,
                // 21.3.4. The owner's, and the one metric on this screen with a
                // rhythm of its own. Null draws nothing — see BeatingHeart.
                pulseBpm = state.reading.heartRateBpm,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** Ride totals — glanced at once at the end, sized accordingly. */
@Composable
private fun RideTotals(state: RideUiState, modifier: Modifier = Modifier) {
    val snapshot = state.snapshot

    // 11.6.19. The owner's *"why not!?"*: tapping the distance reads it the
    // other way round, for this ride only. **Nothing is written** — the stored
    // preference has exactly one writer and it is Settings, which is 2.4.6's
    // rule applied to a second setting before it could be broken the same way.
    // Held here rather than in a CompositionLocal because this tile is the only
    // distance on the screen; saved across a recreation and no further, so it
    // lasts exactly as long as the ride it was flipped during.
    var flipped by rememberSaveable { mutableStateOf(false) }
    val stored = MaterialTheme.units
    val units = if (flipped) stored.other else stored

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)
    ) {
        SmallStat(
            label = "OUTPUT",
            icon = MetricIcons.Output,
            // 11.6.12. Whole kilojoules. This is the tile the owner was looking
            // at: the tenth is noise, and it is what made a three-digit total
            // squeeze the "kJ" label beside it off the tile.
            value = Formatters.kilojoulesValue(snapshot.totalOutputKj),
            unit = "kJ",
            accent = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f)
        )
        SmallStat(
            label = "DISTANCE",
            icon = MetricIcons.Distance,
            value = Formatters.distanceValue(snapshot.distanceKm, units),
            unit = units.distanceLabel,
            accent = MaterialTheme.colorScheme.primary,
            // Reversible by the same gesture, which is also the only way it can
            // be discovered — there is no room on a 113 dp tile for a caption
            // saying it is tappable, and a mis-tap mid-effort costs a rider one
            // tap back.
            onClick = { flipped = !flipped },
            clickLabel = "Show this distance in ${units.other.displayName.lowercase()}",
            modifier = Modifier.weight(1f)
        )
        SmallStat(
            label = "AVG POWER",
            icon = MetricIcons.Power,
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
    icon: ImageVector,
    modifier: Modifier = Modifier,
    valueSize: androidx.compose.ui.unit.TextUnit = 104.sp,
    /** Non-null makes the whole tile a target — see the heart-rate tile. */
    onClick: (() -> Unit)? = null,
    footnote: String? = null,
    /** Non-null beats a heart in the tile's empty half at that rate (21.3.4). */
    pulseBpm: Int? = null,
    /** Whether this tile's band is the class's instruction (11.7.3). */
    emphasis: TargetEmphasis = TargetEmphasis.Instruction
) {
    Card(
        modifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier,
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
            // Behind the number rather than beside it: the tile's right-hand
            // half is empty, and a heart the rider catches in peripheral vision
            // must not push the digits around when it swells.
            BeatingHeart(
                bpm = pulseBpm,
                color = accent,
                modifier = Modifier.align(Alignment.CenterEnd)
            )

            MetricReadout(
                label = label,
                value = value,
                unit = unit,
                accent = accent,
                band = band,
                rawValue = rawValue,
                // Sized for a 21-inch screen read from a metre away, mid-effort.
                valueSize = valueSize,
                // 11.6.4. There is room for the numbers here, and this is the
                // screen a rider reads rather than glances at.
                showTargetRange = true,
                icon = icon,
                emphasis = emphasis,
                modifier = Modifier.fillMaxWidth()
            )

            if (footnote != null) {
                Text(
                    text = footnote,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.BottomStart)
                )
            }
        }
    }
}

@Composable
private fun SmallStat(
    label: String,
    value: String,
    unit: String,
    accent: Color,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    /** Non-null makes the whole tile a target — see the distance tile (11.6.19). */
    onClick: (() -> Unit)? = null,
    clickLabel: String? = null
) {
    Card(
        // `onClickLabel` rather than a `contentDescription`: the tile's own
        // label and value are what a screen reader should read, and setting a
        // description on the merged node would replace them with the name of
        // the action.
        modifier = if (onClick != null) {
            modifier.clickable(onClickLabel = clickLabel, onClick = onClick)
        } else {
            modifier
        },
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 11.6.3, same argument as the big tiles: the label is the only
                // thing naming the number and it is the smallest text here.
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false
                )
            }
            Row(verticalAlignment = Alignment.Bottom) {
                // 11.6.17. Shrinks rather than clipping. An hour at 300 W is
                // 1080 kJ and this tile is a third of a 360 dp column: at a
                // fixed 34 sp the fourth digit was simply cut off the edge, in
                // silence, exactly as the tenth was in 11.6.12.
                ShrinkToFitText(
                    text = value,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-1).sp,
                    color = accent,
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

/**
 * The next effort, swapping to a countdown for the final seconds (11.6.1).
 *
 * Lives beside the current interval rather than in the right-hand column. The
 * countdown is the one thing on this screen that must not be missed, and the
 * rider's eyes are already on the interval card when it fires.
 */
@Composable
private fun NextUpBlock(
    interval: IntervalState,
    modifier: Modifier = Modifier
) {
    val next = interval.next ?: return

    // 11.6.16, the other half. The list below absorbs the growth now, so
    // nothing falls off the bottom either way — but the countdown is still
    // 10-odd dp taller than the preview it replaces, and every interval
    // boundary would otherwise shuffle the rest of the class up and back.
    //
    // Reserved from what has actually been measured rather than from a number
    // typed in here: the block settles at the taller of the two states the
    // first time it sees it and never gives the height back. Same trade as
    // 11.6.8's *reserve the widest string*, turned ninety degrees, and it needs
    // no maintenance when either child changes shape.
    val density = LocalDensity.current
    var reserved by remember { mutableStateOf(0.dp) }

    Box(modifier = modifier.heightIn(min = reserved)) {
        AnimatedContent(
            targetState = interval.isChangeImminent,
            transitionSpec = {
                (fadeIn() + scaleIn(initialScale = 0.85f))
                    .togetherWith(fadeOut() + scaleOut(targetScale = 0.85f))
            },
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { size ->
                    val height = with(density) { size.height.toDp() }
                    if (height > reserved) reserved = height
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
}

/**
 * The race, and the controls.
 *
 * It used to be the rest of the class and the controls. The *next* effort left
 * first (11.6.1), and *then* followed it in 24.3.13b — now, next and then are
 * one thought and they belong in one column. What arrived in their place is
 * the live leaderboard, on the owner's own reading of the screen: this is
 * where the eye goes, and a race is the thing worth looking at.
 *
 * Pause / end / overlay stay exactly where they are, because a thumb learns
 * where they are.
 */
@Composable
private fun UpNextColumn(
    state: RideUiState,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onEnd: () -> Unit,
    onBackToHud: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)
    ) {
        // Exactly one of these two draws (24.3.11): the board is the race, and
        // the single gap is the same race with a `LIMIT 1` on it, kept behind
        // a flag. The service never populates both.
        RivalGap(state.snapshot.rival, Modifier.fillMaxWidth())
        LiveLeaderboardCard(state.snapshot.standings, Modifier.fillMaxWidth())

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
                    text = stringResource(R.string.ride_view_in_overlay),
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
