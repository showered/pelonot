package com.pelonot.ui.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pelonot.data.local.entity.UserEntity
import com.pelonot.domain.identity.Avatar
import com.pelonot.domain.identity.AvatarFace
import com.pelonot.domain.identity.AvatarPaint
import com.pelonot.domain.progress.RiderLevel
import com.pelonot.domain.progress.RidingTotals
import com.pelonot.ui.components.RiderAvatar
import com.pelonot.ui.components.RiderScore
import com.pelonot.ui.components.drawable
import com.pelonot.ui.theme.AvatarPalette
import com.pelonot.ui.theme.expressiveShapes
import com.pelonot.ui.theme.spacing

/**
 * How much of the bottom edge fades out while there is more grid below it
 * (20.1.6). A little under half a tile at the floor size, which is enough to
 * read as a soft edge from two metres and not enough to hide a face.
 */
private val SCROLL_FADE = 48.dp


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
 *
 * **With nobody on the bike this is also the first run (19.1.6)**, and it says
 * so rather than asking a question the rider cannot answer. The empty state is
 * a *state of this screen* and deliberately not a screen in front of it: an
 * empty `profiles` list is the only first run there is, so the database is the
 * flag and no "has seen the welcome" preference exists to drift out of step
 * with it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileSelectorScreen(
    profiles: List<UserEntity>,
    onProfileSelected: (UserEntity) -> Unit,
    onGuestSelected: () -> Unit,
    onCreateProfile: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Each rider's level (26.4), keyed by profile — absent means level 1.
     *
     * **On trial here, and the trial is the item** (26.4.4): 26.1.1 took a
     * number off this screen and the owner's answer was "SO much better", so a
     * badge earns its place back only if it reads as *identity* — "I'm the one
     * on 7" — rather than as a measurement of the rider.
     */
    riderLevels: Map<Int, RiderLevel> = emptyMap(),
    onSaveProfile: (UserEntity, String, Avatar) -> Unit = { _, _, _ -> },
    onDeleteProfile: (UserEntity) -> Unit = {}
) {
    var editing by rememberSaveable(stateSaver = UserIdSaver) {
        mutableStateOf<Int?>(null)
    }
    val editingProfile = profiles.firstOrNull { it.localUserId == editing }

    if (editingProfile != null) {
        ProfileEditDialog(
            profile = editingProfile,
            onSave = { name, avatar ->
                editing = null
                // **One tap of Save is one write.** Two calls here, each
                // reading the profile and writing back a copy, is the defect
                // that left a rider's new FTP on the floor for the life of the
                // project (7.9): whichever coroutine read first put its stale
                // value back on the way past. The name and the face travel
                // together into a single read-modify-write.
                onSaveProfile(editingProfile, name, avatar)
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

        // **The heading and the hint are fixed; only the riders scroll**
        // (20.1.6). Past about twenty tiles the grid has to overflow — that is
        // 20.1.2's floor doing its job, and the floor is right — but the whole
        // screen used to scroll as one piece, so the overflow arrived as a row
        // of tiles sliced off at y 1080 with nothing under it. A row cut by the
        // *edge of the screen* reads as a rendering fault; the same row cut
        // above a line of text reads as more to come.
        //
        // The column still wraps its content, so a household of three is
        // centred exactly as before and nothing here costs them anything.
        val scroll = rememberScrollState()

        // Nobody on the bike is the first run, and there is no other one
        // (19.1.6).
        val firstRun = profiles.isEmpty()

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                // *Who's riding?* is unanswerable on an empty bike, and it was
                // the whole of what the app said to a rider who had just
                // side-loaded it. The name is the heading here because the
                // question this screen has to answer first is *what is this*.
                text = if (firstRun) "Pelonot" else "Who's riding?",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.semantics { heading() }
            )

            if (firstRun) {
                Spacer(Modifier.size(MaterialTheme.spacing.medium))
                Text(
                    // One sentence, and it is the overlay (Phase 26: err
                    // towards saying less). That is the thing this app *is*
                    // and the one part of it a rider cannot discover — the
                    // offline promise is already made at the moment it costs
                    // something, on the account offer at the end of profile
                    // creation: *"Everything keeps working without one."*
                    text = "Your ride, on the bike's own tablet, over whatever " +
                        "you're watching.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.size(MaterialTheme.spacing.extraLarge))

            Box(
                // `fill = false`: take the room the tiles need and no more, so
                // the screen is still one centred block when they fit.
                modifier = Modifier.weight(1f, fill = false)
            ) {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(scroll),
                    horizontalArrangement = Arrangement.spacedBy(
                        MaterialTheme.spacing.large,
                        Alignment.CenterHorizontally
                    ),
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.large)
                ) {
                    profiles.forEach { user ->
                        ProfileTile(
                            name = user.name,
                            avatar = Avatar.parse(user.avatar, user.localUserId),
                            size = tileSize,
                            level = riderLevels[user.localUserId] ?: RiderLevel.of(RidingTotals()),
                            // **26.1.1 undone here and nowhere else** (20.6.5,
                            // 26.4.8). That item took `150 W FTP` off this tile
                            // at the owner's request and every sentence of its
                            // argument still holds; what the note of 16 August
                            // adds is not about the picker's job but about the
                            // rider's relationship with their own number —
                            // *"I feel like FTP and lvl are both important"*,
                            // and *"the goal should be FTP, not lvl"*. A number
                            // the app moves by itself (Phase 7) and never shows
                            // is a number nobody can argue with.
                            ftpWatts = user.ftpWatts,
                            onClick = { onProfileSelected(user) },
                            onLongClick = { editing = user.localUserId }
                        )
                    }

                    // Guest and "new" are peers of the riders in *layout*
                    // (20.1.4) and deliberately not in weight (20.1.3): both
                    // are outlined rather than filled, so the eye lands on a
                    // real rider first without having to read anything.
                    //
                    // **On an empty bike that rule inverts, because it is the
                    // same rule** (19.1.6): there is no real rider for the eye
                    // to land on, so setting one up is filled and comes first,
                    // and Guest keeps the outline it always had. "New rider"
                    // is also new *compared to what* on a bike with nobody on
                    // it, so the first run says what the tile does instead.
                    val setUp = @Composable {
                        SecondaryTile(
                            icon = Icons.Default.Add,
                            label = if (firstRun) "Set up" else "New rider",
                            // What it costs and what it buys. 20.3 asks four
                            // things — a name, a weight, a year of birth and
                            // one sentence about your riding — so the count is
                            // the truth rather than a reassurance.
                            detail = if (firstRun) "Four questions, then ride" else "Add a profile",
                            size = tileSize,
                            onClick = onCreateProfile,
                            contentDescription = "Create a new profile",
                            filled = firstRun
                        )
                    }
                    if (firstRun) setUp()

                    SecondaryTile(
                        icon = Icons.Default.PersonOutline,
                        label = "Guest",
                        detail = "Not saved to a profile",
                        size = tileSize,
                        onClick = onGuestSelected,
                        contentDescription = "Ride as a guest, without saving to a profile"
                    )

                    if (!firstRun) setUp()
                }

                // Each edge fades into the background while there is more past
                // it, and is not drawn at all when there is not. Two metres
                // away on a bike is too far to notice half a tile; a soft edge
                // is visible from there and says the same thing. Both ends,
                // because once the grid has been scrolled the riders above it
                // are the ones that have gone missing.
                if (scroll.canScrollBackward) {
                    ScrollEdgeFade(Alignment.TopCenter)
                }
                if (scroll.canScrollForward) {
                    ScrollEdgeFade(Alignment.BottomCenter)
                }
            }

            if (profiles.isNotEmpty()) {
                Spacer(Modifier.size(MaterialTheme.spacing.large))
                Text(
                    // The hint earns a second job here: it is the line the last
                    // visible row is cut against, which is what makes the cut
                    // legible as "there is more" rather than as a clipped
                    // screen. It says nothing about scrolling — a rider who can
                    // see half a tile does not need to be told (Phase 26).
                    text = "Press and hold a rider to rename or remove them.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * One soft edge on the scrolling grid (20.1.6), fading towards whichever side
 * the hidden tiles are on.
 */
@Composable
private fun BoxScope.ScrollEdgeFade(edge: Alignment) {
    val background = MaterialTheme.colorScheme.background
    val colours = if (edge == Alignment.TopCenter) {
        listOf(background, Color.Transparent)
    } else {
        listOf(Color.Transparent, background)
    }
    Box(
        modifier = Modifier
            .align(edge)
            .fillMaxWidth()
            .height(SCROLL_FADE)
            .background(Brush.verticalGradient(colours))
    )
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
    avatar: Avatar,
    size: Dp,
    level: RiderLevel,
    ftpWatts: Int,
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
            .semantics { contentDescription = "Ride as $name" },
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
            // Sized off the tile, which is itself sized off the screen
            // (20.1.2): a fixed disc would leave a small letter marooned in a
            // large circle, which is the fault that rule exists for.
            //
            // **The level rides on the face** (20.6.4) rather than sitting in a
            // row of its own under the name, which is the owner's *"lvl should
            // be part of the avatar"* and also what buys the row the FTP goes
            // in: the tile is the same height it was.
            RiderAvatar(
                name = name,
                avatar = avatar,
                size = size * 0.44f,
                level = level
            )

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
                // **`FTP 150 W`, not `150 W FTP`.** The rejected form led with
                // the number, which made it a headline on a tile whose headline
                // is the rider's name; label first reads as a fact *about* them
                // and is scanned past by anyone who did not come for it. Quiet
                // on purpose — the eye still lands on the face, then the name,
                // and this is third (20.6.5).
                text = "FTP $ftpWatts W",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1
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
    contentDescription: String,
    filled: Boolean = false
) {
    Card(
        modifier = Modifier
            .size(size)
            .semantics { this.contentDescription = contentDescription },
        onClick = onClick,
        shape = MaterialTheme.expressiveShapes.extraLarge,
        // Outlined rather than filled, so these read as the lesser options
        // without needing to be read — except on the first run, where one of
        // them is the only thing to do (19.1.6).
        colors = if (filled) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        } else {
            CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        },
        border = if (filled) null else CardDefaults.outlinedCardBorder()
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
                tint = if (filled) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(size * 0.3f)
            )
            Spacer(Modifier.size(MaterialTheme.spacing.medium))
            Text(
                text = label,
                style = MaterialTheme.typography.titleLarge,
                color = if (filled) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = if (filled) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
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
    onSave: (String, Avatar) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    var name by rememberSaveable(profile.localUserId) { mutableStateOf(profile.name) }
    var confirmingDelete by rememberSaveable(profile.localUserId) { mutableStateOf(false) }

    // The face the rider currently has — which for somebody who has never
    // chosen is the one derived from their row id, so the picker opens showing
    // what is actually on screen rather than nothing selected (20.2.3).
    val current = remember(profile.avatar, profile.localUserId) {
        Avatar.parse(profile.avatar, profile.localUserId)
    }
    var paint by rememberSaveable(profile.localUserId) { mutableStateOf(current.paint) }
    var face by rememberSaveable(profile.localUserId) { mutableStateOf(current.face) }
    val chosen = Avatar(paint, face)

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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // The face is shown at the size it is *read* at rather than
                    // the size the tile draws it: this is a preview of a choice
                    // being made, and the tile is where it is looked at.
                    RiderAvatar(
                        name = name.ifBlank { profile.name },
                        avatar = chosen,
                        size = 56.dp
                    )
                    Spacer(Modifier.width(MaterialTheme.spacing.medium))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.size(MaterialTheme.spacing.medium))
                AvatarPicker(
                    paint = paint,
                    face = face,
                    onPaint = { paint = it },
                    onFace = { face = it }
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
                onClick = { onSave(name, chosen) },
                // Save is live when *either* half has moved. Keying it on the
                // name alone would have left a rider who changed only their
                // face looking at a disabled button, which reads as the app
                // having refused rather than as nothing having changed.
                enabled = name.isNotBlank() &&
                    (name.trim() != profile.name || chosen.store() != profile.avatar)
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

/**
 * Choosing a face (20.2.1, 20.2.3, 20.6.1).
 *
 * **A colour and, if the rider wants one, a face** — two rows rather than a
 * grid of every combination, because eight colours times twenty faces is a
 * hundred and sixty tiles and a decision nobody asked for. Phase 26's rule is
 * about saying less and it applies to controls as much as to sentences (26.3):
 * this is a *pick*, so it is allowed more than three answers, but the ceiling
 * is how many things are told apart at a glance rather than how many exist.
 *
 * **Twenty faces at the owner's own choice** (20.6.1), and they are drawn
 * people rather than the six Material icons this dialog used to offer — *"the
 * ones we have are not good"*. Each swatch shows the face on the colour that is
 * currently chosen, so the row is a preview of the outcome rather than a
 * catalogue: changing the colour re-tints all twenty at once.
 *
 * **The rider's own initial is the first option in the face row and it is
 * selected by default.** It is not an absence dressed up as a choice: an
 * initial is unambiguous between two housemates with different names, and a
 * face is what serves the household where two names start with the same letter
 * — the one case an initial genuinely cannot.
 *
 * Nothing here is a required step. A rider who never opens this dialog has a
 * face already, derived from their row id, and the column stays null so the app
 * can still tell that they never chose.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AvatarPicker(
    paint: AvatarPaint,
    face: AvatarFace?,
    onPaint: (AvatarPaint) -> Unit,
    onFace: (AvatarFace?) -> Unit
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)
    ) {
        AvatarPaint.entries.forEach { option ->
            Swatch(
                selected = option == paint,
                fill = AvatarPalette[option.ordinal],
                label = "Colour ${option.ordinal + 1}",
                onClick = { onPaint(option) }
            ) {}
        }
    }

    Spacer(Modifier.size(MaterialTheme.spacing.small))

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)
    ) {
        // The initial first, because it is what the rider already has.
        Swatch(
            selected = face == null,
            fill = AvatarPalette[paint.ordinal],
            label = "Your initial",
            size = FACE_SWATCH,
            onClick = { onFace(null) }
        ) {
            Text(
                text = "A",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
        }
        AvatarFace.entries.forEach { option ->
            Swatch(
                selected = option == face,
                fill = AvatarPalette[paint.ordinal],
                // The ids are seeds and mean nothing to a rider (`lark`,
                // `dune`), so a screen reader is told the position instead:
                // there is no honest name for a drawn stranger's face, and
                // inventing one would name a person the app knows nothing
                // about.
                label = "Face ${option.ordinal + 1}",
                size = FACE_SWATCH,
                onClick = { onFace(option) }
            ) {
                Image(
                    painter = painterResource(option.drawable),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

/**
 * One option in the picker.
 *
 * **The selection is a ring with a gap inside it, and the gap is the whole
 * fix.** The first version drew the ring as a border *on* the disc, in the
 * brand teal — which was legible on the mark row, where every swatch is a dark
 * grey, and very nearly invisible on the colour row, because one of the eight
 * colours *is* a turquoise and a teal ring on it reads as an edge rather than
 * as a choice. Watched on the tablet AVD and it is the state a rider is in most
 * often: the swatch they are looking at is the one they already have. Insetting
 * the fill lets the dialog's own surface show between the ring and the colour,
 * so the signal is the **separation** rather than a hue that some of the
 * options can defeat.
 *
 * A tick on top was the other candidate and is worse here: it hides part of the
 * thing being chosen. 48 dp is the touch target the rest of this screen is
 * built to — 20.1.2's tile floor exists for the same reason, a thumb on a bike.
 */
@Composable
private fun Swatch(
    selected: Boolean,
    fill: Color,
    label: String,
    onClick: () -> Unit,
    size: Dp = 48.dp,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(MaterialTheme.expressiveShapes.pill)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
                shape = MaterialTheme.expressiveShapes.pill
            )
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = label
                this.selected = selected
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                // Unselected fills to the outline, so only the chosen one
                // changes shape and the row does not shimmer as it is scanned.
                .size(if (selected) size - SWATCH_INSET else size)
                .clip(MaterialTheme.expressiveShapes.pill)
                .background(fill),
            contentAlignment = Alignment.Center,
            content = { content() }
        )
    }
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

/**
 * The face swatches are larger than the colour ones, and the reason is that
 * they carry a drawing rather than a flat fill. At 48 dp — the colour row's
 * size, and the touch target the rest of this screen is built to — a selected
 * Open Peeps figure is inset to 38 dp and stops being a person you can tell
 * from the one beside it, which is the entire basis on which the set was
 * chosen (`avatars/README.md`).
 */
private val FACE_SWATCH = 64.dp

/** How far the fill is inset when a swatch is the chosen one (20.2.3a). */
private val SWATCH_INSET = 10.dp
