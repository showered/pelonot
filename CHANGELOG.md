# Changelog

## Phase 0 — Project Scaffolding & Build System ✅

**Goal:** Android project skeleton targeting API 24 with all dependencies wired.

**Files created:**
- `app/build.gradle.kts` — Dependencies: Compose BOM 2024.10, Material 3, Room 2.6.1 (KSP), Coroutines 1.9, Supabase SDK 3.0.2, WorkManager 2.9.1, Navigation Compose 2.8.4
- `build.gradle.kts` — Root with AGP 8.7.3, Kotlin 2.0.21, Compose compiler plugin, Serialization plugin
- `settings.gradle.kts` — Google + Maven Central repos, `:app` module
- `gradle.properties` — AndroidX, JVM args
- `local.properties` — SDK path (`~/Library/Android/sdk`)
- `.gitignore` — Common Android ignores
- `gradle/wrapper/` — Gradle 8.9 wrapper (Java 17)
- `LICENSE` — MIT License

**Build status:** `./gradlew assembleDebug` — SUCCESSFUL

---

## Phase 1 — Local Database (Room) & Supabase Cloud Sync ✅

**Goal:** Offline-first data layer with Room entities, DAOs, and Supabase cloud backend.

### Room (Local) — 4 entities, 4 DAOs, AppDatabase

**Entities:**
- `UserEntity` (`profiles`): `localUserId` (PK), `name`, `weightKg`, `ftpWatts`, `themePreference`, `createdAt`
- `ClassTemplateEntity` (`class_templates`): `id` (PK), `title`, `category`, `durationSec`, `intervalsJson`, `createdAt`
- `WorkoutEntity` (`workouts`): `id` (PK), FK→User, FK→ClassTemplate, `durationSec`, `totalOutputKj`, `totalDistanceKm`, `avgCadence`, `avgPower`, `avgHr`, `intentModifier`, `rpeRating`, `timestamp`
- `WorkoutMetricEntity` (`workout_metrics`): `id` (auto PK), FK→Workout (CASCADE), `timestampSec`, `cadence`, `resistance`, `power`, `heartRate`

**DAOs:**
- `UserDao` — CRUD operations and Flow queries
- `ClassTemplateDao` — Batch insert, category filtering, template count
- `WorkoutDao` — Personal Best, Personal Average, Household Best queries by duration range; all-time best; recent N workouts for FTP analysis; workout-by-class queries
- `WorkoutMetricDao` — Time-series insert/get, power array extraction for 20-min peak FTP calculation

**AppDatabase:** Singleton pattern with all 4 entities registered, version 1.

### Supabase (Cloud) — SQL migration + Kotlin client

**Migration (`supabase/migration.sql`):**
- Tables: `profiles` (UUID PK), `class_templates` (text PK + JSONB intervals), `workouts` (UUID PK + JSONB `metrics_payload`)
- Row Level Security enabled on all tables
- RLS policies: public read for class_templates, user-scoped read/insert for profiles and workouts
- Seed data: 40 class templates (Aerobic Engine x10, HIIT & Heavy Climbs x10, Tabata Bursts x10, Threshold Pyramids x10)

**Kotlin client:**
- `SupabaseConfig.kt` — Project URL (`https://podsmtujqarlqhvorpdh.supabase.co`) + anon key
- `SupabaseClient.kt` — Supabase client singleton with Postgrest, `MetricSnapshot` DTO
- `SupabaseSyncRepository.kt` — `syncWorkout()`, `syncProfile()`, `fetchClassTemplates()`, `updateFtp()`

**Build status:** `./gradlew assembleDebug` — SUCCESSFUL (KSP processes Room, Supabase deps resolve)

---

## Phase 2-5 — Telemetry, HUD Overlay & Power Zone Engine ✅

**Goal:** Real-time sensor data collection, floating HUD overlay, and power zone calculations.

### Telemetry Engine

**Files created:**
 - `SensorTick.kt` — Data classes for `SensorTick` and `SensorReading`
 - `SerialPortReader.kt` — Reads telemetry from `/dev/ttyS1` with cadence/power calculation
 - `BleHeartRateManager.kt` — BLE heart rate monitor scanning and GATT connection
 - `SensorRepository.kt` — Singleton merging serial + BLE data with auto-reconnect
 - `WorkoutMetricsCalculator.kt` — Rolling averages, total output, distance estimation

### HUD Overlay

**Files created:**
 - `OverlayPermissionHelper.kt` — SYSTEM_ALERT_WINDOW permission check and request
 - `HudOverlayManager.kt` — WindowManager-based floating overlay with ComposeView
 - `PowerZoneCalculator.kt` — Coggan 7-zone power model with intent modifiers
 - `HudOverlayMain.kt` — HUD UI with metrics, targets, and controls

**Features:**
 - Draggable overlay with drag handle
 - Real-time cadence, power, and heart rate display
 - Target zone indicators with alert animations
 - Pause/Resume/Stop controls
 - Power zone calculation based on FTP

---

## Phase 6 — Main App UI ✅

**Goal:** The full app experience — profile selection, dashboard, class library, settings.

**Files created:**
 - `ProfileSelectorScreen.kt` — Grid of user profiles with guest mode
  - `MainDashboardScreen.kt` — **Redesigned** to use Material Expressive components:
    - Hero card with FTP metric and start‑ride button
    - Primary and secondary buttons with spring animations
    - Metric cards for today’s progress and recent ride
    - Status chip for FTP stability
    - Subtle fade/scale animations for card entrance
 - `PreRideIntentPrompt.kt` — Intent selection dialog
 - `ClassLibraryScreen.kt` — List of class templates
 - `PostRideSummaryScreen.kt` — Ride summary with RPE selection
 - `SettingsScreen.kt` — Settings for FTP, weight, theme
 - `NavGraph.kt` — Navigation setup with NavHost
 - `ProfileCreationDialog.kt` — Profile creation with name, weight, FTP
 - `ClassDetailScreen.kt` — Class detail with interval breakdown
 - `JustRideScreen.kt` — Free ride without class structure

**Screens:**
 - Profile selector with 2-column grid
 - Dashboard with workout controls
 - Intent prompt (Reach New Milestones / Just Stay Fit)
 - Class library with duration display
 - Post-ride summary with RPE buttons
 - Settings with form inputs

---

## Phase 7 — Auto-FTP Engine & Cloud Sync ✅

**Goal:** Post-ride analysis, FTP auto-detection, and background sync to Supabase.

**Files created:**
 - `PostWorkoutAnalyzer.kt` — 20-minute peak power, biometric decoupling, RPE analysis
 - `FtpBreakthroughDialog.kt` — FTP update prompt dialog
 - `WorkoutSyncWorker.kt` — Background sync worker for Supabase
 - `ClassTemplateSeeder.kt` — Seeds class templates from assets

**Features:**
 - 20-minute peak power FTP estimation
 - Biometric decoupling detection
 - RPE-based FTP increase suggestions
 - WorkManager-based sync with exponential backoff

---

## Phase 8 — Polish & Testing (in progress)

**Goal:** Production readiness.

**Files created:**
 - `ZoneAlertManager.kt` — Haptic feedback and TTS audio cues for zone alerts

**Recent changes:**
 - `WorkoutService.kt` — Added `recoverIncompleteWorkout()` for crash recovery, uses `getIncompleteWorkout()` and `getLastMetricForWorkout()` DAO methods
 - `WorkoutDao.kt` — Added `getIncompleteWorkout()` query for crash recovery
 - `WorkoutMetricDao.kt` — Added `getLastMetricForWorkout()` query for crash recovery
 - `PostRideSummaryScreen.kt` — Added `isGuest` parameter to show guest mode prompt
 - `NavGraph.kt` — Updated post_ride route to accept `isGuest` parameter

**Tests:**
 - `PowerZoneCalculatorTest.kt` — Unit tests for all 7 zones, edge cases, and intent modifiers
 - `PostWorkoutAnalyzerTest.kt` — Unit tests for FTP calculation, biometric decoupling, and RPE suggestions
 - `WorkoutDaoTest.kt` — Instrumented tests for Room DAO operations
 - `WorkoutServiceTest.kt` — Instrumented tests for WorkoutState and WorkoutSession

---

## Design System: Material Expressive Color Tokens (completed)

**Goal:** Audit current color palette and define Material Expressive color tokens for primary, secondary, tertiary, error, surface, and on-surface variants.

**Files modified:**
- `Color.kt` — Complete Material Expressive color system:
  - Added comprehensive color tokens for all Material 3 roles (primary, secondary, tertiary, error, surface, on-surface variants)
  - Added on-color tokens: `OnPrimary`, `OnSecondary`, `OnTertiary`
  - Added on-surface container tokens for elevation hierarchy
  - Added inverse surface tokens for inverted UI states
  - Added proper error color tokens with containers
  - Added outline tokens for borders and dividers
  - Maintained existing live workout metrics and power zone colors
  - Backward-compatible aliases preserved for existing code

- `Theme.kt` — Updated Material 3 color schemes:
  - Dark scheme now uses explicit tokens (`OnSecondaryDark`, `OnTertiaryDark`, `ErrorDark`, `OutlineDark`, etc.)
  - Light scheme now uses explicit tokens (`OnSecondaryLight`, `OnTertiaryLight`, `ErrorLight`, `OutlineLight`, etc.)
  - Proper tonal palette implementation for all color roles

**Build status:** `./gradlew assembleDebug` — SUCCESSFUL


## Design System: Material Expressive Shapes (added)

**Goal:** Create a dedicated `Shapes.kt` with expressive corner shape tokens and migrate all hardcoded shape references to use the tokenized system.

**Files created:**
- `app/src/main/java/com/pelonot/ui/theme/Shapes.kt` — Expressive shape token system:
  - Rounded rectangle shapes: `extraSmall` (4dp) through `container` (24dp)
  - `pill` shape (999dp) for fully rounded elements
  - Cut corner shapes: `cutCornerSmall`, `cutCornerMedium`, `cutCornerLarge`
  - Top-only rounded shapes: `topRoundedSmall`, `topRoundedLarge` (for dialogs, bottom sheets)
  - Bottom-only rounded shapes: `bottomRoundedSmall`, `bottomRoundedLarge` (for headers, top bars)
  - `LocalExpressiveShapes` CompositionLocal for theme integration

**Files modified:**
- `Theme.kt` — Added `LocalExpressiveShapes` provider in `CompositionLocalProvider` and `MaterialTheme.expressiveShapes` extension property
- `ExpressiveComponents.kt` — Replaced all hardcoded `RoundedCornerShape` and `CircleShape` references with `MaterialTheme.shapes.*` tokens
- `HudOverlayMain.kt` — Replaced all hardcoded `RoundedCornerShape` references with `MaterialTheme.shapes.*` tokens

**Design Philosophy:**
- All shape values are centralized in `Shapes.kt` — no duplicated corner radius values
- Consistent shape language across all components (cards, buttons, chips, dialogs, panels)
- Cut corners provide premium visual variety for accent elements
- Top/bottom-only rounded shapes enable proper dialog and sheet styling
- Backward compatible — all existing composables continue to work with tokenized shapes

---

## Design System: Dashboard Redesign — Material Expressive Card Layouts (8.11.17)

**Goal:** Redesign `MainDashboardScreen` with Material Expressive card layouts, proper elevation hierarchy, and surface tonal variants.

**Files modified:**
- `MainDashboardScreen.kt` — Complete redesign:
  - Expressive greeting header with multi-line typography (headlineSmall greeting + headlineLarge name)
  - FTP hero card with `surfaceContainer` background, elevation level2, and teal gradient accent bar
  - Primary action card ("Just Ride") using `primaryContainer` color with leading icon
  - Secondary action cards ("Begin Class", "Settings") using `surfaceVariant` color with icon + subtitle
  - Progress section with `surfaceContainerLow` metric cards featuring tonal icon circles
  - FTP status badge with pill shape and tonal background
  - All cards use proper elevation tokens (level1–level2) and shape tokens (large, extraLarge, pill)
  - Removed dependency on `ExpressiveComponents.kt` — all components are now self-contained private composables
  - Uses `AutoMirrored` icons for `DirectionsBike` and `TrendingUp` to eliminate deprecation warnings
  - Clean build with zero warnings

**Design Philosophy:**
- Cards use Material 3 surface tonal variants (`surfaceContainer`, `surfaceContainerLow`, `surfaceVariant`, `primaryContainer`) for visual depth without relying on elevation alone
- Elevation hierarchy: level0 (background), level1 (action cards, metric cards), level2 (hero card)
- Shape hierarchy: extraLarge (hero, primary), large (secondary, metric), pill (badge, icon circles)
- All spacing uses the centralized `MaterialTheme.spacing` token system
- All shapes use `MaterialTheme.shapes.*` and `MaterialTheme.expressiveShapes.*` token system

---

## Design System: Material Expressive Foundation (added)

**Goal:** Create a cohesive, reusable design system and visual language for the app, inspired by Google Health, Fitbit, WHOOP, and Garmin Connect.

**Files modified:**
- `Color.kt` — Complete Material Expressive color system overhaul:
  - Calm, premium base palette (Zinc 950/900/800 for dark; F8F9FA for light)
  - Proper color roles: primary (teal), secondary (slate), tertiary (amber)
  - Reserved bright neon colors (`MetricCadenceCyan`, `MetricPowerCoral`, `MetricHeartRateGreen`) exclusively for live workout metrics
  - Zone and alert colors (`AlertRed`, `AlertOrange`, `AlertGreen`)
  - Coggan 7-zone power zone palette
  - Reusable gradient definitions (`PelonotGradients`)
  - Backward-compatible aliases for existing code (`CadenceCyan`, `PowerCoral`, `HeartRateGreen`, `TextPrimary`, `TextSecondary`)

- `Type.kt` — Expressive typography scale:
  - Oversized hero metrics: `displayLarge` (72sp Black), `displayMedium` (48sp ExtraBold)
  - Bold expressive headings: `headlineLarge` (30sp ExtraBold) through `headlineSmall`
  - Complete Material 3 type scale with proper font weights and letter spacing
  - `FitnessTypography` object with `HeroMetricGiant` (96sp), `MetricValue` (54sp), `TargetValue` (24sp)

- `Theme.kt` — Design token system and theme updates:
  - `Spacing` data class: 4dp-32dp scale
  - `Elevation` data class: level0-level5 (0dp-12dp)
  - `CornerRadius` data class: small (4dp) to pill (999dp), including container (24dp)
  - `IconSizes` data class: small (16dp) to extraLarge (48dp)
  - `MotionTokens` data class: duration presets (50ms-400ms) and easing curves (emphasized, decelerated, accelerated, standard)
  - `MaterialTheme` extension properties for all token access
  - Color schemes updated to use `PrimaryTealDark/Light`, `SecondarySlateDark/Light`, `TertiaryAmberDark/Light`
  - `CompositionLocalProvider` wrapping all design tokens

**Files created:**
- `app/src/main/java/com/pelonot/ui/components/ExpressiveComponents.kt` — Reusable expressive composables:
  - `PrimaryButton` — Teal gradient pill button with spring-based scale animation
  - `SecondaryButton` — Outlined pill button with press animation
  - `HeroCard` — Premium card container with title, subtitle, action, and content slot
  - `SectionHeader` — Bold section header with optional subtitle and action
  - `MetricCard` — Card with animated metric value display using `AnimatedContent`
  - `HeroMetric` — Giant oversized metric display (96sp) with label, value, unit, subtitle
  - `StatusChip` — Animated color chip with optional icon
  - `InfoCard` — Insight card with leading icon
  - `ExpressiveTopBar` — Top bar with expressive title/subtitle and action slots
  - `BottomActionBar` — Bottom bar with rounded top corners

**Design Philosophy:**
- Premium fitness application feel, not an engineering dashboard
- Calm surface colors with reserved bright accents for live metrics only
- Strong visual hierarchy with oversized hero typography
- Spring animations, AnimatedContent transitions, and subtle scale/fade animations
- Motion communicates state changes, not decoration
- All styling is centralised in theme tokens — no duplicated values

---

## Design System: Dynamic Color Scheme (added)

**Goal:** Implement Material 3 dynamic color scheme with proper tonal palettes for light and dark themes.

**Files modified:**
- `Theme.kt` — Dynamic color scheme implementation:
  - Added `dynamicDarkColorScheme`/`dynamicLightColorScheme` support for Android 12+ (API 31+)
  - Falls back to static Pelonot color schemes on older APIs (minSdk 24)
  - Expanded both dark and light color schemes with full tonal palette roles:
    - `primaryContainer`/`onPrimaryContainer` for elevated primary surfaces
    - `secondaryContainer`/`onSecondaryContainer` for elevated secondary surfaces
    - `tertiaryContainer`/`onTertiaryContainer` for elevated tertiary surfaces
    - `surfaceContainer`/`surfaceContainerLow`/`surfaceContainerHigh`/`surfaceContainerLowest` for consistent depth hierarchy
    - `inverseOnSurface`/`inverseSurface`/`inversePrimary` for inverted UI states
    - `onError`/`errorContainer`/`onErrorContainer` for complete error color roles
    - `outline`/`outlineVariant` for borders and dividers
  - Added `LocalContext` import for dynamic color scheme resolution
  - Added `Build.VERSION.SDK_INT` check for API-level conditional dynamic color

**Design Philosophy:**
- Dynamic color adapts to system wallpaper on Android 12+ for personalized theming
- Static fallback ensures consistent branding on older Android versions
- Full tonal palette ensures proper contrast and visual hierarchy across all components
- Container roles enable Material 3's elevated surface system for depth

---

## Design System: Material Expressive Typography — Inter Font (added)

**Goal:** Replace system default font with Inter variable font (SIL Open Font License) for a Google Sans-like Material Expressive aesthetic that's free to use.

**Files created:**
- `app/src/main/res/font/inter_variable.ttf` — Inter variable font (one file covers all weights from Thin to Black)
- `app/src/main/res/font/inter.xml` — Font family XML descriptor for Android resource system

**Files modified:**
- `Type.kt` — All `FontFamily.Default` references replaced with `InterFontFamily` (loaded from `R.font.inter_variable`), including `FitnessTypography` helper styles
- `PLAN.md` — Added `8.11.0` and `8.11.0a` entries under "Typography (Material Expressive)" section

**Design Philosophy:**
- Inter is a free, open-source (SIL OFL) typeface designed by Rasmus Andersson — the same designer behind Google Sans
- Variable font format means a single `.ttf` file covers all weights (100-900), reducing APK size vs. bundling individual weight files
- Clean, modern, highly legible at both small body sizes and large display sizes — ideal for fitness metrics
- No licensing restrictions: Inter is freely usable in any commercial or open-source project

**Build status:** `./gradlew assembleDebug` — SUCCESSFUL

---

## GitHub

- Remote: `https://github.com/showered/pelonot.git`
- Branch: `setup`
- Commits: 14 (scaffold, room-db, supabase-client, hud-overlay, main-ui, profile-dialog, phase-7, zone-alerts, crash-recovery, guest-mode, unit-tests, instrumented-tests, dynamic-color)
CHANGELOG update\n\n[Task 8.11.3]\n- Added extended color palette for fitness metrics (power zones, heart rate zones, cadence ranges)
