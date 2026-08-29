package com.pelonot.domain.alerts

import com.pelonot.core.Formatters

/**
 * How an alert is said, in one place because it is said in two (PLAN 27.3.1,
 * 27.4.1).
 *
 * **The headline carries no unit and usually no number**, which is Phase 26
 * rather than brevity for its own sake. The rule is that a unit belongs where a
 * measurement is being *read*: on the summary the rider is being told something
 * happened, and *"Your best 20 minutes"* — 27.3.1's own example, word for word
 * — says it. The watts belong one screen along, on the list, where a rider has
 * gone specifically to look at numbers; [detail] is that line and it is the
 * only place a `W` or a `kJ` appears in this phase.
 *
 * Pure: the strings are in the app's voice and not in the resource file,
 * matching every other piece of prose this project generates from data.
 */
object AlertWording {

    /**
     * The one line the rider is shown after a ride (27.3.1) — a whole sentence,
     * with its full stop.
     */
    fun headline(alert: RiderAlert): String = "${phrase(alert)}."

    /**
     * The same words without the stop, for the dashboard's card.
     *
     * **Two forms rather than a `trimEnd`**, because the difference is real and
     * a caller should not have to know which one it is holding. On the summary
     * this is a sentence the app says to the rider and it ends; on a card it is
     * a label, sitting under `Your records` beside `Last ride` → `Zone 2
     * Steady` and `Last 30 days` → `13 rides · 340 min`, none of which is
     * punctuated. Seen on the AVD: one card with a full stop among three
     * without is the kind of thing that reads as a mistake before it is read as
     * anything else.
     */
    fun phrase(alert: RiderAlert): String = when (alert.kind) {
        AlertKind.WeeklyStreak -> streakLine(alert.value.toInt())

        AlertKind.ClassOutput ->
            alert.subjectTitle
                ?.let { "Your best ride of $it" }
                ?: "Your best ride of this class"

        AlertKind.PowerWindow ->
            "Your best ${window(alert.subjectKey.toIntOrNull() ?: 0)}"

        AlertKind.RideOutput -> "Your biggest ride yet"

        AlertKind.RideDuration -> "Your longest ride yet"
    }

    /**
     * The figure underneath it, on 27.4's list only.
     *
     * A record says what it beat; a streak has nothing to beat and says
     * nothing, rather than inventing a comparison to keep the shape regular.
     */
    fun detail(alert: RiderAlert): String? {
        val now = amount(alert) ?: return null
        val before = alert.previousValue?.let { amount(alert, it) } ?: return null
        return "$now, up from $before"
    }

    private fun amount(alert: RiderAlert, value: Double = alert.value): String? =
        when (alert.kind) {
            AlertKind.WeeklyStreak -> null
            AlertKind.ClassOutput, AlertKind.RideOutput -> Formatters.kilojoules(value)
            AlertKind.PowerWindow -> Formatters.watts(value)
            AlertKind.RideDuration -> Formatters.duration(value.toInt())
        }

    /**
     * `5 seconds`, `20 minutes`, `1 hour` — the stretch spelled out.
     *
     * Words rather than `1200 s`, because this sentence is read once and not
     * scanned against other numbers; it is the one place in the app where a
     * mean-maximal window is prose.
     */
    private fun window(seconds: Int): String = when {
        seconds >= 3_600 -> Formatters.plural(seconds / 3_600, "hour")
        seconds >= 60 -> Formatters.plural(seconds / 60, "minute")
        else -> Formatters.plural(seconds, "second")
    }

    /**
     * A run of weeks, said the way a person would say it.
     *
     * The long runs get their own sentence rather than a bigger number:
     * *"Twenty-six weeks in a row"* is arithmetic and *"Half a year, every
     * week"* is the thing the rider actually did. Everything shorter keeps the
     * plain form, because a rider four weeks in does not want a flourish.
     */
    private fun streakLine(weeks: Int): String = when {
        weeks >= WEEKS_IN_YEAR && weeks % WEEKS_IN_YEAR == 0 -> {
            val years = weeks / WEEKS_IN_YEAR
            if (years == 1) "A whole year, every week"
            else "${spelled(years)} years, every week"
        }
        weeks == 26 -> "Half a year, every week"
        else -> "${spelled(weeks)} weeks in a row"
    }

    private const val WEEKS_IN_YEAR = 52

    /**
     * Small numbers as words, because this is a sentence rather than a figure.
     *
     * Only as far as the milestones actually reach; anything past them falls
     * back to the digits, which is the right failure — a wrong word is worse
     * than a plain number.
     */
    private fun spelled(value: Int): String = when (value) {
        2 -> "Two"
        3 -> "Three"
        4 -> "Four"
        5 -> "Five"
        8 -> "Eight"
        13 -> "Thirteen"
        else -> value.toString()
    }
}
