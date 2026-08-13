> Part of the Pelonot plan — the index is [PLAN.md](../PLAN.md).

## Phase 8: Polish, Testing & Edge Cases

- [x] **8.1** Serial disconnection handled with a single backoff policy
- [x] **8.2** BLE disconnection handled without self-triggered reconnect loops
- [x] **8.3** Crash recovery via `is_complete`, surfaced through `WorkoutService.recoverableWorkout`
- [x] **8.3a** Recovery prompt shown at launch, driven from `AppViewModel` rather than the service. It offers to **keep** the ride, not resume it: the rider stopped pedalling when the app went away, and restarting the clock would splice a gap of unknown length into the record. `WorkoutAggregates` rebuilds the totals from the samples that did land.
- [x] **8.3b** **Crash recovery cannot tell a crashed ride from one that is
      running right now.** `getIncompleteWorkout()` is
      `SELECT * FROM workouts WHERE is_complete = 0 ORDER BY timestamp DESC
      LIMIT 1` and `clearRecoverableWorkouts()` is
      `DELETE FROM workouts WHERE is_complete = 0`. Neither excludes the ride
      in flight — and a ride in flight is `is_complete = 0` by design (1.12).
      `AppViewModel` runs the query in `init`, so **any** creation of
      `MainActivity` while a ride is recording raises the non-dismissible "You
      have an unfinished ride" dialog over the profile picker, mid-class, and
      *Discard* deletes the live row out from under the service — taking its
      metric series with it by cascade and leaving the next per-second insert to
      violate the foreign key that historically killed all recording (3.4). The
      trigger is not exotic: the rider starts a class, minimises to the strip,
      and forty minutes of Netflix on a tablet this size is ample reason for
      Android to destroy a backgrounded Activity. Same shape as the 8.3
      correction one layer down — that one returned the ride you had just
      finished, this one returns the ride you are still on.
      *Reproduced first, and it reads worse than it describes: the dialog says
      the app "was closed part-way through a ride" while the HUD strip two
      inches above it shows 02:11 and 66 rpm, live. The fix is `RideInProgress`,
      deliberately process-scoped rather than a column — "is a ride being
      recorded?" is a question about **this process**, and if it died then none
      is, whatever the table says, which is exactly the case this prompt exists
      for. Two instrumented tests, one of them for the trap in the SQL: written
      the obvious way as `id != :excludingId`, excluding nothing excludes
      everything, because `id != NULL` is never true and no ride could ever be
      recovered again. **Observed on the tablet AVD**: same repro — ride
      started, task swiped away, app reopened — now cold-starts with the ride
      still running and no dialog, and a genuine orphan (left behind by
      reinstalling over a live ride) is still offered*
- [x] **8.3c** **The ride summary was a dead end after a crash recovery.**
      Found by driving 8.3b's own repro one screen further. Force-stop
      mid-class, relaunch, answer *Keep it* — and the summary arrives with
      **both** buttons inert. *Discard* did nothing, *Keep as a guest ride* did
      nothing, and the only way off the screen was to kill the app, which
      leaves another unfinished ride behind it and starts the loop again.
      *One unread Boolean, which is the whole family this plan's Corrections
      table exists for. `popBackStack(Dashboard, inclusive = false)` returns
      **false** when Dashboard was never on the stack, and the recovery dialog
      navigates to the summary straight from "Who's riding?", where it never
      has been. **11.1a.5 hit this exact trap on the other door into a live
      ride** and closed it by pushing Dashboard underneath — its comment names
      the failure in so many words — and this door was simply missed. Answered
      by reading the Boolean rather than by faking a stack: at that point
      nobody has said who is riding, so the honest destination is the profile
      selector. **Observed on the tablet AVD**: recover, keep, and the summary
      returns to "Who's riding?"*
- [x] **8.3d** **Resume an interrupted ride, not merely keep it.** The owner,
      verbatim from the inbox: *"I recently had a crash (it's beng fixed right
      now in a worktree) but this made me think — in addition to just 'saving'
      an interrupted ride, we should be able to RESUME it."*
      **This contests 8.3a, which decided the opposite, so the reason 8.3a gave
      has to be answered rather than overruled.** It reads: *"It offers to keep
      the ride, not resume it: the rider stopped pedalling when the app went
      away, and restarting the clock would splice a gap of unknown length into
      the record."*
      **The objection does not survive contact with the code, in two separate
      ways.**
      - **The gap's length is not unknown; it is arithmetic.** `workouts
        .timestamp` is the wall-clock start (`startedAtEpochMs`), the last
        `workout_metrics.timestamp_sec` is the last second that recorded, and
        `System.currentTimeMillis()` is now. The break is
        `(now − timestamp)/1000 − lastSecond`, to within the sample period. An
        app that can measure a thing is not entitled to call it unknown.
      - **And the app already does exactly this, deliberately, for pauses.**
        `WorkoutService.elapsedSeconds()` subtracts `accumulatedPausedMs`, so
        `timestamp_sec` has never been *seconds since the ride started* — it is
        **seconds of actual riding**. A rider who pauses for five minutes leaves
        no hole in the series and nobody has ever thought that dishonest. **A
        crash is a pause that nobody got to press.** Resuming at the last
        recorded second is therefore not a new claim about the record; it is the
        claim the record has been making since Phase 3.
      That is what makes this safe to build, and it also fixes what to build:
      *Built and observed on the tablet AVD. A 20-minute class ridden to 02:37,
      force-stopped, relaunched, resumed — and then crashed and resumed a
      **second** time, because a ride that can be picked up once should survive
      being picked up twice. `Workout resumed: 1b5c8fb5 at 150s after 147s not
      riding`, then `at 270s after 347s not riding`, same workout id both times.
      Three defects came out of driving it that no amount of reading the diff
      had found; they are written up under 8.3d.4 because that is the item that
      predicted the shape of them.*
- [x] **8.3d.1** **The ride clock resumes at the last recorded second, not at
      wall-clock elapsed.** The alternative — advancing the clock by however
      long the app was dead — punishes the rider for a crash by running the
      class on without them: a rider who goes down at minute 5 and is back
      ninety seconds later would return to minute 6:30 of a class they have
      ridden five minutes of. **A class is a prescription of work, not an
      appointment**, and `ClassIntervalEngine` is a pure function of elapsed
      seconds, so resuming the clock resumes the intervals correctly for free.
      `durationSec` stays honest without special-casing because
      `WorkoutAggregates` rebuilds it from the samples that actually landed.
      *Observed. The ride came back at **02:41** on a clock that had read 02:37
      when the process died, still on **interval 2 of 13** with 01:19 of it
      left, and the second resume came back on interval 3. The totals came with
      it — 21.6 kJ, 0.2 mi, 135 W average — rather than restarting at zero.
      The check that matters most is in the database rather than on the screen:
      **`avg_power` on the row is 82.36 and `AVG(power)` over that ride's own
      `workout_metrics` is 82.36**, across a resume boundary, which is what
      proves `restoredWith` carries the running means at the sample counts they
      were built at. `avg_cadence` agrees to the same two places. That is the
      technique the `avg_hr` defect taught (CLAUDE.md), applied to the thing
      most likely to be wrong here.*
- [x] **8.3d.2** **The interruption is written down rather than smoothed over.**
      This is the part that keeps 8.3a's *concern* even though its conclusion
      goes. Because the series resumes contiguously (8.3d.1), a reader of
      `workout_metrics` afterwards cannot see that anything happened — and a
      45-minute ride that was actually ridden across two hours with a crash in
      the middle is a different ride from one ridden straight through, whatever
      the totals say. So the break becomes a fact on the row. `was_recovered`
      (12.5.5) will not do: it means *"rebuilt from its samples after a crash"*,
      which is the **keep** path, and a resumed ride is a third state — same
      family as `power_is_measured` being nullable and `target_position` being
      absent, where the honest design has always been that these are *different
      claims* rather than one flag doing two jobs.
      *Done as `workouts.resume_count` and `workouts.interrupted_sec`
      (migration 10 → 11), both `NOT NULL DEFAULT 0`. The contrast with 9 → 10
      is the reasoning and is worth keeping: `synced_at` was left null because a
      default would have **claimed** something untrue about rides nobody had
      checked, and zero here claims only what is certainly the case — no ride
      already on a tablet was ever resumed, because resuming did not exist. **A
      default is safe exactly when it states a fact rather than a guess.**
      Observed: `rc = 1, int_sec = 65` on a ride resumed once, and the migration
      ran clean over an existing database with real rides in it.*
- [x] **8.3d.3** **The prompt now asks a three-way question, and the wording is
      the hard part.** Today it is *keep* or *discard* (8.3a, 8.3b). It becomes
      *resume*, *keep*, *discard* — and the rider has to be able to tell the
      first two apart at a glance, on a tablet, having just had a crash. Three
      things to get right rather than assume: **resume is only offerable while
      resuming is meaningful** (a ride interrupted yesterday should not offer to
      pick the class back up, and the break length from 8.3d is exactly the
      number that decides it); **the interrupted ride's class, intent and FTP
      have to come back with it**, which is what `ActiveRide` already carries
      for the live case (11.1a.5) and what the `workouts` row carries for this
      one — note **`ftp_watts` must come from the row, not the profile**, or a
      breakthrough accepted in between silently rescores the ride (7.8); and
      **discard still must not be able to reach the live ride** (8.3b), which
      is `RideInProgress`' job and stays.
      *Done: **Discard · Keep it · Carry on riding**, and the prompt says how
      much was ridden — "You had ridden 02:30" — because that is the number the
      rider does not have in their head and it is what makes *carry on* a
      different offer from *keep it* rather than two words for one thing.
      Resume is withheld past `RideInterruption.MAX_RESUMABLE_BREAK_SEC`, where
      the original two answers come back.
      Two things worth recording. **The button order was wrong when first
      built** and only looked wrong on the device: an `AlertDialog` lays its
      dismiss slot to the **left** of its confirm slot, so putting keep beside
      discard left the one irreversible answer in the *middle*, between two safe
      ones and a thumb's width from the primary. Discard goes first now.
      And **"You had ridden 02:30" against a screen that read 02:37 is correct
      rather than a rounding slip** — metrics are written in batches of fifteen,
      so the last few seconds were still in memory when the process died. The
      dialog reports what reached the disk, which is the only thing it can
      honestly offer to give back.*
- [x] **8.3d.4** **Where it must not regress.** `WorkoutService.startWorkout`
      returns early unless the state is `Idle` and mints a fresh
      `UUID.randomUUID()`; resuming has to adopt an **existing** workout id
      instead, which means the row must not be re-inserted (`beginWorkout` on an
      existing id) and `RideInProgress.begin` must be told the resumed id so
      8.3b's exclusion keeps working — otherwise the app offers to recover the
      ride it has just resumed, which is 8.3b again by a new route. The
      per-second insert also has to continue past the highest existing
      `timestamp_sec` rather than restart at 1, or the primary-key/ordering
      assumptions in the chart code meet two samples claiming the same second.
      *All three held, and the series proves it: **332 samples, 332 distinct
      seconds, 1 to 332, no gaps and no duplicates** across two resumes. The
      ticker takes the highest second already written instead of starting at
      `-1`, `RideInProgress.begin` is told the adopted id, and the row is
      updated rather than re-inserted — deliberately not an upsert, because
      `workout_metrics` points at it and REPLACE is a delete plus an insert that
      fires foreign-key actions (the trap that has already cost this project
      three tables).*
      ***But the item did not predict the one that actually bit, and the shape
      of it is worth more than the fix.*** `stopWorkout` finalises a ride by
      building a **fresh** `WorkoutEntity` out of `WorkoutSession` — so every
      column the session does not carry is written back as its default. The
      resume was stamped on `workouts` correctly and then **overwritten with
      zero by the finalise twenty minutes later**, and a ride observed to resume
      twice sat on disk claiming it had been ridden straight through. Nothing on
      any screen was wrong; the ride looked perfect. It is exactly 7.10.3's
      defect — two writers, one row, the later one holding a stale copy of a
      field it does not know about — and it was found the way that one was, by
      building the feature that records the data and then **looking at the
      data**. The session carries both columns now, with a JVM test on it.
      The rule this leaves behind: **anything written to `workouts` during a
      ride must also be on `WorkoutSession`, or the finalise will quietly
      revert it.** `rpe_rating`, `ftp_proposal_declined` and `synced_at` are
      safe only because they are all written *after* the ride ends.*
- [x] **8.4** Guest post-ride: file against an existing profile, create one on the spot, keep as a household guest ride, or discard
- [x] **8.5** Haptic feedback for interval alerts — **and the `VIBRATE` permission it needs**
- [x] **8.6** TTS audio cues, with navigation-guidance audio attributes so the rider's video ducks under them
- [x] **8.6a** `RideCoach` wired into the ride, driven by the pure `RideCoachPolicy`. Replaces `ZoneAlertManager`, which had no caller and no decision logic to call it with.
- [x] **8.7** Unit tests: `PowerZone`, `PostWorkoutAnalyzer`, `WorkoutMetricsCalculator`, `RideIntent`, `SerialProtocolParser`, `CadenceTracker`, `PowerModel`, BLE parsing, `IntervalParser`, `ClassIntervalEngine`, `TargetBand`, `RideCoachPolicy`, `WorkoutAggregates`, `UnitSystem`, `Formatters`, `RideDayGrouping`, `WorkoutSession` — **192 tests**
- [x] **8.8** Instrumented tests for Room DAOs (foreign key ordering, `is_complete` filtering, cascade delete)
- [x] **8.8a** Instrumented test for `WorkoutService` lifecycle — start/pause/resume/stop, the workout row existing before its first metric, the batched tail being flushed, and a finished ride no longer being offered for recovery
- [ ] **8.8b** **`WorkoutServiceTest` is flaky, roughly one run in three.**
      `aFinishedRideIsNoLongerOfferedForRecovery` times out on *the ride to be
      finalised*, and when it does, the next test inherits a service still in
      `Completed` and fails too — so one flake reads as two. **Measured on both
      sides of the tenth sitting's changes** (base 1 failure in 4 runs, after
      2 in 4, always the same test and the same message), so it is not new and
      the fix is not in the sensor path. `stopWorkout` finalises inside
      `serviceScope.launch`, and when that does not land within 15 s, nothing
      says why; `stopSelf()` never runs, which is what leaks the state into the
      next test. Worth an hour: a flaky test on the one thing that guards the
      ride's own record is a test nobody will trust when it matters.

      **It did not reproduce in the forty-fourth sitting, and that is a
      measurement rather than a shrug.** Eight runs on the tablet AVD — four of
      `WorkoutServiceTest` alone and four of the whole instrumented suite,
      **112 tests, 0 failures** — against the recorded base of 1 failure in 4.
      The likeliest reason is already described in the test's own `setup()`
      comment: **2.4.6's preference race**, where reaching past
      `SettingsRepository` to call `setMode` directly raced `PelonotApp`'s
      collector and let the device's stored *Hardware* setting win, leaving two
      unrelated tests timing out with no telemetry. That fix landed after this
      item was written and it is exactly the shape of what was measured.

      **The box stays unticked**, because a flake that does not reproduce today
      is not a flake that is fixed, and eight runs against "one in three" is
      good evidence and not proof. What the item asks for is worth building if
      it returns and is worth nothing until then: `stopWorkout`'s finalise
      should **say why** when it does not land, and the state should not leak
      into the next test when it does not. **Do not re-run this as a first
      move** — it costs half an hour and has been answered once
- [x] **8.9** Manual testing on Gen 1 Peloton hardware — profile selector → dashboard → settings → Hardware telemetry → Just Ride → live board data → post-ride summary → persisted ride and 246 metric rows, 31 July 2026. Imperial units picked up from the device locale with no prompting (13.2), on the actual tablet this time
- [x] **8.12** Verified end-to-end on an emulator: profile creation → class library → intervals → simulated ride → post-ride summary → persisted metrics
- [x] **8.13** Verified on a 1920×1080 landscape tablet emulator, which is the shape of the device this actually runs on

### 8.11 Material Expressive design

- [x] **8.11.0** Inter variable font (SIL OFL)
- [x] **8.11.1–8.11.3** Colour tokens, light/dark schemes, fitness metric palette
- [x] **8.11.4–8.11.6** Elevation, shape and motion tokens — **deduplicated**; there were two conflicting elevation scales and a shape scale that shadowed itself with literal-named tokens
- [x] **8.11.7–8.11.11** Theme wired to `MaterialTheme.shapes` so stock components use it
- [x] **8.11.11a** Dynamic colour made opt-in — it previously overrode the entire palette on API 31+, making the brand theme dead code on any modern device
- [x] **8.11.13** Fade-through screen transitions
- [x] **8.11.17** Dashboard redesign
- [x] **8.11.27–8.11.29** Class library with filter chips and duration badges
- [x] **8.11.32/34** Class detail with zone-coloured interval cards
- [x] **8.11.37–8.11.38** Intent prompt with descriptions
- [x] **8.11.48/50** Post-ride summary cards and RPE selector (now a `FlowRow` — ten 48dp buttons in a `Row` overflowed every phone screen, making the higher ratings untappable)
- [x] **8.11.52** FTP breakthrough dialog
- [x] **8.11.65–8.11.67** Content descriptions, 48dp touch targets, semantic headings
- [x] **8.11.81** Shape as a semantic channel: the zone badge is a circle at Zone 1 and a twelve-point star at Zone 7, morphing between them on a change. Built on `androidx.graphics.shapes`, which is the mechanism behind Material 3 Expressive's shape language and works without moving to the material3 alpha.
- [x] **8.11.82** Off-target is amber, never red — power's own accent is coral, and a coral number turning red is not a signal anyone can read at a glance. The direction is also spelled out beside the label, so colour is never the only channel.
- [x] **8.11.83** Springy, physical motion: interval changes wash the HUD with the new zone's colour, cards overshoot and settle, the countdown re-bounces on every tick
- [ ] **8.11.12** Shared element transitions between profile selector and dashboard
- [ ] **8.11.14** Container transform for library → detail
- [ ] **8.11.15** Predictive back for Android 14+
- [ ] **8.11.16** Navigation rail or bottom bar
- [ ] **8.11.18** Large expressive FAB for Just Ride
- [ ] **8.11.21** Dashboard skeleton loading states
- [ ] **8.11.30–8.11.31** Class search bar, empty-state illustrations
- [ ] **8.11.33/35/36** Sticky class header, start-button loading state, difficulty indicator
- [ ] **8.11.39–8.11.41** Preparation checklist, sensor status, countdown
- [ ] **8.11.42–8.11.47** HUD redesign: progress arcs, blur pause overlay, expressive alerts
- [ ] **8.11.49/51** Achievement badges, share button
- [ ] **8.11.53–8.11.57** Charts: power with zone overlay, heart rate, cadence distribution, PB comparison
- [ ] **8.11.58–8.11.64** Extract the remaining shared components (`ZoneBadge`, `ProgressArc`, `SkeletonLoader`)
- [ ] **8.11.68–8.11.69** High contrast mode, font scaling
- [ ] **8.11.70–8.11.80** Micro-interactions, shimmer, pull-to-refresh, scroll edge effects

> The unticked items above are cosmetic and none of them are on the HUD. Phase
> 11 is worth more than all of them: the app runs a class now, and the question
> is whether the surface the rider actually spends forty minutes glancing at is
> good enough. Charts and shimmer are for the two minutes either side of that.
