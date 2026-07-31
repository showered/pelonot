package com.pelonot.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import com.pelonot.core.Formatters
import com.pelonot.data.local.entity.WorkoutEntity
import com.pelonot.ui.theme.expressiveShapes
import com.pelonot.ui.theme.spacing
import com.pelonot.ui.theme.units

/**
 * A completed ride's figures.
 *
 * Extracted out of `PostRideSummaryScreen` rather than copied into the ride
 * detail screen (12.2.2): the two show exactly the same six numbers, and two
 * copies would have drifted the first time one of them gained a unit or a
 * label.
 *
 * What the two screens do *not* share is everything around it — the RPE prompt,
 * the FTP breakthrough dialog and the guest-filing choices all belong to the
 * ninety seconds after a ride and have no business on a ride from last March.
 */
@Composable
fun RideSummaryCard(
    workout: WorkoutEntity,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.expressiveShapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(Modifier.padding(MaterialTheme.spacing.large)) {
            SummaryRow("Duration", Formatters.duration(workout.durationSec))
            SummaryRow("Total output", Formatters.kilojoules(workout.totalOutputKj))
            SummaryRow("Average power", Formatters.watts(workout.avgPower ?: 0.0))
            SummaryRow("Average cadence", Formatters.rpm(workout.avgCadence ?: 0.0))
            SummaryRow("Average heart rate", Formatters.bpm(workout.avgHr?.toInt()))
            SummaryRow(
                "Distance",
                Formatters.distance(workout.totalDistanceKm, MaterialTheme.units)
            )
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = MaterialTheme.spacing.small)
            // Read as one phrase by a screen reader rather than two
            // disconnected fragments.
            .clearAndSetSemantics { contentDescription = "$label: $value" },
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
