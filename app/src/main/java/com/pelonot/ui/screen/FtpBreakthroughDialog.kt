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
                text = "A stronger FTP",
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Text(
                text = "$currentFtp → ${estimatedFtp.toInt()} W\n\n" +
                    "Your best measured 20-minute effort supports a higher estimate. " +
                    "Use it for your future class targets?",
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        confirmButton = {
            Button(onClick = onAccept) {
                Text("Use ${estimatedFtp.toInt()} W")
            }
        },
        dismissButton = {
            Button(onClick = onDecline) {
                Text("Keep $currentFtp W")
            }
        }
    )
}