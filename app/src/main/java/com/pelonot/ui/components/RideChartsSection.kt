package com.pelonot.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pelonot.core.Formatters
import com.pelonot.domain.chart.RideChartSummaries
import com.pelonot.domain.chart.RideCharts
import com.pelonot.domain.chart.RideTrace
import com.pelonot.domain.model.PowerProvenance
import com.pelonot.ui.theme.MetricPowerCoral
import com.pelonot.ui.theme.loneCard
import com.pelonot.ui.theme.spacing

/**
 * A ride of the same class that can be drawn behind this one — a housemate's
 * (24.3.1), or **the rider's own previous best** (16.3.4).
 *
 * One type rather than two, because from the chart's point of view they are the
 * same thing: another ride of the same class, on the same axes, under the same
 * measured-power rule. [you] is the only difference, and it exists because
 * "Alex" and "your best" read very differently under a trace.
 */
data class RideRival(
    val workoutId: String,
    val name: String,
    val outputKj: Double,
    val you: Boolean = false
)

/** That ride, fetched and reduced, ready to draw. */
data class RideGhost(
    val workoutId: String,
    val name: String,
    val outputKj: Double,
    val trace: RideTrace,
    val you: Boolean = false
)

/**
 * Every chart a finished ride has, laid out for the width this app actually
 * has: two columns on the tablet — 1280 dp is a great deal to spend on one
 * 140 dp chart at a time — and a single column anywhere narrower.
 *
 * **Shared between ride detail and the post-ride summary (12.6.1)**, for the
 * same reason `RideFigures` is (12.2.2): they are the same ride, and the owner's
 * question — *"this should be pretty much the same as when you view it from
 * history, right?"* — is answered yes for everything except the three things
 * that are only true tonight. Before this the section was private to
 * `RideDetailScreen`, so a rider who had just stopped pedalling got the totals
 * and no picture of the ride at all, purely because 16.1 landed on the other
 * screen first.
 *
 * The ghost and the rivals are ride detail's alone and default to nothing. They
 * are a *comparison*, and the summary is the one screen where the rider has not
 * asked for one — they have asked how the last twenty minutes went.
 */
@Composable
fun RideChartsSection(
    charts: RideCharts?,
    isGuestRide: Boolean,
    modifier: Modifier = Modifier,
    rivals: List<RideRival> = emptyList(),
    ghost: RideGhost? = null,
    onPickRival: (String?) -> Unit = {}
) {
    if (charts == null) {
        // Distinguished from "this ride recorded nothing", which is a
        // different sentence and a permanent one.
        Row(modifier, verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.size(MaterialTheme.spacing.small))
            Text(
                text = "Working out how the ride went…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    if (!charts.hasAnything) {
        Text(
            text = "This ride has no second-by-second record — only its totals. " +
                "Rides recorded before the app started keeping one look like this.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier
        )
        return
    }

    BoxWithConstraints(modifier) {
        val twoUp = maxWidth >= TWO_COLUMN_BREAKPOINT

        Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)) {
            if (twoUp) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)
                ) {
                    PowerCard(charts, ghost, rivals, onPickRival, isGuestRide, Modifier.weight(1f))
                    HeartCard(charts, Modifier.weight(1f))
                }
                // The two cadence cards side by side on purpose: they are the
                // same metric answering two questions — when, and how long at
                // each — and the one with a time axis is the only one a
                // prescribed cadence can be drawn on (16.1.5a).
                Row(
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)
                ) {
                    CadenceTraceCard(charts, Modifier.weight(1f))
                    CadenceCard(charts, Modifier.weight(1f))
                }
                // 22.6: no partner beside it, so it stops at a column's width
                // rather than becoming a bar across the room.
                ZoneCard(charts, Modifier.loneCard())
            } else {
                PowerCard(charts, ghost, rivals, onPickRival, isGuestRide, Modifier.fillMaxWidth())
                HeartCard(charts, Modifier.fillMaxWidth())
                CadenceTraceCard(charts, Modifier.fillMaxWidth())
                CadenceCard(charts, Modifier.fillMaxWidth())
                ZoneCard(charts, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun PowerCard(
    charts: RideCharts,
    ghost: RideGhost?,
    rivals: List<RideRival>,
    onPickRival: (String?) -> Unit,
    isGuestRide: Boolean,
    modifier: Modifier
) = ChartCard(
    title = "Power",
    // 16.1.6, and it now reads from where the watts actually came from:
    // `workout_metrics.power_is_measured` records it per sample. A ride from
    // before that column existed says "estimated", which is the safe
    // direction — the rule this project cares about is never presenting a
    // modelled watt as a measured one, and "we did not write it down" is not
    // grounds for claiming a measurement.
    caption = listOfNotNull(
        when (charts.powerProvenance) {
            PowerProvenance.Measured -> "Measured by the bike"
            PowerProvenance.Mixed ->
                "Partly measured — the bike's sensor dropped out during this ride"
            else -> "Estimated from cadence and resistance — see Settings"
        },
        // Only said when there are blocks to explain. On a free ride there is
        // no prescription and no legend for one.
        "blocks are what the class asked for".takeUnless { charts.prescribed.isEmpty },
        // 7.8.4. Said only when it is true, and it is true only of rides
        // recorded before the app kept the number. Bands drawn from an FTP the
        // ride never saw are a re-derivation from a source that has moved
        // since, and they must not sit here looking like a record.
        "zones from your FTP today — this ride did not record its own"
            .takeUnless { charts.ftpIsTheRides },
        // 7.8.5, adjusted. A guest ride *does* have an FTP — the app's default,
        // which is what its live targets and its zone ladder were built from
        // during the ride, so the number on the row is a true record of what it
        // was judged against. What it does not have is a *rider*, and "Zone 5"
        // is a claim about somebody. Said, rather than the bands withdrawn:
        // withdrawing them would also take the prescription and the
        // time-in-zone with them, and would make the ride's own screen disagree
        // with what the guest was looking at while they rode it.
        "no rider on this ride — zones from the app's default FTP"
            .takeIf { isGuestRide },
        // 23.4.3. The one sentence that separates a record from a sketch of
        // one: the line has the same shape, the same peak and the same axis
        // either way, and nothing else on the screen could tell them apart.
        "condensed to a ${charts.detailSec}-second outline"
            .takeIf { charts.isTrimmed }
    ).joinToString(" · "),
    summary = listOf(
        RideChartSummaries.power(charts.power, charts.powerProvenance, charts.detailSec),
        RideChartSummaries.prescribed(charts.prescribed),
        // 16.2.4: the canvas is inert to a screen reader, so a second trace
        // that is not in this sentence does not exist for the rider using one.
        ghost?.let {
            if (it.you) {
                "Your previous best at this class is drawn behind it, dashed, " +
                    "at ${Formatters.kilojoules(it.outputKj)} total."
            } else {
                "${it.name}'s ride of this class is drawn behind it, dashed, " +
                    "at ${Formatters.kilojoules(it.outputKj)} total."
            }
        }.orEmpty()
    ).filter { it.isNotEmpty() }.joinToString(" "),
    modifier = modifier
) {
    Column {
        PowerTraceChart(
            trace = charts.power,
            ftpWatts = charts.ftpWatts,
            prescribed = charts.prescribed,
            ghost = ghost?.trace,
            // The rider's own earlier ride is *them*, so it is drawn in the
            // power colour rather than in the grey that means "a second rider"
            // — dimmed, because it is still the thing behind rather than the
            // record on top (16.3.4).
            ghostColor = if (ghost?.you == true) {
                MetricPowerCoral.copy(alpha = 0.55f)
            } else {
                GhostTraceColor
            }
        )
        RivalPicker(rivals, ghost, onPickRival)
    }
}

/**
 * "Ride against" — the housemates who have ridden this class (24.3.1).
 *
 * **Draws nothing at all when there is nobody**, which is the common case and
 * the same rule 24.1.6 settled for the leaderboard card: a household of one
 * must never see an empty comparison, because an empty comparison is a message
 * about the people who are not on it.
 *
 * Opt-in per tap rather than drawn by default. The rider opened this screen to
 * look at their own ride, and a second line arriving unasked over the top of it
 * is somebody else's ride being made the point of the chart.
 */
@Composable
private fun RivalPicker(
    rivals: List<RideRival>,
    ghost: RideGhost?,
    onPick: (String?) -> Unit
) {
    if (rivals.isEmpty()) return

    Spacer(Modifier.size(MaterialTheme.spacing.small))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)
    ) {
        Text(
            text = "Ride against",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        rivals.forEach { rival ->
            val on = ghost?.workoutId == rival.workoutId
            FilterChip(
                selected = on,
                onClick = { onPick(rival.workoutId) },
                label = {
                    Text("${rival.name} · ${Formatters.kilojoules(rival.outputKj)}")
                },
                modifier = Modifier.semantics {
                    contentDescription = when {
                        rival.you && on -> "Hide your previous best"
                        rival.you -> "Draw your previous best at this class behind this ride"
                        on -> "Hide ${rival.name}'s ride"
                        else -> "Draw ${rival.name}'s ride behind yours"
                    }
                }
            )
        }
    }
}

@Composable
private fun HeartCard(charts: RideCharts, modifier: Modifier) = ChartCard(
    title = "Heart rate",
    // 21.4.2a. Said only when it is true, and it is true only of rides recorded
    // before the app kept the number — exactly the sentence the power card
    // carries about the FTP, for exactly the same reason. Bands drawn from a
    // maximum the ride never saw are a re-derivation from a source that has
    // moved since, and they must not sit here looking like a record.
    caption = listOfNotNull(
        "zones from %HRmax".takeIf { charts.maxHrBpm != null },
        "your maximum today — this ride did not record its own"
            .takeIf { charts.maxHrBpm != null && !charts.maxHrIsTheRides }
    ).joinToString(" · ").takeIf { it.isNotEmpty() },
    summary = RideChartSummaries.heartRate(charts.heartRate),
    modifier = modifier
) {
    HeartRateTraceChart(trace = charts.heartRate, maxHrBpm = charts.maxHrBpm)
}

@Composable
private fun CadenceTraceCard(charts: RideCharts, modifier: Modifier) = ChartCard(
    title = "Cadence over time",
    caption = "What the class asked for, behind what you turned"
        .takeUnless { charts.prescribed.isEmpty },
    summary = RideChartSummaries.cadenceOverTime(charts.cadenceTrace, charts.prescribed),
    modifier = modifier
) {
    CadenceTraceChart(trace = charts.cadenceTrace, prescribed = charts.prescribed)
}

@Composable
private fun CadenceCard(charts: RideCharts, modifier: Modifier) = ChartCard(
    title = "Cadence spread",
    caption = if (charts.isTrimmed) {
        // Counted while the seconds were still there (23.4.2), which is why it
        // still adds up to the length of the ride.
        "How long was spent at each cadence, counted before this ride was condensed"
    } else {
        "How long was spent at each cadence"
    },
    summary = RideChartSummaries.cadence(charts.cadence),
    modifier = modifier
) {
    CadenceDistributionChart(distribution = charts.cadence)
}

@Composable
private fun ZoneCard(charts: RideCharts, modifier: Modifier) = ChartCard(
    title = "Time in zone",
    caption = listOfNotNull(
        "counted before this ride was condensed".takeIf { charts.isTrimmed },
        // Only ever true of a ride that never recorded its own FTP (7.8) and
        // has since been condensed: the counts were frozen against the number
        // the rider had that day, and they are not today's zones.
        charts.zoneFtpWatts
            ?.takeIf { it != charts.ftpWatts }
            ?.let { "at ${'$'}it W, the FTP at the time" }
    ).joinToString(" · ").takeIf { it.isNotEmpty() },
    summary = RideChartSummaries.timeInZone(charts.timeInZone),
    modifier = modifier
) {
    TimeInZoneBar(timeInZone = charts.timeInZone)
}

/**
 * Below this the charts stack; above it they go two across.
 *
 * 900 dp rather than the panel's own width: two 140 dp charts need room for
 * their axes before they are easier to compare than one above the other.
 */
private val TWO_COLUMN_BREAKPOINT = 900.dp
