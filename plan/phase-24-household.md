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

- [ ] **24.3.3** **The rival is chosen before the ride, not during it.** The
      class detail screen already draws the board (24.1.2), so the tap that
      starts the class is the natural place to pick who you are riding against —
      a housemate's best, your own best, or nobody. Choosing mid-ride is a menu
      over a rider who is pedalling, and 15.1.6's rule about modals during a
      ride applies to everything, not only to auth
- [ ] **24.3.4** **What is actually shown is a gap, in one number, in the unit
      the board already ranks on.** *"+18 kJ"* or *"−4 kJ"* against the rival at
      this second of the ride, in the ride screen's own colour language: ahead
      is not green-means-good, it is the output colour, because a rider behind a
      stronger housemate is not doing anything wrong. **Not a position, not a
      percentage, not a list.** A leaderboard of two is a number
- [ ] **24.3.5** **Aligned by elapsed seconds and never stretched**, which is
      24.3.1's decision and the same reasoning: the comparison a rider wants is
      *at twelve minutes they were 18 kJ up on me*. It also makes the ghost
      **cumulative rather than instantaneous** — comparing this second's watts
      to their second's watts flickers, and 11.6.7 already settled that the ride
      screen's numbers change too fast to read
- [ ] **24.3.6** **A ghost that runs out says so and stops.** The rival's ride
      ends when it ends: a shorter one leaves the ghost with nothing to say
      after minute 18, and the honest answer is *"they finished"* and a final
      gap, never a line extrapolated forward or a comparison that silently
      freezes. Same family as `isStaleAt` and the gap-not-a-clamp rule
- [ ] **24.3.7** **The measured-power gate applies to both sides**, exactly as
      24.3.1 has it. A modelled ghost is `PowerModel` at 137 W RMSE presented as
      a race, and the rider cannot tell. If either side is not measured there is
      no ghost — the offer simply is not made on the class detail screen, which
      is 24.1.6's rule about not drawing an empty comparison
- [ ] **24.3.8** **It must survive a pause, a resume and a crash.** The ride can
      be paused (auto-pause), resumed after a crash and reopened after being
      ended by accident (8.3d, 12.6.2), and `elapsedSeconds()` has excluded
      paused time since Phase 3. The ghost reads that clock and nothing else —
      wall-clock anywhere in this feature is a rider who stopped for a phone
      call losing a race they were winning
- [ ] **24.3.9** **Nothing about the ghost is written to `workouts`**, and the
      reason is 8.3d.4's rule: `stopWorkout` builds a fresh row from
      `WorkoutSession`, so any column the session does not carry goes back to
      its default. If a future item wants *"you beat Kilo's best"* on the ride
      record — and 27.2.3 does — the field goes on the session first, or it will
      be silently reverted twenty minutes later by the finalise

- [ ] **24.3.10 A live leaderboard, not just a single gap** — the owner's
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

### 24.4 Honesty, and the column that is now blocking three things

- [x] **24.4.1** No caveat, and the card carries none
- [x] **24.4.2** **Built, and it is the column three features were waiting
      on.** `workout_metrics.power_is_measured` (migration 3→4) records it per
      sample; `PowerProvenance` is the per-ride verdict. The board excludes any
      ride with a single non-measured sample — including a `NULL` one, since a
      ride recorded before the column existed cannot be *shown* to be
      measurement. 7.10.7 and 16.1.6 are closed by the same change
