# Pelonot — working notes

Subscription-free Android client for Peloton bikes (Gen 1/Gen 2). Runs on a
**stock, un-jailbroken bike** — telemetry comes from Peloton's own sensor
service, not from root.
Kotlin, Jetpack Compose, Room, minSdk 24 / targetSdk 34.

**Read `ARCHITECTURE.md` for how data flows through the app, and `PLAN.md` for
what is done and what is next.** PLAN.md opens with a *Where the work stands*
section naming the current priority — read that before picking work.

---

## Commands

```bash
./gradlew assembleDebug            # must always pass
./gradlew testDebugUnitTest        # 186 JVM tests, must stay green
./gradlew installDebug             # needs a booted emulator or device
./gradlew connectedDebugAndroidTest
```

Emulator: `~/Library/Android/sdk/emulator/emulator -avd Medium_Phone_API_36.1`,
with `adb` at `~/Library/Android/sdk/platform-tools/adb`.

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
- **`PowerModel`'s coefficients are unvalidated.** Absolute watts are not
  trustworthy; they are self-consistent between the user's own rides only. Say
  so rather than presenting them as measured.
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
- **`SensorMode.Hardware` deliberately does not fall back to simulation.**
  Substituting fabricated telemetry mid-ride would corrupt a permanent record.
- **The database uses `fallbackToDestructiveMigration()`** while pre-release.
  Swap to real migrations before anyone installs a build with data they care
  about.

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
