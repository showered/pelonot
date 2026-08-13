# The class library — what makes a class worth riding

PLAN.md **23.2.6**. The owner rode one of the shipped classes and the verdict
was: not good enough. 23.2.1 bundled all 72 in the APK, which fixed *how many*
a rider gets and said nothing about whether any of them is worth an hour.

This directory holds the rules, the catalogue those rules are applied to, and
the generator that turns the catalogue into `app/src/main/assets/classes/`.
**Do not hand-edit the assets.** Edit `catalogue.py`, run `build.py`, commit
both.

---

## What was wrong with the first 72

They were generated rather than designed: a duration was chosen, percentages of
it were sliced off, and zones were dropped into the slices. That is visible in
the artefacts it leaves, and all four of these were measured on the shipped
library rather than inferred from reading it.

**1. Nobody can ride a 97-second climb.** 770 intervals carried **101 distinct
lengths**. Only 213 of the 770 were a whole number of minutes and only 265 were
a multiple of even fifteen seconds. `97 s`, `146 s`, `195 s`, `334 s`, `41 s` —
these are percentages of a total, not efforts. A rider glancing at the ride
screen wants "two minutes of this"; they got 3:14 remaining of something that
ends at 3:17 for no reason.

**2. Seventy-two classes, twelve shapes.** Grouped by their sequence of zones:

| Classes | Sharing one shape |
|---|---|
| SS-04…07 **and** TH-01…07 | ten classes, one shape |
| SS-08…12 **and** TH-08…12 | ten classes, one shape |
| VO2-01…08 | eight classes, one shape |
| RC-02, RC-04…10 | eight classes, one shape |
| PZE-01, 02, 05, 07, 08, 10 | six classes, one shape |

Sweet Spot and Threshold were not merely repetitive within themselves — they
were *the same category*. `SS-03` and `TH-03` are the same class. Where two
classes did differ it was usually by one zone number: HC-01 through HC-04 are
one warmup, one shape, one cooldown, and a climb block that is Z4, Z5, Z4+Z5 or
Z4+Z6.

**3. Cadence was a lookup from the zone.** Z1 was always 80–90, Z2 always
85–95, Z3 always 88–98, Z4 always 90–100. Cadence carried no information the
zone did not already carry — so the library had no way to express the thing the
plan item calls out directly: *a Z5 effort at 85 rpm and one at 105 rpm are
different workouts*. The only place cadence varied independently was the climb
category, where it was a second lookup.

**4. Some of it is not rideable at all.** `TB-01` is sixteen consecutive rounds
of 20 s at Z6 with 10 s between them and no break at all between sets. Tabata
is eight rounds; the sixteenth round of a set of sixteen is not a Z6 effort,
it is a rider soft-pedalling and the prescription lying about it. `RC-01` is a
twenty-minute *recovery* class with a 6½-minute Z2 block in the middle of it.

**5. The warmup was a flat block and the cooldown was another one.** Four
minutes at Z1 80–90, identically, in front of every class in the library —
including the VO2 classes, where the first hard effort is therefore also the
warmup for the second.

---

## The rules

These are the rules the rebuild is designed to, and **most of them are asserted
in `ClassLibraryAssetsTest`** so a future generator that drifts fails the build
rather than the ride. Where a rule is judgement rather than arithmetic it says
so.

### R1 — Every block is a length a rider can hold

Whole minutes at two minutes and above; below that, one of
**10, 15, 20, 30, 40, 45, 60, 75, 90, 105 seconds**. An explicit vocabulary
rather than "a multiple of *n*", because 20 s on / 10 s off is the Tabata
protocol and has to be sayable while 97 s — a percentage of a total — must not
be. The rebuilt library uses **20 distinct block lengths across all 72
classes**, against 101 before. *Tested.*

### R2 — The warmup warms up

Progressive, never one flat block: easy spin, then Z2, then a short Z3 lift.
Anything with work at Z5 or above gets **primers** on top of that — 1 to 3
short efforts at the work zone with full recovery — so the first work interval
is work rather than the real warmup.

*Tested:* the class opens with at least two minutes at Z1; at least three
distinct zones are ridden before the first block at Z4 or above; at least five
minutes elapse before it; and in a class that reaches Z5, **the first block at
Z5+ is no longer than 45 seconds**, which is the primer stated as arithmetic.

A class that never leaves Z2 is exempt, because it has no work to be warm for.
That is what makes Recovery exempt rather than special-cased.

### R3 — The cooldown cools down

At least 3 minutes, descending, ending on Z1. *Tested.*

### R4 — Recovery is proportionate to the effort it follows

After an effort at Z5 or above, recovery is at least as long as the effort;
after Z4, at least half.

Two exemptions, both deliberate. **Efforts under a minute are exempt** — 20 s
on / 10 s off is a protocol, not a shortfall, and R6 is what bounds how much of
it a class may contain. And the rule applies only to a recovery block *between*
two efforts: the block after the last interval is the cooldown, and holding it
to this ratio would say nothing about the session. *Tested.*

### R5 — Cadence is a separate axis from zone

Every block names a cadence intent, and the intent is chosen for the session
rather than derived from the zone:

| Intent | rpm | What it is for |
|---|---|---|
| `GRIND` | 50–60 | Seated heavy torque. Strength work, not aerobic work |
| `CLIMB` | 60–70 | A climb you stay seated for |
| `STAND` | 70–80 | Out of the saddle, or the transition into it |
| `EASY` | 75–85 | Recovery spin. Low enough to be genuinely easy |
| `STEADY` | 80–90 | The default riding cadence |
| `BRISK` | 85–95 | Endurance with intent |
| `FAST` | 95–105 | Aerobic work on the fast side |
| `SPIN` | 105–115 | Leg speed |
| `SURGE` | 110–125 | Sprints |

The library must contain the same zone ridden at more than one intent, or the
axis is decorative. *Tested: at least four zones appear at three or more
distinct cadence bands.*

### R6 — The dose matches what the zone is for

Two halves, both tested. **Ceilings**, per class:

| Zone | Longest single block | Total |
|---|---|---|
| Z4 | 20 min | 40 min |
| Z5 | 8 min | 20 min |
| Z6 | 90 s | 10 min |
| Z7 | 30 s | 3 min |

The single-block caps are the important ones: a Z6 block of four minutes is not
an anaerobic effort, it is a rider failing at Z5 and the class having no idea.
There is also a **stacking** cap — no more than eight consecutive efforts at
Z6+ without a recovery block of 45 s or more — which is the rule the old
`TB-01` broke sixteen rounds deep.

**Floors**, so a category delivers what its name promises:

| Category | Floor |
|---|---|
| Sweet Spot, Threshold, Climbs | 25% of the class at Z4+ |
| VO2 Max | 15% at Z5+ |
| Sprints | 3 minutes at Z6+ |

A fraction rather than a number of minutes, because a 20-minute threshold class
and a 60-minute one are not owed the same dose.

### R7 — A recovery class recovers

Nothing above Z2, and Z2 is a minority of the ride, not the bulk of it.
*Tested.*

### R8 — The work has a shape, and the shape has a name

Each class is built from one of a small set of structures, and the structure is
what the title names:

- **Steady** — one long block. Endurance, sweet spot, threshold.
- **Sets** — fixed work, fixed recovery. The plainest interval session.
- **Build** — each block harder than the last, or longer.
- **Descend** — the reverse; the hardest work first, while the legs are there.
- **Pyramid** — up and back down, by duration or by zone.
- **Over/under** — alternating either side of a boundary without full recovery.
- **Shrinking rest** — fixed work, recovery that gets shorter.
- **Mixed** — deliberately varied, for the classes that are about variety.

*Not tested*, because "did this build?" is not a thing arithmetic can check —
but it is what `catalogue.py` is written in, so a class that has no shape is
hard to author by accident.

### R9 — No two classes are the same class

No two classes may share a full signature (zones, lengths and cadences), and
two classes of the same category and duration must differ in more than one
block. *Tested.* The rebuilt library has **50 distinct sequences of zones
across 72 classes**, against 12 before. It was 51 until `SWT-05` was replaced
by `SWT-13` (PLAN 25.4.3), whose zone sequence is `SWT-12`'s — which the rule
allows and should: one is 30 minutes of 4-5-6 over the gear and the other an
hour of 10/10/15, so they share a *shape* (three efforts, two recoveries) and
nothing a rider would recognise. The number is a measure of variety, not a
target; a class earns its blocks from what it is for.

### R10 — The title says what the ride is

Name the shape and the demand — "Threshold 3×8", "Descending Climbs", "Sweet
Spot Over/Under". Not the category and the length, which the rider can already
see.

**It said *not tested*, and every one of the 72 titles broke it.** Each ended
in its own duration — "The Long Climb 30" drawn beside a chip already reading
`30 min`, on the library, the start screen, the ride screen and in history —
which is the exact thing the sentence above bans, repeated seventy-two times
in the file the sentence is about. That is what an untested rule is worth: it
described the library nobody had built. `build.py` now checks it, and the
titles are the shape and the demand alone.

Two things the check had to get right, both learned by running it:

- **It matches the class's own length, not a trailing number.** `SWT-01` is
  "Sweet Spot 5 + 4" and the 4 is a block length; the looser version flagged it
  on the first run.
- **Titles must now be unique on their own**, because the duration was quietly
  doing that work. Stripping it produced no collisions across all 72, but
  nothing would have caught the next one, so uniqueness is checked here too.

A rename is safe — see the note at the end of this rule.

**And it may only name what the intervals carry.** PLAN.md **25.4.2**. Four
classes were named after a position their blocks did not prescribe: `END-08`
"Seated Climbs 45", `SWT-05` "Big Gear Sweet Spot 30", `THR-06` "Big Gear
Threshold 4×4 30" and `END-12` "Big Gear Endurance 60". That is the same defect
25.1 opened with — `CLB-02` was called "Standing Attacks" and nothing but its
title made it standing — except pointed the other way: the ride screen, the
spoken coach and the overlay all say *nothing* about position while the title
says "seated", so the rider is told one thing by the name and another by every
surface that speaks during the ride.

The owner's call was to rename rather than to bend R11's cap, and the working
rule that comes out of it:

> **A position word in a title is a promise that the blocks say it too.**
> "Seated", "standing", "out of the saddle" — and **"big gear"**, which in
> cycling usage means seated torque and reads as the same instruction.

*Tested — since PLAN 23.2.8, and it was not before.* The rule sat in this
paragraph for two months and was enforced on no surface: not on titles at all,
and on descriptions in the **standing direction only**. It is R10's own lesson
one rule along, so the second time it got a check rather than a sentence, and
one shared with R13 so the two surfaces cannot drift. What it found the day it
was written is at the end of R13.

So `SWT-09` "Big Gear / Fast Legs 45" keeps its name: its two big-gear blocks
*are* marked `SEATED`, and at 5 minutes each with 3 minutes between them they
are under both the length cap and the nagging guide. The other four are named
off the axis the data does carry — cadence:

| Was | Is | What the data says |
|---|---|---|
| `END-08` Seated Climbs 45 | **Tempo Climbs 5×5 45** | 5 × 5 min at Z3, `CLIMB` |
| `END-12` Big Gear Endurance 60 | **Climb and Spin 60** | 6 min `CLIMB` alternating 1 min `SPIN`, all Z2 |
| `SWT-05` Big Gear Sweet Spot 30 | **Low Cadence Sweet Spot 30** | 4 × 4 min at Z4, `CLIMB`, spin recoveries — since replaced by `SWT-13`, *Low Cadence Sweet Spot 4-5-6 30*, because those blocks were `THR-06`'s (25.4.3) |
| `THR-06` Big Gear Threshold 4×4 30 | **Low Cadence Threshold 4×4 30** | 4 × 4 min at Z4, `CLIMB`, easy recoveries |

"Low cadence" rather than "big gear" because it is the thing the interval
literally states — 60–70 rpm is on the class detail screen and on the ride
screen — and because it makes no claim about the saddle either way, which is
exactly what an unpositioned block means. It is also what the rest of the
industry calls these: TrainerRoad and Wahoo SYSTM both name the variant "low
cadence", and Peloton, which has no way to prescribe anything, sells the same
session as a "Climb Ride".

**A rename is safe and a re-id is not.** `workouts.class_id` is the foreign
key; the title is not, so a ride recorded on `END-08` still resolves and simply
displays the better name. It reaches a tablet that has already seeded because
`build.py`'s fingerprint hashes each file's whole body — `ClassTemplateSeeder`
upserts on a fingerprint change, and nothing is retired because no id moved.

### R11 — A position is an instruction, so it has to be a possible one

PLAN.md **25.1**. A block may name `STANDING`, `SEATED` or nothing, and
**nothing is the default and means the rider chooses**. Three bounds, all
checked by `build.py` and by `ClassLibraryAssetsTest`:

| | |
|---|---|
| Standing block length | at most **3 minutes** |
| Standing cadence | top of the band at most **110 rpm** |
| Positioned share of a class | **less than half** of its running time |

The first two are about whether the instruction can be followed at all: nobody
rides out of the saddle for five minutes, and "stand up" at 120 rpm is a
sentence with no action behind it.

The third is the one worth arguing about, and it is the same argument as the
default. **A class that prescribes a position for every block is nagging, not
coaching** — the point of the field is that a handful of moments in a session
are genuinely standing moments and the rest are the rider's own business. Ten
of the 72 classes name a position at all, and that is the intended proportion
rather than a stage on the way to all of them.

**A fourth bound is not arithmetic and cannot be tested, so it lives here: do
not put a position on a tight repeat.** A positioned effort with an
*unpositioned* recovery after it is a fresh instruction next time round — that
is deliberate, and it is right for `CLB-02`, where the rider sits down for 45
seconds between standing attacks and has to be told to get up again. It is
wrong at Tabata spacing: `sets(8, on=20, off=10, position=…)` calls the same
instruction eight times in four minutes, out loud and on the overlay, which is
precisely the nagging the half-a-class cap exists to prevent and which no
percentage would catch. **Rough guide: 30 seconds of recovery between
positioned reps.** Below that, position the block or the set, not every rep of
it.

### R12 — One instruction at a time, and the block says which

PLAN.md **11.7**. The owner's complaint, riding: *"what do i do? do i focus on
zone, cadence, or resistance?"* The answer is that it was never three
instructions. Power is not something a rider *does* — it is what happens when
you turn the pedals at some cadence against some resistance. One outcome, two
controls, drawn as three tiles of equal weight.

So **exactly one metric is the instruction on any block**, and the block names
it. In practice the cadence intent already does:

| Intent | rpm | Governs |
|---|---|---|
| `GRIND` | 50–60 | **cadence** |
| `CLIMB` | 60–70 | **cadence** |
| `STAND` | 70–80 | power |
| `EASY` | 75–85 | power |
| `STEADY` | 80–90 | power |
| `BRISK` | 85–95 | power |
| `FAST` | 95–105 | power |
| `SPIN` | 105–115 | **cadence** |
| `SURGE` | 110–125 | **cadence** |

The tails govern and the middle does not, and that is a statement about
meaning rather than about numbers: an author writing `GRIND` has said *this
block is about turning a big gear slowly*, and an author writing `STEADY` has
said *ride normally and let the watts be the point*. **This is not inference
from a band** — the band is a consequence of the intent, which is why the
plan's route (a) was rejected and this is route (b).

Override either way when a block means something the intent does not:
`POWER(CLIMB)` for a threshold effort that happens to sit at climbing cadence,
`CADENCE(SWEET_SPOT_BAND)` for the owner's *"perhaps there's a way we can use
both"* case. Both read at the call site.

Three bounds, checked by `build.py` and by `ClassLibraryAssetsTest`:

| | |
|---|---|
| A cadence-governed band | must fall outside **75–95 rpm** |
| Climbs and Sprints | must have at least one cadence-governed block |
| Cadence's share of the library | at most **a third** of all blocks |

The first is the one that does real work. A block claiming to be governed by
80–90 rpm is claiming nothing: that is the library's default seated cadence,
and prescribing it is exactly how a rider spinning a perfectly good 92 rpm
during a threshold effort came to be shown amber for it (11.7.1a).

The third is a ceiling rather than a floor, and the failure mode it guards is
the field spreading until "one instruction at a time" means "always the
cadence". Today it sits at **231 of 1071 blocks, 22%** — the same 231 the plan
measured out in the tails before the field existed.

On disk the field is `governed_by`, **optional, and absent means power** — the
same additive shape as `target_position`, so every class written before it
decodes unchanged. Unlike position there is no third claim: absent is not "the
rider chooses", it is simply the ordinary case.

### R13 — Every class says what it is for

**The only prose in this library, and the only rule a build cannot check for
truth.** Everything else about a class is *derived from its blocks* — the title,
the duration, the shape sentence, the chart on the Start Class screen — so the
app can tell a rider that a ride is "20 min · Climbs · four hard efforts" and
cannot tell them why they would choose it, what it trains, or what it will feel
like at minute fourteen. That gap is what made the Start Class screen answer
*"here are 52 numbers"* to somebody meeting the app for the first time.

The descriptions live in **`descriptions.py`**, keyed by id, deliberately apart
from the blocks in `catalogue.py`. Two reasons: the catalogue stays readable as
blocks of real time, and these 72 sentences have to be **read as a set** to be
any good — the failure mode is not one wrong sentence, it is 72 that all sound
alike, which a rider learns to skip within four classes.

What `build.py` and `ClassLibraryAssetsTest` hold:

- every class has one, between **80 and 320 characters**;
- it does **not name its own duration**, which is drawn beside it — R10's
  failure, one surface along;
- it does **not name its own category**, also drawn beside it. This is the most
  useful rule of the five, and not because of repetition: it is what forces
  *"the hardest pace you could hold for about an hour"* instead of
  *"threshold"*. The plain sentence is the one a first-time rider can act on,
  and the jargon was never doing the work anyway;
- **no units and no acronyms** — no watts, no kilojoules, no FTP, no rpm. A
  rider choosing tonight's ride is deciding, not reading a measurement. The
  *vocabulary* of riding is fine: "a firm effort", "a heavy cadence", "flat
  out" all describe feelings;
- and a description **promising a position** has to be describing blocks that
  ask for it. That is R11 and PLAN 25.4.2 again, and it is the only way a
  sentence here can be wrong in a way arithmetic can catch.

**That last clause read `standing` only, and both classes that broke it broke
it the other way** (PLAN 23.2.8). `CLB-04` "Rolling Climbs" said *"repeated
seated rises"* and **not one of its seventeen blocks carried a position** —
it is `END-04` line for line otherwise, same helper, same lengths, same
`CLIMB` cadence, one zone apart, and `END-04` has `position=SEATED`. And
`SPR-05` "Sprints, Three Ways" promised *"seated, out of the saddle, and wound
up from a low speed"* while its only positioned blocks ask the rider to stand
up. Two fixes of different kinds, and which one applies is worth knowing before
the next one:

- **`CLB-04` got the position**, because the description had always been right
  and the blocks were the omission. 25.4.1's audit of positions across the
  library went past it because it was looking at the classes that already had
  one.
- **`SPR-05` got a new sentence**, because making its first set a seated torque
  effort is a *different exercise* under a live id — PLAN 25.4.3, and the
  reason `SWT-13` was a new id rather than an edited `SWT-05`.

What no rule can check is whether the sentence is *true* of the ride. Read it
against the blocks — and the two above are what that is worth, because both
sat in a library every rule in this file passed.

---

## The seven categories

Two were renamed on the way through, because the old names described a Peloton
menu rather than what the classes contain.

| Category | Ids | Classes | What it is |
|---|---|---|---|
| Endurance | `END-` | 12 | Z2 with Z3 accents. Where most riders spend most of their time, so it carries most of the cadence variety |
| Recovery | `REC-` | 10 | Nothing above Z2. The category most easily ruined by being made interesting |
| Sweet Spot | `SWT-` | 12 | Long blocks at the Z3/Z4 boundary, short recoveries, a cadence you could hold all day |
| Threshold | `THR-` | 12 | Sustained Z4, real recovery, faster cadence, and the occasional trip above it |
| VO2 Max | `VMX-` | 10 | Z5, and therefore primed warmups throughout |
| Climbs | `CLB-` | 10 | Heavy torque at `GRIND` and `CLIMB`. Was "HIIT & Heavy Climbs", which was two categories with one name |
| Sprints | `SPR-` | 6 | Tabata and its relatives. Was "Tabata Bursts", of which only some were Tabata |

Sweet Spot and Threshold overlap by design, and the old library collapsed them
into each other completely — `SS-03` and `TH-03` were the same class. What
separates them here is the work-to-rest ratio and the cadence, and every class
in both is checked against R9.

## Three constraints that are not negotiable

**`workouts.class_id` is a foreign key.** A class id that has been ridden must
keep existing or the rider's history loses its link to what they rode. The
rebuild therefore **does not reuse the old ids** — it uses a new series, and
`ClassTemplateSeeder` *retires* an old class that a ride points at rather than
deleting it (PLAN 23.2.6c). Retired classes stay out of the library browser and
stay resolvable from history.

Reusing the ids was the other option and it is the wrong one for the same
reason 2.7.5 came down the way it did: changing what `HC-01` *is*, while a real
20-minute ride on the bike still points at it, silently rewrites what that ride
was. Mark and say so; change nothing behind the rider's back.

**A bad generator must fail the build, not the ride.** `ClassLibraryAssetsTest`
already enforced contiguity and agreement with `duration_sec`; it now enforces
most of the rules above. Run `./gradlew testDebugUnitTest` after every
`build.py`.

**The cloud copy has to move with the assets**, or the update channel (23.2.3)
hands the old library straight back. Nothing reads the cloud copy today, so
this is a note for whoever builds 23.2.3 rather than a step here.

---

## Running it

```bash
python3 classlibrary/build.py
```

Rewrites `app/src/main/assets/classes/` from scratch — it deletes the tree and
regenerates it, so a class removed from the catalogue disappears from the
assets. It prints a summary and refuses to write anything if the catalogue
breaks a rule it can check itself.

## The catalogue

`catalogue.py` holds the 72 sessions, one call per class, in blocks of real
time. The generator does the arithmetic — running timestamps, the
`intervals_json` string, the category directory, `duration_sec` — so the only
thing a session author decides is the training.
