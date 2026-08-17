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
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pelonot.R
import com.pelonot.core.Formatters
import com.pelonot.data.sensor.SensorReading
import com.pelonot.data.service.RideSnapshot
import com.pelonot.domain.coach.CoachStyle
import com.pelonot.domain.coach.PositionCallTracker
import com.pelonot.domain.model.GovernedBy
import com.pelonot.domain.model.HudDock
import com.pelonot.domain.model.HudOpacity
import com.pelonot.domain.model.IntervalState
import com.pelonot.domain.model.RideCue
import com.pelonot.domain.model.RidePosition
import com.pelonot.domain.model.TargetBand
import com.pelonot.domain.model.TargetEmphasis
import com.pelonot.domain.model.ZoneScale
import com.pelonot.ui.components.CountdownBanner
import com.pelonot.ui.components.HudPositionCall
import com.pelonot.ui.components.IntervalTimeline
import com.pelonot.ui.components.MetricIcons
import com.pelonot.ui.components.MetricReadout
import com.pelonot.ui.components.NextUpPreview
import com.pelonot.ui.components.PowerZoneScale
import com.pelonot.ui.components.ProgressArc
import com.pelonot.ui.components.ShrinkToFitText
import com.pelonot.ui.components.VolumeSliders
import com.pelonot.ui.components.ShrinkToFitText
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
 * docked to one edge and spans the whole of it, leaving the middle of the
 * screen — where faces and subtitles live — completely clear. It is not a
 * draggable card: dragging snaps it between the four screen edges, and nothing
 * parks it over the film.
 *
 * **A vertical dock re-flows rather than rotates** (11.1b.5). Docked left or
 * right the strip is a fixed-width column, so the chips stack, the four live
 * numbers go from one row of four to two rows of two, and the controls sit at
 * the far end of the column instead of the far end of a row. Every one of those
 * is the same component in a different arrangement; nothing here is drawn twice.
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
    onOpenSettings: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    volumeOpen: Boolean,
    mediaVolume: Float,
    coachVolume: Float,
    volumeError: String?,
    onToggleVolume: () -> Unit,
    onCloseVolume: () -> Unit,
    onMediaVolumeChange: (Float) -> Unit,
    onCoachVolumeChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    /** How solid the rider has asked the strip to be (11.1b.1). */
    opacity: Float = HudOpacity.DEFAULT
) {
    val interval = snapshot.interval
    val zone = interval.targetZone

    // 25.3.2. Stand or sit is the only instruction on this surface that has to
    // be acted on the instant it arrives, and it is therefore the only thing
    // allowed to move for its own sake — so it is driven by the **edge** and
    // never by the state. The tracker (the same one the spoken coach asks)
    // answers "is this boundary a call?"; the answer is held for a few seconds
    // and then dropped, and while it is null nothing on the strip moves.
    val positions = remember { PositionCallTracker() }
    var positionCall by remember { mutableStateOf<RidePosition?>(null) }
    LaunchedEffect(interval.index) {
        positionCall = positions.onInterval(interval.index, interval.current?.position)
        if (positionCall != null) {
            delay(POSITION_CALL_MS)
            positionCall = null
        }
    }

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

    // A hairline of the current zone's colour along the very screen edge, and
    // **it exists only while a change is coming** (11.1b.10). It thickens and
    // pulses on approach — the earliest warning on the whole HUD, and the one
    // that needs no reading at all. Full strength, never dimmed by the opacity
    // setting: it is an alert, and it is one pixel band of a screen the rider
    // has otherwise got back.
    //
    // At rest it used to sit at `alpha = 0.45`, which is exactly the weight of
    // a divider — so the owner reported it twice, once as "a weird grey line"
    // (zone 1's colour is grey) and once as "the HUD orange line". That it read
    // as a stray rule in two different colours is the answer: a hairline drawn
    // edge to edge across somebody's film **is** a rule, whatever colour it is.
    // Nothing is lost by its absence: at rest it was saying only what the zone
    // badge, the chips' wash and the ladder already say.
    //
    // Docked down a side it is the same hairline stood on its end: a rule along
    // the edge the strip is pinned to, whichever edge that is.
    val edge: @Composable () -> Unit = {
        val paint = accent.copy(alpha = if (interval.isChangeImminent) glowPulse else 0f)
        if (dock.isVertical) {
            Box(Modifier.fillMaxHeight().width(edgeGlow).background(paint))
        } else {
            Box(Modifier.fillMaxWidth().height(edgeGlow).background(paint))
        }
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
        val frame = modifier
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

        // The pieces are the same four whichever edge the strip is on; what
        // changes is whether they are stacked across the screen or down it, and
        // in which order — the handle and the hairline always end up on the
        // rider's side of the numbers, so the figures never move under them.
        val body: @Composable () -> Unit = {
            HudBody(
                snapshot = snapshot,
                reading = reading,
                dock = dock,
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
                dock = dock,
                opacity = panelOpacity,
                mediaVolume = mediaVolume,
                coachVolume = coachVolume,
                error = volumeError,
                onMediaVolumeChange = onMediaVolumeChange,
                onCoachVolumeChange = onCoachVolumeChange,
                onDismiss = onCloseVolume,
                onOpenSettings = onOpenSettings
            )
        }

        // Always on the *inner* side of the numbers, whichever edge the
        // strip is docked against, so the window grows into the screen and
        // the figures the rider is reading do not move underneath them.
        // It survives collapsing for the same reason the countdown does: a
        // rider who has given the film back the rest of the band still has
        // to be told to stand up.
        val positionCue: @Composable () -> Unit = {
            HudPositionCall(call = positionCall, animate = coachStyle.animates)
        }

        val handle: @Composable () -> Unit = {
            HudHandle(dock, collapsed, panelOpacity, onToggleCollapsed, onDockChange)
        }

        // The handle and the hairline sit on the rider's side of the numbers —
        // the *inner* side for the handle, the screen edge itself for the
        // hairline — so both are ordered by which end of the axis the strip is
        // pinned to. Everything else is the same call in both branches.
        val edgeFirst = dock == HudDock.Bottom || dock == HudDock.Right

        if (dock.isVertical) {
            // Docked down a side, the volume panel and the stand/sit cue stack
            // *inside* the column rather than beside it: the window is a fixed
            // 244 dp wide, so anything placed alongside the numbers would be
            // taking width off the one thing the rider is reading.
            val column: @Composable () -> Unit = {
                body(); positionCue(); volumePanel()
            }
            Row(
                modifier = frame,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (edgeFirst) {
                    edge()
                    handle()
                    Column(Modifier.weight(1f)) { column() }
                } else {
                    Column(Modifier.weight(1f)) { column() }
                    handle()
                    edge()
                }
            }
        } else {
            Column(modifier = frame.fillMaxWidth()) {
                if (edgeFirst) {
                    edge(); handle(); volumePanel(); positionCue(); body()
                } else {
                    body(); positionCue(); volumePanel(); handle(); edge()
                }
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
    dock: HudDock = HudDock.DEFAULT,
    collapsed: Boolean = false,
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
        // Docked down a side, the strip is a full-height column and this bar
        // takes the top edge (`HudDock.timelineEdge`), so the two would meet in
        // a corner. The bar steps aside by exactly the strip's own width rather
        // than being drawn under it: a timeline whose first minutes are hidden
        // is worse than a slightly shorter one, and the width is a constant
        // precisely so this can be an inset rather than a guess.
        // Whichever of the two vertical widths is live (11.1b.11). Insetting
        // by the expanded one while the strip is collapsed would hand back a
        // hundred dp of film to nothing at all.
        val strip = HudDock.widthDp(dock, collapsed).dp
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(
                    start = if (dock == HudDock.Left) strip else HUD_MARGIN,
                    end = if (dock == HudDock.Right) strip else HUD_MARGIN,
                    top = MaterialTheme.spacing.small,
                    bottom = MaterialTheme.spacing.small
                )
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
 * The grab bar. Tapping collapses the HUD to a slim strip; dragging it towards
 * a screen edge sends it there.
 *
 * A small pill rather than the full-width invisible band it used to be: with no
 * panel behind it, a 1280 dp drag target that shows nothing is a gesture the
 * rider fires by accident over their film and can never find on purpose.
 *
 * **The drag is two-dimensional since 11.1b.4**, because there are four edges
 * to reach. The rule lives in [HudDock.dragTarget] rather than here: it is the
 * one piece of this surface that is a decision rather than a drawing, the
 * dominant axis decides so a drag that wanders is read as where it mostly went,
 * and being pure it can be tested without a window.
 *
 * The whole gesture is measured rather than each `onDrag` step. Deciding per
 * step meant a slow drag never crossed the threshold in a single callback and a
 * fast one fired on whichever axis happened to move first; both are the same
 * bug, which is that a drag is a shape and not a sample.
 */
@Composable
private fun HudHandle(
    dock: HudDock,
    collapsed: Boolean,
    opacity: Float,
    onToggleCollapsed: () -> Unit,
    onDockChange: (HudDock) -> Unit
) {
    val snap = with(LocalDensity.current) { DRAG_SNAP_DP.dp.toPx() }
    var dragX by remember { mutableFloatStateOf(0f) }
    var dragY by remember { mutableFloatStateOf(0f) }

    val pill = @Composable {
        Box(
            modifier = Modifier
                .then(
                    if (dock.isVertical) {
                        Modifier.size(width = 18.dp, height = 72.dp)
                    } else {
                        Modifier.size(width = 72.dp, height = 18.dp)
                    }
                )
                .clip(MaterialTheme.expressiveShapes.pill)
                .background(hudChipColor(opacity))
                .clickable(onClick = onToggleCollapsed)
                .pointerInput(dock) {
                    detectDragGestures(
                        onDragStart = { dragX = 0f; dragY = 0f },
                        onDragEnd = {
                            HudDock.dragTarget(dock, dragX, dragY, snap)
                                ?.let(onDockChange)
                            dragX = 0f
                            dragY = 0f
                        }
                    ) { change, amount ->
                        change.consume()
                        dragX += amount.x
                        dragY += amount.y
                    }
                }
                .semantics {
                    contentDescription = if (collapsed) {
                        "Expand the ride overlay. Drag it to any screen edge, " +
                            "or double tap it to open Pelonot."
                    } else {
                        "Collapse the ride overlay. Drag it to any screen edge, " +
                            "or double tap it to open Pelonot."
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .then(
                        if (dock.isVertical) {
                            Modifier.size(width = 3.dp, height = 36.dp)
                        } else {
                            Modifier.size(width = 36.dp, height = 3.dp)
                        }
                    )
                    .clip(MaterialTheme.expressiveShapes.pill)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
            )
        }
    }

    // Aligned with the chips rather than centred in the window. Centring it
    // looks deliberate while the strip spans the edge and looks like a stray
    // object the moment it does not — which, collapsed, it does not.
    if (dock.isVertical) {
        Box(
            modifier = Modifier.padding(vertical = HUD_MARGIN, horizontal = 3.dp),
            contentAlignment = Alignment.Center
        ) { pill() }
    } else {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = HUD_MARGIN, vertical = 3.dp),
            contentAlignment = Alignment.CenterStart
        ) { pill() }
    }
}

@Composable
private fun HudBody(
    snapshot: RideSnapshot,
    reading: SensorReading,
    dock: HudDock,
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
        when {
            // Collapsed down a side, everything the band held in a row has to
            // stack — which is the one place the vertical dock genuinely earns
            // its own composable rather than a different arrangement of the
            // same one.
            isCollapsed && dock.isVertical -> HudCollapsedVertical(
                snapshot, reading, accent, opacity, flash,
                volumeOpen, onToggleVolume, onPause, onResume, onStop
            )
            isCollapsed -> HudCollapsed(
                snapshot, reading, accent, opacity, flash,
                volumeOpen, onToggleVolume, onPause, onResume, onStop
            )
            dock.isVertical -> HudExpandedVertical(
                snapshot, reading, coachStyle, accent, opacity, flash,
                volumeOpen, onToggleVolume, onPause, onResume, onStop
            )
            else -> HudExpanded(
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
    dock: HudDock,
    opacity: Float,
    mediaVolume: Float,
    coachVolume: Float,
    error: String?,
    onMediaVolumeChange: (Float) -> Unit,
    onCoachVolumeChange: (Float) -> Unit,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit
) {
    // 11.5.9. It opened and closed from one small button, so a rider who opened
    // it mid-ride had to find that same control again with the sliders now in
    // the way. Two ways out instead.
    //
    // The swipe goes *towards the strip's own edge* — up when docked top, down
    // when docked bottom — so the direction follows the dock rather than being
    // hardcoded, and the panel folds back into the strip it came out of.
    //
    // On a **vertical** dock the panel sits inside the column, so the edge it
    // would fold into is left or right — and that is the one axis a slider is
    // already consuming. It gets the same vertical swipe as a bottom dock
    // instead: a flick down the column, away from the numbers. The two must not
    // fight over the same finger, and the slider's claim wins because it is the
    // control the rider actually came here for.
    val threshold = with(LocalDensity.current) { VOLUME_DISMISS_DP.dp.toPx() }

    // And a timeout, because this panel is the one part of the strip that is
    // not about the next sixty seconds of pedalling (11.5.5): left open it is
    // just film the rider has lost. Keyed on the volumes, so every adjustment
    // restarts the clock and it fires only once they have actually stopped.
    LaunchedEffect(visible, mediaVolume, coachVolume) {
        if (!visible) return@LaunchedEffect
        delay(VOLUME_IDLE_MS)
        onDismiss()
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        var dragged by remember { mutableFloatStateOf(0f) }

        val swipe = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = HUD_MARGIN,
                vertical = MaterialTheme.spacing.extraSmall
            )
            .pointerInput(dock) {
                detectVerticalDragGestures(
                    onDragStart = { dragged = 0f },
                    onDragEnd = {
                        val towardsEdge = when (dock) {
                            HudDock.Top -> dragged <= -threshold
                            HudDock.Bottom, HudDock.Left, HudDock.Right ->
                                dragged >= threshold
                        }
                        if (towardsEdge) onDismiss()
                        dragged = 0f
                    }
                ) { _, amount -> dragged += amount }
            }

        val sliders: @Composable (Modifier) -> Unit = { m ->
            HudChip(opacity = opacity, modifier = m) {
                VolumeSliders(
                    mediaVolume = mediaVolume,
                    coachVolume = coachVolume,
                    onMediaVolumeChange = onMediaVolumeChange,
                    onCoachVolumeChange = onCoachVolumeChange,
                    error = error,
                    compact = true
                )
            }
        }

        // 11.6.10. The overlay's route into the settings a rider discovers
        // they need mid-ride — a strap that never paired, a board that has
        // died. It lives here rather than as a fifth button on the resting
        // strip, because this panel is already where a rider comes when
        // they want to change something rather than read something, and the
        // strip's job is the next sixty seconds of pedalling.
        val moreSettings: @Composable (Modifier) -> Unit = { m ->
            HudChip(opacity = opacity, modifier = m) {
                TextButton(onClick = onOpenSettings) {
                    Text("More settings", style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        if (dock.isVertical) {
            // The column is already the narrow dimension, so there is no half
            // of it to leave as film — the two stack instead.
            Column(
                modifier = swipe,
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)
            ) {
                sliders(Modifier.fillMaxWidth())
                moreSettings(Modifier.fillMaxWidth())
            }
        } else {
            Row(modifier = swipe) {
                // Half the width, not the whole of it: this is two sliders, and
                // a panel that reaches the far edge of a 1280 dp screen to hold
                // them is covering film for nothing.
                sliders(Modifier.weight(1f))
                moreSettings(Modifier.padding(start = MaterialTheme.spacing.small))
                Spacer(Modifier.weight(1f))
            }
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
 * The same strip, stood on its end (11.1b.5).
 *
 * A column of 244 dp is not a row with less width in it, so this re-flows
 * rather than shrinking: the chips stack, the four live numbers go from one row
 * of four to two rows of two, and the controls sit at the far end of the column
 * where the eye finishes rather than beside the numbers.
 *
 * Two things are deliberately dropped rather than squeezed. The **next-up
 * preview** goes, because the timeline bar still runs across the top edge and
 * the countdown replaces it wholesale the moment a change is imminent — which
 * is the part that is never optional. And the zone ladder keeps its place: it
 * is a 8 dp band that reads at a glance, which is the whole reason it survived
 * the horizontal strip's own width budget.
 */
@Composable
private fun HudExpandedVertical(
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

        HudChip(opacity = opacity, wash = wash, modifier = Modifier.fillMaxWidth()) {
            ClockBlock(snapshot, Modifier.fillMaxWidth())
        }

        if (interval.hasClass) {
            HudChip(opacity = opacity, wash = wash, modifier = Modifier.fillMaxWidth()) {
                NowBlock(snapshot, accent, Modifier.fillMaxWidth(), compact = true)
            }
        }

        HudChip(opacity = opacity, wash = wash, modifier = Modifier.fillMaxWidth()) {
            MetricsBlock(
                snapshot = snapshot,
                reading = reading,
                showTargets = interval.hasClass,
                stacked = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // The countdown only. It is the one element on this HUD that is never
        // optional, and in a column there is no width to trade it against.
        if (interval.hasClass && interval.isChangeImminent) {
            interval.next?.let { next ->
                HudChip(
                    opacity = opacity,
                    wash = wash,
                    padding = PaddingValues(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    CountdownBanner(
                        secondsRemaining = interval.remainingInIntervalSec,
                        nextZone = next.powerZone,
                        animate = coachStyle.animates
                    )
                }
            }
        }

        // Directly under the numbers rather than pinned to the foot of the
        // side. Pinned, they sat 400 px clear of the last chip and the strip
        // read as two objects instead of one instrument.
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
private fun NowBlock(
    snapshot: RideSnapshot,
    accent: Color,
    modifier: Modifier = Modifier,
    /** A vertical dock has 190 dp to put this in, against the band's 216. */
    compact: Boolean = false
) {
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
            modifier = Modifier.size(if (compact) 64.dp else 78.dp)
        ) {
            ZoneGlyph(
                zone = zone,
                modifier = Modifier.size(if (compact) 44.dp else 54.dp),
                rotating = zone.number >= 5
            ) {
                Text(
                    text = "${zone.number}",
                    fontSize = if (compact) 20.sp else 24.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.Black.copy(alpha = 0.8f)
                )
            }
        }

        Spacer(
            Modifier.width(
                if (compact) MaterialTheme.spacing.small else MaterialTheme.spacing.medium
            )
        )

        Column {
            Text(
                text = zone.displayName.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = accent,
                fontWeight = FontWeight.Black,
                // A column has the one thing the band never had, which is
                // height — so `ACTIVE RECOVERY` wraps rather than becoming
                // `ACTIVE RECO…`. This is the zone's *name*, and a name is the
                // thing on the strip least worth truncating.
                maxLines = if (compact) 2 else 1,
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
            // 11.7.4. The strip has room for one instruction and the ride
            // screen has room for three, so this is where "one instruction at
            // a time" either works or does not — it is the surface a rider
            // actually watches for forty minutes. It used to say the cadence
            // band *and* a resistance percentage inverted out of a power curve
            // that is 66% out at the median, under a zone name, which is three
            // answers to a question with one.
            //
            // Now it is the governing metric's target and nothing else's, and
            // that is the first principled answer this line has had to what it
            // drops when it runs out of width.
            val instruction = when (snapshot.governedBy) {
                GovernedBy.Cadence -> snapshot.cadenceTarget.label?.let { "$it RPM" }
                // Watts rather than the zone: the zone is already the ring and
                // the word beside it, and a rider steering their resistance
                // needs the number the ladder does not give them.
                GovernedBy.Power -> snapshot.powerTarget.label?.let { "$it W" }
            }
            if (instruction != null) {
                Text(
                    text = instruction,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun MetricsBlock(
    snapshot: RideSnapshot,
    reading: SensorReading,
    showTargets: Boolean,
    modifier: Modifier = Modifier,
    /**
     * Two rows of two rather than one row of four, for a vertical dock
     * (11.1b.5).
     *
     * The pairing is the same one the row already reads left to right, so a
     * rider who has moved the strip does not have to re-learn where a number
     * is: **what you change** on the top row, **what it produces** underneath.
     */
    stacked: Boolean = false
) {
    // Cadence and resistance first, together: they are the only two things the
    // rider can actually change. Power is what those two produce, and heart
    // rate is what the body makes of it.
    // 2.4.5. A number the bike stopped sending is not a small number, it is no
    // number, and the strip already has a word for that — the same "--" a
    // missing heart-rate strap gets. A frozen 88 rpm over a rider who has
    // stopped pedalling is the one thing this display must never say.
    val live = snapshot.telemetryLive

    // One list of four, arranged either way. Written out once because four
    // readouts duplicated across two branches is four places for a target band
    // or a "--" to go quietly missing on one dock and not the other.
    val cells: List<@Composable (Modifier) -> Unit> = listOf(
        { m ->
            MetricReadout(
                label = "CADENCE",
                icon = MetricIcons.Cadence,
                value = if (live) reading.cadenceRpm.toInt().toString() else NO_READING,
                unit = "RPM",
                accent = MetricCadenceCyan,
                band = snapshot.cadenceTarget,
                emphasis = if (showTargets) snapshot.cadenceEmphasis else TargetEmphasis.None,
                rawValue = reading.cadenceRpm,
                valueSize = 42.sp,
                modifier = m
            )
        },
        { m ->
            MetricReadout(
                label = "RESISTANCE",
                icon = MetricIcons.Resistance,
                value = if (live) reading.resistancePercent.toInt().toString() else NO_READING,
                unit = "%",
                accent = MetricResistanceViolet,
                // 11.7.3. The knob, not a target — and no class prescribes it.
                band = TargetBand.NONE,
                emphasis = TargetEmphasis.None,
                rawValue = reading.resistancePercent,
                valueSize = 42.sp,
                modifier = m
            )
        },
        { m ->
            MetricReadout(
                label = "POWER",
                icon = MetricIcons.Power,
                value = if (live) reading.powerWatts.toInt().toString() else NO_READING,
                unit = "W",
                accent = MetricPowerCoral,
                band = snapshot.powerTarget,
                emphasis = if (showTargets) snapshot.powerEmphasis else TargetEmphasis.None,
                rawValue = reading.powerWatts,
                valueSize = 42.sp,
                modifier = m
            )
        },
        { m ->
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
                modifier = m
            )
        }
    )

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val gap = Arrangement.spacedBy(MaterialTheme.spacing.small)
        if (stacked) {
            Row(horizontalArrangement = gap) {
                cells[0](Modifier.weight(1f))
                cells[1](Modifier.weight(1f))
            }
            Row(horizontalArrangement = gap) {
                cells[2](Modifier.weight(1f))
                cells[3](Modifier.weight(1f))
            }
        } else {
            Row(horizontalArrangement = gap) {
                cells.forEach { cell -> cell(Modifier.weight(1f)) }
            }
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
    onStop: () -> Unit,
    /**
     * Stacked rather than in a row — **the owner's words**, 11.1b.11: *"the
     * play/pause/end buttons should be aligned vertically"*.
     *
     * True only for the collapsed vertical strip, and that is the whole of the
     * argument: three 52 dp buttons side by side is 172 dp, and on a strip whose
     * job is to give the screen back it was the buttons setting the width. The
     * expanded column has 244 dp to spend and keeps the row, so the two states
     * differ where they have a reason to.
     */
    stacked: Boolean = false
) {
    val buttons: @Composable () -> Unit = {
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

    val gap = Arrangement.spacedBy(MaterialTheme.spacing.small)
    if (stacked) {
        Column(
            verticalArrangement = gap,
            horizontalAlignment = Alignment.CenterHorizontally
        ) { buttons() }
    } else {
        Row(horizontalArrangement = gap) { buttons() }
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

/**
 * Collapsed, down a side.
 *
 * The horizontal version is one pill and a row of buttons with the whole middle
 * of the band left as film. A column cannot spend width that way, so the numbers
 * stack into a short chip at the top and the controls stay at the bottom where
 * they are in every other state. What the rider gets back is the rest of the
 * side.
 *
 * **And since 11.1b.11 it gets back most of the width too.** This state has its
 * own window width — [HudDock.VERTICAL_COLLAPSED_WIDTH_DP], 132 dp against the
 * expanded strip's 244 — which is only possible because the transport buttons
 * stack here: in a row they are 172 dp on their own, and they were setting the
 * width of the one state whose whole purpose is to be out of the way. The owner
 * reported both halves of that in one sentence.
 *
 * What decides 132 rather than something smaller is the readouts, and the first
 * build of this got that wrong: `143 BPM` on one line drew as `143 BP` with a
 * lone `M` under it. **The unit was never a candidate for dropping**
 * (11.1b.11a) — a ride surface is one of the few places Phase 26 says a unit
 * belongs — so the pair stacks instead, which also takes the strip's width out
 * of the hands of how many characters a unit happens to have.
 */
@Composable
private fun HudCollapsedVertical(
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
    val live = snapshot.telemetryLive

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = HUD_MARGIN, vertical = MaterialTheme.spacing.small),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)
    ) {
        HudChip(opacity = opacity, wash = wash) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall)
            ) {
                // 132 dp is narrower than `03:14` at this weight — measured on
                // the tablet, where the same clock had fitted a minute earlier
                // reading `01:51`, because a `1` is half the width of a `3`. A
                // clock whose wrapping depends on which digits the ride happens
                // to be showing is exactly the class of defect `ShrinkToFitText`
                // exists for, so it is sized against the widest string of *its
                // own shape*: the type can step down once, at the hour, and
                // never pulses as the seconds turn over.
                ShrinkToFitText(
                    text = Formatters.duration(snapshot.elapsedSeconds),
                    fontSize = MaterialTheme.typography.headlineSmall.fontSize,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    measureAgainst = if (snapshot.elapsedSeconds >= SECONDS_IN_HOUR) {
                        "00:00:00"
                    } else {
                        "00:00"
                    }
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

                CompactMetric(
                    if (live) reading.cadenceRpm.toInt().toString() else NO_READING,
                    "RPM",
                    MetricCadenceCyan,
                    stacked = true
                )
                CompactMetric(
                    if (live) reading.resistancePercent.toInt().toString() else NO_READING,
                    "%",
                    MetricResistanceViolet,
                    stacked = true
                )
                CompactMetric(
                    if (live) reading.powerWatts.toInt().toString() else NO_READING,
                    "W",
                    MetricPowerCoral,
                    stacked = true
                )
                CompactMetric(
                    reading.heartRateBpm?.toString() ?: "--",
                    "BPM",
                    MetricHeartRateGreen,
                    stacked = true
                )
            }
        }

        val next = interval.next
        if (interval.isChangeImminent && next != null) {
            // Survives collapsing here for the same reason it does on the band.
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
        }

        Controls(
            isPaused = snapshot.isPaused,
            volumeOpen = volumeOpen,
            onToggleVolume = onToggleVolume,
            onPause = onPause,
            onResume = onResume,
            onStop = onStop,
            stacked = true
        )
    }
}

/**
 * A live number and its unit, side by side — or stacked, where there is no width
 * for both (11.1b.11).
 *
 * **Found by looking at the tablet rather than at the diff**, on the first build
 * of the narrower collapsed strip: `143 BPM` drew as `143 BP` with a lone `M`
 * underneath it. That is the same family as `RESISTANC` and `100 RP` before it —
 * a readout measured against a width somebody else chose — and it is the third
 * time in this component's history. The two answers this project allows are
 * *smaller* and *wrapped*, never *cut*; here the honest one is neither, because
 * the thing that does not fit is a **pair**, not a word.
 *
 * So the pair stacks. The unit keeps its own line, at full size, and the width
 * of the strip stops depending on how many characters a unit happens to have —
 * which also means a rider with larger system text does not reopen the same
 * defect. **The unit is not dropped**, and 11.1b.11a is where that is written
 * down: a ride surface is one of the few places Phase 26 says a unit belongs.
 */
@Composable
private fun CompactMetric(
    value: String,
    unit: String,
    accent: Color,
    stacked: Boolean = false
) {
    val number: @Composable () -> Unit = {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Black,
            color = accent
        )
    }
    val label: @Composable () -> Unit = {
        Text(
            text = unit,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = if (stacked) Modifier else Modifier.padding(bottom = 3.dp)
        )
    }

    if (stacked) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            number()
            label()
        }
    } else {
        Row(verticalAlignment = Alignment.Bottom) {
            number()
            Spacer(Modifier.width(3.dp))
            label()
        }
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

/**
 * How far the volume panel has to be pushed towards the dock edge to close it
 * (11.5.9), and how long it stays open with nobody touching it.
 *
 * The distance is comfortably more than a slider's own travel would produce as
 * incidental vertical movement, and the timeout is long enough to set two
 * levels one after the other without the panel vanishing mid-thought.
 */
private const val VOLUME_DISMISS_DP = 40
private const val VOLUME_IDLE_MS = 8_000L

/**
 * How long the stand/sit cue stays up before the strip goes quiet again
 * (25.3.1).
 *
 * The whole design of this overlay is about not competing with the film, and
 * this is the one thing on it that deliberately does — so the interesting
 * number is not how long it takes to notice but how soon it stops. Six seconds
 * covers the spoken announcement plus the second or two it takes to get out of
 * the saddle, and is well short of the shortest block in the library.
 */
private const val POSITION_CALL_MS = 6_000L

/** How far the chips sit in from the screen's own edges. */
private val HUD_MARGIN = 12.dp

/** So the collapsed clock can ask whether it has grown a third field. */
private const val SECONDS_IN_HOUR = 3600

/**
 * How far the handle has to travel before the strip changes edge (11.1b.4).
 *
 * In **dp** rather than the raw pixels it used to be, which mattered less when
 * the gesture was one axis and a decisive flick: on this tablet's 240 dpi the
 * old 12 px was 8 dp, and a two-dimensional gesture that fires at 8 dp is one a
 * rider trips over reaching past the handlebars. 40 dp is deliberate travel and
 * still well short of a drag across the screen — the direction is what is being
 * asked, not the distance.
 */
private const val DRAG_SNAP_DP = 40
private val WIDE_BREAKPOINT = 900.dp
private val ROOMY_BREAKPOINT = 1100.dp
