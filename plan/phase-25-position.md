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

- [x] **25.3.1** So the rule to design to: **the overlay may animate for a
      transition and must go quiet again.** A few seconds of arrow at the moment
      the position changes, then nothing. Never a persistent indicator, never
      anything that moves while the prescription is unchanged. *Six seconds,
      which is the spoken announcement plus the time it takes to get out of the
      saddle. Observed on the tablet AVD over the launcher, riding `CLB-02` with
      the overlay raised: "STAY SEATED" at 05:01, gone by 05:08; "OUT OF THE
      SADDLE" at 11:03, gone by 11:14 — and in between, the strip back at its
      resting height with nothing on it moving.* Two decisions inside it:
      - **Amber, not the zone accent.** The zone colour is already the
        interval-change flash, so reusing it would say "new effort" twice and
        "stand up" never — and **Zone 1's colour is grey** (11.1b.10), so a
        warm-up that prescribed a position would announce it in the colour of a
        stray divider. One colour for position across both surfaces, matching
        the ride screen's
      - **On the inner side of the numbers, whichever edge the strip is docked
        against.** The overlay window is `WRAP_CONTENT` against a gravity, so it
        grows into the screen and the figures the rider is reading do not move
        underneath them. It survives collapsing, for the same reason the
        countdown does: a rider who has given the film back the rest of the band
        still has to be told to stand up
- [x] **25.3.2** Which means it must be driven by the *edge*, not by the state.
      Build it off the interval change and not off "the current interval says
      standing", or it will animate forever on a five-minute block. *Built as
      `PositionCallTracker`, and **the spoken coach now asks the same object** —
      so the voice and the arrow cannot disagree about what counts as a change.
      The two rules that are easy to get subtly wrong live there once instead of
      at each call site: keyed on the **value** (`CLB-06` alternates six times
      and would otherwise announce twelve), and compared against **the interval
      just left** rather than the last thing announced (a rider sits down during
      the recovery between two standing efforts). Observed: `CLB-02`'s second
      standing attack was called again at 12:31, 90 seconds after the first*
- [x] **25.3.3** Read 11.1b.9 and 11.1b.4a first — the chips are a design the
      owner has said he will come back to, and this lands in the middle of them.
      *Read, and the cue deliberately does **not** join the chip family: it is
      not a chip, it does not take the chip fill or the opacity setting, and it
      is gone before the rider could form an opinion about it. Whatever 11.1b.9
      settles about the chips leaves this untouched*
- [ ] **25.3.4** And check it over video on the bike, which is the only place it
      can be judged. Note the blind spot from `CLAUDE.md`: `screencap` returns
      black over DRM playback, so **how it looks over a film has to come from
      the rider**. *Still open and still needs the owner. What the AVD can and
      does show is everything except the one question — whether an amber
      lozenge over a moving picture reads as an instruction or as an
      interruption*

### 25.4 Then the library uses it

- [x] **25.4.1** Go back through the 72 and put a position on the blocks that
      want one — the standing attacks, the seated grinds, the sprint efforts —
      and on nothing else. This is a catalogue edit, not a code change, and it
      wants doing *after* 25.2 and 25.3 so the effect of each one can be seen.
      **Done, and it is an audit rather than a sweep — one block wanted one.**
      Every heavy-torque and standing block in the library was listed with its
      cadence and its current position, and the result was:
      - **`CLB-05`'s torque ladder was the only `GRIND` work not marked
        seated.** At 50–60 rpm that is not decoration: standing at that cadence
        is a different exercise, and torque work out of the saddle is not torque
        work. Its rests are two and three minutes, so the call at the top of each
        rung is an instruction rather than a nag. Fixed
      - **The sprint efforts resolve to *seated*, and therefore to nothing.**
        `SPR-01`–`04` and `06` are at `SURGE`, 110–125 rpm, which R11 will not
        let stand — rightly: a 120 rpm sprint *is* a seated sprint. Absence
        already says the true thing there, and `SPR-05` is the class that makes
        the distinction explicit by putting its one standing set at `STAND`
        cadence
      - **The `CLIMB`-cadence blocks were left alone on purpose.** A five- or
        fifteen-minute climb at 60–70 rpm is exactly where a rider *should* come
        out of the saddle when they feel like it, and prescribing "stay seated"
        across it would be the app talking over the rider's own judgement
- [x] **25.4.2** **Three classes are named after a position they cannot
      state, and it is the owner's call.** `END-08` "Seated Climbs 45",
      `SWT-05` "Big Gear Sweet Spot 30" and `THR-06` "Big Gear Threshold 4×4 30"
      are each *entirely* about being in the saddle in a big gear, and in each
      one the title is the only thing saying so — which is precisely the defect
      25.1 opened with, where `CLB-02` was called "Standing Attacks" and nothing
      but its title made it standing.

      They cannot be fixed as they stand, because marking their work blocks
      seated puts them over **R11's half-a-class cap** — 1500 s of 2700, 960 of
      1800, 960 of 1800. The cap is right for the general case and was settled
      in 25.1.3, so it has not been touched. But the case it does not
      distinguish is **a class whose identity *is* the position**: a rider who
      picks "Seated Climbs" has opted into being told to stay seated, and the
      cap's own words — *leave most of a class to the rider* — are about
      discretion the rider has not already spent.

      Three ways out, and the choice is a taste call rather than an
      engineering one: measure the cap against a class's **work** rather than
      its whole duration; exempt a class that declares the position as its
      premise; or rename the three so the title stops claiming something the
      data does not carry. **Do not simply raise the number** — the failure it
      is guarding against is real, and 25.4.1 above is the evidence that most
      classes want nothing

      **The owner chose the third: rename them.** *"Rename the ride so it's not
      contradictory. Do what you think is best based on your thoughts and other
      established rides by Peloton, Whoosh, etc."* R11's cap is untouched, which
      is the point — the cap was never the thing that was wrong.

      **It was four classes, not three.** Auditing the titles for the rename
      turned up `END-12` "Big Gear Endurance 60" doing exactly the same thing at
      Z2, and the audit is only worth having if it is complete. The rule the
      four have in common, now written into `classlibrary/README.md` under R10:
      **a position word in a title is a promise that the blocks say it too** —
      and "big gear" is a position word, because in cycling usage it means
      seated torque and a rider reads it as the instruction. So `SWT-09` "Big
      Gear / Fast Legs 45" keeps its name, since its two big-gear blocks really
      are marked `SEATED`.

      | Was | Is |
      |---|---|
      | `END-08` Seated Climbs 45 | **Tempo Climbs 5×5 45** |
      | `END-12` Big Gear Endurance 60 | **Climb and Spin 60** |
      | `SWT-05` Big Gear Sweet Spot 30 | **Low Cadence Sweet Spot 30** |
      | `THR-06` Big Gear Threshold 4×4 30 | **Low Cadence Threshold 4×4 30** |

      Named off the axis the data does carry. "Low cadence" rather than "big
      gear" because 60–70 rpm is literally on the class detail screen and on the
      ride screen while the rider is in the block, and because it claims nothing
      about the saddle in either direction — which is precisely what an
      unpositioned block means. It is also the industry's own word: TrainerRoad
      and Wahoo SYSTM both call the variant "low cadence", and Peloton, which
      cannot prescribe a position at all, sells the session as a "Climb Ride".

      **A rename is safe where a re-id would not be.** `workouts.class_id` is
      the foreign key and the title is not, so a ride recorded on `END-08` keeps
      resolving and simply shows the better name — the opposite of 23.2.6's
      constraint, which is why that rebuild had to take new ids and this did
      not. It reaches an already-seeded tablet because `build.py`'s fingerprint
      hashes each file's whole body, so the title change moves it (`42e31385` →
      `0047b4b8`) and `ClassTemplateSeeder` upserts all 72; nothing is retired,
      because no id moved.

      *Observed on the tablet AVD: the library browser lists all four new
      titles, `Low Cadence Threshold 4×4 30` opens with its 4 × 4 min at 60–70
      rpm and no position on any block, and the seeder logged one reconcile on
      first launch and nothing on the second.*

- [ ] **25.4.3** **`SWT-05` and `THR-06` are nearly the same class**, which the
      rename made visible by putting them in the same words. Identical work — 4
      × 4 min at Z4, 60–70 rpm — and they differ only in the recovery: Z2 at
      105–115 rpm against Z1 at 75–85. That passes R9, which compares the full
      signature, and it is defensible on paper (the spin recovery is what makes
      one sweet spot and the other threshold). But 23.2.6's whole complaint
      about the old library was that `SS-03` and `TH-03` were the same class,
      and this is the same shape of thing at a much smaller scale. Either give
      one of them a different work interval, or let the titles say what actually
      separates them
