package com.pelonot.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pelonot.ui.components.HeroCard
import com.pelonot.ui.components.HeroMetric
import com.pelonot.ui.components.MetricCard
import com.pelonot.ui.components.PrimaryButton
import com.pelonot.ui.components.SecondaryButton
import com.pelonot.ui.components.SectionHeader
import com.pelonot.ui.components.StatusChip

/**
 * Main dashboard screen – redesigned to use the Material Expressive design system.
 *
 * The screen is intentionally split into small composables to keep the
 * implementation maintainable and testable.  All business‑logic callbacks are
 * preserved from the original signature.
 */
@Composable
fun MainDashboardScreen(
    userName: String,
    ftp: Int,
    onJustRide: () -> Unit,
    onBeginClass: () -> Unit,
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // 1️⃣ Greeting header
            SectionHeader(
                title = "Good morning, $userName",
                subtitle = "Ready to ride?"
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 2️⃣ Hero card – shows FTP and start‑ride button
            HeroCard(
                title = "Your FTP",
                subtitle = "Personal Training Power",
                actionText = "View",
                onActionClick = { /* placeholder – could navigate to FTP details */ },
                content = {
                    HeroMetric(
                        label = "FTP",
                        value = "$ftp",
                        unit = "W",
                        accentColor = MaterialTheme.colorScheme.primary,
                        subtitle = "Your power baseline"
                    )
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 3️⃣ Primary action – Start Ride
            PrimaryButton(
                text = "Start Ride",
                onClick = onJustRide,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 4️⃣ Secondary actions – Begin Class & Settings
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SecondaryButton(
                    text = "Begin Class",
                    onClick = onBeginClass,
                    modifier = Modifier.weight(1f)
                )
                SecondaryButton(
                    text = "Settings",
                    onClick = onSettings,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 5️⃣ Progress section header
            SectionHeader(
                title = "Your Progress",
                subtitle = "Track your performance"
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 6️⃣ Metric cards – Today's progress & Recent ride
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                MetricCard(
                    label = "Today's Progress",
                    value = "12.5",
                    unit = "kJ",
                    accentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    label = "Recent Ride",
                    value = "8.3",
                    unit = "kJ",
                    accentColor = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 7️⃣ FTP status chip
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                StatusChip(
                    text = "FTP Stable",
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
