> Part of the Pelonot plan — the index is [PLAN.md](../PLAN.md).

## Phase 23: Offline by default — making the ungated tier complete

Rule 1 of *The connectivity model* says a rider with no account makes no
request to Supabase. Today an install with credentials makes two, and one of
them is on the first-launch path. This phase closes that, and then makes sure
the tier on the other side of it is a whole app rather than a stub.

### 23.1 The consent gate

- [x] **23.1.1** **One place that answers "may this profile talk to the
      cloud?"** — `CloudAccess`, gating on `UserEntity.auth_user_id != null`
      (new column, migration 2→3) read per profile. `SupabaseSyncRepository`
      asks it at its single choke point, before the client is even resolved,
      and **no method can be called without naming the rider it acts for**:
      `fetchClassTemplates()` used to take nobody, which is how the class
      library came to be fetched before there was a rider on the tablet at all
- [x] **23.1.2** `WorkoutService` asks the gate rather than `finalSession.userId
      != null`, and the worker asks again when it runs — a job queued for a
      signed-in rider can execute after they sign out, and the answer that
      matters is the one at the moment the ride would leave the tablet.
      Observed both ways round on the AVD, including the queued job refusing
      itself once the account was taken away
- [x] **23.1.3** **A fence, not a behavioural test**, and deliberately opening
      no socket — a behavioural one would have to be trusted not to reach the
      real project at exactly the moment it failed. Four structural claims: the
      SDK is reachable from one package, the client is dereferenced once and
      behind the gate, the build-time flag cannot come back as consent, and
      every entry point names a rider. Same reasoning as the 2.2a.8 fence: the
      danger is the third caller nobody has written yet
- [x] **23.1.4** Settings says which rung, in one line: *This bike only. Your
      rides are recorded here and stay here* / *Backed up to your account*.
      Both seen on the AVD
- [x] **23.1.5** No toggle, no greyed-out control and no mention of Supabase
      for a rider with no account. The old copy explained that *this build* had
      no credentials configured, which is a fact about a developer's machine
      offered to a rider as if it were about them
- [x] **23.1.6** One behaviour for all three reasons to be offline — no
      account, no credentials, backup switched off. `CloudAccess` collapses
      them deliberately, and `CloudAccessTest` holds it to that

> **A fourth violation of rule 1 turned up that this plan had not listed.**
> `UserRepository.save` upserted a profile's name, weight and FTP to the cloud
> on every create, rename, FTP change and weight change — so a rider who never
> signed in had their name in Supabase from the moment they typed it. It goes
> through the same door as everything else, so the gate closed it too, but the
> plan's list of three was wrong and the way it was found is worth keeping:
> grepping for the *client*, not for the features known to use it.

> **The consequence to state plainly: no build can reach the cloud now.**
> Nothing sets `auth_user_id` because Phase 15 does not exist, so every profile
> is offline and every cloud call returns `Disabled`. That is rule 1 working
> rather than a regression — but it makes **14.1.6 unreachable from the app**
> until sign-in is built, and any verification of the cloud path before then
> needs the column set by hand (one `UPDATE profiles SET auth_user_id=…`
> against the tablet's database, which is how 23.1.2 was observed).

### 23.2 The class library, offline

**The size of this: `assets/classes` holds 5 class JSONs; the cloud holds 72.**
Under the old model that was a fallback a configured build would never hit.
Under this one it is the default library, and the class library is most of
what the app *is*.

- [x] **23.2.1** All 72 bundled: **104 KB of JSON, 9 KB once the package is
      compressed**, which is the non-decision it was predicted to be. Named by
      class id and grouped by category, and checked at build time by
      `ClassLibraryAssetsTest` — every class decodes, ids are unique, intervals
      parse, and (the one worth having) intervals are contiguous and end
      exactly on `duration_sec`. A gap leaves the rider with no prescription
      for the seconds it spans and nothing on screen would say so
- [x] **23.2.2** `ClassTemplateSeeder` no longer knows Supabase exists.
      Observed on a cleared install: *Seeded 72 class templates from assets*,
      no `SupabaseSync` line at all, seven categories in the Class Library and
      a newly bundled VO2 Max class drawing its 12 intervals
- [ ] **23.2.3** The cloud becomes an **update channel** for a signed-in rider:
      additive, and never deleting a class anyone has ridden. `workouts` has a
      foreign key onto `class_templates`, so a class disappearing takes the
      history's link to it with it
- [ ] **23.2.4** `updated_at` (or a version) on `class_templates` so an update
      is a diff rather than a re-seed, and so a rider who has ridden 40 classes
      does not re-download 72
- [x] **23.2.5** Both `intervals_json` shapes still decode —
      `ClassTemplateDtoTest` covers the pair and `ClassLibraryAssetsTest` now
      decodes all 72 shipped files through the same DTO, so the assets are
      exercised rather than assumed
- [x] **23.2.6** **The classes themselves are not good enough — rebuild the
      library.** *Done and observed.* The owner's verdict after riding one. Bundling all 72 (23.2.1)
      fixed *how many* a rider gets and says nothing about whether they are
      worth riding, and they were generated rather than designed. Before
      regenerating, write down what makes one good: a real warmup and cooldown,
      progression that goes somewhere, interval lengths a rider can hold,
      cadence targets that match the zone asked for (a Z5 effort at 85 rpm and
      one at 105 rpm are different workouts), and enough variety across the
      library that two classes of the same length are not the same class.
      **Three constraints that are not negotiable**: `workouts.class_id` is a
      foreign key, so an id that has been ridden must keep existing or the
      rider's history loses its link (23.2.3); `ClassLibraryAssetsTest` already
      enforces contiguity and `duration_sec` agreement, so a bad generator
      fails the build rather than the ride; and the cloud copy has to move with
      the assets or the update channel (23.2.3) will hand back the old ones

      **What was wrong, measured rather than read.** The old 72 were generated
      by slicing percentages off a duration, and it shows in four artefacts:
      770 intervals carrying **101 distinct lengths**, only 213 of them a whole
      number of minutes (nobody rides a 97-second climb); **twelve distinct
      sequences of zones across all 72 classes**, with SS-04…07 *and* TH-01…07
      sharing one, so Sweet Spot and Threshold were not merely repetitive but
      the same category; cadence a pure lookup from the zone, which made "a Z5
      at 85 rpm and a Z5 at 105 rpm are different workouts" unsayable; and
      `TB-01` prescribing sixteen consecutive Tabata rounds with no break
      between sets, which the sixteenth round is not honest about.

      **What replaced it.** `classlibrary/` holds the rules (`README.md`), the
      72 sessions written in blocks of real time (`catalogue.py`) and a
      generator that refuses to write if a session breaks a rule it can check
      (`build.py`). Eleven rules, most of them also asserted in
      `ClassLibraryAssetsTest` against the emitted JSON, because the assets are
      what ships and a generator nobody runs cannot vouch for them. The result:
      **20 distinct block lengths, 51 distinct zone sequences**, four zones
      ridden at three or more cadence bands, progressive warmups with primers
      before any Z5 work, and recovery proportionate to the effort it follows.
      Seven categories, two renamed — "HIIT & Heavy Climbs" was two categories
      with one name, and only some of "Tabata Bursts" was Tabata.

      **New ids, not reused ones** (END/REC/SWT/THR/VMX/CLB/SPR). Reusing them
      was the other option and it is wrong for the same reason 2.7.5 came down
      the way it did: the bike holds a real twenty-minute ride on `HC-01`, and
      changing what `HC-01` *is* silently rewrites what that ride was. Mark and
      say so; change nothing behind the rider's back

- [x] **23.2.6c** **The rebuild has to reach a bike that already seeded.**
      `seedIfEmpty` stopped at "is the table empty?", which was right for as
      long as the library never changed — a tablet with the old 72 would have
      kept them forever. Seeding is a reconcile now, gated on a fingerprint in
      `assets/class_library.json` so the common case is one small read rather
      than seventy-two. A class the bundle drops is **retired** if a ride
      points at it (`class_templates.retired_at`, migration 4→5) and deleted if
      nobody rode it, so a fresh install carries no ghosts and the bike keeps
      exactly the ones it needs.

      **And a latent data-loss bug fell out of building it.** `ClassTemplateDao`
      used `OnConflictStrategy.REPLACE`. SQLite implements REPLACE as a delete
      plus an insert, and the delete fires foreign-key actions — so re-inserting
      a class somebody had ridden would run `workouts.class_id`'s
      `ON DELETE SET NULL` and detach every one of those rides. Measured against
      `sqlite3` rather than reasoned about. It is `@Upsert` now. Harmless while
      seeding only ever ran on an empty table; **23.2.3 would have walked
      straight into it**.

      *Observed on the tablet AVD set up to match the bike — two rides attached
      to old classes, then the new build installed: "Dropped 70 classes nobody
      had ridden / Retired 2 classes that history still points at [HC-01,
      RC-05]". 74 rows, 72 live, both rides still resolving to their old titles.
      The library browser shows the seven new categories and none of the retired
      classes; History still says "Hill Grind 20". A second launch logs nothing
      at all.*

### 23.3 What the offline tier must already contain

Not new work — a checklist of what "offline is not degraded" cashes out to, so
that a future phase cannot quietly move one of these behind an account.

Local history and detail (12.1, 12.2), delete (12.3), per-ride export (12.4.3),
whole-database backup and restore (12.4.4 / 19.1.3), the post-ride charts
(16.1), units (13), profiles (20), heart-rate zones (21), the dashboard (22)
and household social (24). **Every one of these is either done or planned with
no cloud dependency**, which is the good news in this phase.

- [x] **23.3.1** Backup is the offline rider's only durability story and it is
      manual. A gentle reminder after N rides since the last backup, or a
      change of tablet, is the offline answer to what the cloud tier gets for
      free. It must be a reminder and not a nag.

      *Done and observed.* N is **ten rides**, which is about three weeks for
      somebody on the bike three or four times a week, and long enough that
      nobody meets it in their first fortnight. "A reminder and not a nag" is
      the whole design and it cashes out to four decisions, three of them in
      `BackupReminder` where they are tested against a count rather than
      against a screenshot:
      - **Counted in rides, not in days.** A rider who has been off the bike
        for a fortnight has lost nothing since their last backup and does not
        need telling. Time passing is not risk; unbacked riding is
      - **"Not now" moves the line rather than silencing it.** One mark serves
        both a backup and a dismissal, because the reminder only ever asks one
        question — how much riding has happened that a backup would not have
        covered. The rides already recorded stop asking; the next ten earn the
        next reminder
      - **Never having backed up does not lower the bar.** The temptation is to
        treat it as urgent; it is not. A rider three rides in has nothing to
        lose yet, and an app that opens with a warning is one whose warnings
        are ignored by the day they matter. It changes the sentence, not the
        threshold
      - **A card on the dashboard, under the actions, in tertiary and not
        error.** Nothing has gone wrong. It is a fact about where the rides
        live, and a modal on launch is how that fact gets dismissed by reflex.
        "Back up" goes to Settings rather than raising a second picker: the
        backup flow exists once, including the sentence saying how many bytes
        landed

      Two smaller things worth keeping. The count is **across the whole
      tablet**, not the selected profile, because the backup file is the whole
      database — a housemate's rides and a guest's ride are equally in it and
      equally lost without it. And the mark is written **only on success**:
      recording a failed backup would tell the rider they are safe on precisely
      the day they are not.

      *Observed on the tablet AVD across all three paths — the card at 12
      unbacked rides; "Not now" clearing it and surviving a force-stop, with
      `has_ever_backed_up` still unset because a dismissal is not a backup; the
      reminder returning at 14 once more rides landed; and a real backup
      through the picker writing 405,504 bytes to Downloads, setting the flag
      and clearing the card.*
- [ ] **23.3.1a** **Whose backup, once accounts exist?** The reminder counts
      rides on the tablet and says nothing about sign-in, which is right today
      because no profile can have an account (15 does not exist) — but it is a
      real question and not an oversight. Cloud backup is **per profile** and
      the backup file is **per tablet**, so a household where one rider signs
      in still has everybody else's rides in one place only. The likely answer
      is that the reminder counts *unsynced* rides rather than all of them, and
      it belongs to whoever builds 15
- [x] **23.3.2** The Backup section says it: *copy it somewhere safe and it can
      be restored onto any tablet running Pelonot*. Reworded for a signed-in
      rider too, where "your rides live on this tablet and nowhere else" had
      become false

### 23.4 Retention and trimming — the local database is the one that fills

***The owner asked for this on 3 August 2026, so the "not yet" below is
lifted*** — in their words, alongside the endpoint decision (14.10.4):
*"we will implement auto-cleanup where old rides are condensed to just basic
information rather than full tick-by-tick record."* That is 23.4.2 exactly, and
the items under it were already the right ones. What follows is the original
framing, kept, plus what the eighteenth sitting's work changed about the
prerequisites (23.4.8–23.4.11).

**The original framing, still correct about where the pressure is.** The
numbers are in *What a workout costs* and they say the cloud is not the
constraint: four riders at a ride a week is ~6 MB of Supabase a year against a
500 MB allowance. What the same measurement *did* show is that the local
database fills seven to ten times faster, and that the 12.4.4 backup file is a
full copy of it. So this is a local feature with a cloud counterpart.

**What the owner's answer changes is the *reason*, not the design.** It is no
longer "the tablet is getting full" — it is that a household endpoint should
not accumulate tick-by-tick records forever when nothing reads most of them.
23.4.1 still stands: measure the real thing first, because everything below is
sized off a model that a real bike can contradict.

- [ ] **23.4.1** **Measure the real thing before building any of it.** On the
      bike: `SELECT COUNT(*) FROM workout_metrics`, the file size of
      `pelonot_database`, and how many rides are actually on there. A year of
      household riding is ~61 MB by the model; if the tablet says something
      else, the model is wrong and everything below is sized off it
- [ ] **23.4.2** Trim = drop the `workout_metrics` rows for rides older than a
      chosen age, keeping the workout row, its aggregates, its time-in-zone and
      a **downsampled trace** (10 s buckets is ~30× smaller and still draws a
      recognisable ride). Peaks preserved by min/max per bucket, exactly as
      16.2.2 already does for drawing
- [ ] **23.4.3** **A trimmed ride is marked as trimmed** — a
      `metrics_detail_sec` column, or equivalent — and every chart and export
      says what resolution it is showing. A coarse line drawn as if it were the
      record is the same defect family as 7.8 and 16.1.6: a derived number
      whose provenance was thrown away. This column is the whole discipline of
      the feature; without it, do not ship it
- [ ] **23.4.4** **Off by default, and never silent.** The rider chooses the
      age, or chooses never. An app that quietly deletes the second-by-second
      record of a rider's best ever ride has done the thing this project exists
      not to do
- [ ] **23.4.5** Offer the export first — 12.4.3 (one ride) and 12.4.4 (all of
      it) both already exist, so this can be an honest offer rather than a
      warning
- [ ] **23.4.6** Never trim a ride that has not reached the cloud, for a rider
      who has an account. Needs 14.2.4's `synced_at` to be knowable at all
- [ ] **23.4.7** The cloud counterpart is the same policy applied server-side.
      **The owner has now asked for it** (14.10.4), so it is wanted rather than
      hypothetical — but the rule it was written with is unchanged and is the
      important half: it trims the ***payload*, never the workout row.** The
      aggregates are what the history, the trends and the leaderboards read, and
      a workout row costs a few hundred bytes against the payload's ~30 KB, so
      deleting it saves nothing and destroys everything

---

**What the eighteenth sitting changed about the prerequisites.** Trimming is
the one feature in this project that *destroys* data on purpose, so the
question is not "can we drop the rows" but "what silently reads them". Four
things do, and three of them are wrong afterwards in ways nobody would notice.

- [ ] **23.4.8** **16.3.3a is now a hard prerequisite, not an optimisation.**
      `WorkoutRepository.personalBests` re-scans **every measured ride's
      samples** on every load of *Your FTP* — that is how mean-maximal power is
      computed today. Trim a rider's older rides and their five-second and
      twenty-minute bests **silently get worse**, because the rides that set
      them no longer have the seconds in them. The rider is not told; the number
      simply drops, and it drops on the screen that exists to show that their
      training is working. 16.3.3a's fix — per-ride bests computed once at
      recording and stored on the row — is what makes trimming survivable, and
      it has to land **first**, because a best that was never computed cannot be
      recovered from a trimmed ride
- [ ] **23.4.9** **Audit the other three readers and say what each does with a
      trimmed ride**, rather than finding out from a chart. They are:
      `RideDetailViewModel` (the ride's own charts — 16.1, and the 24.3.1
      housemate trace and 16.3.4 previous-best comparison drawn behind it),
      `PostRideViewModel` (the post-ride analysis, which only ever reads a ride
      minutes old and is therefore safe by construction), and
      `WorkoutSyncWorker` (which uploads the payload — see 23.4.6, and note the
      ordering is now enforceable because `synced_at` exists). **Calibration is
      *not* one**: `CalibrationRepository` accumulates its grid live and stores
      it serialised, so it never re-reads `workout_metrics` and trimming cannot
      touch this bike's power curve. Checked rather than assumed
- [ ] **23.4.10** **A signed-in rider's cloud copy makes trimming reversible,
      and an offline rider's does not.** That is the most interesting thing the
      connectivity model does to this feature and it must not be papered over:
      for a rider with an account, local trimming is a **cache eviction** and
      the full series can be pulled back on demand; for a rider on the middle
      rung it is **deletion**, full stop. Two different features wearing one
      name. Either build only the offline-safe half and say so plainly, or build
      rehydration as its own item — but do not offer one confirmation dialog
      that means different things to two riders on the same tablet
- [ ] **23.4.11** **Retention on a shared household endpoint is a policy about
      other people's data.** 23.4.7 applies server-side, and the endpoint now
      has *"one or two friends"* on it (14.10.4). A server-side trim decided by
      whoever runs the project deletes a friend's record without asking them,
      which is a different act from a rider trimming their own tablet. Decide
      whether it is per rider and rider-controlled — and note this is the first
      item in the project where one person's setting reaches another person's
      history

---

### 23.2.7 A class that says what it is for — the owner's note, 5 August 2026

**Verbatim, the second half of the Start Class note (22.7.5):** *"And maybe we
could spend some time adding a rich 'description' to each class which tells you
what the ride is and what it focuses on. That is more useful."*

**It is more useful, and the reason is worth stating rather than agreeing with.**
Everything the library tells a rider today is *derived* — the title, the
category, the duration, `ClassProfile.shape`'s generated sentence, the chart.
All of it is a description of the **blocks**, computed from them, and none of it
can say a single thing the blocks do not contain. So the library can tell a
rider that a class is "20 min · Climbs · four hard efforts" and it cannot tell
them **why they would ride it**, what it is training, who it suits, or what it
is going to feel like at minute fourteen. That is authored knowledge and there
is nowhere to put it.

**This is the first genuinely authored prose in the library**, which makes it
the first thing in `catalogue.py` that a build rule cannot check for truth. That
cuts both ways and both belong in the item: it is why it is worth doing (a
generator cannot produce it) and why it needs its own rules (a generator cannot
police it either).

- [ ] **23.2.7a** **`description` is a field on the class, authored in
      `catalogue.py`, carried by `build.py` into the asset JSON.** Follow the
      existing path exactly — `to_json`, the entity's new column, a migration
      (12.5, and `class_templates` is `@Upsert` since 23.2.6c, *not* REPLACE),
      the exported schema, a `MigrationTestHelper` test, and the fingerprint in
      `assets/class_library.json` so an already-seeded tablet actually receives
      it. **A catalogue change that is not rebuilt does not reach a tablet**,
      which is the trap CLAUDE.md names
- [ ] **23.2.7b** **Two or three sentences, and the rules are R10's rules one
      level down.** What it says: what the ride *is*, what it *trains*, and what
      it will feel like. What it must not say: the duration, the category or the
      block count — all three are already on the screen beside it, and R10
      exists because the titles were doing exactly that. No FTP, no watts, no
      kilojoules: this is a rider choosing, not reading a measurement, which is
      CLAUDE.md's standing rule and Phase 26's whole argument
- [ ] **23.2.7c** **A build rule for what a build *can* check.** Not the prose —
      `build.py` cannot know whether a sentence is true — but it can hold the
      shape: every class has one, it is within a length band, it does not end in
      a digit (R10's own trap), and it does not contain the class's own
      duration or category. **And it can check the one substantive thing**: a
      description naming a position (*"out of the saddle"*) or a cadence
      character (*"grinding"*) is a promise the blocks have to keep, which is
      R11 and 25.4.2's rule arriving in a second place
- [ ] **23.2.7d** **72 of them is the cost and it is the real one.** This cannot
      be generated from a template without producing 72 sentences that all sound
      the same, which would be worse than none — a rider scanning the library
      would learn to skip them within four classes. Write them per class, in the
      voice Phase 26 describes, and accept that the categories will share
      vocabulary because the rides genuinely do
- [ ] **23.2.7e** **Where it shows.** The Start Class screen is what the note is
      about (22.7.5b) and is where the whole description goes. Whether a line of
      it also belongs on the **library list** is a separate question and
      probably no: that screen is three cards across and already carries a title
      and a shape sentence, so a third line is 26.3's failure mode. Decide it by
      looking, not here
