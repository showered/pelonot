package com.pelonot.ui.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The *other* answer to a wide screen (PLAN 22.4).
 *
 * `Modifier.readableColumn()` caps a column because 1280 dp of body text is
 * hard to read. That is a fact about a **line**, and it kept being applied as
 * though it were a fact about the **screen** — so every surface that reached
 * for the token quietly chose "cap it" and left 520 dp of tablet empty. In the
 * owner's words: *"just because I don't want one piece of UI to stretch the
 * full width, doesn't mean we can't actually use the full width!"*
 *
 * The rule the two tokens split between them, in one line:
 * **cap what is read at arm's length, tile what is looked at.**
 * Paragraphs, form fields and settings rows are read. Figures, charts, tiles
 * and cards in a set are looked at, and belong here.
 *
 * **Row-major, so the order survives the fold.** Cells fill left to right and
 * then wrap, which means the sequence a rider's eye takes across a wide screen
 * is the same sequence a narrow one gets down its single column. A
 * column-major grid reads in a different order at each width, and there is no
 * width at which both are right.
 *
 * The last row's cells stay the width of every other row's — a trailing gap,
 * not three cells stretched to fill it — because a set of figures where the
 * final tile is twice as wide reads as though that tile matters more.
 *
 * **And the rows are balanced.** Six figures in a grid that fits five wide are
 * two rows of three, never five and a stray. Measured on the tablet AVD, where
 * the first version of this drew exactly that stray and it read as a mistake
 * rather than as a layout — see [balancedColumns].
 */
@Composable
fun <T> WideGrid(
    items: List<T>,
    modifier: Modifier = Modifier,
    minCellWidth: Dp = 220.dp,
    maxColumns: Int = Int.MAX_VALUE,
    spacing: Dp = MaterialTheme.spacing.medium,
    itemContent: @Composable (T) -> Unit
) {
    if (items.isEmpty()) return

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val fits = columnsFor(maxWidth, minCellWidth, spacing, maxColumns)
        val columns = balancedColumns(items.size, fits)

        Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
            items.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
                    row.forEach { item ->
                        Box(Modifier.weight(1f)) { itemContent(item) }
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/**
 * How many cells of at least [minCellWidth] fit in [available], counting the
 * [spacing] between them.
 *
 * Pure, and separate from [WideGrid] because the arithmetic is the part worth
 * being sure of: *n* cells need `n × min + (n − 1) × spacing`, and forgetting
 * the second term is how a grid comes out one column too many and every cell
 * ends up under its minimum.
 *
 * Never returns less than 1 — a screen too narrow for one cell still has to
 * draw it.
 */
fun columnsFor(
    available: Dp,
    minCellWidth: Dp,
    spacing: Dp = 0.dp,
    maxColumns: Int = Int.MAX_VALUE
): Int {
    if (minCellWidth <= 0.dp) return 1
    val fits = ((available + spacing).value / (minCellWidth + spacing).value).toInt()
    return fits.coerceIn(1, maxColumns.coerceAtLeast(1))
}

/**
 * The column count that spreads [itemCount] cells evenly over as few rows as
 * [fits] allows.
 *
 * Six figures in a grid five columns wide is *five and a stray*, and a single
 * tile alone on a second row reads as something having gone wrong rather than
 * as a layout — which is exactly how it read on the tablet AVD. Two rows of
 * three is the same information, the same order and the same number of rows,
 * and looks deliberate.
 *
 * Widening the cells rather than narrowing the grid, so the row still fills the
 * screen: the rows are as few as they can be, and the columns are then as few
 * as those rows need.
 */
fun balancedColumns(itemCount: Int, fits: Int): Int {
    if (itemCount <= 0) return 1
    if (fits <= 1) return 1
    if (itemCount <= fits) return itemCount
    val rows = (itemCount + fits - 1) / fits
    return (itemCount + rows - 1) / rows
}
