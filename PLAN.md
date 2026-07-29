# Pelonot — Implementation Plan

> **Open-Source Peloton Client** — A subscription-free fitness app for jailbroken Peloton bikes (Gen 1/Gen 2).

---

## Phase 0: Project Scaffolding & Build System ✅

**Goal:** Create the Android project skeleton with all dependencies wired, targeting API 24.

- [x] **0.1** Create Android project directory structure (`app/src/main/java/com/pelonot/...`, `app/src/main/res/...`, `app/build.gradle.kts`, `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`, `local.properties`)
- [x] **0.2** Configure `minSdkVersion = 24`, `targetSdkVersion = 34`, `compileSdk = 34` in `app/build.gradle.kts`
- [x] **0.3** Add all dependencies to `app/build.gradle.kts` (Compose, Material3, Room + KSP, Coroutines, Supabase, WorkManager, Navigation, Lifecycle)
- [x] **0.4** Create `AndroidManifest.xml` with permissions (SYSTEM_ALERT_WINDOW, FOREGROUND_SERVICE, BLUETOOTH, INTERNET, etc.)
- [x] **0.5** Create `Application` class (`PelonotApp`)
- [x] **0.6** Verify project compiles and runs on `MainActivity` with Compose theme

---

## Phase 1: Local Database (Room) ✅

**Goal:** Offline-first data layer with Room entities, DAOs, and database class — complete and building.

- [x] **1.1** Create `User` entity (`profiles` table)
- [x] **1.2** Create `ClassTemplate` entity with `intervalsJson` (String storage)
- [x] **1.3** Create `Workout` entity with foreign keys to User and ClassTemplate
- [x] **1.4** Create `WorkoutMetric` entity with foreign key to Workout (CASCADE delete)
- [x] **1.5** Create all 4 DAOs with leaderboard, PB, average, and FTP analysis queries
- [x] **1.6** Create `AppDatabase` with singleton pattern, version 1
- [x] **1.7** Build verified — KSP processes Room annotations successfully

### Supabase (Cloud) ✅

- [x] **1.8** Supabase project created (`https://podsmtujqarlqhvorpdh.supabase.co`) with credentials configured
- [x] **1.9** SQL migration script in `supabase/migration.sql` — run this in Supabase SQL Editor
- [x] **1.10** `SupabaseClientProvider` with Postgrest installed
- [x] **1.11** `SupabaseSyncRepository` with `syncWorkout()`, `syncProfile()`, `fetchClassTemplates()`
- [x] Build verified — all Supabase dependencies resolve and compile

---

## Phase 2: Telemetry Engine — Serial Port & BLE (in progress)

**Goal:** Read real-time sensor data from the Peloton sensor board (serial) and BLE HR monitors.

### 2.1 Serial Port Reader
- [x] Create service directory and `SerialPortReader` class
- [ ] Test — needs connecting to `/dev/ttyS1` on hardware

### 2.2 Grupetto Tick Parsing
- [x] Implement `SensorTick` data class and tick buffer logic
- [x] Compute cadence (RPM) from tick intervals
- [x] Compute instantaneous power from resistance + cadence model
- [ ] Validate against known Grupetto power curve constants

### 2.3 BLE Heart Rate
- [x] Create `BleHeartRateManager` with scanning, GATT connect, characteristic subscription
- [x] Expose `heartRate: StateFlow<Int>`
- [x] Manual pairing UI — deferred to Settings screen (Phase 6)

### 2.4 Sensor Repository
- [x] Create `SensorRepository` singleton merging serial + BLE into unified `StateFlow<SensorReading>`
- [x] Auto-reconnect logic for BLE (retry with exponential backoff)
- [x] Auto-reconnect logic for serial port

### 2.5 Workout Metrics Calculator
- [x] Implement total output integration (kJ)
- [x] Implement rolling averages (1s, 30s)
- [x] Implement distance estimation from cadence + resistance

---

## Phase 3: Foreground Service & Workout Lifecycle ✅

**Goal:** A persistent Android Foreground Service that manages workout state, even when the app is backgrounded.

- [x] **3.1** Create `WorkoutService` extending `Service()`:
  - Start foreground with persistent notification ("Pelonot — Riding")
  - Bind to `SensorRepository` and collect `StateFlow<SensorReading>`
  - Expose `workoutState: StateFlow<WorkoutState>` (Idle / Active / Paused / Completed)
- [x] **3.2** Create `WorkoutSession` data class: `workoutId`, `classId`, `startTime`, `elapsedSeconds`, `metrics: List<WorkoutMetric>`, `intentModifier`
- [x] **3.3** Implement workout controls: `startWorkout()`, `pauseWorkout()`, `resumeWorkout()`, `stopWorkout()`
- [x] **3.4** Implement metric recording: every 1 second, insert a `WorkoutMetric` row into Room via `WorkoutMetricDao`
- [x] **3.5** Create notification channel and persistent notification with live metrics (cadence, power, HR, timer)

---

## Phase 4: Floating HUD Overlay (WindowManager) ✅

**Goal:** A transparent, draggable overlay that renders Jetpack Compose UI on top of third-party apps.

- [x] **4.1** Create `OverlayPermissionHelper` to request `SYSTEM_ALERT_WINDOW` permission and check status
- [x] **4.2** Create `HudOverlayManager`:
  - Uses `WindowManager` with `WindowManager.LayoutParams` of type `TYPE_APPLICATION_OVERLAY` (API 26+) or `TYPE_PHONE` (API 24-25)
  - Sets layout flags: `FLAG_NOT_FOCUSABLE`, `FLAG_LAYOUT_IN_SCREEN`, `FLAG_NOT_TOUCH_MODAL`
  - Inflates a `ComposeView` as the overlay content
- [x] **4.3** Attach `ViewTreeLifecycleOwner` and `ViewTreeSavedStateRegistryOwner` to the `ComposeView` for proper Compose lifecycle
- [x] **4.4** Implement drag-to-move on the overlay's top bar handle using `View.OnTouchListener` with `GestureDetector`
- [x] **4.5** Implement overlay show/hide lifecycle tied to `WorkoutService` state

---

## Phase 5: HUD Compose UI & Power Zone Engine ✅

**Goal:** The visual HUD with real-time metrics, target zones, and leaderboard.

- [x] **5.1** Create `HudTheme.kt` — Material 3 dark theme with:
  - Background: `#121212`
  - Cadence accent: Electric Cyan
  - Power accent: Hot Coral
  - HR accent: Neon Green
  - Typography: DisplayLarge/Medium for numbers, LabelSmall for units
- [x] **5.2** Create `MetricCard` composable: displays a single metric (label, value, unit) with animated value transitions
- [x] **5.3** Create `TargetZoneIndicator` composable:
  - Shows target RPM range and target Power Zone
  - If current value is outside range, card turns red with spring bounce animation
- [x] **5.4** Create `LeaderboardPanel` composable (collapsible):
  - Shows PB, Personal Average, Household Best for current workout duration
  - Queries Room via `WorkoutDao` for leaderboard data
- [x] **5.5** Create `HudControls` composable: Pause / Resume / Stop buttons
- [x] **5.6** Create `HudMainContent` composable that assembles all HUD components
- [x] **5.7** Implement `PowerZoneCalculator`:
  - Takes FTP + current power → returns Zone (1-7) based on Coggan model
  - `getZoneForPower(power: Float, ftp: Float): PowerZone`
- [x] **5.8** Implement `IntentModifier` logic:
  - `"Reach New Milestones"` → `k = 1.05`
  - `"Just Stay Fit"` → `k = 0.95`
  - `P_target = FTP × Zone% × k`
- [x] **5.9** Implement target zone alerting: if rider drops outside prescribed range for >5 seconds, trigger visual alert on HUD

---

## Phase 6: Main App UI (Non-Overlay Screens) ✅

**Goal:** The full app experience — profile selection, dashboard, class library, settings.

- [x] **6.1** Create `ProfileSelectorScreen` — Netflix-style grid of user profiles with "Guest Mode" option
- [x] **6.2** Create `ProfileCreationDialog` — enter name, weight, initial FTP
- [x] **6.3** Create `MainDashboardScreen`:
  - "Just Ride" button (free ride, no class)
  - "Begin Class" button → navigates to class library
  - "Settings / FTP" button
- [x] **6.4** Create `PreRideIntentPrompt` dialog — "Reach New Milestones" vs "Just Stay Fit" selection
- [x] **6.5** Create `ClassLibraryScreen`:
  - Lists all class templates from Room (pre-loaded from assets JSON)
  - Filterable by category (Aerobic, HIIT, Tabata, Threshold)
  - Shows duration, category badge, title
- [x] **6.6** Create `ClassDetailScreen` — shows interval breakdown, start button
- [x] **6.7** Create `PostRideSummaryScreen`:
  - Displays total output, duration, avg power, avg HR, distance
  - Shows RPE prompt ("Rate this effort 1-10")
  - Shows FTP breakthrough dialog if detected
- [x] **6.8** Create `SettingsScreen`:
  - Edit FTP manually
  - Edit weight
  - Theme toggle (dark/light)
  - BLE device management (pair/forget HR monitors)
  - Overlay permission status
- [x] **6.9** Implement navigation using Jetpack Compose Navigation (NavHost, NavController)

---

## Phase 7: Auto-FTP Engine, Workload JSON & Cloud Sync

**Goal:** Post-ride analysis, FTP auto-detection, and background sync to Supabase.

- [x] **7.1** Create `PostWorkoutAnalyzer`:
  - **20-Min Peak Power:** Calculate max rolling 20-min average wattage from `WorkoutMetric` time-series → `FTP_est = P_20min_max × 0.95`
  - **Biometric Decoupling:** Detect if Zone 4 power sustained >10 min while HR <80% max HR
  - **RPE Survey:** If RPE ≤ 4 on a hard class, propose 3% FTP increase
- [x] **7.2** Create `FtpBreakthroughDialog` composable — shows current FTP, estimated new FTP, Accept/Decline buttons
- [x] **7.3** Implement FTP update flow: on accept, update `User.ftpWatts` in Room + schedule sync
- [x] **7.4** Create `WorkoutSyncWorker` extending `CoroutineWorker`:
  - On successful ride completion, enqueue sync via WorkManager
  - Compresses `WorkoutMetric` list into JSON array
  - Uploads workout + metrics payload to Supabase `workouts` table
  - Handles retry logic (3 retries with exponential backoff)
- [x] **7.5** Create `ClassTemplateSeeder`:
  - Reads JSON files from `app/src/main/assets/classes/`
  - Parses into `ClassTemplate` objects
  - Inserts into Room `class_templates` table on first launch
- [x] **7.6** Create all class template JSON files in `assets/classes/`

---

## Phase 8: Polish, Testing & Edge Cases

**Goal:** Production readiness.

- [x] **8.1** Handle serial port disconnection gracefully (auto-reconnect with exponential backoff)
- [x] **8.2** Handle BLE disconnection gracefully (auto-scan + reconnect)
- [x] **8.3** Handle app crash during workout — recover state from last known good metrics in Room
- [x] **8.4** Handle guest mode workouts (no user ID) — prompt to save or discard post-ride
- [x] **8.5** Add haptic feedback on HUD for zone alerts
- [x] **8.6** Add audio cues (TTS) for zone changes if desired
- [x] **8.7** Write unit tests for: `PowerZoneCalculator`, `PostWorkoutAnalyzer`, `WorkoutMetricsCalculator`, `IntentModifier`
- [x] **8.8** Write instrumented tests for: Room DAOs, WorkoutService lifecycle
- [ ] **8.9** Manual testing on actual Gen 1/Gen 2 Peloton tablet hardware
- [ ] **8.10** Guest post-ride prompt: save to new profile, existing profile, or discard
- [ ] **8.11** Apply Material Expressive design language throughout the app

### Design System
- [x] **8.11.1** Audit current color palette and define Material Expressive color tokens (primary, secondary, tertiary, error, surface, on-surface variants)
- [x] **8.11.2** Define dynamic color scheme for light and dark themes with proper tonal palettes
- [x] **8.11.3** Create extended color palette for fitness metrics (power zones, heart rate zones, cadence ranges)

* Added: Power Zones 8-9 (extended recovery shades)
* Added: Heart Rate Zones 1-5 (progressive intensity)
* Added: Cadence Zones 1-5 (progressive intensity)
* All colors follow Material Expressive Design System with calm premium aesthetic
- [x] **8.11.4** Define elevation and shadow tokens (Level 0-5) for consistent depth hierarchy
- [x] **8.11.5** Create shape tokens with expressive corner sizes (4dp, 8dp, 12dp, 16dp, 28dp, 32dp)
- [x] **8.11.6** Define motion duration tokens (short, medium, long) and easing curves

### Material Expressive Theme
- [x] **8.11.7** Update `Color.kt` with Material Expressive color system (dynamic tonal palettes)
- [x] **8.11.8** Update `Type.kt` with expressive typography scale (DisplayLarge to LabelSmall with proper weights)
- [x] **8.11.9** Create `Shapes.kt` with expressive corner shapes (pill shapes, cut corners, rounded rectangles)
- [x] **8.11.10** Update `Theme.kt` to use Material Expressive components and dynamic shapes
- [x] **8.11.11** Add motion tokens to theme (standard, emphasized, decelerated, accelerated easing)

### Navigation & Motion
- [ ] **8.11.12** Implement shared element transitions between profile selector and dashboard
- [ ] **8.11.13** Add fade-through animations for screen transitions
- [ ] **8.11.14** Implement container transform for class library to class detail navigation
- [ ] **8.11.15** Add predictive back gesture support for Android 14+
- [ ] **8.11.16** Create navigation rail or bottom bar with expressive icon animations

### Dashboard Redesign
- [ ] **8.11.17** Redesign `MainDashboardScreen` with Material Expressive card layouts
- [ ] **8.11.18** Add large expressive FAB for primary action (Just Ride)
- [ ] **8.11.19** Implement dashboard cards with proper elevation and surface tonal variants
- [ ] **8.11.20** Add micro-interactions for button presses and state changes
- [ ] **8.11.21** Create dashboard loading states with skeleton screens

### Profile Selection Redesign
- [ ] **8.11.22** Redesign `ProfileSelectorScreen` with expressive profile cards
- [ ] **8.11.23** Add profile avatar with dynamic color backgrounds
- [ ] **8.11.24** Implement profile card hover/press states with expressive animations
- [ ] **8.11.25** Add guest mode card with distinct visual treatment
- [ ] **8.11.26** Create profile creation flow with expressive dialogs

### Class Library Redesign
- [ ] **8.11.27** Redesign `ClassLibraryScreen` with expressive list items
- [ ] **8.11.28** Add category filter chips with dynamic selection states
- [ ] **8.11.29** Implement class cards with duration badges and category tags
- [ ] **8.11.30** Add search bar with expressive leading/trailing icons
- [ ] **8.11.31** Create empty state illustrations for no classes found

### Class Details Redesign
- [ ] **8.11.32** Redesign `ClassDetailScreen` with interval timeline visualization
- [ ] **8.11.33** Add sticky header for class title and metadata
- [ ] **8.11.34** Implement interval cards with zone color coding
- [ ] **8.11.35** Add start button with expressive loading state
- [ ] **8.11.36** Create difficulty indicator with expressive progress visualization

### Ride Preparation Flow
- [ ] **8.11.37** Redesign `PreRideIntentPrompt` with expressive choice chips
- [ ] **8.11.38** Add intent selection with iconography and descriptions
- [ ] **8.11.39** Implement preparation checklist with progress indicator
- [ ] **8.11.40** Add sensor connection status with expressive status indicators
- [ ] **8.11.41** Create countdown animation before ride start

### Workout HUD Redesign
- [ ] **8.11.42** Redesign `HudOverlayMain` with expressive metric cards
- [ ] **8.11.43** Add dynamic shape transitions for metric value changes
- [ ] **8.11.44** Implement target zone indicator with expressive progress arcs
- [ ] **8.11.45** Add zone alert with expressive color flash and haptic
- [ ] **8.11.46** Create collapsible leaderboard with smooth expand/collapse
- [ ] **8.11.47** Add pause overlay with expressive blur effect

### Post Ride Summary Redesign
- [ ] **8.11.48** Redesign `PostRideSummaryScreen` with summary cards
- [ ] **8.11.49** Add achievement badges for workout milestones
- [ ] **8.11.50** Implement RPE selection with expressive slider
- [ ] **8.11.51** Add share button with expressive icon animation
- [ ] **8.11.52** Create FTP breakthrough dialog with celebratory animation

### Charts & Visualisations
- [x] **8.11.53** Add power output chart with zone color overlay
- [ ] **8.11.54** Implement heart rate chart with time markers
- [ ] **8.11.55** Create cadence distribution visualization
- [ ] **8.11.56** Add workout comparison chart (current vs PB)
- [ ] **8.11.57** Implement smooth chart animations on load

### Component Library
- [ ] **8.11.58** Create reusable `MetricCard` with expressive styling
- [ ] **8.11.59** Add `ZoneBadge` composable for power zones
- [ ] **8.11.60** Implement `ProgressArc` with expressive stroke and animation
- [ ] **8.11.61** Create `FilterChip` with dynamic selection states
- [ ] **8.11.62** Add `ExpressiveButton` with proper touch feedback
- [ ] **8.11.63** Implement `ExpressiveDialog` with rounded top corners
- [ ] **8.11.64** Create `SkeletonLoader` for loading states

### Accessibility
- [ ] **8.11.65** Add content descriptions for all interactive elements
- [ ] **8.11.66** Implement proper touch target sizes (minimum 48dp)
- [ ] **8.11.67** Add semantic headings for screen readers
- [ ] **8.11.68** Implement high contrast mode support
- [ ] **8.11.69** Add font scaling support for large text

### Polish & Micro-interactions
- [ ] **8.11.70** Add haptic feedback for all button interactions
- [ ] **8.11.71** Implement spring animations for value changes
- [ ] **8.11.72** Add staggered animations for list item appearance
- [ ] **8.11.73** Create expressive ripple effects for touch feedback
- [ ] **8.11.74** Add loading shimmer effects for async content
- [ ] **8.11.75** Implement smooth state transitions for all UI elements
- [ ] **8.11.76** Add adaptive iconography for different states
- [ ] **8.11.77** Create consistent error states with expressive messaging
- [ ] **8.11.78** Add success animations for completed actions
- [ ] **8.11.79** Implement pull-to-refresh with expressive indicator
- [ ] **8.11.80** Add scroll edge effects with tonal overlays

---

## How to Use This Plan

1. **Each checkbox is a single, focused task** — small enough to complete in one AI session, large enough to be meaningful.
2. **Work through phases in order** — each phase builds on the previous one.
3. **When switching AI models**, paste the current plan state (with checkboxes marked) into the new context so the model knows exactly where to pick up.
4. **Check off completed items by replacing `[ ]` with `[x]`** so progress persists across sessions.

---

## Quick Reference: Coggan 7-Zone Power Model

| Zone | Name | % of FTP | Training Purpose |
|------|------|----------|------------------|
| Z1 | Active Recovery | < 55% | Warmup, cooldown |
| Z2 | Endurance | 56–75% | Aerobic base building |
| Z3 | Tempo | 76–90% | Aerobic efficiency |
| Z4 | Lactate Threshold | 91–105% | Sustainable hard effort |
| Z5 | VO2 Max | 106–120% | Max oxygen uptake |
| Z6 | Anaerobic Capacity | 121–150% | Short power bursts |
| Z7 | Neuromuscular Power | > 150% | Explosive sprints |

## Quick Reference: Intent Modifier

| Intent | `k_intent` | Effect |
|--------|-----------|--------|
| "Reach New Milestones" | 1.05 | Scales target power up 5% |
| "Just Stay Fit" | 0.95 | Scales target power down 5% |