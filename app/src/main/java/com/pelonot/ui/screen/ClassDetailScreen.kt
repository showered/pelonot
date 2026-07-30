package com.pelonot.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pelonot.R
import com.pelonot.core.Formatters
import com.pelonot.data.repository.ClassPlan
import com.pelonot.domain.model.Interval
import com.pelonot.domain.model.RideIntent
import com.pelonot.domain.model.targetPowerRange
import com.pelonot.ui.theme.color
import com.pelonot.ui.theme.expressiveShapes
import com.pelonot.ui.theme.spacing

/**
 * Interval breakdown for a class.
 *
 * The interval list was always empty before this change: the screen declared
 * its own `Interval` type with camelCase field names and decoded the asset
 * JSON — which is snake_case, and describes segments by start/end timestamp
 * rather than duration — inside a `try/catch` that returned `emptyList()`.
 * Parsing now lives in [com.pelonot.domain.model.IntervalParser] against the
 * real format.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassDetailScreen(
    plan: ClassPlan?,
    ftp: Double,
    onBack: () -> Unit,
    onStart: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(plan?.title ?: "Class") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        if (plan == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "This class is no longer available.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Text(
                text = "${Formatters.minutes(plan.durationSec)} · ${plan.category} · " +
                    "${plan.intervals.size} intervals",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = MaterialTheme.spacing.large)
            )

            Spacer(Modifier.size(MaterialTheme.spacing.medium))

            if (plan.intervals.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(MaterialTheme.spacing.doubleExtraLarge),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "This class has no readable interval data, so targets " +
                            "cannot be shown. You can still ride it as a free ride.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = MaterialTheme.spacing.large),
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)
                ) {
                    items(plan.intervals) { interval ->
                        IntervalCard(interval = interval, ftp = ftp)
                    }
                }
            }

            Button(
                onClick = onStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(MaterialTheme.spacing.large)
                    .height(56.dp),
                shape = MaterialTheme.expressiveShapes.pill
            ) {
                Text("Start class")
            }
        }
    }
}

@Composable
private fun IntervalCard(interval: Interval, ftp: Double) {
    val zone = interval.powerZone
    val powerRange = zone.targetPowerRange(ftp, RideIntent.DEFAULT)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "Zone ${zone.number}, ${zone.displayName}, " +
                    "${Formatters.duration(interval.durationSec)}, " +
                    "cadence ${interval.cadenceMin} to ${interval.cadenceMax} RPM"
            },
        shape = MaterialTheme.expressiveShapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Colour rail: zone intensity readable at a glance while scanning.
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .height(72.dp)
                    .background(zone.color)
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(MaterialTheme.spacing.medium)
            ) {
                Text(
                    text = "Z${zone.number} · ${zone.displayName}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${interval.cadenceMin}–${interval.cadenceMax} RPM · " +
                        "${powerRange.start.toInt()}–${powerRange.endInclusive.toInt()} W",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = Formatters.duration(interval.durationSec),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = MaterialTheme.spacing.large)
            )
        }
    }
}
