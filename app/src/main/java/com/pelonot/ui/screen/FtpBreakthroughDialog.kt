package com.pelonot.ui.screen

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun FtpBreakthroughDialog(
    currentFtp: Int,
    estimatedFtp: Double,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDecline,
        title = {
            Text(
                text = "FTP Breakthrough!",
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Text(
                text = "New estimated FTP: ${estimatedFtp.toInt()}W (current: ${currentFtp}W)\n\nYour fitness has improved! Update your FTP?",
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        confirmButton = {
            Button(onClick = onAccept) {
                Text("Update FTP")
            }
        },
        dismissButton = {
            Button(onClick = onDecline) {
                Text("Keep Current")
            }
        }
    )
}