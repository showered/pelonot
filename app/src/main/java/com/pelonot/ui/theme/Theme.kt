package com.pelonot.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

// ==========================================
// Design Tokens - Systems Definitions
// ==========================================

/**
 * Spacing System
 */
data class Spacing(
    val extraSmall: Dp = 4.dp,
    val small: Dp = 8.dp,
    val medium: Dp = 12.dp,
    val large: Dp = 16.dp,
    val extraLarge: Dp = 24.dp,
    val doubleExtraLarge: Dp = 32.dp
)
val LocalSpacing = staticCompositionLocalOf { Spacing() }

/**
 * Elevation System
 */
data class Elevation(
    val level0: Dp = 0.dp,
    val level1: Dp = 1.dp,
    val level2: Dp = 3.dp,
    val level3: Dp = 6.dp,
    val level4: Dp = 8.dp,
    val level5: Dp = 12.dp
)
val LocalElevation = staticCompositionLocalOf { Elevation() }

/**
 * Corner Radius System (Expressive Shapes)
 */
data class CornerRadius(
    val small: Dp = 4.dp,
    val medium: Dp = 8.dp,
    val large: Dp = 12.dp,
    val extraLarge: Dp = 16.dp,
    val container: Dp = 24.dp,
    val pill: Dp = 999.dp
)
val LocalCornerRadius = staticCompositionLocalOf { CornerRadius() }

/**
 * Icon Sizing System
 */
data class IconSizes(
    val small: Dp = 16.dp,
    val medium: Dp = 24.dp,
    val large: Dp = 32.dp,
    val extraLarge: Dp = 48.dp
)
val LocalIconSizes = staticCompositionLocalOf { IconSizes() }

/**
 * Motion System - Durations and Easings
 */
data class MotionTokens(
    val durationShort1: Int = 50,
    val durationShort2: Int = 100,
    val durationMedium1: Int = 200,
    val durationMedium2: Int = 250,
    val durationLong1: Int = 300,
    val durationLong2: Int = 400,

    val easingEmphasized: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f),
    val easingDecelerated: Easing = CubicBezierEasing(0f, 0f, 0f, 1f),
    val easingAccelerated: Easing = CubicBezierEasing(0.3f, 0f, 1f, 1f),
    val easingStandard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
)
val LocalMotion = staticCompositionLocalOf { MotionTokens() }

// ==========================================
// Theme Extension Properties
// ==========================================

val MaterialTheme.spacing: Spacing
    @Composable
    @ReadOnlyComposable
    get() = LocalSpacing.current

val MaterialTheme.elevationTokens: Elevation
    @Composable
    @ReadOnlyComposable
    get() = LocalElevation.current

val MaterialTheme.cornerRadius: CornerRadius
    @Composable
    @ReadOnlyComposable
    get() = LocalCornerRadius.current

val MaterialTheme.iconSizes: IconSizes
    @Composable
    @ReadOnlyComposable
    get() = LocalIconSizes.current

val MaterialTheme.motionTokens: MotionTokens
    @Composable
    @ReadOnlyComposable
    get() = LocalMotion.current

val MaterialTheme.expressiveShapes: ExpressiveShapes
    @Composable
    @ReadOnlyComposable
    get() = LocalExpressiveShapes.current

// ==========================================
// Material 3 Color Schemes (Calm, Premium)
// ==========================================

private val PelonotDarkColorScheme = darkColorScheme(
    primary = PrimaryTealDark,
    onPrimary = OnPrimaryDark,
    primaryContainer = PrimaryTealDark.copy(alpha = 0.3f),
    onPrimaryContainer = OnPrimaryDark.copy(alpha = 0.9f),
    secondary = SecondarySlateDark,
    onSecondary = OnSecondaryDark,
    secondaryContainer = SecondarySlateDark.copy(alpha = 0.3f),
    onSecondaryContainer = OnSecondaryDark.copy(alpha = 0.9f),
    tertiary = TertiaryAmberDark,
    onTertiary = OnTertiaryDark,
    tertiaryContainer = TertiaryAmberDark.copy(alpha = 0.3f),
    onTertiaryContainer = OnTertiaryDark.copy(alpha = 0.9f),
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceVariant,
    onBackground = DarkTextPrimary,
    onSurface = DarkTextPrimary,
    onSurfaceVariant = DarkTextSecondary,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerLow = DarkSurface.copy(alpha = 0.95f),
    surfaceContainerHigh = DarkSurface.copy(alpha = 0.85f),
    surfaceContainerLowest = DarkSurface.copy(alpha = 0.98f),
    inverseOnSurface = InverseOnSurfaceDark,
    inverseSurface = LightSurface,
    inversePrimary = PrimaryTealLight,
    error = ErrorDark,
    onError = OnErrorDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark
)

private val PelonotLightColorScheme = lightColorScheme(
    primary = PrimaryTealLight,
    onPrimary = OnPrimaryLight,
    primaryContainer = PrimaryTealLight.copy(alpha = 0.15f),
    onPrimaryContainer = OnPrimaryLight.copy(alpha = 0.9f),
    secondary = SecondarySlateLight,
    onSecondary = OnSecondaryLight,
    secondaryContainer = SecondarySlateLight.copy(alpha = 0.15f),
    onSecondaryContainer = OnSecondaryLight.copy(alpha = 0.9f),
    tertiary = TertiaryAmberLight,
    onTertiary = OnTertiaryLight,
    tertiaryContainer = TertiaryAmberLight.copy(alpha = 0.15f),
    onTertiaryContainer = OnTertiaryLight.copy(alpha = 0.9f),
    background = LightBackground,
    surface = LightSurface,
    surfaceVariant = LightSurfaceVariant,
    onBackground = LightTextPrimary,
    onSurface = LightTextPrimary,
    onSurfaceVariant = LightTextSecondary,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerLow = LightSurface.copy(alpha = 0.98f),
    surfaceContainerHigh = LightSurface.copy(alpha = 0.85f),
    surfaceContainerLowest = LightSurface.copy(alpha = 0.95f),
    inverseOnSurface = InverseOnSurfaceLight,
    inverseSurface = DarkBackground,
    inversePrimary = PrimaryTealDark,
    error = ErrorLight,
    onError = OnErrorLight,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight
)

// ==========================================
// Pelonot Theme Composable
// ==========================================

@Composable
fun PelonotTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current

    // Use dynamic color scheme on Android 12+ (API 31+), fall back to
    // static Pelonot color schemes on older APIs.
    val colorScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (darkTheme) {
            dynamicDarkColorScheme(context)
        } else {
            dynamicLightColorScheme(context)
        }
    } else {
        if (darkTheme) PelonotDarkColorScheme else PelonotLightColorScheme
    }

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    CompositionLocalProvider(
        LocalSpacing provides Spacing(),
        LocalElevation provides Elevation(),
        LocalCornerRadius provides CornerRadius(),
        LocalIconSizes provides IconSizes(),
        LocalMotion provides MotionTokens(),
        LocalExpressiveShapes provides ExpressiveShapes()
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = PelonotTypography,
            content = content
        )
    }
}
val Elevation0 = 0.dp  // Base level
val Elevation1 = 4.dp  // Subtle
val Elevation2 = 8.dp  // Moderate
val Elevation3 = 12.dp // Strong
val Elevation4 = 16.dp // Prominent
val Elevation5 = 24.dp // Maximum

// Follows Material Expressive Design System
// Elevation hierarchy follows WHOOP's depth guidelines
