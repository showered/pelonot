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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
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
import com.pelonot.domain.model.HudOpacity
import com.pelonot.domain.model.IntervalState
import com.pelonot.domain.model.RideCue
import com.pelonot.domain.model.TargetBand
import com.pelonot.domain.model.ZoneScale
import com.pelonot.ui.components.CountdownBanner
import com.pelonot.ui.components.IntervalTimeline
import com.pelonot.ui.components.MetricIcons
import com.pelonot.ui.components.MetricReadout
import com.pelonot.ui.components.NextUpPreview
import com.pelonot.ui.components.PowerZoneScale
import com.pelonot.ui.components.ProgressArc
import com.pelonot.ui.components.VolumeSliders
import com.pelonot.ui.components.ZoneGlyph
import com.pelonot.ui.components.rememberFlash
import com.pelonot.ui.components.rememberPulse
import com.pelonot.ui.theme.HudMinimumOpacity
import com.pelonot.ui.theme.hudLabelColor
import com.pelonot.ui.theme.MetricCadenceCyan
import com.pelonot.ui.theme.MetricHeartRateGreen
import com.pelonot.ui.theme.MetricPowerCoral
import com.pelonot.ui.theme.MetricResistanceViolet
import com.pelonot.ui.theme.color
import com.pelonot.ui.theme.expressiveShapes
import com.pelonot.ui.theme.spacing
import com.pelonot.ui.theme.units
import kotlinx.coroutines.delay

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
    volumeOpen: Boolean,
    mediaVolume: Float,
    coachVolume: Float,
    volumeError: String?,
    onToggleVolume: () -> Unit,
    onMediaVolumeChange: (Float) -> Unit,
    onCoachVolumeChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    /** How solid the rider has asked the strip to be (11.1b.1). */
    opacity: Float = HudOpacity.DEFAULT
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

    // A hairline of the current zone's colour along the very screen edge. It
    // stays quiet until a change is coming, then thickens and pulses — the
    // earliest warning on the whole HUD, and the one that needs no reading at
    // all. Full strength, never dimmed by the opacity setting: it is an alert,
    // and it is one pixel band of a screen the rider has otherwise got back.
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

    // 11.1b.2. Every label on the strip reads `onSurfaceVariant`, including the
    // ones inside shared components, so the lift is applied once here rather
    // than at thirty call sites. At full opacity it is the same grey it always
    // was; as the rider gives the film back, it climbs towards the primary text
    // colour by exactly as much as contrast requires.
    val panelOpacity = HudOpacity.clamp(opacity, HudMinimumOpacity)
    val scheme = MaterialTheme.colorScheme

    MaterialTheme(
        colorScheme = scheme.copy(onSurfaceVariant = hudLabelColor(panelOpacity)),
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes
    ) {
        // No panel. The strip is a *transparent* full-width band with a handful
        // of chips floating in it, and everything between them is film. The
        // previous version painted the whole band, which meant a rider asking
        // for more of their picture back could only ask for a lighter wash over
        // all of it — the numbers got harder to read and the picture never came
        // back. Backing goes only where a number or a control sits.
        Column(
            modifier = modifier
                .fillMaxWidth()
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
            val body: @Composable () -> Unit = {
                HudBody(
                    snapshot = snapshot,
                    reading = reading,
                    collapsed = collapsed,
                    coachStyle = coachStyle,
                    accent = accent,
                    opacity = panelOpacity,
                    flash = flash,
                    volumeOpen = volumeOpen,
                    onToggleVolume = onToggleVolume,
                    onPause = onPause,
                    onResume = onResume,
                    onStop = onStop
                )
            }

            // Between the handle and the numbers, so it opens *into* the screen
            // rather than pushing the numbers away from the edge they are
            // docked against.
            val volumePanel: @Composable () -> Unit = {
                HudVolumePanel(
                    visible = volumeOpen,
                    opacity = panelOpacity,
                    mediaVolume = mediaVolume,
                    coachVolume = coachVolume,
                    error = volumeError,
                    onMediaVolumeChange = onMediaVolumeChange,
                    onCoachVolumeChange = onCoachVolumeChange
                )
            }

            if (dock == HudDock.Top) {
                body()
                volumePanel()
                HudHandle(dock, collapsed, panelOpacity, onToggleCollapsed, onDockChange)
                edge()
            } else {
                edge()
                HudHandle(dock, collapsed, panelOpacity, onToggleCollapsed, onDockChange)
                volumePanel()
                body()
            }
        }
    }
}

/**
 * One floating panel — a clock, a group of numbers, a control.
 *
 * This is the only thing on the HUD that paints over the rider's film, so it
 * paints as little as it can: a rounded container exactly the size of what it
 * holds, and a hairline that keeps its edge legible against a bright scene
 * without adding a second visible surface.
 *
 * [wash] is the zone-change flash. It washes the chips rather than the whole
 * band, because the band is the film.
 */
@Composable
private fun HudChip(
    opacity: Float,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.expressiveShapes.extraLarge,
    wash: Color = Color.Transparent,
    padding: PaddingValues = PaddingValues(
        horizontal = MaterialTheme.spacing.medium,
        vertical = MaterialTheme.spacing.small
    ),
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(hudChipColor(opacity))
            // Layered after the fill so it washes over the chip rather than
            // being painted under it.
            .background(wash)
            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f), shape)
            .padding(padding)
    ) {
        content()
    }
}

/**
 * The class timeline, alone on the **opposite** screen edge from the numbers.
 *
 * It lives in its own overlay window (see `HudOverlayManager`) for two reasons.
 * The furniture is split into two thin bands instead of one tall block, which
 * is the difference between losing a strip of a film and losing a corner of it;
 * and because nothing here is interactive, that window is `FLAG_NOT_TOUCHABLE`
 * — every tap in this band goes straight through to whatever is playing. The
 * strip below cannot do that: it has a pause button on it.
 */
@Composable
fun HudTimelineBar(
    snapshot: RideSnapshot,
    modifier: Modifier = Modifier,
    opacity: Float = HudOpacity.DEFAULT
) {
    val interval = snapshot.interval
    if (!interval.hasClass) return

    val panelOpacity = HudOpacity.clamp(opacity, HudMinimumOpacity)

    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(
            onSurfaceVariant = hudLabelColor(panelOpacity)
        ),
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes
    ) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = HUD_MARGIN, vertical = MaterialTheme.spacing.small)
        ) {
            HudChip(
                opacity = panelOpacity,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.expressiveShapes.pill,
                // Slim: it is a 10 dp bar, and a pill with room to breathe
                // around it becomes the largest object on the screen.
                padding = PaddingValues(
                    horizontal = MaterialTheme.spacing.medium,
                    vertical = MaterialTheme.spacing.extraSmall
                )
            ) {
                IntervalTimeline(
                    intervals = snapshot.intervals,
                    elapsedSec = interval.classElapsedSec,
                    durationSec = interval.classDurationSec,
                    currentIndex = interval.index
                )
            }
        }
    }
}

/**
 * A chip's fill, as solid as the rider has asked for it to be (11.1b.1).
 *
 * Floored at [HudMinimumOpacity], which is calculated from the strip's own
 * colours rather than chosen — see `HudOpacity`. Note what the floor is *not*
 * protecting any more: there is no full-width panel, so this alpha applies only
 * behind numbers and controls, and everything else on the band is untouched
 * film at any setting.
 */
@Composable
private fun hudChipColor(opacity: Float): Color =
    MaterialTheme.colorScheme.surfaceContainerLowest
        .copy(alpha = HudOpacity.clamp(opacity, HudMinimumOpacity))

/**
 * The grab bar. Tapping collapses the HUD to a slim strip; dragging away from
 * the current edge sends it to the other one.
 *
 * A small centred pill rather than the full-width invisible band it used to be:
 * with no panel behind it, a 1280 dp drag target that shows nothing is a
 * gesture the rider fires by accident over their film and can never find on
 * purpose.
 */
@Composable
private fun HudHandle(
    dock: HudDock,
    collapsed: Boolean,
    opacity: Float,
    onToggleCollapsed: () -> Unit,
    onDockChange: (HudDock) -> Unit
) {
    // Aligned with the chips rather than centred in the window. Centring it
    // looks deliberate while the strip spans the width and looks like a stray
    // object the moment it does not — which, collapsed, it does not.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = HUD_MARGIN, vertical = 3.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .size(width = 72.dp, height = 18.dp)
                .clip(MaterialTheme.expressiveShapes.pill)
                .background(hudChipColor(opacity))
                .clickable(onClick = onToggleCollapsed)
                .pointerInput(dock) {
                    detectVerticalDragGestures { change, dragAmount ->
                        change.consume()
                        // Only a decisive drag away from the current edge moves
                        // it, so brushing the handle mid-ride does nothing.
                        if (dock == HudDock.Bottom && dragAmount < -DRAG_SNAP_PX) {
                            onDockChange(HudDock.Top)
                        } else if (dock == HudDock.Top && dragAmount > DRAG_SNAP_PX) {
                            onDockChange(HudDock.Bottom)
                        }
                    }
                }
                .semantics {
                    contentDescription = if (collapsed) {
                        "Expand the ride overlay. Drag to move it to the other " +
                            "edge, or double tap it to open Pelonot."
                    } else {
                        "Collapse the ride overlay. Drag to move it to the other " +
                            "edge, or double tap it to open Pelonot."
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(width = 36.dp, height = 3.dp)
                    .clip(MaterialTheme.expressiveShapes.pill)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
            )
        }
    }
}

@Composable
private fun HudBody(
    snapshot: RideSnapshot,
    reading: SensorReading,
    collapsed: Boolean,
    coachStyle: CoachStyle,
    accent: Color,
    opacity: Float,
    flash: Float,
    volumeOpen: Boolean,
    onToggleVolume: () -> Unit,
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
            HudCollapsed(
                snapshot, reading, accent, opacity, flash,
                volumeOpen, onToggleVolume, onPause, onResume, onStop
            )
        } else {
            HudExpanded(
                snapshot, reading, coachStyle, accent, opacity, flash,
                volumeOpen, onToggleVolume, onPause, onResume, onStop
            )
        }
    }
}

/**
 * The volume sliders, opened from the button among the ride controls (11.5.4).
 *
 * This is the deliberate exception to "nothing on the strip that is not about
 * the next sixty seconds of pedalling" (18.6 / 19.4). It earns its place only
 * because this tablet has **no status bar and therefore no system volume UI at
 * all** — the app is not one of the places a rider can change the volume, it is
 * the only one. Kept behind a tap so the resting strip is unchanged (11.5.5).
 */
@Composable
private fun HudVolumePanel(
    visible: Boolean,
    opacity: Float,
    mediaVolume: Float,
    coachVolume: Float,
    error: String?,
    onMediaVolumeChange: (Float) -> Unit,
    onCoachVolumeChange: (Float) -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = HUD_MARGIN,
                    vertical = MaterialTheme.spacing.extraSmall
                )
        ) {
            // Half the width, not the whole of it: this is two sliders, and a
            // panel that reaches the far edge of a 1280 dp screen to hold them
            // is covering film for nothing.
            HudChip(opacity = opacity, modifier = Modifier.weight(1f)) {
                VolumeSliders(
                    mediaVolume = mediaVolume,
                    coachVolume = coachVolume,
                    onMediaVolumeChange = onMediaVolumeChange,
                    onCoachVolumeChange = onCoachVolumeChange,
                    error = error,
                    compact = true
                )
            }
            Spacer(Modifier.weight(1f))
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
    opacity: Float,
    flash: Float,
    volumeOpen: Boolean,
    onToggleVolume: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit
) {
    val interval = snapshot.interval
    val wash = accent.copy(alpha = 0.30f * flash)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = HUD_MARGIN, vertical = MaterialTheme.spacing.small),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)
    ) {
        CueBand(interval.cue, accent, animate = coachStyle.animates)

        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val wide = maxWidth >= WIDE_BREAKPOINT
            val roomy = maxWidth >= ROOMY_BREAKPOINT

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)
            ) {
                HudChip(opacity = opacity, wash = wash) {
                    ClockBlock(snapshot, Modifier.width(if (roomy) 150.dp else 128.dp))
                }

                if (interval.hasClass) {
                    HudChip(opacity = opacity, wash = wash) {
                        NowBlock(
                            snapshot,
                            accent,
                            Modifier.width(if (roomy) 216.dp else 188.dp)
                        )
                    }
                }

                // The four live numbers travel together in one chip rather than
                // four: they are read as a group, and four separate containers
                // put three gaps of film through the middle of the one thing on
                // this HUD the rider is actually looking at.
                HudChip(opacity = opacity, wash = wash, modifier = Modifier.weight(1f)) {
                    MetricsBlock(
                        snapshot = snapshot,
                        reading = reading,
                        showTargets = interval.hasClass,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (wide && interval.hasClass) {
                    // Its own chip after all. NextUpPreview and CountdownBanner
                    // paint a *tinted* container — 10-24% of a zone colour —
                    // which was a fine surface while a solid panel sat behind
                    // it and is a pale smear over a white scene without one.
                    HudChip(
                        opacity = opacity,
                        wash = wash,
                        padding = PaddingValues(4.dp)
                    ) {
                        NextSlot(interval, coachStyle, Modifier.width(208.dp))
                    }
                }

                // The controls are filled buttons — already solid, already the
                // right size to hit, and needing no container of their own.
                Controls(
                    isPaused = snapshot.isPaused,
                    volumeOpen = volumeOpen,
                    onToggleVolume = onToggleVolume,
                    onPause = onPause,
                    onResume = onResume,
                    onStop = onStop
                )
            }
        }
    }
}

/**
 * The class's headline instruction — the last hard effort, or the cooldown.
 *
 * The one alert on this HUD worth a whole sentence, so it is the one element
 * allowed to be loud: a **solid** lozenge in the zone's own colour with black
 * type on it, springing in rather than fading, and breathing while the final
 * push is on. Nothing else on the strip is filled with an accent at full
 * strength, which is exactly why this reads from across the room.
 *
 * The previous version was a full-width 16%-alpha wash with accent-coloured
 * text on it — the least legible combination on the strip, over video, for the
 * message that matters most.
 */
@Composable
private fun CueBand(cue: RideCue, accent: Color, animate: Boolean) {
    AnimatedVisibility(
        visible = cue != RideCue.None,
        // Springs in from small. A slab could not do this — scaling a
        // full-width panel peels it off the screen edge — but a lozenge that
        // is not touching anything can, and it is the difference between a
        // rider noticing and not.
        enter = fadeIn() + scaleIn(
            initialScale = 0.80f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
        ),
        exit = fadeOut() + scaleOut(targetScale = 0.85f)
    ) {
        val pulse = if (animate && cue == RideCue.FinalPush) {
            rememberPulse(periodMs = 1100, from = 0.82f, to = 1f)
        } else {
            1f
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Row(
                modifier = Modifier
                    .clip(MaterialTheme.expressiveShapes.pill)
                    .background(accent.copy(alpha = pulse))
                    .padding(horizontal = MaterialTheme.spacing.large, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = cue.message.uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    // Black on the accent, not the accent on a wash of itself.
                    // Every zone colour in this palette is a bright one, so dark
                    // type is the readable direction on all seven.
                    color = Color.Black.copy(alpha = 0.85f),
                    fontWeight = FontWeight.Black,
                    letterSpacing = 3.sp
                )
            }
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
            // The bike going quiet outranks everything except a pause, because
            // it is the only line here the rider cannot work out for
            // themselves — and because it says the record has a hole in it
            // (2.4.4), which they will want to know afterwards.
            text = when {
                // 19.1.2. A ride that stopped on its own has to say so, or it
                // is indistinguishable from one that has frozen — and the way
                // out has to be on the same line, because this chip is the only
                // thing the rider can see over their film.
                snapshot.isPaused && snapshot.autoPaused -> "PAUSED · PEDAL"
                snapshot.isPaused -> "PAUSED"
                // Two words, because this chip is only as wide as the clock
                // above it: "NO SIGNAL · NOT RECORDING" was clipped mid-word to
                // "NO SIGNAL · NOT", which reads as an unfinished sentence and
                // is worse than the short version. The full sentence, and what
                // it means for the record, is on the ride screen where there
                // is room for it.
                !snapshot.telemetryLive -> "NO SIGNAL"
                snapshot.interval.hasClass ->
                    "${Formatters.duration(snapshot.interval.classRemainingSec)} LEFT"
                else -> "${Formatters.kilojoules(snapshot.totalOutputKj)} · " +
                    Formatters.distance(snapshot.distanceKm, MaterialTheme.units)
            },
            style = MaterialTheme.typography.labelSmall,
            color = if (snapshot.isPaused || !snapshot.telemetryLive) {
                MaterialTheme.colorScheme.tertiary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
            // A clipped word here is a half-sentence in the corner of a film.
            overflow = TextOverflow.Ellipsis
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
    // 2.4.5. A number the bike stopped sending is not a small number, it is no
    // number, and the strip already has a word for that — the same "--" a
    // missing heart-rate strap gets. A frozen 88 rpm over a rider who has
    // stopped pedalling is the one thing this display must never say.
    val live = snapshot.telemetryLive

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)
        ) {
            MetricReadout(
                label = "CADENCE",
                icon = MetricIcons.Cadence,
                value = if (live) reading.cadenceRpm.toInt().toString() else NO_READING,
                unit = "RPM",
                accent = MetricCadenceCyan,
                band = if (showTargets) snapshot.cadenceTarget else TargetBand.NONE,
                rawValue = reading.cadenceRpm,
                valueSize = 42.sp,
                modifier = Modifier.weight(1f)
            )
            MetricReadout(
                label = "RESISTANCE",
                icon = MetricIcons.Resistance,
                value = if (live) reading.resistancePercent.toInt().toString() else NO_READING,
                unit = "%",
                accent = MetricResistanceViolet,
                band = if (showTargets) snapshot.resistanceTarget else TargetBand.NONE,
                rawValue = reading.resistancePercent,
                valueSize = 42.sp,
                modifier = Modifier.weight(1f)
            )
            MetricReadout(
                label = "POWER",
                icon = MetricIcons.Power,
                value = if (live) reading.powerWatts.toInt().toString() else NO_READING,
                unit = "W",
                accent = MetricPowerCoral,
                band = if (showTargets) snapshot.powerTarget else TargetBand.NONE,
                rawValue = reading.powerWatts,
                valueSize = 42.sp,
                modifier = Modifier.weight(1f)
            )
            MetricReadout(
                label = "HEART RATE",
                icon = MetricIcons.HeartRate,
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

        // 11.6.2a. The ladder under the numbers it is a reading of, in its
        // compact form: segments and the zone digit, no watt labels. The
        // boundaries are the first thing that stops being legible at this size,
        // and a rider glancing at an overlay over a film is asking "how hard am
        // I going", not "what is the next boundary" — that question has the
        // ride screen. Two rows of 8 dp is the whole cost of it.
        PowerZoneScale(
            scale = ZoneScale.forReading(
                ftp = snapshot.ftpWatts,
                powerWatts = reading.powerWatts,
                telemetryLive = live,
                prescribed = if (showTargets) snapshot.interval.targetZone else null
            ),
            compact = true,
            modifier = Modifier.fillMaxWidth()
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
    volumeOpen: Boolean,
    onToggleVolume: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
        // Tonal rather than filled, and first in the row: it is the one control
        // here that is not about the ride, and it should not compete with pause
        // and stop for a glance.
        FilledIconButton(
            onClick = onToggleVolume,
            modifier = Modifier.size(52.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = if (volumeOpen) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                },
                contentColor = if (volumeOpen) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = stringResource(
                    if (volumeOpen) R.string.cd_hide_volume else R.string.cd_show_volume
                )
            )
        }
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
        StopButton(onStop = onStop)
    }
}

/**
 * Ending the ride from the strip, in two taps (11.6.6).
 *
 * The ride screen asks with a dialog; this window cannot. It is
 * `FLAG_NOT_FOCUSABLE` by design — the whole point of the strip is that it
 * never takes focus from the film — so the button asks for itself: the first
 * tap turns it into the question, the second answers it, and a few seconds of
 * no answer is an answer too.
 *
 * That is worth more here than on the ride screen, not less. This control sits
 * a thumb's width from pause, on the edge of a screen someone reaches past to
 * pick up a bottle, and there is no undo behind it.
 */
@Composable
private fun StopButton(onStop: () -> Unit) {
    var confirming by remember { mutableStateOf(false) }

    LaunchedEffect(confirming) {
        if (confirming) {
            delay(STOP_CONFIRM_TIMEOUT_MS)
            confirming = false
        }
    }

    val end = {
        if (confirming) {
            confirming = false
            onStop()
        } else {
            confirming = true
        }
    }

    // Two different components rather than one that swaps its content: an
    // IconButton sizes itself to a 52 dp circle whatever is inside it, so the
    // word wrapped to "EN / D?". A button that changed only its colour would be
    // worse — the rider has to be able to see that the second tap does
    // something the first one did not.
    if (confirming) {
        Button(
            onClick = end,
            modifier = Modifier.height(52.dp),
            shape = MaterialTheme.expressiveShapes.pill,
            contentPadding = PaddingValues(horizontal = MaterialTheme.spacing.large),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            )
        ) {
            Text(
                text = "END?",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                softWrap = false
            )
        }
    } else {
        FilledIconButton(
            onClick = end,
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
    opacity: Float,
    flash: Float,
    volumeOpen: Boolean,
    onToggleVolume: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit
) {
    val interval = snapshot.interval
    val wash = accent.copy(alpha = 0.30f * flash)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = HUD_MARGIN, vertical = MaterialTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)
    ) {
        // One chip, sized to its contents and left where the eye already knows
        // to look. The rest of the band is film — which is the entire point of
        // having collapsed it.
        HudChip(opacity = opacity, wash = wash, shape = MaterialTheme.expressiveShapes.pill) {
            Row(
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

                val live = snapshot.telemetryLive
                CompactMetric(
                    if (live) reading.cadenceRpm.toInt().toString() else NO_READING,
                    "RPM",
                    MetricCadenceCyan
                )
                CompactMetric(
                    if (live) reading.resistancePercent.toInt().toString() else NO_READING,
                    "%",
                    MetricResistanceViolet
                )
                CompactMetric(
                    if (live) reading.powerWatts.toInt().toString() else NO_READING,
                    "W",
                    MetricPowerCoral
                )
                CompactMetric(
                    reading.heartRateBpm?.toString() ?: "--",
                    "BPM",
                    MetricHeartRateGreen
                )
            }
        }

        Spacer(Modifier.weight(1f))

        val next = interval.next
        if (interval.isChangeImminent && next != null) {
            // The countdown survives collapsing. It is the one thing on this
            // HUD that is never optional — so it comes back solid in the next
            // zone's own colour rather than as text floating over the film.
            Row(
                modifier = Modifier
                    .clip(MaterialTheme.expressiveShapes.pill)
                    .background(next.powerZone.color)
                    .padding(horizontal = MaterialTheme.spacing.medium, vertical = 6.dp)
            ) {
                Text(
                    text = "Z${next.powerZone.number} in ${interval.remainingInIntervalSec}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = Color.Black.copy(alpha = 0.85f)
                )
            }
            Spacer(Modifier.width(MaterialTheme.spacing.extraSmall))
        }

        Controls(
            isPaused = snapshot.isPaused,
            volumeOpen = volumeOpen,
            onToggleVolume = onToggleVolume,
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

/**
 * Shown in place of a metric the bike is no longer reporting (2.4.5).
 *
 * The same two dashes an absent heart-rate strap has always used, for the same
 * reason: unknown is its own value and must never be drawn as a number.
 */
private const val NO_READING = "--"

/**
 * How long the strip's stop button waits for its second tap (11.6.6).
 *
 * Long enough to be answered by someone out of breath, short enough that the
 * button is back to being a stop button before the rider next glances at it.
 */
private const val STOP_CONFIRM_TIMEOUT_MS = 4_000L

/** How far the chips sit in from the screen's own edges. */
private val HUD_MARGIN = 12.dp
private const val DRAG_SNAP_PX = 12f
private val WIDE_BREAKPOINT = 900.dp
private val ROOMY_BREAKPOINT = 1100.dp
