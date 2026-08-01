# Contributing to Pelonot

Thanks for looking. This file is short and specific, because most of what a
contributor needs to know about this project is unusual enough that generic
advice would not help.

## Start here

- **`PLAN.md`** is the backlog *and* the history. Its *Where the work stands*
  section names the current priority; read that before picking something up.
  It is an **index**: the phases themselves are one file each under `plan/`,
  and PLAN.md's *Where the rest of this plan lives* table says which. Item
  numbers (2.7c, 11.6.5, 24.3.1) are unchanged and work as references
  throughout the repo.
- **`ARCHITECTURE.md`** is how data moves through the app.
- **`HARDWARE.md`** is the bike's tablet as measured — read it before any UI
  work, so a layout is checked at the size it will really run at.

## You do not need a bike

Settings → *Telemetry source* → **Simulated** drives the whole app from a
simulated rider. Class flow, recording, HUD, charts, history and export all work
on an emulator with no hardware at all. Most of this project was built that way.

Use an AVD that matches the bike — **1920 × 1080 at 240 dpi, landscape** — and
not a phone. A phone AVD hides every layout problem this app has, and an AVD at
the right resolution but the wrong density hides half of them. `HARDWARE.md` has
a recipe.

## The two rules that matter

### 1. A box is only ticked when the behaviour has been observed working

Not when the code was written, not when it compiled, not when a unit test
passed. `plan/corrections.md` lists features that were marked
complete while entirely non-functional — cloud sync that had never written a
row, a heart-rate stack behind a permission the manifest did not declare, an
energy calculation off by a factor of a thousand. Every one of them looked fine
from the UI.

So: install it, drive the real flow, and query the database when data integrity
is the question. `CLAUDE.md` has the commands for pulling the SQLite file off a
device.

### 2. Anything that can fail quietly must have somewhere it can be seen to fail

The single most common defect in this project's history is a caught exception
returned as a value nothing reads. From every surface anyone looks at, that is
indistinguishable from success. If you add something that can fail — a sync, an
export, a login, a delete — add the place the rider finds out it did.

## House conventions

- **Pure logic stays free of Android imports.** Everything under `domain/`, plus
  `PowerModel`, `CadenceTracker`, `SerialProtocolParser`,
  `WorkoutMetricsCalculator` and `PostWorkoutAnalyzer`, is plain Kotlin and
  JVM-testable. Do not add `android.util.Log` to them.
- **No database access from composables.** Repository → ViewModel `StateFlow` →
  `collectAsStateWithLifecycle`.
- **Dependencies come from `ServiceLocator`**, not new singletons.
- **Dependency versions live in `gradle/libs.versions.toml`**, never inline.
- **Secrets live in `local.properties` → `BuildConfig`**, never in source.
- **Comments explain *why*, not *what*.** Match the density of the code around
  them. A comment saying what a line does is noise; one saying why it is not the
  obvious thing is the most valuable line in the file.

## Things that will bite you

`CLAUDE.md` keeps the full list. The ones worth repeating here:

- **`heartRateBpm` is nullable and null means *unknown*.** Never default it to
  zero. That writes a fabricated sample into a rider's permanent record and
  drags every average down. This has had to be fixed three times.
- **`workout_metrics` has a foreign key onto `workouts`.** The workout row must
  exist before any metric is written.
- **Every schema change needs a `Migration`,** an exported schema in
  `app/schemas/`, and a `MigrationTestHelper` test. The destructive fallback is
  gone deliberately: it deleted the rider's entire training history.
- **A runtime permission the manifest does not declare is denied instantly,**
  with no dialog and nothing in logcat. Two of this project's defects are that
  exact shape.
- **An aggregate is only trustworthy when checked against the rows it
  summarises.** `avg_hr` was wrong for the project's whole history sitting
  beside two neighbours that were exact.

## Before opening a pull request

```bash
./gradlew assembleDebug
```

```bash
./gradlew testDebugUnitTest
```

Both must pass; 253 unit tests are currently green and should stay that way. If
you touched the database, run the instrumented tests too:

```bash
./gradlew connectedDebugAndroidTest
```

Say in the PR **what you observed working, and how you observed it** — the
screen you drove, the query you ran, the log line you read. That sentence is
worth more here than the diff summary.
