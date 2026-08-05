> Part of the Pelonot plan — the index is [PLAN.md](../PLAN.md).

## Phase 24: Household social — the social tier that needs no cloud

Rule 3 of *The connectivity model*: everyone with a profile on this tablet is a
household, and they can compete with each other without an account, without a
network and without a single line of RLS.

**Why this comes before 17 and 18.** It serves more riders — most riders will
never sign in. It needs nothing that does not already exist: the profiles are
in Room, the rides are in Room, `WorkoutDao` already has leaderboard queries.
And **the comparison it makes is the fairest one this app will ever produce**:
the same board, the same knob, the same calibration, usually the same week. A
cross-bike comparison carries 18.7's caveat about modelled watts and per-bike
differences. This one carries none, because there is nothing to caveat.

### 24.1 The household leaderboard

- [x] **24.1.1** `WorkoutDao.householdLeaderboard(classId)` — one row per
      rider, their best ride of that class, best first. Per rider and not per
      ride: a board listing somebody's six attempts is a personal history, not
      a comparison
- [x] **24.1.2** On the post-ride summary and on the class detail screen, where
      it sits *above* the interval list — that screen is where a rider is
      choosing what to ride, and "your housemate did 240 kJ on this one" is the
      reason to pick it
- [x] **24.1.3** Ranked on total output in kJ with kJ/kg beside it. The AVD
      case is the one the design is for: Simon 240.0 kJ / 3.11 kJ/kg first,
      Alex 210.0 kJ / 3.56 kJ/kg second — the two numbers disagree, and both
      are shown
- [x] **24.1.4** Guests excluded by the join onto `profiles`
- [x] **24.1.5** Nothing was added to the overlay, and nothing may be
- [x] **24.1.6** A household of one draws no card at all — seen by marking one
      of the two rides simulated and watching the whole card vanish
- [x] **24.1.7** Falls out of the join rather than needing a decision: a
      deleted profile's rides are `SET NULL`, so they leave the board rather
      than sitting on it attributed to nobody. Their rides survive in history,
      which is what 20.1's dialog already promises
- [x] **24.1.8 The static board has no ceiling, and it is the tallest thing on
      two screens.** The owner's note, 5 August 2026, under *Post ride
      summary*, verbatim: *"Leaderboard has the potential to really throw the
      screen out of alignment when it grows long. Even just with 3, next to the
      'how did it feel' section? I'm not sure. Please have a think."*

      **The note is right and the mechanism is worse than it looks.**
      `ClassLeaderboard.of` places everybody and `ClassLeaderboardCard` draws
      every entry — there is no `LIMIT` anywhere between the query and the
      screen. On the summary the card sits in a `Row` beside `RpeCard` with
      `weight(1f)` each, so the row is as tall as the taller child: at two rows
      the board is shorter than the question and the layout is fine, at eight it
      is a column of names with 200 dp of air beside *how did it feel*. The
      owner noticed it at **three**, which is the number a household of three
      produces on the first night anybody rides the same class.

      **And it is unbounded for a reason that is about to get worse.** 18.11
      removed the friend graph: everyone registered is on everyone's board. So
      the number of rows is not "how many people live here", it is "how many
      people use this app", and the two are different by an order of magnitude
      the moment a second household exists.

      **The answer this plan already owns is 24.3.13's**, and it is worth
      noticing that the live board solved this exact problem three items ago:
      *a window, not a list*. The rows that matter are the ones next to you.
      The static board is not the live one and the window need not be three —
      the top of the board is genuinely interesting *after* a ride in a way it
      is not *during* one — so the shape to reach for is **the podium plus your
      neighbourhood**, with one line saying how many rows are not being drawn.
      Do not simply truncate to the top N: on a board of twelve that shows a
      rider nothing about their own ride, which is the one thing this screen is
      about.

      Three things to check when it is picked up:
      - **Both screens draw this card**, and they want the same ceiling for
        different reasons — the summary because of the `Row` above, class
        detail because 22.7.3 says the whole screen is too busy. One change.
      - **The window must include you even when you are last**, which is the
        same rule and the same failure mode as 24.3.13's sliding window.
      - **Whatever is hidden has to be counted.** *"and 6 more"* is what stops
        a truncated board from being a false claim about the size of the field
        — and it is the fact 24.3.17c deliberately drops from the *live* board,
        so the two boards will disagree on this and should: a rider mid-effort
        cannot act on the size of the field, and a rider reading a summary can

### 24.2 The household, seen

- [x] **24.2.1** Who has ridden this week, on the dashboard, below the rider's
      own numbers and never above them (18.2's rule, applied here). *Observed
      on the tablet AVD: Simon 7 rides / 256 kJ, Kilo 2 rides / 75 kJ.* It
      deliberately does **not** rank — the per-class board (24.1) is a
      comparison because the class is the same, and a week is not the same
      thing for two people
- [x] **24.2.2** Streaks per profile. `StreakCalculator` is pure, with the
      clock and the timezone injected, and **both DST transitions are tests**:
      stepping back a fixed 86 400 000 ms lands an hour inside the wrong day
      twice a year and would break a streak that was never broken, on a date
      nobody would think to test. One decision worth knowing: **a streak that
      ended yesterday still counts today**, because telling somebody their run
      is over before the day is out is both wrong and how a person gives up
- [x] **24.2.3** **Per-profile opt-out**, in the first version as this item
      asked. `profiles.household_visible` (migration 5→6) gates the week *and*
      the per-class leaderboard through one column, because a rider who does
      not want to be seen has not asked to be seen on half of it. Defaults to
      **true** — the value that is *true of the rows that exist*, since
      everyone was already on 24.1's board. That is the opposite conclusion to
      `auth_user_id`'s from the same rule, and the pair is worth remembering:
      a migration must never decide a preference on a rider's behalf.
      *Observed both ways on the AVD, each taking effect immediately*
- [x] **24.2.4** No nudging one household member about another. **Made
      structural rather than remembered**: `householdWeek` is an inner join, so
      a rider with no rides this week is *absent* rather than present with a
      zero. There is no row that could ever be rendered as "Sam hasn't ridden
      this week", which is a stronger guarantee than deciding not to render one

> **Building this found a data-loss bug older than any of it, and the plan
> should carry it where it will be seen.** One toggle in Settings, and the
> rider's seven rides came back with `user_id = NULL` and a dashboard reading
> "No rides recorded yet". `UserDao.insertUser` was
> `@Insert(onConflict = REPLACE)`; SQLite implements REPLACE as a delete plus an
> insert with foreign-key actions firing; `workouts.user_id` is
> `ON DELETE SET NULL`. **So every FTP change, weight change and rename has been
> silently unattributing that rider's whole history for the life of the
> project**, invisibly, because the rides were still there. Same defect as
> 23.2.6c's, in a far busier path, and `workouts` was the third instance
> waiting to go off over `workout_metrics`' CASCADE. All three are `@Upsert`
> now and `UserDaoTest` was checked against the bug as well as against the fix.
> The technique that found it: **build the feature that reads the data, then
> look at the data.**

### 24.3 Riding against a housemate

- [x] **24.3.1** Pick a household member's ride of the same class and draw
      their trace behind yours on the ride detail screen. This is 18.4 —
      arguably the most motivating social feature in the plan — and in this
      tier it costs one query and no schema. **Built and observed**, and it did
      cost one query and no schema. Four things decided on the way:
      - **The measured-power gate applies to both sides.** `householdRivals`
        carries the same `WHERE` clause as the board's, and the *symmetric*
        half is checked in the ViewModel: a modelled trace of **mine** drawn
        against a measured one of theirs is the same lie facing the other way.
        `PowerModel` is 137 W out at RMSE, which on a power chart is most of the
        height of a zone — the comparison would look exact and be fiction
      - **Aligned by absolute elapsed seconds, never stretched to fit.** The
        comparison a rider wants is "at twelve minutes they were at 250 W and I
        was at 210"; rescaling a ride that ran forty seconds longer moves every
        one of their efforts off the block it was ridden in. Buckets past the
        right-hand edge are dropped rather than squeezed in, so the chart runs
        out rather than lying about when their last effort happened
      - **A bare dashed line and nothing else** — no envelope, no second set of
        zone bands, no second prescription. The chart already carries one
        rider's zones and a second full record on the same axes is a graph
        rather than a comparison. The ceiling grows to include the ghost, or a
        stronger housemate is drawn along the top of the box and it reads as a
        tie
      - **Opt-in per tap, and nothing at all when there is nobody** — 24.1.6's
        rule, because an empty comparison is a message about the people who are
        not on it
      - One thing to know before touching the query: it relies on **SQLite's
        bare-column rule** — with `MAX()` over a `GROUP BY`, the other columns
        come from the row the maximum came from. That is a documented SQLite
        guarantee and not standard SQL, and getting it wrong draws the *wrong
        ride* with the right number beside it, which nobody would spot. It has
        a test of its own for that reason.

      *Observed on the tablet AVD against two hand-seeded measured rides of
      `CLB-02`: the chip appears under the power chart, tapping it draws Kilo's
      dashed trace above Simon's with the axis growing from 200 W to 300 W to
      hold it, tapping again clears it — and setting **one** of Kilo's 1200
      samples to modelled removes the whole row.*
- [ ] **24.3.2** A live pace target from that ride *during* a ride is the
      interesting version of it, and it belongs on the full ride screen only,
      never on the overlay (24.1.5). Read 11.6 first; that screen already has a
      target gauge and a zone ladder on it and this must not become a third
      thing competing for the same glance

---

**The live ghost — the owner's note, 4 August 2026.** Verbatim: *"Maybe this is
all already in the plan but i'm not seeing much social 'gamification' anywhere.
Please add appropriate tasks to the plan if they aren't already created. If you
start a class that someone else (or yourself) has already created then there
should be a live leaderboard (aka 'ghost')."*

**Half of it is in the plan and the half that is missing is the half the note is
actually about.** What exists: a per-class board on class detail and the
post-ride summary (24.1), the household's last 30 days with streaks (24.2), a
housemate's trace drawn behind yours on ride detail (24.3.1), and one
everyone-registered board across bikes (18.11). Every one of those is **after**
or **before** the ride. Nothing at all happens *during* one, and that is where a
ghost lives — so the owner is right that it is missing, and right that it is the
motivating version. 24.3.2 above is the item, written in the seventeenth sitting
and never built; it is one line, and the note deserves more than one line.

**"Or yourself" is the part that makes it cheap and the part that makes it
work.** A rider on a household bike will often be the only person who has ridden
a given class, and racing your own previous best needs no second rider, no
account and no network — the same Room query as 24.3.1 with the rider's own id.
It is also the honest floor: a ghost that only appears when a housemate happens
to have ridden the same class is a feature most riders would never see.

**The constraint that shapes all of it is the overlay's.** 24.1.5 and 18.6 both
say nothing social goes on the strip, and they are right for the reason 19.4
gives: the overlay has half a second of attention and it belongs to the next
sixty seconds of pedalling. So the ghost is a **full ride screen** feature, and
the full ride screen is a thing a rider chooses to look at. That is not a
downgrade — it is why the feature can afford to be interesting.

- [x] **24.3.3** **The rival is chosen before the ride, not during it.** The
      class detail screen already draws the board (24.1.2), so the tap that
      starts the class is the natural place to pick who you are riding against —
      a housemate's best, your own best, or nobody. Choosing mid-ride is a menu
      over a rider who is pedalling, and 15.1.6's rule about modals during a
      ride applies to everything, not only to auth
- [x] **24.3.4** **What is actually shown is a gap, in one number, in the unit
      the board already ranks on.** *"+18 kJ"* or *"−4 kJ"* against the rival at
      this second of the ride, in the ride screen's own colour language: ahead
      is not green-means-good, it is the output colour, because a rider behind a
      stronger housemate is not doing anything wrong. **Not a position, not a
      percentage, not a list.** A leaderboard of two is a number
- [x] **24.3.5** **Aligned by elapsed seconds and never stretched**, which is
      24.3.1's decision and the same reasoning: the comparison a rider wants is
      *at twelve minutes they were 18 kJ up on me*. It also makes the ghost
      **cumulative rather than instantaneous** — comparing this second's watts
      to their second's watts flickers, and 11.6.7 already settled that the ride
      screen's numbers change too fast to read
- [x] **24.3.6** **A ghost that runs out says so and stops.** The rival's ride
      ends when it ends: a shorter one leaves the ghost with nothing to say
      after minute 18, and the honest answer is *"they finished"* and a final
      gap, never a line extrapolated forward or a comparison that silently
      freezes. Same family as `isStaleAt` and the gap-not-a-clamp rule.

      **Seen at last, on the leaderboard rather than on the single gap** — it
      had tests and no observation through two sittings, because seeing it
      needs a ride that outlasts its rival's and nothing about a 20-minute
      class makes that convenient. What made it cheap was seeding a rival with
      a **90-second** ride: the state arrives at minute two instead of minute
      eighteen. Worth writing down as a technique, because the same trick
      reaches any "and then it ends" state in this feature.

      *Observed on the tablet AVD, both ways round in ninety seconds:* at
      01:34, `1 GRACE / FINISHED / +6` above the rider with her number frozen
      at her ride's own total while theirs climbed; at 02:47, `LEADING` with
      `1 YOU 23 kJ / 2 YOUR BEST −2 / 3 GRACE FINISHED −3`. **A board turns
      out to be a better home for this than the single gap was**, and for a
      reason the item did not anticipate: a frozen number beside two moving
      ones is obviously frozen, where a single frozen number is
      indistinguishable from a comparison that has quietly broken.
- [x] **24.3.7** **The measured-power gate applies to both sides**, exactly as
      24.3.1 has it. A modelled ghost is `PowerModel` at 137 W RMSE presented as
      a race, and the rider cannot tell. If either side is not measured there is
      no ghost — the offer simply is not made on the class detail screen, which
      is 24.1.6's rule about not drawing an empty comparison
- [x] **24.3.8** **It must survive a pause, a resume and a crash.** The ride can
      be paused (auto-pause), resumed after a crash and reopened after being
      ended by accident (8.3d, 12.6.2), and `elapsedSeconds()` has excluded
      paused time since Phase 3. The ghost reads that clock and nothing else —
      wall-clock anywhere in this feature is a rider who stopped for a phone
      call losing a race they were winning
- [x] **24.3.9** **Nothing about the ghost is written to `workouts`**, and the
      reason is 8.3d.4's rule: `stopWorkout` builds a fresh row from
      `WorkoutSession`, so any column the session does not carry goes back to
      its default. If a future item wants *"you beat Kilo's best"* on the ride
      record — and 27.2.3 does — the field goes on the session first, or it will
      be silently reverted twenty minutes later by the finalise

> **What was actually observed, twenty-eighth sitting**, since six of those
> boxes are now ticked and two are not.
>
> *On the real bike (`PLTN-RB1VQ`, real measured power, migration 15 → 16
> applied to the owner's own seven rides with nothing lost):* the picker
> offering *Your best · 238 kJ* — the owner's real 30-minute END-03 ride —
> with no leaderboard card beside it, because only one rider has ridden that
> class and 24.1.6 says a household of one draws nothing. Then
> `Racing Your best: 238 kJ over 1800s` in logcat with **no** follow-on
> *Dropping the ghost*, which is the measured-power gate passing; the card
> rendering on the ride screen as `YOUR BEST / −1 kJ`; the
> `active_ride_rival` row present in `sqlite3` *while the ride was running*
> and gone after it ended.
>
> *On the tablet AVD, against two hand-seeded measured rides of `CLB-02`:*
> both chips with the right numbers (*Your best · 228 kJ*, *Grace · 246 kJ*),
> and — the useful half — `Dropping the ghost at 1s: these watts are
> modelled`, with no card on the ride screen at all. That is 24.3.7 refusing
> to race on a simulated ride, which is the case it exists for.
>
> **24.3.6 is deliberately not ticked.** The *"they finished"* state has
> tests but has never been seen, because seeing it needs a ride that outlasts
> its rival's. **Nor has the number been watched moving under a rider** — that
> needs somebody pedalling and the owner was not able to at the time. Both are
> what is owed on this item; everything around them is observed.
>
> Two things were got wrong and fixed by looking at the tablet rather than at
> the diff: the picker card's width (capped and unfilled, then filled and
> uncapped — 22.6 both ways round), and the gap drawn in a coral that reads as
> red, which told a rider one kilojoule down that they were failing.

- [x] **24.3.10 A live leaderboard, not just a single gap** — the owner's
      note, 4 August 2026, live in chat rather than through the inbox.
      Paraphrased for the numbers: *"let's do what Peloton does and show a
      live leaderboard (in watts) which includes a live 'as it stands'
      leaderboard of where YOUR personal best is (on this class) and also
      your FRIEND's personal best on this class. So if you're on 56 watts and
      your PB is 65 (at that point in time) you know you need to speed up if
      you want a new PB. The ghost score should be the score that user had at
      that exact moment in the class."*

      This is Peloton's own leaderboard shape — several rows, ranked live,
      rather than one chosen rival — and it directly reopens two things
      24.3.4/24.3.5 already decided, on purpose:
      - **24.3.4 says "not a list."** *A leaderboard of two is a number.* A
        leaderboard of *several* is a leaderboard, and the owner is asking for
        exactly the thing that ruled out, for a real reason: it is more
        useful with more than one rival on it — your own PB alongside a
        friend's, both live.
      - **24.3.5 says cumulative, not instantaneous, because instantaneous
        flickers.** 11.6.7 already fixed the ride screen's numbers changing
        too fast to read, and a raw watts-vs-watts comparison reopens exactly
        that. The owner's own example — *"you're on 56 watts and your PB is
        65"* — is instantaneous, not cumulative. The two decisions disagree,
        and this item does not resolve that disagreement, it records it.

      **Not built as part of 24.3.3–24.3.9**, on the owner's own instruction
      this sitting — *"let's keep what you're doing so I can see if you've
      stumbled upon something brilliant"* — so the single cumulative-kJ gap
      ships first and stands as the comparison. This item is queued behind
      it, not decided against it: whether the ranked figure is cumulative
      output or instantaneous watts, and whether the display is one gap or a
      short ranked list, is the open question for whoever picks this up next,
      informed by how the single-gap version actually reads on the tablet.

      **Answered, 4 August 2026, through the inbox** — the owner came back to
      it after seeing the ghost and settled the shape rather than leaving it to
      whoever picked it up. 24.3.11 to 24.3.14 are that answer, split by the
      decision each one carries.

      **Built, 5 August 2026**, as 24.3.11 to 24.3.13b. Both tensions this item
      recorded rather than resolved are now resolved, and neither the way it
      feared:
      - *"Not a list"* against *leaderboard* was settled by **24.3.13's
        window**. The board has any number of rows and the ride screen shows
        three of them, so 24.3.4's argument survives intact — what a rider
        reads mid-effort is still small enough to read.
      - *Cumulative against instantaneous* was settled by **the owner
        directly** in 24.3.14: the score is the class total in kilojoules, so
        the word *watts* in this item was loose language and 11.6.7 was never
        actually reopened.

      What was **not** foreseen here is how much of the ghost the board would
      simply inherit — see 24.3.11. The presentation is nearly all of what
      changed.

- [x] **24.3.11** **The leaderboard wins, and the rival goes behind a flag
      rather than into the bin.** The owner, verbatim: *"We have two competing
      ideas. One is yours, one is mine. I think i prefer Leaderboard as a
      feature for 'chasing' your PB or your friend's PB. It has scope for
      including unlimited number of people whereas rivals is (i think) just one
      person you race against. Let's not waste all the effort though, let's
      feature flag the Rivals feature and keep it hidden away. But i do think
      Leaderboard is better."*

      The reasoning is the owner's own and it is the right one: **the rival's
      ceiling is one**. Everything the ghost does — the elapsed-second
      alignment, the measured-power gate, `RivalTrace`, `active_ride_rival`
      surviving a crash — is a leaderboard with a `LIMIT 1` on it, so the work
      is a foundation rather than a detour. What is genuinely single-rival is
      the *presentation*: the picker on the class detail screen, and the one
      `+18 kJ` card.

      So: **hidden, not deleted.** A build-time flag off by default, the picker
      and the card behind it, the tests kept green, `RIVALS.md` kept and marked
      as superseded. Two reasons not to simply delete it. It is the comparison
      the owner asked for — *"let's keep what you're doing so I can see if
      you've stumbled upon something brilliant"* — and it cannot be that if it
      cannot be turned on. And 24.3.6, the *"they finished"* state, is a
      question the leaderboard has too; the rival is where it is already
      written down.

      **Built exactly that way.** `BuildConfig.RIVAL_GHOST`, false, read
      through `core/Features` rather than at the call sites — and the reason
      for the indirection is worth keeping, because it is what stops a flag
      rotting: a `BuildConfig` boolean is a **compile-time** `false`, so
      `if (BuildConfig.X)` folds away and the compiler starts calling the
      branch behind it unreachable. Warnings become a deletion nobody meant to
      make. Reading it through a `val` keeps the code alive.

      **How little is actually behind it is the finding.** Two call sites: the
      picker's query on the class detail screen, and the choice of which race
      to load in `WorkoutService`. `RivalTrace`, the elapsed-second alignment,
      the measured-power gate and `active_ride_rival` are all still on the
      live path — which is 24.3.11's own claim ("the work is a foundation
      rather than a detour") turning out to be true when it was cashed in.
      `RIVALS.md` carries the superseded banner and says which flag turns it
      back on. *Observed on the tablet AVD: the class detail screen for
      `END-01` with the household board and no* Ride against *card.*

- [x] **24.3.12** **What is on the board, and it is more than housemates.**
      Verbatim: *"Not only can it include your own PB as a 'ghost' to chase on
      the leaderboard, but also it could be PB this month, PB this year, and
      all your friends scores too. Just something to always be reaching for,
      you know?"*

      Four kinds of row, and they are four different queries rather than four
      formats:
      - **your best ever on this class** — 16.3.4 already computes it, and
        24.3.3's picker already offers it
      - **your best this month** and **your best this year** — the same query
        with a date floor. These are the interesting ones, because a rider who
        is improving has a *reachable* ghost in the month and an unreachable
        one all-time, and the reason the owner gives is exactly that: something
        to always be reaching for. Note 22.5's finding underneath this — a
        rider who rides once a week has a thin month, so a month with one ride
        in it puts your only ride on the board as a rival to itself. That has
        to read as absent, not as a rival you are dead level with.
      - **housemates' bests** — 24.3.1's query, unchanged
      - **friends' bests** — needs 18, and needs their samples in the cloud,
        which is the one row here that does not work offline. It must degrade
        to absent rather than to an error: rule 3 of the connectivity model
        says the household board is a Room query and never touches the network.

      **Built, and the four queries turned out to be one query with a floor on
      it plus `householdRivals` unchanged.** `previousBestOfClass` took a
      `sinceMs`, so *ever*, *twelve months* and *thirty days* are the same SQL
      with the same measured-power exclusion — which matters more than the line
      count: three copies of that `NOT EXISTS` clause is three places for the
      honesty rule to drift out of step.

      **The windows are rolling, and that is 22.5.1 applied rather than
      rediscovered.** The owner asked for *"PB this month, PB this year"* and
      the calendar reading of that is the defect 22.5.1 already wrote down: a
      month resets on the 1st, so a rider who rode on the 29th and the 30th
      opens a class on the 1st and the reachable ghost they were chasing is
      simply gone — on the day they least want it to be. Thirty days and
      twelve months never reset. The rows say `30 DAYS` and `12 MONTHS` for
      that reason: a rolling window labelled *this month* would be the same
      lie in the opposite direction.

      **One ride, one row, at its widest label**, which is 22.5's finding as a
      function: `RaceCompetitor.oneRowPerRide`. A rider whose best-ever ride of
      a class was three weeks ago has one ride answering all three of their own
      questions, and this is the ordinary case rather than an exotic one. It is
      pure and JVM-tested, in the domain rather than in the repository, because
      it is a rule and not a query.

      *Observed on the tablet AVD, `END-01` with six seeded measured rides:*
      `Racing 5 on END-01: Your best 200, 12 months 160, 30 days 139, Kilo 180,
      Grace 120` — five rows from six rides, with the 100 kJ ride that is never
      the best of anything correctly absent, and no ride appearing twice.

      *And on the real bike* (`PLTN-RB1VQ`, real measured watts, no lever),
      which is where the dedupe stopped being a hypothetical: `Racing 1 on
      END-03: Your best 238`. **One row from three of the rider's own rides**,
      because their best-ever ride of that class was two days old and is
      therefore also their best of the last twelve months and of the last
      thirty days — the exact case `oneRowPerRide` exists for, arriving
      unprompted on the first real ride it was tried on rather than in a
      fixture. The two rides it left off are right too: a 14 kJ attempt that is
      never the best of anything, and a housemate's 14-second ride with no
      samples at all.

- [ ] **24.3.12a What the opponents are called — the owner's to decide, and
      they have said so.** The note, 5 August 2026, verbatim: *"We're still
      working on the fundamentals so let's no waste time getting hung up on
      what the 'opponents' are called. But just for the record '12 months' is
      no good at all. We should have a brainstorm at some point to come up with
      a definitive list for who the opponents should be. Maybe put that as an
      action on me, and just carry on what you're doing for now. You can remind
      me at a later date if i haven't decided yet."*

      **So this is an open item with the owner named on it**, which is rare
      enough in this plan to be worth marking as such — most items are work
      waiting for a session. It stays open until they have decided, and a
      session that finds it still open should say so rather than invent an
      answer.

      What is worth having ready for that conversation, so it is a decision
      rather than a blank page:
      - **`12 months` is bad for a nameable reason**, and naming it makes the
        replacement easier: it is a *duration*, and every other row is a
        *person*. On a board where the neighbouring rows say `KILO` and `YOU`,
        a row saying `12 MONTHS` is a category error before it is a bad label.
        `30 DAYS` has the same fault and is only less obvious because it is
        shorter.
      - **The constraint is real and it is the screen.** These sit in a 320 dp
        column beside a rank and a 26 sp number, read from two metres by
        somebody at 90 rpm. *"Your best of the last twelve months"* is accurate
        and unreadable, which is how the current labels got to be durations in
        the first place.
      - **The thing being named is not obvious either**, and that may be the
        more useful half of the brainstorm. A row is *the rider, as they were
        over some window* — a past self. Nothing in the app has ever had to
        name one of those, and the answer may not be a shorter description at
        all. It might be an idea: *you in the spring*, a personified ghost, or
        simply a date.
      - **Whatever is chosen, the label is one string per `RaceCompetitor
        .Kind`** and changing it touches nothing else. That is worth saying
        plainly so the decision is not weighed as if it were expensive.

- [x] **24.3.13** **A window, not a list — the row above you and the row
      below.** Verbatim: *"I'm expecting it to show the person above you, the
      person below you."* This is what makes 24.3.4's *"not a list"* and the
      owner's *"leaderboard"* stop contradicting each other, and it is worth
      saying plainly: **the board can have any number of rows and the ride
      screen shows three of them** — you, the one you are chasing, the one
      chasing you. It is Peloton's own behaviour and it is the reason their
      leaderboard is legible at 90 rpm.

      Consequences to design against rather than discover: your row moves
      *between* the other two as you pass and are passed, so the two neighbours
      change identity mid-ride and the change must not read as a number
      jumping; and at the top of the board there is no row above you, which is
      the state worth designing first because it is the one a rider wants to
      be in.

      *Observed on the tablet AVD across two whole classes, with the board
      moving under a rider rather than in a screenshot:* `6TH OF 6` with two
      rows above at the start, `5TH OF 6` after passing *30 days* (which then
      sat below at `−3`), `4TH OF 6` with a neighbour each side, and `LEADING`
      with all three rows below. The neighbours changed identity four times
      and the card never changed size.

      *And the two-row case on the real bike*, which is the one the AVD could
      not produce: `2ND OF 2 / 1 YOUR BEST +2 / 2 YOU 0 kJ`, a field smaller
      than the window shown whole. Worth noting what it is **not** — it is not
      the single-gap card wearing a different hat. It carries the rank, the
      field size and the rider's own total, all three of which the gap card had
      no way to say.

      **Built, and the answer to both consequences is the same one: the window
      slides, it never shrinks.** Leading gives you the three rows below you;
      last gives you the three above; anywhere else gives you the neighbour
      each side. A card that lost a row at the top of the board would change
      height at the exact moment a rider was doing well, and 11.6.8 is this
      project's own finding that a ride screen which resizes under a rider is
      unreadable at 90 rpm. **Last is not a corner case either — it is the
      first ten seconds of every race**, because the whole field starts on
      zero and anybody who moved first is ahead of a rider who has not turned
      a pedal.

      Two smaller decisions the drawing forced:
      - **The header carries what the window hides.** Three rows cannot say
        whether there are two more below or twenty, so the card says
        `6TH OF 6` — and `LEADING` instead of `1ST OF 6`, because that is the
        thing a rider was trying to do and the ordinal is a worse way of
        saying it.
      - **Two number spaces on one card, and the sign tells them apart.** Your
        row carries your total with its unit; every other row carries the gap
        to you, signed, without one. It is safe only because the ranking
        agrees with it — the row above always reads `+`, the row below always
        reads `−` — which is the same reason 24.3.4's single gap could get
        away with the opposite convention.

- [x] **24.3.13a A lever so the race can be *seen* on the emulator.** Not in
      any earlier item, and it was needed the moment the board existed: 24.3.7
      means every AVD ride drops its race one second in, so the only place the
      feature could be looked at was a bike with a rider on it — and CLAUDE.md
      is right that that is a perishable resource. `com.pelonot.debug.RACE`,
      beside `COAST`, `CORRUPT` and `SILENCE`, is the same move for the same
      reason.

      **What makes it safe is what it does not touch.** `power_is_measured` is
      still recorded honestly, sample by sample, so a ride captured under the
      lever is still excluded from every board, every FTP proposal and every
      calibration fit afterwards. Only the *live* comparison stops refusing to
      draw. A lever that made a simulated ride **claim** to be measured would
      poison the record permanently and be indistinguishable from a real ride
      afterwards — which is the exact class of defect that column exists to
      prevent, so the distinction is the whole of the argument and is written
      out in `RaceDebug`.

- [x] **24.3.13b Where the board goes, and what had to move for it.** The
      owner, looking at the first draft on the tablet: *"'Then' section should
      go under 'next'. This then frees up space for leaderboard which is where
      your eyes are naturally drawn to anyway."*

      Both halves are right and the first is **11.6.1's own argument carried
      one step further**. That item moved the *next* effort out of the right
      column to hang off the current one, because "what I am doing" and "what
      I have to be ready for" are read together; the rest of the class was the
      only part of that one thought still sitting in another column. It is
      three rows there rather than four — it is a shape, not a schedule, and
      it now shares a column with the timer and the totals.

      **The board needed the room, and the first draft proved it by being
      wrong.** Squeezed above the totals in the effort column it pushed
      `OUTPUT`, `DISTANCE` and `AVG POWER` off the bottom of the screen —
      clipped silently, with nothing failing. Found by looking at the tablet,
      which is the only way that kind of thing is ever found, and the reason
      CLAUDE.md says `assembleDebug` passing proves very little. With a column
      of its own the rows are 44 dp with a 26 sp number and read at two
      metres.

- [x] **24.3.14 The score is the class total in kilojoules — answered, and the
      answer came with a second ask attached.** The question was put directly
      and the owner answered directly: *"I may have chosen the wrong words. I
      meant kilojoules. I mean the score that the real peloton gives you. The
      'score' for any class should be total kilojoules for that class. E.g. a
      20 min class maybe your high score is 200."*

      So **cumulative, not instantaneous**, and 24.3.5 and 11.6.7 both stand
      rather than being reopened. The word *watts* in 24.3.10 was loose
      language for the score, not a request for a board that re-sorts several
      times a second. Nothing in the ghost changes: `RivalTrace` already
      integrates exactly this number and already agrees with what the rival's
      own ride recorded.

      **The second ask is the interesting one, and it is about shape rather
      than about this feature**: *"Let's not rule out racing by OTHER metrics
      too, such as distance, perhaps structure the data in an agnostic way like
      that. And you know what, if it's really that trivial to do, consider
      having a toggle between racing by output or racing by distance. Otherwise
      just add it to the plan."*

      **The data half was trivial and is built** (see below). **The toggle is
      not, and is 24.3.15.**

- [x] **24.3.14a The race is metric-agnostic, because it was four lines of
      real change.** The owner asked for the judgement as well as the work —
      *"if it's really that trivial"* — so here is the measurement behind the
      answer. A race is **one cumulative series against another, aligned by
      elapsed second**. That shape does not care what is being accumulated, and
      `WorkoutAggregates.from` was *already* integrating both kilojoules and
      kilometres in one pass over the same samples with the same five-second
      gap clamp — `RivalTrace.from` was duplicating half of that loop. So:
      `RaceMetric` (`Output` | `Distance`), `RivalTrace` carries which one it
      is, `RivalStatus.gapKj` becomes `gap` plus a metric, and the ride screen
      picks its formatter off that instead of assuming kilojoules.

      **It also closed a latent drift rather than adding one.** The distance
      integration needs metres-per-revolution and the gap clamp, and writing
      them out a second time would have produced a ghost whose distance
      disagreed with the distance the ride recorded — the `avg_*` family
      exactly. `WorkoutAggregates`' three constants are now `internal` and
      `RivalTrace` borrows them, and the test asserts both integrations agree
      with `WorkoutAggregates` for the same samples, for both metrics.

      **And one finding worth carrying into 24.3.12**, which is why this earns
      its place rather than merely anticipating a request: **a distance race
      does not need measured power.** 24.4.2 excludes any ride with a single
      non-measured sample from an output comparison, which is why most classes
      have no ghost at all today. Distance is integrated *cadence*, measured on
      every ride this app has ever recorded — simulated ones included. So the
      distance board is populated where the output board is empty.
      `RaceMetric.requiresMeasuredPower` says so in one place.

      **Only `Output` is reachable today** — nothing selects the other, because
      selecting it is 24.3.15. The distance path is exercised by tests and by
      nothing else, which is the honest state of it.

- [ ] **24.3.16 The leaderboard on the overlay — and it reopens a rule twice
      written down.** The owner's note, 5 August 2026, verbatim: *"Need to
      think about how it works in HUD mode. We don't want to show too much
      information But maybe we can squeeze a tiny version of the leaderboard
      on. And in compact mode of the HUD there simply isn't space, so don't
      include it. At a later date I will have a think about what we can do to
      improve compact mode. But not now."*

      **This is the owner overruling 24.1.5 and 18.6, and they should be read
      before it is built rather than quietly dropped.** Both say nothing social
      goes on the strip, for the reason 19.4 gives: the overlay has half a
      second of a rider's attention and it belongs to the next sixty seconds of
      pedalling. That reasoning is not wrong and the note does not claim it is
      — *"We don't want to show too much information"* is the same concern,
      arriving at a different answer. What has changed since those items is
      that the thing being excluded is no longer a social ornament: **the race
      is now the only thing on the ride screen that says whether the effort is
      going well**, and a rider watching a film cannot see it at all.

      The note already makes two of the three decisions:
      - **Nothing at all in the collapsed strip.** Stated plainly, and it is
        the right call: `HudCollapsed` is four `CompactMetric` readouts on one
        line and there is no room for a fifth thing, let alone a ranked one.
      - **A "tiny version" in the expanded overlay**, not the card. Which
        raises the only real question here: **what is the smallest honest
        leaderboard?** Three rows will not fit. One row might — and one row is
        24.3.4's single gap, which is exactly what 24.3.11 has just put behind
        a flag. That is not an argument against it; it is an argument for
        being deliberate, because the overlay's answer may legitimately be the
        shape the full screen rejected. Candidates, cheapest first: the
        position alone (`5TH OF 6`), the position plus the gap to the row
        above (the number a rider can act on), or the single row above you.
      - **What the third decision is:** whether it appears at all when there
        is no race, and the answer that follows from everything else here is
        that it must not — 24.1.6's rule, and on the overlay an empty row is
        more expensive than anywhere else in the app.

      Not started. `HudExpanded` in `HudOverlayMain.kt` is where it would go,
      and `RideSnapshot.standings` is already published to it — the overlay
      renders from the same snapshot the ride screen does, so the data is
      there and only the drawing is missing.

- [ ] **24.3.15 The toggle: race by output, or race by distance.** Deferred on
      the owner's own *"otherwise just add it to the plan"*, and the reason it
      is not trivial is not the plumbing — that is done — but **where the
      control goes**. It is a control on the leaderboard's own surface, and
      that surface does not exist yet (24.3.12, 24.3.13); the one place it
      could live today is the rival picker, which 24.3.11 is about to put
      behind a flag. Building it now would be building UI for a screen that is
      being replaced.

      Three things to settle when it is picked up, none of them expensive but
      none of them free either:
      - **Where the choice lives.** Per-ride, like the rival picker, or a
        remembered preference? A rider who races distance probably always
        races distance, which argues for the preference — but 2.4.6's rule
        applies to anything stored: one writer, and the pipeline follows.
      - **What it does to the rows already on the board.** Switching metric
        re-ranks, and 24.3.13's whole point is that your neighbours changing
        identity must not read as a number jumping. A mid-ride toggle is that
        problem at its worst; the honest first version may be that the choice
        is made before the class starts and is fixed for its duration.
      - **Say which it is, once.** A board showing `+18` with no unit is the
        one outcome to design against, and it is the same argument as the
        provenance rule: a number whose meaning has silently changed is worse
        than a number that is missing.

- [x] **24.3.17 The board says less — three notes, and between them they undo
      two of 24.3.13's three decisions.** The owner's note, 5 August 2026,
      under the heading *Leaderboard general improvements in full screen*,
      verbatim:

      > - *"The plus/minus numbers make sense, but they don't work. Swap it
      >   with just the kj."*
      > - *"Only include the number, not the 'kj' unit label"*
      > - *"Even though it's a great idea to have a ranking number, there are
      >   honestly going to be so few actual people using this app that most of
      >   the 'people' are actually going to be targets rather than people, so I
      >   don't think 'rank' really works. Let's get rid of the ranking,
      >   including the 'X of Y'. I will continue to think about who the
      >   opponents are going to be. I believe it will be more of a 'target'
      >   than real people (although of course we WILL include real people!)"*

      **All three are the same instruction from different angles: the board is
      read at 90 rpm, and every mark on it that is not a name or a number is
      costing more than it earns.** Taken together the card becomes three rows
      of *name* and *number*, and nothing else — which is a smaller thing than
      what 24.3.13 built and is, on the tablet, an easier thing to read.

- [x] **24.3.17a Every row carries its own total, not the gap to you.** The
      first note, and it reverses 24.3.13's *"two number spaces, and the sign
      tells them apart"*. That decision was defensible and it is worth saying
      why it loses: a gap is **arithmetic the rider did not ask for**. `+12`
      means *they are twelve up on you*, which is one subtraction away from the
      two totals it was computed from, and the rider has to hold their own
      number in their head to make it mean anything. Four totals in a column
      are compared by eye with no arithmetic at all, and the ordering already
      says who is ahead.

      What it costs, and it is real: the gap was the only number on the screen
      that said *how much* separates two riders **without** being read against
      another number. At 180 kJ against 178 kJ the eye has to work harder than
      it did at `−2`. The owner has looked at both and picked; this item exists
      so that a later session knows the trade was seen rather than missed.

      `LiveStanding.gapToYou` stays on the model — it is one subtraction, it is
      tested, and 24.3.16's overlay is the surface where a single gap may well
      still be the right answer. Nothing on the full ride screen draws it.

- [x] **24.3.17b No unit label on the board.** The second note. Every row is
      now in the same unit, ranked against each other, and the OUTPUT tile
      directly below the board spells out `kJ` in full — so the label on the
      rider's own row was the third place the same fact was being stated.
      Phase 26's rule agrees and is worth quoting against itself: *a unit
      belongs where a measurement is being read, not where a choice is being
      made*. A board is a comparison, not a measurement; the measurement is the
      tile below it.

      **It contradicts 24.3.15's third bullet and that has to be faced rather
      than skipped.** That item says *"a board showing `+18` with no unit is the
      one outcome to design against"*, and the reasoning was sound: a number
      whose meaning silently changed is worse than one that is missing. What
      makes it safe today is that **only `Output` is reachable** (24.3.14a) —
      there is nothing for the number to silently change *into*. So this is not
      a decision to un-take when 24.3.15 lands: **if the metric ever becomes
      selectable, the board has to say which it is somewhere**, and the note's
      instruction is that it must not be on every row. The header is gone
      (24.3.17c), so that somewhere does not exist yet either. Leave a comment
      at the drawing site saying so.

- [x] **24.3.17c No ranking at all — no rank column, no `4TH OF 6`, no
      `LEADING`.** The third note, and it is the one with an argument about the
      product in it rather than about the pixels: *"most of the 'people' are
      actually going to be targets rather than people"*. Four of the row kinds
      the board already has (24.3.12) are the rider's own past rides. Calling
      a rider *4th of 6* when four of the six are themselves is not a small
      overstatement, it is a category error — a position implies a field of
      competitors, and this field is mostly a rider's own history.

      It deletes 24.3.13's second decision outright: *"the header carries what
      the window hides"*. That was a real problem and it is now simply
      **accepted** rather than solved — a rider seeing three rows will not know
      whether there are two more or twenty, and on the owner's reading that is
      fine, because the three rows near you are the only ones that were ever
      actionable.

      Two things survive that it would be easy to delete by accident:
      - **The ranking itself.** It is what orders the board and picks the
        window, and `LiveStandings.yourRank` / `fieldSize` stay for the tests
        and for the overlay (24.3.16 lists *"the position alone"* among its
        candidates). This is a drawing change and must not become a model
        change.
      - **The bounce on `yourRank`.** `attentionBounce(trigger =
        standings.yourRank)` marks the moment the board moves under the rider,
        and that moment is *more* worth marking once the number that announced
        it is gone — passing somebody will otherwise be two rows quietly
        swapping.

      **And it raises the stakes on 24.3.12a**, which is the owner's own open
      item: with the rank gone, **the name is the only identity a row has**.
      `12 months` and `30 days` were weak labels beside a rank and are the
      whole row without one.

### 24.4 Honesty, and the column that is now blocking three things

- [x] **24.4.1** No caveat, and the card carries none
- [x] **24.4.2** **Built, and it is the column three features were waiting
      on.** `workout_metrics.power_is_measured` (migration 3→4) records it per
      sample; `PowerProvenance` is the per-ride verdict. The board excludes any
      ride with a single non-measured sample — including a `NULL` one, since a
      ride recorded before the column existed cannot be *shown* to be
      measurement. 7.10.7 and 16.1.6 are closed by the same change
