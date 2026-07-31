# Pelonot — working notes

Subscription-free Android client for Peloton bikes (Gen 1/Gen 2). Runs on a
**stock, un-jailbroken bike** — telemetry comes from Peloton's own sensor
service, not from root.
Kotlin, Jetpack Compose, Room, minSdk 24 / targetSdk 34.

**Read `ARCHITECTURE.md` for how data flows through the app, and `PLAN.md` for
what is done and what is next.** PLAN.md opens with a *Where the work stands*
section naming the current priority — read that before picking work.
**`HARDWARE.md` has the bike tablet's measured display, system and input facts**
— read it before any UI work, so it is checked at the size it will actually run.

---

## Commands

```bash
./gradlew assembleDebug            # must always pass
./gradlew testDebugUnitTest        # 260 JVM tests, must stay green
./gradlew installDebug             # needs a booted emulator or device
./gradlew connectedDebugAndroidTest
```

`adb` lives at `~/Library/Android/sdk/platform-tools/adb`.

**Do not check UI work on `Medium_Phone_API_36.1`.** The bike is a landscape
**1920 × 1080 at 240 dpi — 1280 × 720 dp** — with a 48 dp bottom navigation bar
and no top status bar. A phone AVD hides every layout problem this app has, and
an AVD at the right resolution but the wrong density hides half of them.
`HARDWARE.md` has the measured figures and an AVD recipe that matches.

---

## Conventions

- **Pure logic stays free of Android imports.** `PowerModel`, `CadenceTracker`,
  `SerialProtocolParser`, `WorkoutMetricsCalculator`, `PostWorkoutAnalyzer` and
  everything in `domain/` are JVM-testable. Do not add `android.util.Log` to
  them.
- **No database access from composables.** Repository → ViewModel `StateFlow` →
  `collectAsStateWithLifecycle`.
- **Dependencies via `ServiceLocator`**, not new singletons.
- **Dependency versions in `gradle/libs.versions.toml`**, never inline.
- **Secrets in `local.properties` → `BuildConfig`**, never in source.
- Comments explain *why*, not *what*. Match the density of surrounding code.

---

## Things that will bite you

- **`workout_metrics` has a foreign key onto `workouts`.** The workout row must
  be inserted (with `is_complete = 0`) *before* any metric is written. This
  ordering is why metric recording was silently broken for the whole project
  history.
- **`intervals_json` is snake_case with start/end timestamps**, not camelCase
  durations. `Interval` uses `@SerialName` to match the assets exactly. A
  mismatch throws and is easy to swallow into an empty list.
- **`heartRateBpm` is nullable and null means *unknown*.** Never default it to
  0 — that writes a fake sample into the rider's record and drags averages down.
- **`PelonotTheme` may be composed from a Service context** (the HUD overlay),
  so anything reaching for an Activity must use a safe cast.
- **`PowerModel`'s coefficients are not merely unvalidated, they are measurably
  wrong.** Against 310 steady-state samples off the real board they score
  **RMSE 137 W, median absolute error 66%, R² 0.21** (`calibration/`). Never
  present a modelled watt as measured. `PowerModel` now delegates to a
  `PowerCurve` — the shipped one, or one auto-calibrated to this bike from its
  own measured rides (PLAN.md 2.2a, `domain/calibration/`). Note the trap that
  builds into: **"beats the shipped curve" is a bar a fit to pure noise
  clears**, precisely because the shipped curve is so bad. Any new acceptance
  test on this needs an absolute accuracy floor as well as a relative one.
- **Whether to calibrate at all is settled — yes — and the argument is written
  down at the head of PLAN.md 2.2a.** Do not re-open it; do not capture another
  manual sweep (2.2.5 is closed, superseded). The reason it is safe rests
  entirely on scope: **`PowerModel` has exactly two consumers** —
  `SimulatedSensorSource` and `RideSnapshot.resistanceForWatts` (the prescribed
  resistance band). A fiction and a suggestion. **If you are about to add a
  third, stop**: anything that derives a *recorded* number from the curve
  breaks the reason calibration is allowed to exist. PLAN.md 2.2a.8 makes this
  a test.
- **Nothing records the FTP a ride was ridden at.** `workouts` has no FTP
  column, so the ride detail chart reads the rider's *current* `ftp_watts` and
  draws every past ride's zone bands and FTP rule from it. Auto-FTP (Phase 7)
  moves that number by itself, so accepting one breakthrough silently redraws
  the whole history. Same family as the `avg_*` trap below — derived on read
  from a source that has since moved. PLAN.md 7.8, and 7.9 for the change
  history that also does not exist.
- **Bike telemetry does not come from a serial port.** The bike's tablet is
  stock, not jailbroken, and no app can open the sensor board's UART
  (`/dev/ttyO0`, `system:system`). `/dev/ttyS1` does not exist and `/dev/ttyS2`
  is Bluetooth. Real telemetry comes from `PelotonSensorServiceSource`, which
  binds Peloton's own `SensorService` — exported with no `android:permission`,
  so the bind just works. `SerialSensorSource` is the fallback for a rooted
  tablet and is exercised by nothing today. See PLAN.md 2.1a.
- **On real hardware the watts are measured, not modelled.** The board reports
  power directly, so `PowerModel` does not run during a bike ride and
  `SensorReading.powerIsMeasured` is true. The uncalibrated-coefficients
  caveat below applies to simulated rides and to the 11.2.1 resistance band.
- **Sensor sources must not reconnect themselves.** `SensorRepository` owns the
  single retry policy. Adding another creates competing schedules.
- **`sensorReading` is a `StateFlow`, so it holds its last value when the board
  dies.** A frozen reading looks exactly like a rider holding a steady 88 rpm.
  Anything that *records* or *averages* a reading must check
  `isStaleAt(now, SensorReading.MAX_AGE_MS)` first; a stale reading is an
  absence, and the honest answer is a gap in the series (PLAN.md 2.4.4).
- **Nothing may call `SensorRepository.setMode` except `PelonotApp`**, which
  collects the stored preference for the life of the process. Calling it
  directly races that collector, and a second caller is how the rider's choice
  of *Hardware* came to survive only until the next launch (2.4.6). Write the
  preference; the pipeline follows.
- **A ride in progress is `is_complete = 0`, exactly like a crashed one.**
  Anything asking "is there a ride to recover?" must exclude
  `RideInProgress.workoutId`, or it offers to discard the class the rider is
  currently pedalling (8.3b).
- **`SensorMode.Hardware` deliberately does not fall back to simulation.**
  Substituting fabricated telemetry mid-ride would corrupt a permanent record.
- **The database uses explicit migrations** (`AppMigrations.ALL`) — the
  destructive fallback is gone except on *downgrade*, which only happens when
  an older APK is installed over a newer one on a development device. Every
  schema change needs a `Migration`, an exported schema in `app/schemas/`, and
  a `MigrationTestHelper` test. See PLAN.md 12.5.
- **A ride's `avg_*` columns are computed live, not derived on read.** Check a
  new one against `AVG()` over its own `workout_metrics` rows before trusting
  it; `avg_hr` was wrong for the project's whole history while `avg_power` and
  `avg_cadence` beside it were exact.
- **Audio attributes request nothing.** Setting `AudioAttributes` on a
  `TextToSpeech` describes the sound; ducking needs an explicit
  `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` request. And configuring a
  `TextToSpeech` before its engine has bound silently discards the call — do it
  in the init callback. Both of these made the coach inaudible over video.

---

## House rule on PLAN.md

Only tick a box when the behaviour has been **observed working**, not when the
code was written. A large batch of items was previously ticked while the
feature was non-functional; PLAN.md's *Corrections* table lists them and exists
to stop it recurring.

---

## Verifying UI work

`assembleDebug` passing proves very little here — most of the historic defects
compiled fine. Install on the emulator and drive the real flow, and query the
database directly when data integrity is the question:

```bash
adb shell "run-as com.pelonot cat /data/data/com.pelonot/databases/pelonot_database" > db.sqlite
adb shell "run-as com.pelonot cat /data/data/com.pelonot/databases/pelonot_database-wal" > db.sqlite-wal
sqlite3 db.sqlite "SELECT COUNT(*) FROM workout_metrics;"
```

Settings → Telemetry source → **Simulated** makes the whole ride flow work
without a bike.

### On the real bike

The bike's tablet connects over wireless adb and identifies as `PLTN-RB1VQ`
(Android 11). `installDebug` targets it like any device. The real columns are
`cadence` / `resistance` / `power` / `heart_rate` — not the `*_watts` names it
is easy to guess:

```bash
sqlite3 db.sqlite "SELECT timestamp_sec, cadence, resistance, power, heart_rate FROM workout_metrics ORDER BY id DESC LIMIT 10;"
```

Settings → Telemetry source → **Hardware** forces the sensor board with no
simulated fallback, which is the setting to verify under. The ride screen's
big metric cards are drawn without semantics, so `uiautomator dump` will not
show cadence, resistance or power — use `screencap` and read the numbers.

Verifying telemetry needs someone **pedalling**, and that is a perishable
resource: work out what you want to capture before asking, and say clearly
when they can stop. Resistance reads from the knob without pedalling, so the
service bind can be confirmed on your own.

The tablet is not stock-stock: it has Nova Launcher and Netflix side-loaded,
and Peloton's own member app is `com.onepeloton.weasel`. Drive the whole UI
over `adb shell input tap` and read it back with `screencap` — but note the
two blind spots below. **`HARDWARE.md` has the full measured picture**: display
geometry, system bars, input devices and the packages that matter.

**Screenshots come back empty over DRM video.** Netflix's player sets
`FLAG_SECURE`, so `adb exec-out screencap` yields a black image and the HUD
cannot be captured over a playing film. It captures fine over Netflix's own
non-secure dialogs. Anything about how the HUD *looks* over moving video has
to come from the rider.

**Do not try to confirm audio ducking by polling `dumpsys audio`.** A cue
lasts a second or two and five-second polling slides straight past it; the
focus stack looked completely untouched while the rider was hearing the duck
happen. Ask them.

**The `avg_*` columns on `workouts` are worth checking against the samples
they summarise**, because one of them was wrong for the project's whole
history and looked fine everywhere:

```bash
sqlite3 db.sqlite "SELECT w.avg_hr, (SELECT AVG(heart_rate) FROM workout_metrics m WHERE m.workout_id=w.id) FROM workouts w ORDER BY w.rowid DESC LIMIT 1;"
```

### Permissions on this tablet

A runtime permission the manifest does not declare is **denied instantly, with
no dialog and nothing in logcat**. That has now caused two defects here
(`VIBRATE`, then `ACCESS_FINE_LOCATION` — which below API 31 is the BLE scan
permission, and this tablet is Android 11). Before believing any permission
path, check the manifest declares what the code asks for.

Overlay permission is already granted, and `SYSTEM_ALERT_WINDOW` can be
checked with `adb shell appops get com.pelonot SYSTEM_ALERT_WINDOW`. Location
is granted; it is the rider's to give, so raise the dialog and let them tap it
rather than granting it yourself.

### Calibrating `PowerModel`

`calibration/` holds measured sweeps and the method. A Hardware-mode ride *is*
the dataset — the board reports watts beside the cadence and resistance that
the model takes as inputs. Read `calibration/README.md` before capturing
another one; the first sweep produced a fit that failed cross-validation, and
the README says exactly what coverage a sufficient sweep needs.
