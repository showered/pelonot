# Pelonot

A subscription-free Android client for the Peloton Bike and Bike+ (Gen 1/Gen 2)
that runs **on the bike's own tablet**, reads its real sensors, and keeps your
rides on your own device.

**Your bike does not need to be jailbroken.** Telemetry comes from Peloton's own
sensor service, which any app is allowed to bind. Nothing here needs root, a
modified bootloader, or a hardware mod.

> **Status: it works, and it is not finished.** A ride records, the HUD runs over
> whatever you are watching, heart-rate straps pair, and the whole history is
> yours to export. Accounts, multi-device sync and a companion web app are not
> built. `PLAN.md` is the honest picture of what is done, what is not, and what
> was once claimed to be done and was not.

---

## What it does

- **Records a real ride.** Cadence, resistance, power and heart rate, every
  second, into a local SQLite database. On the bike the watts are *measured by
  the board* — not estimated.
- **Runs a structured class.** Interval targets, a countdown into each change,
  and a spoken coach that ducks under your film rather than shouting over it.
- **Stays out of the way.** The HUD is a handful of translucent chips docked to
  one screen edge, over Netflix or anything else. It collapses to a single pill
  when you want the picture back.
- **Shows you the ride afterwards.** Power against your own zone bands, heart
  rate drawn only where a strap was actually reporting, cadence distribution,
  time in zone, and the class's prescribed intervals under the trace of what you
  really did.
- **Lets you leave.** Every ride exports to CSV or TCX, so Strava and everything
  else can have it.
- **Works with no bike at all.** Settings → *Telemetry source* → **Simulated**
  drives the entire app from a simulated rider. You can develop, demo and test
  the whole thing on an emulator.

---

## Running it

```bash
./gradlew assembleDebug
```

```bash
./gradlew installDebug
```

Requirements are Android Studio (or a JDK 17 and the Android SDK), `minSdk 24`,
`targetSdk 34`. There are no secrets needed to build: the Supabase keys below
are optional and the app is fully functional without them.

### On an emulator, with no bike

Everything except the sensor board works on an emulator. Pick **Simulated** as
the telemetry source and the full ride flow runs.

Use an AVD that matches the bike, not a phone. The bike is **landscape 1920 ×
1080 at 240 dpi — 1280 × 720 dp** with a 48 dp navigation bar and no status bar,
and a phone AVD hides every layout problem this app has. `HARDWARE.md` has the
measured figures and a recipe.

### On the bike itself

The tablet takes `adb` over the network like any other Android device, and
`./gradlew installDebug` targets it. Settings → *Telemetry source* →
**Hardware** forces the real sensor board with no simulated fallback, which is
the setting to test under — Pelonot will never quietly substitute made-up
telemetry for a ride you are recording.

`HARDWARE.md` documents the tablet as measured: display, system bars, input
devices, and which packages matter.

### Cloud sync (optional)

Sync is off unless you supply your own Supabase project. Put the keys in
`local.properties`, which is git-ignored:

```
supabase.url=https://yourproject.supabase.co
supabase.anonKey=your-anon-key
```

They reach the app through `BuildConfig`, never through source. Without them the
app runs entirely locally, which is the supported configuration — the cloud is a
mirror here, never a dependency.

**Where the build looks, highest first:**

| | Source | Notes |
|---|---|---|
| 1 | `SUPABASE_URL`, `SUPABASE_ANON_KEY` in the environment | for CI and for a build you do not want to leave on disk |
| 2 | `supabase.url`, `supabase.anonKey` in `local.properties` | git-ignored; the normal place |
| 3 | the same two keys in `cloud.properties` | checked in, and **empty** |
| 4 | nothing | offline, and supported |

A blank counts as absent at every level, so an exported-but-empty variable falls
through rather than blanking the build.

`cloud.properties` is in the repository so that a fresh clone has an in-repo
record of what the cloud even is; it ships with no endpoint and no key, and its
comments say why (short version: every RLS policy is still `USING (true)`, and
a shared endpoint is a bill somebody has to pay). A test fails the build if it
stops being empty. **To run your own, see [`supabase/README.md`](supabase/README.md)** —
the schema, the migrations, and the order to apply them in.

---

## How it is put together

Kotlin, Jetpack Compose, Room, WorkManager, a `ServiceLocator` rather than a DI
framework. **`ARCHITECTURE.md` is the map**: how bytes become a reading, how a
reading becomes a ride, and where the ride ends up.

Two things worth knowing before reading any code:

- **The pure logic is deliberately free of Android imports** — `PowerModel`,
  `CadenceTracker`, `WorkoutMetricsCalculator`, `PostWorkoutAnalyzer` and
  everything under `domain/` are plain Kotlin and JVM-testable. That is why
  there are 260 unit tests that run in a couple of seconds.
- **`PowerModel` is not trustworthy and the code says so out loud.** Its shipped
  coefficients score RMSE 137 W against 310 measured samples from a real board.
  It never produces a recorded number: on hardware the board reports watts
  directly, and the model only drives simulated rides and a suggested resistance
  range. A bike can also fit its own curve from your rides — see `calibration/`.

---

## Contributing

See `CONTRIBUTING.md`. In short: `PLAN.md` is the backlog and the history,
`./gradlew testDebugUnitTest` must stay green, and a checkbox is only ticked when
the behaviour has been *observed working* — this project has a table of features
that were marked done while completely broken, and it exists so that does not
happen again.

## Licence

Apache 2.0 — see `LICENSE`.

This is an independent project. It is not affiliated with, endorsed by, or
supported by Peloton Interactive, Inc. "Peloton" is their trademark, used here
only to say which bike this runs on.
