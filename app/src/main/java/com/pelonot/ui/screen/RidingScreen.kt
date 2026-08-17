package com.pelonot.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pelonot.domain.progress.RidingHistory
import com.pelonot.domain.progress.RidingIntensity
import com.pelonot.domain.progress.RidingIntensitySummary
import com.pelonot.domain.progress.RidingWeek
import com.pelonot.ui.components.AxisText
import com.pelonot.ui.components.ChartCard
import com.pelonot.ui.components.RideDaysCard
import com.pelonot.ui.components.RidingTrendCard
import com.pelonot.ui.components.TimeInZoneBar
import com.pelonot.ui.components.monthLabel
import com.pelonot.ui.theme.expressiveShapes
import com.pelonot.ui.theme.loneCard
import com.pelonot.ui.theme.readableText
import com.pelonot.ui.theme.spacing
import kotlin.math.roundToInt

/**
 * How much, and how often (PLAN 16.3.2 and 16.3.5).
 *
 * The second screen in the 16.3 family, and it settles the question 16.3.1 left
 * open by answering it the same way: *Your FTP* is about the rider, this is
 * about the **riding**, and each screen is named after its one subject rather
 * than gathered under "Trends".
 *
 * Volume and consistency live together because neither is worth much alone. Two
 * hundred minutes in a week is a different training week depending on whether it
 * was one long ride or five short ones, and a run of ride days says nothing
 * about how hard any of them were. The bars are the first question and the
 * calendar is the second.
 *
 * Three decisions carry through both:
 *
 * **The current week is drawn as unfinished**, hollow rather than filled, and
 * never counted in the scale. A Monday with one ride on it is not a bad week
 * yet, and a chart that says so is one the rider learns to discount.
 *
 * **A week with no riding is a bar of nothing, not a missing bar.** The gap is
 * the information — it is the fortnight off, drawn at the width of a fortnight.
 *
 * **A day that has not happened is absent, not empty.** Nothing about Thursday
 * is drawn on a Wednesday.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RidingScreen(
    history: RidingHistory,
    intensity: RidingIntensity,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Your riding") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { padding ->
        // 22.4.3, and the owner's rule of 4 August: this screen is two charts
        // and a sentence, and a chart is not a paragraph. Uncapped, with the
        // prose keeping the measure for itself.
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MaterialTheme.spacing.large)
        ) {
            if (!history.hasAnything) {
                // Nothing to draw and nothing to apologise for. An empty chart
                // with four months of zero bars in it says the rider has been
                // slacking since April.
                Text(
                    text = "No rides yet. Once you have ridden, this is where " +
                        "your weeks and your ride days appear.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.readableText()
                )
                return@Column
            }

            ThisWeek(history)

            Spacer(Modifier.size(MaterialTheme.spacing.large))

            // Volume and consistency beside each other where there is room:
            // they are the two halves of one question (16.3.2 / 16.3.5), and a
            // rider comparing them should not have to scroll between them.
            BoxWithConstraints {
                if (maxWidth >= TWO_CARD_BREAKPOINT) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(
                            MaterialTheme.spacing.large
                        )
                    ) {
                        WeeklyVolumeCard(history, Modifier.weight(1f))
                        RideDaysCard(history, Modifier.weight(1f))
                    }
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(
                            MaterialTheme.spacing.large
                        )
                    ) {
                        WeeklyVolumeCard(history, Modifier.fillMaxWidth())
                        RideDaysCard(history, Modifier.fillMaxWidth())
                    }
                }
            }

            // The third question, under the two it completes (21.4.3). Nothing
            // is drawn for a window with no riding in it: the sentence at the
            // top of this screen has already said *0 rides in the last 30 days*
            // and a card repeating it in a second voice is Phase 26's failure
            // case, not a state.
            if (intensity.ridesInWindow > 0) {
                Spacer(Modifier.size(MaterialTheme.spacing.large))
                // 22.6: one card with nothing beside it is capped, because a
                // single card stretched across 1,280 dp of tablet does not look
                // good — and unlike the pair above, this one has no partner to
                // share the row with.
                RidingIntensityCard(intensity, Modifier.loneCard())
            }

            Spacer(Modifier.size(MaterialTheme.spacing.extraLarge))
        }
    }
}

/**
 * The last 30 days, said in words before anything is drawn.
 *
 * A sentence answers *have I been riding* faster than a chart whose right-hand
 * end the rider has to find first.
 *
 * **It said "this week" until 22.5.** On the owner's stated assumption — the
 * bike gets ridden at most once a week — the first thing on this screen was a
 * 0 for six days out of seven, and the streak beneath it counted days, so the
 * rider who has never missed a Sunday scored 1 and was told nothing at all.
 * The window below matches the dashboard card that opens this screen; the bars
 * and the calendar underneath are still weekly, and are still right, because a
 * calendar with one square lit a week is a good picture of a once-a-week rider
 * rather than a bad one (16.3.5).
 */
@Composable
private fun ThisWeek(history: RidingHistory) {
    val recent = history.recent

    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = "${recent.rides}",
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.size(MaterialTheme.spacing.small))
        Text(
            text = if (recent.rides == 1) {
                "ride in the last ${recent.days} days"
            } else {
                "rides in the last ${recent.days} days"
            },
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )
    }

    val detail = buildList {
        if (recent.rides > 0) {
            val minutes = recent.minutes
            add("$minutes ${if (minutes == 1) "minute" else "minutes"}, " +
                "${recent.outputKj.roundToInt()} kJ")
        }
        // A streak is only worth saying once it is one. "1 week" is a ride, and
        // calling it a streak is the kind of encouragement that reads as
        // flattery — which is how a rider stops believing the other numbers.
        if (history.streakWeeks >= 2) add("${history.streakWeeks} weeks in a row")
    }.joinToString(" · ")

    if (detail.isNotEmpty()) {
        Text(
            text = detail,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Weekly volume and output (16.3.2), as two rows of bars on one set of weeks.
 *
 * **Two charts, not one with two axes.** Minutes and kilojoules are different
 * quantities and a second vertical axis can be scaled to make any two series
 * agree or disagree — which is a claim about how the rider is training, made by
 * the drawing rather than by the data. Stacked, sharing a column per week, the
 * comparison is still there to be read and nothing has been asserted.
 */
@Composable
private fun WeeklyVolumeCard(history: RidingHistory, modifier: Modifier = Modifier) {
    val minutesPeak = maxOf(history.busiestFinishedMinutes, 1)
    val outputPeak = maxOf(history.busiestFinishedOutputKj, 1.0)

    RidingTrendCard(
        modifier = modifier,
        title = "Every week",
        summary = "${history.totalRides} rides across " +
            "${history.weeks.size} weeks, in ${history.weeksRiddenIn} of which " +
            "there was riding. Busiest finished week: $minutesPeak minutes."
    ) {
        BarRow(
            label = "Minutes",
            peakLabel = "$minutesPeak min",
            weeks = history.weeks,
            value = { it.minutes.toDouble() },
            peak = minutesPeak.toDouble(),
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.size(MaterialTheme.spacing.large))
        BarRow(
            label = "Output",
            peakLabel = "${outputPeak.roundToInt()} kJ",
            weeks = history.weeks,
            value = { it.outputKj },
            peak = outputPeak,
            color = MaterialTheme.colorScheme.tertiary
        )

        Spacer(Modifier.size(MaterialTheme.spacing.small))
        Row(Modifier.fillMaxWidth()) {
            AxisText(monthLabel(history.weeks.first().startMs))
            Spacer(Modifier.weight(1f))
            AxisText("this week")
        }
    }
}

@Composable
private fun BarRow(
    label: String,
    peakLabel: String,
    weeks: List<RidingWeek>,
    value: (RidingWeek) -> Double,
    peak: Double,
    color: Color
) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = peakLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Spacer(Modifier.size(MaterialTheme.spacing.extraSmall))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(BAR_HEIGHT),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        weeks.forEach { week ->
            val fraction = (value(week) / peak).toFloat().coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    // The whole column is a slot, so a week with nothing in it
                    // still occupies its width: the gap is the information.
                    .background(
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                        MaterialTheme.expressiveShapes.small
                    ),
                contentAlignment = Alignment.BottomCenter
            ) {
                if (fraction > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            // A week with a single short ride still shows: a
                            // sliver is "a little", and nothing is "none".
                            .fillMaxHeight(fraction.coerceAtLeast(0.03f))
                            .clip(MaterialTheme.expressiveShapes.small)
                            .background(
                                // The week in progress is hollow, because it is
                                // not a result yet — it can only grow.
                                if (week.isPartial) color.copy(alpha = 0.35f) else color
                            )
                    )
                }
            }
        }
    }
}

/**
 * How hard the last thirty days were (21.4.3).
 *
 * **The third question this screen answers, and the first one that can change
 * what a rider does next.** The bars above say how much and the squares say how
 * often; a month of the same volume is a different month depending on whether it
 * was ridden easy or ridden at threshold, and until now nothing in the app said
 * which.
 *
 * The same stacked bar ride detail draws for one ride, over a window of them —
 * deliberately the same component and the same colours, because a rider who has
 * learned to read one has learned to read the other, and two drawings of one
 * scale is how two screens come to disagree about what Z4 looks like.
 *
 * **The title is the plain words and the caption is the precise ones.** *Easy
 * and hard* is what the card is for; `Z1–Z2` and `Z4 and above` are in the
 * sentence, where Phase 26 allows the jargon because a chart caption is
 * somewhere a measurement is genuinely being read. And there is no target
 * anywhere on it (21.4.4): the well-known 80-20 would turn every one of these
 * sentences into a mark out of ten.
 */
@Composable
private fun RidingIntensityCard(
    intensity: RidingIntensity,
    modifier: Modifier = Modifier
) = ChartCard(
    title = "Easy and hard",
    caption = RidingIntensitySummary.caption(intensity),
    summary = RidingIntensitySummary.mix(intensity),
    modifier = modifier
) {
    // A window with rides in it but nothing countable draws no bar at all: the
    // sentence says why, and an empty pill would be a chart of nothing that
    // still looks like a chart of something.
    if (intensity.hasAnything) {
        TimeInZoneBar(timeInZone = intensity.timeInZone)
    }
}

/**
 * The calendar (16.3.5): a square per day, a column per week.
 *
 * The cheapest chart in the plan and the one most likely to get somebody on the
 * bike, because it is the only one that shows the shape of a habit rather than
 * the size of an effort. Intensity is **minutes**, not output — a rider on an
 * easy week has not had a fainter day, they have had a shorter one.
 */
private val BAR_HEIGHT = 90.dp

/** Below this the two trend cards' charts get too narrow to read; they stack. */
private val TWO_CARD_BREAKPOINT = 900.dp
