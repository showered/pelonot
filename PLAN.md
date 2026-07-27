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

## Phase 6: Main App UI (Non-Overlay Screens)

**Goal:** The full app experience — profile selection, dashboard, class library, settings.

- [ ] **6.1** Create `ProfileSelectorScreen` — Netflix-style grid of user profiles with "Guest Mode" option
- [ ] **6.2** Create `ProfileCreationDialog` — enter name, weight, initial FTP
- [ ] **6.3** Create `MainDashboardScreen`:
  - "Just Ride" button (free ride, no class)
  - "Begin Class" button → navigates to class library
  - "Settings / FTP" button
- [ ] **6.4** Create `PreRideIntentPrompt` dialog — "Reach New Milestones" vs "Just Stay Fit" selection
- [ ] **6.5** Create `ClassLibraryScreen`:
  - Lists all class templates from Room (pre-loaded from assets JSON)
  - Filterable by category (Aerobic, HIIT, Tabata, Threshold)
  - Shows duration, category badge, title
- [ ] **6.6** Create `ClassDetailScreen` — shows interval breakdown, start button
- [ ] **6.7** Create `PostRideSummaryScreen`:
  - Displays total output, duration, avg power, avg HR, distance
  - Shows RPE prompt ("Rate this effort 1-10")
  - Shows FTP breakthrough dialog if detected
- [ ] **6.8** Create `SettingsScreen`:
  - Edit FTP manually
  - Edit weight
  - Theme toggle (dark/light)
  - BLE device management (pair/forget HR monitors)
  - Overlay permission status
- [ ] **6.9** Implement navigation using Jetpack Compose Navigation (NavHost, NavController)

---

## Phase 7: Auto-FTP Engine, Workload JSON & Cloud Sync

**Goal:** Post-ride analysis, FTP auto-detection, and background sync to Supabase.

- [ ] **7.1** Create `PostWorkoutAnalyzer`:
  - **20-Min Peak Power:** Calculate max rolling 20-min average wattage from `WorkoutMetric` time-series → `FTP_est = P_20min_max × 0.95`
  - **Biometric Decoupling:** Detect if Zone 4 power sustained >10 min while HR <80% max HR
  - **RPE Survey:** If RPE ≤ 4 on a hard class, propose 3% FTP increase
- [ ] **7.2** Create `FtpBreakthroughDialog` composable — shows current FTP, estimated new FTP, Accept/Decline buttons
- [ ] **7.3** Implement FTP update flow: on accept, update `User.ftpWatts` in Room + schedule sync
- [ ] **7.4** Create `WorkoutSyncWorker` extending `CoroutineWorker`:
  - On successful ride completion, enqueue sync via WorkManager
  - Compresses `WorkoutMetric` list into JSON array
  - Uploads workout + metrics payload to Supabase `workouts` table
  - Handles retry logic (3 retries with exponential backoff)
- [ ] **7.5** Create `ClassTemplateSeeder`:
  - Reads JSON files from `app/src/main/assets/classes/`
  - Parses into `ClassTemplate` objects
  - Inserts into Room `class_templates` table on first launch
- [ ] **7.6** Create all 40 class template JSON files (AE-01 to AE-10, HC-01 to HC-10, TB-01 to TB-10, TP-01 to TP-10) in `assets/classes/`

---

## Phase 8: Polish, Testing & Edge Cases

**Goal:** Production readiness.

- [ ] **8.1** Handle serial port disconnection gracefully (auto-reconnect with exponential backoff)
- [ ] **8.2** Handle BLE disconnection gracefully (auto-scan + reconnect)
- [ ] **8.3** Handle app crash during workout — recover state from last known good metrics in Room
- [ ] **8.4** Handle guest mode workouts (no user ID) — prompt to save or discard post-ride
- [ ] **8.5** Add haptic feedback on HUD for zone alerts
- [ ] **8.6** Add audio cues (TTS) for zone changes if desired
- [ ] **8.7** Write unit tests for: `PowerZoneCalculator`, `PostWorkoutAnalyzer`, `WorkoutMetricsCalculator`, `IntentModifier`
- [ ] **8.8** Write instrumented tests for: Room DAOs, WorkoutService lifecycle
- [ ] **8.9** Manual testing on actual Gen 1/Gen 2 Peloton tablet hardware

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