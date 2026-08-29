package com.pelonot.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pelonot.data.repository.StoredAlert
import com.pelonot.ui.components.AlertRow
import com.pelonot.ui.theme.readableColumn
import com.pelonot.ui.theme.readableText
import com.pelonot.ui.theme.spacing

/**
 * Everything the app has ever told this rider about themselves (PLAN 27.4.1).
 *
 * The third screen in the 16.3 family, beside *Your FTP* and *Your riding*, and
 * named after its subject the way both of those are. It is where 27.1.1's table
 * pays for itself: the summary tells a rider **one** thing per ride (27.1.5),
 * so without this screen the other two would be written down and never seen,
 * and there would be no answer at all to *"what did that say?"*.
 *
 * **A column, not a grid.** Each row is a sentence with a figure under it —
 * something read rather than looked at — which is `readableColumn`'s case
 * exactly, and a record book banded across 1280 dp of tablet is harder to read
 * than the same book at 760.
 *
 * **No empty state and no total.** A rider with nothing here does not reach the
 * screen, because the card that opens it is not drawn (22.2.3); and a count at
 * the top would turn a record book into a score, which is the thing Phase 26
 * and 28.1.6 both refuse.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordsScreen(
    alerts: List<StoredAlert>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Your records") },
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
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(padding)
                .padding(horizontal = MaterialTheme.spacing.large)
                .readableColumn()
        ) {
            Text(
                text = "The rides that beat something you had already done.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(vertical = MaterialTheme.spacing.medium)
                    .readableText()
            )

            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                // Keyed on the row id rather than the position, so a new record
                // arriving at the top does not redraw the list underneath it.
                items(alerts, key = { it.id }) { alert ->
                    AlertRow(alert)
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                }
            }
        }
    }
}
