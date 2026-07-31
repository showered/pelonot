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


---

# Quality Refactor — 2026-07-31

A full-repo audit and rework. The app previously built and rendered, but
several core features could not work at runtime. This entry records what was
actually broken, because most of it was invisible: the failures were swallowed
by `catch` blocks and `runCatching`, so the app looked healthy.

## Defects that made features non-functional

| Area | Defect |
|------|--------|
| **Metric recording** | `workout_metrics` has a foreign key onto `workouts`, but the workout row was only inserted at ride *end*. Every per-second insert during a ride violated the constraint and killed the recording coroutine. **No ride ever stored a time series.** |
| **Class intervals** | The `Interval` model declared camelCase fields and a duration, against assets written in snake_case with start/end timestamps. Every decode threw into `catch { emptyList() }`. **No class ever displayed an interval.** |
| **Heart rate** | `connectGatt(null, …)` passed a null Context; the CCCD descriptor was never written, so the strap was never told to notify; straps were matched by looking for "Heart"/"HR" in bonded device names, which misses every strap on the market. **No reading could ever arrive.** |
| **HUD overlay** | `PelonotTheme` cast `view.context as Activity` unconditionally. Inside the overlay's ComposeView that context is the Service, so showing the HUD threw `ClassCastException`. |
| **Energy totals** | Power was integrated against sample *count* rather than elapsed time. A 45-minute ride over-reported energy by roughly three orders of magnitude. |
| **Distance** | Reported the latest sample's contribution under a cumulative name, so it never exceeded a few metres. |
| **Power zones** | Zone ranges left gaps (0.55–0.56, 0.75–0.76 …) and an unmatched lookup fell through to Z7. A 55.5%-of-FTP warmup was reported as Neuromuscular Power. |
| **Cadence** | Froze at its last value when ticks stopped. A stationary bike read 90 RPM and kept accruing power, distance and energy. |
| **Serial framing** | Reads are unframed, but the parser treated each buffer independently. An `R` at a buffer boundary lost its value byte. |
| **Reconnection** | `close()` called `startAutoReconnect()`, and `reconnect()` called `close()`. Ending a workout started an endless retry loop, with three competing backoff schedules. |
| **Crash recovery** | `getIncompleteWorkout()` was `ORDER BY timestamp DESC LIMIT 1` with no completion filter, so the app offered to resume the ride you had just finished, on every launch. |
| **Cloud sync** | Postgrest was passed `Map<String, Any?>`; kotlinx.serialization has no serializer for `Any`, so every call threw. `WorkoutSyncWorker` discarded the result and reported success for failed uploads. |
| **Dashboard** | "Today's Output 12.5 kJ", "Recent Ride 8.3 kJ" and an "FTP Stable" badge were hardcoded literals, shown as the rider's own statistics on a device with no rides. |
| **Post-ride summary** | Called with hardcoded zeros. RPE buttons were `onClick = { /* TODO */ }`. |
| **FTP estimation** | The 20-minute peak search clamped its window to the array end, averaging progressively shorter slices near the tail — a 60-second sprint finish inflated the estimate. Also O(n²). |
| **Ride UI** | The rider-facing screen was a developer diagnostic panel: "✓ Service Started", "✗ Serial Port NOT Ready", a raw byte dump, and a timer permanently reading `00:00`. |
| **Navigation** | Ride screens rendered *before* the `NavHost` behind a boolean and `return`ed. Nothing was on the back stack, so system back exited the app mid-ride. |
| **State** | FTP, weight and theme lived in `remember {}` in the nav graph — lost on rotation, never written to the database. |
| **Tests** | `PostWorkoutAnalyzerTest.kt` was empty. `WorkoutDaoTest` referenced fields that never existed on the entity and had never compiled. Both were ticked complete in PLAN.md. |
| **RPE input** | Ten 48dp buttons in a `Row` need ~520dp; the higher ratings were off-screen and untappable on every phone. |

## Structural changes

- **Domain layer** (`domain/model/`) — `PowerZone`, `RideIntent`, `Interval`, all pure Kotlin and unit-testable.
- **Repository layer** over the DAOs, plus DataStore-backed `SettingsRepository`.
- **ViewModels** with `StateFlow`; no database access from composables.
- **Sensor layer** rebuilt around a cold-flow `SensorSource`, with retry policy owned in one place.
- **`SimulatedSensorSource`** and a Settings toggle, so the whole ride flow is testable without a Peloton.
- **Manual DI** via `ServiceLocator`, replacing scattered `getInstance(context)` singletons.
- **Build**: `gradle/libs.versions.toml`; Supabase credentials moved from source to `local.properties` → `BuildConfig`; R8 enabled for release with keep rules.
- **Design system** deduplicated — two conflicting elevation scales, a shape scale shadowing itself with literal-named tokens, and dynamic colour unconditionally overriding the entire brand palette on API 31+.

## Verification

- `./gradlew assembleDebug` — SUCCESSFUL
- `./gradlew testDebugUnitTest` — **83 tests, 0 failures**
- Verified end-to-end on an API 36 emulator: profile creation → persistence across restart → class library with filters → 35 intervals rendering → simulated ride → post-ride summary with real figures → **80 metric rows persisted to SQLite** → RPE recorded → light/dark themes.

## Known gaps (see PLAN.md phase 9)

- Class intervals parse and display but do not drive a ride.
- The HUD overlay is built but never shown.
- `WorkoutSyncWorker` is correct but nothing calls `enqueue()`.
- Nothing has run against real Peloton hardware; `PowerModel`'s coefficients are unvalidated.

---

# Ride Integration & the HUD-First Redesign — 2026-07-31

The app could not previously run a class. It recorded telemetry and totals, but
the intervals a class prescribes did nothing, and the floating HUD — the surface
this app is actually for — was never shown. Both are now real, and the HUD has
been rebuilt around how the device is used rather than how it is demoed.

## The premise

A rider starts a class and switches to Netflix on the same 21.5" tablet. They
look at Pelonot in glances for the next forty minutes. So the HUD is not an
accessory to the ride screen — it *is* the product, and everything below is
judged by whether it survives being read in half a second, from two metres
away, out of breath.

## Ride integration (PLAN phase 9)

**`ClassIntervalEngine`** advances a class through its intervals as a pure
function of elapsed time, evaluated on the service's existing ticker. It is
deliberately not a timer: the ride already has one authoritative clock, and a
second one would drift away from it over a 45-minute class until the two
disagreed about which interval was running. Pausing the ride pauses the class
for free.

**`RideSnapshot`** is the single object the HUD and the ride screen both render
from, so the two can never disagree. Telemetry stays on its own higher-rate
flow — folding it in would recompose the entire strip several times a second
for numbers that had not moved.

**The closing cue lands on the last *hard* interval**, not the last one. Most
classes end on a cooldown, and shouting "give it everything" over a Zone 1
spin-down is worse than saying nothing. A class that ends on a Z4+ effort gets
it on the final interval instead. No class ever previews a "next up" interval
on its last one.

**The ride finishes itself** when the class timer runs out, and ending from the
HUD, from the screen, or by expiry all take the same path.

## The HUD

Docked full-width to one screen edge — top by default, because subtitles live
along the bottom — carrying the class timeline, the clock, the current interval
in a draining progress ring, three live numbers with target gauges, what is
coming next, and the controls. Dragging snaps it to the other edge; tapping
collapses it to a slim strip that still carries the countdown. The middle of
the screen is never covered and the overlay only takes touches inside its own
strip. `WorkoutService` owns it, because a ride outlives the screen.

**The countdown is the one thing on it that is never optional.** Five seconds
out, the edge hairline thickens and pulses in the next zone's colour, the
preview card scales up into a countdown with the digit at 44sp, the strip washes
with that colour, and a haptic fires on each tick.

**Zone is encoded three times over** so it reads from peripheral vision without
being read: colour, digit, and a badge whose shape sharpens with intensity — a
circle at Zone 1, a twelve-point star at Zone 7, morphing between them on a
change. Built on `androidx.graphics.shapes`, the same machinery behind Material
3 Expressive's shape language, without moving to the material3 alpha.

**Targets are gauges, not numbers.** A rider at 240 W being asked for 250–280 W
should not have to compare two figures while breathing hard.

## The coach

Split into a pure, unit-tested `RideCoachPolicy` that decides *whether* to say
something, and a `RideCoach` that only speaks and buzzes. All the restraint is
in the policy: drift has to persist 12 s before it is mentioned and 45 s before
it is mentioned again, a stationary rider is never told to pedal harder, and
the countdown buzzes on every tick but speaks once.

`CoachStyle` — Spoken, Silent, or Off — is a real setting. Silent by default: a
bike in a shared room should not talk unasked, and in that mode the HUD's motion
*is* the announcement. Speech uses navigation-guidance audio attributes so the
rider's film ducks under it.

## Also

- Crash recovery is offered at launch and rebuilds a killed ride's totals from
  the samples that did land. It offers to *keep* the ride, not resume it.
- Guest rides can be filed against a profile, or a new one created on the spot.
- `WorkoutSyncWorker` is finally enqueued by something.
- The ride screen is rebuilt for the landscape tablet it runs on.

## Defects found by running it, not reading it

| Defect | Effect |
|--------|--------|
| **`android.permission.VIBRATE` was never declared** | Every haptic threw `SecurityException` into a `runCatching`. The buzz simply never happened, and nothing said why. Invisible from the UI; found in logcat during a real ride. |
| **`WorkoutDaoTest` never ran** | `@Before fun setup() = runBlocking { … }` returns the last expression's type, and `insertUser` returns a row id. JUnit rejects a non-void `@Before`, so the class failed to initialise and all ten tests silently did nothing — while PLAN 8.8 was ticked. |
| **The HUD sat under the status bar** | `FLAG_LAYOUT_IN_SCREEN` put the class timeline behind the system clock. |
| **The HUD was legible only over black** | A 0.90–0.97 gradient across the whole strip looks elegant against a dark wallpaper and is unreadable over a bright scene, which is the only place it will ever be used. |
| **Scaling the strip to catch the eye peeled it off the screen edge** | Reads as a rendering fault. Replaced with a colour wash. |
| **The countdown banner clipped inside its slot** | The zone name and the digit did not both fit in 256dp. |

## Verification

- `./gradlew testDebugUnitTest` — **150 tests, 0 failures**
- `./gradlew connectedDebugAndroidTest` — **17 tests, 0 failures** (10 DAO,
  7 `WorkoutService` lifecycle)
- On a 1920×1080 landscape tablet emulator, checked against the database rather
  than the screen:
  - A 20-minute Threshold class ran to its end and finished itself: 1200 s,
    171.6 kJ, 143 W average, **1201 metric rows covering seconds 0–1200 with no
    gaps**. 143 W × 1200 s = 171.6 kJ, so the integration is right.
  - Returning to the app after it ended in the background went straight to the
    summary with those figures.
  - "Cool down — ride easy" appeared on the final Z1 interval, with no next-up
    preview on it.
  - The HUD rendered over the launcher at both edges, collapsed and re-expanded,
    dragged between edges with the choice persisted, and stopped the ride from
    its own button.
  - Crash recovery rebuilt a killed ride from its samples (6:29, 53.4 kJ) and
    then correctly offered the *second* orphaned ride rather than abandoning it.

## Known gaps (see PLAN.md phase 11)

- **Resistance is not on the HUD.** The knob is the rider's only actuator; power
  is an output. This is the largest single omission.
- The leaderboard panel was removed with the old floating card and has not been
  re-homed. `WorkoutRepository.leaderboardFor` is still correct and unused.
- The profile selector, dashboard and post-ride summary are still phone-shaped
  columns on a 1920×1080 screen.
- Spoken coach mode has not been heard over a playing video on a device with a
  real TTS voice installed.
- Nothing has run against a real Peloton.
