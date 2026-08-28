package com.pelonot.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pelonot.core.Formatters
import com.pelonot.domain.model.RideDayGrouping
import com.pelonot.domain.model.RidesOfThisLength
import com.pelonot.ui.theme.expressiveShapes
import com.pelonot.ui.theme.spacing
import java.text.DateFormat
import java.util.Date

/**
 * The rider's own rides of this length, best first (PLAN 24.5).
 *
 * The card the owner's note asked for: *"I've done 3 rides at 30 mins but I
 * can't see my previous 30-min time."* It sits beside [ClassLeaderboardCard]
 * and is deliberately not folded into it — that board compares **people** and
 * draws nothing for a household of one, which is the bike this note was written
 * from.
 *
 * **The class name on every row is load-bearing rather than decoration**
 * ([RidesOfThisLength] rule 3). A recovery ride and a Sprints class are both
 * thirty minutes and are not the same effort, so a bare column of kilojoules
 * would report the recovery ride as a bad ride. Naming the class is what makes
 * the comparison the rider's to make instead of the app's to assert — the same
 * move 24.1.3 makes when kJ and kJ/kg disagree and both are shown.
 *
 * **No caveat about the numbers**, for 24.4.1's reason and one more: every row
 * here is measured watts off this rider's own board, and both sides of every
 * comparison are the same person on the same bike. There is nothing left to
 * disclaim.
 */
@Composable
fun RidesOfThisLengthCard(
    board: RidesOfThisLength,
    modifier: Modifier = Modifier
) {
    if (!board.isWorthShowing) return

    val minutes = board.classDurationSec / 60

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.expressiveShapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(Modifier.padding(MaterialTheme.spacing.large)) {
            Text(
                // "Your 30 minutes" rather than "Your 30-minute rides": the
                // rider's own phrase for this is the length, and Phase 26's
                // rule is to say less where a screen is not being measured.
                text = "Your $minutes minutes",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
            Text(
                // Said only when it is true. On a board that happens to be one
                // class all the way down, "whatever the class" is a promise
                // about rows that are not there.
                text = if (board.crossesClasses) {
                    "Every $minutes minutes you have ridden, whatever the class"
                } else {
                    "Every time you have ridden this"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.size(MaterialTheme.spacing.medium))

            val visible = board.visible
            visible.rows.forEachIndexed { index, entry ->
                RideOfLengthRow(entry)
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
        }
    }
}

@Composable
private fun RideOfLengthRow(entry: RidesOfThisLength.Entry) {
    val whenText = remember(entry.recordedAt) {
        when (RideDayGrouping.relativeTo(entry.recordedAt)) {
            RideDayGrouping.Relative.Today -> "Today"
            RideDayGrouping.Relative.Yesterday -> "Yesterday"
            RideDayGrouping.Relative.Earlier ->
                DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(entry.recordedAt))
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = MaterialTheme.spacing.small)
            .clearAndSetSemantics {
                contentDescription = buildString {
                    append("${entry.rank}. ${entry.classTitle}, $whenText")
                    if (entry.isThisRide) append(", this ride")
                    append(", ${Formatters.kilojoules(entry.outputKj)}")
                }
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(
                    color = if (entry.isThisRide) {
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
                color = if (entry.isThisRide) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }

        Spacer(Modifier.width(MaterialTheme.spacing.medium))

        Column(Modifier.weight(1f)) {
            Text(
                text = entry.classTitle,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = if (entry.isThisRide) FontWeight.Bold else FontWeight.Normal
            )
            Text(
                // The date is what tells one of the rider's own rides from
                // another when two rows carry the same class name, which on
                // this board is the ordinary case rather than the exception.
                text = whenText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Text(
            text = Formatters.kilojoules(entry.outputKj),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold
        )
    }
}
