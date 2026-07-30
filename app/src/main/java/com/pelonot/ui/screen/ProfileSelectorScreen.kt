package com.pelonot.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pelonot.R
import com.pelonot.data.local.entity.UserEntity
import com.pelonot.ui.theme.PowerZone2Endurance
import com.pelonot.ui.theme.PowerZone3Tempo
import com.pelonot.ui.theme.PowerZone4Threshold
import com.pelonot.ui.theme.PowerZone5VO2Max
import com.pelonot.ui.theme.PowerZone6Anaerobic
import com.pelonot.ui.theme.expressiveShapes
import com.pelonot.ui.theme.spacing
import kotlin.math.absoluteValue

/** Deterministic avatar colours, so a profile keeps the same one every launch. */
private val AvatarColors = listOf(
    PowerZone2Endurance,
    PowerZone3Tempo,
    PowerZone4Threshold,
    PowerZone5VO2Max,
    PowerZone6Anaerobic
)

@Composable
fun ProfileSelectorScreen(
    profiles: List<UserEntity>,
    onProfileSelected: (UserEntity) -> Unit,
    onGuestSelected: () -> Unit,
    onCreateProfile: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(MaterialTheme.spacing.large)
    ) {
        Text(
            text = "Who's riding?",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() }
        )

        Spacer(Modifier.size(MaterialTheme.spacing.large))

        LazyVerticalGrid(
            // Adaptive rather than a fixed two columns: the Peloton tablet is
            // considerably wider than a phone and was showing two enormous cards.
            columns = GridCells.Adaptive(minSize = 150.dp),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = MaterialTheme.spacing.large),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)
        ) {
            items(profiles, key = { it.localUserId }) { user ->
                ProfileCard(
                    name = user.name,
                    detail = "${user.ftpWatts} W FTP",
                    accent = AvatarColors[user.localUserId.absoluteValue % AvatarColors.size],
                    onClick = { onProfileSelected(user) }
                )
            }

            item(key = GUEST_KEY) {
                GuestCard(onClick = onGuestSelected)
            }
        }

        OutlinedButton(
            onClick = onCreateProfile,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = MaterialTheme.expressiveShapes.pill
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null
            )
            Spacer(Modifier.size(MaterialTheme.spacing.small))
            Text(stringResource(R.string.cd_add_profile))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileCard(
    name: String,
    detail: String,
    accent: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .semantics { contentDescription = "$name, $detail" },
        onClick = onClick,
        shape = MaterialTheme.expressiveShapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(MaterialTheme.spacing.medium),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(accent, MaterialTheme.expressiveShapes.pill),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = name.take(1).uppercase(),
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.Black
                )
            }

            Spacer(Modifier.size(MaterialTheme.spacing.medium))

            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GuestCard(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .semantics { contentDescription = "Ride as a guest" },
        onClick = onClick,
        shape = MaterialTheme.expressiveShapes.extraLarge,
        // Outlined rather than filled, so guest reads as the lesser option
        // without needing to be read.
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(MaterialTheme.spacing.medium),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.PersonOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp)
            )
            Spacer(Modifier.size(MaterialTheme.spacing.medium))
            Text(
                text = "Guest",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Not saved to a profile",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

private const val GUEST_KEY = "guest"
