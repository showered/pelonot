package com.pelonot.ui.theme

import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

// ==========================================
// Material Expressive - Shape System
// Pill shapes, cut corners, rounded rectangles
// ==========================================

/**
 * Expressive shape tokens for the Pelonot design system.
 *
 * Provides a consistent set of shapes used throughout the app:
 * - Rounded rectangles for cards, buttons, dialogs, and containers
 * - Cut corners for accent / premium visual variety
 * - Pill shapes for fully rounded elements (chips, FABs, primary buttons)
 */
data class ExpressiveShapes(
    // --- Rounded Rectangle Shapes ---
    val extraSmall: Shape = RoundedCornerShape(4.dp),
    val small: Shape = RoundedCornerShape(8.dp),
    val medium: Shape = RoundedCornerShape(12.dp),
    val large: Shape = RoundedCornerShape(16.dp),
    val extraLarge: Shape = RoundedCornerShape(24.dp),
    val container: Shape = RoundedCornerShape(24.dp),

    // --- Pill Shape (fully rounded) ---
    val pill: Shape = RoundedCornerShape(999.dp),

    // --- Cut Corner Shapes (for accent/premium elements) ---
    val cutCornerSmall: Shape = CutCornerShape(4.dp),
    val cutCornerMedium: Shape = CutCornerShape(8.dp),
    val cutCornerLarge: Shape = CutCornerShape(12.dp),

    // --- Top-Only Rounded (for bottom sheets, dialogs) ---
    val topRoundedSmall: Shape = RoundedCornerShape(
        topStart = 8.dp,
        topEnd = 8.dp,
        bottomStart = 0.dp,
        bottomEnd = 0.dp
    ),
    val topRoundedLarge: Shape = RoundedCornerShape(
        topStart = 24.dp,
        topEnd = 24.dp,
        bottomStart = 0.dp,
        bottomEnd = 0.dp
    ),

    // --- Bottom-Only Rounded (for top bars, headers) ---
    val bottomRoundedSmall: Shape = RoundedCornerShape(
        topStart = 0.dp,
        topEnd = 0.dp,
        bottomStart = 8.dp,
        bottomEnd = 8.dp
    ),
    val bottomRoundedLarge: Shape = RoundedCornerShape(
        topStart = 0.dp,
        topEnd = 0.dp,
        bottomStart = 24.dp,
        bottomEnd = 24.dp
    )
)

val LocalExpressiveShapes = staticCompositionLocalOf { ExpressiveShapes() }
