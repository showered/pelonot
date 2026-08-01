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
block. *Tested.* The rebuilt library has **51 distinct sequences of zones
across 72 classes**, against 12 before.

### R10 — The title says what the ride is

Name the shape and the demand — "Threshold 3×8", "Descending Climbs", "Sweet
Spot Over/Under". Not the category and the length, which the rider can already
see. *Not tested.*

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
