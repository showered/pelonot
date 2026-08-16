package com.pelonot.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pelonot.core.Formatters
import com.pelonot.domain.progress.RidingHistory
import com.pelonot.ui.theme.expressiveShapes
import com.pelonot.ui.theme.spacing
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Every day of the rider's last few months, ridden or not (16.3.5, 22.9.4).
 *
 * **One component, two screens.** It was written for *Your riding* and it is on
 * the dashboard because the dashboard's own question is *should I ride today*
 * (22.1.1) and a row of gaps answers it more directly than `1 ride · 3 min`
 * does. Extracted rather than copied, because the recurring defect of this
 * project is **one rule written twice** — 12.7's two effort cards, 20.4.7's two
 * pairing triggers, 23.4.12's seven leaderboard queries — and a grid that
 * disagreed with the screen behind it about which days were ridden would be
 * exactly that, on the first screen anybody sees.
 *
 * **Two rules the drawing keeps.** A day that happened and had no riding in it
 * is a *visible* tile; a day that has not happened yet is nothing at all — if
 * those two read the same, the rest of the current week looks like days the
 * rider missed. And the shade of a ridden day is its minutes against the
 * busiest day, so the grid says how much as well as whether.
 */
@Composable
fun RideDaysCard(
    history: RidingHistory,
    modifier: Modifier = Modifier,
    /**
     * A fixed edge for one day, instead of a seventh of the card's width.
     *
     * **The default sizes the squares off the width and that is only right on a
     * screen the card owns.** A day is `aspectRatio(1f)` inside a column that
     * takes a share of the row, so a card 620 dp wide draws 77 dp squares and
     * the grid alone is 540 dp tall — measured on the dashboard, where it went
     * straight off the bottom of a screen the whole change exists to fit
     * (22.9.3). Given a size, the grid is laid out from the *height* it should
     * occupy and centred in whatever width it is in.
     */
    daySquare: Dp? = null,
    /** Opens *Your riding*, where the same grid is drawn in full. */
    onClick: (() -> Unit)? = null
) {
    val shown = history.weeks
    if (shown.isEmpty()) return

    val peak = maxOf(history.busiestDayMinutes, 1)
    val ridden = shown.flatMap { it.days }.count { it?.ridden == true }
    val accent = MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)

    RidingTrendCard(
        modifier = modifier,
        title = "Ride days",
        summary = "${Formatters.plural(ridden, "day")} ridden in the last " +
            "${Formatters.plural(shown.size, "week")}" +
            if (history.streakDays >= 2) ", ${history.streakDays} of them in a row." else ".",
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (daySquare == null) Arrangement.spacedBy(3.dp)
            else Arrangement.spacedBy(3.dp, Alignment.CenterHorizontally)
        ) {
            shown.forEach { week ->
                Column(
                    modifier = if (daySquare == null) Modifier.weight(1f)
                    else Modifier.width(daySquare),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    week.days.forEach { day ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (daySquare == null) Modifier.aspectRatio(1f)
                                    else Modifier.height(daySquare)
                                )
                                .clip(MaterialTheme.expressiveShapes.small)
                                .background(
                                    when {
                                        // Not yet happened: nothing at all, so
                                        // the rest of this week is not drawn as
                                        // days off.
                                        day == null -> Color.Transparent
                                        !day.ridden -> empty
                                        else -> accent.copy(
                                            alpha = (MIN_DAY_ALPHA +
                                                (1f - MIN_DAY_ALPHA) *
                                                (day.minutes.toFloat() / peak)).coerceIn(0f, 1f)
                                        )
                                    }
                                )
                        )
                    }
                }
            }
        }

        Spacer(Modifier.size(MaterialTheme.spacing.small))
        Row(Modifier.fillMaxWidth()) {
            AxisText(monthLabel(shown.first().startMs))
            Spacer(Modifier.weight(1f))
            AxisText("today")
        }
    }
}

/**
 * The card the trend drawings live in.
 *
 * The same shape as the ride detail screen's `ChartCard` and for the same
 * reason (16.1.8, 16.2.4): one answer to "what does a chart look like here",
 * and a sentence carrying the meaning for a rider who cannot see the drawing.
 */
@Composable
internal fun RidingTrendCard(
    title: String,
    summary: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .let { base ->
                if (onClick == null) base
                else base.clickable(onClickLabel = "See all your riding") { onClick() }
            },
        shape = MaterialTheme.expressiveShapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(Modifier.padding(MaterialTheme.spacing.large)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() }
            )
            Spacer(Modifier.size(MaterialTheme.spacing.medium))

            Box(Modifier.semantics { contentDescription = summary }) {
                Column(Modifier.fillMaxWidth()) { content() }
            }

            Spacer(Modifier.size(MaterialTheme.spacing.small))
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clearAndSetSemantics { }
            )
        }
    }
}

@Composable
internal fun AxisText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

internal fun monthLabel(epochMs: Long): String =
    SimpleDateFormat("MMM", Locale.getDefault()).format(Date(epochMs))

/** Faint enough to read as "a little", solid enough to read as "a day that happened". */
internal const val MIN_DAY_ALPHA = 0.35f
