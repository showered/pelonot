package com.pelonot.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.pelonot.domain.model.UnitSystem

// ==========================================
// Design tokens
// ==========================================

/** Consistent spacing steps. */
data class Spacing(
    val extraSmall: Dp = 4.dp,
    val small: Dp = 8.dp,
    val medium: Dp = 12.dp,
    val large: Dp = 16.dp,
    val extraLarge: Dp = 24.dp,
    val doubleExtraLarge: Dp = 32.dp
)

/**
 * Depth steps, following the Material 3 elevation scale.
 *
 * There used to be two competing sets: this class, and a series of loose
 * top-level `Elevation0`–`Elevation5` vals with entirely different values
 * (0/4/8/12/16/24dp against 0/1/3/6/8/12dp). Components picked whichever was
 * in scope, so nominally equal surfaces rendered at different depths.
 */
data class Elevation(
    val level0: Dp = 0.dp,
    val level1: Dp = 1.dp,
    val level2: Dp = 3.dp,
    val level3: Dp = 6.dp,
    val level4: Dp = 8.dp,
    val level5: Dp = 12.dp
)

data class IconSizes(
    val small: Dp = 16.dp,
    val medium: Dp = 24.dp,
    val large: Dp = 32.dp,
    val extraLarge: Dp = 48.dp
)

/** Durations in milliseconds and the standard Material easing curves. */
data class MotionTokens(
    val durationShort1: Int = 50,
    val durationShort2: Int = 100,
    val durationMedium1: Int = 200,
    val durationMedium2: Int = 250,
    val durationLong1: Int = 300,
    val durationLong2: Int = 400,

    val easingStandard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f),
    val easingEmphasized: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f),
    val easingDecelerated: Easing = CubicBezierEasing(0f, 0f, 0f, 1f),
    val easingAccelerated: Easing = CubicBezierEasing(0.3f, 0f, 1f, 1f)
)

val LocalSpacing = staticCompositionLocalOf { Spacing() }
val LocalElevation = staticCompositionLocalOf { Elevation() }
val LocalIconSizes = staticCompositionLocalOf { IconSizes() }
val LocalMotion = staticCompositionLocalOf { MotionTokens() }

/**
 * The rider's miles-or-kilometres preference, so every surface reads the same
 * one — including the HUD, which is composed from the service rather than the
 * activity and has no ViewModel of its own to thread it through.
 *
 * Deliberately *not* static: this one changes at runtime when the rider flips
 * the setting mid-session, and a `staticCompositionLocalOf` would recompose the
 * entire tree to deliver it.
 */
val LocalUnitSystem = androidx.compose.runtime.compositionLocalOf { UnitSystem.DEFAULT }

val MaterialTheme.spacing: Spacing
    @Composable @ReadOnlyComposable get() = LocalSpacing.current

val MaterialTheme.elevationTokens: Elevation
    @Composable @ReadOnlyComposable get() = LocalElevation.current

val MaterialTheme.iconSizes: IconSizes
    @Composable @ReadOnlyComposable get() = LocalIconSizes.current

val MaterialTheme.motionTokens: MotionTokens
    @Composable @ReadOnlyComposable get() = LocalMotion.current

val MaterialTheme.expressiveShapes: ExpressiveShapes
    @Composable @ReadOnlyComposable get() = LocalExpressiveShapes.current

val MaterialTheme.units: UnitSystem
    @Composable @ReadOnlyComposable get() = LocalUnitSystem.current

// ==========================================
// Colour schemes
// ==========================================

private val PelonotDarkColorScheme = darkColorScheme(
    primary = PrimaryTealDark,
    onPrimary = OnPrimaryDark,
    primaryContainer = PrimaryTealContainerDark,
    onPrimaryContainer = OnPrimaryContainerDark,
    secondary = SecondarySlateDark,
    onSecondary = OnSecondaryDark,
    secondaryContainer = SecondarySlateContainerDark,
    onSecondaryContainer = OnSecondaryContainerDark,
    tertiary = TertiaryAmberDark,
    onTertiary = OnTertiaryDark,
    tertiaryContainer = TertiaryAmberContainerDark,
    onTertiaryContainer = OnTertiaryContainerDark,
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    surfaceContainerLowest = DarkSurfaceContainerLowest,
    surfaceContainerLow = DarkSurfaceContainerLow,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    surfaceContainerHighest = DarkSurfaceContainerHighest,
    inverseSurface = LightSurface,
    inverseOnSurface = InverseOnSurfaceDark,
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
    primaryContainer = PrimaryTealContainerLight,
    onPrimaryContainer = OnPrimaryContainerLight,
    secondary = SecondarySlateLight,
    onSecondary = OnSecondaryLight,
    secondaryContainer = SecondarySlateContainerLight,
    onSecondaryContainer = OnSecondaryContainerLight,
    tertiary = TertiaryAmberLight,
    onTertiary = OnTertiaryLight,
    tertiaryContainer = TertiaryAmberContainerLight,
    onTertiaryContainer = OnTertiaryContainerLight,
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    surfaceContainerLowest = LightSurfaceContainerLowest,
    surfaceContainerLow = LightSurfaceContainerLow,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceContainerHigh,
    surfaceContainerHighest = LightSurfaceContainerHighest,
    inverseSurface = DarkBackground,
    inverseOnSurface = InverseOnSurfaceLight,
    inversePrimary = PrimaryTealDark,
    error = ErrorLight,
    onError = OnErrorLight,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight
)

/**
 * The Pelonot theme.
 *
 * @param useDynamicColor Opt in to Material You wallpaper colours on API 31+.
 *   Defaults to **off**. Previously dynamic colour was applied unconditionally
 *   on API 31+, which meant the entire hand-built Pelonot palette was dead
 *   code on any modern device, and the app's identity changed with the user's
 *   wallpaper — including the metric accent colours the HUD relies on for
 *   at-a-glance readability while riding.
 */
@Composable
fun PelonotTheme(
    darkTheme: Boolean = true,
    useDynamicColor: Boolean = false,
    units: UnitSystem = UnitSystem.DEFAULT,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current

    val colorScheme = when {
        useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        darkTheme -> PelonotDarkColorScheme
        else -> PelonotLightColorScheme
    }

    // The system bar appearance can only be set from an Activity window. This
    // block used to cast unconditionally — `(view.context as Activity)` — which
    // threw ClassCastException when the theme was applied inside the HUD
    // overlay's ComposeView, whose context is the Service. That crashed the
    // overlay every time a ride started.
    val activity = view.context as? Activity
    if (!view.isInEditMode && activity != null) {
        SideEffect {
            WindowCompat.getInsetsController(activity.window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(
        LocalSpacing provides Spacing(),
        LocalElevation provides Elevation(),
        LocalIconSizes provides IconSizes(),
        LocalMotion provides MotionTokens(),
        LocalExpressiveShapes provides ExpressiveShapes(),
        LocalUnitSystem provides units
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = PelonotTypography,
            shapes = PelonotShapes,
            content = content
        )
    }
}
