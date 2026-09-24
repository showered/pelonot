package com.pelonot.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pelonot.domain.model.RideGoal
import com.pelonot.domain.model.UnitSystem

@Composable
fun JustRideGoalDialog(units: UnitSystem, onChoose: (RideGoal?) -> Unit, onDismiss: () -> Unit) {
    var mode by rememberSaveable { mutableStateOf<String?>(null) }
    var amount by rememberSaveable { mutableIntStateOf(30) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Just Ride") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Choose a finish line, or ride as long as you like.")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = mode == "time", onClick = { mode = "time"; amount = 30 }, label = { Text("Time") })
                    FilterChip(selected = mode == "distance", onClick = { mode = "distance"; amount = 10 }, label = { Text("Distance") })
                }
                if (mode != null) {
                    val options = if (mode == "time") listOf(15, 20, 30, 45, 60) else listOf(5, 10, 15, 20, 30)
                    Column {
                        options.forEach { value ->
                            FilterChip(
                                selected = amount == value,
                                onClick = { amount = value },
                                label = { Text("$value ${if (mode == "time") "min" else units.distanceLabel}") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
                TextButton(onClick = { onChoose(null) }) { Text("Open-ended ride") }
            }
        },
        confirmButton = {
            if (mode != null) Button(onClick = {
                onChoose(if (mode == "time") RideGoal.Time(amount * 60) else RideGoal.distance(amount.toDouble(), units))
            }) { Text("Start ride") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
