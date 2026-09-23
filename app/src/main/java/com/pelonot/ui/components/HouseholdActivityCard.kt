package com.pelonot.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pelonot.domain.model.RideDayGrouping
import com.pelonot.domain.social.HouseholdActivity
import com.pelonot.ui.theme.expressiveShapes
import com.pelonot.ui.theme.spacing

/**
 * Three pieces of recent news from this bike (22.8.7).
 *
 * This is intentionally a glance card, not a timeline. Its source is Room, so
 * opening the app never waits for the network; a later account tier can append
 * to the same event type without taking this local answer away.
 */
@Composable
fun HouseholdActivityCard(
    activities: List<HouseholdActivity>,
    youId: Int?,
    modifier: Modifier = Modifier
) {
    val rows = activities.filter { it.localUserId != youId }.take(MAX_ROWS)
    if (rows.isEmpty()) return

    Card(
        modifier = modifier,
        shape = MaterialTheme.expressiveShapes.container,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier.padding(MaterialTheme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)
        ) {
            Text(
                text = "Activity",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            rows.forEach { activity ->
                ActivityRow(activity)
            }
        }
    }
}

@Composable
private fun ActivityRow(activity: HouseholdActivity) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RiderAvatar(name = activity.name, avatar = activity.avatar, size = AVATAR_INLINE)
        Spacer(Modifier.width(MaterialTheme.spacing.small))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = buildString {
                    append(activity.name)
                    append(" completed ")
                    append(activity.classTitle ?: "a ride")
                },
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = buildString {
                    append(relativeTime(activity.completedAt))
                    when (activity.event) {
                        HouseholdActivity.Event.FtpIncreased -> append(" · FTP increased")
                        HouseholdActivity.Event.PersonalBest -> append(" · New personal best")
                        null -> Unit
                    }
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (activity.event == null) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.tertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun relativeTime(timestamp: Long, now: Long = System.currentTimeMillis()): String =
    when (RideDayGrouping.relativeTo(timestamp, now)) {
        RideDayGrouping.Relative.Today -> "Today"
        RideDayGrouping.Relative.Yesterday -> "Yesterday"
        RideDayGrouping.Relative.Earlier -> {
            val days = ((now - timestamp) / DAY_MS).toInt()
            if (days in 2..13) "$days days ago" else "Earlier"
        }
    }

private const val MAX_ROWS = 3
private const val DAY_MS = 24L * 60 * 60 * 1000
