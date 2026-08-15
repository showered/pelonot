> Part of the Pelonot plan — the index is [PLAN.md](../PLAN.md).

## Phase 19: Ideas worth having, ranked

Sorted by value per unit of work. The first group is arguably fundamental and
has simply never been written down.

### 19.1 High value, small
- [x] **19.1.1** **Screen-on lock during a ride** (also 11.3.5) — the tablet sleeping mid-class is a bug the rider experiences as the app being broken.
      *Nothing held the screen awake: no window flag, no wake lock, not even the
      permission. Invisible on the bike because Netflix holds its own, so it
      only bites a rider on Pelonot's own ride screen — or on a podcast, or on
      anything else that does not. Asserted in the two places that are up for
      exactly as long as a ride is: the ride screen's window and the strip's
      overlay window, of which exactly one exists at a time, because the overlay
      stands down while the ride screen is on top. Held through a pause too — a
      rider refilling a bottle has not left. **Observed on the tablet AVD**:
      `SCREEN_BRIGHT_WAKE_LOCK 'WindowManager'` with
      `ws=WorkSource{com.pelonot}` on the ride screen, and still held after
      minimising to the strip with another app in the foreground*
- [x] **19.1.2** **Auto-pause** when cadence has been zero for ~20 s, and auto-resume on the first tick. Every ride has a bottle stop, and it currently drags the averages down
      *Done. `AutoPausePolicy` (`domain/model/`, pure, 9 tests) decides; the
      service's ticker asks it every second — while paused as well as while
      riding, because the tick that lifts an auto-pause is a tick the clock is
      not moving on. Pausing stops the clock, the class and the recording,
      which are the three things that were wrong about standing still.*
      *Two rules the policy holds, both of them the same trap in different
      costumes. **A stalled board is not a stopped rider**: with telemetry not
      live there is no reading to call zero, so the stillness clock is held —
      and a frozen 90 rpm from a dead board does not lift an auto-pause either
      (2.4.4, read in the other direction). **Only a pause the policy caused is
      resumed automatically**: a rider who pressed pause and then turned the
      pedals reaching for a towel has not asked to be racing again.*
      *Both surfaces say why they stopped — "PAUSED — START PEDALLING TO
      RESUME" on the ride screen, "PAUSED · PEDAL" on the overlay. A ride that
      pauses itself silently is indistinguishable from one that has frozen.*
      *The find, and it is 19.1.2a below: **the simulated rider never stops**,
      so none of this could be seen without a bike. **Observed on the tablet
      AVD** once it could be: paused at 20 s of stillness, resumed the second
      the coast ended, held through a manual pause while the simulated rider
      pedalled, and the ride recorded 86 rows over an 85 s ride containing a
      45 s stop — no clock, no class and no rows through the pause*
- [x] **19.1.2a** **The simulated rider never stops pedalling**, so nothing
      about a rider standing still could be exercised without someone on the
      bike — auto-pause, the gap a stop leaves in `workout_metrics`, what the
      averages do across one. `CLAUDE.md` calls a pedalling rider a perishable
      resource, and this was a whole family of behaviour that could only be
      spent on. *Fixed with the smallest lever that stays out of a release
      build: `SimulatedSensorSource.coastFor(seconds)` — cadence and power to
      zero, resistance left where the knob is — driven by a receiver in the
      **debug source set**, so a release build has no way to reach it:*
      ```bash
      adb shell am broadcast -a com.pelonot.debug.COAST \
        -n com.pelonot/com.pelonot.debug.DebugTelemetryReceiver --ei seconds 40
      ```
- [x] **19.1.2b** ~~**The twenty seconds before an auto-pause are still averaged
      in.**~~ They are recorded honestly — the rider really was at zero — but
      `avg_power` and `avg_cadence` are computed live over every row, so a stop
      still costs 20 s of zeros. On a 30-minute ride that is 1%; on the 85 s
      test ride above it was 20 of 86 rows and pulled `avg_cadence` to 61. The
      fix is *not* to delete the rows: they are true, the charts should draw
      them, and deriving a summary by throwing away samples is the shape of
      trouble this plan keeps cataloguing. It is to decide whether the `avg_*`
      columns mean "over the whole recording" or "over the pedalling", say
      which in one place, and apply it in `WorkoutMetricsCalculator`. Worth
      settling alongside 7.8, which is the same question about a different
      column

      ***Done in the fifty-first sitting. The answer is over the pedalling, and
      the reason it is not merely a preference is that the app had already made
      the decision somewhere else.*** *`AutoPausePolicy` exists to say that a
      rider who is not turning the cranks is not riding, and it stops the clock,
      the class and the recording on the strength of it. An average taken over
      seconds the app itself had already ruled a stop is the two halves
      disagreeing. So `PEDALLING_RPM` is now `const` with a paragraph on it
      saying it is **the app's single definition of the word**, and both means
      divide by the seconds that clear it.*

      ***The rows are untouched, which the item was right to insist on.*** *The
      trench is in `workout_metrics`, on the power trace, on the cadence trace
      and in the duration. What changed is one denominator, carried as
      `pedallingSampleCount` on `WorkoutSession` and on `WorkoutAggregates` for
      the same reason `heartRateSampleCount` already was: a resumed ride (8.3d)
      has to re-weight its running mean at the count it was really built at, and
      dividing by the tick count would silently under-weight every second ridden
      after the pick-up. `WorkoutSessionResumeTest` asserts both that and the
      thing 8.3d's whole premise rests on — **the live path and the cold path
      agreeing across a stop**, which is where two implementations of one
      definition would most easily part company.*

      ***`avg_hr` is deliberately not treated this way, and the asymmetry is the
      rule rather than an omission.*** *Cadence and power are measurements of
      the **riding**, and there is none while the cranks are still; a heart rate
      recorded during a bottle stop is a measurement of the **rider**, who is
      still there and whose heart is still the thing being asked about. On the
      verification ride the two figures visibly part — 123 W over the pedalling
      against 111 bpm over everything — and that is correct.*

      ***One consequence found by looking at the screen rather than the diff, and
      fixed in the same change.*** `RideChartSummaries.power` *ended its sentence
      with an average of its own, taken over every bucket in the trace. With
      `avg_power` redefined the two disagreed on any ride with a stop in it —
      one screen, two numbers, both called* "average" *— which is the trap
      `WorkoutAggregates`' own comment names as* one quantity, two derivations.
      *The caption says nothing rather than a rival version of a figure the card
      above already carries.*

      ***Measured on a fresh ride rather than reasoned about.*** *3:09 of Just
      Ride on the tablet AVD with `com.pelonot.debug.COAST --ei seconds 40` in
      the middle of it, auto-pause observed firing and lifting. **189 recorded
      rows, 168 of them pedalling.** The whole-recording means are 109.497 W and
      68.758 rpm; the pedalling means are 123.185 W and 77.353 rpm; and the
      finalised row says **123.185 and 77.353**, to three decimals — the live
      path and a `SELECT AVG(...) WHERE cadence >= 1.0` agreeing exactly. The
      stop cost 12.5% of the cadence average on a three-minute ride.*

      ***No backfill, and it is a decision rather than an oversight.*** *Every
      ride recorded before this change carries the old definition and there is
      no marker on the row saying which it used. A launch-time pass like
      `backfillPowerProvenance` was considered and refused: it cannot be made
      idempotent without a column, it **cannot be done at all for a ride 23.4
      has condensed** (the seconds are gone, and `distributions_json` does not
      hold a mean), and a partial recomputation that looked complete would be
      worse than a dated one. The column is display-only — nothing gates on it,
      not the FTP proposal and not any leaderboard — which is what makes living
      with two definitions cheaper than half-fixing it.*
- [x] **19.1.2c** ~~**A stop is filed as Active Recovery.**~~ Found on the same
      screen in the same sitting, and it is 19.1.2b's decision reaching one card
      further than 19.1.2b touched. The verification ride's *Time in zone* card
      reads **Z1 Active Recovery 01:19 · 42%** on a 3:09 ride, and 21 of those
      79 seconds are the rider standing at the tap. *Active Recovery* is a claim
      about riding easily, and the bar makes it the largest thing on the card.

      It is **not** simply 19.1.2b applied again, which is why it is its own
      item rather than a line under one. Three things have to be decided
      together and one of them is a data question:

      - Time in zone is a **count of seconds**, and 23.4 has already established
        that a count cannot be recomputed once a ride is trimmed — it lives in
        `workouts.distributions_json`, written while the seconds were still
        there. So the fix is at the moment of counting, not at the moment of
        drawing, and an old ride keeps whatever it counted.
      - The honest shape is probably 21.4.1's, which solved exactly this for
        heart rate: the unrecorded seconds are **counted and named**, the zones
        divide the time the rider was *riding*, and the caption says what that
        was out of. What 21.4.1 refused is the tempting version — a grey wedge
        in the bar — because an absence on the same footing as a zone is the
        same mistake wearing a colour.
      - And the cadence spread (16.x) is the same count with the same question,
        one card along. Whatever is decided has to answer for both, or the two
        cards on one screen divide the same ride by different totals

      ***Done in the fifty-second sitting, and the third bullet turned out to be
      the interesting one.*** *The cadence spread was **already** excluding the
      stop, and had been since it was written — with a comment saying* "Coasting
      is not a cadence" *— so the two cards on that screen have disagreed about
      the same ride for as long as both have existed, one of them naming the
      stopped seconds after a zone and the other silently dropping them. What
      made it worth reading rather than copying is **how** it excluded them: a
      private `MIN_CHARTED_CADENCE = 20.0` of its own, which meant this app held
      **three answers to the question* was the rider pedalling* — the pause's
      1.0, the averages' 1.0 as of 19.1.2b, and the charts' 20.0. That is the
      thing 19.1.2b's write-up warned about arriving one sitting later.*

      ***So one definition, and the collapse is measured rather than argued.***
      *`AutoPausePolicy.PEDALLING_RPM` now decides for both cards. The reason 20
      was costing nothing is that **a stop produces no samples at all between 1
      and 19 rpm**: 21 zeros and 168 seconds above 20 on the ride this was
      checked against, which is the board reporting a true zero the moment the
      cranks stop rather than a flywheel coasting down through the low bands —
      the same claim `AutoPausePolicy`'s own comment makes about real hardware.
      What the collapse buys is that a genuine 14 rpm grind is now on the chart
      instead of being silently deleted, and `RideChartBuilderTest` asserts the
      two cards agreeing on a ride that has 85 rpm, 14 rpm and 0 rpm in it.*

      ***Built as the second bullet recommended, and the refusal came with it.***
      `TimeInZone` *gains `secondsStopped`, `recordedSeconds` and `isPartial` —
      `TimeInHeartRateZone`'s three members under different names, because it is
      the same question one metric along. The card says* "pedalling for 02:48 of
      03:09" *when part of the recording was not ridden and **says nothing at
      all** when it was; the stopped seconds are not drawn as a wedge in the bar.*

      ***And the third card on that screen was checked and is deliberately
      different.*** `prescribedPlan` *counts a stop as time **outside** the
      target, with a comment already saying why —* "the rider was asked to turn
      the pedals at 85 and was not" *— and that is right, because compliance
      asks* did you do what was asked *where time in zone asks* what did you do.
      *Two questions, two answers, and neither is the other's bug.*

      ***Watched on the tablet AVD on the ride that exposed it, with the previous
      build as the control and a second ride as the control for silence.*** *Z1
      went from* `01:19 · 42%` *to* `00:58 · 35%` *— a difference of exactly 21
      seconds, which is exactly the number of zero-cadence rows in that ride —
      and the caption appeared reading* "pedalling for 02:48 of 03:09"*, where
      02:48 is 168 seconds and 03:09 is 189, both matching the row counts. A
      second ride with no stop in it draws **no caption at all**. The numbers
      were read against `SELECT COUNT(*) … WHERE power/155.0 < 0.56` in both
      directions rather than off the screenshots: 79 and 58.*

      ***One case this cannot reach, and it is written on the field rather than
      guessed at.*** *A ride **already condensed** by 23.4 carries its counts in
      `distributions_json`, and a blob written before this build has no
      `seconds_stopped` in it — so it keeps the old counting and says nothing
      about it. An untrimmed ride recomputes from its own samples every time, so
      every ride on a tablet today corrects itself the moment this build runs,
      which is a better story than 19.1.2b's `avg_*` got and is the reason no
      backfill is needed here.*
- [x] **19.1.3** **Local backup/restore of the database to a file** — the only safety net that exists before 15, and it survives the destructive-migration problem too
      *Done, and 12.4.4 with it — they were always one piece of work. Settings
      → **Backup** writes the database through the system's own file picker as
      `pelonot-backup-YYYY-MM-DD.db`, and restores it back through the same
      picker.*
      *Three things worth carrying forward. **The checkpoint is the whole
      trick**: Room runs in WAL mode, so the ride just finished lives in
      `pelonot_database-wal` and *not* in the file being copied —
      `PRAGMA wal_checkpoint(TRUNCATE)` first, or the backup is silently
      missing the newest rides and only says so on the day it is restored.
      **The file is the database itself**, not a format of our own: it restores
      by being copied back, anything that reads SQLite can open it, and there
      is no serialiser to fall behind the schema — the `intervals_json` failure
      in another costume. **A restore is refused rather than attempted** when
      the file is not ours, when it comes from a newer schema (Room's answer to
      a database from the future is to empty it, 12.5.1) or when a ride is in
      progress (8.3b's family), and everything is judged against a staged copy
      in the cache, because the moment the live file is touched there is no way
      back. Afterwards the app restarts itself: every DAO and `StateFlow` in
      the process is holding a database that has been swapped underneath it.*
      *The bug found while verifying, and it is a good one: the SQLite magic
      is `SQLite format 3` followed by a **NUL**, and it had been written with
      a trailing space — so every genuine backup was refused with "that file is
      not a Pelonot backup". **No test built out of that same constant could
      have caught it**; there is now one written from the spec instead.*
      ***Observed on the tablet AVD**: 241 kB written to Downloads containing
      the ride recorded two minutes earlier; a `.tcx` offered to the restore
      and refused by name; the real backup restored — process 20063 → 20270,
      the profile created after the backup was taken gone, and 5 workouts /
      491 samples back exactly as the file had them*
- [x] **19.1.3a** **Restore refused every backup this build made, and it is the
      same defect as 19.1.3's twice over.** `BackupFile.verdictFor` compares the
      file's schema version against the app's and refuses one from the future,
      which is right — Room's answer to a database from the future is to empty
      it (12.5.1). The app's half of that comparison was
      `AppDatabase.SCHEMA_VERSION`, a `const` sitting under a comment reading
      *"kept beside the `@Database(version = …)` above and equal to it"*, and it
      drifted the first time somebody bumped the version without reading the
      comment: **16 against a database at 17**, since `de3f968`. So a rider who
      backed up and then restored was told *"That backup was made by a newer
      version of Pelonot. Update the app, then restore it"* — advice that cannot
      be followed, about the newest version there is, on the one safety net that
      exists before Phase 15.

      *Two things make it worth more than its one-line fix. **It is 19.1.3's own
      bug again**: that item found the magic bytes written with a trailing space
      and wrote down that no test built out of the same constant could have
      caught it. This is the neighbouring argument to the same function going
      wrong the same way — `BackupFileTest` was green throughout, because the
      arithmetic was always right and only one of its inputs was a lie. **And a
      number kept equal to another number by a comment is the mechanism**, so
      the fix is not to correct the constant but to delete it: `AppDatabase
      .schemaVersion()` asks the open file, which is the only place the answer
      actually lives.*

      ***Observed on the tablet AVD**, and checked against the bug as well as
      against the fix: `DatabaseBackupTest` writes a backup through the real
      `backupTo` and puts it straight back through `inspect`, which passes now
      and — with `schemaVersion()` pinned to 16 — fails with the rider's own
      message,* expected:&lt;Accept&gt; but was:&lt;Refuse(reason=That backup was made by
      a newer version of Pelonot…)&gt;. *It stops at the verdict on purpose:
      `restoreFrom` closes the shared database and overwrites the live file, and
      the half worth testing is over before then.*
- [ ] **19.1.4** **CI**: GitHub Actions running `assembleDebug` and `testDebugUnitTest` on every PR. An open-source project taking contributions without this is asking maintainers to be the build server

      *Written — `.github/workflows/ci.yml`, JDK 17, the Gradle wrapper,
      `assembleDebug` then `testDebugUnitTest`, with the HTML test report kept
      as an artifact on failure so a contributor can see **which** test rather
      than only that one broke. **The box stays unticked until a run is green
      on GitHub**, which is the house rule and not a formality here: a workflow
      that parses is not a workflow that builds.*

      Two decisions in it worth keeping. **No `local.properties` and no
      secret**, deliberately — the cloud credentials are optional by design
      (14.10.3) and a clone without them must still build and run offline, so
      the day this workflow needs a secret is the day offline-first broke.
      And **not `connectedDebugAndroidTest`**: it needs an emulator, and this
      project's instrumented suite is order-dependent (a test asserting
      `WorkoutService` is `Idle` only holds while nothing earlier in the run
      finished a ride), so a red run would mean "re-run it" often enough to
      train everyone to ignore the whole thing. 8.8b is the same complaint
      about the same suite
- [x] **19.1.6** **The first run explains nothing.** A new rider is dropped
      straight onto the profile picker; profile creation asks for an FTP with
      **200 prefilled** and no way to find a real one (19.2.3 is the guided test
      and is unbuilt); the overlay permission — the thing the entire product is
      built on — is first mentioned at ride start; and a heart-rate strap is
      discoverable only by opening Settings. None of it is broken, and all of it
      assumes the rider already knows what this app is. The smallest honest
      version: say what FTP is and that a guess is fine and the app will correct
      it (7.1 already does), offer the overlay permission before the first ride
      rather than during it, and mention the strap once

      **Three of its four claims were already false, and it was the top row of
      `STATUS.md`'s *Blocking a stranger being able to use this* table the whole
      time.** That is the finding rather than the screen. This item was written
      in the twenty-fourth sitting and read by every session since as a summary
      of what a stranger meets; nothing re-checked it against the code, and
      three separate items fixed three of its four clauses without anybody
      coming back to cross them off. Checked line by line on 13 August 2026:

      - **The FTP is not prefilled with 200 and has not been since 20.3.**
        Profile creation *estimates* it from the weight, the year of birth and
        one sentence about your riding, says so on its own screen — *"Our
        estimate, from your weight, your age, and that you're riding now and
        then. We'll work it out properly from your first few rides."* — and
        offers *I know my FTP — set it myself* underneath. 20.3.6 had already
        found and fixed the `200` itself, one sitting before the screen was
        replaced. **Stale.**
      - **The overlay permission is asked during the countdown, and that is
        where the owner asked for it.** 11.6.14 is their own note: *"This should
        happen DURING the countdown (and pause the countdown while you go away
        and do it)"*. So this clause is not merely stale, it is **superseded by
        a decision that went the other way** — building "before the first ride"
        now would undo an item that was watched working, with the count sitting
        at 10 for ninety seconds. **Superseded.**
      - **A strap is discoverable from the ride screen**, and has been since
        11.6.9/11.6.10: the heart-rate tile carries *"Tap to pair a heart-rate
        strap"* whenever `heartRateBpm` is null, and tapping it opens pairing
        without ending the ride. That is a better answer than "mention it once"
        — it is offered at the only moment a rider is looking at an empty
        heart-rate number. **Stale.**
      - **The opening clause was still true**, and it is the one nobody had
        built: a rider who has just side-loaded this onto their bike meets
        **"Who's riding?" over a Guest tile and a New rider tile**, and that is
        every word the app says about itself. Screenshotted on the tablet AVD
        off a fresh install as the control.

      *Done, and it is a **state of the profile picker** rather than a screen in
      front of it. An empty `profiles` list is the only first run there is, so
      the database is the flag: there is no "has seen the welcome" preference,
      and therefore nothing that can drift out of step with whether a rider
      actually has a profile. The heading becomes the app's name, because
      "Who's riding?" is unanswerable on an empty bike and the question this
      screen has to answer first is _what is this_; under it one sentence —
      "Your ride, on the bike's own tablet, over whatever you're watching."*

      *One sentence, and it is the overlay, under Phase 26's rule about erring
      towards saying less. The overlay is what this app **is** and the one part
      of it a rider cannot discover by pressing things. **The offline promise is
      deliberately not repeated here**: the account offer at the end of profile
      creation already makes it at the moment it costs something — "Your rides
      get copied to your account. Everything keeps working without one." — which
      was read on the screen rather than assumed.*

      *20.1.3's rule inverts on an empty bike **because it is the same rule**.
      Guest and "new" are outlined rather than filled so the eye lands on a real
      rider without reading anything; with no real rider on the bike nothing
      catches the eye at all, so setting one up is filled, comes first, and says
      "Set up" rather than "New rider" — new compared to what. Its second line
      is **counted rather than reassuring**: "Four questions, then ride", which
      is a name plus the three that 20.3's own step 2 already calls "Three
      things, and then you're riding".*

      *Watched on the tablet AVD with the previous build as the control, both
      branches: a fresh install draws the first run, and after one profile was
      created through the whole four-question flow the picker is **"Who's
      riding?" with the rider first, Guest, New rider outlined and the
      press-and-hold hint** — unchanged, which is the half of this that could
      have gone wrong silently.*
- [x] **19.1.5** **README and CONTRIBUTING** covering the build, the fact that simulated telemetry makes the whole app usable with no bike, and — corrected — that **no jailbreak is needed**. Worth saying plainly that it installs on a stock bike, since that is the difference between a project people can try and one they assume they cannot.
      *Written. Note the item's own premise was wrong: there was no README at
      all to correct — the root prerequisite was being advertised by
      **`ARCHITECTURE.md`**, which opened "bytes arrive from the bike's sensor
      board over a serial character device" and drew `/dev/ttyS2` in its first
      diagram. The correction had been added in §6, 380 lines below the claim,
      where nobody reading top-down would reach it first — and `CLAUDE.md` sends
      every newcomer and every new session to that file before any other. §1a
      now leads with the `SensorService` bind and the serial path is demoted to
      §1a-bis, for a rooted tablet, which is what it is*

- [x] **19.1.7** **One page that says where the project is — the owner's note,
      4 August 2026.** Verbatim: *"There is so much plan documentation (this is
      good!) but it's difficult to get a true understanding of where we are.
      Please create a high level summary of what we've done, what's
      outstanding, any issues, and how close we are to being 'done'."*

      **The complaint is correct and the cause is structural rather than
      sloppy.** The plan is ~7,200 lines across 25 files, and every one of them
      is written for the session that will *do* the work: the reasoning is kept
      rather than summarised, deliberately, because that is what has stopped
      this project re-litigating settled decisions and re-making found bugs.
      What none of it answers is the question a person actually asks — *is this
      nearly finished?* PLAN.md's status table comes closest and is still one
      paragraph per phase, written in the same voice, at the same altitude.

      `STATUS.md` at the repo root is that page: what works, what is
      outstanding, what is actually wrong today, and an explicit answer to
      "how close to done" — with **done defined three ways**, because the
      honest answer differs by a lot depending on who is asking. Done for the
      household that owns this bike is nearly here; done for a stranger with a
      Peloton is a short list; done as the plan is written is 69% of the boxes
      and will never reach 100 because the plan is a place ideas are *kept*.

      Three rules it holds, and they are what keep it from becoming a
      fourteenth thing to read:

      - **It is a summary, not a source.** Every claim is a phase file's claim,
        and it names the item so the reasoning is one hop away. Nothing is
        decided here; a decision made in `STATUS.md` would be a decision
        nobody doing the work would find.
      - **It says what is wrong, in one ranked list.** The most useful thing
        the plan hides is that its live problems are scattered across
        twenty-five files by *phase* rather than gathered by *severity*.
      - **It is regenerated, not maintained.** It goes stale the way the
        status table goes stale — so it carries the date it was written and the
        measurement behind it (the test count, the box count), and a sitting
        that changes the picture rewrites it rather than patching it
- [ ] **19.1.7a** **Nothing keeps `STATUS.md` honest, which is the same
      problem as 17.15.2 and has the same cheap answer.** The box counts in it
      are `grep -c` over `plan/*.md` and the test count is the build's own; a
      script that emits both — in `classlibrary/build.py`'s spirit — would make
      the number impossible to state wrongly, and CI (19.1.4) could fail on a
      figure that has drifted. **Do not build it until the file has actually
      gone stale once**, for exactly 17.15.2's reason: a generator nobody runs
      is worse than a page somebody rewrites

### 19.2 High value, medium
- [ ] **19.2.1** **Custom class builder** — build your own intervals in the app. The class library is the subscription's core product and the interval model is already a plain list; this is the feature that makes the app stop needing Peloton at all
- [ ] **19.2.2** **Community class library** — share and import classes. `class_templates` is already a cloud table and already world-readable
- [ ] **19.2.3** **Guided FTP test** — a proper 20-minute protocol with pacing cues, rather than inferring FTP from whatever the rider happened to ride. `PostWorkoutAnalyzer` already does the maths
- [ ] **19.2.4** **Strava upload**, following the `.tcx` export in 12.4.3
- [ ] **19.2.5** **Training load and freshness** over weeks. Flag it hard: built on estimated watts, this is a *relative* trend for one rider and nothing more

### 19.3 Worth doing eventually
- [ ] **19.3.1** Multi-week training programmes
- [ ] **19.3.2** ~~Achievements and streaks (pairs with 16.3.5)~~ — **moved to
      Phase 27** by the owner's note of 4 August 2026, which is what this one
      line actually is: a table that remembers what has already been said, three
      families of alert that are not the same feature, a frequency rule without
      which the whole thing dies in a fortnight, and the same
      measured-power/moving-denominator honesty gates the FTP proposal and the
      household board already carry. Same promotion as 19.3.3 → Phase 21, and
      the owner's weighting stands: **low priority**
- [ ] **19.3.3** ~~Heart-rate zones and HR-based targets~~ — **moved to Phase 21**, which is what this one line actually is: a profile schema change, a zone model, live display, per-ride tracking and HR-targeted classes
- [ ] **19.3.4** Localisation, once the string catalogue is stable
- [ ] **19.3.5** Wear OS or a phone companion as a second HR source
- [ ] **19.3.6** Opt-in, off-by-default crash reporting. For this audience the default matters more than the feature

### 19.4 Explicitly not doing
- Calorie estimates (13.6) — a nutrition claim the power model cannot support
- Anything that requires a network to start a ride
- Anything on the HUD that is not about the next sixty seconds of pedalling
