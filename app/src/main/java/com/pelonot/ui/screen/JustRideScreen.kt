package com.pelonot.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pelonot.ui.theme.CadenceCyan
import com.pelonot.ui.theme.HeartRateGreen
import com.pelonot.ui.theme.PowerCoral
import com.pelonot.ui.theme.TextPrimary
import com.pelonot.ui.theme.TextSecondary

@Composable
fun JustRideScreen(
    modifier: Modifier = Modifier,
    ftp: Int,
    onEndRide: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Just Ride",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Ride freely — no intervals, no pressure.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )

        Spacer(modifier = Modifier.height(48.dp))

        // Placeholder metrics
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            MetricDisplay(label = "Power", value = "0", unit = "W", color = PowerCoral)
            MetricDisplay(label = "Cadence", value = "0", unit = "RPM", color = CadenceCyan)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            MetricDisplay(label = "HR", value = "--", unit = "BPM", color = HeartRateGreen)
            MetricDisplay(label = "Time", value = "00:00", unit = "", color = MaterialTheme.colorScheme.onBackground)
        }

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = onEndRide,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = PowerCoral
            )
        ) {
            Text(
                text = "End Ride",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun MetricDisplay(
    label: String,
    value: String,
    unit: String,
    color: androidx.compose.ui.graphics.Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "$value $unit",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}