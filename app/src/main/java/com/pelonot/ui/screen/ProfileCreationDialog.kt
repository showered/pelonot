package com.pelonot.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pelonot.domain.model.UnitSystem
import com.pelonot.ui.theme.units

/**
 * The weight field is shown in the rider's own units and handed back in
 * kilograms, which is what `profiles.weight_kg` means and the only thing that
 * is ever stored. See [UnitSystem].
 */
@Composable
fun ProfileCreationDialog(
    onProfileCreated: (name: String, weightKg: Double?, ftpWatts: Int) -> Unit,
    onDismiss: () -> Unit
) {
    val units = MaterialTheme.units
    var name by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }
    var ftp by remember { mutableStateOf("200") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Create Profile",
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = weight,
                    onValueChange = { weight = it },
                    label = { Text("Weight (${units.weightLabel})") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = ftp,
                    onValueChange = { ftp = it },
                    label = { Text("FTP (Watts)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onProfileCreated(
                            name,
                            weight.toDoubleOrNull()?.let(units::weightToKg),
                            ftp.toIntOrNull() ?: 200
                        )
                    }
                }
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}