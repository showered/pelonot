package com.pelonot.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.pelonot.domain.model.HudOpacity
import com.pelonot.domain.model.PowerZone

// ==========================================
// Pelonot colour system
//
// Calm, desaturated surfaces so the saturated metric accents carry all the
// visual weight — a rider glancing down mid-effort should find the number
// they need instantly.
// ==========================================

// --- Neutral surfaces: dark ---
val DarkBackground = Color(0xFF09090B)
val DarkSurface = Color(0xFF141417)
val DarkSurfaceVariant = Color(0xFF27272A)
val DarkSurfaceContainerLowest = Color(0xFF0C0C0E)
val DarkSurfaceContainerLow = Color(0xFF141417)
val DarkSurfaceContainer = Color(0xFF1B1B1F)
val DarkSurfaceContainerHigh = Color(0xFF232327)
val DarkSurfaceContainerHighest = Color(0xFF2C2C31)

// --- Neutral surfaces: light ---
val LightBackground = Color(0xFFF8F9FA)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFECEEF0)
val LightSurfaceContainerLowest = Color(0xFFFFFFFF)
val LightSurfaceContainerLow = Color(0xFFF6F7F8)
val LightSurfaceContainer = Color(0xFFF1F3F4)
val LightSurfaceContainerHigh = Color(0xFFEAECEE)
val LightSurfaceContainerHighest = Color(0xFFE3E6E8)

// --- Brand roles ---
// Container colours are solid tones rather than `primary.copy(alpha = 0.3f)`.
// A translucent container composites against whatever happens to sit behind
// it, so the same component rendered on `background` and on an elevated card
// produced two different colours, and the paired `onPrimaryContainer` was only
// legible against one of them.
val PrimaryTealDark = Color(0xFF80CBC4)
val PrimaryTealLight = Color(0xFF00695C)
val PrimaryTealContainerDark = Color(0xFF00504A)
val PrimaryTealContainerLight = Color(0xFFB2DFDB)
val OnPrimaryDark = Color(0xFF00332C)
val OnPrimaryLight = Color(0xFFFFFFFF)
val OnPrimaryContainerDark = Color(0xFFA7F3EB)
val OnPrimaryContainerLight = Color(0xFF00201C)

val SecondarySlateDark = Color(0xFF90A4AE)
val SecondarySlateLight = Color(0xFF455A64)
val SecondarySlateContainerDark = Color(0xFF35444C)
val SecondarySlateContainerLight = Color(0xFFCFD8DC)
val OnSecondaryDark = Color(0xFF17232A)
val OnSecondaryLight = Color(0xFFFFFFFF)
val OnSecondaryContainerDark = Color(0xFFD3E2EA)
val OnSecondaryContainerLight = Color(0xFF16242B)

val TertiaryAmberDark = Color(0xFFFFB74D)
val TertiaryAmberLight = Color(0xFFB44B00)
val TertiaryAmberContainerDark = Color(0xFF5C3A00)
val TertiaryAmberContainerLight = Color(0xFFFFDDB8)
val OnTertiaryDark = Color(0xFF3E2723)
val OnTertiaryLight = Color(0xFFFFFFFF)
val OnTertiaryContainerDark = Color(0xFFFFDDB8)
val OnTertiaryContainerLight = Color(0xFF2E1500)

// --- Text ---
val DarkTextPrimary = Color(0xFFF4F4F5)
val DarkTextSecondary = Color(0xFFA1A1AA)
val LightTextPrimary = Color(0xFF1C1B1F)
val LightTextSecondary = Color(0xFF4A454F)

val InverseOnSurfaceDark = Color(0xFF121212)
val InverseOnSurfaceLight = Color(0xFFF4F4F5)

// --- Outlines ---
val OutlineDark = Color(0xFF6B6B72)
val OutlineLight = Color(0xFF79747E)
val OutlineVariantDark = Color(0xFF3A3A3F)
val OutlineVariantLight = Color(0xFFD5D2DA)

// --- Error ---
// Was #FF1744 on both themes, which fails contrast against a white surface.
val ErrorDark = Color(0xFFFF6B7E)
val ErrorLight = Color(0xFFB3261E)
val ErrorContainerDark = Color(0xFF601410)
val ErrorContainerLight = Color(0xFFF9DEDC)
val OnErrorDark = Color(0xFF3A0A0C)
val OnErrorLight = Color(0xFFFFFFFF)
val OnErrorContainerDark = Color(0xFFFFDAD6)
val OnErrorContainerLight = Color(0xFF410E0B)

// --- Live metric accents ---
// Reserved exclusively for live telemetry, so a saturated colour on screen
// always means "this is a number changing right now".
val MetricCadenceCyan = Color(0xFF00E5FF)
val MetricPowerCoral = Color(0xFFFF5252)
val MetricHeartRateGreen = Color(0xFF00E676)

/**
 * Resistance — the knob.
 *
 * Violet because it has to be unmistakable beside cadence: those two sit next
 * to each other on the HUD and are the only two things the rider can actually
 * change, so confusing them is the one mix-up that costs an interval.
 */
val MetricResistanceViolet = Color(0xFFB388FF)

// --- Alerts ---
val AlertRed = Color(0xFFFF1744)
val AlertAmber = Color(0xFFFF9100)
val AlertGreen = Color(0xFF00E676)

// --- Power zone colours (Coggan 7-zone) ---
// Cool through warm, so intensity reads without needing the number.
val PowerZone1Recovery = Color(0xFF9E9E9E)
val PowerZone2Endurance = Color(0xFF5C6BC0)
val PowerZone3Tempo = Color(0xFF26A69A)
val PowerZone4Threshold = Color(0xFFFFA726)
val PowerZone5VO2Max = Color(0xFFEF5350)
val PowerZone6Anaerobic = Color(0xFFAB47BC)
val PowerZone7Neuromuscular = Color(0xFFD81B60)

/** Gradients for hero surfaces, kept as pairs so they stay two-stop and calm. */
object PelonotGradients {
    val TealFlow = listOf(Color(0xFF00695C), Color(0xFF00897B))
    val DarkFlow = listOf(DarkSurface, DarkBackground)
    val PowerFlow = listOf(MetricPowerCoral, Color(0xFFFF7A00))
    val HeartRateFlow = listOf(MetricHeartRateGreen, Color(0xFF00B0FF))
}

/**
 * The colour for a [PowerZone].
 *
 * Replaces a loose set of `PowerZone1_Recovery`-style vals that each caller
 * mapped by hand — including two extra shades, `PowerZone8_Recovery` and
 * `PowerZone9_Light`, for zones that do not exist in a 7-zone model.
 */
val PowerZone.color: Color
    get() = when (this) {
        PowerZone.Z1 -> PowerZone1Recovery
        PowerZone.Z2 -> PowerZone2Endurance
        PowerZone.Z3 -> PowerZone3Tempo
        PowerZone.Z4 -> PowerZone4Threshold
        PowerZone.Z5 -> PowerZone5VO2Max
        PowerZone.Z6 -> PowerZone6Anaerobic
        PowerZone.Z7 -> PowerZone7Neuromuscular
    }

/**
 * How see-through the rider is allowed to make the HUD (11.1b.2).
 *
 * Derived from the colours above rather than picked. Composite the strip's own
 * panel over the brightest thing a film can be, and take the least opaque it
 * can be while **every** colour it draws text in still passes WCAG — AA for the
 * labels, the large-text 3:1 for the 42sp metric numbers.
 *
 * The metric accents are what set it. A rider reads cadence by its cyan and
 * power by its coral, so those cannot be brightened to buy contrast; the grey
 * labels can, and are — see `HudLabelColor`. Deriving the floor from the white
 * clock alone gave 0.59, at which the coral power figure sat at a contrast
 * ratio of 1.55 against the frame that suits it worst. It is about 0.76 with all of them counted.
 *
 * The HUD is always composed `darkTheme = true` with dynamic colour off (see
 * `HudOverlayManager`), so these are the only colours it is ever drawn in,
 * whatever the app's own theme is set to.
 */
val HudMinimumOpacity: Float = HudOpacity.lowestReadable(
    panel = DarkSurfaceContainerLowest.rgb,
    samples = listOf(
        HudOpacity.TextSample(DarkTextPrimary.rgb),
        HudOpacity.TextSample(MetricCadenceCyan.rgb, HudOpacity.MIN_CONTRAST_LARGE),
        HudOpacity.TextSample(MetricResistanceViolet.rgb, HudOpacity.MIN_CONTRAST_LARGE),
        HudOpacity.TextSample(MetricPowerCoral.rgb, HudOpacity.MIN_CONTRAST_LARGE),
        HudOpacity.TextSample(MetricHeartRateGreen.rgb, HudOpacity.MIN_CONTRAST_LARGE)
    )
)

/**
 * The strip's secondary label colour, brightened as far as the rider's chosen
 * transparency requires (11.1b.2).
 *
 * `DarkTextSecondary` is a quiet grey by design, and at 0.76 against the worst
 * frame it has a contrast ratio of about 2.3 — the labels under the numbers would be
 * the first thing to go. It has no meaning in its greyness, so rather than drag
 * the floor up to 0.81 for everybody it moves towards [DarkTextPrimary] by
 * exactly as much as it has to and no further. At full opacity it does not move
 * at all.
 */
fun hudLabelColor(opacity: Float): Color {
    val lift = HudOpacity.liftToward(
        from = DarkTextSecondary.rgb,
        to = DarkTextPrimary.rgb,
        panel = DarkSurfaceContainerLowest.rgb,
        opacity = opacity,
        target = HudOpacity.MIN_CONTRAST
    )
    return Color(HudOpacity.blend(DarkTextSecondary.rgb, DarkTextPrimary.rgb, lift) or ALPHA_OPAQUE)
}

/** The packed `0xRRGGBB` behind a [Color], which is what the contrast maths takes. */
private val Color.rgb: Int get() = toArgb() and 0xFFFFFF

private const val ALPHA_OPAQUE = 0xFF shl 24
