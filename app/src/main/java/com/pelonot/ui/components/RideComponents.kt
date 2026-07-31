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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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

/**
 * A one-shot flash that spikes when [trigger] changes and decays away.
 *
 * The full-width HUD strip cannot use [attentionBounce] — scaling it peels it
 * away from the screen edge it is docked to, which looks like a rendering bug
 * rather than an announcement. A wash of colour across it does the same job
 * without moving anything.
 */
@Composable
fun rememberFlash(trigger: Any?, enabled: Boolean = true, durationMs: Int = 700): Float {
    val flash = remember { Animatable(0f) }
    LaunchedEffect(trigger, enabled) {
        if (trigger == null || !enabled) return@LaunchedEffect
        flash.snapTo(1f)
        flash.animateTo(0f, animationSpec = tween(durationMs, easing = LinearEasing))
    }
    return flash.value
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
 * One glyph per quantity, shared by the ride screen and the strip (11.6.3).
 *
 * Defined in one place because the two surfaces are meant to be the same
 * display at two sizes — a rider who learns the lightning bolt on the strip
 * must not meet a different bolt in the app.
 */
object MetricIcons {
    /** Revolutions, which is what a cadence is. */
    val Cadence: ImageVector = Icons.Filled.Autorenew

    /** The knob. Resistance is the one metric the rider sets by hand. */
    val Resistance: ImageVector = Icons.Filled.Tune
    val Power: ImageVector = Icons.Filled.Bolt
    val HeartRate: ImageVector = Icons.Filled.Favorite

    /** Total work done, rather than the rate of it. */
    val Output: ImageVector = Icons.Filled.LocalFireDepartment
    val Distance: ImageVector = Icons.Filled.Straighten
}

/**
 * One live number with its target gauge beneath it.
 *
 * The value is the largest thing in the tile and the label the smallest,
 * because at a glance the rider already knows which tile is which — they know
 * where power lives — and only needs the digits.
 *
 * [showTargetRange] spells the band out in numbers under the gauge (11.6.4).
 * Off by default and opted into only by the ride screen: the strip has a
 * quarter of the width for the same tile, and shrinking one design until it
 * fits both surfaces is how the gauge ended up saying nothing on either.
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
    compact: Boolean = false,
    showTargetRange: Boolean = false,
    icon: ImageVector? = null
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

    val targetRange = band.label

    Column(
        modifier = modifier.clearAndSetSemantics {
            contentDescription = buildString {
                append("$label $value $unit")
                // The band is spoken whenever there is one, on both surfaces:
                // the reason for hiding it on the strip is width, and a screen
                // reader has none.
                if (targetRange != null) append(", target $targetRange $unit")
                append(status.spokenSuffix)
            }
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
                // "BPM" was stacking into a vertical B/P/M in a narrow tile.
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.padding(bottom = valueSize.value.dp * 0.12f)
            )
        }

        // No gauge without a target. An empty track under a free ride's numbers
        // is a gauge that measures nothing, which is worse than no gauge.
        if (!compact && band.isDefined) {
            Spacer(Modifier.height(6.dp))
            TargetGauge(band = band, value = rawValue, accent = accent)
            Spacer(Modifier.height(4.dp))

            // 11.6.4. The gauge shows a rider they are under the band without
            // ever telling them the band is 85–95. The unit is repeated here
            // rather than borrowed from the value above it: "85–95" beside a
            // resistance tile is ambiguous on its own, and this line is read
            // separately from the number it sits under.
            if (showTargetRange && targetRange != null) {
                Text(
                    text = "TARGET ${targetWithUnit(targetRange, unit)}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false
                )
                Spacer(Modifier.height(4.dp))
            }
        }

        // Colour alone is never the whole signal, so the direction is marked
        // beside the label — an arrow rather than a word, because "CADENCE
        // ▼ LOW" does not fit a quarter of the HUD strip and wrapping it makes
        // the whole strip taller. The screen reader still gets the sentence.
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 11.6.3. The label is the smallest text on the tile and the only
            // thing saying which number this is, read from a metre away
            // mid-effort — a glyph is recognised faster than a word. It sits
            // *beside* the word rather than replacing it, because nobody
            // recognises a bare icon for "resistance" unaided.
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    // The whole tile already carries one description; a labelled
                    // icon inside it would be announced twice.
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(LabelIconSize)
                )
                Spacer(Modifier.width(4.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false
            )
            if (status.isOffTarget) {
                Spacer(Modifier.width(5.dp))
                Text(
                    text = if (status == TargetStatus.Below) "▼" else "▲",
                    style = MaterialTheme.typography.labelSmall,
                    color = AlertAmber,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

/**
 * Sized against `labelSmall` beside it rather than by the tile: the same glyph
 * appears on a 104 sp ride tile and on a quarter of the strip, and it is
 * labelling the same small word in both.
 */
private val LabelIconSize = 14.dp

/** "85–95 rpm", but "42–52%" — a percent sign does not take a space. */
private fun targetWithUnit(range: String, unit: String): String =
    if (unit == "%") "$range$unit" else "$range $unit"

private val TargetStatus.spokenSuffix: String
    get() = when (this) {
        TargetStatus.Below -> ", below target"
        TargetStatus.Above -> ", above target"
        else -> ""
    }

// ==========================================================================
// The zone the rider is actually in
// ==========================================================================

/**
 * Which power zone the rider is in *right now* (11.6.2).
 *
 * Everything else on the ride screen shows the zone that was *prescribed* —
 * the big glyph in the interval card, the wash of colour behind the whole
 * screen. Being off it was rendered as an amber number and an arrow, which
 * says "wrong" without ever saying "you are in 3 and you were asked for 4".
 *
 * Deliberately not a second badge beside the first. The prescribed zone is a
 * glyph with a shape per zone; this is a strip of words, so the two cannot be
 * confused at a glance even though they carry the same colour scheme. When
 * they agree, the sentence collapses to "ON TARGET" rather than repeating the
 * number back — a rider on target does not need to be told twice.
 *
 * [zone] is null when there is no power to speak of: a dead board, or a rider
 * who has stopped pedalling. [PowerZone.forPower] answers Z1 for zero watts,
 * which is true and useless, and printing "ACTIVE RECOVERY" over a bike nobody
 * is on is the same class of lie as a frozen cadence.
 */
@Composable
fun CurrentZoneBar(
    zone: PowerZone?,
    modifier: Modifier = Modifier,
    prescribed: PowerZone? = null,
    compact: Boolean = false
) {
    val color by animateColorAsState(
        targetValue = zone?.color ?: MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "CurrentZoneColor"
    )

    val onTarget = zone != null && prescribed != null && zone == prescribed
    val spoken = when {
        zone == null -> "No power reading, so no current zone"
        prescribed == null -> "You are in zone ${zone.number}, ${zone.displayName}"
        onTarget -> "You are in zone ${zone.number}, on target"
        else -> "You are in zone ${zone.number}, ${zone.displayName}; " +
            "asked for zone ${prescribed.number}"
    }

    Row(
        modifier = modifier
            .clip(MaterialTheme.expressiveShapes.large)
            .background(color.copy(alpha = 0.14f))
            .padding(
                horizontal = if (compact) 8.dp else MaterialTheme.spacing.medium,
                vertical = if (compact) 4.dp else MaterialTheme.spacing.small
            )
            .clearAndSetSemantics { contentDescription = spoken },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "NOW",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.width(if (compact) 6.dp else MaterialTheme.spacing.small))
        Text(
            text = zone?.let { "Z${it.number}" } ?: "--",
            style = if (compact) {
                MaterialTheme.typography.labelLarge
            } else {
                MaterialTheme.typography.titleMedium
            },
            color = color,
            fontWeight = FontWeight.Black,
            maxLines = 1
        )

        if (!compact && zone != null) {
            Spacer(Modifier.width(6.dp))
            Text(
                text = zone.displayName.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
        }

        if (prescribed != null && zone != null) {
            Spacer(Modifier.weight(1f))
            Text(
                text = if (onTarget) "ON TARGET" else "ASKED FOR Z${prescribed.number}",
                style = MaterialTheme.typography.labelSmall,
                color = if (onTarget) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    AlertAmber
                },
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false
            )
        }
    }
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
            .fillMaxWidth()
            .clip(MaterialTheme.expressiveShapes.large)
            .background(zoneColor.copy(alpha = 0.24f * pulse))
            .attentionBounce(trigger = secondsRemaining, enabled = animate, magnitude = 0.10f)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // The digit leads. It is the only part of this that matters at 2 seconds.
        Text(
            text = "$secondsRemaining",
            fontSize = 44.sp,
            lineHeight = 46.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = (-3).sp,
            color = zoneColor,
            maxLines = 1
        )

        Spacer(Modifier.width(10.dp))

        ZoneGlyph(
            zone = nextZone,
            modifier = Modifier.size(30.dp)
        ) {
            Text(
                text = "${nextZone.number}",
                style = MaterialTheme.typography.labelMedium,
                color = Color.Black.copy(alpha = 0.78f),
                fontWeight = FontWeight.Black
            )
        }

        Spacer(Modifier.width(8.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = "NEXT UP",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = nextZone.displayName.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = zoneColor,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
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
