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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.pelonot.R
import com.pelonot.core.Formatters
import com.pelonot.data.repository.ClassPlan
import com.pelonot.domain.chart.ClassProfile
import com.pelonot.domain.model.ClassLeaderboard
import com.pelonot.domain.model.GovernedBy
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

        // 22.7.5a's escape. Nothing is drawn for it until a rider asks.
        var showingBlocks by rememberSaveable { mutableStateOf(false) }
        if (showingBlocks) {
            ClassBlocksSheet(
                intervals = plan.intervals,
                ftp = ftp,
                onDismiss = { showingBlocks = false }
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 22.7.3. **The class on one side, the people on the other.** The
            // owner's note — *"we've added the leaderboard in there and the
            // whole screen doesn't look good"* — and the diagnosis is that the
            // board was stacked *into the description of the class*, between
            // the picture of it and the list of its blocks. It is not a fact
            // about the class; it is a fact about who has ridden it, and on a
            // 1280 dp panel that is a column of its own.
            //
            // What it buys is vertical space, which is the thing this screen
            // was actually short of: with the board in the stack, the interval
            // grid of a 20-minute class was below the fold on the one screen
            // whose job is to show the whole class (22.7.2's own criterion,
            // broken by the card 22.7.2 could not draw).
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                LazyColumn(
                    // `weight` is the *horizontal* share of the Row; without
                    // `fillMaxHeight` this list is only as tall as its own
                    // content, and `Alignment.CenterVertically` below then has
                    // nothing to centre within. It was invisible until 22.7.5a
                    // took the interval grid out — with thirteen tiles in here
                    // the content always overflowed, so the list filled the
                    // panel by accident and the centring never ran.
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
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
                    ),
                    // 22.7.5b — the owner's own permission: *"It can be centred
                    // in the middle if you like."* With the interval grid gone
                    // the column holds four short things, none of which fills
                    // the panel, and left-aligning them hangs the whole screen
                    // off one edge. Same rule as 22.7.1, on a fifth screen.
                    horizontalAlignment = Alignment.CenterHorizontally
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
                        textAlign = TextAlign.Center,
                        // A sentence is read, so it is capped where it stands.
                        modifier = Modifier.readableText()
                    )
                }

                // 22.7.5c. Nothing to do with the tiles and easy to delete by
                // accident while removing them: a class whose intervals will
                // not parse still has to say so and still has to be rideable.
                if (plan.intervals.isEmpty()) {
                    item {
                        Text(
                            text = "This class has no readable interval data, so targets " +
                                "cannot be shown. You can still ride it as a free ride.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
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

                    // 23.2.7. **What the ride is for** — the one thing on this
                    // screen the blocks cannot say. Everything above it is
                    // derived from them, so a rider could read the title, the
                    // length, the shape sentence and the chart and still not
                    // know why they would pick this class over the one beside
                    // it.
                    //
                    // Below the picture rather than above it: the chart is what
                    // a class is recognised by from across the room, and these
                    // are the sentences read once a rider has stopped on one.
                    plan.description?.let { description ->
                        item {
                            Text(
                                text = description,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.readableText()
                            )
                        }
                    }

                    // **The interval tiles are gone (22.7.5a).** The owner's
                    // note: *"underneath it are a million panels (all the
                    // intervals) and it's just too much and is confusing to new
                    // users."* CLB-01 is thirteen blocks, so that was 52 facts
                    // on the last screen before a rider starts pedalling — and
                    // every one of them is already drawn by the chart above,
                    // which is 26.3's "ten answers where three will do" on the
                    // screen with the most riding on it.
                    //
                    // Three previous notes on this screen were all answered by
                    // *moving* the tiles (22.7.2, 22.7.3, 22.4.3) and none
                    // asked whether they belonged here at all. The argument
                    // that put them here was that this is the screen a rider
                    // *studies* a class on — true of a rider who knows what Z4
                    // is, and this screen's job is the one who does not.
                    //
                    // They are still one tap away rather than deleted: a rider
                    // who wants the block list is a real rider, the data is
                    // already parsed, and [ClassBlocksSheet] costs nothing to
                    // anybody who does not open it.
                    if (plan.intervals.isNotEmpty()) {
                        item {
                            TextButton(onClick = { showingBlocks = true }) {
                                Text("See the blocks")
                            }
                        }
                    }
                }

                // The people, in their own column (22.7.3). Fixed width rather
                // than weighted: the class is what the screen is about and it
                // must not lose a third of the panel on a night nobody has
                // ridden this — which is why the whole column is absent when
                // there is nothing on it (24.1.6's rule, one level up).
                val showPeople = leaderboard?.isWorthShowing == true || rivals.isNotEmpty()
                if (showPeople) {
                    Column(
                        modifier = Modifier
                            .width(PEOPLE_COLUMN_WIDTH)
                            .fillMaxHeight()
                            .padding(end = MaterialTheme.spacing.large),
                        // Level with the class beside it rather than hung off
                        // the app bar — 22.7.1 again, turned ninety degrees.
                        verticalArrangement = Arrangement.spacedBy(
                            MaterialTheme.spacing.large,
                            Alignment.CenterVertically
                        )
                    ) {
                        leaderboard?.let {
                            ClassLeaderboardCard(leaderboard = it, modifier = Modifier.fillMaxWidth())
                        }

                        // 24.3.3. Under the board, because the board is what
                        // makes the offer make sense — a rider looking at
                        // "Kilo did 240 kJ on this one" is exactly the rider
                        // who might want to race it. Chosen here rather than
                        // mid-ride: a menu over somebody who is already
                        // pedalling is 15.1.6's rule.
                        if (rivals.isNotEmpty()) {
                            RivalPicker(
                                rivals = rivals,
                                selectedId = selectedRivalId,
                                onPick = onPickRival,
                                modifier = Modifier.fillMaxWidth()
                            )
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
 * How much of the panel the people take (22.7.3).
 *
 * Wide enough for a name and a number without either wrapping, and narrow
 * enough that the class keeps two thirds of a 1280 dp tablet — which is the
 * right split, because the class is what a rider came here to look at and the
 * board is why they might pick this one.
 */
private val PEOPLE_COLUMN_WIDTH = 400.dp

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

/**
 * Every block of the class, on request (PLAN 22.7.5a).
 *
 * The tiles were on the Start Class screen itself until the owner reported it
 * as *"a million panels"*, and taking them off is the fix. This is not a
 * softened version of that: it is what stops the removal being a *deletion*.
 *
 * The distinction that makes both true at once is **who is asking**. A rider
 * meeting the app for the first time is deciding whether they want to do this
 * ride, and thirteen cards of zone-cadence-watts-duration answer a question
 * they have not asked. A rider who taps *See the blocks* has asked it exactly,
 * and for them the tiles were always the right answer — 11.7's note that this
 * is the screen a class is *studied* on is true of that rider and only that
 * rider.
 *
 * So the content is unchanged, deliberately: same [IntervalCard], same grid,
 * same equal-height rows. Nothing here is new work and nothing is lost.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClassBlocksSheet(
    intervals: List<Interval>,
    ftp: Double,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        LazyColumn(
            contentPadding = PaddingValues(
                start = MaterialTheme.spacing.large,
                end = MaterialTheme.spacing.large,
                bottom = MaterialTheme.spacing.doubleExtraLarge
            ),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.large)
        ) {
            item {
                Text(
                    text = "The blocks",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            item {
                WideGrid(
                    items = intervals,
                    minCellWidth = 300.dp,
                    spacing = MaterialTheme.spacing.small,
                    // One tile carrying a position chip is taller than its
                    // neighbours, and a row of four where the fourth is 20 dp
                    // taller reads as a mistake.
                    equalHeightRows = true
                ) { interval ->
                    IntervalCard(interval = interval, ftp = ftp)
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
                        color = MaterialTheme.colorScheme.onSurface,
                        // The chip beside this is two words wide at most and
                        // must keep its room; the zone name is the part that
                        // can afford to end in an ellipsis.
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    // Only when the class asks for one: an absent position is
                    // the rider's choice and drawing "either" for it would turn
                    // silence into a third instruction (PLAN 25.1.1).
                    interval.position?.let { position ->
                        Spacer(Modifier.width(MaterialTheme.spacing.small))
                        PositionChip(position)
                    }
                }
                // 11.7. Both halves are here — this is the screen a rider
                // *studies* a class on, not the one they glance at mid-effort,
                // and "50–60 rpm at 180–210 W" is the honest description of a
                // grind. What the weight says is which of the two the block is
                // actually asking for, so the instruction is legible before the
                // ride rather than discovered during it.
                val instruction = MaterialTheme.colorScheme.onSurface
                val context = MaterialTheme.colorScheme.onSurfaceVariant
                val cadenceGoverns = interval.governedBy == GovernedBy.Cadence
                Text(
                    text = buildAnnotatedString {
                        withStyle(
                            SpanStyle(
                                color = if (cadenceGoverns) instruction else context,
                                fontWeight = if (cadenceGoverns) FontWeight.Bold else null
                            )
                        ) {
                            append("${interval.cadenceMin}–${interval.cadenceMax} RPM")
                        }
                        withStyle(SpanStyle(color = context)) { append(" · ") }
                        withStyle(
                            SpanStyle(
                                color = if (cadenceGoverns) context else instruction,
                                fontWeight = if (cadenceGoverns) null else FontWeight.Bold
                            )
                        ) {
                            append(
                                "${powerRange.start.toInt()}–" +
                                    "${powerRange.endInclusive.toInt()} W"
                            )
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium
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
