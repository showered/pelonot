package com.pelonot.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pelonot.ui.theme.CadenceCyan
import com.pelonot.ui.theme.TextPrimary

@Composable
fun PostRideSummaryScreen(
    durationSec: Int,
    totalOutputKj: Double,
    avgPower: Double,
    avgCadence: Double,
    avgHeartRate: Int?,
    distanceKm: Double,
    isGuest: Boolean = false,
    onSave: () -> Unit,
    onDiscard: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Ride Summary",
            style = androidx.compose.material3.MaterialTheme.typography.headlineLarge,
            color = TextPrimary
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        SummaryRow(
            label = "Duration",
            value = formatDuration(durationSec)
        )
        
        SummaryRow(
            label = "Total Output",
            value = "%.1f kJ".format(totalOutputKj)
        )
        
        SummaryRow(
            label = "Avg Power",
            value = "%.0f W".format(avgPower)
        )
        
        SummaryRow(
            label = "Avg Cadence",
            value = "%.0f RPM".format(avgCadence)
        )
        
        SummaryRow(
            label = "Avg Heart Rate",
            value = avgHeartRate?.let { "%d BPM".format(it) } ?: "--"
        )
        
        SummaryRow(
            label = "Distance",
            value = "%.2f km".format(distanceKm)
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
            text = "Rate this effort (1-10)",
            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
            color = TextPrimary
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            for (i in 1..10) {
                Button(
                    onClick = { /* TODO: Handle RPE selection */ },
                    modifier = Modifier.size(48.dp)
                ) {
                    Text(text = "$i")
                }
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        if (isGuest) {
            Text(
                text = "Guest Mode: Save or discard this ride?",
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(
                onClick = onSave,
                modifier = Modifier.weight(1f)
            ) {
                Text(if (isGuest) "Save as Guest" else "Save")
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Button(
                onClick = onDiscard,
                modifier = Modifier.weight(1f)
            ) {
                Text("Discard")
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
            color = TextPrimary
        )
        Text(
            text = value,
            style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
            color = CadenceCyan
        )
    }
}

private fun formatDuration(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return "%02d:%02d".format(mins, secs)
}