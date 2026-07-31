# PowerModel calibration data

Measured `(cadence, resistance, power)` triples captured off the bike's own
sensor board, for fitting `PowerModel` (PLAN.md 2.2.4 / 2.2.5).

The bike reports watts directly (see PLAN.md 2.1a), so a Hardware-mode ride
*is* the dataset: every second of `workout_metrics` already holds the model's
two inputs alongside the measured output it is trying to predict. No
instrumentation is needed — only a rider willing to sweep the operating range.

## Capturing a sweep

1. Settings → Telemetry source → **Hardware**, then **Just Ride**.
2. Hold a resistance level and pedal ~15 s at each of several cadences.
   Repeat across the resistance range. Precision does not matter, because
   every second is recorded — **coverage does**.
3. Pull the ride and export it:

```bash
adb shell "run-as com.pelonot cat /data/data/com.pelonot/databases/pelonot_database" > db.sqlite
adb shell "run-as com.pelonot cat /data/data/com.pelonot/databases/pelonot_database-wal" > db.sqlite-wal
sqlite3 -csv db.sqlite "SELECT timestamp_sec,cadence,resistance,power,heart_rate FROM workout_metrics WHERE workout_id=(SELECT id FROM workouts ORDER BY rowid DESC LIMIT 1) ORDER BY timestamp_sec;"
```

Drop samples where the knob is mid-turn or cadence is lurching: those are
transitions between operating points, not operating points.

## What is here

| File | Bike | Date | Notes |
|------|------|------|-------|
| `2026-07-31-sweep-PLTN-RB1VQ.csv` | Gen 1 `PLTN-RB1VQ` | 31 Jul 2026 | 494 s, one rider. 310 steady-state samples after filtering. Resistance held near 0, 20, 27, 40, 54, 60, 75; cadence 30–101. |

## What this data established, and what it did not

**It settled that the shipped coefficients are wrong.** Against the 310
steady-state samples, `PowerModel` as it stands scores RMSE 137 W, median
absolute error 66%, R² 0.21.

**It did not produce replacement coefficients**, and the reason is worth
recording so the next attempt is not a repeat.

A refit of the form `P = (a + b·R^k)·rpm + c·rpm³` — monotone in resistance by
construction, so the `resistanceForWatts` inverse stays well-behaved — reaches
median 10.7% *in sample*. But holding out one resistance level at a time and
predicting it gives 13–25% on samples above 75 W, and **at R=40 the existing
coefficients beat the refit** (11.4% against 22.8%). A model that cannot
predict a level it did not see is interpolating between the six levels that
happened to get ridden, not describing the machine.

The unconstrained fit also extrapolates absurdly — `k ≈ 2.86` puts R=100 at
80 rpm near 1 kW — because nothing above R=75 was sampled.

**A sufficient sweep therefore needs:**

- More resistance levels, **especially 5–20 and 80–100**, which this sweep
  barely touched. There are effectively six distinct levels here and the
  exponent is poorly determined by that.
- Each level held at three or more cadences.
- High resistance at high cadence, which is the corner that is hardest to
  pedal and where the model currently disagrees with itself most.
- Ideally a second rider or a second bike, to separate the machine's curve
  from one rider's pedalling style.

Until then `PowerModel` stays as it is, uncalibrated and labelled as such.
Shipping a fit that fails cross-validation would look like progress and be
none — which is the failure mode PLAN.md's *Corrections* table exists to
prevent.

Note also that on real hardware none of this affects a recorded ride:
the board measures watts and `PowerModel` does not run
(`SensorReading.powerIsMeasured`). The model governs simulated rides and the
prescribed resistance band in 11.2.1.
