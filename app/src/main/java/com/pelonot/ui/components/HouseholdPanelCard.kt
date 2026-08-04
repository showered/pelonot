package com.pelonot.ui.components

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
import com.pelonot.domain.social.HouseholdRider
import com.pelonot.ui.theme.expressiveShapes
import com.pelonot.ui.theme.spacing

/**
 * Who on this bike has ridden in the last 30 days (24.2, 22.5.4).
 *
 * Rule 3 of the connectivity model: everyone with a profile on this tablet is a
 * household, and this is a Room query. No account, no network, nothing to
 * configure.
 *
 * **Two things it deliberately does not do.**
 *
 * It never names somebody who has not ridden — 24.2.4, and the reason there is
 * no "0 rides" row to render is that `WorkoutDao.householdRecent` inner-joins, so
 * the row does not exist. "Sam has not ridden" is a feature that
 * starts arguments, and the way not to ship it is to have nothing to ship it
 * with.
 *
 * And it does not rank. The per-class board (24.1) is a comparison because the
 * class is the same; a month of somebody else’s riding is not, and turning
 * it into a table with places in it would invent a competition nobody entered.
 */
@Composable
fun HouseholdPanelCard(
    riders: List<HouseholdRider>,
    youId: Int?,
    modifier: Modifier = Modifier
) {
    // A household of one is the rider's own numbers with a title on them, and
    // the dashboard has already told them that (same argument as 24.1.6).
    if (riders.size < 2) return

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.expressiveShapes.container,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(Modifier.padding(MaterialTheme.spacing.large)) {
            Text(
                text = "On this bike, last 30 days",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.width(MaterialTheme.spacing.small))

            riders.forEach { rider ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = MaterialTheme.spacing.medium),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (rider.localUserId == youId) "${rider.name} (you)" else rider.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )

                    // A streak of one is "they rode this week", which the ride
                    // count beside it already says. It becomes worth a word at
                    // two — and it counts weeks now (22.5.4), because at one
                    // ride a week a run of days is never more than 1 and the
                    // most consistent rider in the house was the one this
                    // never said anything about.
                    if (rider.streakWeeks >= 2) {
                        Text(
                            text = "${rider.streakWeeks} weeks in a row",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                        Spacer(Modifier.width(MaterialTheme.spacing.medium))
                    }

                    Text(
                        text = buildString {
                            append(rider.rides)
                            append(if (rider.rides == 1) " ride" else " rides")
                            append(" · ")
                            append("%.0f kJ".format(rider.outputKj))
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.width(MaterialTheme.spacing.small))
            Text(
                text = "Everyone with a profile on this bike. Nothing leaves the tablet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = MaterialTheme.spacing.medium)
            )
        }
    }
}
