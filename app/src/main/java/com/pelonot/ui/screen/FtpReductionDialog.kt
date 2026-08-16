package com.pelonot.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.pelonot.domain.progress.FtpReduction
import com.pelonot.ui.theme.spacing
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

/**
 * The app offering to lower a rider's FTP, and showing what it read (PLAN
 * 7.11.4).
 *
 * **7.11.4 asked for this not to mirror [FtpBreakthroughDialog], and the reason
 * is not that that dialog is ugly.** Its shortcuts are all in one direction: it
 * says *"Your fitness has improved!"* as a fact, never mentions the twenty
 * minutes it read or that the watts were measured rather than modelled, and
 * offers `Keep Current` against `Update FTP` — two buttons neither of which
 * names a number. A rider handed good news on thin reasoning loses nothing.
 * Reverse the direction and every one of those becomes a defect: this dialog is
 * telling somebody something about their own body that they did not ask about
 * and may not agree with, so it has to be **arguable, not merely declinable.**
 *
 * Three rules follow, and they are the whole design:
 *
 * - **It shows its working.** The three rides are on the face of it, with their
 *   dates and what each one measures, because the rider is the only person who
 *   knows they were ill that week. Disagreeing with the evidence has to be
 *   possible without first agreeing that there is some.
 * - **It states nothing as a verdict.** No *"your fitness has dropped"*: the
 *   claim is about the rides, and the sentence is what they did. The rider's
 *   FTP is a setting that has become wrong for them, which is a smaller and
 *   truer thing to say than that they have got worse.
 * - **Both buttons name their number.** `Keep 200 W` against `Lower to 175 W`,
 *   so neither can be tapped by reflex without having read what it does — which
 *   is 7.10.5's rule about the accept side, applied to a change that is harder
 *   to notice afterwards.
 *
 * **`onKeep` is also the dismiss**, because keeping the current number is the
 * safe direction and a tap outside must resolve to the conservative outcome.
 * It is written down rather than merely closing the dialog (7.10.5) — and here
 * it also restarts the evidence window, so the rider is not asked again off the
 * same three rides after every subsequent ride.
 */
@Composable
fun FtpReductionDialog(
    reduction: FtpReduction,
    onKeep: () -> Unit,
    onAccept: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onKeep,
        title = {
            Text(
                // Not "FTP drop" and not an exclamation mark. What the app
                // actually has is three rides, and that is what it says.
                text = "Your last ${reduction.evidence.size} hard rides",
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)) {
                Text(
                    text = "All ${reduction.evidence.size} came in under your FTP of " +
                        "${reduction.currentFtp} W, and you were working through every " +
                        "one of them. These are watts the bike measured, not an estimate.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall)) {
                    reduction.evidence.forEach { ride ->
                        EvidenceRow(
                            atEpochMs = ride.recordedAt,
                            watts = ride.impliedFtp.roundToInt(),
                            // The one the offer is made from, so the number in
                            // the button can be found on the list.
                            isStrongest = ride.workoutId == reduction.strongestRide.workoutId
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onAccept) {
                Text("Lower to ${reduction.proposedFtp} W")
            }
        },
        dismissButton = {
            TextButton(onClick = onKeep) {
                Text("Keep ${reduction.currentFtp} W")
            }
        }
    )
}

/**
 * One ride of the evidence: when it was, and what it says the rider's FTP is.
 *
 * The strongest of them is the only one marked, and it is marked by weight
 * rather than by colour — amber is this app's off-target signal (11.8.3) and
 * spending it on a rider's best recent effort would say the opposite of what is
 * meant.
 */
@Composable
private fun EvidenceRow(atEpochMs: Long, watts: Int, isStrongest: Boolean) {
    val date = remember(atEpochMs) {
        DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(atEpochMs))
    }
    val weight = if (isStrongest) FontWeight.SemiBold else FontWeight.Normal
    val colour = if (isStrongest) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = date,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = weight,
            color = colour
        )
        Text(
            text = "$watts W",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = weight,
            color = colour
        )
    }
}
