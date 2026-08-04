package com.pelonot.ui.screen

import androidx.compose.ui.graphics.Color
import java.util.Date
import java.text.DateFormat
import com.pelonot.core.Formatters
import com.pelonot.domain.backup.BackupReminder
import com.pelonot.domain.progress.FtpTrend
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.TrendingUp
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pelonot.ui.theme.PelonotGradients
import com.pelonot.ui.theme.elevationTokens
import com.pelonot.ui.theme.expressiveShapes
import com.pelonot.data.repository.DashboardStats
import com.pelonot.domain.social.HouseholdRiderWeek
import com.pelonot.ui.components.HouseholdWeekCard
import com.pelonot.ui.theme.readableColumn
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
    /** Who else on this bike has ridden this week (24.2.1). */
    householdWeek: List<HouseholdRiderWeek> = emptyList(),
    youId: Int? = null,
    /** How much riding a backup would protect (23.3.1). Draws nothing until it is due. */
    backupReminder: BackupReminder = BackupReminder.None,
    onJustRide: () -> Unit,
    onBeginClass: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
    /** How much and how often (16.3.2, 16.3.5), for the card that opens *Your riding*. */
    ridingHistory: RidingHistory = RidingHistory(),
    /** The full-size trend behind the card's sparkline (16.3.1). */
    onFtpProgress: () -> Unit = {},
    onRiding: () -> Unit = {},
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .readableColumn()
                    .padding(horizontal = MaterialTheme.spacing.large)
            ) {
                Spacer(modifier = Modifier.height(MaterialTheme.spacing.large))

                // ── 1️⃣ Greeting Header ──────────────────────────────────
                GreetingHeader(userName = userName)

                Spacer(modifier = Modifier.height(MaterialTheme.spacing.extraLarge))

                // ── 2️⃣ FTP Hero Card ────────────────────────────────────
                FtpHeroCard(
                    ftp = ftp,
                    trend = ftpTrend,
                    // A guest has no profile and therefore no history of one, so
                    // the card does not invite a tap that lands on an empty
                    // screen. Nothing is disabled — it simply is not a door.
                    onClick = onFtpProgress.takeIf { ftpTrend.current != null }
                )

                Spacer(modifier = Modifier.height(MaterialTheme.spacing.extraLarge))

                // ── 3️⃣ Primary Action – Just Ride ───────────────────────
                PrimaryActionCard(
                    title = "Just Ride",
                    subtitle = "Jump on the bike and ride free",
                    icon = Icons.AutoMirrored.Filled.DirectionsBike,
                    onClick = onJustRide
                )

                Spacer(modifier = Modifier.height(MaterialTheme.spacing.large))

                // ── 4️⃣ Secondary Actions ────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.large)
                ) {
                    SecondaryActionCard(
                        title = "Begin Class",
                        subtitle = "Structured workout",
                        icon = Icons.Default.FitnessCenter,
                        onClick = onBeginClass,
                        modifier = Modifier.weight(1f)
                    )
                    SecondaryActionCard(
                        title = "History",
                        subtitle = "Every ride you've finished",
                        icon = Icons.Default.History,
                        onClick = onHistory,
                        modifier = Modifier.weight(1f)
                    )
                    SecondaryActionCard(
                        title = "Settings",
                        subtitle = "FTP, weight, units",
                        icon = Icons.Default.Settings,
                        onClick = onSettings,
                        modifier = Modifier.weight(1f)
                    )
                }

                // ── 4️⃣a The backup reminder, when there is one ──────────
                // Under the actions rather than above them, and never as a
                // dialog: it is a reminder, not a nag (23.3.1). It sits beside
                // the Settings card because that is where it sends the rider.
                if (backupReminder.isDue) {
                    Spacer(modifier = Modifier.height(MaterialTheme.spacing.large))
                    BackupReminderCard(
                        reminder = backupReminder,
                        onBackup = onSettings,
                        onDismiss = onDismissBackupReminder
                    )
                }

                Spacer(modifier = Modifier.height(MaterialTheme.spacing.extraLarge))

                // ── 5️⃣ Progress Section ─────────────────────────────────
                ProgressSection(stats = stats, riding = ridingHistory, onRiding = onRiding)

                // ── 6️⃣ The household ───────────────────────────────────
                // Below the rider's own numbers and never above them: 18.2's
                // rule, applied here (24.2.1). This screen is about their
                // training first, and everyone else's second.
                if (householdWeek.size >= 2) {
                    Spacer(modifier = Modifier.height(MaterialTheme.spacing.extraLarge))
                    HouseholdWeekCard(riders = householdWeek, youId = youId)
                }

                Spacer(modifier = Modifier.height(MaterialTheme.spacing.extraLarge))
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

@Composable
private fun GreetingHeader(userName: String) {
    // Was hardcoded "Good morning," — cheerfully wrong for two thirds of the
    // day, and on a bike that mostly gets ridden in the evening.
    val greeting = remember {
        greetingFor(java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY))
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = greeting,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            fontWeight = FontWeight.Normal
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = userName,
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Ready to ride?",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
        )
    }
}

// =========================================================================
// FTP Hero Card
// =========================================================================
@Composable
private fun FtpHeroCard(ftp: Int, trend: FtpTrend, onClick: (() -> Unit)? = null) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .let { base ->
                if (onClick == null) base
                else base.clickable(onClickLabel = "See how your FTP has changed") { onClick() }
            },
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = MaterialTheme.elevationTokens.level2
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MaterialTheme.spacing.extraLarge)
        ) {
            // Accent gradient bar at top
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(brush = Brush.horizontalGradient(PelonotGradients.TealFlow))
            )

            Spacer(modifier = Modifier.height(MaterialTheme.spacing.large))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "FTP",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )
                if (onClick != null) {
                    Spacer(modifier = Modifier.weight(1f))
                    // Not "History" — that word is already a card on this same
                    // screen and it means the rider's rides.
                    Text(
                        text = "How it changed",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(MaterialTheme.spacing.small))

            Row(
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = "$ftp",
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Black
                )
                Spacer(modifier = Modifier.width(MaterialTheme.spacing.small))
                Text(
                    text = "W",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(MaterialTheme.spacing.extraSmall))

            FtpTrendLine(trend)

            Text(
                text = "Functional Threshold Power — your baseline for all training zones",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
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
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
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
                .fillMaxWidth()
                .padding(MaterialTheme.spacing.extraLarge),
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
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
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

// =========================================================================
// Progress Section
// =========================================================================
@Composable
private fun ProgressSection(
    stats: DashboardStats,
    riding: RidingHistory,
    onRiding: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Your Progress",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.ExtraBold
        )
        Spacer(modifier = Modifier.height(MaterialTheme.spacing.small))
        Text(
            text = "Track your performance over time",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )

        Spacer(modifier = Modifier.height(MaterialTheme.spacing.large))

        if (!stats.hasRidden) {
            // An honest empty state. This section used to show "12.5 kJ" today
            // and "8.3 kJ" last ride as hardcoded literals, on a device that
            // had never recorded a workout.
            Text(
                text = "No rides recorded yet — your output and recent rides " +
                    "will appear here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
            return@Column
        }

        // How much and how often, and the door to the screen that draws it
        // (16.3.2, 16.3.5). Above the two output cards deliberately: the first
        // thing a rider wants from a progress section is whether they have been
        // riding, and 22.1.2 has been saying so since the sixth sitting. This is
        // not that item — the kJ cards below are still what they were — but it
        // is the number that item asked for, in the place it asked for it.
        RecentRidingCard(
            recent = riding.recent,
            streakWeeks = riding.streakWeeks,
            onClick = onRiding
        )
        Spacer(modifier = Modifier.height(MaterialTheme.spacing.medium))

        ProgressMetricCard(
            label = "Today's Output",
            value = Formatters.kilojoulesValue(stats.todayOutputKj),
            unit = "kJ",
            icon = Icons.AutoMirrored.Filled.TrendingUp,
            accentColor = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(MaterialTheme.spacing.medium))

        stats.lastRide?.let { lastRide ->
            ProgressMetricCard(
                label = "Recent Ride",
                value = Formatters.kilojoulesValue(lastRide.totalOutputKj),
                unit = "kJ",
                icon = Icons.AutoMirrored.Filled.DirectionsBike,
                accentColor = MaterialTheme.colorScheme.tertiary
            )
        }
    }
}

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
private fun RecentRidingCard(recent: RidingWindow, streakWeeks: Int, onClick: () -> Unit) {
    val detail = buildList {
        add(if (recent.rides == 1) "1 ride" else "${recent.rides} rides")
        if (recent.rides > 0) add("${recent.minutes} min")
        // Still only mentioned once there is one, and the same argument: a
        // "1-week streak" is a ride, and calling it a streak is flattery, which
        // is how the rest of the numbers on this screen stop being believed.
        if (streakWeeks >= 2) add("$streakWeeks weeks in a row")
    }.joinToString(" · ")

    Card(
        modifier = Modifier
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

// =========================================================================
// Progress Metric Card
// =========================================================================
@Composable
private fun ProgressMetricCard(
    label: String,
    value: String,
    unit: String,
    icon: ImageVector,
    accentColor: androidx.compose.ui.graphics.Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
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
            // Leading icon in a tonal circle
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(MaterialTheme.expressiveShapes.pill)
                    .background(accentColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = accentColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(MaterialTheme.spacing.large))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = value,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Black
                    )
                    Spacer(modifier = Modifier.width(MaterialTheme.spacing.extraSmall))
                    Text(
                        text = unit,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
            }
        }
    }
}

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
        FtpChangeSource.Unknown -> null
    }
    val date = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(change.atEpochMs))

    Spacer(modifier = Modifier.height(MaterialTheme.spacing.small))

    Row(verticalAlignment = Alignment.CenterVertically) {
        FtpSparkline(
            trend = trend,
            color = accent,
            modifier = Modifier.size(width = 76.dp, height = 24.dp)
        )
        Spacer(modifier = Modifier.width(MaterialTheme.spacing.medium))
        Text(
            text = listOfNotNull(
                "${if (rising) "+" else ""}$delta W since $date",
                who
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = accent,
            fontWeight = FontWeight.Medium
        )
    }

    Spacer(modifier = Modifier.height(MaterialTheme.spacing.small))
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