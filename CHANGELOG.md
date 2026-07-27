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

**AndroidManifest.xml permissions:**
- `SYSTEM_ALERT_WINDOW` (floating HUD)
- `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_DATA_SYNC`
- `BLUETOOTH`, `BLUETOOTH_ADMIN`, `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`
- `INTERNET`, `ACCESS_NETWORK_STATE`, `POST_NOTIFICATIONS`

**Key classes:**
- `PelonotApp` — Application class with notification channels (workout, sync)
- `MainActivity` — Compose entry point with dark theme
- `Color.kt` — Neon palette: Electric Cyan (cadence), Hot Coral (power), Neon Green (HR)
- `Type.kt` — DisplayLarge/Medium 64sp/48sp for metrics, LabelSmall 11sp for units
- `Theme.kt` — Material 3 dark color scheme with all neon accents

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
- `UserDao` — CRUD + Flow queries
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
- `SupabaseClient.kt` — SupabaseClient singleton with Postgrest, `MetricSnapshot` DTO
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
 - `MainDashboardScreen.kt` — Main dashboard with Just Ride, Begin Class, Settings buttons
 - `PreRideIntentPrompt.kt` — Intent selection dialog
 - `ClassLibraryScreen.kt` — List of class templates
 - `PostRideSummaryScreen.kt` — Ride summary with RPE selection
 - `SettingsScreen.kt` — Settings for FTP, weight, theme
 - `NavGraph.kt` — Navigation setup with NavHost
 - `ProfileCreationDialog.kt` — Profile creation with name, weight, FTP
 - `ClassDetailScreen.kt` — Class detail with interval breakdown
 
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
 - `PostWorkoutAnalyzer.kt` — 20-min peak power, biometric decoupling, RPE analysis
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
 
## GitHub
 
- Remote: `https://github.com/showered/pelonot.git`
- Branch: `setup`
- Commits: 13 (scaffold, room-db, supabase-client, hud-overlay, main-ui, profile-dialog, phase-7, zone-alerts, crash-recovery, guest-mode, unit-tests, instrumented-tests)