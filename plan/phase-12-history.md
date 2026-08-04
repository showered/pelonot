> Part of the Pelonot plan — the index is [PLAN.md](../PLAN.md).

## Phase 12: Ride history & the rider's own record — fundamental

**The gap:** every ride since the foreign-key fix has been writing a full
per-second time series to `workout_metrics`, and there is no screen in the app
that shows a rider a ride they finished yesterday. `WorkoutRepository` already
has `observeWorkouts(userId)`, `getRecentWorkouts` and `getMetrics`; the data
layer is done and nothing renders it.
- [x] **11.6.7** **The numbers change too fast to read, on both surfaces.**
      The board reports several times a second and every emission is rendered,
      so a rider glancing down sees a blur rather than a number. Needs a
      **display cadence** — around 2 Hz, or a short rolling mean — applied
      where the value is drawn. **It must not touch what is recorded**: the
      1 Hz sample written to `workout_metrics` is the rider's record and stays
      raw. This is a display concern and belongs in one place both surfaces
      read, not solved twice

      `atDisplayRate` (`data/sensor/DisplayRate.kt`) paces the stream: the
      first value at once, then at most one every 500 ms, always the latest.
      **Nothing is averaged and nothing is invented** — every number on screen
      is one the board actually reported, which is the same rule the fence
      follows, and it also means a burst cannot build a backlog that plays out
      in slow motion after the rider has stopped. It hangs off
      `SensorRepository.displayReading`, which the ride screen and the overlay
      both read; the recorder still reads `sensorReading`.

      **Observed on the tablet AVD**, and the technique is worth keeping
      because a 2 Hz update is hard to photograph: the interval was temporarily
      set to **3000 ms** and the screen sampled through the raw framebuffer.
      The cadence figure changed at 3.04, 3.05, 2.75, 2.99, 2.99, 3.01, 3.03 s
      — the pacing exactly, unmistakably, with no timing argument needed. The
      same ride's `workout_metrics` held **60 rows across a 60-second span,
      every one a distinct cadence**: full raw fidelity underneath a screen
      updating once every three seconds, which is the property that mattered.
      Restored to 500 ms and re-measured at ~1.6 changes/s through a ~3 Hz
      sampler
- [x] **11.6.8** **The zone ladder shifts sideways when the zone changes.** The
      current-zone outline is drawn as a border that adds to the segment's
      layout width, so every neighbour moves along when the rider crosses a
      boundary — on the one element they are watching to see that they have.
      The outline needs to be drawn *inside* the segment bounds (inset stroke,
      or a background/elevation change) so the ladder is geometrically static
      and only its colouring moves. Affects the ride screen and the overlay's
      compact ladder both

      **The diagnosis in the paragraph above is wrong and worth leaving in
      place**, because it cost the first twenty minutes: the prescribed
      outline *is* a `Modifier.border`, and a Compose border draws inside the
      element and adds nothing to layout. The two things that actually moved
      were the columns on either side of the segments, both of which sized
      themselves to their own text — the zone **name** ("TEMPO" and
      "NEUROMUSCULAR POWER" are the same element one zone apart) and the
      **FTP percentage** (99% to 100% is a whole digit). Both now reserve the
      width of the widest string they can ever hold, laid out in the font
      actually in use rather than guessed at in dp, so a system font-size
      change cannot reintroduce it. The widest zone name is derived from the
      enum, so renaming a zone cannot either.

      **Observed on the tablet AVD** by measuring the raw framebuffer rather
      than by eye: with the rider in Z1 / `ACTIVE RECOVERY` / `29% OF FTP` and
      later in Z4 / `LACTATE THRESHOLD` / `103% OF FTP`, the segment strip's
      left and right edges were **x=830 and x=1380 in both**, unchanged to the
      pixel across a name two characters longer and a percentage two digits
      wider
- [x] **11.6.9** **A blank heart rate is a dead end.** The card shows `--` when
      no strap is paired and does nothing when tapped. It should be the way in
      to pairing — the strap is the one metric measured identically for every
      rider whatever the power model does (Phase 21), and today the only route
      to it is Settings, which means ending the ride. Needs 11.6.10

      The tile carries "Tap to pair a heart-rate strap" only while the reading
      is absent, and opens 11.6.10's sheet. **Observed on the tablet AVD** —
      note that it takes *Hardware* mode to see at all, because the simulated
      rider has a fabricated heart rate and so never shows the empty state
      this item is about
- [x] **11.6.10** **Reach Settings from the ride and from the overlay without
      ending the ride.** Pairing a strap, changing the telemetry source, fixing
      the coach volume — all of these are things a rider discovers they need
      *while riding*, and all of them currently cost the ride. A sheet over the
      ride screen rather than a navigation away from it, and the overlay needs
      its own route in. Watch 24.1.5's rule from the other direction: this adds
      a control, not a screenful of numbers

      `RideSettingsSheet` holds exactly those three, reached by a gear beside
      the telemetry chip. It **reuses the section composables Settings draws**
      rather than restating them, which is the only reason a second surface for
      the same preferences is safe to have at all.

      **The overlay's own way in is inside the volume panel**, not a fifth
      button on the resting strip: that panel is already where a rider goes to
      change something rather than read something, and the strip's job is the
      next sixty seconds of pedalling. The overlay has no navigation, so it
      raises `RideSettingsRequest` and brings the app forward — a process-wide
      one-shot rather than an intent extra, because `bringForward` deliberately
      sends the launcher's own `ACTION_MAIN` and 11.1a explains what changing
      that does to a ride in progress.

      **Observed on the tablet AVD**: the sheet over a running ride with the
      clock still counting; telemetry switched to Hardware *mid-ride* from the
      sheet, the chip flipping to "No signal from the bike — not recording"
      with the ride intact; the heart-rate card opening it; and "More settings"
      on the overlay landing on the ride screen with the sheet already up.

      **One snag found while verifying, not fixed.** The volume panel's
      five-second idle timeout (11.5.5) closed it under the first attempt to
      reach the new button — the timeout restarts on slider movement, not on a
      rider reading the panel. It was harmless when the panel held two sliders
      and is now a control that can vanish while being aimed at. Either exempt
      the panel from the timeout once it has been touched, or lengthen it


### 12.1 History screen
- [x] **12.1.1** `HistoryScreen` + `HistoryViewModel`, as a real `NavHost` destination, reached from a History card on the dashboard
- [x] **12.1.2** Rides grouped by day, newest first, with headers. `RideDayGrouping` is pure with the clock and timezone injected, and tested across midnight, both DST transitions and a non-UTC day boundary — an off-by-one here is only visible around midnight, in a timezone the author does not live in
- [x] **12.1.3** Row shows class title (or "Just Ride"), time, duration, output, avg power, distance, and says so when a ride was rebuilt after a crash — which is what `was_recovered` (12.5.5) exists for
- [x] **12.1.4** Only complete rides, asserted in `WorkoutDaoTest`
- [x] **12.1.5** Empty state that says what to do. Guests get their own, since a guest ride belongs to nobody by design
- [x] **12.1.6** Windowed query. `observeHistory` is a projection joining only `workouts` and `class_templates` and **never touches `workout_metrics`**; `observeCompletedCount` tells the screen whether to offer "Show older rides"

### 12.2 Ride detail
- [x] **12.2.1** `RideDetailScreen`, a separate destination from `PostRide`. Not `PostRideViewModel` with a flag: that one runs the FTP analyser over the whole series on load and offers to rewrite the rider's FTP, which is right ninety seconds after a ride and bizarre on a ride from March
- [x] **12.2.2** `RideSummaryCard` extracted out of `PostRideSummaryScreen` and shared by both
- [x] **12.2.3** Charts (phase 16) land here first. *Bookkeeping: this box was
      left open while the work behind it was done and observed. `RideChartsSection`
      is on this screen and 16.1.1–16.1.5 were each ticked against it on the
      tablet AVD; there was never a separate piece of work here*
- [x] **12.2.4** Edit RPE after the fact, saving on each tap. **Observed**: rated 7 from the detail screen, `rpe_rating = 7` in the row

### 12.3 Delete
- [x] **12.3.1** Delete from the detail screen and from a button on the row. **Not** a swipe: a swipe is right for a mis-tap you can take back instantly, and this list is scrolled far more often than it is edited
- [x] **12.3.2** Confirm before deleting, naming the ride and its date, and saying that the per-second record goes with it
- [x] **12.3.3** Undo snackbar. **The delete is deferred, not reversed** — `workout_metrics` cascades, so an "undo" that re-inserted the aggregates would hand the rider a ride with its time series missing. The row is hidden, the snackbar offers to put it back, and the delete only reaches the database when the snackbar goes away or the screen closes. If the process dies mid-window the ride survives, which is the safe direction. **Observed**: undo, then 49 metric rows and `rpe_rating = 7` still in the database
- [x] **12.3.4** `PRAGMA foreign_keys` asserted directly on the real connection in `WorkoutDaoTest`, alongside a 20-sample cascade. An orphaned metric series is invisible from every screen and grows forever
- [ ] **12.3.5** **Deleting a synced ride must delete it in the cloud too**, or the next pull resurrects it. Needs a tombstone (`deleted_at`) rather than a hard delete, since the device may be offline when the rider deletes. Blocked on 14 — the confirm dialog says "this only deletes the ride on this device" in the meantime
- [ ] **12.3.6** Bulk delete / select mode — after 12.3.5, not before

### 12.4 Housekeeping the record
- [ ] **12.4.1** Re-file a household guest ride against a profile from history (the post-ride flow in 8.4 is the only chance to do it today, and the rider is usually still breathing hard)
- [ ] **12.4.2** Filter by class category and by date range
- [x] **12.4.3** Export a ride — CSV of the metric series, and `.tcx` for Strava and everything else. This is an open-source app: not being able to get your own data out is the thing the subscription product does.
      *Both written from the **samples**, never the aggregates: an export that
      disagreed with the database would be worse than none, and `avg_hr` has
      already been wrong here once. A null heart rate stays null all the way to
      the file — blank in CSV, element absent in TCX — because a zero there is a
      fabricated reading in a file the rider may well average later. Numbers are
      formatted `Locale.US` on purpose: a French device writes 91,5, which in a
      comma-separated file is two columns and in XML is a parse error at the far
      end. Saved through the system file picker rather than a share sheet — the
      rider says where it goes, no `FileProvider` is involved, and the bike's
      tablet has almost nothing installed to share **to**. Observed on the
      tablet AVD: both files written to Downloads, pulled back, and the TCX
      parses as XML with **632 trackpoints against 632 rows in
      `workout_metrics`**. Success and failure are both said out loud in a
      snackbar; a silent export is indistinguishable from a successful one*
- [ ] **12.4.3a** `.fit` as well as `.tcx` — a binary format needing a real
      encoder, where TCX is text. Nothing reads `.fit` that will not read
      `.tcx`, so this is for completeness rather than reach
- [ ] **12.4.3b** **The TCX has no per-second distance, and cannot have one.**
      `workout_metrics` stores cadence, resistance, power and heart rate — there
      is no speed or distance column — so a per-trackpoint `DistanceMeters`
      could only be invented by spreading the ride's total evenly across its
      seconds, which describes a ride nobody did. The lap carries the real
      total. Whether Strava is content with that needs an actual upload to find
      out. Same family as 16.1.6: a missing column, not a missing calculation
- [x] **12.4.4** Export/import the whole local database as a file. Until 15 exists this is the *only* backup a rider has. *Done with 19.1.3 — read the detail there*
- [ ] **12.4.5** Trimming the per-second record of old rides is **23.4**, and it is deliberately not scheduled: the measurement in *What a workout costs* says the cloud is nowhere near its 500 MB limit at household scale, and the local database — which fills seven to ten times faster — is the one to watch. 23.4.1 is the measurement that would start the work

### 12.5 Room migrations — do this before anything in 12–19 ships
- [x] **12.5.1** Replace `fallbackToDestructiveMigration()` with explicit `Migration` objects. `AppMigrations.ALL` is the list; a downgrade still falls back destructively, since that only happens when an older APK is installed over a newer one on a development device
- [x] **12.5.2** Export the Room schema to `app/schemas/` and check it in. The stale `2.json` left over from an abandoned `theme_preference` column has been deleted; `1.json` and `2.json` are now the real history
- [x] **12.5.3** `MigrationTestHelper` instrumented test for each migration. 1→2 runs against a real SQLite file created from the exported v1 schema, with rows written beforehand, and asserts they — and the cascade onto `workout_metrics` — survive. **Observed: 18 instrumented tests pass on the tablet emulator**
- [x] **12.5.5** The first migration is one the app needed anyway rather than a placeholder: `workouts.was_recovered`, so history can distinguish a ride rebuilt from its samples after a crash from one that finished normally (12.1.3)
- [ ] **12.5.4** Only then, the schema changes the rest of the plan needs. **Two are done**: `profiles.auth_user_id` (migration 2→3, the consent gate — 23.1.1) and `workout_metrics.power_is_measured` (migration 3→4, which unblocked 16.1.6, 7.10.7 and 24.4.2 in one go). **A third is done**: `workouts.ftp_watts` (migration 6→7, the FTP a ride was ridden at — 7.8). **A fourth is done**: the `ftp_history` table (migration 7→8, which also *seeds* itself from the profiles that already exist — 7.9). Still to come: `deleted_at` and `synced_at` for 14–15, and date of birth on `profiles` (21.1.1). Units (13) turned out to need none — the preference is a display concern and lives in DataStore

> Deliberate consequence: a development device still holding a pre-migration
> database now fails to open rather than silently emptying itself. No shipped
> build has ever existed, so no rider is affected; uninstall and reinstall.

> This is listed last in the phase and is first in the work. Every remaining
> phase adds a column. The moment a build with a real training history is
> installed on the bike's tablet, a destructive fallback stops being a
> pre-release convenience and becomes a data-loss bug that has already happened
> by the time anyone notices.

---

### 12.6 The summary and the record are the same ride — the owner's note, 4 August 2026

**Verbatim:** *"Ride summary (after a ride). This should be pretty much the same
as when you view it from history, right? It currently doesn't contain any
graphs. Also add an option to 'Resume' from here in case it was an accident."*

**The first sentence is a question and the answer is nearly yes** — which is
what makes the difference worth naming rather than closing. 12.2.1 decided
deliberately that these are two screens: `PostRideViewModel` runs the FTP
analyser over the whole series on load and can offer to rewrite the rider's
FTP, which is right ninety seconds after a ride and bizarre on a ride from
March. 12.2.2 then made the *figures* one component so the two could not drift.
Charts were never given the same treatment, and the reason is nothing more
principled than that 16.1 landed on ride detail first: `RideChartsSection` is
private to `RideDetailScreen`.

So the honest statement of the difference is: **the summary is the detail
screen plus the two things that are only true tonight** (the FTP proposal, and
Discard) **minus the charts, for no reason**. Everything below follows from
that.

- [x] **12.6.1** **The charts come to the summary**, by extracting
      `RideChartsSection` the way `RideFigures` was extracted in 12.2.2 — one
      component, two screens, so the next chart is added once. It needs the
      metric series, which the summary does not load today; `PostRideViewModel`
      already reads the samples for the FTP analysis, so the cost is a state
      field rather than a query. Watch 12.1.6's rule in reverse: this is one of
      the two screens where touching `workout_metrics` is correct

      *Done and observed on the tablet AVD: Power beside Heart rate under the
      effort question, filling the half of the screen that was empty.
      `RideChartsSection` lives in `ui/components` now and takes the ghost and
      the rivals as **defaults**, because a comparison is the one thing this
      screen has not been asked for — a rider who has just stopped pedalling
      wants to know how the last twenty minutes went, not how it went against
      Alex.*

      ***The second extraction is the half worth naming.** `buildRideCharts`
      came out with the section, because the FTP rule that decides the zone
      bands (7.8) was inside `RideDetailViewModel` — and a second copy of it is
      a second answer to* which FTP were these bands drawn from*, on the one
      question this app has already got wrong once. The samples are read once
      and used twice: the breakthrough analysis wanted the whole series anyway.*
- [x] **12.6.2** **Resume from the summary — and it is a real hazard, not a
      convenience.** The owner's *"in case it was an accident"* is the two-tap
      stop on the overlay (11.6.6) and the one-tap End on the ride screen, both
      of which sit a thumb's width from pause. 8.3d already built everything
      this needs: a ride is resumable while its row is incomplete, and
      `RideScreen` takes a `resumeWorkoutId` that skips the countdown. **The
      constraint is that the summary is reached *after* `stopWorkout`**, which
      finalises the row — `is_complete = 1`, averages computed, `synced_at`
      queued. So this is not 8.3d's path and must not pretend to be: either the
      finalise is reversible (re-open the row, and let 8.3d's own resume take
      it) or the ride is genuinely over and the offer is a lie. Read 8.3d.4
      before touching it — the finalise writes defaults over any column
      `WorkoutSession` does not carry, so a re-opened ride must go back through
      a session that carries `resume_count`, `interrupted_sec` and `ftp_watts`
      or the second finalise silently reverts them

      ***Done, and the database is the witness** — a resumed series comes back
      contiguous and cannot show any of this on a screen. Ended a ride at
      01:44, tapped* Carry on riding*: the row read `is_complete = 0`,
      `resume_count = 1`, `interrupted_sec = 22`, `synced_at` null, and the ride
      screen came back at 01:48 with 16 kJ and 0.19 mi still on it rather than
      at zero. Ended it again: `is_complete = 1` with **`resume_count` and
      `interrupted_sec` still 1 and 22** — 8.3d.4's trap checked rather than
      trusted — 153 contiguous samples, and `avg_power` 142.978 on the row
      against 142.98 over the samples themselves.*

      ***Three things this needed beyond 8.3d's path, all because that path had
      never met a finished ride.** The reopen now clears `is_complete` — left
      set, a ride still being ridden sits in history, in the leaderboards and
      in the totals — and `syncedAt`, because otherwise the cloud keeps the
      short version of a ride that got longer and never offers it again
      (14.2.5); the upload is an upsert on the ride's own id (15.3.3), so
      re-sending replaces rather than duplicates. And `resumeInterruptedRide`
      accepts `Completed` as well as `Idle`: the summary appears while the
      service is still shutting itself down, and refusing there would make this
      a button that works or does nothing depending on how fast the rider
      tapped it.*

      *It also made a sentence false, which is fixed: the end-ride dialog said*
      "Everything so far is saved either way, but a ride can't be restarted."
- [x] **12.6.3** **Decide what stays different, and say so in the file.** After
      12.6.1 the two screens are one layout with three deltas: the FTP
      breakthrough dialog, Discard/Resume, and the guest destination (8.4). That
      is a small enough difference to be worth stating at the top of both files,
      because the next person to add a card to one of them needs to know which
      list they are adding to. It is also the answer to whether they should be
      merged: **no** — 12.2.1's reasoning stands, and it is the behaviour that
      differs rather than the presentation

      *Done, at the top of both files, and writing it out found a fourth delta
      the item had missed: **ride detail has two of its own**, export (12.4.3)
      and* ride against *(24.3.1, 16.3.4). The second is the interesting one and
      it is why `RideChartsSection` defaults the ghost away rather than taking
      it from wherever it is drawn — a comparison is something a rider comes
      back to make, not something to put in front of them while they are still
      breathing hard.*
- [x] **12.6.4** **Judged on the 1280 × 720 dp AVD after 22.4.6's rebuild.**
      The summary now pins Done and Discard below a scrolling body; adding two
      charts and a Resume button to that body is exactly the change that pushes
      something below the fold, which is how 22.4.2's regression was found
