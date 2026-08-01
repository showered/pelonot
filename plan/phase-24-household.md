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

- [ ] **24.3.1** Pick a household member's ride of the same class and draw
      their trace behind yours on the ride detail screen. This is 18.4 —
      arguably the most motivating social feature in the plan — and in this
      tier it costs one query and no schema
- [ ] **24.3.2** A live pace target from that ride *during* a ride is the
      interesting version of it, and it belongs on the full ride screen only,
      never on the overlay (24.1.5). Read 11.6 first; that screen already has a
      target gauge and a zone ladder on it and this must not become a third
      thing competing for the same glance

### 24.4 Honesty, and the column that is now blocking three things

- [x] **24.4.1** No caveat, and the card carries none
- [x] **24.4.2** **Built, and it is the column three features were waiting
      on.** `workout_metrics.power_is_measured` (migration 3→4) records it per
      sample; `PowerProvenance` is the per-ride verdict. The board excludes any
      ride with a single non-measured sample — including a `NULL` one, since a
      ride recorded before the column existed cannot be *shown* to be
      measurement. 7.10.7 and 16.1.6 are closed by the same change
