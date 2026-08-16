package com.pelonot.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pelonot.R
import com.pelonot.domain.identity.Avatar
import com.pelonot.domain.identity.AvatarFace
import com.pelonot.domain.progress.RiderLevel
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
 *    description of the row. **The level badge is the exception and describes
 *    itself**, because a level is a fact the name does not carry.
 * 4. **The colour is never read as a status.** [AvatarPalette] contains no zone
 *    colour, no live-metric accent and nothing amber, and the reason is written
 *    where the palette is defined.
 * 5. **The level rides on the face only where the face is big enough to carry
 *    it** (20.6.4, 20.6.8). The owner's note is *"lvl should be part of the
 *    avatar (overlaid somehow)"* and their follow-up came with a picture — a
 *    **ring** round the face with a small `LVL` tab on the bottom of it
 *    (`plan/images/leaderboard-idea.png`). That is what [level] draws, and the
 *    ring is the better half of it: **it is the progress arc**, so
 *    `RiderLevel.progress` gets somewhere it can actually be read, and the
 *    drawing inside is left alone. The first version put a filled pill on the
 *    figure's collar, which worked and covered part of them.
 *
 *    But a ring at 32 dp is a hairline and the tab under it is unreadable, so
 *    the household row and the dashboard greeting keep the pill *beside* the
 *    name and simply do not pass a level. Below [LEVEL_RING_FLOOR] it is not
 *    drawn at all, which is the one place this component silently declines to
 *    draw something it was handed; the alternative is a caller shipping an
 *    unreadable badge without ever seeing it.
 */
@Composable
fun RiderAvatar(
    /** The rider's name — the initial is the default face. */
    name: String,
    avatar: Avatar,
    size: Dp,
    modifier: Modifier = Modifier,
    /**
     * The rider's level, drawn on the face itself (20.6.4), or null for the
     * call sites that draw the badge beside the name instead.
     *
     * Null is *not* "level unknown" — `AppUiState.levelFor` already returns
     * null for a guest, and a guest has no face here either (rule 4 of
     * [RiderScore]). This parameter is the *caller's* choice about the shape of
     * its own row.
     */
    level: RiderLevel? = null
) {
    // The ring and the tab are one decision: either the face carries its level
    // or it does not, and half of it is worse than neither.
    val ringed = level != null && size >= LEVEL_RING_FLOOR
    val ringStroke = size * RING_STROKE
    // Rule 2: the initial scales with the *disc*, which is smaller than the
    // component once a ring is round it.
    val disc = if (ringed) size - ringStroke * RING_GAP else size
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
    val arc = MaterialTheme.colorScheme.primary

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
            if (face != null) {
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

        if (ringed) {
            LevelBadge(level!!, size)
        }
    }
}

/**
 * The level's tab, centred on the bottom of the ring (20.6.4, 20.6.8).
 *
 * **Centred on the bottom rather than tucked into a corner**, which is where
 * the owner's reference puts it and is also what the artwork wants: an Open
 * Peeps figure is a head and a pair of shoulders, so the bottom *corners* of
 * the disc are where the drawing is and the bottom *centre* is a collar.
 *
 * It sits *on* the ring rather than beside it, so the two are one object. The
 * ring is the progress; this is the number.
 */
@Composable
private fun BoxScope.LevelBadge(level: RiderLevel, size: Dp) {
    RiderScore(
        level = level,
        // Rule 2: everything on the face scales with it. A 26 dp pill on a
        // 66 dp face and the same pill on a 114 dp face are two different
        // objects, and the tile derives its size from the screen.
        compact = size * COMPACT_BADGE,
        modifier = Modifier.align(Alignment.BottomCenter)
    )
}

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

/** The badge's height as a fraction of the face it sits on. */
private const val COMPACT_BADGE = 0.26f

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
