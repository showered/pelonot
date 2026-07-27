package com.pelonot.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pelonot.ui.theme.TextPrimary

@Composable
fun SettingsScreen(
    currentFtp: Int,
    currentWeight: Double?,
    isDarkTheme: Boolean,
    onFtpChange: (Int) -> Unit,
    onWeightChange: (Double) -> Unit,
    onThemeToggle: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    var ftpText by remember { mutableStateOf(currentFtp.toString()) }
    var weightText by remember { mutableStateOf(currentWeight?.toString() ?: "") }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Settings",
            style = androidx.compose.material3.MaterialTheme.typography.headlineLarge,
            color = TextPrimary
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // FTP Setting
        Text(
            text = "FTP (Watts)",
            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
            color = TextPrimary
        )
        androidx.compose.material3.OutlinedTextField(
            value = ftpText,
            onValueChange = { ftpText = it },
            label = { Text("FTP") },
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Weight Setting
        Text(
            text = "Weight (kg)",
            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
            color = TextPrimary
        )
        androidx.compose.material3.OutlinedTextField(
            value = weightText,
            onValueChange = { weightText = it },
            label = { Text("Weight") },
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Theme Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Dark Theme",
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
            Switch(
                checked = isDarkTheme,
                onCheckedChange = onThemeToggle
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = {
                ftpText.toIntOrNull()?.let { onFtpChange(it) }
                weightText.toDoubleOrNull()?.let { onWeightChange(it) }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save Settings")
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Back")
        }
    }
}