package com.pelonot.ui.overlay

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.pelonot.data.sensor.PowerZoneCalculator
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
    targetZone: PowerZoneCalculator.PowerZone = PowerZoneCalculator.PowerZone.Z3,
    intentModifier: String = "Just Stay Fit",
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onDrag: (Float, Float) -> Unit
) {
    val targetPowerRange = PowerZoneCalculator.getTargetPower(targetZone, ftp, intentModifier)
    val isCadenceAlert = cadence < targetCadenceMin || cadence > targetCadenceMax
    val isPowerAlert = power < targetPowerRange.start || power > targetPowerRange.endInclusive

    Card(
        modifier = Modifier
            .width(360.dp)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.x, dragAmount.y)
                }
            },
        colors = CardDefaults.cardColors(containerColor = DarkBackground.copy(alpha = 0.9f)),
        shape = RoundedCornerShape(16.dp),
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
                        .background(Color.Gray.copy(alpha = 0.5f), RoundedCornerShape(2.dp))
                )
            }

            // Timer & Power Zone Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatDuration(elapsedSeconds),
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Zone ${PowerZoneCalculator.getZoneForPower(power, ftp).number}",
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
        shape = RoundedCornerShape(12.dp)
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
    targetZone: PowerZoneCalculator.PowerZone
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkSurface, RoundedCornerShape(12.dp))
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

private fun formatDuration(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return "%02d:%02d".format(mins, secs)
}
