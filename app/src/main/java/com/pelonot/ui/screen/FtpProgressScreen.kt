package com.pelonot.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pelonot.data.local.entity.FtpChangeSource
import com.pelonot.domain.chart.ChartScale
import com.pelonot.domain.progress.FtpChange
import com.pelonot.domain.progress.FtpTrend
import com.pelonot.ui.components.ChartFrame
import com.pelonot.ui.theme.expressiveShapes
import com.pelonot.ui.theme.readableColumn
import com.pelonot.ui.theme.spacing
import java.text.DateFormat
import java.util.Date

/**
 * A rider's FTP over time, full size (PLAN 16.3.1 / 7.10.1).
 *
 * The dashboard's card (7.10.2) answers "where is it now and which way is it
 * going" in a sparkline the width of a thumbnail. This answers the other
 * question — *how did it get here* — which needs the dates, every value it has
 * held, what caused each move, and a way through to the ride that earned it.
 *
 * Three rules carry over from the card, and they are the reason this screen is
 * worth building rather than guessing at:
 *
 * **Stepped, never interpolated.** FTP was one number until the day it became
 * another. A diagonal between 200 and 215 claims a Tuesday at 207 that nothing
 * measured.
 *
 * **Evidence and claims look different.** An FTP the app measured off a ride is
 * evidence; one the rider typed is a claim about themselves. Both are legitimate
 * and they are not the same thing, so a measured change gets a filled mark and
 * everything else a hollow one — the same distinction `FtpChangeSource`'s own
 * documentation opens with, drawn rather than described.
 *
 * **The first value is not a change.** It is where the number began. It appears
 * as the start of the line and in a sentence under it, never in the list of
 * changes with a delta beside it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FtpProgressScreen(
    trend: FtpTrend,
    onBack: () -> Unit,
    onOpenRide: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Your FTP") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(padding)
                .readableColumn()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MaterialTheme.spacing.large)
        ) {
            val current = trend.current
            if (current == null) {
                // A guest, or a profile whose history has not been written yet.
                // Not an error and not an empty chart: there is nothing to say.
                Text(
                    text = "No FTP has been recorded on this profile yet.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                return@Column
            }

            CurrentValue(current, trend)

            Spacer(Modifier.size(MaterialTheme.spacing.large))

            FtpTrendChart(trend)

            Spacer(Modifier.size(MaterialTheme.spacing.large))

            ChangeList(trend, onOpenRide)

            Spacer(Modifier.size(MaterialTheme.spacing.extraLarge))
        }
    }
}

@Composable
private fun CurrentValue(current: Int, trend: FtpTrend) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = "$current",
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.size(MaterialTheme.spacing.small))
        Text(
            text = "W",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )
    }

    val started = trend.startedAt ?: return
    Text(
        text = if (trend.hasMoved) {
            "${trend.changes.size} ${if (trend.changes.size == 1) "change" else "changes"} " +
                "since ${mediumDate(started.atEpochMs)}, when it was ${started.watts} W"
        } else {
            // The honest sentence for the rider who has just made a profile.
            // "No change" is a fact about the record, not a judgement about them.
            "Recorded ${mediumDate(started.atEpochMs)}. It has not moved since."
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/**
 * The line, full size.
 *
 * Reuses [ChartFrame] for the value axis so this looks like the ride charts
 * rather than like a second opinion about how a chart is drawn (16.1.8), with
 * `durationSec = 0` because the horizontal axis here is **dates**, not elapsed
 * seconds of a ride — the clock row would be a lie about what the axis is.
 */
@Composable
private fun FtpTrendChart(trend: FtpTrend) {
    val range = trend.range ?: return
    val points = trend.points
    val accent = MaterialTheme.colorScheme.primary
    val markFill = MaterialTheme.colorScheme.surface

    // The right-hand edge is *now*, not the last change — the current value is
    // true today, and the flat run out to the edge is how long it has been held.
    // Read once per composition rather than per frame; a chart that spans months
    // does not need the clock to tick.
    val now = remember(points.lastOrNull()?.atEpochMs) { System.currentTimeMillis() }
    val span = trend.spanToNow(now)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.expressiveShapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(Modifier.padding(MaterialTheme.spacing.large)) {
            Text(
                text = "Over time",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() }
            )
            Spacer(Modifier.size(MaterialTheme.spacing.medium))

            // "From 230 to 230 watts across 1 recorded value" is arithmetic
            // about a rider who has one number, and it reads as a bug. A flat
            // line deserves a flat sentence.
            val summary = if (trend.hasMoved) {
                "FTP from ${points.first().watts} to ${points.last().watts} watts " +
                    "across ${points.size} recorded values"
            } else {
                "FTP held at ${points.last().watts} watts"
            }

            // One scale for the gridlines and for the line itself, which is
            // 16.1.8's rule: a label placed by different arithmetic to the trace
            // can drift away from the value it claims.
            val scale = ChartScale(range.first.toDouble(), range.last.toDouble())

            Box(Modifier.semantics { contentDescription = summary }) {
                ChartFrame(
                    scale = scale,
                    unit = " W",
                    durationSec = 0,
                    height = CHART_HEIGHT
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        // A mark's own width of breathing room at each end. A
                        // change made this morning sits hard against the
                        // right-hand edge, and half a circle clipped by the plot
                        // reads as data that continues off the chart.
                        inset(horizontal = MARK_RADIUS.toPx(), vertical = 0f) {
                            val first = points.first().atEpochMs
                            val y = { watts: Int ->
                                size.height * (1f - scale.fractionOf(watts.toDouble()))
                            }
                            // A rider whose FTP has never moved has no span, and
                            // their one value is drawn as what it is: a flat line
                            // across the whole width, true for the whole time.
                            val x = { at: Long ->
                                if (span == null) 0f
                                else size.width * ((at - first).toFloat() / span.toFloat())
                            }

                            val path = Path()
                            path.moveTo(0f, y(points.first().watts))
                            points.drop(1).forEachIndexed { index, point ->
                                // Along at the old value to the day it changed,
                                // then straight up or down. Never a diagonal.
                                path.lineTo(x(point.atEpochMs), y(points[index].watts))
                                path.lineTo(x(point.atEpochMs), y(point.watts))
                            }
                            path.lineTo(size.width, y(points.last().watts))
                            drawPath(
                                path = path,
                                color = accent,
                                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                            )

                            // A mark per change, filled where the app measured it
                            // and hollow where the rider stated it. The first
                            // point gets no mark at all: it is where the line
                            // starts, not somewhere it moved to.
                            points.drop(1).forEach { point ->
                                val centre = Offset(x(point.atEpochMs), y(point.watts))
                                val radius = MARK_RADIUS.toPx()
                                if (FtpChangeSource.fromName(point.source).isMeasured) {
                                    drawCircle(accent, radius = radius, center = centre)
                                } else {
                                    drawCircle(markFill, radius = radius, center = centre)
                                    drawCircle(
                                        color = accent,
                                        radius = radius,
                                        center = centre,
                                        style = Stroke(width = 2.dp.toPx())
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.size(MaterialTheme.spacing.extraSmall))
            Row(Modifier.fillMaxWidth()) {
                AxisDate(points.first().atEpochMs)
                Spacer(Modifier.weight(1f))
                // Today, because that is where the axis ends. Labelling it with
                // the last change would put a date under a point that is not
                // there.
                if (span != null) AxisDate(now)
            }

            Spacer(Modifier.size(MaterialTheme.spacing.small))
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clearAndSetSemantics { }
            )

            if (trend.hasMoved) {
                Spacer(Modifier.size(MaterialTheme.spacing.extraSmall))
                Text(
                    text = "A filled mark is a change the app measured from a ride. " +
                        "A hollow one is a value you set yourself.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AxisDate(atEpochMs: Long) {
    Text(
        text = mediumDate(atEpochMs),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun ChangeList(trend: FtpTrend, onOpenRide: (String) -> Unit) {
    Text(
        text = "Every change",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.semantics { heading() }
    )
    Spacer(Modifier.size(MaterialTheme.spacing.medium))

    if (trend.changes.isEmpty()) {
        Text(
            text = "Nothing to list yet. Every future change — yours or the app's — " +
                "will be recorded here with the ride it came from.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
        trend.changes.forEach { change ->
            ChangeRow(change, onOpenRide)
        }
    }
}

@Composable
private fun ChangeRow(change: FtpChange, onOpenRide: (String) -> Unit) {
    val accent = if (change.isRise) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.tertiary
    }
    val rideId = change.workoutId
    val source = FtpChangeSource.fromName(change.source)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            // Only the rows that lead somewhere are tappable. A manual edit has
            // no ride behind it, and a card that depresses and does nothing is
            // worse than one that never invited the tap.
            .let { base ->
                if (rideId == null) base
                else base.clickable(
                    onClickLabel = "Open the ride this came from"
                ) { onOpenRide(rideId) }
            },
        shape = MaterialTheme.expressiveShapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MaterialTheme.spacing.large),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(accent.copy(alpha = 0.12f), MaterialTheme.expressiveShapes.large),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${if (change.isRise) "+" else ""}${change.deltaWatts}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = accent
                )
            }

            Spacer(Modifier.size(MaterialTheme.spacing.medium))

            Column(Modifier.weight(1f)) {
                Text(
                    text = "${change.from} → ${change.to} W",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = listOfNotNull(mediumDate(change.atEpochMs), source.describe())
                        .joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (rideId != null) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.DirectionsBike,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Whether the app measured this value or the rider stated it.
 *
 * `AutoBreakthrough` and `GuidedTest` are both the app reading watts off a ride
 * — and since 7.10.7 only ever a ride whose power was measured all the way
 * through, so a simulated ride cannot produce one. Everything else is a claim,
 * including `PulledFromCloud`: another device's arithmetic is not this bike's
 * measurement, and `Unknown` is by definition not evidence of anything.
 */
private val FtpChangeSource.isMeasured: Boolean
    get() = this == FtpChangeSource.AutoBreakthrough || this == FtpChangeSource.GuidedTest

/** The same words the dashboard card and Settings use, so one event reads the same everywhere. */
private fun FtpChangeSource.describe(): String? = when (this) {
    FtpChangeSource.ManualEdit -> "you set it"
    FtpChangeSource.AutoBreakthrough -> "measured from a ride"
    FtpChangeSource.GuidedTest -> "an FTP test"
    FtpChangeSource.PulledFromCloud -> "another device"
    FtpChangeSource.ProfileCreated -> "when you made this profile"
    FtpChangeSource.Unknown -> null
}

@Composable
private fun mediumDate(atEpochMs: Long): String = remember(atEpochMs) {
    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(atEpochMs))
}

private val CHART_HEIGHT = 220.dp
private val MARK_RADIUS = 5.dp
