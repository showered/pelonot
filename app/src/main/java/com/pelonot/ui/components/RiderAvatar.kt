package com.pelonot.ui.components

import androidx.annotation.DrawableRes
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
import androidx.compose.ui.graphics.Color
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
 *    it** (20.6.4). The owner's note is *"lvl should be part of the avatar
 *    (overlaid somehow)"* and that is what [level] does — but a badge shrunk
 *    onto a 32 dp disc is two illegible things instead of one legible one, so
 *    the household row and the dashboard greeting keep the pill *beside* the
 *    name and simply do not pass a level. Below [LEVEL_BADGE_FLOOR] it is not
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
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .fillMaxSize()
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
                    fontSize = (size.value * 0.5f).sp,
                    lineHeight = (size.value * 0.55f).sp,
                    fontWeight = FontWeight.Bold,
                    color = ON_AVATAR
                )
            }
        }

        if (level != null && size >= LEVEL_BADGE_FLOOR) {
            LevelBadge(level, size)
        }
    }
}

/**
 * The level, sitting on the bottom edge of the face (20.6.4).
 *
 * **Centred on the bottom edge rather than tucked into a corner**, and the
 * reason is the artwork: an Open Peeps figure is a head and a pair of
 * shoulders, so the bottom corners of the disc are where the drawing is and the
 * bottom centre is a collar. A badge on the corner covers a shoulder and looks
 * like a sticker; on the collar it looks like part of the same object, which is
 * what *"part of the avatar"* asks for.
 *
 * It overhangs the disc by design — half the badge's height sits below the
 * circle — so it reads as attached to the face rather than printed on it, and
 * so it never covers the chin. That overhang is why [RiderAvatar]'s outer `Box`
 * is not clipped and the *inner* one is.
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
 * name, and this sits above both so that neither can acquire a badge by
 * accident. The profile tile derives 66–114 dp from the screen and is
 * comfortably over it.
 */
private val LEVEL_BADGE_FLOOR: Dp = 56.dp

/** The badge's height as a fraction of the face it sits on. */
private const val COMPACT_BADGE = 0.26f
