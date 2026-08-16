> Part of the Pelonot plan — the index is [PLAN.md](../PLAN.md).

## Phase 7: Auto-FTP Engine, Workload JSON & Cloud Sync

- [x] **7.1** `PostWorkoutAnalyzer` — 20-min peak (O(n) sliding window over full-length windows only), biometric decoupling, RPE survey
- [x] **7.2** `FtpBreakthroughDialog`
- [x] **7.3** FTP update flow writing through to the profile
- [x] **7.4** `WorkoutSyncWorker` with a network constraint, retry ceiling, unique work, and a result it actually reads
- [x] **7.5** `ClassTemplateSeeder` listing the assets directory rather than a hardcoded category list
- [x] **7.6** Class template JSON in `assets/classes/`
- [x] **7.7** Seeding moved to application scope (a `LaunchedEffect` was cancelled by navigation mid-seed)

### 7.8 The FTP a ride was ridden at — the bug underneath everything else

**`profiles` holds one `ftp_watts` and `workouts` holds none.** The ride start
passes an FTP into `WorkoutService` (9.1.1), the ride is judged against it live,
and then it is thrown away. Every screen that needs a past ride's FTP therefore
reads the rider's *current* one — `RideDetailViewModel` fetches
`getUser(...).ftpWatts` and hands it to the power chart, which draws the zone
bands and the dashed FTP rule from it.

So an FTP change silently rewrites history: a ride ridden in Zone 5 in January
is redrawn as Zone 4 the moment the rider's FTP goes up in March, and the chart
gives no hint that anything changed. **Auto-FTP (7.1–7.3) makes this fire by
itself**, off a ride the rider only agreed to a dialog about — which is the
difference between a stale reading and a record that edits itself. It is the
same family as the `avg_*` defect in CLAUDE.md: a number derived on read, from
a source that has moved since.

- [x] **7.8.1** `workouts.ftp_watts`, written when the ride is created, with the
      value the ride was actually judged against. A `Migration`, an exported
      schema in `app/schemas/` and a `MigrationTestHelper` test (12.5), and it
      belongs in the same migration as the other columns 12.5.4 is waiting on.
      *Migration 6→7, schema `7.json`, and `migrate6To7_…` in `MigrationTest`.
      Written in `WorkoutService.toEntity()`, which runs at **insert** — that
      is, at the start of the ride — so it is the number the ride was judged
      against and not one reconstructed afterwards. Observed on the AVD: a ride
      started fresh wrote `ftp_watts=230` beside `intent_modifier=1.05`*
- [x] **7.8.2** **Nullable, and null means unknown** — do not backfill existing
      rows with the profile's current FTP, which would bake today's guess into
      the record permanently and look exactly like real data afterwards.
      *The migration test asserts the null rather than the column, because that
      is the whole of this item*
- [x] **7.8.3** Every read site uses the ride's own value and falls back to the
      profile's only when it is null: the power chart's zone bands and FTP rule
      (16.1.1), any time-in-zone summary (16.1.4, 11.3.3), and `leaderboardFor`
      if it ever compares zones rather than raw watts. The fallback is today's
      behaviour, so old rides are no worse than they are now. *One read site
      turned out to feed all of them — `RideDetailViewModel.buildCharts` hands
      one FTP to `RideChartBuilder`, which is where the bands, the FTP rule,
      the prescription and the time-in-zone all come from*
- [x] **7.8.4** Where the fallback is in use, the screen says so rather than
      drawing bands that look as authoritative as the real ones.
      *`RideCharts.ftpIsTheRides` carries it out of the builder; the power card
      says "zones from your FTP today — this ride did not record its own".
      Observed against a seeded ride with a null column, and absent on one with
      230 beside it*
- [x] **7.8.5** ~~A guest ride has no profile and so no FTP at all. It gets no
      zone bands rather than the last-selected rider's.~~ **Done differently,
      because the premise is not true.** A guest ride *does* have an FTP — the
      app's default — and it is not the last-selected rider's: it is what the
      ride's live target gauge, resistance band and zone ladder were all built
      from while the guest was pedalling. So the number on the row is a true
      record of what the ride was judged against, and 7.8.1 writes it for a
      guest like any other ride.

      What a guest ride lacks is a **rider**, and "Zone 5" is a claim about
      somebody. So the card says *"no rider on this ride — zones from the app's
      default FTP"* rather than withdrawing the bands. Withdrawing them was the
      original instruction and it costs more than it saves: `ftpWatts` is the
      single input to the prescription blocks and the time-in-zone bar as well,
      so a guest would lose the record of what the class asked of them — and
      the ride's own screen would then disagree with what the guest was looking
      at while they rode it

### 7.9 FTP history — the progress measure the app already has and discards

FTP is the one number in this app that is genuinely a fitness measure rather
than a volume measure, and the app already recomputes it for free after every
ride. It keeps only the latest value. **16.3.1** ("FTP over time, marked with
the rides that triggered each change") and **22.1.4** both assume a history
exists; nothing creates one. The previous value is overwritten and gone.

- [x] **7.9.1** An `ftp_history` table: profile, watts, when, **how it changed**
      and, where there was one, the workout that caused it. A derived history is
      not available — the old value is destroyed on update — so this has to be
      recorded at the moment of the change or it does not exist. *Migration 7→8,
      schema `8.json`*
- [x] **7.9.2** The *how* is a typed enum, not a string: profile creation,
      manual edit in Settings, accepted auto-breakthrough, guided FTP test
      (19.2.3), pulled from another device (15.3). `RideIntent` is the
      precedent — 5.8 made exactly this a typed enum after a bare display string
      let a typo silently defeat it. The distinction matters on the chart: an
      FTP the rider typed is a claim, one the app measured is evidence.
      *`FtpChangeSource`, stored by name, with `Unknown` as a real case rather
      than a defensive branch*
- [x] **7.9.3** The workout reference is nullable and `ON DELETE SET NULL`.
      Deleting a ride (12.3) must not delete the fact that the rider's FTP
      changed — the training history is not the ride's to take with it.
      *Checked against the database rather than reasoned about, along with its
      counterpart: the **profile** reference is `CASCADE`, because unlike a ride
      — a record of something that happened — an FTP history is a statement
      about somebody and means nothing without them*
- [x] **7.9.4** **One funnel.** Every path that changes FTP goes through a single
      repository method that writes the profile and the history row in one
      transaction. There are already four call sites and 15 and 19.2.3 add more;
      a history that depends on each new path remembering to append to it is a
      history that will be wrong within two features. *It is
      `UserRepository.save`, not `updateFtp` — every path already ends up there,
      including profile creation and a guest keeping their ride. The consequence
      worth knowing: **a caller that changes FTP without naming a reason still
      gets a row**, marked `Unknown`. Losing the reason is survivable; losing the
      change is not, because it cannot be recovered afterwards. There is a test
      of exactly that shape*
- [x] **7.9.5** A change to the same value is not a change. Do not record a row
      when the number has not moved, or a re-save in Settings or an idempotent
      cloud pull will fill the trend chart with vertical noise. *Observed on the
      AVD: pressing Save twice on 215 adds one row*
- [x] **7.9.6** Seed the first row from the existing profile at migration time,
      dated to the profile's `created_at` and marked as unknown-origin. Without
      it every existing rider's chart starts at their second FTP change.
      *Marked `Unknown` rather than `ProfileCreated` on purpose: a profile whose
      FTP has been edited four times since is not described by either, and the
      enum has a case for "nobody wrote it down"*
- [x] **7.9.7** Room migration, exported schema and a `MigrationTestHelper` test
      (12.5), with the seeding in 7.9.6 covered by it — a data-moving migration

> **Building this found a bug older than it, in the busiest FTP path there is.**
> Settings fired two coroutines off one tap of Save — one for FTP, one for
> weight — each doing read-modify-write on the same profile row. The weight
> write read the profile *before* the FTP write committed and carried the old
> FTP back past it, so **typing a new FTP and pressing Save left the old number
> in the database**, with the screen still showing the new one until the next
> launch. Nothing on any screen was wrong, which is why it survived.
>
> What found it was two `ManualEdit` rows for the same value twenty-three
> seconds apart — which can only happen if the number went back in between. The
> same two techniques as 24.2's find: **build the feature that reads the data,
> then look at the data**, and **the database is the witness, not the
> screenshots**. FTP and weight are one write now, and the test asserts the
> property rather than the annotation.

### 7.10 Showing it, and being honest about it

- [x] **7.10.1** 16.3.1 is now buildable: FTP over time, stepped rather than
      interpolated — FTP does not drift smoothly between two rides, it changes
      on a day — with each change marked by what caused it (7.9.2) and tappable
      through to the ride that triggered it. *Done and observed.* The screen it
      wanted is `FtpProgressScreen`, reached from the dashboard's FTP card,
      which now carries "How it changed" and a chevron — **not "History"**,
      which is already a card on the same screen and means the rider's rides.
      Three decisions in it:
      - **A mark per change says how the app came to believe it.** Filled where
        the app measured it off a ride, hollow where the rider typed it — the
        distinction `FtpChangeSource`'s own documentation opens with, drawn
        rather than described. `PulledFromCloud` is hollow too: another
        device's arithmetic is not this bike's measurement
      - **The first value gets no mark and no row.** It is where the number
        began, not somewhere it moved to, and `FtpTrend.changes` is one shorter
        than `points` for exactly that reason
      - **The axis runs to *now*, not to the last change.** Stopping on the day
        of the most recent change says the record ends there; the flat run out
        to the right-hand edge is the rider's answer to "how long have I been
        at this". It also stops a change made this morning being drawn half off
        the plot, which reads as data continuing off the chart. `spanToNow`
        takes the clock as an argument so it stays pure — and a device whose
        time has moved backwards must not mirror the chart, which is a test

      *Observed on the tablet AVD against a hand-seeded four-value history —
      200 → 212 (measured, off `ride-simon`) → 205 → 215 — with the marks
      filled and hollow correctly, the tap on "+12 · measured from a ride"
      landing on `Standing Attacks 20`'s ride detail, and Kilo, whose FTP has
      never moved, getting a flat line, "It has not moved since" and no list. A
      guest's card is not a door rather than a door onto an empty room*
- [x] **7.10.2** On the dashboard (22.1.4): current FTP, when it last changed,
      and the direction. This is the progress line the section is missing.
      *Done, with a stepped sparkline beside it. `FtpTrend` is pure and holds
      the rules: the direction is measured against the **previous** value rather
      than the lowest, so 200 → 240 → 225 is a fall of 15 and not a rise of 25;
      a fall is shown, because a progress card that could only go up would be
      lying by omission; and the range is padded, so a five-watt move is not
      drawn as a cliff filling the box. Observed on the AVD both ways — Simon
      with "+15 W since Aug 2, 2026 · you set it", and Kilo, whose FTP has never
      moved, with nothing but the number*
- [x] **7.10.3** In Settings, beside the editable field: what it is now and when
      it last moved, so a rider who does not remember agreeing to a change can
      see the app made it. *"Last changed Aug 2, 2026 · up from 200 W · you set
      it", observed on the AVD. Silent until the number has actually moved: a
      rider's first row is the value their profile started with, and reporting
      that as a change would be announcing an event that never happened*
- [x] **7.10.4** **An auto-FTP change is the app editing the rider's own
      record**, so it stays visible and reversible: the history says the app did
      it, off which ride, and reverting to the previous value is one action and
      appends a row rather than erasing one.
      *Done and observed*, on the screen 7.10.1 built. Three decisions:
      - **It appends.** Deleting the row would be a second edit covering the
        first, leaving a history that says nothing ever happened — the state 7.9
        exists to make impossible
      - **`AutoBreakthroughReverted` is its own source**, not `ManualEdit`. Both
        are claims rather than evidence, but "I set this myself" and "the app
        moved my FTP and I disagreed" are different events, and only the second
        one says the app was wrong about something. It carries no `workoutId`:
        the ride caused the change being undone, not this one
      - **Offered on the newest change only, and only when the app made it.** An
        undo three moves back would have to decide what the two moves after it
        now mean; on the newest, "put it back" has exactly one meaning. A value
        the rider typed needs no undo from the app — they can type another

      *Observed: accepting a seeded 1,300-second measured ride's proposal wrote
      247 W with an `AutoBreakthrough` row against `bt-ride`, and "Put back
      215 W" appended `215 | AutoBreakthroughReverted` with the auto change
      still listed above it.*
- [x] **7.10.5** A declined breakthrough should not be re-offered for the same
      ride. `PostRideViewModel` runs the analyser on load, so a rider who
      declines and re-opens the summary is asked again about a ride they have
      already answered for.
      *Done and observed.* Asked often enough, "no" stops being a decision and
      becomes a thing to tap past — and the button beside it commits a permanent
      change to the rider's record. The answer is
      `workouts.ftp_proposal_declined` (migration 8→9) rather than a preference,
      because it is **a fact about a ride**: it travels in the backup and it
      goes away when the ride does. `NOT NULL DEFAULT 0`, unlike
      `power_is_measured` — "never asked" and "asked and said no" make no
      different claim here, and both behave the same way. The migration test
      asserts the safe direction: a ride recorded before the column existed has
      **not** been declined, so a genuine breakthrough sitting in an old ride is
      still offered.
      *Observed on the AVD: the summary offering 247 W against 215; declining
      leaving the FTP at 215 and writing the column; and the same ride reopening
      straight to the RPE prompt with no dialog.*
- [ ] **7.10.6** **The honesty caveat — narrower than it first looked, and
      calibration is not part of it.** An FTP trend is only a fitness trend if
      the watts behind it are comparable over time. On the bike they are
      measured off the board (2.1a) and nothing in the app can move them:
      `PowerModel` does not run during a hardware ride, so per-bike calibration
      (2.2a) cannot shift an FTP by a single watt. That concern was raised here
      and is withdrawn — see the decision block at the head of 2.2a. What
      remains is real but simple: a **simulated** ride's watts are fiction, so
      mark on the chart which values came from measured rides. Partly blocked on
      16.1.6, since `powerIsMeasured` is still discarded at the database
      boundary
- [x] **7.10.7** `PostRideViewModel.load` runs `PostWorkoutAnalyzer` only for a
      ride whose provenance is `Measured` all the way through. A simulated ride
      can no longer offer a breakthrough computed from numbers the app
      invented, and 7.9 will therefore never keep one as evidence. `Mixed`
      fails the same test on purpose: half a ride of invented watts still moves
      a twenty-minute peak, and the rider cannot see which half
- [ ] **7.10.8** Decide whether `ftp_history` syncs (14, 15). It is small,
      per-profile and the thing a rider would most miss on a new device, but it
      is also a fitness record about a person and 17.7's private-by-default rule
      applies to it before any of it leaves the tablet

### 7.11 Auto-FTP can only ever go up — the owner's question, 4 August 2026

**Verbatim:** *"Can it go down? It should go down. If your BPM is unusually
high and/or you mark a workout as 'really difficult' and despite this your
scores are going down, it should probably adjust downwards too?"*

**Checked against the code rather than assumed, and the answer is no —
nothing anywhere moves FTP down.** `PostWorkoutAnalyzer.analyze`'s gate is
`proposal >= currentFtp × MIN_MEANINGFUL_GAIN` (1.02), which is `>=` a number
*above* current FTP by construction: a computed peak *below* current FTP
simply fails the gate and produces no proposal, not a downward one.
`FtpChangeSource` has no case for an automatic decrease — `AutoBreakthrough`,
`GuidedTest`, `Estimated`, `ManualEdit`, `AutoBreakthroughReverted`,
`ProfileCreated`, `PulledFromCloud`, `Unknown`, and every one of them is
either upward or rider-initiated. See [AUTO_FTP.md](../AUTO_FTP.md) for the
full mechanism this sits beside.

**And it is closer to built than it looks — the wrong direction, but the
right shape.** `detectBiometricDecoupling(metrics, ftp, maxHr)` already
exists and already asks almost the owner's question, just facing the other
way: it looks for **low** heart rate at threshold power, which is evidence
FTP is set **too low**. The owner is asking for its mirror — elevated heart
rate, or a high RPE, at an output that is not improving, as evidence FTP is
set **too high**. Today neither direction actually runs: `maxHr` is one of
three parameters (`maxHr`, `rpe`, `isHardClass`) that the one production call
site never passes, so `detectBiometricDecoupling` returns `false` before
touching its own logic and its result is discarded even when it ran with
faked arguments.

**Why this is a real gap and not a symmetric oversight.** 20.3.2 leaned on
exactly the asymmetry the owner is now asking to remove: an estimated FTP
that starts *too low* self-corrects, because the first strong ride clears it.
An estimated FTP that starts *too high* is **permanent**, because nothing
lowers it. That argument is only safe *as long as it stays true* — the moment
7.11 ships a downward path, 20.3.2's estimator no longer needs its low bias
for the same reason, and the two items should be read together whenever
either is touched again.

**Why a single ride cannot be the trigger, unlike 7.10.7's ride-at-a-time
check.** The upward proposal is safe to fire off one ride because a
20-minute peak *is* direct evidence of what the rider can produce — there is
no interpretation between the number and the claim. A single hard-feeling
ride is not the same kind of evidence: an off day, a cold coming on, poor
sleep, heat, an unfamiliar class template, or simple under-fuelling can all
produce "high heart rate, high RPE, disappointing watts" with fitness
untouched. Lowering a rider's FTP — and therefore every zone on the ride
screen, the overlay and the resistance band — off one bad ride would be a
worse failure than the one 20.3.2 was written to avoid, because it edits a
number the rider is actively training against rather than merely starting
from. **This has to be a trend across several rides, not a per-ride check**,
which is a materially different computation from anything `PostWorkoutAnalyzer`
does today — it operates on one ride's `metrics` and returns.

- [x] **7.11.1** ***Done, and the shape it took is not the one this item
      sketched.*** Decide the evidence window and the bar. Candidate shape:
      over the last *N* rides at Zone 4 or above (say, the last 5–8 such
      rides, not a calendar window — a rider who rides twice a week needs
      weeks of calendar time to accumulate the same evidence as one who rides
      daily), heart rate at a given power has drifted up by some margin, *or*
      self-reported RPE has been consistently high, while the power those
      efforts produced has not risen to match. None of this is decidable from
      first principles — it needs the same kind of measurement 2.2a's
      calibration work did before it shipped, not a guessed threshold

      **`FtpReductionRule` is what was built, and the thing that made it
      buildable is that the number it offers needs no new arithmetic at all.**
      This item was written expecting a new estimator — *heart rate at a given
      power has drifted up by some margin* — and every such margin is exactly
      the guess it warns against. The rule inverts that. **The measurement is
      the one the upward path already makes**, `P₂₀ × 0.95` on measured watts,
      and what 7.11 adds is a rule about *when it is allowed to be believed*.
      So a downward proposal is never a modelled number, never a percentage
      step, and always something the rider has demonstrably ridden in the last
      few weeks: it is the **best** twenty-minute effort across the window,
      not the mean and not the latest.

      **Three rides, all short, all worked at.** A ride only speaks when
      something says the rider was trying — the owner's own two-signal
      sentence — and one ride that met the number ends it. Rides in between at
      which the rider was *not* working are **skipped rather than counted
      against**: an easy spin is silent, not counter-evidence, and treating it
      as either is wrong. The count is three where this item guessed five to
      eight, and the reduction is not a relaxation: each of the three has
      already had to be a measured twenty-minute effort at or above 80% of the
      rider's maximum heart rate coming in more than 5% short, where the
      candidate shape counted rides that had passed a far weaker test.

      **Nothing had to be stored, which is the other reason this landed.**
      `workout_power_bests` has held each measured ride's twenty minutes since
      16.3.3a, computed at finalise — so the evidence was already on disk, in
      the one form 23.4's trimmer cannot falsify. Recomputing it from
      `workout_metrics` is precisely what 23.4.2 forbids: a condensed ride has
      real rows in that table and the scan would return a number.

      **What is still a guess is named rather than hidden**, and it is one
      number: `MIN_MEANINGFUL_LOSS`, 5% against the upward path's 2%. See
      **7.11.7** for what would settle it
- [x] **7.11.2** ***Done.*** RPE alone should not be enough. A rider who rates
      every ride 9/10 is describing their effort, not their fitness, and
      26.3.3 already settled that RPE is a coarse three-answer scale for
      exactly this reason — it is corroborating evidence for a power/heart-rate
      trend, not a trigger by itself. **This inverts 27's own gate**: alerts
      (Phase 27) require `PowerProvenance.Measured` before anything can be
      shown as a *record*; a downward FTP proposal is the same kind of claim
      about a rider's body and should be held to the same bar — no
      Simulated-mode ride may contribute evidence either way.

      **Both halves hold in the built rule and the second is enforced twice.**
      `w.power_provenance = 'Measured'` is on the evidence query, and the join
      to `workout_power_bests` is a second gate saying the same thing from the
      other side — those rows are only ever written for a measured ride, so
      *the existence of a row is itself the claim* (23.4.12). The RPE half is
      structural rather than a threshold: a rating is **permission to read a
      shortfall, never the shortfall itself**, so the ceiling on what a serial
      *Everything I had* rater can achieve is nothing — the watts still have to
      be short. And where a heart rate exists the measurement leads and the
      rating can only *veto*: a rider who says *Comfortable* discards the ride
      however high the trace went
- [x] **7.11.3** ***Done, and the design question it hands to 7.11.1 is
      answered.*** Requires `maxHr` to be known at all — the same nullable gate
      21.1 already respects (`max_hr_bpm` or `birth_date`, both optional). A
      rider with neither gets no heart-rate-based signal, same as they get no
      heart-rate zones today; the RPE-and-declining-power half could in
      principle stand alone, which is a design question for 7.11.1 to settle
      rather than assume.

      **It stands alone, and only at the top of the scale.** A rider with no
      maximum and no strap can still be asked, on *Everything I had* and
      nothing weaker — which is the owner's *"really difficult"* word for word.
      With a heart rate, *A good workout* is enough, because the measurement is
      carrying the claim and the rating is only being asked not to contradict
      it. A ride with neither signal is silent.

      **And the maximum comes off the ride, not the rider** (21.4.2a).
      `workouts.max_hr_bpm` is what that ride's zones were judged against, so a
      rider who measures a real maximum in September does not silently re-read
      August's evidence against it
- [x] **7.11.4** ***Done and watched on the tablet AVD.*** The dialog is the
      one thing that must not merely mirror `FtpBreakthroughDialog` — see that
      dialog's own list of problems in [AUTO_FTP.md](../AUTO_FTP.md). A
      downward proposal is more sensitive to get wrong than an upward one (a
      rider told their fitness has *dropped* on shaky evidence is a worse
      experience than one offered a number they can simply decline), so this is
      the one to design carefully rather than copy the existing dialog's
      shortcuts forward.

      **The reason not to copy it is sharper than "that dialog is poor": its
      shortcuts all point one way.** It states *"Your fitness has improved!"*
      as fact, never names the twenty minutes it read or the provenance that
      is the whole gate, and offers `Keep Current` against `Update FTP` — two
      buttons neither of which names a number. A rider handed good news on thin
      reasoning loses nothing. **Reverse the direction and every one of those
      becomes a defect**, so `FtpReductionDialog` has three rules instead:

      - **It shows its working** — the three rides on the face of it, with
        their dates and what each measures, because the rider is the only
        person who knows they were ill that week. Disagreeing with the evidence
        has to be possible without first agreeing there is some.
      - **It states nothing as a verdict.** No *"your fitness has dropped"*:
        the claim is about the rides. A rider's FTP is a **setting that has
        become wrong for them**, which is a smaller and truer thing to say
        than that they have got worse.
      - **Both buttons name their number** — `Keep 190 W` against `Lower to
        177 W` — so neither can be tapped by reflex. That is 7.10.5's rule
        about the accept side, applied to a change that is harder to notice
        afterwards.

      **Keeping is also the dismiss, and it is not merely closing a dialog.**
      A tap outside must resolve to the safe direction, and the write it makes
      restarts the evidence window — so the rider has to ride three fresh hard
      rides before the question can be asked again. **That is the cooldown the
      upward path has never had** and which AUTO_FTP.md names as a gap; it
      matters far more here, because being told the same thing about your body
      after every ride is the failure this feature most has to avoid
- [x] **7.11.5** ***Done — and not the name this item offered.***
      `FtpChangeSource` needs a new case, distinct from `AutoBreakthrough`, so
      the trend chart (16.3.1, 7.10.1) can draw a downward automatic change
      differently from a rider's own manual edit, the same distinction 20.3.4
      drew for `Estimated`.

      **It is `AutoReduction` and emphatically not `AutoDecline`.**
      `declineFtpProposal` and `workouts.ftp_proposal_declined` already use
      that word for the *rider saying no*, so a source called `AutoDecline`
      sitting beside them would read as "the app declined" on every screen that
      names a source. The event is the number going down; the name is about the
      number.

      **The enum did its job on the way in**: adding the case failed the build
      in three places — Settings, *Your FTP* and the dashboard — which is
      exactly the three screens that had to learn the new words. They say
      *"measured from your recent rides"*, plural against a breakthrough's
      *"measured from a ride"*, because that is the only place a rider ever
      learns those are different amounts of evidence. It is also on the
      **filled**-mark side of `isMeasured`, which is worth saying rather than
      assuming: it is filled because it was measured, not because it was
      welcome. And `AUTOMATIC_SOURCES` now decides which changes can be put
      back (7.10.4) — what that list enumerates is *the times the app changed a
      number about somebody without being asked*, and a downward change belongs
      on it at least as much as an upward one

- [ ] **7.11.7** **The one number in `FtpReductionRule` that is a guess, and
      what would settle it.** 7.11.1 asked for the window and the bar to be
      *measured* the way 2.2a's curve was, and three of the four constants
      escaped that requirement rather than met it: `FTP_FROM_20_MIN` and
      `WORKING_HR_FRACTION` are `PostWorkoutAnalyzer`'s own numbers, fenced
      against drift by a test, and `MIN_EVIDENCE_RIDES` is a shape rather than
      a threshold. **`MIN_MEANINGFUL_LOSS` is the exception.** 5% has to clear
      the day-to-day spread of one rider's twenty-minute power, which is real,
      is a few percent, and is *not* a fitness change — and nothing in this
      project measures it.

      **It is measurable from the data this app already keeps, and cheaply.**
      `workout_power_bests` holds a twenty-minute figure for every measured
      ride a rider has done; the spread of those within a stable period is the
      number wanted, and one rider's own history answers it for that rider.
      **The honest version is per rider rather than a constant**, which is the
      same move 2.2a made for the power curve: a bar set from somebody else's
      variability is a guess wearing a measurement's clothes. Until then the
      constant is set conservatively in the direction of *not* making the
      claim, which is the right direction to be wrong in.

      **Do not "improve" this by lowering it** on the grounds the feature
      rarely fires. Rarely firing is the design
- [ ] **7.11.8** **The upward path still has no cooldown, and now the two
      directions disagree about that.** 7.11.4 gave the downward proposal one —
      answering it restarts the evidence — because being told the same thing
      about your body after every ride is intolerable. The breakthrough has
      none: `ftp_proposal_declined` is per ride, so a rider hovering at the 2%
      line can be offered the same number ride after ride, which AUTO_FTP.md
      has flagged since it was written. **The asymmetry is defensible and it is
      not obviously right**: an offer of a bigger number is a much smaller
      imposition than an offer of a smaller one. Left as an item rather than
      quietly made symmetrical, because making the upward path harder to fire
      is a change to a feature the owner has never once seen fire (20.3.5
      promised it at signup)
- [ ] **7.11.9** **A downward change wears amber, and nobody chose that.**
      *Your FTP* and the dashboard colour a change by direction —
      `primary` for a rise, `tertiary` for a fall — which was written when the
      only falls were a rider's own edit or a revert. Now the **app** makes
      them, and amber is this project's *off target* signal (11.8.3, and
      `RiderScore`'s third rule forbids it on a rider's identity for exactly
      this reason). A `-13` chip in amber beside a number the app just lowered
      is at risk of reading as *you are wrong* about the rider rather than
      about the number. It may well be fine — orange-for-down is a
      conventional reading on a chart, and this is a delta on a change log
      rather than a badge on a person — so it is **one look on the tablet**
      rather than a change, and it belongs on 22.2.5's trip

- [x] **7.11.6** **Two of the three FTP signals are dead code with a live fuse,
      and one KDoc claims otherwise.** Read in the fifty-second sitting while
      triaging 7.11, and it is a sharper version of the fact 7.11's preamble
      already states in passing.

      `PostWorkoutAnalyzer.analyze` takes `maxHr`, `rpe` and `isHardClass` as
      **defaulted** parameters, and the one production call
      (`PostRideViewModel`) passes neither of the first two. So
      `detectBiometricDecoupling` returns `false` before touching its own logic,
      `suggestFtpFromRpe` returns null before touching its own, and
      `AnalysisResult.biometricDecoupling` is computed and never read. The whole
      of auto-FTP that actually runs is the 20-minute peak.

      **The live fuse is the RPE path, not the decoupling one.**
      `suggestFtpFromRpe` proposes `currentFtp × 1.03` for a hard class rated
      easy, and `analyze` feeds it straight into `proposedFtp` — so a future
      session wiring `rpe` through would ship an automatic **+3% FTP change off
      one subjective answer**, which is a permanent edit to the rider's record
      and is the thing **7.11.2 has since written down as forbidden**: *"RPE
      alone should not be enough."* The parameter having a default is what makes
      it look like plumbing rather than a decision.

      **And `PerceivedEffort`'s KDoc states the opposite as fact.** It says the
      column stays 1–10 partly so that `EASY_RPE_THRESHOLD` *"keeps working —
      `Easy` stores 3, so a hard class that felt easy still proposes an FTP bump
      exactly as before."* Nothing has proposed an FTP bump from RPE at any
      point in this project's history. The mapping reasoning is sound; the claim
      about the effect is false, and it is the same shape as every other
      written-down claim this plan keeps finding — this time in a source comment
      rather than in the plan or in `STATUS.md`.

      There is also a sequencing fact that makes wiring it up harder than it
      looks and is worth recording so it is not rediscovered: **the rider
      answers RPE after `analyze` has already run.** The proposal is computed
      when the summary screen loads and the *How did that feel?* buttons are on
      that same screen, so `rpe` is null at analysis time by construction. Any
      RPE-fed proposal needs a second pass triggered by the answer, which is a
      different flow rather than an extra argument.

      **What to do is a decision this item does not make**, and there are only
      two honest options: delete `suggestFtpFromRpe` and let 7.11 rebuild the
      RPE half under 7.11.1's evidence rules, or keep it and fence it so it
      cannot reach `proposedFtp` without the trend 7.11.2 requires.
      `detectBiometricDecoupling` should be kept either way — 7.11's preamble is
      right that it is the downward path's seed facing the wrong way

      ***Decided in the fifty-fourth sitting: deleted.** `suggestFtpFromRpe`,
      `EASY_RPE_THRESHOLD` and `RPE_FTP_BUMP` are gone, and `analyze` no longer
      takes `rpe` or `isHardClass`. Three reasons, and the first is the one that
      settles it: **a function whose only behaviour 7.11.2 forbids is not
      plumbing waiting to be connected**, and fencing it would mean keeping a
      guard against a caller who does not exist. Second, it is the **wrong
      shape** for what replaces it — 7.11's RPE half is a trend across several
      rides and this reads one ride and returns, which is exactly why
      `detectBiometricDecoupling` is kept and this is not. Third, it could not
      have fired even if wired: the rider answers on the same screen that runs
      the analysis.*

      ***And `maxHr` lost its default**, which is the general form of the
      defect rather than one instance of it: a signal that is optional at the
      call site is a signal nobody notices is missing. `PostRideViewModel`
      passes the rider's resolved maximum now — hoisted out of the chart builder
      two blocks below, which was already resolving it inline — so the
      decoupling check reaches its own logic for the first time. **Nothing reads
      the result**, and the `AnalysisResult` KDoc says so: it is 7.11's seed
      recorded honestly rather than left as a constant `false`.*

      ***No rider-visible change by construction**, so the evidence is the tests
      and the call site rather than a screenshot: **777 JVM tests, 0 failures**
      — four RPE tests and two in `PerceivedEffortTest` deleted, three added,
      including one asserting that passing the maximum is what reaches the
      decoupling check and one that the twenty-minute peak is now the only thing
      that can propose a number. Watched on the tablet AVD to the extent it can
      be: a ride summary loads with the heart-rate chart still captioned* "zones
      from %HRmax · your own number"*, which is the hoisted value arriving where
      it already went, and nothing in logcat.*

      ***`PerceivedEffort`'s KDoc is corrected on the record rather than
      quietly***, because the false claim is more instructive than the fix: the
      mapping reasoning was sound and the sentence about its *effect* was
      invented, which is the same shape as every other stale claim this plan
      keeps finding.

**The owner returned to this exact question from the inbox, 4 August 2026,
without having seen the write-up above.** Verbatim: *"Let's make a solid and
wonderful plan to automatically update FTP both up and down... Going UP is a
different kettle of fish. If you hit a new FTP score, then you have a new FTP
score, there is no two ways about it, you ARE at least that fit."* That is
7.11's own asymmetry argument, arrived at independently and in the owner's own
words rather than the plan's — worth recording as confirmation **rather than
as new instruction to act on**, because it does not supply what 7.11.1 is
actually blocked on: a number for the evidence window and the bar. 2.2a's own
history is the reason not to guess one now — a coefficient fit without real
measurement behind it is exactly the trap that item exists to warn against —
so **7.11.1 stays open until it has ride data to be measured against**, the
same standard 2.2a was held to. What the note does settle is emphasis
("It's important") rather than design; it does not move 7.11 ahead of 15.8 in
*What to do next*, because the triage rule an earlier sitting wrote down is
that an inbox entry is weighed like any other plan item, not promoted for
arriving twice.
