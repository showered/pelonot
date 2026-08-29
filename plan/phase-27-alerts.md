> Part of the Pelonot plan — the index is [PLAN.md](../PLAN.md).

## Phase 27: Being told something worth knowing — the owner's note, 4 August 2026

**Verbatim:** *"Definitely a nice-to-have for the future but you should get lots
of nice alerts like 'this is the fastest you've ever done X' or 'you completed a
Y streak' or 'your friend just beat your high score on Z'. Please use your
knowledge and intelligence to create appropriate ideas in the plan for this.
Definitely nice-to-have and low priority for now!"*

**This is 19.3.2 — *achievements and streaks* — which has been one line in the
"worth doing eventually" list since the plan was written.** It is promoted to a
phase for the same reason 19.3.3 was promoted to Phase 21: the one line is not
one job. Underneath it are a data question (what counts as a record, and against
what), a timing question (when a rider is told), a frequency question (the whole
feature dies if it fires too often) and an honesty question this project has
already answered three times in other places. The owner's own weighting stands:
**low priority, and nothing here outranks anything in *What to do next*.**

---

### What the app already has, so this is not started from nothing

- **Mean-maximal power by duration** (16.3.3) — the rider's best 5 s, 1 min, 5
  min, 20 min, measured rides only, a gap breaking the window. This is already a
  personal-best engine; what it does not have is a memory of what the bests were
  *before* this ride.
- **Streaks** (24.2.2) — `StreakCalculator`, pure, clock and timezone injected,
  both DST transitions tested, and one decision already made that matters here:
  a streak that ended yesterday still counts today.
- **The household board and the everyone board** (24.1, 18.11) — so "somebody
  beat your score" is a query that already exists on both tiers.
- **The post-ride summary** (12.6) — the screen a rider is looking at in the
  ninety seconds after they stop, which is where most of this belongs.

**The gap is that nothing is remembered.** Every one of those is computed from
the whole history on every load, which is exactly right for a chart and useless
for an alert: an alert is a claim about a *change*, and a number recomputed from
scratch cannot tell you it moved.

---

### 27.1 The rules, before any of the alerts

- [x] **27.1.1** **An alert is written down when it fires, and never fires
      twice.** One row per alert: what it was, which ride caused it, when the
      rider saw it. Without that table there is no way to say "new", no way to
      stop it reappearing on the next load, and no way to build 27.4's list.
      This is the item everything else depends on
- [x] **27.1.2** **The honesty gate is `PowerProvenance`, not the raw column.**
      A personal best from a simulated ride is a fiction the app then
      congratulates the rider for, and `PowerModel` is 137 W out at RMSE. Only
      `Measured` counts — `Unknown` and `Mixed` fail it, on purpose, exactly as
      they do for the FTP proposal (7.10.7) and the household board (24.4.2).
      **The consequence on the emulator is that no power alert can ever fire
      there**, which is a verification cost and is worth paying
- [x] **27.1.3** **A record is a claim about the past, so it must not be
      redrawn by the present.** This is 7.8's trap and 21.2.3's, one column
      further along: anything expressed *relative* to a moving number — a
      percentage of FTP, a heart-rate zone, a training-load score — is a
      different claim the day that number moves. Alerts are therefore built on
      absolutes the rider actually produced: watts, kilojoules, seconds, rides.
      **"Your best-ever Zone 5 time" is the alert not to build**
- [x] **27.1.4** **The first ten rides are all records, and that is the design
      problem.** A new rider's every ride is their fastest, their longest and
      their best, so an unguarded implementation fires six alerts on ride one
      and the rider learns in a week that the alerts mean nothing. Some floor is
      needed and it should be chosen deliberately rather than tuned later —
      a minimum number of prior rides, a minimum margin over the old record, or
      both. **An alert that fires every time is not an alert, it is a caption**
- [x] **27.1.5** **At most one per ride, and the best one.** Falls out of
      27.1.4 rather than being a second rule: if three things happened, the
      rider is told the largest and the other two are on 27.4's list. A stack of
      congratulations is how a good moment is made tedious
- [x] **27.1.6** **Nothing goes on the overlay and nothing interrupts a ride.**
      19.4's standing rule, 18.6's and 24.1.5's. The overlay has half a second
      of attention and it belongs to the next sixty seconds of pedalling; an
      alert is by definition about the past. There is no version of this that
      earns a place there
- [x] **27.1.7** **Retention will silently break this, and the order matters.**
      23.4 condenses old rides to aggregates, and 23.4.8 already says personal
      bests are re-scanned from samples on every load. An alert built the same
      way inherits the same defect *and makes it visible*: a rider would be
      congratulated for beating a record that only fell because the ride holding
      it was trimmed. 16.3.3a — bests stored per ride — is a prerequisite for
      both

### 27.2 The three families, which are not the same feature

- [x] **27.2.1** **Your own record** — *"fastest you have ever done X"*. The
      owner's first example, and the one 16.3.3 already computes. The honest
      framings are the absolute ones: best average power over a class you have
      ridden before, best 20-minute power, most output in a single ride. **X
      should usually be a class**, because a class is the only thing in this app
      that makes two rides genuinely comparable — same intervals, same
      prescription, same length — which is also why 24.1 ranks per class
- [x] **27.2.2** **Your own consistency** — *"you completed a Y streak"*.
      Cheapest of the three: `StreakCalculator` exists, the household panel
      already counts in weeks since 22.5.4, and the alert is a threshold
      crossing. **Count in whatever unit the rider's life actually has**, which
      22.5 settled: at one ride a week, a streak counted in days scores the most
      consistent rider this app can have at 1
- [ ] **27.2.3** **Somebody else** — *"your friend just beat your high score on
      Z"*. The interesting one, the only one needing the network, and the only
      one that is about a thing the rider was not present for. It is also the
      only one with a social cost: being told you have been beaten is a
      different message from being told you did well, and a household that
      shares a bike will read it out loud. **Off by default is likely the right
      answer and should be decided rather than defaulted**
- [ ] **27.2.4** **A fourth family the note did not name and this app is unusually
      well placed for: the record you did not notice you were near.** The app
      knows the rider's mean-maximal curve and the class's intervals before the
      ride starts, so *"your best 20 minutes was on this class"* is knowable at
      selection time. It is the one thing here that could change what a rider
      does rather than only how they feel afterwards. It is also the one that
      most easily becomes pressure, so it belongs behind 24.3.3's explicit
      choice to race rather than on every class card

### 27.3 Where a rider is actually told

- [x] **27.3.1** **The post-ride summary is the moment**, for everything in
      27.2.1 and 27.2.2. The rider is holding the tablet, breathing hard, and
      already looking at what they just did — no notification, no permission, no
      delivery problem. One line above the figures, in the summary's own voice
      (Phase 26): *"Your best 20 minutes on this class."* Not a banner, not a
      trophy, not a modal over the charts
- [ ] **27.3.2** **A card on the dashboard for anything that happened while the
      rider was away**, which is 27.2.3 and only 27.2.3. It is read at the top
      of the next session, which is soon enough for something that cannot be
      acted on anyway, and it costs no permission and no background work
- [ ] **27.3.3** **System notifications are a separate decision and probably
      no.** They need `POST_NOTIFICATIONS` on API 33+, and the manifest trap in
      CLAUDE.md applies — a runtime permission the manifest does not declare is
      denied instantly with no dialog and nothing in logcat, which has caused
      two defects here already. More to the point, this is a bike tablet in a
      garage: it is not carried, and a notification on it is read at exactly the
      moment 27.3.2's card would have been. **Build 27.3.2 and see whether
      anybody misses this**
- [x] **27.3.4** **The spoken coach must not read these out.** It ducks under
      the rider's film (8.x, the audio-focus work) and its budget is
      instructions for the next interval. A congratulation delivered over
      somebody's Netflix at minute 34 is the same category error as putting one
      on the overlay

### 27.4 The list, and the way out

- [x] **27.4.1** **Everything that has ever fired, on one screen**, off the
      dashboard's progress section beside *Your FTP* and *Your riding*
      (16.3.1/16.3.2). It is where 27.1.1's table pays for itself, it is the
      answer to *"what did that say?"*, and it is the only place a rider can see
      the shape of a year
- [x] **27.4.2** **Turning them off is one switch and it is honoured
      everywhere**, including 27.3.2's card. A rider who does not want to be
      graded is not asking for a quieter version of being graded
- [x] **27.4.3** **It works entirely offline.** 27.2.1 and 27.2.2 are Room
      queries over the rider's own rides and must never wait on, degrade
      without, or hint at a network — rule 1 of the connectivity model, and rule
      3's argument that the ungated tier is a complete product rather than a
      trial of the paid one. Only 27.2.3 needs an account, and its absence is
      silence rather than a locked feature

---

### What was built, 29 August 2026 — and the four decisions the items left open

**Fourteen of the eighteen boxes, and the four left are the two families that
need somebody else and the two surfaces that only those families use.** 27.2.3
(*your friend beat you*) needs the network, 27.2.4 (*the record you did not
notice you were near*) belongs behind 24.3.3's explicit choice to race, 27.3.2's
card is for 27.2.3 **and only 27.2.3** — the *Your records* card built here is
27.4.1's door and not that — and 27.3.3 stays a *probably no* until somebody
misses it.

**The items set four questions and left them open on purpose. The answers:**

**The floors (27.1.4) are a prior-ride count and a margin, both.** Five prior
rides before any claim about a rider's whole history; **two** before a class
record, which is lower because a class is already the narrower claim and because
a library of 72 makes a third ride of one of them rare on its own. The margin is
**two percent** — about 5 W on a 250 W twenty minutes, which is under a rider's
day-to-day variation, and calling that an improvement is what turns an alert
into a caption. `AlertRules` holds all three as named constants.

**The ranking (27.1.5) is by what the claim *is*, because the arithmetic does
not exist.** 8 weeks and 412 kJ cannot be compared, so "the largest" needed a
judgement: a streak first (rarest by construction — it can only fire at a
milestone — and the only one about the rider's life rather than one ride), then a
class record (27.2.1's own argument: a class is the only thing that makes two
rides genuinely comparable), then power windows longest-first, then most output,
then longest ride — last, because riding for longer is a choice rather than a
performance.

**The switch (27.4.2) is honoured by not detecting.** Nothing is judged and
nothing is written while it is off. The cost is written into the setting's own
second line: records set while it is off are not found again when it comes back
on. The records themselves are unaffected — they live in `workouts` and
`workout_power_bests` — so what is lost is the history of having been told,
which is the only thing the switch is about.

**And there is no back-fill (22 → 23), though one was available.** Every record
this phase fires on is derivable from rows already on disk, which is what makes
this different from 17 → 18's refusal. It is refused because of what an alert
*is*: a claim about a change at the moment it happened. Forty of them dated
tonight would tell a rider they set forty records this evening, and 27.4.1's
screen — the one place a rider can see the shape of a year — would draw a single
stripe. 28.4.2 arrives at the same conclusion about badges from the other side.

### 27.5 What the tablet said that the tests could not

- [x] **27.5.1** **`finaliseWorkout` is not the choke point it looks like, and
      27.1.1 shipped judging only half the rides because of it.** `recoverWorkout`
      completes a crashed ride with its own `updateWorkout` and a direct call to
      `recordPowerFacts` — so a ride recovered after a crash was judged for
      records by nothing at all. 959 JVM tests were green through it, the rules
      were right, and the only way to see it was to recover a ride on the AVD and
      find an empty `rider_alerts`. **`WorkoutRepository.recordRideFacts` is the
      real shared place now** and both callers use it; `finaliseWorkout`'s KDoc
      says so where the next person will be standing. The backfill deliberately
      still calls `recordPowerFacts` alone, because it must never fire
      retroactive alerts
- [x] **27.5.2** **The dashboard's card disagreed with the summary about the same
      ride.** One ride writes its whole set in a single insert under one
      timestamp, so ordering by `recorded_at DESC, id DESC` made the tie-break
      *last written wins* — the card said *"Your biggest ride yet"* about a ride
      whose summary had just said *"Your best ride of Zone 2 Steady"*. The rules
      write in rank order and the autoincrement preserves it, so **`id ASC`
      inside a moment** is the ordering. Same family as every other "two screens,
      one ride, two answers" defect in this plan
- [x] **27.5.3** **A full stop on a card among three without one.**
      `AlertWording` has `phrase` and `headline`: the summary and the record book
      get the sentence, the dashboard's card gets the label. Two forms rather
      than a `trimEnd`, because the difference is real — on the summary the app
      is saying something to the rider and it ends; on a card it is a label under
      `Your records`, beside `Last ride` → `Zone 2 Steady`
- [x] **27.5.4** **The card is what 22.9.4 was looking for.** With the backup
      reminder answered, four glance cards sit abreast of the household panel
      with no scroll — it fills the ~110 dp the dashboard has been honestly empty
      since 22.8, and it is conditional, so a rider who has earned nothing still
      sees the same three cards they always did
- [x] **27.5.5** **`PowerModelFenceTest` fired on a formatter.** Its regex is
      `\.watts\(`, aimed at `PowerCurve.watts(cadence, resistance)`, and
      `Formatters.watts(` had **no callers anywhere in the app** until 27.3.1
      wanted to say `261 W` — so the first use of a formatter that has sat in
      `core` for months failed a fence about the power model. Excluded by name,
      narrowly. A fence that fires on formatting teaches the next person to route
      around it, which is the one failure a source scan cannot recover from

- [x] **27.5.6** **A ride resumed under 12.6.2 kept the records its first half
      earned.** `resumeInterruptedWorkout` already clears `synced_at`,
      `power_bests_at`, `power_provenance` and the stored efforts, each with a
      written reason that applies here word for word — the ride is about to get
      longer, so every derived fact about it is the short version's — and the
      alerts were the one derived fact nobody had added to the list. Without it,
      a rider who ends a 25-minute ride by accident is told about their best
      twenty minutes, carries on to 45, and is never told again about the ride
      they actually did. **This is the one place an alert is deleted, and it is
      deliberately allowed to take back something already shown**: the longer
      ride's twenty minutes can only be as good or better, so the rider ends up
      told once about one ride rather than once about its first half. It is not
      28.1.1's *never revoked* in miniature — that rule is about a ride the
      rider finished

      *Watched: five alerts and the headline on screen, `Carry on riding`,
      **zero** in the table, `resume_count` at 1.* And then the second finalise
      wrote **nothing**, because the one simulated second the emulator recorded
      turned a 1800-sample `Measured` ride into a `Mixed` one — 27.1.2's gate
      refusing every watt over a single modelled sample, which is what it says
      it does. **On a real bike the resumed minutes are measured too**, so
      provenance stays `Measured` and the records re-fire at their new values;
      the untelling seen here is the simulator, not the rule

### What it was watched doing

Two hand-built fixtures on the 56-ride five-profile database, restored
byte-for-byte afterwards, and the migration observed arriving on a real history
**twice** — empty both times, which is 22 → 23's decision put as plainly as it
can be.

**A measured thirty minutes recovered through the crash path.** Five alerts
written in rank order — `ClassOutput`, then the 1200/300/60-second windows,
then `RideOutput` — with **only the headline marked seen**, which is 27.1.5 in
one column. The summary drew *"Your best ride of Zone 2 Steady."* above the
figures. Two correct absences are the better evidence: the **5-second** window
did not fire, because the ride did not beat it, and **`RideDuration`** did not,
at 1799 s against a 1800 s best — the margin refusing a tie.

**A modelled ride in a rider's fourth consecutive week**: *"Four weeks in a
row."*, one row, and **no records at all** — the honesty gate letting a clock
reading through while refusing every watt, and the prior-ride floor holding at
three rides. The FTP breakthrough dialog appeared on the first fixture and not
the second, which is a free re-confirmation of 7.10.7 from the same gate.

**The switch off took the card off the dashboard** and left the other three;
back on brought it back.

