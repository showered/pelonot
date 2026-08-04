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

- [ ] **27.1.1** **An alert is written down when it fires, and never fires
      twice.** One row per alert: what it was, which ride caused it, when the
      rider saw it. Without that table there is no way to say "new", no way to
      stop it reappearing on the next load, and no way to build 27.4's list.
      This is the item everything else depends on
- [ ] **27.1.2** **The honesty gate is `PowerProvenance`, not the raw column.**
      A personal best from a simulated ride is a fiction the app then
      congratulates the rider for, and `PowerModel` is 137 W out at RMSE. Only
      `Measured` counts — `Unknown` and `Mixed` fail it, on purpose, exactly as
      they do for the FTP proposal (7.10.7) and the household board (24.4.2).
      **The consequence on the emulator is that no power alert can ever fire
      there**, which is a verification cost and is worth paying
- [ ] **27.1.3** **A record is a claim about the past, so it must not be
      redrawn by the present.** This is 7.8's trap and 21.2.3's, one column
      further along: anything expressed *relative* to a moving number — a
      percentage of FTP, a heart-rate zone, a training-load score — is a
      different claim the day that number moves. Alerts are therefore built on
      absolutes the rider actually produced: watts, kilojoules, seconds, rides.
      **"Your best-ever Zone 5 time" is the alert not to build**
- [ ] **27.1.4** **The first ten rides are all records, and that is the design
      problem.** A new rider's every ride is their fastest, their longest and
      their best, so an unguarded implementation fires six alerts on ride one
      and the rider learns in a week that the alerts mean nothing. Some floor is
      needed and it should be chosen deliberately rather than tuned later —
      a minimum number of prior rides, a minimum margin over the old record, or
      both. **An alert that fires every time is not an alert, it is a caption**
- [ ] **27.1.5** **At most one per ride, and the best one.** Falls out of
      27.1.4 rather than being a second rule: if three things happened, the
      rider is told the largest and the other two are on 27.4's list. A stack of
      congratulations is how a good moment is made tedious
- [ ] **27.1.6** **Nothing goes on the overlay and nothing interrupts a ride.**
      19.4's standing rule, 18.6's and 24.1.5's. The overlay has half a second
      of attention and it belongs to the next sixty seconds of pedalling; an
      alert is by definition about the past. There is no version of this that
      earns a place there
- [ ] **27.1.7** **Retention will silently break this, and the order matters.**
      23.4 condenses old rides to aggregates, and 23.4.8 already says personal
      bests are re-scanned from samples on every load. An alert built the same
      way inherits the same defect *and makes it visible*: a rider would be
      congratulated for beating a record that only fell because the ride holding
      it was trimmed. 16.3.3a — bests stored per ride — is a prerequisite for
      both

### 27.2 The three families, which are not the same feature

- [ ] **27.2.1** **Your own record** — *"fastest you have ever done X"*. The
      owner's first example, and the one 16.3.3 already computes. The honest
      framings are the absolute ones: best average power over a class you have
      ridden before, best 20-minute power, most output in a single ride. **X
      should usually be a class**, because a class is the only thing in this app
      that makes two rides genuinely comparable — same intervals, same
      prescription, same length — which is also why 24.1 ranks per class
- [ ] **27.2.2** **Your own consistency** — *"you completed a Y streak"*.
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

- [ ] **27.3.1** **The post-ride summary is the moment**, for everything in
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
- [ ] **27.3.4** **The spoken coach must not read these out.** It ducks under
      the rider's film (8.x, the audio-focus work) and its budget is
      instructions for the next interval. A congratulation delivered over
      somebody's Netflix at minute 34 is the same category error as putting one
      on the overlay

### 27.4 The list, and the way out

- [ ] **27.4.1** **Everything that has ever fired, on one screen**, off the
      dashboard's progress section beside *Your FTP* and *Your riding*
      (16.3.1/16.3.2). It is where 27.1.1's table pays for itself, it is the
      answer to *"what did that say?"*, and it is the only place a rider can see
      the shape of a year
- [ ] **27.4.2** **Turning them off is one switch and it is honoured
      everywhere**, including 27.3.2's card. A rider who does not want to be
      graded is not asking for a quieter version of being graded
- [ ] **27.4.3** **It works entirely offline.** 27.2.1 and 27.2.2 are Room
      queries over the rider's own rides and must never wait on, degrade
      without, or hint at a network — rule 1 of the connectivity model, and rule
      3's argument that the ungated tier is a complete product rather than a
      trial of the paid one. Only 27.2.3 needs an account, and its absence is
      silence rather than a locked feature
