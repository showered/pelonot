package com.pelonot.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pelonot.core.Formatters
import com.pelonot.domain.identity.Avatar
import com.pelonot.domain.model.ClassLeaderboard
import com.pelonot.ui.theme.expressiveShapes
import com.pelonot.ui.theme.spacing

/** The selected class's measured scores. Duration records belong on the live ride. */
@Composable
fun ClassLeaderboardCard(
    leaderboard: ClassLeaderboard,
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
                text = when {
                    leaderboard.entries.size == 1 && leaderboard.entries[0].isYou ->
                        "Your best in this class"
                    leaderboard.entries.size == 1 -> "Best in this class"
                    leaderboard.crossesBikes -> "Everyone riding this"
                    else -> "On this bike"
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.size(MaterialTheme.spacing.medium))

            val visible = leaderboard.visible
            visible.rows.forEachIndexed { index, entry ->
                LeaderboardRow(entry, showRank = leaderboard.entries.size > 1)
                if (index == visible.breakAfter) {
                    Text(
                        text = "⋮",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 10.dp)
                    )
                }
            }

            if (visible.hidden > 0) {
                Spacer(Modifier.size(MaterialTheme.spacing.small))
                Text(
                    text = "and ${visible.hidden} more",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (visible.marksAnyRider) {
                Spacer(Modifier.size(MaterialTheme.spacing.small))
                Text(
                    text = "○ different bike",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun LeaderboardRow(entry: ClassLeaderboard.Entry, showRank: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = MaterialTheme.spacing.small)
            .clearAndSetSemantics {
                contentDescription = buildString {
                    if (showRank) append("${entry.rank}. ")
                    append(entry.name)
                    if (entry.isYou) append(", you")
                    if (entry.source == ClassLeaderboard.Source.Cloud && !entry.isYou) {
                        append(", on another bike")
                    }
                    append(", this class ${Formatters.kilojoules(entry.outputKj)}")
                }
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)
    ) {
        if (showRank) {
            Text(
                text = "${entry.rank}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(20.dp)
            )
        }
        RiderAvatar(
            name = entry.name,
            avatar = entry.avatar ?: Avatar.defaultFor(
                entry.accountId?.hashCode() ?: entry.localUserId ?: 0
            ),
            size = 40.dp
        )
        Text(
            text = if (entry.source == ClassLeaderboard.Source.Cloud && !entry.isYou) {
                "${entry.name} ○"
            } else {
                entry.name
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = if (entry.isYou) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = Formatters.kilojoules(entry.outputKj),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold
        )
    }
}
