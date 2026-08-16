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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
 * 2. **Never beside the FTP as if they were the same kind of thing** (26.4.5),
 *    **and the owner has narrowed this one rather than lifted it** (26.4.8).
 *    The FTP is a measurement with two screens of its own; this is an
 *    accumulation. Putting them in one row invites the reading that a higher
 *    level is a fitter rider, which is the one thing it must never say — and
 *    the owner's own note is the best statement of why: *"someone could be lvl
 *    20 but only 50 FTP, so not a very good rider"* (20.6.7). Since 20.6.5 the
 *    **profile selector** draws both about one rider: the badge on the face,
 *    the FTP as a caption under the name, in different sizes on different rows
 *    with the name between them. That is the shape the rule was always about —
 *    a *row* presenting them as two readings of one instrument — and the tile
 *    does not do it.
 *
 *    **The live leaderboard is the second and last screen** (24.3.19b), in the
 *    same shape and for a different reason: an FTP beside a housemate's name
 *    publishes a measurement of somebody who was never asked, and a board is
 *    the one social surface every row has opted into being ranked on —
 *    `household_visible` is that opt-in. **Nothing else may follow either.**
 *    Not the greeting, not the household panel, not the static class board,
 *    not the overlay.
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
 * a computed one nobody reads. **The compact form does not draw it** — it sits
 * on a face whose ring is the same progress at a legible size (20.6.8).
 *
 * **17.15.2 is the catch and it is unfixed.** Nothing keeps `web/tokens.css`
 * and `Color.kt` in step, so this badge does not exist on the companion web app
 * until somebody transcribes it. See 26.4.6.
 */
@Composable
fun RiderScore(
    level: RiderLevel,
    modifier: Modifier = Modifier,
    /**
     * The size to draw at when this badge is riding on a rider's face
     * (20.6.4), or null for the ordinary pill beside a name. It is the height
     * the contents are scaled *from* rather than a fixed height imposed on
     * them, so a rider who has turned the system font up gets a taller badge
     * instead of a clipped one.
     *
     * **A parameter rather than a second component**, which is the whole point
     * of this file: the profile tile now overlays the badge on the avatar and
     * two other screens draw it beside the name, and a private copy in the
     * selector is exactly how the avatar itself came to be on the power-zone
     * palette. Everything the four rules say is true of both forms — same
     * words, same colour, same track, same guest rule — and only the type
     * scale and the padding follow the height.
     */
    compact: Dp? = null
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
                // **Not in compact form**, and that is a rule rather than a
                // saving. A 2 dp track on a 22 dp pill is a hairline nobody can
                // read — and every compact badge there is sits inside
                // `RiderAvatar`'s progress *ring* (20.6.8), which is the same
                // number drawn at a size that can be. Two indicators for one
                // value is two answers to one question.
                if (compact != null || level.progress <= 0f) return@drawBehind
                drawRect(
                    color = track,
                    topLeft = Offset(0f, size.height - trackPx),
                    size = Size(size.width * level.progress, trackPx)
                )
            }
            // A compact badge keeps the *proportion* of the minimum width, so
            // `LVL 8` and `LVL 12` are still one object down a row of tiles —
            // the reason [MIN_WIDTH] exists — without a 64 dp pill hanging off
            // the sides of a 66 dp face.
            .defaultMinSize(minWidth = compact?.times(MIN_WIDTH_RATIO) ?: MIN_WIDTH)
            .semantics { contentDescription = describe(level) },
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (compact != null) {
                    MaterialTheme.spacing.small
                } else {
                    MaterialTheme.spacing.medium
                },
                vertical = MaterialTheme.spacing.extraSmall
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "LVL",
                style = MaterialTheme.typography.labelSmall,
                fontSize = compact?.let { (it.value * LABEL_SCALE).sp } ?: TextUnit.Unspecified,
                lineHeight = compact?.let { (it.value * LABEL_SCALE * 1.2f).sp }
                    ?: TextUnit.Unspecified,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
            Spacer(Modifier.width(MaterialTheme.spacing.extraSmall))
            Text(
                text = level.level.toString(),
                style = MaterialTheme.typography.titleSmall,
                fontSize = compact?.let { (it.value * NUMBER_SCALE).sp } ?: TextUnit.Unspecified,
                lineHeight = compact?.let { (it.value * NUMBER_SCALE * 1.2f).sp }
                    ?: TextUnit.Unspecified,
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

/**
 * The same rule as [MIN_WIDTH], expressed against the height so a compact
 * badge keeps its shape at whatever size the face it rides on happens to be.
 */
private const val MIN_WIDTH_RATIO = 2.4f

/** The number, and then the word, as fractions of a compact badge's height. */
private const val NUMBER_SCALE = 0.44f
private const val LABEL_SCALE = 0.30f

private val TRACK = 2.dp
