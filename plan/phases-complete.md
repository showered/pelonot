> Part of the Pelonot plan — the index is [PLAN.md](../PLAN.md).

## Phase 0: Project Scaffolding & Build System ✅

- [x] **0.1** Android project structure
- [x] **0.2** `minSdk 24`, `targetSdk 34`, `compileSdk 34`
- [x] **0.3** Dependencies wired (Compose, Material3, Room + KSP, Coroutines, Supabase, WorkManager, Navigation, Lifecycle, DataStore)
- [x] **0.4** `AndroidManifest.xml` with permissions
- [x] **0.5** `PelonotApp` application class
- [x] **0.6** Builds and runs
- [x] **0.7** Dependencies centralised in `gradle/libs.versions.toml`
- [x] **0.8** Supabase credentials moved out of source into `local.properties` → `BuildConfig`
- [x] **0.9** R8 enabled for release with kotlinx-serialization / Ktor / Room keep rules

---

## Phase 1: Local Database (Room) ✅

- [x] **1.1**–**1.4** `User`, `ClassTemplate`, `Workout`, `WorkoutMetric` entities
- [x] **1.5** DAOs with leaderboard, PB, average and FTP queries
- [x] **1.6** `AppDatabase` singleton
- [x] **1.7** KSP processes Room annotations
- [x] **1.12** `workouts.is_complete` so a ride is recorded from the moment it starts (fixes the foreign key ordering that prevented all metric recording)
- [x] **1.13** Repository layer over the DAOs (`UserRepository`, `ClassRepository`, `WorkoutRepository`, `SettingsRepository`)

> **Note:** the database uses `fallbackToDestructiveMigration()` while pre-release. **Replace this with explicit migrations before the first real user installs a build** — after that, a schema change silently deletes their entire training history.

> **Sizing, measured 1 August 2026.** A 45-minute ride is **292 KB** in this
> database — ~111 bytes per `workout_metrics` row, about a third of which is the
> 36-character UUID `workout_id` carried on every sample and again in its index.
> A year of four riders at a ride a week is ~61 MB. This is the store that
> fills, not the cloud; see *What a workout costs* and 23.4.

### Supabase (Cloud) — ⚠️ wired, never connected, and now gated

- [x] **1.8** Supabase project created
- [x] **1.9** SQL migration in `supabase/migration.sql`
- [x] **1.10** Client provider, now null when unconfigured so the app is fully offline-capable
- [x] **1.11** Sync repository with **typed DTOs** (was `Map<String, Any?>`, which could not serialize)
- [x] **1.14** `SyncOutcome` distinguishes *disabled* from *failed*, so the worker knows what is worth retrying
- [ ] **1.15** A row actually arriving in the cloud. The DTOs serialize now, and they serialize into columns the schema does not have — see **14.0**. Everything above is real; none of it has ever completed a request

---

## Phase 3: Foreground Service & Workout Lifecycle ✅

- [x] **3.1** `WorkoutService` with persistent notification and bound state
- [x] **3.2** Immutable `WorkoutSession` (in-place mutation plus `copy()` was being conflated away by `StateFlow`)
- [x] **3.3** `startWorkout` / `pauseWorkout` / `resumeWorkout` / `stopWorkout`
- [x] **3.4** Per-second metric recording, batched — **now actually works** (see Corrections)
- [x] **3.5** Notification channel and live metrics, with a real app icon
- [x] **3.6** Elapsed time from `SystemClock.elapsedRealtime()` rather than a drifting counter
- [x] **3.7** Pause time excluded from elapsed

---

## Phase 4: Floating HUD Overlay (WindowManager)

- [x] **4.1** `OverlayPermissionHelper`, with status shown in Settings
- [x] **4.2** `HudOverlayManager` using `TYPE_APPLICATION_OVERLAY` / `TYPE_PHONE`
- [x] **4.3** Lifecycle, saved-state and ViewModelStore owners for the ComposeView
- [x] **4.4** Drag-to-move, scoped to the handle so it cannot swallow button presses
- [x] **4.6** Composition disposed on hide (each ride used to leak one)
- [x] **4.7** Fixed the `view.context as Activity` cast that threw `ClassCastException` from the overlay's Service context — the HUD crashed every time it was shown
- [x] **4.5** The overlay is raised when a ride starts and dropped when it ends
- [x] **4.8** Docked full-width to one screen edge rather than floating: the rider is watching something else, and the middle of the screen has to stay clear
- [x] **4.9** `WorkoutService` owns the overlay, not the ride screen's ViewModel — a ride outlives the screen, which is the entire point of the HUD

---

## Phase 5: HUD Compose UI & Power Zone Engine ✅

- [x] **5.1** HUD theme
- [x] **5.2** `MetricCard` with animated transitions
- [x] **5.3** Target zone indicator with out-of-range alerting
- [x] **5.4** ~~Collapsible leaderboard panel~~ — **removed in the HUD redesign.** It was built for a 300dp floating card and has no home on a full-width edge strip whose whole purpose is to stay out of the way. `WorkoutRepository.leaderboardFor` still exists and is still correct; nothing renders it. Tracked as 11.6 rather than left as a silently-broken tick.
- [x] **5.5** HUD controls — a single pause/resume toggle rather than two buttons, one always a no-op
- [x] **5.6** Assembled HUD content, using theme colours rather than hardcoded constants
- [x] **5.7** `PowerZone` with contiguous zone bands
- [x] **5.8** `RideIntent` as a typed enum (was a bare display string a typo could silently defeat)
- [x] **5.9** Zone alerting, suppressed when the rider is stationary

---

## Phase 6: Main App UI ✅

- [x] **6.1** `ProfileSelectorScreen` with adaptive grid and distinct guest treatment
- [x] **6.2** `ProfileCreationDialog`
- [x] **6.3** `MainDashboardScreen` — now showing **real** statistics
- [x] **6.4** `PreRideIntentPrompt` describing each option's actual effect
- [x] **6.5** `ClassLibraryScreen` with working category filters
- [x] **6.6** `ClassDetailScreen` — interval breakdown **now renders** (see Corrections)
- [x] **6.7** `PostRideSummaryScreen` reading real figures from the database
- [x] **6.8** `SettingsScreen` — FTP, weight, theme, telemetry source, BLE, overlay permission, cloud sync, all persisted
- [x] **6.9** Navigation via `NavHost` with typed destinations; ride screens are real destinations rather than booleans checked before the graph
- [x] **6.10** ViewModels with `StateFlow`; no database access from composables

---

## Phase 9: Ride Integration ✅

**Goal:** Make a class actually run. Today a ride records telemetry and totals, but the class's own intervals do nothing and the floating HUD never appears.

### 9.1 Service lifecycle ✅
- [x] **9.1.1** Ride start launches `WorkoutService` as a foreground service with user, class, intent and FTP
- [x] **9.1.2** `RideViewModel` binds to the service and observes `workoutState` and `currentSession`
- [x] **9.1.3** End Ride stops the service and passes the real workout id to the summary

### 9.2 HUD overlay activation ✅
- [x] **9.2.1** `HudOverlayManager.show()` when a ride starts, `hide()` when it ends
- [x] **9.2.2** The service publishes one `RideSnapshot` the overlay renders from; telemetry stays on its own higher-rate flow so a sensor packet does not recompose the whole strip
- [x] **9.2.3** The overlay's pause/resume/stop drive the service directly
- [x] **9.2.4** Overlay permission asked for at ride start, with "not now" and "don't use the HUD" both honoured
- [x] **9.2.5** The overlay stands down while the app's own ride screen is on top, so the rider never sees two of everything

### 9.3 Class interval engine ✅
- [x] **9.3.1** `ClassIntervalEngine` over `List<Interval>` — a pure function of elapsed time, evaluated on the service's existing ticker rather than running a second clock that would drift away from it
- [x] **9.3.2** `IntervalState`: current and next interval, index, elapsed and remaining, class progress, cue
- [x] **9.3.3** Drives the target gauges on both the HUD and the ride screen
- [x] **9.3.4** The ride finishes itself when the class timer runs out

### 9.3a Interval preview & countdown ✅
- [x] **9.3a.1** `next` on `IntervalState`, null on the final interval by design
- [x] **9.3a.2** The interval clock is a draining progress ring around the zone badge, prominent on every interval rather than only recovery ones
- [x] **9.3a.3** Preview card with the next interval's zone colour, shape, cadence target and length
- [x] **9.3a.4** Five-second warning: the edge hairline thickens and pulses in the next zone's colour, the preview card scales up into a countdown, the strip washes with the new zone's colour, and a haptic fires on each tick
- [x] **9.3a.5** "Give it everything" on the last *hard* interval, "Cool down — ride easy" on a recovery finish, no next-interval preview on the final interval

### 9.4 Sensor data off-hardware ✅
- [x] **9.4.1** `SimulatedSensorProvider`
- [x] **9.4.2** Settings toggle between real and simulated input
- [x] **9.4.3** Graceful fallback when `/dev/ttyS2` is unavailable

### 9.5 Post-ride data flow ✅
- [x] **9.5.1** Final session collected from the service
- [x] **9.5.2** Real duration, average power, output and distance shown in the summary
- [x] **9.5.3** `PostWorkoutAnalyzer` run for FTP breakthrough detection
- [x] **9.5.4** `WorkoutSyncWorker` enqueued when a ride with a profile is saved, and when a guest ride is later filed against one
