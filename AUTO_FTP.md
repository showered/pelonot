# Auto-FTP — exactly how it works

A technical reference, not a plan narrative. Written 4 August 2026 because the
owner has never seen it fire and wanted the exact mechanism for a UX review —
so this documents the code as it exists today, including the parts that never
run.

Core logic: [`PostWorkoutAnalyzer`](app/src/main/java/com/pelonot/data/service/PostWorkoutAnalyzer.kt).
Wired in: [`PostRideViewModel.load`](app/src/main/java/com/pelonot/ui/viewmodel/PostRideViewModel.kt).
Shown by: [`FtpBreakthroughDialog`](app/src/main/java/com/pelonot/ui/screen/FtpBreakthroughDialog.kt),
raised from [`PostRideSummaryScreen`](app/src/main/java/com/pelonot/ui/screen/PostRideSummaryScreen.kt).

## The one place it runs

`PostRideViewModel.load()` runs once, when the post-ride summary screen opens.
There is no background job, no notification, nothing that fires later. If the
rider never opens the summary for a ride — they swipe the app away, or the ride
gets picked up on another screen — no proposal is ever computed for it.

## The gate, in order

All of these must hold, checked in this order:

1. **`workout != null`** — the ride loaded.
2. **`currentFtp > 0`** — the rider has a profile with a nonzero FTP. A guest
   ride (no profile) can never propose.
3. **`!workout.ftpProposalDeclined`** — see *Decline*, below. This is a column
   on `workouts`, so it is **per ride, not per rider**: declining a proposal on
   one ride does not suppress the next ride's proposal.
4. **`PowerProvenance.isTrustworthyAsMeasured`** — the ride's power must be
   `Measured` **for every sample**. `PowerProvenance.of()` computed from the
   ride's own `power_is_measured` column:
   - Any `Modelled` sample (simulated telemetry) anywhere in the ride →
     disqualified.
   - **Even one `Unknown` sample** (recorded before the column existed, or a
     write path that didn't set it) disqualifies the *entire* ride, not just
     that sample — `Mixed` and `Unknown` are both refused. A ride that is 99%
     measured and 1% unknown gets no proposal.
   - This is why **the emulator can never produce a proposal**: simulated
     rides are always `Modelled`. You need Hardware mode telemetry — a real
     bike, or a database edited by hand (see *Forcing it*, below) — to see this
     fire at all.

If all four hold, `PostWorkoutAnalyzer.analyze()` runs.

## What `analyze()` actually computes

```kotlin
fun analyze(metrics, currentFtp, maxHr: Int?): AnalysisResult
```

**All three are passed in production, and none of them has a default (7.11.6).**
It used to take five parameters, of which three — `maxHr`, `rpe`, `isHardClass`
— defaulted at the one call site and were therefore dead. Two of those are gone
with the RPE path; `maxHr` is required, so the call site has to say what it
knows. See *The decision*, below, for what that leaves — the reference to a
*Dead paths* section this file has never had is itself corrected here.

One number is computed, and it is the only thing that can propose a change:

### 1. Twenty-minute peak (the only signal there is)

```
FTP ≈ (best 20-minute average power in the ride) × 0.95
```

- A true sliding window over `metrics` (power per second), O(n).
- **Requires the ride to be at least 20 minutes (1200 samples) long.** Shorter
  than that, this returns `null` outright — no partial-window guess.
- `0.95` is Coggan's standard 20-minute-to-FTP correction, a constant
  (`FTP_FROM_20_MIN`).

### 2. RPE-based bump — deleted (7.11.6)

There used to be a second signal here: `suggestFtpFromRpe` proposed
`currentFtp × 1.03` for a hard class the rider had rated easy, and `analyze`
fed it straight into `proposedFtp`. **It never fired once**, because the call
site never passed `rpe` — the parameter had a default. It is deleted rather
than wired up: 7.11.2 has since written down that RPE alone must not move a
rider's FTP, 7.11's replacement is a trend across several rides rather than a
per-ride check, and the rider answers *How did that feel?* on the same screen
that runs this analysis, so the rating is null at analysis time by
construction.

### The decision

```kotlin
isBreakthrough = fromPeak != null && fromPeak >= currentFtp × 1.02
```

So in practice: **`isBreakthrough` is true exactly when the ride is ≥20
minutes, every sample is measured power, and the 20-minute peak's 0.95-scaled
value is at least 2% above the rider's current FTP.**

`MIN_MEANINGFUL_GAIN = 1.02` exists to filter noise — a proposal 0.5% above
current FTP is inside the power model's own error band and would just be
annoying.

**Biometric decoupling is computed and unread — but it is no longer computed
from nothing (7.11.6).** `detectBiometricDecoupling(metrics, ftp, maxHr)` needs
`maxHr`, and for the project's whole history the one production call site never
passed it: the parameter defaulted to `null` and the function's first line is
`if (maxHr == null …) return false`, so `AnalysisResult.biometricDecoupling` was
always `false` rather than merely discarded. `maxHr` has no default now — a
signal that is optional at the call site is a signal nobody notices is missing —
and `PostRideViewModel` passes the rider's resolved maximum (21.1's nullable
gate: their own number, or Tanaka from the year of birth, or nothing). The
result still reaches no screen; it is 7.11's seed, recorded honestly.

**And decoupling as built only ever argues for raising FTP, never lowering
it.** It looks for *low* heart rate at threshold power — evidence the rider
found threshold effort easy, i.e. FTP is set too low. The opposite pattern is
**not** checked here at all, and since 7.11 it does not need to be: the
downward path is a separate object reading separate evidence. See *The downward
path* below.

## What the rider sees

If `hasBreakthrough` (i.e. `proposedFtp != null`), `PostRideSummaryScreen`
raises `FtpBreakthroughDialog` — a plain Material `AlertDialog`, unconditionally,
on top of the summary. Current copy, verbatim:

> **FTP Breakthrough!**
>
> New estimated FTP: 165W (current: 150W)
>
> Your fitness has improved! Update your FTP?
>
> `[Keep Current]` `[Update FTP]`

Worth flagging for the UX review directly: this is the **least designed
dialog in the app** by the same standard 20.3 just fixed elsewhere — a generic
`AlertDialog`, no explanation of *why* (no mention of the 20-minute effort that
triggered it, no "measured, not modelled" provenance mark despite that being
the whole gate), and "improved" stated as fact rather than as an estimate. It
predates 26.1's "less is more" pass and 20.3.4's rule that an estimate must say
where it came from — neither has reached this screen yet.

## Accept

`acceptFtpProposal()`:

```kotlin
userRepository.updateFtp(
    userId = profileId,
    ftpWatts = proposed,
    source = FtpChangeSource.AutoBreakthrough,
    workoutId = workout.id
)
```

Takes effect immediately — `currentFtp` in the UI state updates in the same
call, before the next screen. Written to `ftp_history` with `workoutId` set,
so *Your FTP* can link the change back to the ride that produced it (7.10.1).
`workout_id` is `ON DELETE SET NULL`, so deleting the ride later doesn't erase
the history entry — the training history isn't the ride's to take with it.

Can be reversed later from Settings / the FTP trend screen —
`revertFtpChange()` writes a new `ftp_history` row with
`source = AutoBreakthroughReverted` rather than editing the accepted row. Nothing
is ever overwritten; every change is an append.

## Decline

`declineFtpProposal()` sets `workouts.ftp_proposal_declined = true` on **that
ride** and clears the in-memory `proposedFtp`. Nothing is written to
`ftp_history` — a decline is not a change, so there is nothing to record there.
Because the flag lives on the ride, **the very next ride can propose again
immediately**, including at the same number, if it also clears the 2% bar.
There is no cooldown and no "don't ask me again."

## Why you've never seen it

Put together, every one of these has to be true simultaneously:

1. Hardware telemetry — a real ride, not the emulator's simulated source
   (`PowerModel` output is always `Modelled`).
2. The ride is at least 20 minutes long.
3. Every single sample in that ride has `power_is_measured = 1` — no dropout,
   no pre-existing `Unknown` samples.
4. The best 20-minute average, scaled by 0.95, beats your current FTP by 2%
   or more.
5. You open the post-ride summary screen for that specific ride (not a later
   one, not skip past it).

On a household bike with a handful of rides and an FTP that was set close to
right at signup, (4) alone is the rare event — you'd need an unusually strong
effort relative to your recorded number.

## Forcing it for testing

The emulator can't produce a measured ride at all, so this needs either the
real bike or a hand-edited database (per CLAUDE.md's `run-as` recipe):

```bash
sqlite3 db.sqlite "UPDATE workout_metrics SET power_is_measured = 1 WHERE workout_id = '<id>';"
sqlite3 db.sqlite "UPDATE profiles SET ftp_watts = <something well below the ride's 20-min peak × 0.95> WHERE local_user_id = <id>;"
```

Then reopen that ride's post-ride summary (or, if it's not the most recent
ride, there's currently no route back to a past ride's summary screen at all —
worth noting as its own gap if you want to re-trigger an old ride's dialog).
The ride also needs `ftp_proposal_declined = 0` and to be at least 1200 rows
in `workout_metrics`.

## The downward path (7.11)

Since 7.11 an FTP can go **down** by itself. It is a different object reading
different evidence, and deliberately not this analyser with its sign flipped.

Core logic: [`FtpReductionRule`](app/src/main/java/com/pelonot/domain/progress/FtpReduction.kt).
Wired in: `PostRideViewModel.load` → `WorkoutRepository.ftpReduction`.
Shown by: [`FtpReductionDialog`](app/src/main/java/com/pelonot/ui/screen/FtpReductionDialog.kt).

### Why not one signed check

A twenty-minute peak *is* direct evidence of what a rider can produce, so one
strong ride can raise an FTP with nothing interpreting it. The mirror is not
true: an off day, a cold coming on, poor sleep, heat, an unfamiliar class or
simple under-fuelling all produce a disappointing twenty minutes with fitness
untouched. And a peak *below* a rider's FTP is the ordinary result of a recovery
spin — it happens constantly and means nothing on its own.

### The gate, in order

1. **The rider has an FTP**, and there is no breakthrough on the table. The two
   cannot both be true of one rider's evidence, and the view model does not even
   compute this one when `proposedFtp` is non-null.
2. **The evidence window** starts at the later of the rider's last FTP change
   (`ftp_history`) and the last proposal they answered (the newest ride with
   `ftp_proposal_declined = 1`). Accepting, editing, or keeping all say *this
   number is right now*.
3. **Candidate rides** are this rider's finished rides with
   `power_provenance = 'Measured'` that have a stored twenty-minute effort in
   `workout_power_bests`, newest first, capped at `EVIDENCE_SCAN_LIMIT` (20).
   The stored effort is used rather than a scan of `workout_metrics`, because
   23.4 may have condensed the ride and the scan would return a wrong number
   rather than nothing.
4. **The rider must have been working** on each one — `riderWasWorking`:
   - heart rate known: `avg_hr >= 0.80 × workouts.max_hr_bpm`, **and** the
     rider's own answer, if given, is not *Comfortable*;
   - heart rate unknown: the answer is *Everything I had*, and nothing weaker.

   A ride with neither signal is **skipped**, not counted against.
5. **All of the newest `MIN_EVIDENCE_RIDES` (3)** working rides must come in at
   or below `currentFtp × 0.95`. One that beats it ends the run.

### The number

`max(peak₂₀ × 0.95)` over the three — the **best** the rider has actually ridden
in the window, rounded to a whole watt. Not the mean, not the latest, and not a
percentage step: whatever is offered has to be something they have done.

### What the rider sees

> **Your last 3 hard rides**
>
> All 3 came in under your FTP of 190 W, and you were working through every one
> of them. These are watts the bike measured, not an estimate.
>
> Aug 14, 2026 &nbsp;&nbsp; 171 W
> **Aug 11, 2026 &nbsp;&nbsp; 177 W**
> Aug 7, 2026 &nbsp;&nbsp; 167 W
>
> `[Keep 190 W]` `[Lower to 177 W]`

### Accept / keep

Accept writes `FtpChangeSource.AutoReduction`, with `workout_id` set to the
**strongest evidence ride** — not the ride that has just finished, which is
usually a different one. Keep writes `ftp_proposal_declined` on the current
ride, which is also step 2's cutoff, so the evidence restarts: three fresh hard
rides before the question can be asked again. **The upward path has no such
cooldown** (7.11.8).

### Forcing it for testing

Same problem as the breakthrough and one more: the emulator cannot produce a
measured ride, and this one needs three of them, each at least twenty minutes.
The recipe that worked on the tablet AVD is three rides inserted in `sqlite3`
with `power_is_measured = 1`, `avg_hr` above 80% of `max_hr_bpm`, and a
twenty-minute peak at or under 95% of the profile's FTP — then open *Your FTP*
once, which runs the backfill that computes `workout_power_bests` from the
samples, and then ride and end anything at all.


## Summary of things worth a UX pass

- The dialog itself: generic `AlertDialog`, no provenance mark, states
  "improved" as fact, doesn't explain the 20-minute-peak method.
- `biometricDecoupling` is computed from real inputs since 7.11.6 and still
  reaches no screen. It is no longer *7.11's seed* — 7.11 shipped without it,
  on a rule that reads stored twenty-minute efforts rather than one ride's
  samples — so the choice now is to wire it into the breakthrough copy ("we
  noticed your heart rate stayed low at threshold power — that's often a sign
  your FTP is set low") or to delete it.
- No cooldown on decline — a rider hovering right at the 2% line could see the
  same prompt ride after ride. **The downward path has one and this does not**,
  which is now an asymmetry rather than an omission; PLAN 7.11.8 is where that
  is written down.
- No messaging anywhere explains *why* the dialog never appears for a rider on
  Hardware mode who rides under 20 minutes, or on Simulated mode at all — a
  rider could reasonably conclude the feature is broken.
