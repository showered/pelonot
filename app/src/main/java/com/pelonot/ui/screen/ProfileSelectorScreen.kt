package com.pelonot.ui.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

/**
 * Who's riding — the first screen anyone sees (PLAN 20.1).
 *
 * **The reference is a TV streaming app's profile picker, and it is the right
 * one**: same device shape, same viewing distance, same job. A bike in a living
 * room has three or four riders, and picking the right one has to take one
 * glance and one tap from two metres away, by someone who has already got their
 * shoes on.
 *
 * The previous version was a `LazyVerticalGrid` of 150dp cards pinned to the
 * top-left of a 1920×1080 screen with a full-width button along the bottom, so
 * two profiles occupied about a twelfth of the display and the rest was black.
 * Everything here is centred both ways and **sized off the screen** rather than
 * in fixed dp (20.1.2), because this app runs on a tablet bolted to a bike.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileSelectorScreen(
    profiles: List<UserEntity>,
    onProfileSelected: (UserEntity) -> Unit,
    onGuestSelected: () -> Unit,
    onCreateProfile: () -> Unit,
    modifier: Modifier = Modifier,
    onRenameProfile: (UserEntity, String) -> Unit = { _, _ -> },
    onDeleteProfile: (UserEntity) -> Unit = {}
) {
    var editing by rememberSaveable(stateSaver = UserIdSaver) {
        mutableStateOf<Int?>(null)
    }
    val editingProfile = profiles.firstOrNull { it.localUserId == editing }

    if (editingProfile != null) {
        ProfileEditDialog(
            profile = editingProfile,
            onRename = { name ->
                editing = null
                onRenameProfile(editingProfile, name)
            },
            onDelete = {
                editing = null
                onDeleteProfile(editingProfile)
            },
            onDismiss = { editing = null }
        )
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .padding(MaterialTheme.spacing.large),
        contentAlignment = Alignment.Center
    ) {
        // One tile per rider plus guest plus "new". Sized so the whole set
        // fits across the screen without ever getting so large that four
        // riders wrap, and never smaller than a comfortable target.
        val tiles = profiles.size + 2
        val tileSize = tileSizeFor(
            available = maxWidth,
            height = maxHeight,
            tiles = tiles
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Who's riding?",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.semantics { heading() }
            )

            Spacer(Modifier.size(MaterialTheme.spacing.extraLarge))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(
                    MaterialTheme.spacing.large,
                    Alignment.CenterHorizontally
                ),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.large)
            ) {
                profiles.forEach { user ->
                    ProfileTile(
                        name = user.name,
                        detail = "${user.ftpWatts} W FTP",
                        accent = AvatarColors[user.localUserId.absoluteValue % AvatarColors.size],
                        size = tileSize,
                        onClick = { onProfileSelected(user) },
                        onLongClick = { editing = user.localUserId }
                    )
                }

                // Guest and "new" are peers of the riders in *layout* (20.1.4)
                // and deliberately not in weight (20.1.3): both are outlined
                // rather than filled, so the eye lands on a real rider first
                // without having to read anything.
                SecondaryTile(
                    icon = Icons.Default.PersonOutline,
                    label = "Guest",
                    detail = "Not saved to a profile",
                    size = tileSize,
                    onClick = onGuestSelected,
                    contentDescription = "Ride as a guest, without saving to a profile"
                )
                SecondaryTile(
                    icon = Icons.Default.Add,
                    label = "New rider",
                    detail = "Add a profile",
                    size = tileSize,
                    onClick = onCreateProfile,
                    contentDescription = "Create a new profile"
                )
            }

            if (profiles.isNotEmpty()) {
                Spacer(Modifier.size(MaterialTheme.spacing.large))
                Text(
                    text = "Press and hold a rider to rename or remove them.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Tile size derived from the screen rather than fixed (20.1.2).
 *
 * Bounded at both ends on purpose. The floor keeps a household of six riders
 * tappable with sweaty hands; the ceiling stops a single rider's tile
 * becoming a comic 500 dp square on a screen this wide.
 */
private fun tileSizeFor(available: Dp, height: Dp, tiles: Int): Dp {
    val perTile = (available - (TILE_GAP * (tiles - 1))) / tiles.coerceAtLeast(1)
    // Height matters too: the tablet is 720 dp tall with a heading above and a
    // hint below, so a tile that fits the width can still not fit the screen.
    val heightBound = height * 0.55f
    return perTile.coerceIn(MIN_TILE, minOf(MAX_TILE, heightBound))
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProfileTile(
    name: String,
    detail: String,
    accent: Color,
    size: Dp,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        // `combinedClickable` rather than `Card(onClick = …)`, which has no
        // long-press at all: the first version put `onLongClick` in `semantics`
        // only, so a real press-and-hold fell through to the click and opened
        // the dashboard instead of the edit dialog. A semantics action is for
        // assistive technology, not a gesture.
        modifier = Modifier
            .size(size)
            .clip(MaterialTheme.expressiveShapes.extraLarge)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                onLongClickLabel = "Rename or remove $name"
            )
            .semantics { contentDescription = "$name, $detail" },
        shape = MaterialTheme.expressiveShapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(MaterialTheme.spacing.medium),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            val avatar = size * 0.44f
            Box(
                modifier = Modifier
                    .size(avatar)
                    .background(accent, MaterialTheme.expressiveShapes.pill),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = name.take(1).uppercase(),
                    // Scaled with the tile: a fixed style would leave a small
                    // letter marooned in the middle of a large circle.
                    fontSize = (avatar.value * 0.5f).sp,
                    lineHeight = (avatar.value * 0.55f).sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
            }

            Spacer(Modifier.size(MaterialTheme.spacing.medium))

            Text(
                text = name,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SecondaryTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    detail: String,
    size: Dp,
    onClick: () -> Unit,
    contentDescription: String
) {
    Card(
        modifier = Modifier
            .size(size)
            .semantics { this.contentDescription = contentDescription },
        onClick = onClick,
        shape = MaterialTheme.expressiveShapes.extraLarge,
        // Outlined rather than filled, so these read as the lesser options
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
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(size * 0.3f)
            )
            Spacer(Modifier.size(MaterialTheme.spacing.medium))
            Text(
                text = label,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Rename or remove, from the screen where a rider is actually looking at their
 * own name (20.1.5).
 *
 * The removal wording is the point of it. `workouts.user_id` is
 * `ON DELETE SET NULL`, so their rides **survive** as unattributed — and a
 * rider who assumed otherwise would either never press it or press it and be
 * surprised. Saying which it is costs one sentence.
 */
@Composable
private fun ProfileEditDialog(
    profile: UserEntity,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    var name by rememberSaveable(profile.localUserId) { mutableStateOf(profile.name) }
    var confirmingDelete by rememberSaveable(profile.localUserId) { mutableStateOf(false) }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text("Remove ${profile.name}?") },
            text = {
                Text(
                    "Their rides are kept — they stop being filed against anyone and " +
                        "stay in the history as unattributed. Only the profile goes."
                )
            },
            confirmButton = { TextButton(onClick = onDelete) { Text("Remove") } },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) { Text("Keep them") }
            }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(profile.name) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.size(MaterialTheme.spacing.small))
                Text(
                    text = "FTP and weight are in Settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onRename(name) },
                enabled = name.isNotBlank() && name.trim() != profile.name
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { confirmingDelete = true }) { Text("Remove rider") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

/** `rememberSaveable` needs a saver for a nullable Int. */
private val UserIdSaver = androidx.compose.runtime.saveable.Saver<Int?, Int>(
    save = { it ?: NO_PROFILE },
    restore = { it.takeIf { id -> id != NO_PROFILE } }
)

private const val NO_PROFILE = Int.MIN_VALUE
private val TILE_GAP = 24.dp
private val MIN_TILE = 150.dp
private val MAX_TILE = 260.dp
