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
fun analyze(metrics, currentFtp, maxHr = null, rpe = null, isHardClass = false): AnalysisResult
```

**Only `metrics` and `currentFtp` are ever passed in production.** `maxHr`,
`rpe` and `isHardClass` all default to null/null/false at the one call site.
That matters — see *Dead paths*, below.

Two candidate numbers are computed, and the higher one wins:

### 1. Twenty-minute peak (the only one that ever fires)

```
FTP ≈ (best 20-minute average power in the ride) × 0.95
```

- A true sliding window over `metrics` (power per second), O(n).
- **Requires the ride to be at least 20 minutes (1200 samples) long.** Shorter
  than that, this returns `null` outright — no partial-window guess.
- `0.95` is Coggan's standard 20-minute-to-FTP correction, a constant
  (`FTP_FROM_20_MIN`).

### 2. RPE-based bump (dead in production — see below)

```
if rpe != null && isHardClass && rpe <= 4:
    proposal = currentFtp × 1.03
```

Since the call site never passes `rpe` or `isHardClass`, `rpe` is always
`null` and this branch always returns `null`. **It cannot fire today.**

### The decision

```kotlin
proposal = max(fromPeak, fromRpe)  // fromRpe is always null in practice
isBreakthrough = proposal != null && proposal >= currentFtp × 1.02
```

So in practice: **`isBreakthrough` is true exactly when the ride is ≥20
minutes, every sample is measured power, and the 20-minute peak's 0.95-scaled
value is at least 2% above the rider's current FTP.**

`MIN_MEANINGFUL_GAIN = 1.02` exists to filter noise — a proposal 0.5% above
current FTP is inside the power model's own error band and would just be
annoying.

**Biometric decoupling is a complete no-op, not just unread.**
`detectBiometricDecoupling(metrics, ftp, maxHr)` needs `maxHr`, and the one
production call site never passes it either — same gap as `rpe` and
`isHardClass`. `maxHr` defaults to `null`, and the function's first line is
`if (maxHr == null …) return false`. So `AnalysisResult.biometricDecoupling`
is not merely discarded downstream — it is always `false`, computed from
nothing. Three of `analyze()`'s five parameters (`maxHr`, `rpe`,
`isHardClass`) are dead at the call site; only `metrics` and `currentFtp` ever
carry real data.

**And decoupling as built only ever argues for raising FTP, never lowering
it.** It looks for *low* heart rate at threshold power — evidence the rider
found threshold effort easy, i.e. FTP is set too low. There is no equivalent
check for the opposite pattern (elevated heart rate, or high RPE, at an output
that isn't improving) — see [PLAN 7.11](plan/phase-07-ftp.md) for why nothing
today can move FTP down at all.

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

## Summary of things worth a UX pass

- The dialog itself: generic `AlertDialog`, no provenance mark, states
  "improved" as fact, doesn't explain the 20-minute-peak method.
- `biometricDecoupling` is computed and silently discarded — either wire it
  into the copy ("we noticed your heart rate stayed low at threshold power —
  that's often a sign your FTP is set low") or remove the computation.
- The RPE path (`suggestFtpFromRpe`) is fully dead code in production — never
  called with real arguments. Either wire it in (it would let a hard-feeling
  class nudge FTP up by 3% even under 20 minutes) or it's confusing to keep
  maintaining/testing a path nothing reaches.
- No cooldown on decline — a rider hovering right at the 2% line could see the
  same prompt ride after ride. Worth deciding if that's desired (7.10.5 already
  covers the "asked too often becomes a reflex tap" risk for the *accept* side,
  but says nothing about repeat prompts across rides).
- No messaging anywhere explains *why* the dialog never appears for a rider on
  Hardware mode who rides under 20 minutes, or on Simulated mode at all — a
  rider could reasonably conclude the feature is broken.
