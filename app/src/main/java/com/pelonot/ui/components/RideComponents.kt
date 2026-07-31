package com.pelonot.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pelonot.core.Formatters
import com.pelonot.domain.model.Interval
import com.pelonot.domain.model.PowerZone
import com.pelonot.domain.model.TargetBand
import com.pelonot.domain.model.TargetStatus
import com.pelonot.ui.theme.AlertAmber
import com.pelonot.ui.theme.color
import com.pelonot.ui.theme.expressiveShapes
import com.pelonot.ui.theme.spacing

// ==========================================================================
// Attention without sound
// ==========================================================================

/**
 * A spring bounce that fires whenever [trigger] changes.
 *
 * This is what the HUD uses instead of a voice when the rider has the coach
 * set to Silent: an interval change is a physical event on screen — the panel
 * overshoots and settles — which the eye catches from the edge of vision while
 * watching something else. Disabled entirely when the rider wants nothing.
 */
@Composable
fun Modifier.attentionBounce(
    trigger: Any?,
    enabled: Boolean = true,
    magnitude: Float = 0.06f
): Modifier {
    if (!enabled) return this

    val scale = remember { Animatable(1f) }
    LaunchedEffect(trigger) {
        // Skip the bounce on first composition; nothing has changed yet.
        if (trigger == null) return@LaunchedEffect
        scale.snapTo(1f - magnitude * 0.5f)
        scale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
    }
    return graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}

/** A slow pulse for anything that needs to look alive without shouting. */
@Composable
fun rememberPulse(periodMs: Int = 900, from: Float = 0.55f, to: Float = 1f): Float {
    val transition = rememberInfiniteTransition(label = "Pulse")
    val value by transition.animateFloat(
        initialValue = from,
        targetValue = to,
        animationSpec = infiniteRepeatable(
            animation = tween(periodMs, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseValue"
    )
    return value
}

// ==========================================================================
// Target gauge
// ==========================================================================

/**
 * "Am I on target?" as a picture.
 *
 * A rider at 240 W being asked for 250–280 W should not have to compare two
 * numbers while breathing hard. The band is drawn in the accent colour, the
 * marker is where they actually are, and being outside turns the marker amber
 * and slides it toward the end of the gauge rather than clamping it there, so
 * *how far* out is visible too.
 */
@Composable
fun TargetGauge(
    band: TargetBand,
    value: Double,
    accent: Color,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 10.dp
) {
    val status = band.statusFor(value)
    val markerFraction by animateFloatAsState(
        targetValue = if (band.isDefined) band.fractionOf(value) else 0.5f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "GaugeMarker"
    )
    val markerColor by animateColorAsState(
        targetValue = if (status.isOffTarget) AlertAmber else accent,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "GaugeMarkerColor"
    )

    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val bandColor = accent.copy(alpha = 0.30f)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clearAndSetSemantics {
                contentDescription = when (status) {
                    TargetStatus.Unset -> "No target set"
                    TargetStatus.Below -> "Below target"
                    TargetStatus.OnTarget -> "On target"
                    TargetStatus.Above -> "Above target"
                }
            }
    ) {
        val radius = CornerRadius(size.height / 2f, size.height / 2f)
        drawRoundRect(color = trackColor, cornerRadius = radius)

        if (band.isDefined) {
            val start = band.bandStartFraction * size.width
            val end = band.bandEndFraction * size.width
            drawRoundRect(
                color = bandColor,
                topLeft = Offset(start, 0f),
                size = Size((end - start).coerceAtLeast(1f), size.height),
                cornerRadius = radius
            )
        }

        // The marker is a lozenge rather than a hairline: it has to be legible
        // over video at arm's length, on a bike.
        val markerWidth = size.height * 0.9f
        val x = (markerFraction * size.width).coerceIn(markerWidth / 2f, size.width - markerWidth / 2f)
        drawRoundRect(
            color = markerColor,
            topLeft = Offset(x - markerWidth / 2f, -size.height * 0.25f),
            size = Size(markerWidth, size.height * 1.5f),
            cornerRadius = CornerRadius(markerWidth / 2f, markerWidth / 2f)
        )
    }
}

// ==========================================================================
// Metric readout
// ==========================================================================

/**
 * One live number with its target gauge beneath it.
 *
 * The value is the largest thing in the tile and the label the smallest,
 * because at a glance the rider already knows which tile is which — they know
 * where power lives — and only needs the digits.
 */
@Composable
fun MetricReadout(
    label: String,
    value: String,
    unit: String,
    accent: Color,
    modifier: Modifier = Modifier,
    band: TargetBand = TargetBand.NONE,
    rawValue: Double = 0.0,
    valueSize: androidx.compose.ui.unit.TextUnit = 56.sp,
    compact: Boolean = false
) {
    val status = band.statusFor(rawValue)
    val valueColor by animateColorAsState(
        // Amber, not red. Power's own accent is coral, and a coral number
        // turning red is not a signal anybody can read at a glance — the two
        // are three hue degrees apart. Amber is unmistakably neither.
        targetValue = if (status.isOffTarget) AlertAmber else accent,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "MetricValueColor"
    )

    Column(
        modifier = modifier.clearAndSetSemantics {
            contentDescription = "$label $value $unit${status.spokenSuffix}"
        },
        horizontalAlignment = Alignment.Start
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                fontSize = valueSize,
                lineHeight = valueSize,
                fontWeight = FontWeight.Black,
                letterSpacing = (-2).sp,
                color = valueColor,
                maxLines = 1
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = unit,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = valueSize.value.dp * 0.12f)
            )
        }

        if (!compact) {
            Spacer(Modifier.height(6.dp))
            TargetGauge(band = band, value = rawValue, accent = accent)
            Spacer(Modifier.height(4.dp))
        }

        // Colour alone is never the whole signal: the direction is spelled out
        // beside the label, which also survives a rider who cannot tell amber
        // from coral.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold
            )
            if (status.isOffTarget) {
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (status == TargetStatus.Below) "▼ LOW" else "▲ HIGH",
                    style = MaterialTheme.typography.labelSmall,
                    color = AlertAmber,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}

private val TargetStatus.spokenSuffix: String
    get() = when (this) {
        TargetStatus.Below -> ", below target"
        TargetStatus.Above -> ", above target"
        else -> ""
    }

// ==========================================================================
// Class timeline
// ==========================================================================

/**
 * The whole class as one strip: every interval in its zone colour, sized by
 * duration, with a playhead.
 *
 * This is the single most useful thing on the HUD after the numbers — it
 * answers "how much of this is left, and does it get harder?" without any
 * reading at all.
 */
@Composable
fun IntervalTimeline(
    intervals: List<Interval>,
    elapsedSec: Int,
    durationSec: Int,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 10.dp,
    currentIndex: Int = -1
) {
    if (intervals.isEmpty() || durationSec <= 0) return

    val progress by animateFloatAsState(
        targetValue = (elapsedSec.toFloat() / durationSec).coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 900, easing = LinearEasing),
        label = "TimelineProgress"
    )

    val zoneColors = remember(intervals) { intervals.map { it.powerZone.color } }
    val playheadColor = MaterialTheme.colorScheme.onSurface

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clearAndSetSemantics {
                contentDescription = "Class timeline, ${Formatters.duration(elapsedSec)} " +
                    "of ${Formatters.duration(durationSec)}"
            }
    ) {
        val gap = 2f
        intervals.forEachIndexed { index, interval ->
            val left = (interval.startSec.toFloat() / durationSec) * size.width
            val right = (interval.endSec.toFloat() / durationSec) * size.width
            val isPast = interval.endSec <= elapsedSec
            val isCurrent = index == currentIndex

            // Segments the rider has already ridden fade back; the one they are
            // in is full strength and taller.
            val alpha = when {
                isCurrent -> 1f
                isPast -> 0.30f
                else -> 0.65f
            }
            val inset = if (isCurrent) 0f else size.height * 0.2f

            drawRoundRect(
                color = zoneColors[index].copy(alpha = alpha),
                topLeft = Offset(left, inset),
                size = Size((right - left - gap).coerceAtLeast(1f), size.height - inset * 2),
                cornerRadius = CornerRadius(size.height / 2f, size.height / 2f)
            )
        }

        val x = progress * size.width
        drawRoundRect(
            color = playheadColor,
            topLeft = Offset(x - 1.5f, -size.height * 0.15f),
            size = Size(3f, size.height * 1.3f),
            cornerRadius = CornerRadius(2f, 2f)
        )
    }
}

// ==========================================================================
// Countdown into the next interval
// ==========================================================================

/**
 * The five seconds before the effort changes.
 *
 * Non-negotiable on this HUD: the rider is looking at something else, and an
 * interval that arrives unannounced is one they start four seconds late. The
 * digit is enormous, the next zone's colour floods the background, and the
 * whole thing re-bounces on every tick so it is impossible to miss even with
 * the sound off.
 */
@Composable
fun CountdownBanner(
    secondsRemaining: Int,
    nextZone: PowerZone,
    modifier: Modifier = Modifier,
    animate: Boolean = true
) {
    val zoneColor = nextZone.color
    val pulse = if (animate) rememberPulse(periodMs = 500, from = 0.7f, to = 1f) else 1f

    Row(
        modifier = modifier
            .clip(MaterialTheme.expressiveShapes.pill)
            .background(zoneColor.copy(alpha = 0.22f * pulse))
            .attentionBounce(trigger = secondsRemaining, enabled = animate, magnitude = 0.12f)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        ZoneGlyph(
            zone = nextZone,
            modifier = Modifier.size(34.dp)
        ) {
            Text(
                text = "${nextZone.number}",
                style = MaterialTheme.typography.labelLarge,
                color = Color.Black.copy(alpha = 0.75f),
                fontWeight = FontWeight.Black
            )
        }

        Spacer(Modifier.width(12.dp))

        Column {
            Text(
                text = "NEXT UP",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = nextZone.displayName.uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = zoneColor,
                fontWeight = FontWeight.Black,
                maxLines = 1
            )
        }

        Spacer(Modifier.width(20.dp))

        Text(
            text = "$secondsRemaining",
            fontSize = 44.sp,
            lineHeight = 44.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = (-2).sp,
            color = zoneColor
        )
    }
}

// ==========================================================================
// Next interval preview
// ==========================================================================

/**
 * What is coming, and when.
 *
 * Shown for the whole of the current interval rather than only during the
 * countdown, because knowing that four minutes of Zone 5 is next changes how
 * you ride the Zone 2 before it. Never rendered on the final interval — see
 * [com.pelonot.domain.model.IntervalState.next].
 */
@Composable
fun NextUpPreview(
    next: Interval,
    secondsUntil: Int,
    modifier: Modifier = Modifier,
    imminent: Boolean = false
) {
    val zone = next.powerZone
    val borderAlpha by animateFloatAsState(
        targetValue = if (imminent) 0.9f else 0.25f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "NextUpBorder"
    )

    Row(
        modifier = modifier
            .clip(MaterialTheme.expressiveShapes.large)
            .background(zone.color.copy(alpha = 0.10f + borderAlpha * 0.12f))
            .padding(horizontal = MaterialTheme.spacing.medium, vertical = MaterialTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ZoneGlyph(zone = zone, modifier = Modifier.size(38.dp)) {
            Text(
                text = "${zone.number}",
                style = MaterialTheme.typography.titleMedium,
                color = Color.Black.copy(alpha = 0.75f),
                fontWeight = FontWeight.Black
            )
        }

        Spacer(Modifier.width(MaterialTheme.spacing.medium))

        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "NEXT",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "in ${Formatters.duration(secondsUntil)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = zone.displayName.uppercase(),
                style = MaterialTheme.typography.titleSmall,
                color = zone.color,
                fontWeight = FontWeight.Black,
                maxLines = 1
            )
            Text(
                text = "${next.cadenceMin}–${next.cadenceMax} rpm · " +
                    Formatters.duration(next.durationSec),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

// ==========================================================================
// The rest of the class
// ==========================================================================

/**
 * The next few efforts after this one, as a list.
 *
 * The timeline says *shape*; this says *names and numbers*. Knowing that the
 * next four segments are 20s / 10s / 20s / 10s is what tells a rider whether
 * to take a drink now or hang on.
 */
@Composable
fun UpcomingIntervals(
    intervals: List<Interval>,
    fromIndex: Int,
    modifier: Modifier = Modifier,
    max: Int = 4
) {
    val upcoming = remember(intervals, fromIndex) {
        intervals.drop((fromIndex + 2).coerceAtLeast(0)).take(max)
    }
    if (upcoming.isEmpty()) return

    Column(modifier = modifier) {
        Text(
            text = "THEN",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        upcoming.forEach { interval ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 4.dp, height = 20.dp)
                        .clip(MaterialTheme.expressiveShapes.pill)
                        .background(interval.powerZone.color)
                )
                Spacer(Modifier.width(MaterialTheme.spacing.small))
                Text(
                    text = "Z${interval.powerZoneNumber}",
                    style = MaterialTheme.typography.labelMedium,
                    color = interval.powerZone.color,
                    fontWeight = FontWeight.Black
                )
                Spacer(Modifier.width(MaterialTheme.spacing.small))
                Text(
                    text = interval.powerZone.displayName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = Formatters.duration(interval.durationSec),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

// ==========================================================================
// Interval progress ring
// ==========================================================================

/**
 * Time left in the current interval, drawn as an arc around whatever it wraps.
 *
 * An arc rather than a bar because it can enclose the number it describes,
 * which halves the space the pair needs on a strip that is only ~150dp tall.
 */
@Composable
fun ProgressArc(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    strokeWidth: androidx.compose.ui.unit.Dp = 6.dp,
    trackAlpha: Float = 0.15f,
    content: @Composable () -> Unit = {}
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 800, easing = LinearEasing),
        label = "ArcProgress"
    )
    val trackColor = color.copy(alpha = trackAlpha)

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = strokeWidth.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)

            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = stroke,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                )
            )
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * animated,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = stroke,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                )
            )
        }
        content()
    }
}
