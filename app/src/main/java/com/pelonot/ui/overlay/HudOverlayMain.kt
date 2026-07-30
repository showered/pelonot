package com.pelonot.ui.overlay

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pelonot.domain.model.PowerZone
import com.pelonot.domain.model.RideIntent
import com.pelonot.domain.model.targetPowerRange
import com.pelonot.core.Formatters
import com.pelonot.ui.theme.*

@Composable
fun HudOverlayMain(
    cadence: Double,
    resistance: Double,
    power: Double,
    heartRate: Int?,
    elapsedSeconds: Int,
    ftp: Double,
    targetCadenceMin: Double = 80.0,
    targetCadenceMax: Double = 100.0,
    targetZone: PowerZone = PowerZone.Z3,
    intent: RideIntent = RideIntent.DEFAULT,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onDrag: (Float, Float) -> Unit
) {
    val targetPowerRange = targetZone.targetPowerRange(ftp, intent)
    val isCadenceAlert = cadence < targetCadenceMin || cadence > targetCadenceMax
    val isPowerAlert = power < targetPowerRange.start || power > targetPowerRange.endInclusive

    Card(
        modifier = Modifier
            .width(280.dp)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.x, dragAmount.y)
                }
            },
        colors = CardDefaults.cardColors(containerColor = DarkBackground.copy(alpha = 0.9f)),
        shape = MaterialTheme.expressiveShapes.large,
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Drag handle / Title bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp, 4.dp)
                        .background(Color.Gray.copy(alpha = 0.5f), MaterialTheme.expressiveShapes.extraSmall)
                )
            }

            // Timer & Power Zone Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = Formatters.duration(elapsedSeconds),
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Zone ${PowerZone.forPower(power, ftp).number}",
                    color = PowerCoral,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Real-time metrics grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                MetricCard(
                    label = "CADENCE",
                    value = cadence.toInt().toString(),
                    unit = "RPM",
                    color = CadenceCyan,
                    isAlert = isCadenceAlert,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    label = "POWER",
                    value = power.toInt().toString(),
                    unit = "WATTS",
                    color = PowerCoral,
                    isAlert = isPowerAlert,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    label = "HEART RATE",
                    value = heartRate?.toString() ?: "--",
                    unit = "BPM",
                    color = HeartRateGreen,
                    isAlert = false,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Targets indicator Panel
            TargetsPanel(
                targetCadenceMin = targetCadenceMin,
                targetCadenceMax = targetCadenceMax,
                targetPowerRange = targetPowerRange,
                targetZone = targetZone
            )

            Spacer(modifier = Modifier.height(12.dp))

            // HUD Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(
                    onClick = onPause,
                    colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant)
                ) {
                    Text("Pause", color = TextPrimary)
                }
                Button(
                    onClick = onResume,
                    colors = ButtonDefaults.buttonColors(containerColor = CadenceCyan)
                ) {
                    Text("Resume", color = DarkBackground)
                }
                Button(
                    onClick = onStop,
                    colors = ButtonDefaults.buttonColors(containerColor = ZoneAlertRed)
                ) {
                    Text("Stop", color = TextPrimary)
                }
            }
        }
    }
}

@Composable
fun MetricCard(
    label: String,
    value: String,
    unit: String,
    color: Color,
    isAlert: Boolean,
    modifier: Modifier = Modifier
) {
    val animatedBgColor by animateColorAsState(
        targetValue = if (isAlert) ZoneAlertRed.copy(alpha = 0.2f) else Color.Transparent,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "MetricCardBgColor"
    )

    val scaleFactor by animateFloatAsState(
        targetValue = if (isAlert) 1.05f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "MetricCardScale"
    )

    Card(
        modifier = modifier
            .padding(4.dp)
            .scale(scaleFactor),
        colors = CardDefaults.cardColors(containerColor = animatedBgColor.takeIf { isAlert } ?: DarkSurface),
        shape = MaterialTheme.expressiveShapes.medium
    ) {
        Column(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                fontSize = 10.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = PelonotTypography.displayLarge,
                color = if (isAlert) ZoneAlertRed else color,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = unit,
                style = PelonotTypography.labelSmall,
                color = TextSecondary
            )
        }
    }
}

@Composable
fun TargetsPanel(
    targetCadenceMin: Double,
    targetCadenceMax: Double,
    targetPowerRange: ClosedRange<Double>,
    targetZone: PowerZone
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkSurface, MaterialTheme.expressiveShapes.medium)
            .padding(8.dp)
    ) {
        Text(
            text = "TARGETS",
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Cadence: ${targetCadenceMin.toInt()}-${targetCadenceMax.toInt()} RPM",
                color = CadenceCyan,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Power: ${targetPowerRange.start.toInt()}-${targetPowerRange.endInclusive.toInt()}W (${targetZone.displayName})",
                color = PowerCoral,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

/**
 * Leaderboard panel showing PB, Personal Average, and Household Best.
 */
@Composable
fun LeaderboardPanel(
    elapsedSeconds: Int,
    currentOutputKj: Double,
    personalBest: Double?,
    personalAverage: Double?,
    householdBest: Double?,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(true) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(DarkSurface, MaterialTheme.expressiveShapes.medium)
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "LEADERBOARD",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
            Text(
                text = if (expanded) "▼" else "▶",
                color = TextSecondary,
                fontSize = 12.sp
            )
        }

        if (expanded) {
            Spacer(modifier = Modifier.height(4.dp))
            
            LeaderboardRow(
                label = "Current",
                value = currentOutputKj,
                isHighlight = true
            )
            LeaderboardRow(
                label = "Personal Best",
                value = personalBest,
                isHighlight = false
            )
            LeaderboardRow(
                label = "Personal Avg",
                value = personalAverage,
                isHighlight = false
            )
            LeaderboardRow(
                label = "Household Best",
                value = householdBest,
                isHighlight = false
            )
        }
    }
}

@Composable
private fun LeaderboardRow(
    label: String,
    value: Double?,
    isHighlight: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isHighlight) CadenceCyan else TextSecondary
        )
        Text(
            text = value?.let { "%.1f kJ".format(it) } ?: "--",
            style = MaterialTheme.typography.bodyMedium,
            color = if (isHighlight) CadenceCyan else TextPrimary
        )
    }
}

