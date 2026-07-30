package com.pelonot.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pelonot.R
import com.pelonot.core.Formatters
import com.pelonot.data.repository.ClassPlan
import com.pelonot.domain.model.RideIntent
import com.pelonot.ui.theme.MetricCadenceCyan
import com.pelonot.ui.theme.MetricHeartRateGreen
import com.pelonot.ui.theme.MetricPowerCoral
import com.pelonot.ui.theme.color
import com.pelonot.ui.theme.expressiveShapes
import com.pelonot.ui.theme.spacing
import com.pelonot.ui.viewmodel.RideUiState
import com.pelonot.ui.viewmodel.RideViewModel

/**
 * The live ride screen.
 *
 * Replaces `JustRideScreen`, which rendered a developer diagnostic panel —
 * "✓ Service Started", "✗ Serial Port NOT Ready", a rolling log of the last
 * five internal events and a raw byte dump — as the rider-facing UI, above a
 * timer permanently reading `00:00`.
 *
 * Note this does not yet run class intervals or raise the floating HUD; those
 * are PLAN.md phase 9.2 and 9.3.
 */
@Composable
fun RideScreen(
    plan: ClassPlan?,
    intent: RideIntent,
    ftp: Int,
    userId: Int? = null,
    onEndRide: (workoutId: String?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RideViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(plan?.id, intent, ftp) {
        viewModel.startRide(
            userId = userId,
            classId = plan?.id,
            intent = intent,
            ftpWatts = ftp
        )
    }

    RideContent(
        state = state,
        title = plan?.title ?: "Just Ride",
        subtitle = plan?.let { "${Formatters.minutes(it.durationSec)} · ${it.category}" }
            ?: "Free ride — no intervals, no pressure",
        onPause = viewModel::pause,
        onResume = viewModel::resume,
        onEnd = { onEndRide(viewModel.endRide()) },
        modifier = modifier
    )
}

@Composable
private fun RideContent(
    state: RideUiState,
    title: String,
    subtitle: String,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(MaterialTheme.spacing.large),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        if (state.isSimulated) {
            Spacer(Modifier.size(MaterialTheme.spacing.medium))
            AssistChip(
                onClick = {},
                enabled = false,
                label = { Text("Simulated telemetry — no bike connected") },
                colors = AssistChipDefaults.assistChipColors(
                    disabledLabelColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    disabledContainerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            )
        }

        Spacer(Modifier.size(MaterialTheme.spacing.extraLarge))

        // Elapsed time is the anchor of the screen, so it gets the largest type.
        Text(
            text = Formatters.duration(state.elapsedSeconds),
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = if (state.isPaused) "PAUSED" else "ELAPSED",
            style = MaterialTheme.typography.labelMedium,
            color = if (state.isPaused) {
                MaterialTheme.colorScheme.tertiary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )

        Spacer(Modifier.size(MaterialTheme.spacing.extraLarge))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)
        ) {
            RideMetric(
                label = "POWER",
                value = state.reading.powerWatts.toInt().toString(),
                unit = "watts",
                accent = MetricPowerCoral,
                modifier = Modifier.weight(1f)
            )
            RideMetric(
                label = "CADENCE",
                value = state.reading.cadenceRpm.toInt().toString(),
                unit = "rpm",
                accent = MetricCadenceCyan,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.size(MaterialTheme.spacing.medium))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)
        ) {
            RideMetric(
                label = "HEART RATE",
                value = state.reading.heartRateBpm?.toString() ?: "--",
                unit = "bpm",
                accent = MetricHeartRateGreen,
                modifier = Modifier.weight(1f)
            )
            RideMetric(
                label = "ZONE",
                value = "Z${state.currentZone.number}",
                unit = state.currentZone.displayName,
                accent = state.currentZone.color,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.size(MaterialTheme.spacing.medium))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)
        ) {
            RideMetric(
                label = "OUTPUT",
                value = String.format(java.util.Locale.US, "%.1f", state.session?.totalOutputKj ?: 0.0),
                unit = "kJ",
                accent = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            RideMetric(
                label = "DISTANCE",
                value = String.format(java.util.Locale.US, "%.2f", state.session?.distanceKm ?: 0.0),
                unit = "km",
                accent = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.size(MaterialTheme.spacing.doubleExtraLarge))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)
        ) {
            val pauseLabel = if (state.isPaused) {
                stringResource(R.string.cd_resume_ride)
            } else {
                stringResource(R.string.cd_pause_ride)
            }

            Button(
                onClick = if (state.isPaused) onResume else onPause,
                modifier = Modifier
                    .weight(1f)
                    // 48dp is the minimum comfortable touch target, and this
                    // is pressed with sweaty hands while moving.
                    .heightIn(min = 56.dp),
                shape = MaterialTheme.expressiveShapes.pill,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            ) {
                Icon(
                    imageVector = if (state.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                    contentDescription = null
                )
                Spacer(Modifier.width(MaterialTheme.spacing.small))
                Text(pauseLabel)
            }

            Button(
                onClick = onEnd,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 56.dp),
                shape = MaterialTheme.expressiveShapes.pill,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) {
                Icon(imageVector = Icons.Default.Stop, contentDescription = null)
                Spacer(Modifier.width(MaterialTheme.spacing.small))
                Text(stringResource(R.string.cd_end_ride))
            }
        }
    }
}

@Composable
private fun RideMetric(
    label: String,
    value: String,
    unit: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.expressiveShapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MaterialTheme.spacing.large),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.size(MaterialTheme.spacing.extraSmall))
            Text(
                text = value,
                style = MaterialTheme.typography.displaySmall,
                color = accent,
                maxLines = 1
            )
            Text(
                text = unit,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
