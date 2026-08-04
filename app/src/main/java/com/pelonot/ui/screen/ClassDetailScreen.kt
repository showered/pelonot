package com.pelonot.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pelonot.R
import com.pelonot.core.Formatters
import com.pelonot.data.repository.ClassPlan
import com.pelonot.domain.chart.ClassProfile
import com.pelonot.domain.model.ClassLeaderboard
import com.pelonot.domain.model.Interval
import com.pelonot.domain.model.RideIntent
import com.pelonot.domain.model.targetPowerRange
import com.pelonot.domain.social.ClassRival
import com.pelonot.ui.components.ClassLeaderboardCard
import com.pelonot.ui.components.ClassProfileChart
import com.pelonot.ui.components.PositionChip
import com.pelonot.ui.theme.WideGrid
import com.pelonot.ui.theme.color
import com.pelonot.ui.theme.expressiveShapes
import com.pelonot.ui.theme.loneCard
import com.pelonot.ui.theme.readableText
import com.pelonot.ui.theme.spacing

/**
 * Interval breakdown for a class.
 *
 * The interval list was always empty before this change: the screen declared
 * its own `Interval` type with camelCase field names and decoded the asset
 * JSON — which is snake_case, and describes segments by start/end timestamp
 * rather than duration — inside a `try/catch` that returned `emptyList()`.
 * Parsing now lives in [com.pelonot.domain.model.IntervalParser] against the
 * real format.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassDetailScreen(
    plan: ClassPlan?,
    ftp: Double,
    onBack: () -> Unit,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Who on this bike has ridden this class (24.1.2). Null while it is being
     * read, and drawn as nothing when there is nothing worth drawing.
     */
    leaderboard: ClassLeaderboard? = null,
    /**
     * Rides of this class that can be raced live (24.3.3). Empty is the
     * ordinary answer and draws nothing at all.
     */
    rivals: List<ClassRival> = emptyList(),
    /** Which of [rivals] is selected, or null for nobody. */
    selectedRivalId: String? = null,
    onPickRival: (String?) -> Unit = {}
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(plan?.title ?: "Class") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        if (plan == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "This class is no longer available.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Scaffold
        }

        val profile = remember(plan.intervals) { ClassProfile.of(plan.intervals) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(
                    start = MaterialTheme.spacing.large,
                    end = MaterialTheme.spacing.large,
                    bottom = MaterialTheme.spacing.large
                ),
                // Centred when it does not fill the panel, which is 22.7.1's
                // rule arriving on a third screen: most classes are seven or
                // eight blocks and leave a third of a 720 dp tablet empty, so
                // top-aligning them hangs the whole screen off the app bar with
                // a hole above the Start button. A long class overflows and
                // scrolls exactly as before.
                verticalArrangement = Arrangement.spacedBy(
                    MaterialTheme.spacing.large,
                    Alignment.CenterVertically
                )
            ) {
                // How long, how hard, what shape — one line, and the profile
                // under it says the same thing without a word (22.7.2). The
                // interval count is gone from here: the picture shows every
                // block and the list below names them, which is three answers
                // to one question (Phase 26).
                item {
                    Text(
                        text = listOfNotNull(
                            if (profile.blocks.isEmpty()) {
                                Formatters.minutes(plan.durationSec)
                            } else {
                                profile.minutesLabel
                            },
                            plan.category,
                            profile.shape
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        // A sentence is read, so it is capped where it stands
                        // rather than centred over left-aligned content.
                        modifier = Modifier.readableText()
                    )
                }

                if (plan.intervals.isEmpty()) {
                    item {
                        Text(
                            text = "This class has no readable interval data, so targets " +
                                "cannot be shown. You can still ride it as a free ride.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.readableText()
                        )
                    }
                } else {
                    // The one thing here that is looked at rather than read, so
                    // it takes the panel: time is the horizontal axis and a
                    // 30-minute class capped at 760 dp loses the proportion
                    // between the work and the recoveries (22.4).
                    item { ClassProfileChart(profile = profile) }
                }

                // Above the interval list rather than below it: this is the
                // screen where a rider is choosing what to ride, and "your
                // housemate did 214 kJ on this one" is the reason to pick it.
                // The interval breakdown is what they read once they already
                // have. A card with nothing beside it is capped (22.6).
                leaderboard?.let {
                    item { ClassLeaderboardCard(leaderboard = it, modifier = Modifier.loneCard()) }
                }

                // 24.3.3. Directly under the board, because the board is what
                // makes the offer make sense — a rider looking at "Kilo did
                // 240 kJ on this one" is exactly the rider who might want to
                // race it. Chosen here rather than mid-ride: a menu over
                // somebody who is already pedalling is 15.1.6's rule.
                if (rivals.isNotEmpty()) {
                    item {
                        RivalPicker(
                            rivals = rivals,
                            selectedId = selectedRivalId,
                            onPick = onPickRival,
                            // Order matters and cost a screenshot to find:
                            // `loneCard` only caps, so without a fill the
                            // card sizes to its chips and sits half the width
                            // of the board above it — and with the fill
                            // *first* the cap cannot shrink it back, so it
                            // spans the whole 1280 dp panel instead (22.6).
                            // Cap outermost, fill inside it.
                            modifier = Modifier.loneCard().fillMaxWidth()
                        )
                    }
                }

                if (plan.intervals.isNotEmpty()) {
                    // Tiles, not a stack of full-width rows. Each row used to
                    // carry four facts down its left edge with 1200 dp of empty
                    // panel beside them, and the seventh block of a 30-minute
                    // class fell below the fold — on the one screen whose job
                    // is to show the whole class (22.6, 22.4).
                    item {
                        WideGrid(
                            items = plan.intervals,
                            minCellWidth = 300.dp,
                            spacing = MaterialTheme.spacing.small,
                            // One tile carrying a position chip is taller than
                            // its neighbours, and a row of four where the
                            // fourth is 20 dp taller reads as a mistake.
                            equalHeightRows = true
                        ) { interval ->
                            IntervalCard(interval = interval, ftp = ftp)
                        }
                    }
                }
            }

            // One control, not a card the width of the room. It keeps the
            // height it had — this is a button pressed by somebody already
            // clipped in — and loses only the 1100 dp of pill either side.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(MaterialTheme.spacing.large),
                contentAlignment = Alignment.Center
            ) {
                Button(
                    onClick = onStart,
                    modifier = Modifier
                        .width(START_BUTTON_WIDTH)
                        .height(56.dp),
                    shape = MaterialTheme.expressiveShapes.pill
                ) {
                    Text("Start class")
                }
            }
        }
    }
}

/**
 * Wide enough to be the obvious thing on the screen, narrow enough not to be a
 * band across it. Judged on the tablet AVD at 1280 dp, which is the only place
 * this question has an answer (22.4.5).
 */
private val START_BUTTON_WIDTH = 420.dp

/**
 * "Ride against" — who this class can be raced live (24.3.3).
 *
 * Opt-in per tap and **nobody selected by default**, which is the same rule
 * 24.3.1 settled for the ride-detail chart: a rider who has not asked for a
 * comparison must not be given one. Tapping the selected chip again clears it.
 *
 * Drawn only when there is somebody, so a rider with no measured rides of this
 * class — and that is most riders, most of the time — sees nothing rather than
 * an empty offer (24.1.6).
 */
@Composable
private fun RivalPicker(
    rivals: List<ClassRival>,
    selectedId: String?,
    onPick: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.expressiveShapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(MaterialTheme.spacing.large),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)
        ) {
            Text(
                text = "Ride against",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                // Phase 26: says what it does, once, without naming a unit
                // the rider is not being asked to read.
                text = "Their pace shows on your ride screen as you go.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)
            ) {
                rivals.forEach { rival ->
                    val on = selectedId == rival.workoutId
                    FilterChip(
                        selected = on,
                        onClick = { onPick(if (on) null else rival.workoutId) },
                        label = {
                            Text("${rival.name} · ${Formatters.kilojoules(rival.outputKj)}")
                        },
                        modifier = Modifier.semantics {
                            contentDescription = when {
                                on -> "Don't race ${rival.name}"
                                rival.you -> "Race your own best ride of this class"
                                else -> "Race ${rival.name}'s ride of this class"
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun IntervalCard(interval: Interval, ftp: Double) {
    val zone = interval.powerZone
    val powerRange = zone.targetPowerRange(ftp, RideIntent.DEFAULT)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .semantics {
                contentDescription = "Zone ${zone.number}, ${zone.displayName}, " +
                    "${Formatters.duration(interval.durationSec)}, " +
                    "cadence ${interval.cadenceMin} to ${interval.cadenceMax} RPM" +
                    (interval.position?.let { ", ${it.instruction}" } ?: "")
            },
        shape = MaterialTheme.expressiveShapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Colour rail: zone intensity readable at a glance while scanning.
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .height(72.dp)
                    .background(zone.color)
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(MaterialTheme.spacing.medium)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Z${zone.number} · ${zone.displayName}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    // Only when the class asks for one: an absent position is
                    // the rider's choice and drawing "either" for it would turn
                    // silence into a third instruction (PLAN 25.1.1).
                    interval.position?.let { position ->
                        Spacer(Modifier.width(MaterialTheme.spacing.small))
                        PositionChip(position)
                    }
                }
                Text(
                    text = "${interval.cadenceMin}–${interval.cadenceMax} RPM · " +
                        "${powerRange.start.toInt()}–${powerRange.endInclusive.toInt()} W",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = Formatters.duration(interval.durationSec),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = MaterialTheme.spacing.large)
            )
        }
    }
}
