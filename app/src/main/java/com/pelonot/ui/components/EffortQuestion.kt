package com.pelonot.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pelonot.domain.model.PerceivedEffort
import com.pelonot.ui.theme.expressiveShapes
import com.pelonot.ui.theme.spacing

/**
 * How hard that felt — three answers, not ten (26.3, the owner's note).
 *
 * **Shared by the post-ride summary and ride detail (12.7.2).** It was the same
 * three buttons written out twice, and the two copies had already drifted four
 * ways: a card against a bare column, two titles, a subtitle on one of them, and
 * two private button-height constants with a comment on one saying it matched
 * the other. `RideFigures` (12.2.2) and `RideChartsSection` (12.6.1) were
 * extracted for the same reason and this is the third of the three cards both
 * screens draw.
 *
 * Three wide buttons rather than ten small ones is what lets each carry a line
 * saying what it means, which is what makes them answerable: *comfortable*
 * against *a good workout* is a real distinction a rider can make in a second,
 * where 6 against 7 is not. See [PerceivedEffort] for why the stored column is
 * still 1–10 — a ride rated on the old ten-point scale reads back as one of the
 * three without anything having been rewritten on disk.
 *
 * **The tense is the only thing that differs between the two screens, and it is
 * derived here rather than passed in as words.** [isTonight] asks *did that
 * feel* on a ride the rider has just finished; every other reading of the same
 * ride asks *did it feel*, and says whether the question was ever answered,
 * because on that screen this is a correction rather than a question. Keeping
 * both sets of words in one file is the point of the extraction.
 *
 * **Where it goes is 12.7.1 and belongs with it**: directly under the ride's
 * figures on both screens, above the charts. A chart is the tallest thing either
 * screen draws, so a question appended after one is below the fold by
 * construction — which is what it was on ride detail, roughly 2,000 dp down,
 * telling a rider who could not see it that they could still answer.
 */
@Composable
fun EffortQuestion(
    selected: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    isTonight: Boolean = false
) {
    val chosen = PerceivedEffort.of(selected)

    Card(
        modifier = modifier,
        shape = MaterialTheme.expressiveShapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        // Filled to whatever the board beside it needs (24.1.8) with the
        // question still at the top, so *"How did that feel?"* and *"On this
        // bike"* sit on the same line. Centring it was tried first and reads
        // worse: the two headings then disagree by a hundred dp and the card
        // looks like it is missing something above the question. A no-op on
        // ride detail, where nothing is beside it.
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(MaterialTheme.spacing.large)
        ) {
            Text(
                text = if (isTonight) "How did that feel?" else "How did it feel?",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() }
            )
            if (!isTonight) {
                Text(
                    text = if (selected == null) {
                        "You didn't answer for this one — you still can"
                    } else {
                        "Tap a different answer to change it"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.size(MaterialTheme.spacing.medium))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)
            ) {
                PerceivedEffort.entries.forEach { effort ->
                    EffortButton(
                        effort = effort,
                        isSelected = chosen == effort,
                        onSelect = { onSelect(effort.rating) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/**
 * One of the three answers.
 *
 * The detail line is inside the button rather than beside it, because the
 * button is what a rider is choosing between and a label they have to look
 * away from to understand is not a label.
 */
@Composable
private fun EffortButton(
    effort: PerceivedEffort,
    isSelected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilledTonalButton(
        onClick = onSelect,
        modifier = modifier
            .sizeIn(minHeight = EFFORT_BUTTON_HEIGHT)
            .semantics {
                contentDescription = "${effort.label}. ${effort.detail}"
            },
        shape = MaterialTheme.expressiveShapes.pill,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        colors = if (isSelected) {
            ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        } else {
            ButtonDefaults.filledTonalButtonColors()
        }
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = effort.label,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Text(
                text = effort.detail,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = LocalContentColor.current.copy(alpha = 0.75f)
            )
        }
    }
}

/** Two lines of text and a comfortable target for someone out of breath. */
private val EFFORT_BUTTON_HEIGHT = 72.dp
