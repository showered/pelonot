package com.pelonot.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pelonot.core.Formatters
import com.pelonot.data.local.entity.WorkoutEntity
import com.pelonot.ui.theme.MetricCadenceCyan
import com.pelonot.ui.theme.MetricHeartRateGreen
import com.pelonot.ui.theme.MetricPowerCoral
import com.pelonot.ui.theme.WideGrid
import com.pelonot.ui.theme.expressiveShapes
import com.pelonot.ui.theme.spacing
import com.pelonot.ui.theme.units

/**
 * A completed ride's figures, tiled across whatever width the screen has.
 *
 * Shared by the post-ride summary and the ride detail screen rather than
 * copied into both (12.2.2): they show the same six numbers, and two copies
 * would have drifted the first time one gained a unit or a label.
 *
 * **It used to be six label-value rows in one card** — `Average power  188 W`
 * — which is a spec sheet, and on the bike's 1280 dp panel it was a spec sheet
 * with 500 dp of black either side of it (22.4.6). Two rules apply here and
 * they point the same way:
 *
 * - **Tile what is looked at** (22.4). Nobody *reads* a ride's figures; they
 *   scan for the one they came for, and a scan wants a grid.
 * - **The value is the largest thing in the tile and the label the smallest**
 *   (26.1.2, and the same argument `MetricReadout` already makes for the ride
 *   screen). A label that only names the number beneath it has not earned
 *   equal weight with it.
 *
 * The accents are the metric colours the ride screen and the charts already
 * use, so a rider who has learnt that coral means power does not meet a
 * different coral here.
 *
 * **A figure with nothing behind it is left out rather than drawn as `--`.**
 * Heart rate is the case: a rider with no strap has no heart-rate data, and a
 * dash is a hole in the layout that says less than the missing tile does. An
 * average that exists and is zero stays — a zero is a measurement.
 */
@Composable
fun RideFigures(
    workout: WorkoutEntity,
    modifier: Modifier = Modifier
) {
    val units = MaterialTheme.units
    val figures = buildList {
        add(
            RideFigure(
                label = "Time",
                value = Formatters.duration(workout.durationSec),
                unit = "",
                icon = Icons.Filled.Schedule,
                accent = MaterialTheme.colorScheme.primary
            )
        )
        add(
            RideFigure(
                label = "Output",
                value = Formatters.kilojoulesValue(workout.totalOutputKj),
                unit = "kJ",
                icon = MetricIcons.Output,
                accent = MaterialTheme.colorScheme.tertiary
            )
        )
        add(
            RideFigure(
                label = "Avg power",
                value = Formatters.wattsValue(workout.avgPower ?: 0.0),
                unit = "watts",
                icon = MetricIcons.Power,
                accent = MetricPowerCoral
            )
        )
        add(
            RideFigure(
                label = "Avg cadence",
                value = Formatters.rpmValue(workout.avgCadence ?: 0.0),
                unit = "rpm",
                icon = MetricIcons.Cadence,
                accent = MetricCadenceCyan
            )
        )
        workout.avgHr?.let { hr ->
            add(
                RideFigure(
                    label = "Avg heart rate",
                    value = Formatters.bpmValue(hr.toInt()),
                    unit = "bpm",
                    icon = MetricIcons.HeartRate,
                    accent = MetricHeartRateGreen
                )
            )
        }
        add(
            RideFigure(
                label = "Distance",
                value = Formatters.distanceValue(workout.totalDistanceKm, units),
                unit = units.distanceLabel,
                icon = MetricIcons.Distance,
                accent = MaterialTheme.colorScheme.secondary
            )
        )
    }

    WideGrid(
        items = figures,
        modifier = modifier,
        minCellWidth = FIGURE_MIN_WIDTH,
        spacing = MaterialTheme.spacing.medium
    ) { figure ->
        RideFigureTile(figure)
    }
}

/** One number on a finished ride, with what it is and what it is measured in. */
data class RideFigure(
    val label: String,
    val value: String,
    val unit: String,
    val icon: ImageVector,
    val accent: Color
)

@Composable
private fun RideFigureTile(figure: RideFigure) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            // One phrase to a screen reader, not four fragments.
            .clearAndSetSemantics {
                contentDescription = "${figure.label}: ${figure.value} ${figure.unit}".trim()
            },
        shape = MaterialTheme.expressiveShapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(Modifier.padding(MaterialTheme.spacing.large)) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(figure.accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = figure.icon,
                    contentDescription = null,
                    tint = figure.accent,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(Modifier.size(MaterialTheme.spacing.medium))

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = figure.value,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                if (figure.unit.isNotEmpty()) {
                    Spacer(Modifier.width(MaterialTheme.spacing.extraSmall))
                    Text(
                        text = figure.unit,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
            }

            Text(
                text = figure.label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

/**
 * All six across the bike's 1280 dp panel in one row, three inside a capped
 * column, one on a phone.
 *
 * 180 rather than 200 for a measured reason: at 200 the sixth figure fell to a
 * second row on its own, and `WideGrid` then balanced it into two rows of
 * three 400 dp tiles with a two-digit number in each. One row of six is the
 * shape this set wants, and 180 is what it costs — still wide enough for
 * `1:23:45` beside `Avg heart rate` without either shrinking.
 */
private val FIGURE_MIN_WIDTH = 180.dp
