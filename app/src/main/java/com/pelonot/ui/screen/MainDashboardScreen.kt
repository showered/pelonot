package com.pelonot.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pelonot.ui.theme.CadenceCyan
import com.pelonot.ui.theme.PowerCoral
import com.pelonot.ui.theme.TextPrimary

@Composable
fun MainDashboardScreen(
    userName: String,
    ftp: Int,
    onJustRide: () -> Unit,
    onBeginClass: () -> Unit,
    onSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Welcome, $userName",
            style = androidx.compose.material3.MaterialTheme.typography.headlineLarge,
            color = TextPrimary
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "FTP: $ftp W",
            style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
            color = TextPrimary
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = onJustRide,
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
        ) {
            Text(
                text = "Just Ride",
                style = androidx.compose.material3.MaterialTheme.typography.titleLarge
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(
            onClick = onBeginClass,
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
        ) {
            Text(
                text = "Begin Class",
                style = androidx.compose.material3.MaterialTheme.typography.titleLarge
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(
            onClick = onSettings,
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
        ) {
            Text(
                text = "Settings / FTP",
                style = androidx.compose.material3.MaterialTheme.typography.titleLarge
            )
        }
    }
}