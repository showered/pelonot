package com.pelonot.ui.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pelonot.R
import com.pelonot.core.Formatters
import com.pelonot.domain.model.PowerZone
import com.pelonot.domain.model.RideIntent
import com.pelonot.domain.model.targetPowerRange
import com.pelonot.ui.theme.AlertRed
import com.pelonot.ui.theme.MetricCadenceCyan
import com.pelonot.ui.theme.MetricHeartRateGreen
import com.pelonot.ui.theme.MetricPowerCoral
import com.pelonot.ui.theme.color
import com.pelonot.ui.theme.expressiveShapes
import com.pelonot.ui.theme.spacing

/**
 * The floating heads-up display shown over a third-party video app.
 *
 * Colours come from the theme's `colorScheme` rather than the hardcoded
 * `DarkBackground` / `TextPrimary` constants this used previously, which meant
 * the HUD ignored the theme entirely and rendered dark-on-dark in light mode.
 */
@Composable
fun HudOverlayMain(
    cadence: Double,
    resistance: Double,
    power: Double,
    heartRate: Int?,
    elapsedSeconds: Int,
    ftp: Double,
    isPaused: Boolean = false,
    targetCadenceMin: Double = DEFAULT_TARGET_CADENCE_MIN,
    targetCadenceMax: Double = DEFAULT_TARGET_CADENCE_MAX,
    targetZone: PowerZone = PowerZone.Z3,
    intent: RideIntent = RideIntent.DEFAULT,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onDrag: (Float, Float) -> Unit
) {
    val targetPower = targetZone.targetPowerRange(ftp, intent)
    // Only flag a cadence miss once the rider is actually pedalling; a
    // stationary bike is not "below target".
    val cadenceAlert = cadence > 1.0 && (cadence < targetCadenceMin || cadence > targetCadenceMax)
    val powerAlert = power > 1.0 && power !in targetPower
    val currentZone = PowerZone.forPower(power, ftp)

    Card(
        modifier = Modifier.width(300.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
        ),
        shape = MaterialTheme.expressiveShapes.large,
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier.padding(MaterialTheme.spacing.medium),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Drag handle. The gesture detector lives here rather than on the
            // whole card, so dragging cannot be swallowed by the buttons.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            onDrag(dragAmount.x, dragAmount.y)
                        }
                    }
                    .semantics {
                        contentDescription = "Drag to reposition the heads-up display"
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 40.dp, height = 4.dp)
                        .background(
                            MaterialTheme.colorScheme.outline,
                            MaterialTheme.expressiveShapes.pill
                        )
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = Formatters.duration(elapsedSeconds),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "Z${currentZone.number}",
                    color = currentZone.color,
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(Modifier.size(MaterialTheme.spacing.medium))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)
            ) {
                HudMetric(
                    label = "CADENCE",
                    value = cadence.toInt().toString(),
                    unit = "RPM",
                    accent = MetricCadenceCyan,
                    isAlert = cadenceAlert,
                    modifier = Modifier.weight(1f)
                )
                HudMetric(
                    label = "POWER",
                    value = power.toInt().toString(),
                    unit = "W",
                    accent = MetricPowerCoral,
                    isAlert = powerAlert,
                    modifier = Modifier.weight(1f)
                )
                HudMetric(
                    label = "HR",
                    value = heartRate?.toString() ?: "--",
                    unit = "BPM",
                    accent = MetricHeartRateGreen,
                    isAlert = false,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.size(MaterialTheme.spacing.small))

            TargetsPanel(
                targetCadenceMin = targetCadenceMin,
                targetCadenceMax = targetCadenceMax,
                targetPowerRange = targetPower,
                targetZone = targetZone,
                resistance = resistance
            )

            Spacer(Modifier.size(MaterialTheme.spacing.medium))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(
                    MaterialTheme.spacing.medium,
                    Alignment.CenterHorizontally
                )
            ) {
                // A single toggle rather than separate Pause and Resume
                // buttons, one of which was always a no-op.
                FilledIconButton(
                    onClick = if (isPaused) onResume else onPause,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Icon(
                        imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                        contentDescription = stringResource(
                            if (isPaused) R.string.cd_resume_ride else R.string.cd_pause_ride
                        )
                    )
                }

                FilledIconButton(
                    onClick = onStop,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = stringResource(R.string.cd_end_ride)
                    )
                }
            }
        }
    }
}

@Composable
private fun HudMetric(
    label: String,
    value: String,
    unit: String,
    accent: Color,
    isAlert: Boolean,
    modifier: Modifier = Modifier
) {
    val containerColor by animateColorAsState(
        targetValue = if (isAlert) {
            AlertRed.copy(alpha = 0.25f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "HudMetricContainer"
    )

    val scale by animateFloatAsState(
        targetValue = if (isAlert) ALERT_SCALE else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "HudMetricScale"
    )

    Card(
        modifier = modifier
            .scale(scale)
            .clearAndSetSemantics { contentDescription = "$label $value $unit" },
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = MaterialTheme.expressiveShapes.medium
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MaterialTheme.spacing.small),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                // Was typography.displayLarge — 72sp — inside a 300dp-wide card
                // split three ways, so every value was clipped.
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                color = if (isAlert) AlertRed else accent,
                maxLines = 1
            )
            Text(
                text = unit,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TargetsPanel(
    targetCadenceMin: Double,
    targetCadenceMax: Double,
    targetPowerRange: ClosedFloatingPointRange<Double>,
    targetZone: PowerZone,
    resistance: Double
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceContainerLow,
                MaterialTheme.expressiveShapes.medium
            )
            .padding(MaterialTheme.spacing.small)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "TARGET · ${targetZone.displayName}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "RES ${resistance.toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.size(MaterialTheme.spacing.extraSmall))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${targetCadenceMin.toInt()}–${targetCadenceMax.toInt()} RPM",
                color = MetricCadenceCyan,
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "${targetPowerRange.start.toInt()}–" +
                    "${targetPowerRange.endInclusive.toInt()} W",
                color = MetricPowerCoral,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

/**
 * Personal best, personal average and household best for the current ride
 * length. Collapsible, since the HUD sits over video the rider is watching.
 */
@Composable
fun LeaderboardPanel(
    currentOutputKj: Double,
    personalBestKj: Double?,
    personalAverageKj: Double?,
    householdBestKj: Double?,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(true) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceContainerLow,
                MaterialTheme.expressiveShapes.medium
            )
            .padding(MaterialTheme.spacing.small)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .semantics {
                    contentDescription =
                        if (expanded) "Collapse leaderboard" else "Expand leaderboard"
                },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "LEADERBOARD",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = if (expanded) "▾" else "▸",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column {
                LeaderboardRow("Current", currentOutputKj, isHighlight = true)
                LeaderboardRow("Personal best", personalBestKj, isHighlight = false)
                LeaderboardRow("Personal average", personalAverageKj, isHighlight = false)
                LeaderboardRow("Household best", householdBestKj, isHighlight = false)
            }
        }
    }
}

@Composable
private fun LeaderboardRow(label: String, value: Double?, isHighlight: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = if (isHighlight) {
                MetricCadenceCyan
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
        Text(
            text = value?.let(Formatters::kilojoules) ?: "--",
            style = MaterialTheme.typography.bodySmall,
            color = if (isHighlight) MetricCadenceCyan else MaterialTheme.colorScheme.onSurface
        )
    }
}

private const val ALERT_SCALE = 1.05f
private const val DEFAULT_TARGET_CADENCE_MIN = 80.0
private const val DEFAULT_TARGET_CADENCE_MAX = 100.0
