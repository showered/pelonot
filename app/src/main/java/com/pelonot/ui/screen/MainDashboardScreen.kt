package com.pelonot.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
    stats: DashboardStats,
    /** Who else on this bike has ridden this week (24.2.1). */
    householdWeek: List<HouseholdRiderWeek> = emptyList(),
    youId: Int? = null,
    onJustRide: () -> Unit,
    onBeginClass: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit
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
                FtpHeroCard(ftp = ftp)

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

                Spacer(modifier = Modifier.height(MaterialTheme.spacing.extraLarge))

                // ── 5️⃣ Progress Section ─────────────────────────────────
                ProgressSection(stats = stats)

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
private fun FtpHeroCard(ftp: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
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

            Text(
                text = "FTP",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            )

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
// Progress Section
// =========================================================================
@Composable
private fun ProgressSection(stats: DashboardStats) {
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

        ProgressMetricCard(
            label = "Today's Output",
            value = String.format(java.util.Locale.US, "%.1f", stats.todayOutputKj),
            unit = "kJ",
            icon = Icons.AutoMirrored.Filled.TrendingUp,
            accentColor = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(MaterialTheme.spacing.medium))

        stats.lastRide?.let { lastRide ->
            ProgressMetricCard(
                label = "Recent Ride",
                value = String.format(java.util.Locale.US, "%.1f", lastRide.totalOutputKj),
                unit = "kJ",
                icon = Icons.AutoMirrored.Filled.DirectionsBike,
                accentColor = MaterialTheme.colorScheme.tertiary
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

// The "FTP Stable" badge that used to sit here was a constant string with no
// input — it read "FTP Stable" whether the rider had ridden once or a hundred
// times, and whether their FTP had just jumped or not. FTP changes are now
// surfaced where they are actually detected, in the post-ride summary.