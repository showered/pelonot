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
./gradlew testDebugUnitTest        # 321 JVM tests, must stay green
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
- **The rider-facing name for the floating display is "overlay"** — never
  "HUD" (jargon) and never "strip" (tried, rejected by the owner). The source,
  `PLAN.md` and `ARCHITECTURE.md` still say HUD internally and should: one name
  in the code, one name on screen. PLAN.md 11.6.5.
- **A column of text or form fields is capped at `MaterialTheme.layout
  .readableWidth`** via `Modifier.readableColumn()` — one token, not a number
  per screen. The bike is 1280 dp wide and a line of body text across all of it
  is harder to read than the same text at 700. **Not** the ride screen or the
  overlay: those are deliberately full-bleed and read at two metres. PLAN.md
  22.2.6.
- **The owner leaves notes in PLAN.md's *owner's inbox*** without opening a
  session. Read it before picking work; it outranks *What to do next*. Write
  each entry up as numbered plan items and then **empty it** — an entry still
  sitting there has not been dealt with.
- Comments explain *why*, not *what*. Match the density of surrounding code.

---

## The connectivity model — read before touching anything cloud-shaped

Settled 1 August 2026 and written out in full in PLAN.md, *The connectivity
model*. Four rules:

1. **Offline by default.** A rider with no account makes **no request to
   Supabase at all**. Offline is the mode, not a fallback.
2. **An account unlocks cloud backup.** Signing in *is* the consent.
3. **An offline rider still gets social with the people on their own bike** —
   a household leaderboard is a Room query and must never touch the network.
4. **A signed-in rider gets both**, plus friends on other bikes.

The gate is **`UserEntity.auth_user_id != null`, per profile** — not
`SupabaseModule.isConfigured`, which asks about the build, not the rider.

**This is built** (PLAN.md 23.1, 23.2). One class, `CloudAccess`, answers it;
`SupabaseSyncRepository` asks at its single choke point before it resolves the
client; and **no cloud method can be called without naming the rider it acts
for**. `CloudAccessFenceTest` holds the shape in place: the Supabase SDK is
importable from `data/remote` only, the client is dereferenced once, and a new
entry point with no rider on it fails the build. If you need the cloud from
somewhere new, route it through `SupabaseSyncRepository` — do not import the
SDK, and do not reach for `isConfigured`.

Two consequences to know before you are surprised by them:

- **Nothing sets `auth_user_id` yet**, because Phase 15 (accounts) does not
  exist. So every profile is offline, every cloud call returns
  `SyncOutcome.Disabled`, and no build can reach Supabase. That is rule 1
  working. To exercise the cloud path, set the column by hand on the device.
- **The class library is bundled** — all 72 in `assets/classes`, seeded from
  assets always. The cloud is an update channel and nothing reads it today.

---

## Things that will bite you

- **`msg.what` FROM THE SENSOR SERVICE IS A LIE, AND THE FRAME IS NOT.** This
  was 2.7, the worst defect the project has had, and it is fixed — but the rule
  it leaves behind is permanent. Peloton's service assigns `what` by **position
  in its own request cycle**, so anything disturbing that cycle slides the
  labels along while the payloads stay put (measured: 55 of 204 messages
  mislabelled, a stationary rider reported at 544 rpm). Every reply also
  carries `responseHexString`, the board's own self-identifying frame:
  `F1 <id> <len> <ASCII digits, least significant first> <checksum> F6`, with
  `0x41` cadence, `0x44` power **in tenths of a watt**, `0x49` resistance and
  `0x4A` **raw** resistance. `PelotonFrameParser` decodes it and **the frame
  decides the metric**. Never reintroduce a `when (msg.what)`. **Read PLAN.md
  2.7c.**
- **The sensor service opens the exclusive UART inside `onBind`, and the port
  leaks.** One `/dev/ttyO0`, one open, so two bike apps can never both work —
  and after the other app is gone the port can stay unopenable until the tablet
  is **rebooted** (observed: retry attempt 141, `could not open /dev/ttyO0`).
  Every rebind reopens it, so our own retry loop is a source of disturbance:
  be reluctant to rebind. PLAN.md 2.7d, 2.7.7, 2.7.8.
- **It does not reproduce on the emulator, and that has been checked properly.**
  278-second simulated ride with the overlay genuinely raised for 192 of them:
  zero corrupt samples. The check that makes this cheap is that **a simulated
  ride carries its own checksum** — the simulator derives power from the
  cadence and resistance it emits, so `power == PowerModel.estimateWatts(
  cadence, resistance)` must hold for every honest row, and any field swap
  breaks it. Two things that look like the bug and are not: the shipped power
  curve genuinely returns ~650 W at 130 rpm, and `ride-simon` / `ride-alex` are
  hand-seeded leaderboard fixtures that fail the invariant by construction.
- **Reject, never clamp.** The fence turns an impossible value into a *gap*,
  because clamping 603 rpm to 200 writes a plausible lie exactly where a gap
  belongs and nobody can tell it from a real sprint afterwards. Same argument
  as nullable `heartRateBpm` and as `isStaleAt`. If you add a new metric,
  give it bounds in `TelemetryBounds` and let it be absent.
- **And an impossible value discredits the values either side of it.** This is
  the part a plain fence got wrong: if the labelling is off by one, the
  intruder is out of range but its neighbours are *in* range and wrong — a
  power of 37 W filed as 37% resistance breaks no bound anyone can write.
  `TelemetryAssembler` therefore throws away everything it holds on a rejection
  and publishes nothing for four seconds. Note the intruder is caught in only
  two columns of three: 636 W is a possible power, and that is precisely the
  636 W spike on the first real ride's chart.
- **The bike is a perishable resource, so both halves of 2.7 are levers on the
  emulator** — `com.pelonot.debug.CORRUPT` and `com.pelonot.debug.SILENCE`,
  beside `COAST`. See PLAN.md 2.7a for the commands and for the run that
  closed 2.7.3 and 2.7.4.
- **The database is the witness, not the screenshots.** Two screenshots taken
  seconds apart cannot show two surfaces disagreeing. `workout_metrics` holds
  what the recorder actually saw, once a second, with a timestamp — which is
  what turned "the overlay looks erratic" into a 0-vs-41 measurement in
  fifteen minutes. Reach for the table first.

- **`workout_metrics` has a foreign key onto `workouts`.** The workout row must
  be inserted (with `is_complete = 0`) *before* any metric is written. This
  ordering is why metric recording was silently broken for the whole project
  history.
- **`intervals_json` is snake_case with start/end timestamps**, not camelCase
  durations. `Interval` uses `@SerialName` to match the assets exactly. A
  mismatch throws and is easy to swallow into an empty list.
- **NEVER `OnConflictStrategy.REPLACE` on a table something else points at.**
  SQLite implements REPLACE as a delete plus an insert, **and the delete fires
  foreign-key actions**. This project had it three times and one of them was
  live: `UserDao.insertUser` was REPLACE, `UserRepository.save` is what every
  FTP change, weight change and rename goes through, and `workouts.user_id` is
  `ON DELETE SET NULL` — so **editing your FTP silently unattributed your whole
  ride history**, for the life of the project, with nothing looking broken
  because the rides were still there. `class_templates` had the same shape
  (23.2.6c) and `workouts` still does not, only because a ride is inserted once
  and finalised through `@Update` — `workout_metrics` is `ON DELETE CASCADE`.
  All three are `@Upsert` now. `UserDaoTest` holds the line and was checked
  against the bug as well as against the fix. Measured with `sqlite3`, not
  reasoned about — it is four lines to check and expensive to be wrong about.
- **Do not hand-edit `assets/classes/`.** The 72 classes are generated from
  `classlibrary/catalogue.py` by `classlibrary/build.py`, which refuses to
  write if a session breaks a design rule. Edit the catalogue, run the build,
  commit both. `classlibrary/README.md` is the rules and the reasoning; PLAN.md
  23.2.6. `ClassLibraryAssetsTest` re-checks the rules against the emitted JSON,
  because the assets are what ships and a generator nobody runs cannot vouch
  for them.
- **A class the bundle drops is *retired*, never deleted, if a ride points at
  it** (`class_templates.retired_at`). `ClassTemplateSeeder` reconciles against
  the bundle on every launch, gated on the fingerprint in
  `assets/class_library.json` — which `build.py` writes, so **a catalogue change
  that is not rebuilt will not reach a tablet that already seeded**.
- **A Room `Flow` only re-emits when a table its query *mentions* is written.**
  The dashboard's household panel is driven off a count over `workouts` joined
  to `profiles` for exactly this reason: without the join, turning
  `household_visible` off left the rider on the panel until somebody rode.
- **An interval's `target_position` is optional and absent means the rider
  chooses.** Never default it. Same family as `heartRateBpm` and
  `power_is_measured`: absent is a claim, and it is a different claim from
  either value. Nothing in the UI may draw an "either" state for it.
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
- **`workout_metrics.power_is_measured` is nullable and null means *nobody
  wrote it down*** — not "modelled". Ask `PowerProvenance`, never the raw
  column: `Unknown` and `Modelled` are different claims and only `Measured`
  passes `isTrustworthyAsMeasured`, which is what gates the FTP proposal
  (7.10.7) and the household leaderboard (24.4.2). `Mixed` fails it too, on
  purpose. **A consequence for verification: the emulator can only produce
  simulated rides, so anything gated on measured power shows nothing there
  until you set the column by hand.**
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

**`connectedDebugAndroidTest` uninstalls the app**, so it wipes every profile
and ride a UI session has just set up. Run instrumented tests *before* driving
the UI, not between.

To change a column by hand — the way the consent gate and the leaderboard were
both driven on the AVD — pull, edit, and push back through `run-as`; the app
must be force-stopped and the WAL checkpointed first, or the edit is lost:

```bash
adb shell am force-stop com.pelonot && adb shell "run-as com.pelonot cat databases/pelonot_database" > db.sqlite && adb shell "run-as com.pelonot cat databases/pelonot_database-wal" > db.sqlite-wal && sqlite3 db.sqlite "PRAGMA wal_checkpoint(TRUNCATE); UPDATE profiles SET auth_user_id='test'; PRAGMA journal_mode=delete;" && adb push db.sqlite /data/local/tmp/db && adb shell "run-as com.pelonot sh -c 'cat /data/local/tmp/db > databases/pelonot_database; rm -f databases/pelonot_database-wal databases/pelonot_database-shm'"
```

```bash
adb shell "run-as com.pelonot cat /data/data/com.pelonot/databases/pelonot_database" > db.sqlite
adb shell "run-as com.pelonot cat /data/data/com.pelonot/databases/pelonot_database-wal" > db.sqlite-wal
sqlite3 db.sqlite "SELECT COUNT(*) FROM workout_metrics;"
```

Settings → Telemetry source → **Simulated** makes the whole ride flow work
without a bike.

The simulated rider rides a smooth effort wave and **never stops**, so anything
about a rider standing still — auto-pause, the gap a stop leaves in the series,
what the averages do across one — is invisible under it unless you ask for a
stop. Debug builds have a receiver for exactly that (PLAN.md 19.1.2a):

```bash
adb shell am broadcast -a com.pelonot.debug.COAST \
  -n com.pelonot/com.pelonot.debug.DebugTelemetryReceiver --ei seconds 40
```

It also never lies and never goes quiet, which is the other half of the same
problem — 2.7 was found on a bike with a rider on it. The same receiver takes
`CORRUPT` (the values rotate between columns, with a ghost near 602 in
whichever one it lands in) and `SILENCE` (the source stops delivering *without
failing*, which is what let a dead board survive a whole ride):

```bash
adb shell am broadcast -a com.pelonot.debug.CORRUPT \
  -n com.pelonot/com.pelonot.debug.DebugTelemetryReceiver --ei seconds 30
```

```bash
adb shell am broadcast -a com.pelonot.debug.SILENCE \
  -n com.pelonot/com.pelonot.debug.DebugTelemetryReceiver --ei seconds 20
```

The result to look for is **gaps in `workout_metrics`, not clamped numbers**,
and `Telemetry live again at …s` in logcat without an app restart.

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

### Logging on this tablet — it drops your logs by default

**`log.tag` is set to `W` device-wide**, so every `Log.i` and `Log.d` in the
app is discarded before it reaches logcat. This cost three attempts and a
wrong conclusion before it was noticed. Raise the tags you need first:

```bash
for t in PelotonSensorSource PelotonSensorTrace SensorRepository WorkoutService DebugTelemetry; do
  adb shell setprop log.tag.$t VERBOSE
done
```

It does not survive a reboot. `Log.w` and above always get through, which is
why the stale-telemetry warnings were visible all along and nothing else was.

**The raw sensor stream can be dumped on demand** (debug builds), which is what
solved 2.7 — it shows each message's `what` beside the board's own frame, so a
mislabel is visible directly:

```bash
adb shell am broadcast -a com.pelonot.debug.TRACE \
  -n com.pelonot/com.pelonot.debug.DebugTelemetryReceiver --ei seconds 60
adb logcat -s PelotonSensorTrace
```

**Most of this needs no rider.** Resistance polls from the knob whether or not
anyone is pedalling, so a bind, the poll cycle, and any mislabelling can all be
confirmed alone — and with the bike stationary a non-zero cadence is
unmistakable evidence rather than something to be picked out of real data. The
whole of 2.7 was diagnosed on 90 seconds of pedalling.

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
