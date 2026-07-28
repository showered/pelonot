package com.pelonot.ui.theme

import androidx.compose.ui.graphics.Color

// ==========================================
// Material Expressive - Color System
// Inspired by Google Health, WHOOP, and Fitbit
// ==========================================

// --- Calm Base Palette (Dark Theme) ---
val DarkBackground = Color(0xFF09090B)       // Zinc 950 - elegant, deep dark
val DarkSurface = Color(0xFF18181B)          // Zinc 900 - calm, premium container bg
val DarkSurfaceVariant = Color(0xFF27272A)   // Zinc 800 - subtle divider or input bg
val DarkSurfaceContainer = Color(0xFF1F1F23)  // Zinc 850 - intermediate container level

// --- Calm Base Palette (Light Theme) ---
val LightBackground = Color(0xFFF8F9FA)      // Very light warm grey
val LightSurface = Color(0xFFFFFFFF)         // Pure white
val LightSurfaceVariant = Color(0xFFF1F3F4)  // Google style light grey
val LightSurfaceContainer = Color(0xFFE8EAED) // Light container background

// --- Premium Branding Accent Roles ---
// Warm, calm, sophisticated theme branding
val PrimaryTealDark = Color(0xFF80CBC4)      // Soft, calm teal (Dark Mode primary)
val PrimaryTealLight = Color(0xFF00695C)     // Deep, trustworthy teal (Light Mode primary)

val SecondarySlateDark = Color(0xFF90A4AE)    // Cool slate blue (Dark Mode secondary)
val SecondarySlateLight = Color(0xFF455A64)   // Deep slate blue (Light Mode secondary)

val TertiaryAmberDark = Color(0xFFFFB74D)     // Warm honey gold
val TertiaryAmberLight = Color(0xFFE65100)    // Deep, energetic orange-amber

// --- Text and Content Colors ---
val OnPrimaryDark = Color(0xFF00332C)
val OnPrimaryLight = Color(0xFFFFFFFF)

val DarkTextPrimary = Color(0xFFF4F4F5)      // Zinc 50 - extreme readability
val DarkTextSecondary = Color(0xFFA1A1AA)    // Zinc 400 - supporting information
val DarkTextDim = Color(0xFF71717A)          // Zinc 500 - deactivated/extremely secondary

val LightTextPrimary = Color(0xFF1C1B1F)     // Material 3 default high contrast
val LightTextSecondary = Color(0xFF4A454F)   // Muted supporting info
val LightTextDim = Color(0xFF79747E)         // De-emphasized details

// --- Live Workout Metric Accent Colors (Reserved exclusively for live stats!) ---
// Avoid neon overload: we use glowing colors strictly for live metric visualizations
val MetricCadenceCyan = Color(0xFF00E5FF)    // Cyan A400 - high visibility cadence
val MetricPowerCoral = Color(0xFFFF5252)     // Red A200 - vibrant power
val MetricHeartRateGreen = Color(0xFF00E676) // Green A400 - clinical/fitness HR green

// --- Zone and Alert Colors ---
val AlertRed = Color(0xFFFF1744)             // Bright warning
val AlertOrange = Color(0xFFFF9100)          // Caution
val AlertGreen = Color(0xFF00E676)           // Success / Safe zone

// --- Power Zone Colors (Coggan 7-Zone) ---
val PowerZone1_Recovery = Color(0xFF9E9E9E)      // Grey
val PowerZone2_Endurance = Color(0xFF5C6BC0)     // Muted Blue
val PowerZone3_Tempo = Color(0xFF26A69A)         // Soft Teal
val PowerZone4_Threshold = Color(0xFFFFA726)     // Soft Orange
val PowerZone5_VO2Max = Color(0xFFEF5350)        // Soft Red
val PowerZone6_Anaerobic = Color(0xFFAB47BC)     // Soft Purple
val PowerZone7_Neuromuscular = Color(0xFFD81B60)  // Deep Pink

// --- Premium Reusable Gradients ---
object PelonotGradients {
    val TealFlow = listOf(Color(0xFF00695C), Color(0xFF00897B))
    val DarkFlow = listOf(Color(0xFF18181B), Color(0xFF09090B))
    val PowerZone = listOf(Color(0xFFFF5252), Color(0xFFFF7A00))
    val HeartRate = listOf(Color(0xFF00E676), Color(0xFF00B0FF))
    val PremiumOverlay = listOf(Color(0xFF27272A), Color(0xFF18181B))
}

// --- Backward-compatible aliases for existing code ---
val CadenceCyan = MetricCadenceCyan
val PowerCoral = MetricPowerCoral
val HeartRateGreen = MetricHeartRateGreen
val ZoneAlertRed = AlertRed
val TextPrimary = DarkTextPrimary
val TextSecondary = DarkTextSecondary