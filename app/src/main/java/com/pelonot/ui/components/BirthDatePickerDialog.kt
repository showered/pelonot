package com.pelonot.ui.components

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.util.Calendar

/**
 * A date picker that opens somewhere useful for a **birthday** (PLAN 20.3.3).
 *
 * Material's `DatePicker` opens on today and offers every year its default
 * range allows, which is right for "when is your next appointment" and wrong
 * for the only date this app ever asks anyone for. Measured on the tablet AVD
 * while driving the new profile flow: the calendar opened on *August 2026*, so
 * a rider born in 1985 faced roughly five hundred presses of the month arrow.
 * The year dropdown is the way out and nothing on the screen says so.
 *
 * Three changes, all of them about the same fact — this date is decades ago and
 * cannot be in the future:
 *
 * - **It opens forty years back**, so the year list opens near the middle of
 *   the plausible range rather than at its very end.
 * - **The year range stops at this year**, because a birthday in 2027 is a
 *   mistyped year rather than a rider.
 * - **A future date cannot be selected at all.** `FtpEstimator.ageYearsAt`
 *   already refuses one, but refusing it *after* the rider has chosen it means
 *   the age adjustment silently disappears with nothing on screen to say why.
 *   Reject at the point of entry, not downstream — the same argument as the
 *   telemetry fence, one screen along.
 *
 * Shared rather than copied because Settings asks the same question (21.1.1)
 * and had the same defect. Two pickers for one question is how they drift.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BirthDatePickerDialog(
    currentSelection: Long?,
    onSelected: (Long?) -> Unit,
    onDismiss: () -> Unit,
    title: String = "When were you born?"
) {
    val now = remember { System.currentTimeMillis() }
    val thisYear = remember {
        Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.YEAR)
    }
    val openAt = remember(currentSelection) {
        currentSelection ?: Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.YEAR, -TYPICAL_RIDER_AGE)
        }.timeInMillis
    }

    val state = rememberDatePickerState(
        initialSelectedDateMillis = currentSelection,
        initialDisplayedMonthMillis = openAt,
        yearRange = EARLIEST_YEAR..thisYear,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= now
            override fun isSelectableYear(year: Int) = year <= thisYear
        }
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onSelected(state.selectedDateMillis)
                onDismiss()
            }) { Text("Use this date") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    ) {
        DatePicker(state = state, title = { Text(title) })
    }
}

/**
 * Where the calendar opens when the rider has not chosen yet.
 *
 * Not a guess about *this* rider — nothing is stored and nothing is prefilled.
 * It only decides which page of a scrolling year list is on screen first, and
 * the middle of the range beats either end.
 */
private const val TYPICAL_RIDER_AGE = 40

/** Old enough that no rider is excluded; recent enough that the list is usable. */
private const val EARLIEST_YEAR = 1920
