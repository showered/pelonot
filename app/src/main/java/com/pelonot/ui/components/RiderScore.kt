package com.pelonot.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pelonot.domain.progress.RiderLevel
import com.pelonot.ui.theme.expressiveShapes
import com.pelonot.ui.theme.spacing

/**
 * A rider's level, drawn the same way everywhere (26.4.3).
 *
 * The owner's phrase in the note was *"consistently, a design system feature"*,
 * and this file is the whole of the answer to it: one shape, one type scale,
 * one colour, so the greeting, the household panel and anything that comes
 * later cannot each draw it slightly differently. It is the same habit as
 * `readableColumn`, `WideGrid` and `loneCard` — one token, and its KDoc naming
 * the rules.
 *
 * **Four rules, and the first three are the ones a future call site will be
 * tempted to break.**
 *
 * 1. **It says `LVL` and a number, and nothing else.** No unit, no watts, no
 *    "fitness", no adjective. `LVL` is the owner's own word from the note and
 *    it is the one word this badge is allowed (26.4.2) — the number's only
 *    honest claim is *has ridden more*, and any label richer than that is the
 *    reader being handed a meaning the number does not have.
 * 2. **Never beside the FTP as if they were the same kind of thing** (26.4.5).
 *    The FTP is a measurement with two screens of its own; this is an
 *    accumulation. Putting them in one row invites the reading that a higher
 *    level is a fitter rider, which is the one thing it must never say.
 * 3. **Never coloured amber.** Amber is this app's off-target signal (11.8.3),
 *    and a rider's own identity must not wear the colour that means *you are
 *    wrong*. It is the brand's own container colour, which is what the rest of
 *    the app uses for a thing that simply *is*.
 * 4. **It draws for a rider who has never ridden, at level 1, and not at all
 *    for a guest.** Level 1 is the start rather than an achievement, and a
 *    badge that appears out of nowhere after the first ride is a badge nobody
 *    was working towards. A guest is the other case and the caller must not
 *    collapse the two: a guest ride is filed against nobody, so a guest can
 *    never leave level 1 however much they ride, and a badge promising a ladder
 *    that does not exist is worse than no badge. `AppUiState.levelFor` returns
 *    null for exactly that, and it is the same rule as the household panel
 *    having no row for a rider who has not ridden.
 *
 * **The thin track along the bottom is the progress to the next level.** It is
 * the one thing here that is not a word, and it is deliberately unlabelled: a
 * rider does not need to know the arithmetic to see that the bar is nearly
 * full. It is also what keeps `RiderLevel.progress` a drawn number rather than
 * a computed one nobody reads.
 *
 * **17.15.2 is the catch and it is unfixed.** Nothing keeps `web/tokens.css`
 * and `Color.kt` in step, so this badge does not exist on the companion web app
 * until somebody transcribes it. See 26.4.6.
 */
@Composable
fun RiderScore(
    level: RiderLevel,
    modifier: Modifier = Modifier
) {
    val shape = MaterialTheme.expressiveShapes.pill
    val track = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.35f)
    val trackPx = with(LocalDensity.current) { TRACK.toPx() }
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            // The progress track, hairline, along the bottom of the pill.
            //
            // **Drawn rather than laid out**, and that is not a style choice: a
            // `fillMaxWidth(fraction)` child resolves the fraction against the
            // *incoming* constraints, which in a `Row` is the whole row — so the
            // first version of this badge measured 1,100 dp wide on the
            // dashboard and squeezed the household panel's figures into a
            // one-letter-per-line column. Seen immediately on the tablet AVD and
            // invisible in the diff, which is 26.2.2 exactly.
            .drawBehind {
                if (level.progress <= 0f) return@drawBehind
                drawRect(
                    color = track,
                    topLeft = Offset(0f, size.height - trackPx),
                    size = Size(size.width * level.progress, trackPx)
                )
            }
            .defaultMinSize(minWidth = MIN_WIDTH)
            .semantics { contentDescription = describe(level) },
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = MaterialTheme.spacing.medium,
                vertical = MaterialTheme.spacing.extraSmall
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "LVL",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
            Spacer(Modifier.width(MaterialTheme.spacing.extraSmall))
            Text(
                text = level.level.toString(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

/**
 * What a screen reader is told, and it is the one place the badge is allowed
 * more than a word.
 *
 * A sighted rider reads `LVL 8` in context — beside their own name, on their
 * own dashboard — and the context is most of the meaning. A screen reader has
 * no context to lend it, so the honest claim is spelled out rather than left
 * for the listener to supply, which is 26.4.2's risk arriving on the one
 * surface where "less is more" makes it worse rather than better.
 */
private fun describe(level: RiderLevel): String {
    val rides = level.totals.rides
    return if (rides == 0) {
        "Riding level ${level.level}. Earned by riding — your first ride starts it"
    } else {
        "Riding level ${level.level}, earned by $rides ${if (rides == 1) "ride" else "rides"}"
    }
}

/**
 * Wide enough that `LVL 8` and `LVL 12` are the same object rather than two
 * differently-sized ones, which is what a badge in a list of them has to be.
 */
private val MIN_WIDTH = 64.dp

private val TRACK = 2.dp
