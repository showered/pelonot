package com.pelonot.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.pelonot.domain.social.HouseholdPanel
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
 *
 * **The level badge does not change that, and the ordering is where it could
 * have** (26.4.4). The rows are still ordered by riding in the window, so a
 * housemate with a higher level can sit below one with a lower — which reads
 * oddly for about a second and is right: the badge says who somebody is over
 * years, and the row says what they have been doing this month. Ordering by
 * level instead would turn a presence card into a lifetime ranking, which is
 * the competition nobody entered arriving by the back door.
 *
 * **It is windowed at six** ([HouseholdPanel]), which is 24.1.8 arriving on the
 * card next door: the note capped the class leaderboard and this panel was left
 * listing every profile that had ridden, twelve deep on the tablet AVD.
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
            Spacer(Modifier.height(MaterialTheme.spacing.small))

            val panel = HouseholdPanel.of(riders, youId)
            panel.rows.forEachIndexed { index, rider ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = MaterialTheme.spacing.medium),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // The level is *identity*, so it goes with the name rather
                    // than with the figures at the other end of the row (26.4.4)
                    // — and it is the one thing on this row that is not about
                    // the last 30 days, which is why it must not be read as
                    // part of the sequence beside it.
                    RiderScore(level = rider.level)
                    Spacer(Modifier.width(MaterialTheme.spacing.medium))

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

                // The rider's own row can be lifted from below the cut, and the
                // skip has to be visible: two adjacent rows reading 4 rides and
                // 1 ride with nothing between them look like the list is simply
                // short, which is the false claim the window exists to avoid.
                if (index == panel.breakAfter) {
                    Text(
                        text = "⋮",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = MaterialTheme.spacing.small)
                    )
                }
            }

            if (panel.hidden > 0) {
                Text(
                    text = "and ${panel.hidden} more",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = MaterialTheme.spacing.medium)
                )
            }

            Spacer(Modifier.height(MaterialTheme.spacing.small))
            Text(
                text = "Everyone with a profile on this bike. Nothing leaves the tablet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = MaterialTheme.spacing.medium)
            )
        }
    }
}
