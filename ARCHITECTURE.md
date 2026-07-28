# Pelonot Application Architecture

> **Open-Source Peloton Client** — A subscription-free fitness app for jailbroken Peloton bikes (Gen 1/Gen 2).

---

## Overview

Pelonot is an Android application built with Jetpack Compose that provides a complete cycling workout experience for Peloton bikes. The app reads real-time sensor data, displays a floating HUD overlay, tracks workouts, and syncs data to the cloud.

**Target SDK:** API 24 (Android 7.0) - API 34  
**Language:** Kotlin  
**Architecture:** MVVM with Repository pattern, Foreground Service, and Jetpack Compose UI

---

## Project Structure

```
pelonot/
├── PLAN.md                    # Implementation roadmap with phases
├── CHANGELOG.md               # Development history
├── build.gradle.kts           # Root Gradle build file
├── settings.gradle.kts          # Project settings
├── gradle.properties            # Gradle configuration
├── local.properties           # SDK path
├── .gitignore                 # Git ignore rules
├── LICENSE                    # MIT License
│
├── app/
│   ├── build.gradle.kts       # App-level dependencies
│   ├── src/
│   │   ├── main/
│   │   │   ├── AndroidManifest.xml
│   │   │   ├── java/com/pelonot/
│   │   │   │   ├── MainActivity.kt
│   │   │   │   ├── PelonotApp.kt
│   │   │   │   │
│   │   │   │   ├── data/
│   │   │   │   │   ├── local/
│   │   │   │   │   │   ├── AppDatabase.kt
│   │   │   │   │   │   ├── ClassTemplateSeeder.kt
│   │   │   │   │   │   │
│   │   │   │   │   │   ├── dao/
│   │   │   │   │   │   │   ├── UserDao.kt
│   │   │   │   │   │   │   ├── ClassTemplateDao.kt
│   │   │   │   │   │   │   ├── WorkoutDao.kt
│   │   │   │   │   │   │   └── WorkoutMetricDao.kt
│   │   │   │   │   │   │
│   │   │   │   │   │   └── entity/
│   │   │   │   │   │       ├── UserEntity.kt
│   │   │   │   │   │       ├── ClassTemplateEntity.kt
│   │   │   │   │   │       ├── WorkoutEntity.kt
│   │   │   │   │   │       └── WorkoutMetricEntity.kt
│   │   │   │   │   │
│   │   │   │   │   ├── remote/
│   │   │   │   │   │   ├── SupabaseClient.kt
│   │   │   │   │   │   ├── SupabaseConfig.kt
│   │   │   │   │   │   └── SupabaseSyncRepository.kt
│   │   │   │   │   │
│   │   │   │   │   ├── sensor/
│   │   │   │   │   │   ├── SensorTick.kt
│   │   │   │   │   │   ├── SerialPortReader.kt
│   │   │   │   │   │   ├── BleHeartRateManager.kt
│   │   │   │   │   │   ├── SensorRepository.kt
│   │   │   │   │   │   ├── PowerZoneCalculator.kt
│   │   │   │   │   │   └── WorkoutMetricsCalculator.kt
│   │   │   │   │   │
│   │   │   │   │   ├── service/
│   │   │   │   │   │   ├── WorkoutService.kt
│   │   │   │   │   │   ├── WorkoutSession.kt
│   │   │   │   │   │   └── PostWorkoutAnalyzer.kt
│   │   │   │   │   │
│   │   │   │   │   └── worker/
│   │   │   │   │       └── WorkoutSyncWorker.kt
│   │   │   │   │
│   │   │   │   ├── ui/
│   │   │   │   │   ├── navigation/
│   │   │   │   │   │   └── NavGraph.kt
│   │   │   │   │   │
│   │   │   │   │   ├── overlay/
│   │   │   │   │   │   ├── HudOverlayManager.kt
│   │   │   │   │   │   ├── HudOverlayMain.kt
│   │   │   │   │   │   ├── OverlayPermissionHelper.kt
│   │   │   │   │   │   └── ZoneAlertManager.kt
│   │   │   │   │   │
│   │   │   │   │   ├── screen/
│   │   │   │   │   │   ├── ProfileSelectorScreen.kt
│   │   │   │   │   │   ├── MainDashboardScreen.kt
│   │   │   │   │   │   ├── PreRideIntentPrompt.kt
│   │   │   │   │   │   ├── ClassLibraryScreen.kt
│   │   │   │   │   │   ├── ClassDetailScreen.kt
│   │   │   │   │   │   ├── PostRideSummaryScreen.kt
│   │   │   │   │   │   ├── SettingsScreen.kt
│   │   │   │   │   │   ├── ProfileCreationDialog.kt
│   │   │   │   │   │   ├── FtpBreakthroughDialog.kt
│   │   │   │   │   │   └── JustRideScreen.kt
│   │   │   │   │   │
│   │   │   │   │   └── theme/
│   │   │   │   │       ├── Color.kt
│   │   │   │   │       ├── Theme.kt
│   │   │   │   │       └── Type.kt
│   │   │   │   │
│   │   │   ├── res/
│   │   │   │   ├── values/
│   │   │   │   │   ├── strings.xml
│   │   │   │   │   ├── themes.xml
│   │   │   │   │   └── ic_launcher_background.xml
│   │   │   │   ├── drawable/
│   │   │   │   │   └── ic_launcher_foreground.xml
│   │   │   │   └── mipmap-*/
│   │   │   │       └── ic_launcher.xml
│   │   │   │
│   │   │   └── assets/
│   │   │       └── classes/
│   │   │           ├── endurance/
│   │   │           │   ├── ae-01.json
│   │   │           │   └── ae-02.json
│   │   │           ├── hiit_heavy_climbs/
│   │   │           │   └── hc-01.json
│   │   │           ├── tabata_bursts/
│   │   │           │   └── tb-01.json
│   │   │           └── threshold/
│   │   │               └── tp-01.json
│   │   │
│   │   ├── test/
│   │   │   └── java/com/pelonot/
│   │   │       ├── data/
│   │   │       │   ├── sensor/
│   │   │       │   │   └── PowerZoneCalculatorTest.kt
│   │   │       │   └── service/
│   │   │       │       └── PostWorkoutAnalyzerTest.kt
│   │   │
│   │   └── androidTest/
│   │       └── java/com/pelonot/
│   │           ├── data/
│   │           │   ├── local/
│   │           │   │   └── dao/
│   │           │   │       └── WorkoutDaoTest.kt
│   │           │   └── service/
│   │           │       └── WorkoutServiceTest.kt
│   │
└── supabase/
    └── migration.sql          # Database schema and seed data
```

---

## Core Components

### 1. Application Entry Point

| File | Purpose |
|------|---------|
| `MainActivity.kt` | Main entry point, sets up Compose content with navigation |
| `PelonotApp.kt` | Application class that creates notification channels for workout and sync |

---

### 2. Data Layer

#### Local Database (Room)

The local database provides offline-first data persistence with 4 entities:

| Entity | Table | Description |
|--------|-------|-------------|
| `UserEntity` | `profiles` | User profiles with name, weight, FTP, and theme preference |
| `ClassTemplateEntity` | `class_templates` | Workout class templates with interval definitions |
| `WorkoutEntity` | `workouts` | Completed workout records with summary metrics |
| `WorkoutMetricEntity` | `workout_metrics` | Time-series metrics (1-second intervals) during workouts |

**Key DAOs:**

- `UserDao` - CRUD operations and Flow queries for user profiles
- `ClassTemplateDao` - Batch insert, category filtering, template count
- `WorkoutDao` - Leaderboard queries (PB, average, household best), recent workouts, crash recovery
- `WorkoutMetricDao` - Time-series insert/get, power array extraction for FTP calculation

#### Remote (Supabase)

Cloud synchronization for data backup and cross-device access:

| File | Purpose |
|------|---------|
| `SupabaseConfig.kt` | Supabase project URL and anonymous key configuration |
| `SupabaseClient.kt` | Supabase client singleton with Postgrest client |
| `SupabaseSyncRepository.kt` | Sync operations: workouts, profiles, FTP updates |

---

### 3. Sensor Layer

Real-time telemetry collection from the Peloton bike:

| File | Purpose |
|------|---------|
| `SensorTick.kt` | Data classes for raw sensor ticks and parsed readings |
| `SerialPortReader.kt` | Reads telemetry from `/dev/ttyS1` (cadence ticks, resistance) |
| `BleHeartRateManager.kt` | BLE heart rate monitor scanning and GATT connection |
| `SensorRepository.kt` | Singleton merging serial + BLE data with auto-reconnect |
| `PowerZoneCalculator.kt` | Coggan 7-zone power model calculations |
| `WorkoutMetricsCalculator.kt` | Rolling averages, total output, distance estimation |

**Sensor Data Flow:**
1. `SerialPortReader` reads raw ticks from `/dev/ttyS1`
2. Cadence is calculated from tick intervals (RPM)
3. Power is calculated from resistance + cadence model
4. `BleHeartRateManager` provides heart rate from BLE monitors
5. `SensorRepository` merges all data into unified `SensorReading`
6. `WorkoutService` collects readings and records metrics

---

### 4. Workout Service

Foreground service managing workout lifecycle:

| File | Purpose |
|------|---------|
| `WorkoutService.kt` | Foreground service with workout state management |
| `WorkoutSession.kt` | Data class for active workout session |
| `PostWorkoutAnalyzer.kt` | FTP auto-detection, biometric decoupling analysis |

**Workout States:**
- `Idle` - No active workout
- `Active` - Workout in progress
- `Paused` - Workout paused
- `Completed` - Workout finished

**Key Features:**
- Persistent notification with live metrics
- 1-second metric recording to Room
- Crash recovery for incomplete workouts
- Auto-reconnect for sensor disconnections

---

### 5. UI Layer

#### Navigation

| File | Purpose |
|------|---------|
| `NavGraph.kt` | Jetpack Compose Navigation setup with all screen routes |

**Routes:**
- `profile_selector` - User profile selection
- `dashboard/{userId}` - Main dashboard
- `class_library` - Class template list
- `class_detail/{classId}` - Class detail view
- `settings` - Settings screen
- `post_ride/{isGuest}` - Post-ride summary

#### Screens

| File | Purpose |
|------|---------|
| `ProfileSelectorScreen.kt` | Grid of user profiles with guest mode option |
| `MainDashboardScreen.kt` | Main dashboard with Just Ride, Begin Class, Settings buttons |
| `PreRideIntentPrompt.kt` | Intent selection dialog (Reach New Milestones / Just Stay Fit) |
| `ClassLibraryScreen.kt` | Filterable list of class templates |
| `ClassDetailScreen.kt` | Interval breakdown and start button |
| `PostRideSummaryScreen.kt` | Ride summary with RPE selection and FTP breakthrough detection |
| `SettingsScreen.kt` | FTP, weight, theme, and BLE device management |
| `ProfileCreationDialog.kt` | Create new user profile |
| `FtpBreakthroughDialog.kt` | Prompt to accept/reject FTP increase |
| `JustRideScreen.kt` | Free ride without class structure |

#### Overlay HUD

| File | Purpose |
|------|---------|
| `HudOverlayManager.kt` | WindowManager-based floating overlay with ComposeView |
| `HudOverlayMain.kt` | HUD UI with metrics, targets, and controls |
| `OverlayPermissionHelper.kt` | SYSTEM_ALERT_WINDOW permission handling |
| `ZoneAlertManager.kt` | Haptic feedback and TTS for zone alerts |

**HUD Features:**
- Real-time cadence, power, and heart rate display
- Target zone indicators with alert animations
- Draggable overlay with drag handle
- Pause/Resume/Stop controls

---

### 6. Background Sync

| File | Purpose |
|------|---------|
| `WorkoutSyncWorker.kt` | WorkManager worker for syncing workouts to Supabase |

**Sync Features:**
- Compresses metrics into JSON payload
- Exponential backoff retry (3 retries)
- Triggered on workout completion

---

## Data Models

### UserEntity
```kotlin
- localUserId: Int (PK)
- name: String
- weightKg: Double
- ftpWatts: Int
- themePreference: String
- createdAt: Long
```

### ClassTemplateEntity
```kotlin
- id: String (PK)
- title: String
- category: String
- durationSec: Int
- intervalsJson: String
- createdAt: Long
```

### WorkoutEntity
```kotlin
- id: String (PK)
- userId: Int (FK → User)
- classId: String? (FK → ClassTemplate)
- durationSec: Int
- totalOutputKj: Double
- totalDistanceKm: Double
- avgCadence: Double
- avgPower: Double
- avgHr: Double?
- intentModifier: Double
- rpeRating: Int?
- timestamp: Long
```

### WorkoutMetricEntity
```kotlin
- id: Int (auto PK)
- workoutId: String (FK → Workout, CASCADE)
- timestampSec: Int
- cadence: Double
- resistance: Double
- power: Double
- heartRate: Int?
```

---

## Class Template JSON Format

```json
{
  "id": "HC-01",
  "title": "Hill Grind 20",
  "category": "HIIT & Heavy Climbs",
  "duration_sec": 1200,
  "intervals_json": "[{\"time_start_sec\":0,\"time_end_sec\":240,\"target_cadence_min\":80,\"target_cadence_max\":90,\"target_power_zone\":1},...]"
}
```

**Interval Structure:**
- `time_start_sec` - Interval start time
- `time_end_sec` - Interval end time
- `target_cadence_min` - Minimum target cadence
- `target_cadence_max` - Maximum target cadence
- `target_power_zone` - Target Coggan power zone (1-7)

---

## Power Zones (Coggan Model)

| Zone | Name | % of FTP |
|------|------|----------|
| Z1 | Active Recovery | < 55% |
| Z2 | Endurance | 56–75% |
| Z3 | Tempo | 76–90% |
| Z4 | Lactate Threshold | 91–105% |
| Z5 | VO2 Max | 106–120% |
| Z6 | Anaerobic Capacity | 121–150% |
| Z7 | Neuromuscular Power | > 150% |

**Intent Modifiers:**
- "Reach New Milestones" → `k = 1.05` (scales target power up 5%)
- "Just Stay Fit" → `k = 0.95` (scales target power down 5%)

---

## Dependencies

| Category | Library | Version |
|----------|---------|---------|
| Compose | compose-bom | 2024.10.01 |
| Material3 | material3 | (via BOM) |
| Room | room-runtime | 2.6.1 |
| Room | room-ktx | 2.6.1 |
| Coroutines | kotlinx-coroutines-android | 1.9.0 |
| Coroutines | kotlinx-coroutines-core | 1.9.0 |
| Serialization | kotlinx-serialization-json | 1.7.3 |
| Supabase | supabase-bom | 3.0.2 |
| Supabase | postgrest-kt | (via BOM) |
| Ktor | ktor-client-android | 3.0.2 |
| WorkManager | work-runtime-ktx | 2.9.1 |
| Lifecycle | lifecycle-service | 2.8.7 |
| Navigation | navigation-compose | 2.8.4 |

---

## Development Phases

Based on PLAN.md, the implementation is organized into 8 phases:

| Phase | Status | Description |
|-------|--------|-------------|
| 0 | ✅ Complete | Project scaffolding & build system |
| 1 | ✅ Complete | Local database (Room) & Supabase cloud sync |
| 2 | ⏳ In Progress | Telemetry engine (serial port & BLE) |
| 3 | ✅ Complete | Foreground service & workout lifecycle |
| 4 | ✅ Complete | Floating HUD overlay |
| 5 | ✅ Complete | HUD Compose UI & power zone engine |
| 6 | ✅ Complete | Main app UI (non-overlay screens) |
| 7 | ✅ Complete | Auto-FTP engine, workload JSON & cloud sync |
| 8 | ⏳ In Progress | Polish, testing & edge cases |

---

## Key Features

### Workout Tracking
- Real-time sensor data collection (cadence, resistance, power, heart rate)
- 1-second metric recording to local database
- Persistent foreground service with notification
- Crash recovery for incomplete workouts

### Class System
- 40+ pre-loaded workout class templates
- Categories: Endurance, Sweet Spot, Threshold, VO2 Max, HIIT & Heavy Climbs, Tabata Bursts, Recovery
- Interval-based target zones with visual feedback

### HUD Overlay
- Floating overlay that works over other apps
- Draggable positioning
- Real-time metrics display
- Target zone alerts with animations

### Analytics
- 20-minute peak power FTP estimation
- Biometric decoupling detection
- RPE-based FTP suggestions
- Personal best tracking

### Cloud Sync
- Supabase integration for data backup
- Workout and profile synchronization
- Background sync with WorkManager

---

## Testing

### Unit Tests
- `PowerZoneCalculatorTest.kt` - Tests for all 7 zones, edge cases, intent modifiers
- `PostWorkoutAnalyzerTest.kt` - Tests for FTP calculation, biometric decoupling, RPE suggestions

### Instrumented Tests
- `WorkoutDaoTest.kt` - Room DAO operations
- `WorkoutServiceTest.kt` - WorkoutState and WorkoutSession lifecycle

---

## Permissions Required

From `AndroidManifest.xml`:
- `SYSTEM_ALERT_WINDOW` - For floating HUD overlay
- `FOREGROUND_SERVICE` - For persistent workout service
- `FOREGROUND_SERVICE_DATA_SYNC` - For data sync service
- `BLUETOOTH` / `BLUETOOTH_ADMIN` / `BLUETOOTH_SCAN` / `BLUETOOTH_CONNECT` - For heart rate monitors
- `INTERNET` / `ACCESS_NETWORK_STATE` - For Supabase sync
- `POST_NOTIFICATIONS` - For workout notifications

---

## Build Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Run unit tests
./gradlew test

# Run instrumented tests
./gradlew connectedAndroidTest

# Install on connected device
./gradlew installDebug
```
