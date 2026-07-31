# Pelonot — Implementation Plan

> **Open-Source Peloton Client** — A subscription-free fitness app for Peloton
> bikes (Gen 1/Gen 2). **A stock, un-jailbroken bike is the supported target**;
> telemetry comes from Peloton's own sensor service, not from root (see 2.1a).

---

## How to use this plan

1. **Each checkbox is one focused task** — small enough for a single session, large enough to matter.
2. **A box is only ticked when the behaviour has been observed working**, not when the code was written. Several items in this plan were previously ticked while the feature was non-functional (see *Corrections* below); that is the failure mode this rule exists to prevent.
3. **Work phases in order** where later ones build on earlier ones. See *Where the work stands* immediately below for the current priority.
4. When switching models or sessions, paste the current plan state so the next session knows where to pick up.

---

## Where the work stands — read this first

**Latest session: 31 July 2026 (second sitting), on the bike with a rider
pedalling and wearing a heart-rate strap.** Everything that needed a human on
the pedals is now done except the endurance ride (10.6).

Closed this session: **10.4** (HUD over Netflix), **10.5 / 2.3.5** (real BLE
strap), **11.1.5** (pause/resume/stop from the strip with the app in the
background), **11.1.6** (coach audible over a playing film), **2.1a.5**
(resistance is a true 0–100).

Three defects were found in the process, all of them invisible from the UI and
none findable without the hardware — the strap could never have been paired on
this tablet at all, the coach had never once ducked the rider's video, and
`avg_hr` had been recording the lowest reading of the warm-up instead of the
average. All three are fixed, tested and in the *Corrections* table; the rule
they sharpen is at the end of it and is worth reading.

**2.2.5 was attempted and deliberately not shipped.** The sweep is checked in
under `calibration/`; the fit failed cross-validation, and the reasoning is in
2.2.5 and `calibration/README.md`.

### Still needing a rider on the bike

Only **10.6** (a full-length ride: battery, thermals, memory, dropped samples
— longest so far is 8 minutes) and **another PowerModel sweep** for 2.2.5,
denser at the low and high ends of the resistance range.

---

**The session before it, same day, established how telemetry gets in at all.**
It changed the shape of the project, so read this too.

**The headline: bike telemetry works on real hardware, and every assumption
about how it would work was wrong.** The app had spent its whole history
preparing to read a serial port that either does not exist or belongs to
Bluetooth, on a bike it assumed was jailbroken and is not. The route that
works is binding Peloton's own `SensorService`. Full detail in **2.1a** —
read that section before touching anything under `data/sensor/`.

Observed on the bike: cadence 0→58 rpm, resistance 16→59% tracking the knob,
power 0→176 W, 246 per-second rows persisted, ride saved at 245 s / 6.7 kJ.

Three consequences worth carrying forward:

1. **Watts are measured on hardware, not modelled.** `PowerModel` does not run
   during a bike ride. `SensorReading.powerIsMeasured` marks which is which.
   Much of the uncalibrated-power caveat that hangs over 16–18 evaporates for
   real rides, and 2.2.5 now has a way to fix it for simulated ones.
2. **`SerialSensorSource` and `SerialProtocolParser` are dead on this
   hardware.** Correct code, wrong target. Kept only for a rooted tablet.
3. **The next unknown was the HUD, not the sensors** — and the second sitting
   answered it. Phase 11's premise, glancing at a strip while watching
   something else, has now been seen on the bike over Netflix (10.4) and the
   strip's controls work from the background (11.1.5). What remains in Phase 11
   is code, not verification.

### What to do next, in order

| Next | Why now |
|------|---------|
| **14.1.6** Cloud round trip from the app | Does **not** need the bike; simulated telemetry drives it. It is the last thing standing between Phase 14 and done, and 15/17/18 all sit on it. Note a *guest* ride never syncs by design, so this needs a real profile — and there is still **no profile on the tablet**, every ride so far has been a guest ride |
| **11.1a** Doors between the HUD and the app | Unchanged in priority and still the journey a rider makes most often. 11.1a.3 confirmed missing on the bike: the app does **not** come forward when a ride ends from the HUD — Netflix simply stayed in front |
| **11.1.3 / 11.1.4** Tap-to-collapse and re-docking | The last two unverified HUD interactions, and unlike the rest of 11.1 they need *code* before they need a rider |
| **20.1** The profile selector | Confirmed on the real tablet, not just the emulator: one small card in the top-left corner of a 1920×1080 screen with the rest black |
| **11.5** Volume sliders | Small, and closer to fundamental than it sounds: the tablet has **no status bar and therefore no system volume UI**, so the app is the only place either level can be changed. Needs no bike |
| **2.2a** Auto-calibrate `PowerModel` per bike | Supersedes the manual sweep (2.2.5), which does not scale to anyone else's bike. Every hardware ride already records measured watts beside the model's own inputs. Needs no bike to build, only rides to feed it |

Still blocked on things not to hand: **10.6** needs a full-length ride.

Worth knowing before planning the next bike session: **the dashboard is fine in
landscape** on the real tablet — it fills the width and does not show the empty
right-hand side 11.3.1 describes. 11.3.1 may be stale or emulator-specific and
should be re-checked before anyone spends a session on it.

---

## Status at a glance

| Phase | Area | State |
|-------|------|-------|
| 0 | Scaffolding & build system | ✅ Complete |
| 1 | Local database (Room) + Supabase | 🔶 Room complete — Supabase writes now land, app-driven sync unproven (14.1.6) |
| 2 | Telemetry engine (sensor service, BLE, simulated) | ✅ **Verified end to end on real hardware** — bike board (2.1a), resistance scale (2.1a.5) and a real BLE strap (2.3.5). Only `PowerModel` calibration remains open (2.2.5) |
| 3 | Foreground service & workout lifecycle | ✅ Complete |
| 4 | Floating HUD overlay | ✅ Complete — raised and driven by the ride |
| 5 | HUD Compose UI & power zones | ✅ Complete |
| 6 | Main app UI | ✅ Complete |
| 7 | Auto-FTP, workload JSON, cloud sync | 🔶 Auto-FTP complete — cloud sync wire path proven, worker unverified (14.1.6) |
| 8 | Polish, testing, edge cases | 🔶 Functional items done; cosmetic backlog remains |
| 9 | Ride integration | ✅ Complete — a class runs |
| 10 | Hardware validation | 🔶 Sensor path, protocol, a real ride, HUD-over-video and the BLE strap all done — only the full-length ride (10.6) remains |
| 11 | **HUD-first experience — the current priority** | 🔶 In progress |
| 12 | Ride history & the rider's own record | 🔶 History, detail, delete and migrations done; export and housekeeping remain |
| 13 | Units and display preferences | ✅ Complete — miles, and the locale default that goes with them |
| 14 | Cloud sync that actually reaches the cloud | 🔶 **In progress** — schema fixed and writes verified against the live project; app-driven round trip still open (14.1.6) |
| 15 | Accounts, login and multi-device sync | ❌ Not started — *fundamental once 14 works* |
| 16 | Data visualisation | ❌ Not started — half fundamental, half polish |
| 17 | Companion web application | ❌ Not started — *nice to have* |
| 18 | Social features in the Android app | ❌ Not started — *nice to have* |
| 19 | Ideas worth having, ranked | ❌ Not started — mixed |
| 20 | Who's riding — profile selector & avatars | ❌ Not started — the selector is *fundamental* on a shared bike |

---

## What is fundamental and what is not

Phases 12–19 were requested together, but they are not the same kind of work,
and building them in the order they were listed would be a mistake. The
ordering below is the one to work in.

**Fundamental — the app is incomplete without these:**

| # | Why it is not optional |
|---|------------------------|
| 12 Ride history + delete | The app records rides and offers the rider no way to see them or get rid of a bad one. Everything downstream — charts, sync, social — is a view onto a history screen that does not exist. |
| 13 Units | A UK rider is shown kilometres with no way to change it. It is an afternoon's work and it is currently wrong for a large fraction of the audience. |
| 14 Working sync | Cloud sync was ticked as complete having **never written a single row** — see 14.0. The schema is fixed and writes now land; the app driving it is still unproven. Every feature in 15, 17 and 18 sits on top of this. |
| 15 Accounts | Sync without an identity puts every rider's data in one anonymous pool. This is also where the current RLS policies stop being a placeholder and start being a security problem. |
| 12.7 Room migrations | `fallbackToDestructiveMigration()` deletes the rider's entire training history on any schema change. Phases 12–19 all change the schema. This has to go first. |

Two more that belong in the fundamental list, added after riding the app on the
tablet:

| # | Why it is not optional |
|---|------------------------|
| 11.1a Getting between the HUD and the app | There is no door between the HUD and the full app in either direction, and the app does not come forward when the class ends. This is the journey a rider makes most often during a ride and it currently routes through the launcher. |
| 20.1 The profile selector | It is the first screen anyone sees and the thing that makes a shared household bike work, and it is a cluster of small cards in the corner of a 1920×1080 screen. |

**Nice to have — real value, none of it load-bearing:**

16 (beyond the post-ride charts), 17, 18, most of 19, and the avatar work in
20.2. A companion web app
and a friends feed are good ideas for an app people already use daily; they
are not what makes people use it daily. The bike, the HUD and an honest record
of the ride are.

> One caution that applies to all of 16–18, **now much narrower than it was**:
> a ride on the bike records the board's own measured watts (2.1a), so those
> numbers are as comparable between riders as the hardware is. It is
> `PowerModel` that stays uncalibrated (2.2.4), and it now only governs
> simulated rides and the 11.2.1 resistance band. Charts, leaderboards and
> friend comparisons should read `SensorReading.powerIsMeasured` and say which
> they are showing, rather than captioning everything "estimated" — or, worse,
> presenting a modelled figure as fact.

---

## Corrections — items previously ticked that were not working

Recorded so the same mistakes are not repeated, and because several of these
were the *reason* a downstream feature looked broken.

| Item | Was ticked | Reality |
|------|-----------|---------|
| 1.11 Supabase repository | ✅ | Passed `Map<String, Any?>` to kotlinx.serialization, which has no serializer for `Any`. Every call threw and was swallowed by `runCatching`. |
| 2.3 BLE heart rate | ✅ | `connectGatt(null, …)`; CCCD descriptor never written, so the strap was never asked to notify. No reading could ever arrive. |
| 2.5 Metrics calculator | ✅ | Integrated power against sample *count*, not elapsed time — a 45-minute ride over-reported energy by ~1000×. |
| 3.4 Per-second metric recording | ✅ | `workout_metrics` has a foreign key onto `workouts`, but the workout row was only written at ride *end*. Every insert violated the constraint. No ride ever stored a time series. |
| 5.7 Power zone calculator | ✅ | Zone ranges left gaps (0.55–0.56 etc.); a lookup that matched nothing fell through to Z7, so an easy warmup was reported as Neuromuscular Power. |
| 6.6 Class detail screen | ✅ | Interval model used camelCase field names against snake_case assets. Every decode threw and was caught into `emptyList()`. No class ever displayed an interval. |
| 8.3 Crash recovery | ✅ | `getIncompleteWorkout()` returned the most recent workout with no completion filter, so the app offered to resume the ride you had just finished, on every launch. |
| 8.7 Unit tests | ✅ | `PostWorkoutAnalyzerTest.kt` was committed empty. |
| 8.8 Instrumented tests | ✅ | `WorkoutDaoTest` referenced `localUserId` / `classTemplateId`, fields that never existed on the entity. It had never compiled. |
| 8.11.17 Dashboard redesign | ✅ | Shipped with `"12.5"` and `"8.3"` kJ hardcoded, and a constant "FTP Stable" badge, shown as the rider's own statistics. |
| 8.5 Haptic feedback | ✅ | The app never declared `android.permission.VIBRATE`. Every call threw `SecurityException` into a `runCatching`, so the buzz simply never happened. Found by reading logcat during a real ride — it is invisible from the UI. |
| 8.8 Instrumented DAO tests | ✅ | Rewritten so they compiled, and ticked — but `@Before fun setup() = runBlocking { … }` infers its return type from the last expression, and `insertUser` returns a row id. JUnit rejects a `@Before` that is not void, so the class failed to initialise and **all ten tests silently never ran**. They pass now. |
| 8.3a Crash recovery prompt | (untickable) | The service exposed `recoverableWorkout`, but nothing binds to the service on a cold start — which is exactly the situation a crash leaves behind — so the prompt could never have appeared wherever it was rendered. |
| 2.1 Serial telemetry | (never ticked) | Not a false tick, but the same failure shape and the most expensive one yet: **the entire premise was wrong and no test could have said so.** The app spent its whole history preparing to read a character device that either does not exist (`ttyS1`) or belongs to Bluetooth (`ttyS2`), on a bike it assumed was jailbroken and is not. `SerialSensorSource`, `SerialProtocolParser`, `CadenceTracker` and `PowerModel` are all correct code aimed at the wrong target. Fifteen minutes on the actual hardware settled it. See 2.1a. |
| 2.3 BLE heart rate (again) | ✅ | **The manifest never declared `ACCESS_FINE_LOCATION`**, which below API 31 *is* the BLE scan permission. A runtime request for an undeclared permission is denied instantly — no dialog, nothing in the log — so on the bike's own Android 11 tablet no strap could ever be found. The rewrite in 2.3 was correct code behind a door that was nailed shut. Behind it, the Scan button reported `PermissionRequired` and never requested anything, so there was no way to grant from inside the app either. Found the first time a strap was put on. |
| 8.6 TTS audio cues | ✅ | Two defects, neither in the coaching logic. `RideCoach` set the language and audio attributes immediately after the `TextToSpeech` constructor, but the engine binds asynchronously, so both were discarded — the device said `setLanguage failed: not bound to TTS engine` and nothing read it. And audio attributes only *describe* a sound; they request nothing. Ducking needs `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`, which nothing ever asked for, so the cue was spoken at full volume underneath a film at full volume. The tick claimed "the rider's video ducks under them" and the video had never ducked once. |
| 3.4 / 9.5.2 Average heart rate | (untickable) | `workouts.avg_hr` was written as **79.0 against a true mean of 105.4** over the ride's own 314 metric rows — 79 being the lowest reading of the warm-up. The running mean was rounded to an `Int` every tick, so once one sample moved it by less than 1 bpm the whole increment was discarded and the average froze. `avg_power` and `avg_cadence` beside it, both `Double`, were exact. A second bug sat behind it: heart rate divided by the *tick* count rather than the heart-rate sample count, which would have buried the readings of any strap connecting mid-ride. Invisible from every screen; found by comparing the stored aggregate against the samples it claimed to summarise. |
| 1.11 / 7.4 Cloud sync (again) | ✅ | **No row had ever reached the cloud** — `profiles` and `workouts` were both empty, by count. The cause was not the DTOs: `migration.sql` never granted `anon` a single table privilege, so every request failed `42501` before RLS was consulted. Behind that sat four more defects, one of which (epoch millis into a `TIMESTAMPTZ`) was invisible to code reading and only appeared on a real insert. Failures returned `SyncOutcome.Failed`, which nothing displays. Full detail in **14.0**. |

All of the above are now fixed and covered by tests, **except the last** —
the wire path is proven and `WorkoutSyncWorker` driving it end to end is not,
which is 14.1.6.

Note the shape of that one, because it is the most expensive kind here: the
first diagnosis, made by reading the code against the migration, was confidently
wrong about the cause and wrong about a security claim. Only a request to the
live project produced the real answer. **For anything involving the cloud, read
the code to form a hypothesis and then go and hit the endpoint.**

The pattern is worth naming, because it has now happened eleven times: a
failure path that is caught, logged and returned as a value nothing reads is
indistinguishable from success from every surface anyone looks at. Where a new
phase below adds a feature that can fail quietly — sync, login, export,
deletion — **it also has to add somewhere the rider can see that it failed.**

The three added on 31 July 2026 sharpen it into a second rule. All three were
found within an hour of a rider sitting on the bike with a strap on, and none
of them could have been found any other way:

- **A permission absent from the manifest is denied with no dialog and no log
  line.** Two of the project's defects are now this exact shape (VIBRATE, then
  ACCESS_FINE_LOCATION). Before trusting any runtime permission path, check the
  manifest declares what the code asks for.
- **Configuring an Android service object before it has bound silently does
  nothing.** `TextToSpeech` warns and continues.
- **An aggregate is only trustworthy when checked against the rows it
  summarises.** `avg_hr` was wrong for the whole project's history beside two
  neighbours that were right.

> **Verify against the source data, not the surface.** Every one of these
> looked correct from the UI. The database, `dumpsys` and logcat are where they
> were visible — and for the ducking, only the rider's ears.

---

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

### Supabase (Cloud) — ⚠️ wired, never connected

- [x] **1.8** Supabase project created
- [x] **1.9** SQL migration in `supabase/migration.sql`
- [x] **1.10** Client provider, now null when unconfigured so the app is fully offline-capable
- [x] **1.11** Sync repository with **typed DTOs** (was `Map<String, Any?>`, which could not serialize)
- [x] **1.14** `SyncOutcome` distinguishes *disabled* from *failed*, so the worker knows what is worth retrying
- [ ] **1.15** A row actually arriving in the cloud. The DTOs serialize now, and they serialize into columns the schema does not have — see **14.0**. Everything above is real; none of it has ever completed a request

---

## Phase 2: Telemetry Engine

**Goal:** Read real-time sensor data from the Peloton sensor board (serial) and BLE HR monitors.

### 2.1 Serial port
- [x] `SerialSensorSource` as a cold flow — collection opens the port, cancellation closes it
- [x] Removed the `close()` → `reconnect()` → `close()` cycle that made ending a workout start an endless retry loop
- [x] Device path injectable (docs said `/dev/ttyS1`, code opened `/dev/ttyS2`)
- [x] **2.1.3** Verified against real hardware, 31 July 2026 — **and the answer is that this whole approach cannot work on a stock bike.** See 2.1a. `SerialSensorSource` is kept as the fallback for a genuinely rooted tablet and is no longer the default

### 2.1a How telemetry actually gets in — findings on the bike, 31 July 2026

Established on a real Gen 1 (`PLTN-RB1VQ`, Android 11) over wireless adb.
**Every assumption this project held about reading the sensor board was wrong**,
and it was wrong in a way no amount of code reading would have exposed.

- `/dev/ttyS1` **does not exist**. `/dev/ttyS2` exists but is the **Bluetooth
  HCI UART** (`bluetooth:bluetooth`) — opening it would never have produced
  bike telemetry, only a fight with the Bluetooth stack.
- The sensor board is `/dev/ttyO0` (OMAP naming; same char device as the absent
  `ttyS1`, major 4 minor 65), owned `system:system` mode `0660`. Confirmed
  independently by the constant `UART_DEFAULT_DEVICE = "/dev/ttyO0"` inside
  Peloton's own service.
- An ordinary app uid cannot open it, and **the bike is not jailbroken**:
  `ro.build.type=user`, release-keys, `ro.secure=1`, no `su`, no Magisk. The
  premise in the README — that this app targets *jailbroken* bikes — is not the
  situation the app is actually installed into, and does not need to be.
- The route that works is **binding Peloton's own `SensorService`**, which
  already owns the port and hands out decoded values. Its `<service>` tag is
  `exported="true"` with **no `android:permission` attribute**, so the
  `onepeloton.permission.ACCESS_SENSOR_SERVICE` it declares at `signature`
  level is never enforced and any app may bind. That is why third-party bike
  apps work on an unmodified tablet.

- [x] **2.1a.1** `PelotonSensorServiceSource` — binds
      `android.intent.action.peloton.SensorData` with category
      `com.peloton.sensor.category.BIKE`, registers `REGISTER_RPM` (1),
      `REGISTER_WATT` (2) and `REGISTER_RESISTANCE` (3) with a `replyTo`
      [Messenger], and receives repeating `EVENT_*` (7/8/9) replies carrying a
      `data` float. Written against the decompiled contract rather than copied
      from the obvious reference client, which is GPL-3.0 against this
      project's Apache-2.0
- [x] **2.1a.2** `<queries>` for `com.peloton.service.SensorData`. Package
      visibility filtering (targetSdk 30+) otherwise hides it and `bindService`
      fails with nothing useful in the log
- [x] **2.1a.3** A `TIME_OUT` reply carries a `0.0` payload and means *the
      board did not answer*, not *the rider produced nothing*. It is dropped
      rather than recorded, and a run of them fails the flow so
      `SensorRepository` backs off — the same argument as nullable
      `heartRateBpm`
- [x] **2.1a.4** **Observed on the bike**: cadence 0→58 rpm, resistance 16→59%
      tracking the knob, power 0→176 W, 246 per-second rows written to
      `workout_metrics`, `avg_hr` NULL with no strap, ride persisted
      `is_complete = 1` at 245 s / 6.7 kJ
- [x] **2.1a.5** **Resistance is a true 0–100.** Both ends driven to the stop
      on the bike, 31 July 2026: full anticlockwise reads `0.0` and full
      clockwise reads `100.0`, each held flat for twenty seconds, and nothing
      on the path from the board to `workout_metrics` clamps either value. The
      rider could not turn the pedals at 100, which is the physical
      corroboration
- [ ] **2.1a.5a** **The sensor saturates at 100 before the knob does.** The
      rider reports the display reaching 100 and the knob then continuing to
      turn some way further before clicking against its stop. So there is dead
      travel at the top where resistance is still rising and the app cannot
      see it. Consequences: a prescribed band (11.2.1) can say "100" but cannot
      distinguish 100 from well past it, and a rider at the top of the range
      gets no feedback for the last part of the turn. Worth checking whether
      the same dead band exists at the bottom

### 2.2 Protocol parsing
- [x] `SerialProtocolParser` as a pure, testable state machine
- [x] Carries partial commands across read boundaries (a trailing `R` used to lose its value byte)
- [x] `CadenceTracker` smooths jitter and **decays to zero when pedalling stops** (cadence used to freeze at its last value forever)
- [ ] **2.2.4** Validate `PowerModel` coefficients against a known curve or a real power meter. **Absolute watts from the model are not trustworthy** — they are self-consistent between your own rides only. **Largely superseded on real hardware** (2.1a): the sensor board reports watts directly, so a ride on the bike no longer infers power at all. `SensorReading.powerIsMeasured` marks which is which. The model still governs simulated rides and the resistance band in 11.2.1, so the caveat stays until it is either calibrated or confined to simulation
- [ ] **2.2.5** Now that measured and modelled watts can be produced for the *same* cadence and resistance, the bike is itself the calibration source 2.2.4 has always lacked. Log both during a ride and fit the coefficients against it.

      **Attempted on the bike, 31 July 2026. The capture method works; one
      sweep was not enough, and the coefficients are unchanged.** A 494-second
      Just Ride sweeping resistance 0–75 against cadence 30–101 is checked in
      at `calibration/2026-07-31-sweep-PLTN-RB1VQ.csv`, with the method and
      full reasoning in `calibration/README.md`.

      What it settled: **the shipped coefficients are badly wrong** — RMSE
      137 W, median absolute error 66%, R² 0.21 over 310 steady-state samples.
      2.2.4's caveat was, if anything, understated.

      Why nothing was shipped: a refit of `P = (a + b·R^k)·rpm + c·rpm³`
      (monotone in resistance, so the `resistanceForWatts` inverse stays
      well-behaved) reaches median 10.7% *in sample*, but holding out one
      resistance level and predicting it gives 13–25% above 75 W — and **at
      R=40 the existing coefficients beat the refit**, 11.4% against 22.8%.
      That is a fit interpolating between the six levels that happened to get
      ridden, not a description of the machine. The unconstrained exponent
      (k ≈ 2.86) also puts R=100 at 80 rpm near 1 kW, because nothing above
      R=75 was sampled.

      A sufficient sweep needs more resistance levels — especially **5–20 and
      80–100**, barely touched here — each held at three or more cadences,
      including high resistance at high cadence, and ideally a second rider to
      separate the machine's curve from one person's pedalling.

      **But prefer 2.2a to doing this again.** A manual sweep does not scale
      past the one person willing to perform it, and per-unit variation and
      wear mean a constant in the source is the wrong shape of answer anyway.
      Every hardware ride already carries the data; 2.2a is the version of this
      that works on a stranger's bike

### 2.2a Auto-calibration — the answer to "what about everyone else's bike?"

**This supersedes the manual sweep as the way `PowerModel` gets calibrated.**
2.2.5 asked one rider to sweep the operating range for five minutes; that does
not scale to other people's bikes, and the cross-validation failure showed it
barely worked for one. The realisation that makes it unnecessary:

> **On real hardware every ride is already a calibration dataset.** The board
> reports measured watts alongside the cadence and resistance that
> `PowerModel` takes as inputs (2.1a). The app does not need a special ride —
> it needs to notice what it is already recording.

Three reasons this has to be per-bike and continuous rather than a constant
shipped in the source:

1. **Per-unit variation.** The resistance figure comes from a position sensor
   whose mapping is unit-specific, and this bike already proves it: the sensor
   pins at 100 while the knob keeps turning (2.1a.5a). A curve keyed on
   "resistance percent" inherits that bike's own quirk.
2. **Drift over time.** Whatever the Gen 1's braking mechanism turns out to be
   — the repo has never established it, and it is worth writing down when
   someone does — a resistance system that is worn, warm or dirty does not
   behave like a new one. If the mechanism is friction-based, pad wear alone
   makes a fixed constant wrong within months.
3. **It costs the rider nothing.** Normal riding covers the operating range
   over a few weeks without anyone being asked to perform a calibration
   ritual, which is the only version of this that a stranger will ever do.

- [ ] **2.2a.1** Accumulate steady-state `(cadence, resistance, measured
      watts)` from rides where `powerIsMeasured` is true. Reuse the filter that
      worked on the 31 July sweep: drop samples where the knob is mid-turn or
      cadence is lurching, since those are transitions rather than operating
      points. Store a compact summary, not every sample — a grid of binned
      means is enough to fit against and does not grow without bound
- [ ] **2.2a.2** **Calibration belongs to the bike, not the rider.** A
      household bike has several profiles and one resistance mechanism, so this
      is device-level state that every profile shares. It must not live on
      `profiles` and must not sync as if it were personal (15)
- [ ] **2.2a.3** **Do not adopt a fit that cannot beat the shipped curve.**
      This is the whole lesson of 2.2.5: hold out a resistance level, predict
      it, and keep the generic coefficients unless the fit genuinely wins. An
      auto-calibration that silently makes the numbers worse is the same
      failure as everything in the *Corrections* table
- [ ] **2.2a.4** Require coverage before fitting at all. The 31 July sweep had
      six distinct resistance levels and that was not enough to determine the
      exponent. Track which cells of the resistance × cadence grid have been
      ridden and stay on the shipped curve until enough of them have
- [ ] **2.2a.5** Weight recent rides more heavily so the fit tracks drift
      rather than averaging a worn mechanism together with how it behaved when
      it was new
- [ ] **2.2a.6** Say so in Settings: whether this bike is running the shipped
      curve or its own, how much of the range it has seen, and when it last
      re-fitted. A calibration that silently does nothing is indistinguishable
      from one that works (see the *Corrections* rule)
- [ ] **2.2a.7** Simulated rides keep the shipped curve. There is no bike to
      calibrate against and the numbers are fiction by construction

> **Scope worth keeping in view before anyone builds this.** On real hardware
> `PowerModel` does not run: a ride records the board's measured watts, and
> another rider's recorded history is unaffected by any of this. Calibration
> only governs **simulated rides** and the **prescribed resistance band**
> (11.2.1) — that is, the quality of a *suggestion*, never the integrity of a
> record. Worth doing, and not worth blocking anything else on.

### 2.3 BLE heart rate
- [x] Rewritten against the Bluetooth SIG spec: real Context, CCCD descriptor write, service-UUID scan filter, cancellable scanning, bounds-checked parsing
- [x] Runtime permission handling across API 24–34 — **and the manifest entry
      it needed.** `ACCESS_FINE_LOCATION` was never declared, and below API 31
      that *is* the BLE scan permission; a runtime request for a permission
      absent from the manifest is denied instantly with no dialog and nothing
      in the log. The bike's tablet is Android 11, so heart rate could never
      have worked on the one device this app exists for. Same shape as the
      VIBRATE bug in 8.5
- [x] `HeartRateStatus` surfaced in Settings
- [x] **2.3.4a** Scan asks for the permission it needs. It previously reported
      `HeartRateStatus.PermissionRequired` and stopped there —
      `SettingsViewModel.heartRatePermissions()` existed and nothing called it
      — so the message named the blocker and offered no way past it
- [x] **2.3.5** **Verified with a real strap on the bike, 31 July 2026.**
      Wahoo TICKR FIT found and connected on the first scan; a 314-second ride
      wrote a heart rate on **every one of its 314 metric rows**, 79–125 bpm,
      no nulls and no fabricated zeros

### 2.4 Sensor repository
- [x] Unified `StateFlow<SensorReading>` merging bike telemetry and heart rate
- [x] **One** reconnect policy with exponential backoff (there were previously three competing ones)
- [x] Hardware mode retries rather than falling back to simulation, so a ride never records fabricated numbers

### 2.5 Metrics calculator
- [x] Total output by integrating power over **elapsed time**
- [x] Rolling averages over time windows
- [x] Cumulative distance estimation
- [x] Long sample gaps clamped so a backgrounded app cannot integrate idle time

### 2.6 Simulated source ✅
- [x] `SimulatedSensorSource` producing a plausible effort profile with lagging heart rate
- [x] Settings toggle: Auto / Hardware / Simulated
- [x] Ride screen states plainly when telemetry is simulated

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

## Phase 7: Auto-FTP Engine, Workload JSON & Cloud Sync ✅

- [x] **7.1** `PostWorkoutAnalyzer` — 20-min peak (O(n) sliding window over full-length windows only), biometric decoupling, RPE survey
- [x] **7.2** `FtpBreakthroughDialog`
- [x] **7.3** FTP update flow writing through to the profile
- [x] **7.4** `WorkoutSyncWorker` with a network constraint, retry ceiling, unique work, and a result it actually reads
- [x] **7.5** `ClassTemplateSeeder` listing the assets directory rather than a hardcoded category list
- [x] **7.6** Class template JSON in `assets/classes/`
- [x] **7.7** Seeding moved to application scope (a `LaunchedEffect` was cancelled by navigation mid-seed)

---

## Phase 8: Polish, Testing & Edge Cases

- [x] **8.1** Serial disconnection handled with a single backoff policy
- [x] **8.2** BLE disconnection handled without self-triggered reconnect loops
- [x] **8.3** Crash recovery via `is_complete`, surfaced through `WorkoutService.recoverableWorkout`
- [x] **8.3a** Recovery prompt shown at launch, driven from `AppViewModel` rather than the service. It offers to **keep** the ride, not resume it: the rider stopped pedalling when the app went away, and restarting the clock would splice a gap of unknown length into the record. `WorkoutAggregates` rebuilds the totals from the samples that did land.
- [x] **8.4** Guest post-ride: file against an existing profile, create one on the spot, keep as a household guest ride, or discard
- [x] **8.5** Haptic feedback for interval alerts — **and the `VIBRATE` permission it needs**
- [x] **8.6** TTS audio cues, with navigation-guidance audio attributes so the rider's video ducks under them
- [x] **8.6a** `RideCoach` wired into the ride, driven by the pure `RideCoachPolicy`. Replaces `ZoneAlertManager`, which had no caller and no decision logic to call it with.
- [x] **8.7** Unit tests: `PowerZone`, `PostWorkoutAnalyzer`, `WorkoutMetricsCalculator`, `RideIntent`, `SerialProtocolParser`, `CadenceTracker`, `PowerModel`, BLE parsing, `IntervalParser`, `ClassIntervalEngine`, `TargetBand`, `RideCoachPolicy`, `WorkoutAggregates`, `UnitSystem`, `Formatters`, `RideDayGrouping`, `WorkoutSession` — **192 tests**
- [x] **8.8** Instrumented tests for Room DAOs (foreign key ordering, `is_complete` filtering, cascade delete)
- [x] **8.8a** Instrumented test for `WorkoutService` lifecycle — start/pause/resume/stop, the workout row existing before its first metric, the batched tail being flushed, and a finished ride no longer being offered for recovery
- [x] **8.9** Manual testing on Gen 1 Peloton hardware — profile selector → dashboard → settings → Hardware telemetry → Just Ride → live board data → post-ride summary → persisted ride and 246 metric rows, 31 July 2026. Imperial units picked up from the device locale with no prompting (13.2), on the actual tablet this time
- [x] **8.12** Verified end-to-end on an emulator: profile creation → class library → intervals → simulated ride → post-ride summary → persisted metrics
- [x] **8.13** Verified on a 1920×1080 landscape tablet emulator, which is the shape of the device this actually runs on

### 8.11 Material Expressive design

- [x] **8.11.0** Inter variable font (SIL OFL)
- [x] **8.11.1–8.11.3** Colour tokens, light/dark schemes, fitness metric palette
- [x] **8.11.4–8.11.6** Elevation, shape and motion tokens — **deduplicated**; there were two conflicting elevation scales and a shape scale that shadowed itself with literal-named tokens
- [x] **8.11.7–8.11.11** Theme wired to `MaterialTheme.shapes` so stock components use it
- [x] **8.11.11a** Dynamic colour made opt-in — it previously overrode the entire palette on API 31+, making the brand theme dead code on any modern device
- [x] **8.11.13** Fade-through screen transitions
- [x] **8.11.17** Dashboard redesign
- [x] **8.11.27–8.11.29** Class library with filter chips and duration badges
- [x] **8.11.32/34** Class detail with zone-coloured interval cards
- [x] **8.11.37–8.11.38** Intent prompt with descriptions
- [x] **8.11.48/50** Post-ride summary cards and RPE selector (now a `FlowRow` — ten 48dp buttons in a `Row` overflowed every phone screen, making the higher ratings untappable)
- [x] **8.11.52** FTP breakthrough dialog
- [x] **8.11.65–8.11.67** Content descriptions, 48dp touch targets, semantic headings
- [x] **8.11.81** Shape as a semantic channel: the zone badge is a circle at Zone 1 and a twelve-point star at Zone 7, morphing between them on a change. Built on `androidx.graphics.shapes`, which is the mechanism behind Material 3 Expressive's shape language and works without moving to the material3 alpha.
- [x] **8.11.82** Off-target is amber, never red — power's own accent is coral, and a coral number turning red is not a signal anyone can read at a glance. The direction is also spelled out beside the label, so colour is never the only channel.
- [x] **8.11.83** Springy, physical motion: interval changes wash the HUD with the new zone's colour, cards overshoot and settle, the countdown re-bounces on every tick
- [ ] **8.11.12** Shared element transitions between profile selector and dashboard
- [ ] **8.11.14** Container transform for library → detail
- [ ] **8.11.15** Predictive back for Android 14+
- [ ] **8.11.16** Navigation rail or bottom bar
- [ ] **8.11.18** Large expressive FAB for Just Ride
- [ ] **8.11.21** Dashboard skeleton loading states
- [ ] **8.11.30–8.11.31** Class search bar, empty-state illustrations
- [ ] **8.11.33/35/36** Sticky class header, start-button loading state, difficulty indicator
- [ ] **8.11.39–8.11.41** Preparation checklist, sensor status, countdown
- [ ] **8.11.42–8.11.47** HUD redesign: progress arcs, blur pause overlay, expressive alerts
- [ ] **8.11.49/51** Achievement badges, share button
- [ ] **8.11.53–8.11.57** Charts: power with zone overlay, heart rate, cadence distribution, PB comparison
- [ ] **8.11.58–8.11.64** Extract the remaining shared components (`ZoneBadge`, `ProgressArc`, `SkeletonLoader`)
- [ ] **8.11.68–8.11.69** High contrast mode, font scaling
- [ ] **8.11.70–8.11.80** Micro-interactions, shimmer, pull-to-refresh, scroll edge effects

> The unticked items above are cosmetic and none of them are on the HUD. Phase
> 11 is worth more than all of them: the app runs a class now, and the question
> is whether the surface the rider actually spends forty minutes glancing at is
> good enough. Charts and shimmer are for the two minutes either side of that.

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

---

## Phase 11: The HUD-first experience — the current priority

**Premise:** this app is used almost entirely from the corner of the rider's
eye. They start a class, switch to Netflix, and look at Pelonot in glances for
the next forty minutes. The HUD is not an accessory to the ride screen; it *is*
the product, and everything in this phase is judged by whether it survives
being read in half a second from two metres away while out of breath.

### 11.1 Verify the HUD's own interactions on a device
- [x] **11.1.1** Docked strip renders over another app without covering the middle of the screen
- [x] **11.1.2** Sits below the status bar rather than under the clock
- [ ] **11.1.3** Tap-to-collapse and the slim strip it collapses to
- [ ] **11.1.4** Drag to re-dock between top and bottom, and that the choice persists
- [x] **11.1.5** **Pause, resume and stop from the HUD with the app in the
      background** — driven from the strip on the bike with Netflix in the
      foreground, 31 July 2026. Pause froze the ride at 03:00 and it was still
      03:00 twelve seconds later, so the pause genuinely leaves elapsed alone
      (3.7); resume advanced it again; stop tore down the notification and the
      overlay window and left Netflix undisturbed. The overlay never took focus
      from the video app at any point
- [x] **11.1.6** **Spoken coach audible over a playing video** — but only
      after two defects, and neither was in the coaching logic. `RideCoach`
      configured the engine straight after the `TextToSpeech` constructor,
      before the service had bound, so both the language *and the audio
      attributes* were discarded ("setLanguage failed: not bound to TTS
      engine", in logcat on the bike). And attributes alone ask the system for
      nothing: ducking requires an explicit
      `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` request, and nothing ever made one.
      `dumpsys audio` showed Netflix holding `GAIN` with `loss: none` and an
      empty ducked-players list throughout, and the rider reported the cue
      inaudible under the film. With focus requested per cue and released when
      the last utterance finishes: **observed ducking, and the coach clearly
      audible over Netflix**

### 11.1a Getting between the HUD and the app

Today the HUD and the ride screen are two places with no door between them. The
rider raises the HUD when the ride starts, switches to Netflix, and there is no
way back into the app except through the launcher — and no way back out to the
HUD except by leaving the app again. That is a gap in the product, not a
polish item: it is the single journey a rider makes most often during a class.

- [x] **11.1a.1** **Double-tap the HUD brings the full app forward.** Double
      rather than single, because single tap already collapses and expands the
      strip (11.1.3) and that is the gesture a rider fires by accident while
      reaching past the tablet. A single tap that yanked Netflix off the screen
      mid-scene would be the worst possible mis-fire on this surface.
      *Observed on the tablet AVD: double-tapped the strip with the launcher in
      front, Pelonot came forward on the ride screen with the ride still
      running and the overlay stood down.* One trap for anyone verifying this
      by `adb`: Compose ignores a second tap that lands inside
      `doubleTapMinTimeMillis` (**40 ms**), and two back-to-back
      `input tap`s are about 26 ms apart, so the gesture reads as a single tap
      and nothing happens. Put a `sleep 0.12` between them
- [x] **11.1a.2** A **"back to the HUD" control on the ride screen**, so the
      journey is symmetric and does not route through the launcher or the
      recents switcher. `moveTaskToBack` rather than `finish()` — the ride
      screen has to survive, because the rider is coming back to it. Hidden
      when the HUD is off or ungranted, since it would then only hide the app.
      *Observed: tapped it, the launcher returned and the strip came up with
      live telemetry on it*
- [x] **11.1a.3** **The full app comes forward when the ride ends.** The class
      finishing is the one moment the rider definitely wants the whole screen —
      the summary, the RPE question and any FTP proposal are all there and none
      of them fit on a strip. **A foreground service is *not* exempt from
      Android 10's background-activity-start rules** — the note here previously
      assumed it was. `SYSTEM_ALERT_WINDOW` is on the exemption list, which is
      the same grant the HUD is already drawn under, so the app that can show a
      strip can always open itself from one. *Observed: stopped the ride from
      the strip with the app in the background and the summary came up by
      itself, showing the ride's real figures*
- [x] **11.1a.4** **Discard the ride from the post-ride summary**, for the
      session that was a warm-up, a mistake, or somebody else pedalling for
      thirty seconds. Guests get this today (8.4) and riders with a profile do
      not — they have to finish, leave, open history and delete. It has to name
      what is going and be as hard to hit by accident as the delete in 12.3.2.
      *Observed: the dialog names the duration, and confirming took the ride
      and all 135 of its `workout_metrics` rows with it — checked by count
      against the database, not by the screen returning.* Local only: an
      already-uploaded ride stays in the cloud until tombstones exist (15.3.4)

### 11.1b The HUD getting out of the way

The premise of this whole phase is that the rider is watching something else.
The strip currently sits on top of that film as a solid block, in a fixed size,
pinned to the top or bottom edge. Every item here is about the HUD taking up
less of the screen and less of the attention.

- [ ] **11.1b.1** **Adjustable opacity**, from solid down to nearly invisible,
      with the film readable through it. Set once in Settings rather than
      fiddled with mid-ride
- [ ] **11.1b.2** A floor on how transparent it can go, and a check that the
      text still passes contrast against **moving** video rather than against
      one paused frame. A HUD nobody can read at a glance has failed at the one
      thing it is for (8.11.82 made the same argument about colour)
- [ ] **11.1b.3** **Resizable**, so a rider who wants three big numbers and a
      rider who wants the whole timeline can both have it. Persisted like the
      dock
- [ ] **11.1b.4** **Dock to the left and right edges too**, not only top and
      bottom. A vertical strip down one side leaves subtitles *and* faces clear
      and is probably the better default on a 16:9 tablet in landscape — which
      is the shape of the device this actually runs on (8.13)
- [ ] **11.1b.5** The layout has to genuinely re-flow for a vertical dock, not
      rotate: the timeline, the zone badge and the three live numbers each need
      a tall arrangement. Extends 11.1.4, which only ever considered top/bottom
- [ ] **11.1b.6** Every one of these choices persists, and the HUD comes back
      where and how the rider left it

### 11.2 What the strip is still missing
- [x] **11.2.1** Resistance, with a prescribed range derived by inverting `PowerModel` at the middle of the cadence target. Shown next to cadence — the two inputs together, then the two outputs. Reports *no* band rather than a clamped percentage when the target is out of the knob's reach at that cadence, because the honest instruction there is "spin faster".
- [ ] **11.2.1a** The resistance band disappears on some Zone 1 intervals for a low-FTP rider: the unloaded curve at 85 rpm already produces more watts than the whole zone allows. That is arguably *true* and worth saying out loud ("you cannot ride this easy at this cadence") rather than saying nothing. Blocked behind 2.2.4 — until the curve is calibrated it is as likely to be a modelling artefact as a real contradiction.
- [ ] **11.2.2** Time in zone: a thin stacked bar of how the ride has been spent, for the collapsed strip where the timeline does not fit
- [ ] **11.2.3** A "you are ahead of / behind your usual" line against `leaderboardFor`, which is the one comparison a rider actually acts on mid-ride
- [ ] **11.2.4** Handle a HUD raised while a call or another overlay is on top

### 11.3 Beyond the strip
- [ ] **11.3.1** **Landscape layout for the dashboard.** It is a phone-shaped column with two thirds of a 1920×1080 screen empty — the FTP card, the Just Ride button and the three action cards all stretch to full width and the whole right-hand side is black. Confirmed by screenshot on the tablet emulator, 31 July 2026. The profile selector has the same problem and is tracked separately in 20.1, because fixing it properly means redesigning it rather than re-flowing it
- [ ] **11.3.2** Post-ride charts: power with zone bands, heart rate, cadence distribution (8.11.53–8.11.57)
- [ ] **11.3.3** Time-in-zone summary on the post-ride screen
- [ ] **11.3.4** Skip or extend the current interval mid-ride, for a rider who needs to take a call
- [ ] **11.3.5** Screen-on lock during a ride, so the tablet does not sleep mid-class

### 11.4 Re-home the leaderboard
- [ ] **11.4.1** Leaderboard on the post-ride summary, where there is room for it
- [ ] **11.4.2** A single-line "vs your best" on the ride screen (not the HUD)

### 11.5 Volume control — the tablet has nowhere else to change it

**The reason this is not a nicety.** On the bike's tablet there is no status
bar to pull down, so there is **no system volume UI at all**. A rider watching
Netflix with the coach speaking over it has no way to change either level
without leaving what they are doing. The app is the only surface that can
offer it, which makes this closer to fundamental than to polish.

- [ ] **11.5.1** **Media volume**, controlling `STREAM_MUSIC` — which is what
      Netflix and everything else plays on. Needs `MODIFY_AUDIO_SETTINGS` in
      the manifest (a normal, install-time permission, no prompt). Declare it
      *before* wiring the slider: an undeclared permission fails silently and
      this project has shipped that bug twice already (8.5, 2.3)
- [ ] **11.5.2** **Coach volume, independent of the media volume.** Do this
      with `TextToSpeech.Engine.KEY_PARAM_VOLUME` in the `Bundle` passed to
      `speak()` — a per-utterance 0..1 scalar — rather than by moving a stream
      volume. A stream-level control would fight the ducking in 11.1.6 and
      could not make the coach quieter *than* the film, which is exactly what
      a rider who finds it shouty will want
- [ ] **11.5.3** Both in **Settings**, as the place they are set deliberately
- [ ] **11.5.4** Both reachable from the **HUD**, since mid-ride is when a
      rider actually discovers the film is too loud, and going to Settings
      means abandoning the ride screen and the film together
- [ ] **11.5.5** **This is the deliberate exception to 18.6 / 19.4** — "nothing
      on the strip that is not about the next sixty seconds of pedalling". It
      earns its place only because the tablet offers no alternative. Keep it
      out of the resting strip: put it behind the collapse/expand (11.1.3) or
      a single small control that opens the sliders, so the default HUD is
      still three big numbers and a countdown
- [ ] **11.5.6** Volume changes persist, and the coach level survives a
      restart. A rider who turned the coach down did not mean "until the next
      ride"
- [ ] **11.5.7** Setting a stream volume can throw `SecurityException` when a
      Do Not Disturb policy is active on API 23+. Catch it and say so rather
      than letting the slider move and nothing happen — a control that lies
      about having worked is worse than one that is absent
- [ ] **11.5.8** Volume keys: the owner reports **no physical rocker**, and the
      driver picture in `HARDWARE.md` is consistent with that — the only devices
      declaring `KEY_VOLUMEUP` are the headphone jack (`ACCDET`, inline remote
      only) and the MediaTek keypad driver, which declares the capability
      whether or not buttons are populated. **Settle it with `adb shell getevent
      -l` and a press of every physical button**; ten seconds with someone at
      the bike. Honour the keys if they arrive, but nothing may depend on them

---

## Phase 12: Ride history & the rider's own record — fundamental

**The gap:** every ride since the foreign-key fix has been writing a full
per-second time series to `workout_metrics`, and there is no screen in the app
that shows a rider a ride they finished yesterday. `WorkoutRepository` already
has `observeWorkouts(userId)`, `getRecentWorkouts` and `getMetrics`; the data
layer is done and nothing renders it.

### 12.1 History screen
- [x] **12.1.1** `HistoryScreen` + `HistoryViewModel`, as a real `NavHost` destination, reached from a History card on the dashboard
- [x] **12.1.2** Rides grouped by day, newest first, with headers. `RideDayGrouping` is pure with the clock and timezone injected, and tested across midnight, both DST transitions and a non-UTC day boundary — an off-by-one here is only visible around midnight, in a timezone the author does not live in
- [x] **12.1.3** Row shows class title (or "Just Ride"), time, duration, output, avg power, distance, and says so when a ride was rebuilt after a crash — which is what `was_recovered` (12.5.5) exists for
- [x] **12.1.4** Only complete rides, asserted in `WorkoutDaoTest`
- [x] **12.1.5** Empty state that says what to do. Guests get their own, since a guest ride belongs to nobody by design
- [x] **12.1.6** Windowed query. `observeHistory` is a projection joining only `workouts` and `class_templates` and **never touches `workout_metrics`**; `observeCompletedCount` tells the screen whether to offer "Show older rides"

### 12.2 Ride detail
- [x] **12.2.1** `RideDetailScreen`, a separate destination from `PostRide`. Not `PostRideViewModel` with a flag: that one runs the FTP analyser over the whole series on load and offers to rewrite the rider's FTP, which is right ninety seconds after a ride and bizarre on a ride from March
- [x] **12.2.2** `RideSummaryCard` extracted out of `PostRideSummaryScreen` and shared by both
- [ ] **12.2.3** Charts (phase 16) land here first
- [x] **12.2.4** Edit RPE after the fact, saving on each tap. **Observed**: rated 7 from the detail screen, `rpe_rating = 7` in the row

### 12.3 Delete
- [x] **12.3.1** Delete from the detail screen and from a button on the row. **Not** a swipe: a swipe is right for a mis-tap you can take back instantly, and this list is scrolled far more often than it is edited
- [x] **12.3.2** Confirm before deleting, naming the ride and its date, and saying that the per-second record goes with it
- [x] **12.3.3** Undo snackbar. **The delete is deferred, not reversed** — `workout_metrics` cascades, so an "undo" that re-inserted the aggregates would hand the rider a ride with its time series missing. The row is hidden, the snackbar offers to put it back, and the delete only reaches the database when the snackbar goes away or the screen closes. If the process dies mid-window the ride survives, which is the safe direction. **Observed**: undo, then 49 metric rows and `rpe_rating = 7` still in the database
- [x] **12.3.4** `PRAGMA foreign_keys` asserted directly on the real connection in `WorkoutDaoTest`, alongside a 20-sample cascade. An orphaned metric series is invisible from every screen and grows forever
- [ ] **12.3.5** **Deleting a synced ride must delete it in the cloud too**, or the next pull resurrects it. Needs a tombstone (`deleted_at`) rather than a hard delete, since the device may be offline when the rider deletes. Blocked on 14 — the confirm dialog says "this only deletes the ride on this device" in the meantime
- [ ] **12.3.6** Bulk delete / select mode — after 12.3.5, not before

### 12.4 Housekeeping the record
- [ ] **12.4.1** Re-file a household guest ride against a profile from history (the post-ride flow in 8.4 is the only chance to do it today, and the rider is usually still breathing hard)
- [ ] **12.4.2** Filter by class category and by date range
- [ ] **12.4.3** Export a ride — CSV of the metric series, and `.tcx`/`.fit` for Strava and everything else. This is an open-source app: not being able to get your own data out is the thing the subscription product does
- [ ] **12.4.4** Export/import the whole local database as a file. Until 15 exists this is the *only* backup a rider has

### 12.5 Room migrations — do this before anything in 12–19 ships
- [x] **12.5.1** Replace `fallbackToDestructiveMigration()` with explicit `Migration` objects. `AppMigrations.ALL` is the list; a downgrade still falls back destructively, since that only happens when an older APK is installed over a newer one on a development device
- [x] **12.5.2** Export the Room schema to `app/schemas/` and check it in. The stale `2.json` left over from an abandoned `theme_preference` column has been deleted; `1.json` and `2.json` are now the real history
- [x] **12.5.3** `MigrationTestHelper` instrumented test for each migration. 1→2 runs against a real SQLite file created from the exported v1 schema, with rows written beforehand, and asserts they — and the cascade onto `workout_metrics` — survive. **Observed: 18 instrumented tests pass on the tablet emulator**
- [x] **12.5.5** The first migration is one the app needed anyway rather than a placeholder: `workouts.was_recovered`, so history can distinguish a ride rebuilt from its samples after a crash from one that finished normally (12.1.3)
- [ ] **12.5.4** Only then, the schema changes phases 14–15 need (`deleted_at`, `synced_at`, `auth_user_id`). Units (13) turned out to need none — the preference is a display concern and lives in DataStore

> Deliberate consequence: a development device still holding a pre-migration
> database now fails to open rather than silently emptying itself. No shipped
> build has ever existed, so no rider is affected; uninstall and reinstall.

> This is listed last in the phase and is first in the work. Every remaining
> phase adds a column. The moment a build with a real training history is
> installed on the bike's tablet, a destructive fallback stops being a
> pre-release convenience and becomes a data-loss bug that has already happened
> by the time anyone notices.

---

## Phase 13: Units and display preferences — fundamental, small

Distance is hardcoded to kilometres (`Formatters.kilometres`), which is the
wrong default for a UK or US rider looking at a Peloton bike whose own display
is in miles.

- [x] **13.1** `UnitSystem` (`METRIC` / `IMPERIAL`) in `SettingsRepository`, defaulting from the device locale. Absent-means-never-chosen, so the locale is consulted on every read rather than metric being pinned on first launch
- [x] **13.2** Settings toggle, next to the existing FTP and weight fields. **Observed on the emulator**: an `en-US` device opens on Imperial with no prompting
- [x] **13.3** `Formatters` takes the unit system: distance km/mi, speed km/h/mph, body weight kg/lb. No no-argument overload survives — a caller that forgot the preference is a compile error rather than a silent kilometre
- [x] **13.4** **Store SI, convert at the edge.** **Observed**: 160 lb typed into profile creation is `72.5747792016057` in `profiles.weight_kg`, read back as `160.0 lb`, and switching to Metric mid-session redraws it as `72.6 kg` without touching the row
- [x] **13.5** Every surface reads the same setting, delivered through `LocalUnitSystem` from `PelonotTheme` — which is what lets the HUD read it, since the overlay is composed from the service and has no ViewModel to thread it through. Ride screen, post-ride summary, HUD strip, settings and profile creation all consume it
- [x] **13.6** Watts, RPM, BPM and kJ are unit-agnostic and stay as they are. No calories, and the Settings copy says why
- [x] **13.7** JVM tests for the conversions, the settings-field round trip, and locale-derived defaults — **plus** `FormattersTest`, which pins every number under `fr-FR` and `hi-IN-u-nu-deva`. A missing `Locale.US` is the same defect class that put epoch millis into a `TIMESTAMPTZ` (14.0)

---

## Phase 14: Cloud sync that actually reaches the cloud — fundamental

### 14.0 Are we connected? — findings, 31 July 2026

Short answer at the time of asking: **no, and not for the reason the code
suggested.** Established against the live project (`podsmtujqarlqhvorpdh`,
eu-west-1) rather than by reading, which changed the answer twice.

**The first failure was missing `GRANT`s, not the payload.** `migration.sql`
creates three tables, enables RLS and writes six policies, and never grants a
single table privilege to `anon`. RLS *narrows* access a role already has; it
cannot confer any. So every request — read and write — died with `42501
permission denied for table` before a policy was ever evaluated, and PostgREST
returned 401. The `anon` role held only `REFERENCES`, `TRIGGER` and `TRUNCATE`:
no `SELECT`, no `INSERT`.

Proof the tables themselves were fine: a genuinely missing table returns
`PGRST205`, an absent key returns a different 401, and `class_templates` held
all 72 seeded rows. `profiles` and `workouts` held **0 rows each** — nothing had
ever synced, confirmed by count rather than inferred.

| Path | What was wrong | State |
|------|---------------|-------|
| everything | `anon` had no DML grants at all. First and total blocker. | ✅ fixed in `002` |
| `syncWorkout` | `WorkoutDto` sends `recorded_at`; the table's column was `timestamp`. | ✅ column renamed in `002` |
| `syncWorkout` | `recordedAtEpochMs: Long` serialises as `1753900000000` into a `TIMESTAMPTZ` → `22008 date/time field value out of range`. **Found only by attempting a real insert** — invisible to every code reading. | ✅ DTO now emits ISO-8601 UTC |
| `syncProfile` | `profiles` had policies for `SELECT` and `UPDATE`, none for `INSERT`. | ✅ policy added in `002` |
| `syncProfile` | Upsert with no `onConflict` targets the primary key `id`, a UUID the DTO never sends — so every call inserts instead of updating. | ✅ `onConflict = "local_user_id"` |
| `syncWorkout` | The DTO carries **no `user_id`**, so a synced ride is anonymous and unattributable. | ❌ 14.3 |
| `fetchClassTemplates` | Cloud `intervals_json` is `JSONB` holding an array; `ClassTemplateDto` reads it as `String`. Decode throws. | ❌ 14.5 |
| all policies | Every one is `USING (true)`. **Not currently an exposure** — the grants in `002` are narrow and `workouts` has no `SELECT` grant at all — but they activate the moment anyone widens a grant. | ❌ 15.5 |

> An earlier draft of this section claimed any client could read every rider's
> data. That was wrong: with no grants, nothing was readable by anyone. The
> `USING (true)` policies are a loaded gun rather than a fired one, and 15.5 is
> still where they get fixed.

### 14.1 Verified working

- [x] **14.1.1** `002_grants_and_sync_fix.sql` applied to the live project. Narrow grants by design: `class_templates` SELECT, `profiles` SELECT/INSERT/UPDATE, `workouts` **INSERT only** — a leaked publishable key cannot enumerate ride history
- [x] **14.1.2** A `workouts` row inserted with the anon key using the app's exact `WorkoutDto` shape — **HTTP 201**, `metrics_payload` intact as JSONB. The first row this project has ever accepted. Test row deleted afterwards
- [x] **14.1.3** Profile upsert round trip: `201` then `200` on repeat, one row, FTP updated in place rather than duplicated. Test row deleted afterwards
- [x] **14.1.4** `WorkoutDto` emits ISO-8601 UTC, with JVM tests covering the timezone drift and locale (`th-TH-u-ca-buddhist`) cases that would silently corrupt it
- [x] **14.1.5** A test asserting the serialised keys are a subset of the real column list — the failure mode that started all of this

- [ ] **14.1.6** **The round trip from the app itself.** Everything above was driven by `curl` with a hand-built payload; it proves the schema, the grants and the wire format, and it proves *nothing* about `WorkoutSyncWorker` enqueueing, running and posting. Install, ride, and see the row appear. **Per the house rule this phase is not complete until this box is ticked** — the whole point of the Corrections table is that "the pieces are right" has repeatedly not meant "it works"

### 14.2 The rest of the path to full connectivity

- [ ] **14.2.1** Carry the rider through: local `user_id` (Int) → cloud `profiles.id` (UUID). Requires the profile to sync first and its cloud id to be stored locally. Until this lands, every uploaded ride is anonymous
- [ ] **14.2.2** Settle `intervals_json` as one type on both sides — `TEXT` holding the JSON is the honest choice, since the app treats it as an opaque string it hands to `IntervalParser`
- [ ] **14.2.3** **Surface sync state in Settings**: configured or not, last successful sync, count pending, and the actual error text of the last failure. `SyncOutcome.Failed` dies in `Log.w` today, which is precisely why this went unnoticed for the project's whole history
- [ ] **14.2.4** `synced_at` on `workouts` locally, so a ride uploads once and a backlog is knowable
- [ ] **14.2.5** Retry the backlog when connectivity returns, not only at ride end
- [ ] **14.2.6** Upload the rides already sitting in the local database — there is a real history on the tablet that predates sync working
- [ ] **14.2.7** Decide the metrics payload ceiling. A 45-minute ride is ~2,700 samples in one JSONB column; a 90-minute ride is double that. Find the point where the insert starts failing before a rider does
- [ ] **14.2.8** `supabase/003_*.sql` for whatever 14.2.1 and 14.2.2 need, keeping migrations incremental and non-destructive — `002` deliberately did not drop or recreate anything, and the 72 class templates are still the originals

### 14.3 Keeping it working

- [ ] **14.3.1** A round-trip check that can be re-run against a throwaway project, scripted and documented in `supabase/README.md`. Three of the five defects above were invisible to `assembleDebug` and to all 158 JVM tests
- [ ] **14.3.2** Keep `supabase/*.sql` and the DTOs verifiably in step — the column-name test in `WorkoutDtoTest` is a start, but it hardcodes the column list and nothing forces it to match the live schema
- [ ] **14.3.3** Fold the schema into CI (19.1.4) once there is a CI to fold it into

### 14.10 Configuring the endpoint — open-source hygiene

The endpoint must be configurable **in code, not in the app's UI**: a rider
should never be asked to type a URL, and a self-hoster should not need to fork
a screen.

- [ ] **14.10.1** A checked-in `cloud.properties` (or `CloudConfig.kt`) holding the default endpoint and publishable key, overridable by `local.properties` and then by env vars. Today the only source is `local.properties`, which is **gitignored** — so a fresh clone of an open-source project has no cloud at all and no in-repo record of what the community endpoint even is
- [ ] **14.10.2** Precedence documented in the README: env → `local.properties` → checked-in default → offline
- [ ] **14.10.3** Keep `SupabaseModule.client == null` and `SyncOutcome.Disabled` as the behaviour when nothing is configured. **Offline-first is not negotiable**; the cloud stays a mirror
- [ ] **14.10.4** Only publish a default key **after 15.5**. A publishable key is safe to check in exactly when RLS is correct, and right now it is `USING (true)` — publishing it today would publish everyone's data with it
- [ ] **14.10.5** `supabase/README.md`: how to stand up your own project, run the migrations in order, and point a build at it

### 14.11 Credential hygiene

`local.properties` currently holds three values, and one of them is far more
dangerous than its name suggests.

- [x] **14.11.1** `local.properties`' third Supabase value (was `supabase.serviceKey`) is **not** a service-role key — it is an `sbp_` **personal access token**, which is account-wide and can create, modify and delete *every project on the account*, not just this one. It is correctly gitignored and, verified, is read by nothing in `app/build.gradle.kts` and referenced nowhere in the source, so it cannot reach `BuildConfig` or an APK
- [x] **14.11.2** Renamed to `supabase.accessToken` so nobody wires it into `BuildConfig` on the assumption that it belongs there. A service-role key in a client app would be bad; **this one is worse**
- [ ] **14.11.3** Never add a `secret()` call for it. The two that exist (`supabase.url`, `supabase.anonKey`) are the only two that may ever become `buildConfigField`s
- [ ] **14.11.4** Rotate it when the schema work is done — it has been used from a shell and lives in a plaintext file
- [x] **14.11.5** Said in `supabase/README.md`, since a contributor following the setup will otherwise put whatever key they find into the same file

---

## Phase 15: Accounts, login and multi-device sync — fundamental once 14 works

**The rule this phase must not break:** the app works with no account, no
network, and no cloud, exactly as it does today. Login is something a rider
opts into to get their history onto a second device and to use anything
social. A signed-out app is not a degraded app.

### 15.1 Auth
- [ ] **15.1.1** Add the Supabase `auth-kt` module — only `Postgrest` is installed today
- [ ] **15.1.2** Email magic link and/or OAuth. Prefer flows with no password field: the app should not be in the business of handling credentials
- [ ] **15.1.3** Session persisted and refreshed; expiry never interrupts a ride or blocks a screen
- [ ] **15.1.4** Sign in from Settings, never as a gate on launch or on starting a class

### 15.2 Identity model
- [ ] **15.2.1** Local Room profiles stay the source of truth. An account **attaches to** one local profile rather than replacing the profile system — the bike is a shared household device and that is the whole reason profiles exist
- [ ] **15.2.2** `profiles.auth_user_id UUID REFERENCES auth.users` in the cloud schema; `cloud_id` on the local `UserEntity`
- [ ] **15.2.3** Household guests never sync. A guest ride has no owner by definition
- [ ] **15.2.4** Two local profiles on one tablet may be two different accounts — nothing may assume a single signed-in user per device

### 15.3 Sync in both directions
- [ ] **15.3.1** On first sign-in, backfill the whole local history, batched and in the background
- [ ] **15.3.2** Pull on a new device: restore rides and profile
- [ ] **15.3.3** Idempotent by the local workout UUID, so a retry or a re-install cannot double a ride
- [ ] **15.3.4** Conflict rule, written down and one line long: **local wins for a ride in progress, last-write-wins for RPE and profile fields, tombstones win over everything** (12.3.5)
- [ ] **15.3.5** Metric series are large — a 45-minute ride is ~2,700 samples. Decide deliberately whether the full series goes up or only the aggregates plus a downsampled trace, and record the reasoning
- [ ] **15.3.6** Sync never runs on the ride's critical path and never blocks the HUD

### 15.4 Leaving
- [ ] **15.4.1** Sign out keeps every local ride. A rider signing out has not asked to lose their training history
- [ ] **15.4.2** "Delete my cloud data" as a separate, explicit action, with the local record untouched
- [ ] **15.4.3** Account deletion end to end, since GDPR applies to a hobby project too

### 15.5 RLS, properly
- [ ] **15.5.1** Rewrite every policy against `auth.uid()` — currently all six are `USING (true)`
- [ ] **15.5.2** A rider can read and write only their own profile and their own workouts
- [ ] **15.5.3** `class_templates` stays world-readable; it is public data
- [ ] **15.5.4** Verify each policy from a second account, not by reading the SQL. This is the one place where being wrong is a breach rather than a bug

---

## Phase 16: Data visualisation

The post-ride charts (16.1) are close to fundamental — they are what makes a
recorded time series worth recording. The trend work in 16.3 is genuinely
nice-to-have and only becomes interesting after a few dozen rides exist.

### 16.1 The ride itself
- [ ] **16.1.1** Power over time with zone bands behind it (was 8.11.53)
- [ ] **16.1.2** Heart rate over time, drawn **only where samples exist** — null is unknown, and a line dropping to the axis says the rider's heart stopped
- [ ] **16.1.3** Cadence distribution
- [ ] **16.1.4** Time in zone as a stacked bar, shared with the HUD's collapsed strip (11.2.2)
- [ ] **16.1.5** The class's prescribed intervals drawn under the actual trace — "what you were asked for" against "what you did" is the single most useful post-ride view
- [ ] **16.1.6** Axis label reads from `SensorReading.powerIsMeasured` rather than saying **estimated** unconditionally. On the bike it *is* a meter (2.1a); on a simulated ride it is a model (2.2.4). A ride can in principle contain both, so decide what a mixed series is labelled

### 16.2 Building them
- [ ] **16.2.1** Compose `Canvas`, no charting dependency — these are four fixed chart types, and a library is a large surface for a small need
- [ ] **16.2.2** Downsample before drawing: 2,700 points into ~300 buckets keeps peaks (min/max per bucket, not mean — averaging erases exactly the sprint the rider wants to see)
- [ ] **16.2.3** Off the main thread, cached on the ride, computed once
- [ ] **16.2.4** Accessible: every chart has a text summary, since a chart is unreadable to a screen reader and a fair amount of this data is a sentence

### 16.3 Trends — nice to have
- [ ] **16.3.1** FTP over time, marked with the rides that triggered each change
- [ ] **16.3.2** Weekly volume and output
- [ ] **16.3.3** Personal bests by duration
- [ ] **16.3.4** This ride against your previous best at the same class (`leaderboardFor` already computes it — see 11.4)
- [ ] **16.3.5** A calendar heatmap of ride days. Cheap, and the streak is the thing that gets people on the bike

---

## Phase 17: Companion web application — nice to have

Only worth starting once 14 and 15 work; it is a view onto the same Supabase
project and has nothing to show before rides are reaching it.

- [ ] **17.1** Stack and repo layout. A separate top-level `web/` directory or a separate repo — **the Android build must never depend on it**
- [ ] **17.2** Auth shared with the app via the same Supabase project; a rider signs in once conceptually
- [ ] **17.3** Ride history and ride detail, reusing the chart definitions from 16 conceptually if not literally
- [ ] **17.4** Profile customisation: display name, avatar, bio, FTP, units
- [ ] **17.5** Friends — request, accept, block. New `friendships` table with its own RLS; this is the first schema where a rider can see another rider's data and it deserves more care than the rest
- [ ] **17.6** A light activity feed: friends' recent rides, kudos, a comment. Deliberately not a full social network
- [ ] **17.7** **Private by default.** Nothing is visible to anyone until the rider opts in, with per-ride visibility (private / friends / public). Defaulting to visible would publish training history people did not know they were publishing
- [ ] **17.8** Self-hosters get the same deal as 14.10 — the endpoint is configured at build time, not typed in
- [ ] **17.9** Decide what "public" means before shipping it: a public profile URL is an outward-facing surface with moderation and abuse implications a hobby project has to actually think about

---

## Phase 18: Social in the Android app — nice to have

Everything here is behind a signed-in account and must vanish cleanly when
signed out — not grey out, not prompt, not appear at all.

- [ ] **18.1** Friends list and requests, mirroring 17.5
- [ ] **18.2** A feed of friends' recent rides on the dashboard, below the rider's own stats and never above them
- [ ] **18.3** Kudos, and nothing that requires typing during or just after a ride
- [ ] **18.4** Compare a class you both rode — same class, both traces, one chart. This is the version of a leaderboard that is actually motivating
- [ ] **18.5** Friend leaderboard on the post-ride summary, alongside the rider's own history (11.4.1)
- [ ] **18.6** **The HUD stays social-free.** Nothing on the strip during a ride. It has half a second of attention and it belongs to the interval
- [ ] **18.7** A comparison across riders is honest when both sides are measured watts off their own boards (2.1a), and misleading when either side is modelled. Carry the caveat on the modelled ones specifically rather than on all of them — a blanket disclaimer nobody reads is the same as none
- [ ] **18.8** Mute, block and report exist from the first version that has a feed, not the version after someone needs them

---

## Phase 19: Ideas worth having, ranked

Sorted by value per unit of work. The first group is arguably fundamental and
has simply never been written down.

### 19.1 High value, small
- [ ] **19.1.1** **Screen-on lock during a ride** (also 11.3.5) — the tablet sleeping mid-class is a bug the rider experiences as the app being broken
- [ ] **19.1.2** **Auto-pause** when cadence has been zero for ~20 s, and auto-resume on the first tick. Every ride has a bottle stop, and it currently drags the averages down
- [ ] **19.1.3** **Local backup/restore of the database to a file** — the only safety net that exists before 15, and it survives the destructive-migration problem too
- [ ] **19.1.4** **CI**: GitHub Actions running `assembleDebug` and `testDebugUnitTest` on every PR. An open-source project taking contributions without this is asking maintainers to be the build server
- [ ] **19.1.5** **README and CONTRIBUTING** covering the build, the fact that simulated telemetry makes the whole app usable with no bike, and — corrected — that **no jailbreak is needed**. The README still advertises a root prerequisite the app does not have and does not want (2.1a). Worth saying plainly that it installs on a stock bike, since that is the difference between a project people can try and one they assume they cannot

### 19.2 High value, medium
- [ ] **19.2.1** **Custom class builder** — build your own intervals in the app. The class library is the subscription's core product and the interval model is already a plain list; this is the feature that makes the app stop needing Peloton at all
- [ ] **19.2.2** **Community class library** — share and import classes. `class_templates` is already a cloud table and already world-readable
- [ ] **19.2.3** **Guided FTP test** — a proper 20-minute protocol with pacing cues, rather than inferring FTP from whatever the rider happened to ride. `PostWorkoutAnalyzer` already does the maths
- [ ] **19.2.4** **Strava upload**, following the `.tcx` export in 12.4.3
- [ ] **19.2.5** **Training load and freshness** over weeks. Flag it hard: built on estimated watts, this is a *relative* trend for one rider and nothing more

### 19.3 Worth doing eventually
- [ ] **19.3.1** Multi-week training programmes
- [ ] **19.3.2** Achievements and streaks (pairs with 16.3.5)
- [ ] **19.3.3** Heart-rate zones and HR-based targets, for riders who trust their strap more than the power model — which today they should
- [ ] **19.3.4** Localisation, once the string catalogue is stable
- [ ] **19.3.5** Wear OS or a phone companion as a second HR source
- [ ] **19.3.6** Opt-in, off-by-default crash reporting. For this audience the default matters more than the feature

### 19.4 Explicitly not doing
- Calorie estimates (13.6) — a nutrition claim the power model cannot support
- Anything that requires a network to start a ride
- Anything on the HUD that is not about the next sixty seconds of pedalling

---

## Phase 20: Who's riding — the profile selector and avatars

The first screen anyone sees, and the one that has had the least thought. It is
also the screen that makes the shared-household story work: a bike in a living
room has three or four riders and picking the right one has to take one glance
and one tap, from two metres away, by someone who has already got their shoes
on.

The obvious reference is a TV streaming app's profile picker, and it is the
right one — same device shape, same distance, same job.

### 20.1 The profile selector

- [ ] **20.1.1** **Centre the profiles and make them big.** Today they are
      small cards in a grid pinned to the top-left of a 1920×1080 screen, with
      the rest of it empty. Confirmed by screenshot on the tablet emulator, 31
      July 2026
- [ ] **20.1.2** Landscape-first, centred both ways, sized off the screen rather
      than a fixed dp — this app runs on a tablet bolted to a bike, not a phone
- [ ] **20.1.3** Guest keeps its distinct treatment (6.1) but stops competing
      with the real riders for the eye. It is the exception, not a peer
- [ ] **20.1.4** "Create a new profile" belongs alongside the riders as one more
      tile, not as a full-width bar at the bottom of an otherwise empty screen
- [ ] **20.1.5** Edit and delete a profile from here. Deleting one has to say
      what happens to their rides — `workouts.user_id` is `ON DELETE SET NULL`,
      so the rides survive as unattributed rather than being destroyed, and the
      dialog should say so rather than letting the rider guess

### 20.2 Avatars

- [ ] **20.2.1** A checked-in set of avatars to choose from. Licence first:
      whatever is used has to be genuinely open (SIL OFL, CC0 or MIT), credited
      in the repo, and vendored rather than fetched at runtime — the app starts
      a ride with no network and that is not negotiable (19.4). Generated
      identicon-style avatars derived from the profile name are the other
      candidate and have no licence question at all
- [ ] **20.2.2** `profiles.avatar` in Room, behind a real migration (12.5).
      Store a **reference** — a pack id or a relative file path — never image
      bytes in the row: a database that carries photos is a database that
      cannot be exported, synced or backed up cheaply
- [ ] **20.2.3** Pick from the built-in set at profile creation, with a sensible
      default so nobody is forced through a choice to start riding
- [ ] **20.2.4** **Set an avatar from the camera or the gallery on Android.**
      `PhotoPicker` on API 33+ and `ACTION_OPEN_DOCUMENT` below it, so the
      common path needs no storage permission at all. Downscale and re-encode
      on import — a 12 MP phone photo has no business being loaded to draw a
      64dp circle — and write it into app-private storage
- [ ] **20.2.5** Strip EXIF on import, and honour the orientation tag before
      discarding it. A gallery photo carries GPS coordinates, and this one will
      end up synced (15) and possibly visible to friends (17.5)
- [ ] **20.2.6** Avatars appear wherever a rider is named: the selector, the
      dashboard greeting, history, and any leaderboard. Not on the HUD (18.6)
- [ ] **20.2.7** Avatar changes sync with the profile, once 14 and 15 work. A
      custom image is a blob and needs Supabase Storage rather than a column;
      decide deliberately whether it goes up at all before building it
- [ ] **20.2.8** Change your avatar from the companion web app — **much later**,
      and strictly after 17 exists. Listed here so it is not re-invented as a
      separate feature when it is the same field

---

## Phase 10: Hardware Validation — partly done, 31 July 2026

Done on a real Gen 1 (`PLTN-RB1VQ`) over wireless adb, with a rider pedalling.
Note that the bike is **stock, not jailbroken**, which turned out to be the
finding rather than an obstacle — see 2.1a.

- [x] **10.1** Sensor board device path confirmed: `/dev/ttyO0`, `system:system`
      `0660`. **Not readable by this app, and not made readable** — there is no
      root on a stock bike. `/dev/ttyS1` does not exist and `/dev/ttyS2` is the
      Bluetooth UART
- [x] **10.2** Moot as written, and answered: the raw byte protocol never
      reaches us. Peloton's service owns the port, decodes the packets
      (`F5,41,36,F6` for RPM, `F5,44,39,F6` watts, `F5,49,3E,F6` resistance,
      19200 baud) and hands over a float. `SerialProtocolParser` is unexercised
      on this hardware and stays only for the rooted-tablet path
- [x] **10.3** Superseded for real rides: the board reports watts directly, so
      there is nothing to calibrate on the bike itself. The remaining
      calibration question is about simulated rides — see 2.2.5
- [x] **10.4** **HUD renders over a video app** — verified over Netflix on the
      bike, 31 July 2026. Full-width strip docked to the top edge, middle of
      the screen clear, every figure live (cadence 84, resistance 32, power
      71 W, heart rate 98, interval countdown and next-interval preview). The
      overlay window is present as an `appop=SYSTEM_ALERT_WINDOW` window and
      never takes focus from the video app. Overlay permission turned out to be
      granted already
- [x] **10.5** **BLE strap connects and streams** — Wahoo TICKR FIT, found and
      connected on the first scan, a heart rate on all 314 rows of a ride. See
      2.3.5 for the two manifest and UI defects that had to be fixed first
- [ ] **10.6** Full-length ride: battery, thermals, memory, no dropped samples.
      The longest run so far is 8 minutes

> **Screenshots do not work over a playing film.** Netflix's player sets
> `FLAG_SECURE`, so `adb exec-out screencap` returns an empty image and the HUD
> cannot be captured over DRM video — it captured fine over Netflix's own
> non-secure PIN dialog. Anything about readability over moving video (11.1b.2)
> has to be judged by the rider's eyes, not from a screenshot. Likewise a
> spoken cue lasts a second or two, so polling `dumpsys audio` every five
> seconds slides straight past the duck; the rider hearing it is the
> measurement.

---

## Quick Reference: Coggan 7-Zone Power Model

Bounds are **contiguous and half-open** (`lower ≤ pct < upper`). The published table quotes whole percentages, which leaves unassigned gaps between zones; modelling those literally means a rider at 55.5% of FTP matches no zone at all.

| Zone | Name | % of FTP | Training purpose |
|------|------|----------|------------------|
| Z1 | Active Recovery | < 56% | Warmup, cooldown |
| Z2 | Endurance | 56–76% | Aerobic base |
| Z3 | Tempo | 76–91% | Aerobic efficiency |
| Z4 | Lactate Threshold | 91–106% | Sustainable hard effort |
| Z5 | VO2 Max | 106–121% | Max oxygen uptake |
| Z6 | Anaerobic Capacity | 121–151% | Short power bursts |
| Z7 | Neuromuscular Power | ≥ 151% | Explosive sprints |

## Quick Reference: Ride Intent

`P_target = FTP × zone% × k`

| Intent | `k` | Effect |
|--------|-----|--------|
| Reach New Milestones | 1.05 | Targets 5% higher |
| Just Stay Fit | 0.95 | Targets 5% lower |
