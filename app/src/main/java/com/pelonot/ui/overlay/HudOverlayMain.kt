package com.pelonot.ui.overlay

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pelonot.R
import com.pelonot.core.Formatters
import com.pelonot.data.sensor.SensorReading
import com.pelonot.data.service.RideSnapshot
import com.pelonot.domain.coach.CoachStyle
import com.pelonot.domain.model.HudDock
import com.pelonot.domain.model.IntervalState
import com.pelonot.domain.model.RideCue
import com.pelonot.domain.model.TargetBand
import com.pelonot.ui.components.CountdownBanner
import com.pelonot.ui.components.IntervalTimeline
import com.pelonot.ui.components.MetricReadout
import com.pelonot.ui.components.NextUpPreview
import com.pelonot.ui.components.ProgressArc
import com.pelonot.ui.components.ZoneGlyph
import com.pelonot.ui.components.rememberFlash
import com.pelonot.ui.components.rememberPulse
import com.pelonot.ui.theme.MetricCadenceCyan
import com.pelonot.ui.theme.MetricHeartRateGreen
import com.pelonot.ui.theme.MetricPowerCoral
import com.pelonot.ui.theme.MetricResistanceViolet
import com.pelonot.ui.theme.color
import com.pelonot.ui.theme.spacing
import com.pelonot.ui.theme.units

/**
 * The floating ride HUD.
 *
 * This is the app's primary surface, not a secondary one. The rider is almost
 * always watching something else full-screen on the same tablet, so the HUD is
 * docked to one edge and spans the full width of it, leaving the middle of the
 * screen — where faces and subtitles live — completely clear. It is not a
 * draggable card: dragging snaps it between the top and bottom edges, and
 * nothing parks it over the film.
 *
 * The previous version was a 300dp square panel floating wherever it was last
 * dropped, with no class information at all.
 *
 * Everything on it is arranged for peripheral vision. Intensity is encoded
 * three times over (colour, digit, and how spiky the zone badge is), targets
 * are gauges rather than numbers to compare, and the countdown into the next
 * effort is the one element that is never optional — see [CountdownBanner].
 */
@Composable
fun HudOverlayMain(
    snapshot: RideSnapshot,
    reading: SensorReading,
    dock: HudDock,
    collapsed: Boolean,
    coachStyle: CoachStyle,
    onToggleCollapsed: () -> Unit,
    onDockChange: (HudDock) -> Unit,
    onOpenApp: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interval = snapshot.interval
    val zone = interval.targetZone
    val accent by animateColorAsState(
        targetValue = if (interval.hasClass) zone.color else MaterialTheme.colorScheme.primary,
        animationSpec = spring(stiffness = Spring.StiffnessVeryLow),
        label = "HudAccent"
    )

    // The strip washes with the new zone's colour when the effort changes.
    // With the coach set to Silent this is the *only* announcement the rider
    // gets, so it has to be visible from the corner of the eye — but it cannot
    // be a scale bounce: scaling a full-width docked strip peels it away from
    // the screen edge and reads as a rendering fault.
    val flash = rememberFlash(
        trigger = if (interval.hasClass) interval.index else null,
        enabled = coachStyle.animates
    )

    val edgeGlow by animateDpAsState(
        targetValue = if (interval.isChangeImminent) 6.dp else 2.dp,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "HudEdgeGlow"
    )
    val glowPulse = if (interval.isChangeImminent && coachStyle.animates) {
        rememberPulse(periodMs = 450, from = 0.45f, to = 1f)
    } else {
        1f
    }

    // A hairline of the current zone's colour along the inner edge. It stays
    // quiet until a change is coming, then thickens and pulses — the earliest
    // warning on the whole HUD, and the one that needs no reading at all.
    val edge: @Composable () -> Unit = {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(edgeGlow)
                .background(
                    accent.copy(
                        alpha = if (interval.isChangeImminent) glowPulse else 0.45f
                    )
                )
        )
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(hudShape(dock)),
        color = Color.Transparent
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(hudBackground(dock))
                // Layered after the gradient so it washes over the panel
                // rather than being painted under it.
                .background(accent.copy(alpha = 0.30f * flash))
                // 11.1a.1: double tap anywhere on the strip opens the full app.
                // Double rather than single deliberately — a single tap is what
                // a rider fires by accident reaching past the tablet, and one
                // that yanked their film off the screen mid-scene would be the
                // worst possible mis-fire on this surface. The buttons and the
                // handle sit in front of this and consume their own taps.
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = { onOpenApp() })
                }
                .semantics {
                    // The gesture is invisible otherwise: there is nothing to
                    // see, and a rider who has not been told will never find it.
                    onClick(label = "Open Pelonot") { onOpenApp(); true }
                }
        ) {
            if (dock == HudDock.Bottom) edge()

            if (dock == HudDock.Top) {
                HudBody(snapshot, reading, collapsed, coachStyle, accent, onPause, onResume, onStop)
                HudHandle(dock, collapsed, onToggleCollapsed, onDockChange)
            } else {
                HudHandle(dock, collapsed, onToggleCollapsed, onDockChange)
                HudBody(snapshot, reading, collapsed, coachStyle, accent, onPause, onResume, onStop)
            }

            if (dock == HudDock.Top) edge()
        }
    }
}

/**
 * The strip's fill: a short translucent lead-in at the inner edge so the
 * boundary is not a hard line across the picture, then effectively opaque
 * wherever the numbers actually sit.
 *
 * The first version graded the *whole* strip from 0.90 to 0.97, which looks
 * elegant in a screenshot against a black wallpaper and is unreadable over
 * anything bright — which is the only place it will ever really be used.
 */
@Composable
private fun hudBackground(dock: HudDock): Brush {
    val edge = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)
    val body = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.985f)
    val deep = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.995f)

    return if (dock == HudDock.Bottom) {
        Brush.verticalGradient(0f to edge, 0.14f to body, 1f to deep)
    } else {
        Brush.verticalGradient(0f to deep, 0.86f to body, 1f to edge)
    }
}

private fun hudShape(dock: HudDock) = if (dock == HudDock.Bottom) {
    androidx.compose.foundation.shape.RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
} else {
    androidx.compose.foundation.shape.RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)
}

/**
 * The grab bar. Tapping collapses the HUD to a slim strip; dragging away from
 * the current edge sends it to the other one.
 */
@Composable
private fun HudHandle(
    dock: HudDock,
    collapsed: Boolean,
    onToggleCollapsed: () -> Unit,
    onDockChange: (HudDock) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(22.dp)
            .clickable(onClick = onToggleCollapsed)
            .pointerInput(dock) {
                detectVerticalDragGestures { change, dragAmount ->
                    change.consume()
                    // Only a decisive drag away from the current edge moves it,
                    // so brushing the handle mid-ride does nothing.
                    if (dock == HudDock.Bottom && dragAmount < -DRAG_SNAP_PX) {
                        onDockChange(HudDock.Top)
                    } else if (dock == HudDock.Top && dragAmount > DRAG_SNAP_PX) {
                        onDockChange(HudDock.Bottom)
                    }
                }
            }
            .semantics {
                contentDescription = if (collapsed) {
                    "Expand the heads-up display. Drag to move it to the other " +
                        "edge, or double tap it to open Pelonot."
                } else {
                    "Collapse the heads-up display. Drag to move it to the other " +
                        "edge, or double tap it to open Pelonot."
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(width = 56.dp, height = 4.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(percent = 50))
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
        )
    }
}

@Composable
private fun HudBody(
    snapshot: RideSnapshot,
    reading: SensorReading,
    collapsed: Boolean,
    coachStyle: CoachStyle,
    accent: Color,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit
) {
    AnimatedContent(
        targetState = collapsed,
        transitionSpec = {
            (fadeIn() + expandVertically()).togetherWith(fadeOut() + shrinkVertically())
        },
        label = "HudDensity"
    ) { isCollapsed ->
        if (isCollapsed) {
            HudCollapsed(snapshot, reading, accent, onPause, onResume, onStop)
        } else {
            HudExpanded(snapshot, reading, coachStyle, accent, onPause, onResume, onStop)
        }
    }
}

// ==========================================================================
// Expanded
// ==========================================================================

@Composable
private fun HudExpanded(
    snapshot: RideSnapshot,
    reading: SensorReading,
    coachStyle: CoachStyle,
    accent: Color,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit
) {
    val interval = snapshot.interval

    Column(modifier = Modifier.fillMaxWidth()) {

        CueBand(interval.cue, accent, animate = coachStyle.animates)

        if (interval.hasClass) {
            IntervalTimeline(
                intervals = snapshot.intervals,
                elapsedSec = interval.classElapsedSec,
                durationSec = interval.classDurationSec,
                currentIndex = interval.index,
                modifier = Modifier.padding(
                    horizontal = MaterialTheme.spacing.large,
                    vertical = MaterialTheme.spacing.small
                )
            )
        }

        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val wide = maxWidth >= WIDE_BREAKPOINT
            val roomy = maxWidth >= ROOMY_BREAKPOINT

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = MaterialTheme.spacing.large,
                        end = MaterialTheme.spacing.large,
                        bottom = MaterialTheme.spacing.medium,
                        top = MaterialTheme.spacing.extraSmall
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.large)
            ) {
                ClockBlock(snapshot, Modifier.width(if (roomy) 168.dp else 140.dp))

                if (interval.hasClass) {
                    NowBlock(snapshot, accent, Modifier.width(if (roomy) 250.dp else 200.dp))
                }

                MetricsBlock(
                    snapshot = snapshot,
                    reading = reading,
                    showTargets = interval.hasClass,
                    modifier = Modifier.weight(1f)
                )

                if (wide && interval.hasClass) {
                    NextSlot(interval, coachStyle, Modifier.width(256.dp))
                }

                Controls(
                    isPaused = snapshot.isPaused,
                    onPause = onPause,
                    onResume = onResume,
                    onStop = onStop
                )
            }
        }
    }
}

/**
 * The class's headline instruction, when it has one — the last hard effort or
 * the cooldown. A full-width band because it is the one thing on the HUD worth
 * reading a whole sentence of.
 */
@Composable
private fun CueBand(cue: RideCue, accent: Color, animate: Boolean) {
    AnimatedVisibility(
        visible = cue != RideCue.None,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        val pulse = if (animate && cue == RideCue.FinalPush) {
            rememberPulse(periodMs = 1100, from = 0.55f, to = 1f)
        } else {
            1f
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(accent.copy(alpha = 0.16f * pulse))
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = cue.message.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = accent,
                fontWeight = FontWeight.Black,
                letterSpacing = 3.sp
            )
        }
    }
}

@Composable
private fun ClockBlock(snapshot: RideSnapshot, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = snapshot.classTitle?.uppercase() ?: "JUST RIDE",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = Formatters.duration(snapshot.elapsedSeconds),
            fontSize = 40.sp,
            lineHeight = 42.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = (-1.5).sp,
            color = if (snapshot.isPaused) {
                MaterialTheme.colorScheme.tertiary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            maxLines = 1
        )
        Text(
            text = when {
                snapshot.isPaused -> "PAUSED"
                snapshot.interval.hasClass ->
                    "${Formatters.duration(snapshot.interval.classRemainingSec)} LEFT"
                else -> "${Formatters.kilojoules(snapshot.totalOutputKj)} · " +
                    Formatters.distance(snapshot.distanceKm, MaterialTheme.units)
            },
            style = MaterialTheme.typography.labelSmall,
            color = if (snapshot.isPaused) {
                MaterialTheme.colorScheme.tertiary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1
        )
    }
}

/** The current interval: zone badge inside its own countdown ring, and targets. */
@Composable
private fun NowBlock(snapshot: RideSnapshot, accent: Color, modifier: Modifier = Modifier) {
    val interval = snapshot.interval
    val zone = interval.targetZone

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ProgressArc(
            // Drains rather than fills: what the rider wants is how much of
            // this effort is left, not how much they have done.
            progress = 1f - interval.intervalProgress,
            color = accent,
            strokeWidth = 5.dp,
            modifier = Modifier.size(78.dp)
        ) {
            ZoneGlyph(
                zone = zone,
                modifier = Modifier.size(54.dp),
                rotating = zone.number >= 5
            ) {
                Text(
                    text = "${zone.number}",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.Black.copy(alpha = 0.8f)
                )
            }
        }

        Spacer(Modifier.width(MaterialTheme.spacing.medium))

        Column {
            Text(
                text = zone.displayName.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = accent,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = Formatters.duration(interval.remainingInIntervalSec),
                fontSize = 28.sp,
                lineHeight = 30.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-1).sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = buildString {
                    append("${snapshot.cadenceTarget.min.toInt()}–")
                    append("${snapshot.cadenceTarget.max.toInt()} RPM")
                    val resistance = snapshot.resistanceTarget
                    if (resistance.isDefined) {
                        append(" · ${resistance.min.toInt()}–${resistance.max.toInt()}%")
                    }
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun MetricsBlock(
    snapshot: RideSnapshot,
    reading: SensorReading,
    showTargets: Boolean,
    modifier: Modifier = Modifier
) {
    // Cadence and resistance first, together: they are the only two things the
    // rider can actually change. Power is what those two produce, and heart
    // rate is what the body makes of it.
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)
    ) {
        MetricReadout(
            label = "CADENCE",
            value = reading.cadenceRpm.toInt().toString(),
            unit = "RPM",
            accent = MetricCadenceCyan,
            band = if (showTargets) snapshot.cadenceTarget else TargetBand.NONE,
            rawValue = reading.cadenceRpm,
            valueSize = 42.sp,
            modifier = Modifier.weight(1f)
        )
        MetricReadout(
            label = "RESISTANCE",
            value = reading.resistancePercent.toInt().toString(),
            unit = "%",
            accent = MetricResistanceViolet,
            band = if (showTargets) snapshot.resistanceTarget else TargetBand.NONE,
            rawValue = reading.resistancePercent,
            valueSize = 42.sp,
            modifier = Modifier.weight(1f)
        )
        MetricReadout(
            label = "POWER",
            value = reading.powerWatts.toInt().toString(),
            unit = "W",
            accent = MetricPowerCoral,
            band = if (showTargets) snapshot.powerTarget else TargetBand.NONE,
            rawValue = reading.powerWatts,
            valueSize = 42.sp,
            modifier = Modifier.weight(1f)
        )
        MetricReadout(
            label = "HEART RATE",
            // Null means no strap, never a measured zero.
            value = reading.heartRateBpm?.toString() ?: "--",
            unit = "BPM",
            accent = MetricHeartRateGreen,
            rawValue = (reading.heartRateBpm ?: 0).toDouble(),
            valueSize = 42.sp,
            compact = true,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * What is coming next, swapping to a countdown for the final five seconds.
 *
 * The swap is an [AnimatedContent] scale rather than a crossfade so it reads as
 * the same object growing in urgency.
 */
@Composable
private fun NextSlot(
    interval: IntervalState,
    coachStyle: CoachStyle,
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
        label = "NextOrCountdown"
    ) { imminent ->
        if (imminent) {
            CountdownBanner(
                secondsRemaining = interval.remainingInIntervalSec,
                nextZone = next.powerZone,
                animate = coachStyle.animates
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

@Composable
private fun Controls(
    isPaused: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
        // A single toggle rather than separate Pause and Resume buttons, one of
        // which is always a no-op.
        FilledIconButton(
            onClick = if (isPaused) onResume else onPause,
            modifier = Modifier.size(52.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
        ) {
            Icon(
                imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                contentDescription = stringResource(
                    if (isPaused) R.string.cd_resume_ride else R.string.cd_pause_ride
                )
            )
        }
        FilledIconButton(
            onClick = onStop,
            modifier = Modifier.size(52.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            )
        ) {
            Icon(
                imageVector = Icons.Default.Stop,
                contentDescription = stringResource(R.string.cd_end_ride)
            )
        }
    }
}

// ==========================================================================
// Collapsed
// ==========================================================================

/**
 * The minimum a rider will accept while giving the screen back to the film:
 * the clock, the three live numbers, and the countdown when one is running.
 */
@Composable
private fun HudCollapsed(
    snapshot: RideSnapshot,
    reading: SensorReading,
    accent: Color,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit
) {
    val interval = snapshot.interval

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.spacing.large, vertical = MaterialTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.large)
    ) {
        Text(
            text = Formatters.duration(snapshot.elapsedSeconds),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurface
        )

        if (interval.hasClass) {
            ZoneGlyph(zone = interval.targetZone, modifier = Modifier.size(28.dp)) {
                Text(
                    text = "${interval.targetZone.number}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Black,
                    color = Color.Black.copy(alpha = 0.8f)
                )
            }
        }

        CompactMetric(reading.cadenceRpm.toInt().toString(), "RPM", MetricCadenceCyan)
        CompactMetric(reading.resistancePercent.toInt().toString(), "%", MetricResistanceViolet)
        CompactMetric(reading.powerWatts.toInt().toString(), "W", MetricPowerCoral)
        CompactMetric(reading.heartRateBpm?.toString() ?: "--", "BPM", MetricHeartRateGreen)

        Spacer(Modifier.weight(1f))

        val next = interval.next
        if (interval.isChangeImminent && next != null) {
            // The countdown survives collapsing. It is the one thing on this
            // HUD that is never optional.
            Text(
                text = "Z${next.powerZone.number} in ${interval.remainingInIntervalSec}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = next.powerZone.color
            )
            Spacer(Modifier.width(MaterialTheme.spacing.medium))
        }

        Controls(
            isPaused = snapshot.isPaused,
            onPause = onPause,
            onResume = onResume,
            onStop = onStop
        )
    }
}

@Composable
private fun CompactMetric(value: String, unit: String, accent: Color) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Black,
            color = accent
        )
        Spacer(Modifier.width(3.dp))
        Text(
            text = unit,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 3.dp)
        )
    }
}

private const val DRAG_SNAP_PX = 12f
private val WIDE_BREAKPOINT = 900.dp
private val ROOMY_BREAKPOINT = 1100.dp
