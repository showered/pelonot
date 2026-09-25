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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pelonot.domain.model.RideDayGrouping
import com.pelonot.domain.social.HouseholdActivity
import com.pelonot.ui.viewmodel.SocialFeedState
import com.pelonot.ui.theme.expressiveShapes
import com.pelonot.ui.theme.spacing

/**
 * Three pieces of recent news from this bike and, when signed in, other bikes.
 *
 * The local rows come from Room and remain visible when the cloud is unreachable.
 */
@Composable
fun HouseholdActivityCard(
    activities: List<HouseholdActivity>,
    youId: Int?,
    socialFeedState: SocialFeedState = SocialFeedState.Offline,
    kudosPendingId: String? = null,
    socialError: String? = null,
    onToggleKudos: (String, Boolean) -> Unit = { _, _ -> },
    onRetrySocial: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val rows = activities.filter { it.localUserId != youId }.take(MAX_ROWS)
    if (rows.isEmpty() && socialFeedState == SocialFeedState.Offline) return

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
                ActivityRow(activity, kudosPendingId, onToggleKudos)
            }
            if (socialFeedState == SocialFeedState.Ready && rows.isEmpty()) {
                Text("No shared rides yet", style = MaterialTheme.typography.bodySmall)
            }
            if (socialFeedState == SocialFeedState.Loading && rows.isEmpty()) {
                Text("Checking other bikes…", style = MaterialTheme.typography.bodySmall)
            }
            if (socialFeedState == SocialFeedState.Unavailable) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Other bikes unavailable", style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f))
                    TextButton(onClick = onRetrySocial) { Text("Retry") }
                }
            }
            if (socialError != null) {
                Text(socialError, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun ActivityRow(
    activity: HouseholdActivity,
    kudosPendingId: String?,
    onToggleKudos: (String, Boolean) -> Unit
) {
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
        activity.cloudWorkoutId?.let { workoutId ->
            TextButton(
                onClick = { onToggleKudos(workoutId, !activity.youGaveKudos) },
                enabled = kudosPendingId == null,
                modifier = Modifier.semantics {
                    contentDescription = if (activity.youGaveKudos) {
                        "Remove kudos from ${activity.name}'s ride, ${activity.kudosCount} kudos"
                    } else {
                        "Give kudos to ${activity.name}'s ride, ${activity.kudosCount} kudos"
                    }
                }
            ) {
                Text(
                    text = "👏 ${activity.kudosCount}",
                    color = if (activity.youGaveKudos) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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
