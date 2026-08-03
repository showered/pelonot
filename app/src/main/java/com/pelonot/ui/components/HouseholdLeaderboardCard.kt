package com.pelonot.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pelonot.core.Formatters
import com.pelonot.domain.model.HouseholdLeaderboard
import com.pelonot.ui.theme.expressiveShapes
import com.pelonot.ui.theme.spacing
import java.util.Locale

/**
 * The household's board for one class (24.1).
 *
 * Draws nothing at all when there is nothing worth drawing — a household of
 * one, or a household whose rides were all simulated. That is
 * [HouseholdLeaderboard.isWorthShowing]'s rule and the caller does not repeat
 * it.
 *
 * **No caveat, deliberately** (24.4.1). Every ride on here came off the same
 * board and the same knob, usually within the same week, so there is nothing
 * to disclaim — and a disclaimer nobody reads is the same as none. The
 * cross-bike version of this (18.7) is the one that needs the sentence.
 */
@Composable
fun HouseholdLeaderboardCard(
    leaderboard: HouseholdLeaderboard,
    modifier: Modifier = Modifier
) {
    if (!leaderboard.isWorthShowing) return

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.expressiveShapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(Modifier.padding(MaterialTheme.spacing.large)) {
            Text(
                text = "On this bike",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Everyone here who has ridden this class, best ride first",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.size(MaterialTheme.spacing.medium))

            for (entry in leaderboard.entries) {
                LeaderboardRow(entry)
            }
        }
    }
}

@Composable
private fun LeaderboardRow(entry: HouseholdLeaderboard.Entry) {
    // Output per kilogram is offered beside the ranking, never as it: raw
    // output is the work actually done, and w/kg is the number a lighter rider
    // will want (24.1.3). Nothing here is ranked on anything FTP-relative —
    // FTP is self-reported and auto-FTP moves it underneath a comparison
    // (7.8 is what that costs).
    //
    // The one deliberate exception to 11.6.12's no-decimals rule, and it is
    // not really an exception: kJ/kg is a different quantity, it lands between
    // roughly 1 and 6, and rounded to whole numbers two housemates who are
    // genuinely apart would tie.
    val perKg = entry.outputPerKg?.let {
        String.format(Locale.US, "%.2f kJ/kg", it)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = MaterialTheme.spacing.small)
            .clearAndSetSemantics {
                contentDescription = buildString {
                    append("${entry.rank}. ${entry.name}")
                    if (entry.isYou) append(", you")
                    append(", ${Formatters.kilojoules(entry.outputKj)}")
                    perKg?.let { append(", $it") }
                }
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(
                    color = if (entry.isYou) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "${entry.rank}",
                style = MaterialTheme.typography.labelLarge,
                color = if (entry.isYou) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }

        Spacer(Modifier.width(MaterialTheme.spacing.medium))

        Text(
            text = entry.name,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = if (entry.isYou) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1f)
        )

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = Formatters.kilojoules(entry.outputKj),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
            if (perKg != null) {
                Text(
                    text = perKg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
