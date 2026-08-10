package com.pelonot.ui.screen

import androidx.compose.ui.graphics.Color
import java.util.Date
import java.text.DateFormat
import com.pelonot.domain.backup.BackupReminder
import com.pelonot.domain.model.RideDayGrouping
import com.pelonot.domain.progress.FtpTrend
import com.pelonot.domain.progress.LastRide
import com.pelonot.domain.progress.RideStanding
import com.pelonot.domain.progress.RidingHistory
import com.pelonot.domain.progress.RidingWindow
import com.pelonot.data.local.entity.FtpChangeSource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Path
import androidx.compose.foundation.Canvas
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pelonot.ui.theme.elevationTokens
import com.pelonot.ui.theme.expressiveShapes
import com.pelonot.ui.theme.WideGrid
import com.pelonot.ui.theme.loneCard
import com.pelonot.data.repository.DashboardStats
import com.pelonot.domain.social.HouseholdRider
import com.pelonot.domain.suggest.ClassSuggestion
import com.pelonot.ui.components.HouseholdPanelCard
import com.pelonot.ui.theme.spacing

/**
 * Main dashboard screen – redesigned with Material Expressive card layouts.
 *
 * Features:
 * - Expressive greeting header with gradient accent
 * - FTP hero card with elevated surface container styling
 * - Primary action card with gradient background
 * - Secondary action cards with surface tonal variants
 * - Progress section with elevated metric cards
 * - Proper elevation hierarchy (level0–level3)
 * - Surface tonal variants for visual depth
 * - Spring-animated entrance transitions
 */
@Composable
fun MainDashboardScreen(
    userName: String,
    ftp: Int,
    ftpTrend: FtpTrend,
    stats: DashboardStats,
    /** Who else on this bike has ridden in the last 30 days (24.2.1, 22.5.4). */
    householdRecent: List<HouseholdRider> = emptyList(),
    youId: Int? = null,
    /** How much riding a backup would protect (23.3.1). Draws nothing until it is due. */
    backupReminder: BackupReminder = BackupReminder.None,
    /**
     * The offer to back this profile up by account, on selecting one that has
     * ridden offline (15.8.4). Takes priority over [backupReminder] when both
     * would otherwise show — one nag about the same risk, not two.
     */
    showAccountOffer: Boolean = false,
    onAccountOffer: () -> Unit = {},
    onDismissAccountOffer: () -> Unit = {},
    onJustRide: () -> Unit,
    onBeginClass: () -> Unit,
    /**
     * The class this screen is offering, and why (22.8.6). Null only while the
     * library is loading, which is the one state in which the app cannot name a
     * class — a rider who has ridden nothing still gets one.
     */
    suggestion: ClassSuggestion? = null,
    /** How many classes there are to browse, for the door beside the offer. */
    classCount: Int? = null,
    /** Opens the suggested class's own screen — never starts a ride (22.7.2). */
    onRideSuggestion: (String) -> Unit = {},
    onHistory: () -> Unit,
    onSettings: () -> Unit,
    /** How much and how often (16.3.2, 16.3.5), for the card that opens *Your riding*. */
    ridingHistory: RidingHistory = RidingHistory(),
    /** The full-size trend behind the card's sparkline (16.3.1). */
    onFtpProgress: () -> Unit = {},
    onRiding: () -> Unit = {},
    /** The last ride, opened on the same detail screen history uses (22.1.5). */
    onLastRide: (String) -> Unit = {},
    onDismissBackupReminder: () -> Unit = {}
) {
    // The whole screen fades in when first composed.
    val visibleState = remember { MutableTransitionState(false) }
    visibleState.targetState = true

    AnimatedVisibility(
        visibleState = visibleState,
        enter = fadeIn(tween(600)) + scaleIn(tween(600)),
        exit = fadeOut(tween(300))
    ) {
        // 22.2.1. 11.3.1 was right that there is no dead right-hand side here —
        // the column filled the width. That is the problem: a card 1200 dp wide
        // with a two-word label in it is *harder* to read than the same card at
        // 700, because the eye has to travel the whole room to get from the
        // label to the value. Capped and centred, a card reads as a card.
        //
        // The cap is a maximum, not a width: below it the column still fills
        // whatever it is given, so nothing changes on a narrow screen.
        // 22.4.3 and 22.2.2/22.2.3, settled by the owner's rule of 4 August:
        // **use the width, and no single card gets all of it.** The cap this
        // screen had was right about a *line* and wrong about a *screen* — it
        // left 520 dp of tablet empty on the first surface anybody sees. What
        // replaces the rails 22.2.2 imagined is simpler than they were: cards
        // in pairs and threes, and the two lone cards below held to a column's
        // width so they line up with the grid above rather than banner across
        // it.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MaterialTheme.spacing.large)
            ) {
                Spacer(modifier = Modifier.height(MaterialTheme.spacing.large))

                // ── 1️⃣ The greeting, and the two doors ─────────────────
                GreetingHeader(
                    userName = userName,
                    onHistory = onHistory,
                    onSettings = onSettings
                )

                Spacer(modifier = Modifier.height(MaterialTheme.spacing.large))

                // ── 2️⃣ The way onto the bike ───────────────────────────
                // **Begin Class is the primary action** (22.8.1), on the
                // owner's instruction and their reasoning: *"i feel like 95%+
                // of usage will be classes."* It had been the leftmost of three
                // identical grey tiles that also held History and Settings,
                // while *Just Ride* — the 5% — was the one card on the screen
                // in the primary colour. A rider whose next act is a class had
                // to pick it out of the furniture.
                //
                // Just Ride keeps a place beside it rather than moving down a
                // level, because it is what somebody already sitting on the
                // bike reaches for and it should be hittable without reading.
                //
                // **And the primary card names a class** (22.8.6). *Begin
                // Class* was a door to a list: the screen's own question is
                // *should I ride today, **and what should I ride*** (22.1.1),
                // and a door answers the first half by inviting a choice it
                // does not help with. The card is the suggestion itself, the
                // library keeps a full-size door of its own beside it, and
                // nothing has moved down a level — the row is the same row.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.large)
                ) {
                    if (suggestion != null) {
                        SuggestedClassCard(
                            suggestion = suggestion,
                            onClick = { onRideSuggestion(suggestion.classId) },
                            modifier = Modifier
                                .weight(2f)
                                .fillMaxHeight()
                        )
                        SecondaryActionCard(
                            title = "All Classes",
                            subtitle = classCount?.let { "$it to choose from" } ?: "Browse them all",
                            icon = Icons.AutoMirrored.Filled.List,
                            onClick = onBeginClass,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                    } else {
                        // No library — a fresh install whose seed has not landed
                        // yet, and the only state in which this app cannot name
                        // a class. The door still works.
                        PrimaryActionCard(
                            title = "Begin Class",
                            subtitle = "Pick one and ride it with a plan",
                            icon = Icons.Default.FitnessCenter,
                            onClick = onBeginClass,
                            modifier = Modifier
                                .weight(2f)
                                .fillMaxHeight()
                        )
                    }
                    SecondaryActionCard(
                        title = "Just Ride",
                        subtitle = "No plan — pedal",
                        icon = Icons.AutoMirrored.Filled.DirectionsBike,
                        onClick = onJustRide,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }

                // ── 2️⃣a The account offer or the backup reminder ────────
                // Under the actions rather than above them, and never as a
                // dialog — same shape as 23.3.1, and 15.8.5 folds them into
                // one slot rather than stacking two nags about the same risk:
                // a rider being offered the fuller answer does not also need
                // the lesser one.
                // A card on its own keeps a column's width rather than
                // stretching across the panel — the owner's rule (22.6). It was
                // held to half the row by a weighted spacer, which is the same
                // cap arrived at by accident and 100 dp narrower than the token
                // says; at that width the sentence wrapped to three lines with
                // 633 dp of nothing beside it.
                if (showAccountOffer) {
                    Spacer(modifier = Modifier.height(MaterialTheme.spacing.large))
                    AccountOfferCard(
                        onLink = onAccountOffer,
                        onDismiss = onDismissAccountOffer,
                        modifier = Modifier.loneCard()
                    )
                } else if (backupReminder.isDue) {
                    Spacer(modifier = Modifier.height(MaterialTheme.spacing.large))
                    BackupReminderCard(
                        reminder = backupReminder,
                        onBackup = onSettings,
                        onDismiss = onDismissBackupReminder,
                        modifier = Modifier.loneCard()
                    )
                }

                Spacer(modifier = Modifier.height(MaterialTheme.spacing.large))

                // ── 3️⃣ Where the rider is, and who else is on the bike ──
                //
                // **The household moved from below the fold to beside it**
                // (22.8.4, 22.8.5). It used to sit last, as a lone card holding
                // a column's width with 633 dp of nothing to its right, on a
                // screen 329 dp taller than the viewport — so the only part of
                // this surface about anybody else was the part nobody saw.
                //
                // 18.2's rule is that the rider's own training comes first and
                // everyone else's second. That is a rule about *order*, and it
                // said nothing about visibility; a panel nobody scrolls to is a
                // panel nobody has. Left before right keeps the order and puts
                // both above the fold, and it is what fills the rail rather
                // than a card invented to fill it (22.8.6).
                ProgressSection(
                    stats = stats,
                    riding = ridingHistory,
                    onRiding = onRiding,
                    onLastRide = onLastRide,
                    ftp = ftp,
                    ftpTrend = ftpTrend,
                    // A guest has no profile and therefore no history of one,
                    // so the card does not invite a tap that lands on an empty
                    // screen. Nothing is disabled — it simply is not a door.
                    onFtpProgress = onFtpProgress.takeIf { ftpTrend.current != null },
                    household = householdRecent.takeIf { it.size >= 2 },
                    youId = youId
                )

                Spacer(modifier = Modifier.height(MaterialTheme.spacing.large))
            }
        }
    }
}

// The cap this screen used to own privately is now `Layout.readableWidth`
// (22.2.6). It was right, and it was the only surface that had one. The rails
// it opens up either side stay deliberately empty: what goes in them is a
// layout decision for the whole screen (22.2.2, 22.2.3), and filling them card
// by card produces three columns of unrelated things, which is worse than one.

// =========================================================================
// Greeting Header
// =========================================================================
/**
 * Time of day, read once per composition of the dashboard.
 *
 * Not a flow: nobody's evening turns into night while they are looking at this
 * screen, and a ride that starts at 17:59 does not need the greeting to change
 * underneath the rider at 18:00. It is re-read every time the dashboard is
 * composed, which is every time they come back to it.
 */
private fun greetingFor(hour: Int): String = when (hour) {
    in 0..4 -> "Still up,"
    in 5..11 -> "Good morning,"
    in 12..17 -> "Good afternoon,"
    else -> "Good evening,"
}

/**
 * The greeting and the two doors that are navigation rather than content
 * (22.8.2, 22.8.3).
 *
 * **Three lines became one.** The greeting was a stacked block 86 dp tall on a
 * screen carrying 993 dp of content into a 664 dp viewport, and its third line
 * — *"Ready to ride?"* — was a question under the rider's own name that neither
 * asked nor said anything (Phase 26).
 *
 * **And *History* and *Settings* are here rather than in the card grid.** They
 * were two of three 405 × 111 dp cards, which gave two doors the same weight as
 * the ride itself on the screen whose question is *should I ride today*. They
 * are the same kind of thing as the navigation bar the tablet already draws
 * (`HARDWARE.md`), and this row is where a tablet puts them.
 *
 * **They keep their words.** A bare icon pair would have been smaller still,
 * and 20.4's whole lesson is about the rider meeting this app for the first
 * time — a gear glyph is a guess and *Settings* is not.
 */
@Composable
private fun GreetingHeader(
    userName: String,
    onHistory: () -> Unit,
    onSettings: () -> Unit
) {
    // Was hardcoded "Good morning," — cheerfully wrong for two thirds of the
    // day, and on a bike that mostly gets ridden in the evening.
    val greeting = remember {
        greetingFor(java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY))
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = buildAnnotatedString {
                append("$greeting ")
                pushStyle(SpanStyle(fontWeight = FontWeight.ExtraBold))
                append(userName)
            },
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        HeaderDoor(text = "History", icon = Icons.Default.History, onClick = onHistory)
        Spacer(modifier = Modifier.width(MaterialTheme.spacing.small))
        HeaderDoor(text = "Settings", icon = Icons.Default.Settings, onClick = onSettings)
    }
}

@Composable
private fun HeaderDoor(text: String, icon: ImageVector, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(MaterialTheme.spacing.small))
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

// =========================================================================
// FTP glance card
// =========================================================================
/**
 * The number the whole app is built on, in the same shape as the two cards
 * below it (22.8.2).
 *
 * **It was a 152 dp hero and it set the height of a row.** `WideRow` equalises
 * heights, so the FTP card's own caption — *"Functional Threshold Power — your
 * baseline for all training zones"* — was deciding how tall *Just Ride* was.
 * That caption is a **definition**, and a definition is read once: it has moved
 * to *Your FTP*, the one screen in this app where the number is genuinely being
 * read rather than glanced at, and where the acronym had never been spelled out
 * at all.
 *
 * **What is kept is the trend**, which is the part 22.1.4 built and the only
 * thing here that is progress rather than a total: the stepped sparkline, how
 * far the number moved, when, and who moved it.
 */
@Composable
private fun FtpGlanceCard(
    ftp: Int,
    trend: FtpTrend,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .let { base ->
                if (onClick == null) base
                else base.clickable(onClickLabel = "See how your FTP has changed") { onClick() }
            },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = MaterialTheme.elevationTokens.level1
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MaterialTheme.spacing.large),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // There was a 4 dp teal gradient bar across the top of this card,
            // as decoration. On a card whose whole content is one measured
            // number, a full-width filled bar is not decoration — it is read as
            // a meter, and a meter that is always at 100% is a claim about the
            // rider that nothing here is making. Phase 26: say less.
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(MaterialTheme.expressiveShapes.pill)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Bolt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.size(MaterialTheme.spacing.large))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "FTP",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "$ftp",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Black
                    )
                    Spacer(modifier = Modifier.width(MaterialTheme.spacing.extraSmall))
                    Text(
                        text = "W",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 3.dp)
                    )
                }
                FtpTrendLine(trend)
            }
            if (onClick != null) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// =========================================================================
// Primary Action Card (Just Ride)
// =========================================================================
@Composable
private fun PrimaryActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = MaterialTheme.elevationTokens.level1
        ),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                // The caller stretches this card to the one beside it, and
                // without the height here the Row wraps its content: the
                // alignment below then centres nothing, and the title sits at
                // the top of a 250 dp block of empty teal.
                .fillMaxSize()
                // `large`, not `extraLarge` (22.8.2). At the wider padding this
                // card was 107 dp tall to hold 43 dp of text, and it is the
                // widest card on the screen — so the emptiness showed up as a
                // field of teal rather than as breathing room.
                .padding(MaterialTheme.spacing.large),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(40.dp)
            )
        }
    }
}

// =========================================================================
// The class this screen is offering (22.8.6)
// =========================================================================
/**
 * *What should I ride?* — answered with a class rather than with a door.
 *
 * **It sits exactly where *Begin Class* sat**, in the primary colour, at the
 * same weight, in the same row: the owner's instruction that beginning a class
 * is the primary action (22.8.1) is honoured more directly by naming one than
 * by pointing at a list. The library keeps a full-size card of its own beside
 * it, because a rider who wants a different class must not have to go through
 * this one.
 *
 * **It opens the class, it does not start it.** The class screen is the last
 * screen between a rider and a ride (22.7.2) — the shape of the workout, the
 * board, and a Start button — and a dashboard that could begin a class on one
 * tap would be a dashboard that begins one by accident.
 *
 * **The reason is the third fact on the line and it is never decoration.**
 * Every phrase here is an observation about this rider's own history, drawn
 * from `ClassSuggestion.Reason` so that what is *said* and what was *decided*
 * cannot drift apart.
 */
@Composable
private fun SuggestedClassCard(
    suggestion: ClassSuggestion,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val reason = when (val reason = suggestion.reason) {
        ClassSuggestion.Reason.FirstRide -> "a good place to start"
        ClassSuggestion.Reason.EasyAfterHard -> "easy after a hard one"
        ClassSuggestion.Reason.NewToYou -> "new to you"
        is ClassSuggestion.Reason.NotSince ->
            "not since " + DateFormat.getDateInstance(DateFormat.MEDIUM)
                .format(Date(reason.atEpochMs))
    }
    val detail = "${suggestion.minutes} min · ${suggestion.category} · $reason"

    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "Ride ${suggestion.title}, $detail. Opens the class."
            },
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = MaterialTheme.elevationTokens.level1
        ),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(MaterialTheme.spacing.large),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Ride this",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
                Text(
                    text = suggestion.title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.Default.FitnessCenter,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(40.dp)
            )
        }
    }
}

// =========================================================================
// Secondary Action Card
// =========================================================================
@Composable
private fun SecondaryActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = MaterialTheme.elevationTokens.level1
        ),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MaterialTheme.spacing.large),
            horizontalAlignment = Alignment.Start
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(MaterialTheme.spacing.medium))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// =========================================================================
// The backup reminder (23.3.1)
// =========================================================================
/**
 * A card, on the dashboard, below the things the rider came for.
 *
 * Deliberately **not** a dialog and **not** at the top of the screen. Backup is
 * the offline rider's only durability story, so the app has to say something —
 * but the item's own words are "a reminder and not a nag", and a modal on
 * launch is how a warning gets dismissed by reflex long before the day it
 * matters. It waits ten rides, it says how many, and "Not now" is answered by
 * moving the line rather than by asking again tomorrow.
 *
 * The action goes to Settings rather than raising the file picker here. The
 * backup flow — picker, write, and the sentence saying how many bytes landed —
 * exists once, and a second copy of it on the dashboard is a second place for
 * it to go quietly wrong.
 */
@Composable
private fun BackupReminderCard(
    reminder: BackupReminder,
    onBackup: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = MaterialTheme.elevationTokens.level1
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MaterialTheme.spacing.large),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Save,
                contentDescription = null,
                // Tertiary, not error: nothing has gone wrong. This is a fact
                // about where the rides live, not a fault to be fixed.
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(MaterialTheme.spacing.large))
            Text(
                text = reminder.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(MaterialTheme.spacing.medium))
            TextButton(onClick = onDismiss) { Text("Not now") }
            TextButton(onClick = onBackup) { Text("Back up") }
        }
    }
}

/**
 * *"Back this up?"* for a profile that has been riding offline (PLAN 15.8.4).
 *
 * The second of the two moments this app offers an account — the first is
 * profile creation itself (15.8.1) — and the only one on the dashboard,
 * because that is where selecting a profile lands. **Never a modal**: a rider
 * choosing a profile is on their way to a ride, and 11.6.14 is the standing
 * lesson about what a dialog in front of that costs.
 */
@Composable
private fun AccountOfferCard(
    onLink: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = MaterialTheme.elevationTokens.level1
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MaterialTheme.spacing.large),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Save,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(MaterialTheme.spacing.large))
            Text(
                // 15.8.6: the cost, in the same breath as the offer.
                text = "This profile's rides are only on this tablet. An account " +
                    "keeps a copy safe — everything keeps working without one.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(MaterialTheme.spacing.medium))
            // Equal weight, same as BackupReminderCard's pair — "not now" is
            // not a smaller answer than "back up".
            TextButton(onClick = onDismiss) { Text("Not now") }
            TextButton(onClick = onLink) { Text("Back this up") }
        }
    }
}

// =========================================================================
// Progress Section
// =========================================================================
/**
 * Which of the rider's own glance cards a cell is drawing.
 *
 * A list rather than three call sites because the same three cards are laid out
 * two ways — down a column beside the household, or abreast when there is no
 * household — and `WideGrid` takes items rather than content (22.8.4).
 */
private enum class OwnCard { Ftp, Recent, Last }

/**
 * The rider's own three glance cards, and the household beside them.
 *
 * **The heading is gone** (22.8.2). *Your Progress* and *"Track your
 * performance over time"* cost 44 dp to introduce cards that say `FTP`,
 * `Last 30 days` and `Last ride` on themselves. A section heading earns its
 * place when a surface has sections to tell apart, and this one has one.
 *
 * **Two columns rather than a stack**, because the alternative was the rail:
 * the household is a lone card at a column's width, so put the rider's own
 * numbers in the other column instead of leaving 633 dp of tablet empty and
 * pushing the household off the bottom (22.8.4).
 */
@Composable
private fun ProgressSection(
    stats: DashboardStats,
    riding: RidingHistory,
    onRiding: () -> Unit,
    onLastRide: (String) -> Unit,
    ftp: Int,
    ftpTrend: FtpTrend,
    onFtpProgress: (() -> Unit)?,
    /** Null when there is nobody to show — never an empty panel (22.2.3). */
    household: List<HouseholdRider>?,
    youId: Int?
) {
    if (!stats.hasRidden && household == null) {
        // An honest empty state. This section used to show "12.5 kJ" today
        // and "8.3 kJ" last ride as hardcoded literals, on a device that
        // had never recorded a workout.
        Text(
            text = "No rides recorded yet — your riding will appear here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )
        return
    }

    // 22.1.2 took a third card away from this set. *Today's Output* and
    // *Recent Ride* were both kilojoule totals on the same axis, and on a rider
    // who rides once a week (22.5) they read `73 kJ` and `73 kJ` on ride day
    // and `0.0 kJ` and `73 kJ` for the six days after — the same number twice,
    // then a zero, on the first screen anybody sees. What is here answers the
    // first half of 22.1.1's question: *have I been riding*, and *how did the
    // last one go*, with the number the whole app is built on above them.
    val own = listOfNotNull(
        OwnCard.Ftp,
        OwnCard.Recent,
        OwnCard.Last.takeIf { stats.lastRide != null }
    )

    @Composable
    fun ownCard(which: OwnCard, modifier: Modifier) = when (which) {
        OwnCard.Ftp -> FtpGlanceCard(ftp, ftpTrend, onFtpProgress, modifier)
        OwnCard.Recent -> RecentRidingCard(
            recent = riding.recent,
            streakWeeks = riding.streakWeeks,
            onClick = onRiding,
            modifier = modifier
        )
        OwnCard.Last -> stats.lastRide?.let { ride ->
            LastRideCard(ride, { onLastRide(ride.workoutId) }, modifier)
        } ?: Unit
    }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val abreast = maxWidth >= CARDS_ABREAST_BREAKPOINT
        if (household != null && abreast) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.large)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.large)
                ) {
                    own.forEach { ownCard(it, Modifier.fillMaxWidth()) }
                }
                HouseholdPanelCard(
                    riders = household,
                    youId = youId,
                    modifier = Modifier.weight(1f)
                )
            }
        } else if (household == null && abreast) {
            // **Nobody else on the bike, so the cards go abreast rather than
            // down the left-hand side** — a household of one is the ordinary
            // case for a new rider, and three cards stacked in half a panel is
            // the empty rail all over again. `WideGrid` is the token for a set
            // of things that are *looked at* (22.4), and no cell takes the
            // panel, which is the other half of the owner's rule.
            WideGrid(items = own, minCellWidth = 320.dp, equalHeightRows = true) { card ->
                ownCard(card, Modifier.fillMaxWidth())
            }
        } else {
            // Too narrow for two of anything: one column, and the panel — if
            // there is one — under it at a column's width rather than banded
            // across whatever it has been given (22.6).
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.large)
            ) {
                own.forEach { ownCard(it, Modifier.fillMaxWidth()) }
                household?.let {
                    HouseholdPanelCard(riders = it, youId = youId, modifier = Modifier.loneCard())
                }
            }
        }
    }
}

// `WideRow` lived here and is gone with the layout that used it (22.8). It was
// this screen's private answer to the owner's rule of 4 August, and every one
// of its five call sites is now either a plain `Row` (the actions), a `Column`
// inside one (the rider's own cards) or a `loneCard` (the nags) — each of which
// says what it is doing at the call site rather than behind a name.
//
// **It also carried a defect nothing had met**, worth recording rather than
// deleting silently: its narrow branch claimed to stack the cards and did not.
// `RowScope` is what the content is written against, so the fallback wrapped
// the whole content in a *second* `Row` — weights and all — and a phone got
// three cards at 130 dp each rather than three stacked. The comment above it
// asserted the opposite ("in the stacked case the weights are ignored"), which
// is what kept it invisible: the bike is 1280 dp and never took that branch.

/**
 * The last 30 days, and the way through to every other one (16.3.2, 16.3.5).
 *
 * Rides rather than kilojoules, because "have I been riding" is answered by a
 * count and not by a total — a rider who did one enormous session and then
 * nothing for ten days has a good kJ number and a bad fortnight.
 *
 * **It was *This Week* until 22.5**, and the owner's note is what changed it:
 * assume the bike gets ridden at most once a week, and a weekly card reads
 * "0 rides" six days out of seven — the first thing on the dashboard telling a
 * rider who is doing exactly what they meant to do that they have done nothing.
 * A rolling 30 days always has four or five in it, and never resets, which a
 * calendar month would do on the 1st.
 *
 * **The streak is counted in weeks for the same reason** (22.5.2): a perfect
 * year of Sundays is a day-streak of 1, and by the rule below that is not shown
 * at all. Two weeks running is a real thing a rider is keeping up; two days is
 * still, often, one weekend.
 */
@Composable
private fun RecentRidingCard(
    recent: RidingWindow,
    streakWeeks: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val detail = buildList {
        add(if (recent.rides == 1) "1 ride" else "${recent.rides} rides")
        if (recent.rides > 0) add("${recent.minutes} min")
        // Still only mentioned once there is one, and the same argument: a
        // "1-week streak" is a ride, and calling it a streak is flattery, which
        // is how the rest of the numbers on this screen stop being believed.
        if (streakWeeks >= 2) add("$streakWeeks weeks in a row")
    }.joinToString(" · ")

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = "Last 30 days: $detail. Opens your riding."
            },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = MaterialTheme.elevationTokens.level1
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MaterialTheme.spacing.large),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(MaterialTheme.expressiveShapes.pill)
                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CalendarMonth,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.size(MaterialTheme.spacing.large))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Last 30 days",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = detail,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * The last ride: what it was, when, and whether it went well (PLAN 22.1.5).
 *
 * **It replaced a kilojoule total**, and that is the item rather than a detail
 * of it. *"Recent Ride 73 kJ"* is a measurement on a screen where no
 * measurement is being read (Phase 26), and it sat beside *"Today's Output"*
 * showing the identical figure whenever the last ride was today. What a rider
 * glancing at this wants is the name of the thing they did and whether to be
 * pleased about it.
 *
 * **The claim is one phrase and it is only ever made when it is true**
 * (22.1.7). *Best you've ridden it* needs measured watts on this ride **and**
 * on the rides it is being put above — a modelled ride scoring RMSE 137 W
 * placed over a real one is the app inventing a personal best, and the rider
 * being the same person on both sides makes that worse rather than better.
 * `RideStanding.NotBest` and `RideStanding.Unclaimed` therefore draw the same
 * thing: the facts, and no verdict.
 *
 * **The shortfall is deliberately not drawn.** *"3 kJ off your best"* was
 * written and taken out: it is a number, in a unit, on a card whose question is
 * *do I ride today*, and it turns a neutral ride into a small failure. The kJ
 * are one tap away on the ride itself, where they are a measurement again.
 */
@Composable
private fun LastRideCard(
    ride: LastRide,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val whenText = remember(ride.atEpochMs) {
        when (RideDayGrouping.relativeTo(ride.atEpochMs)) {
            RideDayGrouping.Relative.Today -> "Today"
            RideDayGrouping.Relative.Yesterday -> "Yesterday"
            // The same format the FTP card uses for the day it changed. A bare
            // weekday would be ambiguous past a week, and at one ride a week
            // (22.5) past a week is the ordinary case.
            RideDayGrouping.Relative.Earlier ->
                DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(ride.atEpochMs))
        }
    }
    val title = ride.classTitle ?: "Just Ride"
    val verdict = "best you've ridden it".takeIf { ride.standing == RideStanding.Best }
    val detail = listOfNotNull(whenText, "${ride.minutes} min", verdict).joinToString(" · ")

    // The verdict is a clause of the same line rather than a line of its own,
    // and it is not only typography: a fourth line grew the whole row —
    // `WideRow` equalises heights — and pushed the good news off the bottom of
    // a screen 22.4.3 got fitting without a scroll. Seen doing exactly that on
    // the AVD before it was folded in. The colour is what carries the emphasis
    // the line break was carrying.
    val accent = MaterialTheme.colorScheme.primary
    val detailText = remember(detail, verdict, accent) {
        buildAnnotatedString {
            append(detail)
            if (verdict != null) {
                addStyle(
                    SpanStyle(color = accent, fontWeight = FontWeight.Medium),
                    detail.length - verdict.length,
                    detail.length
                )
            }
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = "Last ride: $title, $detail. Opens the ride."
            },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = MaterialTheme.elevationTokens.level1
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MaterialTheme.spacing.large),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(MaterialTheme.expressiveShapes.pill)
                    .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.DirectionsBike,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.size(MaterialTheme.spacing.large))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Last ride",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = detailText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// `ProgressMetricCard` — a label, a big number and a unit — lived here and is
// gone with the two kilojoule totals it drew (22.1.2). Nothing on this screen
// is a bare measurement any more, which is Phase 26 arriving on the dashboard:
// the two cards left both say a sentence.

/**
 * The rider's FTP over time, on the card that shows the number (7.10.2 / 22.1.4).
 *
 * **Draws nothing until the number has actually moved.** A brand-new rider has
 * one recorded value — the one their profile started with — and a card
 * announcing "last changed" for it would be reporting an event that never
 * happened, on their first ever look at the app. This is the same rule the
 * household panel follows (24.1.6): the empty case draws nothing at all, not an
 * empty version of the thing.
 *
 * The line is **stepped, not interpolated** (7.10.1). FTP does not drift
 * smoothly between two rides; it was one number until the day it became
 * another. A diagonal between 200 and 215 would claim the rider passed through
 * 207 on a Tuesday, which nothing measured.
 *
 * And it says **who moved it**, which is 7.10.4's reason rather than this
 * item's: an accepted auto-FTP change is the app editing the rider's own
 * record, and a number that changes by itself and cannot be traced is
 * indistinguishable from a bug.
 */
@Composable
private fun FtpTrendLine(trend: FtpTrend) {
    val change = trend.lastChange ?: return
    val delta = trend.deltaWatts ?: return

    val rising = delta > 0
    val accent = if (rising) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.tertiary
    }
    val who = when (FtpChangeSource.fromName(change.source)) {
        FtpChangeSource.ManualEdit -> "you set it"
        FtpChangeSource.AutoBreakthrough -> "measured from a ride"
        FtpChangeSource.GuidedTest -> "an FTP test"
        FtpChangeSource.PulledFromCloud -> "another device"
        FtpChangeSource.AutoBreakthroughReverted -> "you put it back"
        FtpChangeSource.ProfileCreated -> null
        FtpChangeSource.Estimated -> "the app's first guess"
        FtpChangeSource.Unknown -> null
    }
    val date = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(change.atEpochMs))

    Spacer(modifier = Modifier.height(MaterialTheme.spacing.extraSmall))

    Row(verticalAlignment = Alignment.CenterVertically) {
        FtpSparkline(
            trend = trend,
            color = accent,
            modifier = Modifier.size(width = 60.dp, height = 18.dp)
        )
        Spacer(modifier = Modifier.width(MaterialTheme.spacing.medium))
        Text(
            text = listOfNotNull(
                "${if (rising) "+" else ""}$delta W since $date",
                who
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = accent,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * The stepped line itself — a horizontal run at each value, a vertical jump on
 * the day it changed.
 *
 * Positioned by **when** each value became true rather than by its index, so
 * three changes in one week and three across a year do not look the same. A
 * rider with two points gets one step, which is the honest picture of one
 * change.
 */
@Composable
private fun FtpSparkline(trend: FtpTrend, color: Color, modifier: Modifier = Modifier) {
    val points = trend.points
    val range = trend.range ?: return
    if (points.size < 2) return

    Canvas(modifier = modifier.semantics {
        contentDescription = "FTP from ${points.first().watts} to ${points.last().watts} watts"
    }) {
        val span = (range.last - range.first).coerceAtLeast(1)
        val first = points.first().atEpochMs
        val elapsed = (points.last().atEpochMs - first).coerceAtLeast(1L)

        val x = { at: Long -> size.width * ((at - first).toFloat() / elapsed) }
        val y = { watts: Int ->
            size.height * (1f - ((watts - range.first).toFloat() / span))
        }

        val path = Path()
        path.moveTo(0f, y(points.first().watts))
        points.drop(1).forEach { point ->
            // Along at the old value to the day it changed, then straight up or
            // down. Never a diagonal: the rider was not gradually becoming
            // fitter between two numbers, they had one number and then another.
            path.lineTo(x(point.atEpochMs), y(points[points.indexOf(point) - 1].watts))
            path.lineTo(x(point.atEpochMs), y(point.watts))
        }
        // And hold the latest value out to the right-hand edge, because it is
        // true now and not only on the day it was set.
        path.lineTo(size.width, y(points.last().watts))

        drawPath(path, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
    }
}

// The "FTP Stable" badge that used to sit here was a constant string with no
// input — it read "FTP Stable" whether the rider had ridden once or a hundred
// times, and whether their FTP had just jumped or not. FTP changes are now
// surfaced where they are actually detected, in the post-ride summary.

/**
 * Below this a row of three cards is three slivers, so they stack instead.
 *
 * 900 dp is the same figure the ride detail charts and *Your riding* use — one
 * number for "this screen has room for more than one thing", not a different
 * guess per surface.
 */
private val CARDS_ABREAST_BREAKPOINT = 900.dp
