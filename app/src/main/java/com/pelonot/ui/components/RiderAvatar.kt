package com.pelonot.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pelonot.domain.identity.Avatar
import com.pelonot.domain.identity.AvatarMark
import com.pelonot.ui.theme.AvatarPalette
import com.pelonot.ui.theme.expressiveShapes
import androidx.compose.material3.Icon as M3Icon

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
 * **Four rules.**
 *
 * 1. **Never on the HUD** (18.6, 20.2.6). That surface belongs to the next
 *    sixty seconds of pedalling and a rider already knows who they are. The
 *    same argument that keeps the leaderboard off it.
 * 2. **It scales with what it sits in, and the mark scales with it.** The size
 *    is a parameter rather than a constant because the profile tile derives
 *    its own from the screen (20.1.2), and a fixed glyph inside a derived disc
 *    is how a small letter came to sit marooned in a large circle.
 * 3. **It is silent to a screen reader.** A face beside a name says nothing a
 *    name does not, and announcing "avatar" before every rider on the household
 *    panel is three extra words per row for no fact. The caller owns the
 *    description of the row.
 * 4. **The colour is never read as a status.** [AvatarPalette] contains no zone
 *    colour, no live-metric accent and nothing amber, and the reason is written
 *    where the palette is defined.
 */
@Composable
fun RiderAvatar(
    /** The rider's name — the initial is the default face. */
    name: String,
    avatar: Avatar,
    size: Dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size)
            .background(AvatarPalette[avatar.paint.ordinal], MaterialTheme.expressiveShapes.pill)
            // Rule 3: the name is beside it in every call site there is.
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center
    ) {
        val glyph = avatar.mark?.icon
        if (glyph != null) {
            M3Icon(
                imageVector = glyph,
                contentDescription = null,
                tint = ON_AVATAR,
                modifier = Modifier.size(size * 0.55f)
            )
        } else {
            Text(
                // A blank name cannot happen — the field that writes it refuses
                // one — but `take(1)` on an empty string is an empty glyph
                // rather than a crash, so this degrades to a plain disc.
                text = name.take(1).uppercase(),
                // Scaled with the disc rather than a type style: rule 2.
                fontSize = (size.value * 0.5f).sp,
                lineHeight = (size.value * 0.55f).sp,
                fontWeight = FontWeight.Bold,
                color = ON_AVATAR
            )
        }
    }
}

/**
 * The glyph for a mark.
 *
 * The mapping lives here rather than on [AvatarMark] because the domain type
 * stays free of Android imports, and an `ImageVector` is one. It is exhaustive
 * on purpose: adding a mark should fail the build here rather than draw a
 * rider's chosen face as nothing.
 */
val AvatarMark.icon: ImageVector
    get() = when (this) {
        AvatarMark.Bolt -> Icons.Filled.Bolt
        AvatarMark.Mountain -> Icons.Filled.Terrain
        AvatarMark.Paw -> Icons.Filled.Pets
        AvatarMark.Note -> Icons.Filled.MusicNote
        AvatarMark.Rocket -> Icons.Filled.RocketLaunch
        AvatarMark.Coffee -> Icons.Filled.LocalCafe
    }

/**
 * What sits on the disc.
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
