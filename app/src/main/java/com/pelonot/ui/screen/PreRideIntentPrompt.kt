package com.pelonot.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.pelonot.domain.model.RideIntent
import com.pelonot.ui.theme.expressiveShapes
import com.pelonot.ui.theme.spacing

/**
 * Asks how hard the rider wants to work, which scales every power target for
 * the session.
 *
 * Emits a typed [RideIntent] rather than the display string the previous
 * version passed around, and shows each option's effect so the choice is
 * informed rather than a guess between two slogans.
 */
@Composable
fun PreRideIntentPrompt(
    onIntentSelected: (RideIntent) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.expressiveShapes.extraLarge,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        ) {
            Column(
                modifier = Modifier.padding(MaterialTheme.spacing.extraLarge),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "What's your goal today?",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(Modifier.size(MaterialTheme.spacing.large))

                RideIntent.entries.forEach { intent ->
                    IntentOption(
                        intent = intent,
                        onClick = { onIntentSelected(intent) }
                    )
                    Spacer(Modifier.size(MaterialTheme.spacing.medium))
                }

                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IntentOption(
    intent: RideIntent,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp),
        onClick = onClick,
        shape = MaterialTheme.expressiveShapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MaterialTheme.spacing.large)
        ) {
            Text(
                text = intent.displayName,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = intent.description,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
