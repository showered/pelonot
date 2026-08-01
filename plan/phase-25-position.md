> Part of the Pelonot plan — the index is [PLAN.md](../PLAN.md).

## Phase 25: Out of the saddle — the instruction the classes cannot give

**From the owner, 1 August 2026.** In his words: a Peloton class is a live
instructor talking, and once the chat is stripped away it is mostly the same
handful of instructions every time — which is part of why this app exists. But
one of them is not in our classes at all: **standing up and sitting down.**
*"Out of the saddle for this one."*

It is the one prescription a bike class gives that neither zone nor cadence can
express. A 60 rpm effort at Z4 seated and the same effort standing are
different workouts in the legs, and the library has had no way to say which.
`CLB-02` is called "Standing Attacks" and the only thing making it standing is
its title.

Two halves, and the second is the interesting one.

### 25.1 The field

- [x] **25.1.1** An **optional** `target_position` on an interval — `standing`,
      `seated`, or absent. **Absent is the default and means the rider chooses**,
      which is what most intervals should say. A class that prescribes a
      position for every one of its blocks is nagging, not coaching
- [x] **25.1.2** Optional in the schema as well as in spirit: `Interval` gains a
      nullable field with `@SerialName("target_position")`, so every class
      written before this decodes unchanged. Three of these have gone wrong
      before by being non-null with a default that stated something false —
      `heartRateBpm`, `power_is_measured`, `retired_at` — and the rule that came
      out of them applies here exactly: **absent is a claim, and it is a
      different claim from either value**
- [x] **25.1.3** The catalogue can say it (`stand=`/`seat=` on a block) and
      `build.py` checks the ones that can be checked: a standing block that runs
      for minutes is a mistake, and standing at 110 rpm is a different mistake

### 25.2 On the ride screen

- [x] **25.2.1** The interval list on class detail says which blocks have a
      position, so a rider can see what they are choosing before they start
- [x] **25.2.2** The ride screen shows the current interval's position, and
      **the change is what matters, not the state** — a rider who has been
      standing for two minutes does not need telling; a rider who must stand
      *now* does
- [x] **25.2.3** The spoken coach says it. Folded into the interval
      announcement rather than added as a second utterance, and **first in the
      sentence** — it is the only part that has to be acted on the instant it is
      heard. Announced on the *change* and compared against the interval just
      left rather than the last position announced, because a rider sits down
      during the recovery between two standing efforts and the second one has to
      be called again. *Observed on the AVD riding `CLB-02`: "Stay seated. Zone
      4, Lactate Threshold. 60 to 70 R P M." at 05:00, then "Out of the saddle.
      Zone 6, Anaerobic Capacity. 70 to 80 R P M." at 11:00 **and again** at
      12:30.* `RideCoach` now logs what it said, which was previously
      unanswerable from logcat

### 25.3 On the overlay — the part worth designing

The owner's note is specific about this: *"the UI has an opportunity to be quite
interesting for this. Flashing/animated arrows, that kind of thing, to really
get the attention of the user especially if they are in HUD mode."*

He is right that it is the overlay that needs it most, and that is also what
makes it hard. The overlay sits over a film, is read at two metres, and the
whole design so far has been about *not* competing for attention (11.1b, and
24.1.5's rule that nothing social may ever go on it). A flashing arrow is
exactly the thing that design has been avoiding — and exactly the thing this
particular instruction deserves, because it is an instruction to act *right
now*, unlike every other number on there.

- [ ] **25.3.1** So the rule to design to: **the overlay may animate for a
      transition and must go quiet again.** A few seconds of arrow at the moment
      the position changes, then nothing. Never a persistent indicator, never
      anything that moves while the prescription is unchanged
- [ ] **25.3.2** Which means it must be driven by the *edge*, not by the state.
      Build it off the interval change and not off "the current interval says
      standing", or it will animate forever on a five-minute block
- [ ] **25.3.3** Read 11.1b.9 and 11.1b.4a first — the chips are a design the
      owner has said he will come back to, and this lands in the middle of them
- [ ] **25.3.4** And check it over video on the bike, which is the only place it
      can be judged. Note the blind spot from `CLAUDE.md`: `screencap` returns
      black over DRM playback, so **how it looks over a film has to come from
      the rider**

### 25.4 Then the library uses it

- [ ] **25.4.1** Go back through the 72 and put a position on the blocks that
      want one — the standing attacks, the seated grinds, the sprint efforts —
      and on nothing else. This is a catalogue edit, not a code change, and it
      wants doing *after* 25.2 and 25.3 so the effect of each one can be seen
