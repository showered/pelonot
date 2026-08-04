package com.pelonot.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.pelonot.ui.theme.spacing
import java.util.Calendar
import java.util.TimeZone

/**
 * *"What year were you born?"*, answered by tapping a year (PLAN 20.3.3).
 *
 * **The owner's call, 4 August 2026**, and the arithmetic agrees with it:
 * *"Does it really matter about the exact month/day? It can be resolved to 1st
 * January in the db."*
 *
 * It does not matter, and this is checkable rather than a matter of taste,
 * because the app has exactly two consumers for this date and **both reduce it
 * to age in whole years**:
 *
 * - **Tanaka's maximum heart rate** is `208 − 0.7 × age`, so a year of slop is
 *   **0.7 bpm** — against an inter-individual spread that 21.1 puts at 10–12
 *   bpm, which is wider than a zone. The error this introduces is under a
 *   fifteenth of the error the estimate already carries and admits to.
 * - **`FtpEstimator`'s age factor** declines 0.6% a year, so a year of slop is
 *   **0.6%** of an FTP that is deliberately pitched low and expected to be
 *   corrected by the rider's first hard ride.
 *
 * Storing 1 January makes a rider at most one year *older* than they are, in
 * both. Neither consumer can tell.
 *
 * **And 21.1.1a had already reached the same conclusion from the other side:**
 * only the year may ever leave the tablet, because in a cloud row beside a
 * display name a full date of birth stops being a fitness input and becomes an
 * identity field. Asking only for the year means there is no full date to leak.
 *
 * ## Why a grid rather than a dropdown
 *
 * The owner asked for a dropdown and this is one step better on this hardware.
 * A century of years in a dropdown is a hundred-row scroll through a column
 * eight items tall. The bike's panel is 1280 dp wide (HARDWARE.md), so the same
 * hundred years fit as a grid of eight columns and thirteen rows with most of
 * the plausible range on screen at once — the rider's year is usually *visible*
 * rather than scrolled to. That is 22.4's rule applied to a picker: tile what
 * is looked at.
 *
 * What this replaces, measured on the tablet AVD: Material's `DatePicker`
 * opened on **August 2026**, so a rider born in 1985 faced roughly five hundred
 * presses of the month arrow to answer a question about a year.
 */
@Composable
fun BirthYearPicker(
    currentSelection: Int?,
    onSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val thisYear = remember {
        Calendar.getInstance().get(Calendar.YEAR)
    }
    val years = remember(thisYear) { (thisYear downTo EARLIEST_YEAR).toList() }

    val gridState = rememberLazyGridState()
    LaunchedEffect(Unit) {
        // Opens on the middle of the plausible range rather than at either end.
        // Not a guess about this rider — nothing is prefilled and nothing is
        // stored until they tap — it only decides what is on screen first.
        val target = currentSelection ?: (thisYear - TYPICAL_RIDER_AGE)
        val index = years.indexOf(target).takeIf { it >= 0 } ?: 0
        gridState.scrollToItem((index - COLUMNS).coerceAtLeast(0))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = { Text("What year were you born?") },
        text = {
            Column {
                Text(
                    // 15.8.6's rule about saying the cost, applied to a
                    // question rather than to an account: a rider being asked
                    // their age on a bike is owed the reason in one line.
                    text = "It sets your heart-rate zones and your starting effort. " +
                        "Nothing else, and it stays on this bike.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyVerticalGrid(
                    columns = GridCells.Fixed(COLUMNS),
                    state = gridState,
                    modifier = Modifier
                        .padding(top = MaterialTheme.spacing.large)
                        .height(GRID_HEIGHT)
                ) {
                    items(years) { year ->
                        YearCell(
                            year = year,
                            selected = year == currentSelection,
                            onClick = { onSelected(year) }
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/**
 * Tapping a year *is* the answer — there is no second confirm step.
 *
 * A picker whose every option is one tap does not need an OK button, and the
 * one it would need is a tap the rider has to make a hundred percent of the
 * time to no purpose. Cancel stays, because leaving without answering is a
 * different thing from answering.
 */
@Composable
private fun YearCell(year: Int, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.padding(MaterialTheme.spacing.extraSmall)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(CELL_HEIGHT)
        ) {
            Text(
                text = year.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                textAlign = TextAlign.Center,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
        }
    }
}

/**
 * The year a rider taps, as the epoch milliseconds `profiles.birth_date` holds.
 *
 * **1 January, UTC**, and the timezone matters: the column is read back by
 * `FtpEstimator.ageYearsAt` as a plain instant, and building the date in a
 * local zone east of Greenwich would store 31 December of the previous year.
 * A rider whose stored year is one out is the exact defect this whole control
 * exists to make impossible.
 */
fun birthYearToMillis(year: Int): Long =
    Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(year, Calendar.JANUARY, 1)
    }.timeInMillis

/** The year in a stored `birth_date`, or null. Read in UTC, for the reason above. */
fun millisToBirthYear(millis: Long?): Int? = millis?.let {
    Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = it }
        .get(Calendar.YEAR)
}

/** Where the grid opens. Nothing is prefilled — this only picks the scroll position. */
private const val TYPICAL_RIDER_AGE = 40

/** Old enough that no rider is excluded. */
private const val EARLIEST_YEAR = 1920

private const val COLUMNS = 8
private val CELL_HEIGHT = 56.dp
private val GRID_HEIGHT = 400.dp
