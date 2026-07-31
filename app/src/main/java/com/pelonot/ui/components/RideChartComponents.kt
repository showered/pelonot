package com.pelonot.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
 * Power over time with the rider's own zone bands behind it (16.1.1).
 *
 * The band is the point. A line alone says "you did 240 W"; the same line over
 * a Threshold band says "that was threshold work for you", which is the thing
 * a rider is actually asking.
 *
 * Drawn as a **min/max envelope with the mean through it**, not a single line:
 * each bucket is several seconds, and the envelope is what keeps a one-second
 * sprint visible after downsampling.
 */
@Composable
fun PowerTraceChart(
    trace: RideTrace,
    ftpWatts: Int,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = CHART_HEIGHT
) {
    if (trace.isEmpty) {
        EmptyChart("No power was recorded.", modifier, height)
        return
    }

    val ceiling = maxOf(trace.maxValue, ftpWatts * 1.2).coerceAtLeast(1.0)
    val zoneBands = if (ftpWatts > 0) {
        PowerZone.entries.map { zone ->
            val range = zone.powerRange(ftpWatts.toDouble())
            Triple(zone.color, range.start / ceiling, range.endInclusive / ceiling)
        }
    } else {
        emptyList()
    }
    val envelope = MetricPowerCoral.copy(alpha = 0.35f)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
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

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
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
        Spacer(Modifier.size(MaterialTheme.spacing.extraSmall))
        Row(Modifier.fillMaxWidth()) {
            Text(
                text = "${bands.first().first} rpm",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "${bands.last().second} rpm",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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

private val CHART_HEIGHT = 140.dp
