> Part of the Pelonot plan — the index is [PLAN.md](../PLAN.md).

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
- [x] **2.2.4** ~~Validate `PowerModel` coefficients against a known curve or a real power meter.~~ **Answered, and the answer was "they are badly wrong"** — RMSE 137 W, median absolute error 66%, R² 0.21 against 310 measured samples (2.2.5). The validation this box asked for is done; what it *implied* — that the app then goes and fixes the constants — is settled the other way in **2.2a**, which calibrates per bike instead. The standing caveat is now scoped and no longer open-ended: modelled watts govern simulated rides and the resistance band, nothing else, and 2.2a.8 makes that a test rather than a promise
- [x] **2.2.5** ~~Fit the coefficients from a measured sweep.~~ **Done once, deliberately not shipped, and deliberately not to be repeated** — superseded by 2.2a. The capture method and the data are worth keeping; the approach is not. Kept in full below because it is the evidence 2.2a's decision rests on.

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

> ## Decided: yes, calibrate. The whole argument, in one place
>
> This has been spread across 2.2.4, 2.2.5, this section and a caveat hanging
> over 16–18, and it has read as far more alarming than it is. Settled here so
> nobody re-opens it.
>
> **What the power curve can and cannot touch.** `PowerModel` has exactly two
> live consumers in the app: `SimulatedSensorSource`, which fabricates a ride,
> and `RideSnapshot.resistanceForWatts`, which turns an interval's power target
> into "put the knob about here" (11.2.1). On the bike,
> `PelotonSensorServiceSource` reports the board's own measured watts and the
> curve is never consulted. **No recorded number from a real ride comes from
> this model.** A wrong curve gives a rider a bad suggestion; it cannot corrupt
> a record, move an FTP, or make one ride incomparable with the next.
>
> **So the only question is whether the suggestion is any good — and today it
> is not.** The shipped coefficients score RMSE 137 W, median absolute error
> 66% and R² 0.21 against 310 measured samples off this bike (2.2.5). A
> resistance band built on that is not advice, it is a guess with a confident
> border drawn round it.
>
> **Leaving it hardcoded is not the cautious option, it is the current
> defect.** And a *better* hardcoded constant is not available: the one manual
> sweep failed cross-validation (2.2.5), no stranger will ever perform a sweep
> on their own bike, and per-unit sensor variation (2.1a.5a) plus mechanism
> wear mean a single constant is the wrong **shape** of answer however
> carefully it is measured.
>
> **The drift worry, answered — it was misapplied.** Drift is dangerous when a
> stored record is reinterpreted later against a moved yardstick. That is a
> real problem in this app and it is why a ride must snapshot the FTP it was
> judged against (7.8). Calibration has no such exposure, because nothing it
> produces is ever stored: the band is computed for the interval the rider is
> in and is gone a minute later. Recency weighting (2.2a.5) is therefore a
> feature rather than a hazard — a mechanism that has worn, or been serviced,
> *should* stop being described by how it behaved when it was new.
>
> **The downside is bounded at the status quo.** A fit is adopted only if it
> beats the shipped curve, *and* lands within an absolute 25% out of sample,
> *and* has seen enough of the grid (2.2a.3, 2.2a.4); a simulated ride never
> contributes (2.2a.7). Every failure path ends in "keep using the shipped
> curve", which is exactly where the app is today. The worst case is that this
> does nothing — visibly, in Settings.
>
> **What would make us abandon it**, stated in advance so it is a measurement
> and not a mood: if 2.2a.1 shows that ordinary riding does not cover enough of
> the resistance × cadence grid within a few weeks, this never fires, and it
> should then be **deleted** rather than left as machinery that looks like it
> works. Settings already reports the coverage needed to judge that.

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

**Built and tested; one half of it still needs a bike.** Everything below is in
`domain/calibration/` (pure, JVM-tested) plus a `CalibrationRepository` and a
Settings section. The gates are exercised against the real 31 July sweep in
`RealSweepCalibrationTest`, which is the only measured data this project has.

- [ ] **2.2a.1** Accumulate steady-state `(cadence, resistance, measured
      watts)` from rides where `powerIsMeasured` is true. Reuse the filter that
      worked on the 31 July sweep: drop samples where the knob is mid-turn or
      cadence is lurching, since those are transitions rather than operating
      points. Store a compact summary, not every sample — a grid of binned
      means is enough to fit against and does not grow without bound.
      **Written and unit-tested — 3,000 samples collapse to ≤4 cells, and the
      steady-state filter drops mid-knob-turn and lurching-cadence samples —
      but the hardware half is unobserved.** Nothing has yet watched a real
      Hardware-mode ride land in the grid, and per the house rule that is what
      this box is for. Ride the bike and check Settings says more than
      "0 measured seconds"
- [x] **2.2a.2** **Calibration belongs to the bike, not the rider.** A
      household bike has several profiles and one resistance mechanism, so this
      is device-level state that every profile shares. It must not live on
      `profiles` and must not sync as if it were personal (15). *Its own
      `pelonot_bike_calibration` DataStore: no profile column, no Room table,
      not in any DTO — so there is nothing for 15's sync to pick up by
      accident.* Deliberately not a Room migration either: it is derived state,
      and losing it costs a few weeks of passive accumulation rather than a
      rider's record
- [x] **2.2a.3** **Do not adopt a fit that cannot beat the shipped curve.**
      This is the whole lesson of 2.2.5: hold out a resistance level, predict
      it, and keep the generic coefficients unless the fit genuinely wins. An
      auto-calibration that silently makes the numbers worse is the same
      failure as everything in the *Corrections* table.
      **And a second gate the first test of this found the hard way**: "better
      than shipped" is a bar a fit to *pure noise* clears, because the shipped
      curve's median error on real data is 66%. So a candidate must also be
      within an absolute 25% out of sample before it is used at all — chosen
      against what the number is for, since a resistance band 25% out still
      puts a rider in the right part of the knob
- [x] **2.2a.4** Require coverage before fitting at all. The 31 July sweep had
      six distinct resistance levels and that was not enough to determine the
      exponent. Track which cells of the resistance × cadence grid have been
      ridden and stay on the shipped curve until enough of them have.
      *Seven levels, each at three or more cadences, spanning at least 40
      percentage points. The regression test that matters: **the 31 July sweep
      itself does not clear this gate**, and is refused for want of coverage
      rather than for want of accuracy — exactly the conclusion
      `calibration/README.md` reached by hand. If that test ever goes green on
      an `Adopted`, the thresholds have drifted below what was already known to
      be insufficient*
- [x] **2.2a.5** Weight recent rides more heavily so the fit tracks drift
      rather than averaging a worn mechanism together with how it behaved when
      it was new. *Each cell's weight decays 3% per ride and a cell that falls
      below 0.5 is forgotten, so a mechanism that has since been serviced stops
      voting*
- [x] **2.2a.6** Say so in Settings: whether this bike is running the shipped
      curve or its own, how much of the range it has seen, and when it last
      re-fitted. A calibration that silently does nothing is indistinguishable
      from one that works (see the *Corrections* rule). *Observed: "Using the
      built-in curve", the coverage bar, "0 of 7 levels, from 0 measured
      seconds", and a "Start again" that appears only once there is something
      to throw away.* It also says out loud that the built-in curve is known to
      be well out, and that recorded watts are unaffected either way
- [x] **2.2a.7** Simulated rides keep the shipped curve. There is no bike to
      calibrate against and the numbers are fiction by construction — learning
      from one would be the model teaching itself its own answer. *Observed: a
      full simulated ride left Settings reading "0 of 7 levels, from 0 measured
      seconds", and the calibration DataStore file was never even created*

- [ ] **2.2a.8** **Fence the model with a test rather than a comment.** The
      scope argument above is the entire safety case for calibrating at all, and
      right now it holds only because two call sites happen not to have grown. A
      test that asserts `PowerModel` is reached from exactly the simulated source
      and the resistance band — and fails the build when a third consumer
      appears — is what stops some future feature quietly deriving a *recorded*
      number from an uncalibrated curve. It is cheap, and it is the difference
      between a rule and a hope
- [ ] **2.2a.9** **Say what the resistance band is worth while the shipped curve
      is still in use.** Until a bike has calibrated itself, 11.2.1's band comes
      from coefficients known to be 66% out at the median, and it is drawn with
      exactly the same authority as the cadence band beside it — which is
      prescribed by the class directly and is simply *true*. Either mark it as
      approximate until the bike has its own curve, or do not draw it at all.
      The most-wrong number on the ride screen should not be the most
      confident-looking one. Settings already knows which curve is in use
      (2.2a.6), so the screen can too
- [ ] **2.2a.10** Once a bike is on its own curve, revisit **11.2.1a** — the
      Zone 1 band that vanishes for a low-FTP rider because the unloaded curve
      at 85 rpm already exceeds the whole zone. That item is currently blocked
      on "we cannot tell a modelling artefact from a real contradiction", and a
      calibrated curve is precisely what resolves it

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
- [x] **2.4.4** **A stale reading is an absence, not a sample.** When the board
      stops answering, `PelotonSensorServiceSource` closes the flow and
      `SensorRepository` backs off and retries — but `_sensorReading` is a
      `StateFlow`, so it goes on holding **the last value that arrived**. The
      ticker in `WorkoutService.recordMetric` reads `.value` unconditionally
      once a second, so a dropout writes that frozen reading into
      `workout_metrics` every second for as long as it lasts, folds it into
      `avg_power` and `avg_cadence`, and feeds it to the calibration grid with
      `powerIsMeasured` still true. Total output is the one figure that escapes,
      by accident: `WorkoutMetricsCalculator` integrates against
      `reading.timestampMs`, and a repeated reading carries a repeated
      timestamp, so `dt` is zero. That accident is the shape of the fix —
      **`timestampMs` already says how old a reading is and nothing else asks.**
      Same family as nullable `heartRateBpm` and the `TIME_OUT` payload in
      2.1a.3: a zero or a repeat that means *we do not know* must not enter the
      rider's permanent record as though it were measured.
      *Fixed by the one thing that already knew: `timestampMs` says how old a
      reading is and nothing asked. A reading older than three seconds ends the
      tick — no `workout_metrics` row, no contribution to the averages, no
      calibration sample — so the second is a **gap**, which is what actually
      happened. Seven JVM tests on the rule, including the two traps: `EMPTY`
      carries `timestampMs = 0` and must never read as live, and a heart-rate
      packet `copy()`s the bike's reading and must not refresh its timestamp.
      **Observed on the tablet AVD**: a 47-second ride in Hardware mode with no
      board wrote **0 rows** to `workout_metrics` and finalised at 0.0 kJ,
      where before it would have written 47 fabricated ones. The frozen-mid-ride
      case — the board dying at 200 W and holding it — is covered by the tests
      and has not been staged on a device; there is no way to make the
      simulator stall*
- [x] **2.4.5** **The rider is never told the sensor stopped.**
      `SensorStatus.Reconnecting` is constructed, logged and rendered nowhere:
      the only consumer of `SensorStatus` in the whole UI is `isSimulated` on
      `RideUiState`. In Hardware mode with a dead board the ride screen shows
      frozen numbers with no explanation and the HUD shows nothing at all — and
      the strip has no simulated-telemetry marker either, though the ride screen
      does. This is the *Corrections* rule applied to telemetry: a failure path
      that is caught, logged and returned as a value nothing reads is
      indistinguishable from success from every surface anyone looks at.
      *Both surfaces now show the two dashes an absent heart-rate strap has
      always had, rather than a frozen number: `telemetryLive` rides on
      `RideSnapshot` because it is a fact about the **absence** of telemetry,
      and a flow that has stopped emitting cannot announce that it stopped —
      the service's tick is the only thing in the app that notices time passing
      without a reading. **Observed on the tablet AVD** in Hardware mode with no
      board: the ride screen says "No signal from the bike — not recording" and
      all four tiles read `--`; the strip's status line says NO SIGNAL in the
      same amber the pause state uses. One thing found by looking: the full
      sentence clipped to "NO SIGNAL · NOT" on the strip, because that chip is
      only as wide as the clock above it. The strip gets two words and the ride
      screen keeps the sentence. Two dashes at 104 sp read as two coloured bars
      — legible as "no value", but worth a look when 11.6.3 does the metric
      tiles properly*
- [x] **2.4.6** **The telemetry source the rider picked was forgotten at the
      next launch.** Found while verifying 2.4.5, and worse than what it was
      found in aid of. `SensorRepository.setMode` had exactly one caller —
      `SettingsViewModel`, on tap — so the choice was applied to the *object*
      only in the session it was made in. On every launch after that the
      repository was back to its default of `Auto`, while Settings went on
      drawing the chip the rider had chosen, because **Settings reads DataStore
      and the pipeline reads its own field** and nothing reconciled the two.
      The consequence is not cosmetic: `SensorMode.Hardware` exists so that a
      ride never records fabricated numbers, and after a restart it was
      silently `Auto`, which falls back to simulated telemetry when the board
      does not answer. Same shape as everything in *Corrections* — a setting
      that reads back correctly everywhere a human looks and does nothing.
      *Observed on the tablet AVD, and it is the clearest evidence of the day:
      Settings showing **Hardware** selected while the ride beside it recorded
      a plausible simulated 68 rpm / 71 W. `PelonotApp` now collects the
      preference for the life of the process; after the fix the same cold start
      rides "No signal from the bike — not recording"*

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

### 2.7 What the first real ride found — the overlay corrupts telemetry

**Found 1 August 2026, on the bike, with the owner pedalling. This is the most
serious defect this project has had**, because it is the one that writes wrong
numbers into a rider's permanent record while every surface looks plausible.

**The measurement.** One free ride, Hardware mode, rider pedalling steadily
throughout. The first 82 seconds were on the full-screen ride screen; at second
83 the overlay was raised and nothing else changed.

| Phase | Samples | Impossible values |
|-------|---------|-------------------|
| Full screen, 1–82 s | 82 | **0** |
| Overlay up, 83 s+ | 53 | **41 (77%)** |

Clean phase: cadence climbing 35→67 rpm, resistance rock-steady at 33%, power
tracking 18→53 W, heart rate 96→104. From second 83 **the same three real
values start appearing in each other's columns**:

```
82 |  67 |  33 |  53      ← true: cadence 67, resistance 33, power 53
83 |  67 |  53 |  53
84 |  33 |  68 | 603
86 |  67 | 602 |  33
91 | 603 |  66 |  52
95 | 602 |  33 |  51
```

**What it costs the rider, in one line:** over the clean phase they averaged
**61 rpm and 47 W**. The ride summary for that ride reported **109 RPM and
137 W** — cadence inflated 79%, power 191%. The aggregates are computed
correctly; they are computed over corrupted samples.

**The already-recorded ride is affected.** The first real ride (`HC-01`, 1196
samples) carries 11 impossible cadence values, 6 impossible resistance values
and 1 power value over 600 W — the 636 W spike visible on its power chart. The
overlay was up for part of it.

- [ ] **2.7.1** **Fix the field rotation.** `PelotonSensorServiceSource` keeps
      `powerWatts` / `cadenceRpm` / `resistance` as locals in one `callbackFlow`
      and dispatches on `msg.what` from a single `HandlerThread`, so *one*
      instance cannot rotate values. Rotation means **more than one live
      registration**, each holding its own partial state and all emitting into
      `_sensorReading`. The prime suspect is that raising the overlay causes a
      second bind or a second send of `REGISTER_COMMANDS` — start by logging
      every bind and every register, then by counting them across an
      overlay raise. `SensorRepository.start()` guards on `telemetryJob`, so
      the second one is unlikely to be there; the manifest declares no separate
      process, so it is not two `ServiceLocator`s

      **Half done, 1 August 2026 (tenth sitting), and the half that is done is
      not the cause.** Two things shipped, neither of which can be confirmed
      without the bike:

      - **`TelemetryAssembler`** replaces the three locals. It holds each of
        the board's streams with the instant it arrived and hands back a
        reading only when all three are present *and* within 2.5 s of each
        other. This fixes a real defect found while reading the code rather
        than the ride: the three locals started at `0.0` and *all three* were
        emitted whenever any one changed, so **the first message of every
        hardware ride published two measured-looking zeroes**, and every
        reading after it mixed instants. That is not the rotation, but it is
        the seam the rotation lives in, and it is now a pure class with tests.
      - **A single registration is now guaranteed per binding.**
        `onServiceConnected` runs again when the service dies and the system
        rebinds us to its replacement, and every extra `REGISTER_COMMANDS` is
        another repeating poll answering into the same reply Messenger.
        A process-wide counter logs at `E` the moment a second one is live —
        which is the count the plan asked for, ready for the next ride.

      **The "two registrations" hypothesis above is wrong, and 2.7b replaces
      it.** Reading the recorded samples rather than the summary shows the
      defect is a **one-place shift in a four-value stream**, and the fourth
      value has been identified. See 2.7b for the evidence. What remains of
      this item is the labelling itself, which needs the bike
- [x] **2.7.2** **Identify the ~602/668 value.** It is not any of the three
      metrics and appeared in both sessions. Most likely an event type the
      source does not handle whose `msg.what` collides with `EVENT_RPM` /
      `EVENT_WATT` / `EVENT_RESISTANCE`, or a second registration receiving a
      different event set. Log every `msg.what` seen with its payload for one
      minute of pedalling and the answer falls out

      **Identified from the recorded rides, without the bike. It is the raw
      resistance reading**: `≈ 11.13 × resistance% + 229`, which predicts all
      three independent sightings across two rides to within 1%, and spans 229
      at 0% to 1342 at 100% — a potentiometer's ADC range. Heart rate, the only
      rival with the right magnitude, is out by 12%. Full working in 2.7b
- [x] **2.7.3** **A plausibility fence at the recording boundary.** Cadence 603
      is not a measurement, and neither is resistance 602. This is the same
      argument as 2.4.4: a value that cannot be true is an absence, and the
      honest answer is a gap. **Reject, never clamp** — clamping 603 to 140
      would write a plausible lie where a gap belongs. Bounds should be
      physical (cadence 0–200 rpm, resistance 0–100%, power 0–2500 W) and
      generous enough that a real sprint is never dropped. **This is a fence,
      not the fix**: it must land alongside 2.7.1 rather than instead of it,
      or the rotation continues silently within the plausible range — 33 and 52
      swapping is invisible to any bound

      Built with exactly those bounds and applied twice: where the reading is
      published, so the overlay and the ride screen never show 603 either, and
      again in `recordMetric`, because that is the line that makes a number
      permanent. A rejected reading is not published at all, so the flow ages
      out on its own timestamp and the recorder leaves the gap that actually
      happened. **Observed on the tablet AVD** — see the run below
- [x] **2.7.4** **The board stops and never comes back.** With the overlay up,
      telemetry went stale and stayed stale for the rest of the ride —
      `WorkoutService` logged *Telemetry stale at 86s*, the overlay showed
      `--` on every metric, and **pedalling did not revive it**. Only a restart
      of the app did. The cause is that the flow does not *error*: it simply
      stops delivering, so `retryWhen` never fires and nothing ever rebinds.
      `MAX_CONSECUTIVE_TIMEOUTS` only counts explicit `TIME_OUT` responses, and
      silence is not one. Needs a **watchdog**: no reading for N seconds while a
      ride is running means tear the source down and rebuild it. Note the
      interaction with 2.7.1 — a rebuild must not leave the old registration
      alive, which may be the very thing causing the rotation

      `failOnSilence(6 s)` sits between the source and the existing
      `retryWhen`, so silence becomes an `IOException` and **the app's one
      retry policy does the rebuilding** — no second schedule, which is the
      rule `SensorRepository` exists to hold. Failing the flow cancels the
      source, which for the Peloton source means `awaitClose` unbinds and drops
      the registration *before* the new one is made, so the rebuild cannot
      leave two alive. **Observed on the tablet AVD**
- [x] **2.7.5** **Decide what happens to the rides already recorded.** They are
      real rides with real effort in them and a minority of corrupted samples.
      Deleting them is wrong; presenting their aggregates as fact is also wrong.
      The options are to recompute the aggregates with impossible samples
      excluded, or to mark the ride as suspect and say so. **Do not silently
      rewrite a rider's history either way** — whatever is chosen has to be
      visible, and it is the same provenance argument as 23.4.3

      **This is now the open item in 2.7**, and note that the fence has changed
      what it means: from here on a ride *cannot* accumulate impossible
      samples, so this is about the two rides on the bike today and nothing
      that comes after them. `implausibleValues()` is the same predicate the
      recorder uses, so counting the affected samples per ride is one query

      **Answered: mark and say so, and change nothing.** `RideIntegrity`
      (`domain/chart/`) judges a ride's own samples on read against the same
      `TelemetryBounds` the recorder rejects on — **whole-row**, because one
      impossible field means the labels slid and the other two values are then
      in range and in the wrong columns. The charts are drawn from the samples
      that survive; the samples, the `avg_*` columns and the export are
      untouched; and ride detail says all of that out loud, with the stored
      average and the corrected one **side by side** rather than one replacing
      the other. The stored figure is what the app told the rider on the day
      and is part of the record.

      **Observed on the bike's own database** — it turned out to be three
      rides, not two:

      | Ride | Samples | Impossible | Stored cadence | Corrected |
      |------|---------|-----------|----------------|-----------|
      | `HC-01`, 20 min | 1196 | 17 (1%) | 83 rpm | 78 rpm |
      | Just Ride, 3 min | 180 | 39 (22%) | 109 rpm | 53 rpm |
      | Just Ride, 5 min | 85 | 32 (38%) | 196 rpm | 11 rpm |
      | Just Ride, post-fix | 200 | **0** | 52 rpm | — |

      The notice renders correctly on all three and is absent on the fourth and
      on a clean AVD ride. Note the last row: the first ride recorded *after*
      the frame fix has nothing wrong with it, which is the fix and the fence
      seen from the record's side.

      **One thing this deliberately does not do.** The 636 W spike on the first
      ride's chart is still there, because 636 W is a possible power — the
      intruder is only impossible in two columns of three. The fence never
      claimed otherwise and neither does the notice: the record is free of the
      impossible, not free of the wrong
- [x] **2.7.6** **A test that would have caught this.** Nothing in 308 JVM
      tests could: the simulator is one well-behaved source and the defect
      needs two. A fake source that emits interleaved partial state, and an
      assertion that `SensorRepository` never publishes a reading whose fields
      came from different instants, is the shape of it

      25 of them, and the shape turned out to be slightly different: rather
      than faking two sources, the seam where three streams become one reading
      was **lifted out of the Android class into `TelemetryAssembler`**, where
      interleaved partial state is what every test feeds it. Plus the fence's
      bounds against the numbers the bike actually produced, and the watchdog
      on virtual time, so a six-second timeout costs nothing to assert. 346
      JVM tests, 0 failures

> **Verification note.** All of this was found with `adb` and one rider, in
> about fifteen minutes, because the **database is the neutral witness**: two
> screenshots taken 15 seconds apart cannot prove two surfaces disagree, but
> `workout_metrics` records what the recorder actually saw, once a second, with
> a timestamp. Reach for the table before the screenshots.

#### 2.7b What is actually happening — read this before 2.7.1

Established 1 August 2026 (tenth sitting) from `workout_metrics` alone, no
bike required. **The earlier description — "values rotate between fields" —
was true but too vague to act on, and the hypothesis attached to it (two live
registrations) is wrong.**

**It is a one-place shift in a stream of four values.** Here is the first real
ride, `HC-01`, at the second burst. The rider is holding 78 rpm, 37%, 80 W:

| t | cadence | resistance | power |
|---|---|---|---|
| 559 | 78 | 37 | 89.3 |
| **560** | **78** | **636** | **37** |
| **561** | **636** | **37** | **80** |
| 568 | 77 | 87.4 | **636** |
| 569 | 89.3 | **636** | 79 |

Read 560 and 561 side by side. The underlying sequence is `78, 636, 37, 80`,
and each recorded row is **three consecutive values from it, advancing by one**.
That is not rotation and it is not averaging. It is three fields being filled
from a stream that has one value too many in it.

**The extra value is the raw resistance reading.** Three sightings, two
different rides, one straight line:

| ride | resistance at the time | intruder | predicted by `11.13 × R + 229` |
|---|---|---|---|
| HC-01, t≈10–23 | 19% | 439 | 440.3 (0.3%) |
| free ride, overlay up | 33% | 602 | 596.1 (1.0%) |
| HC-01, t≈560–575 | 37% | 636 | 640.6 (0.7%) |

0% → 229, 100% → 1342: a potentiometer's ADC range. Heart rate is the only
rival with the right magnitude and it misses by 12%. **So the board reports
resistance twice — scaled and raw — and the raw one is entering the labelled
stream.**

Three consequences that change what to do:

- **It self-heals, which is why most of the ride is clean.** Our code files
  values by `msg.what` into three slots, so once the intruder stops, each slot
  is corrected within one cycle. 17 impossible samples out of 1196. The bursts
  are bursts because the intruder is intermittent, not because the alignment
  drifts back.
- **The 636 W spike on that ride's power chart is the intruder**, not a rider.
  And it is the one column where no bound can catch it: 636 W is a possible
  power, while 636 rpm and 636% are not. Across the knob's whole travel the
  raw value stays inside any power bound worth having.
- **Whatever mislabels is upstream of us.** We dispatch on `msg.what` and one
  `HandlerThread`; a positional shift cannot originate there. Either Peloton's
  service labels responses by position in a request queue and the unsolicited
  raw report desyncs it, or the raw report is being labelled as one of the
  three. Both are on the far side of the binder.

**Superseded by 2.7c, which has the answer.** The paragraph above guessed at
two possibilities; the bike settled it, and it is the second one — the service
labels by position and the raw report slides the labels along. Read 2.7c.

- [ ] **2.7.1a** ~~**An impossible value discredits its neighbours.**~~ **Done.**
      The fence alone was the wrong shape: it removed the intruder and left the
      values on either side of it, which are in range, wrong, and now the only
      thing in the record. `TelemetryAssembler` treats a rejection as evidence
      that the *labelling* is untrustworthy — it throws away everything stored
      and publishes nothing for four seconds, re-arming on each further
      sighting. The burst becomes a gap. 4 s bridges the longest gap between
      sightings inside either recorded burst, which was 3 s.

      **Its one limitation is a test rather than a hope**: detection cannot
      precede evidence, so the first reading of a burst can still get out
      before the intruder has been seen once.
- [x] **2.7.1b** **The experiment that identifies the mislabeller.** Done on
      the bike, 1 August 2026, and it needed no protocol change at all — the
      answer was sitting in the message bundle the whole time. See 2.7c
- [x] **2.7.1c** **Why the overlay makes it worse.** Answered: **the overlay
      is not the cause.** With one app on the sensor service, a 464-message
      capture with the overlay genuinely up and a rider pedalling recorded zero
      mislabels, zero raw frames and zero dropouts. What the overlay correlates
      with is *leaving the app* — and on this tablet the other things you leave
      it for include a second bike app. 2.7c has the mechanism

#### 2.7c The answer — the frame says what it is, and `msg.what` does not

Settled on the bike, 1 August 2026. **This is the root cause and the fix; 2.7b
is the correct description of the symptom and 2.7c is why.**

**Every reply carries the board's own frame.** `SensorService` puts the decoded
value in `data` and a `what` naming the metric — and it *also* passes
`responseHexString`, the untouched wire frame. Nobody had looked at it. It is
self-identifying:

```
F1 <id> <len> <len ASCII digits, least significant first> <checksum> F6

F1 49 03 33 33 30 D3 F6         0x49  resistance   "330" -> 33 %
F1 41 03 30 35 37 ...           0x41  cadence      whole rpm
F1 44 05 30 38 33 30 30 35 F6   0x44  power        tenths of a watt -> 38.0 W
F1 4A 04 33 34 35 30 0B F6      0x4A  RAW resistance -> 543
```

Checksum is the sum of every byte from `F1` to the last digit, mod 256.
`0x4A` is **the intruder of 2.7b, identified**: raw resistance, one digit
longer than the scaled reading beside it, `≈ 11.05 × resistance% + 233` across
five sightings.

**`msg.what` is assigned by position, not by content.** Provoked deliberately
— starting and stopping a second sensor app three times, rider stationary:

| | |
|---|---|
| Messages traced | 204 |
| **Payloads disagreeing with their own label** | **55** |
| Raw-resistance frames in the stream | 19 |
| Reported cadence, rider standing still | **544 rpm** |

A `0x49` resistance frame arrived under `what=8` (*power*). A `0x4A` raw frame
arrived under `what=9` (*resistance*). That is the one-place shift of 2.7b,
caught in the act, with the payload's own identity beside the wrong label.

**So the fix is to stop believing the label.** `PelotonFrameParser` decodes the
frame and *that* decides the metric; the label is only cross-checked and logged
when it disagrees. The raw-resistance frame is dropped **by identity rather
than by plausibility**, which is the whole difference between a fence and a
fix — the fence could never see it in the power column, and that was the 636 W
spike on the first real ride's chart.

**Verified on the bike after the fix**, one app, rider pedalling:

| Phase | Messages | Mislabels | Raw frames | Dropouts |
|-------|----------|-----------|------------|----------|
| Ride screen | 1609 | **0** | **0** | **0** |
| Overlay up | 464 | **0** | **0** | **0** |

and the ride it recorded: **200 samples, 0 impossible values, 0 gaps**,
resistance steady at 27–28%, `power_is_measured` true throughout, and a
spin-down that reads like a spin-down (21 → 19 → 17 → 15 → 12 rpm).

#### 2.7d The other defect underneath it — one serial port, and it leaks

Found in Peloton's own logs while chasing 2.7c, and it is the *silence* half of
2.7 rather than the corruption half:

```
E SerialServiceJNI:      could not open /dev/ttyO0
E SerialHandlerManager:  java.io.IOException: Could not open serial port /dev/ttyO0
    at android.hardware.SerialManager.openSerialPort
    at com.peloton.sensor.SensorService.onBind
```

**`SensorService` opens the exclusive UART inside `onBind`.** One port, one
open. Two bike apps therefore cannot both work — and worse, **the port leaks**:
after a second app was force-stopped, Pelonot sat dead on retry attempt **141**,
and force-stopping every client no longer released it. `SerialService` lives in
`system_server`, so the tablet had to be rebooted.

This explains the rest of the first real ride: *Telemetry stale at 86s*,
pedalling not reviving it, only an app restart helping — except an app restart
would not have helped either, if the port was gone.

Three consequences:

- **Each rebind reopens the port**, so our own retry loop is itself a source of
  the disturbance that mislabels the stream. That is now harmless (2.7c) but it
  is why the corruption came in bursts and why it self-healed.
- **The owner's position, recorded 1 August 2026:** he will never want another
  bike app running beside Pelonot, so *sharing* the board is explicitly not a
  goal. What matters is that Pelonot alone is correct, which 2.7c verifies.
- **But a leaked port is still a ride lost**, and the app used to say nothing
  useful about it — 2.7.7 and 2.7.8 are done, and what they leave is that the
  ride is still lost. The app now stops pretending otherwise after 65 seconds
  and names the remedy; it cannot recover the port, because nothing in
  userspace can.

- [x] **2.7.7** **Say what has actually happened when the board cannot be
      opened.** Right now a leaked serial port looks exactly like a bike that
      is switched off: the overlay shows `--`, the retry counter climbs
      forever, and nothing tells the rider that another app has the sensor or
      that the tablet needs restarting. `SerialHandlerManager`'s failure is
      visible in logcat and our bind still "succeeds", so this needs the
      distinction to be inferred — a bind that connects but never delivers,
      repeatedly, is a different condition from a board that is absent, and it
      has a different remedy. **Cap the retries and say so**, rather than
      backing off to 30 s forever. ***Done in the forty-sixth sitting***

      **The distinction the item asked to infer is written on the wire.** A
      service that cannot open the port still binds, still accepts the
      registration, and then answers **every poll with `TIME_OUT`** — so a
      steady stream of those means *the service is up and the board is not*,
      which is a claim, not a guess. A service that has died sends no reply at
      all, including no timeout. `SensorBoardNotAnswering` carries the first;
      the second falls out as `NeverStarted`, and no service to bind at all is
      `SensorServiceMissing`, which gives up on the **first** failure because
      waiting cannot change it.

      **What decides between the two schedules is not the exception, it is
      whether this bind ever delivered a reading.** That is one `var` in
      `SensorRepository.start()`, read by `ReconnectPolicy.onFailure` and
      cleared after. A productive attempt clears the whole run: a board that
      dropped out once and came back must not be met with a 30-second wait
      next time because a counter never reset — which the old
      `retryWhen`-attempt backoff did.

      **Giving up is the honest answer and 2.7d is the evidence for it.**
      Force-stopping every client did not release the port; `SerialService`
      lives in `system_server` and the tablet had to be rebooted. So a *Try
      again* control is deliberately **not** offered: it would invite the
      rider to reopen a port that has already refused, and the measured remedy
      is a reboot, which ends the process anyway.

      The chip says the remedy and nothing else says it: **"The bike's sensor
      isn't answering — not recording. Restarting the tablet usually frees
      it."** *Usually*, not *will* — what the app knows is that five binds
      produced nothing, not what is holding the port. **The overlay is
      deliberately left saying `NO SIGNAL`**: a remedy sentence over somebody's
      film is 24.1.5's family of rule, and the ride screen is one tap away.
- [x] **2.7.8** **Do not rebind more often than necessary.** Every rebind
      reopens the UART, and every reopen is a chance to lose it (2.7d). The
      silence watchdog is right to exist and should be *reluctant*: consider a
      longer first timeout, and never rebind while the last bind is still
      producing frames. ***Done in the forty-sixth sitting***

      **There were two silence detectors and the eager one always won.**
      `MAX_CONSECUTIVE_TIMEOUTS = 5` in `PelotonSensorServiceSource` counted
      *messages*, and with three repeating polls answering several times a
      second that is **under a second** of quiet — so the source tore itself
      down and rebound long before `SILENCE_TIMEOUT_MS` (6 s) could run, and
      that constant's own comment describes a patience the code never had. It
      is measured in time now, `BOARD_QUIET_TIMEOUT_MS = 4_000`, still shorter
      than the repository's watchdog on purpose, because this failure is the
      *informative* one and losing it would cost 2.7.7 its evidence.

      The second half — a longer first timeout — is the barren schedule: 3 s
      rather than 1 s before the first retry of a bind that produced nothing,
      which is precisely the case where trying again is what makes a leaked
      port permanent. **The cost is real and is written down rather than
      hidden**: on a 20-second dropout the gap in `workout_metrics` measured
      **17 seconds against the old schedule's ~7**, because the second attempt
      is now correctly treated as barren and waits 6 s. Two rebinds where
      there used to be four. That is the trade 2.7.8 asked for.

- [x] **2.7.9** **Giving up crashed the app, and it was found by driving it
      rather than by reading it.** ***Done in the forty-sixth sitting***, and
      it is the reason this pair is not a code-only change.
      `retryWhen` returning `false` **rethrows**, and an uncaught throw inside
      `scope.launch` takes the process down — a `SupervisorJob` isolates
      siblings, it does not handle. The old code returned `true` for ever, so
      the path never existed to be wrong. Measured on the first run of the new
      lever: `FATAL EXCEPTION: DefaultDispatcher-worker-4`, `Process
      com.pelonot has died`, and the ride the rider was on left for the crash
      recovery prompt to find — a *worse* outcome than the defect being fixed.
      A `.catch` after `retryWhen` is the fix; the status the policy already
      set is the report, and the exception has nowhere left to go.

      **The general shape is worth keeping**: every retry policy in this
      project was infinite, so no exception had ever reached the end of the
      pipeline. The first item to make one terminal inherits an error path
      nobody has walked.

#### 2.7a The repro, and the run that closed 2.7.3 and 2.7.4

The defect was found on a bike with a rider on it, which is not a thing to
spend on a regression. **Both halves of it are now levers on the emulator**,
beside the `COAST` receiver that 19.1.2a added for the same reason:

```bash
# The board reports nonsense — the signature of 2.7, values rotating between
# columns with a ghost near 602 in whichever one it lands in.
adb shell am broadcast -a com.pelonot.debug.CORRUPT \
  -n com.pelonot/com.pelonot.debug.DebugTelemetryReceiver --ei seconds 30

# The board goes quiet *without failing* — the part that survived a whole ride.
adb shell am broadcast -a com.pelonot.debug.SILENCE \
  -n com.pelonot/com.pelonot.debug.DebugTelemetryReceiver --ei seconds 20

# Every bind connects and delivers nothing — the leaked port of 2.7d, which is
# what 2.7.7 gives up on. Needs ~65s of failing binds to reach the giving up,
# so ask for longer than you think.
adb shell am broadcast -a com.pelonot.debug.DEAD_BOARD \
  -n com.pelonot/com.pelonot.debug.DebugTelemetryReceiver --ei seconds 180
```

**The run that closed 2.7.7 and 2.7.8, 13 August 2026, on the tablet AVD.**
Two rides, each with the other as its control.

| | Dead board | Dropout after real readings |
|---|---|---|
| Schedule | 3 s, 6 s, 12 s, 24 s, then stop | 1 s, then 6 s |
| Rebinds | 4 | 2 |
| Gave up | **yes, at 65 s** — `Giving up on simulated after 5 attempts (BoardNotAnswering)` | no |
| The chip said | *The bike's sensor isn't answering — not recording. Restarting the tablet usually frees it.* | *Simulated telemetry — no bike connected*, once it recovered |
| The record | 0 samples, and the summary says *"no second-by-second record"* rather than a row of zeros | 69 samples of 86 s, with a **17-second gap** exactly where the silence was |

The app stayed up through both — 0 `FATAL EXCEPTION` in logcat, against 1 on
the first attempt, which is 2.7.9.

The corruption is modelled on the measurement: the three real values rotate,
and **41 samples in 53 carry the ghost**, exactly as they did on the bike.

**The run, 1 August 2026, on the tablet AVD.** One 213-second free ride: clean
to 54 s, corrupt to 84 s, clean to 101 s, silent to 121 s, clean to the end.

| | Before | After |
|---|---|---|
| Impossible values recorded | 41 of 53 samples | **0 of 188** |
| Seconds recorded, of 213 | — | 188 (25 s of gaps) |
| Gaps | none — the lies were recorded instead | 57→61, 69→74, 83→85, **104→122** |
| Telemetry after a dead source | dead for the rest of the ride | *Telemetry live again at 122s* |

The four gaps are the whole result. Three small ones fall inside the corrupt
window, where readings were refused and the flow aged out; the seventeen-second
one is the silence, where `TelemetrySilence` fired at six seconds, the retry
policy rebuilt the source, and the ride picked up **on its own, without an app
restart** — which is the single thing 2.7.4 was about.

**Two honesties about that table.** The first is that `max cadence` on the
recorded ride is 173 rpm: a *power* value that landed in the cadence column
while pedalling hard, perfectly possible as a cadence, and no bound will ever
catch it. The plan said so — "33 and 52 swapping is invisible to any bound" —
and the run is the proof. That is what 2.7.1a's quarantine now answers.

The second is that this run exercised the fence, the watchdog and the recording
boundary, but **not `TelemetryAssembler`**, which only runs in the hardware
source. The emulator cannot produce a hardware ride, so the assembler is
covered by tests and by nothing else.

**The control run, and the invariant that made it worth doing.** The owner
believed the corruption had also been seen under *simulated* telemetry, which
if true would put the bug somewhere every ride goes through. It is testable
without a bike, because **a simulated ride carries its own checksum**: the
simulator derives power from the cadence and resistance it emits, so
`power == PowerModel.estimateWatts(cadence, resistance)` must hold for every
honestly recorded row, and any field swap breaks it.

A 278-second simulated ride with the overlay **genuinely raised** for 192 of
them — the earlier run had dismissed the overlay prompt, so it had not tested
this at all:

| Phase | Samples | `power != watts(cadence, resistance)` | Impossible values |
|-------|---------|---------------------------------------|-------------------|
| Ride screen | 87 | **0** | 0 |
| Overlay up | 192 | **0** | 0 |

**It does not reproduce off the bike.** Everything from the `StateFlow` to the
row in `workout_metrics` is clean under exactly the condition that triggers it
on hardware, which is what confines the defect to the multi-stream assembly in
`PelotonSensorServiceSource` — and to whatever labels those streams.

Two things worth knowing about what *can* look like the bug on the emulator,
because both were mistaken for it during this session:

- **The shipped power curve produces genuinely large numbers.** The simulated
  rider reaches 130 rpm, and the curve returns ~650 W there. Those are
  spikes, and they are not corruption; they are the curve being measurably
  wrong (RMSE 137 W, R² 0.21). Same family as everything in 2.2a.
- **`ride-simon` and `ride-alex` are hand-seeded fixtures** from the household
  leaderboard work — 1200 rows each at a fixed 175 W and 200 W. Every one of
  them fails the invariant above, by construction, because no simulator made
  them. Anything reading them as a real simulated ride will conclude the app is
  corrupting data.
