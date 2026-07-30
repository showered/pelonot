# Pelonot — working notes

Subscription-free Android client for jailbroken Peloton bikes (Gen 1/Gen 2).
Kotlin, Jetpack Compose, Room, minSdk 24 / targetSdk 34.

**Read `ARCHITECTURE.md` for how data flows through the app, and `PLAN.md` for
what is done and what is next.** Phase 9 is the current priority.

---

## Commands

```bash
./gradlew assembleDebug            # must always pass
./gradlew testDebugUnitTest        # 83 JVM tests, must stay green
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
