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
- `SupabaseSyncRepository.kt` — `syncWorkout()` (with compressed JSON metrics payload), `syncProfile()`, `fetchClassTemplates()`

**Build status:** `./gradlew assembleDebug` — SUCCESSFUL (KSP processes Room, Supabase deps resolve)

---

## GitHub

- Remote: `https://github.com/showered/pelonot.git`
- Branch: `setup`
- Commits: 3 (scaffold, room-db, supabase-client)