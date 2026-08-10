> Part of the Pelonot plan — the index is [PLAN.md](../PLAN.md).

## Phase 28: Achievements — things the rider owns, not things the app says

**The owner's note, 10 August 2026, verbatim:** *"One for the backlog. Gamify it
all even further. Reach milestones and get an achievement. And any other
achievements you can think of. Tie this into the dashboard."*

**The owner's weighting is on the face of it — *one for the backlog* — and it
stands.** Nothing in this phase outranks anything in *What to do next*. It is
written at length for the reason Phase 27 was: the one sentence is not one job,
and the parts of it that are easy to get wrong are the parts that would be
decided quickly in a hurry.

---

### Why this is not Phase 27 with a different name

Phase 27 is *being told something worth knowing*. This is *having something*.
They sound like one feature and they fail differently:

- **An alert is an event.** It fires, the rider reads it, it is over. Its
  failure mode is **frequency** — 27.1.4's whole argument is that an alert that
  fires every ride is a caption.
- **An achievement is a possession.** It is named, it is finite, the rider can
  see the ones they do not have yet, and **it is never taken away**. Its failure
  mode is **meaning** — a set anyone earns in a fortnight is a participation
  certificate.

The relationship is one-directional and it decides the build order: **Phase 27
is the delivery mechanism and this phase must not build a second one.** Earning
an achievement is exactly the kind of thing 27.3.1 puts a line about on the
post-ride summary. If Phase 28 is built first it will grow its own toast, its
own dashboard card and its own "at most one per ride" rule, and then Phase 27
arrives and there are two.

**And it is the honest form of the thing 26.4 was right to refuse.** The owner
offered to leave 26.4 — a single score shown like a game's level — and this plan
agreed, because *"a score built on FTP is 26.1.1's defect with the unit filed
off"*: a made-up scalar, presented with the authority of a measurement. An
achievement is the opposite shape. Each one is **a discrete, nameable, true
sentence about something the rider actually did** — *you have ridden a hundred
times*, *you have ridden every class in this collection*. It is gamification
that cannot lie, because there is nothing in it to round off. *That is the
reconciliation between the owner's two notes, and it is worth having in writing
rather than leaving them to disagree quietly.*

---

### 28.1 The rules, before any of the badges

- [ ] **28.1.1** **An achievement is never revoked.** The rider earned it on the
      day they earned it and no later event may take it back. This is not
      sentiment, it is the same defect this project has met three times: 7.8's
      ride chart redrawn by a *current* FTP that has since moved, 27.1.3's
      records expressed relative to a moving number, and 27.1.7's bests falling
      because retention trimmed the ride holding them. **The concrete traps
      here**: 7.11 lets auto-FTP go *down*, so an FTP-gain badge computed live
      would un-earn itself; 23.4 condenses old rides, so a ride-count badge
      recomputed from samples would too. **The award is recorded, not derived.**
- [ ] **28.1.2** **`PowerProvenance` gates anything derived from watts**, and
      nothing else. 27.1.2's rule, and the split matters more here than there
      because most of the catalogue is on the free side of it: **a count of
      rides, a duration and a date are the same quantity whoever measured
      them**, so volume and consistency badges need no gate at all. Kilojoules,
      average power and FTP do — `PowerModel` is 137 W out at RMSE and a
      milestone built on it is a fiction the app then hands the rider as a
      trophy. **The consequence is the usual one: no power badge can be earned
      on the emulator**, and the ones that can be are the ones worth building
      first.
- [ ] **28.1.3** **No achievement may reward something a coach would advise
      against.** A rest day is training. This rules out the obvious gamified
      shapes: *rode 7 days in a row*, *two rides in one day*, *rode every day
      this month*, anything for riding at 3 a.m. **22.5 already made this
      decision once without calling it a rule** — the streak counts weeks, and
      the reason given was that at one ride a week a day-streak scores the most
      consistent rider this app can have at 1. The health argument points the
      same way as the honesty one, which is usually a sign the answer is right.
- [ ] **28.1.4** **The set is finite, and the rider can see what they have not
      got.** This is what makes it a collection rather than a slot machine, and
      it is also the difference between an achievement and 27.2.1's record: a
      record is *better than last time* and has no end; an achievement is *this
      specific thing*, and the ones still grey are the interesting half of the
      screen. It has a cost — an unearnable badge is a permanent reproach — so
      **nothing in the catalogue may depend on hardware or an account the rider
      does not have** (see 28.3.6).
- [ ] **28.1.5** **It works entirely offline**, rule 1 of the connectivity
      model. Every family below except 28.3.5's across-bikes half is a Room
      query over the rider's own rides. Nothing here waits on the network,
      degrades without it, or shows a locked badge that an account would unlock
      — that last one is rule 3's argument, and a greyed-out *sign in to earn
      this* is exactly the trial-of-the-paid-tier shape the model forbids.
- [ ] **28.1.6** **The names are prose, and there are no points.** Phase 26: an
      achievement is read once, so it says what happened in the app's own voice
      — *A hundred rides*, *Every class in Endurance* — not `CENTURY CLUB` and
      not `+50 XP`. No score, no level, no total (26.4). **The count of badges
      earned is the only number this phase may show**, and it is a fact rather
      than a rating.

### 28.2 Where it lives

- [ ] **28.2.1** **One ledger or two — decide this before writing either.**
      27.1.1 is a table of alerts that have fired; this phase needs a table of
      achievements earned. They are nearly the same row — *what, which ride,
      when, has the rider seen it* — and the recommendation is **two tables and
      one delivery path**: an alert is transient and an achievement is
      permanent, so an alert row may be pruned and an achievement row may never
      be (28.1.1). Whichever way it goes, **it is a migration with an exported
      schema and a `MigrationTestHelper` test** (12.5), and it is `@Upsert` and
      not `OnConflictStrategy.REPLACE` — see CLAUDE.md for what that has cost
      this project.
- [ ] **28.2.2** **Awarding happens once, at the end of a ride, in one place.**
      `stopWorkout` is where a ride becomes a fact, and CLAUDE.md's rule applies
      directly: anything written to `workouts` during a ride is reverted by the
      finalise. Awarding after the row is complete avoids that entirely. **It
      must not run on the main thread and must not delay the summary** — the
      summary can show a badge that arrives a moment later; it cannot show a
      spinner.
- [ ] **28.2.3** **The catalogue is data, not code, and it is versioned.** The
      class library learned this the hard way (23.2.6): 72 classes generated
      from a catalogue with build-time rules, because hand-edited assets drift.
      A badge list is smaller but has the same property — it will be edited by
      somebody adding "just one more" — and the rule it needs is the one
      `build.py` enforces: **a definition that has ever been awarded may be
      retired but never deleted or redefined**, exactly as `class_templates
      .retired_at` works. A rider holding a badge whose definition changed
      underneath them holds a different badge.

### 28.3 The catalogue — the owner's *"any other achievements you can think of"*

**Ordered by how much of the work is already done**, which is also roughly the
order they should be built in. Every one names what it is computed from.

- [ ] **28.3.1** **Volume — the plain milestones, and the whole family needs no
      provenance gate.** First ride; 10, 25, 50, 100, 250, 500 rides; 1, 10, 24
      and 100 hours in the saddle. All of it is `COUNT(*)` and `SUM(duration)`
      over `workouts`, which the dashboard's own queries already window (12.1.6)
      and which never touches `workout_metrics`. **Total kilojoules is the one
      to leave out of this family** and put in 28.3.4 where the gate is, because
      it is the only volume figure derived from watts.
- [ ] **28.3.2** **Consistency — counted in weeks, per 28.1.3.** Four weeks in a
      row, twelve, twenty-six, fifty-two. `StreakCalculator` exists, is pure,
      has both DST transitions tested, and already holds the decision that a
      streak ending yesterday still counts today (24.2.2). **And one for coming
      back**: a ride after thirty days away is the moment a rider is most likely
      to stop for good, and it is the only badge in this catalogue that is
      earned by *failing* first. It is worth having for that reason and it needs
      a name with no irony in it.
- [ ] **28.3.3** **Breadth — the class library, and this is the family this app
      is unusually well placed for.** Nothing else here is possible only because
      of a decision already taken: the bundled library is 72 authored classes
      with real structure (23.2.6), so *every class in this collection*, *a
      class from every discipline*, *your first class over 45 minutes* and *the
      same class five times* are all joins onto `class_templates` that need no
      new data at all. **The last of those is the most useful badge in the
      phase**: riding one class repeatedly is what makes two rides comparable
      (24.1's whole reason for ranking per class), so it rewards the behaviour
      that makes everything else in the app work. **Retired classes are the
      trap** — a collection whose membership changed must not un-complete
      itself (28.1.1, and `retired_at` is how the library already answers it).
- [ ] **28.3.4** **Effort — everything here is behind `PowerProvenance`
      (28.1.2).** Your first measured ride; total output milestones in kJ; an
      FTP that has risen since the first value recorded for the profile, at
      +5%, +10%, +25%. **The FTP family is the sharpest instance of 28.1.1**:
      7.11 lets the number go down, and a badge that appears and disappears with
      it is worse than no badge. Award on the crossing, from `ftp_history`
      (7.9), which is a record of what was true rather than a derivation from
      what is. **What does *not* belong here is "a new best 20 minutes"** —
      that is 27.2.1, a record, and it recurs; the achievement is the *first*
      crossing of a named threshold, not each improvement on it.
- [ ] **28.3.5** **Other people — both tiers, and the household one first.**
      Topped the household board on a class (24.1, a Room query, no account);
      raced and won against a generated ghost (24.3.18); beaten your own
      previous ride of a class (22.1.5's `RideStanding.Best`, already computed
      and already gated). The across-bikes half is Phase 18 and needs an
      account. **28.1.5 decides how the offline rider sees this family: they see
      the household badges as ordinary badges, and the across-bikes ones are not
      in their catalogue at all** — not greyed out, not locked, absent.
- [ ] **28.3.6** **Position, and a note on what must not be in the set.** Phase
      25 knows standing from seated, so *your first ten minutes out of the
      saddle* is real and cheap once 25 lands. **But this is the item where
      28.1.4's cost bites**: heart-rate badges would need a strap the rider may
      not own, and hardware-power badges are unearnable on a simulated setup.
      Anything gated on equipment either stays out of the catalogue or is
      **absent** for a rider without it, on 28.3.5's rule. A permanent grey
      badge saying *buy a chest strap* is an advert.
- [ ] **28.3.7** **Deliberately not built, and written down so it is not
      re-proposed:** anything timed by clock hour (*early bird*, *night owl*) —
      it rewards nothing and 28.1.3 is uncomfortable about half of it; anything
      counting consecutive days (28.1.3); a badge for using a *feature* rather
      than for riding, which is the app congratulating itself; and rarity
      percentages against other riders, which need the community endpoint this
      project does not have (14.10.4) and turn a private record into a
      comparison nobody asked for.

### 28.4 The history that already exists

- [ ] **28.4.1** **Award retrospectively on first run, and do it once.** A rider
      with a year of rides who opens the update to find zero achievements has
      been told their history does not count. The back-fill is a scan of
      `workouts` at migration time — cheap, because 28.3.1 to 28.3.3 are counts
      and joins that never open `workout_metrics`.
- [ ] **28.4.2** **But do not *announce* the back-fill.** Forty badges arriving
      at once through 27.3's delivery path is the frequency failure at its worst
      and would poison the feature on the day it ships. Back-filled rows are
      written already-seen; **the first badge a rider is told about is the first
      one they earn after the update.** They can find the other forty on 28.5's
      screen, which is the right way round.
- [ ] **28.4.3** **The back-fill is where 28.1.1 gets tested for real.** Run it
      twice and the second run must award nothing. Run it against a database
      whose FTP has since fallen and the FTP badges must still be there. Both
      are JVM-testable against a seeded Room database and neither needs a
      device.

### 28.5 The screen, and the dashboard

- [ ] **28.5.1** **One screen, reached from the progress section**, beside *Your
      FTP* and *Your riding* (16.3.1, 16.3.2) and beside 27.4.1's list of what
      has fired. Earned and unearned in one view, because 28.1.4 says the ones
      not yet got are half the point. **`WideGrid`, not `readableColumn`** — a
      set of badges is looked at, not read (CLAUDE.md's three rules), and this
      is the clearest example of that distinction in the app.
- [ ] **28.5.2** **The dashboard's share is one line, and it is 22.8.8's hole.**
      The owner asked to *"tie this into the dashboard"*, and the dashboard's
      own sentence is *should I ride today, and what should I ride* (22.1.1) — a
      wall of trophies answers neither. What does belong is **the most recent
      one, or the nearest unearned one**, as a single line with a door onto
      28.5.1, in the shape `RecentRidingCard` already uses: a fact and a way
      through. *The nearest unearned one is the more interesting of the two,
      because it is the only thing in this phase that answers the first half of
      22.1.1's question* — **three rides to fifty** is a reason to ride today.
- [ ] **28.5.3** **Turning it off is 27.4.2's switch, not a second one.** A
      rider who does not want to be graded is not asking for a quieter version
      of being graded, and they are certainly not asking to configure it twice.

### 28.6 Sync

- [ ] **28.6.1** **Rows sync; awarding does not re-run on the second device.**
      An achievement is a fact about a moment, and a device that recomputed the
      catalogue on pull would date every badge to the day the rider bought a
      tablet. The row carries its own `earned_at` and the ride that earned it,
      and the pull is 15.3.2's, not a new path. **`SupabaseSyncRepository` is
      the only way out** (CLAUDE.md), and every call names the rider it acts
      for.
- [ ] **28.6.2** **Two devices earning the same badge is a conflict and the
      earlier date wins.** It is the easiest merge rule in the project and worth
      stating before somebody implements last-write-wins by accident, which
      would move a badge's date *forward* and quietly contradict 28.1.1.
