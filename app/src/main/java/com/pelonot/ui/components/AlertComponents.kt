package com.pelonot.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pelonot.core.Formatters
import com.pelonot.data.repository.StoredAlert
import com.pelonot.domain.alerts.AlertWording
import com.pelonot.ui.theme.readableText
import com.pelonot.ui.theme.spacing
import java.text.DateFormat
import java.util.Date

/**
 * The one thing a ride earned, said once, above the figures (PLAN 27.3.1).
 *
 * **A line and not a card**, which is 27.3.1 word for word: not a banner, not
 * a trophy, not a modal over the charts. The rider is holding the tablet and
 * breathing hard, and the thing they came to this screen for is underneath it.
 *
 * **Primary and never amber.** Amber means *off target* everywhere else in this
 * app — the governing metric's band on the ride screen — and `RiderScore`'s
 * third rule already refuses it for a rider's identity for exactly this reason.
 * A congratulation wearing the colour of a warning is worse than a plain one.
 */
@Composable
fun RideAlertLine(alert: StoredAlert, modifier: Modifier = Modifier) {
    Text(
        text = AlertWording.headline(alert.alert),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.readableText()
    )
}

/**
 * One row of the record book (PLAN 27.4.1).
 *
 * **This is where the numbers live.** The headline says what happened and
 * carries no unit, because on the summary a rider is being told something; here
 * they have gone to look, so `261 W, up from 240 W` is a measurement being read
 * and the unit belongs (Phase 26's rule, both halves of it).
 */
@Composable
fun AlertRow(alert: StoredAlert, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = MaterialTheme.spacing.small),
        verticalAlignment = Alignment.Top
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = AlertWording.headline(alert.alert),
                style = MaterialTheme.typography.titleMedium
            )
            AlertWording.detail(alert.alert)?.let { detail ->
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        Text(
            text = DATE_FORMAT.format(Date(alert.recordedAt)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = MaterialTheme.spacing.medium)
        )
    }
}

/**
 * `12 Aug` — the date and nothing else.
 *
 * A record is only ever interesting beside the other records, and the year is
 * carried by the ordering; a full timestamp on every row would be the noise
 * 26.1 keeps taking off screens.
 */
private val DATE_FORMAT: DateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM)

/** Kept beside the row it formats, so the two cannot drift. */
internal fun alertCountLabel(count: Int): String = Formatters.plural(count, "record")
