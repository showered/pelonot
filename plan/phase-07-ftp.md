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

- [ ] **7.10.1** 16.3.1 is now buildable: FTP over time, stepped rather than
      interpolated — FTP does not drift smoothly between two rides, it changes
      on a day — with each change marked by what caused it (7.9.2) and tappable
      through to the ride that triggered it. *Half done: the **stepped** part is
      built and drawn on the dashboard's card (7.10.2), and `FtpTrend` carries
      the source and the workout id for every point. What is missing is the
      full-size version — marks per change, and tapping one through to the ride
      — which wants a screen to live on that does not exist yet (16.3)*
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
- [ ] **7.10.4** **An auto-FTP change is the app editing the rider's own
      record**, so it stays visible and reversible: the history says the app did
      it, off which ride, and reverting to the previous value is one action and
      appends a row rather than erasing one
- [ ] **7.10.5** A declined breakthrough should not be re-offered for the same
      ride. `PostRideViewModel` runs the analyser on load, so a rider who
      declines and re-opens the summary is asked again about a ride they have
      already answered for
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
