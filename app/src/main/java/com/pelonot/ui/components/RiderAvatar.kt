package com.pelonot.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pelonot.R
import com.pelonot.domain.identity.Avatar
import com.pelonot.domain.identity.AvatarFace
import com.pelonot.domain.progress.RiderLevel
import com.pelonot.di.ServiceLocator
import com.pelonot.ui.theme.AlertGreen
import com.pelonot.ui.theme.AvatarPalette
import com.pelonot.ui.theme.expressiveShapes

/**
 * A rider's face, drawn the same way everywhere (PLAN 20.2.6).
 *
 * Same habit as [RiderScore], `readableColumn` and `loneCard`: one component,
 * one shape, one set of rules in its KDoc, so the selector, the greeting and
 * the household panel cannot each draw a rider slightly differently. Before
 * this existed the only avatar in the app lived *inside*
 * `ProfileSelectorScreen` as a private `Box`, which is how it came to be drawn
 * off the power-zone palette and why nowhere else drew one at all.
 *
 * **Five rules.**
 *
 * 1. **Never on the HUD** (18.6, 20.2.6). That surface belongs to the next
 *    sixty seconds of pedalling and a rider already knows who they are. The
 *    same argument that keeps the leaderboard off it.
 * 2. **It scales with what it sits in, and everything on it scales too.** The
 *    size is a parameter rather than a constant because the profile tile
 *    derives its own from the screen (20.1.2), and a fixed glyph inside a
 *    derived disc is how a small letter came to sit marooned in a large circle.
 * 3. **It is silent to a screen reader.** A face beside a name says nothing a
 *    name does not, and announcing "avatar" before every rider on the household
 *    panel is three extra words per row for no fact. The caller owns the
 *    description of the row. **The FTP badge is the exception and describes
 *    itself**, because FTP and confirmation are facts the name does not carry.
 * 4. **The colour is never read as a status.** [AvatarPalette] contains no zone
 *    colour, no live-metric accent and nothing amber, and the reason is written
 *    where the palette is defined.
 * 5. **Details need room.** The level's progress ring starts at
 *    [LEVEL_RING_FLOOR]; the power capsule starts at [FTP_BADGE_FLOOR].
 *    Small inline faces stay uncluttered. The ring still represents riding
 *    volume, while the capsule shows FTP and its measured confirmation (20.9).
 */
@Composable
fun RiderAvatar(
    /** The rider's name — the initial is the default face. */
    name: String,
    avatar: Avatar,
    size: Dp,
    modifier: Modifier = Modifier,
    /**
     * The rider's level progress, drawn around the face (20.6.4), or null for the
     * call sites that draw the badge beside the name instead.
     *
     * Null is *not* "level unknown" — `AppUiState.levelFor` already returns
     * null for a guest, and a guest has no face here either (rule 4 of
     * [RiderScore]). This parameter is the *caller's* choice about the shape of
     * its own row.
     */
    level: RiderLevel? = null,
    /** Shown on the face only where a complete measurement still fits. */
    ftpWatts: Int? = null,
    /** A measured assessment gets the green tick; estimates and entries have no seal. */
    ftpVerified: Boolean = false
) {
    val photograph = rememberAvatarPhoto(avatar, size)

    // Small inline faces cannot carry a readable progress ring.
    val ringed = level != null && size >= LEVEL_RING_FLOOR
    val ringStroke = size * RING_STROKE
    // Rule 2: the initial scales with the *disc*, which is smaller than the
    // component once a ring is round it.
    val disc = if (ringed) size - ringStroke * RING_GAP else size
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
    val arc = level?.let {
        lerp(
            MaterialTheme.colorScheme.secondary,
            MaterialTheme.colorScheme.primary,
            ((it.level - 1) / 12f).coerceIn(0f, 1f)
        )
    } ?: MaterialTheme.colorScheme.primary

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        if (ringed) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = ringStroke.toPx()
                val topLeft = Offset(stroke / 2f, stroke / 2f)
                val arcSize = Size(size.toPx() - stroke, size.toPx() - stroke)
                // The whole circle first, so an early level reads as *a ring
                // with a little of it filled* rather than as a broken one.
                drawArc(
                    color = track,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
                if (level!!.progress > 0f) {
                    drawArc(
                        // From the top, clockwise — the direction every
                        // progress ring anybody has seen goes.
                        color = arc,
                        startAngle = -90f,
                        sweepAngle = 360f * level.progress,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                // Inside the ring rather than under it: the face is a drawing
                // and an arc across its shoulders would read as a fault.
                .size(disc)
                .clip(MaterialTheme.expressiveShapes.pill)
                .background(AvatarPalette[avatar.paint.ordinal])
                // Rule 3: the name is beside it in every call site there is.
                .clearAndSetSemantics { },
            contentAlignment = Alignment.Center
        ) {
            val face = avatar.face
            if (photograph != null) {
                Image(
                    bitmap = photograph,
                    contentDescription = null,
                    // Already square (`AvatarPhotoStore` centre-crops on import),
                    // so `Crop` here only ever absorbs a rounding pixel.
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (face != null) {
                Image(
                    painter = painterResource(face.drawable),
                    contentDescription = null,
                    // The drawn figure fills its own square and sits on the
                    // disc's bottom edge, which is what a head and shoulders in
                    // a circle looks like everywhere else. `Crop` rather than
                    // `Fit` because `Fit` insets it and leaves a floating head.
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    // A blank name cannot happen — the field that writes it
                    // refuses one — but `take(1)` on an empty string is an empty
                    // glyph rather than a crash, so this degrades to a plain
                    // disc.
                    text = name.take(1).uppercase(),
                    // Scaled with the disc rather than a type style: rule 2.
                    fontSize = (disc.value * 0.5f).sp,
                    lineHeight = (disc.value * 0.55f).sp,
                    fontWeight = FontWeight.Bold,
                    color = ON_AVATAR
                )
            }
        }

        if (ftpWatts != null && size >= FTP_BADGE_FLOOR) {
            FtpBadge(ftpWatts, ftpVerified, size)
        }
    }
}

/**
 * A rider's photograph at the size it is about to be drawn, or null (20.2.4).
 *
 * **Null covers two different things on purpose, and both end the same way.**
 * The rider may have no photograph, or they may have one whose file is not
 * there — a database exported and imported onto another tablet (12.4.4) names
 * pictures that did not travel with it. Either way the disc falls back to the
 * colour and initial [Avatar.parse] kept beside the photograph precisely so
 * there would be something to fall back to. A broken-image glyph on the first
 * screen anybody sees would be worse than a face they did not choose.
 *
 * **This is the one composable in the app that reaches [ServiceLocator]**, and
 * the rule it bends is worth naming rather than leaving to be discovered. The
 * house rule is *no database access from composables*; a file whose name the
 * caller is already holding is not the database, and the size to decode it at
 * is known **here** and nowhere else — [RiderAvatar] is drawn at 32, 40, 56,
 * 112 and a derived 66–114 dp. The alternative is six view models each learning
 * to decode a photograph, which is the duplication every other rule in this file
 * exists to prevent.
 */
@Composable
private fun rememberAvatarPhoto(avatar: Avatar, size: Dp): ImageBitmap? {
    val photo = avatar.photo ?: return null
    val store = remember { ServiceLocator.avatarPhotoStore }
    val px = with(LocalDensity.current) { size.roundToPx() }
    return remember(photo, px) { store.decode(photo, px)?.asImageBitmap() }
}

/**
 * Power belongs to the number, confirmation to the green seal (20.9).
 * A translucent dark capsule lets the portrait show through in both themes.
 * Full units and provenance remain in its spoken label.
 */
@Composable
private fun BoxScope.FtpBadge(ftpWatts: Int, verified: Boolean, size: Dp) {
    Row(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .clip(MaterialTheme.expressiveShapes.pill)
            .background(FTP_BADGE_SURFACE.copy(alpha = 0.60f))
            .border(1.dp, FTP_BADGE_OUTLINE.copy(alpha = 0.7f), MaterialTheme.expressiveShapes.pill)
            .clearAndSetSemantics {
                contentDescription = "FTP $ftpWatts watts, " +
                    if (verified) "confirmed from measured riding" else "not confirmed from measured riding"
            }
            .padding(horizontal = size * 0.075f, vertical = size * 0.035f),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Bolt,
            contentDescription = null,
            tint = FTP_BADGE_INK,
            modifier = Modifier.size(size * 0.16f)
        )
        Spacer(Modifier.size(size * 0.025f))
        Text(
            text = ftpWatts.toString(),
            fontSize = (size.value * 0.16f).sp,
            lineHeight = (size.value * 0.18f).sp,
            fontWeight = FontWeight.Bold,
            color = FTP_BADGE_INK,
            maxLines = 1
        )
        if (verified) {
            Spacer(Modifier.size(size * 0.05f))
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = FTP_BADGE_SURFACE,
                modifier = Modifier
                    .size(size * 0.19f)
                    .background(AlertGreen, MaterialTheme.expressiveShapes.pill)
                    .padding(size * 0.03f)
            )
        }
    }
}

// Dark enough at 60% opacity to keep white digits legible over the lightest portrait.
private val FTP_BADGE_SURFACE = Color(0xFF141417)
private val FTP_BADGE_OUTLINE = Color(0xFF52525B)
private val FTP_BADGE_INK = Color(0xFFF4F4F5)

/**
 * The drawable for a face.
 *
 * The mapping lives here rather than on [AvatarFace] because the domain type
 * stays free of Android imports, and a resource id is one. It is exhaustive on
 * purpose: adding a face should fail the build here rather than draw a rider's
 * chosen face as nothing.
 */
@get:DrawableRes
val AvatarFace.drawable: Int
    get() = when (this) {
        AvatarFace.Ash -> R.drawable.avatar_ash
        AvatarFace.Bay -> R.drawable.avatar_bay
        AvatarFace.Cove -> R.drawable.avatar_cove
        AvatarFace.Dune -> R.drawable.avatar_dune
        AvatarFace.Elm -> R.drawable.avatar_elm
        AvatarFace.Fern -> R.drawable.avatar_fern
        AvatarFace.Glen -> R.drawable.avatar_glen
        AvatarFace.Haze -> R.drawable.avatar_haze
        AvatarFace.Isle -> R.drawable.avatar_isle
        AvatarFace.Kite -> R.drawable.avatar_kite
        AvatarFace.Lark -> R.drawable.avatar_lark
        AvatarFace.Moss -> R.drawable.avatar_moss
        AvatarFace.Nova -> R.drawable.avatar_nova
        AvatarFace.Opal -> R.drawable.avatar_opal
        AvatarFace.Pine -> R.drawable.avatar_pine
        AvatarFace.Quill -> R.drawable.avatar_quill
        AvatarFace.Reed -> R.drawable.avatar_reed
        AvatarFace.Sage -> R.drawable.avatar_sage
        AvatarFace.Tide -> R.drawable.avatar_tide
        AvatarFace.Vale -> R.drawable.avatar_vale
    }

/**
 * What sits on the disc when the rider has no face — their initial.
 *
 * Black rather than `onSurface`: every colour in [AvatarPalette] is a light
 * tint chosen to carry it, and a theme-following ink would turn white on the
 * same disc in dark mode and vanish. The disc is its own small surface.
 */
private val ON_AVATAR = Color.Black

/** The face beside a name in a list — the household panel's row height. */
val AVATAR_INLINE: Dp = 32.dp

/** The face beside the dashboard greeting, which is one line of headline. */
val AVATAR_GREETING: Dp = 40.dp

/**
 * The smallest face that may carry a level (rule 5).
 *
 * Set from the two sizes above rather than picked: both of them are *beside* a
 * name, and this sits above both so that neither can acquire a ring by
 * accident. The profile tile derives 66–114 dp from the screen and is
 * comfortably over it.
 */
private val LEVEL_RING_FLOOR: Dp = 56.dp

/** Smaller faces keep their silhouette; a power badge needs room for three digits. */
private val FTP_BADGE_FLOOR = 80.dp

/** The progress ring's thickness, as a fraction of the face. */
private const val RING_STROKE = 0.055f

/**
 * How far the face is inset inside the ring, in ring-strokes.
 *
 * Over 2 because the stroke is centred on the circle it is drawn along, so one
 * stroke of it is already inside the outer edge; the rest is the gap that makes
 * the ring and the face read as two objects rather than a border.
 */
private const val RING_GAP = 3.2f
