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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.pelonot.domain.model.IntervalState
import com.pelonot.domain.model.RideCue
import com.pelonot.domain.model.RideIntent
import com.pelonot.domain.model.TargetBand
import com.pelonot.ui.components.BeatingHeart
import com.pelonot.ui.components.CountdownBanner
import com.pelonot.ui.components.IntervalTimeline
import com.pelonot.ui.components.RidePositionCall
import com.pelonot.ui.components.MetricIcons
import com.pelonot.ui.components.MetricReadout
import com.pelonot.ui.components.NextUpPreview
import com.pelonot.ui.components.PowerZoneScale
import com.pelonot.ui.components.ProgressArc
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
                ftpWatts = ftp
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
                    append("Everything so far is saved either way, but a ride can't be restarted.")
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
                band = if (hasTargets) snapshot.cadenceTarget else TargetBand.NONE,
                rawValue = state.reading.cadenceRpm,
                modifier = Modifier.weight(1f)
            )
            RideMetricTile(
                label = "RESISTANCE",
                icon = MetricIcons.Resistance,
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
                icon = MetricIcons.Power,
                value = if (live) state.reading.powerWatts.toInt().toString() else NO_READING,
                unit = "watts",
                accent = MetricPowerCoral,
                band = if (hasTargets) snapshot.powerTarget else TargetBand.NONE,
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
            value = Formatters.distanceValue(snapshot.distanceKm, MaterialTheme.units),
            unit = MaterialTheme.units.distanceLabel,
            accent = MaterialTheme.colorScheme.primary,
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
    pulseBpm: Int? = null
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

    AnimatedContent(
        targetState = interval.isChangeImminent,
        transitionSpec = {
            (fadeIn() + scaleIn(initialScale = 0.85f))
                .togetherWith(fadeOut() + scaleOut(targetScale = 0.85f))
        },
        modifier = modifier,
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

/**
 * The rest of the class, and the controls.
 *
 * The *next* effort moved out of here to sit under the current one (11.6.1);
 * what stays is the shape of the class beyond it, and pause / end / overlay,
 * which stay put because a thumb learns where they are.
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
    val interval = state.snapshot.interval

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)
    ) {
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
