package com.pelonot.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * The Material 3 shape scale, wired into [PelonotTheme] so `MaterialTheme.shapes`
 * — which every stock Material component reads — actually reflects the app's
 * design language. It previously used the library defaults while a parallel
 * custom scale sat unused alongside it.
 */
val PelonotShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

/**
 * Shapes beyond the standard scale, for the expressive treatments the stock
 * `Shapes` type has no slot for.
 *
 * The previous version additionally exposed `fourDp`, `eightDp`, `twelveDp`,
 * `sixteenDp`, `twentyEightDp` and `thirtyTwoDp` — literal sizes duplicating
 * the semantic names directly above them, which defeats the point of a token.
 */
data class ExpressiveShapes(
    val extraSmall: Shape = RoundedCornerShape(4.dp),
    val small: Shape = RoundedCornerShape(8.dp),
    val medium: Shape = RoundedCornerShape(12.dp),
    val large: Shape = RoundedCornerShape(16.dp),
    val extraLarge: Shape = RoundedCornerShape(28.dp),
    val container: Shape = RoundedCornerShape(32.dp),

    /** Fully rounded, for chips, FABs and primary actions. */
    val pill: Shape = RoundedCornerShape(percent = 50),

    /** Bottom sheets and dialogs that rise from the bottom edge. */
    val topRounded: Shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),

    /** Headers that hang from the top edge. */
    val bottomRounded: Shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)
)

val LocalExpressiveShapes = staticCompositionLocalOf { ExpressiveShapes() }
