package com.pelonot.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pelonot.core.Formatters
import com.pelonot.domain.chart.CadenceDistribution
import com.pelonot.domain.chart.ChartScale
import com.pelonot.domain.chart.PrescribedPlan
import com.pelonot.domain.chart.RideTrace
import com.pelonot.domain.chart.TimeInZone
import com.pelonot.domain.model.PowerZone
import com.pelonot.ui.theme.MetricCadenceCyan
import com.pelonot.ui.theme.MetricHeartRateGreen
import com.pelonot.ui.theme.MetricPowerCoral
import com.pelonot.ui.theme.color
import com.pelonot.ui.theme.expressiveShapes
import com.pelonot.ui.theme.spacing
import kotlin.math.roundToInt

/**
 * Post-ride charts, drawn with Compose `Canvas` and no charting dependency
 * (16.2.1).
 *
 * Four fixed chart types is a small need, and a library is a large surface for
 * it — particularly one that would have to be taught that a null heart rate is
 * *unknown* rather than zero.
 *
 * Every chart here carries a **text summary** rather than a
 * `contentDescription` of "chart" (16.2.4): a canvas is unreadable to a screen
 * reader, and a good deal of this data is a sentence anyway.
 */
@Composable
fun ChartCard(
    title: String,
    summary: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.expressiveShapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(Modifier.padding(MaterialTheme.spacing.large)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() }
            )
            if (caption != null) {
                Text(
                    text = caption,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.size(MaterialTheme.spacing.medium))

            // The drawing itself is inert to assistive technology; the sentence
            // below carries the meaning.
            Box(Modifier.semantics { contentDescription = summary }) { content() }

            Spacer(Modifier.size(MaterialTheme.spacing.small))
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                // Already announced by the Box above; saying it twice is worse
                // than saying it once.
                modifier = Modifier.clearAndSetSemantics { }
            )
        }
    }
}

/**
 * The axis treatment, decided once and shared by every trace (16.1.8).
 *
 * Before this the charts had no scale at all — the heart-rate one was a green
 * line with no numbers on it, and a rider could not answer "what was I at
 * during the second climb" (16.1.7).
 *
 * **Beautiful, not scientific.** No boxed axes, no tick forests, no frame. Two
 * or three hairlines at round values with the number sitting on the line at the
 * right-hand edge, and the clock underneath. Right-hand edge because a ride
 * starts at zero and climbs: the left of the plot is where the trace is.
 *
 * The labels are placed from [ChartScale.fractionOf], the same function the
 * drawing uses for the trace itself, so a gridline cannot drift away from the
 * value it claims.
 */
@Composable
fun ChartFrame(
    scale: ChartScale,
    unit: String,
    durationSec: Int,
    height: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    plot: @Composable () -> Unit
) {
    val ticks = scale.ticks()
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)

    Column(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(height)) {
            Canvas(Modifier.fillMaxSize()) {
                ticks.forEach { value ->
                    val y = size.height * (1f - scale.fractionOf(value))
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1.dp.toPx()
                    )
                }
            }

            plot()

            ticks.forEach { value ->
                // Half a line of type above the rule it belongs to, so the
                // number sits *on* the line rather than hanging under it.
                val fromTop = height * (1f - scale.fractionOf(value)) - LABEL_LIFT
                Text(
                    text = "${value.roundToInt()}$unit",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = fromTop.coerceAtLeast(0.dp), end = 4.dp)
                        // A scrim the colour of the card behind it. A ride that
                        // finishes on a sprint runs its trace straight through
                        // this label, and a number that cannot be read is not
                        // an axis.
                        .background(
                            MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.75f),
                            MaterialTheme.expressiveShapes.small
                        )
                        .padding(horizontal = 3.dp)
                )
            }
        }

        if (durationSec > 0) {
            Spacer(Modifier.size(MaterialTheme.spacing.extraSmall))
            Row(Modifier.fillMaxWidth()) {
                AxisLabel(Formatters.duration(0))
                Spacer(Modifier.weight(1f))
                AxisLabel(Formatters.duration(durationSec / 2))
                Spacer(Modifier.weight(1f))
                AxisLabel(Formatters.duration(durationSec))
            }
        }
    }
}

@Composable
private fun AxisLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** Half a line of `labelSmall`, near enough, and it does not need to be exact. */
private val LABEL_LIFT = 7.dp

/**
 * Power over time with the rider's own zone bands behind it (16.1.1) and the
 * class's prescribed intervals drawn under the trace (16.1.5).
 *
 * The band is the point. A line alone says "you did 240 W"; the same line over
 * a Threshold band says "that was threshold work for you", which is the thing
 * a rider is actually asking. The prescription goes one further and says what
 * was *asked for* at that moment, so the gap between the two is visible without
 * arithmetic.
 *
 * Drawn as a **min/max envelope with the mean through it**, not a single line:
 * each bucket is several seconds, and the envelope is what keeps a one-second
 * sprint visible after downsampling.
 *
 * [ghost] is a housemate's ride of the same class, drawn behind (24.3.1). It is
 * a bare mean line and nothing else — no envelope, no bands, no prescription —
 * because the chart already carries one rider's zones and a second full record
 * on the same axes is a graph rather than a comparison. **It is aligned by
 * absolute elapsed seconds against [trace]'s duration**, not stretched to fit:
 * the comparison a rider wants is "at twelve minutes they were at 250 W and I
 * was at 210", and rescaling a ride that ran forty seconds longer would move
 * every one of their efforts off the block it was ridden in.
 */
@Composable
fun PowerTraceChart(
    trace: RideTrace,
    ftpWatts: Int,
    modifier: Modifier = Modifier,
    prescribed: PrescribedPlan = PrescribedPlan(),
    ghost: RideTrace? = null,
    ghostColor: Color = GhostTraceColor,
    height: androidx.compose.ui.unit.Dp = CHART_HEIGHT
) {
    if (trace.isEmpty) {
        EmptyChart("No power was recorded.", modifier, height)
        return
    }

    // Room for the *floor* of every prescribed band, not its ceiling: a Z7
    // sprint is prescribed up to twice FTP, and scaling a whole ride to fit the
    // top of that block flattens the trace into a line along the axis. Blocks
    // taller than the chart are clipped instead.
    // The ghost is inside the ceiling too, or a stronger housemate's trace is
    // drawn along the top of the box and the comparison reads as a tie.
    val ceiling = maxOf(
        trace.maxValue,
        ftpWatts * 1.2,
        prescribed.highestTargetFloor * 1.15,
        ghost?.maxValue ?: 0.0
    ).coerceAtLeast(1.0)
    val zoneBands = if (ftpWatts > 0) {
        PowerZone.entries.map { zone ->
            val range = zone.powerRange(ftpWatts.toDouble())
            Triple(zone.color, range.start / ceiling, range.endInclusive / ceiling)
        }
    } else {
        emptyList()
    }
    val envelope = MetricPowerCoral.copy(alpha = 0.35f)

    ChartFrame(
        scale = ChartScale(0.0, ceiling),
        unit = " W",
        durationSec = trace.durationSec,
        height = height,
        modifier = modifier
    ) {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .clip(MaterialTheme.expressiveShapes.small)
    ) {
        zoneBands.forEach { (color, low, high) ->
            val top = size.height * (1f - high.toFloat()).coerceIn(0f, 1f)
            val bottom = size.height * (1f - low.toFloat()).coerceIn(0f, 1f)
            if (bottom > top) {
                drawRect(
                    // Faint: this is the backdrop the trace is read against,
                    // not a thing to look at in its own right.
                    color = color.copy(alpha = 0.13f),
                    topLeft = Offset(0f, top),
                    size = Size(size.width, bottom - top)
                )
            }
        }

        val x = { sec: Int -> size.width * (sec.toFloat() / trace.durationSec.coerceAtLeast(1)) }
        val y = { watts: Double -> size.height * (1f - (watts / ceiling).toFloat()) }

        // What the class asked for, under what the rider did. Drawn before the
        // trace so it never hides it: the record goes on top of the
        // prescription, not the other way round.
        prescribed.segments.forEach { segment ->
            val left = x(segment.startSec)
            val right = x(segment.endSec)
            val top = y(segment.targetHighWatts).coerceAtLeast(0f)
            val bottom = y(segment.targetLowWatts).coerceAtMost(size.height)
            if (right > left && bottom > top) {
                val block = Size(right - left, bottom - top)
                drawRect(
                    // Light enough that a Z1 block — which runs from zero and
                    // so fills the bottom third of the chart — is a backdrop
                    // rather than a slab with a ride drawn on it.
                    color = segment.zone.color.copy(alpha = 0.18f),
                    topLeft = Offset(left, top),
                    size = block
                )
                // Outlined as well as filled: two adjacent intervals in the same
                // zone are one continuous block without it, which is a different
                // class from the one that was ridden.
                drawRect(
                    color = segment.zone.color.copy(alpha = 0.55f),
                    topLeft = Offset(left, top),
                    size = block,
                    style = Stroke(width = 1.dp.toPx())
                )
            }
        }

        // The housemate, under the rider's own trace and over the prescription
        // (24.3.1). Buckets past the right-hand edge are dropped rather than
        // squeezed in: their ride ran longer than this one, and the honest
        // picture is that the chart runs out, not that their last effort
        // happened earlier than it did.
        if (ghost != null && !ghost.isEmpty) {
            val ghostPath = Path()
            var started = false
            ghost.buckets.forEach { bucket ->
                if (bucket.startSec > trace.durationSec) return@forEach
                val px = x(bucket.startSec)
                val py = y(bucket.mean)
                if (!started) {
                    ghostPath.moveTo(px, py); started = true
                } else {
                    ghostPath.lineTo(px, py)
                }
            }
            if (started) {
                drawPath(
                    ghostPath,
                    ghostColor,
                    style = Stroke(
                        width = 2.dp.toPx(),
                        // Dashed, so it is legible as *somebody else's* ride
                        // even where the two lines run together — which on the
                        // same class they will, for minutes at a time.
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(8.dp.toPx(), 5.dp.toPx())
                        )
                    )
                )
            }
        }

        // The envelope, as one closed path along the maxima and back along the
        // minima.
        val area = Path().apply {
            trace.buckets.forEachIndexed { i, bucket ->
                val px = x(bucket.startSec)
                if (i == 0) moveTo(px, y(bucket.max)) else lineTo(px, y(bucket.max))
            }
            trace.buckets.asReversed().forEach { bucket ->
                lineTo(x(bucket.startSec), y(bucket.min))
            }
            close()
        }
        drawPath(area, envelope)

        val mean = Path().apply {
            trace.buckets.forEachIndexed { i, bucket ->
                val px = x(bucket.startSec)
                if (i == 0) moveTo(px, y(bucket.mean)) else lineTo(px, y(bucket.mean))
            }
        }
        drawPath(mean, MetricPowerCoral, style = Stroke(width = 2.dp.toPx()))

        if (ftpWatts > 0) {
            drawFtpLine(y(ftpWatts.toDouble()))
        }
    }
    }
}

/** A dashed rule at FTP, because it is the number every zone is derived from. */
private fun DrawScope.drawFtpLine(atY: Float) {
    val dash = 6.dp.toPx()
    var x = 0f
    while (x < size.width) {
        drawLine(
            color = Color.White.copy(alpha = 0.35f),
            start = Offset(x, atY),
            end = Offset((x + dash).coerceAtMost(size.width), atY),
            strokeWidth = 1.dp.toPx()
        )
        x += dash * 2
    }
}

/**
 * Heart rate over time, **drawn only where samples exist** (16.1.2).
 *
 * Null means the strap was not reporting. A line that dips to the axis across
 * that gap says the rider's heart stopped, so each contiguous run of samples is
 * its own path and the gaps are simply not drawn.
 */
@Composable
fun HeartRateTraceChart(
    trace: RideTrace,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = CHART_HEIGHT
) {
    if (trace.isEmpty) {
        EmptyChart("No strap was paired for this ride.", modifier, height)
        return
    }

    // Padded a little either side so a steady heart rate is not a line stuck to
    // the top of the box.
    val low = (trace.minValue - 10).coerceAtLeast(0.0)
    val high = (trace.maxValue + 10).coerceAtLeast(low + 1)

    ChartFrame(
        scale = ChartScale(low, high),
        unit = "",
        durationSec = trace.durationSec,
        height = height,
        modifier = modifier
    ) {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .clip(MaterialTheme.expressiveShapes.small)
    ) {
        val x = { sec: Int -> size.width * (sec.toFloat() / trace.durationSec.coerceAtLeast(1)) }
        val y = { bpm: Double -> size.height * (1f - ((bpm - low) / (high - low)).toFloat()) }

        // A gap wider than a couple of buckets is a strap that dropped out, and
        // gets a break in the line rather than a straight segment across it.
        val gapThreshold = (trace.durationSec.toFloat() / trace.buckets.size) * 2.5f

        var path = Path()
        var started = false
        var lastEnd = 0

        trace.buckets.forEach { bucket ->
            val broke = started && (bucket.startSec - lastEnd) > gapThreshold
            if (broke) {
                drawPath(path, MetricHeartRateGreen, style = Stroke(width = 2.dp.toPx()))
                path = Path()
                started = false
            }
            val px = x(bucket.startSec)
            val py = y(bucket.mean)
            if (!started) path.moveTo(px, py) else path.lineTo(px, py)
            started = true
            lastEnd = bucket.endSec
        }
        if (started) {
            drawPath(path, MetricHeartRateGreen, style = Stroke(width = 2.dp.toPx()))
        }
    }
    }
}

/**
 * Cadence over time, with the cadence the class asked for drawn under it
 * (16.1.5a).
 *
 * The prescription was parsed and thrown away before this existed: an interval
 * names a cadence range as well as a zone, and the distribution chart below has
 * no time axis to lay a target on. So either this chart or nothing — and a
 * torque block ridden at 95 rpm is a different session from the one that was
 * written, which is precisely what a rider cannot see from a histogram.
 *
 * **The blocks are absolute rpm**, not scaled by the ride's intent multiplier
 * the way the power bands are: riding a class easier means fewer watts, not
 * slower legs.
 *
 * **Zeros are drawn.** A coast is measured, and it happened at a moment — which
 * is the difference between this and [HeartRateTraceChart], where a gap is
 * *unknown* and drawing it would say the rider's heart stopped.
 */
@Composable
fun CadenceTraceChart(
    trace: RideTrace,
    modifier: Modifier = Modifier,
    prescribed: PrescribedPlan = PrescribedPlan(),
    height: androidx.compose.ui.unit.Dp = CHART_HEIGHT
) {
    if (trace.isEmpty) {
        EmptyChart("No cadence was recorded.", modifier, height)
        return
    }

    // From zero, because a coast is a real reading and a chart starting at 40
    // rpm would draw one as the floor of the box rather than as a stop. The
    // ceiling fits the prescription whole — cadence targets are a narrow human
    // range, so unlike the power chart there is nothing to be gained by
    // clipping them.
    val ceiling = maxOf(
        trace.maxValue,
        prescribed.highestTargetCadence.toDouble()
    ).coerceAtLeast(1.0) * CADENCE_HEADROOM

    ChartFrame(
        scale = ChartScale(0.0, ceiling),
        unit = "",
        durationSec = trace.durationSec,
        height = height,
        modifier = modifier
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .clip(MaterialTheme.expressiveShapes.small)
        ) {
            val x = { sec: Int -> size.width * (sec.toFloat() / trace.durationSec.coerceAtLeast(1)) }
            val y = { rpm: Double -> size.height * (1f - (rpm / ceiling).toFloat()) }

            // Under the trace, like the power prescription: the record goes on
            // top of what was asked for.
            prescribed.segments.forEach { segment ->
                val left = x(segment.startSec)
                val right = x(segment.endSec)
                val top = y(segment.targetCadenceHigh.toDouble())
                val bottom = y(segment.targetCadenceLow.toDouble())
                if (right > left && bottom > top) {
                    val block = Size(right - left, bottom - top)
                    drawRect(
                        color = MetricCadenceCyan.copy(alpha = 0.16f),
                        topLeft = Offset(left, top),
                        size = block
                    )
                    // Outlined as well as filled, for the same reason the power
                    // blocks are: two neighbouring intervals asking for the same
                    // cadence are one long block without it, and that is a
                    // different class from the one that was ridden.
                    drawRect(
                        color = MetricCadenceCyan.copy(alpha = 0.5f),
                        topLeft = Offset(left, top),
                        size = block,
                        style = Stroke(width = 1.dp.toPx())
                    )
                }
            }

            val area = Path().apply {
                trace.buckets.forEachIndexed { i, bucket ->
                    val px = x(bucket.startSec)
                    if (i == 0) moveTo(px, y(bucket.max)) else lineTo(px, y(bucket.max))
                }
                trace.buckets.asReversed().forEach { bucket ->
                    lineTo(x(bucket.startSec), y(bucket.min))
                }
                close()
            }
            drawPath(area, MetricCadenceCyan.copy(alpha = 0.30f))

            val mean = Path().apply {
                trace.buckets.forEachIndexed { i, bucket ->
                    val px = x(bucket.startSec)
                    if (i == 0) moveTo(px, y(bucket.mean)) else lineTo(px, y(bucket.mean))
                }
            }
            drawPath(mean, MetricCadenceCyan, style = Stroke(width = 2.dp.toPx()))
        }
    }
}

/** Enough that a ride's fastest spin is not drawn along the top edge. */
private const val CADENCE_HEADROOM = 1.08

/** How the ride's cadence was spread (16.1.3). */
@Composable
fun CadenceDistributionChart(
    distribution: CadenceDistribution,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = CHART_HEIGHT
) {
    val bands = distribution.bands
    if (bands.isEmpty()) {
        EmptyChart("No cadence was recorded.", modifier, height)
        return
    }

    val peak = distribution.maxSeconds.coerceAtLeast(1)

    Column(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(height)) {
        // 16.1.8, the same treatment as the traces: this chart's vertical axis
        // is *time*, and without a number on it the tallest bar could be twenty
        // seconds or ten minutes. One label at the peak is enough — the bars
        // are read against each other, not against an absolute scale.
        Text(
            text = Formatters.duration(peak),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.TopEnd).padding(end = 4.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(height),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            bands.forEach { (_, _, seconds) ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        // A band with a single second still gets a sliver, so
                        // the shape of the distribution is not a row of gaps.
                        .fillMaxHeight(
                            if (seconds == 0) 0f else (seconds.toFloat() / peak).coerceAtLeast(0.02f)
                        )
                        .clip(MaterialTheme.expressiveShapes.small)
                        .background(MetricCadenceCyan.copy(alpha = 0.75f))
                )
            }
        }
        }
        Spacer(Modifier.size(MaterialTheme.spacing.extraSmall))
        Row(Modifier.fillMaxWidth()) {
            AxisLabel("${bands.first().first} rpm")
            Spacer(Modifier.weight(1f))
            AxisLabel("${(bands.first().first + bands.last().second) / 2} rpm")
            Spacer(Modifier.weight(1f))
            AxisLabel("${bands.last().second} rpm")
        }
    }
}

/**
 * Time in zone as one stacked bar (16.1.4).
 *
 * Shared in spirit with the HUD's collapsed strip (11.2.2): the same question,
 * "how was this ride actually spent", and the same answer in one line.
 */
@Composable
fun TimeInZoneBar(
    timeInZone: TimeInZone,
    modifier: Modifier = Modifier
) {
    val occupied = timeInZone.occupied
    if (occupied.isEmpty()) {
        EmptyChart("Time in zone needs an FTP, and this ride has none.", modifier, 24.dp)
        return
    }

    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .clip(MaterialTheme.expressiveShapes.pill)
        ) {
            occupied.forEach { (zone, seconds) ->
                Box(
                    modifier = Modifier
                        .weight(seconds.toFloat())
                        .fillMaxHeight()
                        .background(zone.color)
                )
            }
        }

        Spacer(Modifier.size(MaterialTheme.spacing.small))

        // A legend, because seven colours in a bar is a code nobody has been
        // given the key to.
        occupied.forEach { (zone, seconds) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(MaterialTheme.expressiveShapes.pill)
                        .background(zone.color)
                )
                Spacer(Modifier.width(MaterialTheme.spacing.small))
                Text(
                    text = "Z${zone.number} ${zone.displayName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${Formatters.duration(seconds)} · " +
                        "${(timeInZone.fractionOf(zone) * 100).roundToInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Said plainly rather than drawn as an empty box the rider has to interpret. */
@Composable
private fun EmptyChart(
    message: String,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * A housemate's trace (24.3.1).
 *
 * Deliberately neither a metric accent nor a zone colour — it is not a
 * *quantity* on this chart, it is a second rider. A cool grey-blue sits behind
 * the coral power line at every zone colour without ever being mistaken for
 * one of them.
 */
private val GhostTraceColor = Color(0xFF9FB4C7)

private val CHART_HEIGHT = 140.dp
