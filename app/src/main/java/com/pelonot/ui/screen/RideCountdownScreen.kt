package com.pelonot.ui.screen

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pelonot.data.repository.ClassPlan
import com.pelonot.ui.components.ProgressArc
import com.pelonot.ui.components.attentionBounce
import com.pelonot.ui.theme.color
import com.pelonot.ui.theme.expressiveShapes
import com.pelonot.ui.theme.spacing
import kotlinx.coroutines.delay

/** How long a rider gets between tapping start and the clock running (11.6.13). */
private const val COUNTDOWN_SECONDS = 10

/**
 * How wide the zone explainer is allowed to run (11.8.2).
 *
 * Narrower than `readableWidth`, because this is read at arm's length by
 * somebody already on the bike rather than at a desk, and because it sits under
 * a centred column where a line the width of the panel would not look like part
 * of it.
 */
private val EXPLAINER_WIDTH = 720.dp

/**
 * The ten seconds between "start" and the ride (11.6.13).
 *
 * **The owner asked for this because it felt wrong without it, and the feeling
 * has a cause the record can see.** A class used to begin its first interval
 * and its clock on the same tick as the tap, so the rider was already behind a
 * Z1 target while still reaching for the handlebars — and the opening seconds
 * of every ride on disk are a rider getting onto a bike, filed as riding.
 *
 * So this sits **before** `startWorkout`, not over a ride already running.
 * Nothing is recorded here, no interval is burning and the clock has not
 * started; that is the whole point, and a countdown drawn as a curtain over a
 * live ride would have moved the defect rather than fixed it.
 *
 * Ten seconds rather than five, because the job is feet into cages and a film
 * started. Skippable, because the second ride of the day does not need it and a
 * beat nobody can escape is a worse feeling than the one being fixed — and the
 * hardware back button still works, which during a countdown is a free cancel
 * rather than the mid-ride hazard 11.6.6 has to guard.
 */
@Composable
fun RideCountdownScreen(
    plan: ClassPlan?,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Holds the count where it is (11.6.14). Set while the rider is answering
     * the overlay permission — in the dialog, or away in Android's settings.
     */
    paused: Boolean = false,
    /**
     * Whether to say what a power zone is (11.8.2). True only for a rider who
     * has never finished one.
     */
    explainZones: Boolean = false
) {
    // Survives a rotation without restarting the count — the rider does not get
    // their ten seconds back for turning the tablet.
    var remaining by rememberSaveable { mutableIntStateOf(COUNTDOWN_SECONDS) }
    val start by rememberUpdatedState(onStart)

    // Keyed on the count rather than looping inside one effect, so *Start now*
    // — which simply sets the count to zero — cancels the second already in
    // flight instead of waiting for it to elapse.
    //
    // Keyed on `paused` for the same reason, in the other direction: the second
    // in flight is cancelled rather than allowed to land while the rider is
    // away answering the overlay prompt, and starts whole when they come back.
    // Rounding the rider's way is the right rounding on a beat that exists to
    // let them get onto a bike.
    LaunchedEffect(remaining, paused) {
        if (remaining <= 0) {
            start()
            return@LaunchedEffect
        }
        if (paused) return@LaunchedEffect
        delay(1000)
        remaining -= 1
    }

    val first = plan?.intervals?.firstOrNull()
    val accent by animateColorAsState(
        targetValue = first?.powerZone?.color ?: MaterialTheme.colorScheme.primary,
        animationSpec = spring(stiffness = Spring.StiffnessVeryLow),
        label = "CountdownAccent"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        accent.copy(alpha = 0.16f),
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.background
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = plan?.title ?: "Just Ride",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Get set",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(MaterialTheme.spacing.large))

            ProgressArc(
                // Drains, like the interval arc on the ride screen: what the
                // rider is reading is how much of this is left.
                progress = remaining / COUNTDOWN_SECONDS.toFloat(),
                color = accent,
                strokeWidth = 10.dp,
                modifier = Modifier.size(280.dp)
            ) {
                Text(
                    text = "$remaining",
                    fontSize = 140.sp,
                    lineHeight = 140.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-6).sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    // Each second is its own event, so each one lands.
                    modifier = Modifier.attentionBounce(trigger = remaining, magnitude = 0.12f)
                )
            }

            Spacer(Modifier.height(MaterialTheme.spacing.large))

            // What the rider is about to be asked for. It is the one thing
            // worth knowing before the pedals turn, and there is nowhere else
            // in the flow that says it at this size.
            if (first != null) {
                Text(
                    text = "FIRST UP",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Z${first.powerZone.number} · ${first.powerZone.displayName} · " +
                        "${first.cadenceMin}–${first.cadenceMax} rpm",
                    style = MaterialTheme.typography.titleLarge,
                    color = accent,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            } else {
                Text(
                    text = "No intervals, no targets — ride how you like",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 11.8.2. **What a power zone is, said once, to a rider who has
            // never ridden one.** The owner's note, after watching somebody
            // meet the app for the first time: *"Explain what powerzone is to
            // first time users."*
            //
            // Here rather than anywhere else, and the moment is the argument.
            // A rider in the countdown is clipped in, sitting still, with ten
            // seconds and nothing to do — and the class's first zone is already
            // on the screen above this, so the sentence has something to point
            // at. Every other candidate is worse: profile creation is minutes
            // earlier and about a different number, and mid-ride is a wall of
            // text at somebody who is breathing hard.
            //
            // Two sentences, and no tutorial, no carousel and no modal. It is
            // shown only while `explainZones` is true, which is a query against
            // the rider's own finished rides, so it stops appearing by itself
            // the moment there is one.
            //
            // This is one of Phase 26's stated exceptions: a rider being taught
            // what the number means *is* reading a measurement, so "watts" and
            // "FTP" belong here in a way they do not on a screen where somebody
            // is choosing.
            if (explainZones) {
                Spacer(Modifier.height(MaterialTheme.spacing.large))
                Text(
                    text = "NEW TO THIS?",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(MaterialTheme.spacing.extraSmall))
                Text(
                    text = "A power zone is how hard to push, from Z1 easy to Z7 flat out. " +
                        "The class calls a zone, you turn the resistance up or down until " +
                        "your watts sit inside it — that number is the one to ride to.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = EXPLAINER_WIDTH)
                )
            }

            Spacer(Modifier.height(MaterialTheme.spacing.large))

            OutlinedButton(
                onClick = { remaining = 0 },
                modifier = Modifier
                    .widthIn(min = 280.dp)
                    // Reached for with a foot already clipped in.
                    .heightIn(min = 72.dp),
                shape = MaterialTheme.expressiveShapes.pill
            ) {
                Text(
                    text = "Start now",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}
