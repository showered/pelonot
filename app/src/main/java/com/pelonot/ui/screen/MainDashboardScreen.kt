package com.pelonot.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

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
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "FTP: $ftp W",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground
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
                style = MaterialTheme.typography.titleLarge
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
                style = MaterialTheme.typography.titleLarge
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
                style = MaterialTheme.typography.titleLarge
            )
        }
    }
}