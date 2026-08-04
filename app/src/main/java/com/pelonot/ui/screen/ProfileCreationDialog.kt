package com.pelonot.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pelonot.data.local.entity.UserEntity
import com.pelonot.domain.model.UnitSystem
import com.pelonot.ui.theme.units

/**
 * The weight field is shown in the rider's own units and handed back in
 * kilograms, which is what `profiles.weight_kg` means and the only thing that
 * is ever stored. See [UnitSystem].
 *
 * 13.8: the unit is **asked**, not assumed. The label used to be seeded from
 * the locale and left at that, so a rider on a bike in the UK — where body
 * weight is not measured in the same system as road distance — was shown
 * `Weight (lb)` on the first screen the app has, with no way to disagree: the
 * only unit control lives in Settings, and there is no route to Settings before
 * a profile exists. The chips beside the field are that route.
 *
 * The choice deliberately does **not** write the app-wide unit preference. A
 * rider who weighs themselves in kilograms may well still want their distance
 * in miles, and this dialog is not the place to decide that for them.
 */
@Composable
fun ProfileCreationDialog(
    onProfileCreated: (name: String, weightKg: Double?, ftpWatts: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }
    // One default, in one place (20.3.6). This field used to open on "200" and
    // fall back to 200 when it was cleared, while every other path in the app —
    // `UserEntity`'s own default, Settings, the ride detail chart, the nav
    // graph — used 150. So a rider created here started 50 W above a rider
    // created anywhere else, and nothing on any screen said which they were.
    // Whatever 20.3 lands on, exactly one constant expresses it.
    var ftp by remember { mutableStateOf(UserEntity.DEFAULT_FTP.toString()) }
    // Opens on whatever the rest of the app is using, which on a fresh install
    // is the locale's guess. It is a starting point rather than an answer.
    val preferred = MaterialTheme.units
    var weightUnits by remember(preferred) { mutableStateOf(preferred) }

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

                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = weight,
                        onValueChange = { weight = it },
                        label = { Text("Weight (${weightUnits.weightLabel})") },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        UnitSystem.entries.forEach { option ->
                            FilterChip(
                                selected = weightUnits == option,
                                onClick = { weightUnits = option },
                                label = { Text(option.weightLabel) }
                            )
                        }
                    }
                }

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
                            // Converted with the unit the rider chose, not the
                            // one the app guessed for them.
                            weight.toDoubleOrNull()?.let(weightUnits::weightToKg),
                            ftp.toIntOrNull() ?: UserEntity.DEFAULT_FTP
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
