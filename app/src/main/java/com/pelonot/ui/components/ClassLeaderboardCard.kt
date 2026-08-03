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
import com.pelonot.domain.model.ClassLeaderboard
import com.pelonot.ui.theme.expressiveShapes
import com.pelonot.ui.theme.spacing
import java.util.Locale

/**
 * The household's board for one class (24.1).
 *
 * Draws nothing at all when there is nothing worth drawing — a household of
 * one, or a household whose rides were all simulated. That is
 * [ClassLeaderboard.isWorthShowing]'s rule and the caller does not repeat
 * it.
 *
 * **No caveat, deliberately** (24.4.1). Every ride on here came off the same
 * board and the same knob, usually within the same week, so there is nothing
 * to disclaim — and a disclaimer nobody reads is the same as none. The
 * cross-bike version of this (18.7) is the one that needs the sentence.
 */
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
            // The heading follows the rows rather than the feature: a rider
            // with no account, or with wifi down, gets exactly the card they
            // got before, still saying "On this bike" — which is true, and is
            // the whole reason the household half never touches the network.
            Text(
                text = if (leaderboard.crossesBikes) "Everyone riding this" else "On this bike",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (leaderboard.crossesBikes) {
                    "Everyone who has ridden this class, best ride first"
                } else {
                    "Everyone here who has ridden this class, best ride first"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.size(MaterialTheme.spacing.medium))

            for (entry in leaderboard.entries) {
                LeaderboardRow(entry)
            }

            // 18.7, and the reason it is one line rather than a paragraph: the
            // comparison **is** honest — every row on this board is measured
            // watts off the rider's own board, enforced in the query rather
            // than hoped for — so this says where somebody rode, not that the
            // number is doubtful. A blanket disclaimer nobody reads is the
            // same as none.
            if (leaderboard.marksAnyRider) {
                Spacer(Modifier.size(MaterialTheme.spacing.small))
                Text(
                    text = "\u25CB rode a different bike. Only measured watts are ranked, " +
                        "on either bike.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun LeaderboardRow(entry: ClassLeaderboard.Entry) {
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
                    if (entry.source == ClassLeaderboard.Source.Cloud && !entry.isYou) {
                        append(", on another bike")
                    }
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
            // A small ring after the name marks a rider on another bike, and
            // the caption under the card says what it means. A glyph rather
            // than the word "cloud": the rider does not care where the row was
            // stored, only that it is not somebody standing next to them.
            // Never on your own row. Seen on the AVD: the rider's only ranked
            // ride was the cloud copy — their local ones were simulated, so the
            // household query excluded them (24.4.2) — and the board told them
            // *they* had ridden a different bike. The mark answers "who is this
            // stranger?", which is not a question anybody asks about
            // themselves.
            text = if (entry.source == ClassLeaderboard.Source.Cloud && !entry.isYou) {
                "${entry.name} \u25CB"
            } else {
                entry.name
            },
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
