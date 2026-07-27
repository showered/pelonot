package com.pelonot.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pelonot.data.local.entity.ClassTemplateEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable

@Serializable
data class Interval(
    val durationSec: Int,
    val targetCadenceMin: Double,
    val targetCadenceMax: Double,
    val targetPowerMin: Double,
    val targetPowerMax: Double,
    val zone: Int
)

@Composable
fun ClassDetailScreen(
    classTemplate: ClassTemplateEntity,
    ftp: Double,
    onBack: () -> Unit,
    onStart: (String) -> Unit
) {
    val intervals = try {
        Json.decodeFromString<List<Interval>>(classTemplate.intervalsJson)
    } catch (e: Exception) {
        emptyList()
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = classTemplate.title,
            style = androidx.compose.material3.MaterialTheme.typography.headlineLarge
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "${classTemplate.durationSec / 60} min • ${classTemplate.category}",
            style = androidx.compose.material3.MaterialTheme.typography.bodyLarge
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        LazyColumn(
            modifier = Modifier.weight(1f)
        ) {
            items(intervals) { interval ->
                IntervalRow(
                    interval = interval,
                    ftp = ftp
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(onClick = onBack) {
                Text("Back")
            }
            
            Button(onClick = { onStart(classTemplate.id.toString()) }) {
                Text("Start Class")
            }
        }
    }
}

@Composable
private fun IntervalRow(
    interval: Interval,
    ftp: Double
) {
    androidx.compose.material3.Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = "Zone ${interval.zone}: ${interval.durationSec / 60} min",
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Cadence: ${interval.targetCadenceMin.toInt()}-${interval.targetCadenceMax.toInt()} RPM",
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Power: ${interval.targetPowerMin.toInt()}-${interval.targetPowerMax.toInt()}W",
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
            )
        }
    }
}